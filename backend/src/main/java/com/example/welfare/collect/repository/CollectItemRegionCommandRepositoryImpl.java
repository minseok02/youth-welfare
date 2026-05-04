package com.example.welfare.collect.repository;

import com.example.welfare.policy.entity.ServiceRegion;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class CollectItemRegionCommandRepositoryImpl implements CollectItemRegionCommandRepository {

    private static final String DELETE_SQL = "DELETE FROM service_regions WHERE service_id = ?";
    private static final String INSERT_SQL =
            "INSERT INTO service_regions (service_id, region_code, sido_name, sgg_name) VALUES (?, ?, ?, ?)";

    private final JdbcTemplate jdbcTemplate;

    @Override
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

    private void bindRegion(PreparedStatement ps, ServiceRegion region) throws SQLException {
        ps.setLong(1, region.getService().getId());
        ps.setString(2, region.getRegionCode());
        ps.setString(3, region.getSidoName());
        ps.setString(4, region.getSggName());
    }
}
