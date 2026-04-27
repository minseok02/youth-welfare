package com.example.welfare.user.repository;

import com.example.welfare.user.entity.UserPii;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface UserPiiRepository extends JpaRepository<UserPii, Long> {

    Optional<UserPii> findByUserKey(String userKey);

    @Query(value = """
            select up.user_key as userKey,
                   u.email as email,
                   u.name as name,
                   u.birth_date as birthDate,
                   up.email_enc as emailEnc,
                   up.name_enc as nameEnc,
                   up.birth_date_enc as birthDateEnc
            from users u
            join youth_welfare_pii.user_pii up on up.user_key = u.user_key
            where up.email_enc is null or up.email_enc = ''
               or up.name_enc is null or up.name_enc = ''
               or up.birth_date_enc is null or up.birth_date_enc = ''
            order by u.id
            """, nativeQuery = true)
    List<UserPiiBackfillTarget> findBackfillTargets();

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            update youth_welfare_pii.user_pii
            set email_enc = case
                    when ?2 is not null and (email_enc is null or email_enc = '') then ?2
                    else email_enc
                end,
                name_enc = case
                    when ?3 is not null and (name_enc is null or name_enc = '') then ?3
                    else name_enc
                end,
                birth_date_enc = case
                    when ?4 is not null and (birth_date_enc is null or birth_date_enc = '') then ?4
                    else birth_date_enc
                end
            where user_key = ?1
            """, nativeQuery = true)
    int backfillEncryptedFields(String userKey, String emailEnc, String nameEnc, String birthDateEnc);

    void deleteByUserKey(String userKey);
}
