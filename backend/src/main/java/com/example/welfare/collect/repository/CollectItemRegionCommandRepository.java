package com.example.welfare.collect.repository;

import com.example.welfare.policy.entity.ServiceRegion;

import java.util.List;

public interface CollectItemRegionCommandRepository {

    void replaceAll(Long serviceId, List<ServiceRegion> regions);
}
