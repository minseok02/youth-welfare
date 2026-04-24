package com.example.welfare.collect.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 단일 애플리케이션 인스턴스 기준 수집 중복 실행 방지 가드.
 */
@Slf4j
@Component
public class CollectExecutionGuard {

    private final AtomicBoolean running = new AtomicBoolean(false);

    public void runExclusive(String jobName, Runnable task) {
        if (!running.compareAndSet(false, true)) {
            log.warn("[CollectExecutionGuard] 이미 수집 작업이 실행 중입니다. request={}", jobName);
            throw new CustomException(ErrorCode.COLLECT_ALREADY_RUNNING);
        }

        try {
            log.info("[CollectExecutionGuard] 수집 실행 시작 job={}", jobName);
            task.run();
        } finally {
            running.set(false);
            log.info("[CollectExecutionGuard] 수집 실행 종료 job={}", jobName);
        }
    }
}
