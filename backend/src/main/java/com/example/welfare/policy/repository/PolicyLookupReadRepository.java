package com.example.welfare.policy.repository;

import com.example.welfare.policy.entity.WelfareService;

import java.util.Optional;

public interface PolicyLookupReadRepository {

    Optional<WelfareService> findById(Long serviceId);
}
