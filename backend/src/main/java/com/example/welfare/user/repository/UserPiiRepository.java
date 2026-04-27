package com.example.welfare.user.repository;

import com.example.welfare.user.entity.UserPii;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserPiiRepository extends JpaRepository<UserPii, Long> {

    Optional<UserPii> findByUserKey(String userKey);

    void deleteByUserKey(String userKey);
}
