package com.example.welfare.user.service;

import com.example.welfare.chat.service.ChatSessionCleanupService;
import com.example.welfare.recommend.service.RecommendationRefreshCacheService;
import com.example.welfare.user.entity.User;
import com.example.welfare.user.repository.UserAttributeRepository;
import com.example.welfare.user.repository.UserPriorityRepository;
import com.example.welfare.user.repository.UserRepository;
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

    @Mock private UserRepository userRepository;
    @Mock private UserAttributeRepository userAttributeRepository;
    @Mock private UserPriorityRepository userPriorityRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private RedisTemplate<String, String> redisTemplate;
    @Mock private AccessTokenRevocationService accessTokenRevocationService;
    @Mock private ChatSessionCleanupService chatSessionCleanupService;
    @Mock private UserCoreSyncService userCoreSyncService;
    @Mock private RecommendationRefreshCacheService recommendationRefreshCacheService;
    @Mock private UserKeyLookupService userKeyLookupService;

    @Test
    @DisplayName("회원탈퇴는 refresh token 삭제와 현재 access token revoke까지 함께 수행한다")
    void withdrawDeletesChatSessionsRefreshTokenAndRevokesAccessToken() {
        UserAccountCommandService service = new UserAccountCommandService(
                userRepository,
                userAttributeRepository,
                userPriorityRepository,
                passwordEncoder,
                redisTemplate,
                accessTokenRevocationService,
                chatSessionCleanupService,
                userCoreSyncService,
                recommendationRefreshCacheService,
                userKeyLookupService
        );
        User user = User.builder()
                .id(1L)
                .userKey("user-key-1")
                .email("user@example.com")
                .passwordHash("encoded-password")
                .name("tester")
                .build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "encoded-password")).thenReturn(true);

        service.withdraw(1L, "password123", "access-token-value");

        verify(recommendationRefreshCacheService).evict("user-key-1");
        verify(userAttributeRepository).deleteByUserKey("user-key-1");
        verify(userPriorityRepository).deleteByUserKey("user-key-1");
        verify(chatSessionCleanupService).deleteAllByUserKey(user.getUserKey());
        verify(redisTemplate).delete("refresh:user-key-1");
        verify(accessTokenRevocationService).revoke("access-token-value");
        verify(userCoreSyncService).syncFromUser(user);
        assertThat(user.isActive()).isFalse();
        assertThat(user.getEmail()).isEqualTo("withdrawn_1");
    }
}
