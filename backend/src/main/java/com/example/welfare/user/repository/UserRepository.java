package com.example.welfare.user.repository;

import com.example.welfare.user.entity.User;
import com.example.welfare.user.util.EmailLookupKeyGenerator;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    @Query(value = """
            select u.*
            from users u
            join auth_users au on au.user_key = u.user_key
            where au.email_lookup_hash = :emailLookupHash
            limit 1
            """, nativeQuery = true)
    Optional<User> findByEmailLookupHash(@Param("emailLookupHash") String emailLookupHash);

    default Optional<User> findByEmail(String email) {
        return findByEmailLookupHash(EmailLookupKeyGenerator.hash(email));
    }

    @Query(value = """
            select exists(
                select 1
                from auth_users au
                where au.email_lookup_hash = :emailLookupHash
            )
            """, nativeQuery = true)
    boolean existsByEmailLookupHash(@Param("emailLookupHash") String emailLookupHash);

    default boolean existsByEmail(String email) {
        return existsByEmailLookupHash(EmailLookupKeyGenerator.hash(email));
    }

    Optional<User> findByUserKey(String userKey);

    List<User> findByNotificationYnTrueAndNotificationPeriod(User.NotificationPeriod period);

    @Query(value = "select user_key from users where id = ?1", nativeQuery = true)
    Optional<String> findUserKeyById(Long userId);

    @Query("select u.id from User u where u.userKey = :userKey")
    Optional<Long> findIdByUserKey(@Param("userKey") String userKey);

    @Query("""
            select u.userKey as userKey,
                   u.email as email,
                   u.name as name,
                   u.birthDate as birthDate
            from User u
            where u.userKey in :userKeys
            """)
    List<UserLegacyPiiSourceReadModel> findPiiBackfillSourcesByUserKeys(@Param("userKeys") Collection<String> userKeys);
}
