package com.example.welfare.collect.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NormalizationKeySupportTest {

    @Test
    @DisplayName("공식 youth mid label 여부를 공통 support에서 판단한다")
    void resolvesOfficialYouthMidLabel() {
        assertThat(NormalizationKeySupport.isOfficialYouthMidLabel("취업")).isTrue();
        assertThat(NormalizationKeySupport.isOfficialYouthMidLabel("온·오프라인교육")).isFalse();
        assertThat(NormalizationKeySupport.isOfficialYouthMidLabel(null)).isFalse();
    }

    @Test
    @DisplayName("youth mid term refresh scope는 official/raw alias를 같이 묶는다")
    void expandsYouthMidRefreshScope() {
        assertThat(NormalizationKeySupport.refreshScopeGroups(NormalizationKeySupport.TERM_GROUP_YOUTH_MID))
                .containsExactly(
                        NormalizationKeySupport.TERM_GROUP_YOUTH_MID,
                        NormalizationKeySupport.TERM_GROUP_YOUTH_MID_RAW_ALIAS
                );
        assertThat(NormalizationKeySupport.refreshScopeGroups(NormalizationKeySupport.TERM_GROUP_TARGET_GROUP))
                .containsExactly(NormalizationKeySupport.TERM_GROUP_TARGET_GROUP);
    }

    @Test
    @DisplayName("source field priority는 detail 근거를 list fallback보다 앞세운다")
    void resolvesSourceFieldPriority() {
        assertThat(NormalizationKeySupport.sourceFieldPriority(
                NormalizationKeySupport.SOURCE_FIELD_TARGET_DETAIL_SELECTION_CRITERIA
        )).isEqualTo(0);
        assertThat(NormalizationKeySupport.sourceFieldPriority(
                NormalizationKeySupport.SOURCE_FIELD_BOKJIRO_DIGEST
        )).isEqualTo(2);
    }

    @Test
    @DisplayName("복합 source field 안의 token 포함 여부를 판정한다")
    void checksSourceFieldTokenMembership() {
        assertThat(NormalizationKeySupport.sourceFieldContains(
                NormalizationKeySupport.SOURCE_FIELD_TARGET_DETAIL_SELECTION_CRITERIA,
                NormalizationKeySupport.SOURCE_FIELD_SELECTION_CRITERIA
        )).isTrue();
        assertThat(NormalizationKeySupport.sourceFieldContains(
                NormalizationKeySupport.SOURCE_FIELD_BOKJIRO_DIGEST,
                NormalizationKeySupport.SOURCE_FIELD_TARGET_DETAIL
        )).isFalse();
    }
}
