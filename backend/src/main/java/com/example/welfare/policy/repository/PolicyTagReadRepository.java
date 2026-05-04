package com.example.welfare.policy.repository;

import com.example.welfare.policy.entity.ServiceTag;

import java.util.List;
import java.util.Map;

public interface PolicyTagReadRepository {

    List<ServiceTag> findByServiceId(Long serviceId);

    Map<Long, List<ServiceTag>> findByServiceIds(List<Long> serviceIds);
}
