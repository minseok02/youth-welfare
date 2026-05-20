package com.example.welfare.recommend.repository;

import com.example.welfare.recommend.entity.ClusterAiResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClusterAiResultRepository extends JpaRepository<ClusterAiResult, Long> {

    // 군집별 캐시 전체 조회
    List<ClusterAiResult> findByClusterId(String clusterId);
}
