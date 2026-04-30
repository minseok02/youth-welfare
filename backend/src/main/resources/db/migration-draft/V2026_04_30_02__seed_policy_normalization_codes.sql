-- DRAFT ONLY
-- 아직 official codebook 전체 import 와 sidecar persistence 가 완성되지 않았으므로 실제 migration 경로(db/migration)에는 넣지 않는다.
-- canonical code table 의 metadata/대표 seed 와 현재 welfare_services 기반 summary backfill 방향만 고정한다.

USE youth_welfare;

INSERT INTO normalization_code_sets (
    code_set_key,
    domain_type,
    source_system,
    description,
    version_label,
    is_active
) VALUES
    ('SYSTEM_COMPAT_UNIFIED_CATEGORY', 'TAXONOMY', 'SYSTEM', '현재 추천/응답 unifiedCategory 호환 코드', 'draft-2026-04-30', 1),
    ('YOUTH_MAJOR', 'TAXONOMY', 'YOUTH', '온통청년 정책 대분류', 'draft-2026-04-30', 1),
    ('YOUTH_MID', 'TAXONOMY', 'YOUTH', '온통청년 정책 중분류', 'draft-2026-04-30', 1),
    ('YOUTH_KEYWORD', 'TAXONOMY', 'YOUTH', '온통청년 정책 키워드', 'draft-2026-04-30', 1),
    ('GOV24_SERVICE_FIELD', 'TAXONOMY', 'GOV24', 'Gov24 서비스 분야', 'draft-2026-04-30', 1),
    ('GOV24_USER_TYPE', 'TAXONOMY', 'GOV24', 'Gov24 사용자 구분', 'draft-2026-04-30', 1),
    ('GOV24_BENEFIT_TYPE', 'TAXONOMY', 'GOV24', 'Gov24 지원 유형', 'draft-2026-04-30', 1),
    ('GOV24_SUPPORT_CONDITION', 'FACT', 'GOV24', 'Gov24 지원 조건 코드', 'draft-2026-04-30', 1)
ON DUPLICATE KEY UPDATE
    domain_type = VALUES(domain_type),
    source_system = VALUES(source_system),
    description = VALUES(description),
    version_label = VALUES(version_label),
    is_active = VALUES(is_active),
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO normalization_codes (
    code_set_key,
    code,
    label,
    parent_code,
    sort_order,
    extra_json,
    is_active
) VALUES
    ('SYSTEM_COMPAT_UNIFIED_CATEGORY', 'JOB', '일자리', NULL, 10, JSON_OBJECT('legacyUnifiedCategory', '일자리'), 1),
    ('SYSTEM_COMPAT_UNIFIED_CATEGORY', 'HOUSING', '주거', NULL, 20, JSON_OBJECT('legacyUnifiedCategory', '주거'), 1),
    ('SYSTEM_COMPAT_UNIFIED_CATEGORY', 'EDUCATION_TRAINING', '교육·직업훈련', NULL, 30, JSON_OBJECT('legacyUnifiedCategory', '교육·직업훈련'), 1),
    ('SYSTEM_COMPAT_UNIFIED_CATEGORY', 'FINANCE_LIFE_SUPPORT', '금융·생활지원', NULL, 40, JSON_OBJECT('legacyUnifiedCategory', '금융·생활지원'), 1),
    ('SYSTEM_COMPAT_UNIFIED_CATEGORY', 'CULTURE_LEISURE', '문화·여가', NULL, 50, JSON_OBJECT('legacyUnifiedCategory', '문화·여가'), 1),
    ('SYSTEM_COMPAT_UNIFIED_CATEGORY', 'HEALTH_MEDICAL', '건강·의료', NULL, 60, JSON_OBJECT('legacyUnifiedCategory', '건강·의료'), 1),
    ('SYSTEM_COMPAT_UNIFIED_CATEGORY', 'FAMILY_CARE', '가족·돌봄', NULL, 70, JSON_OBJECT('legacyUnifiedCategory', '가족·돌봄'), 1),
    ('SYSTEM_COMPAT_UNIFIED_CATEGORY', 'SAFETY_CRISIS', '안전·위기', NULL, 80, JSON_OBJECT('legacyUnifiedCategory', '안전·위기'), 1),
    ('SYSTEM_COMPAT_UNIFIED_CATEGORY', 'PARTICIPATION_OPPORTUNITY', '참여·기회', NULL, 90, JSON_OBJECT('legacyUnifiedCategory', '참여·기회'), 1),
    ('SYSTEM_COMPAT_UNIFIED_CATEGORY', 'OTHER', '기타', NULL, 999, JSON_OBJECT('legacyUnifiedCategory', '기타'), 1),
    ('YOUTH_MAJOR', 'JOB', '일자리', NULL, 10, JSON_OBJECT('sourceLabel', '일자리'), 1),
    ('YOUTH_MAJOR', 'HOUSING', '주거', NULL, 20, JSON_OBJECT('sourceLabel', '주거'), 1),
    ('YOUTH_MAJOR', 'EDUCATION', '교육', NULL, 30, JSON_OBJECT('sourceLabel', '교육지원'), 1),
    ('YOUTH_MAJOR', 'WELFARE_CULTURE', '복지문화', NULL, 40, JSON_OBJECT('sourceLabel', '복지문화'), 1),
    ('YOUTH_MAJOR', 'PARTICIPATION_RIGHTS', '참여권리', NULL, 50, JSON_OBJECT('sourceLabel', '참여·기반'), 1)
ON DUPLICATE KEY UPDATE
    label = VALUES(label),
    parent_code = VALUES(parent_code),
    sort_order = VALUES(sort_order),
    extra_json = VALUES(extra_json),
    is_active = VALUES(is_active),
    updated_at = CURRENT_TIMESTAMP;

-- official YOUTH_MID / GOV24 code import 는 후속 task로 분리한다.
-- 이 draft 에서는 metadata set 만 만들고, 현재 welfare_services 기반 summary backfill 에 필요한 최소 대표 코드만 먼저 seed 한다.

INSERT INTO service_taxonomies (
    service_id,
    primary_source_system,
    compat_unified_category_code,
    compat_unified_category_label,
    youth_major_code,
    youth_major_label,
    youth_mid_code,
    youth_mid_label,
    authority,
    confidence
)
SELECT
    ws.id AS service_id,
    CASE
        WHEN ws.source_type = 'YOUTH' THEN 'YOUTH'
        WHEN ws.source_type IN ('BOKJIRO_CENTRAL', 'BOKJIRO_LOCAL') THEN 'BOKJIRO'
        ELSE 'SYSTEM'
    END AS primary_source_system,
    CASE ws.unified_category
        WHEN '일자리' THEN 'JOB'
        WHEN '주거' THEN 'HOUSING'
        WHEN '교육·직업훈련' THEN 'EDUCATION_TRAINING'
        WHEN '금융·생활지원' THEN 'FINANCE_LIFE_SUPPORT'
        WHEN '문화·여가' THEN 'CULTURE_LEISURE'
        WHEN '건강·의료' THEN 'HEALTH_MEDICAL'
        WHEN '가족·돌봄' THEN 'FAMILY_CARE'
        WHEN '안전·위기' THEN 'SAFETY_CRISIS'
        WHEN '참여·기회' THEN 'PARTICIPATION_OPPORTUNITY'
        ELSE 'OTHER'
    END AS compat_unified_category_code,
    ws.unified_category AS compat_unified_category_label,
    CASE
        WHEN ws.source_type = 'YOUTH' AND ws.category_main = '일자리' THEN 'JOB'
        WHEN ws.source_type = 'YOUTH' AND ws.category_main = '주거' THEN 'HOUSING'
        WHEN ws.source_type = 'YOUTH' AND ws.category_main IN ('교육', '교육지원', '교육·직업훈련') THEN 'EDUCATION'
        WHEN ws.source_type = 'YOUTH' AND ws.category_main IN ('복지문화', '금융·복지·문화') THEN 'WELFARE_CULTURE'
        WHEN ws.source_type = 'YOUTH' AND ws.category_main IN ('참여권리', '참여·기반') THEN 'PARTICIPATION_RIGHTS'
        ELSE NULL
    END AS youth_major_code,
    CASE
        WHEN ws.source_type = 'YOUTH' THEN ws.category_main
        ELSE NULL
    END AS youth_major_label,
    NULL AS youth_mid_code,
    CASE
        WHEN ws.source_type = 'YOUTH' AND TRIM(COALESCE(ws.category_sub, '')) IN (
            '취업',
            '재직자',
            '창업',
            '주택 및 거주지',
            '기숙사',
            '전월세 및 주거급여 지원',
            '미래역량강화',
            '교육비지원',
            '온라인교육',
            '취약계층 및 금융지원',
            '건강',
            '예술인지원',
            '문화활동',
            '청년참여',
            '정책인프라구축',
            '청년국제교류',
            '권익보호'
        ) THEN TRIM(ws.category_sub)
        ELSE NULL
    END AS youth_mid_label,
    CASE
        WHEN ws.source_type = 'YOUTH' THEN 'OFFICIAL'
        WHEN ws.source_type IN ('BOKJIRO_CENTRAL', 'BOKJIRO_LOCAL') THEN 'SYSTEM_DERIVED'
        ELSE 'SYSTEM_DERIVED'
    END AS authority,
    CASE
        WHEN ws.source_type = 'YOUTH' THEN 1.000
        WHEN ws.source_type IN ('BOKJIRO_CENTRAL', 'BOKJIRO_LOCAL') THEN 0.850
        ELSE 0.700
    END AS confidence
FROM welfare_services ws
LEFT JOIN service_taxonomies st
    ON st.service_id = ws.id
WHERE st.service_id IS NULL;
