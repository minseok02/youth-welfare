package com.example.welfare.user.controller;

import com.example.welfare.global.response.ApiResponse;
import com.example.welfare.user.dto.response.UserPiiBackfillResponse;
import com.example.welfare.user.service.UserPiiBackfillService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class UserAdminController {

    private final UserPiiBackfillService userPiiBackfillService;

    @PostMapping("/pii-backfill")
    public ResponseEntity<ApiResponse<UserPiiBackfillResponse>> backfillUserPii() {
        log.info("[Admin] user_pii email/name/birth_date 앱 레벨 암호화 백필 트리거");
        return ResponseEntity.ok(ApiResponse.success(userPiiBackfillService.backfillMissingEncryptedFields()));
    }
}
