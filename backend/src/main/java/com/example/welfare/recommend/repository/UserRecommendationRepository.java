package com.example.welfare.recommend.repository;

import com.example.welfare.recommend.entity.UserRecommendation;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserRecommendationRepository extends JpaRepository<UserRecommendation, Long> {

    // 사용자 추천 목록 — 최종점수 내림차순
    @Query("""
            SELECT ur FROM UserRecommendation ur
            JOIN FETCH ur.service
            WHERE ur.user.id = :userId
              AND ur.recommendedAt = (
                    SELECT MAX(ur2.recommendedAt)
                    FROM UserRecommendation ur2
                    WHERE ur2.user.id = :userId
                      AND ur2.service.id = ur.service.id
              )
            ORDER BY ur.finalScore DESC
            """)
    List<UserRecommendation> findTopByUserId(@Param("userId") Long userId, Pageable pageable);

    @Query("""
            SELECT ur FROM UserRecommendation ur
            WHERE ur.user.id = :userId
              AND ur.recommendedAt = (
                    SELECT MAX(ur2.recommendedAt)
                    FROM UserRecommendation ur2
                    WHERE ur2.user.id = :userId
                      AND ur2.service.id = ur.service.id
              )
            """)
    List<UserRecommendation> findLatestByUserId(@Param("userId") Long userId);

    // 북마크 목록
    @Query("""
            SELECT ur FROM UserRecommendation ur
            JOIN FETCH ur.service
            WHERE ur.user.id = :userId
              AND ur.isBookmarked = true
              AND ur.recommendedAt = (
                    SELECT MAX(ur2.recommendedAt)
                    FROM UserRecommendation ur2
                    WHERE ur2.user.id = :userId
                      AND ur2.service.id = ur.service.id
              )
            ORDER BY ur.recommendedAt DESC
            """)
    List<UserRecommendation> findLatestBookmarkedByUserId(@Param("userId") Long userId);

    @Query("""
            SELECT ur.service.id FROM UserRecommendation ur
            WHERE ur.user.id = :userId
              AND ur.service.id IN :serviceIds
              AND ur.isBookmarked = true
              AND ur.recommendedAt = (
                    SELECT MAX(ur2.recommendedAt)
                    FROM UserRecommendation ur2
                    WHERE ur2.user.id = :userId
                      AND ur2.service.id = ur.service.id
              )
            """)
    List<Long> findLatestBookmarkedServiceIds(@Param("userId") Long userId,
                                              @Param("serviceIds") List<Long> serviceIds);

    long countByUserIdAndIsBookmarkedTrue(Long userId);

    Optional<UserRecommendation> findTopByUserIdAndServiceIdOrderByRecommendedAtDesc(Long userId, Long serviceId);

    // 클릭 추적용 단건 조회
    Optional<UserRecommendation> findByIdAndUserId(Long id, Long userId);

    // 오늘 이미 추천받은 서비스 제외 (중복 추천 방지)
    @Query("""
            SELECT ur.service.id FROM UserRecommendation ur
            WHERE ur.user.id = :userId
              AND ur.recommendedAt >= :since
            """)
    List<Long> findRecentlyRecommendedServiceIds(@Param("userId") Long userId,
                                                  @Param("since") LocalDateTime since);

    // 30일 지나고 북마크 없는 추천 삭제 (데이터 보존 정책)
    @Modifying
    @Query("""
            DELETE FROM UserRecommendation ur
            WHERE ur.user.id = :userId
              AND ur.isBookmarked = false
            """)
    void deleteUnbookmarkedByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("""
            DELETE FROM UserRecommendation ur
            WHERE ur.recommendedAt < :before AND ur.isBookmarked = false
            """)
    void deleteOldUnbookmarked(@Param("before") LocalDateTime before);
}
