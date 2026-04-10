package com.example.welfare.policy.repository;

import com.example.welfare.policy.entity.WelfareService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WelfareServiceRepository extends JpaRepository<WelfareService, Long> {

    Optional<WelfareService> findBySourceTypeAndSourceId(
            WelfareService.SourceType sourceType, String sourceId);

    // 추천 후보 조회: 나이·소득 필터 + ACTIVE/UPCOMING 상태
    @Query("""
            SELECT ws FROM WelfareService ws
            WHERE ws.status IN ('ACTIVE', 'UPCOMING')
              AND (ws.minAge IS NULL OR ws.minAge <= :age)
              AND (ws.maxAge IS NULL OR ws.maxAge >= :age)
              AND (ws.minIncome IS NULL OR ws.minIncome <= :incomeLevel)
              AND (ws.maxIncome IS NULL OR ws.maxIncome >= :incomeLevel)
            """)
    List<WelfareService> findCandidates(@Param("age") int age,
                                        @Param("incomeLevel") int incomeLevel,
                                        Pageable pageable);

    // 지역 필터 포함 추천 후보
    @Query("""
            SELECT DISTINCT ws FROM WelfareService ws
            JOIN ServiceRegion sr ON sr.service = ws
            WHERE ws.status IN ('ACTIVE', 'UPCOMING')
              AND (ws.minAge IS NULL OR ws.minAge <= :age)
              AND (ws.maxAge IS NULL OR ws.maxAge >= :age)
              AND (ws.minIncome IS NULL OR ws.minIncome <= :incomeLevel)
              AND (ws.maxIncome IS NULL OR ws.maxIncome >= :incomeLevel)
              AND (sr.regionCode = :regionCode OR sr.sidoName = :sidoName)
            """)
    List<WelfareService> findCandidatesWithRegion(@Param("age") int age,
                                                   @Param("incomeLevel") int incomeLevel,
                                                   @Param("regionCode") String regionCode,
                                                   @Param("sidoName") String sidoName,
                                                   Pageable pageable);

    // FULLTEXT 검색 (Native Query — MySQL ngram)
    // ft_ws_search 인덱스: title, description, support_content, keyword 4개 컬럼 — 반드시 동일하게 지정
    @Query(value = """
            SELECT * FROM welfare_services
            WHERE status IN ('ACTIVE', 'UPCOMING')
              AND MATCH(title, description, support_content, keyword) AGAINST (:keyword IN BOOLEAN MODE)
            ORDER BY MATCH(title, description, support_content, keyword) AGAINST (:keyword IN BOOLEAN MODE) DESC
            LIMIT :limit OFFSET :offset
            """, nativeQuery = true)
    List<WelfareService> searchByKeyword(@Param("keyword") String keyword,
                                         @Param("limit") int limit,
                                         @Param("offset") int offset);

    // 카테고리 필터 조회
    Page<WelfareService> findByUnifiedCategoryAndStatusIn(
            String unifiedCategory,
            List<WelfareService.ServiceStatus> statuses,
            Pageable pageable);

    // 상태별 전체 조회 (StatusUpdateService 용)
    List<WelfareService> findByStatus(WelfareService.ServiceStatus status);
}
