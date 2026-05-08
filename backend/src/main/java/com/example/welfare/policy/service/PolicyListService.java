package com.example.welfare.policy.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.policy.dto.PolicySummaryResponse;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.PolicyListReadCondition;
import com.example.welfare.policy.repository.WelfareServiceReadRepository;
import com.example.welfare.policy.support.WelfareSourceTypeSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PolicyListService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final WelfareServiceReadRepository welfareServiceReadRepository;
    private final PolicyPresentationReadService policyPresentationReadService;

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
                normalizePageable(pageable)
        );

        return policyPresentationReadService.buildSummaryPage(userId, page);
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

    private Pageable normalizePageable(Pageable pageable) {
        int pageNumber = pageable == null ? 0 : Math.max(0, pageable.getPageNumber());
        int requestedSize = pageable == null ? DEFAULT_PAGE_SIZE : pageable.getPageSize();
        int pageSize = requestedSize <= 0 ? DEFAULT_PAGE_SIZE : Math.min(requestedSize, MAX_PAGE_SIZE);
        return PageRequest.of(pageNumber, pageSize);
    }
}
