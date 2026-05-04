package com.example.welfare.user.service;

import com.example.welfare.user.repository.UserPiiCommandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserPiiCommandService {

    private final UserPiiCommandRepository userPiiCommandRepository;

    @Transactional
    public void backfillEncryptedFields(String userKey, String emailEnc, String nameEnc, String birthDateEnc) {
        userPiiCommandRepository.backfillEncryptedFields(userKey, emailEnc, nameEnc, birthDateEnc);
    }

    @Transactional
    public void upsertUserPii(String userKey, String emailEnc, String nameEnc, String birthDateEnc, String phoneEnc) {
        userPiiCommandRepository.upsertUserPii(userKey, emailEnc, nameEnc, birthDateEnc, phoneEnc);
    }

    @Transactional
    public void deleteByUserKey(String userKey) {
        userPiiCommandRepository.deleteByUserKey(userKey);
    }
}
