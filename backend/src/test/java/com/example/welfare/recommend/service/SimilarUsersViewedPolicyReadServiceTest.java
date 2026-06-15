package com.example.welfare.recommend.service;

import com.example.welfare.policy.dto.PolicySummaryResponse;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.WelfareServiceRepository;
import com.example.welfare.policy.service.PolicyPresentationReadService;
import com.example.welfare.recommend.dto.RecommendationUserSnapshot;
import com.example.welfare.recommend.dto.SimilarUsersViewedPolicyResponse;
import com.example.welfare.recommend.repository.SimilarUsersViewedPolicyCandidate;
import com.example.welfare.recommend.repository.SimilarUsersViewedPolicyQuery;
import com.example.welfare.recommend.repository.SimilarUsersViewedPolicyReadRepository;
import com.example.welfare.user.service.UserRecommendationReadService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SimilarUsersViewedPolicyReadServiceTest {

    @Mock
    private UserRecommendationReadService userRecommendationReadService;
    @Mock
    private SimilarUsersViewedPolicyReadRepository similarUsersViewedPolicyReadRepository;
    @Mock
    private WelfareServiceRepository welfareServiceRepository;
    @Mock
    private PolicyPresentationReadService policyPresentationReadService;

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    @Test
    @DisplayName("유사 사용자 조회 정책은 후보 순서를 유지해 policy summary 응답으로 변환한다")
    void getSimilarUsersViewedPoliciesReturnsOrderedSummaries() {
        SimilarUsersViewedPolicyReadService service = service();
        RecommendationUserSnapshot snapshot = snapshotWithSignals();
        WelfareService first = service(11L, "청년 월세 지원");
        WelfareService second = service(22L, "청년 전세 지원");

        given(userRecommendationReadService.getRecommendationSnapshot(1L)).willReturn(snapshot);
        given(similarUsersViewedPolicyReadRepository.findCandidates(any()))
                .willReturn(List.of(
                        new SimilarUsersViewedPolicyCandidate(22L, 3, 5),
                        new SimilarUsersViewedPolicyCandidate(11L, 2, 4)
                ));
        given(welfareServiceRepository.findAllById(List.of(22L, 11L))).willReturn(List.of(first, second));
        given(policyPresentationReadService.buildSummaryResponses(1L, List.of(second, first)))
                .willReturn(List.of(
                        PolicySummaryResponse.from(second, false),
                        PolicySummaryResponse.from(first, false)
                ));

        List<SimilarUsersViewedPolicyResponse> result = service.getSimilarUsersViewedPolicies(1L, 6);

        assertThat(result).extracting(response -> response.policy().getId()).containsExactly(22L, 11L);
        assertThat(result).allSatisfy(response ->
                assertThat(response.reasonLabel()).isEqualTo("비슷한 프로필의 사용자가 최근 확인")
        );
        assertThat(meterRegistry.counter(
                SimilarUsersViewedPolicyMetrics.REQUEST_COUNTER,
                "outcome",
                "returned"
        ).count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("유사도 신호가 없으면 조회 로그 repository를 호출하지 않는다")
    void getSimilarUsersViewedPoliciesReturnsEmptyWhenProfileHasNoSignals() {
        SimilarUsersViewedPolicyReadService service = service();
        given(userRecommendationReadService.getRecommendationSnapshot(1L)).willReturn(snapshotWithoutSignals());

        List<SimilarUsersViewedPolicyResponse> result = service.getSimilarUsersViewedPolicies(1L, 6);

        assertThat(result).isEmpty();
        verify(similarUsersViewedPolicyReadRepository, never()).findCandidates(any());
        verify(welfareServiceRepository, never()).findAllById(any());
        verify(policyPresentationReadService, never()).buildSummaryResponses(any(), any());
        assertThat(meterRegistry.counter(
                SimilarUsersViewedPolicyMetrics.REQUEST_COUNTER,
                "outcome",
                "no_signal"
        ).count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("유사 사용자 조회 정책은 size를 1..20 범위로 정규화해 후보를 여유 있게 읽는다")
    void getSimilarUsersViewedPoliciesNormalizesSize() {
        SimilarUsersViewedPolicyReadService service = service();
        given(userRecommendationReadService.getRecommendationSnapshot(1L)).willReturn(snapshotWithSignals());
        given(similarUsersViewedPolicyReadRepository.findCandidates(any())).willReturn(List.of());

        service.getSimilarUsersViewedPolicies(1L, 1000);

        ArgumentCaptor<SimilarUsersViewedPolicyQuery> queryCaptor =
                ArgumentCaptor.forClass(SimilarUsersViewedPolicyQuery.class);
        verify(similarUsersViewedPolicyReadRepository).findCandidates(queryCaptor.capture());
        assertThat(queryCaptor.getValue().limit()).isEqualTo(60);
        assertThat(queryCaptor.getValue().minSimilarUsers()).isEqualTo(2);
        assertThat(queryCaptor.getValue().minSimilarityScore()).isEqualTo(3.0);
        assertThat(meterRegistry.counter(
                SimilarUsersViewedPolicyMetrics.REQUEST_COUNTER,
                "outcome",
                "empty"
        ).count()).isEqualTo(1.0);
    }

    private SimilarUsersViewedPolicyReadService service() {
        return new SimilarUsersViewedPolicyReadService(
                userRecommendationReadService,
                similarUsersViewedPolicyReadRepository,
                welfareServiceRepository,
                policyPresentationReadService,
                new SimilarUsersViewedPolicyMetrics(meterRegistry)
        );
    }

    private RecommendationUserSnapshot snapshotWithSignals() {
        return new RecommendationUserSnapshot(
                1L,
                "user-key-1",
                25,
                "20대",
                "서울특별시",
                "관악구",
                "11000",
                (byte) 5,
                null,
                "미취업",
                6,
                0.5,
                List.of("주거"),
                List.of("청년"),
                List.of()
        );
    }

    private RecommendationUserSnapshot snapshotWithoutSignals() {
        return new RecommendationUserSnapshot(
                1L,
                "user-key-1",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                6,
                0.5,
                List.of(),
                List.of(),
                List.of()
        );
    }

    private WelfareService service(Long id, String title) {
        return WelfareService.builder()
                .id(id)
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId("SRC-" + id)
                .title(title)
                .status(WelfareService.ServiceStatus.ACTIVE)
                .build();
    }
}
