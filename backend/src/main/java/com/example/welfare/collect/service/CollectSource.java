package com.example.welfare.collect.service;

import java.util.List;

public enum CollectSource {
    YOUTH("YOUTH", "collect-youth"),
    BOKJIRO_CENTRAL("BOKJIRO_CENTRAL", "collect-bokjiro-central"),
    BOKJIRO_LOCAL("BOKJIRO_LOCAL", "collect-bokjiro-local"),
    BOKJIRO_DETAIL("BOKJIRO_DETAIL", "collect-bokjiro-details");

    private static final List<CollectSource> EXECUTION_ORDER = List.of(
            YOUTH,
            BOKJIRO_CENTRAL,
            BOKJIRO_LOCAL,
            BOKJIRO_DETAIL
    );

    private final String jobName;
    private final String lockName;

    CollectSource(String jobName, String lockName) {
        this.jobName = jobName;
        this.lockName = lockName;
    }

    public String jobName() {
        return jobName;
    }

    public String lockName() {
        return lockName;
    }

    public static List<CollectSource> executionOrder() {
        return EXECUTION_ORDER;
    }
}
