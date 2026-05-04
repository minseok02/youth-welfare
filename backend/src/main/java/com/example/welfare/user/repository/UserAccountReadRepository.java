package com.example.welfare.user.repository;

import com.example.welfare.user.entity.User;

import java.util.Optional;

public interface UserAccountReadRepository {

    Optional<User> findById(Long userId);

    Optional<User> findByUserKey(String userKey);
}
