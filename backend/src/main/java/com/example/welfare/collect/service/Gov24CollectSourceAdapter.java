package com.example.welfare.collect.service;

import com.example.welfare.collect.dto.Gov24ServiceListDto;
import com.example.welfare.collect.gateway.Gov24Client;
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
public class Gov24CollectSourceAdapter extends AbstractListCollectSourceAdapter<Gov24ServiceListDto.Item> {

    private final Gov24Client gov24Client;
    private final WelfareServiceMapper welfareServiceMapper;
    private final CollectItemSaver saver;
    private final RawApiPayloadService rawApiPayloadService;
    private final CollectListResponsePolicy collectListResponsePolicy;

    private ListCollectSourceBinding<Gov24ServiceListDto.Item> binding() {
        return CollectSourceRegistry.GOV24.listBinding(welfareServiceMapper);
    }

    @Override
    public CollectSource source() {
        return CollectSource.GOV24;
    }

    @Override
    protected List<Gov24ServiceListDto.Item> fetchItems() {
        return gov24Client.fetchAll();
    }

    @Override
    protected void recordStats(List<Gov24ServiceListDto.Item> items, FieldQualityStats stats) {
        binding().recordStats(items, stats);
    }

    @Override
    protected boolean saveRawPayload(Gov24ServiceListDto.Item item) {
        return rawApiPayloadService.saveList(binding(), item);
    }

    @Override
    protected boolean isValid(Gov24ServiceListDto.Item item) {
        return RawFieldValidator.isValidGov24(item);
    }

    @Override
    protected void saveItem(Gov24ServiceListDto.Item item) {
        saver.save(binding(), item);
    }

    @Override
    protected String itemId(Gov24ServiceListDto.Item item) {
        return item.getServiceId();
    }

    @Override
    protected String failureIdLabel() {
        return "serviceId";
    }

    @Override
    protected void handleEmptyItems(List<Gov24ServiceListDto.Item> items) {
        collectListResponsePolicy.ensureNonEmpty(source(), items);
    }
}
