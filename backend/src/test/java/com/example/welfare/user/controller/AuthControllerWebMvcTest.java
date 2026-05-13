package com.example.welfare.user.controller;

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
import org.springframework.boot.test.mock.mockito.MockBean;
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

    @MockBean
    private AuthAvailabilityService authAvailabilityService;
    @MockBean
    private AuthRateLimitService authRateLimitService;
    @MockBean
    private ClientFingerprintService clientFingerprintService;
    @MockBean
    private EmailVerificationService emailVerificationService;
    @MockBean
    private AuthSignupService authSignupService;
    @MockBean
    private AuthLoginService authLoginService;
    @MockBean
    private AuthSessionService authSessionService;
    @MockBean
    private PasswordResetService passwordResetService;
    @MockBean
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
        mockMvc.perform(post("/api/auth/password-reset/request")
                        .contentType("application/json")
                        .content("""
                                {
                                  "email": "user@example.com"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        then(passwordResetService).should().requestPasswordReset("user@example.com");
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
