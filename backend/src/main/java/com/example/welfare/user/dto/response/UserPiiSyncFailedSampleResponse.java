package com.example.welfare.user.dto.response;

import com.example.welfare.global.util.RedisKeyHash;
import com.example.welfare.user.entity.UserPiiSyncQueue;

import java.time.LocalDateTime;

public record UserPiiSyncFailedSampleResponse(
        String userKeyHash,
        int attemptCount,
        LocalDateTime lastAttemptAt,
        String lastError
) {
    public static UserPiiSyncFailedSampleResponse from(UserPiiSyncQueue queue) {
        return new UserPiiSyncFailedSampleResponse(
                RedisKeyHash.sha256Hex(queue.getUserKey()),
                queue.getAttemptCount(),
                queue.getLastAttemptAt(),
                queue.getLastError()
        );
    }
}
