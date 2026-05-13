package com.example.welfare.policy.service;

import com.example.welfare.chat.config.ChatRetrievalProperties;
import com.example.welfare.chat.repository.ChatPolicyReadCondition;
import com.example.welfare.chat.service.ChatSemanticSearchService;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.WelfareServiceRepository;
import com.example.welfare.policy.repository.WelfareServiceSearchRepository;
import com.example.welfare.recommend.repository.RecommendationCandidateReadCondition;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PolicyExplorationService {

    private static final List<WelfareService.ServiceStatus> CHAT_SEARCHABLE_STATUSES = List.of(
            WelfareService.ServiceStatus.ACTIVE,
            WelfareService.ServiceStatus.UPCOMING
    );

    private final WelfareServiceRepository welfareServiceRepository;
    private final WelfareServiceSearchRepository welfareServiceSearchRepository;
    private final ChatSemanticSearchService chatSemanticSearchService;
    private final ChatRetrievalProperties chatRetrievalProperties;

    @Transactional(readOnly = true)
    public List<WelfareService> findChatCandidates(ChatPolicyReadCondition condition) {
        return traceChatCandidates(condition).finalCandidates();
    }

    @Transactional(readOnly = true)
    public ChatExplorationTrace traceChatCandidates(ChatPolicyReadCondition condition) {
        return traceChatCandidates(condition, chatRetrievalProperties);
    }

    @Transactional(readOnly = true)
    public ChatExplorationTrace traceChatCandidates(ChatPolicyReadCondition condition,
                                                    ChatRetrievalProperties tuning) {
        LinkedHashMap<Long, WelfareService> merged = new LinkedHashMap<>();
        String searchKeyword = buildSearchKeyword(condition.keyword(), condition.preferredTerms(), tuning);
        List<WelfareService> ftsCandidates = List.of();

        if (StringUtils.hasText(searchKeyword)) {
            ftsCandidates = welfareServiceSearchRepository.searchChatCandidates(searchKeyword, condition.limit()).stream()
                    .filter(service -> matchesPreferredCategory(service, condition.preferredCategory()))
                    .toList();
            ftsCandidates.forEach(service -> merged.putIfAbsent(service.getId(), service));
        }

        List<WelfareService> semanticCandidates = chatSemanticSearchService.findCandidates(
                condition.keyword(),
                condition.preferredCategory(),
                condition.preferredTerms(),
                condition.limit()
        );
        int semanticAddLimit = merged.isEmpty()
                ? Math.min(condition.limit(), tuning.semanticOnlyLimit())
                : Math.min(
                Math.max(0, condition.limit() - merged.size()),
                tuning.semanticBlendLimit()
        );
        addUniqueCandidates(merged, semanticCandidates, semanticAddLimit);

        int minimumTargetCount = Math.min(condition.limit(), tuning.minResultCount());
        if (merged.size() >= minimumTargetCount) {
            return new ChatExplorationTrace(
                    searchKeyword,
                    "MERGED_RESULTS",
                    ftsCandidates,
                    semanticCandidates,
                    merged.values().stream()
                    .limit(condition.limit())
                    .toList()
            );
        }

        String fallbackStrategy;
        List<WelfareService> fallbackCandidates;
        if (StringUtils.hasText(condition.preferredCategory())) {
            fallbackCandidates = welfareServiceRepository
                    .findBySearchYouthRelevantTrueAndStatusInAndUnifiedCategoryOrderByApiViewCountDescViewCountDescCreatedAtDesc(
                            CHAT_SEARCHABLE_STATUSES,
                            condition.preferredCategory(),
                            PageRequest.of(0, Math.max(condition.limit(), tuning.minResultCount()))
                    );
            fallbackStrategy = merged.isEmpty() ? "CATEGORY_FALLBACK" : "MERGED_WITH_CATEGORY_FILL";
        } else {
            fallbackCandidates =
                    welfareServiceRepository.findBySearchYouthRelevantTrueAndStatusInOrderByApiViewCountDescViewCountDescCreatedAtDesc(
                            CHAT_SEARCHABLE_STATUSES,
                            PageRequest.of(0, Math.max(condition.limit(), tuning.minResultCount()))
                    );
            fallbackStrategy = merged.isEmpty() ? "POPULAR_FALLBACK" : "MERGED_WITH_POPULAR_FILL";
        }

        addUniqueCandidates(
                merged,
                fallbackCandidates,
                Math.max(0, condition.limit() - merged.size())
        );
        return new ChatExplorationTrace(
                searchKeyword,
                fallbackStrategy,
                ftsCandidates,
                semanticCandidates,
                merged.values().stream()
                        .limit(condition.limit())
                        .toList()
        );
    }

    @Transactional(readOnly = true)
    public List<WelfareService> findRecommendationBaseCandidates(RecommendationCandidateReadCondition condition) {
        if (StringUtils.hasText(condition.regionCode())) {
            return welfareServiceRepository.findCandidatesWithRegionCode(
                    condition.age(),
                    condition.incomeLevel(),
                    condition.regionCode().trim(),
                    PageRequest.of(0, condition.baseFetchSize())
            );
        }
        if (condition.sido() != null) {
            return welfareServiceRepository.findCandidatesWithSido(
                    condition.age(),
                    condition.incomeLevel(),
                    condition.sido(),
                    PageRequest.of(0, condition.baseFetchSize())
            );
        }
        return welfareServiceRepository.findCandidates(
                condition.age(),
                condition.incomeLevel(),
                PageRequest.of(0, condition.baseFetchSize())
        );
    }

    @Transactional(readOnly = true)
    public List<WelfareService> findRecommendationLatestCandidates(RecommendationCandidateReadCondition condition) {
        if (StringUtils.hasText(condition.regionCode())) {
            return welfareServiceRepository.findLatestCandidatesWithRegionCode(
                    condition.age(),
                    condition.incomeLevel(),
                    condition.regionCode().trim(),
                    PageRequest.of(0, condition.latestFetchSize())
            );
        }
        if (condition.sido() != null) {
            return welfareServiceRepository.findLatestCandidatesWithSido(
                    condition.age(),
                    condition.incomeLevel(),
                    condition.sido(),
                    PageRequest.of(0, condition.latestFetchSize())
            );
        }
        return welfareServiceRepository.findLatestCandidates(
                condition.age(),
                condition.incomeLevel(),
                PageRequest.of(0, condition.latestFetchSize())
        );
    }

    private boolean matchesPreferredCategory(WelfareService service, String preferredCategory) {
        return !StringUtils.hasText(preferredCategory) || preferredCategory.equals(service.getUnifiedCategory());
    }

    private String buildSearchKeyword(String keyword, List<String> preferredTerms) {
        return buildSearchKeyword(keyword, preferredTerms, chatRetrievalProperties);
    }

    private String buildSearchKeyword(String keyword, List<String> preferredTerms, ChatRetrievalProperties tuning) {
        Set<String> tokens = new LinkedHashSet<>(com.example.welfare.global.util.SearchKeywordSupport.extractTokens(keyword));
        if (preferredTerms != null) {
            int addedPreferredTerms = 0;
            for (String preferredTerm : preferredTerms) {
                if (addedPreferredTerms >= tuning.maxPreferredTermsInSearchKeyword()) {
                    break;
                }
                List<String> preferredTokens = com.example.welfare.global.util.SearchKeywordSupport.extractTokens(preferredTerm);
                if (preferredTokens.isEmpty()) {
                    continue;
                }
                tokens.addAll(preferredTokens);
                addedPreferredTerms++;
            }
        }
        return String.join(" ", tokens);
    }

    private void addUniqueCandidates(LinkedHashMap<Long, WelfareService> merged,
                                     List<WelfareService> candidates,
                                     int addLimit) {
        if (addLimit <= 0) {
            return;
        }
        int added = 0;
        for (WelfareService candidate : candidates) {
            if (candidate == null || merged.containsKey(candidate.getId())) {
                continue;
            }
            merged.put(candidate.getId(), candidate);
            added++;
            if (added >= addLimit) {
                return;
            }
        }
    }

    public record ChatExplorationTrace(
            String searchKeyword,
            String fallbackStrategy,
            List<WelfareService> ftsCandidates,
            List<WelfareService> semanticCandidates,
            List<WelfareService> finalCandidates
    ) {
    }
}
