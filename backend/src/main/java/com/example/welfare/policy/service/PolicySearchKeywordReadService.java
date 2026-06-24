package com.example.welfare.policy.service;

import com.example.welfare.global.util.SearchKeywordSupport;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.PolicySearchKeywordReadRepository;
import com.example.welfare.policy.repository.WelfareServiceSearchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
        ).stream()
                .filter(this::isPublicKeywordCandidate)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<String> getSuggestions(String keyword, Integer limit) {
        String normalizedInput = normalizeInput(keyword);
        if (normalizedInput == null) {
            return List.of();
        }
        int normalizedLimit = normalizeSuggestionLimit(limit);
        List<String> logSuggestions = policySearchKeywordReadRepository.findSuggestions(
                        normalizedInput,
                        LocalDateTime.now().minusDays(SUGGESTION_WINDOW_DAYS),
                        MIN_STORED_KEYWORD_LENGTH,
                        normalizedLimit
                ).stream()
                .filter(this::isPublicKeywordCandidate)
                .map(String::trim)
                .toList();

        List<String> policyTitleSuggestions = logSuggestions.size() < normalizedLimit
                ? welfareServiceSearchRepository.searchChatCandidates(normalizedInput, POLICY_CANDIDATE_LIMIT).stream()
                    .map(WelfareService::getTitle)
                    .filter(title -> title != null && !title.isBlank())
                    .map(String::trim)
                    .toList()
                : List.of();

        List<String> merged = mergeSuggestions(normalizedInput, logSuggestions, policyTitleSuggestions);
        if (merged.isEmpty()) {
            return List.of();
        }
        return merged.stream()
                .limit(normalizedLimit)
                .toList();
    }

    private List<String> mergeSuggestions(String normalizedInput,
                                          List<String> logSuggestions,
                                          List<String> policyTitleSuggestions) {
        Map<String, CandidateMetadata> logMetadata = buildMetadata(logSuggestions);
        Map<String, CandidateMetadata> policyMetadata = buildMetadata(policyTitleSuggestions);

        return Stream.concat(logSuggestions.stream(), policyTitleSuggestions.stream())
                .distinct()
                .sorted(candidateComparator(normalizedInput, logMetadata, policyMetadata))
                .toList();
    }

    private Map<String, CandidateMetadata> buildMetadata(List<String> candidates) {
        return java.util.stream.IntStream.range(0, candidates.size())
                .boxed()
                .collect(Collectors.toMap(
                        candidates::get,
                        index -> CandidateMetadata.of(index),
                        (left, right) -> left
                ));
    }

    private Comparator<String> candidateComparator(String normalizedInput,
                                                   Map<String, CandidateMetadata> logMetadata,
                                                   Map<String, CandidateMetadata> policyMetadata) {
        return Comparator
                .comparingInt((String candidate) -> matchPriority(candidate, normalizedInput))
                .reversed()
                .thenComparing(Comparator.comparingInt(
                        (String candidate) -> sourcePriority(candidate, logMetadata, policyMetadata)
                ).reversed())
                .thenComparingInt(candidate -> sourceOrder(candidate, logMetadata, policyMetadata))
                .thenComparing(String::toLowerCase, String.CASE_INSENSITIVE_ORDER);
    }

    private int matchPriority(String candidate, String normalizedInput) {
        String normalizedCandidate = normalizeInput(candidate);
        if (normalizedCandidate == null) {
            return 0;
        }

        String compactInput = compact(normalizedInput);
        String compactCandidate = compact(normalizedCandidate);
        if (compactCandidate.equals(compactInput)) {
            return 4;
        }
        if (compactCandidate.startsWith(compactInput)) {
            return 3;
        }
        if (compactCandidate.contains(compactInput)) {
            return 2;
        }
        return normalizedCandidate.contains(normalizedInput) ? 1 : 0;
    }

    private int sourcePriority(String candidate,
                               Map<String, CandidateMetadata> logMetadata,
                               Map<String, CandidateMetadata> policyMetadata) {
        boolean inLog = logMetadata.containsKey(candidate);
        boolean inPolicy = policyMetadata.containsKey(candidate);
        if (inLog && inPolicy) {
            return 3;
        }
        if (inLog) {
            return 2;
        }
        return inPolicy ? 1 : 0;
    }

    private int sourceOrder(String candidate,
                            Map<String, CandidateMetadata> logMetadata,
                            Map<String, CandidateMetadata> policyMetadata) {
        CandidateMetadata log = logMetadata.get(candidate);
        CandidateMetadata policy = policyMetadata.get(candidate);
        if (log != null && policy != null) {
            return Math.min(log.order(), policy.order());
        }
        if (policy != null) {
            return policy.order();
        }
        return log != null ? log.order() : Integer.MAX_VALUE;
    }

    private String compact(String text) {
        return text.replace(" ", "").toLowerCase(Locale.ROOT);
    }

    private boolean isPublicKeywordCandidate(String value) {
        return value != null
                && !value.isBlank()
                && !PolicySearchKeywordPrivacy.containsSensitiveIdentifier(value);
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

    private record CandidateMetadata(int order) {
        private static CandidateMetadata of(int order) {
            return new CandidateMetadata(order);
        }
    }
}
