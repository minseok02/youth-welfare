package com.example.welfare.policy.controller;

import com.example.welfare.global.response.ApiResponse;
import com.example.welfare.policy.dto.PolicyCategoryAuditResponse;
import com.example.welfare.policy.dto.PolicyEmbeddingRefreshResponse;
import com.example.welfare.policy.dto.PolicyReferenceUrlBackfillResponse;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/admin/policies")
@RequiredArgsConstructor
public class PolicyAdminController {

    private final SearchYouthRelevanceService searchYouthRelevanceService;
    private final PolicyEmbeddingAdminService policyEmbeddingAdminService;
    private final PolicyRetrievalEvaluationService policyRetrievalEvaluationService;
    private final PolicyRetrievalEvaluationExportService policyRetrievalEvaluationExportService;
    private final PolicyRetrievalQualityGateService policyRetrievalQualityGateService;
    private final PolicyCategoryAuditService policyCategoryAuditService;
    private final PolicyReferenceUrlAdminService policyReferenceUrlAdminService;

    @PostMapping("/search-youth-relevance/rebuild")
    public ResponseEntity<ApiResponse<SearchYouthRelevanceBackfillResponse>> rebuildSearchYouthRelevance() {
        log.info("[Admin] 검색용 청년 플래그 백필 트리거");
        return ResponseEntity.ok(ApiResponse.success(searchYouthRelevanceService.backfillAll()));
    }

    @PostMapping("/embeddings/rebuild")
    public ResponseEntity<ApiResponse<PolicyEmbeddingRefreshResponse>> rebuildPolicyEmbeddings(
            @RequestParam(required = false) List<Long> serviceId
    ) {
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
            @RequestParam(required = false) List<WelfareService.SourceType> sourceType,
            @RequestParam(defaultValue = "0") int limitPerSource,
            @RequestParam(defaultValue = "true") boolean missingOnly
    ) {
        PolicyReferenceUrlBackfillResponse response =
                policyReferenceUrlAdminService.rebuildReferenceUrls(sourceType, limitPerSource, missingOnly);
        log.info("[Admin] 정책 참고 URL 재구축 트리거 scope={} sourceTypes={} limitPerSource={} missingOnly={} scanned={} skipped={} updated={} missing={} failed={}",
                response.scope(),
                response.sourceTypes(),
                response.limitPerSource(),
                response.missingOnly(),
                response.scannedCount(),
                response.skippedCount(),
                response.updatedCount(),
                response.missingServiceCount(),
                response.failedCount());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/retrieval-evaluations/run")
    public ResponseEntity<ApiResponse<PolicyRetrievalEvaluationResponse>> runRetrievalEvaluations() {
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
    public ResponseEntity<ApiResponse<PolicyRetrievalQualityGateResponse>> evaluateRetrievalQualityGate() {
        PolicyRetrievalQualityGateResponse response = policyRetrievalQualityGateService.evaluateGate();
        log.info("[Admin] retrieval quality gate 실행 datasetKey={} passed={} failureReasons={}",
                response.datasetKey(),
                response.passed(),
                response.failureReasons());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/retrieval-evaluations/compare")
    public ResponseEntity<ApiResponse<PolicyRetrievalEvaluationCompareResponse>> compareRetrievalEvaluations(
            @RequestBody(required = false) PolicyRetrievalEvaluationCompareRequest request
    ) {
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
            @RequestBody(required = false) PolicyRetrievalEvaluationCompareRequest request
    ) {
        PolicyRetrievalEvaluationCompareResponse response = policyRetrievalEvaluationService.compareBaseline(request);
        String csv = policyRetrievalEvaluationExportService.toCsv(response);
        String filename = response.candidate().datasetKey() + "-compare.csv";
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
    public ResponseEntity<String> exportRetrievalEvaluations() {
        PolicyRetrievalEvaluationResponse response = policyRetrievalEvaluationService.evaluateBaseline();
        String csv = policyRetrievalEvaluationExportService.toCsv(response);
        String filename = response.datasetKey() + ".csv";
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
}
