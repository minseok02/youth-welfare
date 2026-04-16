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
    // LEFT JOIN: service_regions 레코드가 없는 전국 정책(복지로 중앙 등)도 포함
    // sr이 NULL인 경우 = 전국 정책 → 지역 조건 없이 통과
    // sr이 존재하는 경우 = 지역 정책 → regionCode 또는 sidoName 일치 시 통과
    @Query("""
            SELECT DISTINCT ws FROM WelfareService ws
            LEFT JOIN ServiceRegion sr ON sr.service = ws
            WHERE ws.status IN ('ACTIVE', 'UPCOMING')
              AND (ws.minAge IS NULL OR ws.minAge <= :age)
              AND (ws.maxAge IS NULL OR ws.maxAge >= :age)
              AND (ws.minIncome IS NULL OR ws.minIncome <= :incomeLevel)
              AND (ws.maxIncome IS NULL OR ws.maxIncome >= :incomeLevel)
              AND (sr.id IS NULL OR sr.regionCode = :regionCode OR sr.sidoName = :sidoName)
            """)
    List<WelfareService> findCandidatesWithRegion(@Param("age") int age,
                                                   @Param("incomeLevel") int incomeLevel,
                                                   @Param("regionCode") String regionCode,
                                                   @Param("sidoName") String sidoName,
                                                   Pageable pageable);

    // 추천 후보 조회(최신순): 신규 정책 M건 강제 포함용
    @Query("""
            SELECT ws FROM WelfareService ws
            WHERE ws.status IN ('ACTIVE', 'UPCOMING')
              AND (ws.minAge IS NULL OR ws.minAge <= :age)
              AND (ws.maxAge IS NULL OR ws.maxAge >= :age)
              AND (ws.minIncome IS NULL OR ws.minIncome <= :incomeLevel)
              AND (ws.maxIncome IS NULL OR ws.maxIncome >= :incomeLevel)
            ORDER BY ws.createdAt DESC
            """)
    List<WelfareService> findLatestCandidates(@Param("age") int age,
                                              @Param("incomeLevel") int incomeLevel,
                                              Pageable pageable);

    // 지역 필터 포함 추천 후보 조회(최신순): 신규 정책 M건 강제 포함용
    @Query("""
            SELECT DISTINCT ws FROM WelfareService ws
            LEFT JOIN ServiceRegion sr ON sr.service = ws
            WHERE ws.status IN ('ACTIVE', 'UPCOMING')
              AND (ws.minAge IS NULL OR ws.minAge <= :age)
              AND (ws.maxAge IS NULL OR ws.maxAge >= :age)
              AND (ws.minIncome IS NULL OR ws.minIncome <= :incomeLevel)
              AND (ws.maxIncome IS NULL OR ws.maxIncome >= :incomeLevel)
              AND (sr.id IS NULL OR sr.regionCode = :regionCode OR sr.sidoName = :sidoName)
            ORDER BY ws.createdAt DESC
            """)
    List<WelfareService> findLatestCandidatesWithRegion(@Param("age") int age,
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

    // FULLTEXT + 필터 검색 (정렬: RELEVANCE / VIEWS / LATEST)
    @Query(value = """
            SELECT ws.* FROM welfare_services ws
            WHERE (
                    (:status IS NULL AND ws.status IN ('ACTIVE', 'UPCOMING'))
                    OR (:status IS NOT NULL AND ws.status = :status)
                  )
              AND (:category IS NULL OR ws.unified_category = :category)
              AND (:sourceType IS NULL OR ws.source_type = :sourceType)
              AND (:onlineApply IS NULL OR ws.is_online_apply = :onlineApply)
              AND MATCH(ws.title, ws.description, ws.support_content, ws.keyword)
                  AGAINST (:keyword IN BOOLEAN MODE)
            ORDER BY
                CASE
                    WHEN :sort = 'VIEWS' THEN ws.view_count
                    ELSE NULL
                END DESC,
                CASE
                    WHEN :sort = 'LATEST' THEN ws.created_at
                    ELSE NULL
                END DESC,
                CASE
                    WHEN :sort = 'RELEVANCE' THEN MATCH(ws.title, ws.description, ws.support_content, ws.keyword)
                        AGAINST (:keyword IN BOOLEAN MODE)
                    ELSE NULL
                END DESC,
                ws.view_count DESC,
                ws.created_at DESC
            LIMIT :limit OFFSET :offset
            """, nativeQuery = true)
    List<WelfareService> searchByKeywordWithFilters(@Param("keyword") String keyword,
                                                    @Param("status") String status,
                                                    @Param("category") String category,
                                                    @Param("sourceType") String sourceType,
                                                    @Param("onlineApply") Integer onlineApply,
                                                    @Param("sort") String sort,
                                                    @Param("limit") int limit,
                                                    @Param("offset") int offset);

    // 카테고리 필터 조회
    Page<WelfareService> findByUnifiedCategoryAndStatusIn(
            String unifiedCategory,
            List<WelfareService.ServiceStatus> statuses,
            Pageable pageable);

    // 상태별 전체 조회 (StatusUpdateService 용)
    List<WelfareService> findByStatus(WelfareService.ServiceStatus status);
    List<WelfareService> findByStatusIn(List<WelfareService.ServiceStatus> statuses);

    List<WelfareService> findBySourceType(WelfareService.SourceType sourceType);
}
