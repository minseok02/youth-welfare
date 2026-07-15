package com.example.welfare.policy.controller;

import com.example.welfare.global.auth.AuthenticatedUser;
import com.example.welfare.global.response.ApiResponse;
import com.example.welfare.global.util.RedisKeyHash;
import com.example.welfare.global.web.ClientFingerprintService;
import com.example.welfare.policy.dto.PolicyDetailResponse;
import com.example.welfare.policy.dto.PolicyErrorReportCreateRequest;
import com.example.welfare.policy.dto.PolicyErrorReportResponse;
import com.example.welfare.policy.dto.PolicyRankingResponse;
import com.example.welfare.policy.dto.PolicySearchRequest;
import com.example.welfare.policy.dto.PolicySearchResponse;
import com.example.welfare.policy.dto.PolicySearchSuggestionRequest;
import com.example.welfare.policy.dto.PolicySummaryResponse;
import com.example.welfare.policy.dto.RecommendationClickRequest;
import com.example.welfare.policy.service.PolicyBookmarkCommandService;
import com.example.welfare.policy.service.PolicyDetailService;
import com.example.welfare.policy.service.PolicyErrorReportCommandService;
import com.example.welfare.policy.service.PolicyListService;
import com.example.welfare.policy.service.PolicySearchLogCommand;
import com.example.welfare.policy.service.PolicySearchLogService;
import com.example.welfare.policy.service.PolicySearchKeywordReadService;
import com.example.welfare.policy.service.PolicyRankingService;
import com.example.welfare.policy.service.PolicySearchService;
import com.example.welfare.policy.service.PolicyTrafficRateLimitService;
import com.example.welfare.policy.service.PolicyViewLogService;
import com.example.welfare.recommend.service.RecommendationLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/policies")
@RequiredArgsConstructor
@Validated
@Slf4j
public class PolicyController {

    private static final int MAX_PUBLIC_PAGE_NUMBER = 1000;
    private static final int MAX_PUBLIC_PAGE_SIZE = 100;
    private static final long SLOW_POLICY_LIST_TIMING_THRESHOLD_MS = 100;

    private final PolicyListService policyListService;
    private final PolicyDetailService policyDetailService;
    private final PolicyBookmarkCommandService policyBookmarkCommandService;
    private final PolicyErrorReportCommandService policyErrorReportCommandService;
    private final PolicyRankingService policyRankingService;
    private final PolicySearchService policySearchService;
    private final PolicySearchKeywordReadService policySearchKeywordReadService;
    private final RecommendationLogService recommendationLogService;
    private final PolicyViewLogService policyViewLogService;
    private final PolicySearchLogService policySearchLogService;
    private final PolicyTrafficRateLimitService policyTrafficRateLimitService;
    private final ClientFingerprintService clientFingerprintService;

    // 정책 목록 조회 (카테고리 필터, 페이징)
    @GetMapping
    public ResponseEntity<ApiResponse<Page<PolicySummaryResponse>>> getList(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestParam(required = false) @Size(max = 100) String category,
            @RequestParam(required = false) @Size(max = 40) String sourceType,
            @RequestParam(required = false) @Size(max = 20) String status,
            @RequestParam(required = false) @Size(max = 20) String statusFilter,
            @RequestParam(required = false) @Size(max = 100) String sido,
            @RequestParam(required = false) @Size(max = 100) String sgg,
            @RequestParam(required = false) Boolean onlineApply,
            @RequestParam(required = false) @Size(max = 20) String sort,
            @RequestParam(required = false) @Min(1) @Max(10) Integer incomeLevel,
            @RequestParam(required = false) @Size(max = 100) String targetGroup,
            @RequestParam(required = false) @Size(max = 100) String gov24ServiceField,
            @RequestParam(required = false) @Size(max = 100) String gov24UserType,
            @RequestParam(required = false) @Size(max = 100) String gov24BenefitType,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            HttpServletRequest request) {
        long totalStartedNanos = System.nanoTime();
        validatePublicPageable(pageable);
        long fingerprintStartedNanos = System.nanoTime();
        String clientFingerprint = clientFingerprintService.build(request);
        long clientFingerprintMs = elapsedMs(fingerprintStartedNanos);
        long rateLimitStartedNanos = System.nanoTime();
        policyTrafficRateLimitService.checkListLimit(resolveRateLimitActorKey(authenticatedUser, clientFingerprint));
        long rateLimitMs = elapsedMs(rateLimitStartedNanos);
        long serviceStartedNanos = System.nanoTime();
        Page<PolicySummaryResponse> response = policyListService.getList(resolveUserId(authenticatedUser), category, sourceType, status, statusFilter, sido, sgg, onlineApply, sort, incomeLevel, targetGroup, gov24ServiceField, gov24UserType, gov24BenefitType, pageable);
        long serviceMs = elapsedMs(serviceStartedNanos);
        long totalMs = elapsedMs(totalStartedNanos);
        log.info("[UserAction] action=policy_list userKeyHash={} clientFingerprint={} total={} page={} size={} category={} sourceType={} statusFilter={} sidoPresent={} sggPresent={} sort={}",
                actorHash(authenticatedUser),
                clientFingerprint,
                response.getTotalElements(),
                pageable.getPageNumber(),
                pageable.getPageSize(),
                normalizeLogValue(category),
                normalizeLogValue(sourceType),
                normalizeLogValue(statusFilter != null ? statusFilter : status),
                hasText(sido),
                hasText(sgg),
                normalizeLogValue(sort));
        logPolicyListTimingIfSlow(
                totalMs,
                clientFingerprintMs,
                rateLimitMs,
                serviceMs,
                authenticatedUser != null,
                response.getTotalElements(),
                pageable,
                category,
                sourceType,
                statusFilter != null ? statusFilter : status,
                sido,
                sgg,
                sort
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // 정책 상세 조회
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PolicyDetailResponse>> getDetail(
            @PathVariable @Min(1) Long id,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            HttpServletRequest request) {
        Long userId = resolveUserId(authenticatedUser);
        String clientFingerprint = clientFingerprintService.build(request);
        policyTrafficRateLimitService.checkDetailLimit(resolveRateLimitActorKey(authenticatedUser, clientFingerprint), id);
        boolean increaseViewCount = policyViewLogService.registerViewIfFirstInWindow(id, userId, clientFingerprint);
        return ResponseEntity.ok(ApiResponse.success(policyDetailService.getDetail(userId, id, increaseViewCount)));
    }

    @PostMapping("/{id}/recommendation-click")
    public ResponseEntity<ApiResponse<Void>> markRecommendationClick(
            @PathVariable @Min(1) Long id,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody RecommendationClickRequest request) {
        recommendationLogService.markClicked(request.logId(), resolveUserId(authenticatedUser), id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // 정책 검색 (FULLTEXT)
    @PostMapping("/search")
    public ResponseEntity<ApiResponse<PolicySearchResponse>> search(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody PolicySearchRequest searchRequest,
            HttpServletRequest request) {
        Long userId = resolveUserId(authenticatedUser);
        String clientFingerprint = clientFingerprintService.build(request);
        policyTrafficRateLimitService.checkSearchLimit(resolveRateLimitActorKey(authenticatedUser, clientFingerprint));
        String trimmedKeyword = searchRequest.keyword().trim();
        int page = searchRequest.page() == null ? 0 : searchRequest.page();
        int size = searchRequest.size() == null ? 20 : searchRequest.size();
        PolicySearchResponse response = policySearchService.search(
                userId,
                trimmedKeyword,
                searchRequest.status(),
                searchRequest.statusFilter(),
                searchRequest.category(),
                searchRequest.sourceType(),
                searchRequest.onlineApply(),
                searchRequest.sido(),
                searchRequest.sgg(),
                searchRequest.sort(),
                searchRequest.incomeLevel(),
                searchRequest.targetGroup(),
                searchRequest.gov24ServiceField(),
                searchRequest.gov24UserType(),
                searchRequest.gov24BenefitType(),
                page,
                size
        );
        policySearchLogService.record(PolicySearchLogCommand.builder()
                .userId(userId)
                .clientFingerprint(clientFingerprint)
                .keyword(trimmedKeyword)
                .resultCount(response.getTotalElements())
                .status(searchRequest.status())
                .statusFilter(searchRequest.statusFilter())
                .category(searchRequest.category())
                .sourceType(searchRequest.sourceType())
                .onlineApply(searchRequest.onlineApply())
                .sido(searchRequest.sido())
                .sgg(searchRequest.sgg())
                .sort(searchRequest.sort())
                .page(page)
                .size(size)
                .build());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/search/trending")
    public ResponseEntity<ApiResponse<List<String>>> trending(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestParam(required = false) @Min(1) @Max(10) Integer limit,
            HttpServletRequest request) {
        String clientFingerprint = clientFingerprintService.build(request);
        policyTrafficRateLimitService.checkTrendingLimit(resolveRateLimitActorKey(authenticatedUser, clientFingerprint));
        List<String> response = policySearchKeywordReadService.getTrendingKeywords(limit);
        log.info("[UserAction] action=search_trending userKeyHash={} clientFingerprint={} limit={} resultCount={}",
                actorHash(authenticatedUser), clientFingerprint, limit, response.size());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/search/suggestions")
    public ResponseEntity<ApiResponse<List<String>>> suggestions(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody PolicySearchSuggestionRequest requestBody,
            HttpServletRequest request) {
        String clientFingerprint = clientFingerprintService.build(request);
        policyTrafficRateLimitService.checkSuggestionLimit(resolveRateLimitActorKey(authenticatedUser, clientFingerprint));
        List<String> response = policySearchKeywordReadService.getSuggestions(requestBody.keyword(), requestBody.limit());
        log.info("[UserAction] action=search_suggestions userKeyHash={} clientFingerprint={} keywordLength={} limit={} resultCount={}",
                actorHash(authenticatedUser),
                clientFingerprint,
                requestBody.keyword() == null ? 0 : requestBody.keyword().trim().length(),
                requestBody.limit(),
                response.size());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // 조회수 기반 랭킹 (내부 조회수 + 외부 조회수 보조 + 최신성)
    @GetMapping("/ranking")
    public ResponseEntity<ApiResponse<List<PolicyRankingResponse>>> ranking(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PUBLIC_PAGE_SIZE) int size,
            HttpServletRequest request) {
        String clientFingerprint = clientFingerprintService.build(request);
        policyTrafficRateLimitService.checkRankingLimit(resolveRateLimitActorKey(authenticatedUser, clientFingerprint));
        List<PolicyRankingResponse> response = policyRankingService.getRanking(size);
        log.info("[UserAction] action=policy_ranking userKeyHash={} clientFingerprint={} size={} resultCount={}",
                actorHash(authenticatedUser), clientFingerprint, size, response.size());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{id}/bookmark")
    public ResponseEntity<ApiResponse<Void>> toggleBookmark(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable @Min(1) Long id) {
        policyBookmarkCommandService.toggleBookmark(resolveUserId(authenticatedUser), id);
        log.info("[UserAction] action=bookmark_toggle userKeyHash={} serviceId={}",
                actorHash(authenticatedUser), id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/{id}/error-reports")
    public ResponseEntity<ApiResponse<PolicyErrorReportResponse>> submitErrorReport(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable @Min(1) Long id,
            @Valid @RequestBody(required = false) PolicyErrorReportCreateRequest request) {
        policyTrafficRateLimitService.checkErrorReportLimit(resolveErrorReportActorKey(authenticatedUser), id);
        PolicyErrorReportResponse response = policyErrorReportCommandService.submit(
                        resolveUserId(authenticatedUser),
                        authenticatedUser != null ? authenticatedUser.userKey() : null,
                        id,
                        request
                );
        log.info("[UserAction] action=policy_error_report userKeyHash={} serviceId={} reasonCode={} reportId={}",
                actorHash(authenticatedUser),
                id,
                request != null && request.reasonCode() != null ? request.reasonCode() : "UNKNOWN",
                response.reportId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    private Long resolveUserId(AuthenticatedUser authenticatedUser) {
        return authenticatedUser != null ? authenticatedUser.userId() : null;
    }

    private String actorHash(AuthenticatedUser authenticatedUser) {
        if (authenticatedUser == null) {
            return null;
        }
        if (authenticatedUser.hasUserKey()) {
            return RedisKeyHash.sha256Hex(authenticatedUser.userKey());
        }
        return authenticatedUser.hasUserId() ? RedisKeyHash.sha256Hex(String.valueOf(authenticatedUser.userId())) : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String normalizeLogValue(String value) {
        if (!hasText(value)) {
            return null;
        }
        return value.trim().replaceAll("[^A-Za-z0-9._:-]", "_");
    }

    private String resolveRateLimitActorKey(AuthenticatedUser authenticatedUser, String clientFingerprint) {
        if (authenticatedUser != null && authenticatedUser.hasUserKey()) {
            return "user:" + authenticatedUser.userKey();
        }
        return "fp:" + clientFingerprint;
    }

    private String resolveErrorReportActorKey(AuthenticatedUser authenticatedUser) {
        if (authenticatedUser != null && authenticatedUser.hasUserKey()) {
            return "user:" + authenticatedUser.userKey();
        }
        return "unknown";
    }

    private void validatePublicPageable(Pageable pageable) {
        if (pageable == null) {
            return;
        }
        if (pageable.getPageNumber() < 0
                || pageable.getPageNumber() > MAX_PUBLIC_PAGE_NUMBER
                || pageable.getPageSize() < 1
                || pageable.getPageSize() > MAX_PUBLIC_PAGE_SIZE) {
            throw new com.example.welfare.global.exception.CustomException(
                    com.example.welfare.global.exception.ErrorCode.INVALID_INPUT
            );
        }
    }

    private void logPolicyListTimingIfSlow(long totalMs,
                                           long clientFingerprintMs,
                                           long rateLimitMs,
                                           long serviceMs,
                                           boolean authenticated,
                                           long totalElements,
                                           Pageable pageable,
                                           String category,
                                           String sourceType,
                                           String statusFilter,
                                           String sido,
                                           String sgg,
                                           String sort) {
        if (totalMs < SLOW_POLICY_LIST_TIMING_THRESHOLD_MS) {
            return;
        }
        log.info("[PolicyListControllerTiming] totalMs={} clientFingerprintMs={} rateLimitMs={} serviceMs={} authenticated={} totalElements={} page={} size={} category={} sourceType={} statusFilter={} sidoPresent={} sggPresent={} sort={}",
                totalMs,
                clientFingerprintMs,
                rateLimitMs,
                serviceMs,
                authenticated,
                totalElements,
                pageable == null ? 0 : pageable.getPageNumber(),
                pageable == null ? 0 : pageable.getPageSize(),
                normalizeLogValue(category),
                normalizeLogValue(sourceType),
                normalizeLogValue(statusFilter),
                hasText(sido),
                hasText(sgg),
                normalizeLogValue(sort));
    }

    private long elapsedMs(long startedNanos) {
        return Math.max(0, (System.nanoTime() - startedNanos) / 1_000_000);
    }
}
