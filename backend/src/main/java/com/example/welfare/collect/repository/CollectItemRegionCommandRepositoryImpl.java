package com.example.welfare.collect.repository;

import com.example.welfare.policy.entity.ServiceRegion;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class CollectItemRegionCommandRepositoryImpl implements CollectItemRegionCommandRepository {

    private static final String DELETE_SQL = "DELETE FROM service_regions WHERE service_id = ?";
    private static final String BULK_DELETE_SQL = "DELETE FROM service_regions WHERE service_id IN (:serviceIds)";
    private static final String INSERT_SQL =
            "INSERT INTO service_regions (service_id, region_code, sido_name, sgg_name) VALUES (?, ?, ?, ?)";
    private static final int DELETE_CHUNK_SIZE = 1_000;
    private static final int INSERT_BATCH_SIZE = 1_000;

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Override
    @Transactional
    public void replaceAll(Long serviceId, List<ServiceRegion> regions) {
        jdbcTemplate.update(DELETE_SQL, serviceId);
        if (regions.isEmpty()) {
            return;
        }

        jdbcTemplate.batchUpdate(
                INSERT_SQL,
                regions,
                200,
                this::bindRegion
        );
    }

    @Override
    @Transactional
    public void replaceAllBatch(List<Long> serviceIds, List<ServiceRegion> regions) {
        List<Long> distinctServiceIds = new ArrayList<>(new LinkedHashSet<>(serviceIds));
        if (distinctServiceIds.isEmpty()) {
            return;
        }

        for (int from = 0; from < distinctServiceIds.size(); from += DELETE_CHUNK_SIZE) {
            int to = Math.min(from + DELETE_CHUNK_SIZE, distinctServiceIds.size());
            namedParameterJdbcTemplate.update(
                    BULK_DELETE_SQL,
                    new MapSqlParameterSource("serviceIds", distinctServiceIds.subList(from, to))
            );
        }
        if (regions.isEmpty()) {
            return;
        }

        jdbcTemplate.batchUpdate(
                INSERT_SQL,
                regions,
                INSERT_BATCH_SIZE,
                this::bindRegion
        );
    }

    private void bindRegion(PreparedStatement ps, ServiceRegion region) throws SQLException {
        ps.setLong(1, region.getService().getId());
        ps.setString(2, region.getRegionCode());
        ps.setString(3, region.getSidoName());
        ps.setString(4, region.getSggName());
    }
}
