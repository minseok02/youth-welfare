package com.example.welfare.admin.dashboard.service;

import com.example.welfare.admin.dashboard.repository.AdminDashboardReadRows;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class AdminDashboardQueryPolicyTest {

    @Test
    @DisplayName("real user traffic gate는 empty/no-traffic/thin/click-thin/ready 순서로 판정한다")
    void resolveRealUserTrafficGate() {
        assertThat(AdminDashboardQueryPolicy.resolveRealUserTrafficGate(
                new AdminDashboardReadRows.RecommendationSummaryRow(0, 0, 0, 0, 0, null),
                trafficMix(0, 0, 0)
        )).isEqualTo("DEFERRED_EMPTY_COHORT");

        assertThat(AdminDashboardQueryPolicy.resolveRealUserTrafficGate(
                new AdminDashboardReadRows.RecommendationSummaryRow(10, 1, 5, 0, 0, null),
                trafficMix(0, 0, 0)
        )).isEqualTo("DEFERRED_NO_REAL_USER_TRAFFIC");

        assertThat(AdminDashboardQueryPolicy.resolveRealUserTrafficGate(
                new AdminDashboardReadRows.RecommendationSummaryRow(10, 1, 5, 0, 0, null),
                trafficMix(2, 2, 0)
        )).isEqualTo("DEFERRED_REAL_USER_SAMPLE_THIN");

        assertThat(AdminDashboardQueryPolicy.resolveRealUserTrafficGate(
                new AdminDashboardReadRows.RecommendationSummaryRow(10, 1, 5, 0, 0, null),
                trafficMix(6, 3, 2)
        )).isEqualTo("DEFERRED_REAL_USER_CLICK_SAMPLE_THIN");

        assertThat(AdminDashboardQueryPolicy.resolveRealUserTrafficGate(
                new AdminDashboardReadRows.RecommendationSummaryRow(10, 1, 5, 0, 0, null),
                trafficMix(9, 4, 3)
        )).isEqualTo("READY_REAL_USER_TRAFFIC");
    }

    @Test
    @DisplayName("top1 leader signal summary는 cohort mix를 기준으로 leader 출처를 요약한다")
    void resolveTop1LeaderSignalSummary() {
        assertThat(AdminDashboardQueryPolicy.resolveTop1LeaderSignalSummary(concentration(
                0, 0, 0, 0, 0, "DEFERRED_EMPTY_COHORT", "DEFERRED_EMPTY_COHORT"
        ))).isEqualTo("EMPTY_TOP1_LEADER");

        assertThat(AdminDashboardQueryPolicy.resolveTop1LeaderSignalSummary(concentration(
                12, 12, 0, 0, 0, "CONCENTRATED_TOP1", "READY_REAL_USER_COHORT"
        ))).isEqualTo("EXAMPLE_SMOKE_ONLY_LEADER");

        assertThat(AdminDashboardQueryPolicy.resolveTop1LeaderSignalSummary(concentration(
                12, 10, 2, 0, 0, "CONCENTRATED_TOP1", "READY_REAL_USER_COHORT"
        ))).isEqualTo("BOUNDED_LOCAL_WITH_EXAMPLE_LEADER");

        assertThat(AdminDashboardQueryPolicy.resolveTop1LeaderSignalSummary(concentration(
                12, 9, 2, 0, 2, "CONCENTRATED_TOP1", "DEFERRED_NO_REAL_USER_COHORT"
        ))).isEqualTo("LOCAL_SEED_WITHOUT_REAL_USER_LEADER");

        assertThat(AdminDashboardQueryPolicy.resolveTop1LeaderSignalSummary(concentration(
                12, 7, 0, 2, 2, "CONCENTRATED_TOP1", "DEFERRED_REAL_USER_SAMPLE_THIN"
        ))).isEqualTo("REAL_USER_SIGNAL_THIN_LEADER");

        assertThat(AdminDashboardQueryPolicy.resolveTop1LeaderSignalSummary(concentration(
                12, 5, 0, 4, 4, "CONCENTRATED_TOP1", "READY_REAL_USER_COHORT"
        ))).isEqualTo("MIXED_REAL_USER_LEADER");

        assertThat(AdminDashboardQueryPolicy.resolveTop1LeaderSignalSummary(concentration(
                12, 0, 0, 12, 12, "CONCENTRATED_TOP1", "READY_REAL_USER_COHORT"
        ))).isEqualTo("REAL_USER_ONLY_LEADER");
    }

    @Test
    @DisplayName("recommendation review gate는 traffic gate와 leader signal을 함께 반영한다")
    void resolveRecommendationReviewGate() {
        assertThat(AdminDashboardQueryPolicy.resolveRecommendationReviewGate(
                "DEFERRED_NO_REAL_USER_TRAFFIC",
                concentration(12, 9, 2, 0, 2, "CONCENTRATED_TOP1", "DEFERRED_NO_REAL_USER_COHORT")
        )).isEqualTo("DEFERRED_NO_REAL_USER_TRAFFIC");

        assertThat(AdminDashboardQueryPolicy.resolveRecommendationReviewGate(
                "READY_REAL_USER_TRAFFIC",
                concentration(12, 7, 0, 2, 2, "CONCENTRATED_TOP1", "DEFERRED_REAL_USER_SAMPLE_THIN")
        )).isEqualTo("DEFERRED_REAL_USER_SAMPLE_THIN");

        assertThat(AdminDashboardQueryPolicy.resolveRecommendationReviewGate(
                "READY_REAL_USER_TRAFFIC",
                concentration(12, 9, 2, 0, 2, "CONCENTRATED_TOP1", "READY_REAL_USER_COHORT")
        )).isEqualTo("DEFERRED_NON_REAL_LEADER_SIGNAL");

        assertThat(AdminDashboardQueryPolicy.resolveRecommendationReviewGate(
                "READY_REAL_USER_TRAFFIC",
                concentration(12, 7, 0, 2, 2, "CONCENTRATED_TOP1", "READY_REAL_USER_COHORT")
        )).isEqualTo("DEFERRED_REAL_USER_LEADER_SIGNAL_THIN");

        assertThat(AdminDashboardQueryPolicy.resolveRecommendationReviewGate(
                "READY_REAL_USER_TRAFFIC",
                concentration(12, 0, 0, 12, 12, "CONCENTRATED_TOP1", "READY_REAL_USER_COHORT")
        )).isEqualTo("READY_CONCENTRATED_TOP1_REVIEW");

        assertThat(AdminDashboardQueryPolicy.resolveRecommendationReviewGate(
                "READY_REAL_USER_TRAFFIC",
                concentration(12, 0, 0, 12, 12, "NO_PRIORITY_DOMINANT", "READY_REAL_USER_COHORT")
        )).isEqualTo("READY_NO_PRIORITY_DOMINANT_REVIEW");

        assertThat(AdminDashboardQueryPolicy.resolveRecommendationReviewGate(
                "READY_REAL_USER_TRAFFIC",
                concentration(12, 0, 0, 12, 12, "BALANCED_ENOUGH_FOR_LOGIC_REVIEW", "READY_REAL_USER_COHORT")
        )).isEqualTo("READY_BALANCED_LOGIC_REVIEW");
    }

    private static AdminDashboardReadRows.RecommendationTrafficMixRow trafficMix(
            long realUserLogs,
            long realUserUsers,
            long realUserClickedUsers
    ) {
        return new AdminDashboardReadRows.RecommendationTrafficMixRow(
                0, 0, 0, realUserLogs, realUserLogs,
                0, 0, 0, realUserUsers, realUserUsers,
                0, 0, 0, realUserClickedUsers, realUserClickedUsers
        );
    }

    private static AdminDashboardReadRows.RecommendationConcentrationRow concentration(
            long top1LeaderUsers,
            long top1LeaderExampleUsers,
            long top1LeaderBoundedLocalUsers,
            long top1LeaderRealUserUsers,
            long top1LeaderRealNonExampleUsers,
            String concentrationReadiness,
            String realUserCohortGate
    ) {
        return new AdminDashboardReadRows.RecommendationConcentrationRow(
                100,
                20,
                5,
                2622L,
                "청년월세 지원사업",
                "BOKJIRO_CENTRAL",
                "주거",
                top1LeaderUsers,
                new BigDecimal("59.69"),
                top1LeaderExampleUsers,
                top1LeaderBoundedLocalUsers,
                top1LeaderRealNonExampleUsers - top1LeaderRealUserUsers,
                top1LeaderRealUserUsers,
                top1LeaderRealNonExampleUsers,
                concentrationReadiness,
                realUserCohortGate,
                "LOCAL_REAL_NON_EXAMPLE_SEED_WITH_NON_REAL_BATCH"
        );
    }
}
