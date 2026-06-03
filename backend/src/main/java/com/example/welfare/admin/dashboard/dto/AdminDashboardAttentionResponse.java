package com.example.welfare.admin.dashboard.dto;

import java.time.LocalDateTime;
import java.util.List;

public record AdminDashboardAttentionResponse(
        LocalDateTime generatedAt,
        int itemCount,
        List<AttentionItem> items
) {
    public record AttentionItem(
            String key,
            String severity,
            String title,
            String message,
            String targetId,
            String source
    ) {
    }
}
