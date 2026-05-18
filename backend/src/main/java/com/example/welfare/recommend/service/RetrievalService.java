package com.example.welfare.recommend.service;

import com.example.welfare.policy.entity.ServiceTag;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.recommend.repository.RecommendationCandidateReadCondition;
import com.example.welfare.recommend.repository.RecommendationCandidateReadRepository;
import com.example.welfare.recommend.dto.RecommendationCandidateProjection;
import com.example.welfare.recommend.dto.RetrievedRecommendationCandidates;
import com.example.welfare.recommend.dto.RecommendationUserSnapshot;
import com.example.welfare.recommend.support.RecommendationYouthRelevanceSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.ArrayList;
import java.util.stream.Collectors;

/**
 * 추천 후보 추출 — SQL 필터 (pass/fail)
 * K=50건 선별 + 신규 정책 M=5건 강제 포함
 */
@Service
@RequiredArgsConstructor
public class RetrievalService {

    private static final int K = 50;
    private static final int M = 5;
    private static final int FETCH_SIZE = 150; // 후처리 필터 감안해 넉넉히 조회

    private final RecommendationCandidateReadRepository recommendationCandidateReadRepository;
    private final RecommendationYouthRelevanceSupport recommendationYouthRelevanceSupport;
    private final RecommendationProjectionReadService recommendationProjectionReadService;

    @Transactional(readOnly = true)
    public RetrievedRecommendationCandidates retrieve(String clusterId, RecommendationUserSnapshot user) {
        RecommendationRetrievalTrace trace = trace(user);
        return new RetrievedRecommendationCandidates(trace.mergedCandidates(), trace.allProjections());
    }

    @Transactional(readOnly = true)
    public RecommendationRetrievalTrace trace(RecommendationUserSnapshot user) {
        int age = user.resolvedAge();
        int incomeLevel = user.resolvedIncomeLevel();

        RecommendationCandidateReadCondition condition = new RecommendationCandidateReadCondition(
                age,
                incomeLevel,
                user.sido(),
                normalizeRegionCode(user.regionCode()),
                FETCH_SIZE,
                M * 4
        );
        List<WelfareService> rawBaseCandidates = recommendationCandidateReadRepository.findBaseCandidates(condition);
        List<WelfareService> rawLatestCandidates = recommendationCandidateReadRepository.findLatestCandidates(condition);

        Map<Long, RecommendationCandidateProjection> projections = loadProjections(rawBaseCandidates, rawLatestCandidates);
        Map<Long, List<ServiceTag>> tagsByServiceId = loadTags(rawBaseCandidates, rawLatestCandidates);
        Map<Long, CandidateFilterTrace> filterTraces = evaluateCandidateFilters(
                rawBaseCandidates,
                rawLatestCandidates,
                projections,
                tagsByServiceId,
                age
        );

        boolean noPriorityProfile = user.priorities() == null || user.priorities().isEmpty();

        List<WelfareService> filteredBase = applyRecommendationFilters(rawBaseCandidates, filterTraces);
        if (noPriorityProfile) {
            filteredBase = rebalanceNoPriorityCandidates(filteredBase);
        }
        filteredBase = filteredBase.stream()
                .limit(K)
                .toList();

        List<WelfareService> filteredLatest = applyRecommendationFilters(rawLatestCandidates, filterTraces);
        if (noPriorityProfile) {
            filteredLatest = rebalanceNoPriorityCandidates(filteredLatest);
        }
        filteredLatest = filteredLatest.stream()
                .limit(M)
                .toList();

        List<WelfareService> candidates = mergeBaseAndLatest(filteredBase, filteredLatest).stream()
                .limit(K + M)
                .collect(Collectors.toList());

        return new RecommendationRetrievalTrace(
                rawBaseCandidates,
                rawLatestCandidates,
                filteredBase,
                filteredLatest,
                candidates,
                projections,
                filterTraces
        );
    }

    /**
     * 구조화된 나이 필드(min_age/max_age)가 비어있는 정책에 한해
     * KEYWORD의 COND_AGE_MIN_*, COND_AGE_MAX_* 토큰으로 보조 필터를 적용한다.
     */
    private List<WelfareService> applyRecommendationFilters(List<WelfareService> candidates,
                                                            Map<Long, CandidateFilterTrace> filterTraces) {
        if (candidates.isEmpty()) return candidates;

        return candidates.stream()
                .filter(service -> filterTraces.getOrDefault(service.getId(), CandidateFilterTrace.PASS_ALL).passesAll())
                .collect(Collectors.toList());
    }

    private Map<Long, RecommendationCandidateProjection> loadProjections(List<WelfareService> rawCandidates,
                                                                         List<WelfareService> latestCandidates) {
        LinkedHashSet<Long> serviceIds = new LinkedHashSet<>();
        rawCandidates.stream()
                .map(WelfareService::getId)
                .forEach(serviceIds::add);
        latestCandidates.stream()
                .map(WelfareService::getId)
                .forEach(serviceIds::add);
        return recommendationProjectionReadService.findCandidateProjectionsByServiceIds(List.copyOf(serviceIds));
    }

    private Map<Long, List<ServiceTag>> loadTags(List<WelfareService> rawCandidates,
                                                 List<WelfareService> latestCandidates) {
        LinkedHashSet<Long> serviceIds = new LinkedHashSet<>();
        rawCandidates.stream()
                .map(WelfareService::getId)
                .forEach(serviceIds::add);
        latestCandidates.stream()
                .map(WelfareService::getId)
                .forEach(serviceIds::add);
        if (serviceIds.isEmpty()) {
            return Map.of();
        }
        return recommendationCandidateReadRepository.findTagsByServiceIds(List.copyOf(serviceIds));
    }

    private Map<Long, CandidateFilterTrace> evaluateCandidateFilters(List<WelfareService> rawBaseCandidates,
                                                                     List<WelfareService> rawLatestCandidates,
                                                                     Map<Long, RecommendationCandidateProjection> projections,
                                                                     Map<Long, List<ServiceTag>> tagsByServiceId,
                                                                     int userAge) {
        LinkedHashMap<Long, CandidateFilterTrace> traces = new LinkedHashMap<>();
        ArrayList<WelfareService> allCandidates = new ArrayList<>(rawBaseCandidates.size() + rawLatestCandidates.size());
        allCandidates.addAll(rawBaseCandidates);
        allCandidates.addAll(rawLatestCandidates);
        for (WelfareService service : allCandidates) {
            if (service == null || traces.containsKey(service.getId())) {
                continue;
            }
            List<ServiceTag> tags = tagsByServiceId.getOrDefault(service.getId(), Collections.emptyList());
            boolean primaryAudienceRelevant = isPrimaryAudienceRelevant(service, projections.get(service.getId()), tags);
            boolean ageConstraintMatched = matchAgeConstraint(service, userAge, tags);
            traces.put(service.getId(), new CandidateFilterTrace(primaryAudienceRelevant, ageConstraintMatched));
        }
        return Map.copyOf(traces);
    }

    private boolean isPrimaryAudienceRelevant(WelfareService service,
                                              RecommendationCandidateProjection projection,
                                              List<ServiceTag> tags) {
        if (projection != null) {
            return projection.youthRelevant();
        }
        if (!service.isSearchYouthRelevant()) {
            return false;
        }
        return recommendationYouthRelevanceSupport.isYouthRelevant(service, tags);
    }

    private boolean matchAgeConstraint(WelfareService service, int userAge, List<ServiceTag> tags) {
        // 구조화 필드가 있으면 DB 단계에서 이미 필터링됨
        if (service.getMinAge() != null || service.getMaxAge() != null) return true;
        if (tags == null || tags.isEmpty()) return true;

        OptionalInt min = tags.stream()
                .filter(tag -> tag.getTagType() == ServiceTag.TagType.KEYWORD)
                .map(ServiceTag::getTagValue)
                .filter(v -> v.startsWith("COND_AGE_MIN_"))
                .map(v -> v.substring("COND_AGE_MIN_".length()))
                .mapToInt(this::safeInt)
                .filter(v -> v > 0)
                .max();

        OptionalInt max = tags.stream()
                .filter(tag -> tag.getTagType() == ServiceTag.TagType.KEYWORD)
                .map(ServiceTag::getTagValue)
                .filter(v -> v.startsWith("COND_AGE_MAX_"))
                .map(v -> v.substring("COND_AGE_MAX_".length()))
                .mapToInt(this::safeInt)
                .filter(v -> v > 0)
                .min();

        if (min.isPresent() && userAge < min.getAsInt()) return false;
        if (max.isPresent() && userAge > max.getAsInt()) return false;
        return true;
    }

    private int safeInt(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (Exception ignored) {
            return -1;
        }
    }

    private String normalizeRegionCode(String regionCode) {
        if (!StringUtils.hasText(regionCode)) {
            return null;
        }
        return regionCode.trim();
    }

    /**
     * 기본 후보 K + 최신 정책 M을 중복 없이 합친다.
     * 순서는 기본 후보 우선, 이후 최신 후보를 뒤에 보강한다.
     */
    private List<WelfareService> mergeBaseAndLatest(List<WelfareService> base, List<WelfareService> latest) {
        LinkedHashMap<Long, WelfareService> merged = new LinkedHashMap<>();
        base.forEach(service -> merged.put(service.getId(), service));
        latest.forEach(service -> merged.putIfAbsent(service.getId(), service));
        return List.copyOf(merged.values());
    }

    /**
     * priority가 비어 있는 사용자에게는 같은 source가 상위 구간을 독점하지 않도록
     * source별 원래 순서를 유지한 채 round-robin으로 후보를 섞는다.
     */
    private List<WelfareService> rebalanceNoPriorityCandidates(List<WelfareService> candidates) {
        if (candidates == null || candidates.size() < 4) {
            return candidates;
        }

        LinkedHashMap<WelfareService.SourceType, List<WelfareService>> bySource = new LinkedHashMap<>();
        for (WelfareService candidate : candidates) {
            WelfareService.SourceType sourceType = candidate.getSourceType();
            bySource.computeIfAbsent(sourceType, key -> new ArrayList<>()).add(candidate);
        }
        if (bySource.size() < 2) {
            return candidates;
        }

        ArrayList<WelfareService> balanced = new ArrayList<>(candidates.size());
        int offset = 0;
        boolean appended;
        do {
            appended = false;
            for (List<WelfareService> sourceCandidates : bySource.values()) {
                if (offset < sourceCandidates.size()) {
                    balanced.add(sourceCandidates.get(offset));
                    appended = true;
                }
            }
            offset++;
        } while (appended);
        return List.copyOf(balanced);
    }

    public record RecommendationRetrievalTrace(
            List<WelfareService> rawBaseCandidates,
            List<WelfareService> rawLatestCandidates,
            List<WelfareService> filteredBaseCandidates,
            List<WelfareService> filteredLatestCandidates,
            List<WelfareService> mergedCandidates,
            Map<Long, RecommendationCandidateProjection> allProjections,
            Map<Long, CandidateFilterTrace> filterTraces
    ) {
        public RecommendationRetrievalTrace {
            rawBaseCandidates = rawBaseCandidates == null ? List.of() : List.copyOf(rawBaseCandidates);
            rawLatestCandidates = rawLatestCandidates == null ? List.of() : List.copyOf(rawLatestCandidates);
            filteredBaseCandidates = filteredBaseCandidates == null ? List.of() : List.copyOf(filteredBaseCandidates);
            filteredLatestCandidates = filteredLatestCandidates == null ? List.of() : List.copyOf(filteredLatestCandidates);
            mergedCandidates = mergedCandidates == null ? List.of() : List.copyOf(mergedCandidates);
            allProjections = allProjections == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(allProjections));
            filterTraces = filterTraces == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(filterTraces));
        }
    }

    public record CandidateFilterTrace(
            boolean primaryAudienceRelevant,
            boolean ageConstraintMatched
    ) {
        static final CandidateFilterTrace PASS_ALL = new CandidateFilterTrace(true, true);

        public boolean passesAll() {
            return primaryAudienceRelevant && ageConstraintMatched;
        }
    }
}
