package com.example.welfare.collect.repository;

import com.example.welfare.policy.entity.ServiceTag;

import java.util.List;

public interface CollectItemTagCommandRepository {

    void replaceAll(Long serviceId, List<ServiceTag> tags);
}
