package com.example.welfare.chat.gateway;

import com.example.welfare.chat.dto.ChatAiResult;
import com.example.welfare.chat.dto.ChatPolicyCandidate;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChatAiGatewayTest {

    private ChatAiGateway chatAiGateway;

    @BeforeEach
    void setUp() {
        chatAiGateway = new ChatAiGateway(null, new ObjectMapper());
    }

    @Test
    @DisplayName("AI 응답 파서는 후보 목록에 없는 service_id를 버린다")
    void parseContentFiltersUnknownServiceIds() {
        List<ChatPolicyCandidate> candidates = List.of(
                ChatPolicyCandidate.builder()
                        .serviceId(1829L)
                        .title("청년월세 한시 특별지원")
                        .build()
        );

        String content = """
                {
                  "answer": "청년월세 한시 특별지원을 먼저 확인해보세요.",
                  "needs_clarification": false,
                  "references": [
                    {"service_id": 1829, "reason": "주거비 부담 완화와 연결됩니다."},
                    {"service_id": 9999, "reason": "허용되지 않은 정책입니다."}
                  ]
                }
                """;

        ChatAiResult result = chatAiGateway.parseContent(content, candidates);

        assertThat(result).isNotNull();
        assertThat(result.getReferences()).hasSize(1);
        assertThat(result.getReferences().get(0).getServiceId()).isEqualTo(1829L);
        assertThat(result.getReferences().get(0).getTitle()).isEqualTo("청년월세 한시 특별지원");
    }

    @Test
    @DisplayName("AI 응답 파서는 answer가 비어 있으면 무효로 처리한다")
    void parseContentReturnsNullWhenAnswerBlank() {
        List<ChatPolicyCandidate> candidates = List.of(
                ChatPolicyCandidate.builder()
                        .serviceId(1829L)
                        .title("청년월세 한시 특별지원")
                        .build()
        );

        String content = """
                {
                  "answer": " ",
                  "needs_clarification": false,
                  "references": [
                    {"service_id": 1829, "reason": "주거비 부담 완화와 연결됩니다."}
                  ]
                }
                """;

        assertThat(chatAiGateway.parseContent(content, candidates)).isNull();
    }
}
