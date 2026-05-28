package com.example.welfare.user.service;

import com.example.welfare.policy.dto.PolicySummaryResponse;
import com.example.welfare.policy.entity.RecentPolicyView;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.RecentPolicyViewRepository;
import com.example.welfare.policy.service.PolicyPresentationReadService;
import com.example.welfare.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserRecentViewedPolicyReadServiceTest {

    @Mock
    private ActiveUserReadService activeUserReadService;

    @Mock
    private RecentPolicyViewRepository recentPolicyViewRepository;

    @Mock
    private PolicyPresentationReadService policyPresentationReadService;

    @Test
    @DisplayName("최근 본 정책 조회는 최신 로그의 service 목록을 summary response로 변환한다")
    void getRecentViewedPoliciesReturnsPolicySummaries() {
        UserRecentViewedPolicyReadService service = new UserRecentViewedPolicyReadService(
                activeUserReadService,
                recentPolicyViewRepository,
                policyPresentationReadService
        );
        User user = User.builder()
                .id(1L)
                .email("user@example.com")
                .passwordHash("hash")
                .build();
        WelfareService first = WelfareService.builder()
                .id(11L)
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId("Y-11")
                .title("청년 월세 지원")
                .status(WelfareService.ServiceStatus.ACTIVE)
                .build();
        WelfareService second = WelfareService.builder()
                .id(21L)
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId("Y-21")
                .title("도약계좌")
                .status(WelfareService.ServiceStatus.ACTIVE)
                .build();

        given(activeUserReadService.getActiveUserContext(1L))
                .willReturn(new ActiveUserReadService.ActiveUserContext(user, "user-key-1"));
        given(recentPolicyViewRepository.findRecentViewsByUserKey(eq("user-key-1"), org.mockito.ArgumentMatchers.any(Pageable.class)))
                .willReturn(List.of(
                        RecentPolicyView.builder().id(101L).userKey("user-key-1").service(second).lastViewedAt(LocalDateTime.now()).build(),
                        RecentPolicyView.builder().id(100L).userKey("user-key-1").service(first).lastViewedAt(LocalDateTime.now().minusMinutes(1)).build()
                ));
        given(policyPresentationReadService.buildSummaryResponses(1L, List.of(second, first)))
                .willReturn(List.of(
                        PolicySummaryResponse.from(second, false),
                        PolicySummaryResponse.from(first, true)
                ));

        List<PolicySummaryResponse> response = service.getRecentViewedPolicies(1L, 40);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(recentPolicyViewRepository).findRecentViewsByUserKey(eq("user-key-1"), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(0);
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(30);
        assertThat(response).extracting(PolicySummaryResponse::getId).containsExactly(21L, 11L);
    }

    @Test
    @DisplayName("최근 본 정책 조회 limit이 없으면 기본값 10을 사용한다")
    void getRecentViewedPoliciesUsesDefaultLimitWhenLimitIsMissing() {
        UserRecentViewedPolicyReadService service = new UserRecentViewedPolicyReadService(
                activeUserReadService,
                recentPolicyViewRepository,
                policyPresentationReadService
        );
        User user = User.builder()
                .id(1L)
                .email("user@example.com")
                .passwordHash("hash")
                .build();

        given(activeUserReadService.getActiveUserContext(1L))
                .willReturn(new ActiveUserReadService.ActiveUserContext(user, "user-key-1"));
        given(recentPolicyViewRepository.findRecentViewsByUserKey(eq("user-key-1"), org.mockito.ArgumentMatchers.any(Pageable.class)))
                .willReturn(List.of());

        service.getRecentViewedPolicies(1L, null);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(recentPolicyViewRepository).findRecentViewsByUserKey(eq("user-key-1"), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(10);
    }

    @Test
    @DisplayName("최근 본 정책 조회 limit이 0 이하면 기본값 10을 사용한다")
    void getRecentViewedPoliciesUsesDefaultLimitWhenLimitIsNonPositive() {
        UserRecentViewedPolicyReadService service = new UserRecentViewedPolicyReadService(
                activeUserReadService,
                recentPolicyViewRepository,
                policyPresentationReadService
        );
        User user = User.builder()
                .id(1L)
                .email("user@example.com")
                .passwordHash("hash")
                .build();

        given(activeUserReadService.getActiveUserContext(1L))
                .willReturn(new ActiveUserReadService.ActiveUserContext(user, "user-key-1"));
        given(recentPolicyViewRepository.findRecentViewsByUserKey(eq("user-key-1"), org.mockito.ArgumentMatchers.any(Pageable.class)))
                .willReturn(List.of());

        service.getRecentViewedPolicies(1L, 0);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(recentPolicyViewRepository).findRecentViewsByUserKey(eq("user-key-1"), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(10);
    }

    @Test
    @DisplayName("최근 본 정책이 없으면 summary 변환 없이 빈 목록을 반환한다")
    void getRecentViewedPoliciesReturnsEmptyWithoutPresentationCall() {
        UserRecentViewedPolicyReadService service = new UserRecentViewedPolicyReadService(
                activeUserReadService,
                recentPolicyViewRepository,
                policyPresentationReadService
        );
        User user = User.builder()
                .id(1L)
                .email("user@example.com")
                .passwordHash("hash")
                .build();

        given(activeUserReadService.getActiveUserContext(1L))
                .willReturn(new ActiveUserReadService.ActiveUserContext(user, "user-key-1"));
        given(recentPolicyViewRepository.findRecentViewsByUserKey(eq("user-key-1"), org.mockito.ArgumentMatchers.any(Pageable.class)))
                .willReturn(List.of());

        List<PolicySummaryResponse> response = service.getRecentViewedPolicies(1L, 5);

        assertThat(response).isEmpty();
        verify(policyPresentationReadService, never()).buildSummaryResponses(eq(1L), org.mockito.ArgumentMatchers.anyList());
    }
}
