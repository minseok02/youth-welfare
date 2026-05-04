package com.example.welfare.user.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.recommend.dto.RecommendationUserSnapshot;
import com.example.welfare.user.entity.User;
import com.example.welfare.user.entity.UserProfile;
import com.example.welfare.user.repository.UserAccountReadRepository;
import com.example.welfare.user.repository.RecommendationUserReadModel;
import com.example.welfare.user.repository.RecommendationUserReadRepository;
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

    @Mock private UserAccountReadRepository userAccountReadRepository;
    @Mock private RecommendationUserReadRepository recommendationUserReadRepository;
    @Mock private UserKeyLookupService userKeyLookupService;
    @Mock private AuthIdentityReadService authIdentityReadService;

    @Test
    @DisplayName("추천 컨텍스트 조회는 active user 검증 후 user entity 와 snapshot 을 함께 반환한다")
    void getRecommendationContextReturnsUserAndSnapshot() {
        UserReadService userReadService = new UserReadService(
                userAccountReadRepository,
                recommendationUserReadRepository,
                userKeyLookupService,
                authIdentityReadService
        );

        User user = User.builder()
                .id(7L)
                .userKey("user-key-7")
                .notificationPeriod(User.NotificationPeriod.DAILY)
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

        when(userAccountReadRepository.findById(7L)).thenReturn(Optional.of(user));
        when(authIdentityReadService.requireActiveUserKey("user-key-7")).thenReturn("user-key-7");
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
                userAccountReadRepository,
                recommendationUserReadRepository,
                userKeyLookupService,
                authIdentityReadService
        );
        User user = User.builder()
                .id(9L)
                .userKey("user-key-9")
                .build();

        when(userAccountReadRepository.findById(9L)).thenReturn(Optional.of(user));

        UserReadService.ActiveUserContext context = userReadService.getActiveUserContext(9L);

        assertThat(context.user()).isEqualTo(user);
        assertThat(context.userKey()).isEqualTo("user-key-9");
    }

    @Test
    @DisplayName("optional active user by userKey 조회는 탈퇴 사용자를 제외한다")
    void findOptionalActiveUserByUserKeyExcludesWithdrawnUser() {
        UserReadService userReadService = new UserReadService(
                userAccountReadRepository,
                recommendationUserReadRepository,
                userKeyLookupService,
                authIdentityReadService
        );
        User withdrawnUser = User.builder()
                .id(10L)
                .userKey("user-key-10")
                .isActive(false)
                .build();

        when(userAccountReadRepository.findByUserKey("user-key-10")).thenReturn(Optional.of(withdrawnUser));

        assertThat(userReadService.findOptionalActiveUserByUserKey("user-key-10")).isEmpty();
    }

    @Test
    @DisplayName("required active user by userKey 조회는 탈퇴 사용자를 찾지 못한 것으로 처리한다")
    void getActiveUserByUserKeyRejectsWithdrawnUser() {
        UserReadService userReadService = new UserReadService(
                userAccountReadRepository,
                recommendationUserReadRepository,
                userKeyLookupService,
                authIdentityReadService
        );
        User withdrawnUser = User.builder()
                .id(10L)
                .userKey("user-key-10")
                .isActive(false)
                .build();

        when(userAccountReadRepository.findByUserKey("user-key-10")).thenReturn(Optional.of(withdrawnUser));

        assertThatThrownBy(() -> userReadService.getActiveUserByUserKey("user-key-10"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(com.example.welfare.global.exception.ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("existing user id by userKey 조회는 userId를 반환한다")
    void requireExistingUserIdByUserKeyReturnsUserId() {
        UserReadService userReadService = new UserReadService(
                userAccountReadRepository,
                recommendationUserReadRepository,
                userKeyLookupService,
                authIdentityReadService
        );

        when(userKeyLookupService.findRequiredUserId("user-key-11")).thenReturn(Optional.of(11L));

        assertThat(userReadService.requireExistingUserIdByUserKey("user-key-11")).isEqualTo(11L);
    }
}
