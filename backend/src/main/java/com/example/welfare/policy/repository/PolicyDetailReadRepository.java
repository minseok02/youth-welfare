package com.example.welfare.policy.repository;

import com.example.welfare.policy.service.PolicyDetailReadService;

public interface PolicyDetailReadRepository {

    PolicyDetailReadService.PolicyDetailAggregate findAggregate(Long serviceId);
}
