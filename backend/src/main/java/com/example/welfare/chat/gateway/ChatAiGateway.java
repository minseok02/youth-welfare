package com.example.welfare.chat.gateway;

import com.example.welfare.chat.dto.ChatAiResult;
import com.example.welfare.chat.dto.ChatPolicyCandidate;
import com.example.welfare.chat.dto.response.ChatReferenceResponse;
import com.example.welfare.chat.entity.ChatMessage;
import com.example.welfare.user.entity.User;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatAiGateway {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("(?i)\\b[0-9a-z._%+-]+@[0-9a-z.-]+\\.[a-z]{2,}\\b");
    private static final Pattern PHONE_PATTERN =
            Pattern.compile("\\b01[0-9][- ]?[0-9]{3,4}[- ]?[0-9]{4}\\b");
    private static final Pattern BIRTH_DATE_PATTERN =
            Pattern.compile("\\b(?:19|20)\\d{2}[-./](?:0[1-9]|1[0-2])[-./](?:0[1-9]|[12]\\d|3[01])\\b");

    private static final String SYSTEM_PROMPT =
            "당신은 한국 청년 복지 정책 상담 보조입니다. " +
            "반드시 제공된 정책 후보 안에서만 답변하고, 자격 또는 지급 확정 표현을 하지 마세요. " +
            "정책 후보 밖의 service_id를 만들지 말고 반드시 JSON만 응답하세요.";

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${openai.api-key:}")
    private String apiKey;

    @Value("${openai.model:gpt-4o-mini}")
    private String model;

    @Value("${openai.timeout:30000}")
    private long timeoutMillis;

    public ChatAiResult generateAnswer(
            User user,
            String question,
            List<ChatMessage> recentMessages,
            List<ChatPolicyCandidate> candidates,
            Map<Long, String> evidenceByServiceId) {
        if (!StringUtils.hasText(apiKey) || candidates.isEmpty()) {
            return null;
        }

        try {
            String responseBody = callOpenAi(buildUserPrompt(user, question, recentMessages, candidates, evidenceByServiceId));
            return parseResponse(responseBody, candidates, evidenceByServiceId);
        } catch (Exception e) {
            log.warn("[ChatAiGateway] OpenAI 호출 실패, fallback 사용: {}", e.getMessage());
            return null;
        }
    }

    public ChatAiResult generateAnswer(
            User user,
            String question,
            List<ChatMessage> recentMessages,
            List<ChatPolicyCandidate> candidates) {
        return generateAnswer(user, question, recentMessages, candidates, Map.of());
    }

    ChatAiResult parseResponse(String responseBody, List<ChatPolicyCandidate> candidates) {
        return parseResponse(responseBody, candidates, Map.of());
    }

    ChatAiResult parseResponse(String responseBody, List<ChatPolicyCandidate> candidates, Map<Long, String> evidenceByServiceId) {
        if (!StringUtils.hasText(responseBody)) {
            return null;
        }

        try {
            Map<?, ?> parsed = objectMapper.readValue(responseBody, Map.class);
            List<?> choices = (List<?>) parsed.get("choices");
            if (choices == null || choices.isEmpty()) {
                return null;
            }

            Map<?, ?> message = (Map<?, ?>) ((Map<?, ?>) choices.get(0)).get("message");
            if (message == null) {
                return null;
            }

            String content = (String) message.get("content");
            return parseContent(content, candidates, evidenceByServiceId);
        } catch (Exception e) {
            log.warn("[ChatAiGateway] OpenAI 응답 파싱 실패: {}", e.getMessage());
            return null;
        }
    }

    ChatAiResult parseContent(String content, List<ChatPolicyCandidate> candidates) {
        return parseContent(content, candidates, Map.of());
    }

    ChatAiResult parseContent(String content, List<ChatPolicyCandidate> candidates, Map<Long, String> evidenceByServiceId) {
        if (!StringUtils.hasText(content)) {
            return null;
        }

        try {
            AiResponse payload = objectMapper.readValue(content, AiResponse.class);
            if (!StringUtils.hasText(payload.getAnswer())) {
                return null;
            }

            Map<Long, ChatPolicyCandidate> candidateMap = candidates.stream()
                    .collect(Collectors.toMap(
                            ChatPolicyCandidate::getServiceId,
                            candidate -> candidate,
                            (left, right) -> left,
                            LinkedHashMap::new
                    ));

            Map<Long, ChatReferenceResponse> referenceMap = new LinkedHashMap<>();
            if (payload.getReferences() != null) {
                payload.getReferences().stream()
                        .map(reference -> toReference(reference, candidateMap, evidenceByServiceId))
                        .filter(Objects::nonNull)
                        .forEach(reference -> referenceMap.putIfAbsent(reference.getServiceId(), reference));
            }
            List<ChatReferenceResponse> references = List.copyOf(referenceMap.values());

            return ChatAiResult.builder()
                    .answer(payload.getAnswer().trim())
                    .needsClarification(payload.isNeedsClarification())
                    .references(references)
                    .build();
        } catch (JsonProcessingException e) {
            log.warn("[ChatAiGateway] JSON content 파싱 실패: {}", e.getMessage());
            return null;
        }
    }

    private String buildUserPrompt(
            User user,
            String question,
            List<ChatMessage> recentMessages,
            List<ChatPolicyCandidate> candidates,
            Map<Long, String> evidenceByServiceId) {
        String ageGroup = resolveAgeGroup(user);
        String region = buildRegion(user);
        String incomeRange = user.getIncomeLevel() != null ? user.getIncomeLevel() + "분위" : "미입력";
        String employment = StringUtils.hasText(user.getEmploymentStatus()) ? user.getEmploymentStatus().trim() : "미입력";

        String historyBlock = recentMessages.isEmpty()
                ? "없음"
                : recentMessages.stream()
                .map(message -> message.getRole().name() + ": " + trimToLength(redactSensitiveText(message.getContent()), 200))
                .collect(Collectors.joining("\n"));

        String candidateBlock = candidates.stream()
                .map(candidate -> String.format(
                        "- service_id:%d | title:%s | host_org:%s | support:%s | description:%s | evidence:%s",
                        candidate.getServiceId(),
                        candidate.getTitle(),
                        nullToPlaceholder(candidate.getHostOrg()),
                        nullToPlaceholder(trimToLength(candidate.getSupportContent(), 120)),
                        nullToPlaceholder(trimToLength(candidate.getDescription(), 120)),
                        nullToPlaceholder(trimToLength(evidenceByServiceId.get(candidate.getServiceId()), 120))
                ))
                .collect(Collectors.joining("\n"));

        return String.format("""
                [사용자 범주 정보]
                나이대: %s
                지역: %s
                소득분위: %s
                취업상태: %s

                [최근 대화]
                %s

                [현재 질문]
                %s

                [정책 후보]
                %s

                [응답 규칙]
                - 반드시 아래 JSON 객체 하나만 반환
                - references의 service_id는 정책 후보에 있는 값만 사용
                - 질문이 모호하면 needs_clarification=true 와 보충질문을 answer에 작성
                - 정책 후보를 추천할 때는 이유를 1문장으로 작성

                [응답 형식]
                {
                  "answer": "string",
                  "needs_clarification": false,
                  "references": [
                    {
                      "service_id": 1,
                      "reason": "질문과 연결된 이유"
                    }
                  ]
                }
                """,
                ageGroup,
                region,
                incomeRange,
                employment,
                historyBlock,
                redactSensitiveText(question.trim()),
                candidateBlock
        );
    }

    String redactSensitiveText(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        String redacted = EMAIL_PATTERN.matcher(value).replaceAll("[REDACTED_EMAIL]");
        redacted = PHONE_PATTERN.matcher(redacted).replaceAll("[REDACTED_PHONE]");
        redacted = BIRTH_DATE_PATTERN.matcher(redacted).replaceAll("[REDACTED_BIRTH_DATE]");
        return redacted;
    }

    private String callOpenAi(String userPrompt) {
        Map<String, Object> requestBody = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", SYSTEM_PROMPT),
                        Map.of("role", "user", "content", userPrompt)
                ),
                "temperature", 0.2,
                "response_format", Map.of("type", "json_object")
        );

        return webClient.post()
                .uri("https://api.openai.com/v1/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .block(Duration.ofMillis(timeoutMillis));
    }

    private ChatReferenceResponse toReference(
            AiReference reference,
            Map<Long, ChatPolicyCandidate> candidateMap,
            Map<Long, String> evidenceByServiceId) {
        if (reference == null || reference.getServiceId() == null) {
            return null;
        }

        ChatPolicyCandidate candidate = candidateMap.get(reference.getServiceId());
        if (candidate == null) {
            return null;
        }

        String reason = StringUtils.hasText(reference.getReason())
                ? trimToLength(reference.getReason().trim(), 120)
                : "질문과 직접 연결되는 청년 정책입니다.";

        return ChatReferenceResponse.builder()
                .serviceId(candidate.getServiceId())
                .title(candidate.getTitle())
                .reason(reason)
                .evidence(trimToLength(evidenceByServiceId.get(candidate.getServiceId()), 120))
                .build();
    }

    private String resolveAgeGroup(User user) {
        if (user.getBirthDate() == null) {
            return "미입력";
        }
        int age = LocalDate.now().getYear() - user.getBirthDate().getYear();
        if (age < 25) {
            return "19-24세";
        }
        if (age < 30) {
            return "25-29세";
        }
        return "30-34세";
    }

    private String buildRegion(User user) {
        String sido = StringUtils.hasText(user.getSido()) ? user.getSido().trim() : null;
        String sgg = StringUtils.hasText(user.getSgg()) ? user.getSgg().trim() : null;
        if (sido == null && sgg == null) {
            return "미입력";
        }
        if (sido != null && sgg != null) {
            return sido + " " + sgg;
        }
        return sido != null ? sido : sgg;
    }

    private String trimToLength(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private String nullToPlaceholder(String value) {
        return StringUtils.hasText(value) ? value : "없음";
    }

    @Getter
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class AiResponse {
        private String answer;
        @JsonProperty("needs_clarification")
        private boolean needsClarification;
        private List<AiReference> references;
    }

    @Getter
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class AiReference {
        @JsonProperty("service_id")
        private Long serviceId;
        private String reason;
    }
}
