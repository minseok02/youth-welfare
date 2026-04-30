package com.example.welfare.recommend.gateway;

import com.example.welfare.recommend.dto.RecommendationUserSnapshot;
import com.example.welfare.recommend.dto.ScoredCandidate;
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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
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

    @Value("${recommend.ai.replay-trace.enabled:false}")
    private boolean replayTraceEnabled;

    @Value("${recommend.ai.replay-seed:}")
    private String replaySeedValue;

    private static final int AI_TOP_N = 15; // 상위 N건만 AI 호출 (비용 절감 + 누락 방지)

    @Override
    public List<ScoredCandidate> score(String clusterId, List<ScoredCandidate> candidates, RecommendationUserSnapshot user) {
        // rule_weighted_score 내림차순으로 상위 N개만 AI 호출
        List<ScoredCandidate> topCandidates = candidates.stream()
                .sorted((a, b) -> Double.compare(b.getRuleWeightedScore(), a.getRuleWeightedScore()))
                .limit(AI_TOP_N)
                .collect(Collectors.toList());

        try {
            String prompt = buildUserPrompt(topCandidates, user);
            Long replaySeed = replaySeedOrNull();
            logReplayTrace(clusterId, topCandidates, user, prompt, replaySeed);
            AiCallResult callResult = callOpenAi(prompt, replaySeed);
            logReplayTraceResponse(clusterId, prompt, callResult);
            AiResponse response = callResult.aiResponse();

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

    void logReplayTrace(String clusterId, List<ScoredCandidate> topCandidates, RecommendationUserSnapshot user, String prompt, Long replaySeed) {
        if (!replayTraceEnabled) {
            return;
        }
        log.info(
                "[RealtimeAiGateway][replay-trace] clusterId={} ageGroup={} region={} income={} employment={} candidateIds={} candidateRuleScores={} promptSha256={} replaySeed={}",
                clusterId,
                ageGroup(user),
                regionLabel(user),
                incomeRangeLabel(user),
                employmentLabel(user),
                candidateIds(topCandidates),
                candidateRuleScores(topCandidates),
                sha256Hex(prompt),
                replaySeed != null ? replaySeed : "none"
        );
    }

    void logReplayTraceResponse(String clusterId, String prompt, AiCallResult callResult) {
        if (!replayTraceEnabled) {
            return;
        }
        log.info(
                "[RealtimeAiGateway][replay-trace-response] clusterId={} promptSha256={} responseId={} systemFingerprint={} responseSeed={} resultsCount={}",
                clusterId,
                sha256Hex(prompt),
                callResult.responseId() != null ? callResult.responseId() : "none",
                callResult.systemFingerprint() != null ? callResult.systemFingerprint() : "none",
                callResult.responseSeed() != null ? callResult.responseSeed() : "none",
                callResult.aiResponse() != null && callResult.aiResponse().getResults() != null
                        ? callResult.aiResponse().getResults().size()
                        : 0
        );
    }

    static String candidateIds(List<ScoredCandidate> topCandidates) {
        return topCandidates.stream()
                .map(candidate -> String.valueOf(candidate.getService().getId()))
                .collect(Collectors.joining(","));
    }

    static String candidateRuleScores(List<ScoredCandidate> topCandidates) {
        return topCandidates.stream()
                .map(candidate -> candidate.getService().getId() + ":" + String.format(java.util.Locale.ROOT, "%.2f", candidate.getRuleWeightedScore()))
                .collect(Collectors.joining(","));
    }

    static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte value : bytes) {
                sb.append(String.format(java.util.Locale.ROOT, "%02x", value));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private String ageGroup(RecommendationUserSnapshot user) {
        int age = user.resolvedAge();
        return age < 25 ? "19-24세" : age < 30 ? "25-29세" : "30-34세";
    }

    private String regionLabel(RecommendationUserSnapshot user) {
        return user.sido() != null ? user.sido() : "지역 미입력";
    }

    private String incomeRangeLabel(RecommendationUserSnapshot user) {
        return user.incomeLevel() != null
                ? user.incomeLevel() + "분위" : "소득 미입력";
    }

    private String employmentLabel(RecommendationUserSnapshot user) {
        return user.employmentStatus() != null
                ? user.employmentStatus() : "취업상태 미입력";
    }

    private static final String SYSTEM_PROMPT =
            "당신은 한국 청년 복지 정책 추천 전문가입니다. " +
            "사용자의 특성에 맞는 정책 적합도를 0~100점으로 평가합니다. " +
            "반드시 JSON만 응답하고, 입력된 모든 정책에 대해 빠짐없이 평가해야 합니다.";

    Long replaySeedOrNull() {
        if (replaySeedValue == null || replaySeedValue.isBlank()) {
            return null;
        }
        return Long.parseLong(replaySeedValue.trim());
    }

    private String buildUserPrompt(List<ScoredCandidate> topCandidates, RecommendationUserSnapshot user) {
        // NFR-02-12: 개인식별정보 전송 금지 — 범주값만 전송
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
                ageGroup(user), regionLabel(user), incomeRangeLabel(user), employmentLabel(user),
                topCandidates.size(), policyList,
                topCandidates.size());
    }

    private AiCallResult callOpenAi(String userPrompt, Long replaySeed) {
        try {
            Map<String, Object> requestBody = buildRequestBody(userPrompt, replaySeed, model);

            String responseBody = webClient.post()
                    .uri("https://api.openai.com/v1/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return parseAiCallResult(objectMapper, responseBody);

        } catch (Exception e) {
            log.warn("[RealtimeAiGateway] OpenAI 파싱 실패: {}", e.getMessage());
            return AiCallResult.empty();
        }
    }

    static Map<String, Object> buildRequestBody(String userPrompt, Long replaySeed, String modelName) {
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", modelName);
        requestBody.put("messages", List.of(
                Map.of("role", "system", "content", SYSTEM_PROMPT),
                Map.of("role", "user", "content", userPrompt)
        ));
        requestBody.put("temperature", 0.3);
        requestBody.put("response_format", Map.of("type", "json_object"));
        if (replaySeed != null) {
            requestBody.put("seed", replaySeed);
        }
        return requestBody;
    }

    static AiCallResult parseAiCallResult(ObjectMapper objectMapper, String responseBody) throws Exception {
        Map<?, ?> parsed = objectMapper.readValue(responseBody, Map.class);
        List<?> choices = (List<?>) parsed.get("choices");
        if (choices == null || choices.isEmpty()) {
            return AiCallResult.empty();
        }

        Map<?, ?> message = (Map<?, ?>) ((Map<?, ?>) choices.get(0)).get("message");
        String content = (String) message.get("content");
        AiResponse aiResponse = objectMapper.readValue(content, AiResponse.class);

        return new AiCallResult(
                aiResponse,
                stringValue(parsed.get("id")),
                stringValue(parsed.get("system_fingerprint")),
                longValue(parsed.get("seed"))
        );
    }

    private static String stringValue(Object value) {
        return value != null ? String.valueOf(value) : null;
    }

    private static Long longValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    record AiCallResult(AiResponse aiResponse, String responseId, String systemFingerprint, Long responseSeed) {
        static AiCallResult empty() {
            return new AiCallResult(null, null, null, null);
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
