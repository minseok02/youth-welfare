package com.example.welfare.collect.service;

import com.example.welfare.collect.dto.BokjiroCentralDto;
import com.example.welfare.collect.gateway.BokjiroCentralClient;
import com.example.welfare.collect.mapper.WelfareServiceMapper;
import com.example.welfare.collect.support.CollectSourceRegistry;
import com.example.welfare.collect.support.ListCollectSourceBinding;
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
public class BokjiroCentralCollectSourceAdapter extends AbstractListCollectSourceAdapter<BokjiroCentralDto.Item> {

    private final BokjiroCentralClient bokjiroCentralClient;
    private final WelfareServiceMapper welfareServiceMapper;
    private final CollectItemSaver saver;
    private final BokjiroYouthFilter bokjiroYouthFilter;
    private final RawApiPayloadService rawApiPayloadService;
    private final CollectListResponsePolicy collectListResponsePolicy;
    private ListCollectSourceBinding<BokjiroCentralDto.Item> binding() {
        return CollectSourceRegistry.BOKJIRO_CENTRAL.listBinding(welfareServiceMapper);
    }

    @Override
    public CollectSource source() {
        return CollectSource.BOKJIRO_CENTRAL;
    }

    @Override
    protected List<BokjiroCentralDto.Item> fetchItems() {
        return bokjiroCentralClient.fetchAll();
    }

    @Override
    protected void recordStats(List<BokjiroCentralDto.Item> items, FieldQualityStats stats) {
        binding().recordStats(items, stats);
    }

    @Override
    protected void saveRawPayload(BokjiroCentralDto.Item item) {
        rawApiPayloadService.saveList(binding(), item);
    }

    @Override
    protected boolean isValid(BokjiroCentralDto.Item item) {
        return RawFieldValidator.isValidBokjiroCentral(item);
    }

    @Override
    protected boolean shouldCollect(BokjiroCentralDto.Item item) {
        return bokjiroYouthFilter.shouldCollect(item);
    }

    @Override
    protected void saveItem(BokjiroCentralDto.Item item) {
        saver.save(binding(), item);
    }

    @Override
    protected String itemId(BokjiroCentralDto.Item item) {
        return item.getServId();
    }

    @Override
    protected String failureIdLabel() {
        return "servId";
    }

    @Override
    protected void handleEmptyItems(List<BokjiroCentralDto.Item> items) {
        collectListResponsePolicy.ensureNonEmpty(source(), items);
    }
}
