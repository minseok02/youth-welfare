package com.example.welfare.policy.repository;

import com.example.welfare.policy.entity.ServiceTag;
import com.example.welfare.policy.entity.WelfareService;

import java.util.List;

public interface SearchYouthRelevanceReadRepository {

    List<WelfareService> findBackfillTargetServices();

    List<ServiceTag> findTagsByServiceId(Long serviceId);

    List<ServiceTag> findTagsByServiceIds(List<Long> serviceIds);
}
