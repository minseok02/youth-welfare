package com.example.welfare.recommend.service;

import com.example.welfare.policy.entity.ServiceTag;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.ServiceTagRepository;
import com.example.welfare.recommend.dto.ScoredCandidate;
import com.example.welfare.user.entity.User;
import com.example.welfare.user.entity.UserAttribute;
import com.example.welfare.user.repository.UserAttributeRepository;
import com.example.welfare.user.repository.UserPriorityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuleScoringServiceTest {

    @Mock
    private UserAttributeRepository userAttributeRepository;

    @Mock
    private UserPriorityRepository userPriorityRepository;

    @Mock
    private ServiceTagRepository serviceTagRepository;

    @Mock
    private PriorityMatcher priorityMatcher;

    private RuleScoringService ruleScoringService;

    @BeforeEach
    void setUp() {
        ruleScoringService = new RuleScoringService(
                userAttributeRepository,
                userPriorityRepository,
                serviceTagRepository,
                priorityMatcher,
                new YouthPolicyFilter()
        );
        when(userAttributeRepository.findByUserId(1L)).thenReturn(List.of());
        when(userPriorityRepository.findByUserIdOrderByPriorityRank(1L)).thenReturn(List.of());
    }

    @Test
    @DisplayName("명시적 청년 정책이 나이만 겹치는 정책보다 높은 rule 점수를 받는다")
    void explicitYouthPolicyGetsHigherRuleScoreThanAgeOnlyPolicy() {
        User user = User.builder().id(1L).build();

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
        User user = User.builder()
                .id(1L)
                .incomeLevel((byte) 6)
                .build();
        when(userAttributeRepository.findByUserId(1L)).thenReturn(List.of(
                UserAttribute.builder()
                        .user(user)
                        .attrType(UserAttribute.AttrType.INTEREST_FIELD.name())
                        .attrValue("취업")
                        .build()
        ));

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

    private ScoredCandidate findByServiceId(List<ScoredCandidate> scored, Long serviceId) {
        return scored.stream()
                .filter(candidate -> serviceId.equals(candidate.getService().getId()))
                .findFirst()
                .orElseThrow();
    }
}
