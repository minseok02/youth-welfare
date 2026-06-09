package com.example.welfare.user.service;

import com.example.welfare.recommend.service.RecommendationRefreshCacheService;
import com.example.welfare.user.entity.User;
import com.example.welfare.user.repository.UserMetadataCommandRepository;
import com.example.welfare.global.util.RedisKeyHash;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAccountCommandServiceTest {

    @Mock private ActiveUserReadService activeUserReadService;
    @Mock private UserMetadataCommandRepository userMetadataCommandRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private RedisTemplate<String, String> redisTemplate;
    @Mock private AccessTokenRevocationService accessTokenRevocationService;
    @Mock private UserCoreSyncService userCoreSyncService;
    @Mock private RecommendationRefreshCacheService recommendationRefreshCacheService;
    @Mock private UserWithdrawalDataCleanupService userWithdrawalDataCleanupService;

    @Test
    @DisplayName("회원탈퇴는 refresh token 삭제와 현재 access token revoke까지 함께 수행한다")
    void withdrawDeletesChatSessionsRefreshTokenAndRevokesAccessToken() {
        UserAccountCommandService service = new UserAccountCommandService(
                activeUserReadService,
                userMetadataCommandRepository,
                passwordEncoder,
                redisTemplate,
                accessTokenRevocationService,
                userCoreSyncService,
                recommendationRefreshCacheService,
                userWithdrawalDataCleanupService
        );
        User user = User.builder()
                .id(1L)
                .userKey("user-key-1")
                .email("user@example.com")
                .passwordHash("encoded-password")
                .name("tester")
                .build();
        when(activeUserReadService.getActiveUserContext(1L))
                .thenReturn(new ActiveUserReadService.ActiveUserContext(user, "user-key-1"));
        when(passwordEncoder.matches("password123", "encoded-password")).thenReturn(true);

        service.withdraw(1L, "password123", "access-token-value");

        verify(recommendationRefreshCacheService).evict("user-key-1");
        verify(userMetadataCommandRepository).deleteAllByUserKey("user-key-1");
        verify(userWithdrawalDataCleanupService).cleanupByUserKey("user-key-1");
        verify(redisTemplate).delete(java.util.List.of(
                "refresh:v2:" + RedisKeyHash.sha256Hex("user-key-1"),
                "refresh:user-key-1"
        ));
        verify(accessTokenRevocationService).revoke("access-token-value");
        verify(userCoreSyncService).syncWithdrawnUser(user, "user-key-1");
        assertThat(user.isActive()).isFalse();
        assertThat(user.getEmail()).isEqualTo("withdrawn_1");
    }
}
