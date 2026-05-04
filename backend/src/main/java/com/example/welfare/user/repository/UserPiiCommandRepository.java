package com.example.welfare.user.repository;

public interface UserPiiCommandRepository {

    int backfillEncryptedFields(String userKey, String emailEnc, String nameEnc, String birthDateEnc);

    int upsertUserPii(String userKey, String emailEnc, String nameEnc, String birthDateEnc, String phoneEnc);

    int deleteByUserKey(String userKey);
}
