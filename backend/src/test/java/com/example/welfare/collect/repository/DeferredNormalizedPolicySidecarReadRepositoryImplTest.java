package com.example.welfare.collect.repository;

import com.example.welfare.collect.normalization.NormalizedPolicyAggregate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class DeferredNormalizedPolicySidecarReadRepositoryImplTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @InjectMocks
    private DeferredNormalizedPolicySidecarReadRepositoryImpl deferredNormalizedPolicySidecarReadRepository;

    @Test
    @DisplayName("sidecar read repository는 필수 sidecar 테이블 준비 여부를 위임한다")
    void sidecarTablesReadyDelegates() {
        given(jdbcTemplate.queryForObject(anyString(), eq(Integer.class))).willReturn(4);

        assertThat(deferredNormalizedPolicySidecarReadRepository.sidecarTablesReady()).isTrue();
    }

    @Test
    @DisplayName("sidecar read repository는 summary slot 테이블 준비 여부를 위임한다")
    void summarySlotTableReadyDelegates() {
        given(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("service_taxonomy_summary_slots")))
                .willReturn(1);

        assertThat(deferredNormalizedPolicySidecarReadRepository.summarySlotTableReady()).isTrue();
    }

    @Test
    @DisplayName("sidecar read repository는 기존 fact 목록 조회를 위임한다")
    void findExistingFactsDelegates() {
        NormalizedPolicyAggregate.Fact fact = NormalizedPolicyAggregate.Fact.builder()
                .factGroup("AGE")
                .factCodeSetKey("AGE_SET")
                .factCode("YOUTH_AGE")
                .factMergeKey("AGE_RANGE")
                .factLabel("지원 연령")
                .operator(NormalizedPolicyAggregate.Operator.RANGE)
                .valueType(NormalizedPolicyAggregate.ValueType.INTEGER)
                .rangeMinInt(19)
                .rangeMaxInt(34)
                .sourceField("eligibility")
                .authority(NormalizedPolicyAggregate.Authority.OFFICIAL)
                .confidence(BigDecimal.ONE)
                .dateValue(LocalDate.of(2026, 5, 4))
                .build();
        given(namedParameterJdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .willReturn(List.of(fact));

        assertThat(deferredNormalizedPolicySidecarReadRepository.findExistingFacts(1L)).containsExactly(fact);
    }
}
