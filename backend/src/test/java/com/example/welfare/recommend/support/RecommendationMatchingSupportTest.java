package com.example.welfare.recommend.support;

import com.example.welfare.policy.entity.ServiceTag;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.recommend.dto.RecommendationCandidateProjection;
import com.example.welfare.recommend.dto.RecommendationUserSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RecommendationMatchingSupportTest {

    @Test
    @DisplayName("beneficiary bucket은 소득분위 기준으로 projection term을 매칭한다")
    void matchesBeneficiaryBucket() {
        RecommendationUserSnapshot user = snapshot((byte) 3, null, null, List.of());
        RecommendationCandidateProjection projection = RecommendationCandidateProjection.builder()
                .targetGroupBuckets(Set.of(RecommendationProjectionHeuristicSupport.BENEFICIARY_SUPPORT_BUCKET))
                .beneficiaryTerms(Set.of("차상위계층"))
                .build();

        assertThat(RecommendationMatchingSupport.beneficiaryBucketMatches(user, projection)).isTrue();
    }

    @Test
    @DisplayName("beneficiary bucket은 기초생활수급권자 표준 코드가 있으면 소득분위 없이도 매칭한다")
    void matchesBeneficiaryBucketFromStandardRecipientCode() {
        RecommendationUserSnapshot user = snapshot((byte) 5, null, null, List.of(), "1", null);
        RecommendationCandidateProjection projection = RecommendationCandidateProjection.builder()
                .targetGroupBuckets(Set.of(RecommendationProjectionHeuristicSupport.BENEFICIARY_SUPPORT_BUCKET))
                .beneficiaryTerms(Set.of("기초생활수급자"))
                .build();

        assertThat(RecommendationMatchingSupport.beneficiaryBucketMatches(user, projection)).isTrue();
    }

    @Test
    @DisplayName("기초생활수급권자 '해당하지 않음'(NONE)은 보유로 보지 않아 소득분위 없이는 매칭하지 않는다")
    void doesNotMatchBeneficiaryBucketWhenRecipientCodeIsNotApplicable() {
        RecommendationUserSnapshot user = snapshot((byte) 5, null, null, List.of(), "NONE", null);
        RecommendationCandidateProjection projection = RecommendationCandidateProjection.builder()
                .targetGroupBuckets(Set.of(RecommendationProjectionHeuristicSupport.BENEFICIARY_SUPPORT_BUCKET))
                .beneficiaryTerms(Set.of("기초생활수급자"))
                .build();

        assertThat(RecommendationMatchingSupport.beneficiaryBucketMatches(user, projection)).isFalse();
    }

    @Test
    @DisplayName("장애등급 '해당하지 않음'(NONE)은 보유로 보지 않아 장애 bucket과 매칭하지 않는다")
    void doesNotMatchSpecialTargetWhenDisabilityCodeIsNotApplicable() {
        RecommendationUserSnapshot user = snapshot((byte) 5, null, null, List.of(), null, "NONE");
        RecommendationCandidateProjection projection = RecommendationCandidateProjection.builder()
                .specialTargetBuckets(Set.of(RecommendationProjectionHeuristicSupport.SPECIAL_TARGET_DISABILITY))
                .build();

        assertThat(RecommendationMatchingSupport.specialTargetMatches(
                user,
                Set.of(),
                WelfareService.builder().title("일반 지원").build(),
                List.of(),
                projection
        )).isFalse();
    }

    @Test
    @DisplayName("special target signal은 projection bucket이 있으면 raw text 없이도 true가 된다")
    void detectsSpecialTargetSignalFromProjection() {
        RecommendationCandidateProjection projection = RecommendationCandidateProjection.builder()
                .specialTargetBuckets(Set.of(RecommendationProjectionHeuristicSupport.SPECIAL_TARGET_RURAL))
                .build();

        assertThat(RecommendationMatchingSupport.hasSpecialTargetSignal(
                WelfareService.builder().title("일반 지원").build(),
                List.of(),
                projection
        )).isTrue();
    }

    @Test
    @DisplayName("target group fallback은 employment/household raw 값을 매칭한다")
    void matchesTargetGroupFallbackValues() {
        RecommendationUserSnapshot user = snapshot((byte) 5, "1인 가구", "미취업", List.of());

        assertThat(RecommendationMatchingSupport.targetGroupMatches(
                user,
                List.of("미취업청년", "1인가구"),
                null
        )).isTrue();
    }

    @Test
    @DisplayName("target group fallback은 영어 household 코드도 한글 대상 그룹과 매칭한다")
    void matchesTargetGroupFallbackForEnglishHouseholdCodes() {
        RecommendationUserSnapshot user = snapshot((byte) 5, "ONE_PERSON", "EMPLOYED", List.of());

        assertThat(RecommendationMatchingSupport.targetGroupMatches(
                user,
                List.of("1인가구"),
                null
        )).isTrue();
    }

    @Test
    @DisplayName("special target match는 projection bucket 기준으로 유저 target type을 매칭한다")
    void matchesSpecialTargetProjectionBuckets() {
        RecommendationUserSnapshot user = snapshot((byte) 5, null, null, List.of(RecommendationProjectionHeuristicSupport.SPECIAL_TARGET_RURAL));
        RecommendationCandidateProjection projection = RecommendationCandidateProjection.builder()
                .specialTargetBuckets(Set.of(RecommendationProjectionHeuristicSupport.SPECIAL_TARGET_RURAL))
                .build();

        assertThat(RecommendationMatchingSupport.specialTargetMatches(
                user,
                Set.of(RecommendationProjectionHeuristicSupport.SPECIAL_TARGET_RURAL),
                WelfareService.builder().title("일반 지원").build(),
                List.of(),
                projection
        )).isTrue();
    }

    @Test
    @DisplayName("special target match는 장애등급 표준 코드가 있으면 장애 bucket과 매칭한다")
    void matchesSpecialTargetFromDisabilityCode() {
        RecommendationUserSnapshot user = snapshot((byte) 5, null, null, List.of(), null, "041");
        RecommendationCandidateProjection projection = RecommendationCandidateProjection.builder()
                .specialTargetBuckets(Set.of(RecommendationProjectionHeuristicSupport.SPECIAL_TARGET_DISABILITY))
                .build();

        assertThat(RecommendationMatchingSupport.specialTargetMatches(
                user,
                Set.of(),
                WelfareService.builder().title("일반 지원").build(),
                List.of(),
                projection
        )).isTrue();
    }

    @Test
    @DisplayName("주거 프로필은 월세 표준 코드와 월세보증금 키워드를 매칭한다")
    void matchesHousingProfileFromHouseTenureCode() {
        RecommendationUserSnapshot user = snapshot((byte) 5, null, null, List.of(), null, null, "3", null);
        RecommendationCandidateProjection projection = RecommendationCandidateProjection.builder()
                .keywordTags(Set.of("월세보증금"))
                .build();

        assertThat(RecommendationMatchingSupport.housingProfileMatches(
                user,
                WelfareService.builder().title("청년 전월세 지원").build(),
                List.of(),
                projection
        )).isTrue();
    }

    @Test
    @DisplayName("주거 프로필은 기숙사 주택유형 코드와 기숙사 신호를 매칭한다")
    void matchesHousingProfileFromHousingTypeCode() {
        RecommendationUserSnapshot user = snapshot((byte) 5, null, null, List.of(), null, null, null, "7");

        assertThat(RecommendationMatchingSupport.housingProfileMatches(
                user,
                WelfareService.builder().title("기숙사 입주 지원").build(),
                List.of(),
                null
        )).isTrue();
    }

    @Test
    @DisplayName("주거 프로필 '해당하지 않음'(NONE)은 주거 신호와 매칭하지 않는다")
    void doesNotMatchHousingProfileWhenHousingCodesAreNotApplicable() {
        RecommendationUserSnapshot user = snapshot((byte) 5, null, null, List.of(), null, null, "NONE", "NONE");

        assertThat(RecommendationMatchingSupport.housingProfileMatches(
                user,
                WelfareService.builder().title("청년 월세 주거 지원").build(),
                List.of(),
                null
        )).isFalse();
    }

    @Test
    @DisplayName("주거형태가 해당하지 않음이면 기존 불일치 주택유형 값도 주거 신호로 쓰지 않는다")
    void doesNotMatchHousingProfileWhenHouseTenureIsNotApplicableEvenWithHousingType() {
        RecommendationUserSnapshot user = snapshot((byte) 5, null, null, List.of(), null, null, "NONE", "4");

        assertThat(RecommendationMatchingSupport.housingProfileMatches(
                user,
                WelfareService.builder().title("청년 아파트 주거 지원").build(),
                List.of(),
                null
        )).isFalse();
    }

    private RecommendationUserSnapshot snapshot(Byte incomeLevel,
                                                String householdType,
                                                String employmentStatus,
                                                List<String> targetTypes) {
        return snapshot(incomeLevel, householdType, employmentStatus, targetTypes, null, null);
    }

    private RecommendationUserSnapshot snapshot(Byte incomeLevel,
                                                String householdType,
                                                String employmentStatus,
                                                List<String> targetTypes,
                                                String basicLivingRecipientTypeCode,
                                                String disabilityGradeCode) {
        return snapshot(
                incomeLevel,
                householdType,
                employmentStatus,
                targetTypes,
                basicLivingRecipientTypeCode,
                disabilityGradeCode,
                null,
                null
        );
    }

    private RecommendationUserSnapshot snapshot(Byte incomeLevel,
                                                String householdType,
                                                String employmentStatus,
                                                List<String> targetTypes,
                                                String basicLivingRecipientTypeCode,
                                                String disabilityGradeCode,
                                                String houseTenureCode,
                                                String housingTypeCode) {
        return new RecommendationUserSnapshot(
                1L,
                "user-key",
                25,
                "20대",
                null,
                null,
                null,
                incomeLevel,
                householdType,
                employmentStatus,
                houseTenureCode,
                housingTypeCode,
                basicLivingRecipientTypeCode,
                disabilityGradeCode,
                3,
                null,
                List.of(),
                targetTypes,
                List.of()
        );
    }
}
