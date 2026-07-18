package com.example.welfare.policy.service;

import com.example.welfare.chat.config.ChatRetrievalProperties;
import com.example.welfare.chat.repository.ChatPolicyReadCondition;
import com.example.welfare.chat.service.ChatSemanticSearchService;
import com.example.welfare.global.util.RegionCodeUtil;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.WelfareServiceRepository;
import com.example.welfare.policy.repository.WelfareServiceSearchRepository;
import com.example.welfare.recommend.repository.RecommendationCandidateReadCondition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
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
        String regionSearchKeyword = buildRegionSearchKeyword(condition, tuning);
        int searchLimit = expandedChatSearchLimit(condition);
        List<WelfareService> ftsCandidates = List.of();

        if (StringUtils.hasText(regionSearchKeyword)) {
            ftsCandidates = welfareServiceSearchRepository.searchChatCandidates(regionSearchKeyword, searchLimit).stream()
                    .filter(service -> matchesPreferredCategory(service, condition.preferredCategory()))
                    .toList();
            ftsCandidates.forEach(service -> merged.putIfAbsent(service.getId(), service));
        }

        if (StringUtils.hasText(searchKeyword)) {
            List<WelfareService> keywordCandidates = welfareServiceSearchRepository.searchChatCandidates(searchKeyword, searchLimit).stream()
                    .filter(service -> matchesPreferredCategory(service, condition.preferredCategory()))
                    .toList();
            LinkedHashMap<Long, WelfareService> ftsMerged = new LinkedHashMap<>();
            ftsCandidates.forEach(service -> ftsMerged.putIfAbsent(service.getId(), service));
            keywordCandidates.forEach(service -> ftsMerged.putIfAbsent(service.getId(), service));
            ftsCandidates = ftsMerged.values().stream().toList();
            keywordCandidates.forEach(service -> merged.putIfAbsent(service.getId(), service));
        }

        int semanticAddLimit = resolveSemanticAddLimit(merged.size(), condition.limit(), tuning);
        List<WelfareService> semanticCandidates;
        if (semanticAddLimit <= 0) {
            semanticCandidates = List.of();
            log.info("[ChatSemanticSearchTiming] outcome=skipped reason=fts_full preferredCategory={} preferredTerms={} requestedLimit={} searchLimit={} ftsCandidates={} mergedCandidates={}",
                    condition.preferredCategory(),
                    condition.preferredTerms() != null ? condition.preferredTerms().size() : 0,
                    condition.limit(),
                    searchLimit,
                    ftsCandidates.size(),
                    merged.size());
        } else {
            semanticCandidates = chatSemanticSearchService.findCandidates(
                    condition.keyword(),
                    condition.preferredCategory(),
                    condition.preferredTerms(),
                    searchLimit
            );
        }
        addUniqueCandidates(merged, semanticCandidates, semanticAddLimit);

        int minimumTargetCount = Math.min(condition.limit(), tuning.minResultCount());
        if (merged.size() >= minimumTargetCount) {
            List<WelfareService> filteredCandidates = filterRegionMismatches(
                    merged.values().stream().toList(),
                    condition
            );
            filteredCandidates = fillExplicitRegionCategoryCandidates(filteredCandidates, condition, tuning);
            return new ChatExplorationTrace(
                    searchKeyword,
                    "MERGED_RESULTS",
                    ftsCandidates,
                    semanticCandidates,
                    orderChatCandidatesForRegion(filteredCandidates, condition).stream()
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
        List<WelfareService> filteredCandidates = filterRegionMismatches(
                merged.values().stream().toList(),
                condition
        );
        filteredCandidates = fillExplicitRegionCategoryCandidates(filteredCandidates, condition, tuning);
        return new ChatExplorationTrace(
                searchKeyword,
                fallbackStrategy,
                ftsCandidates,
                semanticCandidates,
                orderChatCandidatesForRegion(filteredCandidates, condition).stream()
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
                    condition.sido(),
                    condition.sgg(),
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
                    condition.sido(),
                    condition.sgg(),
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

    private int expandedChatSearchLimit(ChatPolicyReadCondition condition) {
        if (!hasRegionContext(condition)) {
            return condition.limit();
        }
        return Math.max(condition.limit(), Math.min(condition.limit() * 4, 20));
    }

    private List<WelfareService> orderChatCandidatesForRegion(List<WelfareService> candidates,
                                                              ChatPolicyReadCondition condition) {
        if (candidates.isEmpty() || !hasRegionContext(condition)) {
            return candidates;
        }

        List<Long> serviceIds = candidates.stream()
                .map(WelfareService::getId)
                .filter(java.util.Objects::nonNull)
                .toList();
        if (serviceIds.isEmpty()) {
            return candidates;
        }

        Set<Long> servicesWithRegions = new java.util.HashSet<>(
                welfareServiceRepository.findServiceIdsWithRegions(serviceIds)
        );
        Set<Long> regionMatchedServices = new java.util.HashSet<>(
                welfareServiceRepository.findRegionMatchedServiceIds(
                        serviceIds,
                        condition.regionCode(),
                        condition.sido(),
                        condition.sgg()
                )
        );
        Map<Long, Integer> originalOrder = new java.util.HashMap<>();
        for (int i = 0; i < candidates.size(); i++) {
            originalOrder.put(candidates.get(i).getId(), i);
        }

        Comparator<WelfareService> comparator = condition.explicitRegion()
                ? Comparator
                .comparingInt((WelfareService service) -> regionRank(
                        service,
                        condition,
                        servicesWithRegions,
                        regionMatchedServices
                ))
                .thenComparingInt(service -> branchTermRank(service, condition.preferredTerms()))
                : Comparator
                .comparingInt((WelfareService service) -> branchTermRank(service, condition.preferredTerms()))
                .thenComparingInt(service -> regionRank(
                        service,
                        condition,
                        servicesWithRegions,
                        regionMatchedServices
                ));

        List<WelfareService> ordered = new ArrayList<>(candidates);
        ordered.sort(comparator.thenComparingInt(service -> originalOrder.getOrDefault(
                service.getId(),
                Integer.MAX_VALUE
        )));
        return ordered;
    }

    private List<WelfareService> fillExplicitRegionCategoryCandidates(List<WelfareService> candidates,
                                                                      ChatPolicyReadCondition condition,
                                                                      ChatRetrievalProperties tuning) {
        if (!condition.explicitRegion()
                || !hasRegionContext(condition)
                || !StringUtils.hasText(condition.preferredCategory())
                || hasExplicitRegionSpecificCandidate(candidates, condition)) {
            return candidates;
        }

        List<WelfareService> regionCategoryCandidates = welfareServiceRepository.findExplicitRegionChatCategoryFill(
                CHAT_SEARCHABLE_STATUSES,
                condition.preferredCategory(),
                condition.regionCode(),
                condition.sido(),
                condition.sgg(),
                PageRequest.of(0, Math.max(condition.limit(), tuning.minResultCount()))
        );
        if (regionCategoryCandidates.isEmpty()) {
            return candidates;
        }

        LinkedHashMap<Long, WelfareService> merged = new LinkedHashMap<>();
        candidates.forEach(candidate -> merged.putIfAbsent(candidate.getId(), candidate));
        addUniqueCandidates(merged, regionCategoryCandidates, condition.limit());
        return filterRegionMismatches(merged.values().stream().toList(), condition);
    }

    private boolean hasExplicitRegionSpecificCandidate(List<WelfareService> candidates,
                                                       ChatPolicyReadCondition condition) {
        if (candidates.isEmpty()) {
            return false;
        }

        List<Long> serviceIds = candidates.stream()
                .map(WelfareService::getId)
                .filter(java.util.Objects::nonNull)
                .toList();
        if (serviceIds.isEmpty()) {
            return false;
        }

        Set<Long> servicesWithRegions = new java.util.HashSet<>(
                welfareServiceRepository.findServiceIdsWithRegions(serviceIds)
        );
        Set<Long> regionMatchedServices = new java.util.HashSet<>(
                welfareServiceRepository.findRegionMatchedServiceIds(
                        serviceIds,
                        condition.regionCode(),
                        condition.sido(),
                        condition.sgg()
                )
        );
        if (!regionMatchedServices.isEmpty()) {
            return true;
        }

        return candidates.stream()
                .filter(service -> service.getId() != null && servicesWithRegions.contains(service.getId()))
                .map(this::inferServiceRegions)
                .flatMap(List::stream)
                .anyMatch(region -> inferredRegionMatches(condition, region));
    }

    private List<WelfareService> filterRegionMismatches(List<WelfareService> candidates,
                                                        ChatPolicyReadCondition condition) {
        if (candidates.isEmpty() || !hasRegionContext(condition)) {
            return candidates;
        }

        List<Long> serviceIds = candidates.stream()
                .map(WelfareService::getId)
                .filter(java.util.Objects::nonNull)
                .toList();
        if (serviceIds.isEmpty()) {
            return candidates;
        }

        Set<Long> servicesWithRegions = new java.util.HashSet<>(
                welfareServiceRepository.findServiceIdsWithRegions(serviceIds)
        );
        Set<Long> regionMatchedServices = new java.util.HashSet<>(
                welfareServiceRepository.findRegionMatchedServiceIds(
                        serviceIds,
                        condition.regionCode(),
                        condition.sido(),
                        condition.sgg()
                )
        );

        List<WelfareService> filtered = candidates.stream()
                .filter(service -> !isExplicitRegionMismatch(
                        service,
                        condition,
                        servicesWithRegions,
                        regionMatchedServices
                ))
                .toList();
        if (condition.explicitRegion()) {
            return filtered;
        }
        return filtered.isEmpty() ? candidates : filtered;
    }

    private boolean isExplicitRegionMismatch(WelfareService service,
                                             ChatPolicyReadCondition condition,
                                             Set<Long> servicesWithRegions,
                                             Set<Long> regionMatchedServices) {
        Long serviceId = service.getId();
        if (serviceId != null && regionMatchedServices.contains(serviceId)) {
            return false;
        }
        if (serviceId != null && servicesWithRegions.contains(serviceId)) {
            List<RegionCodeUtil.RegionName> inferredRegions = inferServiceRegions(service);
            return inferredRegions.isEmpty()
                    || inferredRegions.stream().noneMatch(region -> inferredRegionMatches(condition, region));
        }

        List<RegionCodeUtil.RegionName> inferredRegions = inferServiceRegions(service);
        if (inferredRegions.isEmpty()) {
            return false;
        }
        return inferredRegions.stream().noneMatch(region -> inferredRegionMatches(condition, region));
    }

    private List<RegionCodeUtil.RegionName> inferServiceRegions(WelfareService service) {
        return RegionCodeUtil.inferRegionNamesFromText(
                service.getTitle(),
                service.getDescription(),
                service.getSupportContent(),
                service.getHostOrg(),
                service.getOperatingOrg()
        );
    }

    private boolean inferredRegionMatches(ChatPolicyReadCondition condition,
                                          RegionCodeUtil.RegionName region) {
        if (!StringUtils.hasText(condition.sido()) || !condition.sido().equals(region.sidoName())) {
            return false;
        }
        if (!StringUtils.hasText(condition.sgg())) {
            return true;
        }
        return condition.sgg().equals(region.sggName());
    }

    private int branchTermRank(WelfareService service, List<String> preferredTerms) {
        if (preferredTerms == null || preferredTerms.isEmpty()) {
            return 0;
        }
        return preferredTerms.stream().anyMatch(term -> matchesServiceText(service, term)) ? 0 : 1;
    }

    private int regionRank(WelfareService service,
                           ChatPolicyReadCondition condition,
                           Set<Long> servicesWithRegions,
                           Set<Long> regionMatchedServices) {
        Long serviceId = service.getId();
        if (serviceId != null && regionMatchedServices.contains(serviceId)) {
            return 0;
        }
        if (matchesSidoText(service, condition.sido())) {
            return 0;
        }
        boolean hasExplicitRegions = serviceId != null && servicesWithRegions.contains(serviceId);
        if (!hasExplicitRegions && !isLocalOnlySource(service)) {
            return 1;
        }
        return 2;
    }

    private boolean hasRegionContext(ChatPolicyReadCondition condition) {
        return StringUtils.hasText(condition.regionCode()) || StringUtils.hasText(condition.sido());
    }

    private boolean isLocalOnlySource(WelfareService service) {
        return service.getSourceType() == WelfareService.SourceType.BOKJIRO_LOCAL
                || service.getSourceType() == WelfareService.SourceType.GOV24;
    }

    private boolean matchesSidoText(WelfareService service, String sido) {
        if (!StringUtils.hasText(sido)) {
            return false;
        }
        String fullSido = sido.trim();
        String shortSido = fullSido
                .replace("특별자치도", "")
                .replace("특별자치시", "")
                .replace("광역시", "")
                .replace("특별시", "")
                .replace("도", "");
        return contains(service.getTitle(), fullSido)
                || contains(service.getDescription(), fullSido)
                || (StringUtils.hasText(shortSido)
                && (contains(service.getTitle(), shortSido) || contains(service.getDescription(), shortSido)));
    }

    private boolean contains(String source, String token) {
        return StringUtils.hasText(source) && StringUtils.hasText(token) && source.contains(token);
    }

    private boolean matchesServiceText(WelfareService service, String term) {
        if (!StringUtils.hasText(term)) {
            return false;
        }
        String normalizedTerm = term.trim();
        return contains(service.getTitle(), normalizedTerm)
                || contains(service.getDescription(), normalizedTerm)
                || contains(service.getSupportContent(), normalizedTerm)
                || contains(service.getKeyword(), normalizedTerm);
    }

    private String buildRegionSearchKeyword(ChatPolicyReadCondition condition, ChatRetrievalProperties tuning) {
        if (!StringUtils.hasText(condition.sido()) || condition.preferredTerms().isEmpty()) {
            return null;
        }
        Set<String> tokens = new LinkedHashSet<>();
        String shortSido = shortenSido(condition.sido());
        if (StringUtils.hasText(shortSido)) {
            tokens.add(shortSido);
        } else {
            tokens.add(condition.sido().trim());
        }
        int addedPreferredTerms = 0;
        for (String preferredTerm : condition.preferredTerms()) {
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
        return String.join(" ", tokens);
    }

    private String shortenSido(String sido) {
        if (!StringUtils.hasText(sido)) {
            return null;
        }
        return sido.trim()
                .replace("특별자치도", "")
                .replace("특별자치시", "")
                .replace("광역시", "")
                .replace("특별시", "")
                .replace("도", "");
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

    private int resolveSemanticAddLimit(int mergedCandidateCount,
                                        int requestedLimit,
                                        ChatRetrievalProperties tuning) {
        if (mergedCandidateCount <= 0) {
            return Math.min(requestedLimit, tuning.semanticOnlyLimit());
        }
        return Math.min(
                Math.max(0, requestedLimit - mergedCandidateCount),
                tuning.semanticBlendLimit()
        );
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
