package com.example.welfare.user.service;

import com.example.welfare.notification.gateway.EmailClient;
import com.example.welfare.user.util.EmailLookupKeyGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HexFormat;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private EmailClient emailClient;
    @Mock
    private AuthIdentityReadService authIdentityReadService;

    private EmailVerificationService emailVerificationService;

    @BeforeEach
    void setUp() {
        emailVerificationService = new EmailVerificationService(
                redisTemplate,
                emailClient,
                authIdentityReadService
        );
        ReflectionTestUtils.setField(emailVerificationService, "codeTtlMinutes", 5L);
        ReflectionTestUtils.setField(emailVerificationService, "verifiedTtlMinutes", 15L);
        ReflectionTestUtils.setField(emailVerificationService, "cooldownSeconds", 60L);
        ReflectionTestUtils.setField(emailVerificationService, "codeHmacSecret", "test-email-code-secret");
    }

    @Test
    @DisplayName("이미 가입된 이메일 인증코드 발송은 외부 응답을 일반화하되 코드 저장과 메일 발송을 하지 않는다")
    void sendCodeSuppressesExistingAccountEmail() {
        String email = "user@example.com";
        String hash = EmailLookupKeyGenerator.hash(email);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.setIfAbsent("email-verify:cooldown:" + hash, "1", Duration.ofSeconds(60)))
                .willReturn(true);
        given(authIdentityReadService.existsByEmail(email)).willReturn(true);

        emailVerificationService.sendCode(email);

        then(redisTemplate).should().delete("email-verify:code:" + hash);
        then(redisTemplate).should().delete("email-verify:attempts:" + hash);
        then(valueOperations).should(never()).set(eq("email-verify:code:" + hash), anyString(), any(Duration.class));
        then(emailClient).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("이메일 인증코드는 Redis에 원문 대신 HMAC으로 저장한다")
    void sendCodeStoresHmacCodeInsteadOfPlaintextCode() {
        String email = "new@example.com";
        String hash = EmailLookupKeyGenerator.hash(email);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.setIfAbsent("email-verify:cooldown:" + hash, "1", Duration.ofSeconds(60)))
                .willReturn(true);
        given(authIdentityReadService.existsByEmail(email)).willReturn(false);
        given(emailClient.send(eq(email), eq("[청년복지] 이메일 인증코드"), anyString())).willReturn(true);

        emailVerificationService.sendCode(email);

        then(valueOperations).should().set(
                eq("email-verify:code:" + hash),
                argThat(value -> value.matches("hmac:[0-9a-f]{64}")),
                eq(Duration.ofMinutes(5))
        );
    }

    @Test
    @DisplayName("이메일 인증 확인은 HMAC 저장 코드를 검증하고 verified marker를 저장한다")
    void verifyCodeAcceptsHmacStoredCode() {
        String email = "new@example.com";
        String hash = EmailLookupKeyGenerator.hash(email);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("email-verify:code:" + hash))
                .willReturn("hmac:" + hmacSha256(hash + "|123456"));
        given(valueOperations.increment("email-verify:attempts:" + hash)).willReturn(1L);

        emailVerificationService.verifyCode(email, "123456");

        then(redisTemplate).should().delete("email-verify:code:" + hash);
        then(redisTemplate).should().delete("email-verify:attempts:" + hash);
        then(valueOperations).should().set("email-verify:verified:" + hash, "1", Duration.ofMinutes(15));
    }

    @Test
    @DisplayName("이메일 인증 확인은 TTL 안의 legacy 원문 코드도 검증한다")
    void verifyCodeAcceptsLegacyPlainStoredCode() {
        String email = "new@example.com";
        String hash = EmailLookupKeyGenerator.hash(email);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("email-verify:code:" + hash)).willReturn("123456");
        given(valueOperations.increment("email-verify:attempts:" + hash)).willReturn(1L);

        emailVerificationService.verifyCode(email, "123456");

        then(valueOperations).should().set("email-verify:verified:" + hash, "1", Duration.ofMinutes(15));
    }

    private String hmacSha256(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec("test-email-code-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
