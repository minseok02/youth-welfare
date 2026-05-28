package com.example.welfare.policy.repository;

import com.example.welfare.policy.entity.ServiceViewLog;
import com.example.welfare.policy.entity.WelfareService;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public class PolicyViewLogCommandRepositoryImpl implements PolicyViewLogCommandRepository {

    private final ServiceViewLogRepository serviceViewLogRepository;
    private final EntityManager entityManager;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public PolicyViewLogCommandRepositoryImpl(
            ServiceViewLogRepository serviceViewLogRepository,
            EntityManager entityManager,
            @Qualifier("primaryNamedParameterJdbcTemplate") NamedParameterJdbcTemplate namedParameterJdbcTemplate
    ) {
        this.serviceViewLogRepository = serviceViewLogRepository;
        this.entityManager = entityManager;
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

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
    public void saveView(Long serviceId, String userKey, String clientFingerprint, LocalDateTime viewedAt) {
        WelfareService serviceRef = entityManager.getReference(WelfareService.class, serviceId);
        serviceViewLogRepository.save(ServiceViewLog.builder()
                .service(serviceRef)
                .userKey(userKey)
                .clientFingerprint(clientFingerprint)
                .viewedAt(viewedAt)
                .build());
    }

    @Override
    public void upsertRecentView(Long serviceId, String userKey, LocalDateTime viewedAt) {
        namedParameterJdbcTemplate.update("""
                        INSERT INTO recent_policy_views (user_key, service_id, last_viewed_at)
                        VALUES (:userKey, :serviceId, :lastViewedAt)
                        ON CONFLICT (user_key, service_id)
                        DO UPDATE SET last_viewed_at = EXCLUDED.last_viewed_at
                        """,
                new MapSqlParameterSource()
                        .addValue("userKey", userKey)
                        .addValue("serviceId", serviceId)
                        .addValue("lastViewedAt", viewedAt)
        );
    }
}
