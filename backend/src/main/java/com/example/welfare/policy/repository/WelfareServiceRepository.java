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
              AND (
                    (ws.minIncome IS NULL AND ws.maxIncome IS NULL)
                    OR (ws.minIncome = 0 AND ws.maxIncome = 0)
                    OR (
                        (ws.minIncome IS NULL OR ws.minIncome <= :incomeLevel)
                        AND (ws.maxIncome IS NULL OR ws.maxIncome >= :incomeLevel)
                    )
                  )
            """)
    List<WelfareService> findCandidates(@Param("age") int age,
                                        @Param("incomeLevel") int incomeLevel,
                                        Pageable pageable);

    // 지역코드 필터 포함 추천 후보
    // service_regions 레코드가 없는 전국 정책(복지로 중앙 등)도 포함
    @Query("""
            SELECT ws FROM WelfareService ws
            WHERE ws.status IN ('ACTIVE', 'UPCOMING')
              AND (ws.minAge IS NULL OR ws.minAge <= :age)
              AND (ws.maxAge IS NULL OR ws.maxAge >= :age)
              AND (
                    (ws.minIncome IS NULL AND ws.maxIncome IS NULL)
                    OR (ws.minIncome = 0 AND ws.maxIncome = 0)
                    OR (
                        (ws.minIncome IS NULL OR ws.minIncome <= :incomeLevel)
                        AND (ws.maxIncome IS NULL OR ws.maxIncome >= :incomeLevel)
                    )
                  )
              AND (
                    NOT EXISTS (
                        SELECT sr1.id FROM ServiceRegion sr1
                        WHERE sr1.service = ws
                    )
                    OR EXISTS (
                        SELECT sr2.id FROM ServiceRegion sr2
                        WHERE sr2.service = ws
                          AND sr2.regionCode = :regionCode
                    )
                  )
            """)
    List<WelfareService> findCandidatesWithRegionCode(@Param("age") int age,
                                                      @Param("incomeLevel") int incomeLevel,
                                                      @Param("regionCode") String regionCode,
                                                      Pageable pageable);

    // 시도 필터 포함 추천 후보
    @Query("""
            SELECT ws FROM WelfareService ws
            WHERE ws.status IN ('ACTIVE', 'UPCOMING')
              AND (ws.minAge IS NULL OR ws.minAge <= :age)
              AND (ws.maxAge IS NULL OR ws.maxAge >= :age)
              AND (
                    (ws.minIncome IS NULL AND ws.maxIncome IS NULL)
                    OR (ws.minIncome = 0 AND ws.maxIncome = 0)
                    OR (
                        (ws.minIncome IS NULL OR ws.minIncome <= :incomeLevel)
                        AND (ws.maxIncome IS NULL OR ws.maxIncome >= :incomeLevel)
                    )
                  )
              AND (
                    NOT EXISTS (
                        SELECT sr1.id FROM ServiceRegion sr1
                        WHERE sr1.service = ws
                    )
                    OR EXISTS (
                        SELECT sr2.id FROM ServiceRegion sr2
                        WHERE sr2.service = ws
                          AND sr2.sidoName = :sidoName
                    )
                  )
            """)
    List<WelfareService> findCandidatesWithSido(@Param("age") int age,
                                                @Param("incomeLevel") int incomeLevel,
                                                @Param("sidoName") String sidoName,
                                                Pageable pageable);

    // 추천 후보 조회(최신순): 신규 정책 M건 강제 포함용
    @Query("""
            SELECT ws FROM WelfareService ws
            WHERE ws.status IN ('ACTIVE', 'UPCOMING')
              AND (ws.minAge IS NULL OR ws.minAge <= :age)
              AND (ws.maxAge IS NULL OR ws.maxAge >= :age)
              AND (
                    (ws.minIncome IS NULL AND ws.maxIncome IS NULL)
                    OR (ws.minIncome = 0 AND ws.maxIncome = 0)
                    OR (
                        (ws.minIncome IS NULL OR ws.minIncome <= :incomeLevel)
                        AND (ws.maxIncome IS NULL OR ws.maxIncome >= :incomeLevel)
                    )
                  )
            ORDER BY ws.createdAt DESC
            """)
    List<WelfareService> findLatestCandidates(@Param("age") int age,
                                              @Param("incomeLevel") int incomeLevel,
                                              Pageable pageable);

    // 지역코드 필터 포함 추천 후보 조회(최신순): 신규 정책 M건 강제 포함용
    @Query("""
            SELECT ws FROM WelfareService ws
            WHERE ws.status IN ('ACTIVE', 'UPCOMING')
              AND (ws.minAge IS NULL OR ws.minAge <= :age)
              AND (ws.maxAge IS NULL OR ws.maxAge >= :age)
              AND (
                    (ws.minIncome IS NULL AND ws.maxIncome IS NULL)
                    OR (ws.minIncome = 0 AND ws.maxIncome = 0)
                    OR (
                        (ws.minIncome IS NULL OR ws.minIncome <= :incomeLevel)
                        AND (ws.maxIncome IS NULL OR ws.maxIncome >= :incomeLevel)
                    )
                  )
              AND (
                    NOT EXISTS (
                        SELECT sr1.id FROM ServiceRegion sr1
                        WHERE sr1.service = ws
                    )
                    OR EXISTS (
                        SELECT sr2.id FROM ServiceRegion sr2
                        WHERE sr2.service = ws
                          AND sr2.regionCode = :regionCode
                    )
                  )
            ORDER BY ws.createdAt DESC
            """)
    List<WelfareService> findLatestCandidatesWithRegionCode(@Param("age") int age,
                                                            @Param("incomeLevel") int incomeLevel,
                                                            @Param("regionCode") String regionCode,
                                                            Pageable pageable);

    // 시도 필터 포함 추천 후보 조회(최신순): 신규 정책 M건 강제 포함용
    @Query("""
            SELECT ws FROM WelfareService ws
            WHERE ws.status IN ('ACTIVE', 'UPCOMING')
              AND (ws.minAge IS NULL OR ws.minAge <= :age)
              AND (ws.maxAge IS NULL OR ws.maxAge >= :age)
              AND (
                    (ws.minIncome IS NULL AND ws.maxIncome IS NULL)
                    OR (ws.minIncome = 0 AND ws.maxIncome = 0)
                    OR (
                        (ws.minIncome IS NULL OR ws.minIncome <= :incomeLevel)
                        AND (ws.maxIncome IS NULL OR ws.maxIncome >= :incomeLevel)
                    )
                  )
              AND (
                    NOT EXISTS (
                        SELECT sr1.id FROM ServiceRegion sr1
                        WHERE sr1.service = ws
                    )
                    OR EXISTS (
                        SELECT sr2.id FROM ServiceRegion sr2
                        WHERE sr2.service = ws
                          AND sr2.sidoName = :sidoName
                    )
                  )
            ORDER BY ws.createdAt DESC
            """)
    List<WelfareService> findLatestCandidatesWithSido(@Param("age") int age,
                                                      @Param("incomeLevel") int incomeLevel,
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

    @Query(value = """
            SELECT * FROM welfare_services
            WHERE status IN ('ACTIVE', 'UPCOMING')
              AND search_youth_relevant = 1
              AND MATCH(title, description, support_content, keyword) AGAINST (:keyword IN BOOLEAN MODE)
            ORDER BY MATCH(title, description, support_content, keyword) AGAINST (:keyword IN BOOLEAN MODE) DESC,
                     view_count DESC,
                     created_at DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<WelfareService> searchChatCandidates(@Param("keyword") String keyword,
                                              @Param("limit") int limit);

    List<WelfareService> findBySearchYouthRelevantTrueAndStatusInOrderByViewCountDescCreatedAtDesc(
            List<WelfareService.ServiceStatus> statuses,
            Pageable pageable
    );

    // statusFilter 값 의미 (검색·목록 쿼리 공통):
    //   ACTIVE_ONLY(기본) : status IN (ACTIVE, UPCOMING) AND apply_end_date >= 오늘 or NULL
    //   EXPIRED_ONLY      : status = CLOSED OR apply_end_date < 오늘
    //                       온통청년처럼 DB status는 ACTIVE지만 신청 마감일이 지난 정책 포함
    //   ALL               : 모든 상태
    // status 파라미터가 직접 지정되면 statusFilter를 무시하고 status 단일 값으로 매칭

    // FULLTEXT + 필터 검색 (정렬: RELEVANCE / VIEWS / LATEST / NAME)
    // 지역 필터가 없는 일반 검색은 service_regions 조인을 피해서 DISTINCT/임시 테이블 비용을 줄인다.
    @Query(value = """
            SELECT ws.* FROM welfare_services ws
            WHERE (
                    (:status IS NULL AND (
                        (:statusFilter = 'ALL' AND ws.status IN ('ACTIVE', 'UPCOMING', 'CLOSED'))
                        OR (:statusFilter = 'EXPIRED_ONLY' AND (ws.status = 'CLOSED' OR (ws.apply_end_date IS NOT NULL AND ws.apply_end_date < CURDATE())))
                        OR ((:statusFilter IS NULL OR :statusFilter = 'ACTIVE_ONLY') AND ws.status IN ('ACTIVE', 'UPCOMING') AND (ws.apply_end_date IS NULL OR ws.apply_end_date >= CURDATE()))
                    ))
                    OR (:status IS NOT NULL AND ws.status = :status)
                  )
              AND ws.search_youth_relevant = 1
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
                    WHEN :sort = 'NAME' THEN ws.title
                    ELSE NULL
                END ASC,
                CASE
                    WHEN :sort = 'RELEVANCE' THEN MATCH(ws.title, ws.description, ws.support_content, ws.keyword)
                        AGAINST (:keyword IN BOOLEAN MODE)
                    ELSE NULL
                END DESC,
                ws.view_count DESC,
                ws.created_at DESC
            """,
            countQuery = """
            SELECT COUNT(*) FROM welfare_services ws
            WHERE (
                    (:status IS NULL AND (
                        (:statusFilter = 'ALL' AND ws.status IN ('ACTIVE', 'UPCOMING', 'CLOSED'))
                        OR (:statusFilter = 'EXPIRED_ONLY' AND (ws.status = 'CLOSED' OR (ws.apply_end_date IS NOT NULL AND ws.apply_end_date < CURDATE())))
                        OR ((:statusFilter IS NULL OR :statusFilter = 'ACTIVE_ONLY') AND ws.status IN ('ACTIVE', 'UPCOMING') AND (ws.apply_end_date IS NULL OR ws.apply_end_date >= CURDATE()))
                    ))
                    OR (:status IS NOT NULL AND ws.status = :status)
                  )
              AND ws.search_youth_relevant = 1
              AND (:category IS NULL OR ws.unified_category = :category)
              AND (:sourceType IS NULL OR ws.source_type = :sourceType)
              AND (:onlineApply IS NULL OR ws.is_online_apply = :onlineApply)
              AND MATCH(ws.title, ws.description, ws.support_content, ws.keyword)
                  AGAINST (:keyword IN BOOLEAN MODE)
            """, nativeQuery = true)
    Page<WelfareService> searchByKeywordWithFiltersNoRegion(@Param("keyword") String keyword,
                                                            @Param("status") String status,
                                                            @Param("statusFilter") String statusFilter,
                                                            @Param("category") String category,
                                                            @Param("sourceType") String sourceType,
                                                            @Param("onlineApply") Integer onlineApply,
                                                            @Param("sort") String sort,
                                                            Pageable pageable);

    @Query(value = """
            SELECT ws.* FROM welfare_services ws
            WHERE (
                    (:status IS NULL AND (
                        (:statusFilter = 'ALL' AND ws.status IN ('ACTIVE', 'UPCOMING', 'CLOSED'))
                        OR (:statusFilter = 'EXPIRED_ONLY' AND (ws.status = 'CLOSED' OR (ws.apply_end_date IS NOT NULL AND ws.apply_end_date < CURDATE())))
                        OR ((:statusFilter IS NULL OR :statusFilter = 'ACTIVE_ONLY') AND ws.status IN ('ACTIVE', 'UPCOMING') AND (ws.apply_end_date IS NULL OR ws.apply_end_date >= CURDATE()))
                    ))
                    OR (:status IS NOT NULL AND ws.status = :status)
                  )
              AND ws.search_youth_relevant = 1
              AND (:category IS NULL OR ws.unified_category = :category)
              AND (:sourceType IS NULL OR ws.source_type = :sourceType)
              AND (:onlineApply IS NULL OR ws.is_online_apply = :onlineApply)
              AND (
                    NOT EXISTS (
                        SELECT 1 FROM service_regions sr1
                        WHERE sr1.service_id = ws.id
                    )
                    OR EXISTS (
                        SELECT 1 FROM service_regions sr2
                        WHERE sr2.service_id = ws.id
                          AND sr2.sido_name = :sido
                    )
                  )
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
                    WHEN :sort = 'NAME' THEN ws.title
                    ELSE NULL
                END ASC,
                CASE
                    WHEN :sort = 'RELEVANCE' THEN MATCH(ws.title, ws.description, ws.support_content, ws.keyword)
                        AGAINST (:keyword IN BOOLEAN MODE)
                    ELSE NULL
                END DESC,
                ws.view_count DESC,
                ws.created_at DESC
            """,
            countQuery = """
            SELECT COUNT(*) FROM welfare_services ws
            WHERE (
                    (:status IS NULL AND (
                        (:statusFilter = 'ALL' AND ws.status IN ('ACTIVE', 'UPCOMING', 'CLOSED'))
                        OR (:statusFilter = 'EXPIRED_ONLY' AND (ws.status = 'CLOSED' OR (ws.apply_end_date IS NOT NULL AND ws.apply_end_date < CURDATE())))
                        OR ((:statusFilter IS NULL OR :statusFilter = 'ACTIVE_ONLY') AND ws.status IN ('ACTIVE', 'UPCOMING') AND (ws.apply_end_date IS NULL OR ws.apply_end_date >= CURDATE()))
                    ))
                    OR (:status IS NOT NULL AND ws.status = :status)
                  )
              AND ws.search_youth_relevant = 1
              AND (:category IS NULL OR ws.unified_category = :category)
              AND (:sourceType IS NULL OR ws.source_type = :sourceType)
              AND (:onlineApply IS NULL OR ws.is_online_apply = :onlineApply)
              AND (
                    NOT EXISTS (
                        SELECT 1 FROM service_regions sr1
                        WHERE sr1.service_id = ws.id
                    )
                    OR EXISTS (
                        SELECT 1 FROM service_regions sr2
                        WHERE sr2.service_id = ws.id
                          AND sr2.sido_name = :sido
                    )
                  )
              AND MATCH(ws.title, ws.description, ws.support_content, ws.keyword)
                  AGAINST (:keyword IN BOOLEAN MODE)
            """, nativeQuery = true)
    Page<WelfareService> searchByKeywordWithFiltersWithSido(@Param("keyword") String keyword,
                                                            @Param("status") String status,
                                                            @Param("statusFilter") String statusFilter,
                                                            @Param("category") String category,
                                                            @Param("sourceType") String sourceType,
                                                            @Param("onlineApply") Integer onlineApply,
                                                            @Param("sido") String sido,
                                                            @Param("sort") String sort,
                                                            Pageable pageable);

    @Query(value = """
            SELECT ws.* FROM welfare_services ws
            WHERE (
                    (:status IS NULL AND (
                        (:statusFilter = 'ALL' AND ws.status IN ('ACTIVE', 'UPCOMING', 'CLOSED'))
                        OR (:statusFilter = 'EXPIRED_ONLY' AND (ws.status = 'CLOSED' OR (ws.apply_end_date IS NOT NULL AND ws.apply_end_date < CURDATE())))
                        OR ((:statusFilter IS NULL OR :statusFilter = 'ACTIVE_ONLY') AND ws.status IN ('ACTIVE', 'UPCOMING') AND (ws.apply_end_date IS NULL OR ws.apply_end_date >= CURDATE()))
                    ))
                    OR (:status IS NOT NULL AND ws.status = :status)
                  )
              AND ws.search_youth_relevant = 1
              AND (:category IS NULL OR ws.unified_category = :category)
              AND (:sourceType IS NULL OR ws.source_type = :sourceType)
              AND (:onlineApply IS NULL OR ws.is_online_apply = :onlineApply)
              AND (
                    NOT EXISTS (
                        SELECT 1 FROM service_regions sr1
                        WHERE sr1.service_id = ws.id
                    )
                    OR EXISTS (
                        SELECT 1 FROM service_regions sr2
                        WHERE sr2.service_id = ws.id
                          AND sr2.sido_name = :sido
                          AND sr2.sgg_name = :sgg
                    )
                  )
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
                    WHEN :sort = 'NAME' THEN ws.title
                    ELSE NULL
                END ASC,
                CASE
                    WHEN :sort = 'RELEVANCE' THEN MATCH(ws.title, ws.description, ws.support_content, ws.keyword)
                        AGAINST (:keyword IN BOOLEAN MODE)
                    ELSE NULL
                END DESC,
                ws.view_count DESC,
                ws.created_at DESC
            """,
            countQuery = """
            SELECT COUNT(*) FROM welfare_services ws
            WHERE (
                    (:status IS NULL AND (
                        (:statusFilter = 'ALL' AND ws.status IN ('ACTIVE', 'UPCOMING', 'CLOSED'))
                        OR (:statusFilter = 'EXPIRED_ONLY' AND (ws.status = 'CLOSED' OR (ws.apply_end_date IS NOT NULL AND ws.apply_end_date < CURDATE())))
                        OR ((:statusFilter IS NULL OR :statusFilter = 'ACTIVE_ONLY') AND ws.status IN ('ACTIVE', 'UPCOMING') AND (ws.apply_end_date IS NULL OR ws.apply_end_date >= CURDATE()))
                    ))
                    OR (:status IS NOT NULL AND ws.status = :status)
                  )
              AND ws.search_youth_relevant = 1
              AND (:category IS NULL OR ws.unified_category = :category)
              AND (:sourceType IS NULL OR ws.source_type = :sourceType)
              AND (:onlineApply IS NULL OR ws.is_online_apply = :onlineApply)
              AND (
                    NOT EXISTS (
                        SELECT 1 FROM service_regions sr1
                        WHERE sr1.service_id = ws.id
                    )
                    OR EXISTS (
                        SELECT 1 FROM service_regions sr2
                        WHERE sr2.service_id = ws.id
                          AND sr2.sido_name = :sido
                          AND sr2.sgg_name = :sgg
                    )
                  )
              AND MATCH(ws.title, ws.description, ws.support_content, ws.keyword)
                  AGAINST (:keyword IN BOOLEAN MODE)
            """, nativeQuery = true)
    Page<WelfareService> searchByKeywordWithFiltersWithSidoSgg(@Param("keyword") String keyword,
                                                               @Param("status") String status,
                                                               @Param("statusFilter") String statusFilter,
                                                               @Param("category") String category,
                                                               @Param("sourceType") String sourceType,
                                                               @Param("onlineApply") Integer onlineApply,
                                                               @Param("sido") String sido,
                                                               @Param("sgg") String sgg,
                                                               @Param("sort") String sort,
                                                               Pageable pageable);

    // 카테고리 필터 조회
    Page<WelfareService> findByUnifiedCategoryAndStatusIn(
            String unifiedCategory,
            List<WelfareService.ServiceStatus> statuses,
            Pageable pageable);

    // 목록 조회 JPQL (statusFilter 값 의미는 위 searchByKeyword 블록 주석 참조)
    @Query(value = """
            SELECT ws FROM WelfareService ws
            WHERE (
                    (:status IS NULL AND (
                        (:statusFilter = 'ALL' AND ws.status IN ('ACTIVE', 'UPCOMING', 'CLOSED'))
                        OR (:statusFilter = 'EXPIRED_ONLY' AND (ws.status = 'CLOSED' OR (ws.applyEndDate IS NOT NULL AND ws.applyEndDate < CURRENT_DATE)))
                        OR ((:statusFilter IS NULL OR :statusFilter = 'ACTIVE_ONLY') AND ws.status IN ('ACTIVE', 'UPCOMING') AND (ws.applyEndDate IS NULL OR ws.applyEndDate >= CURRENT_DATE))
                    ))
                    OR (:status IS NOT NULL AND ws.status = :status)
                  )
              AND (:category IS NULL OR ws.unifiedCategory = :category)
              AND (:sourceType IS NULL OR ws.sourceType = :sourceType)
              AND (:onlineApply IS NULL OR ws.isOnlineApply = :onlineApply)
              AND (
                    :sido IS NULL
                    OR NOT EXISTS (
                        SELECT sr1.id FROM ServiceRegion sr1
                        WHERE sr1.service = ws
                    )
                    OR EXISTS (
                        SELECT sr2.id FROM ServiceRegion sr2
                        WHERE sr2.service = ws
                          AND (
                              sr2.sidoName = :sido
                              OR (:sidoCode IS NOT NULL AND sr2.regionCode LIKE CONCAT(:sidoCode, '%'))
                          )
                          AND (
                              :sgg IS NULL
                              OR sr2.sggName = :sgg
                              OR (:regionCode IS NOT NULL AND sr2.regionCode = :regionCode)
                          )
                    )
                  )
            """,
            countQuery = """
            SELECT COUNT(ws) FROM WelfareService ws
            WHERE (
                    (:status IS NULL AND (
                        (:statusFilter = 'ALL' AND ws.status IN ('ACTIVE', 'UPCOMING', 'CLOSED'))
                        OR (:statusFilter = 'EXPIRED_ONLY' AND (ws.status = 'CLOSED' OR (ws.applyEndDate IS NOT NULL AND ws.applyEndDate < CURRENT_DATE)))
                        OR ((:statusFilter IS NULL OR :statusFilter = 'ACTIVE_ONLY') AND ws.status IN ('ACTIVE', 'UPCOMING') AND (ws.applyEndDate IS NULL OR ws.applyEndDate >= CURRENT_DATE))
                    ))
                    OR (:status IS NOT NULL AND ws.status = :status)
                  )
              AND (:category IS NULL OR ws.unifiedCategory = :category)
              AND (:sourceType IS NULL OR ws.sourceType = :sourceType)
              AND (:onlineApply IS NULL OR ws.isOnlineApply = :onlineApply)
              AND (
                    :sido IS NULL
                    OR NOT EXISTS (
                        SELECT sr1.id FROM ServiceRegion sr1
                        WHERE sr1.service = ws
                    )
                    OR EXISTS (
                        SELECT sr2.id FROM ServiceRegion sr2
                        WHERE sr2.service = ws
                          AND (
                              sr2.sidoName = :sido
                              OR (:sidoCode IS NOT NULL AND sr2.regionCode LIKE CONCAT(:sidoCode, '%'))
                          )
                          AND (
                              :sgg IS NULL
                              OR sr2.sggName = :sgg
                              OR (:regionCode IS NOT NULL AND sr2.regionCode = :regionCode)
                          )
                    )
                  )
            """)
    Page<WelfareService> findListWithFilters(@Param("category") String category,
                                             @Param("sourceType") WelfareService.SourceType sourceType,
                                             @Param("status") WelfareService.ServiceStatus status,
                                             @Param("statusFilter") String statusFilter,
                                             @Param("sido") String sido,
                                             @Param("sgg") String sgg,
                                             // 온통청년 시도 필터: sido 앞 2자리 행정코드 (예: "11"), null이면 regionCode 경로 비활성
                                             @Param("sidoCode") String sidoCode,
                                             // 온통청년 시군구 필터: 5자리 행정코드 (예: "11680"), null이면 regionCode 경로 비활성
                                             @Param("regionCode") String regionCode,
                                             @Param("onlineApply") Boolean onlineApply,
                                             Pageable pageable);

    // 상태별 전체 조회 (StatusUpdateService 용)
    List<WelfareService> findByStatus(WelfareService.ServiceStatus status);
    List<WelfareService> findByStatusIn(List<WelfareService.ServiceStatus> statuses);

    List<WelfareService> findBySourceType(WelfareService.SourceType sourceType);
}
