package com.example.welfare.user.repository;

import java.util.Optional;

public interface UserKeyReadRepository {

    Optional<String> findUserKeyById(Long userId);

    Optional<Long> findIdByUserKey(String userKey);
}
