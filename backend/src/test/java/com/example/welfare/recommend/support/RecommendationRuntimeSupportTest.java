package com.example.welfare.recommend.support;

import com.example.welfare.policy.entity.ServiceTag;
import com.example.welfare.policy.entity.WelfareService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RecommendationRuntimeSupportTest {

    @Test
    @DisplayName("deadline helper는 오늘부터 7일 미만 마감만 true로 본다")
    void detectsDeadlineSoon() {
        LocalDate today = LocalDate.of(2026, 5, 1);

        assertThat(RecommendationRuntimeSupport.isDeadlineSoon(today.plusDays(3), today)).isTrue();
        assertThat(RecommendationRuntimeSupport.isDeadlineSoon(today.plusDays(7), today)).isFalse();
        assertThat(RecommendationRuntimeSupport.isDeadlineSoon(today.minusDays(1), today)).isFalse();
    }

    @Test
    @DisplayName("signal helper는 service 본문과 tag 모두에서 신호를 찾는다")
    void detectsSignalsAcrossFieldsAndTags() {
        WelfareService service = WelfareService.builder()
                .title("농어촌 정착 지원")
                .description("청년 지원")
                .build();
        List<ServiceTag> tags = List.of(
                ServiceTag.builder().tagValue("한부모").build()
        );

        assertThat(RecommendationRuntimeSupport.containsSignal(service, List.of(), "농어촌")).isTrue();
        assertThat(RecommendationRuntimeSupport.containsSignal(WelfareService.builder().title("기본 지원").build(), tags, "한부모")).isTrue();
        assertThat(RecommendationRuntimeSupport.containsAnySignal(service, tags, "다문화", "한부모")).isTrue();
    }
}
