package com.example.welfare.user.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.notification.gateway.EmailClient;
import com.example.welfare.user.util.EmailLookupKeyGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private static final String CODE_PREFIX      = "email-verify:code:";
    private static final String ATTEMPTS_PREFIX  = "email-verify:attempts:";
    private static final String VERIFIED_PREFIX  = "email-verify:verified:";
    private static final String COOLDOWN_PREFIX  = "email-verify:cooldown:";
    private static final int    MAX_ATTEMPTS     = 5;
    private static final String SUBJECT          = "[청년복지] 이메일 인증코드";

    private final RedisTemplate<String, String> redisTemplate;
    private final EmailClient emailClient;
    private final AuthIdentityReadService authIdentityReadService;

    @Value("${auth.email-verification.code-ttl-minutes:5}")
    private long codeTtlMinutes;

    @Value("${auth.email-verification.verified-ttl-minutes:15}")
    private long verifiedTtlMinutes;

    @Value("${auth.email-verification.cooldown-seconds:60}")
    private long cooldownSeconds;

    @Value("${auth.email-verification.code-hmac-secret:${jwt.secret}}")
    private String codeHmacSecret;

    private final SecureRandom random = new SecureRandom();

    public void sendCode(String rawEmail) {
        String hash = EmailLookupKeyGenerator.hash(rawEmail);

        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                COOLDOWN_PREFIX + hash, "1", Duration.ofSeconds(cooldownSeconds));
        if (!Boolean.TRUE.equals(acquired)) {
            throw new CustomException(ErrorCode.EMAIL_VERIFICATION_SEND_LIMIT);
        }

        if (authIdentityReadService.existsByEmail(rawEmail)) {
            redisTemplate.delete(CODE_PREFIX + hash);
            redisTemplate.delete(ATTEMPTS_PREFIX + hash);
            return;
        }

        String code = String.format("%06d", random.nextInt(1_000_000));
        redisTemplate.opsForValue().set(CODE_PREFIX + hash, codeStorageValue(hash, code), Duration.ofMinutes(codeTtlMinutes));
        redisTemplate.delete(ATTEMPTS_PREFIX + hash);

        boolean sent = emailClient.send(rawEmail, SUBJECT, buildBody(code));
        if (!sent) {
            redisTemplate.delete(CODE_PREFIX + hash);
            redisTemplate.delete(COOLDOWN_PREFIX + hash);
            throw new CustomException(ErrorCode.PASSWORD_RESET_EMAIL_SEND_FAILED);
        }
    }

    public void verifyCode(String rawEmail, String inputCode) {
        String hash = EmailLookupKeyGenerator.hash(rawEmail);
        String storedCode = redisTemplate.opsForValue().get(CODE_PREFIX + hash);

        if (storedCode == null) {
            throw new CustomException(ErrorCode.EMAIL_VERIFICATION_CODE_INVALID);
        }

        Long attempts = redisTemplate.opsForValue().increment(ATTEMPTS_PREFIX + hash);
        if (attempts != null && attempts == 1L) {
            redisTemplate.expire(ATTEMPTS_PREFIX + hash, Duration.ofMinutes(codeTtlMinutes));
        }

        if (attempts != null && attempts > MAX_ATTEMPTS) {
            redisTemplate.delete(CODE_PREFIX + hash);
            redisTemplate.delete(ATTEMPTS_PREFIX + hash);
            throw new CustomException(ErrorCode.EMAIL_VERIFICATION_CODE_INVALID);
        }

        if (!matchesCode(hash, storedCode, inputCode)) {
            throw new CustomException(ErrorCode.EMAIL_VERIFICATION_CODE_INVALID);
        }

        redisTemplate.delete(CODE_PREFIX + hash);
        redisTemplate.delete(ATTEMPTS_PREFIX + hash);
        redisTemplate.opsForValue().set(VERIFIED_PREFIX + hash, "1", Duration.ofMinutes(verifiedTtlMinutes));
    }

    public boolean isVerified(String rawEmail) {
        String hash = EmailLookupKeyGenerator.hash(rawEmail);
        return "1".equals(redisTemplate.opsForValue().get(VERIFIED_PREFIX + hash));
    }

    public void clearVerified(String rawEmail) {
        redisTemplate.delete(VERIFIED_PREFIX + EmailLookupKeyGenerator.hash(rawEmail));
    }

    private String buildBody(String code) {
        return """
                이메일 인증코드입니다.

                인증코드: %s

                이 코드는 %d분 동안 유효합니다.
                본인이 요청하지 않았다면 이 메일을 무시하세요.
                """.formatted(code, codeTtlMinutes);
    }

    private String codeStorageValue(String emailHash, String code) {
        return "hmac:" + hmacSha256(emailHash + "|" + code);
    }

    private boolean matchesCode(String emailHash, String storedCode, String inputCode) {
        String expected = codeStorageValue(emailHash, inputCode);
        if (constantTimeEquals(storedCode, expected)) {
            return true;
        }
        return constantTimeEquals(storedCode, inputCode);
    }

    private boolean constantTimeEquals(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return MessageDigest.isEqual(
                left.getBytes(StandardCharsets.UTF_8),
                right.getBytes(StandardCharsets.UTF_8)
        );
    }

    private String hmacSha256(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(codeHmacSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("failed to hash email verification code", e);
        }
    }
}
