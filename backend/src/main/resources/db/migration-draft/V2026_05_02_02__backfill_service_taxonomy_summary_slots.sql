-- DRAFT ONLY
-- 기존 service_taxonomies summary row를 generic summary slot row로 옮기는 로컬 backfill 초안.
-- 아직 정식 migration 경로(db/migration)가 아니라 local draft schema/replay 검증용으로만 사용한다.

USE youth_welfare;

DELETE FROM service_taxonomy_summary_slots
WHERE slot_key IN (
    'YOUTH_MAJOR',
    'YOUTH_MID',
    'GOV24_SERVICE_FIELD',
    'GOV24_USER_TYPE',
    'GOV24_BENEFIT_TYPE',
    'PROVISION_METHOD'
);

INSERT INTO service_taxonomy_summary_slots (
    service_id,
    slot_key,
    code_set_key,
    slot_code,
    slot_label,
    source_field,
    authority,
    confidence
)
SELECT
    st.service_id,
    slots.slot_key,
    slots.code_set_key,
    slots.slot_code,
    slots.slot_label,
    NULL AS source_field,
    st.authority,
    st.confidence
FROM service_taxonomies st
JOIN (
    SELECT
        service_id,
        'YOUTH_MAJOR' AS slot_key,
        'YOUTH_MAJOR' AS code_set_key,
        COALESCE(youth_major_code, '') AS slot_code,
        youth_major_label AS slot_label
    FROM service_taxonomies
    WHERE youth_major_label IS NOT NULL

    UNION ALL

    SELECT
        service_id,
        'YOUTH_MID' AS slot_key,
        'YOUTH_MID' AS code_set_key,
        COALESCE(youth_mid_code, '') AS slot_code,
        youth_mid_label AS slot_label
    FROM service_taxonomies
    WHERE youth_mid_label IS NOT NULL

    UNION ALL

    SELECT
        service_id,
        'GOV24_SERVICE_FIELD' AS slot_key,
        'GOV24_SERVICE_FIELD' AS code_set_key,
        COALESCE(gov24_service_field_code, '') AS slot_code,
        gov24_service_field_label AS slot_label
    FROM service_taxonomies
    WHERE gov24_service_field_label IS NOT NULL

    UNION ALL

    SELECT
        service_id,
        'GOV24_USER_TYPE' AS slot_key,
        'GOV24_USER_TYPE' AS code_set_key,
        COALESCE(gov24_user_type_code, '') AS slot_code,
        gov24_user_type_label AS slot_label
    FROM service_taxonomies
    WHERE gov24_user_type_label IS NOT NULL

    UNION ALL

    SELECT
        service_id,
        'GOV24_BENEFIT_TYPE' AS slot_key,
        'GOV24_BENEFIT_TYPE' AS code_set_key,
        COALESCE(gov24_benefit_type_code, '') AS slot_code,
        gov24_benefit_type_label AS slot_label
    FROM service_taxonomies
    WHERE gov24_benefit_type_label IS NOT NULL

    UNION ALL

    SELECT
        service_id,
        'PROVISION_METHOD' AS slot_key,
        NULL AS code_set_key,
        '' AS slot_code,
        provision_method_label AS slot_label
    FROM service_taxonomies
    WHERE provision_method_label IS NOT NULL
) slots
    ON slots.service_id = st.service_id;
