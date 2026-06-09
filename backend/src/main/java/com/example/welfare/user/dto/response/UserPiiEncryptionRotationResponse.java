package com.example.welfare.user.dto.response;

public record UserPiiEncryptionRotationResponse(
        int userPiiProcessedCount,
        int userPiiUpdatedCount,
        int queueProcessedCount,
        int queueUpdatedCount,
        int skippedCount,
        int failedCount
) {
}
