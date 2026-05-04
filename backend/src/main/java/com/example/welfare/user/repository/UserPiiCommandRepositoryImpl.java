package com.example.welfare.user.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class UserPiiCommandRepositoryImpl implements UserPiiCommandRepository {

    private final UserPiiReadWriteRepository userPiiReadWriteRepository;

    @Override
    public int backfillEncryptedFields(String userKey, String emailEnc, String nameEnc, String birthDateEnc) {
        return userPiiReadWriteRepository.backfillEncryptedFields(userKey, emailEnc, nameEnc, birthDateEnc);
    }

    @Override
    public int upsertUserPii(String userKey, String emailEnc, String nameEnc, String birthDateEnc, String phoneEnc) {
        return userPiiReadWriteRepository.upsertUserPii(userKey, emailEnc, nameEnc, birthDateEnc, phoneEnc);
    }

    @Override
    public int deleteByUserKey(String userKey) {
        return userPiiReadWriteRepository.deleteByUserKey(userKey);
    }
}
