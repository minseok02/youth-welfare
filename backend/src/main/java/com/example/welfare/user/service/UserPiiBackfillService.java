package com.example.welfare.user.service;

import com.example.welfare.global.util.AesEncryptUtil;
import com.example.welfare.user.dto.response.UserPiiBackfillResponse;
import com.example.welfare.user.repository.UserPiiBackfillTarget;
import com.example.welfare.user.repository.UserPiiRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserPiiBackfillService {

    private final UserPiiRepository userPiiRepository;
    private final AesEncryptUtil aesEncryptUtil;

    @Transactional
    public UserPiiBackfillResponse backfillMissingEncryptedFields() {
        var targets = userPiiRepository.findBackfillTargets();

        int updatedUserCount = 0;
        int emailBackfilledCount = 0;
        int nameBackfilledCount = 0;
        int birthDateBackfilledCount = 0;
        int skippedCount = 0;

        for (UserPiiBackfillTarget target : targets) {
            String emailEnc = missing(target.getEmailEnc()) && StringUtils.hasText(target.getEmail())
                    ? aesEncryptUtil.encrypt(target.getEmail())
                    : null;
            String nameEnc = missing(target.getNameEnc()) && StringUtils.hasText(target.getName())
                    ? aesEncryptUtil.encrypt(target.getName())
                    : null;
            String birthDateEnc = missing(target.getBirthDateEnc()) && target.getBirthDate() != null
                    ? aesEncryptUtil.encrypt(target.getBirthDate().toString())
                    : null;

            if (emailEnc == null && nameEnc == null && birthDateEnc == null) {
                skippedCount++;
                continue;
            }

            userPiiRepository.backfillEncryptedFields(target.getUserKey(), emailEnc, nameEnc, birthDateEnc);
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
                targets.size(), updatedUserCount, emailBackfilledCount, nameBackfilledCount, birthDateBackfilledCount, skippedCount);

        return new UserPiiBackfillResponse(
                targets.size(),
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
}
