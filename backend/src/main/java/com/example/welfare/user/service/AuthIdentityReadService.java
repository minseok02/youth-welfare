package com.example.welfare.user.service;

import com.example.welfare.user.entity.AuthUser;
import com.example.welfare.user.repository.AuthUserRepository;
import com.example.welfare.user.util.EmailLookupKeyGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthIdentityReadService {

    private final AuthUserRepository authUserRepository;

    @Transactional(readOnly = true)
    public boolean existsByEmail(String rawEmail) {
        return authUserRepository.existsByEmailLookupHash(EmailLookupKeyGenerator.hash(rawEmail));
    }

    @Transactional(readOnly = true)
    public Optional<AuthUser> findByEmail(String rawEmail) {
        return authUserRepository.findByEmailLookupHash(EmailLookupKeyGenerator.hash(rawEmail));
    }
}
