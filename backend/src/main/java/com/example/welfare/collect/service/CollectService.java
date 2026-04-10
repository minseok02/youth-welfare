package com.example.welfare.collect.service;

import com.example.welfare.collect.dto.BokjiroCentralDto;
import com.example.welfare.collect.dto.BokjiroLocalDto;
import com.example.welfare.collect.dto.YouthApiDto;
import com.example.welfare.collect.gateway.BokjiroCentralClient;
import com.example.welfare.collect.gateway.BokjiroLocalClient;
import com.example.welfare.collect.gateway.YouthApiClient;
import com.example.welfare.collect.mapper.WelfareServiceMapper;
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

    /**
     * 매일 새벽 2시 수집 배치
     */
    @Scheduled(cron = "0 0 2 * * *", zone = "Asia/Seoul")
    public void collectAll() {
        log.info("[CollectService] 공공API 수집 시작");
        collectYouth();
        collectBokjiroCentral();
        collectBokjiroLocal();
        log.info("[CollectService] 공공API 수집 완료");
    }

    public void collectYouth() {
        List<YouthApiDto.Item> items = youthApiClient.fetchAll();

        FieldQualityStats stats = new FieldQualityStats("YOUTH", items.size());
        RawFieldValidator.recordStatsYouth(items, stats);
        log.info(stats.summary());

        int saved = 0, skipped = 0;
        for (YouthApiDto.Item item : items) {
            if (!RawFieldValidator.isValidYouth(item)) {
                skipped++;
                continue;
            }
            try {
                saver.saveYouth(item);
                saved++;
            } catch (Exception e) {
                log.warn("[CollectService][YOUTH] 저장 실패 plcyNo={}: {}", item.getPlcyNo(), e.getMessage());
            }
        }
        log.info("[CollectService][YOUTH] 저장 완료: {}건 (skip: {}건)", saved, skipped);
    }

    public void collectBokjiroCentral() {
        List<BokjiroCentralDto.Item> items = bokjiroCentralClient.fetchAll();

        FieldQualityStats stats = new FieldQualityStats("BOKJIRO_CENTRAL", items.size());
        RawFieldValidator.recordStatsBokjiroCentral(items, stats);
        log.info(stats.summary());

        int saved = 0, skipped = 0;
        for (BokjiroCentralDto.Item item : items) {
            if (!RawFieldValidator.isValidBokjiroCentral(item)) {
                skipped++;
                continue;
            }
            try {
                saver.saveBokjiroCentral(item);
                saved++;
            } catch (Exception e) {
                log.warn("[CollectService][BOKJIRO_CENTRAL] 저장 실패 servId={}: {}", item.getServId(), e.getMessage());
            }
        }
        log.info("[CollectService][BOKJIRO_CENTRAL] 저장 완료: {}건 (skip: {}건)", saved, skipped);
    }

    public void collectBokjiroLocal() {
        List<BokjiroLocalDto.Item> items = bokjiroLocalClient.fetchAll();

        FieldQualityStats stats = new FieldQualityStats("BOKJIRO_LOCAL", items.size());
        RawFieldValidator.recordStatsBokjiroLocal(items, stats);
        log.info(stats.summary());

        int saved = 0, skipped = 0;
        for (BokjiroLocalDto.Item item : items) {
            if (!RawFieldValidator.isValidBokjiroLocal(item)) {
                skipped++;
                continue;
            }
            try {
                saver.saveBokjiroLocal(item);
                saved++;
            } catch (Exception e) {
                log.warn("[CollectService][BOKJIRO_LOCAL] 저장 실패 servId={}: {}", item.getServId(), e.getMessage());
            }
        }
        log.info("[CollectService][BOKJIRO_LOCAL] 저장 완료: {}건 (skip: {}건)", saved, skipped);
    }
}
