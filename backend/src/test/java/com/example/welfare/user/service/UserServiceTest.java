package com.example.welfare.user.service;

import com.example.welfare.policy.dto.PolicySummaryResponse;
import com.example.welfare.user.dto.request.UpdatePrioritiesRequest;
import com.example.welfare.user.dto.request.UpdateProfileRequest;
import com.example.welfare.user.dto.response.ProfileResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserReadService userReadService;
    @Mock private UserBookmarkReadService userBookmarkReadService;
    @Mock private UserProfileCommandService userProfileCommandService;
    @Mock private UserAccountCommandService userAccountCommandService;

    @InjectMocks
    private UserService userService;

    @Test
    @DisplayName("프로필 조회는 UserReadService에 위임한다")
    void getProfileDelegatesToReadService() {
        ProfileResponse response = ProfileResponse.builder().id(1L).build();
        given(userReadService.getProfile(1L)).willReturn(response);

        ProfileResponse result = userService.getProfile(1L);

        assertThat(result).isSameAs(response);
        then(userReadService).should().getProfile(1L);
    }

    @Test
    @DisplayName("북마크 조회는 UserBookmarkReadService에 위임한다")
    void getBookmarksDelegatesToBookmarkReadService() {
        List<PolicySummaryResponse> responses = List.of(PolicySummaryResponse.builder().id(11L).build());
        given(userBookmarkReadService.getBookmarks(1L)).willReturn(responses);

        List<PolicySummaryResponse> result = userService.getBookmarks(1L);

        assertThat(result).isSameAs(responses);
        then(userBookmarkReadService).should().getBookmarks(1L);
    }

    @Test
    @DisplayName("프로필 수정은 UserProfileCommandService에 위임한다")
    void updateProfileDelegatesToProfileCommandService() {
        UpdateProfileRequest request = new UpdateProfileRequest();

        userService.updateProfile(1L, request);

        then(userProfileCommandService).should().updateProfile(1L, request);
    }

    @Test
    @DisplayName("우선순위 수정은 UserProfileCommandService에 위임한다")
    void updatePrioritiesDelegatesToProfileCommandService() {
        UpdatePrioritiesRequest request = new UpdatePrioritiesRequest();

        userService.updatePriorities(1L, request);

        then(userProfileCommandService).should().updatePriorities(1L, request);
    }

    @Test
    @DisplayName("비밀번호 변경은 UserAccountCommandService에 위임한다")
    void changePasswordDelegatesToAccountCommandService() {
        userService.changePassword(1L, "current", "next");

        then(userAccountCommandService).should().changePassword(1L, "current", "next");
    }

    @Test
    @DisplayName("탈퇴는 UserAccountCommandService에 위임한다")
    void withdrawDelegatesToAccountCommandService() {
        userService.withdraw(1L, "password", "access-token");

        then(userAccountCommandService).should().withdraw(1L, "password", "access-token");
    }

    @Test
    @DisplayName("알림 수신 거부는 UserAccountCommandService에 위임한다")
    void unsubscribeDelegatesToAccountCommandService() {
        userService.unsubscribeNotifications(1L);
        userService.unsubscribeNotificationsByUserKey("user-key-1");

        then(userAccountCommandService).should().unsubscribeNotifications(1L);
        then(userAccountCommandService).should().unsubscribeNotificationsByUserKey("user-key-1");
    }
}
