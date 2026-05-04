package com.example.welfare.user.service;

import com.example.welfare.user.repository.UserPiiReadWriteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserPiiCommandService {

    private final UserPiiReadWriteRepository userPiiReadWriteRepository;

    @Transactional
    public void backfillEncryptedFields(String userKey, String emailEnc, String nameEnc, String birthDateEnc) {
        userPiiReadWriteRepository.backfillEncryptedFields(userKey, emailEnc, nameEnc, birthDateEnc);
    }

    @Transactional
    public void upsertUserPii(String userKey, String emailEnc, String nameEnc, String birthDateEnc, String phoneEnc) {
        userPiiReadWriteRepository.upsertUserPii(userKey, emailEnc, nameEnc, birthDateEnc, phoneEnc);
    }

    @Transactional
    public void deleteByUserKey(String userKey) {
        userPiiReadWriteRepository.deleteByUserKey(userKey);
    }
}
