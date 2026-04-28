package com.example.welfare.collect.service;

import com.example.welfare.collect.dto.YouthApiDto;
import com.example.welfare.collect.gateway.YouthApiClient;
import com.example.welfare.collect.validation.FieldQualityStats;
import com.example.welfare.collect.validation.RawFieldValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class YouthCollectSourceAdapter extends AbstractListCollectSourceAdapter<YouthApiDto.Item> {

    private final YouthApiClient youthApiClient;
    private final CollectItemSaver saver;
    private final RawApiPayloadService rawApiPayloadService;

    @Override
    public CollectSource source() {
        return CollectSource.YOUTH;
    }

    @Override
    protected List<YouthApiDto.Item> fetchItems() {
        return youthApiClient.fetchAll();
    }

    @Override
    protected void recordStats(List<YouthApiDto.Item> items, FieldQualityStats stats) {
        RawFieldValidator.recordStatsYouth(items, stats);
    }

    @Override
    protected void saveRawPayload(YouthApiDto.Item item) {
        rawApiPayloadService.saveYouthList(item);
    }

    @Override
    protected boolean isValid(YouthApiDto.Item item) {
        return RawFieldValidator.isValidYouth(item);
    }

    @Override
    protected void saveItem(YouthApiDto.Item item) {
        saver.saveYouth(item);
    }

    @Override
    protected String itemId(YouthApiDto.Item item) {
        return item.getPlcyNo();
    }

    @Override
    protected String failureIdLabel() {
        return "plcyNo";
    }
}
