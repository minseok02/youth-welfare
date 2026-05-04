package com.example.welfare.collect.repository;

import com.example.welfare.policy.entity.WelfareService;

public interface CollectItemCommandRepository {

    WelfareService saveAndFlush(WelfareService service);
}
