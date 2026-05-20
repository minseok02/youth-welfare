package com.example.welfare.policy.dto;

import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.recommend.dto.RecommendationCandidateProjection;
import com.example.welfare.recommend.dto.RecommendationResponse;
import com.example.welfare.recommend.entity.UserRecommendation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CanonicalGov24PresentationMappingTest {

    @Test
    @DisplayName("recommendation response는 projection의 Gov24 canonical summary label을 우선 사용한다")
    void recommendationResponsePrefersProjectionGov24Labels() {
        WelfareService service = sampleService();
        UserRecommendation recommendation = UserRecommendation.builder()
                .id(1001L)
                .userKey("user-key-1")
                .service(service)
                .finalScore(new BigDecimal("0.9100"))
                .aiScore(new BigDecimal("0.7700"))
                .aiReason("추천 사유")
                .recommendedAt(LocalDateTime.of(2026, 5, 19, 20, 0))
                .build();

        RecommendationResponse response = RecommendationResponse.from(
                recommendation,
                9001L,
                sampleProjection(),
                "서울"
        );

        assertThat(response.getGov24ServiceFieldLabel()).isEqualTo("주거·자립");
        assertThat(response.getGov24UserTypeLabel()).isEqualTo("개인||가구");
        assertThat(response.getGov24BenefitTypeLabel()).isEqualTo("현금(감면)||서비스");
    }

    @Test
    @DisplayName("policy summary response는 projection의 Gov24 canonical summary label을 우선 사용한다")
    void policySummaryResponsePrefersProjectionGov24Labels() {
        PolicySummaryResponse response = PolicySummaryResponse.from(
                sampleService(),
                true,
                sampleProjection(),
                "서울"
        );

        assertThat(response.getGov24ServiceFieldLabel()).isEqualTo("주거·자립");
        assertThat(response.getGov24UserTypeLabel()).isEqualTo("개인||가구");
        assertThat(response.getGov24BenefitTypeLabel()).isEqualTo("현금(감면)||서비스");
    }

    @Test
    @DisplayName("policy detail response는 projection의 Gov24 canonical summary label을 우선 사용한다")
    void policyDetailResponsePrefersProjectionGov24Labels() {
        PolicyDetailResponse response = PolicyDetailResponse.of(
                sampleService(),
                null,
                List.of(),
                List.of(),
                true,
                sampleProjection()
        );

        assertThat(response.getGov24ServiceFieldLabel()).isEqualTo("주거·자립");
        assertThat(response.getGov24UserTypeLabel()).isEqualTo("개인||가구");
        assertThat(response.getGov24BenefitTypeLabel()).isEqualTo("현금(감면)||서비스");
    }

    @Test
    @DisplayName("policy ranking response는 projection의 Gov24 canonical summary label을 우선 사용한다")
    void policyRankingResponsePrefersProjectionGov24Labels() {
        PolicyRankingResponse response = PolicyRankingResponse.of(
                sampleService(),
                7L,
                0.9812,
                sampleProjection()
        );

        assertThat(response.getGov24ServiceFieldLabel()).isEqualTo("주거·자립");
        assertThat(response.getGov24UserTypeLabel()).isEqualTo("개인||가구");
        assertThat(response.getGov24BenefitTypeLabel()).isEqualTo("현금(감면)||서비스");
    }

    private WelfareService sampleService() {
        return WelfareService.builder()
                .id(11L)
                .sourceType(WelfareService.SourceType.GOV24)
                .sourceId("GOV24-11")
                .title("청년 월세 지원")
                .description("legacy description")
                .supportContent("legacy support content")
                .unifiedCategory("기타")
                .hostOrg("서울시")
                .operatingOrg("서울주거재단")
                .status(WelfareService.ServiceStatus.ACTIVE)
                .applyEndDate(LocalDate.of(2026, 12, 31))
                .isOnlineApply(true)
                .apiViewCount(123L)
                .viewCount(8)
                .build();
    }

    private RecommendationCandidateProjection sampleProjection() {
        return RecommendationCandidateProjection.builder()
                .serviceId(11L)
                .summary("projection summary")
                .unifiedCategoryCompat("주거")
                .youthMajorLabel("주거")
                .youthMidLabel("전월세 및 주거급여 지원")
                .provisionMethodLabel("온라인")
                .gov24ServiceFieldLabel("주거·자립")
                .gov24UserTypeLabel("개인||가구")
                .gov24BenefitTypeLabel("현금(감면)||서비스")
                .build();
    }
}
