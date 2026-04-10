package com.example.welfare.collect.service;

import com.example.welfare.collect.dto.BokjiroCentralDto;
import com.example.welfare.collect.dto.BokjiroLocalDto;
import com.example.welfare.collect.dto.YouthApiDto;
import com.example.welfare.collect.mapper.WelfareServiceMapper;
import com.example.welfare.policy.entity.ServiceRegion;
import com.example.welfare.policy.entity.ServiceTag;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.ServiceRegionRepository;
import com.example.welfare.policy.repository.ServiceTagRepository;
import com.example.welfare.policy.repository.WelfareServiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 아이템 단위 저장 — 각 아이템을 별도 트랜잭션으로 처리하여
 * 한 아이템 실패가 전체 배치를 롤백시키지 않도록 분리
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CollectItemSaver {

    private final WelfareServiceMapper mapper;
    private final WelfareServiceRepository welfareServiceRepository;
    private final ServiceRegionRepository regionRepository;
    private final ServiceTagRepository tagRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveYouth(YouthApiDto.Item item) {
        WelfareService entity = upsertService(
                WelfareService.SourceType.YOUTH,
                item.getPlcyNo(),
                mapper.fromYouth(item)
        );
        upsertRegions(entity, mapper.regionsFromYouth(item, entity));
        upsertTags(entity, mapper.tagsFromYouth(item, entity));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveBokjiroCentral(BokjiroCentralDto.Item item) {
        WelfareService entity = upsertService(
                WelfareService.SourceType.BOKJIRO_CENTRAL,
                item.getServId(),
                mapper.fromBokjiroCentral(item)
        );
        upsertRegions(entity, mapper.regionsFromBokjiroCentral(item, entity));
        upsertTags(entity, mapper.tagsFromBokjiroCentral(item, entity));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveBokjiroLocal(BokjiroLocalDto.Item item) {
        WelfareService entity = upsertService(
                WelfareService.SourceType.BOKJIRO_LOCAL,
                item.getServId(),
                mapper.fromBokjiroLocal(item)
        );
        upsertRegions(entity, mapper.regionsFromBokjiroLocal(item, entity));
        upsertTags(entity, mapper.tagsFromBokjiroLocal(item, entity));
    }

    private WelfareService upsertService(WelfareService.SourceType sourceType,
                                          String sourceId, WelfareService incoming) {
        Optional<WelfareService> existing = welfareServiceRepository
                .findBySourceTypeAndSourceId(sourceType, sourceId);
        if (existing.isPresent()) {
            WelfareService ws = existing.get();
            ws.updateFromCollect(incoming);
            return ws;
        } else {
            return welfareServiceRepository.saveAndFlush(incoming);
        }
    }

    private void upsertRegions(WelfareService service, List<ServiceRegion> regions) {
        if (regions.isEmpty()) return;
        regionRepository.deleteByServiceId(service.getId());
        regionRepository.saveAll(regions);
    }

    private void upsertTags(WelfareService service, List<ServiceTag> tags) {
        for (ServiceTag tag : tags) {
            tagRepository.upsert(
                    service.getId(),
                    tag.getTagType().name(),
                    tag.getTagValue()
            );
        }
    }
}
