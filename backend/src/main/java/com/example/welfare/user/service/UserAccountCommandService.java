package com.example.welfare.user.service;

import com.example.welfare.chat.service.ChatSessionCleanupService;
import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.recommend.service.RecommendationRefreshCacheService;
import com.example.welfare.user.entity.User;
import com.example.welfare.user.repository.UserMetadataCommandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class UserAccountCommandService {

    private static final String REFRESH_TOKEN_PREFIX = "refresh:";

    private final ActiveUserReadService activeUserReadService;
    private final UserMetadataCommandRepository userMetadataCommandRepository;
    private final PasswordEncoder passwordEncoder;
    private final RedisTemplate<String, String> redisTemplate;
    private final AccessTokenRevocationService accessTokenRevocationService;
    private final ChatSessionCleanupService chatSessionCleanupService;
    private final UserCoreSyncService userCoreSyncService;
    private final RecommendationRefreshCacheService recommendationRefreshCacheService;

    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        User user = activeUserReadService.getActiveUserContext(userId).user();
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new CustomException(ErrorCode.INVALID_CREDENTIALS);
        }
        user.updatePassword(passwordEncoder.encode(newPassword));
        userCoreSyncService.syncFromUser(user);
    }

    @Transactional
    public void withdraw(Long userId, String password, String accessToken) {
        ActiveUserReadService.ActiveUserContext activeUserContext = activeUserReadService.getActiveUserContext(userId);
        User user = activeUserContext.user();
        String userKey = activeUserContext.userKey();
        recommendationRefreshCacheService.evict(userKey);

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new CustomException(ErrorCode.INVALID_CREDENTIALS);
        }

        userMetadataCommandRepository.deleteAllByUserKey(userKey);
        chatSessionCleanupService.deleteAllByUserKey(userKey);
        redisTemplate.delete(REFRESH_TOKEN_PREFIX + userKey);
        revokePresentedAccessToken(accessToken);
        user.withdraw();
        userCoreSyncService.syncFromUser(user);
    }

    @Transactional
    public void unsubscribeNotifications(Long userId) {
        User user = activeUserReadService.getActiveUserContext(userId).user();
        user.unsubscribeNotifications();
        userCoreSyncService.syncFromUser(user);
    }

    @Transactional
    public void unsubscribeNotificationsByUserKey(String userKey) {
        User user = activeUserReadService.getActiveUserByUserKey(userKey);
        unsubscribeNotifications(user.getId());
    }

    private void revokePresentedAccessToken(String accessToken) {
        if (!StringUtils.hasText(accessToken)) {
            return;
        }
        accessTokenRevocationService.revoke(accessToken);
    }

}
