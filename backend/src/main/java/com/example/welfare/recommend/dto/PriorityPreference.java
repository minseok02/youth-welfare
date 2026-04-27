package com.example.welfare.recommend.dto;

public record PriorityPreference(
        int rank,
        String code,
        double weight
) {
}
