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
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PolicyListService {

    private final WelfareServiceReadRepository welfareServiceReadRepository;
    private final PolicyPresentationReadService policyPresentationReadService;

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

        return policyPresentationReadService.buildSummaryPage(userId, page);
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
