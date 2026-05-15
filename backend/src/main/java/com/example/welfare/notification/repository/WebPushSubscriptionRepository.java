package com.example.welfare.notification.repository;

import com.example.welfare.notification.entity.WebPushSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WebPushSubscriptionRepository extends JpaRepository<WebPushSubscription, Long> {

    Optional<WebPushSubscription> findByEndpoint(String endpoint);

    Optional<WebPushSubscription> findByIdAndUserKey(Long id, String userKey);

    List<WebPushSubscription> findByUserKeyAndEnabledTrueOrderByCreatedAtDesc(String userKey);
}
