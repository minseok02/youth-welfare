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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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
                                               Boolean includeClosed,
                                               String sido,
                                               String sgg,
                                               Boolean onlineApply,
                                               String sort,
                                               Pageable pageable) {
        Page<WelfareService> page = welfareServiceReadRepository.findList(
                new PolicyListReadCondition(
                        normalizeNullable(category),
                        normalizeSourceType(sourceType),
                        normalizeStatus(status),
                        includeClosed != null && includeClosed,
                        normalizeNullable(sido),
                        normalizeNullable(sgg),
                        onlineApply
                ),
                buildPageable(pageable, sort)
        );

        Set<Long> bookmarkedServiceIds = recommendationReadFacade.findBookmarkedServiceIds(userId, page.getContent());
        java.util.Map<Long, RecommendationCandidateProjection> projections = loadProjections(page.getContent());
        return page.map(service -> PolicySummaryResponse.from(
                service,
                bookmarkedServiceIds.contains(service.getId()),
                projections.get(service.getId())
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

    private Pageable buildPageable(Pageable pageable, String sort) {
        Sort resolvedSort = switch (normalizeSort(sort)) {
            case "VIEWS" -> Sort.by(Sort.Order.desc("viewCount"), Sort.Order.desc("createdAt"));
            case "NAME" -> Sort.by(Sort.Order.asc("title"), Sort.Order.desc("createdAt"));
            default -> Sort.by(Sort.Order.desc("createdAt"));
        };

        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), resolvedSort);
    }

    private String normalizeSort(String sort) {
        if (sort == null || sort.isBlank()) return "LATEST";
        String upper = sort.trim().toUpperCase();
        return switch (upper) {
            case "LATEST", "VIEWS", "NAME" -> upper;
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
