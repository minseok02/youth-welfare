package com.example.welfare.support.dto;

import com.example.welfare.support.entity.SupportInquiry;

import java.time.LocalDateTime;

public record SupportInquiryResponse(
        Long id,
        String categoryCode,
        String categoryLabel,
        String contactEmail,
        String status,
        LocalDateTime createdAt
) {
    public static SupportInquiryResponse from(SupportInquiry inquiry) {
        return new SupportInquiryResponse(
                inquiry.getId(),
                inquiry.getCategory().name(),
                inquiry.getCategory().getLabel(),
                inquiry.getContactEmail(),
                inquiry.getStatus().name(),
                inquiry.getCreatedAt()
        );
    }
}
