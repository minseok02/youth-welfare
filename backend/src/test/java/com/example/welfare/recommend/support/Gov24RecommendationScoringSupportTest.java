package com.example.welfare.recommend.support;

import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.recommend.dto.PriorityPreference;
import com.example.welfare.recommend.dto.RecommendationCandidateProjection;
import com.example.welfare.recommend.dto.RecommendationUserSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class Gov24RecommendationScoringSupportTest {

    @Test
    @DisplayName("Gov24 가구 userType 보너스는 가구형태 해당 없음에는 붙지 않는다")
    void householdUserTypeBonusDoesNotApplyToNotApplicableHouseholdType() {
        RecommendationUserSnapshot user = user("해당 없음");
        RecommendationCandidateProjection projection = RecommendationCandidateProjection.builder()
                .gov24UserTypeTokens(List.of("가구"))
                .build();

        double bonus = Gov24RecommendationScoringSupport.softBonus(gov24Service(), user, projection);

        assertThat(bonus).isZero();
    }

    @Test
    @DisplayName("Gov24 가구 userType 보너스는 실제 가구형태에는 유지된다")
    void householdUserTypeBonusAppliesToConcreteHouseholdType() {
        RecommendationUserSnapshot user = user("1인가구");
        RecommendationCandidateProjection projection = RecommendationCandidateProjection.builder()
                .gov24UserTypeTokens(List.of("가구"))
                .build();

        double bonus = Gov24RecommendationScoringSupport.softBonus(gov24Service(), user, projection);

        assertThat(bonus).isEqualTo(2.0);
    }

    private RecommendationUserSnapshot user(String householdType) {
        return new RecommendationUserSnapshot(
                1L,
                "user-key-1",
                25,
                "YOUTH_MID",
                "서울특별시",
                "강남구",
                "11680",
                (byte) 5,
                householdType,
                null,
                null,
                null,
                null,
                null,
                10,
                0.5,
                List.of(),
                List.of(),
                List.of(new PriorityPreference(1, "HOUSING", 2.0))
        );
    }

    private WelfareService gov24Service() {
        return WelfareService.builder()
                .sourceType(WelfareService.SourceType.GOV24)
                .sourceId("gov24-1")
                .title("정부24 서비스")
                .build();
    }
}
