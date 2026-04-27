package com.example.welfare.user.repository;

import com.example.welfare.user.entity.AuthUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AuthUserRepository extends JpaRepository<AuthUser, Long> {

    Optional<AuthUser> findByUserKey(String userKey);

    Optional<AuthUser> findByEmailLookupHash(String emailLookupHash);

    boolean existsByEmailLookupHash(String emailLookupHash);

    void deleteByUserKey(String userKey);
}
