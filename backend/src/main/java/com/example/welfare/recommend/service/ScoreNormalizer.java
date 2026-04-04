package com.example.welfare.recommend.service;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 1차: 단순 min-max 정규화
 * 2차: p5~p95 클리핑 + min-max (normalization_stats 테이블 의존) — 현재 미구현
 */
@Component
public class ScoreNormalizer {

    public double normalize(double value, double min, double max) {
        if (max == min) return 0.5;
        return (value - min) / (max - min);
    }

    public double findMin(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
    }

    public double findMax(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).max().orElse(1.0);
    }
}
