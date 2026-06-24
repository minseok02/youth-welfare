package com.example.welfare.user.service;

import com.example.welfare.global.util.AesEncryptUtil;
import com.example.welfare.global.util.RedisKeyHash;
import com.example.welfare.user.dto.response.UserPiiBackfillResponse;
import com.example.welfare.user.dto.response.UserPiiEncryptionRotationResponse;
import com.example.welfare.user.entity.UserPiiSyncQueue;
import com.example.welfare.user.repository.UserLegacyPiiSourceReadModel;
import com.example.welfare.user.repository.UserPiiBackfillReadRepository;
import com.example.welfare.user.repository.UserPiiBackfillStateReadModel;
import com.example.welfare.user.repository.UserPiiReadModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserPiiBackfillService {

    private final UserPiiBackfillReadRepository userPiiBackfillReadRepository;
    private final UserPiiCommandService userPiiCommandService;
    private final UserPiiSyncQueueService userPiiSyncQueueService;
    private final AesEncryptUtil aesEncryptUtil;

    public UserPiiBackfillResponse backfillMissingEncryptedFields() {
        var backfillStates = userPiiBackfillReadRepository.findMissingEncryptedFields();
        Map<String, UserLegacyPiiSourceReadModel> sourceByUserKey = loadLegacySourceByUserKey(backfillStates);

        int updatedUserCount = 0;
        int emailBackfilledCount = 0;
        int nameBackfilledCount = 0;
        int birthDateBackfilledCount = 0;
        int skippedCount = 0;

        for (UserPiiBackfillStateReadModel state : backfillStates) {
            UserLegacyPiiSourceReadModel source = sourceByUserKey.get(state.userKey());
            if (source == null) {
                log.warn("[UserPiiBackfillService] legacy user source not found for userKeyHash={}",
                        RedisKeyHash.sha256Hex(state.userKey()));
                skippedCount++;
                continue;
            }

            String emailEnc = missing(state.emailEnc()) && StringUtils.hasText(source.getEmail())
                    ? aesEncryptUtil.encrypt(source.getEmail())
                    : null;
            String nameEnc = missing(state.nameEnc()) && StringUtils.hasText(source.getName())
                    ? aesEncryptUtil.encrypt(source.getName())
                    : null;
            String birthDateEnc = missing(state.birthDateEnc()) && source.getBirthDate() != null
                    ? aesEncryptUtil.encrypt(source.getBirthDate().toString())
                    : null;

            if (emailEnc == null && nameEnc == null && birthDateEnc == null) {
                skippedCount++;
                continue;
            }

            userPiiCommandService.backfillEncryptedFields(state.userKey(), emailEnc, nameEnc, birthDateEnc);
            updatedUserCount++;

            if (emailEnc != null) {
                emailBackfilledCount++;
            }
            if (nameEnc != null) {
                nameBackfilledCount++;
            }
            if (birthDateEnc != null) {
                birthDateBackfilledCount++;
            }
        }

        log.info("[UserPiiBackfillService] user_pii 암호화 백필 완료 processed={} updatedUsers={} email={} name={} birthDate={} skipped={}",
                backfillStates.size(), updatedUserCount, emailBackfilledCount, nameBackfilledCount, birthDateBackfilledCount, skippedCount);

        return new UserPiiBackfillResponse(
                backfillStates.size(),
                updatedUserCount,
                emailBackfilledCount,
                nameBackfilledCount,
                birthDateBackfilledCount,
                skippedCount
        );
    }

    public UserPiiEncryptionRotationResponse rotateLegacyEncryptedFields() {
        var userPiiRows = userPiiBackfillReadRepository.findLegacyEncryptedFields();
        int userPiiUpdatedCount = 0;
        int queueUpdatedCount = 0;
        int skippedCount = 0;
        int failedCount = 0;

        for (UserPiiReadModel row : userPiiRows) {
            try {
                userPiiCommandService.upsertUserPii(
                        row.userKey(),
                        rotateIfLegacy(row.emailEnc()),
                        rotateIfLegacy(row.nameEnc()),
                        rotateIfLegacy(row.birthDateEnc()),
                        rotateIfLegacy(row.phoneEnc())
                );
                userPiiUpdatedCount++;
            } catch (RuntimeException e) {
                failedCount++;
                log.error("[UserPiiBackfillService] user_pii encryption rotation failed userKeyHash={} errorType={}",
                        RedisKeyHash.sha256Hex(row.userKey()), e.getClass().getSimpleName());
            }
        }

        var queueRows = userPiiSyncQueueService.findLegacyEncryptedPayloads();
        for (UserPiiSyncQueue queue : queueRows) {
            try {
                queue.replaceEncryptedPayload(
                        rotateIfLegacy(queue.getEmailEnc()),
                        rotateIfLegacy(queue.getNameEnc()),
                        rotateIfLegacy(queue.getBirthDateEnc()),
                        rotateIfLegacy(queue.getPhoneEnc())
                );
                userPiiSyncQueueService.save(queue);
                queueUpdatedCount++;
            } catch (RuntimeException e) {
                failedCount++;
                log.error("[UserPiiBackfillService] user_pii sync queue encryption rotation failed userKeyHash={} errorType={}",
                        RedisKeyHash.sha256Hex(queue.getUserKey()), e.getClass().getSimpleName());
            }
        }

        skippedCount = (userPiiRows.size() - userPiiUpdatedCount) + (queueRows.size() - queueUpdatedCount);
        log.info("[UserPiiBackfillService] user_pii legacy 암호문 회전 완료 userPiiProcessed={} userPiiUpdated={} queueProcessed={} queueUpdated={} skipped={} failed={}",
                userPiiRows.size(), userPiiUpdatedCount, queueRows.size(), queueUpdatedCount, skippedCount, failedCount);

        return new UserPiiEncryptionRotationResponse(
                userPiiRows.size(),
                userPiiUpdatedCount,
                queueRows.size(),
                queueUpdatedCount,
                skippedCount,
                failedCount
        );
    }

    private boolean missing(String value) {
        return !StringUtils.hasText(value);
    }

    private String rotateIfLegacy(String encryptedValue) {
        if (!StringUtils.hasText(encryptedValue) || aesEncryptUtil.isCurrentCipherText(encryptedValue)) {
            return encryptedValue;
        }
        return aesEncryptUtil.encrypt(aesEncryptUtil.decrypt(encryptedValue));
    }

    private Map<String, UserLegacyPiiSourceReadModel> loadLegacySourceByUserKey(Iterable<UserPiiBackfillStateReadModel> backfillStates) {
        java.util.List<String> userKeys = new java.util.ArrayList<>();
        for (UserPiiBackfillStateReadModel state : backfillStates) {
            userKeys.add(state.userKey());
        }
        if (userKeys.isEmpty()) {
            return Map.of();
        }
        return userPiiBackfillReadRepository.findLegacySourceByUserKeys(userKeys);
    }
}
