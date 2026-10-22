package com.example.welfare.collect.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CollectBatchServiceTest {

    @Mock
    private CollectExecutionGuard collectExecutionGuard;
    @Mock
    private CollectSourceExecutionService collectSourceExecutionService;
    @Mock
    private CollectListDiffService collectListDiffService;
    @Mock
    private CollectListChangePolicy collectListChangePolicy;

    @InjectMocks
    private CollectBatchService collectBatchService;

    @Test
    @DisplayName("collectAllNow 는 고정된 source 순서대로 실행하고 중간 실패가 있어도 다음 source 를 계속 처리한다")
    void collectAllRunsSourcesInFixedOrder() {
        List<String> adapterCalls = new ArrayList<>();

        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(1);
            task.run();
            return null;
        }).when(collectExecutionGuard).runExclusive(eq("collect-all"), any(Runnable.class));

        doAnswer(invocation -> {
            CollectSource source = invocation.getArgument(0);
            adapterCalls.add(source.name());
            if (source == CollectSource.BOKJIRO_CENTRAL) {
                throw new IllegalStateException("https://apis.data.go.kr/path?serviceKey=secret-key");
            }
            return CollectResult.of(0, 0, 0, 0, 0);
        }).when(collectSourceExecutionService).collectSource(any());
        stubSuccessfulListDiffs();

        CollectBatchRunResult result = collectBatchService.collectAllNow();

        assertThat(adapterCalls).containsExactly("YOUTH", "BOKJIRO_CENTRAL", "BOKJIRO_LOCAL", "GOV24");
        assertThat(result.completedWithFailures()).isTrue();
        assertThat(result.succeededSourceCount()).isEqualTo(3);
        assertThat(result.failedSourceCount()).isEqualTo(1);
        assertThat(result.sourceResults())
                .extracting(sourceResult -> sourceResult.source().name(), CollectBatchRunResult.SourceRunResult::success)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("YOUTH", true),
                        org.assertj.core.groups.Tuple.tuple("BOKJIRO_CENTRAL", false),
                        org.assertj.core.groups.Tuple.tuple("BOKJIRO_LOCAL", true),
                        org.assertj.core.groups.Tuple.tuple("GOV24", true)
                );
        CollectBatchRunResult.SourceRunResult failed = result.sourceResults().get(1);
        assertThat(failed.errorCode()).isEqualTo("IllegalStateException");
        assertThat(failed.errorMessage())
                .contains("errorCode=IllegalStateException")
                .doesNotContain("serviceKey")
                .doesNotContain("secret-key");
        verify(collectExecutionGuard).runExclusive(eq("collect-all"), any(Runnable.class));
        assertThat(result.status()).isEqualTo(CollectBatchRunResult.Status.PARTIAL_SUCCESS);
        assertThat(result.failedListDiffCount()).isZero();
        assertThat(result.listDiffResults().get(1).status()).isEqualTo(CollectBatchRunResult.Status.SKIPPED);
        assertThat(result.listDiffResults().get(1).reason()).isEqualTo("SOURCE_FAILED");
    }

    @Test
    @DisplayName("list source 가 partial success 이면 불완전한 DB 상태를 diff snapshot 기준선으로 저장하지 않는다")
    void collectAllSkipsListDiffSnapshotAfterPartialListCollect() {
        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(1);
            task.run();
            return null;
        }).when(collectExecutionGuard).runExclusive(eq("collect-all"), any(Runnable.class));
        when(collectSourceExecutionService.collectSource(any()))
                .thenReturn(CollectResult.of(10, 9, 0, 0, 1));

        CollectBatchRunResult result = collectBatchService.collectAllNow();

        assertThat(result.completedWithFailures()).isTrue();
        assertThat(result.status()).isEqualTo(CollectBatchRunResult.Status.PARTIAL_SUCCESS);
        assertThat(result.succeededSourceCount()).isZero();
        assertThat(result.partialSourceCount()).isEqualTo(4);
        assertThat(result.failedSourceCount()).isZero();
        assertThat(result.failedListDiffCount()).isZero();
        assertThat(result.listDiffResults()).hasSize(4).allSatisfy(diff -> {
            assertThat(diff.status()).isEqualTo(CollectBatchRunResult.Status.SKIPPED);
            assertThat(diff.reason()).isEqualTo("SOURCE_PARTIAL_SUCCESS");
        });
        verify(collectListDiffService, never()).recordSnapshot(any(), any());
    }

    @Test
    @DisplayName("snapshot 오류는 최종 실패 표시에 반영하되 성공한 목록 저장 건수는 보존한다")
    void collectAllReportsSnapshotFailureWithoutLosingSuccessfulListCounts() {
        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(1);
            task.run();
            return null;
        }).when(collectExecutionGuard).runExclusive(eq("collect-all"), any(Runnable.class));
        when(collectSourceExecutionService.collectSource(any()))
                .thenReturn(CollectResult.of(10, 10, 0, 0, 0));
        when(collectListDiffService.recordSnapshot(any(), any()))
                .thenThrow(new IllegalStateException("snapshot unavailable"));

        CollectBatchRunResult result = collectBatchService.collectAllNow();

        assertThat(result.completedWithFailures()).isTrue();
        assertThat(result.requestedSourceCount()).isEqualTo(4);
        assertThat(result.succeededSourceCount()).isEqualTo(4);
        assertThat(result.failedSourceCount()).isZero();
        assertThat(result.status()).isEqualTo(CollectBatchRunResult.Status.PARTIAL_SUCCESS);
        assertThat(result.failedListDiffCount()).isEqualTo(4);
        assertThat(result.listDiffResults()).hasSize(4).allSatisfy(diff -> {
            assertThat(diff.status()).isEqualTo(CollectBatchRunResult.Status.FAILED);
            assertThat(diff.errorCode()).isEqualTo("IllegalStateException");
            assertThat(diff.errorMessage()).doesNotContain("snapshot unavailable");
        });
        assertThat(result.sourceResults())
                .allSatisfy(source -> assertThat(source.result().savedCount()).isEqualTo(10));
    }

    @Test
    @DisplayName("목록 비교 실패 뒤에도 나머지 source와 요일 rotation은 계속 실행하고 부분 실패를 보존한다")
    void listDiffFailureDoesNotStopRemainingSourcesOrRotation() {
        allowExclusiveRun();
        stubSuccessfulListDiffs();
        when(collectSourceExecutionService.collectSource(any())).thenReturn(CollectResult.of(10, 10, 0, 0, 0));
        doThrow(new IllegalStateException("snapshot unavailable"))
                .when(collectListDiffService).recordSnapshot(eq(CollectSource.BOKJIRO_CENTRAL), any());
        ReflectionTestUtils.setField(collectBatchService, "clock",
                Clock.fixed(Instant.parse("2026-06-23T17:00:00Z"), ZoneId.of(CollectBatchService.SCHEDULE_ZONE)));
        ReflectionTestUtils.setField(collectBatchService, "rotationEnabled", true);
        ReflectionTestUtils.setField(collectBatchService, "gov24SupportConditionsRotationMaxCalls", 50);
        when(collectSourceExecutionService.collectGov24SupportConditions(50)).thenReturn(CollectResult.of(50, 48, 0, 0, 2));

        CollectBatchRunResult result = collectBatchService.collectAllNow();

        assertThat(result.sourceResults()).extracting(CollectBatchRunResult.SourceRunResult::source)
                .containsExactly(CollectSource.YOUTH, CollectSource.BOKJIRO_CENTRAL, CollectSource.BOKJIRO_LOCAL,
                        CollectSource.GOV24, CollectSource.GOV24_SUPPORT_CONDITIONS);
        assertThat(result.completedWithFailures()).isTrue();
        assertThat(result.succeededSourceCount()).isEqualTo(4);
        assertThat(result.partialSourceCount()).isEqualTo(1);
        assertThat(result.failedSourceCount()).isZero();
        assertThat(result.failedListDiffCount()).isEqualTo(1);
        assertThat(result.listDiffResults().get(1).source()).isEqualTo(CollectSource.BOKJIRO_CENTRAL);
        assertThat(result.listDiffResults().get(1).status()).isEqualTo(CollectBatchRunResult.Status.FAILED);
        verify(collectSourceExecutionService).collectGov24SupportConditions(50);
    }

    @ParameterizedTest
    @CsvSource({"BASELINE_ONLY,false", "BELOW_THRESHOLD,false", "NO_DETAIL_CANDIDATES,false", "COUNT_DROP_GUARDED,true"})
    void intentionalNoDetailDecisionIsNotACollectionFailure(String reason, boolean guarded) {
        allowExclusiveRun();
        stubSuccessfulListDiffs();
        when(collectSourceExecutionService.collectSource(any())).thenReturn(CollectResult.of(10, 10, 0, 0, 0));
        when(collectListChangePolicy.decide(any()))
                .thenReturn(new CollectListChangePolicy.Decision(false, guarded, reason, List.of()));

        CollectBatchRunResult result = collectBatchService.collectAllNow();

        assertThat(result.status()).isEqualTo(CollectBatchRunResult.Status.SUCCESS);
        assertThat(result.completedWithFailures()).isFalse();
        assertThat(result.sourceResults()).hasSize(4);
        assertThat(result.listDiffResults()).hasSize(4).allSatisfy(diff -> {
            assertThat(diff.status()).isEqualTo(CollectBatchRunResult.Status.SUCCESS);
            assertThat(diff.reason()).isEqualTo(reason);
        });
        verify(collectSourceExecutionService, never()).collectGov24DetailsForSourceId(any());
        verify(collectSourceExecutionService, never()).collectGov24SupportConditionsForSourceId(any());
        verify(collectSourceExecutionService, never()).collectBokjiroDetailsRefreshForSourceId(any());
        verify(collectSourceExecutionService, never()).collectYouthDetailsForSourceId(any());
    }

    @Test
    void listDiffPolicyFailureIsReportedWithoutRetryingSnapshot() {
        allowExclusiveRun();
        stubSuccessfulListDiffs();
        when(collectSourceExecutionService.collectSource(any())).thenReturn(CollectResult.of(10, 10, 0, 0, 0));
        when(collectListChangePolicy.decide(any())).thenThrow(new IllegalArgumentException("invalid decision"));

        CollectBatchRunResult result = collectBatchService.collectAllNow();

        assertThat(result.completedWithFailures()).isTrue();
        assertThat(result.failedListDiffCount()).isEqualTo(4);
        for (CollectSource source : CollectSource.executionOrder()) {
            verify(collectListDiffService).recordSnapshot(eq(source), any());
        }
    }

    @Test
    @DisplayName("collectAllNow 는 list source 완료 후 forced detail 을 실행하고 그 뒤 rotation detail 을 실행한다")
    void collectAllRunsForcedDetailsAfterAllListSourcesAndBeforeRotation() {
        List<String> calls = new ArrayList<>();

        ReflectionTestUtils.setField(
                collectBatchService,
                "clock",
                Clock.fixed(Instant.parse("2026-06-22T17:00:00Z"), ZoneId.of(CollectBatchService.SCHEDULE_ZONE))
        );
        ReflectionTestUtils.setField(collectBatchService, "rotationEnabled", true);
        ReflectionTestUtils.setField(collectBatchService, "gov24DetailRotationMaxCalls", 50);

        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(1);
            task.run();
            return null;
        }).when(collectExecutionGuard).runExclusive(eq("collect-all"), any(Runnable.class));
        doAnswer(invocation -> {
            CollectSource source = invocation.getArgument(0);
            calls.add("list:" + source.name());
            return CollectResult.of(10, 10, 0, 0, 0);
        }).when(collectSourceExecutionService).collectSource(any());
        when(collectListDiffService.recordSnapshot(any(), any())).thenAnswer(invocation -> {
            CollectSource source = invocation.getArgument(0);
            return new CollectListDiffService.CollectListDiff(
                    source,
                    100L + source.ordinal(),
                    99L,
                    false,
                    100,
                    source == CollectSource.GOV24 ? 2 : 0,
                    0,
                    0,
                    source == CollectSource.GOV24 ? List.of("GOV-A", "GOV-B") : List.of(),
                    List.of(),
                    List.of()
            );
        });
        when(collectListChangePolicy.decide(any())).thenAnswer(invocation -> {
            CollectListDiffService.CollectListDiff diff = invocation.getArgument(0);
            if (diff.source() == CollectSource.GOV24) {
                return new CollectListChangePolicy.Decision(
                        true,
                        false,
                        "FORCE_DETAIL test",
                        List.of("GOV-A", "GOV-B")
                );
            }
            return new CollectListChangePolicy.Decision(false, false, "BELOW_THRESHOLD test", List.of());
        });
        doAnswer(invocation -> {
            calls.add("forced:gov24-detail:" + invocation.getArgument(0));
            return CollectResult.of(1, 1, 0, 0, 0);
        }).when(collectSourceExecutionService).collectGov24DetailsForSourceId(any());
        doAnswer(invocation -> {
            calls.add("forced:gov24-support:" + invocation.getArgument(0));
            return CollectResult.of(1, 1, 0, 0, 0);
        }).when(collectSourceExecutionService).collectGov24SupportConditionsForSourceId(any());
        doAnswer(invocation -> {
            calls.add("rotation:gov24-detail:" + invocation.getArgument(0));
            return CollectResult.of(50, 10, 40, 0, 0);
        }).when(collectSourceExecutionService).collectGov24Details(50);

        CollectBatchRunResult result = collectBatchService.collectAllNow();

        assertThat(calls).containsExactly(
                "list:YOUTH",
                "list:BOKJIRO_CENTRAL",
                "list:BOKJIRO_LOCAL",
                "list:GOV24",
                "forced:gov24-detail:GOV-A",
                "forced:gov24-detail:GOV-B",
                "forced:gov24-support:GOV-A",
                "forced:gov24-support:GOV-B",
                "rotation:gov24-detail:50"
        );
        assertThat(result.sourceResults())
                .extracting(sourceResult -> sourceResult.source().name())
                .containsExactly(
                        "YOUTH",
                        "BOKJIRO_CENTRAL",
                        "BOKJIRO_LOCAL",
                        "GOV24",
                        "GOV24_DETAIL",
                        "GOV24_SUPPORT_CONDITIONS",
                        "GOV24_DETAIL"
                );
        assertThat(result.status()).isEqualTo(CollectBatchRunResult.Status.SUCCESS);
        assertThat(result.completedWithFailures()).isFalse();
        assertThat(result.failedListDiffCount()).isZero();
    }

    @Test
    void forcedDetailPartialFailureKeepsCountsAndDoesNotSuppressSupportLane() {
        allowExclusiveRun();
        stubSuccessfulListDiffs();
        when(collectSourceExecutionService.collectSource(any())).thenReturn(CollectResult.of(10, 10, 0, 0, 0));
        when(collectListChangePolicy.decide(any())).thenAnswer(invocation -> {
            CollectListDiffService.CollectListDiff diff = invocation.getArgument(0);
            return diff.source() == CollectSource.GOV24
                    ? new CollectListChangePolicy.Decision(true, false, "FORCE_DETAIL test", List.of("GOV-A", "GOV-B"))
                    : new CollectListChangePolicy.Decision(false, false, "BASELINE_ONLY", List.of());
        });
        when(collectSourceExecutionService.collectGov24DetailsForSourceId("GOV-A"))
                .thenReturn(CollectResult.of(1, 1, 0, 0, 0));
        when(collectSourceExecutionService.collectGov24DetailsForSourceId("GOV-B"))
                .thenReturn(CollectResult.of(1, 0, 0, 0, 1));
        when(collectSourceExecutionService.collectGov24SupportConditionsForSourceId(any()))
                .thenReturn(CollectResult.of(1, 1, 0, 0, 0));

        CollectBatchRunResult result = collectBatchService.collectAllNow();

        assertThat(result.completedWithFailures()).isTrue();
        assertThat(result.status()).isEqualTo(CollectBatchRunResult.Status.PARTIAL_SUCCESS);
        assertThat(result.requestedSourceCount()).isEqualTo(6);
        assertThat(result.succeededSourceCount()).isEqualTo(5);
        assertThat(result.partialSourceCount()).isEqualTo(1);
        assertThat(result.failedSourceCount()).isZero();
        assertThat(result.failedListDiffCount()).isZero();
        assertThat(result.sourceResults().get(4).source()).isEqualTo(CollectSource.GOV24_DETAIL);
        assertThat(result.sourceResults().get(4).result()).isEqualTo(CollectResult.of(2, 1, 0, 0, 1));
        assertThat(result.sourceResults().get(5).source()).isEqualTo(CollectSource.GOV24_SUPPORT_CONDITIONS);
        assertThat(result.sourceResults().get(5).result()).isEqualTo(CollectResult.of(2, 2, 0, 0, 0));
        verify(collectSourceExecutionService).collectGov24SupportConditionsForSourceId("GOV-A");
        verify(collectSourceExecutionService).collectGov24SupportConditionsForSourceId("GOV-B");
    }

    private void allowExclusiveRun() {
        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(1);
            task.run();
            return null;
        }).when(collectExecutionGuard).runExclusive(eq("collect-all"), any(Runnable.class));
    }

    private void stubSuccessfulListDiffs() {
        when(collectListDiffService.recordSnapshot(any(), any())).thenAnswer(invocation -> {
            CollectSource source = invocation.getArgument(0);
            return new CollectListDiffService.CollectListDiff(source, 100L + source.ordinal(), null,
                    true, 10, 0, 0, 0, List.of(), List.of(), List.of());
        });
        when(collectListChangePolicy.decide(any()))
                .thenReturn(new CollectListChangePolicy.Decision(false, false, "BASELINE_ONLY", List.of()));
    }
}
