package com.example.welfare.recommend.service;

import com.example.welfare.recommend.entity.UserRecommendation;
import com.example.welfare.user.service.UserKeyLookupService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RecommendationAccessServiceTest {

    @Mock
    private UserKeyLookupService userKeyLookupService;

    @Mock
    private RecommendationResultReadService recommendationResultReadService;

    @InjectMocks
    private RecommendationAccessService recommendationAccessService;

    @Test
    @DisplayName("저장된 추천 목록 조회는 userKey 해석 후 result read service에 위임한다")
    void getRecommendationsDelegatesToResultReadService() {
        UserRecommendation saved = UserRecommendation.builder().id(404L).userKey("user-key-1").build();
        given(userKeyLookupService.findRequired(1L)).willReturn("user-key-1");
        given(recommendationResultReadService.findTopRecommendations("user-key-1", 5))
                .willReturn(List.of(saved));

        List<UserRecommendation> result = recommendationAccessService.getRecommendations(1L, 5);

        assertThat(result).containsExactly(saved);
        verify(recommendationResultReadService).findTopRecommendations("user-key-1", 5);
    }
}
