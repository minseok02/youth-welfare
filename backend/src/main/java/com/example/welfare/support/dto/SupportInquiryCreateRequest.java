package com.example.welfare.support.dto;

import com.example.welfare.support.entity.SupportInquiry;

public record SupportInquiryCreateRequest(
        SupportInquiry.Category category,
        String contactEmail,
        String message,
        String routePath
) {
}
