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
}
