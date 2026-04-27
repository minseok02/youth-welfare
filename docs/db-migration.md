# DB Migration Guide

현재 프로젝트는 신규 DB 초기화는 `backend/src/main/resources/db/schema.sql`로 처리하고, 기존 운영 DB 갱신은 수동 마이그레이션 SQL로 처리한다.

## 대상

- 이미 생성되어 데이터가 있는 MySQL
- `schema.sql`이 컨테이너 최초 기동 시점에만 적용된 환경

## 최신 마이그레이션

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
mysql -h 127.0.0.1 -P 3307 -u root -p youth_welfare < backend/src/main/resources/db/migration/V2026_04_27_01__add_service_region_compound_indexes.sql
mysql -h 127.0.0.1 -P 3307 -u root -p youth_welfare < backend/src/main/resources/db/migration/V2026_04_25_01__add_chat_tables.sql
mysql -h 127.0.0.1 -P 3307 -u root -p youth_welfare < backend/src/main/resources/db/migration/V2026_04_17_01__recent_schema_updates.sql
mysql -h 127.0.0.1 -P 3307 -u root -p youth_welfare < backend/src/main/resources/db/migration/V2026_04_18_02__add_raw_api_payloads.sql
mysql -h 127.0.0.1 -P 3307 -u root -p youth_welfare < backend/src/main/resources/db/migration/V2026_04_23_01__add_api_sync_logs.sql
mysql -h 127.0.0.1 -P 3307 -u root -p youth_welfare < backend/src/main/resources/db/migration/V2026_04_24_01__add_search_youth_relevance.sql
```

도커 컨테이너를 쓰는 경우:

```bash
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
