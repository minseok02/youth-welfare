package com.example.welfare.user.controller;

import com.example.welfare.global.response.ApiResponse;
import com.example.welfare.user.dto.response.UserMetadataUserKeyBackfillResponse;
import com.example.welfare.user.dto.response.UserPiiBackfillResponse;
import com.example.welfare.user.dto.response.UserPiiSyncReplayResponse;
import com.example.welfare.user.dto.response.UserPiiSyncStatusResponse;
import com.example.welfare.user.service.UserMetadataUserKeyBackfillService;
import com.example.welfare.user.service.UserPiiBackfillService;
import com.example.welfare.user.service.UserPiiSyncReplayService;
import com.example.welfare.user.service.UserPiiSyncStatusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class UserAdminController {

    private final UserMetadataUserKeyBackfillService userMetadataUserKeyBackfillService;
    private final UserPiiBackfillService userPiiBackfillService;
    private final UserPiiSyncReplayService userPiiSyncReplayService;
    private final UserPiiSyncStatusService userPiiSyncStatusService;

    @PostMapping("/metadata-user-key-backfill")
    public ResponseEntity<ApiResponse<UserMetadataUserKeyBackfillResponse>> backfillUserMetadataUserKeys() {
        log.info("[Admin] user_attributes/user_priorities user_key 백필 트리거");
        return ResponseEntity.ok(ApiResponse.success(userMetadataUserKeyBackfillService.backfillMissingUserKeys()));
    }

    @PostMapping("/pii-backfill")
    public ResponseEntity<ApiResponse<UserPiiBackfillResponse>> backfillUserPii() {
        log.info("[Admin] user_pii email/name/birth_date 앱 레벨 암호화 백필 트리거");
        return ResponseEntity.ok(ApiResponse.success(userPiiBackfillService.backfillMissingEncryptedFields()));
    }

    @PostMapping("/pii-sync-replay")
    public ResponseEntity<ApiResponse<UserPiiSyncReplayResponse>> replayUserPiiSync(
            @RequestParam(required = false) String userKey,
            @RequestParam(defaultValue = "100") int limit
    ) {
        log.info("[Admin] user_pii sync queue replay 트리거 userKey={} limit={}", userKey, limit);
        return ResponseEntity.ok(ApiResponse.success(userPiiSyncReplayService.replay(userKey, limit)));
    }

    @GetMapping("/pii-sync-status")
    public ResponseEntity<ApiResponse<UserPiiSyncStatusResponse>> getUserPiiSyncStatus(
            @RequestParam(defaultValue = "5") int failedSampleLimit
    ) {
        log.info("[Admin] user_pii sync queue status 조회 failedSampleLimit={}", failedSampleLimit);
        return ResponseEntity.ok(ApiResponse.success(userPiiSyncStatusService.getStatus(failedSampleLimit)));
    }
}
