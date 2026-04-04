package com.example.welfare.recommend.repository;

import com.example.welfare.recommend.entity.RecommendationLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RecommendationLogRepository extends JpaRepository<RecommendationLog, Long> {

    // 전체 로그 수 — Cold Start 단계 판별에 사용
    long countByUserId(Long userId);

    // 사용자 로그 최신순
    List<RecommendationLog> findByUserIdOrderBySentAtDesc(Long userId, Pageable pageable);

    // 클릭 처리용
    Optional<RecommendationLog> findByIdAndUserId(Long id, Long userId);

    // CTR 분석: 사용자별 클릭률 조회
    @Query("""
            SELECT COUNT(rl) FROM RecommendationLog rl
            WHERE rl.user.id = :userId AND rl.isClicked = true
            """)
    long countClickedByUserId(@Param("userId") Long userId);
}
