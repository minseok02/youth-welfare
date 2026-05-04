package com.example.welfare.policy.service;

import com.example.welfare.policy.repository.PolicyViewLogCommandRepository;
import com.example.welfare.user.service.UserKeyLookupService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PolicyViewLogService {

    private static final int DEDUP_WINDOW_HOURS = 24;

    private final PolicyViewLogCommandRepository policyViewLogCommandRepository;
    private final UserKeyLookupService userKeyLookupService;

    @Transactional
    public boolean registerViewIfFirstInWindow(Long serviceId, Long userId, String clientFingerprint) {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(DEDUP_WINDOW_HOURS);
        String userKey = userKeyLookupService.findNullable(userId);

        boolean duplicate = userKey != null
                ? policyViewLogCommandRepository.existsDuplicateUserView(
                serviceId, userKey, cutoff
        )
                : policyViewLogCommandRepository.existsDuplicateAnonymousView(
                serviceId, clientFingerprint, cutoff
        );

        if (duplicate) {
            return false;
        }

        policyViewLogCommandRepository.saveView(serviceId, userKey, clientFingerprint);

        return true;
    }
}
