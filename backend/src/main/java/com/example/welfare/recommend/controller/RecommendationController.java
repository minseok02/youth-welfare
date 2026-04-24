package com.example.welfare.recommend.controller;

import com.example.welfare.global.response.ApiResponse;
import com.example.welfare.recommend.dto.RecommendationResponse;
import com.example.welfare.recommend.entity.UserRecommendation;
import com.example.welfare.recommend.facade.RecommendationFacade;
import com.example.welfare.recommend.service.RecommendationLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/recommendations")
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationFacade recommendationFacade;
    private final RecommendationLogService recommendationLogService;

    // 추천 목록 조회 (저장된 결과 반환 — 실시간 AI 추가 호출 없음)
    @GetMapping
    public ResponseEntity<ApiResponse<List<RecommendationResponse>>> getRecommendations(
            @AuthenticationPrincipal Long userId,
            @RequestParam(defaultValue = "10") int size) {
        List<UserRecommendation> recs = recommendationFacade.getRecommendations(userId, size);
        List<RecommendationResponse> response = recs.stream()
                .map(RecommendationResponse::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // 추천 갱신 — 파이프라인 재실행 (로그인 시 자동 호출 또는 수동 갱신)
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<List<RecommendationResponse>>> refresh(
            @AuthenticationPrincipal Long userId) {
        List<UserRecommendation> recs = recommendationFacade.recommend(userId);

        // 방금 생성된 CTR 로그에서 serviceId → logId 매핑 조회
        List<Long> serviceIds = recs.stream().map(r -> r.getService().getId()).toList();
        Map<Long, Long> serviceLogMap = recommendationLogService.findLatestLogIdMap(userId, serviceIds);

        List<RecommendationResponse> response = recs.stream()
                .map(rec -> RecommendationResponse.from(rec, serviceLogMap.get(rec.getService().getId())))
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // 북마크 토글
    @PostMapping("/{id}/bookmark")
    public ResponseEntity<ApiResponse<Void>> toggleBookmark(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id) {
        recommendationFacade.toggleBookmark(userId, id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
