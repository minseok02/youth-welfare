ALTER TABLE service_regions
    ADD INDEX idx_sr_service_sido_sgg (service_id, sido_name, sgg_name),
    ADD INDEX idx_sr_service_region_code (service_id, region_code);
