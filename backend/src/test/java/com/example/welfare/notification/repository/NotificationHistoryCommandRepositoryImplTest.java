package com.example.welfare.notification.repository;

import com.example.welfare.notification.entity.Notification;
import com.example.welfare.notification.entity.NotificationServiceItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationHistoryCommandRepositoryImplTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationServiceItemRepository notificationServiceItemRepository;

    @InjectMocks
    private NotificationHistoryCommandRepositoryImpl notificationHistoryCommandRepository;

    @Test
    @DisplayName("notification history command repository는 notification 저장을 위임한다")
    void saveNotificationDelegates() {
        Notification notification = Notification.builder().userKey("user-key-1").build();
        given(notificationRepository.save(notification)).willReturn(notification);

        assertThat(notificationHistoryCommandRepository.saveNotification(notification)).isEqualTo(notification);
    }

    @Test
    @DisplayName("notification history command repository는 notification item 저장을 위임한다")
    void saveNotificationItemsDelegates() {
        NotificationServiceItem item = NotificationServiceItem.builder().serviceTitle("title").build();

        notificationHistoryCommandRepository.saveNotificationItems(List.of(item));

        verify(notificationServiceItemRepository).saveAll(List.of(item));
    }
}
