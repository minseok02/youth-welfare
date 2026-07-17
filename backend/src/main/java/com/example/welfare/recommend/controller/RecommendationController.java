package com.example.welfare.recommend.controller;

import com.example.welfare.global.auth.AuthenticatedUser;
import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.global.response.ApiResponse;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.ServiceRegionRepository;
import com.example.welfare.recommend.dto.RecommendationCandidateProjection;
import com.example.welfare.recommend.dto.RecommendationRefreshAsyncResponse;
import com.example.welfare.recommend.dto.RecommendationRefreshStatusResponse;
import com.example.welfare.recommend.dto.RecommendationResponse;
import com.example.welfare.recommend.dto.SimilarUsersViewedPolicyResponse;
import com.example.welfare.recommend.entity.UserRecommendation;
import com.example.welfare.recommend.service.RecommendationAccessService;
import com.example.welfare.recommend.service.RecommendationBookmarkCommandService;
import com.example.welfare.recommend.service.RecommendationGenerationService;
import com.example.welfare.recommend.service.RecommendationLogReadService;
import com.example.welfare.recommend.service.RecommendationProjectionReadService;
import com.example.welfare.recommend.service.RecommendationRefreshAsyncJobService;
import com.example.welfare.recommend.service.SimilarUsersViewedPolicyReadService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/recommendations")
@RequiredArgsConstructor
@Validated
public class RecommendationController {

    private final RecommendationAccessService recommendationAccessService;
    private final RecommendationGenerationService recommendationGenerationService;
    private final RecommendationRefreshAsyncJobService recommendationRefreshAsyncJobService;
    private final RecommendationBookmarkCommandService recommendationBookmarkCommandService;
    private final RecommendationProjectionReadService recommendationProjectionReadService;
    private final RecommendationLogReadService recommendationLogReadService;
    private final SimilarUsersViewedPolicyReadService similarUsersViewedPolicyReadService;
    private final ServiceRegionRepository serviceRegionRepository;

    // 추천 목록 조회 (저장된 결과 반환 — 실시간 AI 추가 호출 없음)
    @GetMapping
    public ResponseEntity<ApiResponse<List<RecommendationResponse>>> getRecommendations(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        Long userId = resolveUserId(authenticatedUser);
        List<UserRecommendation> recs = recommendationAccessService.getRecommendations(userId, size);

        List<Long> serviceIds = recs.stream().map(r -> r.getService().getId()).toList();
        Map<Long, Long> serviceLogMap = recommendationLogReadService.findLatestLogIdMap(userId, serviceIds);
        List<RecommendationResponse> response = toResponses(recs, serviceLogMap);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/similar-users-viewed")
    public ResponseEntity<ApiResponse<List<SimilarUsersViewedPolicyResponse>>> getSimilarUsersViewedPolicies(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestParam(defaultValue = "6") @Min(1) @Max(20) int size) {
        return ResponseEntity.ok(ApiResponse.success(
                similarUsersViewedPolicyReadService.getSimilarUsersViewedPolicies(resolveUserId(authenticatedUser), size)
        ));
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

    @PostMapping("/refresh-async")
    public ResponseEntity<ApiResponse<RecommendationRefreshAsyncResponse>> refreshAsync(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestParam(defaultValue = "false") boolean personal,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        if (personal) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        Long userId = resolveUserId(authenticatedUser);
        List<RecommendationResponse> response = readStoredResponses(userId, size);
        RecommendationRefreshStatusResponse status = recommendationRefreshAsyncJobService.trigger(userId, personal);
        return ResponseEntity.accepted().body(ApiResponse.success(new RecommendationRefreshAsyncResponse(response, status)));
    }

    @GetMapping("/refresh-status")
    public ResponseEntity<ApiResponse<RecommendationRefreshStatusResponse>> refreshStatus(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestParam(defaultValue = "false") boolean personal) {
        Long userId = resolveUserId(authenticatedUser);
        return ResponseEntity.ok(ApiResponse.success(recommendationRefreshAsyncJobService.getStatus(userId, personal)));
    }

    // 북마크 토글
    @PostMapping("/{id}/bookmark")
    public ResponseEntity<ApiResponse<Void>> toggleBookmark(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable @Min(1) Long id) {
        recommendationBookmarkCommandService.toggleRecommendationBookmark(resolveUserId(authenticatedUser), id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    private Long resolveUserId(AuthenticatedUser authenticatedUser) {
        return authenticatedUser != null ? authenticatedUser.userId() : null;
    }

    private List<RecommendationResponse> readStoredResponses(Long userId, int size) {
        List<UserRecommendation> recs = recommendationAccessService.getRecommendations(userId, size);
        List<Long> serviceIds = recs.stream().map(r -> r.getService().getId()).toList();
        Map<Long, Long> serviceLogMap = recommendationLogReadService.findLatestLogIdMap(userId, serviceIds);
        return toResponses(recs, serviceLogMap);
    }

    private List<RecommendationResponse> toResponses(List<UserRecommendation> recs,
                                                     Map<Long, Long> serviceLogMap) {
        Map<Long, RecommendationCandidateProjection> projections =
                recommendationProjectionReadService.findCandidateProjections(recs);
        Map<Long, String> sidoMap = buildSidoMap(recs);

        return recs.stream()
                .map(rec -> RecommendationResponse.from(
                        rec,
                        serviceLogMap.get(rec.getService().getId()),
                        projections.get(rec.getService().getId()),
                        sidoMap.get(rec.getService().getId())
                ))
                .collect(Collectors.toList());
    }

    private Map<Long, String> buildSidoMap(List<UserRecommendation> recs) {
        if (recs == null || recs.isEmpty()) {
            return Map.of();
        }
        List<Long> serviceIds = recs.stream()
                .map(UserRecommendation::getService)
                .map(WelfareService::getId)
                .distinct()
                .toList();
        return serviceRegionRepository.findRegionLabelsByServiceIds(serviceIds).stream()
                .collect(Collectors.toMap(
                        row -> ((Number) row[0]).longValue(),
                        row -> {
                            String regionLabel = (String) row[1];
                            if (regionLabel == null) {
                                return null;
                            }
                            int separator = regionLabel.indexOf(' ');
                            return separator > 0 ? regionLabel.substring(0, separator) : regionLabel;
                        },
                        (left, right) -> left
                ));
    }
}
