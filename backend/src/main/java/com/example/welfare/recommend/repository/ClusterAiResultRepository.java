package com.example.welfare.recommend.repository;

import com.example.welfare.recommend.entity.ClusterAiResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ClusterAiResultRepository extends JpaRepository<ClusterAiResult, Long> {

    // 군집별 캐시 전체 조회
    List<ClusterAiResult> findByClusterId(String clusterId);

    // 만료된 캐시 삭제 (TTL 기반 — 매일 새벽 수집 후 갱신)
    @Modifying
    @Query("DELETE FROM ClusterAiResult c WHERE c.createdAt < :before")
    void deleteExpiredBefore(@Param("before") LocalDateTime before);
}
