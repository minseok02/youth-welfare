package com.example.welfare.notification.controller;

import com.example.welfare.global.auth.AuthenticatedUser;
import com.example.welfare.global.config.JacksonConfig;
import com.example.welfare.global.config.SecurityConfig;
import com.example.welfare.global.util.JwtUtil;
import com.example.welfare.notification.dto.WebPushTestSendResponse;
import com.example.welfare.notification.service.DeadlineReminderDispatchService;
import com.example.welfare.notification.service.NotificationDispatchService;
import com.example.welfare.notification.service.NotificationUnsubscribeTokenService;
import com.example.welfare.notification.service.UserAlertCommandService;
import com.example.welfare.notification.service.UserAlertReadService;
import com.example.welfare.notification.service.WebPushDispatchService;
import com.example.welfare.notification.service.WebPushSubscriptionCommandService;
import com.example.welfare.notification.service.WebPushSubscriptionReadService;
import com.example.welfare.user.service.ActiveUserReadService;
import com.example.welfare.user.service.UserAccountCommandService;
import com.example.welfare.user.service.UserNotificationReadService;
import com.example.welfare.user.service.UserSessionRevocationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
@Import({SecurityConfig.class, JacksonConfig.class})
class NotificationSecurityWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtUtil jwtUtil;
    @MockBean
    private UserSessionRevocationService userSessionRevocationService;
    @MockBean
    private NotificationUnsubscribeTokenService notificationUnsubscribeTokenService;
    @MockBean
    private UserAccountCommandService userAccountCommandService;
    @MockBean
    private UserAlertReadService userAlertReadService;
    @MockBean
    private UserAlertCommandService userAlertCommandService;
    @MockBean
    private NotificationDispatchService notificationDispatchService;
    @MockBean
    private DeadlineReminderDispatchService deadlineReminderDispatchService;
    @MockBean
    private WebPushSubscriptionReadService webPushSubscriptionReadService;
    @MockBean
    private WebPushSubscriptionCommandService webPushSubscriptionCommandService;
    @MockBean
    private WebPushDispatchService webPushDispatchService;
    @MockBean
    private ActiveUserReadService activeUserReadService;
    @MockBean
    private UserNotificationReadService userNotificationReadService;
    @MockBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    @DisplayName("일반 사용자 토큰으로 notification test dispatch를 호출하면 403을 반환한다")
    void notificationTestDispatchRejectsNonAdminUser() throws Exception {
        mockAuthenticatedToken("user-token", List.of(new SimpleGrantedAuthority("ROLE_USER")));

        mockMvc.perform(post("/api/notifications/digest-test-dispatch")
                        .header("Authorization", "Bearer user-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("C003"));
    }

    @Test
    @DisplayName("관리자 토큰으로 notification push test send를 호출하면 web push service를 실행한다")
    void notificationPushTestSendAllowsAdminUser() throws Exception {
        mockAuthenticatedToken("admin-token", List.of(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("ROLE_ADMIN")
        ));
        given(webPushDispatchService.sendTestMessage(
                org.mockito.ArgumentMatchers.eq("user-key-1"),
                org.mockito.ArgumentMatchers.any()
        )).willReturn(new WebPushTestSendResponse(1, 1, 0, 0));

        mockMvc.perform(post("/api/notifications/push-test-send")
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "test",
                                  "body": "body",
                                  "url": "/mypage?tab=3"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.sentCount").value(1));
    }

    private void mockAuthenticatedToken(String token, List<SimpleGrantedAuthority> authorities) {
        doNothing().when(jwtUtil).validate(token);
        given(userSessionRevocationService.isAccessAllowed(token)).willReturn(true);
        given(jwtUtil.getAuthenticatedUser(token)).willReturn(new AuthenticatedUser(1L, "user-key-1"));
        given(jwtUtil.getAuthorities(token)).willReturn(List.copyOf(authorities));
    }
}
