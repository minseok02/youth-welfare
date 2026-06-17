package com.example.welfare.collect.repository;

import com.example.welfare.policy.entity.ServiceRegion;
import com.example.welfare.policy.entity.WelfareService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ParameterizedPreparedStatementSetter;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class CollectItemRegionCommandRepositoryImplTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @InjectMocks
    private CollectItemRegionCommandRepositoryImpl collectItemRegionCommandRepository;

    @Test
    @DisplayName("collect item region command repository는 region 전체 교체를 위임한다")
    void replaceAllDelegates() {
        ServiceRegion region = ServiceRegion.builder()
                .service(WelfareService.builder().id(11L).build())
                .regionCode("11110")
                .sidoName("서울")
                .sggName("종로구")
                .build();

        given(jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM policy_region_corrections WHERE service_id = ? AND active = TRUE)",
                Boolean.class,
                11L
        )).willReturn(false);

        collectItemRegionCommandRepository.replaceAll(11L, List.of(region));

        then(jdbcTemplate).should().update("DELETE FROM service_regions WHERE service_id = ?", 11L);
        then(jdbcTemplate).should().batchUpdate(
                eq("INSERT INTO service_regions (service_id, region_code, sido_name, sgg_name) VALUES (?, ?, ?, ?)"),
                eq(List.of(region)),
                eq(200),
                any(ParameterizedPreparedStatementSetter.class)
        );
    }

    @Test
    @DisplayName("collect item region command repository는 빈 region 집합이면 batch insert를 생략한다")
    void replaceAllSkipsBatchInsertWhenEmpty() {
        given(jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM policy_region_corrections WHERE service_id = ? AND active = TRUE)",
                Boolean.class,
                12L
        )).willReturn(false);

        collectItemRegionCommandRepository.replaceAll(12L, List.of());

        then(jdbcTemplate).should().update("DELETE FROM service_regions WHERE service_id = ?", 12L);
        then(jdbcTemplate).should(never()).batchUpdate(
                any(String.class),
                any(List.class),
                eq(200),
                any(ParameterizedPreparedStatementSetter.class)
        );
    }

    @Test
    @DisplayName("수동 지역 보정이 있는 정책은 collect region 교체에서 제외한다")
    void replaceAllSkipsWhenManualCorrectionExists() {
        given(jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM policy_region_corrections WHERE service_id = ? AND active = TRUE)",
                Boolean.class,
                13L
        )).willReturn(true);

        collectItemRegionCommandRepository.replaceAll(13L, List.of());

        then(jdbcTemplate).should(never()).update("DELETE FROM service_regions WHERE service_id = ?", 13L);
    }

    @Test
    @DisplayName("collect item region command repository는 여러 service region을 bulk delete 후 batch insert 한다")
    void replaceAllBatchDelegates() {
        ServiceRegion region = ServiceRegion.builder()
                .service(WelfareService.builder().id(11L).build())
                .regionCode("11110")
                .sidoName("서울")
                .sggName("종로구")
                .build();

        given(namedParameterJdbcTemplate.queryForList(
                eq("SELECT service_id FROM policy_region_corrections WHERE active = TRUE AND service_id IN (:serviceIds)"),
                any(MapSqlParameterSource.class),
                eq(Long.class)
        )).willReturn(List.of());

        collectItemRegionCommandRepository.replaceAllBatch(List.of(11L, 11L, 12L), List.of(region));

        then(namedParameterJdbcTemplate).should().update(
                eq("DELETE FROM service_regions WHERE service_id IN (:serviceIds)"),
                any(MapSqlParameterSource.class)
        );
        then(jdbcTemplate).should().batchUpdate(
                eq("INSERT INTO service_regions (service_id, region_code, sido_name, sgg_name) VALUES (?, ?, ?, ?)"),
                eq(List.of(region)),
                eq(1_000),
                any(ParameterizedPreparedStatementSetter.class)
        );
    }

    @Test
    @DisplayName("bulk region 교체도 수동 지역 보정 정책은 delete와 insert 대상에서 제외한다")
    void replaceAllBatchSkipsCorrectedPolicies() {
        ServiceRegion corrected = ServiceRegion.builder()
                .service(WelfareService.builder().id(11L).build())
                .regionCode("11110")
                .sidoName("서울")
                .sggName("종로구")
                .build();
        ServiceRegion normal = ServiceRegion.builder()
                .service(WelfareService.builder().id(12L).build())
                .regionCode("28110")
                .sidoName("인천광역시")
                .sggName("중구")
                .build();
        given(namedParameterJdbcTemplate.queryForList(
                eq("SELECT service_id FROM policy_region_corrections WHERE active = TRUE AND service_id IN (:serviceIds)"),
                any(MapSqlParameterSource.class),
                eq(Long.class)
        )).willReturn(List.of(11L));

        collectItemRegionCommandRepository.replaceAllBatch(List.of(11L, 12L), List.of(corrected, normal));

        then(namedParameterJdbcTemplate).should().update(
                eq("DELETE FROM service_regions WHERE service_id IN (:serviceIds)"),
                any(MapSqlParameterSource.class)
        );
        then(jdbcTemplate).should().batchUpdate(
                eq("INSERT INTO service_regions (service_id, region_code, sido_name, sgg_name) VALUES (?, ?, ?, ?)"),
                eq(List.of(normal)),
                eq(1_000),
                any(ParameterizedPreparedStatementSetter.class)
        );
    }
}
