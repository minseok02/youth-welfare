package com.example.welfare.reference.controller;

import com.example.welfare.global.response.ApiResponse;
import com.example.welfare.reference.dto.OfficialCodebookDetailResponse;
import com.example.welfare.reference.dto.OfficialCodebookSummaryResponse;
import com.example.welfare.reference.service.OfficialCodebookReadService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/reference/official-codes")
@RequiredArgsConstructor
public class OfficialCodebookController {

    private final OfficialCodebookReadService officialCodebookReadService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<OfficialCodebookSummaryResponse>>> listCodebooks() {
        return ResponseEntity.ok(ApiResponse.success(officialCodebookReadService.listCodebooks()));
    }

    @GetMapping("/{codeSetKey}")
    public ResponseEntity<ApiResponse<OfficialCodebookDetailResponse>> getCodebook(
            @PathVariable String codeSetKey,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) @Min(1) @Max(500) Integer limit) {
        return ResponseEntity.ok(ApiResponse.success(officialCodebookReadService.getCodebook(codeSetKey, q, limit)));
    }
}
