package com.example.welfare.collect.repository;

import com.example.welfare.policy.entity.WelfareService;

import java.util.List;

public interface StatusUpdateReadRepository {

    List<WelfareService> findActiveServices();

    List<WelfareService> findUpcomingServices();

    List<WelfareService> findClosedServices();
}
