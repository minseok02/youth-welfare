package com.example.welfare.notification.repository;

import com.example.welfare.notification.entity.Notification;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class NotificationRetryReadRepositoryImplTest {

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private NotificationRetryReadRepositoryImpl notificationRetryReadRepository;

    @Test
    @DisplayName("notification retry read repository는 FAILED + nextRetryAt 조회를 위임한다")
    void findRetryableFailedNotificationsDelegates() {
        Notification notification = Notification.builder().userKey("user-key-1").build();
        LocalDateTime now = LocalDateTime.of(2026, 5, 4, 15, 0);
        given(notificationRepository.findByStatusAndNextRetryAtBefore(Notification.NotificationStatus.FAILED, now))
                .willReturn(List.of(notification));

        assertThat(notificationRetryReadRepository.findRetryableFailedNotifications(now)).containsExactly(notification);
    }
}
