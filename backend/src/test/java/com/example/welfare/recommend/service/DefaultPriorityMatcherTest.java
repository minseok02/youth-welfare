package com.example.welfare.recommend.service;

import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.recommend.dto.PriorityPreference;
import com.example.welfare.recommend.dto.RecommendationCandidateProjection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultPriorityMatcherTest {

    private final DefaultPriorityMatcher matcher = new DefaultPriorityMatcher();

    @Test
    @DisplayName("projection compatPriorityBucket 이 있으면 legacy unifiedCategory 대신 사용한다")
    void matchesUsesProjectionCompatPriorityBucket() {
        WelfareService service = WelfareService.builder()
                .id(1L)
                .sourceType(WelfareService.SourceType.BOKJIRO_LOCAL)
                .sourceId("L1")
                .title("주거 지원")
                .unifiedCategory("기타")
                .status(WelfareService.ServiceStatus.ACTIVE)
                .build();

        RecommendationCandidateProjection projection = RecommendationCandidateProjection.builder()
                .serviceId(service.getId())
                .unifiedCategoryCompat("주거")
                .compatPriorityBucket("HOUSING")
                .build();

        assertThat(matcher.matches(priority("HOUSING"), service, projection)).isTrue();
        assertThat(matcher.matches(priority("JOB"), service, projection)).isFalse();
    }

    @Test
    @DisplayName("projection applyEndDate 가 있으면 deadline priority 는 projection 날짜를 우선 사용한다")
    void matchesUsesProjectionApplyEndDate() {
        WelfareService service = WelfareService.builder()
                .id(2L)
                .sourceType(WelfareService.SourceType.BOKJIRO_LOCAL)
                .sourceId("L2")
                .title("마감 지원")
                .status(WelfareService.ServiceStatus.ACTIVE)
                .applyEndDate(LocalDate.now().plusDays(30))
                .build();

        RecommendationCandidateProjection projection = RecommendationCandidateProjection.builder()
                .serviceId(service.getId())
                .applyEndDate(LocalDate.now().plusDays(3))
                .build();

        assertThat(matcher.matches(priority("DEADLINE"), service, projection)).isTrue();
        assertThat(matcher.matches(priority("DEADLINE"), service, null)).isFalse();
    }

    @Test
    @DisplayName("projection priority bucket 이 있으면 compat 문자열 없이도 priority 를 매칭한다")
    void matchesUsesProjectionPriorityBucketsBeforeCompatString() {
        WelfareService service = WelfareService.builder()
                .id(3L)
                .sourceType(WelfareService.SourceType.BOKJIRO_LOCAL)
                .sourceId("L3")
                .title("참여 지원")
                .unifiedCategory("기타")
                .status(WelfareService.ServiceStatus.ACTIVE)
                .build();

        RecommendationCandidateProjection projection = RecommendationCandidateProjection.builder()
                .serviceId(service.getId())
                .priorityBuckets(Set.of("PARTICIPATION"))
                .build();

        assertThat(matcher.matches(priority("PARTICIPATION"), service, projection)).isTrue();
        assertThat(matcher.matches(priority("FAMILY"), service, projection)).isFalse();
    }

    @Test
    @DisplayName("Gov24 read-model priority bucket은 serviceField와 같은 상위 분류에만 hard condition을 연다")
    void gov24ReadModelPriorityBucketOpensMatcherHardCondition() {
        WelfareService service = WelfareService.builder()
                .id(4L)
                .sourceType(WelfareService.SourceType.GOV24)
                .sourceId("G4")
                .title("주거 자립 현금 지원")
                .unifiedCategory("기타")
                .status(WelfareService.ServiceStatus.ACTIVE)
                .build();

        RecommendationCandidateProjection projection = RecommendationCandidateProjection.builder()
                .serviceId(service.getId())
                .gov24ServiceFieldLabel("주거·자립")
                .gov24UserTypeTokens(java.util.List.of("개인", "가구"))
                .gov24BenefitTypeTokens(java.util.List.of("현금"))
                .priorityBuckets(Set.of("HOUSING", "FINANCE"))
                .build();

        assertThat(matcher.matches(priority("HOUSING"), service, projection)).isTrue();
        assertThat(matcher.matches(priority("FINANCE"), service, projection)).isFalse();
    }

    @Test
    @DisplayName("Gov24 benefitType priority bucket은 serviceField 상위 분류와 충돌하면 hard condition을 열지 않는다")
    void gov24BenefitTypeBucketDoesNotOpenHardConditionWhenServiceFieldConflicts() {
        WelfareService service = WelfareService.builder()
                .id(5L)
                .sourceType(WelfareService.SourceType.GOV24)
                .sourceId("G5")
                .title("창업 교육 운영")
                .unifiedCategory("일자리")
                .status(WelfareService.ServiceStatus.ACTIVE)
                .build();

        RecommendationCandidateProjection projection = RecommendationCandidateProjection.builder()
                .serviceId(service.getId())
                .unifiedCategoryCompat("일자리")
                .compatPriorityBucket("JOB")
                .gov24ServiceFieldLabel("고용·창업")
                .gov24BenefitTypeTokens(java.util.List.of("기타(교육)"))
                .priorityBuckets(Set.of("JOB", "EDUCATION"))
                .build();

        assertThat(matcher.matches(priority("JOB"), service, projection)).isTrue();
        assertThat(matcher.matches(priority("EDUCATION"), service, projection)).isFalse();
    }

    private PriorityPreference priority(String code) {
        return new PriorityPreference(1, code, 1.4);
    }
}
