package com.example.welfare.user.service;

import com.example.welfare.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.mockito.BDDMockito.then;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;

@ExtendWith(MockitoExtension.class)
class UserCoreProjectionSyncServiceTest {

    @Mock
    private com.example.welfare.user.repository.UserCoreProjectionCommandRepository userCoreProjectionCommandRepository;

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
        UserCoreProjectionSyncService service = new UserCoreProjectionSyncService(
                userCoreProjectionCommandRepository
        );

        service.syncAuthUser(user, "user-key-1");

        then(userCoreProjectionCommandRepository).should()
                .upsertAuthProjection(
                        same(user),
                        eq("user-key-1"),
                        eq("b4c9a289323b21a01c3e940f150eb9b8c542587f1abfd8f0e1cc1ffc5e475514")
                );
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
        UserCoreProjectionSyncService service = new UserCoreProjectionSyncService(
                userCoreProjectionCommandRepository
        );

        service.syncUserProfile(user, "user-key-1", 28, "25_29", LocalDateTime.of(2026, 5, 4, 1, 0));

        then(userCoreProjectionCommandRepository).should()
                .upsertUserProfileProjection(
                        same(user),
                        eq("user-key-1"),
                        eq(28),
                        eq("25_29"),
                        eq(LocalDateTime.of(2026, 5, 4, 1, 0))
                );
    }
}
