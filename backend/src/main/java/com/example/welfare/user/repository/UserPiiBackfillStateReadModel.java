package com.example.welfare.user.repository;

public record UserPiiBackfillStateReadModel(
        String userKey,
        String emailEnc,
        String nameEnc,
        String birthDateEnc
) {
}
