package com.example.welfare.collect.service;

import com.example.welfare.collect.dto.BokjiroLocalDto;
import com.example.welfare.collect.gateway.BokjiroLocalClient;
import com.example.welfare.collect.mapper.WelfareServiceMapper;
import com.example.welfare.collect.validation.BokjiroYouthFilter;
import com.example.welfare.collect.validation.FieldQualityStats;
import com.example.welfare.collect.validation.RawFieldValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BokjiroLocalCollectSourceAdapter extends AbstractListCollectSourceAdapter<BokjiroLocalDto.Item> {

    private final BokjiroLocalClient bokjiroLocalClient;
    private final WelfareServiceMapper welfareServiceMapper;
    private final CollectItemSaver saver;
    private final BokjiroYouthFilter bokjiroYouthFilter;
    private final RawApiPayloadService rawApiPayloadService;

    @Override
    public CollectSource source() {
        return CollectSource.BOKJIRO_LOCAL;
    }

    @Override
    protected List<BokjiroLocalDto.Item> fetchItems() {
        return bokjiroLocalClient.fetchAll();
    }

    @Override
    protected void recordStats(List<BokjiroLocalDto.Item> items, FieldQualityStats stats) {
        RawFieldValidator.recordStatsBokjiroLocal(items, stats);
    }

    @Override
    protected void saveRawPayload(BokjiroLocalDto.Item item) {
        rawApiPayloadService.saveList(source().toWelfareSourceType(), item.getServId(), item);
    }

    @Override
    protected boolean isValid(BokjiroLocalDto.Item item) {
        return RawFieldValidator.isValidBokjiroLocal(item);
    }

    @Override
    protected boolean shouldCollect(BokjiroLocalDto.Item item) {
        return bokjiroYouthFilter.shouldCollect(item);
    }

    @Override
    protected void saveItem(BokjiroLocalDto.Item item) {
        saver.save(CollectItemSaver.SaveCommand.builder()
                .sourceType(source().toWelfareSourceType())
                .sourceId(item.getServId())
                .incoming(welfareServiceMapper.fromBokjiroLocal(item))
                .regions(entity -> welfareServiceMapper.regionsFromBokjiroLocal(item, entity))
                .tags(entity -> welfareServiceMapper.tagsFromBokjiroLocal(item, entity))
                .aggregate(welfareServiceMapper.toNormalizedBokjiroLocal(item, null))
                .build());
    }

    @Override
    protected String itemId(BokjiroLocalDto.Item item) {
        return item.getServId();
    }

    @Override
    protected String failureIdLabel() {
        return "servId";
    }

    @Override
    protected void handleEmptyItems(List<BokjiroLocalDto.Item> items) {
        if (items.isEmpty()) {
            log.warn("[CollectSourceAdapter][{}] 수집 결과 0건입니다. 외부 API 제한 또는 일시 장애 가능성이 있어 기존 적재 데이터는 유지합니다.",
                    source().jobName());
        }
    }
}
