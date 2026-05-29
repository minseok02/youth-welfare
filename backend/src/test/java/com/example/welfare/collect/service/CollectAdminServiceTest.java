package com.example.welfare.collect.service;

import com.example.welfare.collect.dto.InvertedAgeBackfillResponse;
import com.example.welfare.policy.entity.WelfareService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.inOrder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CollectAdminServiceTest {

    @Mock
    private CollectExecutionGuard collectExecutionGuard;
    @Mock
    private CollectSourceExecutionService collectSourceExecutionService;
    @Mock
    private YouthDetailCollectService youthDetailCollectService;
    @Mock
    private InvertedAgeBackfillService invertedAgeBackfillService;

    @InjectMocks
    private CollectAdminService collectAdminService;

    @Test
    @DisplayName("단일 source 수집은 해당 source 의 lock 과 실행 엔진만 사용한다")
    void collectDelegatesToRequestedSource() {
        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(1);
            task.run();
            return null;
        }).when(collectExecutionGuard).runExclusive(eq("collect-youth"), any(Runnable.class));

        collectAdminService.collect(CollectSource.YOUTH);

        verify(collectExecutionGuard).runExclusive(eq("collect-youth"), any(Runnable.class));
        verify(collectSourceExecutionService).collectSource(CollectSource.YOUTH);
    }

    @Test
    @DisplayName("수동 Gov24 수집은 list 뒤에 detail/support follow-up lane도 이어서 실행한다")
    void collectGov24AlsoTriggersDetailAndSupportFollowUp() {
        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(1);
            task.run();
            return null;
        }).when(collectExecutionGuard).runExclusive(eq("collect-gov24"), any(Runnable.class));

        collectAdminService.collect(CollectSource.GOV24);

        verify(collectExecutionGuard).runExclusive(eq("collect-gov24"), any(Runnable.class));
        var inOrder = inOrder(collectSourceExecutionService);
        inOrder.verify(collectSourceExecutionService).collectSource(CollectSource.GOV24);
        inOrder.verify(collectSourceExecutionService).collectGov24Details();
        inOrder.verify(collectSourceExecutionService).collectGov24SupportConditions();
    }

    @Test
    @DisplayName("Gov24 detail sourceId override는 전용 실행 엔진으로 위임한다")
    void collectGov24DetailBySourceIdDelegates() {
        CollectResult expected = CollectResult.of(1, 1, 0, 0, 0);

        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(1);
            task.run();
            return null;
        }).when(collectExecutionGuard).runExclusive(eq("collect-gov24-details"), any(Runnable.class));
        when(collectSourceExecutionService.collectGov24DetailsForSourceId("305000000168"))
                .thenReturn(expected);

        CollectResult actual = collectAdminService.collect(CollectSource.GOV24_DETAIL, "305000000168");

        assertThat(actual).isEqualTo(expected);
        verify(collectExecutionGuard).runExclusive(eq("collect-gov24-details"), any(Runnable.class));
        verify(collectSourceExecutionService).collectGov24DetailsForSourceId("305000000168");
    }

    @Test
    @DisplayName("복지로 detail refresh sourceId override는 전용 실행 엔진으로 위임한다")
    void collectBokjiroDetailRefreshBySourceIdDelegates() {
        CollectResult expected = CollectResult.of(1, 1, 0, 0, 0);

        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(1);
            task.run();
            return null;
        }).when(collectExecutionGuard).runExclusive(eq("collect-bokjiro-details-refresh"), any(Runnable.class));
        when(collectSourceExecutionService.collectBokjiroDetailsRefreshForSourceId("WLF00004717"))
                .thenReturn(expected);

        CollectResult actual = collectAdminService.collect(CollectSource.BOKJIRO_DETAIL_REFRESH, "WLF00004717");

        assertThat(actual).isEqualTo(expected);
        verify(collectExecutionGuard).runExclusive(eq("collect-bokjiro-details-refresh"), any(Runnable.class));
        verify(collectSourceExecutionService).collectBokjiroDetailsRefreshForSourceId("WLF00004717");
    }

    @Test
    @DisplayName("detail gap fill 수집은 전용 lock 과 실행 엔진을 사용한다")
    void collectDetailGapFillUsesExclusiveLock() {
        BokjiroDetailCollectService.GapFillResult expected =
                new BokjiroDetailCollectService.GapFillResult(2, 2, 95, 10, 7, 2, 1, false);

        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(1);
            task.run();
            return null;
        }).when(collectExecutionGuard).runExclusive(eq("collect-bokjiro-details-gap-fill"), any(Runnable.class));
        when(collectSourceExecutionService.collectBokjiroDetailGapFill(2, 95)).thenReturn(expected);

        BokjiroDetailCollectService.GapFillResult actual = collectAdminService.collectBokjiroDetailGapFill(2, 95);

        assertThat(actual).isEqualTo(expected);
        verify(collectExecutionGuard).runExclusive(eq("collect-bokjiro-details-gap-fill"), any(Runnable.class));
        verify(collectSourceExecutionService).collectBokjiroDetailGapFill(2, 95);
    }

    @Test
    @DisplayName("온통청년 detail 수집은 전용 lock 아래에서 detail service를 실행한다")
    void collectYouthDetailsUsesExclusiveLock() {
        CollectResult expected = CollectResult.of(10, 8, 1, 0, 1);

        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(1);
            task.run();
            return null;
        }).when(collectExecutionGuard).runExclusive(eq("collect-youth-details"), any(Runnable.class));
        when(youthDetailCollectService.collectYouthDetails()).thenReturn(expected);

        CollectResult actual = collectAdminService.collectYouthDetails();

        assertThat(actual).isEqualTo(expected);
        verify(collectExecutionGuard).runExclusive(eq("collect-youth-details"), any(Runnable.class));
        verify(youthDetailCollectService).collectYouthDetails();
    }

    @Test
    @DisplayName("inverted age backfill은 전용 lock 아래에서 backfill service를 실행한다")
    void backfillInvertedAgeRangesUsesExclusiveLock() {
        InvertedAgeBackfillResponse expected = new InvertedAgeBackfillResponse(
                "selected-source-types",
                java.util.List.of(WelfareService.SourceType.YOUTH),
                25,
                2,
                1,
                0,
                1,
                0
        );

        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(1);
            task.run();
            return null;
        }).when(collectExecutionGuard).runExclusive(eq("collect-inverted-age-backfill"), any(Runnable.class));
        when(invertedAgeBackfillService.backfill(java.util.List.of(WelfareService.SourceType.YOUTH), 25)).thenReturn(expected);

        InvertedAgeBackfillResponse actual = collectAdminService.backfillInvertedAgeRanges(
                java.util.List.of(WelfareService.SourceType.YOUTH),
                25
        );

        assertThat(actual).isEqualTo(expected);
        verify(collectExecutionGuard).runExclusive(eq("collect-inverted-age-backfill"), any(Runnable.class));
        verify(invertedAgeBackfillService).backfill(java.util.List.of(WelfareService.SourceType.YOUTH), 25);
    }
}
