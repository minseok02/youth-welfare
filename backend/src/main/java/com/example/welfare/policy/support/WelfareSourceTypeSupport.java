package com.example.welfare.policy.support;

import com.example.welfare.policy.entity.WelfareService;

import java.util.Map;

/**
 * source type 문자열 정규화와 대표 source system 매핑을 한 곳에 모은다.
 */
public final class WelfareSourceTypeSupport {

    private static final Map<String, WelfareService.SourceType> SOURCE_TYPES = Map.of(
            "YOUTH", WelfareService.SourceType.YOUTH,
            "BOKJIRO_CENTRAL", WelfareService.SourceType.BOKJIRO_CENTRAL,
            "BOKJIRO_LOCAL", WelfareService.SourceType.BOKJIRO_LOCAL,
            "GOV24", WelfareService.SourceType.GOV24
    );
    private static final Map<String, String> PRIMARY_SOURCE_SYSTEMS = Map.of(
            "YOUTH", "YOUTH",
            "BOKJIRO_CENTRAL", "BOKJIRO",
            "BOKJIRO_LOCAL", "BOKJIRO",
            "GOV24", "GOV24"
    );

    private WelfareSourceTypeSupport() {
    }

    public static WelfareService.SourceType parseNullable(String rawSourceType) {
        String normalized = normalizeInput(rawSourceType);
        if (normalized == null) {
            return null;
        }
        WelfareService.SourceType sourceType = SOURCE_TYPES.get(normalized);
        if (sourceType == null) {
            throw new IllegalArgumentException("지원하지 않는 sourceType 입니다: " + rawSourceType);
        }
        return sourceType;
    }

    public static String normalizeNullable(String rawSourceType) {
        WelfareService.SourceType sourceType = parseNullable(rawSourceType);
        return sourceType == null ? null : sourceType.name();
    }

    public static String primarySourceSystem(Enum<?> sourceType) {
        if (sourceType == null) {
            return null;
        }
        String primarySourceSystem = PRIMARY_SOURCE_SYSTEMS.get(sourceType.name());
        if (primarySourceSystem == null) {
            throw new IllegalArgumentException("지원하지 않는 sourceType 입니다: " + sourceType.name());
        }
        return primarySourceSystem;
    }

    private static String normalizeInput(String rawSourceType) {
        if (rawSourceType == null || rawSourceType.isBlank()) {
            return null;
        }
        return rawSourceType.trim().toUpperCase();
    }
}
