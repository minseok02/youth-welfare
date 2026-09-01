package com.example.welfare.admin.dashboard.service;

import com.example.welfare.admin.dashboard.dto.AdminPolicyErrorReportResponse;
import com.example.welfare.admin.dashboard.dto.AdminQueueStatusFilter;
import com.example.welfare.admin.dashboard.dto.AdminReviewActionResponse;
import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.global.util.RedisKeyHash;
import com.example.welfare.policy.entity.PolicyErrorReport;
import com.example.welfare.policy.repository.PolicyErrorReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminPolicyErrorReportService {

    private final PolicyErrorReportRepository policyErrorReportRepository;

    @Transactional(readOnly = true)
    public AdminPolicyErrorReportResponse getRecentReports(Integer requestedLimit) {
        return getRecentReports(requestedLimit, AdminQueueStatusFilter.OPEN);
    }

    @Transactional(readOnly = true)
    public AdminPolicyErrorReportResponse getRecentReports(Integer requestedLimit, AdminQueueStatusFilter statusFilter) {
        int limit = requestedLimit == null ? 10 : Math.max(1, Math.min(requestedLimit, 20));
        long openCount = policyErrorReportRepository.countByStatus(PolicyErrorReport.Status.OPEN);
        long recentOpenCount24h = policyErrorReportRepository.countByStatusAndCreatedAtAfter(
                PolicyErrorReport.Status.OPEN,
                LocalDateTime.now().minusHours(24)
        );
        List<AdminPolicyErrorReportResponse.Item> items = selectReports(statusFilter, limit)
                .stream()
                .map(report -> new AdminPolicyErrorReportResponse.Item(
                        report.getId(),
                        report.getPolicy().getId(),
                        report.getPolicy().getTitle(),
                        report.getPolicy().getSourceType().name(),
                        report.getPolicy().getSourceId(),
                        report.getReasonCode().name(),
                        report.getReasonCode().getLabel(),
                        report.getNote(),
                        hashNullable(report.getUserKey()),
                        report.getCreatedAt(),
                        report.getStatus().name(),
                        report.getReviewNote(),
                        report.getReviewedByUserKey(),
                        report.getReviewedAt()
                ))
                .toList();
        return new AdminPolicyErrorReportResponse(openCount, recentOpenCount24h, items);
    }

    private String hashNullable(String value) {
        return value == null || value.isBlank() ? null : RedisKeyHash.sha256Hex(value);
    }

    private List<PolicyErrorReport> selectReports(AdminQueueStatusFilter statusFilter, int limit) {
        PageRequest pageRequest = PageRequest.of(0, limit);
        return switch (statusFilter) {
            case OPEN -> policyErrorReportRepository.findByStatusOrderByCreatedAtDesc(
                    PolicyErrorReport.Status.OPEN,
                    pageRequest
            );
            case REVIEWED -> policyErrorReportRepository.findByStatusOrderByCreatedAtDesc(
                    PolicyErrorReport.Status.REVIEWED,
                    pageRequest
            );
            case ALL -> policyErrorReportRepository.findAllByOrderByCreatedAtDesc(pageRequest);
        };
    }

    @Transactional
    public AdminReviewActionResponse markReviewed(Long reportId, String adminUserKey, String reviewNote) {
        PolicyErrorReport report = policyErrorReportRepository.findById(reportId)
                .orElseThrow(() -> new CustomException(ErrorCode.POLICY_NOT_FOUND));
        report.markReviewed(normalizeReviewNote(reviewNote), normalizeAdminUserKey(adminUserKey), LocalDateTime.now());
        return new AdminReviewActionResponse(
                report.getId(),
                report.getStatus().name(),
                report.getReviewNote(),
                report.getReviewedByUserKey(),
                report.getReviewedAt()
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
}
