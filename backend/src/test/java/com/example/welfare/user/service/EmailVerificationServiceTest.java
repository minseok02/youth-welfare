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

import java.time.Duration;

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
}
