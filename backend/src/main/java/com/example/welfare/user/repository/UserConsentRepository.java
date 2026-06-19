package com.example.welfare.user.repository;

import com.example.welfare.user.entity.UserConsent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserConsentRepository extends JpaRepository<UserConsent, Long> {

    Optional<UserConsent> findByUserKeyAndConsentType(String userKey, UserConsent.ConsentType consentType);

    boolean existsByUserKeyAndConsentTypeAndWithdrawnAtIsNull(String userKey, UserConsent.ConsentType consentType);

    @Modifying
    @Query("""
            update UserConsent uc
               set uc.withdrawnAt = CURRENT_TIMESTAMP
             where uc.userKey = :userKey
               and uc.withdrawnAt is null
            """)
    int withdrawActiveByUserKey(@Param("userKey") String userKey);
}
