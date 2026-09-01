package com.example.welfare.recommend.dto;

import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.recommend.entity.AiScoreStatus;
import com.example.welfare.recommend.entity.UserRecommendation;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class RecommendationResponseTest {

    @Test
    void fromSanitizesAiReasonForApiResponse() {
        WelfareService service = WelfareService.builder()
                .id(11L)
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId("Y1")
                .title("청년 정책")
                .unifiedCategory("주거")
                .status(WelfareService.ServiceStatus.ACTIVE)
                .build();
        UserRecommendation recommendation = UserRecommendation.builder()
                .id(1L)
                .userKey("user-key-1")
                .service(service)
                .recommendedAt(LocalDateTime.of(2026, 6, 6, 12, 0))
                .aiStatus(AiScoreStatus.SCORED)
                .aiReason("  지역\n청년\t주거 지원 조건과 지역 조건이 잘 맞아요  ")
                .build();

        RecommendationResponse response = RecommendationResponse.from(recommendation);

        assertThat(response.getAiReason()).isEqualTo("지역 청년 주거 지원 조건과 지역 조");
        assertThat(response.getReasonFactors())
                .extracting(RecommendationResponse.ReasonFactor::getLabel, RecommendationResponse.ReasonFactor::getValue)
                .contains(
                        org.assertj.core.groups.Tuple.tuple("분류", "주거"),
                        org.assertj.core.groups.Tuple.tuple("출처", "YOUTH")
                );
    }

    @Test
    void fromAddsRuleBasedFitAndPolicyConditionReasonFactorsFirst() {
        WelfareService service = WelfareService.builder()
                .id(22L)
                .sourceType(WelfareService.SourceType.GOV24)
                .sourceId("G1")
                .title("청년 주거 지원")
                .unifiedCategory("주거")
                .status(WelfareService.ServiceStatus.ACTIVE)
                .minAge(19)
                .maxAge(34)
                .maxIncome(5)
                .build();
        UserRecommendation recommendation = UserRecommendation.builder()
                .id(2L)
                .userKey("user-key-1")
                .service(service)
                .recommendedAt(LocalDateTime.of(2026, 6, 6, 12, 0))
                .aiStatus(AiScoreStatus.NOT_REQUESTED)
                .finalScore(new BigDecimal("0.72"))
                .build();

        RecommendationResponse response = RecommendationResponse.from(recommendation, null, null, "충청남도");

        assertThat(response.getReasonFactors())
                .extracting(RecommendationResponse.ReasonFactor::getLabel, RecommendationResponse.ReasonFactor::getValue)
                .startsWith(
                        org.assertj.core.groups.Tuple.tuple("평가", "규칙 기반"),
                        org.assertj.core.groups.Tuple.tuple("적합도", "높음"),
                        org.assertj.core.groups.Tuple.tuple("지역", "충청남도"),
                        org.assertj.core.groups.Tuple.tuple("연령", "19-34세"),
                        org.assertj.core.groups.Tuple.tuple("소득", "5분위 이하")
                )
                .contains(
                        org.assertj.core.groups.Tuple.tuple("분류", "주거"),
                        org.assertj.core.groups.Tuple.tuple("출처", "GOV24")
                );
    }

    @Test
    void fromDoesNotExposeZeroIncomeRangeAsRecommendationReasonFactor() {
        WelfareService service = WelfareService.builder()
                .id(23L)
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId("Y2")
                .title("청년 주거 정책")
                .unifiedCategory("주거")
                .status(WelfareService.ServiceStatus.ACTIVE)
                .minIncome(0)
                .maxIncome(0)
                .build();
        UserRecommendation recommendation = UserRecommendation.builder()
                .id(3L)
                .userKey("user-key-1")
                .service(service)
                .recommendedAt(LocalDateTime.of(2026, 6, 6, 12, 0))
                .aiStatus(AiScoreStatus.NOT_REQUESTED)
                .finalScore(new BigDecimal("0.72"))
                .build();

        RecommendationResponse response = RecommendationResponse.from(recommendation);

        assertThat(response.getReasonFactors())
                .extracting(RecommendationResponse.ReasonFactor::getLabel, RecommendationResponse.ReasonFactor::getValue)
                .doesNotContain(org.assertj.core.groups.Tuple.tuple("소득", "0-0분위"))
                .doesNotContain(org.assertj.core.groups.Tuple.tuple("소득", "0분위"));
    }
}
