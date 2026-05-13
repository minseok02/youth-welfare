package com.example.welfare.policy.service;

import com.example.welfare.chat.service.PolicyChunkEmbeddingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class PolicyEmbeddingRefreshRequestServiceTest {

    @Mock
    private PolicyChunkEmbeddingService policyChunkEmbeddingService;

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("트랜잭션이 없으면 즉시 임베딩 refresh 를 실행한다")
    void refreshesImmediatelyWithoutTransaction() {
        PolicyEmbeddingRefreshRequestService service =
                new PolicyEmbeddingRefreshRequestService(policyChunkEmbeddingService);

        service.request(List.of(4L, 4L, 9L));

        verify(policyChunkEmbeddingService).refreshEmbeddingsForServiceIds(List.of(4L, 9L));
    }

    @Test
    @DisplayName("트랜잭션이 있으면 service id 를 모았다가 afterCommit 에 한 번만 refresh 한다")
    void batchesRefreshUntilAfterCommit() {
        PolicyEmbeddingRefreshRequestService service =
                new PolicyEmbeddingRefreshRequestService(policyChunkEmbeddingService);
        TransactionSynchronizationManager.initSynchronization();

        service.request(List.of(4L, 4L));
        service.request(List.of(9L));

        verifyNoMoreInteractions(policyChunkEmbeddingService);
        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCommit();
            synchronization.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
        }

        verify(policyChunkEmbeddingService).refreshEmbeddingsForServiceIds(List.of(4L, 9L));
    }

    @Test
    @DisplayName("batch scope 안에서는 요청을 모았다가 종료 시 한 번만 refresh 한다")
    void batchesRefreshWithinBatchScope() {
        PolicyEmbeddingRefreshRequestService service =
                new PolicyEmbeddingRefreshRequestService(policyChunkEmbeddingService);

        service.runInBatch(() -> {
            service.request(List.of(4L, 4L));
            service.request(List.of(9L));
        });

        verify(policyChunkEmbeddingService).refreshEmbeddingsForServiceIds(List.of(4L, 9L));
    }

    @Test
    @DisplayName("batch scope 안에서는 트랜잭션 afterCommit 대신 batch 종료 시 refresh 한다")
    void batchScopeOverridesTransactionSync() {
        PolicyEmbeddingRefreshRequestService service =
                new PolicyEmbeddingRefreshRequestService(policyChunkEmbeddingService);
        TransactionSynchronizationManager.initSynchronization();

        service.runInBatch(() -> service.request(List.of(4L, 9L)));

        verify(policyChunkEmbeddingService).refreshEmbeddingsForServiceIds(List.of(4L, 9L));
        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCommit();
            synchronization.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
        }
        verifyNoMoreInteractions(policyChunkEmbeddingService);
    }
}
