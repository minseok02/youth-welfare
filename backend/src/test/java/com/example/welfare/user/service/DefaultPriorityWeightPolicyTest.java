package com.example.welfare.user.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultPriorityWeightPolicyTest {

    private final DefaultPriorityWeightPolicy policy = new DefaultPriorityWeightPolicy();

    @Test
    @DisplayName("우선순위 weight는 높은 순위에 더 강한 배율을 준다")
    void weightForRankUsesStrongerTopPriorityBias() {
        assertThat(policy.weightForRank(1)).isEqualTo(3.0);
        assertThat(policy.weightForRank(2)).isEqualTo(2.2);
        assertThat(policy.weightForRank(3)).isEqualTo(1.6);
        assertThat(policy.weightForRank(4)).isEqualTo(1.2);
        assertThat(policy.weightForRank(5)).isEqualTo(1.0);
    }
}
