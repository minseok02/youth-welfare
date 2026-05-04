package com.example.welfare.user.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.user.entity.AuthUser;
import com.example.welfare.user.repository.AuthIdentityReadRepository;
import com.example.welfare.user.util.EmailLookupKeyGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthIdentityReadService {

    private final AuthIdentityReadRepository authIdentityReadRepository;

    @Transactional(readOnly = true)
    public boolean existsByEmail(String rawEmail) {
        return authIdentityReadRepository.existsByEmailLookupHash(EmailLookupKeyGenerator.hash(rawEmail));
    }

    @Transactional(readOnly = true)
    public Optional<AuthUser> findByEmail(String rawEmail) {
        return authIdentityReadRepository.findByEmailLookupHash(EmailLookupKeyGenerator.hash(rawEmail));
    }

    @Transactional(readOnly = true)
    public Optional<AuthUser> findByUserKey(String userKey) {
        return authIdentityReadRepository.findByUserKey(userKey);
    }

    @Transactional(readOnly = true)
    public String requireActiveUserKey(String userKey) {
        AuthUser authUser = findByUserKey(userKey)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        if (!authUser.isActive()) {
            throw new CustomException(ErrorCode.WITHDRAWN_USER);
        }
        return userKey;
    }
}
