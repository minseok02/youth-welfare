package com.example.welfare.collect.support;

import com.example.welfare.collect.dto.YouthApiDto;
import com.example.welfare.collect.normalization.NormalizedPolicyAggregate;
import com.example.welfare.policy.entity.WelfareService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

class YouthNormalizationSupportTest {

    @Test
    @DisplayName("youth mid partition은 official label과 raw alias를 분리한다")
    void partitionsYouthMidLabels() {
        YouthNormalizationSupport.YouthMidPartition partition =
                YouthNormalizationSupport.partitionYouthMidLabels("취업,재직자,온·오프라인교육");

        assertThat(partition.officialLabels()).containsExactly("취업", "재직자");
        assertThat(partition.rawAliases()).containsExactly("온·오프라인교육");
        assertThat(partition.summaryLabel()).isNull();
    }

    @Test
    @DisplayName("youth taxonomy terms는 major, mid, keyword를 source field와 함께 만든다")
    void buildsYouthTaxonomyTerms() {
        YouthApiDto.Item item = new YouthApiDto.Item();
        ReflectionTestUtils.setField(item, "lclsfNm", "주거");
        ReflectionTestUtils.setField(item, "mclsfNm", "전월세 및 주거급여 지원");
        ReflectionTestUtils.setField(item, "plcyKywdNm", "월세,주거");

        var partition = YouthNormalizationSupport.partitionYouthMidLabels(item.getMclsfNm());
        var terms = YouthNormalizationSupport.taxonomyTerms(item, partition);

        assertThat(terms)
                .extracting(NormalizedPolicyAggregate.TaxonomyTerm::termGroup,
                        NormalizedPolicyAggregate.TaxonomyTerm::termLabel,
                        NormalizedPolicyAggregate.TaxonomyTerm::sourceField)
                .contains(
                        tuple("YOUTH_MAJOR", "주거", "lclsfNm"),
                        tuple("YOUTH_MID", "전월세 및 주거급여 지원", "category_sub"),
                        tuple("YOUTH_KEYWORD", "월세", "plcyKywdNm")
                );
    }

    @Test
    @DisplayName("youth facts는 age, income, apply-end canonical key를 만든다")
    void buildsYouthFacts() {
        WelfareService service = WelfareService.builder()
                .minAge(19)
                .maxAge(34)
                .minIncome(0)
                .maxIncome(100)
                .applyEndDate(LocalDate.of(2026, 12, 31))
                .build();
        YouthApiDto.Item item = new YouthApiDto.Item();
        ReflectionTestUtils.setField(item, "jobCd", "0013003,0013006,0013003");
        ReflectionTestUtils.setField(item, "schoolCd", "0049005,0049006,0049005");
        ReflectionTestUtils.setField(item, "mrgSttsCd", "0055003");
        ReflectionTestUtils.setField(item, "earnCndSeCd", "0043002");

        var facts = YouthNormalizationSupport.facts(service, item);

        assertThat(facts)
                .extracting(NormalizedPolicyAggregate.Fact::factMergeKey)
                .containsExactlyInAnyOrder(
                        "YOUTH_AGE_ELIGIBILITY",
                        "YOUTH_INCOME_MIN",
                        "YOUTH_INCOME_MAX",
                        "YOUTH_APPLY_END_DATE",
                        "YOUTH_EMPLOYMENT_REQUIREMENT",
                        "YOUTH_EDUCATION_REQUIREMENT",
                        "YOUTH_MARITAL_STATUS",
                        "YOUTH_INCOME_CONDITION_TYPE"
                );
        assertThat(facts)
                .filteredOn(fact -> "YOUTH_EMPLOYMENT_REQUIREMENT".equals(fact.factMergeKey()))
                .singleElement()
                .satisfies(fact -> {
                    assertThat(fact.factCodeSetKey()).isEqualTo("YOUTH_EMPLOYMENT_REQUIREMENT");
                    assertThat(fact.factCode()).isEqualTo("0013003");
                    assertThat(fact.rawValue()).isEqualTo("0013003,0013006");
                    assertThat(fact.textValue()).isEqualTo("미취업자, (예비)창업자");
                    assertThat(fact.operator()).isEqualTo(NormalizedPolicyAggregate.Operator.MEMBER);
                    assertThat(fact.sourceField()).isEqualTo("jobCd");
                });
        assertThat(facts)
                .filteredOn(fact -> "YOUTH_EDUCATION_REQUIREMENT".equals(fact.factMergeKey()))
                .singleElement()
                .satisfies(fact -> {
                    assertThat(fact.factCodeSetKey()).isEqualTo("YOUTH_EDUCATION_REQUIREMENT");
                    assertThat(fact.factCode()).isEqualTo("0049005");
                    assertThat(fact.rawValue()).isEqualTo("0049005,0049006");
                    assertThat(fact.textValue()).isEqualTo("대학 재학, 대졸 예정");
                    assertThat(fact.operator()).isEqualTo(NormalizedPolicyAggregate.Operator.MEMBER);
                    assertThat(fact.sourceField()).isEqualTo("schoolCd");
                });
        assertThat(facts)
                .filteredOn(fact -> "YOUTH_MARITAL_STATUS".equals(fact.factMergeKey()))
                .singleElement()
                .satisfies(fact -> {
                    assertThat(fact.factCodeSetKey()).isEqualTo("YOUTH_MARITAL_STATUS");
                    assertThat(fact.factCode()).isEqualTo("0055003");
                    assertThat(fact.textValue()).isEqualTo("제한없음");
                    assertThat(fact.operator()).isEqualTo(NormalizedPolicyAggregate.Operator.EQ);
                    assertThat(fact.sourceField()).isEqualTo("mrgSttsCd");
                });
        assertThat(facts)
                .filteredOn(fact -> "YOUTH_INCOME_CONDITION_TYPE".equals(fact.factMergeKey()))
                .singleElement()
                .satisfies(fact -> {
                    assertThat(fact.factCodeSetKey()).isEqualTo("YOUTH_INCOME_CONDITION_TYPE");
                    assertThat(fact.factCode()).isEqualTo("0043002");
                    assertThat(fact.textValue()).isEqualTo("연소득");
                    assertThat(fact.sourceField()).isEqualTo("earnCndSeCd");
                });
    }
}
