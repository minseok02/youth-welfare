package com.example.welfare.notification.controller;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.global.util.JwtUtil;
import com.example.welfare.notification.service.NotificationUnsubscribeTokenService;
import com.example.welfare.notification.service.DeadlineReminderDispatchService;
import com.example.welfare.notification.service.NotificationDispatchService;
import com.example.welfare.notification.service.UserAlertCommandService;
import com.example.welfare.notification.service.UserAlertReadService;
import com.example.welfare.notification.service.WebPushDispatchService;
import com.example.welfare.notification.service.WebPushSubscriptionCommandService;
import com.example.welfare.notification.service.WebPushSubscriptionReadService;
import com.example.welfare.user.service.ActiveUserReadService;
import com.example.welfare.user.service.UserAccountCommandService;
import com.example.welfare.user.service.UserNotificationReadService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
@AutoConfigureMockMvc(addFilters = false)
class NotificationControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtUtil jwtUtil;
    @MockitoBean
    private NotificationUnsubscribeTokenService notificationUnsubscribeTokenService;
    @MockitoBean
    private UserAccountCommandService userAccountCommandService;
    @MockitoBean
    private UserAlertReadService userAlertReadService;
    @MockitoBean
    private UserAlertCommandService userAlertCommandService;
    @MockitoBean
    private NotificationDispatchService notificationDispatchService;
    @MockitoBean
    private DeadlineReminderDispatchService deadlineReminderDispatchService;
    @MockitoBean
    private WebPushSubscriptionReadService webPushSubscriptionReadService;
    @MockitoBean
    private WebPushSubscriptionCommandService webPushSubscriptionCommandService;
    @MockitoBean
    private WebPushDispatchService webPushDispatchService;
    @MockitoBean
    private ActiveUserReadService activeUserReadService;
    @MockitoBean
    private UserNotificationReadService userNotificationReadService;
    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    @DisplayName("POST 수신 거부 토큰으로 알림 설정을 해제한다")
    void unsubscribeByToken() throws Exception {
        given(notificationUnsubscribeTokenService.consumeUserKey("unsubscribe-token"))
                .willReturn(java.util.Optional.of("user-key-7"));

        mockMvc.perform(post("/api/notifications/unsubscribe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "token": "unsubscribe-token"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        then(notificationUnsubscribeTokenService).should().consumeUserKey("unsubscribe-token");
        then(userAccountCommandService).should().unsubscribeNotificationsByUserKey("user-key-7");
    }

    @Test
    @DisplayName("legacy GET 수신 거부 링크도 기존 메일 호환을 위해 허용한다")
    void unsubscribeByLegacyGetToken() throws Exception {
        given(notificationUnsubscribeTokenService.consumeUserKey("unsubscribe-token"))
                .willReturn(java.util.Optional.of("user-key-7"));

        mockMvc.perform(get("/api/notifications/unsubscribe")
                        .param("token", "unsubscribe-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        then(notificationUnsubscribeTokenService).should().consumeUserKey("unsubscribe-token");
        then(userAccountCommandService).should().unsubscribeNotificationsByUserKey("user-key-7");
    }

    @Test
    @DisplayName("opaque token이 없으면 기존 JWT unsubscribe token fallback을 허용한다")
    void unsubscribeFallsBackToLegacyJwtToken() throws Exception {
        given(notificationUnsubscribeTokenService.consumeUserKey("legacy-jwt-token"))
                .willReturn(java.util.Optional.empty());
        given(jwtUtil.getSubject("legacy-jwt-token")).willReturn("user-key-9");

        mockMvc.perform(post("/api/notifications/unsubscribe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "token": "legacy-jwt-token"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        then(jwtUtil).should().validate("legacy-jwt-token");
        then(jwtUtil).should().getSubject("legacy-jwt-token");
        then(userAccountCommandService).should().unsubscribeNotificationsByUserKey("user-key-9");
    }

    @Test
    @DisplayName("만료된 수신 거부 토큰이면 401 A002를 반환한다")
    void unsubscribeByExpiredTokenReturnsUnauthorized() throws Exception {
        willThrow(new CustomException(ErrorCode.EXPIRED_TOKEN))
                .given(jwtUtil)
                .validate("expired-token");

        mockMvc.perform(post("/api/notifications/unsubscribe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "token": "expired-token"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("A002"));

        then(userAccountCommandService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("유효하지 않은 수신 거부 토큰이면 401 A001을 반환한다")
    void unsubscribeByInvalidTokenReturnsUnauthorized() throws Exception {
        willThrow(new CustomException(ErrorCode.INVALID_TOKEN))
                .given(jwtUtil)
                .validate("invalid-token");

        mockMvc.perform(post("/api/notifications/unsubscribe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "token": "invalid-token"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("A001"));

        then(userAccountCommandService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("푸시 테스트 발송은 과도하게 긴 제목을 서비스 호출 전에 거부한다")
    void pushTestSendRejectsTooLongTitle() throws Exception {
        mockMvc.perform(post("/api/notifications/push-test-send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "%s",
                                  "body": "body",
                                  "url": "/mypage?tab=3"
                                }
                                """.formatted("t".repeat(101))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("C001"));

        then(webPushDispatchService).should(never())
                .sendTestMessage(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("마감 테스트 발송은 허용 범위를 벗어난 일수를 서비스 호출 전에 거부한다")
    void deadlineTestDispatchRejectsOutOfRangeDays() throws Exception {
        mockMvc.perform(post("/api/notifications/deadline-test-dispatch")
                        .param("days", "365"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("C001"));

        then(deadlineReminderDispatchService).shouldHaveNoInteractions();
    }
}
