INSERT INTO normalization_code_sets (
    code_set_key,
    domain_type,
    source_system,
    description,
    version_label,
    is_active
) VALUES
    ('GOV24_SERVICE_FIELD', 'TAXONOMY', 'GOV24', 'Gov24 서비스 분야', 'internal-2026-06-01', TRUE),
    ('GOV24_USER_TYPE_TOKEN', 'TAXONOMY', 'GOV24', 'Gov24 사용자 구분 token', 'internal-2026-06-01', TRUE),
    ('GOV24_BENEFIT_TYPE_TOKEN', 'TAXONOMY', 'GOV24', 'Gov24 지원 유형 token', 'internal-2026-06-01', TRUE)
ON CONFLICT (code_set_key) DO UPDATE SET
    domain_type = EXCLUDED.domain_type,
    source_system = EXCLUDED.source_system,
    description = EXCLUDED.description,
    version_label = EXCLUDED.version_label,
    is_active = EXCLUDED.is_active,
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
    ('GOV24_SERVICE_FIELD', 'LIFE_STABILITY', '생활안정', NULL, 10, '{"source":"gov24-list.serviceField"}'::jsonb, TRUE),
    ('GOV24_SERVICE_FIELD', 'AGRI_FISHERY', '농림축산어업', NULL, 20, '{"source":"gov24-list.serviceField"}'::jsonb, TRUE),
    ('GOV24_SERVICE_FIELD', 'CHILD_EDUCATION', '보육·교육', NULL, 30, '{"source":"gov24-list.serviceField"}'::jsonb, TRUE),
    ('GOV24_SERVICE_FIELD', 'HEALTH_MEDICAL', '보건·의료', NULL, 40, '{"source":"gov24-list.serviceField"}'::jsonb, TRUE),
    ('GOV24_SERVICE_FIELD', 'PREGNANCY_BIRTH', '임신·출산', NULL, 50, '{"source":"gov24-list.serviceField"}'::jsonb, TRUE),
    ('GOV24_SERVICE_FIELD', 'JOB_STARTUP', '고용·창업', NULL, 60, '{"source":"gov24-list.serviceField"}'::jsonb, TRUE),
    ('GOV24_SERVICE_FIELD', 'CULTURE_ENVIRONMENT', '문화·환경', NULL, 70, '{"source":"gov24-list.serviceField"}'::jsonb, TRUE),
    ('GOV24_SERVICE_FIELD', 'PROTECTION_CARE', '보호·돌봄', NULL, 80, '{"source":"gov24-list.serviceField"}'::jsonb, TRUE),
    ('GOV24_SERVICE_FIELD', 'ADMIN_SAFETY', '행정·안전', NULL, 90, '{"source":"gov24-list.serviceField"}'::jsonb, TRUE),
    ('GOV24_SERVICE_FIELD', 'HOUSING_SELF_RELIANCE', '주거·자립', NULL, 100, '{"source":"gov24-list.serviceField"}'::jsonb, TRUE),
    ('GOV24_USER_TYPE_TOKEN', 'INDIVIDUAL', '개인', NULL, 10, '{"source":"gov24-list.userType"}'::jsonb, TRUE),
    ('GOV24_USER_TYPE_TOKEN', 'ORG_FACILITY_GROUP', '법인/시설/단체', NULL, 20, '{"source":"gov24-list.userType"}'::jsonb, TRUE),
    ('GOV24_USER_TYPE_TOKEN', 'HOUSEHOLD', '가구', NULL, 30, '{"source":"gov24-list.userType"}'::jsonb, TRUE),
    ('GOV24_USER_TYPE_TOKEN', 'SMALL_BUSINESS', '소상공인', NULL, 40, '{"source":"gov24-list.userType"}'::jsonb, TRUE),
    ('GOV24_BENEFIT_TYPE_TOKEN', 'CASH', '현금', NULL, 10, '{"source":"gov24-list.supportType"}'::jsonb, TRUE),
    ('GOV24_BENEFIT_TYPE_TOKEN', 'IN_KIND', '현물', NULL, 20, '{"source":"gov24-list.supportType"}'::jsonb, TRUE),
    ('GOV24_BENEFIT_TYPE_TOKEN', 'OTHER', '기타', NULL, 30, '{"source":"gov24-list.supportType"}'::jsonb, TRUE),
    ('GOV24_BENEFIT_TYPE_TOKEN', 'CASH_REDUCTION', '현금(감면)', NULL, 40, '{"source":"gov24-list.supportType"}'::jsonb, TRUE),
    ('GOV24_BENEFIT_TYPE_TOKEN', 'VOUCHER', '이용권', NULL, 50, '{"source":"gov24-list.supportType"}'::jsonb, TRUE),
    ('GOV24_BENEFIT_TYPE_TOKEN', 'MEDICAL_SERVICE', '서비스(의료)', NULL, 60, '{"source":"gov24-list.supportType"}'::jsonb, TRUE),
    ('GOV24_BENEFIT_TYPE_TOKEN', 'FACILITY_USE', '시설이용', NULL, 70, '{"source":"gov24-list.supportType"}'::jsonb, TRUE),
    ('GOV24_BENEFIT_TYPE_TOKEN', 'EDUCATION_OTHER', '기타(교육)', NULL, 80, '{"source":"gov24-list.supportType"}'::jsonb, TRUE),
    ('GOV24_BENEFIT_TYPE_TOKEN', 'CASH_INSURANCE', '현금(보험)', NULL, 90, '{"source":"gov24-list.supportType"}'::jsonb, TRUE),
    ('GOV24_BENEFIT_TYPE_TOKEN', 'CASH_SCHOLARSHIP', '현금(장학금)', NULL, 100, '{"source":"gov24-list.supportType"}'::jsonb, TRUE),
    ('GOV24_BENEFIT_TYPE_TOKEN', 'CASH_LOAN', '현금(융자)', NULL, 110, '{"source":"gov24-list.supportType"}'::jsonb, TRUE),
    ('GOV24_BENEFIT_TYPE_TOKEN', 'COUNSELING_OTHER', '기타(상담)', NULL, 120, '{"source":"gov24-list.supportType"}'::jsonb, TRUE),
    ('GOV24_BENEFIT_TYPE_TOKEN', 'CARE_SERVICE', '서비스(돌봄)', NULL, 130, '{"source":"gov24-list.supportType"}'::jsonb, TRUE),
    ('GOV24_BENEFIT_TYPE_TOKEN', 'JOB_SERVICE', '서비스(일자리)', NULL, 140, '{"source":"gov24-list.supportType"}'::jsonb, TRUE),
    ('GOV24_BENEFIT_TYPE_TOKEN', 'MEDICAL_SUPPORT', '의료지원', NULL, 150, '{"source":"gov24-list.supportType"}'::jsonb, TRUE),
    ('GOV24_BENEFIT_TYPE_TOKEN', 'COUNSEL_LEGAL_SUPPORT', '상담/법률지원', NULL, 160, '{"source":"gov24-list.supportType"}'::jsonb, TRUE),
    ('GOV24_BENEFIT_TYPE_TOKEN', 'TECHNICAL_SUPPORT', '기술지원', NULL, 170, '{"source":"gov24-list.supportType"}'::jsonb, TRUE),
    ('GOV24_BENEFIT_TYPE_TOKEN', 'CULTURE_LEISURE_SUPPORT', '문화/여가지원', NULL, 180, '{"source":"gov24-list.supportType"}'::jsonb, TRUE),
    ('GOV24_BENEFIT_TYPE_TOKEN', 'CIVIL_SERVICE', '민원', NULL, 190, '{"source":"gov24-list.supportType"}'::jsonb, TRUE),
    ('GOV24_BENEFIT_TYPE_TOKEN', 'VOLUNTEER_DONATION', '봉사/기부', NULL, 200, '{"source":"gov24-list.supportType"}'::jsonb, TRUE)
ON CONFLICT (code_set_key, code) DO UPDATE SET
    label = EXCLUDED.label,
    parent_code = EXCLUDED.parent_code,
    sort_order = EXCLUDED.sort_order,
    extra_json = EXCLUDED.extra_json,
    is_active = EXCLUDED.is_active,
    updated_at = CURRENT_TIMESTAMP;

CREATE TEMP TABLE gov24_taxonomy_code_map (
    code_set_key VARCHAR(64) NOT NULL,
    code VARCHAR(64) NOT NULL,
    label TEXT NOT NULL
);

INSERT INTO gov24_taxonomy_code_map (code_set_key, code, label) VALUES
    ('GOV24_SERVICE_FIELD', 'LIFE_STABILITY', '생활안정'),
    ('GOV24_SERVICE_FIELD', 'AGRI_FISHERY', '농림축산어업'),
    ('GOV24_SERVICE_FIELD', 'CHILD_EDUCATION', '보육·교육'),
    ('GOV24_SERVICE_FIELD', 'HEALTH_MEDICAL', '보건·의료'),
    ('GOV24_SERVICE_FIELD', 'PREGNANCY_BIRTH', '임신·출산'),
    ('GOV24_SERVICE_FIELD', 'JOB_STARTUP', '고용·창업'),
    ('GOV24_SERVICE_FIELD', 'CULTURE_ENVIRONMENT', '문화·환경'),
    ('GOV24_SERVICE_FIELD', 'PROTECTION_CARE', '보호·돌봄'),
    ('GOV24_SERVICE_FIELD', 'ADMIN_SAFETY', '행정·안전'),
    ('GOV24_SERVICE_FIELD', 'HOUSING_SELF_RELIANCE', '주거·자립'),
    ('GOV24_USER_TYPE_TOKEN', 'INDIVIDUAL', '개인'),
    ('GOV24_USER_TYPE_TOKEN', 'ORG_FACILITY_GROUP', '법인/시설/단체'),
    ('GOV24_USER_TYPE_TOKEN', 'HOUSEHOLD', '가구'),
    ('GOV24_USER_TYPE_TOKEN', 'SMALL_BUSINESS', '소상공인'),
    ('GOV24_BENEFIT_TYPE_TOKEN', 'CASH', '현금'),
    ('GOV24_BENEFIT_TYPE_TOKEN', 'IN_KIND', '현물'),
    ('GOV24_BENEFIT_TYPE_TOKEN', 'OTHER', '기타'),
    ('GOV24_BENEFIT_TYPE_TOKEN', 'CASH_REDUCTION', '현금(감면)'),
    ('GOV24_BENEFIT_TYPE_TOKEN', 'VOUCHER', '이용권'),
    ('GOV24_BENEFIT_TYPE_TOKEN', 'MEDICAL_SERVICE', '서비스(의료)'),
    ('GOV24_BENEFIT_TYPE_TOKEN', 'FACILITY_USE', '시설이용'),
    ('GOV24_BENEFIT_TYPE_TOKEN', 'EDUCATION_OTHER', '기타(교육)'),
    ('GOV24_BENEFIT_TYPE_TOKEN', 'CASH_INSURANCE', '현금(보험)'),
    ('GOV24_BENEFIT_TYPE_TOKEN', 'CASH_SCHOLARSHIP', '현금(장학금)'),
    ('GOV24_BENEFIT_TYPE_TOKEN', 'CASH_LOAN', '현금(융자)'),
    ('GOV24_BENEFIT_TYPE_TOKEN', 'COUNSELING_OTHER', '기타(상담)'),
    ('GOV24_BENEFIT_TYPE_TOKEN', 'CARE_SERVICE', '서비스(돌봄)'),
    ('GOV24_BENEFIT_TYPE_TOKEN', 'JOB_SERVICE', '서비스(일자리)'),
    ('GOV24_BENEFIT_TYPE_TOKEN', 'MEDICAL_SUPPORT', '의료지원'),
    ('GOV24_BENEFIT_TYPE_TOKEN', 'COUNSEL_LEGAL_SUPPORT', '상담/법률지원'),
    ('GOV24_BENEFIT_TYPE_TOKEN', 'TECHNICAL_SUPPORT', '기술지원'),
    ('GOV24_BENEFIT_TYPE_TOKEN', 'CULTURE_LEISURE_SUPPORT', '문화/여가지원'),
    ('GOV24_BENEFIT_TYPE_TOKEN', 'CIVIL_SERVICE', '민원'),
    ('GOV24_BENEFIT_TYPE_TOKEN', 'VOLUNTEER_DONATION', '봉사/기부');

DELETE FROM service_taxonomy_terms blank_term
USING gov24_taxonomy_code_map code_map
WHERE blank_term.term_group = code_map.code_set_key
  AND blank_term.term_label = code_map.label
  AND blank_term.term_code = ''
  AND EXISTS (
      SELECT 1
      FROM service_taxonomy_terms coded_term
      WHERE coded_term.service_id = blank_term.service_id
        AND coded_term.term_group = blank_term.term_group
        AND coded_term.term_code = code_map.code
        AND coded_term.term_label = blank_term.term_label
        AND coded_term.authority = blank_term.authority
  );

UPDATE service_taxonomy_terms term
SET code_set_key = code_map.code_set_key,
    term_code = code_map.code,
    updated_at = CURRENT_TIMESTAMP
FROM gov24_taxonomy_code_map code_map
WHERE term.term_group = code_map.code_set_key
  AND term.term_label = code_map.label
  AND (
      term.code_set_key IS DISTINCT FROM code_map.code_set_key
      OR term.term_code IS DISTINCT FROM code_map.code
  );

DELETE FROM service_taxonomy_summary_slots blank_slot
USING gov24_taxonomy_code_map code_map
WHERE code_map.code_set_key = 'GOV24_SERVICE_FIELD'
  AND blank_slot.slot_key = 'GOV24_SERVICE_FIELD'
  AND blank_slot.slot_label = code_map.label
  AND blank_slot.slot_code = ''
  AND EXISTS (
      SELECT 1
      FROM service_taxonomy_summary_slots coded_slot
      WHERE coded_slot.service_id = blank_slot.service_id
        AND coded_slot.slot_key = blank_slot.slot_key
        AND coded_slot.slot_code = code_map.code
        AND coded_slot.authority = blank_slot.authority
  );

UPDATE service_taxonomy_summary_slots slot
SET code_set_key = 'GOV24_SERVICE_FIELD',
    slot_code = code_map.code,
    updated_at = CURRENT_TIMESTAMP
FROM gov24_taxonomy_code_map code_map
WHERE code_map.code_set_key = 'GOV24_SERVICE_FIELD'
  AND slot.slot_key = 'GOV24_SERVICE_FIELD'
  AND slot.slot_label = code_map.label
  AND (
      slot.code_set_key IS DISTINCT FROM 'GOV24_SERVICE_FIELD'
      OR slot.slot_code IS DISTINCT FROM code_map.code
  );

UPDATE service_taxonomies taxonomy
SET gov24_service_field_code = code_map.code,
    updated_at = CURRENT_TIMESTAMP
FROM gov24_taxonomy_code_map code_map
WHERE code_map.code_set_key = 'GOV24_SERVICE_FIELD'
  AND taxonomy.gov24_service_field_label = code_map.label
  AND taxonomy.gov24_service_field_code IS DISTINCT FROM code_map.code;

DROP TABLE gov24_taxonomy_code_map;
