package com.example.welfare.recommend.dto;

import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.recommend.entity.AiScoreStatus;
import com.example.welfare.recommend.entity.UserRecommendation;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class RecommendationResponseTest {

    @Test
    void fromSanitizesAiReasonForApiResponse() {
        WelfareService service = WelfareService.builder()
                .id(11L)
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId("Y1")
                .title("청년 정책")
                .status(WelfareService.ServiceStatus.ACTIVE)
                .build();
        UserRecommendation recommendation = UserRecommendation.builder()
                .id(1L)
                .userKey("user-key-1")
                .service(service)
                .recommendedAt(LocalDateTime.of(2026, 6, 6, 12, 0))
                .aiStatus(AiScoreStatus.SCORED)
                .aiReason("  지역\n청년\t주거 지원 조건과 지역 조건이 잘 맞아요  ")
                .build();

        RecommendationResponse response = RecommendationResponse.from(recommendation);

        assertThat(response.getAiReason()).isEqualTo("지역 청년 주거 지원 조건과 지역 조");
    }
}
