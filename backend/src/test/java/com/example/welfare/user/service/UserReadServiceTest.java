package com.example.welfare.user.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.util.AesEncryptUtil;
import com.example.welfare.notification.dto.NotificationTarget;
import com.example.welfare.recommend.dto.RecommendationUserSnapshot;
import com.example.welfare.user.dto.response.ProfileResponse;
import com.example.welfare.user.entity.AuthUser;
import com.example.welfare.user.entity.User;
import com.example.welfare.user.entity.UserProfile;
import com.example.welfare.user.repository.AuthUserRepository;
import com.example.welfare.user.repository.NotificationTargetAggregateReadModel;
import com.example.welfare.user.repository.NotificationPiiReadRepository;
import com.example.welfare.user.repository.NotificationTargetReadRepository;
import com.example.welfare.user.repository.RecommendationUserReadModel;
import com.example.welfare.user.repository.RecommendationUserReadRepository;
import com.example.welfare.user.repository.UserProfileAggregateReadModel;
import com.example.welfare.user.repository.UserPiiReadModel;
import com.example.welfare.user.repository.UserPiiReadWriteRepository;
import com.example.welfare.user.repository.UserProfileRepository;
import com.example.welfare.user.repository.UserProfileReadRepository;
import com.example.welfare.user.repository.UserRepository;
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
class UserReadServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private AuthUserRepository authUserRepository;
    @Mock private UserProfileRepository userProfileRepository;
    @Mock private UserProfileReadRepository userProfileReadRepository;
    @Mock private RecommendationUserReadRepository recommendationUserReadRepository;
    @Mock private UserPiiReadWriteRepository userPiiReadWriteRepository;
    @Mock private NotificationPiiReadRepository notificationPiiReadRepository;
    @Mock private NotificationTargetReadRepository notificationTargetReadRepository;
    @Mock private AesEncryptUtil aesEncryptUtil;
    @Mock private UserKeyLookupService userKeyLookupService;

    @Test
    @DisplayName("프로필 조회는 app_pii_rw 저장소에서 PII 암호문을 읽는다")
    void getProfileLoadsPiiFromAppPiiReadWriteRepository() {
        UserReadService userReadService = new UserReadService(
                userRepository,
                authUserRepository,
                userProfileRepository,
                userProfileReadRepository,
                recommendationUserReadRepository,
                userPiiReadWriteRepository,
                notificationPiiReadRepository,
                notificationTargetReadRepository,
                aesEncryptUtil,
                userKeyLookupService
        );

        AuthUser authUser = AuthUser.builder()
                .userKey("user-key-1")
                .isActive(true)
                .build();
        UserProfile profile = UserProfile.builder()
                .userKey("user-key-1")
                .sido("서울특별시")
                .sgg("관악구")
                .displayCount(12)
                .build();

        when(userKeyLookupService.findRequired(1L)).thenReturn("user-key-1");
        when(authUserRepository.findByUserKey("user-key-1")).thenReturn(Optional.of(authUser));
        when(userProfileReadRepository.findProfileAggregateByUserKey("user-key-1"))
                .thenReturn(Optional.of(new UserProfileAggregateReadModel(
                        profile,
                        new UserPiiReadModel("user-key-1", "enc-email", "enc-name", "enc-birth", "enc-phone"),
                        List.of(),
                        List.of()
                )));
        when(aesEncryptUtil.decrypt("enc-email")).thenReturn("user@example.com");
        when(aesEncryptUtil.decrypt("enc-name")).thenReturn("홍길동");
        when(aesEncryptUtil.decrypt("enc-birth")).thenReturn("1999-01-10");

        ProfileResponse response = userReadService.getProfile(1L);

        assertThat(response.getEmail()).isEqualTo("user@example.com");
        assertThat(response.getName()).isEqualTo("홍길동");
        assertThat(response.getBirthDate()).isEqualTo(java.time.LocalDate.of(1999, 1, 10));
        verify(userProfileReadRepository).findProfileAggregateByUserKey("user-key-1");
    }

    @Test
    @DisplayName("알림 대상 조회는 notification_pii_ro 저장소에서 이메일 암호문을 별도 조회한다")
    void getNotificationTargetsLoadsEmailsFromNotificationPiiReadRepository() {
        UserReadService userReadService = new UserReadService(
                userRepository,
                authUserRepository,
                userProfileRepository,
                userProfileReadRepository,
                recommendationUserReadRepository,
                userPiiReadWriteRepository,
                notificationPiiReadRepository,
                notificationTargetReadRepository,
                aesEncryptUtil,
                userKeyLookupService
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

        List<NotificationTarget> targets = userReadService.getNotificationTargets(User.NotificationPeriod.DAILY);

        assertThat(targets).containsExactly(
                new NotificationTarget(1L, "user-key-1", "user@example.com", User.NotificationPeriod.DAILY, 0.7, 10)
        );
        verify(notificationTargetReadRepository).findNotificationTargetsByPeriod(User.NotificationPeriod.DAILY);
    }

    @Test
    @DisplayName("알림 재시도용 이메일 조회는 notification_pii_ro 저장소를 사용한다")
    void getNotificationEmailByUserKeyUsesNotificationPiiReadRepository() {
        UserReadService userReadService = new UserReadService(
                userRepository,
                authUserRepository,
                userProfileRepository,
                userProfileReadRepository,
                recommendationUserReadRepository,
                userPiiReadWriteRepository,
                notificationPiiReadRepository,
                notificationTargetReadRepository,
                aesEncryptUtil,
                userKeyLookupService
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
                userProfileReadRepository,
                recommendationUserReadRepository,
                userPiiReadWriteRepository,
                notificationPiiReadRepository,
                notificationTargetReadRepository,
                aesEncryptUtil,
                userKeyLookupService
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

    @Test
    @DisplayName("추천 컨텍스트 조회는 active user 검증 후 user entity 와 snapshot 을 함께 반환한다")
    void getRecommendationContextReturnsUserAndSnapshot() {
        UserReadService userReadService = new UserReadService(
                userRepository,
                authUserRepository,
                userProfileRepository,
                userProfileReadRepository,
                recommendationUserReadRepository,
                userPiiReadWriteRepository,
                notificationPiiReadRepository,
                notificationTargetReadRepository,
                aesEncryptUtil,
                userKeyLookupService
        );

        User user = User.builder()
                .id(7L)
                .userKey("user-key-7")
                .notificationPeriod(User.NotificationPeriod.DAILY)
                .build();
        AuthUser authUser = AuthUser.builder()
                .userKey("user-key-7")
                .isActive(true)
                .build();
        UserProfile profile = UserProfile.builder()
                .userKey("user-key-7")
                .age(27)
                .ageBand("25-29")
                .sido("서울특별시")
                .sgg("관악구")
                .regionCode("11620")
                .incomeLevel((byte) 3)
                .householdType("1인가구")
                .employmentStatus("미취업")
                .displayCount(12)
                .notificationMinScore(0.7)
                .build();

        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(authUserRepository.findByUserKey("user-key-7")).thenReturn(Optional.of(authUser));
        when(recommendationUserReadRepository.findByUserKey("user-key-7"))
                .thenReturn(Optional.of(new RecommendationUserReadModel(
                        profile,
                        List.of(),
                        List.of()
                )));

        UserReadService.RecommendationReadContext context = userReadService.getRecommendationContext(7L);

        assertThat(context.user()).isEqualTo(user);
        RecommendationUserSnapshot snapshot = context.snapshot();
        assertThat(snapshot.userId()).isEqualTo(7L);
        assertThat(snapshot.userKey()).isEqualTo("user-key-7");
        assertThat(snapshot.sido()).isEqualTo("서울특별시");
        assertThat(snapshot.regionCode()).isEqualTo("11620");
    }

    @Test
    @DisplayName("active user context 조회는 user와 resolved userKey를 함께 반환한다")
    void getActiveUserContextReturnsUserAndUserKey() {
        UserReadService userReadService = new UserReadService(
                userRepository,
                authUserRepository,
                userProfileRepository,
                userProfileReadRepository,
                recommendationUserReadRepository,
                userPiiReadWriteRepository,
                notificationPiiReadRepository,
                notificationTargetReadRepository,
                aesEncryptUtil,
                userKeyLookupService
        );
        User user = User.builder()
                .id(9L)
                .userKey("user-key-9")
                .build();

        when(userRepository.findById(9L)).thenReturn(Optional.of(user));

        UserReadService.ActiveUserContext context = userReadService.getActiveUserContext(9L);

        assertThat(context.user()).isEqualTo(user);
        assertThat(context.userKey()).isEqualTo("user-key-9");
    }

    @Test
    @DisplayName("optional active user by userKey 조회는 탈퇴 사용자를 제외한다")
    void findOptionalActiveUserByUserKeyExcludesWithdrawnUser() {
        UserReadService userReadService = new UserReadService(
                userRepository,
                authUserRepository,
                userProfileRepository,
                userProfileReadRepository,
                recommendationUserReadRepository,
                userPiiReadWriteRepository,
                notificationPiiReadRepository,
                notificationTargetReadRepository,
                aesEncryptUtil,
                userKeyLookupService
        );
        User withdrawnUser = User.builder()
                .id(10L)
                .userKey("user-key-10")
                .isActive(false)
                .build();

        when(userRepository.findByUserKey("user-key-10")).thenReturn(Optional.of(withdrawnUser));

        assertThat(userReadService.findOptionalActiveUserByUserKey("user-key-10")).isEmpty();
    }

    @Test
    @DisplayName("required active user by userKey 조회는 탈퇴 사용자를 찾지 못한 것으로 처리한다")
    void getActiveUserByUserKeyRejectsWithdrawnUser() {
        UserReadService userReadService = new UserReadService(
                userRepository,
                authUserRepository,
                userProfileRepository,
                userProfileReadRepository,
                recommendationUserReadRepository,
                userPiiReadWriteRepository,
                notificationPiiReadRepository,
                notificationTargetReadRepository,
                aesEncryptUtil,
                userKeyLookupService
        );
        User withdrawnUser = User.builder()
                .id(10L)
                .userKey("user-key-10")
                .isActive(false)
                .build();

        when(userRepository.findByUserKey("user-key-10")).thenReturn(Optional.of(withdrawnUser));

        assertThatThrownBy(() -> userReadService.getActiveUserByUserKey("user-key-10"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(com.example.welfare.global.exception.ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("existing user id by userKey 조회는 userId를 반환한다")
    void requireExistingUserIdByUserKeyReturnsUserId() {
        UserReadService userReadService = new UserReadService(
                userRepository,
                authUserRepository,
                userProfileRepository,
                userProfileReadRepository,
                recommendationUserReadRepository,
                userPiiReadWriteRepository,
                notificationPiiReadRepository,
                notificationTargetReadRepository,
                aesEncryptUtil,
                userKeyLookupService
        );

        when(userRepository.findIdByUserKey("user-key-11")).thenReturn(Optional.of(11L));

        assertThat(userReadService.requireExistingUserIdByUserKey("user-key-11")).isEqualTo(11L);
    }
}
