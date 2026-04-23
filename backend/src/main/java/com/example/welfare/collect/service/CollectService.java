package com.example.welfare.collect.service;

import com.example.welfare.collect.dto.BokjiroCentralDto;
import com.example.welfare.collect.dto.BokjiroLocalDto;
import com.example.welfare.collect.dto.YouthApiDto;
import com.example.welfare.collect.gateway.BokjiroCentralClient;
import com.example.welfare.collect.gateway.BokjiroLocalClient;
import com.example.welfare.collect.gateway.YouthApiClient;
import com.example.welfare.collect.mapper.WelfareServiceMapper;
import com.example.welfare.collect.validation.BokjiroYouthFilter;
import com.example.welfare.collect.validation.FieldQualityStats;
import com.example.welfare.collect.validation.RawFieldValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CollectService {

    private final YouthApiClient youthApiClient;
    private final BokjiroCentralClient bokjiroCentralClient;
    private final BokjiroLocalClient bokjiroLocalClient;
    private final WelfareServiceMapper mapper;
    private final CollectItemSaver saver;
    private final BokjiroDetailCollectService bokjiroDetailCollectService;
    private final BokjiroYouthFilter bokjiroYouthFilter;
    private final RawApiPayloadService rawApiPayloadService;
    private final CollectExecutionGuard collectExecutionGuard;
    private final ApiSyncLogService apiSyncLogService;

    /**
     * 매일 새벽 2시 수집 배치
     */
    @Scheduled(cron = "0 0 2 * * *", zone = "Asia/Seoul")
    public void collectAll() {
        collectExecutionGuard.runExclusive("collect-all", () -> {
            log.info("[CollectService] 공공API 수집 시작");
            runSource("YOUTH", this::collectYouthInternal);
            runSource("BOKJIRO_CENTRAL", this::collectBokjiroCentralInternal);
            runSource("BOKJIRO_LOCAL", this::collectBokjiroLocalInternal);
            runSource("BOKJIRO_DETAIL", this::collectBokjiroDetailsInternal);
            log.info("[CollectService] 공공API 수집 완료");
        });
    }

    public void collectYouth() {
        collectExecutionGuard.runExclusive("collect-youth", () -> apiSyncLogService.runWithLog("YOUTH", this::collectYouthInternal));
    }

    public void collectBokjiroCentral() {
        collectExecutionGuard.runExclusive("collect-bokjiro-central", () -> apiSyncLogService.runWithLog("BOKJIRO_CENTRAL", this::collectBokjiroCentralInternal));
    }

    public void collectBokjiroLocal() {
        collectExecutionGuard.runExclusive("collect-bokjiro-local", () -> apiSyncLogService.runWithLog("BOKJIRO_LOCAL", this::collectBokjiroLocalInternal));
    }

    public void collectBokjiroDetails() {
        collectExecutionGuard.runExclusive("collect-bokjiro-details", () -> apiSyncLogService.runWithLog("BOKJIRO_DETAIL", this::collectBokjiroDetailsInternal));
    }

    private CollectResult collectYouthInternal() {
        List<YouthApiDto.Item> items = youthApiClient.fetchAll();

        FieldQualityStats stats = new FieldQualityStats("YOUTH", items.size());
        RawFieldValidator.recordStatsYouth(items, stats);
        log.info(stats.summary());

        int saved = 0, skipped = 0, failed = 0;
        for (YouthApiDto.Item item : items) {
            rawApiPayloadService.saveYouthList(item);
            if (!RawFieldValidator.isValidYouth(item)) {
                skipped++;
                continue;
            }
            try {
                saver.saveYouth(item);
                saved++;
            } catch (Exception e) {
                failed++;
                log.warn("[CollectService][YOUTH] 저장 실패 plcyNo={}: {}", item.getPlcyNo(), e.getMessage());
            }
        }
        log.info("[CollectService][YOUTH] 저장 완료: {}건 (skip: {}건, failed: {}건)", saved, skipped, failed);
        return CollectResult.of(items.size(), saved, skipped, 0, failed);
    }

    private CollectResult collectBokjiroCentralInternal() {
        List<BokjiroCentralDto.Item> items = bokjiroCentralClient.fetchAll();
        if (items.isEmpty()) {
            log.warn("[CollectService][BOKJIRO_CENTRAL] 수집 결과 0건입니다. 외부 API 제한 또는 일시 장애 가능성이 있어 기존 적재 데이터는 유지합니다.");
        }

        FieldQualityStats stats = new FieldQualityStats("BOKJIRO_CENTRAL", items.size());
        RawFieldValidator.recordStatsBokjiroCentral(items, stats);
        log.info(stats.summary());

        int saved = 0, skipped = 0, filteredOut = 0, failed = 0;
        for (BokjiroCentralDto.Item item : items) {
            rawApiPayloadService.saveBokjiroCentralList(item);
            if (!RawFieldValidator.isValidBokjiroCentral(item)) {
                skipped++;
                continue;
            }
            if (!bokjiroYouthFilter.shouldCollect(item)) {
                filteredOut++;
                continue;
            }
            try {
                saver.saveBokjiroCentral(item);
                saved++;
            } catch (Exception e) {
                failed++;
                log.warn("[CollectService][BOKJIRO_CENTRAL] 저장 실패 servId={}: {}", item.getServId(), e.getMessage());
            }
        }
        log.info("[CollectService][BOKJIRO_CENTRAL] 저장 완료: {}건 (skip: {}건, filtered: {}건, failed: {}건)", saved, skipped, filteredOut, failed);
        return CollectResult.of(items.size(), saved, skipped, filteredOut, failed);
    }

    private CollectResult collectBokjiroLocalInternal() {
        List<BokjiroLocalDto.Item> items = bokjiroLocalClient.fetchAll();
        if (items.isEmpty()) {
            log.warn("[CollectService][BOKJIRO_LOCAL] 수집 결과 0건입니다. 외부 API 제한 또는 일시 장애 가능성이 있어 기존 적재 데이터는 유지합니다.");
        }

        FieldQualityStats stats = new FieldQualityStats("BOKJIRO_LOCAL", items.size());
        RawFieldValidator.recordStatsBokjiroLocal(items, stats);
        log.info(stats.summary());

        int saved = 0, skipped = 0, filteredOut = 0, failed = 0;
        for (BokjiroLocalDto.Item item : items) {
            rawApiPayloadService.saveBokjiroLocalList(item);
            if (!RawFieldValidator.isValidBokjiroLocal(item)) {
                skipped++;
                continue;
            }
            if (!bokjiroYouthFilter.shouldCollect(item)) {
                filteredOut++;
                continue;
            }
            try {
                saver.saveBokjiroLocal(item);
                saved++;
            } catch (Exception e) {
                failed++;
                log.warn("[CollectService][BOKJIRO_LOCAL] 저장 실패 servId={}: {}", item.getServId(), e.getMessage());
            }
        }
        log.info("[CollectService][BOKJIRO_LOCAL] 저장 완료: {}건 (skip: {}건, filtered: {}건, failed: {}건)", saved, skipped, filteredOut, failed);
        return CollectResult.of(items.size(), saved, skipped, filteredOut, failed);
    }

    private CollectResult collectBokjiroDetailsInternal() {
        CollectResult result = bokjiroDetailCollectService.collectBokjiroDetailsResult();
        log.info("[CollectService][BOKJIRO_DETAIL] 저장 완료: {}건", result.savedCount());
        return result;
    }

    private void runSource(String sourceName, ApiSyncLogService.CollectTask task) {
        try {
            apiSyncLogService.runWithLog(sourceName, task);
        } catch (Exception e) {
            log.warn("[CollectService][{}] 수집 실패 - 다음 source 계속 진행: {}", sourceName, e.getMessage(), e);
        }
    }
}
