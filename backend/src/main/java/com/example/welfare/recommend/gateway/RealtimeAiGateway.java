package com.example.welfare.recommend.gateway;

import com.example.welfare.recommend.dto.ScoredCandidate;
import com.example.welfare.user.entity.User;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * OpenAI GPT-4o-mini 실시간 단건 호출 (1차 구현)
 * 개인 식별 정보 전송 금지 — 군집 범주값(나이대/지역/소득범위/취업상태)만 전송
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RealtimeAiGateway implements AiRecommendationGateway {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${openai.api-key}")
    private String apiKey;

    @Value("${openai.model:gpt-4o-mini}")
    private String model;

    private static final int AI_TOP_N = 15; // 상위 N건만 AI 호출 (비용 절감 + 누락 방지)

    @Override
    public List<ScoredCandidate> score(String clusterId, List<ScoredCandidate> candidates, User user) {
        // rule_weighted_score 내림차순으로 상위 N개만 AI 호출
        List<ScoredCandidate> topCandidates = candidates.stream()
                .sorted((a, b) -> Double.compare(b.getRuleWeightedScore(), a.getRuleWeightedScore()))
                .limit(AI_TOP_N)
                .collect(Collectors.toList());

        try {
            String prompt = buildUserPrompt(topCandidates, user);
            AiResponse response = callOpenAi(prompt);

            if (response != null && response.getResults() != null) {
                Map<Long, AiResponse.Result> resultMap = response.getResults().stream()
                        .collect(Collectors.toMap(AiResponse.Result::getServiceId, r -> r));

                topCandidates.forEach(c -> {
                    AiResponse.Result r = resultMap.get(c.getService().getId());
                    if (r != null) {
                        c.setAiScore((double) r.getScore());
                        c.setAiReason(r.getReason());
                    }
                });
            }
        } catch (Exception e) {
            log.warn("[RealtimeAiGateway] AI 호출 실패, fallback to rule-only: {}", e.getMessage());
            // aiScore = null 유지 → ReRankingService에서 rule만 사용
        }

        return candidates;
    }

    private static final String SYSTEM_PROMPT =
            "당신은 한국 청년 복지 정책 추천 전문가입니다. " +
            "사용자의 특성에 맞는 정책 적합도를 0~100점으로 평가합니다. " +
            "반드시 JSON만 응답하고, 입력된 모든 정책에 대해 빠짐없이 평가해야 합니다.";

    private String buildUserPrompt(List<ScoredCandidate> topCandidates, User user) {
        // NFR-02-12: 개인식별정보 전송 금지 — 범주값만 전송
        int age = user.getBirthDate() != null
                ? LocalDate.now().getYear() - user.getBirthDate().getYear() : 25;
        String ageGroup = age < 25 ? "19-24세" : age < 30 ? "25-29세" : "30-34세";
        String region = user.getSido() != null ? user.getSido() : "지역 미입력";
        String incomeRange = user.getIncomeLevel() != null
                ? user.getIncomeLevel() + "분위" : "소득 미입력";
        String employment = user.getEmploymentStatus() != null
                ? user.getEmploymentStatus() : "취업상태 미입력";

        StringBuilder policyList = new StringBuilder();
        topCandidates.forEach(c -> {
            String desc = c.getService().getDescription();
            String shortDesc = (desc != null && desc.length() > 80)
                    ? desc.substring(0, 80) : (desc != null ? desc : "");
            policyList
                    .append("- id:").append(c.getService().getId())
                    .append(" | 제목:").append(c.getService().getTitle())
                    .append(" | 분류:").append(c.getService().getUnifiedCategory())
                    .append(" | 내용:").append(shortDesc)
                    .append("\n");
        });

        return String.format("""
                [사용자 특성]
                나이대: %s, 거주지역: %s, 소득: %s, 취업상태: %s

                [평가할 정책 목록 — 아래 %d개를 반드시 모두 평가]
                %s
                [응답 형식] 누락 없이 전체 %d개 평가:
                {"results": [{"service_id": 숫자, "score": 0~100정수, "reason": "사용자 특성 기준 1문장 이유"}]}
                """,
                ageGroup, region, incomeRange, employment,
                topCandidates.size(), policyList,
                topCandidates.size());
    }

    private AiResponse callOpenAi(String userPrompt) {
        try {
            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "messages", List.of(
                            Map.of("role", "system", "content", SYSTEM_PROMPT),
                            Map.of("role", "user", "content", userPrompt)
                    ),
                    "temperature", 0.3,
                    "response_format", Map.of("type", "json_object")
            );

            String responseBody = webClient.post()
                    .uri("https://api.openai.com/v1/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            // OpenAI 응답에서 content 파싱
            Map<?, ?> parsed = objectMapper.readValue(responseBody, Map.class);
            List<?> choices = (List<?>) parsed.get("choices");
            if (choices == null || choices.isEmpty()) return null;

            Map<?, ?> message = (Map<?, ?>) ((Map<?, ?>) choices.get(0)).get("message");
            String content = (String) message.get("content");

            return objectMapper.readValue(content, AiResponse.class);

        } catch (Exception e) {
            log.warn("[RealtimeAiGateway] OpenAI 파싱 실패: {}", e.getMessage());
            return null;
        }
    }

    @Getter
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class AiResponse {
        private List<Result> results;

        @Getter
        @JsonIgnoreProperties(ignoreUnknown = true)
        static class Result {
            @JsonProperty("service_id")
            private Long serviceId;
            private int score;
            private String reason;
        }
    }
}
