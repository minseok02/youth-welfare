package com.example.welfare.user.service;

import com.example.welfare.chat.service.ChatSessionCleanupService;
import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.recommend.service.RecommendationRefreshCacheService;
import com.example.welfare.user.entity.User;
import com.example.welfare.user.repository.UserAttributeRepository;
import com.example.welfare.user.repository.UserPriorityRepository;
import com.example.welfare.user.repository.UserRepository;
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

    private final UserRepository userRepository;
    private final UserAttributeRepository userAttributeRepository;
    private final UserPriorityRepository userPriorityRepository;
    private final PasswordEncoder passwordEncoder;
    private final RedisTemplate<String, String> redisTemplate;
    private final AccessTokenRevocationService accessTokenRevocationService;
    private final ChatSessionCleanupService chatSessionCleanupService;
    private final UserCoreSyncService userCoreSyncService;
    private final RecommendationRefreshCacheService recommendationRefreshCacheService;

    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        User user = findActiveUser(userId);
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new CustomException(ErrorCode.INVALID_CREDENTIALS);
        }
        user.updatePassword(passwordEncoder.encode(newPassword));
        userCoreSyncService.syncFromUser(user);
    }

    @Transactional
    public void withdraw(Long userId, String password, String accessToken) {
        User user = findActiveUser(userId);
        String userKey = user.getUserKey() != null ? user.getUserKey() : resolveUserKey(userId);
        recommendationRefreshCacheService.evict(userKey);

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new CustomException(ErrorCode.INVALID_CREDENTIALS);
        }

        userAttributeRepository.deleteByUserKey(userKey);
        userPriorityRepository.deleteByUserKey(userKey);
        chatSessionCleanupService.deleteAllByUserKey(userKey);
        redisTemplate.delete(REFRESH_TOKEN_PREFIX + userKey);
        revokePresentedAccessToken(accessToken);
        user.withdraw();
        userCoreSyncService.syncFromUser(user);
    }

    @Transactional
    public void unsubscribeNotifications(Long userId) {
        User user = findActiveUser(userId);
        user.unsubscribeNotifications();
        userCoreSyncService.syncFromUser(user);
    }

    @Transactional
    public void unsubscribeNotificationsByUserKey(String userKey) {
        Long userId = userRepository.findIdByUserKey(userKey)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        unsubscribeNotifications(userId);
    }

    private User findActiveUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        if (!user.isActive()) {
            throw new CustomException(ErrorCode.WITHDRAWN_USER);
        }
        return user;
    }

    private void revokePresentedAccessToken(String accessToken) {
        if (!StringUtils.hasText(accessToken)) {
            return;
        }
        accessTokenRevocationService.revoke(accessToken);
    }

    private String resolveUserKey(Long userId) {
        return userRepository.findUserKeyById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }
}
