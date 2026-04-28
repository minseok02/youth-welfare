package com.example.welfare.user.repository;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class NotificationPiiReadRepository {

    private static final RowMapper<NotificationPiiEmailRow> EMAIL_ROW_MAPPER = (rs, rowNum) ->
            new NotificationPiiEmailRow(
                    rs.getString("user_key"),
                    rs.getString("email_enc")
            );

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public NotificationPiiReadRepository(
            @Qualifier("notificationPiiReadNamedParameterJdbcTemplate")
            NamedParameterJdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, String> findEncryptedEmailsByUserKeys(List<String> userKeys) {
        if (userKeys.isEmpty()) {
            return Map.of();
        }

        String sql = """
                select user_key, email_enc
                from youth_welfare_pii.user_pii
                where user_key in (:userKeys)
                """;

        return jdbcTemplate.query(
                        sql,
                        new MapSqlParameterSource("userKeys", userKeys),
                        EMAIL_ROW_MAPPER
                ).stream()
                .collect(LinkedHashMap::new,
                        (map, row) -> map.put(row.userKey(), row.emailEnc()),
                        Map::putAll);
    }

    public Optional<String> findEncryptedEmailByUserKey(String userKey) {
        String sql = """
                select user_key, email_enc
                from youth_welfare_pii.user_pii
                where user_key = :userKey
                """;

        return jdbcTemplate.query(
                        sql,
                        new MapSqlParameterSource("userKey", userKey),
                        EMAIL_ROW_MAPPER
                ).stream()
                .findFirst()
                .map(NotificationPiiEmailRow::emailEnc);
    }

    private record NotificationPiiEmailRow(String userKey, String emailEnc) {
    }
}
