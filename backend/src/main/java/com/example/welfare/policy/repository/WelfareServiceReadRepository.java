package com.example.welfare.policy.repository;

import com.example.welfare.policy.entity.WelfareService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface WelfareServiceReadRepository {

    Page<WelfareService> findList(PolicyListReadCondition condition, Pageable pageable);

    Page<WelfareService> search(PolicySearchReadCondition condition, Pageable pageable);
}
