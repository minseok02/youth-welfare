package com.example.welfare.collect.repository;

import com.example.welfare.collect.entity.ApiSyncLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApiSyncLogRepository extends JpaRepository<ApiSyncLog, Long> {
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE api_sync_logs
            SET status = 'failed',
                finished_at = NOW(),
                failed_count = GREATEST(failed_count, 1),
                error_code = 'InterruptedRun',
                error_message = :reason
            WHERE status = 'running'
            """, nativeQuery = true)
    int markRunningLogsAsFailed(@Param("reason") String reason);
}
