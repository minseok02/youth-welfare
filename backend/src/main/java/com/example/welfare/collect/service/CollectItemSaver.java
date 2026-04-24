package com.example.welfare.collect.service;

import com.example.welfare.collect.dto.BokjiroCentralDto;
import com.example.welfare.collect.dto.BokjiroLocalDto;
import com.example.welfare.collect.dto.YouthApiDto;
import com.example.welfare.collect.mapper.WelfareServiceMapper;
import com.example.welfare.policy.entity.ServiceRegion;
import com.example.welfare.policy.entity.ServiceTag;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.ServiceTagRepository;
import com.example.welfare.policy.repository.WelfareServiceRepository;
import com.example.welfare.policy.service.SearchYouthRelevanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * 아이템 단위 저장 — 각 아이템을 별도 트랜잭션으로 처리하여
 * 한 아이템 실패가 전체 배치를 롤백시키지 않도록 분리
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CollectItemSaver {

    private final WelfareServiceMapper mapper;
    private final WelfareServiceRepository welfareServiceRepository;
    private final ServiceTagRepository tagRepository;
    private final PlatformTransactionManager transactionManager;
    private final JdbcTemplate jdbcTemplate;
    private final SearchYouthRelevanceService searchYouthRelevanceService;

    private static final int MAX_SAVE_ATTEMPTS = 3;
    private static final long BASE_BACKOFF_MS = 200L;

    public void saveYouth(YouthApiDto.Item item) {
        executeWithRetry("YOUTH", item.getPlcyNo(), () -> saveYouthOnce(item));
    }

    public void saveYouthOnce(YouthApiDto.Item item) {
        WelfareService entity = upsertService(
                WelfareService.SourceType.YOUTH,
                item.getPlcyNo(),
                mapper.fromYouth(item)
        );
        upsertRegions(entity, mapper.regionsFromYouth(item, entity));
        List<ServiceTag> tags = mapper.tagsFromYouth(item, entity);
        upsertTags(entity, tags);
        searchYouthRelevanceService.refreshForService(entity, tags);
    }

    public void saveBokjiroCentral(BokjiroCentralDto.Item item) {
        executeWithRetry("BOKJIRO_CENTRAL", item.getServId(), () -> saveBokjiroCentralOnce(item));
    }

    public void saveBokjiroCentralOnce(BokjiroCentralDto.Item item) {
        WelfareService entity = upsertService(
                WelfareService.SourceType.BOKJIRO_CENTRAL,
                item.getServId(),
                mapper.fromBokjiroCentral(item)
        );
        upsertRegions(entity, mapper.regionsFromBokjiroCentral(item, entity));
        List<ServiceTag> tags = mapper.tagsFromBokjiroCentral(item, entity);
        upsertTags(entity, tags);
        searchYouthRelevanceService.refreshForService(entity, tags);
    }

    public void saveBokjiroLocal(BokjiroLocalDto.Item item) {
        executeWithRetry("BOKJIRO_LOCAL", item.getServId(), () -> saveBokjiroLocalOnce(item));
    }

    public void saveBokjiroLocalOnce(BokjiroLocalDto.Item item) {
        WelfareService entity = upsertService(
                WelfareService.SourceType.BOKJIRO_LOCAL,
                item.getServId(),
                mapper.fromBokjiroLocal(item)
        );
        upsertRegions(entity, mapper.regionsFromBokjiroLocal(item, entity));
        List<ServiceTag> tags = mapper.tagsFromBokjiroLocal(item, entity);
        upsertTags(entity, tags);
        searchYouthRelevanceService.refreshForService(entity, tags);
    }

    private WelfareService upsertService(WelfareService.SourceType sourceType,
                                          String sourceId, WelfareService incoming) {
        Optional<WelfareService> existing = welfareServiceRepository
                .findBySourceTypeAndSourceId(sourceType, sourceId);
        if (existing.isPresent()) {
            WelfareService ws = existing.get();
            ws.updateFromCollect(incoming);
            return ws;
        } else {
            return welfareServiceRepository.saveAndFlush(incoming);
        }
    }

    private void upsertRegions(WelfareService service, List<ServiceRegion> regions) {
        jdbcTemplate.update("DELETE FROM service_regions WHERE service_id = ?", service.getId());
        if (regions.isEmpty()) return;

        jdbcTemplate.batchUpdate(
                "INSERT INTO service_regions (service_id, region_code, sido_name, sgg_name) VALUES (?, ?, ?, ?)",
                regions,
                200,
                this::bindRegion
        );
    }

    private void bindRegion(PreparedStatement ps, ServiceRegion region) throws SQLException {
        ps.setLong(1, region.getService().getId());
        ps.setString(2, region.getRegionCode());
        ps.setString(3, region.getSidoName());
        ps.setString(4, region.getSggName());
    }

    private void upsertTags(WelfareService service, List<ServiceTag> tags) {
        for (ServiceTag tag : tags) {
            tagRepository.upsert(
                    service.getId(),
                    tag.getTagType().name(),
                    tag.getTagValue()
            );
        }
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
}
