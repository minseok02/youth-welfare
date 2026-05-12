# PostgreSQL Phase 2 Spec - 검색 계층 재작성

문서군 진입점: [postgres-refactor-spec-index.md](./postgres-refactor-spec-index.md)

## 목표

MySQL `MATCH ... AGAINST` 기반 검색을 PostgreSQL 검색 계층으로 교체하고, 정책 검색과 챗봇 후보 검색을 복구한다.

## 브랜치

- 권장 브랜치: `refactor/backend-postgres-search`

## 범위

- 정책 검색 repository/query 교체
- 챗봇 후보 검색 query 교체
- PostgreSQL FTS 적용
- 필요 시 `pg_trgm` 도입
- 검색 integration/smoke 기준 갱신

## 비범위

- `pgvector`
- 정책 chunk / RAG
- 추천/챗봇 공통 엔진 정리

## 주요 수정 대상

- `backend/src/main/java/com/example/welfare/policy/repository/WelfareServiceRepository.java`
- `backend/src/main/java/com/example/welfare/policy/service/PolicySearchService.java`
- `backend/src/main/java/com/example/welfare/chat/service/ChatPolicyService.java`
- `backend/src/main/java/com/example/welfare/chat/repository/ChatPolicyReadRepositoryImpl.java`
- 검색 관련 integration/smoke

## 설계 원칙

- 1차 목표는 "동작 복구"다.
- MySQL 검색 결과와 100% 동일할 필요는 없다.
- broad query/짧은 질의 대응은 `pg_trgm`으로 보완 가능하다.

## 완료 조건

- 정책 검색 API 정상 동작
- 카테고리/지역/정렬 필터 유지
- 챗봇 후보 검색 정상 동작
- broad query에서 완전 0건 fallback만 반복되지 않음

## 검증

- 정책 검색 API 수동 검증
- 지역/카테고리/정렬 integration
- 챗봇 질문 후보 검색 smoke
- `./gradlew test`, `./gradlew integrationTest`

## 리스크

- PostgreSQL FTS 한글 품질이 기대보다 낮을 수 있다.
- 챗봇 질문은 짧고 broad한 경우가 많아서 일반 검색보다 더 민감할 수 있다.

## 다음 문서

Phase 2 완료 후 [postgres-phase-3-chat-foundation-spec.md](./postgres-phase-3-chat-foundation-spec.md)로 진행한다.
