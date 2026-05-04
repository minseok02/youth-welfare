package com.example.welfare.recommend.controller;

import com.example.welfare.global.auth.AuthenticatedUser;
import com.example.welfare.global.response.ApiResponse;
import com.example.welfare.recommend.dto.RecommendationCandidateProjection;
import com.example.welfare.recommend.dto.RecommendationResponse;
import com.example.welfare.recommend.entity.UserRecommendation;
import com.example.welfare.recommend.service.RecommendationAccessService;
import com.example.welfare.recommend.service.RecommendationBookmarkCommandService;
import com.example.welfare.recommend.service.RecommendationGenerationService;
import com.example.welfare.recommend.service.RecommendationLogReadService;
import com.example.welfare.recommend.service.RecommendationProjectionReadService;
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

    private final RecommendationAccessService recommendationAccessService;
    private final RecommendationGenerationService recommendationGenerationService;
    private final RecommendationBookmarkCommandService recommendationBookmarkCommandService;
    private final RecommendationProjectionReadService recommendationProjectionReadService;
    private final RecommendationLogReadService recommendationLogReadService;

    // 추천 목록 조회 (저장된 결과 반환 — 실시간 AI 추가 호출 없음)
    @GetMapping
    public ResponseEntity<ApiResponse<List<RecommendationResponse>>> getRecommendations(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestParam(defaultValue = "10") int size) {
        Long userId = resolveUserId(authenticatedUser);
        List<UserRecommendation> recs = recommendationAccessService.getRecommendations(userId, size);

        List<Long> serviceIds = recs.stream().map(r -> r.getService().getId()).toList();
        Map<Long, Long> serviceLogMap = recommendationLogReadService.findLatestLogIdMap(userId, serviceIds);
        List<RecommendationResponse> response = toResponses(recs, serviceLogMap);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // 추천 갱신 — 파이프라인 재실행
    // personal=true: 군집 캐시 무시, 개인 프로필 기반 실시간 AI 호출
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<List<RecommendationResponse>>> refresh(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestParam(defaultValue = "false") boolean personal) {
        Long userId = resolveUserId(authenticatedUser);
        List<UserRecommendation> recs = recommendationGenerationService.recommend(userId, personal);

        // 방금 생성된 CTR 로그에서 serviceId → logId 매핑 조회
        List<Long> serviceIds = recs.stream().map(r -> r.getService().getId()).toList();
        Map<Long, Long> serviceLogMap = recommendationLogReadService.findLatestLogIdMap(userId, serviceIds);
        List<RecommendationResponse> response = toResponses(recs, serviceLogMap);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // 북마크 토글
    @PostMapping("/{id}/bookmark")
    public ResponseEntity<ApiResponse<Void>> toggleBookmark(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable Long id) {
        recommendationBookmarkCommandService.toggleRecommendationBookmark(resolveUserId(authenticatedUser), id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    private Long resolveUserId(AuthenticatedUser authenticatedUser) {
        return authenticatedUser != null ? authenticatedUser.userId() : null;
    }

    private List<RecommendationResponse> toResponses(List<UserRecommendation> recs,
                                                     Map<Long, Long> serviceLogMap) {
        Map<Long, RecommendationCandidateProjection> projections =
                recommendationProjectionReadService.findCandidateProjections(recs);

        return recs.stream()
                .map(rec -> RecommendationResponse.from(
                        rec,
                        serviceLogMap.get(rec.getService().getId()),
                        projections.get(rec.getService().getId())
                ))
                .collect(Collectors.toList());
    }
}
