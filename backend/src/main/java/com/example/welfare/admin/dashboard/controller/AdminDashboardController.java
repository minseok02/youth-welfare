package com.example.welfare.admin.dashboard.controller;

import com.example.welfare.admin.dashboard.dto.AdminDashboardResponse;
import com.example.welfare.admin.dashboard.service.AdminDashboardService;
import com.example.welfare.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<AdminDashboardResponse>> getSummary(
            @RequestParam(name = "trendWindowDays", required = false) List<Integer> trendWindowDays
    ) {
        log.info("[Admin] dashboard summary 조회 trendWindowDays={}", trendWindowDays);
        return ResponseEntity.ok(ApiResponse.success(adminDashboardService.getSummary(trendWindowDays)));
    }
}
