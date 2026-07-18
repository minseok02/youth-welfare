WITH remaps(source_type, source_id, from_category, to_category, to_code) AS (
    VALUES
        ('BOKJIRO_LOCAL', 'WLF00004900', '주거', '일자리', 'JOB'),
        ('BOKJIRO_LOCAL', 'WLF00005229', '주거', '일자리', 'JOB'),
        ('BOKJIRO_LOCAL', 'WLF00005751', '주거', '일자리', 'JOB'),
        ('BOKJIRO_LOCAL', 'WLF00006470', '주거', '일자리', 'JOB'),
        ('BOKJIRO_LOCAL', 'WLF00006576', '주거', '일자리', 'JOB'),
        ('GOV24', '475000000194', '주거', '일자리', 'JOB'),
        ('GOV24', '488000000120', '주거', '일자리', 'JOB'),
        ('YOUTH', '20260330005400212321', '주거', '일자리', 'JOB'),
        ('YOUTH', '20260330005400212324', '주거', '일자리', 'JOB'),
        ('YOUTH', '20260330005400212325', '주거', '일자리', 'JOB'),
        ('YOUTH', '20260504005400213017', '주거', '일자리', 'JOB')
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
