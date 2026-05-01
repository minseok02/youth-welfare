package com.example.welfare.user.repository;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class UserPiiReadWriteRepository {

    private static final RowMapper<UserPiiReadModel> USER_PII_ROW_MAPPER = (rs, rowNum) -> new UserPiiReadModel(
            rs.getString("user_key"),
            rs.getString("email_enc"),
            rs.getString("name_enc"),
            rs.getString("birth_date_enc"),
            rs.getString("phone_enc")
    );

    private static final RowMapper<UserPiiBackfillStateReadModel> BACKFILL_STATE_ROW_MAPPER = (rs, rowNum) ->
            new UserPiiBackfillStateReadModel(
                    rs.getString("user_key"),
                    rs.getString("email_enc"),
                    rs.getString("name_enc"),
                    rs.getString("birth_date_enc")
            );

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public UserPiiReadWriteRepository(
            @Qualifier("appPiiReadWriteNamedParameterJdbcTemplate")
            NamedParameterJdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<UserPiiReadModel> findByUserKey(String userKey) {
        return jdbcTemplate.query("""
                        select user_key, email_enc, name_enc, birth_date_enc, phone_enc
                        from youth_welfare_pii.user_pii
                        where user_key = :userKey
                        """,
                new MapSqlParameterSource("userKey", userKey),
                USER_PII_ROW_MAPPER
        ).stream().findFirst();
    }

    public List<UserPiiBackfillStateReadModel> findMissingEncryptedFields() {
        return jdbcTemplate.query("""
                        select user_key, email_enc, name_enc, birth_date_enc
                        from youth_welfare_pii.user_pii
                        where email_enc is null or email_enc = ''
                           or name_enc is null or name_enc = ''
                           or birth_date_enc is null or birth_date_enc = ''
                        order by user_key
                        """,
                BACKFILL_STATE_ROW_MAPPER
        );
    }

    public int backfillEncryptedFields(String userKey, String emailEnc, String nameEnc, String birthDateEnc) {
        return jdbcTemplate.update("""
                        update youth_welfare_pii.user_pii
                        set email_enc = case
                                when :emailEnc is not null and (email_enc is null or email_enc = '') then :emailEnc
                                else email_enc
                            end,
                            name_enc = case
                                when :nameEnc is not null and (name_enc is null or name_enc = '') then :nameEnc
                                else name_enc
                            end,
                            birth_date_enc = case
                                when :birthDateEnc is not null and (birth_date_enc is null or birth_date_enc = '') then :birthDateEnc
                                else birth_date_enc
                            end
                        where user_key = :userKey
                        """,
                new MapSqlParameterSource()
                        .addValue("userKey", userKey)
                        .addValue("emailEnc", emailEnc)
                        .addValue("nameEnc", nameEnc)
                        .addValue("birthDateEnc", birthDateEnc)
        );
    }

    public int upsertUserPii(String userKey, String emailEnc, String nameEnc, String birthDateEnc, String phoneEnc) {
        return jdbcTemplate.update("""
                        insert into youth_welfare_pii.user_pii (
                            user_key,
                            email_enc,
                            name_enc,
                            birth_date_enc,
                            phone_enc
                        )
                        values (
                            :userKey,
                            :emailEnc,
                            :nameEnc,
                            :birthDateEnc,
                            :phoneEnc
                        )
                        on duplicate key update
                            email_enc = values(email_enc),
                            name_enc = values(name_enc),
                            birth_date_enc = values(birth_date_enc),
                            phone_enc = values(phone_enc)
                        """,
                new MapSqlParameterSource()
                        .addValue("userKey", userKey)
                        .addValue("emailEnc", emailEnc)
                        .addValue("nameEnc", nameEnc)
                        .addValue("birthDateEnc", birthDateEnc)
                        .addValue("phoneEnc", phoneEnc)
        );
    }

    public int deleteByUserKey(String userKey) {
        return jdbcTemplate.update("""
                        delete from youth_welfare_pii.user_pii
                        where user_key = :userKey
                        """,
                new MapSqlParameterSource("userKey", userKey)
        );
    }
}
