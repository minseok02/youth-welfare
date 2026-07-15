package com.example.welfare.policy.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.global.util.RegionCodeUtil;
import com.example.welfare.global.util.RedisKeyHash;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
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
    private static final long SEARCH_TIMING_OBSERVATION_THRESHOLD_MS = 80L;
    private static final long PUBLIC_SEARCH_CACHE_TTL_MILLIS = 30_000L;
    private static final int MAX_PUBLIC_SEARCH_CACHE_ENTRIES = 256;
    private static final String PUBLIC_SEARCH_CACHE_PREFIX = "policy:search:public:v1:";

    private final WelfareServiceReadRepository welfareServiceReadRepository;
    private final PolicyPresentationReadService policyPresentationReadService;
    private final Clock clock;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration publicSearchCacheTtl;
    private final Map<SearchCacheKey, CachedSearchResponse> publicSearchCache = new ConcurrentHashMap<>();

    @Autowired
    public PolicySearchService(WelfareServiceReadRepository welfareServiceReadRepository,
                               PolicyPresentationReadService policyPresentationReadService,
                               RedisTemplate<String, String> redisTemplate,
                               ObjectMapper objectMapper,
                               @Value("${policy.cache.public-search.ttl-seconds:30}") long publicSearchCacheTtlSeconds) {
        this(
                welfareServiceReadRepository,
                policyPresentationReadService,
                Clock.systemUTC(),
                redisTemplate,
                objectMapper,
                Duration.ofSeconds(publicSearchCacheTtlSeconds)
        );
    }

    PolicySearchService(WelfareServiceReadRepository welfareServiceReadRepository,
                        PolicyPresentationReadService policyPresentationReadService,
                        Clock clock) {
        this(welfareServiceReadRepository, policyPresentationReadService, clock, null, null, Duration.ofMillis(PUBLIC_SEARCH_CACHE_TTL_MILLIS));
    }

    PolicySearchService(WelfareServiceReadRepository welfareServiceReadRepository,
                        PolicyPresentationReadService policyPresentationReadService,
                        Clock clock,
                        RedisTemplate<String, String> redisTemplate,
                        ObjectMapper objectMapper,
                        Duration publicSearchCacheTtl) {
        this.welfareServiceReadRepository = welfareServiceReadRepository;
        this.policyPresentationReadService = policyPresentationReadService;
        this.clock = clock;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.publicSearchCacheTtl = publicSearchCacheTtl.isNegative() || publicSearchCacheTtl.isZero()
                ? Duration.ofMillis(PUBLIC_SEARCH_CACHE_TTL_MILLIS)
                : publicSearchCacheTtl;
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
        long totalStartedNanos = System.nanoTime();
        long normalizeStartedNanos = System.nanoTime();
        String normalizedKeyword = normalizeKeyword(keyword);
        int limit = normalizeSize(size);
        int pageNumber = Math.max(0, page);
        String normalizedStatus = normalizeStatus(status);
        String normalizedStatusFilter = normalizeStatusFilter(statusFilter);
        String normalizedSourceType = normalizeSourceType(sourceType);
        String normalizedCategory = normalizeNullable(category);
        String normalizedSido = normalizeSidoNullable(sido);
        String normalizedSgg = normalizeNullable(sgg);
        Integer onlineApplyFlag = onlineApply == null ? null : (onlineApply ? 1 : 0);
        String normalizedSort = normalizeSort(sort);
        String normalizedGov24ServiceField = normalizeGov24ServiceField(gov24ServiceField);
        String normalizedGov24UserType = normalizeGov24UserType(gov24UserType);
        String normalizedGov24BenefitType = normalizeGov24BenefitType(gov24BenefitType);
        boolean hasRegionFilter = normalizedSido != null;

        Integer incomeMaxWon = resolveIncomeMaxWon(incomeLevel);
        String normalizedTargetGroup = normalizeNullable(targetGroup);
        long normalizeMs = elapsedMs(normalizeStartedNanos);

        SearchCacheKey cacheKey = null;
        long cacheLookupMs = 0;
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
            long cacheLookupStartedNanos = System.nanoTime();
            PublicSearchCacheLookup cacheLookup = getCachedPublicSearch(cacheKey);
            cacheLookupMs = elapsedMs(cacheLookupStartedNanos);
            if (cacheLookup.response() != null) {
                logSearchObservation(
                        cacheLookup.response(),
                        normalizedKeyword,
                        normalizedStatus,
                        normalizedStatusFilter,
                        normalizedCategory,
                        normalizedSourceType,
                        normalizedSort,
                        System.nanoTime() - totalStartedNanos
                );
                logSearchTimingIfObserved(
                        "cache_hit",
                        cacheLookup.source(),
                        elapsedMs(totalStartedNanos),
                        normalizeMs,
                        cacheLookupMs,
                        0,
                        0,
                        0,
                        0,
                        0,
                        userId,
                        normalizedKeyword,
                        normalizedStatus,
                        normalizedStatusFilter,
                        normalizedCategory,
                        normalizedSourceType,
                        normalizedSort,
                        pageNumber,
                        limit,
                        cacheLookup.response()
                );
                return cacheLookup.response();
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
        long repositoryStartedNanos = System.nanoTime();
        Page<WelfareService> resultPage = welfareServiceReadRepository.search(
                condition,
                PageRequest.of(pageNumber, repositoryPageSize)
        );
        long repositoryMs = elapsedMs(repositoryStartedNanos);

        long summaryStartedNanos = System.nanoTime();
        List<PolicySummaryResponse> content = collectVisibleSearchSummaries(
                userId,
                condition,
                resultPage,
                pageNumber,
                limit,
                repositoryPageSize
        );
        long summaryMs = elapsedMs(summaryStartedNanos);

        long responseBuildStartedNanos = System.nanoTime();
        PolicySearchResponse response = PolicySearchResponse.builder()
                .content(content)
                .totalElements(resultPage.getTotalElements())
                .totalPages(resultPage.getTotalPages())
                .pageNumber(pageNumber)
                .pageSize(limit)
                .hasNext(resultPage.hasNext())
                .build();
        long responseBuildMs = elapsedMs(responseBuildStartedNanos);

        logSearchObservation(
                response,
                normalizedKeyword,
                normalizedStatus,
                normalizedStatusFilter,
                normalizedCategory,
                normalizedSourceType,
                normalizedSort,
                System.nanoTime() - totalStartedNanos
        );
        long cacheWriteMs = 0;
        if (cacheKey != null) {
            long cacheWriteStartedNanos = System.nanoTime();
            cachePublicSearch(cacheKey, response);
            cacheWriteMs = elapsedMs(cacheWriteStartedNanos);
        }
        logSearchTimingIfObserved(
                "cache_miss",
                userId == null ? "miss" : "authenticated",
                elapsedMs(totalStartedNanos),
                normalizeMs,
                cacheLookupMs,
                repositoryMs,
                summaryMs,
                responseBuildMs,
                cacheWriteMs,
                resultPage.getNumberOfElements(),
                userId,
                normalizedKeyword,
                normalizedStatus,
                normalizedStatusFilter,
                normalizedCategory,
                normalizedSourceType,
                normalizedSort,
                pageNumber,
                limit,
                response
        );
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

    private PublicSearchCacheLookup getCachedPublicSearch(SearchCacheKey cacheKey) {
        CachedSearchResponse cached = publicSearchCache.get(cacheKey);
        if (cached == null) {
            PolicySearchResponse redisCached = getRedisCachedPublicSearch(cacheKey);
            if (redisCached != null) {
                cachePublicSearchLocal(cacheKey, redisCached);
                return new PublicSearchCacheLookup(redisCached, "redis");
            }
            return new PublicSearchCacheLookup(null, "miss");
        }
        long nowMillis = clock.millis();
        if (nowMillis - cached.cachedAtMillis() >= publicSearchCacheTtl.toMillis()) {
            publicSearchCache.remove(cacheKey, cached);
            PolicySearchResponse redisCached = getRedisCachedPublicSearch(cacheKey);
            if (redisCached != null) {
                cachePublicSearchLocal(cacheKey, redisCached);
                return new PublicSearchCacheLookup(redisCached, "redis_after_local_expired");
            }
            return new PublicSearchCacheLookup(null, "expired_miss");
        }
        return new PublicSearchCacheLookup(cached.response(), "local");
    }

    private void cachePublicSearch(SearchCacheKey cacheKey, PolicySearchResponse response) {
        cachePublicSearchLocal(cacheKey, response);
        cachePublicSearchRedis(cacheKey, response);
    }

    private void cachePublicSearchLocal(SearchCacheKey cacheKey, PolicySearchResponse response) {
        if (publicSearchCache.size() >= MAX_PUBLIC_SEARCH_CACHE_ENTRIES) {
            publicSearchCache.clear();
        }
        publicSearchCache.put(cacheKey, new CachedSearchResponse(clock.millis(), response));
    }

    private PolicySearchResponse getRedisCachedPublicSearch(SearchCacheKey cacheKey) {
        if (redisTemplate == null || objectMapper == null) {
            return null;
        }
        try {
            String cached = redisTemplate.opsForValue().get(redisCacheKey(cacheKey));
            if (cached == null || cached.isBlank()) {
                return null;
            }
            return objectMapper.readValue(cached, PolicySearchResponse.class);
        } catch (Exception e) {
            log.warn("[PolicySearchService] Redis public search cache read failed errorType={}", e.getClass().getSimpleName());
            return null;
        }
    }

    private void cachePublicSearchRedis(SearchCacheKey cacheKey, PolicySearchResponse response) {
        if (redisTemplate == null || objectMapper == null) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(
                    redisCacheKey(cacheKey),
                    objectMapper.writeValueAsString(response),
                    publicSearchCacheTtl
            );
        } catch (Exception e) {
            log.warn("[PolicySearchService] Redis public search cache write failed errorType={}", e.getClass().getSimpleName());
        }
    }

    private String redisCacheKey(SearchCacheKey cacheKey) {
        return PUBLIC_SEARCH_CACHE_PREFIX + RedisKeyHash.sha256Hex(cacheKey.cacheMaterial());
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

    private void logSearchTimingIfObserved(String cacheState,
                                           String cacheSource,
                                           long totalMs,
                                           long normalizeMs,
                                           long cacheLookupMs,
                                           long repositoryMs,
                                           long summaryMs,
                                           long responseBuildMs,
                                           long cacheWriteMs,
                                           int repositoryResultCount,
                                           Long userId,
                                           String keyword,
                                           String status,
                                           String statusFilter,
                                           String category,
                                           String sourceType,
                                           String sort,
                                           int page,
                                           int size,
                                           PolicySearchResponse response) {
        if (totalMs < SEARCH_TIMING_OBSERVATION_THRESHOLD_MS) {
            return;
        }
        log.info("[PolicySearchServiceTiming] totalMs={} cacheState={} cacheSource={} normalizeMs={} cacheLookupMs={} repositoryMs={} summaryMs={} responseBuildMs={} cacheWriteMs={} authenticated={} totalElements={} resultCount={} repositoryResultCount={} page={} size={} hasNext={} statusFilter={} status={} categoryPresent={} sourceType={} sort={} keywordLength={} keywordTokens={}",
                totalMs,
                cacheState,
                cacheSource,
                normalizeMs,
                cacheLookupMs,
                repositoryMs,
                summaryMs,
                responseBuildMs,
                cacheWriteMs,
                userId != null,
                response.getTotalElements(),
                response.getContent() == null ? 0 : response.getContent().size(),
                repositoryResultCount,
                page,
                size,
                response.isHasNext(),
                statusFilter,
                status,
                category != null,
                sourceType,
                sort,
                keyword == null ? 0 : keyword.length(),
                SearchKeywordSupport.extractTokens(keyword).size());
    }

    private long elapsedMs(long startedNanos) {
        return Math.max(0, (System.nanoTime() - startedNanos) / 1_000_000);
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
            case "ACTIVE_ONLY" -> "ACTIVE_ONLY";
            default -> throw new CustomException(ErrorCode.INVALID_INPUT);
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

    private String normalizeSidoNullable(String text) {
        String normalized = normalizeNullable(text);
        return normalized == null ? null : RegionCodeUtil.fullSidoName(normalized);
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
        if (incomeLevel > 10) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
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
        private String cacheMaterial() {
            return String.join("|",
                    value(keyword),
                    value(status),
                    value(statusFilter),
                    value(category),
                    value(sourceType),
                    value(onlineApply),
                    value(sido),
                    value(sgg),
                    value(sort),
                    value(incomeMaxWon),
                    value(targetGroup),
                    value(gov24ServiceField),
                    value(gov24UserType),
                    value(gov24BenefitType),
                    Integer.toString(page),
                    Integer.toString(size)
            );
        }

        private static String value(Object value) {
            return value == null ? "" : value.toString();
        }
    }

    private record CachedSearchResponse(long cachedAtMillis, PolicySearchResponse response) {
    }

    private record PublicSearchCacheLookup(PolicySearchResponse response, String source) {
    }
}
