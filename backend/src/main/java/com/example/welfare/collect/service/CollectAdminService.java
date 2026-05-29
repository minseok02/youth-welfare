package com.example.welfare.collect.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import com.example.welfare.collect.dto.InvertedAgeBackfillResponse;
import com.example.welfare.policy.entity.WelfareService;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Service
@RequiredArgsConstructor
public class CollectAdminService {

    private final CollectExecutionGuard collectExecutionGuard;
    private final CollectSourceExecutionService collectSourceExecutionService;
    private final YouthDetailCollectService youthDetailCollectService;
    private final InvertedAgeBackfillService invertedAgeBackfillService;

    public void collect(CollectSource source) {
        collectExecutionGuard.runExclusive(source.lockName(), () -> {
            collectSourceExecutionService.collectSource(source);
            if (source == CollectSource.GOV24) {
                collectSourceExecutionService.collectGov24Details();
                collectSourceExecutionService.collectGov24SupportConditions();
            }
        });
    }

    public CollectResult collect(CollectSource source, int maxCallsPerRun) {
        AtomicReference<CollectResult> resultRef = new AtomicReference<>();
        collectExecutionGuard.runExclusive(source.lockName(), () -> resultRef.set(switch (source) {
            case GOV24_DETAIL -> collectSourceExecutionService.collectGov24Details(maxCallsPerRun);
            case GOV24_SUPPORT_CONDITIONS -> collectSourceExecutionService.collectGov24SupportConditions(maxCallsPerRun);
            default -> throw new IllegalArgumentException("maxCallsPerRun override is not supported for source=" + source);
        }));
        return resultRef.get();
    }

    public CollectResult collect(CollectSource source, String sourceId) {
        AtomicReference<CollectResult> resultRef = new AtomicReference<>();
        collectExecutionGuard.runExclusive(source.lockName(), () -> resultRef.set(switch (source) {
            case GOV24_DETAIL -> collectSourceExecutionService.collectGov24DetailsForSourceId(sourceId);
            case GOV24_SUPPORT_CONDITIONS -> collectSourceExecutionService.collectGov24SupportConditionsForSourceId(sourceId);
            case BOKJIRO_DETAIL -> collectSourceExecutionService.collectBokjiroDetailsForSourceId(sourceId);
            case BOKJIRO_DETAIL_REFRESH -> collectSourceExecutionService.collectBokjiroDetailsRefreshForSourceId(sourceId);
            default -> throw new IllegalArgumentException("sourceId override is not supported for source=" + source);
        }));
        return resultRef.get();
    }

    public BokjiroDetailCollectService.GapFillResult collectBokjiroDetailGapFill(int rounds, int maxCallsPerRound) {
        AtomicReference<BokjiroDetailCollectService.GapFillResult> resultRef = new AtomicReference<>();
        collectExecutionGuard.runExclusive(CollectSource.BOKJIRO_DETAIL_GAP_FILL.lockName(), () ->
                resultRef.set(collectSourceExecutionService.collectBokjiroDetailGapFill(rounds, maxCallsPerRound)));
        return resultRef.get();
    }

    public CollectResult collectYouthDetails() {
        AtomicReference<CollectResult> resultRef = new AtomicReference<>();
        collectExecutionGuard.runExclusive(CollectRuntimeLaneCatalog.YOUTH_DETAILS_LOCK_NAME, () ->
                resultRef.set(youthDetailCollectService.collectYouthDetails()));
        return resultRef.get();
    }

    public InvertedAgeBackfillResponse backfillInvertedAgeRanges(List<WelfareService.SourceType> sourceTypes,
                                                                 int limitPerSource) {
        AtomicReference<InvertedAgeBackfillResponse> resultRef = new AtomicReference<>();
        collectExecutionGuard.runExclusive(CollectRuntimeLaneCatalog.INVERTED_AGE_BACKFILL_LOCK_NAME, () ->
                resultRef.set(invertedAgeBackfillService.backfill(sourceTypes, limitPerSource)));
        return resultRef.get();
    }
}
