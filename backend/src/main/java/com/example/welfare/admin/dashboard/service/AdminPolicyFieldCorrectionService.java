package com.example.welfare.admin.dashboard.service;

import com.example.welfare.admin.dashboard.dto.AdminPolicyFieldCorrectionListResponse;
import com.example.welfare.admin.dashboard.dto.AdminPolicyFieldCorrectionRequest;
import com.example.welfare.admin.dashboard.dto.AdminPolicyFieldCorrectionResponse;
import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.policy.entity.PolicyErrorReport;
import com.example.welfare.policy.entity.PolicyFieldCorrection;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.PolicyErrorReportRepository;
import com.example.welfare.policy.repository.PolicyFieldCorrectionRepository;
import com.example.welfare.policy.service.PolicyLookupService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AdminPolicyFieldCorrectionService {

    private final PolicyLookupService policyLookupService;
    private final PolicyErrorReportRepository policyErrorReportRepository;
    private final PolicyFieldCorrectionRepository policyFieldCorrectionRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public AdminPolicyFieldCorrectionListResponse getCorrections(Integer requestedLimit) {
        int limit = requestedLimit == null ? 20 : Math.max(1, Math.min(requestedLimit, 100));
        return new AdminPolicyFieldCorrectionListResponse(
                policyFieldCorrectionRepository.findAllByOrderByUpdatedAtDesc(PageRequest.of(0, limit)).stream()
                        .map(this::toListItem)
                        .toList()
        );
    }

    @Transactional
    public AdminPolicyFieldCorrectionResponse applyCorrection(AdminPolicyFieldCorrectionRequest request,
                                                              String adminUserKey) {
        if (request == null || request.policyId() == null || request.correctionType() == null) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        String normalizedAdminUserKey = normalizeAdminUserKey(adminUserKey);
        WelfareService policy = policyLookupService.getRequiredService(request.policyId());
        PolicyErrorReport report = resolveReport(request.reportId(), policy.getId());
        PolicyFieldCorrection.Type type = parseType(request.correctionType());
        String originalJson = originalJson(policy, type);
        String correctionJson = correctionJson(request, type);

        applyToPolicy(policy, request, type);
        if (report != null) {
            report.markReviewed(normalizeNote(request.correctionNote()), normalizedAdminUserKey, LocalDateTime.now());
        }

        PolicyFieldCorrection saved = policyFieldCorrectionRepository.save(PolicyFieldCorrection.builder()
                .service(policy)
                .correctionType(type)
                .originalJson(originalJson)
                .correctionJson(correctionJson)
                .reasonReport(report)
                .correctionNote(normalizeNote(request.correctionNote()))
                .active(true)
                .createdByUserKey(normalizedAdminUserKey)
                .updatedByUserKey(normalizedAdminUserKey)
                .build());

        return new AdminPolicyFieldCorrectionResponse(
                saved.getId(),
                policy.getId(),
                saved.getCorrectionType().name(),
                saved.getCorrectionJson(),
                report != null ? report.getId() : null,
                saved.getCorrectionNote(),
                saved.getUpdatedByUserKey(),
                saved.getUpdatedAt()
        );
    }

    private void applyToPolicy(WelfareService policy,
                               AdminPolicyFieldCorrectionRequest request,
                               PolicyFieldCorrection.Type type) {
        switch (type) {
            case APPLICATION_PERIOD -> policy.applyAdminApplicationPeriod(request.applyStartDate(), request.applyEndDate());
            case DETAIL_URL -> {
                String detailUrl = normalizeRequiredExternalUrl(request.detailUrl());
                policy.applyAdminDetailUrl(detailUrl);
            }
            case ELIGIBILITY -> {
                String eligibilityText = normalizeRequired(request.eligibilityText());
                policy.applyAdminEligibilityText(eligibilityText);
            }
            case DUPLICATE_POLICY -> {
                if (request.duplicateOfPolicyId() == null) {
                    throw new CustomException(ErrorCode.INVALID_INPUT);
                }
            }
        }
    }

    private PolicyFieldCorrection.Type parseType(String value) {
        try {
            return PolicyFieldCorrection.Type.valueOf(value.trim());
        } catch (Exception e) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
    }

    private PolicyErrorReport resolveReport(Long reportId, Long policyId) {
        if (reportId == null) {
            return null;
        }
        PolicyErrorReport report = policyErrorReportRepository.findById(reportId)
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_INPUT));
        if (report.getPolicy() == null || !Objects.equals(report.getPolicy().getId(), policyId)) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        return report;
    }

    private String originalJson(WelfareService policy, PolicyFieldCorrection.Type type) {
        return toJson(switch (type) {
            case APPLICATION_PERIOD -> mapOf(
                    "applyStartDate", policy.getApplyStartDate(),
                    "applyEndDate", policy.getApplyEndDate());
            case DETAIL_URL -> Map.of("detailUrl", nullToEmpty(policy.getDetailUrl()));
            case ELIGIBILITY -> Map.of("description", nullToEmpty(policy.getDescription()));
            case DUPLICATE_POLICY -> Map.of("policyId", policy.getId());
        });
    }

    private String correctionJson(AdminPolicyFieldCorrectionRequest request, PolicyFieldCorrection.Type type) {
        return toJson(switch (type) {
            case APPLICATION_PERIOD -> mapOf(
                    "applyStartDate", request.applyStartDate(),
                    "applyEndDate", request.applyEndDate());
            case DETAIL_URL -> Map.of("detailUrl", normalizeRequiredExternalUrl(request.detailUrl()));
            case ELIGIBILITY -> Map.of("eligibilityText", normalizeRequired(request.eligibilityText()));
            case DUPLICATE_POLICY -> singleMap("duplicateOfPolicyId", request.duplicateOfPolicyId());
        });
    }

    private Map<String, Object> singleMap(String key, Object value) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(key, value);
        return result;
    }

    private Map<String, Object> mapOf(String firstKey, Object firstValue, String secondKey, Object secondValue) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(firstKey, firstValue);
        result.put(secondKey, secondValue);
        return result;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private AdminPolicyFieldCorrectionListResponse.Item toListItem(PolicyFieldCorrection correction) {
        WelfareService policy = correction.getService();
        return new AdminPolicyFieldCorrectionListResponse.Item(
                correction.getId(),
                policy.getId(),
                policy.getTitle(),
                policy.getSourceType().name(),
                policy.getSourceId(),
                correction.getCorrectionType().name(),
                correction.getCorrectionJson(),
                correction.getCorrectionNote(),
                correction.getUpdatedByUserKey(),
                correction.getUpdatedAt()
        );
    }

    private String normalizeAdminUserKey(String adminUserKey) {
        if (adminUserKey == null || adminUserKey.isBlank()) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        return adminUserKey.trim();
    }

    private String normalizeRequired(String value) {
        String normalized = normalizeText(value, 4000);
        if (normalized == null) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        return normalized;
    }

    private String normalizeRequiredExternalUrl(String value) {
        String normalized = normalizeText(value, 1000);
        if (normalized == null) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }

        try {
            URI uri = new URI(normalized);
            String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase(Locale.ROOT) : null;
            if ((!"http".equals(scheme) && !"https".equals(scheme))
                    || uri.getHost() == null
                    || uri.getRawUserInfo() != null) {
                throw new CustomException(ErrorCode.INVALID_INPUT);
            }
            return uri.toASCIIString();
        } catch (URISyntaxException | IllegalArgumentException e) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
    }

    private String normalizeNote(String note) {
        return normalizeText(note, 1000);
    }

    private String normalizeText(String note, int maxLength) {
        if (note == null) {
            return null;
        }
        String normalized = note.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > maxLength) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        return normalized;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
