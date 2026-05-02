package com.example.welfare.policy.service;

import com.example.welfare.policy.entity.ServiceViewLog;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.ServiceViewLogRepository;
import com.example.welfare.user.repository.UserRepository;
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
    private final UserRepository userRepository;
    private final EntityManager entityManager;

    @Transactional
    public boolean registerViewIfFirstInWindow(Long serviceId, Long userId, String clientFingerprint) {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(DEDUP_WINDOW_HOURS);
        String userKey = resolveUserKey(userId);

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

    private String resolveUserKey(Long userId) {
        if (userId == null) {
            return null;
        }
        return userRepository.findUserKeyById(userId).orElse(null);
    }
}
