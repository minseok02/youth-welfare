package com.example.welfare.policy.repository;

public record PolicySearchReadCondition(
        String keyword,
        String status,
        // ACTIVE_ONLY / EXPIRED_ONLY / ALL (상세 의미는 WelfareServiceRepository 주석 참조)
        String statusFilter,
        String category,
        String sourceType,
        Integer onlineApply,
        String sido,
        String sgg,
        String sort
) {
}
