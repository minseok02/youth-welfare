package com.example.welfare.collect.service;

import com.example.welfare.collect.dto.BokjiroCentralDto;
import com.example.welfare.collect.dto.BokjiroLocalDto;
import com.example.welfare.collect.dto.YouthApiDto;
import com.example.welfare.collect.gateway.BokjiroCentralClient;
import com.example.welfare.collect.gateway.BokjiroLocalClient;
import com.example.welfare.collect.gateway.YouthApiClient;
import com.example.welfare.collect.mapper.WelfareServiceMapper;
import com.example.welfare.policy.entity.ServiceRegion;
import com.example.welfare.policy.entity.ServiceTag;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.ServiceRegionRepository;
import com.example.welfare.policy.repository.ServiceTagRepository;
import com.example.welfare.policy.repository.WelfareServiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CollectService {

    private final YouthApiClient youthApiClient;
    private final BokjiroCentralClient bokjiroCentralClient;
    private final BokjiroLocalClient bokjiroLocalClient;
    private final WelfareServiceMapper mapper;

    private final WelfareServiceRepository welfareServiceRepository;
    private final ServiceRegionRepository regionRepository;
    private final ServiceTagRepository tagRepository;

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

    @Transactional
    public void collectYouth() {
        List<YouthApiDto.Item> items = youthApiClient.fetchAll();
        int saved = 0;
        for (YouthApiDto.Item item : items) {
            try {
                WelfareService entity = upsertService(
                        WelfareService.SourceType.YOUTH,
                        item.getBizId(),
                        mapper.fromYouth(item)
                );
                upsertRegions(entity, mapper.regionsFromYouth(item, entity));
                upsertTags(entity, mapper.tagsFromYouth(item, entity));
                saved++;
            } catch (Exception e) {
                log.warn("[CollectService][YOUTH] 저장 실패 bizId={}: {}", item.getBizId(), e.getMessage());
            }
        }
        log.info("[CollectService][YOUTH] 저장 완료: {}건", saved);
    }

    @Transactional
    public void collectBokjiroCentral() {
        List<BokjiroCentralDto.Item> items = bokjiroCentralClient.fetchAll();
        int saved = 0;
        for (BokjiroCentralDto.Item item : items) {
            try {
                WelfareService entity = upsertService(
                        WelfareService.SourceType.BOKJIRO_CENTRAL,
                        item.getServId(),
                        mapper.fromBokjiroCentral(item)
                );
                upsertRegions(entity, mapper.regionsFromBokjiroCentral(item, entity));
                upsertTags(entity, mapper.tagsFromBokjiroCentral(item, entity));
                saved++;
            } catch (Exception e) {
                log.warn("[CollectService][BOKJIRO_CENTRAL] 저장 실패 servId={}: {}", item.getServId(), e.getMessage());
            }
        }
        log.info("[CollectService][BOKJIRO_CENTRAL] 저장 완료: {}건", saved);
    }

    @Transactional
    public void collectBokjiroLocal() {
        List<BokjiroLocalDto.Item> items = bokjiroLocalClient.fetchAll();
        int saved = 0;
        for (BokjiroLocalDto.Item item : items) {
            try {
                WelfareService entity = upsertService(
                        WelfareService.SourceType.BOKJIRO_LOCAL,
                        item.getServId(),
                        mapper.fromBokjiroLocal(item)
                );
                upsertRegions(entity, mapper.regionsFromBokjiroLocal(item, entity));
                upsertTags(entity, mapper.tagsFromBokjiroLocal(item, entity));
                saved++;
            } catch (Exception e) {
                log.warn("[CollectService][BOKJIRO_LOCAL] 저장 실패 servId={}: {}", item.getServId(), e.getMessage());
            }
        }
        log.info("[CollectService][BOKJIRO_LOCAL] 저장 완료: {}건", saved);
    }

    /**
     * source_type + source_id 기준 UPSERT
     * 기존 레코드가 있으면 필드 업데이트, 없으면 신규 저장
     */
    private WelfareService upsertService(WelfareService.SourceType sourceType,
                                          String sourceId, WelfareService incoming) {
        Optional<WelfareService> existing = welfareServiceRepository
                .findBySourceTypeAndSourceId(sourceType, sourceId);

        if (existing.isPresent()) {
            // 기존 레코드 업데이트 — JPA dirty checking
            WelfareService ws = existing.get();
            copyFields(ws, incoming);
            return ws;
        } else {
            return welfareServiceRepository.save(incoming);
        }
    }

    private void copyFields(WelfareService target, WelfareService source) {
        // Entity의 필드를 직접 업데이트하는 메서드
        // WelfareService에 updateFromCollect() 메서드 추가 필요
        target.updateFromCollect(source);
    }

    private void upsertRegions(WelfareService service, List<ServiceRegion> regions) {
        if (regions.isEmpty()) return;
        // 기존 지역 삭제 후 재삽입
        regionRepository.deleteByServiceId(service.getId());
        regionRepository.saveAll(regions);
    }

    private void upsertTags(WelfareService service, List<ServiceTag> tags) {
        // UNIQUE KEY uq_st 기반 UPSERT — 중복 삽입 방지 (rule_base_score 이중합산 버그 방지)
        for (ServiceTag tag : tags) {
            tagRepository.upsert(
                    service.getId(),
                    tag.getTagType().name(),
                    tag.getTagValue()
            );
        }
    }
}
