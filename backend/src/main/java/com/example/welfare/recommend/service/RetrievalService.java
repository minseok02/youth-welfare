package com.example.welfare.recommend.service;

import com.example.welfare.policy.entity.ServiceTag;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.recommend.repository.RecommendationCandidateReadCondition;
import com.example.welfare.recommend.repository.RecommendationCandidateReadRepository;
import com.example.welfare.recommend.dto.RecommendationCandidateProjection;
import com.example.welfare.recommend.dto.RetrievedRecommendationCandidates;
import com.example.welfare.recommend.dto.RecommendationUserSnapshot;
import com.example.welfare.recommend.facade.RecommendationReadFacade;
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
    private final RecommendationReadFacade recommendationReadFacade;

    @Transactional(readOnly = true)
    public RetrievedRecommendationCandidates retrieve(String clusterId, RecommendationUserSnapshot user) {
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
        List<WelfareService> rawCandidates = recommendationCandidateReadRepository.findBaseCandidates(condition);
        List<WelfareService> latestCandidates = recommendationCandidateReadRepository.findLatestCandidates(condition);

        Map<Long, RecommendationCandidateProjection> projections = loadProjections(rawCandidates, latestCandidates);

        List<WelfareService> filteredBase = applyRecommendationFilters(rawCandidates, projections, age).stream()
                .limit(K)
                .toList();

        List<WelfareService> filteredLatest = applyRecommendationFilters(latestCandidates, projections, age).stream()
                .limit(M)
                .toList();

        List<WelfareService> candidates = mergeBaseAndLatest(filteredBase, filteredLatest).stream()
                .limit(K + M)
                .collect(Collectors.toList());

        Map<Long, RecommendationCandidateProjection> filteredProjections = candidates.stream()
                .map(WelfareService::getId)
                .filter(projections::containsKey)
                .collect(Collectors.toMap(
                        id -> id,
                        projections::get,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        return new RetrievedRecommendationCandidates(
                candidates,
                filteredProjections
        );
    }

    /**
     * 구조화된 나이 필드(min_age/max_age)가 비어있는 정책에 한해
     * KEYWORD의 COND_AGE_MIN_*, COND_AGE_MAX_* 토큰으로 보조 필터를 적용한다.
     */
    private List<WelfareService> applyRecommendationFilters(List<WelfareService> candidates,
                                                            Map<Long, RecommendationCandidateProjection> projections,
                                                            int userAge) {
        if (candidates.isEmpty()) return candidates;

        List<Long> ids = candidates.stream().map(WelfareService::getId).toList();
        Map<Long, List<ServiceTag>> tagsByServiceId = recommendationCandidateReadRepository.findTagsByServiceIds(ids);

        return candidates.stream()
                .filter(service -> isPrimaryAudienceRelevant(
                        service,
                        projections.get(service.getId()),
                        tagsByServiceId.getOrDefault(service.getId(), Collections.emptyList())
                ))
                .filter(service -> matchAgeConstraint(
                        service,
                        userAge,
                        tagsByServiceId.getOrDefault(service.getId(), Collections.emptyList())
                ))
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
        return recommendationReadFacade.findCandidateProjectionsByServiceIds(List.copyOf(serviceIds));
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
}
