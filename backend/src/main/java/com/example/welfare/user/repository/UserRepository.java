package com.example.welfare.user.repository;

import com.example.welfare.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    @Query(value = """
            select id, email, password_hash, name, birth_date, phone_enc, sido, sgg, region_code,
                   income_level, household_type, employment_status, is_active, notification_yn,
                   notification_period, notification_min_score, notification_consent_at,
                   login_fail_count, locked_until, display_count, profile_completeness,
                   withdrawn_at, created_at, updated_at
            from users
            where user_key = ?1
            """, nativeQuery = true)
    Optional<User> findByUserKey(String userKey);

    boolean existsByEmail(String email);

    List<User> findByNotificationYnTrueAndNotificationPeriod(User.NotificationPeriod period);

    @Query(value = "select user_key from users where id = ?1", nativeQuery = true)
    Optional<String> findUserKeyById(Long userId);
}
