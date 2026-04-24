package com.example.welfare.collect.repository;

import com.example.welfare.collect.entity.ApiSyncLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApiSyncLogRepository extends JpaRepository<ApiSyncLog, Long> {
}
