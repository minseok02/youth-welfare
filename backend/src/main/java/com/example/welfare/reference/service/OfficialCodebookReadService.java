package com.example.welfare.reference.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.reference.dto.OfficialCodebookDetailResponse;
import com.example.welfare.reference.dto.OfficialCodebookSummaryResponse;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class OfficialCodebookReadService {

    private static final int MAX_LIMIT = 500;

    private final List<OfficialCodebookSummaryResponse> summaries;
    private final Map<String, SmallCodebook> smallCodebooks;
    private final Map<String, LargeCodebook> largeCodebooks;
    private final Map<String, LinkedHashSet<String>> codeValuesByCodeSet;

    public OfficialCodebookReadService(ObjectMapper objectMapper) {
        Payload payload = loadPayload(objectMapper);
        this.summaries = new ArrayList<>();
        this.smallCodebooks = new LinkedHashMap<>();
        this.largeCodebooks = new LinkedHashMap<>();
        this.codeValuesByCodeSet = new LinkedHashMap<>();

        for (SmallCodebook codebook : payload.smallCodebooks()) {
            smallCodebooks.put(codebook.codeSetKey(), codebook);
            codeValuesByCodeSet.put(codebook.codeSetKey(), extractCodeValues(codebook));
            summaries.add(new OfficialCodebookSummaryResponse(
                    codebook.codeSetKey(),
                    codebook.sourceType(),
                    codebook.sourceFile(),
                    codebook.intendedUse(),
                    codebook.rowCount(),
                    true
            ));
        }
        for (LargeCodebook codebook : payload.largeCodebooks()) {
            largeCodebooks.put(codebook.codeSetKey(), codebook);
            summaries.add(new OfficialCodebookSummaryResponse(
                    codebook.codeSetKey(),
                    codebook.sourceType(),
                    codebook.sourceFile(),
                    codebook.intendedUse(),
                    codebook.rowCount(),
                    false
            ));
        }
    }

    public List<OfficialCodebookSummaryResponse> listCodebooks() {
        return List.copyOf(summaries);
    }

    public boolean containsCode(String codeSetKey, String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        LinkedHashSet<String> codes = codeValuesByCodeSet.get(codeSetKey);
        return codes != null && codes.contains(code.trim());
    }

    public OfficialCodebookDetailResponse getCodebook(String codeSetKey, String query, Integer limit) {
        SmallCodebook small = smallCodebooks.get(codeSetKey);
        if (small != null) {
            List<Map<String, String>> matchedRows = filterRows(small.rows(), query);
            int effectiveLimit = resolveLimit(limit, matchedRows.size());
            return new OfficialCodebookDetailResponse(
                    small.codeSetKey(),
                    small.sourceType(),
                    small.sourceFile(),
                    small.intendedUse(),
                    small.rowCount(),
                    matchedRows.size(),
                    small.sheetName(),
                    small.headers(),
                    matchedRows.subList(0, Math.min(effectiveLimit, matchedRows.size())),
                    null
            );
        }

        LargeCodebook large = largeCodebooks.get(codeSetKey);
        if (large != null) {
            Map<String, Object> metadata = new LinkedHashMap<>();
            if (large.activeRowCount() != null) {
                metadata.put("activeRowCount", large.activeRowCount());
            }
            if (large.activeTopLevelRowCount() != null) {
                metadata.put("activeTopLevelRowCount", large.activeTopLevelRowCount());
            }
            if (large.topOrganizationTypes() != null && !large.topOrganizationTypes().isEmpty()) {
                metadata.put("topOrganizationTypes", large.topOrganizationTypes());
            }
            if (large.sampleRows() != null && !large.sampleRows().isEmpty()) {
                metadata.put("sampleRows", large.sampleRows());
            }
            return new OfficialCodebookDetailResponse(
                    large.codeSetKey(),
                    large.sourceType(),
                    large.sourceFile(),
                    large.intendedUse(),
                    large.rowCount(),
                    large.rowCount(),
                    null,
                    large.headers(),
                    null,
                    metadata
            );
        }

        throw new CustomException(ErrorCode.REFERENCE_NOT_FOUND);
    }

    private List<Map<String, String>> filterRows(List<Map<String, String>> rows, String query) {
        if (query == null || query.isBlank()) {
            return rows;
        }
        String needle = query.trim().toLowerCase(Locale.ROOT);
        List<Map<String, String>> result = new ArrayList<>();
        for (Map<String, String> row : rows) {
            for (String value : row.values()) {
                if (value != null && value.toLowerCase(Locale.ROOT).contains(needle)) {
                    result.add(row);
                    break;
                }
            }
        }
        return result;
    }

    private int resolveLimit(Integer requestedLimit, int fallback) {
        if (requestedLimit == null) {
            return fallback;
        }
        return Math.min(Math.max(requestedLimit, 1), MAX_LIMIT);
    }

    private LinkedHashSet<String> extractCodeValues(SmallCodebook codebook) {
        LinkedHashSet<String> codeValues = new LinkedHashSet<>();
        String fallbackHeader = (codebook.headers() != null && !codebook.headers().isEmpty())
                ? codebook.headers().get(0)
                : null;
        for (Map<String, String> row : codebook.rows()) {
            String codeValue = row.get("코드값");
            if ((codeValue == null || codeValue.isBlank()) && fallbackHeader != null) {
                codeValue = row.get(fallbackHeader);
            }
            if (codeValue != null && !codeValue.isBlank()) {
                codeValues.add(codeValue.trim());
            }
        }
        return codeValues;
    }

    private Payload loadPayload(ObjectMapper objectMapper) {
        ClassPathResource resource = new ClassPathResource("reference/official-codes/local-official-codebooks.json");
        try (InputStream inputStream = resource.getInputStream()) {
            return objectMapper.readValue(inputStream, Payload.class);
        } catch (IOException e) {
            throw new IllegalStateException("local official codebooks resource load failed", e);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Payload(
            List<SmallCodebook> smallCodebooks,
            List<LargeCodebook> largeCodebooks
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SmallCodebook(
            String codeSetKey,
            String sourceFile,
            String sourceType,
            String sheetName,
            List<String> headers,
            int rowCount,
            String intendedUse,
            List<Map<String, String>> rows
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record LargeCodebook(
            String codeSetKey,
            String sourceFile,
            String sourceType,
            List<String> headers,
            String intendedUse,
            int rowCount,
            Integer activeRowCount,
            Integer activeTopLevelRowCount,
            List<Object> topOrganizationTypes,
            List<Map<String, String>> sampleRows
    ) {
    }
}
