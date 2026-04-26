package com.example.welfare.user.controller;

import com.example.welfare.user.service.AuthService;
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
    private AuthService authService;
    @MockBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    @DisplayName("이메일 중복확인은 인증 없이도 사용 가능 여부를 반환한다")
    void checkEmailAvailability() throws Exception {
        given(authService.checkEmailAvailability("new@example.com"))
                .willReturn(new com.example.welfare.user.dto.response.EmailAvailabilityResponse(true));

        mockMvc.perform(get("/api/auth/check-email")
                        .param("email", "new@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.available").value(true));

        then(authService).should().checkEmailAvailability("new@example.com");
    }

    @Test
    @DisplayName("로그아웃은 인증 없이 refresh cookie만으로도 서버 토큰을 무효화한다")
    void logoutUsesRefreshCookieWithoutAuthentication() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .cookie(new jakarta.servlet.http.Cookie("refresh_token", "refresh-token-value")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(header().string(HttpHeaders.SET_COOKIE,
                        org.hamcrest.Matchers.containsString("refresh_token=")));

        then(authService).should().logoutByRefreshToken("refresh-token-value");
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

        then(authService).should().requestPasswordReset("user@example.com");
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

        then(authService).should().confirmPasswordReset("reset-token", "new-password123");
    }
}
