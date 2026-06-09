package com.example.welfare.policy.controller;

import com.example.welfare.global.auth.AuthenticatedUser;
import com.example.welfare.global.response.ApiResponse;
import com.example.welfare.global.web.ClientFingerprintService;
import com.example.welfare.policy.dto.PolicyDetailResponse;
import com.example.welfare.policy.dto.PolicyErrorReportCreateRequest;
import com.example.welfare.policy.dto.PolicyErrorReportResponse;
import com.example.welfare.policy.dto.PolicyRankingResponse;
import com.example.welfare.policy.dto.PolicySearchResponse;
import com.example.welfare.policy.dto.PolicySummaryResponse;
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
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
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
public class PolicyController {

    private static final int MAX_PUBLIC_PAGE_NUMBER = 1000;
    private static final int MAX_PUBLIC_PAGE_SIZE = 100;

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
        validatePublicPageable(pageable);
        String clientFingerprint = clientFingerprintService.build(request);
        policyTrafficRateLimitService.checkListLimit(resolveRateLimitActorKey(authenticatedUser, clientFingerprint));
        return ResponseEntity.ok(ApiResponse.success(
                policyListService.getList(resolveUserId(authenticatedUser), category, sourceType, status, statusFilter, sido, sgg, onlineApply, sort, incomeLevel, targetGroup, gov24ServiceField, gov24UserType, gov24BenefitType, pageable)
        ));
    }

    // 정책 상세 조회 + 클릭 추적 (?log_id= 파라미터)
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PolicyDetailResponse>> getDetail(
            @PathVariable @Min(1) Long id,
            @RequestParam(required = false) @Min(1) Long logId,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            HttpServletRequest request) {
        Long userId = resolveUserId(authenticatedUser);
        String clientFingerprint = clientFingerprintService.build(request);
        policyTrafficRateLimitService.checkDetailLimit(resolveRateLimitActorKey(authenticatedUser, clientFingerprint), id);
        if (logId != null) {
            recommendationLogService.markClicked(logId, userId);
        }
        boolean increaseViewCount = policyViewLogService.registerViewIfFirstInWindow(id, userId, clientFingerprint);
        return ResponseEntity.ok(ApiResponse.success(policyDetailService.getDetail(userId, id, increaseViewCount)));
    }

    // 정책 검색 (FULLTEXT)
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<PolicySearchResponse>> search(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestParam @NotBlank @Size(max = 100) String keyword,
            @RequestParam(required = false) @Size(max = 20) String status,
            @RequestParam(required = false) @Size(max = 20) String statusFilter,
            @RequestParam(required = false) @Size(max = 100) String category,
            @RequestParam(required = false) @Size(max = 40) String sourceType,
            @RequestParam(required = false) Boolean onlineApply,
            @RequestParam(required = false) @Size(max = 100) String sido,
            @RequestParam(required = false) @Size(max = 100) String sgg,
            @RequestParam(required = false) @Size(max = 20) String sort,
            @RequestParam(required = false) @Min(1) @Max(10) Integer incomeLevel,
            @RequestParam(required = false) @Size(max = 100) String targetGroup,
            @RequestParam(required = false) @Size(max = 100) String gov24ServiceField,
            @RequestParam(required = false) @Size(max = 100) String gov24UserType,
            @RequestParam(required = false) @Size(max = 100) String gov24BenefitType,
            @RequestParam(defaultValue = "0") @Min(0) @Max(MAX_PUBLIC_PAGE_NUMBER) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PUBLIC_PAGE_SIZE) int size,
            HttpServletRequest request) {
        Long userId = resolveUserId(authenticatedUser);
        String clientFingerprint = clientFingerprintService.build(request);
        policyTrafficRateLimitService.checkSearchLimit(resolveRateLimitActorKey(authenticatedUser, clientFingerprint));
        String trimmedKeyword = keyword.trim();
        PolicySearchResponse response = policySearchService.search(
                userId,
                trimmedKeyword,
                status,
                statusFilter,
                category,
                sourceType,
                onlineApply,
                sido,
                sgg,
                sort,
                incomeLevel,
                targetGroup,
                gov24ServiceField,
                gov24UserType,
                gov24BenefitType,
                page,
                size
        );
        policySearchLogService.record(PolicySearchLogCommand.builder()
                .userId(userId)
                .clientFingerprint(clientFingerprint)
                .keyword(trimmedKeyword)
                .resultCount(response.getTotalElements())
                .status(status)
                .statusFilter(statusFilter)
                .category(category)
                .sourceType(sourceType)
                .onlineApply(onlineApply)
                .sido(sido)
                .sgg(sgg)
                .sort(sort)
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
        return ResponseEntity.ok(ApiResponse.success(policySearchKeywordReadService.getTrendingKeywords(limit)));
    }

    @GetMapping("/search/suggestions")
    public ResponseEntity<ApiResponse<List<String>>> suggestions(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestParam @NotBlank @Size(max = 100) String keyword,
            @RequestParam(required = false) @Min(1) @Max(10) Integer limit,
            HttpServletRequest request) {
        String clientFingerprint = clientFingerprintService.build(request);
        policyTrafficRateLimitService.checkSuggestionLimit(resolveRateLimitActorKey(authenticatedUser, clientFingerprint));
        return ResponseEntity.ok(ApiResponse.success(policySearchKeywordReadService.getSuggestions(keyword, limit)));
    }

    // 조회수 기반 랭킹 (내부 조회수 + 외부 조회수 보조 + 최신성)
    @GetMapping("/ranking")
    public ResponseEntity<ApiResponse<List<PolicyRankingResponse>>> ranking(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PUBLIC_PAGE_SIZE) int size,
            HttpServletRequest request) {
        String clientFingerprint = clientFingerprintService.build(request);
        policyTrafficRateLimitService.checkRankingLimit(resolveRateLimitActorKey(authenticatedUser, clientFingerprint));
        return ResponseEntity.ok(ApiResponse.success(policyRankingService.getRanking(size)));
    }

    @PostMapping("/{id}/bookmark")
    public ResponseEntity<ApiResponse<Void>> toggleBookmark(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable @Min(1) Long id) {
        policyBookmarkCommandService.toggleBookmark(resolveUserId(authenticatedUser), id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/{id}/error-reports")
    public ResponseEntity<ApiResponse<PolicyErrorReportResponse>> submitErrorReport(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable @Min(1) Long id,
            @Valid @RequestBody(required = false) PolicyErrorReportCreateRequest request) {
        policyTrafficRateLimitService.checkErrorReportLimit(resolveErrorReportActorKey(authenticatedUser), id);
        return ResponseEntity.ok(ApiResponse.success(
                policyErrorReportCommandService.submit(
                        resolveUserId(authenticatedUser),
                        authenticatedUser != null ? authenticatedUser.userKey() : null,
                        id,
                        request
                )
        ));
    }

    private Long resolveUserId(AuthenticatedUser authenticatedUser) {
        return authenticatedUser != null ? authenticatedUser.userId() : null;
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
}
