package com.example.welfare.support.dto;

import com.example.welfare.support.entity.SupportInquiry;

import java.time.LocalDateTime;

/**
 * 로그인 사용자가 자신이 제출한 문의 내역과 운영 답변을 조회할 때 쓰는 응답.
 * {@code reviewNote}가 운영자가 작성한 답변이며, {@code status}가 OPEN이면 아직 답변 전이다.
 */
public record MySupportInquiryResponse(
        Long id,
        String categoryCode,
        String categoryLabel,
        String message,
        String contactEmail,
        String routePath,
        String status,
        String reviewNote,
        LocalDateTime createdAt,
        LocalDateTime reviewedAt
) {
    public static MySupportInquiryResponse from(SupportInquiry inquiry) {
        return new MySupportInquiryResponse(
                inquiry.getId(),
                inquiry.getCategory().name(),
                inquiry.getCategory().getLabel(),
                inquiry.getMessage(),
                inquiry.getContactEmail(),
                inquiry.getRoutePath(),
                inquiry.getStatus().name(),
                inquiry.getReviewNote(),
                inquiry.getCreatedAt(),
                inquiry.getReviewedAt()
        );
    }
}
