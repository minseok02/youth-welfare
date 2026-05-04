package com.example.welfare.notification.service;

import com.example.welfare.notification.entity.Notification;
import com.example.welfare.notification.repository.NotificationRetryReadRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class NotificationRetryReadServiceTest {

    @Mock
    private NotificationRetryReadRepository notificationRetryReadRepository;

    @InjectMocks
    private NotificationRetryReadService notificationRetryReadService;

    @Test
    @DisplayName("notification retry read service는 재시도 가능한 실패 알림 조회를 위임한다")
    void findRetryableFailedNotificationsDelegates() {
        LocalDateTime now = LocalDateTime.now();
        Notification notification = Notification.builder().id(1L).userKey("user-key-1").build();
        given(notificationRetryReadRepository.findRetryableFailedNotifications(eq(now)))
                .willReturn(List.of(notification));

        assertThat(notificationRetryReadService.findRetryableFailedNotifications(now))
                .containsExactly(notification);
        then(notificationRetryReadRepository).should().findRetryableFailedNotifications(eq(now));
    }
}
