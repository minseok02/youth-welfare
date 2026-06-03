package com.example.welfare.chat.service;

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
            "말고", "대신", "추가로", "그중", "그 중", "또는", "그리고", "그 외"
    );
    private static final int SHORT_FOLLOW_UP_MAX_LENGTH = 18;
    private static final int BRIEF_FOLLOW_UP_MAX_LENGTH = 30;

    private final ObjectMapper objectMapper;
    private final ChatBranchCatalog chatBranchCatalog;

    public ConversationContext resolve(String question,
                                       String requestedBranchKey,
                                       List<ChatMessage> recentMessages,
                                       List<ChatRetrievalSnapshot> recentSnapshots) {
        String normalizedQuestion = normalize(question);
        String previousUserQuestion = findPreviousUserQuestion(normalizedQuestion, recentMessages);
        String suggestedBranchKey = findSuggestedBranchMatch(normalizedQuestion, recentSnapshots);
        String inheritedBranchKey = findLatestBranchKey(recentSnapshots);
        String effectiveBranchKey = StringUtils.hasText(requestedBranchKey)
                ? requestedBranchKey.trim()
                : StringUtils.hasText(suggestedBranchKey)
                ? suggestedBranchKey
                : inheritedBranchKey;

        boolean followUp = shouldTreatAsFollowUp(normalizedQuestion, previousUserQuestion, effectiveBranchKey, suggestedBranchKey);
        String retrievalQuestion = followUp
                ? previousUserQuestion + "\n후속 질문: " + normalizedQuestion
                : normalizedQuestion;

        String conversationSummary = buildConversationSummary(previousUserQuestion, effectiveBranchKey, recentMessages);
        return new ConversationContext(
                retrievalQuestion,
                effectiveBranchKey,
                conversationSummary,
                followUp
        );
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
        return shouldTreatAsFollowUp(question, previousUserQuestion, inheritedBranchKey, null);
    }

    private boolean shouldTreatAsFollowUp(String question,
                                          String previousUserQuestion,
                                          String inheritedBranchKey,
                                          String suggestedBranchKey) {
        if (!StringUtils.hasText(question) || !StringUtils.hasText(previousUserQuestion)) {
            return false;
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

        if (!StringUtils.hasText(inheritedBranchKey)) {
            return false;
        }

        List<String> tokens = SearchKeywordSupport.extractTokens(question);
        return question.length() <= BRIEF_FOLLOW_UP_MAX_LENGTH && tokens.size() <= 4;
    }

    private String buildConversationSummary(String previousUserQuestion,
                                            String inheritedBranchKey,
                                            List<ChatMessage> recentMessages) {
        List<String> lines = new ArrayList<>();
        if (StringUtils.hasText(previousUserQuestion)) {
            lines.add("직전 사용자 질문: " + trimToLength(previousUserQuestion, 120));
        }

        if (StringUtils.hasText(inheritedBranchKey)) {
            chatBranchCatalog.findByKey(inheritedBranchKey)
                    .ifPresent(branch -> lines.add("직전 탐색 방향: " + branch.label()));
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
                    if (titles.size() >= 3) {
                        return List.copyOf(titles);
                    }
                }
            } catch (Exception ignored) {
                // best-effort context extraction only
            }
        }
        return List.copyOf(titles);
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
            boolean followUp
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
