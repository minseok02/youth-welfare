package com.example.welfare.collect.support;

import com.example.welfare.collect.dto.Gov24ServiceListDto;
import com.example.welfare.collect.normalization.NormalizedPolicyAggregate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

class Gov24NormalizationSupportTest {

    @Test
    @DisplayName("gov24 taxonomy terms는 서비스분야 exact label과 사용자/지원유형 token을 함께 만든다")
    void buildsGov24TaxonomyTerms() {
        Gov24ServiceListDto.Item item = new Gov24ServiceListDto.Item();
        ReflectionTestUtils.setField(item, "serviceField", "주거·자립");
        ReflectionTestUtils.setField(item, "userType", "개인||가구||개인");
        ReflectionTestUtils.setField(item, "supportType", "현금(융자)||서비스(의료)||현금(융자)");

        var terms = Gov24NormalizationSupport.taxonomyTerms(item);

        assertThat(terms)
                .extracting(NormalizedPolicyAggregate.TaxonomyTerm::termGroup,
                        NormalizedPolicyAggregate.TaxonomyTerm::termLabel,
                        NormalizedPolicyAggregate.TaxonomyTerm::sourceField)
                .contains(
                        tuple("GOV24_SERVICE_FIELD", "주거·자립", "serviceField"),
                        tuple("GOV24_USER_TYPE_TOKEN", "개인", "userType"),
                        tuple("GOV24_USER_TYPE_TOKEN", "가구", "userType"),
                        tuple("GOV24_BENEFIT_TYPE_TOKEN", "현금(융자)", "supportType"),
                        tuple("GOV24_BENEFIT_TYPE_TOKEN", "서비스(의료)", "supportType")
                );
    }
}
