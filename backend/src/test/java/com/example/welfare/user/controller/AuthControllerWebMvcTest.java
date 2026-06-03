package com.example.welfare.user.controller;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.global.web.ClientFingerprintService;
import com.example.welfare.user.service.AuthAvailabilityService;
import com.example.welfare.user.service.AuthLoginService;
import com.example.welfare.user.service.AuthRateLimitService;
import com.example.welfare.user.service.AuthSessionService;
import com.example.welfare.user.service.AuthSignupService;
import com.example.welfare.user.service.EmailVerificationService;
import com.example.welfare.user.service.PasswordResetService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthAvailabilityService authAvailabilityService;
    @MockitoBean
    private AuthRateLimitService authRateLimitService;
    @MockitoBean
    private ClientFingerprintService clientFingerprintService;
    @MockitoBean
    private EmailVerificationService emailVerificationService;
    @MockitoBean
    private AuthSignupService authSignupService;
    @MockitoBean
    private AuthLoginService authLoginService;
    @MockitoBean
    private AuthSessionService authSessionService;
    @MockitoBean
    private PasswordResetService passwordResetService;
    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    @DisplayName("이메일 확인은 인증 없이도 계정 존재를 숨긴 응답을 반환한다")
    void checkEmailAvailability() throws Exception {
        given(clientFingerprintService.build(org.mockito.ArgumentMatchers.any())).willReturn("fp-auth");
        given(authAvailabilityService.checkEmailAvailability("new@example.com"))
                .willReturn(new com.example.welfare.user.dto.response.EmailAvailabilityResponse(true));

        mockMvc.perform(get("/api/auth/check-email")
                        .param("email", "new@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.available").value(true));

        then(authAvailabilityService).should().checkEmailAvailability("new@example.com");
        then(authRateLimitService).should().checkEmailCheckLimit("fp-auth");
    }

    @Test
    @DisplayName("이메일 확인은 email 파라미터가 없으면 500 대신 400 invalid input을 반환한다")
    void checkEmailAvailabilityWithoutEmailReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/auth/check-email"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("C001"))
                .andExpect(jsonPath("$.message").value("입력값이 올바르지 않습니다."));

        then(authAvailabilityService).should(never()).checkEmailAvailability(org.mockito.ArgumentMatchers.any());
        then(authRateLimitService).should(never()).checkEmailCheckLimit(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("이메일 확인은 잘못된 이메일 형식을 서비스 호출 전에 거부한다")
    void checkEmailAvailabilityRejectsInvalidEmail() throws Exception {
        mockMvc.perform(get("/api/auth/check-email")
                        .param("email", "not-an-email"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("C001"));

        then(authAvailabilityService).should(never()).checkEmailAvailability(org.mockito.ArgumentMatchers.any());
        then(authRateLimitService).should(never()).checkEmailCheckLimit(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("이메일 인증코드 발송은 fingerprint rate limit을 먼저 확인한다")
    void sendEmailVerificationChecksFingerprintRateLimit() throws Exception {
        given(clientFingerprintService.build(org.mockito.ArgumentMatchers.any())).willReturn("fp-email-send");

        mockMvc.perform(post("/api/auth/email-verification/send")
                        .param("email", "new@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        then(authRateLimitService).should().checkEmailVerificationSendLimit("fp-email-send");
        then(emailVerificationService).should().sendCode("new@example.com");
    }

    @Test
    @DisplayName("이메일 인증코드 발송은 잘못된 이메일 형식을 400으로 거부한다")
    void sendEmailVerificationRejectsInvalidEmail() throws Exception {
        mockMvc.perform(post("/api/auth/email-verification/send")
                        .param("email", "not-an-email"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("C001"));

        then(authRateLimitService).should(never()).checkEmailVerificationSendLimit(org.mockito.ArgumentMatchers.any());
        then(emailVerificationService).should(never()).sendCode(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("이메일 인증코드 발송은 fingerprint rate limit 초과 시 429를 반환한다")
    void sendEmailVerificationReturnsTooManyRequestsWhenFingerprintLimitExceeded() throws Exception {
        given(clientFingerprintService.build(org.mockito.ArgumentMatchers.any())).willReturn("fp-email-send");
        org.mockito.BDDMockito.willThrow(new CustomException(ErrorCode.AUTH_RATE_LIMIT_EXCEEDED))
                .given(authRateLimitService)
                .checkEmailVerificationSendLimit("fp-email-send");

        mockMvc.perform(post("/api/auth/email-verification/send")
                        .param("email", "new@example.com"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("A010"));

        then(emailVerificationService).should(never()).sendCode(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("이메일 인증 확인은 6자리 숫자가 아닌 코드를 서비스 호출 전에 거부한다")
    void verifyEmailCodeRejectsInvalidCodeFormat() throws Exception {
        mockMvc.perform(post("/api/auth/email-verification/verify")
                        .param("email", "new@example.com")
                        .param("code", "12ab"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("C001"));

        then(emailVerificationService).should(never())
                .verifyCode(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("로그아웃은 인증 없이 refresh cookie만으로도 서버 토큰을 무효화한다")
    void logoutUsesRefreshCookieWithoutAuthentication() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access-token-value")
                        .cookie(new jakarta.servlet.http.Cookie("refresh_token", "refresh-token-value")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(header().string(HttpHeaders.SET_COOKIE,
                        org.hamcrest.Matchers.containsString("refresh_token=")));

        then(authSessionService).should().logoutByRefreshToken("refresh-token-value", "access-token-value");
    }

    @Test
    @DisplayName("비밀번호 재설정 요청은 인증 없이도 사용 가능하다")
    void requestPasswordResetWithoutAuthentication() throws Exception {
        given(clientFingerprintService.build(org.mockito.ArgumentMatchers.any())).willReturn("fp-reset");

        mockMvc.perform(post("/api/auth/password-reset/request")
                        .contentType("application/json")
                        .content("""
                                {
                                  "email": "user@example.com"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        then(authRateLimitService).should().checkPasswordResetRequestLimit("fp-reset");
        then(passwordResetService).should().requestPasswordReset("user@example.com");
    }

    @Test
    @DisplayName("비밀번호 재설정 요청은 fingerprint rate limit 초과 시 429를 반환한다")
    void requestPasswordResetReturnsTooManyRequestsWhenFingerprintLimitExceeded() throws Exception {
        given(clientFingerprintService.build(org.mockito.ArgumentMatchers.any())).willReturn("fp-reset");
        org.mockito.BDDMockito.willThrow(new CustomException(ErrorCode.AUTH_RATE_LIMIT_EXCEEDED))
                .given(authRateLimitService)
                .checkPasswordResetRequestLimit("fp-reset");

        mockMvc.perform(post("/api/auth/password-reset/request")
                        .contentType("application/json")
                        .content("""
                                {
                                  "email": "user@example.com"
                                }
                                """))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("A010"));

        then(passwordResetService).should(never()).requestPasswordReset(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("비밀번호 재설정 확인은 토큰과 새 비밀번호를 받아 처리한다")
    void confirmPasswordResetWithoutAuthentication() throws Exception {
        mockMvc.perform(post("/api/auth/password-reset/confirm")
                        .contentType("application/json")
                        .content("""
                                {
                                  "token": "reset-token",
                                  "newPassword": "new-password123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        then(passwordResetService).should().confirmPasswordReset("reset-token", "new-password123");
    }

    @Test
    @DisplayName("로그인은 fingerprint rate limit을 먼저 확인한다")
    void loginChecksFingerprintRateLimit() throws Exception {
        given(clientFingerprintService.build(org.mockito.ArgumentMatchers.any())).willReturn("fp-login");
        given(authLoginService.login(org.mockito.ArgumentMatchers.any()))
                .willReturn(com.example.welfare.user.dto.response.TokenResponse.of("access-token", "refresh-token"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("""
                                {
                                  "email": "user@example.com",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        then(authRateLimitService).should().checkLoginLimit("fp-login");
        then(authLoginService).should().login(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("로그인은 fingerprint rate limit 초과 시 429를 반환한다")
    void loginReturnsTooManyRequestsWhenFingerprintLimitExceeded() throws Exception {
        given(clientFingerprintService.build(org.mockito.ArgumentMatchers.any())).willReturn("fp-login");
        org.mockito.BDDMockito.willThrow(new CustomException(ErrorCode.AUTH_RATE_LIMIT_EXCEEDED))
                .given(authRateLimitService)
                .checkLoginLimit("fp-login");

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("""
                                {
                                  "email": "user@example.com",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("A010"));

        then(authLoginService).should(never()).login(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("회원가입 JSON 파싱 오류는 500 대신 400 invalid input 을 반환한다")
    void signupMalformedJsonReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType("application/json")
                        .content("""
                                {
                                  "email": "bad@example.com",
                                  "password": "password123!",
                                  "nickname": "잘못된필드",
                                  "birthDate": "2001-01-01"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("C001"))
                .andExpect(jsonPath("$.message").value("입력값이 올바르지 않습니다."));

        then(authSignupService).should(never()).signup(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("회원가입은 100자를 넘는 비밀번호를 거부한다")
    void signupRejectsTooLongPassword() throws Exception {
        String longPassword = "a".repeat(101);

        mockMvc.perform(post("/api/auth/signup")
                        .contentType("application/json")
                        .content("""
                                {
                                  "email": "user@example.com",
                                  "password": "%s",
                                  "name": "홍길동",
                                  "birthDate": "2001-01-01"
                                }
                                """.formatted(longPassword)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        then(authSignupService).should(never()).signup(org.mockito.ArgumentMatchers.any());
    }
}
