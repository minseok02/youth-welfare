WITH remaps(source_type, source_id, from_category, to_category, to_code) AS (
    VALUES
        ('GOV24', '646000000136', '일자리', '주거', 'HOUSING'),
        ('GOV24', '442000000656', '일자리', '주거', 'HOUSING'),
        ('BOKJIRO_LOCAL', 'WLF00002989', '주거', '교육·직업훈련', 'EDUCATION_TRAINING'),
        ('YOUTH', '20250718005400211454', '금융·생활지원', '주거', 'HOUSING')
),
updated_services AS (
    UPDATE welfare_services ws
       SET unified_category = remaps.to_category,
           updated_at = CURRENT_TIMESTAMP
      FROM remaps
     WHERE ws.source_type = remaps.source_type
       AND ws.source_id = remaps.source_id
       AND ws.unified_category = remaps.from_category
     RETURNING ws.id, remaps.to_category, remaps.to_code
)
UPDATE service_taxonomies st
   SET compat_unified_category_code = updated_services.to_code,
       compat_unified_category_label = updated_services.to_category,
       updated_at = CURRENT_TIMESTAMP
  FROM updated_services
 WHERE st.service_id = updated_services.id;
