package com.example.welfare.chat.service;

import com.example.welfare.chat.dto.ChatAiResult;
import com.example.welfare.chat.dto.ChatAnswerMode;
import com.example.welfare.chat.dto.ChatPolicyCandidate;
import com.example.welfare.chat.dto.request.SendChatMessageRequest;
import com.example.welfare.chat.dto.response.ChatReferenceResponse;
import com.example.welfare.chat.entity.ChatMessage;
import com.example.welfare.chat.entity.ChatMessageRole;
import com.example.welfare.chat.entity.ChatRetrievalSnapshot;
import com.example.welfare.chat.entity.ChatSession;
import com.example.welfare.chat.gateway.ChatAiGateway;
import com.example.welfare.chat.repository.ChatMessageReadRepository;
import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.user.entity.User;
import com.example.welfare.user.entity.UserProfile;
import com.example.welfare.user.repository.UserProfileRepository;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
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
    private ChatSessionContextStateService chatSessionContextStateService;
    @Mock
    private ActiveUserReadService activeUserReadService;
    @Mock
    private ChatGroundingService chatGroundingService;
    @Mock
    private ChatApplicationCoachingService chatApplicationCoachingService;
    @Mock
    private UserProfileRepository userProfileRepository;

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
                chatSessionContextStateService,
                new ChatConversationContextSupport(new ObjectMapper(), new ChatBranchCatalog()),
                chatApplicationCoachingService,
                activeUserReadService,
                userProfileRepository,
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
        when(chatPolicyService.traceCandidatesForUser("서울 월세 지원 알려줘", null, 3, user)).thenReturn(trace);
        when(chatGroundingService.loadEvidenceMap(any(List.class)))
                .thenReturn(Map.of(
                        1829L, "월세 부담을 낮추는 지원을 제공합니다.",
                        2451L, "전세 주거 안정을 지원합니다."
                ));
        when(chatAiGateway.generateAnswer(any(User.class), nullable(String.class), anyString(), any(List.class), any(List.class), any(Map.class), nullable(String.class)))
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
        verify(chatMessageCommandService).appendAssistantMessage(
                eq(10L),
                eq("청년월세 한시 특별지원과 청년전세임대를 먼저 확인해보세요."),
                eq("[1829,2451]"),
                argThat(value -> value != null
                        && value.contains("\"serviceId\":1829")
                        && value.contains("\"serviceId\":2451")
                        && value.contains("주거비 부담 완화와 연결됩니다.")),
                any()
        );
        verify(chatRateLimitService).checkMessageSendLimit(1L);
        verify(chatRetrievalSnapshotService).recordInteractiveTrace(session, "서울 월세 지원 알려줘", trace, false);
        verify(chatSessionContextStateService).captureHousingAnswer(10L, "서울 월세 지원 알려줘", null, response.getReferences());
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
        when(chatPolicyService.traceCandidatesForUser("조건을 모르겠어", null, 3, user)).thenReturn(trace);
        when(chatGroundingService.loadEvidenceMap(any(List.class))).thenReturn(Map.of());

        var response = chatConversationService.sendMessage(1L, 10L, request);

        assertThat(response.isNeedsClarification()).isTrue();
        assertThat(response.getAnswerMode()).isEqualTo(ChatAnswerMode.CLARIFICATION);
        assertThat(response.getReferences()).isEmpty();
        assertThat(response.getAnswer()).contains("조금 더 구체적으로");
        verify(chatMessageCommandService).appendUserMessage(10L, "조건을 모르겠어", null);
        verify(chatRateLimitService).checkMessageSendLimit(1L);
        verify(chatRetrievalSnapshotService).recordInteractiveTrace(session, "조건을 모르겠어", trace, true);
        verify(chatSessionContextStateService).captureHousingAnswer(10L, "조건을 모르겠어", null, List.of());
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
        when(userProfileRepository.findByUserKey("user-key-1"))
                .thenReturn(Optional.of(UserProfile.builder().userKey("user-key-1").ageBand("25_29").build()));
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
        when(chatPolicyService.traceCandidatesForUser("서울 월세 지원 알려줘", null, 3, user)).thenReturn(trace);
        when(chatGroundingService.loadEvidenceMap(any(List.class)))
                .thenReturn(Map.of(1829L, "월세 부담을 낮추는 지원을 제공합니다."));
        when(chatAiGateway.generateAnswer(any(User.class), nullable(String.class), anyString(), any(List.class), any(List.class), any(Map.class), nullable(String.class)))
                .thenReturn(null);
        when(chatMessageReadRepository.findRecentMessages(10L, 6)).thenReturn(List.of());

        var response = chatConversationService.sendMessage(1L, 10L, request);

        assertThat(response.isNeedsClarification()).isFalse();
        assertThat(response.getAnswerMode()).isEqualTo(ChatAnswerMode.POLICY_GROUNDED);
        assertThat(response.getAnswer()).isEqualTo("청년월세 한시 특별지원 정책을 먼저 확인해보세요.");
        assertThat(response.getReferences()).hasSize(1);
        assertThat(response.getReferences().get(0).getEvidence()).isEqualTo("월세 부담을 낮추는 지원을 제공합니다.");
        verify(chatMessageCommandService).appendAssistantMessage(
                eq(10L),
                eq("청년월세 한시 특별지원 정책을 먼저 확인해보세요."),
                eq("[1829]"),
                argThat(value -> value != null
                        && value.contains("\"serviceId\":1829")
                        && value.contains("서울 청년의 주거비 부담 완화와 직접 연결됩니다.")),
                any()
        );
        verify(chatRateLimitService).checkMessageSendLimit(1L);
        verify(chatRetrievalSnapshotService).recordInteractiveTrace(session, "서울 월세 지원 알려줘", trace, false);
        verify(chatSessionContextStateService).captureHousingAnswer(10L, "서울 월세 지원 알려줘", null, response.getReferences());
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
        verify(chatPolicyService, never()).traceCandidatesForUser(any(String.class), isNull(), anyInt(), any(User.class));
        verify(chatMessageCommandService).appendAssistantMessage(eq(10L), any(String.class), eq("[]"), eq("[]"), any());
        verify(chatRetrievalSnapshotService).recordInteractiveBranchSuggestions(eq(session), eq("주거 지원"), isNull(), any(List.class));
        verify(chatSessionContextStateService).captureHousingBranchSuggestions(eq(10L), eq("주거 지원"), any(List.class));
    }

    @Test
    @DisplayName("후속 질문이면 직전 질문과 branch 맥락을 이어서 retrieval한다")
    void sendMessageCarriesPreviousQuestionAndBranchForFollowUp() {
        User user = createUser(1L);
        ChatSessionContextState sessionState = ChatSessionContextState.builder()
                .housing(ChatSessionContextState.HousingContext.builder()
                        .activeBranchKey("housing-cash")
                        .anchorQuestion("서울 월세 지원 알려줘")
                        .recentTopics(List.of("월세"))
                        .recentPolicyTitles(List.of("청년월세 한시 특별지원"))
                        .recentPolicyIds(List.of(1829L))
                        .build())
                .memory(ChatSessionContextState.MemoryContext.builder()
                        .summary("사용자는 서울 주거 지원을 탐색했고 월세성 지원에 관심을 보였다.")
                        .recentUserQuestions(List.of("서울 월세 지원 알려줘"))
                        .recentPolicyTitles(List.of("청년월세 한시 특별지원"))
                        .build())
                .build();
        ChatSession session = ChatSession.builder()
                .id(10L)
                .userKey("user-key-1")
                .contextStateJson(new ObjectMapper().valueToTree(sessionState).toString())
                .build();
        SendChatMessageRequest request = new SendChatMessageRequest();
        ReflectionTestUtils.setField(request, "content", "그럼 전세는?");

        ChatMessage previousUserMessage = ChatMessage.builder()
                .id(100L)
                .session(session)
                .role(ChatMessageRole.USER)
                .content("서울 월세 지원 알려줘")
                .build();
        ChatMessage previousAssistantMessage = ChatMessage.builder()
                .id(101L)
                .session(session)
                .role(ChatMessageRole.ASSISTANT)
                .content("청년월세 한시 특별지원을 먼저 확인해보세요.")
                .referencesJson("[{\"serviceId\":1829,\"title\":\"청년월세 한시 특별지원\",\"reason\":\"월세 지원\",\"evidence\":\"월세 지원\"}]")
                .build();
        ChatRetrievalSnapshot snapshot = ChatRetrievalSnapshot.builder()
                .id(15L)
                .snapshotType("INTERACTIVE")
                .sessionId(10L)
                .userKey("user-key-1")
                .question("서울 월세 지원 알려줘")
                .branchKey("housing-cash")
                .resultCount(1)
                .build();

        when(activeUserReadService.getActiveUserContext(1L))
                .thenReturn(new ActiveUserReadService.ActiveUserContext(user, "user-key-1"));
        when(chatMessageReadRepository.findOwnedSession(10L, "user-key-1")).thenReturn(Optional.of(session));
        when(chatMessageReadRepository.findRecentMessages(10L, 6))
                .thenReturn(List.of(previousAssistantMessage, previousUserMessage));
        when(chatRetrievalSnapshotService.findSessionSnapshots(10L)).thenReturn(List.of(snapshot));

        ChatPolicyService.CandidateTrace trace = new ChatPolicyService.CandidateTrace(
                "서울 주거 지원\n현재 관심 갈래: 장기 주거 안정\n후속 질문: 그럼 전세는?",
                "서울 주거 지원 현재 관심 갈래 장기 주거 안정 후속 질문 그럼 전세는",
                "housing-stability",
                "주거",
                List.of("전세", "임대", "공공임대", "주거 안정"),
                "MERGED_RESULTS",
                List.of(),
                List.of(),
                List.of(
                        ChatPolicyCandidate.builder().serviceId(2451L).title("청년전세임대").description("청년 전세 주거 안정을 지원합니다.").build()
                )
        );
        when(chatPolicyService.traceCandidatesForUser(
                "서울 주거 지원\n현재 관심 갈래: 장기 주거 안정\n후속 질문: 그럼 전세는?",
                "housing-stability",
                3,
                user
        ))
                .thenReturn(trace);
        when(chatGroundingService.loadEvidenceMap(any(List.class)))
                .thenReturn(Map.of(2451L, "전세 주거 안정을 지원합니다."));
        when(chatAiGateway.generateAnswer(any(User.class), nullable(String.class), anyString(), any(List.class), any(List.class), any(Map.class), anyString()))
                .thenReturn(null);

        var response = chatConversationService.sendMessage(1L, 10L, request);

        assertThat(response.getAnswerMode()).isEqualTo(ChatAnswerMode.POLICY_GROUNDED);
        verify(chatPolicyService).traceCandidatesForUser(
                "서울 주거 지원\n현재 관심 갈래: 장기 주거 안정\n후속 질문: 그럼 전세는?",
                "housing-stability",
                3,
                user
        );
        verify(chatAiGateway).generateAnswer(
                any(User.class),
                nullable(String.class),
                eq("그럼 전세는?"),
                any(List.class),
                any(List.class),
                any(Map.class),
                argThat(value -> value != null
                        && value.contains("저장된 세션 요약: 사용자는 서울 주거 지원을 탐색했고 월세성 지원에 관심을 보였다.")
                        && value.contains("누적 질문 관심사: 서울 월세 지원 알려줘")
                        && value.contains("누적 추천 정책: 청년월세 한시 특별지원")
                        && value.contains("직전 사용자 질문: 서울 월세 지원 알려줘")
                        && value.contains("직전 탐색 방향: 장기 주거 안정")
                        && value.contains("최근 탐색 흐름: 즉시 현금성 지원")
                        && value.contains("주거 세션 상태: 월세")
                        && value.contains("직전 추천 정책: 청년월세 한시 특별지원"))
        );
        verify(chatSessionContextStateService).captureHousingAnswer(eq(10L), eq("그럼 전세는?"), eq("housing-stability"), any(List.class));
    }

    @Test
    @DisplayName("직전 branch suggestion 이후 자유 입력도 suggestion branch로 이어서 retrieval한다")
    void sendMessageCarriesSuggestedBranchForFreeformFollowUp() {
        User user = createUser(1L);
        ChatSession session = ChatSession.builder().id(10L).userKey("user-key-1").build();
        SendChatMessageRequest request = new SendChatMessageRequest();
        ReflectionTestUtils.setField(request, "content", "월세 쪽으로 보여줘");

        ChatMessage olderUserMessage = ChatMessage.builder()
                .id(98L)
                .session(session)
                .role(ChatMessageRole.USER)
                .content("취업 지원도 있나")
                .build();
        ChatMessage olderAssistantMessage = ChatMessage.builder()
                .id(99L)
                .session(session)
                .role(ChatMessageRole.ASSISTANT)
                .content("채용/인턴, 훈련/교육, 창업/금융 중에서 어느 방향으로 찾을지 골라주시면 좁혀서 보여드리겠습니다.")
                .referencesJson("[{\"serviceId\":3001,\"title\":\"청년일자리 도약장려금\",\"reason\":\"취업 지원\",\"evidence\":\"취업 지원\"}]")
                .build();
        ChatMessage previousUserMessage = ChatMessage.builder()
                .id(100L)
                .session(session)
                .role(ChatMessageRole.USER)
                .content("주거 지원")
                .build();
        ChatMessage previousAssistantMessage = ChatMessage.builder()
                .id(101L)
                .session(session)
                .role(ChatMessageRole.ASSISTANT)
                .content("장기 주거 안정, 즉시 현금성 지원, 청약/입주 정보 중에서 어느 방향으로 찾을지 골라주시면 그 기준으로 정책을 좁혀서 보여드리겠습니다.")
                .build();
        ChatRetrievalSnapshot olderSnapshot = ChatRetrievalSnapshot.builder()
                .id(15L)
                .snapshotType("INTERACTIVE")
                .sessionId(10L)
                .userKey("user-key-1")
                .question("취업 지원도 있나")
                .branchKey("job-training")
                .branchSuggestionKeysJson("[\"job-employment\",\"job-training\",\"job-startup\"]")
                .resultCount(0)
                .build();
        ChatRetrievalSnapshot snapshot = ChatRetrievalSnapshot.builder()
                .id(16L)
                .snapshotType("INTERACTIVE")
                .sessionId(10L)
                .userKey("user-key-1")
                .question("주거 지원")
                .branchSuggestionKeysJson("[\"housing-stability\",\"housing-cash\",\"housing-subscription\"]")
                .resultCount(0)
                .build();

        when(activeUserReadService.getActiveUserContext(1L))
                .thenReturn(new ActiveUserReadService.ActiveUserContext(user, "user-key-1"));
        when(chatMessageReadRepository.findOwnedSession(10L, "user-key-1")).thenReturn(Optional.of(session));
        when(chatMessageReadRepository.findRecentMessages(10L, 6))
                .thenReturn(List.of(previousAssistantMessage, previousUserMessage, olderAssistantMessage, olderUserMessage));
        when(chatRetrievalSnapshotService.findSessionSnapshots(10L)).thenReturn(List.of(olderSnapshot, snapshot));

        ChatPolicyService.CandidateTrace trace = new ChatPolicyService.CandidateTrace(
                "주거 지원\n후속 질문: 월세 쪽으로 보여줘",
                "주거 월세",
                "housing-cash",
                "주거",
                List.of("월세", "주거비", "지원금"),
                "MERGED_RESULTS",
                List.of(),
                List.of(),
                List.of(
                        ChatPolicyCandidate.builder().serviceId(1829L).title("청년월세 한시 특별지원")
                                .description("청년 월세 부담을 줄이는 정책입니다.").build()
                )
        );
        when(chatPolicyService.traceCandidatesForUser("주거 지원\n후속 질문: 월세 쪽으로 보여줘", "housing-cash", 3, user))
                .thenReturn(trace);
        when(chatGroundingService.loadEvidenceMap(any(List.class)))
                .thenReturn(Map.of(1829L, "월세 부담을 낮추는 지원을 제공합니다."));
        when(chatAiGateway.generateAnswer(any(User.class), nullable(String.class), anyString(), any(List.class), any(List.class), any(Map.class), anyString()))
                .thenReturn(null);

        var response = chatConversationService.sendMessage(1L, 10L, request);

        assertThat(response.getAnswerMode()).isEqualTo(ChatAnswerMode.POLICY_GROUNDED);
        verify(chatPolicyService).traceCandidatesForUser("주거 지원\n후속 질문: 월세 쪽으로 보여줘", "housing-cash", 3, user);
        verify(chatAiGateway).generateAnswer(
                any(User.class),
                nullable(String.class),
                eq("월세 쪽으로 보여줘"),
                any(List.class),
                any(List.class),
                any(Map.class),
                argThat(value -> value != null
                        && value.contains("직전 사용자 질문: 주거 지원")
                        && value.contains("최근 질문 흐름: 취업 지원도 있나 -> 주거 지원")
                        && value.contains("직전 탐색 방향: 즉시 현금성 지원")
                        && value.contains("최근 탐색 흐름: 훈련/교육")
                        && value.contains("최근 제안 갈래: 장기 주거 안정, 즉시 현금성 지원, 청약/입주 정보")
                        && value.contains("직전 추천 정책: 청년일자리 도약장려금"))
        );
        verify(chatSessionContextStateService).captureHousingAnswer(eq(10L), eq("월세 쪽으로 보여줘"), eq("housing-cash"), any(List.class));
    }

    @Test
    @DisplayName("저장된 memory는 일반 후속 질문의 retrieval 입력에 포함되어 multi-turn ranking 맥락을 보강한다")
    void sendMessageUsesStoredMemoryForGeneralFollowUpRetrieval() {
        User user = createUser(1L);
        ChatSessionContextState sessionState = ChatSessionContextState.builder()
                .memory(ChatSessionContextState.MemoryContext.builder()
                        .summary("사용자는 취업 지원과 창업 자금 정책을 비교하고 있다.")
                        .recentUserQuestions(List.of("취업 지원도 있나", "창업 자금도 궁금해"))
                        .recentPolicyTitles(List.of("청년일자리 도약장려금", "청년창업 지원자금"))
                        .build())
                .build();
        ChatSession session = ChatSession.builder()
                .id(10L)
                .userKey("user-key-1")
                .contextStateJson(new ObjectMapper().valueToTree(sessionState).toString())
                .build();
        SendChatMessageRequest request = new SendChatMessageRequest();
        ReflectionTestUtils.setField(request, "content", "그럼 대출은?");

        ChatMessage previousUserMessage = ChatMessage.builder()
                .id(100L)
                .session(session)
                .role(ChatMessageRole.USER)
                .content("창업 자금도 궁금해")
                .build();
        ChatMessage previousAssistantMessage = ChatMessage.builder()
                .id(101L)
                .session(session)
                .role(ChatMessageRole.ASSISTANT)
                .content("청년창업 지원자금을 확인해보세요.")
                .referencesJson("[{\"serviceId\":77,\"title\":\"청년창업 지원자금\",\"reason\":\"창업 자금\",\"evidence\":\"창업 자금\"}]")
                .build();
        String expectedRetrievalQuestion = String.join("\n",
                "저장된 관심 맥락: 사용자는 취업 지원과 창업 자금 정책을 비교하고 있다. / 취업 지원도 있나 창업 자금도 궁금해 / 청년일자리 도약장려금 청년창업 지원자금",
                "창업 자금도 궁금해",
                "후속 질문: 그럼 대출은?"
        );

        when(activeUserReadService.getActiveUserContext(1L))
                .thenReturn(new ActiveUserReadService.ActiveUserContext(user, "user-key-1"));
        when(chatMessageReadRepository.findOwnedSession(10L, "user-key-1")).thenReturn(Optional.of(session));
        when(chatMessageReadRepository.findRecentMessages(10L, 6))
                .thenReturn(List.of(previousAssistantMessage, previousUserMessage));
        when(chatRetrievalSnapshotService.findSessionSnapshots(10L)).thenReturn(List.of());

        ChatPolicyService.CandidateTrace trace = new ChatPolicyService.CandidateTrace(
                expectedRetrievalQuestion,
                "저장된 관심 맥락 취업 창업 자금 대출",
                null,
                null,
                List.of(),
                "MERGED_RESULTS",
                List.of(),
                List.of(),
                List.of(
                        ChatPolicyCandidate.builder().serviceId(77L).title("청년창업 지원자금").description("창업 대출을 지원합니다.").build()
                )
        );
        when(chatPolicyService.traceCandidatesForUser(expectedRetrievalQuestion, null, 3, user)).thenReturn(trace);
        when(chatGroundingService.loadEvidenceMap(any(List.class)))
                .thenReturn(Map.of(77L, "창업 대출을 지원합니다."));
        when(chatAiGateway.generateAnswer(any(User.class), nullable(String.class), anyString(), any(List.class), any(List.class), any(Map.class), anyString()))
                .thenReturn(null);

        var response = chatConversationService.sendMessage(1L, 10L, request);

        assertThat(response.getAnswerMode()).isEqualTo(ChatAnswerMode.POLICY_GROUNDED);
        verify(chatPolicyService).traceCandidatesForUser(expectedRetrievalQuestion, null, 3, user);
        verify(chatAiGateway).generateAnswer(
                any(User.class),
                nullable(String.class),
                eq("그럼 대출은?"),
                any(List.class),
                any(List.class),
                any(Map.class),
                argThat(value -> value != null
                        && value.contains("저장된 세션 요약: 사용자는 취업 지원과 창업 자금 정책을 비교하고 있다.")
                        && value.contains("누적 추천 정책: 청년일자리 도약장려금, 청년창업 지원자금")
                        && value.contains("직전 사용자 질문: 창업 자금도 궁금해"))
        );
        verify(chatSessionContextStateService).captureConversationMemory(eq(10L), eq("그럼 대출은?"), any(String.class), any(List.class));
    }

    @Test
    @DisplayName("주거 후속 질문에서 정책 근거가 있으면 AI clarification 응답도 grounded로 승격한다")
    void sendMessagePromotesHousingFollowUpWithReferencesEvenWhenAiRequestsClarification() {
        User user = createUser(1L);
        ChatSessionContextState sessionState = ChatSessionContextState.builder()
                .housing(ChatSessionContextState.HousingContext.builder()
                        .activeBranchKey("housing-cash")
                        .anchorQuestion("서울 월세 지원 알려줘")
                        .recentTopics(List.of("월세"))
                        .recentPolicyTitles(List.of("청년월세 한시 특별지원"))
                        .recentPolicyIds(List.of(1829L))
                        .build())
                .build();
        ChatSession session = ChatSession.builder()
                .id(10L)
                .userKey("user-key-1")
                .contextStateJson(new ObjectMapper().valueToTree(sessionState).toString())
                .build();
        SendChatMessageRequest request = new SendChatMessageRequest();
        ReflectionTestUtils.setField(request, "content", "그럼 전세는?");

        ChatMessage previousUserMessage = ChatMessage.builder()
                .id(100L)
                .session(session)
                .role(ChatMessageRole.USER)
                .content("서울 월세 지원 알려줘")
                .build();
        ChatMessage previousAssistantMessage = ChatMessage.builder()
                .id(101L)
                .session(session)
                .role(ChatMessageRole.ASSISTANT)
                .content("청년월세 한시 특별지원을 먼저 확인해보세요.")
                .referencesJson("[{\"serviceId\":1829,\"title\":\"청년월세 한시 특별지원\",\"reason\":\"월세 지원\",\"evidence\":\"월세 지원\"}]")
                .build();
        ChatRetrievalSnapshot snapshot = ChatRetrievalSnapshot.builder()
                .id(15L)
                .snapshotType("INTERACTIVE")
                .sessionId(10L)
                .userKey("user-key-1")
                .question("서울 월세 지원 알려줘")
                .branchKey("housing-cash")
                .resultCount(1)
                .build();

        when(activeUserReadService.getActiveUserContext(1L))
                .thenReturn(new ActiveUserReadService.ActiveUserContext(user, "user-key-1"));
        when(chatMessageReadRepository.findOwnedSession(10L, "user-key-1")).thenReturn(Optional.of(session));
        when(chatMessageReadRepository.findRecentMessages(10L, 6))
                .thenReturn(List.of(previousAssistantMessage, previousUserMessage));
        when(chatRetrievalSnapshotService.findSessionSnapshots(10L)).thenReturn(List.of(snapshot));

        ChatPolicyService.CandidateTrace trace = new ChatPolicyService.CandidateTrace(
                "서울 주거 지원\n현재 관심 갈래: 장기 주거 안정\n후속 질문: 그럼 전세는?",
                "서울 주거 지원 현재 관심 갈래 장기 주거 안정 후속 질문 그럼 전세는",
                "housing-stability",
                "주거",
                List.of("전세", "임대", "공공임대", "주거 안정"),
                "MERGED_RESULTS",
                List.of(),
                List.of(),
                List.of(
                        ChatPolicyCandidate.builder().serviceId(6359L).title("국토교통부 전세보증금반환보증 보증료 지원")
                                .description("전세보증금반환보증 보증료를 지원합니다.").build()
                )
        );
        when(chatPolicyService.traceCandidatesForUser(
                "서울 주거 지원\n현재 관심 갈래: 장기 주거 안정\n후속 질문: 그럼 전세는?",
                "housing-stability",
                3,
                user
        )).thenReturn(trace);
        when(chatGroundingService.loadEvidenceMap(any(List.class)))
                .thenReturn(Map.of(6359L, "전세보증금반환보증 보증료를 지원합니다."));
        when(chatAiGateway.generateAnswer(any(User.class), nullable(String.class), anyString(), any(List.class), any(List.class), any(Map.class), anyString()))
                .thenReturn(ChatAiResult.builder()
                        .answer("전세에 대한 지원으로는 국토교통부 전세보증금반환보증 보증료 지원이 있습니다.")
                        .needsClarification(true)
                        .references(List.of(
                                ChatReferenceResponse.builder()
                                        .serviceId(6359L)
                                        .title("국토교통부 전세보증금반환보증 보증료 지원")
                                        .reason("전세 관련 지원입니다.")
                                        .evidence("전세보증금반환보증 보증료를 지원합니다.")
                                        .build()
                        ))
                        .build());

        var response = chatConversationService.sendMessage(1L, 10L, request);

        assertThat(response.getAnswerMode()).isEqualTo(ChatAnswerMode.POLICY_GROUNDED);
        assertThat(response.isNeedsClarification()).isFalse();
        assertThat(response.getReferences()).hasSize(1);
        verify(chatSessionContextStateService).captureHousingAnswer(eq(10L), eq("그럼 전세는?"), eq("housing-stability"), any(List.class));
    }

    @Test
    @DisplayName("일반 후속 질문도 정책 근거가 있으면 AI clarification 응답을 grounded로 승격한다")
    void sendMessagePromotesGeneralFollowUpWithReferencesEvenWhenAiRequestsClarification() {
        User user = createUser(1L);
        ChatSession session = ChatSession.builder().id(10L).userKey("user-key-1").build();
        SendChatMessageRequest request = new SendChatMessageRequest();
        ReflectionTestUtils.setField(request, "content", "청년창업센터 사업화자금 쪽으로 보여줘");

        ChatMessage previousUserMessage = ChatMessage.builder()
                .id(100L)
                .session(session)
                .role(ChatMessageRole.USER)
                .content("창업 지원 정책 알려줘")
                .build();
        ChatMessage previousAssistantMessage = ChatMessage.builder()
                .id(101L)
                .session(session)
                .role(ChatMessageRole.ASSISTANT)
                .content("청년 창업 지원 정책이 있습니다.")
                .referencesJson("[{\"serviceId\":14348,\"title\":\"청년 창업 지원\",\"reason\":\"창업 지원\",\"evidence\":\"창업활동비를 지원합니다.\"}]")
                .build();

        when(activeUserReadService.getActiveUserContext(1L))
                .thenReturn(new ActiveUserReadService.ActiveUserContext(user, "user-key-1"));
        when(chatMessageReadRepository.findOwnedSession(10L, "user-key-1")).thenReturn(Optional.of(session));
        when(chatMessageReadRepository.findRecentMessages(10L, 6))
                .thenReturn(List.of(previousAssistantMessage, previousUserMessage));
        when(chatRetrievalSnapshotService.findSessionSnapshots(10L)).thenReturn(List.of());

        String expectedRetrievalQuestion = "청년창업센터 사업화자금 쪽으로 보여줘";
        ChatPolicyService.CandidateTrace trace = new ChatPolicyService.CandidateTrace(
                expectedRetrievalQuestion,
                "청년창업센터 사업화자금 쪽으로 보여줘",
                null,
                "일자리",
                List.of("창업"),
                "MERGED_RESULTS",
                List.of(),
                List.of(),
                List.of(
                        ChatPolicyCandidate.builder().serviceId(14349L).title("청년창업센터 지원")
                                .description("사무공간, 멘토링, 창업교육, 사업화자금 지원 등 초기창업 보육을 지원합니다.")
                                .build()
                )
        );
        when(chatPolicyService.traceCandidatesForUser(expectedRetrievalQuestion, null, 3, user))
                .thenReturn(trace);
        when(chatGroundingService.loadEvidenceMap(any(List.class)))
                .thenReturn(Map.of(14349L, "사무공간, 멘토링, 창업교육, 사업화자금 지원 등 초기창업 보육을 지원합니다."));
        when(chatAiGateway.generateAnswer(any(User.class), nullable(String.class), anyString(), any(List.class), any(List.class), any(Map.class), anyString()))
                .thenReturn(ChatAiResult.builder()
                        .answer("청년창업센터 지원은 예비 또는 창업 7년 이내 청년기업을 대상으로 사업화자금과 보육 프로그램을 제공합니다.")
                        .needsClarification(true)
                        .references(List.of(
                                ChatReferenceResponse.builder()
                                        .serviceId(14349L)
                                        .title("청년창업센터 지원")
                                        .reason("사업화자금과 창업 보육 지원입니다.")
                                        .evidence("사무공간, 멘토링, 창업교육, 사업화자금 지원 등 초기창업 보육을 지원합니다.")
                                        .build()
                        ))
                        .build());

        var response = chatConversationService.sendMessage(1L, 10L, request);

        assertThat(response.getAnswerMode()).isEqualTo(ChatAnswerMode.POLICY_GROUNDED);
        assertThat(response.isNeedsClarification()).isFalse();
        assertThat(response.getReferences()).hasSize(1);
        verify(chatRetrievalSnapshotService).recordInteractiveTrace(eq(session), eq("청년창업센터 사업화자금 쪽으로 보여줘"), eq(trace), eq(false));
    }

    @Test
    @DisplayName("메시지 재조회는 snapshot 기준으로 branch suggestion 메타를 복원한다")
    void getMessagesRestoresBranchSuggestionMetadata() {
        User user = createUser(1L);
        ChatSession session = ChatSession.builder().id(10L).userKey("user-key-1").build();
        ChatMessage userMessage = ChatMessage.builder()
                .id(100L)
                .session(session)
                .role(ChatMessageRole.USER)
                .content("주거 지원")
                .referencedServiceIds(null)
                .build();
        ReflectionTestUtils.setField(userMessage, "createdAt", java.time.LocalDateTime.of(2026, 5, 14, 10, 0, 0));
        ChatMessage assistantMessage = ChatMessage.builder()
                .id(101L)
                .session(session)
                .role(ChatMessageRole.ASSISTANT)
                .content("주거 안정, 전월세 지원, 긴급 주거 지원 중에서 어느 방향으로 찾을지 골라주시면 그 기준으로 정책을 좁혀서 보여드리겠습니다.")
                .referencedServiceIds("[]")
                .build();
        ReflectionTestUtils.setField(assistantMessage, "createdAt", java.time.LocalDateTime.of(2026, 5, 14, 10, 0, 1));
        ChatRetrievalSnapshot snapshot = ChatRetrievalSnapshot.builder()
                .id(15L)
                .snapshotType("INTERACTIVE")
                .sessionId(10L)
                .userKey("user-key-1")
                .question("주거 지원")
                .branchSuggestionKeysJson("[\"housing-stability\",\"housing-cash\",\"housing-subscription\"]")
                .resultCount(0)
                .build();
        ReflectionTestUtils.setField(snapshot, "createdAt", java.time.LocalDateTime.of(2026, 5, 14, 10, 0, 0));

        when(activeUserReadService.getActiveUserContext(1L))
                .thenReturn(new ActiveUserReadService.ActiveUserContext(user, "user-key-1"));
        when(chatMessageReadRepository.findOwnedSession(10L, "user-key-1")).thenReturn(Optional.of(session));
        when(chatMessageReadRepository.findMessages(10L)).thenReturn(List.of(userMessage, assistantMessage));
        when(chatRetrievalSnapshotService.findSessionSnapshots(10L)).thenReturn(List.of(snapshot));

        var responses = chatConversationService.getMessages(1L, 10L);

        assertThat(responses).hasSize(2);
        assertThat(responses.get(1).getAnswerMode()).isEqualTo(ChatAnswerMode.BRANCH_SUGGESTION);
        assertThat(responses.get(1).isNeedsClarification()).isFalse();
        assertThat(responses.get(1).getReferences()).isEmpty();
        assertThat(responses.get(1).getBranchSuggestions())
                .extracting("branchKey")
                .containsExactly("housing-stability", "housing-cash", "housing-subscription");
    }

    @Test
    @DisplayName("메시지 재조회는 snapshot의 clarification 플래그를 우선 복원한다")
    void getMessagesRestoresClarificationMetadataFromSnapshot() {
        User user = createUser(1L);
        ChatSession session = ChatSession.builder().id(10L).userKey("user-key-1").build();
        ChatMessage userMessage = ChatMessage.builder()
                .id(100L)
                .session(session)
                .role(ChatMessageRole.USER)
                .content("지원은 있는데 제 상황에 맞는지 모르겠어")
                .build();
        ReflectionTestUtils.setField(userMessage, "createdAt", java.time.LocalDateTime.of(2026, 5, 14, 10, 0, 0));
        ChatMessage assistantMessage = ChatMessage.builder()
                .id(101L)
                .session(session)
                .role(ChatMessageRole.ASSISTANT)
                .content("먼저 조건을 조금 더 알려주시면 정확히 좁힐 수 있습니다.")
                .referencedServiceIds("[1829]")
                .referencesJson("[{\"serviceId\":1829,\"title\":\"청년월세 한시 특별지원\",\"reason\":\"주거비 지원\",\"evidence\":\"월세 지원\"}]")
                .build();
        ReflectionTestUtils.setField(assistantMessage, "createdAt", java.time.LocalDateTime.of(2026, 5, 14, 10, 0, 1));
        ChatRetrievalSnapshot snapshot = ChatRetrievalSnapshot.builder()
                .id(16L)
                .snapshotType("INTERACTIVE")
                .sessionId(10L)
                .userKey("user-key-1")
                .question("지원은 있는데 제 상황에 맞는지 모르겠어")
                .needsClarification(true)
                .resultCount(1)
                .build();
        ReflectionTestUtils.setField(snapshot, "createdAt", java.time.LocalDateTime.of(2026, 5, 14, 10, 0, 0));

        when(activeUserReadService.getActiveUserContext(1L))
                .thenReturn(new ActiveUserReadService.ActiveUserContext(user, "user-key-1"));
        when(chatMessageReadRepository.findOwnedSession(10L, "user-key-1")).thenReturn(Optional.of(session));
        when(chatMessageReadRepository.findMessages(10L)).thenReturn(List.of(userMessage, assistantMessage));
        when(chatRetrievalSnapshotService.findSessionSnapshots(10L)).thenReturn(List.of(snapshot));

        var responses = chatConversationService.getMessages(1L, 10L);

        assertThat(responses).hasSize(2);
        assertThat(responses.get(1).getAnswerMode()).isEqualTo(ChatAnswerMode.CLARIFICATION);
        assertThat(responses.get(1).isNeedsClarification()).isTrue();
        assertThat(responses.get(1).getReferences()).hasSize(1);
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
                .content("청년월세지원이 있습니다.")
                .referencedServiceIds("[1829,2451]")
                .referencesJson("[{\"serviceId\":1829,\"title\":\"청년월세 한시 특별지원\",\"reason\":\"주거비 지원\",\"evidence\":\"월세 지원\"},{\"serviceId\":2451,\"title\":\"청년전세임대\",\"reason\":\"전세 지원\",\"evidence\":\"전세 지원\"}]")
                .build();

        when(activeUserReadService.getActiveUserContext(1L))
                .thenReturn(new ActiveUserReadService.ActiveUserContext(user, "user-key-1"));
        when(chatMessageReadRepository.findOwnedSession(10L, "user-key-1")).thenReturn(Optional.of(session));
        when(chatMessageReadRepository.findMessages(10L)).thenReturn(List.of(userMessage, assistantMessage));

        var responses = chatConversationService.getMessages(1L, 10L);

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).getReferencedServiceIds()).isEmpty();
        assertThat(responses.get(1).getReferencedServiceIds()).containsExactly(1829L, 2451L);
        assertThat(responses.get(1).getReferences()).hasSize(2);
        assertThat(responses.get(1).getReferences().get(0).getTitle()).isEqualTo("청년월세 한시 특별지원");
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
