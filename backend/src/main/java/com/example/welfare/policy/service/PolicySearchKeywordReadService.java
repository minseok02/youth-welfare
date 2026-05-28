package com.example.welfare.policy.service;

import com.example.welfare.global.util.SearchKeywordSupport;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.PolicySearchKeywordReadRepository;
import com.example.welfare.policy.repository.WelfareServiceSearchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PolicySearchKeywordReadService {

    private static final int TRENDING_DEFAULT_LIMIT = 6;
    private static final int TRENDING_MAX_LIMIT = 10;
    private static final int SUGGESTION_DEFAULT_LIMIT = 8;
    private static final int SUGGESTION_MAX_LIMIT = 10;
    private static final int MIN_STORED_KEYWORD_LENGTH = 2;
    private static final int MAX_INPUT_LENGTH = 100;
    private static final int TRENDING_WINDOW_DAYS = 30;
    private static final int SUGGESTION_WINDOW_DAYS = 90;
    private static final int POLICY_CANDIDATE_LIMIT = 5;

    private final PolicySearchKeywordReadRepository policySearchKeywordReadRepository;
    private final WelfareServiceSearchRepository welfareServiceSearchRepository;

    @Transactional(readOnly = true)
    public List<String> getTrendingKeywords(Integer limit) {
        return policySearchKeywordReadRepository.findTrendingKeywords(
                LocalDateTime.now().minusDays(TRENDING_WINDOW_DAYS),
                MIN_STORED_KEYWORD_LENGTH,
                normalizeTrendingLimit(limit)
        );
    }

    @Transactional(readOnly = true)
    public List<String> getSuggestions(String keyword, Integer limit) {
        String normalizedInput = normalizeInput(keyword);
        if (normalizedInput == null) {
            return List.of();
        }
        int normalizedLimit = normalizeSuggestionLimit(limit);
        LinkedHashSet<String> merged = new LinkedHashSet<>();

        policySearchKeywordReadRepository.findSuggestions(
                        normalizedInput,
                        LocalDateTime.now().minusDays(SUGGESTION_WINDOW_DAYS),
                        MIN_STORED_KEYWORD_LENGTH,
                        normalizedLimit
                ).stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .forEach(merged::add);

        if (merged.size() < normalizedLimit) {
            welfareServiceSearchRepository.searchChatCandidates(normalizedInput, POLICY_CANDIDATE_LIMIT).stream()
                    .map(WelfareService::getTitle)
                    .filter(title -> title != null && !title.isBlank())
                    .map(String::trim)
                    .forEach(merged::add);
        }

        if (merged.isEmpty()) {
            return List.of();
        }
        return merged.stream()
                .limit(normalizedLimit)
                .toList();
    }

    private int normalizeTrendingLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return TRENDING_DEFAULT_LIMIT;
        }
        return Math.min(limit, TRENDING_MAX_LIMIT);
    }

    private int normalizeSuggestionLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return SUGGESTION_DEFAULT_LIMIT;
        }
        return Math.min(limit, SUGGESTION_MAX_LIMIT);
    }

    private String normalizeInput(String keyword) {
        if (keyword == null) {
            return null;
        }
        String trimmed = keyword.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_INPUT_LENGTH) {
            return null;
        }
        String normalized = SearchKeywordSupport.normalizeText(trimmed);
        return normalized.isBlank() ? null : normalized;
    }
}
