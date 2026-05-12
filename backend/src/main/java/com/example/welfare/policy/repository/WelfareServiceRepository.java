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

    @Query("""
            SELECT ws.id FROM WelfareService ws
            WHERE ws.searchYouthRelevant = true
              AND ws.status IN :statuses
            ORDER BY ws.id ASC
            """)
    List<Long> findIdsBySearchYouthRelevantTrueAndStatusIn(@Param("statuses") List<WelfareService.ServiceStatus> statuses);

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

    List<WelfareService> findBySearchYouthRelevantTrueAndStatusInOrderByApiViewCountDescViewCountDescCreatedAtDesc(
            List<WelfareService.ServiceStatus> statuses,
            Pageable pageable
    );

    List<WelfareService> findBySearchYouthRelevantTrueAndStatusInAndUnifiedCategoryOrderByApiViewCountDescViewCountDescCreatedAtDesc(
            List<WelfareService.ServiceStatus> statuses,
            String unifiedCategory,
            Pageable pageable
    );

    // statusFilter 값 의미 (검색·목록 쿼리 공통):
    //   ACTIVE_ONLY(기본) : status IN (ACTIVE, UPCOMING) AND apply_end_date >= 오늘 or NULL
    //   EXPIRED_ONLY      : status = CLOSED OR apply_end_date < 오늘
    //                       온통청년처럼 DB status는 ACTIVE지만 신청 마감일이 지난 정책 포함
    //   ALL               : 모든 상태
    // status 파라미터가 직접 지정되면 statusFilter를 무시하고 status 단일 값으로 매칭

    // 카테고리 필터 조회
    Page<WelfareService> findByUnifiedCategoryAndStatusIn(
            String unifiedCategory,
            List<WelfareService.ServiceStatus> statuses,
            Pageable pageable);

    // 목록 조회 native SQL — 지역 선택 시 해당 지역 정책을 먼저 표시 후 전국 정책 표시 (region-first)
    @Query(value = """
            SELECT ws.* FROM welfare_services ws
            WHERE (
                    (
                        :status IS NULL AND (
                            (:statusFilter = 'ALL' AND ws.status IN ('ACTIVE', 'UPCOMING', 'CLOSED'))
                            OR (:statusFilter = 'EXPIRED_ONLY' AND (ws.status = 'CLOSED' OR (ws.apply_end_date IS NOT NULL AND ws.apply_end_date < CURRENT_DATE)))
                            OR ((:statusFilter IS NULL OR :statusFilter = 'ACTIVE_ONLY') AND ws.status IN ('ACTIVE', 'UPCOMING') AND (ws.apply_end_date IS NULL OR ws.apply_end_date >= CURRENT_DATE))
                        )
                    )
                    OR (:status IS NOT NULL AND ws.status = :status)
                  )
              AND (:category IS NULL OR ws.unified_category = :category)
              AND (:sourceType IS NULL OR ws.source_type = :sourceType)
              AND (:onlineApply IS NULL OR ws.is_online_apply = CASE WHEN :onlineApply = 1 THEN TRUE ELSE FALSE END)
              AND (:targetGroup IS NULL OR EXISTS (
                    SELECT 1 FROM service_tags st
                    WHERE st.service_id = ws.id AND st.tag_type = 'TARGET_GROUP' AND st.tag_value = :targetGroup
                  ))
              AND (:incomeMaxWon IS NULL OR ws.max_income IS NULL OR ws.max_income = 0 OR ws.max_income > :incomeMaxWon)
              AND (
                    :sido IS NULL
                    OR NOT EXISTS (
                        SELECT 1 FROM service_regions sr1
                        WHERE sr1.service_id = ws.id
                    )
                    OR EXISTS (
                        SELECT 1 FROM service_regions sr2
                        WHERE sr2.service_id = ws.id
                          AND (
                              sr2.sido_name = :sido
                              OR (:sidoCode IS NOT NULL AND sr2.region_code LIKE CONCAT(:sidoCode, '%'))
                          )
                          AND (
                              :sgg IS NULL
                              OR sr2.sgg_name = :sgg
                              OR (:regionCode IS NOT NULL AND sr2.region_code = :regionCode)
                          )
                    )
                  )
            ORDER BY
                -- B안: LATEST일 때만 지역이 1순위 그룹 (지역 정책 먼저 → 전국 정책)
                CASE
                    WHEN :sort = 'LATEST' AND :sido IS NOT NULL AND EXISTS (
                        SELECT 1 FROM service_regions sr3
                        WHERE sr3.service_id = ws.id
                          AND (
                              sr3.sido_name = :sido
                              OR (:sidoCode IS NOT NULL AND sr3.region_code LIKE CONCAT(:sidoCode, '%'))
                          )
                          AND (
                              :sgg IS NULL
                              OR sr3.sgg_name = :sgg
                              OR (:regionCode IS NOT NULL AND sr3.region_code = :regionCode)
                          )
                    ) THEN 0
                    WHEN :sort = 'LATEST' THEN 1
                    ELSE 0
                END ASC,
                CASE WHEN :sort = 'VIEWS' THEN COALESCE(ws.api_view_count, 0) ELSE NULL END DESC,
                CASE WHEN :sort = 'VIEWS' THEN COALESCE(ws.view_count, 0) ELSE NULL END DESC,
                CASE WHEN :sort = 'LATEST' THEN COALESCE(ws.last_modified_at, ws.registered_at, ws.created_at) ELSE NULL END DESC,
                -- NAME: UI 정렬 옵션에서 제거됐지만 API 호환성 유지 목적으로 보존
                CASE WHEN :sort = 'NAME' THEN ws.title ELSE NULL END ASC,
                -- DEADLINE: 프론트 마감임박순 기능을 위해 추가 — apply_end_date 빠른 순, NULL이면 맨 뒤
                CASE WHEN :sort = 'DEADLINE' THEN COALESCE(ws.apply_end_date, '9999-12-31') ELSE NULL END ASC,
                -- 공통 tiebreaker: 동점일 때 지역 정책 우선 (VIEWS/DEADLINE은 여기서 지역 우선 결정)
                CASE
                    WHEN :sido IS NOT NULL AND EXISTS (
                        SELECT 1 FROM service_regions sr4
                        WHERE sr4.service_id = ws.id
                          AND (
                              sr4.sido_name = :sido
                              OR (:sidoCode IS NOT NULL AND sr4.region_code LIKE CONCAT(:sidoCode, '%'))
                          )
                          AND (
                              :sgg IS NULL
                              OR sr4.sgg_name = :sgg
                              OR (:regionCode IS NOT NULL AND sr4.region_code = :regionCode)
                          )
                    ) THEN 0
                    ELSE 1
                END ASC,
                COALESCE(ws.last_modified_at, ws.registered_at, ws.created_at) DESC,
                ws.id DESC
            """,
            countQuery = """
            SELECT COUNT(*) FROM welfare_services ws
            WHERE (
                    (
                        :status IS NULL AND (
                            (:statusFilter = 'ALL' AND ws.status IN ('ACTIVE', 'UPCOMING', 'CLOSED'))
                            OR (:statusFilter = 'EXPIRED_ONLY' AND (ws.status = 'CLOSED' OR (ws.apply_end_date IS NOT NULL AND ws.apply_end_date < CURRENT_DATE)))
                            OR ((:statusFilter IS NULL OR :statusFilter = 'ACTIVE_ONLY') AND ws.status IN ('ACTIVE', 'UPCOMING') AND (ws.apply_end_date IS NULL OR ws.apply_end_date >= CURRENT_DATE))
                        )
                    )
                    OR (:status IS NOT NULL AND ws.status = :status)
                  )
              AND (:category IS NULL OR ws.unified_category = :category)
              AND (:sourceType IS NULL OR ws.source_type = :sourceType)
              AND (:onlineApply IS NULL OR ws.is_online_apply = CASE WHEN :onlineApply = 1 THEN TRUE ELSE FALSE END)
              AND (:targetGroup IS NULL OR EXISTS (
                    SELECT 1 FROM service_tags st
                    WHERE st.service_id = ws.id AND st.tag_type = 'TARGET_GROUP' AND st.tag_value = :targetGroup
                  ))
              AND (:incomeMaxWon IS NULL OR ws.max_income IS NULL OR ws.max_income = 0 OR ws.max_income > :incomeMaxWon)
              AND (
                    :sido IS NULL
                    OR NOT EXISTS (
                        SELECT 1 FROM service_regions sr1
                        WHERE sr1.service_id = ws.id
                    )
                    OR EXISTS (
                        SELECT 1 FROM service_regions sr2
                        WHERE sr2.service_id = ws.id
                          AND (
                              sr2.sido_name = :sido
                              OR (:sidoCode IS NOT NULL AND sr2.region_code LIKE CONCAT(:sidoCode, '%'))
                          )
                          AND (
                              :sgg IS NULL
                              OR sr2.sgg_name = :sgg
                              OR (:regionCode IS NOT NULL AND sr2.region_code = :regionCode)
                          )
                    )
                  )
            """, nativeQuery = true)
    Page<WelfareService> findListWithFilters(@Param("category") String category,
                                             @Param("sourceType") String sourceType,
                                             @Param("status") String status,
                                             @Param("statusFilter") String statusFilter,
                                             @Param("sido") String sido,
                                             @Param("sgg") String sgg,
                                             @Param("sidoCode") String sidoCode,
                                             @Param("regionCode") String regionCode,
                                             @Param("onlineApply") Integer onlineApply,
                                             @Param("sort") String sort,
                                             @Param("incomeMaxWon") Integer incomeMaxWon,
                                             @Param("targetGroup") String targetGroup,
                                             Pageable pageable);

    // 상태별 전체 조회 (StatusUpdateService 용)
    List<WelfareService> findByStatus(WelfareService.ServiceStatus status);
    List<WelfareService> findByStatusIn(List<WelfareService.ServiceStatus> statuses);

    List<WelfareService> findBySourceType(WelfareService.SourceType sourceType);
}
