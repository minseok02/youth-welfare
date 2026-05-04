package com.example.welfare.user.repository;

import com.example.welfare.user.entity.AuthUser;

import java.util.Optional;

public interface AuthIdentityReadRepository {

    boolean existsByEmailLookupHash(String emailLookupHash);

    Optional<AuthUser> findByEmailLookupHash(String emailLookupHash);

    Optional<AuthUser> findByUserKey(String userKey);
}
