-- DRAFT ONLY
-- 기존 local draft DB에 이미 service_taxonomy_summary_slots 가 생성된 경우
-- slot_label 길이 부족 문제를 TEXT 로 보정한다.

USE youth_welfare;

ALTER TABLE service_taxonomy_summary_slots
    DROP INDEX uq_service_summary_slot,
    MODIFY COLUMN slot_label TEXT NOT NULL,
    ADD UNIQUE KEY uq_service_summary_slot (service_id, slot_key, slot_code, authority);
