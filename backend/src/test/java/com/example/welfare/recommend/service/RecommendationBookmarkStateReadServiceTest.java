package com.example.welfare.recommend.service;

import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.recommend.entity.UserRecommendation;
import com.example.welfare.recommend.repository.RecommendationResultReadRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class RecommendationBookmarkStateReadServiceTest {

    @Mock
    private RecommendationResultReadRepository recommendationResultReadRepository;

    @InjectMocks
    private RecommendationBookmarkStateReadService recommendationBookmarkStateReadService;

    @Test
    @DisplayName("bookmark state read service는 최신 추천 row에서 serviceId별 북마크 상태 맵을 만든다")
    void findLatestBookmarkStateByServiceIdBuildsMap() {
        WelfareService service = WelfareService.builder().id(11L).build();
        UserRecommendation latestRecommendation = UserRecommendation.builder()
                .id(99L)
                .userKey("user-key-7")
                .service(service)
                .recommendedAt(LocalDateTime.now())
                .isBookmarked(true)
                .build();
        given(recommendationResultReadRepository.findLatestRecommendationRows("user-key-7"))
                .willReturn(List.of(latestRecommendation));

        assertThat(recommendationBookmarkStateReadService.findLatestBookmarkStateByServiceId("user-key-7"))
                .containsEntry(11L, true);
    }
}
