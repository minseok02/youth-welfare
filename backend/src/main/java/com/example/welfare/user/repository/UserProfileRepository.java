package com.example.welfare.user.repository;

import com.example.welfare.user.entity.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {

    Optional<UserProfile> findByUserKey(String userKey);

    @Query(value = """
            select u.id as userId,
                   up.user_key as userKey,
                   pii.email_enc as emailEnc,
                   up.notification_period as notificationPeriod,
                   up.notification_min_score as notificationMinScore,
                   up.display_count as displayCount
            from user_profiles up
            join users u on u.user_key = up.user_key
            join auth_users au on au.user_key = up.user_key
            left join youth_welfare_pii.user_pii pii on pii.user_key = up.user_key
            where up.notification_yn = true
              and up.notification_period = ?1
              and au.is_active = true
            order by u.id
            """, nativeQuery = true)
    List<NotificationTargetReadModel> findNotificationTargetsByPeriod(String notificationPeriod);

    void deleteByUserKey(String userKey);
}
