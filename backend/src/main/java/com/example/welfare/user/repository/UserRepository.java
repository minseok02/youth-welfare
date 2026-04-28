package com.example.welfare.user.repository;

import com.example.welfare.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByUserKey(String userKey);

    boolean existsByEmail(String email);

    List<User> findByNotificationYnTrueAndNotificationPeriod(User.NotificationPeriod period);

    @Query(value = "select user_key from users where id = ?1", nativeQuery = true)
    Optional<String> findUserKeyById(Long userId);

    @Query("select u.id from User u where u.userKey = :userKey")
    Optional<Long> findIdByUserKey(@Param("userKey") String userKey);
}
