package com.example.welfare.collect.repository;

import com.example.welfare.collect.entity.ApiSyncLog;

import java.time.LocalDateTime;

public interface ApiSyncLogCommandRepository {

    ApiSyncLog save(ApiSyncLog syncLog);

    int failStaleRunningLogs(String jobName,
                             LocalDateTime finishedAt,
                             String errorCode,
                             String errorMessage);
}
