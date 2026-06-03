package com.example.welfare.chat.gateway;

import com.example.welfare.chat.dto.ChatAiResult;
import com.example.welfare.chat.dto.ChatPolicyCandidate;
import com.example.welfare.chat.entity.ChatMessage;
import com.example.welfare.chat.entity.ChatMessageRole;
import com.example.welfare.user.entity.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

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
    @DisplayName("AI 응답 파서는 grounding evidence를 참조 카드에 포함한다")
    void parseContentIncludesEvidence() {
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
                    {"service_id": 1829, "reason": "주거비 부담 완화와 연결됩니다."}
                  ]
                }
                """;

        ChatAiResult result = chatAiGateway.parseContent(
                content,
                candidates,
                java.util.Map.of(1829L, "월세 부담을 낮추는 지원을 제공합니다.")
        );

        assertThat(result).isNotNull();
        assertThat(result.getReferences().get(0).getEvidence()).isEqualTo("월세 부담을 낮추는 지원을 제공합니다.");
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

    @Test
    @DisplayName("AI 프롬프트 전송 전 이메일, 전화번호, 생년월일을 마스킹한다")
    void redactSensitiveTextMasksDirectIdentifiers() {
        String source = "메일 test.user@example.com, 전화 010-1234-5678, 생년월일 2001-04-30, 주민번호 900101-1234567, 계좌번호 123-456-789012, 이름 김민수, 주소 인천광역시 중구 은하수로 10, 학교명 인천대학교";

        String redacted = chatAiGateway.redactSensitiveText(source);

        assertThat(redacted).contains("[REDACTED_EMAIL]");
        assertThat(redacted).contains("[REDACTED_PHONE]");
        assertThat(redacted).contains("[REDACTED_BIRTH_DATE]");
        assertThat(redacted).contains("[REDACTED_RRN]");
        assertThat(redacted).contains("[REDACTED_ACCOUNT]");
        assertThat(redacted).contains("[REDACTED_NAME]");
        assertThat(redacted).contains("[REDACTED_ADDRESS]");
        assertThat(redacted).contains("[REDACTED_ORG]");
        assertThat(redacted).doesNotContain("test.user@example.com");
        assertThat(redacted).doesNotContain("010-1234-5678");
        assertThat(redacted).doesNotContain("2001-04-30");
        assertThat(redacted).doesNotContain("900101-1234567");
        assertThat(redacted).doesNotContain("123-456-789012");
        assertThat(redacted).doesNotContain("김민수");
        assertThat(redacted).doesNotContain("인천광역시 중구 은하수로 10");
        assertThat(redacted).doesNotContain("인천대학교");
    }

    @Test
    @DisplayName("챗봇 시스템 프롬프트는 모름과 비확정 답변 원칙을 고정한다")
    void systemPromptKeepsGroundedNonCommittalContract() {
        assertThat(ChatAiGateway.systemPrompt())
                .contains("모르면 모른다고 답")
                .contains("자격 또는 지급 확정 표현을 하지 말고")
                .contains("정책 후보 밖의 service_id를 만들지 말고")
                .contains("반드시 JSON만 응답");
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("챗봇 OpenAI 요청 body는 JSON object 응답 형식을 고정한다")
    void buildRequestBodyPinsJsonOnlyContract() {
        Map<String, Object> request = ChatAiGateway.buildRequestBody("gpt-4o-mini", ChatAiGateway.systemPrompt(), "prompt");

        assertThat(request)
                .containsEntry("model", "gpt-4o-mini")
                .containsEntry("temperature", 0.2)
                .containsEntry("response_format", Map.of("type", "json_object"));

        List<Map<String, String>> messages = (List<Map<String, String>>) request.get("messages");
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0)).containsEntry("role", "system");
        assertThat(messages.get(0)).containsEntry("content", ChatAiGateway.systemPrompt());
        assertThat(messages.get(1)).containsEntry("role", "user");
        assertThat(messages.get(1)).containsEntry("content", "prompt");
    }

    @Test
    @DisplayName("챗봇 사용자 프롬프트는 민감정보를 줄이고 확인 필요 원칙을 포함한다")
    void buildUserPromptRedactsIdentifiersAndPinsAnswerRules() {
        User user = User.builder()
                .email("user@example.com")
                .passwordHash("encoded")
                .name("김민수")
                .birthDate(LocalDate.of(2001, 4, 30))
                .sido("인천광역시")
                .sgg("중구")
                .incomeLevel((byte) 5)
                .employmentStatus("미취업")
                .build();
        List<ChatMessage> recentMessages = List.of(
                ChatMessage.builder()
                        .role(ChatMessageRole.USER)
                        .content("이름 김민수, 주소 인천광역시 중구 은하수로 10, 학교명 인천대학교입니다.")
                        .build()
        );
        List<ChatPolicyCandidate> candidates = List.of(
                ChatPolicyCandidate.builder()
                        .serviceId(1829L)
                        .title("청년월세 한시 특별지원")
                        .hostOrg("국토교통부")
                        .supportContent("월세 비용을 지원합니다.")
                        .description("청년 주거비 부담 완화")
                        .build()
        );

        String prompt = chatAiGateway.buildUserPrompt(
                user,
                "25_29",
                "제 이름 김민수이고 집 주소는 인천광역시 중구 은하수로 10인데 학교 서울대학교 기준으로 바로 받을 수 있나요?",
                recentMessages,
                candidates,
                java.util.Map.of(1829L, "월세 부담을 낮추는 지원을 제공합니다.")
        );

        assertThat(prompt)
                .contains("[REDACTED_NAME]")
                .contains("[REDACTED_ADDRESS]")
                .contains("[REDACTED_ORG]")
                .contains("answer는 제공된 정책 후보와 evidence 안에서만 근거를 말할 것")
                .contains("자격 충족 여부나 실제 지급 확정처럼 단정하지 말 것")
                .contains("조건이 불명확하면 추측하지 말고 확인이 필요한 항목을 짚을 것")
                .doesNotContain("김민수")
                .doesNotContain("인천광역시 중구 은하수로 10")
                .doesNotContain("인천대학교");
    }

    @Test
    @DisplayName("챗봇 사용자 프롬프트는 대화 연속 맥락 블록을 별도로 포함한다")
    void buildUserPromptIncludesConversationSummary() {
        User user = User.builder()
                .email("user@example.com")
                .passwordHash("encoded")
                .name("김민수")
                .birthDate(LocalDate.of(2001, 4, 30))
                .sido("인천광역시")
                .sgg("중구")
                .employmentStatus("미취업")
                .build();
        List<ChatPolicyCandidate> candidates = List.of(
                ChatPolicyCandidate.builder()
                        .serviceId(2451L)
                        .title("청년전세임대")
                        .hostOrg("LH")
                        .description("청년 전세 주거 안정을 지원합니다.")
                        .build()
        );

        String prompt = chatAiGateway.buildUserPrompt(
                user,
                "25_29",
                "그럼 전세는?",
                List.of(),
                candidates,
                Map.of(2451L, "전세 주거 안정을 지원합니다."),
                "직전 사용자 질문: 서울 월세 지원 알려줘\n직전 추천 정책: 청년월세 한시 특별지원"
        );

        assertThat(prompt)
                .contains("[대화 연속 맥락]")
                .contains("직전 사용자 질문: 서울 월세 지원 알려줘")
                .contains("직전 추천 정책: 청년월세 한시 특별지원");
    }
}
