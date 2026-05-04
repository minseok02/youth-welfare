package com.example.welfare.recommend.service;

import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.recommend.entity.RecommendationLog;
import com.example.welfare.recommend.repository.RecommendationLogReadRepository;
import com.example.welfare.user.service.UserKeyLookupService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class RecommendationLogReadServiceTest {

    @Mock
    private RecommendationLogReadRepository recommendationLogReadRepository;

    @Mock
    private UserKeyLookupService userKeyLookupService;

    @InjectMocks
    private RecommendationLogReadService recommendationLogReadService;

    @Test
    @DisplayName("findLatestLogIdMap은 userKey별 최신 로그 id 맵을 반환한다")
    void findLatestLogIdMapReturnsMap() {
        RecommendationLog log = RecommendationLog.builder()
                .id(100L)
                .service(WelfareService.builder().id(10L).build())
                .build();
        given(userKeyLookupService.findNullable(7L)).willReturn("user-key-7");
        given(recommendationLogReadRepository.findLatestByUserKeyAndServiceIds("user-key-7", List.of(10L)))
                .willReturn(List.of(log));

        Map<Long, Long> actual = recommendationLogReadService.findLatestLogIdMap(7L, List.of(10L));

        assertThat(actual).containsEntry(10L, 100L);
    }

    @Test
    @DisplayName("findLatestLogIdMap은 비로그인 userKey가 없으면 빈 맵을 반환한다")
    void findLatestLogIdMapReturnsEmptyWhenNoUserKey() {
        given(userKeyLookupService.findNullable(7L)).willReturn(null);

        assertThat(recommendationLogReadService.findLatestLogIdMap(7L, List.of(10L))).isEmpty();
        then(recommendationLogReadRepository).should(never()).findLatestByUserKeyAndServiceIds("user-key-7", List.of(10L));
    }
}
