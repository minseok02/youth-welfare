package com.example.welfare.collect.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CollectListResponsePolicyTest {

    private final CollectListResponsePolicy collectListResponsePolicy = new CollectListResponsePolicy();

    @Test
    @DisplayName("list source 가 빈 응답이면 외부 장애로 보고 COLLECT_API_FAILED 를 던진다")
    void ensureNonEmptyThrowsForTrackedListSources() {
        assertThatThrownBy(() -> collectListResponsePolicy.ensureNonEmpty(CollectSource.YOUTH, List.of()))
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> org.assertj.core.api.Assertions.assertThat(((CustomException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.COLLECT_API_FAILED));
    }

    @Test
    @DisplayName("비어 있지 않은 응답은 그대로 통과시킨다")
    void ensureNonEmptyAllowsNonEmptyItems() {
        collectListResponsePolicy.ensureNonEmpty(CollectSource.BOKJIRO_LOCAL, List.of("item"));
    }
}
