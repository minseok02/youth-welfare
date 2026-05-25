package com.example.welfare.notification.service;

import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.recommend.entity.RecommendationLog;
import com.example.welfare.recommend.entity.UserRecommendation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class NotificationMessageServiceTest {

    @Mock
    private NotificationUnsubscribeTokenService notificationUnsubscribeTokenService;

    @InjectMocks
    private NotificationMessageService notificationMessageService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(notificationMessageService, "appBaseUrl", "https://youth-welfare.kr");
    }

    @Test
    @DisplayName("추천 본문은 추천 이유, 정책 링크, 수신 거부 링크를 포함한다")
    void buildRecommendationMessageIncludesReasonAndLinks() {
        WelfareService service = WelfareService.builder()
                .id(11L)
                .title("청년 월세 지원")
                .build();
        UserRecommendation recommendation = UserRecommendation.builder()
                .service(service)
                .finalScore(new BigDecimal("0.91"))
                .aiReason("주거비 부담 완화에 적합")
                .build();
        RecommendationLog log = RecommendationLog.builder().id(100L).build();
        given(notificationUnsubscribeTokenService.issueToken("user-key-1")).willReturn("unsubscribe-token");

        String message = notificationMessageService.buildRecommendationMessage(
                "user-key-1",
                1L,
                List.of(recommendation),
                List.of(log)
        );

        assertThat(message).contains("추천 이유: 주거비 부담 완화에 적합");
        assertThat(message).contains("/policies/11?log_id=100");
        assertThat(message).contains("unsubscribe-token");
    }
}
