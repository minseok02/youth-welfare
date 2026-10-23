package com.example.welfare.collect.service;

import com.example.welfare.collect.normalization.NormalizedPolicySidecarBackfillService;
import com.example.welfare.collect.normalization.NormalizedPolicySidecarBackfillService.BackfillResult;
import com.example.welfare.collect.repository.CollectExecutionLockCleanupCommandRepository;
import com.example.welfare.collect.repository.CollectExecutionLockRepository;
import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CollectSidecarBackfillApplicationServiceTest {

    private static final int LIMIT = 25;
    private final List<String> events = new ArrayList<>();
    private final CollectExecutionLockRepository lockRepository = mock(CollectExecutionLockRepository.class);
    private final CollectExecutionLockCleanupCommandRepository cleanupRepository =
            mock(CollectExecutionLockCleanupCommandRepository.class);
    private NormalizedPolicySidecarBackfillService backfillService;
    private CollectSidecarBackfillApplicationService applicationService;
    private CollectExecutionGuard guard;
    private boolean requireTransaction;

    @BeforeEach
    void setUp() {
        when(lockRepository.tryAcquire(eq("collect-global"), any(), any(), any())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            events.add("acquire");
            return true;
        });
        when(cleanupRepository.release(eq("collect-global"), any())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            events.add("release");
            return true;
        });
        backfillService = mock(NormalizedPolicySidecarBackfillService.class, invocation -> {
            if (invocation.getMethod().getReturnType() != BackfillResult.class) {
                return org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
            }
            if (requireTransaction) {
                assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            }
            assertThat((int) invocation.getArgument(0)).isEqualTo(LIMIT);
            events.add(invocation.getMethod().getName());
            return new BackfillResult(2, 1, 0, 1);
        });
        guard = new CollectExecutionGuard(lockRepository, cleanupRepository, 15, 60);
        applicationService = new CollectSidecarBackfillApplicationService(guard, backfillService);
    }

    static Stream<BackfillCase> scopes() {
        return Stream.of(
                new BackfillCase("bokjiro", "list", false, List.of("backfillBokjiroListSidecars")),
                new BackfillCase("bokjiro", "detail", false, List.of("backfillBokjiroDetailSidecars")),
                new BackfillCase("bokjiro", "all", false,
                        List.of("backfillBokjiroListSidecars", "backfillBokjiroDetailSidecars")),
                new BackfillCase("gov24", "list", false, List.of("backfillGov24ListSidecars")),
                new BackfillCase("gov24", "regions", false, List.of("backfillGov24ListRegions")),
                new BackfillCase("gov24", "support", false, List.of("backfillGov24SupportConditionSidecars")),
                new BackfillCase("gov24", "support-conditions", false, List.of("backfillGov24SupportConditionSidecars")),
                new BackfillCase("gov24", "all", false,
                        List.of("backfillGov24ListSidecars", "backfillGov24ListRegions", "backfillGov24SupportConditionSidecars")),
                new BackfillCase("gov24", "list", true, List.of("backfillGov24MissingListSidecars"))
        );
    }

    @ParameterizedTest
    @MethodSource("scopes")
    @DisplayName("모든 sidecar scope는 전역 잠금 한 번 안에서 기존 순서와 집계를 유지한다")
    void runsEachScopeUnderOneGlobalLock(BackfillCase scope) {
        BackfillResult result = run(applicationService, scope);

        int calls = scope.calls().size();
        assertThat(result).isEqualTo(new BackfillResult(2 * calls, calls, 0, calls));
        List<String> expected = new ArrayList<>();
        expected.add("acquire");
        expected.addAll(scope.calls());
        expected.add("release");
        assertThat(events).containsExactlyElementsOf(expected);
        verify(lockRepository).tryAcquire(eq("collect-global"), any(), any(), any());
        verify(cleanupRepository).release(eq("collect-global"), any());
    }

    @ParameterizedTest
    @MethodSource("scopes")
    @DisplayName("전역 잠금 점유 시 모든 scope에서 내부 backfill과 잠금 해제를 실행하지 않는다")
    void rejectsEachScopeWhenGlobalLockIsBusy(BackfillCase scope) {
        when(lockRepository.tryAcquire(eq("collect-global"), any(), any(), any())).thenReturn(false);

        assertThatThrownBy(() -> run(applicationService, scope))
                .isInstanceOf(CustomException.class)
                .satisfies(error -> assertThat(((CustomException) error).getErrorCode())
                        .isEqualTo(ErrorCode.COLLECT_ALREADY_RUNNING));

        verify(lockRepository).tryAcquire(eq("collect-global"), any(), any(), any());
        verifyNoInteractions(backfillService, cleanupRepository);
    }

    @ParameterizedTest
    @CsvSource({"bokjiro,unknown,false", "gov24,unknown,false", "gov24,all,true", "gov24,regions,true",
            "gov24,support,true", "gov24,support-conditions,true", "gov24,unknown,true"})
    @DisplayName("잘못된 scope와 missingOnly 조합은 잠금 DB 접근 전에 거절한다")
    void rejectsInvalidScopeBeforeAcquiringLock(String provider, String scope, boolean missingOnly) {
        assertThatThrownBy(() -> run(applicationService, new BackfillCase(provider, scope, missingOnly, List.of())))
                .isInstanceOf(CustomException.class)
                .satisfies(error -> assertThat(((CustomException) error).getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT));

        verifyNoInteractions(lockRepository, cleanupRepository, backfillService);
    }

    @Test
    @DisplayName("Gov24 all의 지역 처리 예외는 후속 support를 실행하지 않고 잠금을 해제한다")
    void releasesGov24LockWithoutRunningLaterStageWhenRegionsFail() {
        IllegalStateException failure = new IllegalStateException("regions failed");
        doAnswer(invocation -> {
            events.add("backfillGov24ListRegions");
            throw failure;
        }).when(backfillService).backfillGov24ListRegions(LIMIT);

        assertThatThrownBy(() -> applicationService.backfillGov24("all", LIMIT, false)).isSameAs(failure);

        assertThat(events).containsExactly("acquire", "backfillGov24ListSidecars", "backfillGov24ListRegions", "release");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("실제 Spring 트랜잭션 프록시의 commit 또는 rollback이 잠금 해제보다 먼저 완료된다")
    void completesDownstreamTransactionsBeforeUnlocking(boolean failDetail) {
        requireTransaction = true;
        IllegalStateException failure = new IllegalStateException("detail failed");
        if (failDetail) {
            doAnswer(invocation -> {
                assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
                events.add("backfillBokjiroDetailSidecars");
                throw failure;
            }).when(backfillService).backfillBokjiroDetailSidecars(LIMIT);
        }
        var transactionManager = new RecordingTransactionManager();
        var transactionalBackfill = transactionalProxy(backfillService, transactionManager);
        // Proxy the application service as Spring would, so an accidental outer
        // @Transactional also breaks the acquire/release transaction assertions.
        var application = transactionalProxy(
                new CollectSidecarBackfillApplicationService(guard, transactionalBackfill), transactionManager);

        if (failDetail) {
            assertThatThrownBy(() -> application.backfillBokjiro("all", LIMIT)).isSameAs(failure);
        } else {
            assertThat(application.backfillBokjiro("all", LIMIT)).isEqualTo(new BackfillResult(4, 2, 0, 2));
        }

        assertThat(events).containsExactly("acquire", "begin", "backfillBokjiroListSidecars", "commit",
                "begin", "backfillBokjiroDetailSidecars", failDetail ? "rollback" : "commit", "release");
    }

    @Test
    @DisplayName("하위 commit 자체의 예외도 작업 실패로 전파하고 잠금을 해제한다")
    void releasesLockAfterCommitFailure() {
        requireTransaction = true;
        var transactionManager = new RecordingTransactionManager();
        transactionManager.failCommit = true;
        var application = transactionalProxy(new CollectSidecarBackfillApplicationService(
                guard, transactionalProxy(backfillService, transactionManager)), transactionManager);

        assertThatThrownBy(() -> application.backfillGov24("support", LIMIT, false))
                .isInstanceOf(TransactionSystemException.class).hasMessage("commit failed");

        assertThat(events).containsExactly("acquire", "begin", "backfillGov24SupportConditionSidecars", "commit", "release");
        verify(cleanupRepository).release(eq("collect-global"), any());
    }

    private BackfillResult run(CollectSidecarBackfillApplicationService service, BackfillCase scope) {
        return "bokjiro".equals(scope.provider())
                ? service.backfillBokjiro(scope.scope(), LIMIT)
                : service.backfillGov24(scope.scope(), LIMIT, scope.missingOnly());
    }

    @SuppressWarnings("unchecked")
    private <T> T transactionalProxy(T target, RecordingTransactionManager transactionManager) {
        ProxyFactory factory = new ProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor(transactionManager, new AnnotationTransactionAttributeSource()));
        return (T) factory.getProxy();
    }

    record BackfillCase(String provider, String scope, boolean missingOnly, List<String> calls) {}

    private class RecordingTransactionManager extends AbstractPlatformTransactionManager {
        private boolean failCommit;

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected boolean isExistingTransaction(Object transaction) {
            return TransactionSynchronizationManager.isActualTransactionActive();
        }

        @Override
        protected void doBegin(Object transaction, org.springframework.transaction.TransactionDefinition definition) {
            events.add("begin");
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            events.add("commit");
            if (failCommit) {
                throw new TransactionSystemException("commit failed");
            }
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            events.add("rollback");
        }
    }
}
