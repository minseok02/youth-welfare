package com.example.welfare.recommend.repository;

import com.example.welfare.recommend.entity.UserRecommendation;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserRecommendationRepository extends JpaRepository<UserRecommendation, Long> {

    List<UserRecommendation> findByUserKey(String userKey);

    @Query("""
            SELECT ur FROM UserRecommendation ur
            JOIN FETCH ur.service
            WHERE ur.userKey = :userKey
              AND ur.recommendedAt = (
                    SELECT MAX(ur2.recommendedAt)
                    FROM UserRecommendation ur2
                    WHERE ur2.userKey = :userKey
              )
            ORDER BY ur.finalScore DESC, ur.service.id DESC
            """)
    List<UserRecommendation> findLatestBatchByUserKeyOrderByFinalScoreDesc(@Param("userKey") String userKey);

    @Query("""
            SELECT ur FROM UserRecommendation ur
            JOIN FETCH ur.service
            WHERE ur.userKey = :userKey
              AND ur.recommendedAt = :recommendedAt
            ORDER BY ur.finalScore DESC, ur.service.id DESC
            """)
    List<UserRecommendation> findByUserKeyAndRecommendedAtOrderByFinalScoreDesc(@Param("userKey") String userKey,
                                                                                @Param("recommendedAt") LocalDateTime recommendedAt);

    @Query("""
            SELECT ur FROM UserRecommendation ur
            JOIN FETCH ur.service
            WHERE ur.userKey = :userKey
              AND ur.recommendedAt = (
                    SELECT MAX(ur2.recommendedAt)
                    FROM UserRecommendation ur2
                    WHERE ur2.userKey = :userKey
                      AND ur2.service.id = ur.service.id
              )
            ORDER BY ur.finalScore DESC, ur.service.id DESC
            """)
    List<UserRecommendation> findLatestByUserKeyOrderByFinalScoreDesc(@Param("userKey") String userKey);

    // 사용자 추천 목록 — 최종점수 내림차순
    @Query("""
            SELECT ur FROM UserRecommendation ur
            JOIN FETCH ur.service
            WHERE ur.userKey = :userKey
              AND ur.recommendedAt = (
                    SELECT MAX(ur2.recommendedAt)
                    FROM UserRecommendation ur2
                    WHERE ur2.userKey = :userKey
                      AND ur2.service.id = ur.service.id
              )
            ORDER BY ur.finalScore DESC, ur.service.id DESC
            """)
    List<UserRecommendation> findTopByUserKey(@Param("userKey") String userKey, Pageable pageable);

    @Query("""
            SELECT ur FROM UserRecommendation ur
            WHERE ur.userKey = :userKey
              AND ur.recommendedAt = (
                    SELECT MAX(ur2.recommendedAt)
                    FROM UserRecommendation ur2
                    WHERE ur2.userKey = :userKey
                      AND ur2.service.id = ur.service.id
              )
            """)
    List<UserRecommendation> findLatestByUserKey(@Param("userKey") String userKey);

    // 북마크 목록
    @Query("""
            SELECT ur FROM UserRecommendation ur
            JOIN FETCH ur.service
            WHERE ur.userKey = :userKey
              AND ur.isBookmarked = true
              AND ur.recommendedAt = (
                    SELECT MAX(ur2.recommendedAt)
                    FROM UserRecommendation ur2
                    WHERE ur2.userKey = :userKey
                      AND ur2.service.id = ur.service.id
              )
            ORDER BY ur.recommendedAt DESC
            """)
    List<UserRecommendation> findLatestBookmarkedByUserKey(@Param("userKey") String userKey);

    @Query("""
            SELECT ur.service.id FROM UserRecommendation ur
            WHERE ur.userKey = :userKey
              AND ur.service.id IN :serviceIds
              AND ur.isBookmarked = true
              AND ur.recommendedAt = (
                    SELECT MAX(ur2.recommendedAt)
                    FROM UserRecommendation ur2
                    WHERE ur2.userKey = :userKey
                      AND ur2.service.id = ur.service.id
              )
            """)
    List<Long> findLatestBookmarkedServiceIdsByUserKey(@Param("userKey") String userKey,
                                                       @Param("serviceIds") List<Long> serviceIds);

    long countByUserKeyAndIsBookmarkedTrue(String userKey);

    Optional<UserRecommendation> findTopByUserKeyAndServiceIdOrderByRecommendedAtDesc(String userKey, Long serviceId);

    // 클릭 추적용 단건 조회
    Optional<UserRecommendation> findByIdAndUserKey(Long id, String userKey);

    // 오늘 이미 추천받은 서비스 제외 (중복 추천 방지)
    @Query("""
            SELECT ur.service.id FROM UserRecommendation ur
            WHERE ur.userKey = :userKey
              AND ur.recommendedAt >= :since
            """)
    List<Long> findRecentlyRecommendedServiceIds(@Param("userKey") String userKey,
                                                 @Param("since") LocalDateTime since);

}
