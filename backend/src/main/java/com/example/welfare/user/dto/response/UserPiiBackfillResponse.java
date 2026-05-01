package com.example.welfare.user.dto.response;

public record UserPiiBackfillResponse(
        int processedCount,
        int updatedUserCount,
        int emailBackfilledCount,
        int nameBackfilledCount,
        int birthDateBackfilledCount,
        int skippedCount
) {
}
