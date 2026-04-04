package com.example.welfare.policy.controller;

import com.example.welfare.global.response.ApiResponse;
import com.example.welfare.policy.dto.PolicyDetailResponse;
import com.example.welfare.policy.dto.PolicySummaryResponse;
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
            @RequestParam(defaultValue = "0") int page) {
        return ResponseEntity.ok(ApiResponse.success(policySearchService.search(keyword.trim(), page)));
    }
}
