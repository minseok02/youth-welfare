package com.example.welfare.collect.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicReference;

@Service
@RequiredArgsConstructor
public class CollectAdminService {

    private final CollectExecutionGuard collectExecutionGuard;
    private final CollectSourceExecutionService collectSourceExecutionService;

    public void collect(CollectSource source) {
        collectExecutionGuard.runExclusive(source.lockName(), () -> collectSourceExecutionService.collectSource(source));
    }

    public BokjiroDetailCollectService.GapFillResult collectBokjiroDetailGapFill(int rounds, int maxCallsPerRound) {
        AtomicReference<BokjiroDetailCollectService.GapFillResult> resultRef = new AtomicReference<>();
        collectExecutionGuard.runExclusive(CollectSource.BOKJIRO_DETAIL_GAP_FILL.lockName(), () ->
                resultRef.set(collectSourceExecutionService.collectBokjiroDetailGapFill(rounds, maxCallsPerRound)));
        return resultRef.get();
    }
}
