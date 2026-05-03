package com.example.welfare.user.service;

import com.example.welfare.chat.service.ChatSessionCleanupService;
import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.global.util.JwtUtil;
import com.example.welfare.user.dto.response.TokenResponse;
import com.example.welfare.user.entity.User;
import com.example.welfare.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthTokenService {

    private static final String REFRESH_TOKEN_PREFIX = "refresh:";

    private final JwtUtil jwtUtil;
    private final RedisTemplate<String, String> redisTemplate;
    private final UserRepository userRepository;
    private final AccessTokenRevocationService accessTokenRevocationService;
    private final ChatSessionCleanupService chatSessionCleanupService;

    public TokenResponse issueTokens(String userKey, Long userId, List<String> roles) {
        String accessToken = jwtUtil.generateAccessToken(userKey, userId, roles);
        String refreshToken = jwtUtil.generateRefreshToken(userKey, userId);
        saveRefreshToken(userKey, refreshToken);
        return TokenResponse.of(accessToken, refreshToken);
    }

    @Transactional
    public TokenResponse refresh(String refreshToken, Function<String, List<String>> rolesResolver) {
        jwtUtil.validate(refreshToken);

        String userKey = resolveTokenUserKey(refreshToken);
        Long userId = jwtUtil.getUserId(refreshToken);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        if (!user.isActive()) {
            invalidateRefreshToken(userKey);
            throw new CustomException(ErrorCode.WITHDRAWN_USER);
        }

        String stored = redisTemplate.opsForValue().get(refreshTokenKey(userKey));
        if (stored == null || !stored.equals(refreshToken)) {
            redisTemplate.delete(refreshTokenKey(userKey));
            throw new CustomException(ErrorCode.REUSED_REFRESH_TOKEN);
        }

        return issueTokens(userKey, userId, rolesResolver.apply(user.getEmail()));
    }

    @Transactional
    public void logout(Long userId, String accessToken) {
        String userKey = userRepository.findUserKeyById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        logoutByUserKey(userKey, accessToken);
    }

    @Transactional
    public void logoutByUserKey(String userKey, String accessToken) {
        invalidateRefreshToken(userKey);
        revokePresentedAccessToken(accessToken);
        chatSessionCleanupService.deleteAllByUserKey(userKey);
    }

    @Transactional
    public void logoutByRefreshToken(String refreshToken, String accessToken) {
        String userKey = resolveTokenUserKeyAllowExpired(refreshToken);
        invalidateRefreshToken(userKey);
        revokePresentedAccessToken(accessToken);
        chatSessionCleanupService.deleteAllByUserKey(userKey);
    }

    public void invalidateRefreshToken(String userKey) {
        redisTemplate.delete(refreshTokenKey(userKey));
    }

    private void saveRefreshToken(String userKey, String refreshToken) {
        redisTemplate.opsForValue().set(refreshTokenKey(userKey), refreshToken, 7, TimeUnit.DAYS);
    }

    private void revokePresentedAccessToken(String accessToken) {
        if (!StringUtils.hasText(accessToken)) {
            return;
        }
        try {
            accessTokenRevocationService.revoke(accessToken);
        } catch (CustomException e) {
            log.debug("Skipping access-token revocation during logout because presented token was invalid");
        }
    }

    private String resolveTokenUserKey(String token) {
        return requireUserKeySubject(jwtUtil.getSubject(token));
    }

    private String resolveTokenUserKeyAllowExpired(String token) {
        return requireUserKeySubject(jwtUtil.getSubjectAllowExpired(token));
    }

    private String requireUserKeySubject(String subject) {
        if (!StringUtils.hasText(subject)) {
            throw new CustomException(ErrorCode.INVALID_TOKEN);
        }
        if (subject.chars().allMatch(Character::isDigit)) {
            throw new CustomException(ErrorCode.INVALID_TOKEN);
        }
        return subject;
    }

    private String refreshTokenKey(String userKey) {
        return REFRESH_TOKEN_PREFIX + userKey;
    }
}
