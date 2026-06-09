package com.example.welfare.chat.controller;

import com.example.welfare.chat.dto.request.SendChatMessageRequest;
import com.example.welfare.chat.service.ChatConversationService;
import com.example.welfare.chat.service.ChatSessionCommandService;
import com.example.welfare.chat.service.ChatSessionQueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChatSessionController.class)
@AutoConfigureMockMvc(addFilters = false)
class ChatSessionControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChatSessionCommandService chatSessionCommandService;
    @MockitoBean
    private ChatSessionQueryService chatSessionQueryService;
    @MockitoBean
    private ChatConversationService chatConversationService;
    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    @DisplayName("챗 메시지 전송은 잘못된 branchKey 형식을 400으로 거부한다")
    void sendMessageRejectsInvalidBranchKey() throws Exception {
        mockMvc.perform(post("/api/chat/sessions/{sessionId}/messages", 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "content": "서울 월세 지원 알려줘",
                                  "branchKey": "housing\\r\\nattack"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("C001"));

        verify(chatConversationService, never()).sendMessage(any(), any(), any(SendChatMessageRequest.class));
    }

    @Test
    @DisplayName("챗 메시지 전송은 1 미만 sessionId를 400으로 거부한다")
    void sendMessageRejectsInvalidSessionId() throws Exception {
        mockMvc.perform(post("/api/chat/sessions/{sessionId}/messages", 0L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "content": "서울 월세 지원 알려줘"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("C001"));

        verify(chatConversationService, never()).sendMessage(any(), any(), any(SendChatMessageRequest.class));
    }

    @Test
    @DisplayName("챗 세션 생성은 과도한 제목을 400으로 거부한다")
    void createSessionRejectsOversizedTitle() throws Exception {
        mockMvc.perform(post("/api/chat/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "%s"
                                }
                                """.formatted("x".repeat(101))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("C001"));

        verify(chatSessionCommandService, never()).createSession(any(), any());
    }
}
