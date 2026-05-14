package com.example.welfare.collect.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class Gov24DetailCollectSourceAdapter implements CollectSourceAdapter {

    private final Gov24DetailCollectService gov24DetailCollectService;

    @Override
    public CollectSource source() {
        return CollectSource.GOV24_DETAIL;
    }

    @Override
    public CollectResult collect() {
        CollectResult result = gov24DetailCollectService.collectGov24Details();
        log.info("[CollectSourceAdapter][{}] 저장 완료: {}건", source().jobName(), result.savedCount());
        return result;
    }
}
