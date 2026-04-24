package com.example.welfare.policy.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.policy.dto.PolicySearchResponse;
import com.example.welfare.policy.dto.PolicySummaryResponse;
import com.example.welfare.policy.entity.ServiceTag;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.ServiceTagRepository;
import com.example.welfare.policy.repository.WelfareServiceRepository;
import com.example.welfare.recommend.repository.UserRecommendationRepository;
import com.example.welfare.recommend.service.YouthPolicyFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PolicySearchService {

    private static final int DEFAULT_SEARCH_LIMIT = 20;
    private static final int MAX_SEARCH_LIMIT = 100;
    private static final int SEARCH_SCAN_BATCH_SIZE = 200;
    private static final long SEARCH_WARN_DURATION_MS = 300L;
    private static final int SEARCH_WARN_BATCH_COUNT = 3;
    private static final int SEARCH_WARN_RAW_SCAN_COUNT = 400;

    private final WelfareServiceRepository welfareServiceRepository;
    private final ServiceTagRepository serviceTagRepository;
    private final UserRecommendationRepository userRecommendationRepository;
    private final YouthPolicyFilter youthPolicyFilter;

    @Transactional(readOnly = true)
    public PolicySearchResponse search(Long userId, String keyword, int page) {
        return search(userId, keyword, null, null, null, null, null, null, null, null, page, DEFAULT_SEARCH_LIMIT);
    }

    @Transactional(readOnly = true)
    public PolicySearchResponse search(Long userId,
                                       String keyword,
                                       String status,
                                       Boolean includeClosed,
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
        Integer normalizedIncludeClosed = normalizeIncludeClosed(includeClosed);
        String normalizedSourceType = normalizeSourceType(sourceType);
        String normalizedCategory = normalizeNullable(category);
        String normalizedSido = normalizeNullable(sido);
        String normalizedSgg = normalizeNullable(sgg);
        Integer onlineApplyFlag = onlineApply == null ? null : (onlineApply ? 1 : 0);
        String normalizedSort = normalizeSort(sort);

        int startIndex = pageNumber * limit;
        int endExclusive = startIndex + limit;
        int offset = 0;
        long totalFilteredCount = 0L;
        long totalRawScannedCount = 0L;
        int scanBatchCount = 0;
        List<WelfareService> pageServices = new ArrayList<>();

        while (true) {
            scanBatchCount++;
            List<WelfareService> results = welfareServiceRepository.searchByKeywordWithFilters(
                    ftKeyword,
                    normalizedStatus,
                    normalizedIncludeClosed,
                    normalizedCategory,
                    normalizedSourceType,
                    onlineApplyFlag,
                    normalizedSido,
                    normalizedSgg,
                    normalizedSort,
                    SEARCH_SCAN_BATCH_SIZE,
                    offset);

            if (results.isEmpty()) {
                break;
            }

            totalRawScannedCount += results.size();

            List<WelfareService> filteredResults = filterYouthRelevant(results);
            for (WelfareService service : filteredResults) {
                if (totalFilteredCount >= startIndex && totalFilteredCount < endExclusive) {
                    pageServices.add(service);
                }
                totalFilteredCount++;
            }

            if (results.size() < SEARCH_SCAN_BATCH_SIZE) {
                break;
            }
            offset += SEARCH_SCAN_BATCH_SIZE;
        }

        Set<Long> bookmarkedServiceIds = getBookmarkedServiceIds(userId, pageServices);
        List<PolicySummaryResponse> content = pageServices.stream()
                .map(service -> PolicySummaryResponse.from(
                        service,
                        bookmarkedServiceIds.contains(service.getId())
                ))
                .collect(Collectors.toList());

        int totalPages = totalFilteredCount == 0
                ? 0
                : (int) Math.ceil((double) totalFilteredCount / limit);

        PolicySearchResponse response = PolicySearchResponse.builder()
                .content(content)
                .totalElements(totalFilteredCount)
                .totalPages(totalPages)
                .pageNumber(pageNumber)
                .pageSize(limit)
                .hasNext(pageNumber + 1 < totalPages)
                .build();

        logSearchObservation(
                response,
                keyword,
                normalizedStatus,
                normalizedIncludeClosed,
                normalizedCategory,
                normalizedSourceType,
                normalizedSort,
                totalRawScannedCount,
                totalFilteredCount,
                scanBatchCount,
                System.nanoTime() - startedAt
        );
        return response;
    }

    private void logSearchObservation(PolicySearchResponse response,
                                      String keyword,
                                      String status,
                                      Integer includeClosed,
                                      String category,
                                      String sourceType,
                                      String sort,
                                      long totalRawScannedCount,
                                      long totalFilteredCount,
                                      int scanBatchCount,
                                      long elapsedNanos) {
        long elapsedMs = elapsedNanos / 1_000_000L;
        int keywordTokenCount = keyword.trim().isEmpty() ? 0 : keyword.trim().split("\\s+").length;
        boolean warn = elapsedMs >= SEARCH_WARN_DURATION_MS
                || scanBatchCount >= SEARCH_WARN_BATCH_COUNT
                || totalRawScannedCount >= SEARCH_WARN_RAW_SCAN_COUNT;

        if (warn) {
            log.warn("[PolicySearchService] 검색 비용 경고 elapsedMs={} rawScanned={} filtered={} batches={} page={} size={} hasNext={} includeClosed={} status={} category={} sourceType={} sort={} keywordTokens={}",
                    elapsedMs,
                    totalRawScannedCount,
                    totalFilteredCount,
                    scanBatchCount,
                    response.getPageNumber(),
                    response.getPageSize(),
                    response.isHasNext(),
                    includeClosed == 1,
                    status,
                    category,
                    sourceType,
                    sort,
                    keywordTokenCount);
            return;
        }

        if (scanBatchCount > 1 || totalRawScannedCount > response.getPageSize()) {
            log.info("[PolicySearchService] 검색 스캔 관측 elapsedMs={} rawScanned={} filtered={} batches={} page={} size={} hasNext={} includeClosed={} status={} category={} sourceType={} sort={} keywordTokens={}",
                    elapsedMs,
                    totalRawScannedCount,
                    totalFilteredCount,
                    scanBatchCount,
                    response.getPageNumber(),
                    response.getPageSize(),
                    response.isHasNext(),
                    includeClosed == 1,
                    status,
                    category,
                    sourceType,
                    sort,
                    keywordTokenCount);
        }
    }

    private List<WelfareService> filterYouthRelevant(List<WelfareService> results) {
        if (results.isEmpty()) {
            return results;
        }

        List<Long> serviceIds = results.stream()
                .map(WelfareService::getId)
                .toList();

        Map<Long, List<ServiceTag>> tagsByServiceId = serviceTagRepository.findByServiceIdIn(serviceIds).stream()
                .collect(Collectors.groupingBy(tag -> tag.getService().getId()));

        return results.stream()
                .filter(service -> youthPolicyFilter.isYouthRelevant(
                        service,
                        tagsByServiceId.getOrDefault(service.getId(), Collections.emptyList())
                ))
                .toList();
    }

    private Set<Long> getBookmarkedServiceIds(Long userId, List<WelfareService> services) {
        if (userId == null || services.isEmpty()) {
            return Collections.emptySet();
        }

        List<Long> serviceIds = services.stream()
                .map(WelfareService::getId)
                .toList();

        return new HashSet<>(userRecommendationRepository.findLatestBookmarkedServiceIds(userId, serviceIds));
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

    private Integer normalizeIncludeClosed(Boolean includeClosed) {
        return includeClosed != null && includeClosed ? 1 : 0;
    }

    private String normalizeSourceType(String sourceType) {
        if (sourceType == null || sourceType.isBlank()) return null;
        String upper = sourceType.trim().toUpperCase();
        return switch (upper) {
            case "YOUTH", "BOKJIRO_CENTRAL", "BOKJIRO_LOCAL" -> upper;
            default -> throw new CustomException(ErrorCode.INVALID_INPUT);
        };
    }

    private String normalizeNullable(String text) {
        if (text == null) return null;
        String trimmed = text.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
