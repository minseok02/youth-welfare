package com.example.welfare.chat.repository;

public record ChatPolicyReadCondition(
        String keyword,
        int limit
) {
}
