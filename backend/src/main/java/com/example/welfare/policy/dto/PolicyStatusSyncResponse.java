package com.example.welfare.policy.dto;

import com.example.welfare.collect.service.StatusUpdateService;

public record PolicyStatusSyncResponse(
        int closedCount,
        int activatedCount,
        boolean clusterAiCacheCleanupExecuted
) {
    public static PolicyStatusSyncResponse from(StatusUpdateService.StatusSyncResult result) {
        return new PolicyStatusSyncResponse(
                result.closedCount(),
                result.activatedCount(),
                result.clusterAiCacheCleanupExecuted()
        );
    }
}
