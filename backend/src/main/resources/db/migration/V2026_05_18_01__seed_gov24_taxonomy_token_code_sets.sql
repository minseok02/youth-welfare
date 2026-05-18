INSERT INTO normalization_code_sets (
    code_set_key,
    domain_type,
    source_system,
    description,
    version_label,
    is_active
) VALUES
    ('GOV24_USER_TYPE_TOKEN', 'TAXONOMY', 'GOV24', 'Gov24 사용자 구분 token', 'draft-2026-05-18', TRUE),
    ('GOV24_BENEFIT_TYPE_TOKEN', 'TAXONOMY', 'GOV24', 'Gov24 지원 유형 token', 'draft-2026-05-18', TRUE)
ON CONFLICT (code_set_key) DO UPDATE SET
    domain_type = EXCLUDED.domain_type,
    source_system = EXCLUDED.source_system,
    description = EXCLUDED.description,
    version_label = EXCLUDED.version_label,
    is_active = EXCLUDED.is_active,
    updated_at = CURRENT_TIMESTAMP;
