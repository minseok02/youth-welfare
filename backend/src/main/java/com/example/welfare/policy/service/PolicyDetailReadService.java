package com.example.welfare.policy.service;

import com.example.welfare.policy.entity.ServiceRegion;
import com.example.welfare.policy.entity.ServiceTag;
import com.example.welfare.policy.entity.WelfareServiceDetail;
import com.example.welfare.policy.repository.PolicyDetailReadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PolicyDetailReadService {

    private final PolicyDetailReadRepository policyDetailReadRepository;

    @Transactional(readOnly = true)
    public PolicyDetailAggregate getAggregate(Long serviceId) {
        return policyDetailReadRepository.findAggregate(serviceId);
    }

    public record PolicyDetailAggregate(
            WelfareServiceDetail detail,
            List<ServiceRegion> regions,
            List<ServiceTag> tags
    ) {
    }
}
