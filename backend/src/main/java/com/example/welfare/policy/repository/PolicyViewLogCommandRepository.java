package com.example.welfare.policy.repository;

import java.time.LocalDateTime;

public interface PolicyViewLogCommandRepository {

    boolean existsDuplicateUserView(Long serviceId, String userKey, LocalDateTime cutoff);

    boolean existsDuplicateAnonymousView(Long serviceId, String clientFingerprint, LocalDateTime cutoff);

    void saveView(Long serviceId, String userKey, String clientFingerprint, LocalDateTime viewedAt);

    void upsertRecentView(Long serviceId, String userKey, LocalDateTime viewedAt);
}
