package com.example.welfare.policy.repository;

import com.example.welfare.policy.entity.ServiceViewLog;
import com.example.welfare.policy.entity.WelfareService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
@RequiredArgsConstructor
public class PolicyViewLogCommandRepositoryImpl implements PolicyViewLogCommandRepository {

    private final ServiceViewLogRepository serviceViewLogRepository;
    private final EntityManager entityManager;

    @Override
    public boolean existsDuplicateUserView(Long serviceId, String userKey, LocalDateTime cutoff) {
        return serviceViewLogRepository.existsByServiceIdAndUserKeyAndViewedAtAfter(serviceId, userKey, cutoff);
    }

    @Override
    public boolean existsDuplicateAnonymousView(Long serviceId, String clientFingerprint, LocalDateTime cutoff) {
        return serviceViewLogRepository.existsByServiceIdAndClientFingerprintAndViewedAtAfter(
                serviceId,
                clientFingerprint,
                cutoff
        );
    }

    @Override
    public void saveView(Long serviceId, String userKey, String clientFingerprint) {
        WelfareService serviceRef = entityManager.getReference(WelfareService.class, serviceId);
        serviceViewLogRepository.save(ServiceViewLog.builder()
                .service(serviceRef)
                .userKey(userKey)
                .clientFingerprint(clientFingerprint)
                .build());
    }
}
