package com.example.welfare.policy.service;

import com.example.welfare.global.util.RedisKeyHash;
import com.example.welfare.policy.repository.PolicyViewLogCommandRepository;
import com.example.welfare.user.service.UserKeyLookupService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@Slf4j
@RequiredArgsConstructor
public class PolicyViewLogService {

    private static final int DEDUP_WINDOW_HOURS = 24;

    private final PolicyViewLogCommandRepository policyViewLogCommandRepository;
    private final UserKeyLookupService userKeyLookupService;

    @Transactional
    public boolean registerViewIfFirstInWindow(Long serviceId, Long userId, String clientFingerprint) {
        LocalDateTime viewedAt = LocalDateTime.now();
        LocalDateTime cutoff = viewedAt.minusHours(DEDUP_WINDOW_HOURS);
        String userKey = userKeyLookupService.findNullable(userId);

        boolean duplicate = userKey != null
                ? policyViewLogCommandRepository.existsDuplicateUserView(
                serviceId, userKey, cutoff
        )
                : policyViewLogCommandRepository.existsDuplicateAnonymousView(
                serviceId, clientFingerprint, cutoff
        );

        updateRecentViewIfLoggedIn(serviceId, userKey, viewedAt);

        if (duplicate) {
            return false;
        }

        policyViewLogCommandRepository.saveView(serviceId, userKey, clientFingerprint, viewedAt);

        return true;
    }

    private void updateRecentViewIfLoggedIn(Long serviceId, String userKey, LocalDateTime viewedAt) {
        if (userKey == null) {
            return;
        }
        try {
            policyViewLogCommandRepository.upsertRecentView(serviceId, userKey, viewedAt);
        } catch (RuntimeException exception) {
            log.warn("recent policy view upsert failed serviceId={} userKeyHash={}",
                    serviceId, RedisKeyHash.sha256Hex(userKey), exception);
        }
    }
}
