package com.example.welfare.user.repository;

import com.example.welfare.user.entity.AuthUser;
import com.example.welfare.user.entity.User;
import com.example.welfare.user.entity.UserProfile;
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
class UserCoreProjectionCommandRepositoryImplTest {

    @Mock
    private AuthUserRepository authUserRepository;

    @Mock
    private UserProfileRepository userProfileRepository;

    @Test
    @DisplayName("auth projection upsert는 auth_user row를 생성하거나 갱신한다")
    void upsertAuthProjection() {
        User user = User.builder()
                .id(1L)
                .userKey("user-key-1")
                .email("user@example.com")
                .passwordHash("pw-hash")
                .name("Queue User")
                .birthDate(LocalDate.of(1998, 1, 10))
                .build();
        given(authUserRepository.findByUserKey("user-key-1")).willReturn(Optional.empty());

        UserCoreProjectionCommandRepositoryImpl repository = new UserCoreProjectionCommandRepositoryImpl(
                authUserRepository,
                userProfileRepository
        );

        repository.upsertAuthProjection(
                user,
                "user-key-1",
                "b4c9a289323b21a01c3e940f150eb9b8c542587f1abfd8f0e1cc1ffc5e475514"
        );

        ArgumentCaptor<AuthUser> captor = ArgumentCaptor.forClass(AuthUser.class);
        then(authUserRepository).should().save(captor.capture());
        assertThat(captor.getValue().getUserKey()).isEqualTo("user-key-1");
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("pw-hash");
    }

    @Test
    @DisplayName("user profile projection upsert는 user_profile row를 생성하거나 갱신한다")
    void upsertUserProfileProjection() {
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

        UserCoreProjectionCommandRepositoryImpl repository = new UserCoreProjectionCommandRepositoryImpl(
                authUserRepository,
                userProfileRepository
        );

        repository.upsertUserProfileProjection(
                user,
                "user-key-1",
                28,
                "25_29",
                LocalDateTime.of(2026, 5, 4, 1, 0),
                true,
                true
        );

        ArgumentCaptor<UserProfile> captor = ArgumentCaptor.forClass(UserProfile.class);
        then(userProfileRepository).should().save(captor.capture());
        assertThat(captor.getValue().getUserKey()).isEqualTo("user-key-1");
        assertThat(captor.getValue().getSido()).isEqualTo("서울특별시");
        assertThat(captor.getValue().isHasName()).isTrue();
        assertThat(captor.getValue().isHasBirthDate()).isTrue();
        assertThat(captor.getValue().isHasPhone()).isTrue();
    }
}
