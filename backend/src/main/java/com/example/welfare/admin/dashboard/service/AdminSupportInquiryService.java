package com.example.welfare.admin.dashboard.service;

import com.example.welfare.admin.dashboard.dto.AdminQueueStatusFilter;
import com.example.welfare.admin.dashboard.dto.AdminSupportInquiryResponse;
import com.example.welfare.admin.dashboard.dto.AdminReviewActionResponse;
import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.notification.gateway.EmailClient;
import com.example.welfare.support.entity.SupportInquiry;
import com.example.welfare.support.repository.SupportInquiryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminSupportInquiryService {

    private static final String REPLY_EMAIL_SUBJECT = "[청년복지] 문의하신 내용에 답변드립니다";

    private final SupportInquiryRepository supportInquiryRepository;
    private final EmailClient emailClient;

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

        String answer = inquiry.getReviewNote();
        if (answer != null && !answer.isBlank()) {
            sendReplyEmail(inquiry, answer);
        }

        return new AdminReviewActionResponse(
                inquiry.getId(),
                inquiry.getStatus().name(),
                inquiry.getReviewNote(),
                inquiry.getReviewedByUserKey(),
                inquiry.getReviewedAt()
        );
    }

    /**
     * 답변(reviewNote)이 등록되면 문의자가 입력한 contactEmail로 답변을 발송한다.
     * 발송 실패는 로깅만 하고 검토 처리 자체는 성공으로 둔다. (EmailClient가 예외를 삼키고 boolean을 반환)
     */
    private void sendReplyEmail(SupportInquiry inquiry, String answer) {
        String body = """
                안녕하세요, 청년복지플랫폼입니다.
                보내주신 문의에 답변드립니다.

                [문의 유형] %s

                [문의 내용]
                %s

                [답변]
                %s

                추가로 궁금한 점이 있으면 서비스 내 '서비스 문의'로 다시 보내주세요.
                """.formatted(inquiry.getCategory().getLabel(), inquiry.getMessage(), answer);
        boolean sent = emailClient.send(inquiry.getContactEmail(), REPLY_EMAIL_SUBJECT, body);
        if (!sent) {
            log.warn("[AdminSupportInquiry] 답변 이메일 발송 실패 inquiryId={}", inquiry.getId());
        }
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
