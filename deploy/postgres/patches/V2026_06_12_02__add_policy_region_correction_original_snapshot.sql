ALTER TABLE policy_region_corrections
    ADD COLUMN IF NOT EXISTS original_regions_json TEXT NOT NULL DEFAULT '[]';
