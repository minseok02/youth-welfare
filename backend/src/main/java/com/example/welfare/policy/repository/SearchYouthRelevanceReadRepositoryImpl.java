package com.example.welfare.policy.repository;

import com.example.welfare.policy.entity.ServiceTag;
import com.example.welfare.policy.entity.WelfareService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class SearchYouthRelevanceReadRepositoryImpl implements SearchYouthRelevanceReadRepository {

    private final WelfareServiceRepository welfareServiceRepository;
    private final ServiceTagRepository serviceTagRepository;

    @Override
    public List<WelfareService> findBackfillTargetServices() {
        return welfareServiceRepository.findAll();
    }

    @Override
    public List<ServiceTag> findTagsByServiceIds(List<Long> serviceIds) {
        if (serviceIds == null || serviceIds.isEmpty()) {
            return List.of();
        }
        return serviceTagRepository.findByServiceIdIn(serviceIds);
    }
}
