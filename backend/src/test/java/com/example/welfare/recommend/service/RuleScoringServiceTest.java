package com.example.welfare.recommend.service;

import com.example.welfare.policy.entity.ServiceTag;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.recommend.dto.PriorityPreference;
import com.example.welfare.recommend.dto.RecommendationCandidateProjection;
import com.example.welfare.recommend.dto.RecommendationUserSnapshot;
import com.example.welfare.recommend.dto.RetrievedRecommendationCandidates;
import com.example.welfare.recommend.dto.ScoredCandidate;
import com.example.welfare.recommend.repository.RecommendationCandidateReadRepository;
import com.example.welfare.recommend.support.RecommendationProjectionHeuristicSupport;
import com.example.welfare.recommend.support.RecommendationYouthRelevanceSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuleScoringServiceTest {

    @Mock
    private RecommendationCandidateReadRepository recommendationCandidateReadRepository;

    @Mock
    private PriorityMatcher priorityMatcher;

    private RuleScoringService ruleScoringService;

    @BeforeEach
    void setUp() {
        ruleScoringService = new RuleScoringService(
                recommendationCandidateReadRepository,
                priorityMatcher,
                new RecommendationYouthRelevanceSupport()
        );
        ReflectionTestUtils.setField(ruleScoringService, "educationCanonicalBonusEnabled", false);
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

        when(recommendationCandidateReadRepository.findTagsByServiceIds(anyList())).thenReturn(tagsByServiceId(
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

        when(recommendationCandidateReadRepository.findTagsByServiceIds(anyList())).thenReturn(tagsByServiceId(
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

        when(recommendationCandidateReadRepository.findTagsByServiceIds(anyList())).thenReturn(Map.of());

        List<ScoredCandidate> scored = ruleScoringService.score(
                new RetrievedRecommendationCandidates(
                        List.of(baseline, beneficiary),
                        Map.of(
                                beneficiary.getId(),
                                RecommendationCandidateProjection.builder()
                                        .serviceId(beneficiary.getId())
                                        .targetGroupBuckets(Set.of(
                                                RecommendationProjectionHeuristicSupport.BENEFICIARY_SUPPORT_BUCKET))
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

        when(recommendationCandidateReadRepository.findTagsByServiceIds(anyList())).thenReturn(Map.of());

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

        when(recommendationCandidateReadRepository.findTagsByServiceIds(anyList())).thenReturn(Map.of());

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
    @DisplayName("주거 표준코드가 맞는 후보는 housing profile bonus를 받는다")
    void housingProfileMatchAddsBonus() {
        RecommendationUserSnapshot user = snapshotWithStandardHousingCodes(
                List.of(),
                List.of(),
                (byte) 5,
                null,
                null,
                "3",
                null
        );

        WelfareService baseline = welfareService(80_1L, "기본 지원");
        WelfareService projected = welfareService(80_2L, "전월세 지원");

        when(recommendationCandidateReadRepository.findTagsByServiceIds(anyList())).thenReturn(Map.of());

        List<ScoredCandidate> scored = ruleScoringService.score(
                new RetrievedRecommendationCandidates(
                        List.of(baseline, projected),
                        Map.of(
                                projected.getId(),
                                RecommendationCandidateProjection.builder()
                                        .serviceId(projected.getId())
                                        .keywordTags(Set.of("월세보증금"))
                                        .build()
                        )
                ),
                user
        );

        assertThat(findByServiceId(scored, 80_2L).getRuleBaseScore())
                .isEqualTo(findByServiceId(scored, 80_1L).getRuleBaseScore() + 8.0);
    }

    @Test
    @DisplayName("projection 관심사 신호가 있는데 사용자 관심분야와 어긋나면 mismatch penalty를 준다")
    void projectionInterestSignalAppliesMismatchPenalty() {
        RecommendationUserSnapshot user = snapshot(List.of("주거"), List.of(), (byte) 5, null, null);

        WelfareService baseline = welfareService(81_1L, "기본 지원");
        WelfareService projected = welfareService(81_2L, "금융 지원");

        when(recommendationCandidateReadRepository.findTagsByServiceIds(anyList())).thenReturn(Map.of());

        List<ScoredCandidate> scored = ruleScoringService.score(
                new RetrievedRecommendationCandidates(
                        List.of(baseline, projected),
                        Map.of(
                                projected.getId(),
                                RecommendationCandidateProjection.builder()
                                        .serviceId(projected.getId())
                                        .interestThemes(Set.of("금융"))
                                        .build()
                        )
                ),
                user
        );

        assertThat(findByServiceId(scored, 81_2L).getRuleBaseScore())
                .isEqualTo(findByServiceId(scored, 81_1L).getRuleBaseScore() - 6.0);
    }

    @Test
    @DisplayName("category priority signal이 있는데 사용자 우선순위와 어긋나면 mismatch penalty를 준다")
    void categoryPrioritySignalAppliesMismatchPenalty() {
        RecommendationUserSnapshot user = snapshotWithPriorities(
                List.of(),
                List.of(),
                (byte) 5,
                null,
                null,
                List.of(new PriorityPreference(1, "HOUSING", 2.0))
        );

        WelfareService baseline = welfareService(81_3L, "기본 지원");
        WelfareService projected = welfareService(81_4L, "금융 지원");

        when(recommendationCandidateReadRepository.findTagsByServiceIds(anyList())).thenReturn(Map.of());

        List<ScoredCandidate> scored = ruleScoringService.score(
                new RetrievedRecommendationCandidates(
                        List.of(baseline, projected),
                        Map.of(
                                projected.getId(),
                                RecommendationCandidateProjection.builder()
                                        .serviceId(projected.getId())
                                        .priorityBuckets(Set.of("FINANCE"))
                                        .build()
                        )
                ),
                user
        );

        assertThat(findByServiceId(scored, 81_4L).getRuleBaseScore())
                .isEqualTo(findByServiceId(scored, 81_3L).getRuleBaseScore() - 10.0);
    }

    @Test
    @DisplayName("category priority match가 있으면 가장 높은 우선순위 rank를 후보에 기록한다")
    void storesBestMatchedPriorityRank() {
        RecommendationUserSnapshot user = snapshotWithPriorities(
                List.of(),
                List.of(),
                (byte) 5,
                null,
                null,
                List.of(
                        new PriorityPreference(1, "EDUCATION", 3.0),
                        new PriorityPreference(2, "JOB", 2.2)
                )
        );

        WelfareService projected = welfareService(81_5L, "일자리 지원");

        when(recommendationCandidateReadRepository.findTagsByServiceIds(anyList())).thenReturn(Map.of());
        when(priorityMatcher.matches(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(projected), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> "JOB".equals(((PriorityPreference) invocation.getArgument(0)).code()));

        List<ScoredCandidate> scored = ruleScoringService.score(
                new RetrievedRecommendationCandidates(
                        List.of(projected),
                        Map.of(
                                projected.getId(),
                                RecommendationCandidateProjection.builder()
                                        .serviceId(projected.getId())
                                        .priorityBuckets(Set.of("JOB"))
                                        .build()
                        )
                ),
                user
        );

        assertThat(findByServiceId(scored, 81_5L).getMatchedPriorityRank()).isEqualTo(2);
        assertThat(findByServiceId(scored, 81_5L).isHasPriorityMismatch()).isFalse();
    }

    @Test
    @DisplayName("projection audience relevance bonus는 legacy youth heuristic 없이도 rule base score에 반영된다")
    void projectionAudienceBonusAddsYouthRelevanceWithoutLegacySignals() {
        RecommendationUserSnapshot user = snapshot(List.of(), List.of(), (byte) 5, null, null);

        WelfareService baseline = welfareService(82L, "기본 지원");
        WelfareService projected = welfareService(83L, "일반 지원");

        when(recommendationCandidateReadRepository.findTagsByServiceIds(anyList())).thenReturn(Map.of());

        List<ScoredCandidate> scored = ruleScoringService.score(
                new RetrievedRecommendationCandidates(
                        List.of(baseline, projected),
                        Map.of(
                                projected.getId(),
                                RecommendationCandidateProjection.builder()
                                        .serviceId(projected.getId())
                                        .audienceRelevanceBonus(23.0)
                                        .build()
                        )
                ),
                user
        );

        assertThat(findByServiceId(scored, 83L).getRuleBaseScore())
                .isEqualTo(findByServiceId(scored, 82L).getRuleBaseScore() + 23.0);
    }

    @Test
    @DisplayName("projection special target bucket은 raw text 없이도 mismatch penalty를 계산한다")
    void projectionSpecialTargetBucketsDriveMismatchPenaltyWithoutLegacySignals() {
        RecommendationUserSnapshot user = snapshot(List.of(), List.of(), (byte) 5, null, null);

        WelfareService baseline = welfareService(84L, "일반 지원");
        WelfareService projected = welfareService(85L, "일반 지원");

        when(recommendationCandidateReadRepository.findTagsByServiceIds(anyList())).thenReturn(Map.of());

        List<ScoredCandidate> scored = ruleScoringService.score(
                new RetrievedRecommendationCandidates(
                        List.of(baseline, projected),
                        Map.of(
                                projected.getId(),
                                RecommendationCandidateProjection.builder()
                                        .serviceId(projected.getId())
                                        .specialTargetBuckets(Set.of("농어촌"))
                                        .build()
                        )
                ),
                user
        );

        assertThat(findByServiceId(scored, 85L).getRuleBaseScore())
                .isEqualTo(findByServiceId(scored, 84L).getRuleBaseScore() - 8.0);
        assertThat(findByServiceId(scored, 85L).isHasSpecialTargetMismatch()).isTrue();
    }

    @Test
    @DisplayName("장애인 target group special target은 일반 청년 사용자에게 mismatch penalty를 준다")
    void disabilityTargetGroupAppliesSpecialTargetMismatchForGeneralYouthUser() {
        RecommendationUserSnapshot user = snapshot(List.of("교육"), List.of(), (byte) 5, "1인 가구", "미취업");

        WelfareService baseline = welfareService(86L, "일반 청년 지원");
        WelfareService projected = welfareService(87L, "청년 발달장애인 자산형성 지원사업");

        when(recommendationCandidateReadRepository.findTagsByServiceIds(anyList())).thenReturn(Map.of());

        List<ScoredCandidate> scored = ruleScoringService.score(
                new RetrievedRecommendationCandidates(
                        List.of(baseline, projected),
                        Map.of(
                                projected.getId(),
                                RecommendationCandidateProjection.builder()
                                        .serviceId(projected.getId())
                                        .targetGroupsRaw(Set.of("장애인"))
                                        .specialTargetBuckets(Set.of(RecommendationProjectionHeuristicSupport.SPECIAL_TARGET_DISABILITY))
                                        .build()
                        )
                ),
                user
        );

        assertThat(findByServiceId(scored, 87L).getRuleBaseScore())
                .isEqualTo(findByServiceId(scored, 86L).getRuleBaseScore() - 8.0);
        assertThat(findByServiceId(scored, 87L).isHasSpecialTargetMismatch()).isTrue();
    }

    @Test
    @DisplayName("projection factKeys는 deadline helper 경계에 연결되어도 기존 applyEndDate bonus 의미를 유지한다")
    void projectionFactKeysKeepsLegacyDeadlineBonusMeaning() {
        RecommendationUserSnapshot user = snapshot(List.of(), List.of(), (byte) 5, null, null);

        WelfareService baseline = welfareService(90L, "마감 임박 지원", java.time.LocalDate.now().plusDays(3));
        WelfareService projected = welfareService(91L, "canonical 마감 지원", java.time.LocalDate.now().plusDays(3));

        when(recommendationCandidateReadRepository.findTagsByServiceIds(anyList())).thenReturn(Map.of());

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

    @Test
    @DisplayName("교육 canonical priority experiment flag가 켜지면 compat 기타 + youth major 교육 후보에만 priority bonus를 준다")
    void educationCanonicalPriorityExperimentAddsPriorityBonusForEducationMajor() {
        ReflectionTestUtils.setField(ruleScoringService, "educationCanonicalBonusEnabled", true);
        RecommendationUserSnapshot user = snapshotWithPriorities(
                List.of(),
                List.of(),
                (byte) 5,
                null,
                null,
                List.of(new PriorityPreference(1, "EDUCATION", 2.0))
        );

        WelfareService baseline = welfareService(100L, "일반 교육 지원", java.time.LocalDate.now().plusDays(3));
        WelfareService projected = welfareService(101L, "교육 실험 대상", java.time.LocalDate.now().plusDays(3));

        when(recommendationCandidateReadRepository.findTagsByServiceIds(anyList())).thenReturn(Map.of());
        when(priorityMatcher.matches(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(false);

        List<ScoredCandidate> scored = ruleScoringService.score(
                new RetrievedRecommendationCandidates(
                        List.of(baseline, projected),
                        Map.of(
                                projected.getId(),
                                RecommendationCandidateProjection.builder()
                                        .serviceId(projected.getId())
                                        .educationPriorityBoostEligible(true)
                                        .build()
                        )
                ),
                user
        );

        assertThat(findByServiceId(scored, 100L).getRuleWeightedScore()).isEqualTo(5.0);
        assertThat(findByServiceId(scored, 101L).getRuleWeightedScore()).isEqualTo(10.0);
    }

    @Test
    @DisplayName("교육 canonical priority experiment는 projection flag가 false면 raw compat/youth major 조합을 다시 보지 않는다")
    void educationCanonicalPriorityExperimentUsesProjectionFlagOnly() {
        ReflectionTestUtils.setField(ruleScoringService, "educationCanonicalBonusEnabled", true);
        RecommendationUserSnapshot user = snapshotWithPriorities(
                List.of(),
                List.of(),
                (byte) 5,
                null,
                null,
                List.of(new PriorityPreference(1, "EDUCATION", 2.0))
        );

        WelfareService service = welfareService(110L, "참여 실험 제외", java.time.LocalDate.now().plusDays(3));

        when(recommendationCandidateReadRepository.findTagsByServiceIds(anyList())).thenReturn(Map.of());
        when(priorityMatcher.matches(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(false);

        List<ScoredCandidate> scored = ruleScoringService.score(
                new RetrievedRecommendationCandidates(
                        List.of(service),
                        Map.of(
                                service.getId(),
                                RecommendationCandidateProjection.builder()
                                        .serviceId(service.getId())
                                        .educationPriorityBoostEligible(false)
                                        .unifiedCategoryCompat("기타")
                                        .youthMajorLabel("교육")
                                        .build()
                        )
                ),
                user
        );

        assertThat(findByServiceId(scored, 110L).getRuleWeightedScore()).isEqualTo(5.0);
    }

    @Test
    @DisplayName("Gov24 serviceField는 같은 priority code와 정렬될 때 bounded soft bonus를 준다")
    void gov24ServiceFieldAddsSoftBonusForMatchingPriority() {
        RecommendationUserSnapshot user = snapshotWithPriorities(
                List.of(),
                List.of(),
                (byte) 5,
                null,
                null,
                List.of(new PriorityPreference(1, "HOUSING", 2.0))
        );

        WelfareService baseline = welfareService(120L, "기본 Gov24 정책", WelfareService.SourceType.GOV24, null);
        WelfareService projected = welfareService(121L, "주거 자립 정책", WelfareService.SourceType.GOV24, null);

        when(recommendationCandidateReadRepository.findTagsByServiceIds(anyList())).thenReturn(Map.of());

        List<ScoredCandidate> scored = ruleScoringService.score(
                new RetrievedRecommendationCandidates(
                        List.of(baseline, projected),
                        Map.of(
                                projected.getId(),
                                RecommendationCandidateProjection.builder()
                                        .serviceId(projected.getId())
                                        .gov24ServiceFieldLabel("주거·자립")
                                        .build()
                        )
                ),
                user
        );

        assertThat(findByServiceId(scored, 121L).getRuleBaseScore())
                .isEqualTo(findByServiceId(scored, 120L).getRuleBaseScore() + 7.0);
    }

    @Test
    @DisplayName("Gov24 benefitType는 같은 priority code와 정렬될 때 bounded soft bonus를 준다")
    void gov24BenefitTypeAddsSoftBonusForMatchingPriority() {
        RecommendationUserSnapshot user = snapshotWithPriorities(
                List.of(),
                List.of(),
                (byte) 5,
                null,
                null,
                List.of(new PriorityPreference(1, "EDUCATION", 2.0))
        );

        WelfareService baseline = welfareService(122L, "기본 Gov24 정책", WelfareService.SourceType.GOV24, null);
        WelfareService projected = welfareService(123L, "장학 정책", WelfareService.SourceType.GOV24, null);

        when(recommendationCandidateReadRepository.findTagsByServiceIds(anyList())).thenReturn(Map.of());

        List<ScoredCandidate> scored = ruleScoringService.score(
                new RetrievedRecommendationCandidates(
                        List.of(baseline, projected),
                        Map.of(
                                projected.getId(),
                                RecommendationCandidateProjection.builder()
                                        .serviceId(projected.getId())
                                        .gov24BenefitTypeTokens(List.of("현금(장학금)"))
                                        .build()
                        )
                ),
                user
        );

        assertThat(findByServiceId(scored, 123L).getRuleBaseScore())
                .isEqualTo(findByServiceId(scored, 122L).getRuleBaseScore() + 5.0);
    }

    @Test
    @DisplayName("Gov24 soft bonus는 서비스분야, 지원유형, 사용자구분 신호를 합쳐도 bounded cap 안에서만 더한다")
    void gov24SoftBonusStaysWithinBoundedCap() {
        RecommendationUserSnapshot user = snapshotWithPriorities(
                List.of(),
                List.of(),
                (byte) 5,
                "1인 가구",
                null,
                List.of(new PriorityPreference(1, "EDUCATION", 2.0))
        );

        WelfareService baseline = welfareService(124L, "기본 Gov24 정책", WelfareService.SourceType.GOV24, null);
        WelfareService projected = welfareService(125L, "교육 지원 정책", WelfareService.SourceType.GOV24, null);

        when(recommendationCandidateReadRepository.findTagsByServiceIds(anyList())).thenReturn(Map.of());

        List<ScoredCandidate> scored = ruleScoringService.score(
                new RetrievedRecommendationCandidates(
                        List.of(baseline, projected),
                        Map.of(
                                projected.getId(),
                                RecommendationCandidateProjection.builder()
                                        .serviceId(projected.getId())
                                        .gov24ServiceFieldLabel("보육·교육")
                                        .gov24BenefitTypeTokens(List.of("현금(장학금)"))
                                        .gov24UserTypeTokens(List.of("개인", "가구"))
                                        .build()
                        )
                ),
                user
        );

        assertThat(findByServiceId(scored, 125L).getRuleBaseScore())
                .isEqualTo(findByServiceId(scored, 124L).getRuleBaseScore() + 10.0);
    }

    private ScoredCandidate findByServiceId(List<ScoredCandidate> scored, Long serviceId) {
        return scored.stream()
                .filter(candidate -> serviceId.equals(candidate.getService().getId()))
                .findFirst()
                .orElseThrow();
    }

    private Map<Long, List<ServiceTag>> tagsByServiceId(ServiceTag... tags) {
        return java.util.Arrays.stream(tags)
                .collect(java.util.stream.Collectors.groupingBy(tag -> tag.getService().getId()));
    }

    private WelfareService welfareService(Long id, String title) {
        return welfareService(id, title, null);
    }

    private WelfareService welfareService(Long id, String title, java.time.LocalDate applyEndDate) {
        return welfareService(id, title, WelfareService.SourceType.BOKJIRO_LOCAL, applyEndDate);
    }

    private WelfareService welfareService(Long id,
                                          String title,
                                          WelfareService.SourceType sourceType,
                                          java.time.LocalDate applyEndDate) {
        return WelfareService.builder()
                .id(id)
                .sourceType(sourceType)
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
        return snapshotWithPriorities(
                interestFields,
                targetTypes,
                incomeLevel,
                householdType,
                employmentStatus,
                List.of()
        );
    }

    private RecommendationUserSnapshot snapshotWithPriorities(List<String> interestFields,
                                                              List<String> targetTypes,
                                                              Byte incomeLevel,
                                                              String householdType,
                                                              String employmentStatus,
                                                              List<PriorityPreference> priorities) {
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
                priorities
        );
    }

    private RecommendationUserSnapshot snapshotWithStandardHousingCodes(List<String> interestFields,
                                                                        List<String> targetTypes,
                                                                        Byte incomeLevel,
                                                                        String householdType,
                                                                        String employmentStatus,
                                                                        String houseTenureCode,
                                                                        String housingTypeCode) {
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
                houseTenureCode,
                housingTypeCode,
                null,
                null,
                10,
                0.5,
                interestFields,
                targetTypes,
                List.of()
        );
    }
}
