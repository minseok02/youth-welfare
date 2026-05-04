package com.example.welfare.user.controller;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.global.response.ApiResponse;
import com.example.welfare.user.dto.response.UserMetadataUserKeyBackfillResponse;
import com.example.welfare.user.dto.response.UserPiiBackfillResponse;
import com.example.welfare.user.dto.response.UserPiiSyncReplayResponse;
import com.example.welfare.user.dto.response.UserPiiSyncStatusResponse;
import com.example.welfare.user.service.UserMetadataUserKeyBackfillService;
import com.example.welfare.user.service.UserPiiBackfillService;
import com.example.welfare.user.service.UserKeyLookupService;
import com.example.welfare.user.service.UserPiiSyncReplayService;
import com.example.welfare.user.service.UserPiiSyncStatusService;
import com.example.welfare.user.service.UserSessionRevocationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestBody;
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
    private final UserSessionRevocationService userSessionRevocationService;
    private final UserKeyLookupService userKeyLookupService;

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

    @PostMapping("/forced-logout")
    public ResponseEntity<ApiResponse<ForcedLogoutResponse>> forceLogoutUserSessions(
            @RequestBody ForcedLogoutRequest request
    ) {
        String userKey = request.userKey() == null ? null : request.userKey().trim();
        if (!StringUtils.hasText(userKey)) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        userKeyLookupService.requireExistingUserIdByUserKey(userKey);

        long cutoffMillis = System.currentTimeMillis();
        userSessionRevocationService.revokeUserSessions(userKey, cutoffMillis);
        log.info("[Admin] forced logout 트리거 userKey={} cutoffMillis={}", userKey, cutoffMillis);
        return ResponseEntity.ok(ApiResponse.success(new ForcedLogoutResponse(userKey, true)));
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

    public record ForcedLogoutRequest(String userKey) {
    }

    public record ForcedLogoutResponse(String userKey, boolean accepted) {
    }
}
