package com.example.welfare.policy.service;

import com.example.welfare.policy.entity.ServiceViewLog;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.ServiceViewLogRepository;
import com.example.welfare.user.service.UserKeyLookupService;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PolicyViewLogService {

    private static final int DEDUP_WINDOW_HOURS = 24;

    private final ServiceViewLogRepository serviceViewLogRepository;
    private final UserKeyLookupService userKeyLookupService;
    private final EntityManager entityManager;

    @Transactional
    public boolean registerViewIfFirstInWindow(Long serviceId, Long userId, String clientFingerprint) {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(DEDUP_WINDOW_HOURS);
        String userKey = userKeyLookupService.findNullable(userId);

        boolean duplicate = userKey != null
                ? serviceViewLogRepository.existsByServiceIdAndUserKeyAndViewedAtAfter(
                serviceId, userKey, cutoff
        )
                : serviceViewLogRepository.existsByServiceIdAndClientFingerprintAndViewedAtAfter(
                serviceId, clientFingerprint, cutoff
        );

        if (duplicate) {
            return false;
        }

        WelfareService serviceRef = entityManager.getReference(WelfareService.class, serviceId);
        serviceViewLogRepository.save(ServiceViewLog.builder()
                .service(serviceRef)
                .userKey(userKey)
                .clientFingerprint(clientFingerprint)
                .build());

        return true;
    }
}
