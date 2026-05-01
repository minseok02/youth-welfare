package com.example.welfare.collect.support;

import com.example.welfare.collect.normalization.NormalizedPolicyAggregate;
import com.example.welfare.collect.service.CollectItemSaver;
import com.example.welfare.collect.validation.FieldQualityStats;
import com.example.welfare.policy.entity.ServiceRegion;
import com.example.welfare.policy.entity.ServiceTag;
import com.example.welfare.policy.entity.WelfareService;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * list source별 source id / stats / entity mapper / sidecar aggregate mapper를 한 곳에 모은다.
 */
public record ListCollectSourceBinding<T>(
        WelfareService.SourceType sourceType,
        Function<T, String> sourceIdExtractor,
        BiConsumer<List<T>, FieldQualityStats> statsRecorder,
        Function<T, WelfareService> incomingMapper,
        BiFunction<T, WelfareService, List<ServiceRegion>> regionsMapper,
        BiFunction<T, WelfareService, List<ServiceTag>> tagsMapper,
        Function<T, NormalizedPolicyAggregate> aggregateMapper
) {

    public String sourceId(T item) {
        return sourceIdExtractor.apply(item);
    }

    public void recordStats(List<T> items, FieldQualityStats stats) {
        statsRecorder.accept(items, stats);
    }

    public CollectItemSaver.SaveCommand toSaveCommand(T item) {
        return CollectItemSaver.SaveCommand.builder()
                .sourceType(sourceType)
                .sourceId(sourceId(item))
                .incoming(incomingMapper.apply(item))
                .regions(entity -> regionsMapper.apply(item, entity))
                .tags(entity -> tagsMapper.apply(item, entity))
                .aggregate(aggregateMapper.apply(item))
                .build();
    }
}
