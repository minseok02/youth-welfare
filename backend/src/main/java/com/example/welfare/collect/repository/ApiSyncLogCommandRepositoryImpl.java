package com.example.welfare.collect.repository;

import com.example.welfare.collect.entity.ApiSyncLog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
@RequiredArgsConstructor
public class ApiSyncLogCommandRepositoryImpl implements ApiSyncLogCommandRepository {

    private final ApiSyncLogRepository apiSyncLogRepository;

    @Override
    public ApiSyncLog save(ApiSyncLog syncLog) {
        return apiSyncLogRepository.save(syncLog);
    }

    @Override
    public int failStaleRunningLogs(String jobName,
                                    LocalDateTime finishedAt,
                                    String errorCode,
                                    String errorMessage) {
        return apiSyncLogRepository.failStaleRunningLogs(
                jobName,
                finishedAt,
                errorCode,
                errorMessage
        );
    }
}
