package com.example.welfare.recommend.service;

import com.example.welfare.recommend.dto.RecommendationRefreshStatusResponse;
import com.example.welfare.recommend.entity.UserRecommendation;
import com.example.welfare.user.service.UserKeyLookupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class RecommendationRefreshAsyncJobServiceTest {

    private final UserKeyLookupService userKeyLookupService = mock(UserKeyLookupService.class);
    private final RecommendationGenerationService recommendationGenerationService = mock(RecommendationGenerationService.class);
    private final RecommendationResultReadService recommendationResultReadService = mock(RecommendationResultReadService.class);
    private final RecommendationRefreshStatusStore recommendationRefreshStatusStore = mock(RecommendationRefreshStatusStore.class);
    private final Executor directExecutor = Runnable::run;

    private RecommendationRefreshAsyncJobService service;

    @BeforeEach
    void setUp() {
        service = new RecommendationRefreshAsyncJobService(
                userKeyLookupService,
                recommendationGenerationService,
                recommendationResultReadService,
                recommendationRefreshStatusStore,
                directExecutor
        );
        given(userKeyLookupService.findRequired(1L)).willReturn("user-key-1");
        given(recommendationResultReadService.findLatestSavedRecommendations("user-key-1")).willReturn(List.of());
    }

    @Test
    @DisplayName("이미 같은 사용자 refresh가 active이면 새 작업을 enqueue하지 않는다")
    void triggerDoesNotEnqueueWhenStatusIsActive() {
        RecommendationRefreshStatusResponse running = status(
                RecommendationRefreshStatusResponse.RecommendationRefreshState.RUNNING,
                true
        );
        given(recommendationRefreshStatusStore.read("user-key-1", false, null, 0)).willReturn(running);

        RecommendationRefreshStatusResponse response = service.trigger(1L, false);

        assertThat(response.state()).isEqualTo(RecommendationRefreshStatusResponse.RecommendationRefreshState.RUNNING);
        verify(recommendationRefreshStatusStore, never()).tryStart(eq("user-key-1"), eq(false), any());
        verify(recommendationGenerationService, never()).recommend(any(), eq(false));
    }

    @Test
    @DisplayName("새 async refresh는 기존 추천 생성 경로를 그대로 호출하고 성공 상태를 기록한다")
    void triggerRunsExistingRecommendationGenerationPath() {
        RecommendationRefreshStatusResponse idle = status(
                RecommendationRefreshStatusResponse.RecommendationRefreshState.IDLE,
                false
        );
        RecommendationRefreshStatusResponse succeeded = status(
                RecommendationRefreshStatusResponse.RecommendationRefreshState.SUCCEEDED,
                false
        );
        RecommendationRefreshStatusStore.ActiveRefreshHandle handle =
                new RecommendationRefreshStatusStore.ActiveRefreshHandle(
                        "user-key-1",
                        false,
                        "owner-token",
                        LocalDateTime.of(2026, 7, 17, 9, 0)
                );
        UserRecommendation saved = UserRecommendation.builder()
                .recommendedAt(LocalDateTime.of(2026, 7, 17, 9, 0, 5))
                .build();

        given(recommendationRefreshStatusStore.read("user-key-1", false, null, 0))
                .willReturn(idle, succeeded);
        given(recommendationRefreshStatusStore.tryStart(eq("user-key-1"), eq(false), any()))
                .willReturn(Optional.of(handle));
        given(recommendationGenerationService.recommend(1L, false)).willReturn(List.of(saved));

        RecommendationRefreshStatusResponse response = service.trigger(1L, false);

        assertThat(response.state()).isEqualTo(RecommendationRefreshStatusResponse.RecommendationRefreshState.SUCCEEDED);
        verify(recommendationGenerationService).recommend(1L, false);
        verify(recommendationRefreshStatusStore).markRunning(eq(handle), any());
        verify(recommendationRefreshStatusStore).markSucceeded(
                eq(handle),
                any(),
                any(),
                eq(LocalDateTime.of(2026, 7, 17, 9, 0, 5)),
                eq(1),
                anyLong()
        );
        verify(recommendationRefreshStatusStore).release(handle);
    }

    private RecommendationRefreshStatusResponse status(RecommendationRefreshStatusResponse.RecommendationRefreshState state,
                                                       boolean active) {
        return new RecommendationRefreshStatusResponse(
                state,
                active,
                false,
                state.name(),
                null,
                null,
                null,
                null,
                0,
                null,
                null,
                active ? 1000L : null
        );
    }
}
