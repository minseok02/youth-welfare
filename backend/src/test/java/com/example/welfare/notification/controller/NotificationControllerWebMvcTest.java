package com.example.welfare.notification.controller;

import com.example.welfare.global.util.JwtUtil;
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
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
@AutoConfigureMockMvc(addFilters = false)
class NotificationControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtUtil jwtUtil;
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
    @DisplayName("수신 거부 토큰으로 알림 설정을 해제한다")
    void unsubscribeByToken() throws Exception {
        given(jwtUtil.getSubjectAllowExpired("unsubscribe-token")).willReturn("user-key-7");

        mockMvc.perform(get("/api/notifications/unsubscribe")
                        .param("token", "unsubscribe-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        then(userAccountCommandService).should().unsubscribeNotificationsByUserKey("user-key-7");
    }
}
