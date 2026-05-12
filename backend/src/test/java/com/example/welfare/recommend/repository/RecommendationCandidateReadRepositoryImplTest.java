package com.example.welfare.recommend.repository;

import com.example.welfare.policy.entity.ServiceTag;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.PolicyTagReadRepository;
import com.example.welfare.policy.service.PolicyExplorationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class RecommendationCandidateReadRepositoryImplTest {

    @Mock
    private PolicyExplorationService policyExplorationService;

    @Mock
    private PolicyTagReadRepository policyTagReadRepository;

    @InjectMocks
    private RecommendationCandidateReadRepositoryImpl recommendationCandidateReadRepository;

    @Test
    @DisplayName("recommendation candidate read repository는 기본 후보 조회를 위임한다")
    void findBaseCandidatesDelegates() {
        WelfareService service = WelfareService.builder().id(1L).sourceId("SRC-1").title("청년 정책").build();
        RecommendationCandidateReadCondition condition =
                new RecommendationCandidateReadCondition(26, 5, "서울특별시", null, 150, 20);
        given(policyExplorationService.findRecommendationBaseCandidates(condition))
                .willReturn(List.of(service));

        assertThat(recommendationCandidateReadRepository.findBaseCandidates(condition))
                .containsExactly(service);
    }

    @Test
    @DisplayName("recommendation candidate read repository는 최신 후보 조회를 위임한다")
    void findLatestCandidatesDelegates() {
        WelfareService service = WelfareService.builder().id(2L).sourceId("SRC-2").title("최신 정책").build();
        RecommendationCandidateReadCondition condition =
                new RecommendationCandidateReadCondition(26, 5, "서울특별시", "11680", 150, 20);
        given(policyExplorationService.findRecommendationLatestCandidates(condition))
                .willReturn(List.of(service));

        assertThat(recommendationCandidateReadRepository.findLatestCandidates(condition))
                .containsExactly(service);
    }

    @Test
    @DisplayName("recommendation candidate read repository는 후보 태그 조회를 위임한다")
    void findTagsByServiceIdsDelegates() {
        ServiceTag tag = ServiceTag.builder().id(1L).tagValue("COND_AGE_MIN_19").build();
        given(policyTagReadRepository.findByServiceIds(List.of(1L)))
                .willReturn(Map.of(1L, List.of(tag)));

        assertThat(recommendationCandidateReadRepository.findTagsByServiceIds(List.of(1L)))
                .containsEntry(1L, List.of(tag));
    }
}
