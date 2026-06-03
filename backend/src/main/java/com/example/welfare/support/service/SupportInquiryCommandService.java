package com.example.welfare.support.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.support.dto.SupportInquiryCreateRequest;
import com.example.welfare.support.dto.SupportInquiryResponse;
import com.example.welfare.support.entity.SupportInquiry;
import com.example.welfare.support.repository.SupportInquiryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SupportInquiryCommandService {

    private final SupportInquiryRepository supportInquiryRepository;

    @Transactional
    public SupportInquiryResponse submit(Long userId, String userKey, SupportInquiryCreateRequest request) {
        if (request == null || request.category() == null) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }

        String contactEmail = normalizeEmail(request.contactEmail());
        String message = normalizeMessage(request.message());
        String routePath = normalizeRoutePath(request.routePath());

        SupportInquiry inquiry = supportInquiryRepository.save(SupportInquiry.builder()
                .userId(userId)
                .userKey(normalizeUserKey(userKey))
                .contactEmail(contactEmail)
                .category(request.category())
                .message(message)
                .routePath(routePath)
                .status(SupportInquiry.Status.OPEN)
                .build());

        return SupportInquiryResponse.from(inquiry);
    }

    private String normalizeUserKey(String userKey) {
        if (userKey == null) {
            return null;
        }
        String normalized = userKey.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizeEmail(String email) {
        if (email == null) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        String normalized = email.trim();
        if (normalized.isEmpty() || normalized.length() > 320 || !normalized.contains("@")) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        return normalized;
    }

    private String normalizeMessage(String message) {
        if (message == null) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        String normalized = message.trim();
        if (normalized.isEmpty() || normalized.length() > 2000) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        return normalized;
    }

    private String normalizeRoutePath(String routePath) {
        if (routePath == null) {
            return null;
        }
        String normalized = routePath.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > 255) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        return normalized;
    }
}
