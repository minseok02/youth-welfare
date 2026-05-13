package com.example.welfare.chat.service;

import com.example.welfare.chat.config.ChatRetrievalProperties;
import com.example.welfare.chat.dto.ChatPolicyCandidate;
import com.example.welfare.chat.repository.ChatPolicyReadCondition;
import com.example.welfare.chat.repository.ChatPolicyReadRepository;
import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.global.util.SearchKeywordSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatPolicyService {

    private static final int DEFAULT_CANDIDATE_LIMIT = 5;
    private static final int MAX_CANDIDATE_LIMIT = 10;

    private final ChatPolicyReadRepository chatPolicyReadRepository;
    private final ChatBranchCatalog chatBranchCatalog;
    private final ChatCategoryHintCatalog chatCategoryHintCatalog;

    @Transactional(readOnly = true)
    public List<ChatPolicyCandidate> findCandidates(String question) {
        return findCandidates(question, null, DEFAULT_CANDIDATE_LIMIT);
    }

    @Transactional(readOnly = true)
    public List<ChatPolicyCandidate> findCandidates(String question, int limit) {
        return findCandidates(question, null, limit);
    }

    @Transactional(readOnly = true)
    public List<ChatPolicyCandidate> findCandidates(String question, String branchKey, int limit) {
        return traceCandidates(question, branchKey, limit).finalCandidates();
    }

    @Transactional(readOnly = true)
    public CandidateTrace traceCandidates(String question, String branchKey, int limit) {
        return traceCandidates(question, branchKey, limit, null);
    }

    @Transactional(readOnly = true)
    public CandidateTrace traceCandidates(String question,
                                          String branchKey,
                                          int limit,
                                          ChatRetrievalProperties tuning) {
        if (!StringUtils.hasText(question)) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }

        int normalizedLimit = normalizeLimit(limit);
        String keyword = SearchKeywordSupport.normalizeText(question);
        ChatBranchCatalog.BranchDefinition branch = resolveBranch(branchKey);
        ChatCategoryHintCatalog.CategoryHint categoryHint = branch == null
                ? chatCategoryHintCatalog.infer(keyword).orElse(null)
                : null;
        ChatPolicyReadCondition condition = new ChatPolicyReadCondition(
                keyword,
                normalizedLimit,
                branch != null ? branch.branchKey() : null,
                branch != null ? branch.preferredCategory() : categoryHint != null ? categoryHint.preferredCategory() : null,
                branch != null ? branch.searchTerms() : categoryHint != null ? categoryHint.searchTerms() : List.of()
        );
        com.example.welfare.policy.service.PolicyExplorationService.ChatExplorationTrace trace =
                tuning == null
                        ? chatPolicyReadRepository.traceCandidates(condition)
                        : chatPolicyReadRepository.traceCandidates(condition, tuning);

        return new CandidateTrace(
                keyword,
                trace.searchKeyword(),
                condition.branchKey(),
                condition.preferredCategory(),
                condition.preferredTerms(),
                trace.fallbackStrategy(),
                trace.ftsCandidates().stream().map(ChatPolicyCandidate::from).toList(),
                trace.semanticCandidates().stream().map(ChatPolicyCandidate::from).toList(),
                trace.finalCandidates().stream().map(ChatPolicyCandidate::from).toList()
        );
    }

    private ChatBranchCatalog.BranchDefinition resolveBranch(String branchKey) {
        if (!StringUtils.hasText(branchKey)) {
            return null;
        }
        return chatBranchCatalog.findByKey(branchKey)
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_INPUT));
    }

    private int normalizeLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_CANDIDATE_LIMIT;
        }
        return Math.min(limit, MAX_CANDIDATE_LIMIT);
    }

    public record CandidateTrace(
            String normalizedKeyword,
            String searchKeyword,
            String branchKey,
            String preferredCategory,
            List<String> preferredTerms,
            String fallbackStrategy,
            List<ChatPolicyCandidate> ftsCandidates,
            List<ChatPolicyCandidate> semanticCandidates,
            List<ChatPolicyCandidate> finalCandidates
    ) {
    }
}
