package com.example.welfare.collect.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CollectListChangePolicyTest {

    private final CollectListChangePolicy policy = new CollectListChangePolicy(
            30,
            0.02,
            100,
            0.05,
            0.15,
            3
    );

    @Test
    @DisplayName("첫 스냅샷은 기준선만 만들고 detail 강제 실행하지 않는다")
    void baselineDoesNotForceDetail() {
        CollectListChangePolicy.Decision decision = policy.decide(new CollectListDiffService.CollectListDiff(
                CollectSource.GOV24,
                1L,
                null,
                true,
                10_000,
                0,
                0,
                0,
                List.of(),
                List.of(),
                List.of()
        ));

        assertThat(decision.forceDetail()).isFalse();
        assertThat(decision.reason()).isEqualTo("BASELINE_ONLY");
    }

    @Test
    @DisplayName("신규 또는 변경 임계치를 넘으면 후보 sourceId를 우선순위 순서로 detail 강제 처리한다")
    void largeNewOrChangedDiffForcesDetailCandidates() {
        CollectListChangePolicy.Decision decision = policy.decide(new CollectListDiffService.CollectListDiff(
                CollectSource.BOKJIRO_CENTRAL,
                2L,
                1L,
                false,
                1_000,
                31,
                120,
                0,
                List.of("new-1", "new-2"),
                List.of("changed-1", "changed-2"),
                List.of()
        ));

        assertThat(decision.forceDetail()).isTrue();
        assertThat(decision.detailCandidateSourceIds()).containsExactly("new-1", "new-2", "changed-1");
        assertThat(decision.reason()).contains("FORCE_DETAIL");
    }

    @Test
    @DisplayName("대량 missing 은 detail 강제가 아니라 count-drop guard 로 막는다")
    void largeMissingDiffIsGuarded() {
        CollectListChangePolicy.Decision decision = policy.decide(new CollectListDiffService.CollectListDiff(
                CollectSource.GOV24,
                3L,
                2L,
                false,
                800,
                0,
                5,
                200,
                List.of(),
                List.of("changed-1"),
                List.of("missing-1")
        ));

        assertThat(decision.forceDetail()).isFalse();
        assertThat(decision.guardedCountDrop()).isTrue();
        assertThat(decision.reason()).contains("COUNT_DROP_GUARDED");
    }
}
