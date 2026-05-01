package com.example.welfare.user.repository;

public record UserPiiReadModel(
        String userKey,
        String emailEnc,
        String nameEnc,
        String birthDateEnc,
        String phoneEnc
) {
}
