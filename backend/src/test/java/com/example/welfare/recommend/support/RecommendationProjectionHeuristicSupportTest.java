package com.example.welfare.recommend.support;

import com.example.welfare.collect.support.NormalizationKeySupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RecommendationProjectionHeuristicSupportTest {

    @Test
    @DisplayName("detail beneficiary term만 beneficiary bucket으로 승격한다")
    void resolvesBeneficiaryDetailTerm() {
        assertThat(RecommendationProjectionHeuristicSupport.isBeneficiaryDetailTerm(
                "기초생활수급자",
                NormalizationKeySupport.SOURCE_FIELD_TARGET_DETAIL_SELECTION_CRITERIA
        )).isTrue();
        assertThat(RecommendationProjectionHeuristicSupport.isBeneficiaryDetailTerm(
                "기초생활수급자",
                NormalizationKeySupport.SOURCE_FIELD_BOKJIRO_TARGET_GROUP_ARRAY
        )).isFalse();
    }

    @Test
    @DisplayName("projection heuristic은 explicit youth signal과 focused life stage bonus를 계산한다")
    void computesAudienceRelevanceBonus() {
        double bonus = RecommendationProjectionHeuristicSupport.audienceRelevanceBonus(
                "청년 농어촌 정착 지원",
                "한부모 청년의 농촌 정착을 지원",
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of("청년"),
                19,
                34
        );

        assertThat(bonus).isEqualTo(23.0);
    }

    @Test
    @DisplayName("special target bucket은 중복 없이 누적된다")
    void collectsSpecialTargetBuckets() {
        Set<String> buckets = new LinkedHashSet<>();

        RecommendationProjectionHeuristicSupport.collectSpecialTargetBuckets(buckets, "농어촌 한부모 지원");
        RecommendationProjectionHeuristicSupport.collectSpecialTargetBuckets(buckets, "농촌 정착 청년");

        assertThat(buckets).containsExactlyInAnyOrder(
                RecommendationProjectionHeuristicSupport.SPECIAL_TARGET_RURAL,
                RecommendationProjectionHeuristicSupport.SPECIAL_TARGET_SINGLE_PARENT
        );
    }

    @Test
    @DisplayName("education priority boost eligibility는 compat code/youth major 조합으로 계산한다")
    void resolvesEducationPriorityBoostEligibility() {
        assertThat(RecommendationProjectionHeuristicSupport.educationPriorityBoostEligible("OTHER", "교육")).isTrue();
        assertThat(RecommendationProjectionHeuristicSupport.educationPriorityBoostEligible("HOUSING", "교육")).isFalse();
    }
}
