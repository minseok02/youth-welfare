package com.example.welfare.policy.service;

import com.example.welfare.chat.service.PolicyChunkEmbeddingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
@Slf4j
public class PolicyEmbeddingRefreshRequestService {

    private static final Object RESOURCE_KEY = new Object();
    private static final ThreadLocal<BatchScope> BATCH_SCOPE = new ThreadLocal<>();

    private final PolicyChunkEmbeddingService policyChunkEmbeddingService;

    public void request(Long serviceId) {
        if (serviceId == null) {
            return;
        }
        request(List.of(serviceId));
    }

    public void request(List<Long> serviceIds) {
        if (CollectionUtils.isEmpty(serviceIds)) {
            return;
        }
        List<Long> distinctIds = serviceIds.stream()
                .filter(id -> id != null)
                .distinct()
                .toList();
        if (distinctIds.isEmpty()) {
            return;
        }

        BatchScope batchScope = BATCH_SCOPE.get();
        if (batchScope != null) {
            batchScope.pendingServiceIds().addAll(distinctIds);
            return;
        }

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            policyChunkEmbeddingService.refreshEmbeddingsForServiceIds(distinctIds);
            return;
        }

        Set<Long> pendingServiceIds = getOrRegisterPendingServiceIds();
        pendingServiceIds.addAll(distinctIds);
    }

    public void runInBatch(Runnable runnable) {
        runInBatch(() -> {
            runnable.run();
            return null;
        });
    }

    public <T> T runInBatch(Supplier<T> supplier) {
        BatchScope existingScope = BATCH_SCOPE.get();
        if (existingScope != null) {
            existingScope.incrementDepth();
            try {
                return supplier.get();
            } finally {
                existingScope.decrementDepth();
            }
        }

        BatchScope newScope = new BatchScope();
        BATCH_SCOPE.set(newScope);
        try {
            T result = supplier.get();
            refreshBatchScopeSafely(newScope);
            return result;
        } finally {
            BATCH_SCOPE.remove();
        }
    }

    private void refreshBatchScopeSafely(BatchScope batchScope) {
        if (batchScope.pendingServiceIds().isEmpty()) {
            return;
        }
        List<Long> serviceIds = new ArrayList<>(batchScope.pendingServiceIds());
        try {
            policyChunkEmbeddingService.refreshEmbeddingsForServiceIds(serviceIds);
        } catch (RuntimeException e) {
            log.warn("[PolicyEmbeddingRefreshRequestService] batch embedding refresh skipped serviceCount={} err={}",
                    serviceIds.size(), e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Set<Long> getOrRegisterPendingServiceIds() {
        Object boundResource = TransactionSynchronizationManager.getResource(RESOURCE_KEY);
        if (boundResource instanceof Set<?> pendingServiceIds) {
            return (Set<Long>) pendingServiceIds;
        }

        Set<Long> pendingServiceIds = new LinkedHashSet<>();
        TransactionSynchronizationManager.bindResource(RESOURCE_KEY, pendingServiceIds);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                policyChunkEmbeddingService.refreshEmbeddingsForServiceIds(new ArrayList<>(pendingServiceIds));
            }

            @Override
            public void afterCompletion(int status) {
                TransactionSynchronizationManager.unbindResourceIfPossible(RESOURCE_KEY);
            }
        });
        return pendingServiceIds;
    }

    private static final class BatchScope {
        private final Set<Long> pendingServiceIds = new LinkedHashSet<>();
        private int depth = 1;

        private Set<Long> pendingServiceIds() {
            return pendingServiceIds;
        }

        private void incrementDepth() {
            depth++;
        }

        private void decrementDepth() {
            if (depth > 0) {
                depth--;
            }
        }
    }
}
