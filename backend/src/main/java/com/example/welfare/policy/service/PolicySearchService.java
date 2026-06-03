package com.example.welfare.policy.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.global.util.SearchKeywordSupport;
import com.example.welfare.policy.dto.PolicySearchResponse;
import com.example.welfare.policy.dto.PolicySummaryResponse;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.PolicySearchReadCondition;
import com.example.welfare.policy.repository.WelfareServiceReadRepository;
import com.example.welfare.policy.support.Gov24BenefitTypeSupport;
import com.example.welfare.policy.support.Gov24ServiceFieldSupport;
import com.example.welfare.policy.support.Gov24UserTypeSupport;
import com.example.welfare.policy.support.WelfareSourceTypeSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class PolicySearchService {

    private static final int DEFAULT_SEARCH_LIMIT = 20;
    private static final int MAX_SEARCH_LIMIT = 100;
    private static final int MAX_KEYWORD_LENGTH = 100;
    private static final int MAX_KEYWORD_TOKENS = 10;
    private static final long SEARCH_WARN_DURATION_MS = 500L;
    private static final long PUBLIC_SEARCH_CACHE_TTL_MILLIS = 30_000L;
    private static final int MAX_PUBLIC_SEARCH_CACHE_ENTRIES = 256;

    private final WelfareServiceReadRepository welfareServiceReadRepository;
    private final PolicyPresentationReadService policyPresentationReadService;
    private final Clock clock;
    private final Map<SearchCacheKey, CachedSearchResponse> publicSearchCache = new ConcurrentHashMap<>();

    @Autowired
    public PolicySearchService(WelfareServiceReadRepository welfareServiceReadRepository,
                               PolicyPresentationReadService policyPresentationReadService) {
        this(welfareServiceReadRepository, policyPresentationReadService, Clock.systemUTC());
    }

    PolicySearchService(WelfareServiceReadRepository welfareServiceReadRepository,
                        PolicyPresentationReadService policyPresentationReadService,
                        Clock clock) {
        this.welfareServiceReadRepository = welfareServiceReadRepository;
        this.policyPresentationReadService = policyPresentationReadService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PolicySearchResponse search(Long userId, String keyword, int page) {
        return search(userId, keyword, null, null, null, null, null, null, null, null, null, null, null, null, null, page, DEFAULT_SEARCH_LIMIT);
    }

    // 소득분위(1~9) → 연소득 상한 (만원) — PolicyListService와 동일 기준
    private static final int[] INCOME_THRESHOLDS = {
        0, 2228, 3714, 5570, 7428, 9285, 11142, 13000, 15000, 20000
    };

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
                                       Integer incomeLevel,
                                       String targetGroup,
                                       String gov24ServiceField,
                                       String gov24UserType,
                                       String gov24BenefitType,
                                       int page,
                                       int size) {
        String normalizedKeyword = normalizeKeyword(keyword);
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
        String normalizedGov24ServiceField = normalizeGov24ServiceField(gov24ServiceField);
        String normalizedGov24UserType = normalizeGov24UserType(gov24UserType);
        String normalizedGov24BenefitType = normalizeGov24BenefitType(gov24BenefitType);
        boolean hasRegionFilter = normalizedSido != null;

        Integer incomeMaxWon = resolveIncomeMaxWon(incomeLevel);
        String normalizedTargetGroup = normalizeNullable(targetGroup);
        long startedAt = System.nanoTime();

        SearchCacheKey cacheKey = null;
        if (userId == null) {
            cacheKey = new SearchCacheKey(
                    normalizedKeyword,
                    normalizedStatus,
                    normalizedStatusFilter,
                    normalizedCategory,
                    normalizedSourceType,
                    onlineApplyFlag,
                    normalizedSido,
                    normalizedSgg,
                    normalizedSort,
                    incomeMaxWon,
                    normalizedTargetGroup,
                    normalizedGov24ServiceField,
                    normalizedGov24UserType,
                    normalizedGov24BenefitType,
                    pageNumber,
                    limit
            );
            PolicySearchResponse cached = getCachedPublicSearch(cacheKey);
            if (cached != null) {
                logSearchObservation(
                        cached,
                        normalizedKeyword,
                        normalizedStatus,
                        normalizedStatusFilter,
                        normalizedCategory,
                        normalizedSourceType,
                        normalizedSort,
                        System.nanoTime() - startedAt
                );
                return cached;
            }
        }

        // 지역 분기 및 sido/sgg → regionCode 변환은 WelfareServiceReadRepositoryImpl에서 처리
        PolicySearchReadCondition condition = new PolicySearchReadCondition(
                normalizedKeyword,
                normalizedStatus,
                normalizedStatusFilter,
                normalizedCategory,
                normalizedSourceType,
                onlineApplyFlag,
                normalizedSido,
                normalizedSgg,
                normalizedSort,
                incomeMaxWon,
                normalizedTargetGroup,
                normalizedGov24ServiceField,
                normalizedGov24UserType,
                normalizedGov24BenefitType
        );
        int repositoryPageSize = hasRegionFilter ? Math.min(limit * 3, MAX_SEARCH_LIMIT) : limit;
        Page<WelfareService> resultPage = welfareServiceReadRepository.search(
                condition,
                PageRequest.of(pageNumber, repositoryPageSize)
        );

        List<PolicySummaryResponse> content = collectVisibleSearchSummaries(
                userId,
                condition,
                resultPage,
                pageNumber,
                limit,
                repositoryPageSize
        );

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
                normalizedKeyword,
                normalizedStatus,
                normalizedStatusFilter,
                normalizedCategory,
                normalizedSourceType,
                normalizedSort,
                System.nanoTime() - startedAt
        );
        if (cacheKey != null) {
            cachePublicSearch(cacheKey, response);
        }
        return response;
    }

    private List<PolicySummaryResponse> collectVisibleSearchSummaries(Long userId,
                                                                      PolicySearchReadCondition condition,
                                                                      Page<WelfareService> firstPage,
                                                                      int pageNumber,
                                                                      int limit,
                                                                      int repositoryPageSize) {
        List<PolicySummaryResponse> visible = new ArrayList<>(filterRegionCompatibleSummaries(
                policyPresentationReadService.buildSummaryPage(userId, firstPage).getContent(),
                condition.sido(),
                condition.sgg()
        ));
        if (condition.sido() == null) {
            return visible;
        }
        int nextPage = pageNumber + 1;
        int extraFetchCount = 0;
        Page<WelfareService> currentPage = firstPage;
        while (visible.size() < limit && currentPage.hasNext() && extraFetchCount < 2) {
            currentPage = welfareServiceReadRepository.search(
                    condition,
                    PageRequest.of(nextPage, repositoryPageSize)
            );
            visible.addAll(filterRegionCompatibleSummaries(
                    policyPresentationReadService.buildSummaryPage(userId, currentPage).getContent(),
                    condition.sido(),
                    condition.sgg()
            ));
            nextPage += 1;
            extraFetchCount += 1;
        }
        return visible.size() <= limit ? visible : visible.subList(0, limit);
    }

    private List<PolicySummaryResponse> filterRegionCompatibleSummaries(List<PolicySummaryResponse> summaries,
                                                                        String sido,
                                                                        String sgg) {
        if (sido == null) {
            return summaries;
        }
        return summaries.stream()
                .filter(summary -> matchesRequestedRegion(summary, sido, sgg))
                .toList();
    }

    private boolean matchesRequestedRegion(PolicySummaryResponse summary, String sido, String sgg) {
        String regionLabel = normalizeNullable(summary.getRegionLabel());
        if (regionLabel == null) {
            return true;
        }
        if (!regionLabel.startsWith(sido)) {
            return false;
        }
        if (sgg == null) {
            return true;
        }
        return regionLabel.equals(sido + " " + sgg) || regionLabel.startsWith(sido + " " + sgg + " ");
    }

    private PolicySearchResponse getCachedPublicSearch(SearchCacheKey cacheKey) {
        CachedSearchResponse cached = publicSearchCache.get(cacheKey);
        if (cached == null) {
            return null;
        }
        long nowMillis = clock.millis();
        if (nowMillis - cached.cachedAtMillis() >= PUBLIC_SEARCH_CACHE_TTL_MILLIS) {
            publicSearchCache.remove(cacheKey, cached);
            return null;
        }
        return cached.response();
    }

    private void cachePublicSearch(SearchCacheKey cacheKey, PolicySearchResponse response) {
        if (publicSearchCache.size() >= MAX_PUBLIC_SEARCH_CACHE_ENTRIES) {
            publicSearchCache.clear();
        }
        publicSearchCache.put(cacheKey, new CachedSearchResponse(clock.millis(), response));
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
        int keywordTokenCount = SearchKeywordSupport.extractTokens(keyword).size();
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
    private String normalizeKeyword(String keyword) {
        if (keyword == null) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        String trimmed = keyword.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_KEYWORD_LENGTH) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        String normalized = SearchKeywordSupport.normalizeText(trimmed);
        int tokenCount = SearchKeywordSupport.extractTokens(trimmed).size();
        if (normalized.isBlank()) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        if (tokenCount > MAX_KEYWORD_TOKENS) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        return normalized;
    }

    private int normalizeSize(int size) {
        if (size <= 0) return DEFAULT_SEARCH_LIMIT;
        return Math.min(size, MAX_SEARCH_LIMIT);
    }

    private String normalizeSort(String sort) {
        if (sort == null || sort.isBlank()) return "RELEVANCE";
        String upper = sort.trim().toUpperCase();
        return switch (upper) {
            case "RELEVANCE", "VIEWS", "LATEST", "DEADLINE" -> upper;
            // NAME: UI 정렬 옵션에서는 제거됐지만 코드는 유지 (API 호환성)
            case "NAME" -> upper;
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

    private String normalizeGov24ServiceField(String gov24ServiceField) {
        String normalized = Gov24ServiceFieldSupport.normalizeManagedLabel(gov24ServiceField);
        if (gov24ServiceField == null || gov24ServiceField.isBlank()) {
            return null;
        }
        if (normalized == null) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        return normalized;
    }

    private String normalizeGov24UserType(String gov24UserType) {
        String normalized = Gov24UserTypeSupport.normalizeManagedToken(gov24UserType);
        if (gov24UserType == null || gov24UserType.isBlank()) {
            return null;
        }
        if (normalized == null) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        return normalized;
    }

    private String normalizeGov24BenefitType(String gov24BenefitType) {
        String normalized = Gov24BenefitTypeSupport.normalizeManagedToken(gov24BenefitType);
        if (gov24BenefitType == null || gov24BenefitType.isBlank()) {
            return null;
        }
        if (normalized == null) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        return normalized;
    }

    private Integer resolveIncomeMaxWon(Integer incomeLevel) {
        if (incomeLevel == null || incomeLevel <= 1) return null;
        int prevLevel = incomeLevel - 2;
        if (prevLevel <= 0) return null;
        return INCOME_THRESHOLDS[prevLevel];
    }

    private record SearchCacheKey(
            String keyword,
            String status,
            String statusFilter,
            String category,
            String sourceType,
            Integer onlineApply,
            String sido,
            String sgg,
            String sort,
            Integer incomeMaxWon,
            String targetGroup,
            String gov24ServiceField,
            String gov24UserType,
            String gov24BenefitType,
            int page,
            int size
    ) {
    }

    private record CachedSearchResponse(long cachedAtMillis, PolicySearchResponse response) {
    }
}
