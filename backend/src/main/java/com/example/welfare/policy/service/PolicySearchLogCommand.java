package com.example.welfare.policy.service;

import lombok.Builder;

@Builder
public record PolicySearchLogCommand(
        Long userId,
        String clientFingerprint,
        String keyword,
        long resultCount,
        String status,
        Boolean includeClosed,
        String category,
        String sourceType,
        Boolean onlineApply,
        String sido,
        String sgg,
        String sort,
        int page,
        int size
) {
}
