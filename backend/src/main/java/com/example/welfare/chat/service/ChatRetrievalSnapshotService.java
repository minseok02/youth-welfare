package com.example.welfare.chat.service;

import com.example.welfare.chat.dto.ChatPolicyCandidate;
import com.example.welfare.chat.dto.response.ChatBranchOptionResponse;
import com.example.welfare.chat.entity.ChatRetrievalSnapshot;
import com.example.welfare.chat.entity.ChatSession;
import com.example.welfare.chat.repository.ChatRetrievalSnapshotRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatRetrievalSnapshotService {

    private final ChatRetrievalSnapshotRepository chatRetrievalSnapshotRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void recordInteractiveBranchSuggestions(ChatSession session,
                                                   String question,
                                                   String branchKey,
                                                   List<ChatBranchOptionResponse> branchSuggestions) {
        persistSnapshot(ChatRetrievalSnapshot.builder()
                .snapshotType("INTERACTIVE")
                .sessionId(session.getId())
                .userKey(session.getUserKey())
                .question(question.trim())
                .normalizedKeyword(com.example.welfare.global.util.SearchKeywordSupport.normalizeText(question))
                .branchKey(normalizeNullable(branchKey))
                .branchSuggestionKeysJson(writeJson(branchSuggestions.stream()
                        .map(ChatBranchOptionResponse::getBranchKey)
                        .toList()))
                .resultCount(0)
                .build());
    }

    @Transactional
    public void recordInteractiveTrace(ChatSession session,
                                       String question,
                                       ChatPolicyService.CandidateTrace trace,
                                       boolean needsClarification) {
        persistSnapshot(toSnapshotBuilder("INTERACTIVE", null, question, trace, needsClarification)
                .sessionId(session.getId())
                .userKey(session.getUserKey())
                .build());
    }

    @Transactional
    public void recordEvaluationTrace(String scenarioKey,
                                      String question,
                                      List<ChatBranchOptionResponse> branchSuggestions,
                                      ChatPolicyService.CandidateTrace trace) {
        ChatRetrievalSnapshot.ChatRetrievalSnapshotBuilder builder =
                toSnapshotBuilder("EVALUATION", scenarioKey, question, trace, false);
        if (branchSuggestions != null && !branchSuggestions.isEmpty()) {
            builder.branchSuggestionKeysJson(writeJson(branchSuggestions.stream()
                    .map(ChatBranchOptionResponse::getBranchKey)
                    .toList()));
        }
        persistSnapshot(builder.build());
    }

    @Transactional(readOnly = true)
    public List<ChatRetrievalSnapshot> findSessionSnapshots(Long sessionId) {
        if (sessionId == null) {
            return List.of();
        }
        return chatRetrievalSnapshotRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
    }

    private ChatRetrievalSnapshot.ChatRetrievalSnapshotBuilder toSnapshotBuilder(String snapshotType,
                                                                                 String scenarioKey,
                                                                                 String question,
                                                                                 ChatPolicyService.CandidateTrace trace,
                                                                                 Boolean needsClarification) {
        return ChatRetrievalSnapshot.builder()
                .snapshotType(snapshotType)
                .scenarioKey(normalizeNullable(scenarioKey))
                .question(question.trim())
                .normalizedKeyword(normalizeNullable(trace.normalizedKeyword()))
                .searchKeyword(normalizeNullable(trace.searchKeyword()))
                .branchKey(normalizeNullable(trace.branchKey()))
                .preferredCategory(normalizeNullable(trace.preferredCategory()))
                .preferredTermsJson(writeJson(trace.preferredTerms()))
                .ftsServiceIdsJson(writeJson(serviceIds(trace.ftsCandidates())))
                .semanticServiceIdsJson(writeJson(serviceIds(trace.semanticCandidates())))
                .mergedServiceIdsJson(writeJson(serviceIds(trace.finalCandidates())))
                .fallbackStrategy(normalizeNullable(trace.fallbackStrategy()))
                .needsClarification(needsClarification)
                .resultCount(trace.finalCandidates().size());
    }

    private List<Long> serviceIds(List<ChatPolicyCandidate> candidates) {
        return candidates.stream()
                .map(ChatPolicyCandidate::getServiceId)
                .toList();
    }

    private void persistSnapshot(ChatRetrievalSnapshot snapshot) {
        try {
            chatRetrievalSnapshotRepository.save(snapshot);
        } catch (RuntimeException e) {
            log.warn("[ChatRetrievalSnapshotService] snapshot 저장 실패 type={} scenarioKey={} sessionId={} questionLength={}",
                    snapshot.getSnapshotType(),
                    snapshot.getScenarioKey(),
                    snapshot.getSessionId(),
                    lengthOf(snapshot.getQuestion()),
                    e);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("retrieval snapshot json serialization failed", e);
        }
    }

    private String normalizeNullable(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private Integer lengthOf(String question) {
        return question != null ? question.length() : null;
    }
}
