package com.example.welfare.chat.service;

import com.example.welfare.chat.dto.response.ChatBranchOptionResponse;
import com.example.welfare.chat.dto.response.ChatReferenceResponse;
import com.example.welfare.chat.entity.ChatSession;
import com.example.welfare.chat.repository.ChatSessionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatSessionContextStateService {

    private static final int MAX_TOPICS = 4;
    private static final int MAX_POLICIES = 4;
    private static final int MAX_SUGGESTIONS = 3;
    private static final int MAX_MEMORY_QUESTIONS = 5;
    private static final int MAX_MEMORY_POLICIES = 6;
    private static final int MEMORY_SUMMARY_LIMIT = 500;
    private static final int MEMORY_FIELD_LIMIT = 120;

    private final ChatSessionRepository chatSessionRepository;
    private final ObjectMapper objectMapper;
    private final ChatBranchCatalog chatBranchCatalog;

    @Transactional
    public void captureHousingBranchSuggestions(Long sessionId,
                                                String question,
                                                List<ChatBranchOptionResponse> branchSuggestions) {
        List<String> housingBranchKeys = chatBranchCatalog.filterHousingBranchKeys(
                branchSuggestions.stream()
                        .map(ChatBranchOptionResponse::getBranchKey)
                        .toList()
        );
        if (housingBranchKeys.isEmpty()) {
            return;
        }

        chatSessionRepository.findById(sessionId).ifPresent(session -> {
            ChatSessionContextState state = readState(session.getContextStateJson());
            ChatSessionContextState.HousingContext housing = ensureHousingContext(state);
            housing.setAnchorQuestion(resolveAnchorQuestion(question, housing.getAnchorQuestion()));
            housing.setSuggestedBranchKeys(limitDistinct(housingBranchKeys, MAX_SUGGESTIONS));
            housing.setRecentTopics(mergeDistinct(
                    housing.getRecentTopics(),
                    chatBranchCatalog.extractHousingTopics(question, null),
                    MAX_TOPICS
            ));
            session.updateContextStateJson(writeState(state));
        });
    }

    @Transactional
    public void captureHousingAnswer(Long sessionId,
                                     String question,
                                     String effectiveBranchKey,
                                     List<ChatReferenceResponse> references) {
        String inferredHousingBranchKey = chatBranchCatalog.matchHousingQuestion(question)
                .map(ChatBranchCatalog.BranchDefinition::branchKey)
                .orElse(null);
        List<String> housingTopics = chatBranchCatalog.extractHousingTopics(question, effectiveBranchKey);
        boolean housingBranch = chatBranchCatalog.isHousingBranchKey(effectiveBranchKey)
                || chatBranchCatalog.isHousingBranchKey(inferredHousingBranchKey);
        if (!housingBranch && housingTopics.isEmpty()) {
            return;
        }

        chatSessionRepository.findById(sessionId).ifPresent(session -> {
            ChatSessionContextState state = readState(session.getContextStateJson());
            ChatSessionContextState.HousingContext housing = ensureHousingContext(state);
            String activeBranchKey = chatBranchCatalog.isHousingBranchKey(effectiveBranchKey)
                    ? effectiveBranchKey
                    : inferredHousingBranchKey;
            if (chatBranchCatalog.isHousingBranchKey(activeBranchKey)) {
                housing.setActiveBranchKey(activeBranchKey);
            }
            housing.setAnchorQuestion(resolveAnchorQuestion(question, housing.getAnchorQuestion()));
            housing.setRecentTopics(mergeDistinct(housing.getRecentTopics(), housingTopics, MAX_TOPICS));
            housing.setRecentPolicyTitles(mergeDistinct(
                    housing.getRecentPolicyTitles(),
                    references.stream().map(ChatReferenceResponse::getTitle).toList(),
                    MAX_POLICIES
            ));
            housing.setRecentPolicyIds(mergeDistinctLongs(
                    housing.getRecentPolicyIds(),
                    references.stream().map(ChatReferenceResponse::getServiceId).toList(),
                    MAX_POLICIES
            ));
            session.updateContextStateJson(writeState(state));
        });
    }

    @Transactional
    public void captureConversationMemory(Long sessionId,
                                          String question,
                                          String answer,
                                          List<ChatReferenceResponse> references) {
        if (!StringUtils.hasText(question) && !StringUtils.hasText(answer) && (references == null || references.isEmpty())) {
            return;
        }

        chatSessionRepository.findById(sessionId).ifPresent(session -> {
            ChatSessionContextState state = readState(session.getContextStateJson());
            ChatSessionContextState.MemoryContext memory = ensureMemoryContext(state);
            memory.setRecentUserQuestions(mergeRecentDistinct(
                    memory.getRecentUserQuestions(),
                    singleTextList(question),
                    MAX_MEMORY_QUESTIONS
            ));
            memory.setRecentPolicyTitles(mergeRecentDistinct(
                    memory.getRecentPolicyTitles(),
                    references == null
                            ? List.of()
                            : references.stream()
                            .map(ChatReferenceResponse::getTitle)
                            .map(title -> trimToLength(title, MEMORY_FIELD_LIMIT))
                            .toList(),
                    MAX_MEMORY_POLICIES
            ));
            memory.setSummary(buildMemorySummary(memory, question, answer));
            session.updateContextStateJson(writeState(state));
        });
    }

    private ChatSessionContextState readState(String rawState) {
        if (!StringUtils.hasText(rawState)) {
            return new ChatSessionContextState();
        }
        try {
            return objectMapper.readValue(rawState, ChatSessionContextState.class);
        } catch (Exception e) {
            log.warn("[ChatSessionContextStateService] context state parse failed, reset state", e);
            return new ChatSessionContextState();
        }
    }

    private String writeState(ChatSessionContextState state) {
        try {
            return objectMapper.writeValueAsString(state);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("chat session context state serialization failed", e);
        }
    }

    private ChatSessionContextState.HousingContext ensureHousingContext(ChatSessionContextState state) {
        if (state.getHousing() == null) {
            state.setHousing(ChatSessionContextState.HousingContext.builder()
                    .recentTopics(List.of())
                    .recentPolicyTitles(List.of())
                    .recentPolicyIds(List.of())
                    .suggestedBranchKeys(List.of())
                    .build());
        }
        return state.getHousing();
    }

    private ChatSessionContextState.MemoryContext ensureMemoryContext(ChatSessionContextState state) {
        if (state.getMemory() == null) {
            state.setMemory(ChatSessionContextState.MemoryContext.builder()
                    .recentUserQuestions(List.of())
                    .recentPolicyTitles(List.of())
                    .build());
        }
        return state.getMemory();
    }

    private String buildMemorySummary(ChatSessionContextState.MemoryContext memory, String question, String answer) {
        List<String> parts = new ArrayList<>();
        if (memory.getRecentUserQuestions() != null && !memory.getRecentUserQuestions().isEmpty()) {
            parts.add("최근 질문 흐름: " + String.join(" -> ", memory.getRecentUserQuestions()));
        }
        if (StringUtils.hasText(answer)) {
            parts.add("최근 답변 요지: " + trimToLength(answer.trim(), MEMORY_FIELD_LIMIT));
        }
        if (memory.getRecentPolicyTitles() != null && !memory.getRecentPolicyTitles().isEmpty()) {
            parts.add("누적 추천 정책: " + String.join(", ", memory.getRecentPolicyTitles().stream().limit(3).toList()));
        }
        return trimToLength(String.join(" / ", parts), MEMORY_SUMMARY_LIMIT);
    }

    private List<String> singleTextList(String value) {
        String trimmed = trimToLength(value, MEMORY_FIELD_LIMIT);
        return StringUtils.hasText(trimmed) ? List.of(trimmed) : List.of();
    }

    private String resolveAnchorQuestion(String question, String existingAnchorQuestion) {
        if (containsBroadHousingSignal(question)) {
            return question.trim();
        }
        if (StringUtils.hasText(existingAnchorQuestion)) {
            return existingAnchorQuestion;
        }
        return question.trim();
    }

    private boolean containsBroadHousingSignal(String question) {
        if (!StringUtils.hasText(question)) {
            return false;
        }
        List<String> tokens = com.example.welfare.global.util.SearchKeywordSupport.extractTokens(question);
        return tokens.stream().anyMatch(token -> List.of("주거", "집", "거주").contains(token));
    }

    private List<String> mergeDistinct(List<String> existing, List<String> additions, int limit) {
        List<String> merged = new ArrayList<>();
        if (existing != null) {
            for (String value : existing) {
                if (StringUtils.hasText(value) && !merged.contains(value.trim())) {
                    merged.add(value.trim());
                }
            }
        }
        if (additions != null) {
            for (String value : additions) {
                if (StringUtils.hasText(value) && !merged.contains(value.trim())) {
                    merged.add(value.trim());
                }
            }
        }
        return merged.stream().limit(limit).toList();
    }

    private List<String> mergeRecentDistinct(List<String> existing, List<String> additions, int limit) {
        List<String> merged = new ArrayList<>();
        if (existing != null) {
            for (String value : existing) {
                if (StringUtils.hasText(value) && !merged.contains(value.trim())) {
                    merged.add(value.trim());
                }
            }
        }
        if (additions != null) {
            for (String value : additions) {
                if (!StringUtils.hasText(value)) {
                    continue;
                }
                String trimmed = value.trim();
                merged.remove(trimmed);
                merged.add(trimmed);
            }
        }
        int fromIndex = Math.max(0, merged.size() - limit);
        return merged.subList(fromIndex, merged.size()).stream().toList();
    }

    private String trimToLength(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() <= maxLength) {
            return trimmed;
        }
        return trimmed.substring(0, maxLength);
    }

    private List<String> limitDistinct(List<String> values, int limit) {
        return mergeDistinct(List.of(), values, limit);
    }

    private List<Long> mergeDistinctLongs(List<Long> existing, List<Long> additions, int limit) {
        List<Long> merged = new ArrayList<>();
        if (existing != null) {
            for (Long value : existing) {
                if (value != null && !merged.contains(value)) {
                    merged.add(value);
                }
            }
        }
        if (additions != null) {
            for (Long value : additions) {
                if (value != null && !merged.contains(value)) {
                    merged.add(value);
                }
            }
        }
        return merged.stream().limit(limit).toList();
    }
}
