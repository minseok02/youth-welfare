package com.example.welfare.recommend.service;

import com.example.welfare.policy.entity.ServiceTag;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.ServiceTagRepository;
import com.example.welfare.recommend.dto.RecommendationCandidateProjection;
import com.example.welfare.recommend.dto.RecommendationUserSnapshot;
import com.example.welfare.recommend.dto.RetrievedRecommendationCandidates;
import com.example.welfare.recommend.dto.ScoredCandidate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuleScoringServiceTest {

    @Mock
    private ServiceTagRepository serviceTagRepository;

    @Mock
    private PriorityMatcher priorityMatcher;

    private static final String BENEFICIARY_SUPPORT_BUCKET = "BENEFICIARY_SUPPORT";

    private RuleScoringService ruleScoringService;

    @BeforeEach
    void setUp() {
        ruleScoringService = new RuleScoringService(
                serviceTagRepository,
                priorityMatcher,
                new YouthPolicyFilter()
        );
    }

    @Test
    @DisplayName("명시적 청년 정책이 나이만 겹치는 정책보다 높은 rule 점수를 받는다")
    void explicitYouthPolicyGetsHigherRuleScoreThanAgeOnlyPolicy() {
        RecommendationUserSnapshot user = snapshot(List.of(), List.of(), (byte) 5, null, null);

        WelfareService explicitYouth = WelfareService.builder()
                .id(10L)
                .sourceType(WelfareService.SourceType.BOKJIRO_CENTRAL)
                .sourceId("C1")
                .title("청년내일저축계좌")
                .status(WelfareService.ServiceStatus.ACTIVE)
                .build();

        WelfareService ageOnly = WelfareService.builder()
                .id(20L)
                .sourceType(WelfareService.SourceType.BOKJIRO_CENTRAL)
                .sourceId("C2")
                .title("사회진입 교통비 지원")
                .status(WelfareService.ServiceStatus.ACTIVE)
                .minAge(19)
                .maxAge(34)
                .build();

        when(serviceTagRepository.findByServiceIdIn(anyList())).thenReturn(List.of(
                ServiceTag.builder()
                        .service(explicitYouth)
                        .tagType(ServiceTag.TagType.TARGET_GROUP)
                        .tagValue("청년")
                        .build()
        ));

        List<ScoredCandidate> scored = ruleScoringService.score(List.of(explicitYouth, ageOnly), user);

        assertThat(scored).hasSize(2);
        assertThat(findByServiceId(scored, 10L).getRuleBaseScore())
                .isGreaterThan(findByServiceId(scored, 20L).getRuleBaseScore());
    }

    @Test
    @DisplayName("특수 대상이 맞지 않는 청년 정책은 일반 청년 정책보다 낮은 rule 점수를 받는다")
    void mismatchedSpecialAudiencePolicyGetsLowerScore() {
        RecommendationUserSnapshot user = snapshot(List.of("취업"), List.of(), (byte) 6, null, null);

        WelfareService openYouth = WelfareService.builder()
                .id(30L)
                .sourceType(WelfareService.SourceType.BOKJIRO_CENTRAL)
                .sourceId("C3")
                .title("청년 취업 지원")
                .status(WelfareService.ServiceStatus.ACTIVE)
                .build();

        WelfareService specialYouth = WelfareService.builder()
                .id(40L)
                .sourceType(WelfareService.SourceType.BOKJIRO_CENTRAL)
                .sourceId("C4")
                .title("농촌출신대학생학자금융자")
                .status(WelfareService.ServiceStatus.ACTIVE)
                .build();

        when(serviceTagRepository.findByServiceIdIn(anyList())).thenReturn(List.of(
                ServiceTag.builder()
                        .service(openYouth)
                        .tagType(ServiceTag.TagType.KEYWORD)
                        .tagValue("취업")
                        .build()
        ));

        List<ScoredCandidate> scored = ruleScoringService.score(List.of(openYouth, specialYouth), user);

        assertThat(findByServiceId(scored, 30L).getRuleBaseScore())
                .isGreaterThan(findByServiceId(scored, 40L).getRuleBaseScore());
    }

    @Test
    @DisplayName("beneficiary projection bucket은 중복 없이 한 번만 target group bonus를 준다")
    void beneficiaryProjectionBucketAddsSingleTargetGroupBonus() {
        RecommendationUserSnapshot user = snapshot(List.of(), List.of(), (byte) 3, null, null);

        WelfareService baseline = WelfareService.builder()
                .id(50L)
                .sourceType(WelfareService.SourceType.BOKJIRO_LOCAL)
                .sourceId("L50")
                .title("일반 생활 지원")
                .status(WelfareService.ServiceStatus.ACTIVE)
                .build();

        WelfareService beneficiary = WelfareService.builder()
                .id(60L)
                .sourceType(WelfareService.SourceType.BOKJIRO_LOCAL)
                .sourceId("L60")
                .title("생활 안정 지원")
                .status(WelfareService.ServiceStatus.ACTIVE)
                .build();

        when(serviceTagRepository.findByServiceIdIn(anyList())).thenReturn(List.of());

        List<ScoredCandidate> scored = ruleScoringService.score(
                new RetrievedRecommendationCandidates(
                        List.of(baseline, beneficiary),
                        Map.of(
                                beneficiary.getId(),
                                RecommendationCandidateProjection.builder()
                                        .serviceId(beneficiary.getId())
                                        .targetGroupBuckets(Set.of(
                                                BENEFICIARY_SUPPORT_BUCKET))
                                        .beneficiaryTerms(Set.of("기초생활수급자", "차상위계층"))
                                        .build()
                        )
                ),
                user
        );

        assertThat(findByServiceId(scored, 60L).getRuleBaseScore())
                .isEqualTo(findByServiceId(scored, 50L).getRuleBaseScore() + 10.0);
    }

    @Test
    @DisplayName("projection interest theme은 legacy INTEREST_THEME 태그 없이도 관심분야 bonus를 준다")
    void projectionInterestThemeAddsInterestBonusWithoutLegacyTags() {
        RecommendationUserSnapshot user = snapshot(List.of("주거"), List.of(), (byte) 5, null, null);

        WelfareService baseline = welfareService(70L, "일반 지원");
        WelfareService projected = welfareService(71L, "주거 생활 지원");

        when(serviceTagRepository.findByServiceIdIn(anyList())).thenReturn(List.of());

        List<ScoredCandidate> scored = ruleScoringService.score(
                new RetrievedRecommendationCandidates(
                        List.of(baseline, projected),
                        Map.of(
                                projected.getId(),
                                RecommendationCandidateProjection.builder()
                                        .serviceId(projected.getId())
                                        .interestThemes(Set.of("주거"))
                                        .build()
                        )
                ),
                user
        );

        assertThat(findByServiceId(scored, 71L).getRuleBaseScore())
                .isEqualTo(findByServiceId(scored, 70L).getRuleBaseScore() + 15.0);
    }

    @Test
    @DisplayName("projection targetGroupsRaw는 legacy TARGET_GROUP 태그 없이도 broad target group bonus를 준다")
    void projectionTargetGroupsRawAddsTargetGroupBonusWithoutLegacyTags() {
        RecommendationUserSnapshot user = snapshot(List.of(), List.of(), (byte) 5, "1인 가구", "미취업");

        WelfareService baseline = welfareService(80L, "기본 지원");
        WelfareService projected = welfareService(81L, "생활 지원");

        when(serviceTagRepository.findByServiceIdIn(anyList())).thenReturn(List.of());

        List<ScoredCandidate> scored = ruleScoringService.score(
                new RetrievedRecommendationCandidates(
                        List.of(baseline, projected),
                        Map.of(
                                projected.getId(),
                                RecommendationCandidateProjection.builder()
                                        .serviceId(projected.getId())
                                        .targetGroupsRaw(Set.of("미취업청년", "1인가구"))
                                        .build()
                        )
                ),
                user
        );

        assertThat(findByServiceId(scored, 81L).getRuleBaseScore())
                .isEqualTo(findByServiceId(scored, 80L).getRuleBaseScore() + 10.0);
    }

    @Test
    @DisplayName("projection factKeys는 deadline helper 경계에 연결되어도 기존 applyEndDate bonus 의미를 유지한다")
    void projectionFactKeysKeepsLegacyDeadlineBonusMeaning() {
        RecommendationUserSnapshot user = snapshot(List.of(), List.of(), (byte) 5, null, null);

        WelfareService baseline = welfareService(90L, "마감 임박 지원", java.time.LocalDate.now().plusDays(3));
        WelfareService projected = welfareService(91L, "canonical 마감 지원", java.time.LocalDate.now().plusDays(3));

        when(serviceTagRepository.findByServiceIdIn(anyList())).thenReturn(List.of());

        List<ScoredCandidate> scored = ruleScoringService.score(
                new RetrievedRecommendationCandidates(
                        List.of(baseline, projected),
                        Map.of(
                                projected.getId(),
                                RecommendationCandidateProjection.builder()
                                        .serviceId(projected.getId())
                                        .factKeys(Set.of("BK_APPLY_END_DATE"))
                                        .build()
                        )
                ),
                user
        );

        assertThat(findByServiceId(scored, 90L).getRuleBaseScore()).isEqualTo(5.0);
        assertThat(findByServiceId(scored, 91L).getRuleBaseScore()).isEqualTo(5.0);
    }

    private ScoredCandidate findByServiceId(List<ScoredCandidate> scored, Long serviceId) {
        return scored.stream()
                .filter(candidate -> serviceId.equals(candidate.getService().getId()))
                .findFirst()
                .orElseThrow();
    }

    private WelfareService welfareService(Long id, String title) {
        return welfareService(id, title, null);
    }

    private WelfareService welfareService(Long id, String title, java.time.LocalDate applyEndDate) {
        return WelfareService.builder()
                .id(id)
                .sourceType(WelfareService.SourceType.BOKJIRO_LOCAL)
                .sourceId("S" + id)
                .title(title)
                .status(WelfareService.ServiceStatus.ACTIVE)
                .applyEndDate(applyEndDate)
                .build();
    }

    private RecommendationUserSnapshot snapshot(List<String> interestFields,
                                                List<String> targetTypes,
                                                Byte incomeLevel,
                                                String householdType,
                                                String employmentStatus) {
        return new RecommendationUserSnapshot(
                1L,
                "user-key-1",
                25,
                "25_29",
                "서울특별시",
                "강남구",
                "11680",
                incomeLevel,
                householdType,
                employmentStatus,
                10,
                0.5,
                interestFields,
                targetTypes,
                List.of()
        );
    }
}
