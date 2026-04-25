package com.example.welfare.user.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.global.util.JwtUtil;
import com.example.welfare.user.dto.request.LoginRequest;
import com.example.welfare.user.dto.request.SignupRequest;
import com.example.welfare.user.dto.response.EmailAvailabilityResponse;
import com.example.welfare.user.dto.response.TokenResponse;
import com.example.welfare.user.entity.User;
import com.example.welfare.user.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final int MAX_LOGIN_FAIL = 5;
    private static final int LOCK_MINUTES = 30;
    private static final String REFRESH_TOKEN_PREFIX = "refresh:";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final RedisTemplate<String, String> redisTemplate;

    @Value("${security.admin-emails:}")
    private String adminEmailsProperty;

    private Set<String> adminEmails = Set.of();

    @PostConstruct
    void initAdminEmails() {
        adminEmails = Arrays.stream(adminEmailsProperty.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(email -> email.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    @Transactional(readOnly = true)
    public EmailAvailabilityResponse checkEmailAvailability(String email) {
        return new EmailAvailabilityResponse(!userRepository.existsByEmail(email));
    }

    @Transactional
    public void signup(SignupRequest request) {
        if (isAdminEmail(request.getEmail())) {
            throw new CustomException(ErrorCode.ADMIN_EMAIL_SIGNUP_FORBIDDEN);
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new CustomException(ErrorCode.DUPLICATE_EMAIL);
        }

        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .name(request.getName())
                .birthDate(request.getBirthDate())
                .sido(request.getSido())
                .sgg(request.getSgg())
                .incomeLevel(request.getIncomeLevel())
                .employmentStatus(request.getEmploymentStatus())
                .householdType(request.getHouseholdType())
                .build();

        userRepository.save(user);
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_CREDENTIALS));

        if (!user.isActive()) {
            throw new CustomException(ErrorCode.WITHDRAWN_USER);
        }

        if (user.isLocked()) {
            throw new CustomException(ErrorCode.ACCOUNT_LOCKED);
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            user.increaseLoginFailCount();
            if (user.getLoginFailCount() >= MAX_LOGIN_FAIL) {
                user.lock(LocalDateTime.now().plusMinutes(LOCK_MINUTES));
                log.warn("Account locked: userId={}", user.getId());
            }
            throw new CustomException(ErrorCode.INVALID_CREDENTIALS);
        }

        user.resetLoginFail();

        String accessToken = jwtUtil.generateAccessToken(user.getId(), resolveRoles(user.getEmail()));
        String refreshToken = jwtUtil.generateRefreshToken(user.getId());

        saveRefreshToken(user.getId(), refreshToken);

        return TokenResponse.of(accessToken, refreshToken);
    }

    @Transactional
    public TokenResponse refresh(String refreshToken) {
        jwtUtil.validate(refreshToken);

        Long userId = jwtUtil.getUserId(refreshToken);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        String key = REFRESH_TOKEN_PREFIX + userId;
        String stored = redisTemplate.opsForValue().get(key);

        // Reuse Detection: 저장된 토큰과 다르면 탈취 가능성 → 전체 무효화
        if (stored == null || !stored.equals(refreshToken)) {
            redisTemplate.delete(key);
            throw new CustomException(ErrorCode.REUSED_REFRESH_TOKEN);
        }

        String newAccessToken = jwtUtil.generateAccessToken(userId, resolveRoles(user.getEmail()));
        String newRefreshToken = jwtUtil.generateRefreshToken(userId);

        // Rotation: 새 Refresh Token으로 교체
        saveRefreshToken(userId, newRefreshToken);

        return TokenResponse.of(newAccessToken, newRefreshToken);
    }

    public void logout(Long userId) {
        redisTemplate.delete(REFRESH_TOKEN_PREFIX + userId);
    }

    public void logoutByRefreshToken(String refreshToken) {
        Long userId = jwtUtil.getUserIdAllowExpired(refreshToken);
        redisTemplate.delete(REFRESH_TOKEN_PREFIX + userId);
    }

    private void saveRefreshToken(Long userId, String refreshToken) {
        redisTemplate.opsForValue().set(
                REFRESH_TOKEN_PREFIX + userId,
                refreshToken,
                7,
                TimeUnit.DAYS
        );
    }

    private boolean isAdminEmail(String email) {
        if (!StringUtils.hasText(email)) {
            return false;
        }
        return adminEmails.contains(email.trim().toLowerCase(Locale.ROOT));
    }

    private List<String> resolveRoles(String email) {
        if (isAdminEmail(email)) {
            return List.of("ROLE_USER", "ROLE_ADMIN");
        }
        return List.of("ROLE_USER");
    }
}
