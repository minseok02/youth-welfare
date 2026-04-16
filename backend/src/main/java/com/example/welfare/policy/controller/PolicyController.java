package com.example.welfare.policy.controller;

import com.example.welfare.global.response.ApiResponse;
import com.example.welfare.policy.dto.PolicyDetailResponse;
import com.example.welfare.policy.dto.PolicyRankingResponse;
import com.example.welfare.policy.dto.PolicySummaryResponse;
import com.example.welfare.policy.service.PolicyRankingService;
import com.example.welfare.policy.service.PolicySearchService;
import com.example.welfare.policy.service.PolicyService;
import com.example.welfare.recommend.service.RecommendationLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/policies")
@RequiredArgsConstructor
public class PolicyController {

    private final PolicyService policyService;
    private final PolicyRankingService policyRankingService;
    private final PolicySearchService policySearchService;
    private final RecommendationLogService recommendationLogService;

    // 정책 목록 조회 (카테고리 필터, 페이징)
    @GetMapping
    public ResponseEntity<ApiResponse<Page<PolicySummaryResponse>>> getList(
            @RequestParam(required = false) String category,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(policyService.getList(category, pageable)));
    }

    // 정책 상세 조회 + 클릭 추적 (?log_id= 파라미터)
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PolicyDetailResponse>> getDetail(
            @PathVariable Long id,
            @RequestParam(required = false) Long logId) {
        if (logId != null) {
            recommendationLogService.markClicked(logId);
        }
        return ResponseEntity.ok(ApiResponse.success(policyService.getDetail(id)));
    }

    // 정책 검색 (FULLTEXT)
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<PolicySummaryResponse>>> search(
            @RequestParam String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) Boolean onlineApply,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                policySearchService.search(keyword.trim(), status, category, sourceType, onlineApply, sort, page, size)
        ));
    }

    // 조회수 기반 랭킹 (내부 조회수 + 외부 조회수 보조 + 최신성)
    @GetMapping("/ranking")
    public ResponseEntity<ApiResponse<List<PolicyRankingResponse>>> ranking(
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(policyRankingService.getRanking(size)));
    }
}
