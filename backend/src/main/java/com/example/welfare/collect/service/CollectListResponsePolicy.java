package com.example.welfare.collect.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Slf4j
@Service
public class CollectListResponsePolicy {

    private static final Set<CollectSource> FAIL_ON_EMPTY_SOURCES = Set.of(
            CollectSource.YOUTH,
            CollectSource.BOKJIRO_CENTRAL,
            CollectSource.BOKJIRO_LOCAL
    );

    public void ensureNonEmpty(CollectSource source, List<?> items) {
        if (!items.isEmpty()) {
            return;
        }
        if (!FAIL_ON_EMPTY_SOURCES.contains(source)) {
            return;
        }

        log.error("[CollectListResponsePolicy] 빈 응답을 외부 장애로 판단해 수집 실패 처리합니다. source={}", source.jobName());
        throw new CustomException(ErrorCode.COLLECT_API_FAILED);
    }
}
