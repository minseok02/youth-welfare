package com.example.welfare.admin.dashboard.service;

import com.example.welfare.admin.dashboard.dto.AdminPolicyLinkReviewResponse;
import com.example.welfare.admin.dashboard.dto.AdminQueueStatusFilter;
import com.example.welfare.admin.dashboard.dto.AdminReviewActionResponse;
import com.example.welfare.admin.dashboard.repository.AdminPolicyLinkReviewReadRepository;
import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.policy.entity.PolicyLinkReviewRecord;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.PolicyLinkReviewRecordRepository;
import com.example.welfare.policy.repository.WelfareServiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminPolicyLinkReviewService {

    private final AdminPolicyLinkReviewReadRepository readRepository;
    private final PolicyLinkReviewRecordRepository reviewRecordRepository;
    private final WelfareServiceRepository welfareServiceRepository;

    @Transactional(readOnly = true)
    public AdminPolicyLinkReviewResponse getRecentReviews(Integer requestedLimit) {
        return getRecentReviews(requestedLimit, AdminQueueStatusFilter.OPEN);
    }

    @Transactional(readOnly = true)
    public AdminPolicyLinkReviewResponse getRecentReviews(Integer requestedLimit, AdminQueueStatusFilter statusFilter) {
        int limit = requestedLimit == null ? 10 : Math.max(1, Math.min(requestedLimit, 20));
        long openCount = readRepository.countOpenReviews();
        long recentOpenCount24h = readRepository.countRecentOpenReviews24h();
        List<AdminPolicyLinkReviewResponse.Item> items = readRepository.findRows(statusFilter, limit)
                .stream()
                .map(row -> new AdminPolicyLinkReviewResponse.Item(
                        row.serviceId(),
                        row.title(),
                        row.sourceType(),
                        row.sourceId(),
                        row.reviewBucket(),
                        blankToNull(row.hostOrg()),
                        blankToNull(row.operatingOrg()),
                        blankToNull(row.categoryMain()),
                        blankToNull(row.categorySub()),
                        row.applyEndDate(),
                        row.endDate(),
                        row.createdAt(),
                        row.reviewedAt() == null ? "OPEN" : "REVIEWED",
                        row.reviewNote(),
                        row.reviewedByUserKey(),
                        row.reviewedAt()
                ))
                .toList();
        return new AdminPolicyLinkReviewResponse(openCount, recentOpenCount24h, items);
    }

    @Transactional
    public AdminReviewActionResponse markReviewed(Long serviceId, String adminUserKey, String reviewNote) {
        WelfareService policy = welfareServiceRepository.findById(serviceId)
                .orElseThrow(() -> new CustomException(ErrorCode.POLICY_NOT_FOUND));
        String normalizedAdminUserKey = normalizeAdminUserKey(adminUserKey);
        String normalizedReviewNote = normalizeReviewNote(reviewNote);
        PolicyLinkReviewRecord record = reviewRecordRepository.findByPolicyId(serviceId)
                .orElseGet(() -> PolicyLinkReviewRecord.builder()
                        .policy(policy)
                        .reviewedByUserKey(normalizedAdminUserKey)
                        .reviewedAt(LocalDateTime.now())
                        .build());
        record.markReviewed(normalizedReviewNote, normalizedAdminUserKey, LocalDateTime.now());
        PolicyLinkReviewRecord saved = reviewRecordRepository.save(record);
        return new AdminReviewActionResponse(
                saved.getId(),
                "REVIEWED",
                saved.getReviewNote(),
                saved.getReviewedByUserKey(),
                saved.getReviewedAt()
        );
    }

    private String normalizeAdminUserKey(String adminUserKey) {
        if (adminUserKey == null || adminUserKey.isBlank()) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        return adminUserKey.trim();
    }

    private String normalizeReviewNote(String reviewNote) {
        if (reviewNote == null) {
            return null;
        }
        String normalized = reviewNote.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > 1000) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        return normalized;
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }
}
