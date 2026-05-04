package com.example.welfare.notification.repository;

import com.example.welfare.notification.entity.Notification;
import com.example.welfare.notification.entity.NotificationServiceItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Optional;

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

    @Test
    @DisplayName("dispatch key unique 충돌이면 reservation은 empty를 반환한다")
    void reserveNotificationReturnsEmptyOnDuplicateKey() {
        Notification notification = Notification.builder().dispatchKey("daily:user-key-1:2026-05-05").build();
        given(notificationRepository.save(notification))
                .willThrow(new DataIntegrityViolationException("duplicate"));

        assertThat(notificationHistoryCommandRepository.reserveNotification(notification)).isEqualTo(Optional.empty());
    }

    @Test
    @DisplayName("replace items 는 기존 item 삭제 후 새 item 을 저장한다")
    void replaceNotificationItemsReplacesExistingItems() {
        NotificationServiceItem item = NotificationServiceItem.builder().serviceTitle("title").build();

        notificationHistoryCommandRepository.replaceNotificationItems(11L, List.of(item));

        verify(notificationServiceItemRepository).deleteByNotificationId(11L);
        verify(notificationServiceItemRepository).saveAll(List.of(item));
    }
}
