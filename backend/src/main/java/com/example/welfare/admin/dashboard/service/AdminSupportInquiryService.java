package com.example.welfare.admin.dashboard.service;

import com.example.welfare.admin.dashboard.dto.AdminQueueStatusFilter;
import com.example.welfare.admin.dashboard.dto.AdminSupportInquiryResponse;
import com.example.welfare.admin.dashboard.dto.AdminReviewActionResponse;
import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.support.entity.SupportInquiry;
import com.example.welfare.support.repository.SupportInquiryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminSupportInquiryService {

    private final SupportInquiryRepository supportInquiryRepository;

    @Transactional(readOnly = true)
    public AdminSupportInquiryResponse getRecentInquiries(Integer requestedLimit) {
        return getRecentInquiries(requestedLimit, AdminQueueStatusFilter.OPEN);
    }

    @Transactional(readOnly = true)
    public AdminSupportInquiryResponse getRecentInquiries(Integer requestedLimit, AdminQueueStatusFilter statusFilter) {
        int limit = requestedLimit == null ? 10 : Math.max(1, Math.min(requestedLimit, 20));
        long openCount = supportInquiryRepository.countByStatus(SupportInquiry.Status.OPEN);
        long recentOpenCount24h = supportInquiryRepository.countByStatusAndCreatedAtAfter(
                SupportInquiry.Status.OPEN,
                LocalDateTime.now().minusHours(24)
        );
        List<AdminSupportInquiryResponse.Item> items = selectInquiries(statusFilter, limit)
                .stream()
                .map(inquiry -> new AdminSupportInquiryResponse.Item(
                        inquiry.getId(),
                        inquiry.getCategory().name(),
                        inquiry.getCategory().getLabel(),
                        inquiry.getContactEmail(),
                        inquiry.getMessage(),
                        inquiry.getRoutePath(),
                        inquiry.getUserKey(),
                        inquiry.getCreatedAt(),
                        inquiry.getStatus().name(),
                        inquiry.getReviewNote(),
                        inquiry.getReviewedByUserKey(),
                        inquiry.getReviewedAt()
                ))
                .toList();
        return new AdminSupportInquiryResponse(openCount, recentOpenCount24h, items);
    }

    private List<SupportInquiry> selectInquiries(AdminQueueStatusFilter statusFilter, int limit) {
        PageRequest pageRequest = PageRequest.of(0, limit);
        return switch (statusFilter) {
            case OPEN -> supportInquiryRepository.findByStatusOrderByCreatedAtDesc(
                    SupportInquiry.Status.OPEN,
                    pageRequest
            );
            case REVIEWED -> supportInquiryRepository.findByStatusOrderByCreatedAtDesc(
                    SupportInquiry.Status.REVIEWED,
                    pageRequest
            );
            case ALL -> supportInquiryRepository.findAllByOrderByCreatedAtDesc(pageRequest);
        };
    }

    @Transactional
    public AdminReviewActionResponse markReviewed(Long inquiryId, String adminUserKey, String reviewNote) {
        SupportInquiry inquiry = supportInquiryRepository.findById(inquiryId)
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_INPUT));
        inquiry.markReviewed(normalizeReviewNote(reviewNote), normalizeAdminUserKey(adminUserKey), LocalDateTime.now());
        return new AdminReviewActionResponse(
                inquiry.getId(),
                inquiry.getStatus().name(),
                inquiry.getReviewNote(),
                inquiry.getReviewedByUserKey(),
                inquiry.getReviewedAt()
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
