package com.example.welfare.admin.dashboard.service;

import com.example.welfare.admin.dashboard.dto.AdminNotificationStaleHideRequest;
import com.example.welfare.admin.dashboard.dto.AdminNotificationStaleHideResponse;
import com.example.welfare.global.exception.CustomException;
import com.example.welfare.notification.entity.UserAlert;
import com.example.welfare.notification.repository.UserAlertRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

class AdminNotificationBacklogServiceTest {

    private final UserAlertRepository userAlertRepository = mock(UserAlertRepository.class);
    private final AdminNotificationBacklogService service = new AdminNotificationBacklogService(userAlertRepository);

    @Test
    @DisplayName("stale notification backlog hide는 matching unread를 숨긴다")
    void hideStaleAlerts() {
        given(userAlertRepository.hideUnreadByKindAndTitleAndDeeplinkUrlBefore(
                eq(UserAlert.UserAlertKind.DEADLINE_REMINDER),
                eq("북마크한 정책 마감이 임박했어요"),
                eq("/policies/2622"),
                any(),
                any()
        )).willReturn(5);

        AdminNotificationStaleHideResponse response = service.hideStaleAlerts(
                new AdminNotificationStaleHideRequest(
                        "DEADLINE_REMINDER",
                        "북마크한 정책 마감이 임박했어요",
                        "/policies/2622",
                        14
                )
        );

        assertThat(response.hiddenCount()).isEqualTo(5);
        assertThat(response.kind()).isEqualTo("DEADLINE_REMINDER");
        assertThat(response.deeplinkUrl()).isEqualTo("/policies/2622");
        assertThat(response.olderThanDays()).isEqualTo(14);
        then(userAlertRepository).should().hideUnreadByKindAndTitleAndDeeplinkUrlBefore(
                eq(UserAlert.UserAlertKind.DEADLINE_REMINDER),
                eq("북마크한 정책 마감이 임박했어요"),
                eq("/policies/2622"),
                any(),
                any()
        );
    }

    @Test
    @DisplayName("잘못된 kind면 INVALID_INPUT")
    void hideStaleAlertsRejectsInvalidKind() {
        assertThatThrownBy(() -> service.hideStaleAlerts(
                new AdminNotificationStaleHideRequest(
                        "bad-kind",
                        "북마크한 정책 마감이 임박했어요",
                        "/policies/2622",
                        14
                )
        )).isInstanceOf(CustomException.class);
    }

    @Test
    @DisplayName("stale notification backlog hide는 외부 deeplinkUrl을 거부한다")
    void hideStaleAlertsRejectsExternalDeeplink() {
        assertThatThrownBy(() -> service.hideStaleAlerts(
                new AdminNotificationStaleHideRequest(
                        "DEADLINE_REMINDER",
                        "북마크한 정책 마감이 임박했어요",
                        "https://evil.example/policies/2622",
                        14
                )
        )).isInstanceOf(CustomException.class);
    }

    @Test
    @DisplayName("stale notification backlog hide는 protocol-relative deeplinkUrl을 거부한다")
    void hideStaleAlertsRejectsProtocolRelativeDeeplink() {
        assertThatThrownBy(() -> service.hideStaleAlerts(
                new AdminNotificationStaleHideRequest(
                        "DEADLINE_REMINDER",
                        "북마크한 정책 마감이 임박했어요",
                        "//evil.example/policies/2622",
                        14
                )
        )).isInstanceOf(CustomException.class);
    }
}
