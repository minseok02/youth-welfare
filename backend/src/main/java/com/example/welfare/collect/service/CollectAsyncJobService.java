package com.example.welfare.collect.service;

import com.example.welfare.collect.dto.AsyncCollectStatusResponse;
import com.example.welfare.collect.entity.ApiSyncLog;
import com.example.welfare.collect.repository.ApiSyncLogRepository;
import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;

@Slf4j
@Service
@RequiredArgsConstructor
public class CollectAsyncJobService {

    private final CollectAdminService collectAdminService;
    private final ApiSyncLogRepository apiSyncLogRepository;
    @Qualifier("collectAsyncExecutor")
    private final Executor collectAsyncExecutor;

    private final Map<CollectSource, JobSnapshot> snapshots = new EnumMap<>(CollectSource.class);

    public AsyncCollectStatusResponse trigger(CollectSource source) {
        if (!source.requiresAdapter()) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        LocalDateTime requestedAt = LocalDateTime.now();
        synchronized (snapshots) {
            if (hasActiveJob()) {
                throw new CustomException(ErrorCode.COLLECT_ALREADY_RUNNING);
            }
            snapshots.put(source, JobSnapshot.queued(requestedAt));
        }

        try {
            collectAsyncExecutor.execute(() -> runCollect(source, requestedAt));
        } catch (RuntimeException e) {
            synchronized (snapshots) {
                snapshots.put(source, JobSnapshot.failed(
                        requestedAt,
                        requestedAt,
                        requestedAt,
                        "ExecutorRejected",
                        safeFailureMessage("ExecutorRejected")
                ));
            }
            throw e;
        }

        return getStatus(source);
    }

    public AsyncCollectStatusResponse getStatus(CollectSource source) {
        JobSnapshot snapshot;
        synchronized (snapshots) {
            snapshot = snapshots.get(source);
        }
        AsyncCollectStatusResponse.LatestCollectLog latestLog = latestCollectLog(source).orElse(null);
        if (snapshot == null) {
            return buildResponse(source, deriveStateFromLatestLog(latestLog), latestLog);
        }
        return buildResponse(source, snapshot, latestLog);
    }

    private void runCollect(CollectSource source, LocalDateTime requestedAt) {
        LocalDateTime startedAt = LocalDateTime.now();
        synchronized (snapshots) {
            snapshots.put(source, JobSnapshot.running(requestedAt, startedAt));
        }

        try {
            collectAdminService.collect(source);
            synchronized (snapshots) {
                snapshots.put(source, JobSnapshot.succeeded(requestedAt, startedAt, LocalDateTime.now()));
            }
        } catch (CustomException e) {
            log.warn("[CollectAsyncJobService] async collect failed source={} errorCode={}",
                    source.jobName(), e.getErrorCode().getCode());
            synchronized (snapshots) {
                snapshots.put(source, JobSnapshot.failed(
                        requestedAt,
                        startedAt,
                        LocalDateTime.now(),
                        e.getErrorCode().getCode(),
                        safeFailureMessage(e.getErrorCode().getCode())
                ));
            }
        } catch (Exception e) {
            String errorType = e.getClass().getSimpleName();
            log.warn("[CollectAsyncJobService] async collect failed source={} errorType={}",
                    source.jobName(), errorType);
            synchronized (snapshots) {
                snapshots.put(source, JobSnapshot.failed(
                        requestedAt,
                        startedAt,
                        LocalDateTime.now(),
                        errorType,
                        safeFailureMessage(errorType)
                ));
            }
        }
    }

    private String safeFailureMessage(String errorCode) {
        return "async collect failed; see application logs with errorCode=" + errorCode;
    }

    private boolean hasActiveJob() {
        return snapshots.values().stream().anyMatch(JobSnapshot::active);
    }

    private Optional<AsyncCollectStatusResponse.LatestCollectLog> latestCollectLog(CollectSource source) {
        return apiSyncLogRepository.findTopByJobNameOrderByIdDesc(source.jobName())
                .map(log -> new AsyncCollectStatusResponse.LatestCollectLog(
                        log.getId(),
                        log.getStatus().name(),
                        log.getRequestedCount(),
                        log.getSavedCount(),
                        log.getSkippedCount(),
                        log.getFilteredCount(),
                        log.getFailedCount(),
                        log.getStartedAt(),
                        log.getFinishedAt(),
                        log.getMetadataJson()
                ));
    }

    private JobSnapshot deriveStateFromLatestLog(AsyncCollectStatusResponse.LatestCollectLog latestLog) {
        if (latestLog == null) {
            return JobSnapshot.idle();
        }
        return switch (latestLog.status()) {
            case "RUNNING" -> JobSnapshot.running(latestLog.startedAt(), latestLog.startedAt());
            case "SUCCESS", "PARTIAL_SUCCESS" -> JobSnapshot.succeeded(
                    latestLog.startedAt(),
                    latestLog.startedAt(),
                    latestLog.finishedAt()
            );
            case "FAILED" -> JobSnapshot.failed(latestLog.startedAt(), latestLog.startedAt(), latestLog.finishedAt(), null, null);
            default -> JobSnapshot.idle();
        };
    }

    private AsyncCollectStatusResponse buildResponse(CollectSource source,
                                                     JobSnapshot snapshot,
                                                     AsyncCollectStatusResponse.LatestCollectLog latestLog) {
        String message = switch (snapshot.state()) {
            case IDLE -> source.triggerLabel() + " 비동기 수집 이력이 없습니다.";
            case QUEUED -> source.triggerLabel() + " 비동기 수집이 대기열에 등록되었습니다.";
            case RUNNING -> source.triggerLabel() + " 비동기 수집이 실행 중입니다.";
            case SUCCEEDED -> source.triggerLabel() + " 비동기 수집이 완료되었습니다.";
            case FAILED -> source.triggerLabel() + " 비동기 수집이 실패했습니다.";
        };
        return new AsyncCollectStatusResponse(
                source.pathKey(),
                source.jobName(),
                snapshot.state(),
                snapshot.active(),
                message,
                snapshot.requestedAt(),
                snapshot.startedAt(),
                snapshot.finishedAt(),
                snapshot.errorCode(),
                snapshot.errorMessage(),
                latestLog
        );
    }

    private record JobSnapshot(
            AsyncCollectStatusResponse.AsyncCollectState state,
            boolean active,
            LocalDateTime requestedAt,
            LocalDateTime startedAt,
            LocalDateTime finishedAt,
            String errorCode,
            String errorMessage
    ) {
        static JobSnapshot idle() {
            return new JobSnapshot(AsyncCollectStatusResponse.AsyncCollectState.IDLE, false, null, null, null, null, null);
        }

        static JobSnapshot queued(LocalDateTime requestedAt) {
            return new JobSnapshot(AsyncCollectStatusResponse.AsyncCollectState.QUEUED, true, requestedAt, null, null, null, null);
        }

        static JobSnapshot running(LocalDateTime requestedAt, LocalDateTime startedAt) {
            return new JobSnapshot(AsyncCollectStatusResponse.AsyncCollectState.RUNNING, true, requestedAt, startedAt, null, null, null);
        }

        static JobSnapshot succeeded(LocalDateTime requestedAt, LocalDateTime startedAt, LocalDateTime finishedAt) {
            return new JobSnapshot(AsyncCollectStatusResponse.AsyncCollectState.SUCCEEDED, false, requestedAt, startedAt, finishedAt, null, null);
        }

        static JobSnapshot failed(LocalDateTime requestedAt,
                                  LocalDateTime startedAt,
                                  LocalDateTime finishedAt,
                                  String errorCode,
                                  String errorMessage) {
            return new JobSnapshot(AsyncCollectStatusResponse.AsyncCollectState.FAILED, false, requestedAt, startedAt, finishedAt, errorCode, errorMessage);
        }
    }
}
