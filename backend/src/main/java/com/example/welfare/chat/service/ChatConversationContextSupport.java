package com.example.welfare.chat.service;

import com.example.welfare.chat.dto.response.ChatBranchOptionResponse;
import com.example.welfare.chat.entity.ChatMessage;
import com.example.welfare.chat.entity.ChatMessageRole;
import com.example.welfare.chat.entity.ChatRetrievalSnapshot;
import com.example.welfare.global.util.SearchKeywordSupport;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class ChatConversationContextSupport {

    private static final List<String> FOLLOW_UP_MARKERS = List.of(
            "그럼", "그러면", "그거", "그건", "이거", "이건", "저거", "저건",
            "말고", "대신", "추가로", "그중", "그 중", "또는", "그리고", "그 외", "쪽으로"
    );
    private static final List<String> APPLICATION_FOLLOW_UP_PHRASES = List.of(
            "신청", "서류", "절차", "제출", "준비", "자격", "조건", "대상",
            "기간", "마감", "링크", "문의", "담당", "접수", "방법"
    );
    private static final Set<String> STANDALONE_APPLICATION_TOPIC_TOKENS = Set.of(
            "주거", "월세", "전세", "임대", "청약", "입주", "취업", "일자리",
            "창업", "자금", "대출", "장학금", "교육", "훈련", "문화", "건강",
            "의료", "복지", "교통", "금융", "면접", "인턴", "구직"
    );
    private static final int SHORT_FOLLOW_UP_MAX_LENGTH = 18;
    private static final int BRIEF_FOLLOW_UP_MAX_LENGTH = 30;
    private static final int RECENT_QUESTION_SUMMARY_LIMIT = 3;
    private static final int RECENT_BRANCH_SUMMARY_LIMIT = 2;
    private static final int RECENT_POLICY_SUMMARY_LIMIT = 4;
    private static final int RECENT_SUGGESTION_SUMMARY_LIMIT = 3;

    private final ObjectMapper objectMapper;
    private final ChatBranchCatalog chatBranchCatalog;

    public ConversationContext resolve(String question,
                                       String requestedBranchKey,
                                       String sessionContextStateJson,
                                       List<ChatMessage> recentMessages,
                                       List<ChatRetrievalSnapshot> recentSnapshots) {
        String normalizedQuestion = normalize(question);
        String previousUserQuestion = findPreviousUserQuestion(normalizedQuestion, recentMessages);
        ChatSessionContextState sessionContextState = parseSessionContextState(sessionContextStateJson);
        ChatSessionContextState.HousingContext housingContext = sessionContextState != null
                ? sessionContextState.getHousing()
                : null;
        ChatSessionContextState.MemoryContext memoryContext = sessionContextState != null
                ? sessionContextState.getMemory()
                : null;
        String suggestedBranchKey = findSuggestedBranchMatch(normalizedQuestion, recentSnapshots);
        String inheritedBranchKey = findLatestBranchKey(recentSnapshots);
        String housingBranchKey = findHousingBranchMatch(normalizedQuestion, housingContext);
        String effectiveBranchKey = StringUtils.hasText(requestedBranchKey)
                ? requestedBranchKey.trim()
                : StringUtils.hasText(suggestedBranchKey)
                ? suggestedBranchKey
                : StringUtils.hasText(housingBranchKey)
                ? housingBranchKey
                : inheritedBranchKey;

        boolean applicationProcedureFollowUp = StringUtils.hasText(previousUserQuestion)
                && isContextDependentApplicationFollowUp(normalizedQuestion);
        boolean followUp = shouldTreatAsFollowUp(normalizedQuestion, previousUserQuestion, effectiveBranchKey, suggestedBranchKey, housingContext);
        String retrievalQuestion = buildRetrievalQuestion(
                normalizedQuestion,
                previousUserQuestion,
                housingContext,
                memoryContext,
                effectiveBranchKey,
                followUp
        );

        String conversationSummary = buildConversationSummary(
                previousUserQuestion,
                effectiveBranchKey,
                recentMessages,
                recentSnapshots,
                housingContext,
                memoryContext
        );
        return new ConversationContext(
                retrievalQuestion,
                effectiveBranchKey,
                conversationSummary,
                followUp,
                applicationProcedureFollowUp
        );
    }

    private ChatSessionContextState parseSessionContextState(String sessionContextStateJson) {
        if (!StringUtils.hasText(sessionContextStateJson)) {
            return null;
        }
        try {
            return objectMapper.readValue(sessionContextStateJson, ChatSessionContextState.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String findPreviousUserQuestion(String currentQuestion, List<ChatMessage> recentMessages) {
        if (recentMessages == null || recentMessages.isEmpty()) {
            return null;
        }
        for (int i = recentMessages.size() - 1; i >= 0; i--) {
            ChatMessage message = recentMessages.get(i);
            if (message.getRole() != ChatMessageRole.USER) {
                continue;
            }
            String content = normalize(message.getContent());
            if (!StringUtils.hasText(content)) {
                continue;
            }
            if (Objects.equals(content, currentQuestion)) {
                continue;
            }
            return content;
        }
        return null;
    }

    private String findSuggestedBranchMatch(String question, List<ChatRetrievalSnapshot> recentSnapshots) {
        List<String> latestSuggestedBranchKeys = findLatestSuggestedBranchKeys(recentSnapshots);
        if (latestSuggestedBranchKeys.isEmpty()) {
            return null;
        }
        return chatBranchCatalog.matchSuggestedBranchQuestion(question, latestSuggestedBranchKeys)
                .map(ChatBranchCatalog.BranchDefinition::branchKey)
                .orElse(null);
    }

    private String findHousingBranchMatch(String question, ChatSessionContextState.HousingContext housingContext) {
        if (housingContext == null) {
            return null;
        }
        return chatBranchCatalog.matchHousingQuestion(question)
                .map(ChatBranchCatalog.BranchDefinition::branchKey)
                .orElseGet(() -> isShortHousingFollowUp(question, housingContext)
                        ? normalize(housingContext.getActiveBranchKey())
                        : null);
    }

    private List<String> findLatestSuggestedBranchKeys(List<ChatRetrievalSnapshot> recentSnapshots) {
        if (recentSnapshots == null || recentSnapshots.isEmpty()) {
            return List.of();
        }
        for (int i = recentSnapshots.size() - 1; i >= 0; i--) {
            String rawBranchSuggestionKeys = recentSnapshots.get(i).getBranchSuggestionKeysJson();
            if (!StringUtils.hasText(rawBranchSuggestionKeys)) {
                continue;
            }
            try {
                List<String> branchKeys = objectMapper.readValue(rawBranchSuggestionKeys, new TypeReference<List<String>>() {
                });
                if (branchKeys != null && !branchKeys.isEmpty()) {
                    return branchKeys.stream()
                            .filter(StringUtils::hasText)
                            .map(String::trim)
                            .toList();
                }
            } catch (Exception ignored) {
                // best-effort context extraction only
            }
        }
        return List.of();
    }

    private String findLatestBranchKey(List<ChatRetrievalSnapshot> recentSnapshots) {
        if (recentSnapshots == null || recentSnapshots.isEmpty()) {
            return null;
        }
        for (int i = recentSnapshots.size() - 1; i >= 0; i--) {
            String branchKey = normalize(recentSnapshots.get(i).getBranchKey());
            if (StringUtils.hasText(branchKey)) {
                return branchKey;
            }
        }
        return null;
    }

    private boolean shouldTreatAsFollowUp(String question, String previousUserQuestion, String inheritedBranchKey) {
        return shouldTreatAsFollowUp(question, previousUserQuestion, inheritedBranchKey, null, null);
    }

    private boolean shouldTreatAsFollowUp(String question,
                                          String previousUserQuestion,
                                          String inheritedBranchKey,
                                          String suggestedBranchKey,
                                          ChatSessionContextState.HousingContext housingContext) {
        if (!StringUtils.hasText(question) || !StringUtils.hasText(previousUserQuestion)) {
            return isShortHousingFollowUp(question, housingContext);
        }
        if (question.length() <= SHORT_FOLLOW_UP_MAX_LENGTH) {
            return true;
        }

        boolean containsFollowUpMarker = FOLLOW_UP_MARKERS.stream().anyMatch(question::contains);
        if (containsFollowUpMarker && question.length() <= BRIEF_FOLLOW_UP_MAX_LENGTH) {
            return true;
        }

        if (StringUtils.hasText(suggestedBranchKey)) {
            return true;
        }

        if (isShortHousingFollowUp(question, housingContext)) {
            return true;
        }

        if (isContextDependentApplicationFollowUp(question)) {
            return true;
        }

        if (!StringUtils.hasText(inheritedBranchKey)) {
            return false;
        }

        List<String> tokens = SearchKeywordSupport.extractTokens(question);
        return question.length() <= BRIEF_FOLLOW_UP_MAX_LENGTH && tokens.size() <= 4;
    }

    private boolean isShortHousingFollowUp(String question, ChatSessionContextState.HousingContext housingContext) {
        if (housingContext == null || !StringUtils.hasText(housingContext.getActiveBranchKey())) {
            return false;
        }
        if (!chatBranchCatalog.isHousingBranchKey(housingContext.getActiveBranchKey())) {
            return false;
        }
        List<String> housingTopics = chatBranchCatalog.extractHousingTopics(question, null);
        return question.length() <= BRIEF_FOLLOW_UP_MAX_LENGTH
                && (!housingTopics.isEmpty() || FOLLOW_UP_MARKERS.stream().anyMatch(question::contains));
    }

    private String buildRetrievalQuestion(String question,
                                          String previousUserQuestion,
                                          ChatSessionContextState.HousingContext housingContext,
                                          ChatSessionContextState.MemoryContext memoryContext,
                                          String effectiveBranchKey,
                                          boolean followUp) {
        if (housingContext != null && chatBranchCatalog.isHousingBranchKey(effectiveBranchKey)) {
            List<String> parts = new ArrayList<>();
            boolean branchSwitched = chatBranchCatalog.isHousingBranchKey(housingContext.getActiveBranchKey())
                    && !Objects.equals(normalize(housingContext.getActiveBranchKey()), normalize(effectiveBranchKey));
            String anchorQuestion = resolveHousingAnchorQuestion(housingContext, previousUserQuestion, branchSwitched);
            if (StringUtils.hasText(anchorQuestion)) {
                parts.add(anchorQuestion);
            }
            if (!branchSwitched && housingContext.getRecentTopics() != null && !housingContext.getRecentTopics().isEmpty()) {
                parts.add("주거 관심 맥락: " + String.join(", ", housingContext.getRecentTopics()));
            }
            if (!branchSwitched && housingContext.getRecentPolicyTitles() != null && !housingContext.getRecentPolicyTitles().isEmpty()) {
                parts.add("최근 주거 정책: " + String.join(", ", housingContext.getRecentPolicyTitles().stream().limit(3).toList()));
            }
            if (branchSwitched) {
                chatBranchCatalog.findByKey(effectiveBranchKey)
                        .map(ChatBranchCatalog.BranchDefinition::label)
                        .filter(StringUtils::hasText)
                        .ifPresent(label -> parts.add("현재 관심 갈래: " + label));
            }
            parts.add((followUp ? "후속 질문: " : "현재 질문: ") + question);
            return String.join("\n", parts);
        }
        if (followUp && StringUtils.hasText(previousUserQuestion)) {
            if (isSpecificStandaloneFollowUp(question)) {
                return question;
            }
            String memoryRetrievalContext = buildMemoryRetrievalContext(memoryContext);
            return StringUtils.hasText(memoryRetrievalContext)
                    ? memoryRetrievalContext + "\n" + previousUserQuestion + "\n후속 질문: " + question
                    : previousUserQuestion + "\n후속 질문: " + question;
        }
        return question;
    }

    private String buildMemoryRetrievalContext(ChatSessionContextState.MemoryContext memoryContext) {
        if (memoryContext == null) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        if (StringUtils.hasText(memoryContext.getSummary())) {
            parts.add(trimToLength(memoryContext.getSummary().trim(), 180));
        }
        if (memoryContext.getRecentUserQuestions() != null && !memoryContext.getRecentUserQuestions().isEmpty()) {
            parts.add(String.join(" ", memoryContext.getRecentUserQuestions().stream().limit(3).toList()));
        }
        if (memoryContext.getRecentPolicyTitles() != null && !memoryContext.getRecentPolicyTitles().isEmpty()) {
            parts.add(String.join(" ", memoryContext.getRecentPolicyTitles().stream().limit(3).toList()));
        }
        if (parts.isEmpty()) {
            return null;
        }
        return "저장된 관심 맥락: " + String.join(" / ", parts);
    }

    private String resolveHousingAnchorQuestion(ChatSessionContextState.HousingContext housingContext,
                                                String previousUserQuestion,
                                                boolean branchSwitched) {
        String anchorQuestion = normalize(housingContext.getAnchorQuestion());
        if (!branchSwitched) {
            if (StringUtils.hasText(anchorQuestion)) {
                return anchorQuestion;
            }
            return StringUtils.hasText(previousUserQuestion) ? previousUserQuestion : null;
        }

        String sourceQuestion = StringUtils.hasText(anchorQuestion) ? anchorQuestion : previousUserQuestion;
        List<String> contextTokens = SearchKeywordSupport.extractTokens(sourceQuestion).stream()
                .filter(token -> !isHousingSpecificToken(token))
                .filter(token -> !isLowSignalConversationToken(token))
                .distinct()
                .toList();
        if (contextTokens.isEmpty()) {
            return "주거 지원";
        }
        return String.join(" ", contextTokens) + " 주거 지원";
    }

    private boolean isHousingSpecificToken(String token) {
        return chatBranchCatalog.matchHousingQuestion(token)
                .map(branch -> true)
                .orElseGet(() -> List.of("주거", "집", "거주", "월세", "전세", "임대", "공공임대", "주택", "주거비", "청약", "입주", "모집").contains(token));
    }

    private boolean isLowSignalConversationToken(String token) {
        return List.of("지원", "알려줘", "보여줘", "그럼", "그러면", "쪽으로", "현재", "후속", "질문").contains(token);
    }

    private boolean isSpecificStandaloneFollowUp(String question) {
        if (isContextDependentApplicationFollowUp(question)) {
            return false;
        }
        List<String> tokens = SearchKeywordSupport.extractTokens(question).stream()
                .filter(token -> !isLowSignalConversationToken(token))
                .distinct()
                .toList();
        return tokens.size() >= 2;
    }

    private boolean isContextDependentApplicationFollowUp(String question) {
        if (!StringUtils.hasText(question) || APPLICATION_FOLLOW_UP_PHRASES.stream().noneMatch(question::contains)) {
            return false;
        }
        List<String> tokens = SearchKeywordSupport.extractTokens(question).stream()
                .filter(token -> !isLowSignalConversationToken(token))
                .filter(token -> APPLICATION_FOLLOW_UP_PHRASES.stream().noneMatch(token::startsWith))
                .distinct()
                .toList();
        return tokens.stream().noneMatch(STANDALONE_APPLICATION_TOPIC_TOKENS::contains)
                && chatBranchCatalog.matchHousingQuestion(question).isEmpty();
    }

    private String buildConversationSummary(String previousUserQuestion,
                                            String inheritedBranchKey,
                                            List<ChatMessage> recentMessages,
                                            List<ChatRetrievalSnapshot> recentSnapshots,
                                            ChatSessionContextState.HousingContext housingContext,
                                            ChatSessionContextState.MemoryContext memoryContext) {
        List<String> lines = new ArrayList<>();
        if (memoryContext != null && StringUtils.hasText(memoryContext.getSummary())) {
            lines.add("저장된 세션 요약: " + trimToLength(memoryContext.getSummary().trim(), 500));
        }
        if (memoryContext != null && memoryContext.getRecentUserQuestions() != null && !memoryContext.getRecentUserQuestions().isEmpty()) {
            lines.add("누적 질문 관심사: " + String.join(" -> ", memoryContext.getRecentUserQuestions().stream().limit(5).toList()));
        }
        if (memoryContext != null && memoryContext.getRecentPolicyTitles() != null && !memoryContext.getRecentPolicyTitles().isEmpty()) {
            lines.add("누적 추천 정책: " + String.join(", ", memoryContext.getRecentPolicyTitles().stream().limit(4).toList()));
        }
        if (StringUtils.hasText(previousUserQuestion)) {
            lines.add("직전 사용자 질문: " + trimToLength(previousUserQuestion, 120));
        }

        List<String> recentQuestionFlow = findRecentUserQuestions(recentMessages);
        if (!recentQuestionFlow.isEmpty()) {
            lines.add("최근 질문 흐름: " + String.join(" -> ", recentQuestionFlow));
        }

        if (StringUtils.hasText(inheritedBranchKey)) {
            chatBranchCatalog.findByKey(inheritedBranchKey)
                    .ifPresent(branch -> lines.add("직전 탐색 방향: " + branch.label()));
        }

        List<String> recentBranchFlow = findRecentBranchLabels(recentSnapshots);
        if (!recentBranchFlow.isEmpty()) {
            lines.add("최근 탐색 흐름: " + String.join(" -> ", recentBranchFlow));
        }

        List<String> recentSuggestedBranches = findRecentSuggestedBranchLabels(recentSnapshots);
        if (!recentSuggestedBranches.isEmpty()) {
            lines.add("최근 제안 갈래: " + String.join(", ", recentSuggestedBranches));
        }

        if (housingContext != null && housingContext.getRecentTopics() != null && !housingContext.getRecentTopics().isEmpty()) {
            lines.add("주거 세션 상태: " + String.join(", ", housingContext.getRecentTopics()));
        }

        List<String> recentPolicyTitles = findRecentReferencedPolicyTitles(recentMessages);
        if (!recentPolicyTitles.isEmpty()) {
            lines.add("직전 추천 정책: " + String.join(", ", recentPolicyTitles));
        }

        if (lines.isEmpty()) {
            return null;
        }
        return String.join("\n", lines);
    }

    private List<String> findRecentReferencedPolicyTitles(List<ChatMessage> recentMessages) {
        if (recentMessages == null || recentMessages.isEmpty()) {
            return List.of();
        }

        Set<String> titles = new LinkedHashSet<>();
        for (int i = recentMessages.size() - 1; i >= 0; i--) {
            ChatMessage message = recentMessages.get(i);
            if (message.getRole() != ChatMessageRole.ASSISTANT || !StringUtils.hasText(message.getReferencesJson())) {
                continue;
            }
            try {
                List<ReferenceTitleRow> rows = objectMapper.readValue(message.getReferencesJson(), new TypeReference<List<ReferenceTitleRow>>() {
                });
                for (ReferenceTitleRow row : rows) {
                    String title = normalize(row.title());
                    if (StringUtils.hasText(title)) {
                        titles.add(title);
                    }
                    if (titles.size() >= RECENT_POLICY_SUMMARY_LIMIT) {
                        return List.copyOf(titles);
                    }
                }
            } catch (Exception ignored) {
                // best-effort context extraction only
            }
        }
        return List.copyOf(titles);
    }

    private List<String> findRecentUserQuestions(List<ChatMessage> recentMessages) {
        if (recentMessages == null || recentMessages.isEmpty()) {
            return List.of();
        }

        List<String> questions = new ArrayList<>();
        String lastAdded = null;
        for (int i = recentMessages.size() - 1; i >= 0; i--) {
            ChatMessage message = recentMessages.get(i);
            if (message.getRole() != ChatMessageRole.USER) {
                continue;
            }
            String content = normalize(message.getContent());
            if (!StringUtils.hasText(content)) {
                continue;
            }
            String trimmed = trimToLength(content, 60);
            if (Objects.equals(lastAdded, trimmed)) {
                continue;
            }
            questions.add(0, trimmed);
            lastAdded = trimmed;
            if (questions.size() >= RECENT_QUESTION_SUMMARY_LIMIT) {
                break;
            }
        }
        return questions;
    }

    private List<String> findRecentBranchLabels(List<ChatRetrievalSnapshot> recentSnapshots) {
        if (recentSnapshots == null || recentSnapshots.isEmpty()) {
            return List.of();
        }

        List<String> labels = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (int i = recentSnapshots.size() - 1; i >= 0; i--) {
            String branchKey = normalize(recentSnapshots.get(i).getBranchKey());
            if (!StringUtils.hasText(branchKey)) {
                continue;
            }
            chatBranchCatalog.findByKey(branchKey).ifPresent(branch -> {
                if (seen.add(branch.label()) && labels.size() < RECENT_BRANCH_SUMMARY_LIMIT) {
                    labels.add(0, branch.label());
                }
            });
            if (labels.size() >= RECENT_BRANCH_SUMMARY_LIMIT) {
                break;
            }
        }
        return labels;
    }

    private List<String> findRecentSuggestedBranchLabels(List<ChatRetrievalSnapshot> recentSnapshots) {
        List<String> branchKeys = findLatestSuggestedBranchKeys(recentSnapshots);
        if (branchKeys.isEmpty()) {
            return List.of();
        }
        return chatBranchCatalog.toResponsesByKeys(branchKeys).stream()
                .map(ChatBranchOptionResponse::getLabel)
                .filter(StringUtils::hasText)
                .limit(RECENT_SUGGESTION_SUMMARY_LIMIT)
                .toList();
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String trimToLength(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    public record ConversationContext(
            String retrievalQuestion,
            String effectiveBranchKey,
            String conversationSummary,
            boolean followUp,
            boolean applicationProcedureFollowUp
    ) {
    }

    private record ReferenceTitleRow(
            Long serviceId,
            String title,
            String reason,
            String evidence
    ) {
    }
}
