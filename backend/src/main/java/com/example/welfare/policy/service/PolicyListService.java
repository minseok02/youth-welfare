package com.example.welfare.policy.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.global.util.RegionCodeUtil;
import com.example.welfare.policy.dto.PolicySummaryResponse;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.PolicyListReadCondition;
import com.example.welfare.policy.repository.WelfareServiceReadRepository;
import com.example.welfare.policy.support.Gov24BenefitTypeSupport;
import com.example.welfare.policy.support.Gov24ServiceFieldSupport;
import com.example.welfare.policy.support.Gov24UserTypeSupport;
import com.example.welfare.policy.support.WelfareSourceTypeSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PolicyListService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final long SLOW_POLICY_LIST_SERVICE_THRESHOLD_MS = 100;

    // 소득분위(1~9) → 연소득 상한 (만원), 2024년 기준 중위소득 1인 가구 기준
    // 1분위(30%), 2분위(50%), 3분위(75%), 4분위(100%), 5분위(125%), 6분위(150%), 7~9분위 추정치
    // 10분위는 상한 없음(null) — 선택해도 부스팅 대상 없음
    private static final int[] INCOME_THRESHOLDS = {
        0, 2228, 3714, 5570, 7428, 9285, 11142, 13000, 15000, 20000
    };

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
                                               Integer incomeLevel,
                                               String targetGroup,
                                               String gov24ServiceField,
                                               String gov24UserType,
                                               String gov24BenefitType,
                                               Pageable pageable) {
        // sidoCode/regionCode 계산 및 sort는 WelfareServiceReadRepositoryImpl에서 처리
        long totalStartedNanos = System.nanoTime();
        PolicyListReadCondition condition = new PolicyListReadCondition(
                normalizeNullable(category),
                normalizeSourceType(sourceType),
                normalizeStatus(status),
                normalizeStatusFilter(statusFilter),
                normalizeSidoNullable(sido),
                normalizeNullable(sgg),
                onlineApply,
                normalizeSort(sort),
                resolveIncomeMaxWon(incomeLevel),
                normalizeNullable(targetGroup),
                normalizeGov24ServiceField(gov24ServiceField),
                normalizeGov24UserType(gov24UserType),
                normalizeGov24BenefitType(gov24BenefitType)
        );
        Pageable normalizedPageable = normalizePageable(pageable);
        long repositoryStartedNanos = System.nanoTime();
        Page<WelfareService> page = welfareServiceReadRepository.findList(
                condition,
                normalizedPageable
        );
        long repositoryMs = elapsedMs(repositoryStartedNanos);

        long presentationStartedNanos = System.nanoTime();
        Page<PolicySummaryResponse> response = policyPresentationReadService.buildSummaryPage(userId, page);
        long presentationMs = elapsedMs(presentationStartedNanos);
        long totalMs = elapsedMs(totalStartedNanos);
        logListServiceTimingIfSlow(userId, condition, normalizedPageable, page, repositoryMs, presentationMs, totalMs);
        return response;
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
            case "ACTIVE_ONLY" -> "ACTIVE_ONLY";
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

    private String normalizeSidoNullable(String value) {
        String normalized = normalizeNullable(value);
        return normalized == null ? null : RegionCodeUtil.fullSidoName(normalized);
    }

    private String normalizeGov24ServiceField(String gov24ServiceField) {
        String normalized = Gov24ServiceFieldSupport.normalizeManagedLabel(gov24ServiceField);
        if (gov24ServiceField == null || gov24ServiceField.isBlank()) {
            return null;
        }
        if (normalized == null) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        return normalized;
    }

    private String normalizeGov24UserType(String gov24UserType) {
        String normalized = Gov24UserTypeSupport.normalizeManagedToken(gov24UserType);
        if (gov24UserType == null || gov24UserType.isBlank()) {
            return null;
        }
        if (normalized == null) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        return normalized;
    }

    private String normalizeGov24BenefitType(String gov24BenefitType) {
        String normalized = Gov24BenefitTypeSupport.normalizeManagedToken(gov24BenefitType);
        if (gov24BenefitType == null || gov24BenefitType.isBlank()) {
            return null;
        }
        if (normalized == null) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        return normalized;
    }

    private Pageable normalizePageable(Pageable pageable) {
        int pageNumber = pageable == null ? 0 : Math.max(0, pageable.getPageNumber());
        int requestedSize = pageable == null ? DEFAULT_PAGE_SIZE : pageable.getPageSize();
        int pageSize = requestedSize <= 0 ? DEFAULT_PAGE_SIZE : Math.min(requestedSize, MAX_PAGE_SIZE);
        return PageRequest.of(pageNumber, pageSize);
    }

    // 선택 분위보다 낮은 분위 전용 정책을 숨기기 위한 임계값
    // 7분위 선택 → INCOME_THRESHOLDS[5]=9285 → max_income<=9285 정책 제외
    // 1분위 선택 → null (숨길 하위 분위 없음)
    private Integer resolveIncomeMaxWon(Integer incomeLevel) {
        if (incomeLevel == null || incomeLevel <= 1) return null;
        if (incomeLevel > 10) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        int prevLevel = incomeLevel - 2;
        if (prevLevel <= 0) return null;
        return INCOME_THRESHOLDS[prevLevel];
    }

    private void logListServiceTimingIfSlow(Long userId,
                                            PolicyListReadCondition condition,
                                            Pageable pageable,
                                            Page<WelfareService> page,
                                            long repositoryMs,
                                            long presentationMs,
                                            long totalMs) {
        if (totalMs < SLOW_POLICY_LIST_SERVICE_THRESHOLD_MS) {
            return;
        }
        log.info("[PolicyListServiceTiming] totalMs={} repositoryMs={} presentationMs={} authenticated={} resultCount={} totalElements={} page={} size={} sort={} statusFilter={} status={} categoryPresent={} sourceType={} sidoPresent={} sggPresent={} onlineApplyPresent={} incomeFilterPresent={} targetGroupPresent={} gov24FieldPresent={} gov24UserTypePresent={} gov24BenefitTypePresent={}",
                totalMs,
                repositoryMs,
                presentationMs,
                userId != null,
                page.getNumberOfElements(),
                page.getTotalElements(),
                pageable.getPageNumber(),
                pageable.getPageSize(),
                condition.sort(),
                condition.statusFilter(),
                condition.status() == null ? null : condition.status().name(),
                condition.category() != null,
                condition.sourceType() == null ? null : condition.sourceType().name(),
                condition.sido() != null,
                condition.sgg() != null,
                condition.onlineApply() != null,
                condition.incomeMaxWon() != null,
                condition.targetGroup() != null,
                condition.gov24ServiceField() != null,
                condition.gov24UserType() != null,
                condition.gov24BenefitType() != null);
    }

    private long elapsedMs(long startedNanos) {
        return Math.max(0, (System.nanoTime() - startedNanos) / 1_000_000);
    }
}
