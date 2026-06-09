package com.example.welfare.collect.repository;

import com.example.welfare.policy.entity.WelfareService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class CollectListSnapshotRepositoryImpl implements CollectListSnapshotRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Override
    public List<ListItemFingerprint> fetchCurrentItems(WelfareService.SourceType sourceType) {
        String sql = """
                select source_id,
                       title,
                       description,
                       unified_category,
                       category_main,
                       category_sub,
                       keyword,
                       host_org,
                       operating_org,
                       status,
                       min_age,
                       max_age,
                       min_income,
                       max_income,
                       support_content,
                       apply_method_name,
                       start_date,
                       end_date,
                       apply_start_date,
                       apply_end_date,
                       life_stage,
                       detail_url,
                       support_cycle,
                       provision_type,
                       is_online_apply,
                       registered_at,
                       last_modified_at
                  from welfare_services
                 where source_type = :sourceType
                 order by source_id
                """;
        return jdbcTemplate.query(
                sql,
                new MapSqlParameterSource("sourceType", sourceType.name()),
                (rs, rowNum) -> new ListItemFingerprint(rs.getString("source_id"), fingerprint(rs))
        );
    }

    @Override
    public Optional<ListSnapshot> findLatestSnapshot(WelfareService.SourceType sourceType) {
        String sql = """
                select id,
                       source_type,
                       job_name,
                       collected_at,
                       total_count,
                       new_count,
                       changed_count,
                       missing_count
                  from collect_list_snapshots
                 where source_type = :sourceType
                 order by collected_at desc, id desc
                 limit 1
                """;
        List<ListSnapshot> rows = jdbcTemplate.query(
                sql,
                new MapSqlParameterSource("sourceType", sourceType.name()),
                (rs, rowNum) -> new ListSnapshot(
                        rs.getLong("id"),
                        WelfareService.SourceType.valueOf(rs.getString("source_type")),
                        rs.getString("job_name"),
                        rs.getTimestamp("collected_at").toLocalDateTime(),
                        rs.getInt("total_count"),
                        rs.getInt("new_count"),
                        rs.getInt("changed_count"),
                        rs.getInt("missing_count")
                )
        );
        return rows.stream().findFirst();
    }

    @Override
    public Map<String, String> fetchSnapshotItems(long snapshotId) {
        String sql = """
                select source_id, fingerprint_hash
                  from collect_list_snapshot_items
                 where snapshot_id = :snapshotId
                """;
        return jdbcTemplate.query(
                sql,
                new MapSqlParameterSource("snapshotId", snapshotId),
                (rs, rowNum) -> new ListItemFingerprint(rs.getString("source_id"), rs.getString("fingerprint_hash"))
        ).stream().collect(Collectors.toMap(ListItemFingerprint::sourceId, ListItemFingerprint::fingerprintHash));
    }

    @Override
    public long saveSnapshot(SaveSnapshotCommand command, List<ListItemFingerprint> items) {
        String insertSnapshot = """
                insert into collect_list_snapshots (
                    source_type,
                    job_name,
                    collected_at,
                    total_count,
                    new_count,
                    changed_count,
                    missing_count,
                    metadata_json
                ) values (
                    :sourceType,
                    :jobName,
                    now(),
                    :totalCount,
                    :newCount,
                    :changedCount,
                    :missingCount,
                    :metadataJson
                )
                returning id
                """;
        Long snapshotId = jdbcTemplate.queryForObject(
                insertSnapshot,
                new MapSqlParameterSource()
                        .addValue("sourceType", command.sourceType().name())
                        .addValue("jobName", command.jobName())
                        .addValue("totalCount", command.totalCount())
                        .addValue("newCount", command.newCount())
                        .addValue("changedCount", command.changedCount())
                        .addValue("missingCount", command.missingCount())
                        .addValue("metadataJson", command.metadataJson()),
                Long.class
        );
        if (snapshotId == null) {
            throw new IllegalStateException("collect list snapshot id was not returned");
        }
        insertItems(snapshotId, items);
        return snapshotId;
    }

    private void insertItems(long snapshotId, List<ListItemFingerprint> items) {
        if (items.isEmpty()) {
            return;
        }
        String sql = """
                insert into collect_list_snapshot_items (snapshot_id, source_id, fingerprint_hash)
                values (:snapshotId, :sourceId, :fingerprintHash)
                """;
        MapSqlParameterSource[] batch = items.stream()
                .map(item -> new MapSqlParameterSource()
                        .addValue("snapshotId", snapshotId)
                        .addValue("sourceId", item.sourceId())
                        .addValue("fingerprintHash", item.fingerprintHash()))
                .toArray(MapSqlParameterSource[]::new);
        jdbcTemplate.batchUpdate(sql, batch);
    }

    private String fingerprint(ResultSet rs) throws SQLException {
        String joined = String.join("|",
                value(rs.getString("title")),
                value(rs.getString("description")),
                value(rs.getString("unified_category")),
                value(rs.getString("category_main")),
                value(rs.getString("category_sub")),
                value(rs.getString("keyword")),
                value(rs.getString("host_org")),
                value(rs.getString("operating_org")),
                value(rs.getString("status")),
                value(rs.getObject("min_age")),
                value(rs.getObject("max_age")),
                value(rs.getObject("min_income")),
                value(rs.getObject("max_income")),
                value(rs.getString("support_content")),
                value(rs.getString("apply_method_name")),
                value(rs.getObject("start_date")),
                value(rs.getObject("end_date")),
                value(rs.getObject("apply_start_date")),
                value(rs.getObject("apply_end_date")),
                value(rs.getString("life_stage")),
                value(rs.getString("detail_url")),
                value(rs.getString("support_cycle")),
                value(rs.getString("provision_type")),
                value(rs.getObject("is_online_apply")),
                value(rs.getObject("registered_at")),
                value(rs.getObject("last_modified_at"))
        );
        return sha256Hex(joined);
    }

    private String value(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 digest is unavailable", e);
        }
    }
}
