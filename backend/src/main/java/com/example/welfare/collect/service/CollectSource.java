package com.example.welfare.collect.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.policy.entity.WelfareService;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public enum CollectSource {
    YOUTH("YOUTH", "collect-youth", "youth", "온통청년", "온통청년 수집 완료"),
    BOKJIRO_CENTRAL("BOKJIRO_CENTRAL", "collect-bokjiro-central", "bokjiro-central", "복지로 중앙", "복지로 중앙 수집 완료"),
    BOKJIRO_LOCAL("BOKJIRO_LOCAL", "collect-bokjiro-local", "bokjiro-local", "복지로 지자체", "복지로 지자체 수집 완료"),
    BOKJIRO_DETAIL("BOKJIRO_DETAIL", "collect-bokjiro-details", "bokjiro-details", "복지로 상세", "복지로 상세 수집 완료"),
    BOKJIRO_DETAIL_REFRESH("BOKJIRO_DETAIL_REFRESH", "collect-bokjiro-details-refresh", "bokjiro-details-refresh", "복지로 상세 refresh", "복지로 상세 refresh 완료");

    private static final List<CollectSource> EXECUTION_ORDER = List.of(
            YOUTH,
            BOKJIRO_CENTRAL,
            BOKJIRO_LOCAL,
            BOKJIRO_DETAIL
    );
    private static final Map<String, CollectSource> SOURCES_BY_PATH = buildSourcesByPath();

    private final String jobName;
    private final String lockName;
    private final String pathKey;
    private final String triggerLabel;
    private final String successMessage;

    CollectSource(String jobName,
                  String lockName,
                  String pathKey,
                  String triggerLabel,
                  String successMessage) {
        this.jobName = jobName;
        this.lockName = lockName;
        this.pathKey = pathKey;
        this.triggerLabel = triggerLabel;
        this.successMessage = successMessage;
    }

    public String jobName() {
        return jobName;
    }

    public String lockName() {
        return lockName;
    }

    public String pathKey() {
        return pathKey;
    }

    public String triggerLabel() {
        return triggerLabel;
    }

    public String successMessage() {
        return successMessage;
    }

    public WelfareService.SourceType toWelfareSourceType() {
        return switch (this) {
            case YOUTH -> WelfareService.SourceType.YOUTH;
            case BOKJIRO_CENTRAL -> WelfareService.SourceType.BOKJIRO_CENTRAL;
            case BOKJIRO_LOCAL -> WelfareService.SourceType.BOKJIRO_LOCAL;
            case BOKJIRO_DETAIL, BOKJIRO_DETAIL_REFRESH ->
                    throw new IllegalStateException("detail collect source 는 welfare source type 으로 직접 매핑하지 않습니다. source=" + this);
        };
    }

    public static List<CollectSource> executionOrder() {
        return EXECUTION_ORDER;
    }

    public static CollectSource fromPathKey(String pathKey) {
        CollectSource source = SOURCES_BY_PATH.get(pathKey);
        if (source == null) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        return source;
    }

    private static Map<String, CollectSource> buildSourcesByPath() {
        Map<String, CollectSource> sourceMap = new HashMap<>();
        for (CollectSource source : EnumSet.allOf(CollectSource.class)) {
            CollectSource previous = sourceMap.putIfAbsent(source.pathKey(), source);
            if (previous != null) {
                throw new IllegalStateException("중복 collect source path 가 등록되었습니다. path=" + source.pathKey());
            }
        }
        return Map.copyOf(sourceMap);
    }
}
