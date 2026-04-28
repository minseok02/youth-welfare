# DB Migration Guide

현재 프로젝트는 신규 DB 초기화는 `backend/src/main/resources/db/schema.sql`로 처리하고, 기존 운영 DB 갱신은 수동 마이그레이션 SQL로 처리한다.

## 대상

- 이미 생성되어 데이터가 있는 MySQL
- `schema.sql`이 컨테이너 최초 기동 시점에만 적용된 환경

## 최신 마이그레이션

- 파일: [`backend/src/main/resources/db/migration/V2026_04_27_03__add_user_core_split_tables.sql`](../backend/src/main/resources/db/migration/V2026_04_27_03__add_user_core_split_tables.sql)
- 포함 내용:
  - `auth_users`, `user_profiles` 생성
  - `youth_welfare_pii.user_pii` 생성
  - 기존 `users` 기준 1회 backfill
  - `auth_users.email_lookup_hash`, `user_profiles.age/age_band/has_*` 파생값 채움
  - `user_pii` 는 migration 시점에는 `phone_enc` 만 backfill하고, 이후 최신 백엔드의 관리자 백필 API로 `email_enc/name_enc/birth_date_enc` 를 채우는 구조

- 파일: [`backend/src/main/resources/db/migration/V2026_04_27_02__add_user_key_columns.sql`](../backend/src/main/resources/db/migration/V2026_04_27_02__add_user_key_columns.sql)
- 포함 내용:
  - `users.user_key CHAR(32)` 추가 및 기존 사용자 deterministic hash backfill
  - `user_attributes`, `user_priorities`, `user_recommendations`, `recommendation_logs`, `notifications`, `chat_sessions`, `service_view_logs` 에 `user_key` nullable 컬럼 추가
  - 기존 `user_id -> users.user_key` 기준 backfill
  - PII 분리 1단계용 공용 사용자 식별자 호환 경로 준비

- 파일: [`backend/src/main/resources/db/migration/V2026_04_27_01__add_service_region_compound_indexes.sql`](../backend/src/main/resources/db/migration/V2026_04_27_01__add_service_region_compound_indexes.sql)
- 포함 내용:
  - `service_regions(service_id, sido_name, sgg_name)` 복합 인덱스 추가
  - `service_regions(service_id, region_code)` 복합 인덱스 추가
  - 지역 검색 `EXISTS` 서브쿼리와 추천 지역 후보 판정 비용 완화

- 파일: [`backend/src/main/resources/db/migration/V2026_04_25_01__add_chat_tables.sql`](../backend/src/main/resources/db/migration/V2026_04_25_01__add_chat_tables.sql)
- 포함 내용:
  - `chat_sessions` 생성
  - `chat_messages` 생성
  - 챗 세션/메시지 기본 인덱스 추가
  - 로그아웃/회원탈퇴 시 `users -> chat_sessions -> chat_messages` cascade delete 준비

- 파일: [`backend/src/main/resources/db/migration/V2026_04_24_01__add_search_youth_relevance.sql`](../backend/src/main/resources/db/migration/V2026_04_24_01__add_search_youth_relevance.sql)
- 포함 내용:
  - `welfare_services.search_youth_relevant` 컬럼 추가
  - `idx_ws_search_youth` 인덱스 추가
  - 검색용 청년 관련성 플래그 기반 SQL 필터 준비

- 파일: [`backend/src/main/resources/db/migration/V2026_04_23_01__add_api_sync_logs.sql`](../backend/src/main/resources/db/migration/V2026_04_23_01__add_api_sync_logs.sql)
- 포함 내용:
  - `api_sync_logs` 생성
  - source별 수집 실행 상태와 저장/스킵/필터/실패 건수 기록

- 파일: [`backend/src/main/resources/db/migration/V2026_04_17_01__recent_schema_updates.sql`](../backend/src/main/resources/db/migration/V2026_04_17_01__recent_schema_updates.sql)
- 포함 내용:
  - `service_view_logs` 생성
  - `notifications`, `notification_services` 생성
  - `notifications.retry_count`, `notifications.next_retry_at` 추가
  - `idx_noti_retry` 인덱스 추가

- 파일: [`backend/src/main/resources/db/migration/V2026_04_18_02__add_raw_api_payloads.sql`](../backend/src/main/resources/db/migration/V2026_04_18_02__add_raw_api_payloads.sql)
- 포함 내용:
  - `raw_api_payloads` 생성
  - 공공 API 목록/상세 원문 payload 보관

## 적용 방법

```bash
mysql -h 127.0.0.1 -P 3307 -u root -p youth_welfare < backend/src/main/resources/db/migration/V2026_04_27_03__add_user_core_split_tables.sql
mysql -h 127.0.0.1 -P 3307 -u root -p youth_welfare < backend/src/main/resources/db/migration/V2026_04_27_02__add_user_key_columns.sql
mysql -h 127.0.0.1 -P 3307 -u root -p youth_welfare < backend/src/main/resources/db/migration/V2026_04_27_01__add_service_region_compound_indexes.sql
mysql -h 127.0.0.1 -P 3307 -u root -p youth_welfare < backend/src/main/resources/db/migration/V2026_04_25_01__add_chat_tables.sql
mysql -h 127.0.0.1 -P 3307 -u root -p youth_welfare < backend/src/main/resources/db/migration/V2026_04_17_01__recent_schema_updates.sql
mysql -h 127.0.0.1 -P 3307 -u root -p youth_welfare < backend/src/main/resources/db/migration/V2026_04_18_02__add_raw_api_payloads.sql
mysql -h 127.0.0.1 -P 3307 -u root -p youth_welfare < backend/src/main/resources/db/migration/V2026_04_23_01__add_api_sync_logs.sql
mysql -h 127.0.0.1 -P 3307 -u root -p youth_welfare < backend/src/main/resources/db/migration/V2026_04_24_01__add_search_youth_relevance.sql
```

도커 컨테이너를 쓰는 경우:

```bash
docker exec -i youth-welfare-db mysql -uroot -p"$DB_PASSWORD" youth_welfare < backend/src/main/resources/db/migration/V2026_04_27_03__add_user_core_split_tables.sql
docker exec -i youth-welfare-db mysql -uroot -p"$DB_PASSWORD" youth_welfare < backend/src/main/resources/db/migration/V2026_04_27_02__add_user_key_columns.sql
docker exec -i youth-welfare-db mysql -uroot -p"$DB_PASSWORD" youth_welfare < backend/src/main/resources/db/migration/V2026_04_27_01__add_service_region_compound_indexes.sql
docker exec -i youth-welfare-db mysql -uroot -p"$DB_PASSWORD" youth_welfare < backend/src/main/resources/db/migration/V2026_04_25_01__add_chat_tables.sql
docker exec -i youth-welfare-db mysql -uroot -p"$DB_PASSWORD" youth_welfare < backend/src/main/resources/db/migration/V2026_04_17_01__recent_schema_updates.sql
docker exec -i youth-welfare-db mysql -uroot -p"$DB_PASSWORD" youth_welfare < backend/src/main/resources/db/migration/V2026_04_18_02__add_raw_api_payloads.sql
docker exec -i youth-welfare-db mysql -uroot -p"$DB_PASSWORD" youth_welfare < backend/src/main/resources/db/migration/V2026_04_23_01__add_api_sync_logs.sql
docker exec -i youth-welfare-db mysql -uroot -p"$DB_PASSWORD" youth_welfare < backend/src/main/resources/db/migration/V2026_04_24_01__add_search_youth_relevance.sql
```

인덱스를 추가한 뒤에는 통계를 한 번 갱신한다.

```sql
ANALYZE TABLE service_regions;
```

마이그레이션 후 검색용 청년 플래그를 실제 규칙으로 다시 계산한다.

```bash
curl -X POST http://127.0.0.1:8082/api/admin/policies/search-youth-relevance/rebuild
```

주의:

- 컬럼 기본값은 `1`이라 마이그레이션 직후 기존 데이터는 모두 검색 후보로 남아 있다.
- 백엔드 최신 코드 배포 후 위 백필 호출까지 끝나야 실제 청년 필터 기준 검색 성능과 결과가 맞는다.
- 응답 본문에는 `processedCount`, `updatedCount`, `relevantCount`, `excludedCount`가 포함된다.

## 앱 레벨 PII backfill 실행

최신 백엔드 배포 후 `user_pii.email_enc/name_enc/birth_date_enc` 는 관리자 API로 채운다.

```bash
curl -X POST http://127.0.0.1:8082/api/admin/users/pii-backfill \
  -H "Authorization: Bearer <ADMIN_ACCESS_TOKEN>"
```

응답 본문:

- `processedCount`: 누락 암호문이 있어 검사한 `user_pii` row 수
- `updatedUserCount`: 실제로 하나 이상 암호문을 채운 사용자 수
- `emailBackfilledCount`, `nameBackfilledCount`, `birthDateBackfilledCount`: 필드별 채운 건수
- `skippedCount`: 원본 `users.email/name/birth_date` 가 비어 있어 채우지 못한 row 수

## 앱 레벨 metadata `user_key` backfill 실행

최신 백엔드 배포 후 `user_attributes`, `user_priorities` 의 `user_key` 누락 row는 관리자 API로 채운다.

```bash
curl -X POST http://127.0.0.1:8082/api/admin/users/metadata-user-key-backfill \
  -H "Authorization: Bearer <ADMIN_ACCESS_TOKEN>"
```

응답 본문:

- `processedCount`: `user_key` 누락 상태로 검사한 `user_attributes + user_priorities` row 수
- `updatedRowCount`: 실제로 `user_key` 를 채운 전체 row 수
- `attributeUpdatedCount`: `user_attributes.user_key` 채운 row 수
- `priorityUpdatedCount`: `user_priorities.user_key` 채운 row 수

## 확인 쿼리

```sql
SHOW COLUMNS FROM notifications;
SHOW INDEX FROM notifications;
SHOW TABLES LIKE 'service_view_logs';
SHOW TABLES LIKE 'notification_services';
SHOW TABLES LIKE 'raw_api_payloads';
SHOW TABLES LIKE 'api_sync_logs';
SHOW TABLES LIKE 'chat_sessions';
SHOW TABLES LIKE 'chat_messages';
SHOW CREATE TABLE chat_sessions;
SHOW CREATE TABLE chat_messages;
SHOW TABLES LIKE 'auth_users';
SHOW TABLES LIKE 'user_profiles';
SHOW TABLES FROM youth_welfare_pii LIKE 'user_pii';
SHOW COLUMNS FROM auth_users;
SHOW COLUMNS FROM user_profiles;
SHOW COLUMNS FROM youth_welfare_pii.user_pii;
SELECT COUNT(*) AS auth_user_count FROM auth_users;
SELECT COUNT(*) AS user_profile_count FROM user_profiles;
SELECT COUNT(*) AS user_pii_count FROM youth_welfare_pii.user_pii;
SELECT COUNT(*) AS user_pii_missing_enc
FROM youth_welfare_pii.user_pii
WHERE email_enc IS NULL OR email_enc = ''
   OR name_enc IS NULL OR name_enc = ''
   OR birth_date_enc IS NULL OR birth_date_enc = '';
SELECT COUNT(*) AS user_pii_missing_enc_but_source_null
FROM users u
JOIN youth_welfare_pii.user_pii up ON up.user_key = u.user_key
WHERE (up.email_enc IS NULL OR up.email_enc = ''
    OR up.name_enc IS NULL OR up.name_enc = ''
    OR up.birth_date_enc IS NULL OR up.birth_date_enc = '')
  AND (u.name IS NULL OR u.birth_date IS NULL);
SELECT COUNT(*) AS auth_users_without_hash FROM auth_users WHERE email_lookup_hash IS NULL OR email_lookup_hash = '';
SELECT COUNT(*) AS user_profiles_without_user_key FROM user_profiles WHERE user_key IS NULL;
SELECT COUNT(*) AS user_pii_without_user_key FROM youth_welfare_pii.user_pii WHERE user_key IS NULL;
SHOW COLUMNS FROM users LIKE 'user_key';
SHOW INDEX FROM users WHERE Key_name = 'uq_users_user_key';
SHOW COLUMNS FROM user_attributes LIKE 'user_key';
SHOW COLUMNS FROM user_priorities LIKE 'user_key';
SHOW COLUMNS FROM user_recommendations LIKE 'user_key';
SHOW COLUMNS FROM recommendation_logs LIKE 'user_key';
SHOW COLUMNS FROM notifications LIKE 'user_key';
SHOW COLUMNS FROM chat_sessions LIKE 'user_key';
SHOW COLUMNS FROM service_view_logs LIKE 'user_key';
SELECT COUNT(*) AS users_without_user_key FROM users WHERE user_key IS NULL;
SELECT COUNT(*) AS attrs_without_user_key FROM user_attributes WHERE user_key IS NULL;
SELECT COUNT(*) AS priorities_without_user_key FROM user_priorities WHERE user_key IS NULL;
SELECT COUNT(*) AS recommendations_without_user_key FROM user_recommendations WHERE user_key IS NULL;
SELECT COUNT(*) AS logs_without_user_key FROM recommendation_logs WHERE user_key IS NULL;
SELECT COUNT(*) AS notifications_without_user_key FROM notifications WHERE user_key IS NULL;
SELECT COUNT(*) AS chat_sessions_without_user_key FROM chat_sessions WHERE user_key IS NULL;
SELECT COUNT(*) AS identified_view_logs_without_user_key
FROM service_view_logs
WHERE user_id IS NOT NULL
  AND user_key IS NULL;
SHOW INDEX FROM service_regions WHERE Key_name IN ('idx_sr_service_sido_sgg', 'idx_sr_service_region_code');
SHOW COLUMNS FROM welfare_services LIKE 'search_youth_relevant';
SHOW INDEX FROM welfare_services WHERE Key_name = 'idx_ws_search_youth';
SHOW INDEX FROM chat_sessions WHERE Key_name = 'idx_cs_user_last_message';
SHOW INDEX FROM chat_messages WHERE Key_name = 'idx_cm_session_created';
SELECT search_youth_relevant, COUNT(*) FROM welfare_services GROUP BY search_youth_relevant;
```

## 주의

- `schema.sql`은 신규 DB 초기화용이다. 기존 DB 갱신에는 자동 적용되지 않는다.
- 배포 전에 마이그레이션 SQL을 먼저 적용하고, 그 다음 백엔드를 올리는 순서로 진행한다.
- `users.user_key`는 migration 직후부터 새 가입에도 자동 채워지도록 `DEFAULT (REPLACE(UUID(), '-', ''))`를 사용한다.
- 기존 사용자 backfill은 `UUID()` 대량 UPDATE 대신 `SHA2(CONCAT('user:', id), 256)` 앞 32자 사용으로 고정했다. 단일 인스턴스뿐 아니라 binlog safety 경고가 있는 환경에서도 적용 가능하게 하기 위해서다.
- `V2026_04_27_02__add_user_key_columns.sql`은 세션 시작 시 `SET SESSION sql_log_bin = 0`을 실행한다. 현재 운영 절차처럼 root 또는 migration 전용 계정으로 수동 적용하는 것을 전제로 한다.
- `V2026_04_27_03__add_user_core_split_tables.sql`도 세션 시작 시 `SET SESSION sql_log_bin = 0`을 실행한다.
- `user_pii` 의 `email_enc/name_enc/birth_date_enc` 는 migration SQL로 직접 채우지 않는다. 최신 백엔드의 `/api/admin/users/pii-backfill` 가 `AesEncryptUtil` 과 같은 경로로 채우는 것이 기준이다.
- `user_attributes`, `user_priorities` 의 `user_key` 도 migration backfill만으로 끝내지 않는다. migration 이후 JPA 저장 경로가 `user_key` 를 같이 쓰도록 최신 백엔드를 먼저 배포하고, 기존 누락 row는 `/api/admin/users/metadata-user-key-backfill` 로 마무리하는 것이 기준이다.
- `users.name` 또는 `users.birth_date` 가 이미 비어 있는 row는 앱 레벨 backfill 이후에도 남을 수 있다. 이 경우는 source 원문이 없는 상태라 `skippedCount` 로 기록하고 억지로 placeholder 값을 넣지 않는다.
- 하위 테이블의 `user_key`는 아직 nullable로 유지한다. 현재는 `user_attributes`, `user_priorities` 까지 write sync/backfill 을 반영했고, 나머지 테이블은 identity cut-over 전까지 기존 `user_id` FK와 함께 유지한다.
