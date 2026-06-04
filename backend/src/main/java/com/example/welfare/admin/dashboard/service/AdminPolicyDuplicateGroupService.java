package com.example.welfare.admin.dashboard.service;

import com.example.welfare.admin.dashboard.dto.AdminPolicyDuplicateGroupResponse;
import com.example.welfare.admin.dashboard.dto.AdminPolicyDuplicateGroupReviewRequest;
import com.example.welfare.admin.dashboard.dto.AdminQueueStatusFilter;
import com.example.welfare.admin.dashboard.dto.AdminReviewActionResponse;
import com.example.welfare.admin.dashboard.repository.AdminPolicyDuplicateGroupReadRepository;
import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.policy.entity.PolicyDuplicateReviewRecord;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.PolicyDuplicateReviewRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminPolicyDuplicateGroupService {

    private final AdminPolicyDuplicateGroupReadRepository readRepository;
    private final PolicyDuplicateReviewRecordRepository reviewRecordRepository;

    @Transactional(readOnly = true)
    public AdminPolicyDuplicateGroupResponse getRecentGroups(Integer requestedLimit) {
        return getRecentGroups(requestedLimit, AdminQueueStatusFilter.OPEN);
    }

    @Transactional(readOnly = true)
    public AdminPolicyDuplicateGroupResponse getRecentGroups(Integer requestedLimit, AdminQueueStatusFilter statusFilter) {
        int limit = requestedLimit == null ? 10 : Math.max(1, Math.min(requestedLimit, 20));
        long openGroupCount = readRepository.countOpenGroups();
        long recentOpenGroupCount24h = readRepository.countRecentOpenGroups24h();
        long openDuplicateRowCount = readRepository.countOpenDuplicateRows();
        List<AdminPolicyDuplicateGroupResponse.Item> items = readRepository.findGroups(statusFilter, limit)
                .stream()
                .map(row -> new AdminPolicyDuplicateGroupResponse.Item(
                        row.sourceType(),
                        row.title(),
                        row.hostOrgKey(),
                        row.hostOrgLabel(),
                        row.duplicateCount(),
                        row.sourceIds(),
                        row.latestCreatedAt(),
                        row.reviewedAt() == null ? "OPEN" : "REVIEWED",
                        row.reviewNote(),
                        row.reviewedByUserKey(),
                        row.reviewedAt()
                ))
                .toList();
        return new AdminPolicyDuplicateGroupResponse(
                openGroupCount,
                recentOpenGroupCount24h,
                openDuplicateRowCount,
                items
        );
    }

    @Transactional
    public AdminReviewActionResponse markReviewed(AdminPolicyDuplicateGroupReviewRequest request, String adminUserKey) {
        WelfareService.SourceType sourceType = normalizeSourceType(request.sourceType());
        String title = normalizeRequired(request.title());
        String hostOrgKey = normalizeHostOrgKey(request.hostOrgKey());
        String hostOrgLabel = normalizeOptional(request.hostOrgLabel());
        String reviewNote = normalizeOptional(request.reviewNote());
        String normalizedAdminUserKey = normalizeRequired(adminUserKey);
        PolicyDuplicateReviewRecord record = reviewRecordRepository.findBySourceTypeAndTitleAndHostOrgKey(
                        sourceType,
                        title,
                        hostOrgKey
                )
                .orElseGet(() -> PolicyDuplicateReviewRecord.builder()
                        .sourceType(sourceType)
                        .title(title)
                        .hostOrgKey(hostOrgKey)
                        .hostOrgLabel(hostOrgLabel)
                        .reviewedByUserKey(normalizedAdminUserKey)
                        .reviewedAt(LocalDateTime.now())
                        .build());
        record.markReviewed(hostOrgLabel, reviewNote, normalizedAdminUserKey, LocalDateTime.now());
        PolicyDuplicateReviewRecord saved = reviewRecordRepository.save(record);
        return new AdminReviewActionResponse(
                saved.getId(),
                "REVIEWED",
                saved.getReviewNote(),
                saved.getReviewedByUserKey(),
                saved.getReviewedAt()
        );
    }

    private WelfareService.SourceType normalizeSourceType(String raw) {
        String normalized = normalizeRequired(raw);
        try {
            return WelfareService.SourceType.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
    }

    private String normalizeHostOrgKey(String hostOrgKey) {
        if (hostOrgKey == null) {
            return "";
        }
        String normalized = hostOrgKey.trim();
        if (normalized.length() > 255) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        return normalized;
    }

    private String normalizeRequired(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        String normalized = raw.trim();
        if (normalized.length() > 255) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        return normalized;
    }

    private String normalizeOptional(String raw) {
        if (raw == null) {
            return null;
        }
        String normalized = raw.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > 1000) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        return normalized;
    }
}
