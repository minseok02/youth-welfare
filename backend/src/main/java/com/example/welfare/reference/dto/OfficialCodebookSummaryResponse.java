package com.example.welfare.reference.dto;

public record OfficialCodebookSummaryResponse(
        String codeSetKey,
        String sourceType,
        String sourceFile,
        String intendedUse,
        int rowCount,
        boolean rowDataIncluded
) {
}
