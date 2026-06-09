package com.example.welfare.user.service;

import com.example.welfare.user.repository.UserWithdrawalDataCleanupCommandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserWithdrawalDataCleanupService {

    private final UserWithdrawalDataCleanupCommandRepository userWithdrawalDataCleanupCommandRepository;

    @Transactional
    public void cleanupByUserKey(String userKey) {
        if (userKey == null || userKey.isBlank()) {
            return;
        }
        userWithdrawalDataCleanupCommandRepository.cleanupByUserKey(userKey);
    }
}
