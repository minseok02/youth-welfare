package com.example.welfare.policy.service;

import com.example.welfare.policy.entity.ServiceRegion;
import com.example.welfare.policy.entity.ServiceTag;
import com.example.welfare.policy.entity.WelfareServiceDetail;
import com.example.welfare.policy.repository.ServiceRegionRepository;
import com.example.welfare.policy.repository.ServiceTagRepository;
import com.example.welfare.policy.repository.WelfareServiceDetailRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PolicyDetailReadService {

    private final WelfareServiceDetailRepository detailRepository;
    private final ServiceRegionRepository regionRepository;
    private final ServiceTagRepository tagRepository;

    @Transactional(readOnly = true)
    public PolicyDetailAggregate getAggregate(Long serviceId) {
        WelfareServiceDetail detail = detailRepository.findByServiceId(serviceId).orElse(null);
        List<ServiceRegion> regions = regionRepository.findByServiceId(serviceId);
        List<ServiceTag> tags = tagRepository.findByServiceId(serviceId);
        return new PolicyDetailAggregate(detail, regions, tags);
    }

    public record PolicyDetailAggregate(
            WelfareServiceDetail detail,
            List<ServiceRegion> regions,
            List<ServiceTag> tags
    ) {
    }
}
