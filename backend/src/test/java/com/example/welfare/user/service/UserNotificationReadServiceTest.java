package com.example.welfare.user.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.util.AesEncryptUtil;
import com.example.welfare.notification.dto.NotificationTarget;
import com.example.welfare.user.entity.User;
import com.example.welfare.user.repository.NotificationTargetAggregateReadModel;
import com.example.welfare.user.repository.NotificationTargetReadRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserNotificationReadServiceTest {

    @Mock private ActiveUserReadService activeUserReadService;
    @Mock private NotificationTargetReadRepository notificationTargetReadRepository;
    @Mock private AesEncryptUtil aesEncryptUtil;

    @Test
    @DisplayName("알림 대상 조회는 notification read repository aggregate를 사용한다")
    void getNotificationTargetsLoadsTargetsFromReadRepository() {
        UserNotificationReadService service = new UserNotificationReadService(
                activeUserReadService,
                notificationTargetReadRepository,
                aesEncryptUtil
        );
        when(notificationTargetReadRepository.findNotificationTargetsByPeriod(User.NotificationPeriod.DAILY))
                .thenReturn(List.of(new NotificationTargetAggregateReadModel(
                        1L,
                        "user-key-1",
                        "DAILY",
                        0.7,
                        10,
                        "encrypted-email"
                )));
        when(aesEncryptUtil.decrypt("encrypted-email")).thenReturn("user@example.com");

        List<NotificationTarget> targets = service.getNotificationTargets(User.NotificationPeriod.DAILY);

        assertThat(targets).containsExactly(
                new NotificationTarget(1L, "user-key-1", "user@example.com", User.NotificationPeriod.DAILY, 0.7, 10)
        );
        verify(notificationTargetReadRepository).findNotificationTargetsByPeriod(User.NotificationPeriod.DAILY);
    }

    @Test
    @DisplayName("알림 재시도용 이메일 조회는 active user 검증 후 notification read repository를 사용한다")
    void getNotificationEmailByUserKeyUsesNotificationReadRepository() {
        UserNotificationReadService service = new UserNotificationReadService(
                activeUserReadService,
                notificationTargetReadRepository,
                aesEncryptUtil
        );
        User user = User.builder().id(1L).userKey("user-key-1").build();

        when(activeUserReadService.getActiveUserByUserKey("user-key-1")).thenReturn(user);
        when(notificationTargetReadRepository.findEncryptedEmailByUserKey("user-key-1"))
                .thenReturn(Optional.of("encrypted-email"));
        when(aesEncryptUtil.decrypt("encrypted-email")).thenReturn("user@example.com");

        String email = service.getNotificationEmailByUserKey("user-key-1");

        assertThat(email).isEqualTo("user@example.com");
        verify(notificationTargetReadRepository).findEncryptedEmailByUserKey("user-key-1");
    }

    @Test
    @DisplayName("알림 재시도용 이메일이 비어 있으면 발송 실패 예외를 던진다")
    void getNotificationEmailByUserKeyThrowsWhenEmailBlank() {
        UserNotificationReadService service = new UserNotificationReadService(
                activeUserReadService,
                notificationTargetReadRepository,
                aesEncryptUtil
        );
        User user = User.builder().id(1L).userKey("user-key-1").build();

        when(activeUserReadService.getActiveUserByUserKey("user-key-1")).thenReturn(user);
        when(notificationTargetReadRepository.findEncryptedEmailByUserKey("user-key-1"))
                .thenReturn(Optional.of("encrypted-email"));
        when(aesEncryptUtil.decrypt("encrypted-email")).thenReturn("");

        assertThatThrownBy(() -> service.getNotificationEmailByUserKey("user-key-1"))
                .isInstanceOf(CustomException.class);
    }
}
