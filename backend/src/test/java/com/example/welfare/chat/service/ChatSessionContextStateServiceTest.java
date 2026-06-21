package com.example.welfare.chat.service;

import com.example.welfare.chat.dto.response.ChatBranchOptionResponse;
import com.example.welfare.chat.dto.response.ChatReferenceResponse;
import com.example.welfare.chat.entity.ChatSession;
import com.example.welfare.chat.repository.ChatSessionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatSessionContextStateServiceTest {

    @Mock
    private ChatSessionRepository chatSessionRepository;

    private ChatSessionContextStateService chatSessionContextStateService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        chatSessionContextStateService = new ChatSessionContextStateService(
                chatSessionRepository,
                objectMapper,
                new ChatBranchCatalog()
        );
    }

    @Test
    @DisplayName("주거 branch suggestion은 세션 context state에 anchor와 suggestion keys를 남긴다")
    void captureHousingBranchSuggestionsStoresHousingState() throws Exception {
        ChatSession session = ChatSession.builder().id(10L).userKey("user-key-1").build();
        when(chatSessionRepository.findById(10L)).thenReturn(Optional.of(session));

        chatSessionContextStateService.captureHousingBranchSuggestions(
                10L,
                "주거 지원",
                List.of(
                        ChatBranchOptionResponse.builder().branchKey("housing-stability").label("장기 주거 안정").build(),
                        ChatBranchOptionResponse.builder().branchKey("housing-cash").label("즉시 현금성 지원").build(),
                        ChatBranchOptionResponse.builder().branchKey("job-employment").label("채용/인턴").build()
                )
        );

        ChatSessionContextState state = objectMapper.readValue(session.getContextStateJson(), ChatSessionContextState.class);
        assertThat(state.getHousing()).isNotNull();
        assertThat(state.getHousing().getAnchorQuestion()).isEqualTo("주거 지원");
        assertThat(state.getHousing().getSuggestedBranchKeys())
                .containsExactly("housing-stability", "housing-cash");
    }

    @Test
    @DisplayName("주거 grounded answer는 active branch와 최근 topic/policy를 누적한다")
    void captureHousingAnswerStoresActiveBranchAndPolicies() throws Exception {
        ChatSessionContextState seedState = ChatSessionContextState.builder()
                .housing(ChatSessionContextState.HousingContext.builder()
                        .anchorQuestion("주거 지원")
                        .recentTopics(List.of("주거"))
                        .suggestedBranchKeys(List.of("housing-stability", "housing-cash", "housing-subscription"))
                        .build())
                .build();
        ChatSession session = ChatSession.builder()
                .id(10L)
                .userKey("user-key-1")
                .contextStateJson(objectMapper.writeValueAsString(seedState))
                .build();
        when(chatSessionRepository.findById(10L)).thenReturn(Optional.of(session));

        chatSessionContextStateService.captureHousingAnswer(
                10L,
                "월세 쪽으로 보여줘",
                "housing-cash",
                List.of(
                        ChatReferenceResponse.builder().serviceId(1829L).title("청년월세 한시 특별지원").build()
                )
        );

        ChatSessionContextState state = objectMapper.readValue(session.getContextStateJson(), ChatSessionContextState.class);
        assertThat(state.getHousing().getActiveBranchKey()).isEqualTo("housing-cash");
        assertThat(state.getHousing().getAnchorQuestion()).isEqualTo("주거 지원");
        assertThat(state.getHousing().getRecentTopics()).containsExactly("주거", "월세");
        assertThat(state.getHousing().getRecentPolicyTitles()).contains("청년월세 한시 특별지원");
        assertThat(state.getHousing().getRecentPolicyIds()).contains(1829L);
    }

    @Test
    @DisplayName("명시 branch가 없어도 주거 leaf 질문이면 active branch를 추론 저장한다")
    void captureHousingAnswerInfersActiveBranchFromQuestion() throws Exception {
        ChatSession session = ChatSession.builder()
                .id(10L)
                .userKey("user-key-1")
                .build();
        when(chatSessionRepository.findById(10L)).thenReturn(Optional.of(session));

        chatSessionContextStateService.captureHousingAnswer(
                10L,
                "서울 월세 지원 알려줘",
                null,
                List.of(
                        ChatReferenceResponse.builder().serviceId(1829L).title("청년월세 한시 특별지원").build()
                )
        );

        ChatSessionContextState state = objectMapper.readValue(session.getContextStateJson(), ChatSessionContextState.class);
        assertThat(state.getHousing().getActiveBranchKey()).isEqualTo("housing-cash");
        assertThat(state.getHousing().getRecentTopics()).contains("월세");
    }

    @Test
    @DisplayName("일반 지원 질문은 주거 context로 오인 저장하지 않는다")
    void captureHousingAnswerDoesNotInferHousingFromGenericSupportQuestion() {
        ChatSession session = ChatSession.builder()
                .id(10L)
                .userKey("user-key-1")
                .build();

        chatSessionContextStateService.captureHousingAnswer(
                10L,
                "창업 지원 정책 알려줘",
                null,
                List.of(
                        ChatReferenceResponse.builder().serviceId(12964L).title("청년 창업지원카드 사업").build()
                )
        );

        assertThat(session.getContextStateJson()).isNull();
        verify(chatSessionRepository, never()).findById(10L);
    }

    @Test
    @DisplayName("대화 memory는 질문, 답변 요지, 추천 정책을 세션 context state에 압축 저장한다")
    void captureConversationMemoryStoresCompactedSummary() throws Exception {
        ChatSession session = ChatSession.builder().id(10L).userKey("user-key-1").build();
        when(chatSessionRepository.findById(10L)).thenReturn(Optional.of(session));

        chatSessionContextStateService.captureConversationMemory(
                10L,
                "서울 월세 지원 알려줘",
                "청년월세 한시 특별지원을 먼저 확인해보세요.",
                List.of(
                        ChatReferenceResponse.builder().serviceId(1829L).title("청년월세 한시 특별지원").build()
                )
        );

        ChatSessionContextState state = objectMapper.readValue(session.getContextStateJson(), ChatSessionContextState.class);
        assertThat(state.getMemory()).isNotNull();
        assertThat(state.getMemory().getSummary())
                .contains("최근 질문 흐름: 서울 월세 지원 알려줘")
                .contains("최근 답변 요지: 청년월세 한시 특별지원을 먼저 확인해보세요.")
                .contains("누적 추천 정책: 청년월세 한시 특별지원");
        assertThat(state.getMemory().getRecentUserQuestions()).containsExactly("서울 월세 지원 알려줘");
        assertThat(state.getMemory().getRecentPolicyTitles()).containsExactly("청년월세 한시 특별지원");
    }
}
