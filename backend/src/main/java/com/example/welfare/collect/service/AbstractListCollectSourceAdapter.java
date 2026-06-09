package com.example.welfare.collect.service;

import com.example.welfare.collect.validation.FieldQualityStats;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public abstract class AbstractListCollectSourceAdapter<T> implements CollectSourceAdapter {

    @Override
    public CollectResult collect() {
        List<T> items = fetchItems();
        handleEmptyItems(items);

        FieldQualityStats stats = new FieldQualityStats(source().jobName(), items.size());
        recordStats(items, stats);
        log.info(stats.summary());

        int saved = 0;
        int skipped = 0;
        int filteredOut = 0;
        int failed = 0;

        for (T item : items) {
            try {
                if (!saveRawPayload(item)) {
                    failed++;
                    log.warn("[CollectSourceAdapter][{}] raw 저장 실패 {}={}",
                            source().jobName(), failureIdLabel(), itemId(item));
                    continue;
                }
                if (!isValid(item)) {
                    skipped++;
                    continue;
                }
                if (!shouldCollect(item)) {
                    filteredOut++;
                    continue;
                }
                saveItem(item);
                saved++;
            } catch (Exception e) {
                failed++;
                log.warn("[CollectSourceAdapter][{}] 저장 실패 {}={} errorType={}",
                        source().jobName(), failureIdLabel(), itemId(item), e.getClass().getSimpleName());
            }
        }

        log.info("[CollectSourceAdapter][{}] 저장 완료: {}건 (skip: {}건, filtered: {}건, failed: {}건)",
                source().jobName(), saved, skipped, filteredOut, failed);
        CollectResult result = CollectResult.of(items.size(), saved, skipped, filteredOut, failed);
        afterCollect(items, result);
        return result;
    }

    protected abstract List<T> fetchItems();

    protected abstract void recordStats(List<T> items, FieldQualityStats stats);

    protected abstract boolean saveRawPayload(T item);

    protected abstract boolean isValid(T item);

    protected boolean shouldCollect(T item) {
        return true;
    }

    protected abstract void saveItem(T item);

    protected abstract String itemId(T item);

    protected abstract String failureIdLabel();

    protected void handleEmptyItems(List<T> items) {
    }

    protected void afterCollect(List<T> items, CollectResult result) {
    }
}
