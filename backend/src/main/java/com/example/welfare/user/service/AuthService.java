package com.example.welfare.user.service;

import com.example.welfare.chat.service.ChatSessionCleanupService;
import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.global.util.AesEncryptUtil;
import com.example.welfare.global.util.JwtUtil;
import com.example.welfare.notification.gateway.EmailClient;
import com.example.welfare.user.dto.request.LoginRequest;
import com.example.welfare.user.dto.request.SignupRequest;
import com.example.welfare.user.dto.response.EmailAvailabilityResponse;
import com.example.welfare.user.dto.response.TokenResponse;
import com.example.welfare.user.entity.AuthUser;
import com.example.welfare.user.entity.User;
import com.example.welfare.user.entity.UserPii;
import com.example.welfare.user.repository.AuthUserRepository;
import com.example.welfare.user.repository.UserPiiRepository;
import com.example.welfare.user.repository.UserRepository;
import com.example.welfare.user.util.EmailLookupKeyGenerator;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final int MAX_LOGIN_FAIL = 5;
    private static final int LOCK_MINUTES = 30;
    private static final String REFRESH_TOKEN_PREFIX = "refresh:";
    private static final String PASSWORD_RESET_TOKEN_PREFIX = "password-reset:";
    private static final String PASSWORD_RESET_USER_PREFIX = "password-reset:user:";
    private static final String PASSWORD_RESET_SUBJECT = "[청년복지] 비밀번호 재설정 안내";

    private final AuthUserRepository authUserRepository;
    private final UserRepository userRepository;
    private final UserPiiRepository userPiiRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final RedisTemplate<String, String> redisTemplate;
    private final ChatSessionCleanupService chatSessionCleanupService;
    private final EmailClient emailClient;
    private final UserCoreSyncService userCoreSyncService;
    private final AesEncryptUtil aesEncryptUtil;

    @Value("${security.admin-emails:}")
    private String adminEmailsProperty;

    @Value("${auth.password-reset.expiration-minutes:30}")
    private long passwordResetExpirationMinutes;

    @Value("${app.base-url:http://localhost:5173}")
    private String appBaseUrl;

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
        return new EmailAvailabilityResponse(!authUserRepository.existsByEmailLookupHash(EmailLookupKeyGenerator.hash(email)));
    }

    @Transactional
    public void signup(SignupRequest request) {
        if (isAdminEmail(request.getEmail())) {
            throw new CustomException(ErrorCode.ADMIN_EMAIL_SIGNUP_FORBIDDEN);
        }

        if (authUserRepository.existsByEmailLookupHash(EmailLookupKeyGenerator.hash(request.getEmail()))) {
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
        userCoreSyncService.syncFromUser(user);
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        String normalizedEmail = EmailLookupKeyGenerator.normalize(request.getEmail());
        AuthUser authUser = authUserRepository.findByEmailLookupHash(EmailLookupKeyGenerator.hash(normalizedEmail))
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_CREDENTIALS));
        User user = userRepository.findByUserKey(authUser.getUserKey())
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_CREDENTIALS));

        if (!authUser.isActive()) {
            throw new CustomException(ErrorCode.WITHDRAWN_USER);
        }

        if (authUser.getLockedUntil() != null && LocalDateTime.now().isBefore(authUser.getLockedUntil())) {
            throw new CustomException(ErrorCode.ACCOUNT_LOCKED);
        }

        if (!passwordEncoder.matches(request.getPassword(), authUser.getPasswordHash())) {
            user.increaseLoginFailCount();
            if (user.getLoginFailCount() >= MAX_LOGIN_FAIL) {
                user.lock(LocalDateTime.now().plusMinutes(LOCK_MINUTES));
                log.warn("Account locked: userId={}", user.getId());
            }
            userCoreSyncService.syncFromUser(user);
            throw new CustomException(ErrorCode.INVALID_CREDENTIALS);
        }

        user.resetLoginFail();
        userCoreSyncService.syncFromUser(user);

        String accessToken = jwtUtil.generateAccessToken(user.getId(), resolveRoles(normalizedEmail));
        String refreshToken = jwtUtil.generateRefreshToken(user.getId());

        saveRefreshToken(user.getId(), refreshToken);

        return TokenResponse.of(accessToken, refreshToken);
    }

    @Transactional
    public void requestPasswordReset(String rawEmail) {
        String email = EmailLookupKeyGenerator.normalize(rawEmail);
        authUserRepository.findByEmailLookupHash(EmailLookupKeyGenerator.hash(email))
                .filter(AuthUser::isActive)
                .flatMap(authUser -> userRepository.findByUserKey(authUser.getUserKey())
                        .map(user -> new PasswordResetTarget(user, authUser.getUserKey())))
                .ifPresent(user -> {
                    String recipientEmail = resolvePasswordResetRecipient(user.userKey());
                    String token = UUID.randomUUID().toString();
                    savePasswordResetToken(user.user().getId(), token);

                    boolean sent = emailClient.send(
                            recipientEmail,
                            PASSWORD_RESET_SUBJECT,
                            buildPasswordResetText(token)
                    );
                    if (!sent) {
                        clearPasswordResetToken(user.user().getId(), token);
                        throw new CustomException(ErrorCode.PASSWORD_RESET_EMAIL_SEND_FAILED);
                    }
                });
    }

    @Transactional
    public void confirmPasswordReset(String token, String newPassword) {
        String resetToken = token == null ? "" : token.trim();
        if (!StringUtils.hasText(resetToken)) {
            throw new CustomException(ErrorCode.PASSWORD_RESET_TOKEN_INVALID);
        }

        String userIdValue = redisTemplate.opsForValue().get(passwordResetTokenKey(resetToken));
        if (!StringUtils.hasText(userIdValue)) {
            throw new CustomException(ErrorCode.PASSWORD_RESET_TOKEN_INVALID);
        }

        Long userId = Long.parseLong(userIdValue);
        User user = userRepository.findById(userId)
                .filter(User::isActive)
                .orElseThrow(() -> new CustomException(ErrorCode.PASSWORD_RESET_TOKEN_INVALID));

        String latestToken = redisTemplate.opsForValue().get(passwordResetUserKey(userId));
        if (!resetToken.equals(latestToken)) {
            throw new CustomException(ErrorCode.PASSWORD_RESET_TOKEN_INVALID);
        }

        user.updatePassword(passwordEncoder.encode(newPassword));
        user.resetLoginFail();
        userCoreSyncService.syncFromUser(user);
        clearPasswordResetToken(userId, resetToken);
        redisTemplate.delete(REFRESH_TOKEN_PREFIX + userId);
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

    @Transactional
    public void logout(Long userId) {
        redisTemplate.delete(REFRESH_TOKEN_PREFIX + userId);
        chatSessionCleanupService.deleteAllByUserId(userId);
    }

    @Transactional
    public void logoutByRefreshToken(String refreshToken) {
        Long userId = jwtUtil.getUserIdAllowExpired(refreshToken);
        redisTemplate.delete(REFRESH_TOKEN_PREFIX + userId);
        chatSessionCleanupService.deleteAllByUserId(userId);
    }

    private void saveRefreshToken(Long userId, String refreshToken) {
        redisTemplate.opsForValue().set(
                REFRESH_TOKEN_PREFIX + userId,
                refreshToken,
                7,
                TimeUnit.DAYS
        );
    }

    private void savePasswordResetToken(Long userId, String token) {
        String previousToken = redisTemplate.opsForValue().get(passwordResetUserKey(userId));
        if (StringUtils.hasText(previousToken)) {
            redisTemplate.delete(passwordResetTokenKey(previousToken));
        }

        redisTemplate.opsForValue().set(
                passwordResetTokenKey(token),
                String.valueOf(userId),
                passwordResetExpirationMinutes,
                TimeUnit.MINUTES
        );
        redisTemplate.opsForValue().set(
                passwordResetUserKey(userId),
                token,
                passwordResetExpirationMinutes,
                TimeUnit.MINUTES
        );
    }

    private void clearPasswordResetToken(Long userId, String token) {
        redisTemplate.delete(passwordResetTokenKey(token));
        redisTemplate.delete(passwordResetUserKey(userId));
    }

    private boolean isAdminEmail(String email) {
        if (!StringUtils.hasText(email)) {
            return false;
        }
        return adminEmails.contains(EmailLookupKeyGenerator.normalize(email));
    }

    private String resolvePasswordResetRecipient(String userKey) {
        UserPii userPii = userPiiRepository.findByUserKey(userKey)
                .orElseThrow(() -> new CustomException(ErrorCode.PASSWORD_RESET_EMAIL_SEND_FAILED));
        String recipientEmail = aesEncryptUtil.decrypt(userPii.getEmailEnc());
        if (!StringUtils.hasText(recipientEmail)) {
            throw new CustomException(ErrorCode.PASSWORD_RESET_EMAIL_SEND_FAILED);
        }
        return recipientEmail;
    }

    private String buildPasswordResetText(String token) {
        String encodedToken = URLEncoder.encode(token, StandardCharsets.UTF_8);
        String resetUrl = appBaseUrl + "/reset-password?token=" + encodedToken;
        return """
                비밀번호 재설정을 요청하셨다면 아래 링크에서 새 비밀번호를 설정해주세요.

                %s

                이 링크는 %d분 동안만 유효합니다.
                본인이 요청하지 않았다면 이 메일을 무시하셔도 됩니다.
                """.formatted(resetUrl, passwordResetExpirationMinutes);
    }

    private String passwordResetTokenKey(String token) {
        return PASSWORD_RESET_TOKEN_PREFIX + token;
    }

    private String passwordResetUserKey(Long userId) {
        return PASSWORD_RESET_USER_PREFIX + userId;
    }

    private List<String> resolveRoles(String email) {
        if (isAdminEmail(email)) {
            return List.of("ROLE_USER", "ROLE_ADMIN");
        }
        return List.of("ROLE_USER");
    }

    private record PasswordResetTarget(User user, String userKey) {
    }
}
