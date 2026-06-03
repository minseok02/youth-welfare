package com.example.welfare.admin.dashboard.dto;

import java.time.LocalDateTime;

public record AdminUserProfileStandardCodeCoverageResponse(
        LocalDateTime generatedAt,
        long totalUsers,
        long usersWithProfileRow,
        long usersWithoutProfileRow,
        long usersWithAnyStandardCode,
        long usersWithAllStandardCodes,
        long usersMissingAllStandardCodes,
        long houseTenureFilled,
        long housingTypeFilled,
        long basicLivingRecipientTypeFilled,
        long disabilityGradeFilled,
        long profilesWithAnyStandardCode,
        long profilesWithAllStandardCodes,
        long profilesMissingAllStandardCodes,
        long profileOnlyGapRows,
        long userOnlyGapRows,
        long safeReconcileCandidateRows,
        long conflictingValueGapRows
) {
}
