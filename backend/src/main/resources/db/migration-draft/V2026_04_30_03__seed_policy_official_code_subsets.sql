-- DRAFT ONLY
-- 실제 migration 경로(db/migration)가 아니라 canonical code table 설계/리허설용 초안이다.
-- 이 파일은 "공식 source가 stable code 값을 공개한 집합"만 먼저 seed 한다.
-- `YOUTH_MID`, `GOV24_SERVICE_FIELD`, `GOV24_USER_TYPE`, `GOV24_BENEFIT_TYPE` 처럼
-- 공개 필드/라벨은 확인됐지만 stable codebook 까지는 확보하지 못한 집합은 후속 task로 분리한다.

USE youth_welfare;

INSERT INTO normalization_code_sets (
    code_set_key,
    domain_type,
    source_system,
    description,
    version_label,
    is_active
) VALUES
    ('YOUTH_PROVIDER_GROUP', 'TAXONOMY', 'YOUTH', '온통청년 제공기관 그룹코드', 'draft-2026-04-30-youth-codeinfo', 1),
    ('YOUTH_PROVISION_METHOD', 'TAXONOMY', 'YOUTH', '온통청년 정책제공방법코드', 'draft-2026-04-30-youth-codeinfo', 1),
    ('YOUTH_EMPLOYMENT_REQUIREMENT', 'FACT', 'YOUTH', '온통청년 정책취업요건코드', 'draft-2026-04-30-youth-codeinfo', 1),
    ('YOUTH_EDUCATION_REQUIREMENT', 'FACT', 'YOUTH', '온통청년 정책학력요건코드', 'draft-2026-04-30-youth-codeinfo', 1),
    ('YOUTH_SPECIAL_REQUIREMENT', 'FACT', 'YOUTH', '온통청년 정책특화요건코드', 'draft-2026-04-30-youth-codeinfo', 1),
    ('YOUTH_MARITAL_STATUS', 'FACT', 'YOUTH', '온통청년 결혼상태코드', 'draft-2026-04-30-youth-codeinfo', 1),
    ('YOUTH_INCOME_CONDITION_TYPE', 'FACT', 'YOUTH', '온통청년 소득조건구분코드', 'draft-2026-04-30-youth-codeinfo', 1)
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
    ('YOUTH_PROVIDER_GROUP', '0054001', '중앙부처', NULL, 1, JSON_OBJECT('sourceField', 'pvsnInstGroupCd', 'sourceSheet', '코드정보', 'sourceUrl', 'https://www.youthcenter.go.kr/downloadform/API%EC%BD%94%EB%93%9C%EC%A0%95%EB%B3%B4.xlsx'), 1),
    ('YOUTH_PROVIDER_GROUP', '0054002', '지자체', NULL, 2, JSON_OBJECT('sourceField', 'pvsnInstGroupCd', 'sourceSheet', '코드정보', 'sourceUrl', 'https://www.youthcenter.go.kr/downloadform/API%EC%BD%94%EB%93%9C%EC%A0%95%EB%B3%B4.xlsx'), 1),

    ('YOUTH_PROVISION_METHOD', '0042001', '인프라 구축', NULL, 1, JSON_OBJECT('sourceField', 'plcyPvsnMthdCd', 'sourceSheet', '코드정보'), 1),
    ('YOUTH_PROVISION_METHOD', '0042002', '프로그램', NULL, 2, JSON_OBJECT('sourceField', 'plcyPvsnMthdCd', 'sourceSheet', '코드정보'), 1),
    ('YOUTH_PROVISION_METHOD', '0042003', '직접대출', NULL, 3, JSON_OBJECT('sourceField', 'plcyPvsnMthdCd', 'sourceSheet', '코드정보'), 1),
    ('YOUTH_PROVISION_METHOD', '0042004', '공공기관', NULL, 4, JSON_OBJECT('sourceField', 'plcyPvsnMthdCd', 'sourceSheet', '코드정보'), 1),
    ('YOUTH_PROVISION_METHOD', '0042005', '계약(위탁운영)', NULL, 5, JSON_OBJECT('sourceField', 'plcyPvsnMthdCd', 'sourceSheet', '코드정보'), 1),
    ('YOUTH_PROVISION_METHOD', '0042006', '보조금', NULL, 6, JSON_OBJECT('sourceField', 'plcyPvsnMthdCd', 'sourceSheet', '코드정보'), 1),
    ('YOUTH_PROVISION_METHOD', '0042007', '대출보증', NULL, 7, JSON_OBJECT('sourceField', 'plcyPvsnMthdCd', 'sourceSheet', '코드정보'), 1),
    ('YOUTH_PROVISION_METHOD', '0042008', '공적보험', NULL, 8, JSON_OBJECT('sourceField', 'plcyPvsnMthdCd', 'sourceSheet', '코드정보'), 1),
    ('YOUTH_PROVISION_METHOD', '0042009', '조세지출', NULL, 9, JSON_OBJECT('sourceField', 'plcyPvsnMthdCd', 'sourceSheet', '코드정보'), 1),
    ('YOUTH_PROVISION_METHOD', '0042010', '바우처', NULL, 10, JSON_OBJECT('sourceField', 'plcyPvsnMthdCd', 'sourceSheet', '코드정보'), 1),
    ('YOUTH_PROVISION_METHOD', '0042011', '정보제공', NULL, 11, JSON_OBJECT('sourceField', 'plcyPvsnMthdCd', 'sourceSheet', '코드정보'), 1),
    ('YOUTH_PROVISION_METHOD', '0042012', '경제적 규제', NULL, 12, JSON_OBJECT('sourceField', 'plcyPvsnMthdCd', 'sourceSheet', '코드정보'), 1),
    ('YOUTH_PROVISION_METHOD', '0042013', '기타', NULL, 13, JSON_OBJECT('sourceField', 'plcyPvsnMthdCd', 'sourceSheet', '코드정보'), 1),

    ('YOUTH_MARITAL_STATUS', '0055001', '기혼', NULL, 1, JSON_OBJECT('sourceField', 'mrgSttsCd', 'sourceSheet', '코드정보'), 1),
    ('YOUTH_MARITAL_STATUS', '0055002', '미혼', NULL, 2, JSON_OBJECT('sourceField', 'mrgSttsCd', 'sourceSheet', '코드정보'), 1),
    ('YOUTH_MARITAL_STATUS', '0055003', '제한없음', NULL, 3, JSON_OBJECT('sourceField', 'mrgSttsCd', 'sourceSheet', '코드정보'), 1),

    ('YOUTH_INCOME_CONDITION_TYPE', '0043001', '무관', NULL, 1, JSON_OBJECT('sourceField', 'earnCndSeCd', 'sourceSheet', '코드정보'), 1),
    ('YOUTH_INCOME_CONDITION_TYPE', '0043002', '연소득', NULL, 2, JSON_OBJECT('sourceField', 'earnCndSeCd', 'sourceSheet', '코드정보'), 1),
    ('YOUTH_INCOME_CONDITION_TYPE', '0043003', '기타', NULL, 3, JSON_OBJECT('sourceField', 'earnCndSeCd', 'sourceSheet', '코드정보'), 1),

    ('YOUTH_EMPLOYMENT_REQUIREMENT', '0013001', '재직자', NULL, 1, JSON_OBJECT('sourceField', 'jobCd', 'sourceSheet', '코드정보'), 1),
    ('YOUTH_EMPLOYMENT_REQUIREMENT', '0013002', '자영업자', NULL, 2, JSON_OBJECT('sourceField', 'jobCd', 'sourceSheet', '코드정보'), 1),
    ('YOUTH_EMPLOYMENT_REQUIREMENT', '0013003', '미취업자', NULL, 3, JSON_OBJECT('sourceField', 'jobCd', 'sourceSheet', '코드정보'), 1),
    ('YOUTH_EMPLOYMENT_REQUIREMENT', '0013004', '프리랜서', NULL, 4, JSON_OBJECT('sourceField', 'jobCd', 'sourceSheet', '코드정보'), 1),
    ('YOUTH_EMPLOYMENT_REQUIREMENT', '0013005', '일용근로자', NULL, 5, JSON_OBJECT('sourceField', 'jobCd', 'sourceSheet', '코드정보'), 1),
    ('YOUTH_EMPLOYMENT_REQUIREMENT', '0013006', '(예비)창업자', NULL, 6, JSON_OBJECT('sourceField', 'jobCd', 'sourceSheet', '코드정보'), 1),
    ('YOUTH_EMPLOYMENT_REQUIREMENT', '0013007', '단기근로자', NULL, 7, JSON_OBJECT('sourceField', 'jobCd', 'sourceSheet', '코드정보'), 1),
    ('YOUTH_EMPLOYMENT_REQUIREMENT', '0013008', '영농종사자', NULL, 8, JSON_OBJECT('sourceField', 'jobCd', 'sourceSheet', '코드정보'), 1),
    ('YOUTH_EMPLOYMENT_REQUIREMENT', '0013009', '기타', NULL, 9, JSON_OBJECT('sourceField', 'jobCd', 'sourceSheet', '코드정보'), 1),
    ('YOUTH_EMPLOYMENT_REQUIREMENT', '0013010', '제한없음', NULL, 10, JSON_OBJECT('sourceField', 'jobCd', 'sourceSheet', '코드정보'), 1),

    ('YOUTH_EDUCATION_REQUIREMENT', '0049001', '고졸 미만', NULL, 1, JSON_OBJECT('sourceField', 'schoolCd', 'sourceSheet', '코드정보'), 1),
    ('YOUTH_EDUCATION_REQUIREMENT', '0049002', '고교 재학', NULL, 2, JSON_OBJECT('sourceField', 'schoolCd', 'sourceSheet', '코드정보'), 1),
    ('YOUTH_EDUCATION_REQUIREMENT', '0049003', '고졸 예정', NULL, 3, JSON_OBJECT('sourceField', 'schoolCd', 'sourceSheet', '코드정보'), 1),
    ('YOUTH_EDUCATION_REQUIREMENT', '0049004', '고교 졸업', NULL, 4, JSON_OBJECT('sourceField', 'schoolCd', 'sourceSheet', '코드정보'), 1),
    ('YOUTH_EDUCATION_REQUIREMENT', '0049005', '대학 재학', NULL, 5, JSON_OBJECT('sourceField', 'schoolCd', 'sourceSheet', '코드정보'), 1),
    ('YOUTH_EDUCATION_REQUIREMENT', '0049006', '대졸 예정', NULL, 6, JSON_OBJECT('sourceField', 'schoolCd', 'sourceSheet', '코드정보'), 1),
    ('YOUTH_EDUCATION_REQUIREMENT', '0049007', '대학 졸업', NULL, 7, JSON_OBJECT('sourceField', 'schoolCd', 'sourceSheet', '코드정보'), 1),
    ('YOUTH_EDUCATION_REQUIREMENT', '0049008', '석·박사', NULL, 8, JSON_OBJECT('sourceField', 'schoolCd', 'sourceSheet', '코드정보'), 1),
    ('YOUTH_EDUCATION_REQUIREMENT', '0049009', '기타', NULL, 9, JSON_OBJECT('sourceField', 'schoolCd', 'sourceSheet', '코드정보'), 1),
    ('YOUTH_EDUCATION_REQUIREMENT', '0049010', '제한없음', NULL, 10, JSON_OBJECT('sourceField', 'schoolCd', 'sourceSheet', '코드정보'), 1),

    ('YOUTH_SPECIAL_REQUIREMENT', '0014001', '중소기업', NULL, 1, JSON_OBJECT('sourceField', 'sbizCd', 'sourceSheet', '코드정보'), 1),
    ('YOUTH_SPECIAL_REQUIREMENT', '0014002', '여성', NULL, 2, JSON_OBJECT('sourceField', 'sbizCd', 'sourceSheet', '코드정보'), 1),
    ('YOUTH_SPECIAL_REQUIREMENT', '0014003', '기초생활수급자', NULL, 3, JSON_OBJECT('sourceField', 'sbizCd', 'sourceSheet', '코드정보'), 1),
    ('YOUTH_SPECIAL_REQUIREMENT', '0014004', '한부모가정', NULL, 4, JSON_OBJECT('sourceField', 'sbizCd', 'sourceSheet', '코드정보'), 1),
    ('YOUTH_SPECIAL_REQUIREMENT', '0014005', '장애인', NULL, 5, JSON_OBJECT('sourceField', 'sbizCd', 'sourceSheet', '코드정보'), 1),
    ('YOUTH_SPECIAL_REQUIREMENT', '0014006', '농업인', NULL, 6, JSON_OBJECT('sourceField', 'sbizCd', 'sourceSheet', '코드정보'), 1),
    ('YOUTH_SPECIAL_REQUIREMENT', '0014007', '군인', NULL, 7, JSON_OBJECT('sourceField', 'sbizCd', 'sourceSheet', '코드정보'), 1),
    ('YOUTH_SPECIAL_REQUIREMENT', '0014008', '지역인재', NULL, 8, JSON_OBJECT('sourceField', 'sbizCd', 'sourceSheet', '코드정보'), 1),
    ('YOUTH_SPECIAL_REQUIREMENT', '0014009', '기타', NULL, 9, JSON_OBJECT('sourceField', 'sbizCd', 'sourceSheet', '코드정보'), 1),
    ('YOUTH_SPECIAL_REQUIREMENT', '0014010', '제한없음', NULL, 10, JSON_OBJECT('sourceField', 'sbizCd', 'sourceSheet', '코드정보'), 1),

    ('GOV24_SUPPORT_CONDITION', 'JA0101', '남성', NULL, 1, JSON_OBJECT('factGroup', 'GENDER', 'sourceSection', 'supportConditions', 'sourceUrl', 'https://www.data.go.kr/data/15113968/openapi.do'), 1),
    ('GOV24_SUPPORT_CONDITION', 'JA0102', '여성', NULL, 2, JSON_OBJECT('factGroup', 'GENDER', 'sourceSection', 'supportConditions', 'sourceUrl', 'https://www.data.go.kr/data/15113968/openapi.do'), 1),
    ('GOV24_SUPPORT_CONDITION', 'JA0110', '대상연령(시작)', NULL, 10, JSON_OBJECT('factGroup', 'AGE', 'sourceSection', 'supportConditions', 'sourceUrl', 'https://www.data.go.kr/data/15113968/openapi.do'), 1),
    ('GOV24_SUPPORT_CONDITION', 'JA0111', '대상연령(종료)', NULL, 11, JSON_OBJECT('factGroup', 'AGE', 'sourceSection', 'supportConditions', 'sourceUrl', 'https://www.data.go.kr/data/15113968/openapi.do'), 1),
    ('GOV24_SUPPORT_CONDITION', 'JA0201', '중위소득 0~50%', NULL, 20, JSON_OBJECT('factGroup', 'INCOME', 'sourceSection', 'supportConditions', 'sourceUrl', 'https://www.data.go.kr/data/15113968/openapi.do'), 1),
    ('GOV24_SUPPORT_CONDITION', 'JA0202', '중위소득 51~75%', NULL, 21, JSON_OBJECT('factGroup', 'INCOME', 'sourceSection', 'supportConditions', 'sourceUrl', 'https://www.data.go.kr/data/15113968/openapi.do'), 1),
    ('GOV24_SUPPORT_CONDITION', 'JA0203', '중위소득 76~100%', NULL, 22, JSON_OBJECT('factGroup', 'INCOME', 'sourceSection', 'supportConditions', 'sourceUrl', 'https://www.data.go.kr/data/15113968/openapi.do'), 1),
    ('GOV24_SUPPORT_CONDITION', 'JA0204', '중위소득 101~200%', NULL, 23, JSON_OBJECT('factGroup', 'INCOME', 'sourceSection', 'supportConditions', 'sourceUrl', 'https://www.data.go.kr/data/15113968/openapi.do'), 1),
    ('GOV24_SUPPORT_CONDITION', 'JA0205', '중위소득 200% 초과', NULL, 24, JSON_OBJECT('factGroup', 'INCOME', 'sourceSection', 'supportConditions', 'sourceUrl', 'https://www.data.go.kr/data/15113968/openapi.do'), 1),
    ('GOV24_SUPPORT_CONDITION', 'JA0320', '대학생/대학원생', NULL, 32, JSON_OBJECT('factGroup', 'EDUCATION', 'sourceSection', 'supportConditions', 'sourceUrl', 'https://www.data.go.kr/data/15113968/openapi.do'), 1),
    ('GOV24_SUPPORT_CONDITION', 'JA0327', '구직자/실업자', NULL, 39, JSON_OBJECT('factGroup', 'EMPLOYMENT', 'sourceSection', 'supportConditions', 'sourceUrl', 'https://www.data.go.kr/data/15113968/openapi.do'), 1),
    ('GOV24_SUPPORT_CONDITION', 'JA0412', '무주택세대', NULL, 52, JSON_OBJECT('factGroup', 'HOUSEHOLD', 'sourceSection', 'supportConditions', 'sourceUrl', 'https://www.data.go.kr/data/15113968/openapi.do'), 1)
ON DUPLICATE KEY UPDATE
    label = VALUES(label),
    parent_code = VALUES(parent_code),
    sort_order = VALUES(sort_order),
    extra_json = VALUES(extra_json),
    is_active = VALUES(is_active),
    updated_at = CURRENT_TIMESTAMP;

-- `YOUTH_MID` 는 온통청년 공개 코드정의서에 번호 + 라벨만 공개되어 있고 stable code 값은 보이지 않는다.
-- 따라서 official code import 대신 label-only taxonomy term backfill 까지만 이번 draft 범위에 포함한다.
INSERT INTO service_taxonomy_terms (
    service_id,
    term_group,
    code_set_key,
    term_code,
    term_label,
    source_field,
    authority,
    sort_order
)
SELECT
    ws.id,
    'YOUTH_MID',
    'YOUTH_MID',
    '',
    TRIM(ws.category_sub),
    'mclsfNm',
    'OFFICIAL',
    CASE TRIM(ws.category_sub)
        WHEN '취업' THEN 1
        WHEN '재직자' THEN 2
        WHEN '창업' THEN 3
        WHEN '주택 및 거주지' THEN 4
        WHEN '기숙사' THEN 5
        WHEN '전월세 및 주거급여 지원' THEN 6
        WHEN '미래역량강화' THEN 7
        WHEN '교육비지원' THEN 8
        WHEN '온라인교육' THEN 9
        WHEN '취약계층 및 금융지원' THEN 10
        WHEN '건강' THEN 11
        WHEN '예술인지원' THEN 12
        WHEN '문화활동' THEN 13
        WHEN '청년참여' THEN 14
        WHEN '정책인프라구축' THEN 15
        WHEN '청년국제교류' THEN 16
        WHEN '권익보호' THEN 17
        ELSE 999
    END
FROM welfare_services ws
LEFT JOIN service_taxonomy_terms stt
    ON stt.service_id = ws.id
   AND stt.term_group = 'YOUTH_MID'
   AND stt.term_code = ''
   AND stt.term_label = TRIM(ws.category_sub)
   AND stt.authority = 'OFFICIAL'
WHERE ws.source_type = 'YOUTH'
  AND TRIM(COALESCE(ws.category_sub, '')) <> ''
  AND stt.id IS NULL;

-- `GOV24_SERVICE_FIELD`, `GOV24_USER_TYPE`, `GOV24_BENEFIT_TYPE` 는 official API field 자체는 확인했지만,
-- 이번 조사 범위에서는 stable finite codebook/value inventory 를 확보하지 못했다.
-- 이 집합은 live payload 또는 별도 공식 문서 기준 inventory import/backfill task로 분리한다.
