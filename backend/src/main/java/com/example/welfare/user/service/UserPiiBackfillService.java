package com.example.welfare.user.service;

import com.example.welfare.global.util.AesEncryptUtil;
import com.example.welfare.user.dto.response.UserPiiBackfillResponse;
import com.example.welfare.user.repository.UserLegacyPiiSourceReadModel;
import com.example.welfare.user.repository.UserPiiBackfillStateReadModel;
import com.example.welfare.user.repository.UserPiiReadWriteRepository;
import com.example.welfare.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserPiiBackfillService {

    private final UserRepository userRepository;
    private final UserPiiReadWriteRepository userPiiReadWriteRepository;
    private final AesEncryptUtil aesEncryptUtil;

    public UserPiiBackfillResponse backfillMissingEncryptedFields() {
        var backfillStates = userPiiReadWriteRepository.findMissingEncryptedFields();
        Map<String, UserLegacyPiiSourceReadModel> sourceByUserKey = loadLegacySourceByUserKey(backfillStates);

        int updatedUserCount = 0;
        int emailBackfilledCount = 0;
        int nameBackfilledCount = 0;
        int birthDateBackfilledCount = 0;
        int skippedCount = 0;

        for (UserPiiBackfillStateReadModel state : backfillStates) {
            UserLegacyPiiSourceReadModel source = sourceByUserKey.get(state.userKey());
            if (source == null) {
                log.warn("[UserPiiBackfillService] legacy user source not found for userKey={}", state.userKey());
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

            userPiiReadWriteRepository.backfillEncryptedFields(state.userKey(), emailEnc, nameEnc, birthDateEnc);
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

    private boolean missing(String value) {
        return !StringUtils.hasText(value);
    }

    private Map<String, UserLegacyPiiSourceReadModel> loadLegacySourceByUserKey(Iterable<UserPiiBackfillStateReadModel> backfillStates) {
        java.util.List<String> userKeys = new java.util.ArrayList<>();
        for (UserPiiBackfillStateReadModel state : backfillStates) {
            userKeys.add(state.userKey());
        }
        if (userKeys.isEmpty()) {
            return Map.of();
        }

        return userRepository.findPiiBackfillSourcesByUserKeys(userKeys).stream()
                .collect(LinkedHashMap::new,
                        (map, source) -> map.put(source.getUserKey(), source),
                        Map::putAll);
    }
}
