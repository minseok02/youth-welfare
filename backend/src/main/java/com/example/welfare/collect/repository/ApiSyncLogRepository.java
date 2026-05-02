package com.example.welfare.collect.repository;

import com.example.welfare.collect.entity.ApiSyncLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface ApiSyncLogRepository extends JpaRepository<ApiSyncLog, Long> {

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update ApiSyncLog log
               set log.status = com.example.welfare.collect.entity.ApiSyncLog$SyncStatus.FAILED,
                   log.finishedAt = :finishedAt,
                   log.failedCount = case when log.failedCount < 1 then 1 else log.failedCount end,
                   log.errorCode = :errorCode,
                   log.errorMessage = :errorMessage
             where log.jobName = :jobName
               and log.status = com.example.welfare.collect.entity.ApiSyncLog$SyncStatus.RUNNING
            """)
    int failStaleRunningLogs(@Param("jobName") String jobName,
                             @Param("finishedAt") LocalDateTime finishedAt,
                             @Param("errorCode") String errorCode,
                             @Param("errorMessage") String errorMessage);
}
