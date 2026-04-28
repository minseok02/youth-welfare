package com.example.welfare.user.repository;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class UserPiiReadWriteRepository {

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
                (rs, rowNum) -> new UserPiiReadModel(
                        rs.getString("user_key"),
                        rs.getString("email_enc"),
                        rs.getString("name_enc"),
                        rs.getString("birth_date_enc"),
                        rs.getString("phone_enc")
                )
        ).stream().findFirst();
    }
}
