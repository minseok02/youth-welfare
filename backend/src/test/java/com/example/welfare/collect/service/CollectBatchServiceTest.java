package com.example.welfare.collect.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CollectBatchServiceTest {

    @Mock
    private CollectExecutionGuard collectExecutionGuard;
    @Mock
    private CollectSourceExecutionService collectSourceExecutionService;

    @InjectMocks
    private CollectBatchService collectBatchService;

    @Test
    @DisplayName("collectAllNow 는 고정된 source 순서대로 실행하고 중간 실패가 있어도 다음 source 를 계속 처리한다")
    void collectAllRunsSourcesInFixedOrder() {
        List<String> adapterCalls = new ArrayList<>();

        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(1);
            task.run();
            return null;
        }).when(collectExecutionGuard).runExclusive(eq("collect-all"), any(Runnable.class));

        doAnswer(invocation -> {
            CollectSource source = invocation.getArgument(0);
            adapterCalls.add(source.name());
            if (source == CollectSource.BOKJIRO_CENTRAL) {
                throw new IllegalStateException("boom");
            }
            return CollectResult.of(0, 0, 0, 0, 0);
        }).when(collectSourceExecutionService).collectSource(any());

        collectBatchService.collectAllNow();

        assertThat(adapterCalls).containsExactly("YOUTH", "BOKJIRO_CENTRAL", "BOKJIRO_LOCAL", "BOKJIRO_DETAIL");
        verify(collectExecutionGuard).runExclusive(eq("collect-all"), any(Runnable.class));
    }
}
