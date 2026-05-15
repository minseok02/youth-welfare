package com.example.welfare.notification.repository;

import com.example.welfare.notification.entity.UserAlert;
import com.example.welfare.notification.entity.UserAlert.UserAlertStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserAlertRepository extends JpaRepository<UserAlert, Long> {

    List<UserAlert> findTop30ByUserKeyAndStatusNotOrderByCreatedAtDesc(String userKey, UserAlertStatus status);

    long countByUserKeyAndStatus(String userKey, UserAlertStatus status);

    Optional<UserAlert> findByIdAndUserKey(Long id, String userKey);

    Optional<UserAlert> findByEventKey(String eventKey);
}
