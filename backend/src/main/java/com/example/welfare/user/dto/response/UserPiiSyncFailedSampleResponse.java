package com.example.welfare.user.dto.response;

import com.example.welfare.user.entity.UserPiiSyncQueue;

import java.time.LocalDateTime;

public record UserPiiSyncFailedSampleResponse(
        String userKey,
        int attemptCount,
        LocalDateTime lastAttemptAt,
        String lastError
) {
    public static UserPiiSyncFailedSampleResponse from(UserPiiSyncQueue queue) {
        return new UserPiiSyncFailedSampleResponse(
                queue.getUserKey(),
                queue.getAttemptCount(),
                queue.getLastAttemptAt(),
                queue.getLastError()
        );
    }
}
