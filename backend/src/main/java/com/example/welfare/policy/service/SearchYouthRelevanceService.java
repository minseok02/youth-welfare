package com.example.welfare.policy.service;

import com.example.welfare.policy.dto.SearchYouthRelevanceBackfillResponse;
import com.example.welfare.policy.entity.ServiceTag;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.SearchYouthRelevanceReadRepository;
import com.example.welfare.policy.repository.ServiceTagRepository;
import com.example.welfare.recommend.support.RecommendationYouthRelevanceSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchYouthRelevanceService {

    private final SearchYouthRelevanceReadRepository searchYouthRelevanceReadRepository;
    private final ServiceTagRepository serviceTagRepository;
    private final RecommendationYouthRelevanceSupport recommendationYouthRelevanceSupport;

    public boolean compute(WelfareService service, List<ServiceTag> tags) {
        return recommendationYouthRelevanceSupport.isYouthRelevant(service, tags != null ? tags : Collections.emptyList());
    }

    public void refreshForService(WelfareService service, List<ServiceTag> tags) {
        service.updateSearchYouthRelevant(compute(service, tags));
    }

    @Transactional
    public SearchYouthRelevanceBackfillResponse backfillAll() {
        List<WelfareService> services = searchYouthRelevanceReadRepository.findBackfillTargetServices();
        if (services.isEmpty()) {
            return new SearchYouthRelevanceBackfillResponse(0, 0, 0, 0);
        }

        List<Long> serviceIds = services.stream()
                .map(WelfareService::getId)
                .toList();
        Map<Long, List<ServiceTag>> tagsByServiceId = serviceTagRepository.findByServiceIdIn(serviceIds).stream()
                .collect(Collectors.groupingBy(tag -> tag.getService().getId()));

        int updatedCount = 0;
        int relevantCount = 0;
        int excludedCount = 0;

        for (WelfareService service : services) {
            boolean next = compute(service, tagsByServiceId.get(service.getId()));
            if (service.isSearchYouthRelevant() != next) {
                updatedCount++;
                service.updateSearchYouthRelevant(next);
            }
            if (next) {
                relevantCount++;
            } else {
                excludedCount++;
            }
        }

        log.info("[SearchYouthRelevanceService] 백필 완료 processed={} updated={} relevant={} excluded={}",
                services.size(), updatedCount, relevantCount, excludedCount);
        return new SearchYouthRelevanceBackfillResponse(
                services.size(),
                updatedCount,
                relevantCount,
                excludedCount
        );
    }
}
