package com.example.welfare.collect.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class Gov24SupportConditionsCollectSourceAdapter implements CollectSourceAdapter {

    private final Gov24SupportConditionsCollectService gov24SupportConditionsCollectService;

    @Override
    public CollectSource source() {
        return CollectSource.GOV24_SUPPORT_CONDITIONS;
    }

    @Override
    public CollectResult collect() {
        CollectResult result = gov24SupportConditionsCollectService.collectGov24SupportConditions();
        log.info("[CollectSourceAdapter][{}] 저장 완료: {}건", source().jobName(), result.savedCount());
        return result;
    }
}
