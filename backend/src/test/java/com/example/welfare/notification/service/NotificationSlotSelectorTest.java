package com.example.welfare.notification.service;

import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.recommend.entity.UserRecommendation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationSlotSelectorTest {

    private final NotificationSlotSelector selector = new NotificationSlotSelector();

    @Test
    @DisplayName("슬롯 배치는 상위 A 2건 뒤에 신규 B 1건을 우선 배치한다")
    void selectCandidatesPrefersNewBSlotAfterTopTwoA() {
        UserRecommendation a1 = recommendation(1L, "A1", "0.95", "8.0", LocalDateTime.now().minusDays(5));
        UserRecommendation a2 = recommendation(2L, "A2", "0.90", "7.0", LocalDateTime.now().minusDays(4));
        UserRecommendation a3 = recommendation(3L, "A3", "0.89", "6.0", LocalDateTime.now().minusDays(3));
        UserRecommendation b1 = recommendation(4L, "B1", "0.55", "5.0", LocalDateTime.now().minusHours(2));

        List<UserRecommendation> selected = selector.selectCandidates(List.of(a1, a2, a3, b1), 0.8);

        assertThat(selected).extracting(rec -> rec.getService().getTitle())
                .containsExactly("A1", "A2", "B1");
    }

    @Test
    @DisplayName("신규 B 후보가 없으면 기존 top3 A 패턴으로 fallback 한다")
    void selectCandidatesFallsBackToTopThreeAWhenNoBExists() {
        UserRecommendation a1 = recommendation(1L, "A1", "0.95", "8.0", LocalDateTime.now().minusDays(5));
        UserRecommendation a2 = recommendation(2L, "A2", "0.90", "7.0", LocalDateTime.now().minusDays(4));
        UserRecommendation a3 = recommendation(3L, "A3", "0.89", "6.0", LocalDateTime.now().minusDays(3));

        List<UserRecommendation> selected = selector.selectCandidates(List.of(a1, a2, a3), 0.8);

        assertThat(selected).extracting(rec -> rec.getService().getTitle())
                .containsExactly("A1", "A2", "A3");
    }

    private UserRecommendation recommendation(Long serviceId,
                                              String title,
                                              String finalScore,
                                              String ruleBaseScore,
                                              LocalDateTime createdAt) {
        WelfareService service = WelfareService.builder()
                .id(serviceId)
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId("SRC-" + serviceId)
                .title(title)
                .build();
        ReflectionTestUtils.setField(service, "createdAt", createdAt);

        return UserRecommendation.builder()
                .service(service)
                .finalScore(new BigDecimal(finalScore))
                .ruleBaseScore(new BigDecimal(ruleBaseScore))
                .recommendedAt(LocalDateTime.now())
                .build();
    }
}
