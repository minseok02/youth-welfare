package com.example.welfare.policy.controller;

import com.example.welfare.global.response.ApiResponse;
import com.example.welfare.policy.dto.SearchYouthRelevanceBackfillResponse;
import com.example.welfare.policy.service.SearchYouthRelevanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/admin/policies")
@RequiredArgsConstructor
public class PolicyAdminController {

    private final SearchYouthRelevanceService searchYouthRelevanceService;

    @PostMapping("/search-youth-relevance/rebuild")
    public ResponseEntity<ApiResponse<SearchYouthRelevanceBackfillResponse>> rebuildSearchYouthRelevance() {
        log.info("[Admin] 검색용 청년 플래그 백필 트리거");
        return ResponseEntity.ok(ApiResponse.success(searchYouthRelevanceService.backfillAll()));
    }
}
