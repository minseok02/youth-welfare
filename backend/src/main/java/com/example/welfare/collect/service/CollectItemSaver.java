package com.example.welfare.collect.service;

import com.example.welfare.collect.mapper.WelfareServiceMapper;
import com.example.welfare.collect.normalization.NormalizedPolicyAggregate;
import com.example.welfare.collect.repository.CollectItemCommandRepository;
import com.example.welfare.collect.repository.CollectItemRegionCommandRepository;
import com.example.welfare.collect.repository.CollectItemReadRepository;
import com.example.welfare.collect.repository.CollectItemTagCommandRepository;
import com.example.welfare.collect.support.ListCollectSourceBinding;
import com.example.welfare.policy.entity.ServiceRegion;
import com.example.welfare.policy.entity.ServiceTag;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.service.SearchYouthRelevanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * 아이템 단위 저장 — 각 아이템을 별도 트랜잭션으로 처리하여
 * 한 아이템 실패가 전체 배치를 롤백시키지 않도록 분리
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CollectItemSaver {

    private final WelfareServiceMapper mapper;
    private final CollectItemReadRepository collectItemReadRepository;
    private final CollectItemCommandRepository collectItemCommandRepository;
    private final CollectItemRegionCommandRepository collectItemRegionCommandRepository;
    private final CollectItemTagCommandRepository collectItemTagCommandRepository;
    private final PlatformTransactionManager transactionManager;
    private final CollectPolicyAggregateApplyService collectPolicyAggregateApplyService;

    private static final int MAX_SAVE_ATTEMPTS = 3;
    private static final long BASE_BACKOFF_MS = 200L;

    public void save(SaveCommand command) {
        validateCommand(command);
        executeWithRetry(command.sourceType().name(), command.sourceId(), () -> saveOnce(command));
    }

    public <T> void save(ListCollectSourceBinding<T> binding, T item) {
        save(binding.toSaveCommand(item));
    }

    public <T> void save(ListCollectSourceBinding<T> binding,
                         T item,
                         NormalizedPolicyAggregate aggregate) {
        SaveCommand command = binding.toSaveCommand(item);
        save(SaveCommand.builder()
                .sourceType(command.sourceType())
                .sourceId(command.sourceId())
                .incoming(command.incoming())
                .regions(command.regions())
                .tags(command.tags())
                .aggregate(aggregate)
                .build());
    }

    public <T> void saveOnce(ListCollectSourceBinding<T> binding, T item) {
        saveOnce(binding.toSaveCommand(item));
    }

    public <T> void saveOnce(ListCollectSourceBinding<T> binding,
                             T item,
                             NormalizedPolicyAggregate aggregate) {
        SaveCommand command = binding.toSaveCommand(item);
        saveOnce(SaveCommand.builder()
                .sourceType(command.sourceType())
                .sourceId(command.sourceId())
                .incoming(command.incoming())
                .regions(command.regions())
                .tags(command.tags())
                .aggregate(aggregate)
                .build());
    }

    public void saveOnce(SaveCommand command) {
        validateCommand(command);

        WelfareService entity = upsertService(
                command.sourceType(),
                command.sourceId(),
                command.incoming()
        );

        if (command.aggregate() != null) {
            validateAggregate(command.aggregate(), command.sourceType(), command.sourceId());
        }

        upsertRegions(entity, command.regions().apply(entity));
        List<ServiceTag> tags = replaceTags(entity, command.tags().apply(entity));
        collectPolicyAggregateApplyService.applyCollectedItem(entity, command.aggregate(), tags);
    }

    private WelfareService upsertService(WelfareService.SourceType sourceType,
                                          String sourceId, WelfareService incoming) {
        Optional<WelfareService> existing = collectItemReadRepository
                .findServiceBySourceTypeAndSourceId(sourceType, sourceId);
        if (existing.isPresent()) {
            WelfareService ws = existing.get();
            ws.updateFromCollect(incoming);
            return ws;
        } else {
            return collectItemCommandRepository.saveAndFlush(incoming);
        }
    }

    private void upsertRegions(WelfareService service, List<ServiceRegion> regions) {
        collectItemRegionCommandRepository.replaceAll(service.getId(), regions);
    }

    private List<ServiceTag> replaceTags(WelfareService service, List<ServiceTag> tags) {
        List<ServiceTag> normalizedTags = normalizeTags(service, tags);
        collectItemTagCommandRepository.replaceAll(service.getId(), normalizedTags);
        return normalizedTags;
    }

    private List<ServiceTag> normalizeTags(WelfareService service, List<ServiceTag> tags) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }

        Set<TagKey> seen = new LinkedHashSet<>();
        List<ServiceTag> normalized = new ArrayList<>();

        for (ServiceTag tag : tags) {
            if (tag == null || tag.getTagType() == null || tag.getTagValue() == null || tag.getTagValue().isBlank()) {
                continue;
            }
            TagKey key = new TagKey(tag.getTagType(), tag.getTagValue());
            if (!seen.add(key)) {
                continue;
            }
            normalized.add(ServiceTag.builder()
                    .service(service)
                    .tagType(tag.getTagType())
                    .tagValue(tag.getTagValue())
                    .build());
        }

        return normalized;
    }

    private void executeWithRetry(String sourceType, String sourceId, CollectRetrySupport.CheckedRunnable action) {
        for (int attempt = 1; attempt <= MAX_SAVE_ATTEMPTS; attempt++) {
            try {
                runInNewTransaction(action);
                return;
            } catch (Exception e) {
                if (!isRetryableLockException(e) || attempt >= MAX_SAVE_ATTEMPTS) {
                    if (attempt > 1) {
                        log.warn("[CollectItemSaver] 저장 재시도 실패 sourceType={} sourceId={} attempts={} err={}",
                                sourceType, sourceId, attempt, e.getMessage());
                    }
                    rethrowUnchecked(e);
                    return;
                }

                long waitMs = BASE_BACKOFF_MS * attempt;
                log.warn("[CollectItemSaver] 저장 재시도 sourceType={} sourceId={} attempt={}/{} waitMs={} err={}",
                        sourceType, sourceId, attempt, MAX_SAVE_ATTEMPTS, waitMs, e.getMessage());
                sleepQuietly(waitMs);
            }
        }
    }

    private void runInNewTransaction(CollectRetrySupport.CheckedRunnable action) throws Exception {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        template.executeWithoutResult(status -> {
            try {
                action.run();
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
    }

    private boolean isRetryableLockException(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof DeadlockLoserDataAccessException
                    || current instanceof CannotAcquireLockException
                    || current instanceof PessimisticLockingFailureException
                    || current instanceof ObjectOptimisticLockingFailureException) {
                return true;
            }

            String message = current.getMessage();
            if (message != null) {
                String normalized = message.toLowerCase();
                if (normalized.contains("deadlock")
                        || normalized.contains("lock wait timeout")
                        || normalized.contains("row was updated or deleted by another transaction")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("수집 재시도 대기 중 인터럽트 발생", e);
        }
    }

    private void rethrowUnchecked(Exception e) {
        if (e instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        throw new IllegalStateException(e);
    }

    private void validateAggregate(NormalizedPolicyAggregate aggregate,
                                   WelfareService.SourceType expectedSourceType,
                                   String expectedSourceId) {
        if (aggregate == null || aggregate.core() == null) {
            throw new IllegalArgumentException("normalized aggregate/core 는 필수입니다.");
        }
        if (aggregate.core().sourceType() == null || aggregate.core().sourceId() == null || aggregate.core().sourceId().isBlank()) {
            throw new IllegalArgumentException("normalized aggregate sourceType/sourceId 는 필수입니다.");
        }
        if (aggregate.core().sourceType() != NormalizedPolicyAggregate.SourceType.valueOf(expectedSourceType.name())
                || !expectedSourceId.equals(aggregate.core().sourceId())) {
            throw new IllegalArgumentException("normalized aggregate source identity 가 item 과 일치하지 않습니다.");
        }
    }
    private void validateCommand(SaveCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("save command 는 필수입니다.");
        }
        if (command.sourceType() == null || command.sourceId() == null || command.sourceId().isBlank()) {
            throw new IllegalArgumentException("save command sourceType/sourceId 는 필수입니다.");
        }
        if (command.incoming() == null) {
            throw new IllegalArgumentException("save command incoming 은 필수입니다.");
        }
        if (command.incoming().getSourceType() != command.sourceType()) {
            throw new IllegalArgumentException("save command sourceType 과 incoming.sourceType 이 일치하지 않습니다.");
        }
        if (!command.sourceId().equals(command.incoming().getSourceId())) {
            throw new IllegalArgumentException("save command sourceId 와 incoming.sourceId 가 일치하지 않습니다.");
        }
        if (command.regions() == null || command.tags() == null) {
            throw new IllegalArgumentException("save command regions/tags builder 는 필수입니다.");
        }
    }

    private record TagKey(ServiceTag.TagType tagType, String tagValue) {
    }

    @lombok.Builder
    public record SaveCommand(
            WelfareService.SourceType sourceType,
            String sourceId,
            WelfareService incoming,
            Function<WelfareService, List<ServiceRegion>> regions,
            Function<WelfareService, List<ServiceTag>> tags,
            NormalizedPolicyAggregate aggregate
    ) {
    }
}
