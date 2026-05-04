package com.example.welfare.collect.service;

import com.example.welfare.collect.dto.BokjiroLocalDto;
import com.example.welfare.collect.gateway.BokjiroLocalClient;
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
public class BokjiroLocalCollectSourceAdapter extends AbstractListCollectSourceAdapter<BokjiroLocalDto.Item> {

    private final BokjiroLocalClient bokjiroLocalClient;
    private final WelfareServiceMapper welfareServiceMapper;
    private final CollectItemSaver saver;
    private final BokjiroYouthFilter bokjiroYouthFilter;
    private final RawApiPayloadService rawApiPayloadService;
    private final CollectListResponsePolicy collectListResponsePolicy;
    private ListCollectSourceBinding<BokjiroLocalDto.Item> binding() {
        return CollectSourceRegistry.BOKJIRO_LOCAL.listBinding(welfareServiceMapper);
    }

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
        binding().recordStats(items, stats);
    }

    @Override
    protected boolean saveRawPayload(BokjiroLocalDto.Item item) {
        return rawApiPayloadService.saveList(binding(), item);
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
        saver.save(binding(), item);
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
        collectListResponsePolicy.ensureNonEmpty(source(), items);
    }
}
