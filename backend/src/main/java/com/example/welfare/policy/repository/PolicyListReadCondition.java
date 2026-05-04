package com.example.welfare.policy.repository;

import com.example.welfare.policy.entity.WelfareService;

public record PolicyListReadCondition(
        String category,
        WelfareService.SourceType sourceType,
        WelfareService.ServiceStatus status,
        // ACTIVE_ONLY / EXPIRED_ONLY / ALL (상세 의미는 WelfareServiceRepository 주석 참조)
        String statusFilter,
        String sido,
        String sgg,
        Boolean onlineApply,
        // LATEST / VIEWS / NAME
        String sort
) {
}
