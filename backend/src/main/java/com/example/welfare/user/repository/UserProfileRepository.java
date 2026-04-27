package com.example.welfare.user.repository;

import com.example.welfare.user.entity.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {

    Optional<UserProfile> findByUserKey(String userKey);

    void deleteByUserKey(String userKey);
}
