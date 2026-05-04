package com.example.welfare.policy.repository;

import com.example.welfare.policy.entity.ServiceTag;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class PolicyTagReadRepositoryImpl implements PolicyTagReadRepository {

    private final ServiceTagRepository serviceTagRepository;

    @Override
    public List<ServiceTag> findByServiceId(Long serviceId) {
        return serviceTagRepository.findByServiceId(serviceId);
    }

    @Override
    public Map<Long, List<ServiceTag>> findByServiceIds(List<Long> serviceIds) {
        if (serviceIds == null || serviceIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return serviceTagRepository.findByServiceIdIn(serviceIds).stream()
                .collect(Collectors.groupingBy(tag -> tag.getService().getId()));
    }
}
