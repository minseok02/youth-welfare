package com.example.welfare.user.repository;

import com.example.welfare.user.entity.PriorityOption;

import java.util.Optional;

public interface PriorityOptionReadRepository {

    Optional<PriorityOption> findByCode(String code);
}
