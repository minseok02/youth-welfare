package com.example.welfare.chat.service;

import com.example.welfare.chat.dto.response.ChatBranchOptionResponse;
import com.example.welfare.global.util.SearchKeywordSupport;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ChatBranchCatalog {

    private static final List<BranchDefinition> DEFINITIONS = List.of(
            new BranchDefinition(
                    "housing-stability",
                    "주거",
                    "장기 주거 안정",
                    "전세임대, 공공임대, 장기 거주 안정 쪽으로 볼까요?",
                    Set.of("주거", "집", "거주"),
                    Set.of("전세", "임대", "공공임대", "주택"),
                    "주거",
                    List.of("전세", "임대", "공공임대", "주거 안정")
            ),
            new BranchDefinition(
                    "housing-cash",
                    "주거",
                    "즉시 현금성 지원",
                    "월세, 주거비, 현금성 지원 중심으로 찾아볼까요?",
                    Set.of("주거", "집", "거주"),
                    Set.of("월세", "주거비", "현금", "지원금"),
                    "주거",
                    List.of("월세", "주거비", "지원금")
            ),
            new BranchDefinition(
                    "housing-subscription",
                    "주거",
                    "청약/입주 정보",
                    "청약이나 입주 모집 정보 쪽으로 좁혀볼까요?",
                    Set.of("주거", "집", "거주"),
                    Set.of("청약", "입주", "모집공고"),
                    "주거",
                    List.of("청약", "입주", "모집")
            ),
            new BranchDefinition(
                    "job-employment",
                    "일자리",
                    "채용/인턴",
                    "채용 공고나 인턴 기회 중심으로 찾아볼까요?",
                    Set.of("일자리", "취업", "직업"),
                    Set.of("채용", "인턴", "구직"),
                    "일자리",
                    List.of("채용", "인턴", "구직", "취업")
            ),
            new BranchDefinition(
                    "job-training",
                    "일자리",
                    "훈련/교육",
                    "직업훈련이나 역량 교육 중심으로 찾아볼까요?",
                    Set.of("일자리", "취업", "직업"),
                    Set.of("훈련", "교육", "직업훈련", "역량"),
                    "교육·직업훈련",
                    List.of("훈련", "교육", "직업훈련", "역량")
            ),
            new BranchDefinition(
                    "job-startup",
                    "일자리",
                    "창업/금융",
                    "창업 지원이나 사업 자금 쪽으로 좁혀볼까요?",
                    Set.of("일자리", "취업", "직업"),
                    Set.of("창업", "금융", "사업", "자금"),
                    "일자리",
                    List.of("창업", "금융", "사업", "자금")
            )
    );

    public List<BranchDefinition> suggestBranches(String question) {
        List<String> tokens = SearchKeywordSupport.extractTokens(question);
        if (tokens.isEmpty() || tokens.size() > 3) {
            return List.of();
        }

        Optional<String> topLevel = detectBroadTopLevel(tokens);
        if (topLevel.isEmpty()) {
            return List.of();
        }
        if (hasSpecificLeafToken(tokens, topLevel.get())) {
            return List.of();
        }
        return DEFINITIONS.stream()
                .filter(definition -> definition.topLevelLabel().equals(topLevel.get()))
                .toList();
    }

    public Optional<BranchDefinition> findByKey(String branchKey) {
        if (branchKey == null || branchKey.isBlank()) {
            return Optional.empty();
        }
        return DEFINITIONS.stream()
                .filter(definition -> definition.branchKey().equals(branchKey.trim()))
                .findFirst();
    }

    public List<ChatBranchOptionResponse> toResponses(List<BranchDefinition> definitions) {
        return definitions.stream()
                .map(definition -> ChatBranchOptionResponse.builder()
                        .branchKey(definition.branchKey())
                        .label(definition.label())
                        .guideQuestion(definition.guideQuestion())
                        .build())
                .toList();
    }

    public List<ChatBranchOptionResponse> toResponsesByKeys(List<String> branchKeys) {
        if (branchKeys == null || branchKeys.isEmpty()) {
            return List.of();
        }
        return branchKeys.stream()
                .map(this::findByKey)
                .flatMap(Optional::stream)
                .map(definition -> ChatBranchOptionResponse.builder()
                        .branchKey(definition.branchKey())
                        .label(definition.label())
                        .guideQuestion(definition.guideQuestion())
                .build())
                .collect(Collectors.toList());
    }

    public boolean isHousingBranchKey(String branchKey) {
        return findByKey(branchKey)
                .map(definition -> definition.topLevelLabel().equals("주거"))
                .orElse(false);
    }

    public List<String> filterHousingBranchKeys(List<String> branchKeys) {
        if (branchKeys == null || branchKeys.isEmpty()) {
            return List.of();
        }
        return branchKeys.stream()
                .filter(this::isHousingBranchKey)
                .distinct()
                .toList();
    }

    public Optional<BranchDefinition> matchSuggestedBranchQuestion(String question, List<String> branchKeys) {
        if (!StringUtils.hasText(question) || branchKeys == null || branchKeys.isEmpty()) {
            return Optional.empty();
        }

        List<String> tokens = SearchKeywordSupport.extractTokens(question);
        if (tokens.isEmpty()) {
            return Optional.empty();
        }

        List<ScoredBranch> scoredBranches = branchKeys.stream()
                .map(this::findByKey)
                .flatMap(Optional::stream)
                .map(definition -> new ScoredBranch(definition, definition.matchScore(tokens)))
                .filter(scoredBranch -> scoredBranch.score() > 0)
                .sorted(Comparator.comparingInt(ScoredBranch::score).reversed())
                .toList();

        if (scoredBranches.isEmpty()) {
            return Optional.empty();
        }
        if (scoredBranches.size() > 1 && scoredBranches.get(0).score() == scoredBranches.get(1).score()) {
            return Optional.empty();
        }
        return Optional.of(scoredBranches.get(0).definition());
    }

    public Optional<BranchDefinition> matchHousingQuestion(String question) {
        if (!StringUtils.hasText(question)) {
            return Optional.empty();
        }
        List<String> housingBranchKeys = DEFINITIONS.stream()
                .filter(definition -> definition.topLevelLabel().equals("주거"))
                .map(BranchDefinition::branchKey)
                .toList();
        return matchSuggestedBranchQuestion(question, housingBranchKeys);
    }

    public List<String> extractHousingTopics(String question, String branchKey) {
        if (!StringUtils.hasText(question) && !StringUtils.hasText(branchKey)) {
            return List.of();
        }
        List<String> tokens = SearchKeywordSupport.extractTokens(question);
        List<String> topics = new ArrayList<>();

        findByKey(branchKey)
                .filter(definition -> definition.topLevelLabel().equals("주거"))
                .ifPresent(definition -> topics.addAll(definition.matchedKeywords(tokens)));

        if (topics.isEmpty()) {
            matchHousingQuestion(question).ifPresent(definition -> topics.addAll(definition.matchedKeywords(tokens)));
        }
        return topics.stream().distinct().limit(4).toList();
    }

    private Optional<String> detectBroadTopLevel(List<String> tokens) {
        boolean housing = tokens.stream().anyMatch(token -> Set.of("주거", "집", "거주").contains(token));
        boolean job = tokens.stream().anyMatch(token -> Set.of("일자리", "취업", "직업").contains(token));
        if (housing == job) {
            return Optional.empty();
        }
        return Optional.of(housing ? "주거" : "일자리");
    }

    private boolean hasSpecificLeafToken(List<String> tokens, String topLevel) {
        return DEFINITIONS.stream()
                .filter(definition -> definition.topLevelLabel().equals(topLevel))
                .flatMap(definition -> definition.specificTokens().stream())
                .anyMatch(tokens::contains);
    }

    public record BranchDefinition(
            String branchKey,
            String topLevelLabel,
            String label,
            String guideQuestion,
            Set<String> broadTokens,
            Set<String> specificTokens,
            String preferredCategory,
            List<String> searchTerms
    ) {
        int matchScore(List<String> questionTokens) {
            int score = 0;
            for (String token : matchKeywords()) {
                if (matchesQuestionToken(questionTokens, token)) {
                    score++;
                }
            }
            return score;
        }

        List<String> matchedKeywords(List<String> questionTokens) {
            return matchKeywords().stream()
                    .filter(token -> matchesQuestionToken(questionTokens, token))
                    .distinct()
                    .toList();
        }

        private boolean matchesQuestionToken(List<String> questionTokens, String keyword) {
            if (!StringUtils.hasText(keyword) || questionTokens == null || questionTokens.isEmpty()) {
                return false;
            }
            String normalizedKeyword = keyword.trim();
            return questionTokens.stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .anyMatch(token -> token.equals(normalizedKeyword) || token.startsWith(normalizedKeyword));
        }

        private List<String> matchKeywords() {
            List<String> keywords = new ArrayList<>(specificTokens);
            keywords.addAll(searchTerms);
            keywords.addAll(SearchKeywordSupport.extractTokens(label));
            return keywords.stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .filter(token -> !broadTokens.contains(token))
                    .distinct()
                    .toList();
        }
    }

    private record ScoredBranch(
            BranchDefinition definition,
            int score
    ) {
    }
}
