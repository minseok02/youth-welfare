# DB Migration Guide

현재 프로젝트는 신규 DB 초기화는 `backend/src/main/resources/db/schema.sql`로 처리하고, 기존 운영 DB 갱신은 수동 마이그레이션 SQL로 처리한다.

## 대상

- 이미 생성되어 데이터가 있는 MySQL
- `schema.sql`이 컨테이너 최초 기동 시점에만 적용된 환경

## 최신 마이그레이션

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
mysql -h 127.0.0.1 -P 3307 -u root -p youth_welfare < backend/src/main/resources/db/migration/V2026_04_17_01__recent_schema_updates.sql
mysql -h 127.0.0.1 -P 3307 -u root -p youth_welfare < backend/src/main/resources/db/migration/V2026_04_18_02__add_raw_api_payloads.sql
mysql -h 127.0.0.1 -P 3307 -u root -p youth_welfare < backend/src/main/resources/db/migration/V2026_04_23_01__add_api_sync_logs.sql
```

도커 컨테이너를 쓰는 경우:

```bash
docker exec -i youth-welfare-db mysql -uroot -p"$DB_PASSWORD" youth_welfare < backend/src/main/resources/db/migration/V2026_04_17_01__recent_schema_updates.sql
docker exec -i youth-welfare-db mysql -uroot -p"$DB_PASSWORD" youth_welfare < backend/src/main/resources/db/migration/V2026_04_18_02__add_raw_api_payloads.sql
docker exec -i youth-welfare-db mysql -uroot -p"$DB_PASSWORD" youth_welfare < backend/src/main/resources/db/migration/V2026_04_23_01__add_api_sync_logs.sql
```

## 확인 쿼리

```sql
SHOW COLUMNS FROM notifications;
SHOW INDEX FROM notifications;
SHOW TABLES LIKE 'service_view_logs';
SHOW TABLES LIKE 'notification_services';
SHOW TABLES LIKE 'raw_api_payloads';
SHOW TABLES LIKE 'api_sync_logs';
```

## 주의

- `schema.sql`은 신규 DB 초기화용이다. 기존 DB 갱신에는 자동 적용되지 않는다.
- 배포 전에 마이그레이션 SQL을 먼저 적용하고, 그 다음 백엔드를 올리는 순서로 진행한다.
