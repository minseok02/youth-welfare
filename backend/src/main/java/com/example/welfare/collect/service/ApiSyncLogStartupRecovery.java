package com.example.welfare.collect.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApiSyncLogStartupRecovery implements ApplicationRunner {

    private final ApiSyncLogService apiSyncLogService;

    @Override
    public void run(ApplicationArguments args) {
        int recovered = apiSyncLogService.recoverInterruptedRuns();
        if (recovered > 0) {
            log.info("[ApiSyncLogStartupRecovery] 이전 수집 RUNNING 로그 {}건 복구 완료", recovered);
        }
    }
}
