package com.example.welfare.policy.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.policy.dto.PolicyDetailResponse;
import com.example.welfare.policy.dto.PolicySummaryResponse;
import com.example.welfare.policy.entity.ServiceRegion;
import com.example.welfare.policy.entity.ServiceTag;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.entity.WelfareServiceDetail;
import com.example.welfare.policy.repository.PolicyListReadCondition;
import com.example.welfare.policy.repository.ServiceRegionRepository;
import com.example.welfare.policy.repository.ServiceTagRepository;
import com.example.welfare.policy.repository.WelfareServiceDetailRepository;
import com.example.welfare.policy.repository.WelfareServiceReadRepository;
import com.example.welfare.policy.repository.WelfareServiceRepository;
import com.example.welfare.policy.support.WelfareSourceTypeSupport;
import com.example.welfare.recommend.dto.RecommendationCandidateProjection;
import com.example.welfare.recommend.facade.RecommendationReadFacade;
import com.example.welfare.recommend.repository.CanonicalRecommendationReadModelRepository;
import com.example.welfare.recommend.service.RecommendationBookmarkCommandService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PolicyService {

    private final WelfareServiceReadRepository welfareServiceReadRepository;
    private final WelfareServiceRepository welfareServiceRepository;
    private final WelfareServiceDetailRepository detailRepository;
    private final ServiceRegionRepository regionRepository;
    private final ServiceTagRepository tagRepository;
    private final RecommendationReadFacade recommendationReadFacade;
    private final RecommendationBookmarkCommandService recommendationBookmarkCommandService;
    private final CanonicalRecommendationReadModelRepository canonicalRecommendationReadModelRepository;

    @Transactional(readOnly = true)
    public Page<PolicySummaryResponse> getList(Long userId,
                                               String category,
                                               String sourceType,
                                               String status,
                                               String statusFilter,
                                               String sido,
                                               String sgg,
                                               Boolean onlineApply,
                                               String sort,
                                               Pageable pageable) {
        // sidoCode/regionCode 계산 및 sort는 WelfareServiceReadRepositoryImpl에서 처리
        Page<WelfareService> page = welfareServiceReadRepository.findList(
                new PolicyListReadCondition(
                        normalizeNullable(category),
                        normalizeSourceType(sourceType),
                        normalizeStatus(status),
                        normalizeStatusFilter(statusFilter),
                        normalizeNullable(sido),
                        normalizeNullable(sgg),
                        onlineApply,
                        normalizeSort(sort)
                ),
                pageable
        );

        Set<Long> bookmarkedServiceIds = recommendationReadFacade.findBookmarkedServiceIds(userId, page.getContent());
        java.util.Map<Long, RecommendationCandidateProjection> projections = loadProjections(page.getContent());
        Map<Long, String> sidoMap = buildSidoMap(page.getContent());
        return page.map(service -> PolicySummaryResponse.from(
                service,
                bookmarkedServiceIds.contains(service.getId()),
                projections.get(service.getId()),
                sidoMap.get(service.getId())
        ));
    }

    @Transactional
    public PolicyDetailResponse getDetail(Long userId, Long serviceId, boolean increaseViewCount) {
        WelfareService ws = welfareServiceRepository.findById(serviceId)
                .orElseThrow(() -> new CustomException(ErrorCode.POLICY_NOT_FOUND));
        if (increaseViewCount) {
            ws.increaseViewCount();
        }

        WelfareServiceDetail detail = detailRepository.findByServiceId(serviceId).orElse(null);
        List<ServiceRegion> regions = regionRepository.findByServiceId(serviceId);
        List<ServiceTag> tags = tagRepository.findByServiceId(serviceId);
        boolean bookmarked = recommendationReadFacade.findBookmarkedServiceIds(userId, List.of(ws)).contains(serviceId);
        RecommendationCandidateProjection projection = canonicalRecommendationReadModelRepository
                .findByServiceIds(List.of(serviceId))
                .get(serviceId);

        return PolicyDetailResponse.of(ws, detail, regions, tags, bookmarked, projection);
    }

    @Transactional
    public void toggleBookmark(Long userId, Long serviceId) {
        recommendationBookmarkCommandService.togglePolicyBookmark(userId, serviceId);
    }

    private java.util.Map<Long, RecommendationCandidateProjection> loadProjections(List<WelfareService> services) {
        if (services == null || services.isEmpty()) {
            return java.util.Map.of();
        }
        return canonicalRecommendationReadModelRepository.findByServiceIds(
                services.stream().map(WelfareService::getId).toList()
        );
    }

    // 카드 source 표시: hostOrg 없는 복지로 지자체 정책에 sido 제공 (B안)
    // BOKJIRO_LOCAL만 sido_name이 있고, YOUTH는 region_code만 있어 sido_name=NULL → 이 맵에 안 잡힘
    private Map<Long, String> buildSidoMap(List<WelfareService> services) {
        if (services == null || services.isEmpty()) return Map.of();
        List<Long> ids = services.stream().map(WelfareService::getId).toList();
        return regionRepository.findFirstSidoByServiceIds(ids).stream()
                .collect(java.util.stream.Collectors.toMap(
                        row -> ((Number) row[0]).longValue(),
                        row -> (String) row[1],
                        (a, b) -> a
                ));
    }

    private String normalizeSort(String sort) {
        if (sort == null || sort.isBlank()) return "LATEST";
        String upper = sort.trim().toUpperCase();
        return switch (upper) {
            case "LATEST", "VIEWS", "DEADLINE" -> upper;
            // NAME: UI 정렬 옵션에서는 제거됐지만 코드는 유지 (API 호환성)
            case "NAME" -> upper;
            default -> throw new CustomException(ErrorCode.INVALID_INPUT);
        };
    }

    private WelfareService.ServiceStatus normalizeStatus(String status) {
        if (status == null || status.isBlank()) return null;
        String upper = status.trim().toUpperCase();
        return switch (upper) {
            case "ACTIVE" -> WelfareService.ServiceStatus.ACTIVE;
            case "UPCOMING" -> WelfareService.ServiceStatus.UPCOMING;
            case "CLOSED" -> WelfareService.ServiceStatus.CLOSED;
            case "ALL" -> null;
            default -> throw new CustomException(ErrorCode.INVALID_INPUT);
        };
    }

    // ACTIVE_ONLY(기본): 신청가능·예정, 마감일 미도래
    // EXPIRED_ONLY: CLOSED 또는 applyEndDate 지남 (온통청년처럼 DB status=ACTIVE이지만 마감된 경우 포함)
    // ALL: 모든 상태
    private String normalizeStatusFilter(String statusFilter) {
        if (statusFilter == null || statusFilter.isBlank()) return "ACTIVE_ONLY";
        return switch (statusFilter.trim().toUpperCase()) {
            case "ALL" -> "ALL";
            case "EXPIRED_ONLY" -> "EXPIRED_ONLY";
            default -> "ACTIVE_ONLY";
        };
    }

    private WelfareService.SourceType normalizeSourceType(String sourceType) {
        try {
            return WelfareSourceTypeSupport.parseNullable(sourceType);
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
    }

    private String normalizeNullable(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
