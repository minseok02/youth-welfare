package com.example.welfare.collect.normalization;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NormalizedFactMergeSupportTest {

    private final NormalizedFactMergeSupport mergeSupport = new NormalizedFactMergeSupport();

    @Test
    void merge_overwritesListFallbackWhenDetailFactIsStronger() {
        NormalizedPolicyAggregate.Fact listAge = rangeFact(
                "BK_AGE_ELIGIBILITY",
                "BOKJIRO_RULE_AGE",
                19,
                34,
                "servDgst",
                BigDecimal.valueOf(0.80)
        );
        NormalizedPolicyAggregate.Fact detailAge = rangeFact(
                "BK_AGE_ELIGIBILITY",
                "BOKJIRO_RULE_AGE",
                19,
                34,
                "targetDetail/selectionCriteria",
                BigDecimal.valueOf(0.90)
        );

        List<NormalizedPolicyAggregate.Fact> merged = mergeSupport.merge(List.of(listAge), List.of(detailAge));

        assertThat(merged).singleElement().satisfies(fact -> {
            assertThat(fact.sourceField()).isEqualTo("targetDetail/selectionCriteria");
            assertThat(fact.confidence()).isEqualByComparingTo("0.90");
            assertThat(fact.factMergeKey()).isEqualTo("BK_AGE_ELIGIBILITY");
        });
    }

    @Test
    void merge_keepsExistingWhenIncomingListFallbackIsWeaker() {
        NormalizedPolicyAggregate.Fact detailApplyEnd = dateFact(
                "BK_APPLY_END_DATE",
                "BOKJIRO_RULE_APPLY_END_DATE",
                LocalDate.of(2026, 12, 31),
                "applyMethodDetail/supportDetail",
                BigDecimal.valueOf(0.90)
        );
        NormalizedPolicyAggregate.Fact listApplyEnd = dateFact(
                "BK_APPLY_END_DATE",
                "BOKJIRO_RULE_APPLY_END_DATE",
                LocalDate.of(2026, 12, 31),
                "servDgst",
                BigDecimal.valueOf(0.80)
        );

        List<NormalizedPolicyAggregate.Fact> merged = mergeSupport.merge(List.of(detailApplyEnd), List.of(listApplyEnd));

        assertThat(merged).singleElement().satisfies(fact -> {
            assertThat(fact.sourceField()).isEqualTo("applyMethodDetail/supportDetail");
            assertThat(fact.confidence()).isEqualByComparingTo("0.90");
        });
    }

    @Test
    void merge_unionsSetLikeFactsAndDedupeSameMergeKey() {
        NormalizedPolicyAggregate.Fact existingOnePerson = flagFact(
                "BK_HOUSEHOLD:ONE_PERSON",
                "BOKJIRO_RULE_HOUSEHOLD",
                "1인가구"
        );
        NormalizedPolicyAggregate.Fact incomingOnePerson = flagFact(
                "BK_HOUSEHOLD:ONE_PERSON",
                "BOKJIRO_RULE_HOUSEHOLD",
                "1인가구"
        );
        NormalizedPolicyAggregate.Fact incomingHomeless = flagFact(
                "BK_HOUSEHOLD:HOMELESS",
                "BOKJIRO_RULE_HOUSEHOLD",
                "주거취약"
        );

        List<NormalizedPolicyAggregate.Fact> merged = mergeSupport.merge(
                List.of(existingOnePerson),
                List.of(incomingOnePerson, incomingHomeless)
        );

        assertThat(merged)
                .extracting(NormalizedPolicyAggregate.Fact::factMergeKey)
                .containsExactly("BK_HOUSEHOLD:ONE_PERSON", "BK_HOUSEHOLD:HOMELESS");
    }

    @Test
    void merge_overwritesExistingWhenRefreshHasSamePrecedence() {
        NormalizedPolicyAggregate.Fact existingAge = rangeFact(
                "YOUTH_AGE_ELIGIBILITY",
                "YOUTH_AGE",
                19,
                34,
                "sprtTrgtMinAge/sprtTrgtMaxAge",
                BigDecimal.ONE
        );
        NormalizedPolicyAggregate.Fact incomingAge = rangeFact(
                "YOUTH_AGE_ELIGIBILITY",
                "YOUTH_AGE",
                20,
                39,
                "sprtTrgtMinAge/sprtTrgtMaxAge",
                BigDecimal.ONE
        );

        List<NormalizedPolicyAggregate.Fact> merged = mergeSupport.merge(List.of(existingAge), List.of(incomingAge));

        assertThat(merged).singleElement().satisfies(fact -> {
            assertThat(fact.rangeMinInt()).isEqualTo(20);
            assertThat(fact.rangeMaxInt()).isEqualTo(39);
        });
    }

    private NormalizedPolicyAggregate.Fact rangeFact(String mergeKey,
                                                     String factCode,
                                                     Integer minAge,
                                                     Integer maxAge,
                                                     String sourceField,
                                                     BigDecimal confidence) {
        return NormalizedPolicyAggregate.Fact.builder()
                .factGroup("AGE")
                .factCode(factCode)
                .factMergeKey(mergeKey)
                .factLabel("지원 연령")
                .operator(NormalizedPolicyAggregate.Operator.RANGE)
                .valueType(NormalizedPolicyAggregate.ValueType.INTEGER)
                .rangeMinInt(minAge)
                .rangeMaxInt(maxAge)
                .unit("세")
                .sourceField(sourceField)
                .authority(NormalizedPolicyAggregate.Authority.RULE_DERIVED)
                .confidence(confidence)
                .build();
    }

    private NormalizedPolicyAggregate.Fact dateFact(String mergeKey,
                                                    String factCode,
                                                    LocalDate dateValue,
                                                    String sourceField,
                                                    BigDecimal confidence) {
        return NormalizedPolicyAggregate.Fact.builder()
                .factGroup("APPLY_END_DATE")
                .factCode(factCode)
                .factMergeKey(mergeKey)
                .factLabel("신청 종료일")
                .operator(NormalizedPolicyAggregate.Operator.EQ)
                .valueType(NormalizedPolicyAggregate.ValueType.DATE)
                .dateValue(dateValue)
                .sourceField(sourceField)
                .authority(NormalizedPolicyAggregate.Authority.RULE_DERIVED)
                .confidence(confidence)
                .build();
    }

    private NormalizedPolicyAggregate.Fact flagFact(String mergeKey,
                                                    String factCode,
                                                    String factLabel) {
        return NormalizedPolicyAggregate.Fact.builder()
                .factGroup("HOUSEHOLD")
                .factCode(factCode)
                .factMergeKey(mergeKey)
                .factLabel(factLabel)
                .operator(NormalizedPolicyAggregate.Operator.FLAG)
                .valueType(NormalizedPolicyAggregate.ValueType.BOOLEAN)
                .boolValue(true)
                .sourceField("targetDetail")
                .authority(NormalizedPolicyAggregate.Authority.RULE_DERIVED)
                .confidence(BigDecimal.valueOf(0.90))
                .build();
    }
}
