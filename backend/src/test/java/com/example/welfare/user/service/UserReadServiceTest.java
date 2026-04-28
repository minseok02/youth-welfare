package com.example.welfare.user.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.util.AesEncryptUtil;
import com.example.welfare.notification.dto.NotificationTarget;
import com.example.welfare.user.entity.AuthUser;
import com.example.welfare.user.entity.User;
import com.example.welfare.user.repository.AuthUserRepository;
import com.example.welfare.user.repository.NotificationPiiReadRepository;
import com.example.welfare.user.repository.NotificationTargetReadModel;
import com.example.welfare.user.repository.UserAttributeRepository;
import com.example.welfare.user.repository.UserPiiRepository;
import com.example.welfare.user.repository.UserPriorityRepository;
import com.example.welfare.user.repository.UserProfileRepository;
import com.example.welfare.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserReadServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private AuthUserRepository authUserRepository;
    @Mock private UserProfileRepository userProfileRepository;
    @Mock private UserPiiRepository userPiiRepository;
    @Mock private NotificationPiiReadRepository notificationPiiReadRepository;
    @Mock private UserAttributeRepository userAttributeRepository;
    @Mock private UserPriorityRepository userPriorityRepository;
    @Mock private AesEncryptUtil aesEncryptUtil;

    @Test
    @DisplayName("알림 대상 조회는 notification_pii_ro 저장소에서 이메일 암호문을 별도 조회한다")
    void getNotificationTargetsLoadsEmailsFromNotificationPiiReadRepository() {
        UserReadService userReadService = new UserReadService(
                userRepository,
                authUserRepository,
                userProfileRepository,
                userPiiRepository,
                notificationPiiReadRepository,
                userAttributeRepository,
                userPriorityRepository,
                aesEncryptUtil
        );

        NotificationTargetReadModel row = new NotificationTargetReadModel() {
            @Override
            public Long getUserId() {
                return 1L;
            }

            @Override
            public String getUserKey() {
                return "user-key-1";
            }

            @Override
            public String getNotificationPeriod() {
                return "DAILY";
            }

            @Override
            public Double getNotificationMinScore() {
                return 0.7;
            }

            @Override
            public int getDisplayCount() {
                return 10;
            }
        };

        when(userProfileRepository.findNotificationTargetsByPeriod("DAILY")).thenReturn(List.of(row));
        when(notificationPiiReadRepository.findEncryptedEmailsByUserKeys(List.of("user-key-1")))
                .thenReturn(Map.of("user-key-1", "encrypted-email"));
        when(aesEncryptUtil.decrypt("encrypted-email")).thenReturn("user@example.com");

        List<NotificationTarget> targets = userReadService.getNotificationTargets(User.NotificationPeriod.DAILY);

        assertThat(targets).containsExactly(
                new NotificationTarget(1L, "user-key-1", "user@example.com", User.NotificationPeriod.DAILY, 0.7, 10)
        );
        verify(notificationPiiReadRepository).findEncryptedEmailsByUserKeys(List.of("user-key-1"));
    }

    @Test
    @DisplayName("알림 재시도용 이메일 조회는 notification_pii_ro 저장소를 사용한다")
    void getNotificationEmailByUserKeyUsesNotificationPiiReadRepository() {
        UserReadService userReadService = new UserReadService(
                userRepository,
                authUserRepository,
                userProfileRepository,
                userPiiRepository,
                notificationPiiReadRepository,
                userAttributeRepository,
                userPriorityRepository,
                aesEncryptUtil
        );

        AuthUser authUser = AuthUser.builder()
                .userKey("user-key-1")
                .isActive(true)
                .build();

        when(authUserRepository.findByUserKey("user-key-1")).thenReturn(Optional.of(authUser));
        when(notificationPiiReadRepository.findEncryptedEmailByUserKey("user-key-1"))
                .thenReturn(Optional.of("encrypted-email"));
        when(aesEncryptUtil.decrypt("encrypted-email")).thenReturn("user@example.com");

        String email = userReadService.getNotificationEmailByUserKey("user-key-1");

        assertThat(email).isEqualTo("user@example.com");
        verify(notificationPiiReadRepository).findEncryptedEmailByUserKey("user-key-1");
    }

    @Test
    @DisplayName("알림 재시도용 이메일이 비어 있으면 발송 실패 예외를 던진다")
    void getNotificationEmailByUserKeyThrowsWhenEmailBlank() {
        UserReadService userReadService = new UserReadService(
                userRepository,
                authUserRepository,
                userProfileRepository,
                userPiiRepository,
                notificationPiiReadRepository,
                userAttributeRepository,
                userPriorityRepository,
                aesEncryptUtil
        );

        AuthUser authUser = AuthUser.builder()
                .userKey("user-key-1")
                .isActive(true)
                .build();

        when(authUserRepository.findByUserKey("user-key-1")).thenReturn(Optional.of(authUser));
        when(notificationPiiReadRepository.findEncryptedEmailByUserKey("user-key-1"))
                .thenReturn(Optional.of("encrypted-email"));
        when(aesEncryptUtil.decrypt("encrypted-email")).thenReturn("");

        assertThatThrownBy(() -> userReadService.getNotificationEmailByUserKey("user-key-1"))
                .isInstanceOf(CustomException.class);
    }
}
