package com.example.welfare.admin.dashboard.dto;

import java.time.LocalDateTime;
import java.util.List;

public record AdminNotificationStaleTargetResponse(
        int olderThanDays,
        long staleRowCount,
        long staleGroupCount,
        List<Item> recentTargets
) {
    public record Item(
            String kind,
            String title,
            String deeplinkUrl,
            long rowCount,
            long userCount,
            LocalDateTime oldestCreatedAt,
            LocalDateTime newestCreatedAt
    ) {
    }
}
