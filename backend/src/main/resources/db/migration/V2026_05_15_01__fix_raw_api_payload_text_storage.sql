CREATE TEMP TABLE raw_api_payload_oid_candidates AS
SELECT id,
       payload_json::oid AS loid,
       convert_from(lo_get(payload_json::oid), 'UTF8') AS payload_text
FROM raw_api_payloads
WHERE payload_json ~ '^[0-9]+$';

UPDATE raw_api_payloads rap
SET payload_json = c.payload_text
FROM raw_api_payload_oid_candidates c
WHERE rap.id = c.id;

DROP TABLE raw_api_payload_oid_candidates;
