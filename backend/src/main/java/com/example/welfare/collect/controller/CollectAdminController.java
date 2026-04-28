package com.example.welfare.collect.controller;

import com.example.welfare.collect.service.CollectService;
import com.example.welfare.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
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

    @PostMapping("/youth")
    public ResponseEntity<ApiResponse<String>> collectYouth() {
        log.info("[Admin] 온통청년 수집 수동 트리거");
        collectService.collectYouth();
        return ResponseEntity.ok(ApiResponse.success("온통청년 수집 완료"));
    }

    @PostMapping("/bokjiro-central")
    public ResponseEntity<ApiResponse<String>> collectBokjiroCentral() {
        log.info("[Admin] 복지로 중앙 수집 수동 트리거");
        collectService.collectBokjiroCentral();
        return ResponseEntity.ok(ApiResponse.success("복지로 중앙 수집 완료"));
    }

    @PostMapping("/bokjiro-local")
    public ResponseEntity<ApiResponse<String>> collectBokjiroLocal() {
        log.info("[Admin] 복지로 지자체 수집 수동 트리거");
        collectService.collectBokjiroLocal();
        return ResponseEntity.ok(ApiResponse.success("복지로 지자체 수집 완료"));
    }

    @PostMapping("/bokjiro-details")
    public ResponseEntity<ApiResponse<String>> collectBokjiroDetails() {
        log.info("[Admin] 복지로 상세 수집 수동 트리거");
        collectService.collectBokjiroDetails();
        return ResponseEntity.ok(ApiResponse.success("복지로 상세 수집 완료"));
    }

    @PostMapping("/bokjiro-details-refresh")
    public ResponseEntity<ApiResponse<String>> collectBokjiroDetailsRefresh() {
        log.info("[Admin] 복지로 상세 refresh 수동 트리거");
        collectService.collectBokjiroDetailsRefresh();
        return ResponseEntity.ok(ApiResponse.success("복지로 상세 refresh 완료"));
    }
}
