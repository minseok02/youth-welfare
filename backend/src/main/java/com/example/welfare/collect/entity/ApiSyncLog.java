package com.example.welfare.collect.entity;

import com.example.welfare.collect.service.CollectResult;
import com.example.welfare.global.entity.BaseTimeEntity;
import com.example.welfare.collect.entity.converter.ApiSyncLogStatusConverter;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "api_sync_logs", indexes = {
        @Index(name = "idx_api_sync_job_started", columnList = "job_name, started_at"),
        @Index(name = "idx_api_sync_status_started", columnList = "status, started_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ApiSyncLog extends BaseTimeEntity {

    private static final int ERROR_CODE_MAX_LENGTH = 50;
    private static final int ERROR_MESSAGE_MAX_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_name", nullable = false, length = 50)
    private String jobName;

    @Convert(converter = ApiSyncLogStatusConverter.class)
    @Column(nullable = false, length = 30)
    private SyncStatus status;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "requested_count", nullable = false)
    private int requestedCount;

    @Column(name = "saved_count", nullable = false)
    private int savedCount;

    @Column(name = "skipped_count", nullable = false)
    private int skippedCount;

    @Column(name = "filtered_count", nullable = false)
    private int filteredCount;

    @Column(name = "failed_count", nullable = false)
    private int failedCount;

    @Column(name = "error_code", length = ERROR_CODE_MAX_LENGTH)
    private String errorCode;

    @Column(name = "error_message", length = ERROR_MESSAGE_MAX_LENGTH)
    private String errorMessage;

    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;

    public static ApiSyncLog start(String jobName) {
        return ApiSyncLog.builder()
                .jobName(jobName)
                .status(SyncStatus.RUNNING)
                .startedAt(LocalDateTime.now())
                .build();
    }

    public void complete(CollectResult result) {
        this.status = result.failedCount() > 0 ? SyncStatus.PARTIAL_SUCCESS : SyncStatus.SUCCESS;
        this.finishedAt = LocalDateTime.now();
        this.requestedCount = result.requestedCount();
        this.savedCount = result.savedCount();
        this.skippedCount = result.skippedCount();
        this.filteredCount = result.filteredCount();
        this.failedCount = result.failedCount();
        this.metadataJson = result.metadataJson();
        this.errorCode = null;
        this.errorMessage = null;
    }

    public void fail(Throwable throwable) {
        this.status = SyncStatus.FAILED;
        this.finishedAt = LocalDateTime.now();
        this.failedCount = Math.max(this.failedCount, 1);
        this.errorCode = truncate(throwable.getClass().getSimpleName(), ERROR_CODE_MAX_LENGTH);
        this.errorMessage = truncate(throwable.getMessage(), ERROR_MESSAGE_MAX_LENGTH);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    public enum SyncStatus {
        RUNNING,
        SUCCESS,
        PARTIAL_SUCCESS,
        FAILED,
        SKIPPED
    }
}
