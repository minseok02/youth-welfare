package com.example.welfare.collect.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class BokjiroDetailCollectSourceAdapter implements CollectSourceAdapter {

    private final BokjiroDetailCollectService bokjiroDetailCollectService;

    @Override
    public CollectSource source() {
        return CollectSource.BOKJIRO_DETAIL;
    }

    @Override
    public CollectResult collect() {
        CollectResult result = bokjiroDetailCollectService.collectBokjiroDetailsResult();
        log.info("[CollectSourceAdapter][{}] 저장 완료: {}건", source().jobName(), result.savedCount());
        return result;
    }
}
