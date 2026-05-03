package com.example.welfare.policy.repository;

import com.example.welfare.policy.entity.WelfareService;

public record PolicyListReadCondition(
        String category,
        WelfareService.SourceType sourceType,
        WelfareService.ServiceStatus status,
        boolean includeClosed,
        String sido,
        String sgg,
        Boolean onlineApply
) {
}
