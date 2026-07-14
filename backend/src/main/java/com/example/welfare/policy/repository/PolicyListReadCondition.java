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
        // LATEST / VIEWS / DEADLINE / NAME
        String sort,
        // 소득분위 → 변환된 연소득 상한 (만원 단위), null이면 미선택
        Integer incomeMaxWon,
        // 특화조건 태그값 (장애인 / 한부모·조손 / 다문화·탈북민 / 보훈대상자 / 다자녀), null이면 미선택
        String targetGroup,
        // Gov24 exact-label 서비스분야 filter, null이면 미선택
        String gov24ServiceField,
        // Gov24 additive 사용자구분 token filter, null이면 미선택
        String gov24UserType,
        // Gov24 additive 지원유형 token filter, null이면 미선택
        String gov24BenefitType
) {
}
