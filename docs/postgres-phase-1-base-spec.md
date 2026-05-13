# PostgreSQL Phase 1 Spec - 기반 전환

문서군 진입점: [postgres-refactor-spec-index.md](./postgres-refactor-spec-index.md)

## 목표

MySQL 의존 인프라를 PostgreSQL 기준으로 바꾸고, 앱이 PostgreSQL 환경에서 다시 부팅되게 만든다.

## 브랜치

- 권장 브랜치: `refactor/backend-postgres-base`

## 범위

- datasource 설정
- PostgreSQL 드라이버 적용
- Docker Compose DB 교체
- split datasource URL/driver 교체
- schema.sql 및 migration의 PostgreSQL 1차 변환
- integration 환경 전환

## 비범위

- 검색 품질 복구
- PostgreSQL FTS/`pg_trgm`
- `pgvector`
- 챗봇 고도화

## 주요 수정 대상

- `backend/build.gradle`
- `backend/src/main/resources/application.yml`
- `backend/src/test/resources/application-integration.yml`
- `docker-compose.yml`
- `.env.example`
- `backend/src/main/resources/db/schema.sql`
- `backend/src/main/resources/db/migration/**`
- `deploy/mysql/**` 대응 정리

## 설계 원칙

- 기능 확장보다 부팅 복구가 우선이다.
- 검색이 일부 깨져도 Phase 1에서는 허용한다.
- PII 분리 구조는 유지한다.
- MySQL 전용 문법 제거가 우선이며, 검색 엔진 대체는 Phase 2에서 한다.

## 완료 조건

- PostgreSQL 기준 `app + db + redis` 로컬 부팅 성공
- 인증/회원/정책 목록 같은 기본 API가 동작
- integration profile이 PostgreSQL 기준으로 뜬다

## 검증

- `docker compose up -d db redis`
- `cd backend && ./gradlew test`
- `cd backend && ./gradlew integrationTest`
- `/actuator/health`
- 정책 목록/로그인 기본 smoke

## 리스크

- schema/migration이 MySQL 전용 문법에 강하게 묶여 있어 예상보다 변환 범위가 커질 수 있다.
- PII split datasource guard가 MySQL prefix를 가정하고 있으므로 같이 수정해야 한다.

## 다음 문서

Phase 1 완료 후 [postgres-phase-2-search-spec.md](./postgres-phase-2-search-spec.md)로 진행한다.
