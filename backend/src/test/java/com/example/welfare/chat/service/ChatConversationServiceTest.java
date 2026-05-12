package com.example.welfare.chat.service;

import com.example.welfare.chat.dto.ChatAiResult;
import com.example.welfare.chat.dto.ChatAnswerMode;
import com.example.welfare.chat.dto.ChatPolicyCandidate;
import com.example.welfare.chat.dto.request.SendChatMessageRequest;
import com.example.welfare.chat.dto.response.ChatReferenceResponse;
import com.example.welfare.chat.entity.ChatMessage;
import com.example.welfare.chat.entity.ChatMessageRole;
import com.example.welfare.chat.entity.ChatSession;
import com.example.welfare.chat.gateway.ChatAiGateway;
import com.example.welfare.chat.repository.ChatMessageReadRepository;
import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.user.entity.User;
import com.example.welfare.user.service.ActiveUserReadService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatConversationServiceTest {

    @Mock
    private ChatMessageReadRepository chatMessageReadRepository;
    @Mock
    private ChatPolicyService chatPolicyService;
    @Mock
    private ChatAiGateway chatAiGateway;
    @Mock
    private ChatRateLimitService chatRateLimitService;
    @Mock
    private ChatMessageCommandService chatMessageCommandService;
    @Mock
    private ChatRetrievalSnapshotService chatRetrievalSnapshotService;
    @Mock
    private ActiveUserReadService activeUserReadService;
    @Mock
    private ChatGroundingService chatGroundingService;

    private ChatConversationService chatConversationService;

    @BeforeEach
    void setUp() {
        chatConversationService = new ChatConversationService(
                chatMessageReadRepository,
                chatPolicyService,
                new ChatBranchCatalog(),
                chatGroundingService,
                chatAiGateway,
                chatRateLimitService,
                chatMessageCommandService,
                chatRetrievalSnapshotService,
                activeUserReadService,
                new ObjectMapper()
        );
    }

    @Test
    @DisplayName("메시지 전송은 USER와 ASSISTANT 메시지를 저장하고 참조 정책을 응답한다")
    void sendMessageStoresMessagesAndBuildsAnswer() {
        User user = createUser(1L);
        ChatSession session = ChatSession.builder().id(10L).userKey("user-key-1").build();
        SendChatMessageRequest request = new SendChatMessageRequest();
        ReflectionTestUtils.setField(request, "content", "서울 월세 지원 알려줘");

        when(activeUserReadService.getActiveUserContext(1L))
                .thenReturn(new ActiveUserReadService.ActiveUserContext(user, "user-key-1"));
        when(chatMessageReadRepository.findOwnedSession(10L, "user-key-1")).thenReturn(Optional.of(session));
        ChatPolicyService.CandidateTrace trace = new ChatPolicyService.CandidateTrace(
                "서울 월세 지원 알려줘",
                "서울 월세 지원 알려줘",
                null,
                null,
                List.of(),
                "MERGED_RESULTS",
                List.of(),
                List.of(),
                List.of(
                        ChatPolicyCandidate.builder().serviceId(1829L).title("청년월세 한시 특별지원")
                                .supportContent("서울 청년의 주거비 부담 완화와 직접 연결됩니다.").build(),
                        ChatPolicyCandidate.builder().serviceId(2451L).title("청년전세임대")
                                .description("청년 전세 주거 안정을 지원합니다.").build()
                )
        );
        when(chatPolicyService.traceCandidates("서울 월세 지원 알려줘", null, 3)).thenReturn(trace);
        when(chatGroundingService.loadEvidenceMap(any(List.class)))
                .thenReturn(Map.of(
                        1829L, "월세 부담을 낮추는 지원을 제공합니다.",
                        2451L, "전세 주거 안정을 지원합니다."
                ));
        when(chatAiGateway.generateAnswer(any(User.class), any(String.class), any(List.class), any(List.class), any(Map.class)))
                .thenReturn(ChatAiResult.builder()
                        .answer("청년월세 한시 특별지원과 청년전세임대를 먼저 확인해보세요.")
                        .needsClarification(false)
                        .references(List.of(
                                ChatReferenceResponse.builder().serviceId(1829L).title("청년월세 한시 특별지원")
                                        .reason("주거비 부담 완화와 연결됩니다.")
                                        .evidence("월세 부담을 낮추는 지원을 제공합니다.")
                                        .build(),
                                ChatReferenceResponse.builder().serviceId(2451L).title("청년전세임대")
                                        .reason("전세 주거 안정을 지원합니다.")
                                        .evidence("전세 주거 안정을 지원합니다.")
                                        .build()
                        ))
                        .build());
        when(chatMessageReadRepository.findRecentMessages(10L, 6)).thenReturn(List.of());

        var response = chatConversationService.sendMessage(1L, 10L, request);

        assertThat(response.getSessionId()).isEqualTo(10L);
        assertThat(response.isNeedsClarification()).isFalse();
        assertThat(response.getAnswerMode()).isEqualTo(ChatAnswerMode.POLICY_GROUNDED);
        assertThat(response.getAnswer()).isEqualTo("청년월세 한시 특별지원과 청년전세임대를 먼저 확인해보세요.");
        assertThat(response.getReferences()).hasSize(2);
        verify(chatMessageCommandService).appendUserMessage(10L, "서울 월세 지원 알려줘", "서울 월세 지원 알려줘");
        verify(chatMessageCommandService).appendAssistantMessage(eq(10L), eq("청년월세 한시 특별지원과 청년전세임대를 먼저 확인해보세요."), eq("[1829,2451]"), any());
        verify(chatRateLimitService).checkMessageSendLimit(1L);
        verify(chatRetrievalSnapshotService).recordInteractiveTrace(session, "서울 월세 지원 알려줘", trace);
    }

    @Test
    @DisplayName("정책 후보가 없으면 clarification 응답과 빈 참조 목록을 저장한다")
    void sendMessageReturnsClarificationWhenNoCandidate() {
        User user = createUser(1L);
        ChatSession session = ChatSession.builder().id(10L).userKey("user-key-1").title("기존 제목").build();
        SendChatMessageRequest request = new SendChatMessageRequest();
        ReflectionTestUtils.setField(request, "content", "조건을 모르겠어");

        when(activeUserReadService.getActiveUserContext(1L))
                .thenReturn(new ActiveUserReadService.ActiveUserContext(user, "user-key-1"));
        when(chatMessageReadRepository.findOwnedSession(10L, "user-key-1")).thenReturn(Optional.of(session));
        ChatPolicyService.CandidateTrace trace = new ChatPolicyService.CandidateTrace(
                "조건을 모르겠어",
                "조건을 모르겠어",
                null,
                null,
                List.of(),
                "POPULAR_FALLBACK",
                List.of(),
                List.of(),
                List.of()
        );
        when(chatPolicyService.traceCandidates("조건을 모르겠어", null, 3)).thenReturn(trace);
        when(chatGroundingService.loadEvidenceMap(any(List.class))).thenReturn(Map.of());

        var response = chatConversationService.sendMessage(1L, 10L, request);

        assertThat(response.isNeedsClarification()).isTrue();
        assertThat(response.getAnswerMode()).isEqualTo(ChatAnswerMode.CLARIFICATION);
        assertThat(response.getReferences()).isEmpty();
        assertThat(response.getAnswer()).contains("조금 더 구체적으로");
        verify(chatMessageCommandService).appendUserMessage(10L, "조건을 모르겠어", null);
        verify(chatRateLimitService).checkMessageSendLimit(1L);
        verify(chatRetrievalSnapshotService).recordInteractiveTrace(session, "조건을 모르겠어", trace);
    }

    @Test
    @DisplayName("AI 호출이 실패하면 정책 후보 기반 fallback 답변을 사용한다")
    void sendMessageFallsBackWhenAiGatewayReturnsNull() {
        User user = createUser(1L);
        ChatSession session = ChatSession.builder().id(10L).userKey("user-key-1").build();
        SendChatMessageRequest request = new SendChatMessageRequest();
        ReflectionTestUtils.setField(request, "content", "서울 월세 지원 알려줘");

        when(activeUserReadService.getActiveUserContext(1L))
                .thenReturn(new ActiveUserReadService.ActiveUserContext(user, "user-key-1"));
        when(chatMessageReadRepository.findOwnedSession(10L, "user-key-1")).thenReturn(Optional.of(session));
        ChatPolicyService.CandidateTrace trace = new ChatPolicyService.CandidateTrace(
                "서울 월세 지원 알려줘",
                "서울 월세 지원 알려줘",
                null,
                null,
                List.of(),
                "MERGED_RESULTS",
                List.of(),
                List.of(),
                List.of(
                        ChatPolicyCandidate.builder().serviceId(1829L).title("청년월세 한시 특별지원")
                                .supportContent("서울 청년의 주거비 부담 완화와 직접 연결됩니다.").build()
                )
        );
        when(chatPolicyService.traceCandidates("서울 월세 지원 알려줘", null, 3)).thenReturn(trace);
        when(chatGroundingService.loadEvidenceMap(any(List.class)))
                .thenReturn(Map.of(1829L, "월세 부담을 낮추는 지원을 제공합니다."));
        when(chatAiGateway.generateAnswer(any(User.class), any(String.class), any(List.class), any(List.class), any(Map.class)))
                .thenReturn(null);
        when(chatMessageReadRepository.findRecentMessages(10L, 6)).thenReturn(List.of());

        var response = chatConversationService.sendMessage(1L, 10L, request);

        assertThat(response.isNeedsClarification()).isFalse();
        assertThat(response.getAnswerMode()).isEqualTo(ChatAnswerMode.POLICY_GROUNDED);
        assertThat(response.getAnswer()).isEqualTo("청년월세 한시 특별지원 정책을 먼저 확인해보세요.");
        assertThat(response.getReferences()).hasSize(1);
        assertThat(response.getReferences().get(0).getEvidence()).isEqualTo("월세 부담을 낮추는 지원을 제공합니다.");
        verify(chatMessageCommandService).appendAssistantMessage(eq(10L), eq("청년월세 한시 특별지원 정책을 먼저 확인해보세요."), eq("[1829]"), any());
        verify(chatRateLimitService).checkMessageSendLimit(1L);
        verify(chatRetrievalSnapshotService).recordInteractiveTrace(session, "서울 월세 지원 알려줘", trace);
    }

    @Test
    @DisplayName("broad 질문이면 정책 검색 전에 branch suggestion을 반환한다")
    void sendMessageReturnsBranchSuggestionsForBroadQuestion() {
        User user = createUser(1L);
        ChatSession session = ChatSession.builder().id(10L).userKey("user-key-1").build();
        SendChatMessageRequest request = new SendChatMessageRequest();
        ReflectionTestUtils.setField(request, "content", "주거 지원");

        when(activeUserReadService.getActiveUserContext(1L))
                .thenReturn(new ActiveUserReadService.ActiveUserContext(user, "user-key-1"));
        when(chatMessageReadRepository.findOwnedSession(10L, "user-key-1")).thenReturn(Optional.of(session));

        var response = chatConversationService.sendMessage(1L, 10L, request);

        assertThat(response.getAnswerMode()).isEqualTo(ChatAnswerMode.BRANCH_SUGGESTION);
        assertThat(response.getBranchSuggestions()).hasSize(3);
        assertThat(response.getReferences()).isEmpty();
        verify(chatPolicyService, never()).traceCandidates(any(String.class), isNull(), anyInt());
        verify(chatMessageCommandService).appendAssistantMessage(eq(10L), any(String.class), eq("[]"), any());
        verify(chatRetrievalSnapshotService).recordInteractiveBranchSuggestions(eq(session), eq("주거 지원"), isNull(), any(List.class));
    }

    @Test
    @DisplayName("rate limit 초과면 메시지를 저장하지 않고 바로 오류를 반환한다")
    void sendMessageThrowsWhenRateLimitExceeded() {
        User user = createUser(1L);
        SendChatMessageRequest request = new SendChatMessageRequest();
        ReflectionTestUtils.setField(request, "content", "서울 월세 지원 알려줘");

        when(activeUserReadService.getActiveUserContext(1L))
                .thenReturn(new ActiveUserReadService.ActiveUserContext(user, "user-key-1"));
        org.mockito.Mockito.doThrow(new CustomException(ErrorCode.CHAT_RATE_LIMIT_EXCEEDED))
                .when(chatRateLimitService).checkMessageSendLimit(1L);

        assertThatThrownBy(() -> chatConversationService.sendMessage(1L, 10L, request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CHAT_RATE_LIMIT_EXCEEDED);

        verify(chatMessageReadRepository, never()).findOwnedSession(10L, "user-key-1");
        verify(chatMessageCommandService, never()).appendUserMessage(any(), any(), any());
    }

    @Test
    @DisplayName("메시지 조회는 JSON 참조 정책 ID를 리스트로 변환한다")
    void getMessagesParsesReferencedServiceIds() {
        User user = createUser(1L);
        ChatSession session = ChatSession.builder().id(10L).userKey("user-key-1").title("주거 상담").build();
        ChatMessage userMessage = ChatMessage.builder().id(100L).session(session).role(ChatMessageRole.USER).content("월세 지원 있어?").build();
        ChatMessage assistantMessage = ChatMessage.builder().id(101L).session(session).role(ChatMessageRole.ASSISTANT)
                .content("청년월세지원이 있습니다.").referencedServiceIds("[1829,2451]").build();

        when(activeUserReadService.getActiveUserContext(1L))
                .thenReturn(new ActiveUserReadService.ActiveUserContext(user, "user-key-1"));
        when(chatMessageReadRepository.findOwnedSession(10L, "user-key-1")).thenReturn(Optional.of(session));
        when(chatMessageReadRepository.findMessages(10L)).thenReturn(List.of(userMessage, assistantMessage));

        var responses = chatConversationService.getMessages(1L, 10L);

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).getReferencedServiceIds()).isEmpty();
        assertThat(responses.get(1).getReferencedServiceIds()).containsExactly(1829L, 2451L);
    }

    @Test
    @DisplayName("다른 사용자의 세션 메시지 조회 요청은 챗 세션 없음 오류를 반환한다")
    void getMessagesThrowsWhenSessionNotOwned() {
        User user = createUser(1L);

        when(activeUserReadService.getActiveUserContext(1L))
                .thenReturn(new ActiveUserReadService.ActiveUserContext(user, "user-key-1"));
        when(chatMessageReadRepository.findOwnedSession(99L, "user-key-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatConversationService.getMessages(1L, 99L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CHAT_SESSION_NOT_FOUND);
    }

    private User createUser(Long userId) {
        return User.builder().id(userId).userKey("user-key-" + userId).email("chat@example.com").passwordHash("hash").build();
    }
}
