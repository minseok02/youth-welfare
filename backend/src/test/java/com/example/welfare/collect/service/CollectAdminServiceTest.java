package com.example.welfare.collect.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
}
