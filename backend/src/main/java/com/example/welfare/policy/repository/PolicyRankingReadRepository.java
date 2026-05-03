package com.example.welfare.policy.repository;

import com.example.welfare.policy.entity.WelfareService;

import java.util.List;

public interface PolicyRankingReadRepository {

    List<WelfareService> findRankableServices();
}
