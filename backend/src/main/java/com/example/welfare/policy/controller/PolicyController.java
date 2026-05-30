package com.example.welfare.policy.controller;

import com.example.welfare.global.auth.AuthenticatedUser;
import com.example.welfare.global.response.ApiResponse;
import com.example.welfare.global.web.ClientFingerprintService;
import com.example.welfare.policy.dto.PolicyDetailResponse;
import com.example.welfare.policy.dto.PolicyRankingResponse;
import com.example.welfare.policy.dto.PolicySearchResponse;
import com.example.welfare.policy.dto.PolicySummaryResponse;
import com.example.welfare.policy.service.PolicyBookmarkCommandService;
import com.example.welfare.policy.service.PolicyDetailService;
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
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/policies")
@RequiredArgsConstructor
public class PolicyController {

    private final PolicyListService policyListService;
    private final PolicyDetailService policyDetailService;
    private final PolicyBookmarkCommandService policyBookmarkCommandService;
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
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String statusFilter,
            @RequestParam(required = false) String sido,
            @RequestParam(required = false) String sgg,
            @RequestParam(required = false) Boolean onlineApply,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) Integer incomeLevel,
            @RequestParam(required = false) String targetGroup,
            @RequestParam(required = false) String gov24ServiceField,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                policyListService.getList(resolveUserId(authenticatedUser), category, sourceType, status, statusFilter, sido, sgg, onlineApply, sort, incomeLevel, targetGroup, gov24ServiceField, pageable)
        ));
    }

    // 정책 상세 조회 + 클릭 추적 (?log_id= 파라미터)
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PolicyDetailResponse>> getDetail(
            @PathVariable Long id,
            @RequestParam(required = false) Long logId,
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
            @RequestParam String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String statusFilter,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) Boolean onlineApply,
            @RequestParam(required = false) String sido,
            @RequestParam(required = false) String sgg,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) Integer incomeLevel,
            @RequestParam(required = false) String targetGroup,
            @RequestParam(required = false) String gov24ServiceField,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
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
            @RequestParam(required = false) Integer limit,
            HttpServletRequest request) {
        String clientFingerprint = clientFingerprintService.build(request);
        policyTrafficRateLimitService.checkTrendingLimit(resolveRateLimitActorKey(authenticatedUser, clientFingerprint));
        return ResponseEntity.ok(ApiResponse.success(policySearchKeywordReadService.getTrendingKeywords(limit)));
    }

    @GetMapping("/search/suggestions")
    public ResponseEntity<ApiResponse<List<String>>> suggestions(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestParam String keyword,
            @RequestParam(required = false) Integer limit,
            HttpServletRequest request) {
        String clientFingerprint = clientFingerprintService.build(request);
        policyTrafficRateLimitService.checkSuggestionLimit(resolveRateLimitActorKey(authenticatedUser, clientFingerprint));
        return ResponseEntity.ok(ApiResponse.success(policySearchKeywordReadService.getSuggestions(keyword, limit)));
    }

    // 조회수 기반 랭킹 (내부 조회수 + 외부 조회수 보조 + 최신성)
    @GetMapping("/ranking")
    public ResponseEntity<ApiResponse<List<PolicyRankingResponse>>> ranking(
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(policyRankingService.getRanking(size)));
    }

    @PostMapping("/{id}/bookmark")
    public ResponseEntity<ApiResponse<Void>> toggleBookmark(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable Long id) {
        policyBookmarkCommandService.toggleBookmark(resolveUserId(authenticatedUser), id);
        return ResponseEntity.ok(ApiResponse.success(null));
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
}
