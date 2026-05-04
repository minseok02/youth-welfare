package com.example.welfare.collect.repository;

import com.example.welfare.policy.entity.WelfareServiceDetail;

public interface BokjiroDetailCommandRepository {

    WelfareServiceDetail save(WelfareServiceDetail detail);
}
