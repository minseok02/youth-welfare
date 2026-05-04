package com.example.welfare.collect.service;

import com.example.welfare.collect.dto.YouthApiDto;
import com.example.welfare.collect.gateway.YouthApiClient;
import com.example.welfare.collect.mapper.WelfareServiceMapper;
import com.example.welfare.collect.support.CollectSourceRegistry;
import com.example.welfare.collect.support.ListCollectSourceBinding;
import com.example.welfare.collect.validation.FieldQualityStats;
import com.example.welfare.collect.validation.RawFieldValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class YouthCollectSourceAdapter extends AbstractListCollectSourceAdapter<YouthApiDto.Item> {

    private final YouthApiClient youthApiClient;
    private final WelfareServiceMapper welfareServiceMapper;
    private final CollectItemSaver saver;
    private final RawApiPayloadService rawApiPayloadService;
    private final CollectListResponsePolicy collectListResponsePolicy;
    private ListCollectSourceBinding<YouthApiDto.Item> binding() {
        return CollectSourceRegistry.YOUTH.listBinding(welfareServiceMapper);
    }

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
        binding().recordStats(items, stats);
    }

    @Override
    protected void saveRawPayload(YouthApiDto.Item item) {
        rawApiPayloadService.saveList(binding(), item);
    }

    @Override
    protected boolean isValid(YouthApiDto.Item item) {
        return RawFieldValidator.isValidYouth(item);
    }

    @Override
    protected void saveItem(YouthApiDto.Item item) {
        saver.save(binding(), item);
    }

    @Override
    protected String itemId(YouthApiDto.Item item) {
        return item.getPlcyNo();
    }

    @Override
    protected String failureIdLabel() {
        return "plcyNo";
    }

    @Override
    protected void handleEmptyItems(List<YouthApiDto.Item> items) {
        collectListResponsePolicy.ensureNonEmpty(source(), items);
    }
}
