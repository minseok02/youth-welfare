package com.example.welfare.policy.repository;

import com.example.welfare.policy.entity.SearchLog;

public interface PolicySearchLogCommandRepository {

    SearchLog save(SearchLog searchLog);
}
