package com.example.welfare.user.repository;

import com.example.welfare.user.entity.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class AuthIdentityReadRepositoryImpl implements AuthIdentityReadRepository {

    private final AuthUserRepository authUserRepository;

    @Override
    public boolean existsByEmailLookupHash(String emailLookupHash) {
        return authUserRepository.existsByEmailLookupHash(emailLookupHash);
    }

    @Override
    public Optional<AuthUser> findByEmailLookupHash(String emailLookupHash) {
        return authUserRepository.findByEmailLookupHash(emailLookupHash);
    }

    @Override
    public Optional<AuthUser> findByUserKey(String userKey) {
        return authUserRepository.findByUserKey(userKey);
    }
}
