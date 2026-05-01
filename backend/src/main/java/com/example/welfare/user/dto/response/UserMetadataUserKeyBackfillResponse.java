package com.example.welfare.user.dto.response;

public record UserMetadataUserKeyBackfillResponse(
        int processedCount,
        int updatedRowCount,
        int attributeUpdatedCount,
        int priorityUpdatedCount
) {
}
