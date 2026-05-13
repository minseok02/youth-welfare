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

    private RecommendationUserSnapshot snapshot(Byte incomeLevel,
                                                String householdType,
                                                String employmentStatus,
                                                List<String> targetTypes) {
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
                3,
                null,
                List.of(),
                targetTypes,
                List.of()
        );
    }
}
