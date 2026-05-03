package com.example.welfare.user.service;

import com.example.welfare.user.entity.AuthUser;
import com.example.welfare.user.entity.User;
import com.example.welfare.user.entity.UserProfile;
import com.example.welfare.user.repository.AuthUserRepository;
import com.example.welfare.user.repository.UserProfileRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class UserCoreProjectionSyncServiceTest {

    @Mock
    private AuthUserRepository authUserRepository;

    @Mock
    private UserProfileRepository userProfileRepository;

    @Test
    @DisplayName("auth sync는 userKey 기준 auth_user projection을 upsert한다")
    void syncAuthUserUpsertsProjection() {
        User user = User.builder()
                .id(1L)
                .userKey("user-key-1")
                .email("user@example.com")
                .passwordHash("pw-hash")
                .name("Queue User")
                .birthDate(LocalDate.of(1998, 1, 10))
                .build();
        given(authUserRepository.findByUserKey("user-key-1")).willReturn(Optional.empty());

        UserCoreProjectionSyncService service = new UserCoreProjectionSyncService(
                authUserRepository,
                userProfileRepository
        );

        service.syncAuthUser(user, "user-key-1");

        ArgumentCaptor<AuthUser> captor = ArgumentCaptor.forClass(AuthUser.class);
        then(authUserRepository).should().save(captor.capture());
        assertThat(captor.getValue().getUserKey()).isEqualTo("user-key-1");
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("pw-hash");
    }

    @Test
    @DisplayName("profile sync는 userKey 기준 user_profile projection을 upsert한다")
    void syncUserProfileUpsertsProjection() {
        User user = User.builder()
                .id(1L)
                .userKey("user-key-1")
                .email("user@example.com")
                .name("Queue User")
                .birthDate(LocalDate.of(1998, 1, 10))
                .sido("서울특별시")
                .sgg("강남구")
                .phoneEnc("enc-phone")
                .build();
        given(userProfileRepository.findByUserKey("user-key-1")).willReturn(Optional.empty());

        UserCoreProjectionSyncService service = new UserCoreProjectionSyncService(
                authUserRepository,
                userProfileRepository
        );

        service.syncUserProfile(user, "user-key-1", 28, "25_29", LocalDateTime.of(2026, 5, 4, 1, 0));

        ArgumentCaptor<UserProfile> captor = ArgumentCaptor.forClass(UserProfile.class);
        then(userProfileRepository).should().save(captor.capture());
        assertThat(captor.getValue().getUserKey()).isEqualTo("user-key-1");
        assertThat(captor.getValue().getSido()).isEqualTo("서울특별시");
        assertThat(captor.getValue().isHasName()).isTrue();
        assertThat(captor.getValue().isHasBirthDate()).isTrue();
        assertThat(captor.getValue().isHasPhone()).isTrue();
    }
}
