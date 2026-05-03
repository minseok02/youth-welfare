package com.example.welfare.user.service;

import com.example.welfare.user.dto.response.UserPiiSyncReplayResponse;
import com.example.welfare.user.entity.UserPiiSyncQueue;
import com.example.welfare.user.entity.UserPiiSyncQueueStatus;
import com.example.welfare.user.repository.UserPiiSyncQueueRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserPiiSyncReplayService {

    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 1000;

    private final UserPiiSyncQueueService userPiiSyncQueueService;
    private final UserPiiSyncQueueRepository userPiiSyncQueueRepository;
    private final UserPiiSyncProcessor userPiiSyncProcessor;

    public UserPiiSyncReplayResponse replay(String userKey, int limit) {
        if (StringUtils.hasText(userKey)) {
            return replaySingle(userKey.trim());
        }
        return replayBulk(limit);
    }

    private UserPiiSyncReplayResponse replaySingle(String userKey) {
        if (!userPiiSyncQueueService.exists(userKey)) {
            log.warn("[UserPiiSyncReplayService] queue row missing for manual replay userKey={}", userKey);
            return new UserPiiSyncReplayResponse(0, 0, 0, 1);
        }

        UserPiiSyncQueueStatus status = userPiiSyncProcessor.process(userKey);
        UserPiiSyncReplayResponse response = summarize(List.of(status));
        log.info("[UserPiiSyncReplayService] single replay complete userKey={} attempted={} synced={} failed={} missing={}",
                userKey,
                response.attemptedCount(),
                response.syncedCount(),
                response.failedCount(),
                response.missingCount());
        return response;
    }

    private UserPiiSyncReplayResponse replayBulk(int rawLimit) {
        int limit = normalizeLimit(rawLimit);
        List<String> userKeys = new ArrayList<>();
        userKeys.addAll(userPiiSyncQueueRepository.findByStatusOrderByLastAttemptAtAscIdAsc(
                        UserPiiSyncQueueStatus.FAILED,
                        PageRequest.of(0, limit))
                .stream()
                .map(UserPiiSyncQueue::getUserKey)
                .toList());

        if (userKeys.size() < limit) {
            userKeys.addAll(userPiiSyncQueueRepository.findByStatusOrderByLastEnqueuedAtAscIdAsc(
                            UserPiiSyncQueueStatus.PENDING,
                            PageRequest.of(0, limit - userKeys.size()))
                    .stream()
                    .map(UserPiiSyncQueue::getUserKey)
                    .toList());
        }

        List<UserPiiSyncQueueStatus> results = userKeys.stream()
                .map(userPiiSyncProcessor::process)
                .toList();
        UserPiiSyncReplayResponse response = summarize(results);
        log.info("[UserPiiSyncReplayService] bulk replay complete limit={} attempted={} synced={} failed={} missing={}",
                limit,
                response.attemptedCount(),
                response.syncedCount(),
                response.failedCount(),
                response.missingCount());
        return response;
    }

    private UserPiiSyncReplayResponse summarize(List<UserPiiSyncQueueStatus> results) {
        int attemptedCount = results.size();
        int syncedCount = 0;
        int failedCount = 0;
        int missingCount = 0;

        for (UserPiiSyncQueueStatus status : results) {
            if (status == UserPiiSyncQueueStatus.SYNCED) {
                syncedCount++;
            } else if (status == UserPiiSyncQueueStatus.FAILED) {
                failedCount++;
            } else {
                missingCount++;
            }
        }

        return new UserPiiSyncReplayResponse(attemptedCount, syncedCount, failedCount, missingCount);
    }

    private int normalizeLimit(int rawLimit) {
        if (rawLimit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(rawLimit, MAX_LIMIT);
    }
}
