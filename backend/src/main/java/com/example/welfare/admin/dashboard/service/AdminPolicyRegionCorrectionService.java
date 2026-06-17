package com.example.welfare.admin.dashboard.service;

import com.example.welfare.admin.dashboard.dto.AdminPolicyRegionCorrectionRequest;
import com.example.welfare.admin.dashboard.dto.AdminPolicyRegionCorrectionListResponse;
import com.example.welfare.admin.dashboard.dto.AdminPolicyRegionCorrectionRevertRequest;
import com.example.welfare.admin.dashboard.dto.AdminPolicyRegionCorrectionResponse;
import com.example.welfare.admin.dashboard.dto.AdminRegionOptionResponse;
import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.global.util.RegionCodeUtil;
import com.example.welfare.policy.entity.PolicyErrorReport;
import com.example.welfare.policy.entity.PolicyRegionCorrection;
import com.example.welfare.policy.entity.ServiceRegion;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.PolicyErrorReportRepository;
import com.example.welfare.policy.repository.PolicyRegionCorrectionRepository;
import com.example.welfare.policy.repository.ServiceRegionRepository;
import com.example.welfare.policy.service.PolicyLookupService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AdminPolicyRegionCorrectionService {

    private final PolicyLookupService policyLookupService;
    private final PolicyRegionCorrectionRepository policyRegionCorrectionRepository;
    private final PolicyErrorReportRepository policyErrorReportRepository;
    private final ServiceRegionRepository serviceRegionRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public AdminRegionOptionResponse getRegionOptions() {
        return new AdminRegionOptionResponse(RegionCodeUtil.allRegionNames().stream()
                .map(region -> new AdminRegionOptionResponse.RegionOption(
                        region.regionCode(),
                        region.sidoName(),
                        region.sggName(),
                        region.sidoName() + " " + region.sggName() + " (" + region.regionCode() + ")"
                ))
                .toList());
    }

    @Transactional(readOnly = true)
    public AdminPolicyRegionCorrectionListResponse getCorrections(Integer requestedLimit, boolean activeOnly) {
        int limit = requestedLimit == null ? 20 : Math.max(1, Math.min(requestedLimit, 100));
        return new AdminPolicyRegionCorrectionListResponse(
                (activeOnly
                        ? policyRegionCorrectionRepository.findByActiveTrueOrderByUpdatedAtDesc(PageRequest.of(0, limit))
                        : policyRegionCorrectionRepository.findAllByOrderByUpdatedAtDesc(PageRequest.of(0, limit)))
                        .stream()
                        .map(this::toListItem)
                        .toList()
        );
    }

    @Transactional
    public AdminPolicyRegionCorrectionResponse applyCorrection(AdminPolicyRegionCorrectionRequest request,
                                                               String adminUserKey) {
        if (request == null || request.policyId() == null) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        String normalizedAdminUserKey = normalizeAdminUserKey(adminUserKey);
        WelfareService policy = policyLookupService.getRequiredService(request.policyId());
        PolicyErrorReport report = resolveReport(request.reportId(), policy.getId());
        boolean nationwide = Boolean.TRUE.equals(request.nationwide());
        List<RegionCodeUtil.RegionName> regionNames = nationwide
                ? List.of()
                : resolveRegionNames(request.regionCodes());
        PolicyRegionCorrection.Scope scope = nationwide
                ? PolicyRegionCorrection.Scope.NATIONWIDE
                : PolicyRegionCorrection.Scope.REGIONS;
        String note = normalizeNote(request.correctionNote());
        String regionsJson = toRegionsJson(regionNames);
        String originalRegionsJson = toServiceRegionsJson(serviceRegionRepository.findByServiceId(policy.getId()));

        PolicyRegionCorrection correction = policyRegionCorrectionRepository.findByServiceId(policy.getId())
                .orElseGet(() -> PolicyRegionCorrection.builder()
                        .service(policy)
                        .originalRegionsJson(originalRegionsJson)
                        .createdByUserKey(normalizedAdminUserKey)
                        .updatedByUserKey(normalizedAdminUserKey)
                        .active(true)
                        .build());
        correction.replace(scope, regionsJson, originalRegionsJson, report, note, normalizedAdminUserKey);
        PolicyRegionCorrection saved = policyRegionCorrectionRepository.save(correction);

        replaceServiceRegions(policy, regionNames);
        if (report != null) {
            report.markReviewed(note, normalizedAdminUserKey, LocalDateTime.now());
        }

        return new AdminPolicyRegionCorrectionResponse(
                saved.getId(),
                policy.getId(),
                saved.getCorrectionScope().name(),
                regionNames.stream()
                        .map(region -> new AdminPolicyRegionCorrectionResponse.RegionItem(
                                region.regionCode(),
                                region.sidoName(),
                                region.sggName()
                        ))
                        .toList(),
                report != null ? report.getId() : null,
                saved.getCorrectionNote(),
                saved.getUpdatedByUserKey(),
                saved.getUpdatedAt()
        );
    }

    @Transactional
    public AdminPolicyRegionCorrectionResponse revertCorrection(Long correctionId,
                                                                AdminPolicyRegionCorrectionRevertRequest request,
                                                                String adminUserKey) {
        if (correctionId == null) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        String normalizedAdminUserKey = normalizeAdminUserKey(adminUserKey);
        PolicyRegionCorrection correction = policyRegionCorrectionRepository.findById(correctionId)
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_INPUT));
        if (!correction.isActive()) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        List<AdminPolicyRegionCorrectionResponse.RegionItem> originalRegions =
                parseRegionItems(correction.getOriginalRegionsJson());
        replaceServiceRegionItems(correction.getService(), originalRegions);
        String note = normalizeNote(request != null ? request.reviewNote() : null);
        correction.deactivate(normalizedAdminUserKey, note);
        return new AdminPolicyRegionCorrectionResponse(
                correction.getId(),
                correction.getService().getId(),
                correction.getCorrectionScope().name(),
                originalRegions,
                correction.getReasonReport() != null ? correction.getReasonReport().getId() : null,
                correction.getCorrectionNote(),
                correction.getUpdatedByUserKey(),
                correction.getUpdatedAt()
        );
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

    private List<RegionCodeUtil.RegionName> resolveRegionNames(List<String> regionCodes) {
        if (regionCodes == null || regionCodes.isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        Set<String> seen = new LinkedHashSet<>();
        List<RegionCodeUtil.RegionName> regions = new ArrayList<>();
        for (String rawCode : regionCodes) {
            if (rawCode == null || rawCode.isBlank()) {
                continue;
            }
            String code = rawCode.trim();
            if (!seen.add(code)) {
                continue;
            }
            RegionCodeUtil.RegionName region = RegionCodeUtil.getRegionName(code);
            if (region == null) {
                throw new CustomException(ErrorCode.INVALID_INPUT);
            }
            regions.add(region);
        }
        if (regions.isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        return List.copyOf(regions);
    }

    private void replaceServiceRegions(WelfareService policy, List<RegionCodeUtil.RegionName> regionNames) {
        serviceRegionRepository.deleteByServiceId(policy.getId());
        if (regionNames.isEmpty()) {
            return;
        }
        List<ServiceRegion> regions = regionNames.stream()
                .map(region -> ServiceRegion.builder()
                        .service(policy)
                        .regionCode(region.regionCode())
                        .sidoName(region.sidoName())
                        .sggName(region.sggName())
                        .build())
                .toList();
        serviceRegionRepository.saveAll(regions);
    }

    private void replaceServiceRegionItems(WelfareService policy,
                                           List<AdminPolicyRegionCorrectionResponse.RegionItem> regionItems) {
        serviceRegionRepository.deleteByServiceId(policy.getId());
        if (regionItems == null || regionItems.isEmpty()) {
            return;
        }
        List<ServiceRegion> regions = regionItems.stream()
                .map(region -> ServiceRegion.builder()
                        .service(policy)
                        .regionCode(region.regionCode())
                        .sidoName(region.sidoName())
                        .sggName(region.sggName())
                        .build())
                .toList();
        serviceRegionRepository.saveAll(regions);
    }

    private AdminPolicyRegionCorrectionListResponse.Item toListItem(PolicyRegionCorrection correction) {
        WelfareService policy = correction.getService();
        return new AdminPolicyRegionCorrectionListResponse.Item(
                correction.getId(),
                policy.getId(),
                policy.getTitle(),
                policy.getSourceType().name(),
                policy.getSourceId(),
                correction.getCorrectionScope().name(),
                correction.isActive(),
                parseRegionItems(correction.getRegionsJson()),
                correction.getCorrectionNote(),
                correction.getUpdatedByUserKey(),
                correction.getUpdatedAt()
        );
    }

    private String toRegionsJson(List<RegionCodeUtil.RegionName> regions) {
        try {
            return objectMapper.writeValueAsString(regions.stream()
                    .map(region -> new AdminPolicyRegionCorrectionResponse.RegionItem(
                            region.regionCode(),
                            region.sidoName(),
                            region.sggName()
                    ))
                    .toList());
        } catch (JsonProcessingException e) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private String toServiceRegionsJson(List<ServiceRegion> regions) {
        try {
            return objectMapper.writeValueAsString(regions.stream()
                    .map(this::toRawRegionItem)
                    .filter(Objects::nonNull)
                    .toList());
        } catch (JsonProcessingException e) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private AdminPolicyRegionCorrectionResponse.RegionItem toRegionItem(ServiceRegion region) {
        if (region == null) {
            return null;
        }
        String regionCode = region.getRegionCode();
        String sidoName = region.getSidoName();
        String sggName = region.getSggName();
        if ((sidoName == null || sidoName.isBlank()) && regionCode != null) {
            RegionCodeUtil.RegionName regionName = RegionCodeUtil.getRegionName(regionCode);
            if (regionName != null) {
                sidoName = regionName.sidoName();
                sggName = regionName.sggName();
            }
        }
        return new AdminPolicyRegionCorrectionResponse.RegionItem(regionCode, sidoName, sggName);
    }

    private AdminPolicyRegionCorrectionResponse.RegionItem toRawRegionItem(ServiceRegion region) {
        if (region == null) {
            return null;
        }
        return new AdminPolicyRegionCorrectionResponse.RegionItem(
                region.getRegionCode(),
                region.getSidoName(),
                region.getSggName()
        );
    }

    private List<AdminPolicyRegionCorrectionResponse.RegionItem> parseRegionItems(String regionsJson) {
        if (regionsJson == null || regionsJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(
                    regionsJson,
                    new TypeReference<List<AdminPolicyRegionCorrectionResponse.RegionItem>>() {
                    }
            );
        } catch (JsonProcessingException e) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private String normalizeAdminUserKey(String adminUserKey) {
        if (adminUserKey == null || adminUserKey.isBlank()) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        return adminUserKey.trim();
    }

    private String normalizeNote(String note) {
        if (note == null) {
            return null;
        }
        String normalized = note.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > 1000) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        return normalized;
    }
}
