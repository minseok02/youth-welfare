package com.example.welfare.reference.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OfficialCodebookDetailResponse(
        String codeSetKey,
        String sourceType,
        String sourceFile,
        String intendedUse,
        Integer rowCount,
        Integer matchedRowCount,
        String sheetName,
        List<String> headers,
        List<Map<String, String>> rows,
        Map<String, Object> metadata
) {
}
