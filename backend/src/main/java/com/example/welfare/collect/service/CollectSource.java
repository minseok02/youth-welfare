package com.example.welfare.collect.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.policy.entity.WelfareService;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public enum CollectSource {
    YOUTH("YOUTH", "collect-youth", "youth", "온통청년", "온통청년 수집 완료", true, true),
    BOKJIRO_CENTRAL("BOKJIRO_CENTRAL", "collect-bokjiro-central", "bokjiro-central", "복지로 중앙", "복지로 중앙 수집 완료", true, true),
    BOKJIRO_LOCAL("BOKJIRO_LOCAL", "collect-bokjiro-local", "bokjiro-local", "복지로 지자체", "복지로 지자체 수집 완료", true, true),
    GOV24("GOV24", "collect-gov24", "gov24", "정부24", "정부24 수집 완료", true, true),
    GOV24_DETAIL("GOV24_DETAIL", "collect-gov24-details", "gov24-details", "정부24 상세", "정부24 상세 수집 완료", false, true),
    GOV24_SUPPORT_CONDITIONS("GOV24_SUPPORT_CONDITIONS", "collect-gov24-support-conditions", "gov24-support-conditions", "정부24 지원조건", "정부24 지원조건 수집 완료", false, true),
    BOKJIRO_DETAIL("BOKJIRO_DETAIL", "collect-bokjiro-details", "bokjiro-details", "복지로 상세", "복지로 상세 수집 완료", false, true),
    BOKJIRO_DETAIL_GAP_FILL("BOKJIRO_DETAIL_GAP_FILL", "collect-bokjiro-details-gap-fill", "bokjiro-details-gap-fill", "복지로 상세 누락 보강", "복지로 상세 누락 보강 완료", false, false),
    BOKJIRO_DETAIL_REFRESH("BOKJIRO_DETAIL_REFRESH", "collect-bokjiro-details-refresh", "bokjiro-details-refresh", "복지로 상세 재수집", "복지로 상세 재수집 완료", false, true);

    private static final List<CollectSource> EXECUTION_ORDER = Arrays.stream(values())
            .filter(CollectSource::runsInScheduledBatch)
            .toList();
    private static final Map<String, CollectSource> SOURCES_BY_PATH = buildSourcesByPath();

    private final String jobName;
    private final String lockName;
    private final String pathKey;
    private final String triggerLabel;
    private final String successMessage;
    private final boolean runsInScheduledBatch;
    private final boolean requiresAdapter;

    CollectSource(String jobName,
                  String lockName,
                  String pathKey,
                  String triggerLabel,
                  String successMessage,
                  boolean runsInScheduledBatch,
                  boolean requiresAdapter) {
        this.jobName = jobName;
        this.lockName = lockName;
        this.pathKey = pathKey;
        this.triggerLabel = triggerLabel;
        this.successMessage = successMessage;
        this.runsInScheduledBatch = runsInScheduledBatch;
        this.requiresAdapter = requiresAdapter;
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

    public boolean runsInScheduledBatch() {
        return runsInScheduledBatch;
    }

    public boolean requiresAdapter() {
        return requiresAdapter;
    }

    public boolean isListSource() {
        return switch (this) {
            case YOUTH, BOKJIRO_CENTRAL, BOKJIRO_LOCAL, GOV24 -> true;
            case GOV24_DETAIL, GOV24_SUPPORT_CONDITIONS, BOKJIRO_DETAIL, BOKJIRO_DETAIL_GAP_FILL, BOKJIRO_DETAIL_REFRESH -> false;
        };
    }

    public WelfareService.SourceType toWelfareSourceType() {
        return switch (this) {
            case YOUTH -> WelfareService.SourceType.YOUTH;
            case BOKJIRO_CENTRAL -> WelfareService.SourceType.BOKJIRO_CENTRAL;
            case BOKJIRO_LOCAL -> WelfareService.SourceType.BOKJIRO_LOCAL;
            case GOV24 -> WelfareService.SourceType.GOV24;
            case GOV24_DETAIL, GOV24_SUPPORT_CONDITIONS, BOKJIRO_DETAIL, BOKJIRO_DETAIL_GAP_FILL, BOKJIRO_DETAIL_REFRESH ->
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
