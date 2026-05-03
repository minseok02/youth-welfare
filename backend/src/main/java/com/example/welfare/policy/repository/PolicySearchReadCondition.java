package com.example.welfare.policy.repository;

public record PolicySearchReadCondition(
        String keyword,
        String status,
        Integer includeClosed,
        String category,
        String sourceType,
        Integer onlineApply,
        String sido,
        String sgg,
        String sort
) {
}
