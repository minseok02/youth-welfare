WITH remaps(source_type, source_id, from_category, to_category, to_code) AS (
    VALUES
        ('GOV24', '161300000099', '금융·생활지원', '주거', 'HOUSING'),
        ('GOV24', '631000000132', '금융·생활지원', '주거', 'HOUSING'),
        ('GOV24', '391000000152', '금융·생활지원', '주거', 'HOUSING'),
        ('GOV24', '315000000269', '금융·생활지원', '주거', 'HOUSING'),
        ('GOV24', '641000000124', '금융·생활지원', '주거', 'HOUSING'),
        ('GOV24', '488000000133', '금융·생활지원', '주거', 'HOUSING'),
        ('GOV24', '486000000132', '금융·생활지원', '주거', 'HOUSING'),
        ('GOV24', '331000000105', '금융·생활지원', '주거', 'HOUSING'),
        ('GOV24', '541000000155', '금융·생활지원', '주거', 'HOUSING'),
        ('GOV24', '414000000456', '금융·생활지원', '주거', 'HOUSING'),
        ('GOV24', '450000000168', '금융·생활지원', '주거', 'HOUSING'),
        ('GOV24', '451000000242', '금융·생활지원', '주거', 'HOUSING'),
        ('GOV24', '458000000125', '금융·생활지원', '주거', 'HOUSING'),
        ('GOV24', '630000000681', '금융·생활지원', '주거', 'HOUSING'),
        ('GOV24', '641000000192', '주거', '금융·생활지원', 'FINANCE_LIFE_SUPPORT'),
        ('GOV24', '630000000131', '주거', '금융·생활지원', 'FINANCE_LIFE_SUPPORT'),
        ('GOV24', '646000000200', '주거', '금융·생활지원', 'FINANCE_LIFE_SUPPORT'),
        ('GOV24', '494000000114', '주거', '금융·생활지원', 'FINANCE_LIFE_SUPPORT'),
        ('GOV24', '497000000117', '주거', '금융·생활지원', 'FINANCE_LIFE_SUPPORT'),
        ('GOV24', '540000000128', '주거', '금융·생활지원', 'FINANCE_LIFE_SUPPORT'),
        ('GOV24', '407000000115', '주거', '금융·생활지원', 'FINANCE_LIFE_SUPPORT'),
        ('GOV24', '628000000159', '주거', '금융·생활지원', 'FINANCE_LIFE_SUPPORT'),
        ('GOV24', '439000000830', '주거', '금융·생활지원', 'FINANCE_LIFE_SUPPORT'),
        ('GOV24', '641000000695', '주거', '금융·생활지원', 'FINANCE_LIFE_SUPPORT'),
        ('GOV24', '641000000697', '주거', '금융·생활지원', 'FINANCE_LIFE_SUPPORT'),
        ('GOV24', '461000000308', '주거', '금융·생활지원', 'FINANCE_LIFE_SUPPORT'),
        ('BOKJIRO_LOCAL', 'WLF00005099', '주거', '금융·생활지원', 'FINANCE_LIFE_SUPPORT'),
        ('BOKJIRO_LOCAL', 'WLF00006376', '주거', '금융·생활지원', 'FINANCE_LIFE_SUPPORT')
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
