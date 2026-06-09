package com.example.welfare.policy.controller;

import com.example.welfare.admin.service.AdminOperationRateLimitService;
import com.example.welfare.global.auth.AuthenticatedUser;
import com.example.welfare.global.response.ApiResponse;
import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.policy.dto.PolicyCategoryAuditResponse;
import com.example.welfare.policy.dto.PolicyEmbeddingRefreshResponse;
import com.example.welfare.policy.dto.PolicyReferenceUrlBackfillResponse;
import com.example.welfare.policy.dto.PolicyStatusSyncResponse;
import com.example.welfare.policy.dto.PolicyRetrievalEvaluationCompareRequest;
import com.example.welfare.policy.dto.PolicyRetrievalEvaluationCompareResponse;
import com.example.welfare.policy.dto.PolicyRetrievalEvaluationResponse;
import com.example.welfare.policy.dto.PolicyRetrievalQualityGateResponse;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.service.PolicyCategoryAuditService;
import com.example.welfare.policy.dto.SearchYouthRelevanceBackfillResponse;
import com.example.welfare.policy.service.PolicyEmbeddingAdminService;
import com.example.welfare.policy.service.PolicyReferenceUrlAdminService;
import com.example.welfare.policy.service.PolicyRetrievalEvaluationExportService;
import com.example.welfare.policy.service.PolicyRetrievalEvaluationService;
import com.example.welfare.policy.service.PolicyRetrievalQualityGateService;
import com.example.welfare.policy.service.SearchYouthRelevanceService;
import com.example.welfare.collect.service.StatusUpdateService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;

@Slf4j
@RestController
@RequestMapping("/api/admin/policies")
@RequiredArgsConstructor
@Validated
public class PolicyAdminController {

    static final int MAX_REFERENCE_URL_REBUILD_LIMIT_PER_SOURCE = 1_000;

    private final SearchYouthRelevanceService searchYouthRelevanceService;
    private final PolicyEmbeddingAdminService policyEmbeddingAdminService;
    private final PolicyRetrievalEvaluationService policyRetrievalEvaluationService;
    private final PolicyRetrievalEvaluationExportService policyRetrievalEvaluationExportService;
    private final PolicyRetrievalQualityGateService policyRetrievalQualityGateService;
    private final PolicyCategoryAuditService policyCategoryAuditService;
    private final PolicyReferenceUrlAdminService policyReferenceUrlAdminService;
    private final StatusUpdateService statusUpdateService;
    private final AdminOperationRateLimitService adminOperationRateLimitService;

    @PostMapping("/search-youth-relevance/rebuild")
    public ResponseEntity<ApiResponse<SearchYouthRelevanceBackfillResponse>> rebuildSearchYouthRelevance(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser
    ) {
        adminOperationRateLimitService.checkMutationLimit(actorKey(authenticatedUser), "policies:search-youth-relevance-rebuild");
        log.info("[Admin] 검색용 청년 플래그 백필 트리거");
        return ResponseEntity.ok(ApiResponse.success(searchYouthRelevanceService.backfillAll()));
    }

    @PostMapping("/embeddings/rebuild")
    public ResponseEntity<ApiResponse<PolicyEmbeddingRefreshResponse>> rebuildPolicyEmbeddings(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestParam(required = false)
            @Size(max = 100, message = "serviceId는 100개 이하여야 합니다.")
            List<@Min(value = 1, message = "serviceId는 1 이상이어야 합니다.") Long> serviceId
    ) {
        adminOperationRateLimitService.checkMutationLimit(actorKey(authenticatedUser), "policies:embeddings-rebuild");
        PolicyEmbeddingRefreshResponse response = (serviceId == null || serviceId.isEmpty())
                ? policyEmbeddingAdminService.rebuildSearchablePolicyEmbeddings()
                : policyEmbeddingAdminService.rebuildPolicyEmbeddings(serviceId);
        log.info("[Admin] 정책 임베딩 재구축 트리거 scope={} requestedServiceCount={} scannedChunkCount={} refreshedChunkCount={}",
                response.scope(),
                response.requestedServiceCount(),
                response.scannedChunkCount(),
                response.refreshedChunkCount());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/reference-urls/rebuild")
    public ResponseEntity<ApiResponse<PolicyReferenceUrlBackfillResponse>> rebuildPolicyReferenceUrls(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestParam(required = false)
            @Size(max = 10, message = "sourceType은 10개 이하여야 합니다.")
            List<WelfareService.SourceType> sourceType,
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "limitPerSource는 0 이상이어야 합니다.")
            @Max(value = MAX_REFERENCE_URL_REBUILD_LIMIT_PER_SOURCE,
                    message = "limitPerSource는 1000 이하여야 합니다.")
            int limitPerSource,
            @RequestParam(defaultValue = "true") boolean missingOnly
    ) {
        adminOperationRateLimitService.checkMutationLimit(actorKey(authenticatedUser), "policies:reference-urls-rebuild");
        int effectiveLimitPerSource = normalizeReferenceUrlLimitPerSource(limitPerSource);
        PolicyReferenceUrlBackfillResponse response =
                policyReferenceUrlAdminService.rebuildReferenceUrls(sourceType, effectiveLimitPerSource, missingOnly);
        log.info("[Admin] 정책 참고 URL 재구축 트리거 scope={} sourceTypes={} limitPerSource={} missingOnly={} scanned={} skipped={} updated={} missing={} failed={}",
                response.scope(),
                response.sourceTypes(),
                effectiveLimitPerSource,
                response.missingOnly(),
                response.scannedCount(),
                response.skippedCount(),
                response.updatedCount(),
                response.missingServiceCount(),
                response.failedCount());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/status-sync")
    public ResponseEntity<ApiResponse<PolicyStatusSyncResponse>> syncPolicyStatuses(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser
    ) {
        adminOperationRateLimitService.checkMutationLimit(actorKey(authenticatedUser), "policies:status-sync");
        StatusUpdateService.StatusSyncResult result = statusUpdateService.runStatusSync();
        log.info("[Admin] 정책 상태 sync 트리거 closedCount={} activatedCount={} reopenedCount={} cacheCleanup={}",
                result.closedCount(),
                result.activatedCount(),
                result.reopenedCount(),
                result.clusterAiCacheCleanupExecuted());
        return ResponseEntity.ok(ApiResponse.success(PolicyStatusSyncResponse.from(result)));
    }

    private int normalizeReferenceUrlLimitPerSource(int limitPerSource) {
        if (limitPerSource < 0) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        if (limitPerSource == 0) {
            return MAX_REFERENCE_URL_REBUILD_LIMIT_PER_SOURCE;
        }
        if (limitPerSource > MAX_REFERENCE_URL_REBUILD_LIMIT_PER_SOURCE) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        return limitPerSource;
    }

    @PostMapping("/retrieval-evaluations/run")
    public ResponseEntity<ApiResponse<PolicyRetrievalEvaluationResponse>> runRetrievalEvaluations(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser
    ) {
        adminOperationRateLimitService.checkExpensiveReadLimit(actorKey(authenticatedUser), "policies:retrieval-evaluations-run");
        PolicyRetrievalEvaluationResponse response = policyRetrievalEvaluationService.evaluateBaseline();
        log.info("[Admin] retrieval evaluation 실행 datasetKey={} scenarios={} top1Hits={} top3Hits={} fallbackCount={}",
                response.datasetKey(),
                response.scenarioCount(),
                response.top1HitCount(),
                response.top3HitCount(),
                response.fallbackCount());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/retrieval-evaluations/gate")
    public ResponseEntity<ApiResponse<PolicyRetrievalQualityGateResponse>> evaluateRetrievalQualityGate(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser
    ) {
        adminOperationRateLimitService.checkExpensiveReadLimit(actorKey(authenticatedUser), "policies:retrieval-evaluations-gate");
        PolicyRetrievalQualityGateResponse response = policyRetrievalQualityGateService.evaluateGate();
        log.info("[Admin] retrieval quality gate 실행 datasetKey={} passed={} failureReasons={}",
                response.datasetKey(),
                response.passed(),
                response.failureReasons());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/retrieval-evaluations/compare")
    public ResponseEntity<ApiResponse<PolicyRetrievalEvaluationCompareResponse>> compareRetrievalEvaluations(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody(required = false) PolicyRetrievalEvaluationCompareRequest request
    ) {
        adminOperationRateLimitService.checkExpensiveReadLimit(actorKey(authenticatedUser), "policies:retrieval-evaluations-compare");
        PolicyRetrievalEvaluationCompareResponse response = policyRetrievalEvaluationService.compareBaseline(request);
        log.info("[Admin] retrieval evaluation compare 실행 baseline={} candidate={} fallbackDelta={} semanticDelta={}",
                response.baseline().datasetKey(),
                response.candidate().datasetKey(),
                response.delta().fallbackCountDelta(),
                response.delta().semanticContributionCountDelta());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping(value = "/retrieval-evaluations/compare/export", produces = "text/csv")
    public ResponseEntity<String> exportComparedRetrievalEvaluations(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody(required = false) PolicyRetrievalEvaluationCompareRequest request
    ) {
        adminOperationRateLimitService.checkExpensiveReadLimit(actorKey(authenticatedUser), "policies:retrieval-evaluations-compare-export");
        PolicyRetrievalEvaluationCompareResponse response = policyRetrievalEvaluationService.compareBaseline(request);
        String csv = policyRetrievalEvaluationExportService.toCsv(response);
        String filename = safeCsvFilename(response.candidate().datasetKey(), "candidate") + "-compare.csv";
        log.info("[Admin] retrieval evaluation compare csv export baseline={} candidate={} bytes={}",
                response.baseline().datasetKey(),
                response.candidate().datasetKey(),
                csv.length());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(csv);
    }

    @GetMapping(value = "/retrieval-evaluations/export", produces = "text/csv")
    public ResponseEntity<String> exportRetrievalEvaluations(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser
    ) {
        adminOperationRateLimitService.checkExpensiveReadLimit(actorKey(authenticatedUser), "policies:retrieval-evaluations-export");
        PolicyRetrievalEvaluationResponse response = policyRetrievalEvaluationService.evaluateBaseline();
        String csv = policyRetrievalEvaluationExportService.toCsv(response);
        String filename = safeCsvFilename(response.datasetKey(), "retrieval-evaluation") + ".csv";
        log.info("[Admin] retrieval evaluation csv export datasetKey={} scenarios={} bytes={}",
                response.datasetKey(),
                response.scenarioCount(),
                csv.length());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(csv);
    }

    @GetMapping("/category-audit")
    public ResponseEntity<ApiResponse<PolicyCategoryAuditResponse>> readCategoryAudit() {
        PolicyCategoryAuditResponse response = policyCategoryAuditService.readAudit();
        log.info("[Admin] category audit 조회 totalPolicies={} searchablePolicies={} searchableRatio={} categories={} youthBroadMappings={} youthBroadSummaries={}",
                response.totalPolicyCount(),
                response.searchablePolicyCount(),
                response.searchablePolicyRatio(),
                response.unifiedCategoryCounts().size(),
                response.youthBroadCategoryMappings().size(),
                response.youthBroadCategorySummaries().size());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    private String safeCsvFilename(String raw, String fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String sanitized = raw.trim()
                .replaceAll("[^A-Za-z0-9._-]", "_")
                .replaceAll("_+", "_");
        if (sanitized.isBlank()) {
            return fallback;
        }
        String bounded = sanitized.length() > 100 ? sanitized.substring(0, 100) : sanitized;
        return bounded.toLowerCase(Locale.ROOT);
    }

    private String actorKey(AuthenticatedUser authenticatedUser) {
        return authenticatedUser != null ? authenticatedUser.userKey() : "unknown";
    }
}
