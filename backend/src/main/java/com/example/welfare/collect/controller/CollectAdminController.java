package com.example.welfare.collect.controller;

import com.example.welfare.collect.service.CollectSource;
import com.example.welfare.collect.service.CollectService;
import com.example.welfare.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 수집 배치 수동 트리거 — 로컬/개발 환경 전용 (prod 프로파일에서 비활성화)
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/collect")
@RequiredArgsConstructor
public class CollectAdminController {

    private final CollectService collectService;

    @PostMapping("/all")
    public ResponseEntity<ApiResponse<String>> collectAll() {
        log.info("[Admin] 전체 수집 수동 트리거");
        collectService.collectAll();
        return ResponseEntity.ok(ApiResponse.success("수집 완료"));
    }

    @PostMapping("/{sourceKey}")
    public ResponseEntity<ApiResponse<String>> collectSource(@PathVariable String sourceKey) {
        CollectSource source = CollectSource.fromPathKey(sourceKey);
        log.info("[Admin] {} 수집 수동 트리거", source.triggerLabel());
        collectService.collect(source);
        return ResponseEntity.ok(ApiResponse.success(source.successMessage()));
    }
}
