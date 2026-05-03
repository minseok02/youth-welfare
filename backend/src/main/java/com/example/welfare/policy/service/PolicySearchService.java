package com.example.welfare.policy.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.policy.dto.PolicySearchResponse;
import com.example.welfare.policy.dto.PolicySummaryResponse;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.PolicySearchReadCondition;
import com.example.welfare.policy.repository.WelfareServiceReadRepository;
import com.example.welfare.policy.support.WelfareSourceTypeSupport;
import com.example.welfare.recommend.dto.RecommendationCandidateProjection;
import com.example.welfare.recommend.facade.RecommendationReadFacade;
import com.example.welfare.recommend.repository.CanonicalRecommendationReadModelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PolicySearchService {

    private static final int DEFAULT_SEARCH_LIMIT = 20;
    private static final int MAX_SEARCH_LIMIT = 100;
    private static final long SEARCH_WARN_DURATION_MS = 500L;

    private final WelfareServiceReadRepository welfareServiceReadRepository;
    private final RecommendationReadFacade recommendationReadFacade;
    private final CanonicalRecommendationReadModelRepository canonicalRecommendationReadModelRepository;

    @Transactional(readOnly = true)
    public PolicySearchResponse search(Long userId, String keyword, int page) {
        return search(userId, keyword, null, null, null, null, null, null, null, null, page, DEFAULT_SEARCH_LIMIT);
    }

    @Transactional(readOnly = true)
    public PolicySearchResponse search(Long userId,
                                       String keyword,
                                       String status,
                                       String statusFilter,
                                       String category,
                                       String sourceType,
                                       Boolean onlineApply,
                                       String sido,
                                       String sgg,
                                       String sort,
                                       int page,
                                       int size) {
        long startedAt = System.nanoTime();

        // MySQL FULLTEXT 검색 (ngram 파서)
        // keyword는 Controller에서 trim 처리 후 전달됨
        String ftKeyword = buildFulltextKeyword(keyword);
        int limit = normalizeSize(size);
        int pageNumber = Math.max(0, page);
        String normalizedStatus = normalizeStatus(status);
        String normalizedStatusFilter = normalizeStatusFilter(statusFilter);
        String normalizedSourceType = normalizeSourceType(sourceType);
        String normalizedCategory = normalizeNullable(category);
        String normalizedSido = normalizeNullable(sido);
        String normalizedSgg = normalizeNullable(sgg);
        Integer onlineApplyFlag = onlineApply == null ? null : (onlineApply ? 1 : 0);
        String normalizedSort = normalizeSort(sort);

        // 지역 분기 및 sido/sgg → regionCode 변환은 WelfareServiceReadRepositoryImpl에서 처리
        Page<WelfareService> resultPage = welfareServiceReadRepository.search(
                new PolicySearchReadCondition(
                        ftKeyword,
                        normalizedStatus,
                        normalizedStatusFilter,
                        normalizedCategory,
                        normalizedSourceType,
                        onlineApplyFlag,
                        normalizedSido,
                        normalizedSgg,
                        normalizedSort
                ),
                PageRequest.of(pageNumber, limit)
        );

        Set<Long> bookmarkedServiceIds = recommendationReadFacade.findBookmarkedServiceIds(userId, resultPage.getContent());
        java.util.Map<Long, RecommendationCandidateProjection> projections = loadProjections(resultPage.getContent());
        List<PolicySummaryResponse> content = resultPage.getContent().stream()
                .map(service -> PolicySummaryResponse.from(
                        service,
                        bookmarkedServiceIds.contains(service.getId()),
                        projections.get(service.getId())
                ))
                .collect(Collectors.toList());

        PolicySearchResponse response = PolicySearchResponse.builder()
                .content(content)
                .totalElements(resultPage.getTotalElements())
                .totalPages(resultPage.getTotalPages())
                .pageNumber(pageNumber)
                .pageSize(limit)
                .hasNext(resultPage.hasNext())
                .build();

        logSearchObservation(
                response,
                keyword,
                normalizedStatus,
                normalizedStatusFilter,
                normalizedCategory,
                normalizedSourceType,
                normalizedSort,
                System.nanoTime() - startedAt
        );
        return response;
    }

    private void logSearchObservation(PolicySearchResponse response,
                                      String keyword,
                                      String status,
                                      String statusFilter,
                                      String category,
                                      String sourceType,
                                      String sort,
                                      long elapsedNanos) {
        long elapsedMs = elapsedNanos / 1_000_000L;
        int keywordTokenCount = keyword.trim().isEmpty() ? 0 : keyword.trim().split("\\s+").length;
        boolean warn = elapsedMs >= SEARCH_WARN_DURATION_MS;

        if (warn) {
            log.warn("[PolicySearchService] 검색 응답 경고 elapsedMs={} total={} page={} size={} hasNext={} statusFilter={} status={} category={} sourceType={} sort={} keywordTokens={}",
                    elapsedMs,
                    response.getTotalElements(),
                    response.getPageNumber(),
                    response.getPageSize(),
                    response.isHasNext(),
                    statusFilter,
                    status,
                    category,
                    sourceType,
                    sort,
                    keywordTokenCount);
            return;
        }

        if (elapsedMs >= 150L || response.getTotalElements() >= 500) {
            log.info("[PolicySearchService] 검색 응답 관측 elapsedMs={} total={} page={} size={} hasNext={} statusFilter={} status={} category={} sourceType={} sort={} keywordTokens={}",
                    elapsedMs,
                    response.getTotalElements(),
                    response.getPageNumber(),
                    response.getPageSize(),
                    response.isHasNext(),
                    statusFilter,
                    status,
                    category,
                    sourceType,
                    sort,
                    keywordTokenCount);
        }
    }

    private java.util.Map<Long, RecommendationCandidateProjection> loadProjections(List<WelfareService> services) {
        if (services == null || services.isEmpty()) {
            return java.util.Map.of();
        }
        return canonicalRecommendationReadModelRepository.findByServiceIds(
                services.stream().map(WelfareService::getId).toList()
        );
    }

    // Boolean Mode 검색어 구성: 공백 분리 후 각 단어에 + 접두사
    private String buildFulltextKeyword(String keyword) {
        String[] words = keyword.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                sb.append("+").append(word).append(" ");
            }
        }
        return sb.toString().trim();
    }

    private int normalizeSize(int size) {
        if (size <= 0) return DEFAULT_SEARCH_LIMIT;
        return Math.min(size, MAX_SEARCH_LIMIT);
    }

    private String normalizeSort(String sort) {
        if (sort == null || sort.isBlank()) return "RELEVANCE";
        String upper = sort.trim().toUpperCase();
        return switch (upper) {
            case "RELEVANCE", "VIEWS", "LATEST", "NAME" -> upper;
            default -> throw new CustomException(ErrorCode.INVALID_INPUT);
        };
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) return null;
        String upper = status.trim().toUpperCase();
        return switch (upper) {
            case "ACTIVE", "UPCOMING", "CLOSED" -> upper;
            default -> throw new CustomException(ErrorCode.INVALID_INPUT);
        };
    }

    // ACTIVE_ONLY(기본): 신청가능·예정, 마감일 미도래
    // EXPIRED_ONLY: CLOSED 또는 applyEndDate 지남 (온통청년처럼 DB status=ACTIVE이지만 마감된 경우 포함)
    // ALL: 모든 상태
    private String normalizeStatusFilter(String statusFilter) {
        if (statusFilter == null || statusFilter.isBlank()) return "ACTIVE_ONLY";
        return switch (statusFilter.trim().toUpperCase()) {
            case "ALL" -> "ALL";
            case "EXPIRED_ONLY" -> "EXPIRED_ONLY";
            default -> "ACTIVE_ONLY";
        };
    }

    private String normalizeSourceType(String sourceType) {
        try {
            return WelfareSourceTypeSupport.normalizeNullable(sourceType);
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
    }

    private String normalizeNullable(String text) {
        if (text == null) return null;
        String trimmed = text.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
