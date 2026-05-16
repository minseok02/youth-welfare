package com.example.welfare.notification.repository;

import com.example.welfare.notification.entity.Notification;
import com.example.welfare.notification.entity.NotificationServiceItem;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationHistoryCommandRepositoryImplTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationServiceItemRepository notificationServiceItemRepository;

    @Mock
    private EntityManager entityManager;

    @Mock
    private Query query;

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
        Notification notification = Notification.builder()
                .userKey("user-key-1")
                .dispatchKey("daily:user-key-1:2026-05-05")
                .channel(Notification.NotificationChannel.EMAIL)
                .periodType(Notification.NotificationPeriodType.DAILY)
                .status(Notification.NotificationStatus.PENDING)
                .subject("subject")
                .totalServices(0)
                .retryCount(0)
                .build();
        given(entityManager.createNativeQuery(anyString())).willReturn(query);
        given(query.setParameter(anyString(), org.mockito.ArgumentMatchers.any())).willReturn(query);
        given(query.executeUpdate()).willReturn(0);

        assertThat(notificationHistoryCommandRepository.reserveNotification(notification)).isEqualTo(Optional.empty());
    }

    @Test
    @DisplayName("reservation insert 성공이면 dispatch key로 notification을 다시 조회한다")
    void reserveNotificationReturnsSavedNotificationOnInsert() {
        Notification notification = Notification.builder()
                .userKey("user-key-1")
                .dispatchKey("daily:user-key-1:2026-05-05")
                .channel(Notification.NotificationChannel.EMAIL)
                .periodType(Notification.NotificationPeriodType.DAILY)
                .status(Notification.NotificationStatus.PENDING)
                .subject("subject")
                .totalServices(0)
                .retryCount(0)
                .build();
        Notification saved = Notification.builder().id(11L).dispatchKey(notification.getDispatchKey()).build();
        given(entityManager.createNativeQuery(anyString())).willReturn(query);
        given(query.setParameter(anyString(), org.mockito.ArgumentMatchers.any())).willReturn(query);
        given(query.executeUpdate()).willReturn(1);
        given(notificationRepository.findByDispatchKey(notification.getDispatchKey())).willReturn(Optional.of(saved));

        assertThat(notificationHistoryCommandRepository.reserveNotification(notification)).contains(saved);
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
