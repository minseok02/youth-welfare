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
import static org.mockito.BDDMockito.then;

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
        collectItemRegionCommandRepository.replaceAll(12L, List.of());

        then(jdbcTemplate).should().update("DELETE FROM service_regions WHERE service_id = ?", 12L);
        then(jdbcTemplate).shouldHaveNoMoreInteractions();
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
}
