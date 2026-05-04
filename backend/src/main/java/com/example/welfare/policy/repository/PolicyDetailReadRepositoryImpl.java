package com.example.welfare.policy.repository;

import com.example.welfare.policy.entity.ServiceRegion;
import com.example.welfare.policy.entity.ServiceTag;
import com.example.welfare.policy.entity.WelfareServiceDetail;
import com.example.welfare.policy.service.PolicyDetailReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class PolicyDetailReadRepositoryImpl implements PolicyDetailReadRepository {

    private final WelfareServiceDetailRepository detailRepository;
    private final ServiceRegionRepository regionRepository;
    private final PolicyTagReadRepository policyTagReadRepository;

    @Override
    public PolicyDetailReadService.PolicyDetailAggregate findAggregate(Long serviceId) {
        WelfareServiceDetail detail = detailRepository.findByServiceId(serviceId).orElse(null);
        List<ServiceRegion> regions = regionRepository.findByServiceId(serviceId);
        List<ServiceTag> tags = policyTagReadRepository.findByServiceId(serviceId);
        return new PolicyDetailReadService.PolicyDetailAggregate(detail, regions, tags);
    }
}
