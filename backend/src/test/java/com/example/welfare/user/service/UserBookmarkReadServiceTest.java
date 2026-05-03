package com.example.welfare.user.service;

import com.example.welfare.policy.dto.PolicySummaryResponse;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.recommend.facade.RecommendationReadFacade;
import com.example.welfare.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserBookmarkReadServiceTest {

    @Mock private UserReadService userReadService;
    @Mock private RecommendationReadFacade recommendationReadFacade;

    @Test
    @DisplayName("북마크 목록 조회는 recommendation read facade 결과를 그대로 반환한다")
    void getBookmarksReturnsPolicySummaries() {
        UserBookmarkReadService service = new UserBookmarkReadService(
                userReadService,
                recommendationReadFacade
        );
        User user = User.builder()
                .id(1L)
                .email("user@example.com")
                .passwordHash("hash")
                .build();
        WelfareService policy = WelfareService.builder()
                .id(11L)
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId("Y-11")
                .title("청년 월세 지원")
                .description("월세 부담 완화")
                .unifiedCategory("HOUSING")
                .status(WelfareService.ServiceStatus.ACTIVE)
                .build();
        when(userReadService.getActiveUserContext(1L))
                .thenReturn(new UserReadService.ActiveUserContext(user, "user-key-1"));
        when(recommendationReadFacade.findBookmarkedPolicySummaries("user-key-1"))
                .thenReturn(List.of(PolicySummaryResponse.from(policy, true)));

        List<PolicySummaryResponse> response = service.getBookmarks(1L);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).getId()).isEqualTo(11L);
        assertThat(response.get(0).getTitle()).isEqualTo("청년 월세 지원");
        assertThat(response.get(0).isBookmarked()).isTrue();
    }
}
