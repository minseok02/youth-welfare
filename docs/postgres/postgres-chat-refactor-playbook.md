# PostgreSQL 전환 + 검색/챗봇 고도화 플레이북

문서군 진입점: [system-docs-index.md](../core/system-docs-index.md)

이 문서는 현재 MySQL 기반 프로젝트를 보존한 채, `PostgreSQL 전환 -> 검색 계층 재구성 -> 챗봇 고도화 -> 추천/챗봇 공통 탐색 엔진 정리` 순서로 작업하기 위한 실행 문서다.

이 문서는 아래 두 문제를 같이 다룬다.

- 리팩토링을 어디부터 어떤 순서로 할지
- 현재 안정판을 잃지 않고 Git을 어떻게 운영할지

기준 문서:

- [github-workflow.md](../github-workflow.md)
- [chatbot-plan.md](../core/chatbot-plan.md)
- [db-migration.md](../core/db-migration.md)
- [mvp-grow-when-needed-playbook.md](../core/mvp-grow-when-needed-playbook.md)
- [recommendation-pipeline.md](../recommendation/recommendation-pipeline.md)
- Phase spec index: [postgres-refactor-spec-index.md](./postgres-refactor-spec-index.md)

## 1. 현재 상태 요약

현재 구조는 아래와 같다.

- DB: MySQL 8
- 검색: MySQL FULLTEXT (`MATCH ... AGAINST`)
- 추천: 사용자 조건 기반 retrieval + rule scoring + AI 보조 점수 + reranking
- 챗봇: 질문 FULLTEXT 후보 검색 + OpenAI 답변 생성
- 인프라: `app + db + redis` 단일 Docker Compose

현재 챗봇은 구조적으로 아래에 가깝다.

```text
질문
 -> FULLTEXT 후보 3~5개
 -> OpenAI 답변
```

즉 현재 챗봇은 RAG도 아니고, 벡터 검색도 아니며, 정책 탐색을 자연어로 감싼 얇은 계층이다.

반면 추천은 이미 아래 구조를 갖고 있다.

```text
사용자 프로필
 -> 후보 retrieval (K=50 + 최신 정책 보강)
 -> rule scoring
 -> AI scoring
 -> reranking
 -> 저장/로그
```

따라서 챗봇을 현재 MySQL 구조 안에서 "조금만" 고도화하면, 추천과 점점 더 닮아질 가능성이 크다.

## 2. 이번 리팩토링의 목표

이번 리팩토링의 목표는 아래 네 가지다.

1. 현재 MySQL 안정판을 보존한다.
2. PostgreSQL 기반으로 검색/벡터/고도화가 가능한 기반을 만든다.
3. 추천은 `빠른 선제안`, 챗봇은 `깊은 탐색/비교/설명` 역할로 분리한다.
4. 장기적으로 추천과 챗봇이 공통 탐색 엔진을 공유할 수 있게 구조를 정리한다.

## 3. 이번에 하지 않을 것

이번 리팩토링 1차 범위에서 아래는 바로 하지 않는다.

- 추천 엔진 전체 폐기 후 챗봇으로 완전 대체
- 프론트 전체 리디자인
- 운영용 클라우드 완전 분리
- 다중 DB/다중 worker/대규모 분산 아키텍처
- 바로 별도 외부 vector DB 도입

핵심은 "기반을 PostgreSQL 쪽으로 옮기고, 챗봇을 진짜 탐색 인터페이스로 키울 수 있는 구조를 만든다"이다.

## 4. 핵심 판단

### 4.1 왜 MySQL 유지 고도화가 아닌가

MySQL 안에서도 챗봇은 고도화할 수 있다. 다만 장기적으로는 아래 문제가 생긴다.

- FULLTEXT는 MySQL
- 벡터 검색은 별도 시스템
- grounded answer/RAG는 또 별도 구조

즉 기능을 붙일수록 "밖에 다른 것"을 더 붙여야 할 가능성이 높다.

### 4.2 왜 PostgreSQL인가

PostgreSQL은 한 생태계 안에서 아래를 묶기 쉽다.

- 일반 관계형 DB
- 전문 검색 (`tsvector`, `to_tsquery`, `pg_trgm`)
- 벡터 검색 (`pgvector`)

따라서 운영 기준으로는 MySQL보다 확장 방향이 더 단순하다.

### 4.3 챗봇의 역할은 무엇이 되어야 하나

챗봇은 단순히 "정책 몇 개 찾아서 답변"하는 기능으로 가면 추천과 차별성이 약하다.

챗봇은 아래 역할을 가져야 한다.

- broad 질문을 의도별로 나눔
- 서로 다른 정책 유형을 비교함
- 근거를 제시함
- 후속 질문을 받아 탐색을 이어감

예:

- `집 관련 정책 보여줘`
- `장기 주거 안정이 중요한가요, 즉시 현금성 지원이 중요한가요?`
- 사용자가 선택하면 해당 축에서 정책을 제안

이 방향이 추천과 챗봇 역할 분리를 가장 잘 만든다.

## 5. Git 운영 전략

## 5.1 원칙

- 현재 MySQL 기반 안정판은 보존한다.
- PostgreSQL 전환과 챗봇 고도화는 `main`에서 직접 하지 않는다.
- 큰 전환 작업은 하나의 장기 통합 브랜치 아래에서 단계별 브랜치로 쪼개 진행한다.

## 5.2 보존할 안정판

작업 시작 전 현재 상태를 반드시 고정한다.

권장:

- 안정 브랜치: `release/mysql-stable`
- 태그: `mysql-stable-v1`

예:

```bash
git checkout main
git pull
git checkout -b release/mysql-stable
git push -u origin release/mysql-stable

git checkout main
git tag mysql-stable-v1
git push origin mysql-stable-v1
```

의미:

- `release/mysql-stable`: 발표/복구/비교용 브랜치
- `mysql-stable-v1`: PostgreSQL 전환 전 기준점

## 5.3 장기 통합 브랜치

큰 전환은 아래 브랜치에서 진행한다.

- 통합 브랜치: `refactor/common-postgres-chat-platform`

이 브랜치는 바로 `main`에 보내지 않는다.
로컬 통합, 내부 검증, 중간 병합 기준점으로 사용한다.

## 5.4 단계별 작업 브랜치

실제 작업은 아래처럼 쪼갠다.

- `refactor/backend-postgres-base`
- `refactor/backend-postgres-search`
- `feat/backend-chat-retrieval-foundation`
- `feat/backend-pgvector-foundation`
- `refactor/backend-shared-policy-engine`
- `docs/docs-postgres-chat-refactor-playbook`

역할:

- `postgres-base`: datasource, docker, schema, migration, integration 환경
- `postgres-search`: MySQL FULLTEXT -> PostgreSQL FTS/`pg_trgm`
- `chat-retrieval-foundation`: chunk, intent branch, grounded answer 기반
- `pgvector-foundation`: embedding, vector column, similarity retrieval
- `shared-policy-engine`: 추천/챗봇 공통 retrieval/rerank 방향 정리

## 5.5 merge 순서

아래 순서를 권장한다.

1. `refactor/backend-postgres-base`
2. `refactor/backend-postgres-search`
3. `feat/backend-chat-retrieval-foundation`
4. `feat/backend-pgvector-foundation`
5. `refactor/backend-shared-policy-engine`

모든 브랜치는 `refactor/common-postgres-chat-platform`에 순차적으로 병합한다.

`main` 병합은 최소 아래가 만족된 뒤에만 고려한다.

- PostgreSQL 로컬 부팅 성공
- 검색 API 통과
- 추천 API 동작
- 챗봇 기본 흐름 통과
- integration/smoke 기준 재정비 완료

## 5.6 커밋 원칙

기존 [github-workflow.md](../github-workflow.md)를 유지한다.

- `main` 직접 커밋 금지
- 커밋 형식: `<type>(<area>): <summary>`
- 작은 task 단위 커밋
- 브랜치 단위는 하나의 문제 축만 다룸

추가 원칙:

- `git add .` 사용 금지
- DB 전환과 챗봇 기능 추가는 같은 커밋에 섞지 않음
- 검색 재작성과 vector/RAG 작업은 커밋/브랜치까지 분리

## 6. 리팩토링 단계

## 6.1 Phase 0 - 안정판 고정

세부 실행 문서: [postgres-phase-0-freeze-spec.md](./postgres-phase-0-freeze-spec.md)

목표:

- 현재 MySQL 안정판을 복구 가능한 상태로 고정

작업:

- 현재 상태 커밋 정리
- `release/mysql-stable` 브랜치 생성
- `mysql-stable-v1` 태그 생성
- 현재 smoke/test 기준 기록

완료 조건:

- MySQL 안정판으로 언제든 돌아갈 수 있음

예상 시간:

- Codex 기준 `0.5일`

## 6.2 Phase 1 - PostgreSQL 기반 전환

세부 실행 문서: [postgres-phase-1-base-spec.md](./postgres-phase-1-base-spec.md)

목표:

- MySQL 의존 인프라를 PostgreSQL 기준으로 바꾸고 앱이 다시 부팅되게 만듦

주요 수정 대상:

- `backend/build.gradle`
- `backend/src/main/resources/application.yml`
- `backend/src/test/resources/application-integration.yml`
- `docker-compose.yml`
- `.env.example`
- `backend/src/main/resources/db/schema.sql`
- `backend/src/main/resources/db/migration/**`
- `deploy/mysql/**` 대체 또는 제거

방식:

- MySQL driver 제거, PostgreSQL driver 추가
- JDBC URL을 `jdbc:postgresql://...` 로 변경
- split datasource도 PostgreSQL 기준으로 재구성
- MySQL 전용 schema/migration 문법 제거
- 로컬/테스트 compose를 PostgreSQL로 재작성

주의:

- 이 단계에서는 검색이 아직 깨져도 된다.
- 목표는 앱 부팅과 기본 CRUD 회복이다.

완료 조건:

- PostgreSQL 기준 `app + db + redis` 로컬 부팅 성공
- 기본 인증/회원/정책 목록 API 동작

예상 시간:

- Codex 기준 `3~5일`

## 6.3 Phase 2 - 검색 계층 재작성

세부 실행 문서: [postgres-phase-2-search-spec.md](./postgres-phase-2-search-spec.md)

목표:

- MySQL FULLTEXT 의존을 제거하고 PostgreSQL 검색 계층으로 교체

주요 수정 대상:

- `backend/src/main/java/com/example/welfare/policy/repository/WelfareServiceRepository.java`
- `backend/src/main/java/com/example/welfare/policy/service/PolicySearchService.java`
- `backend/src/main/java/com/example/welfare/chat/service/ChatPolicyService.java`
- `backend/src/main/java/com/example/welfare/chat/repository/ChatPolicyReadRepositoryImpl.java`
- 검색 관련 integration/smoke

방식:

- `MATCH ... AGAINST` 제거
- PostgreSQL FTS로 1차 대체
- 짧은 질의/오타 대응 필요 시 `pg_trgm` 보강
- 챗봇 질문용 broad query 정규화 로직 재설계

주의:

- 검색 품질은 MySQL과 100% 같을 필요는 없다.
- 먼저 "동작하고, 의미상 납득 가능한 수준"으로 맞춘다.

완료 조건:

- 정책 검색 API 재동작
- 챗봇 후보 검색 재동작
- 지역/카테고리/정렬 조건 유지

예상 시간:

- Codex 기준 `2~4일`

## 6.4 Phase 3 - 챗봇 retrieval foundation

세부 실행 문서: [postgres-phase-3-chat-foundation-spec.md](./postgres-phase-3-chat-foundation-spec.md)

목표:

- 챗봇을 단순 정책 후보 5개 검색기가 아니라, 의도 분기와 grounded answer가 가능한 구조로 바꿈

핵심 아이디어:

- broad 질문은 먼저 branch를 제안
- branch 선택 후 해당 축 안에서 정책 retrieval
- 답변은 정책 근거를 바탕으로 생성

예:

- `집 관련 정책 보여줘`
- `장기 주거 안정 / 즉시 현금성 지원 / 청약·입주 정보`

주요 수정/추가 대상:

- `chat/` 서비스 계층 전반
- `policy_chunks` 테이블 신설
- 정책 chunk 생성기
- broad query intent branch 서비스
- grounded answer prompt

권장 테이블:

- `policy_chunks`
  - `id`
  - `service_id`
  - `chunk_type`
  - `chunk_text`
  - `metadata_json`
  - `created_at`
  - `updated_at`

이 단계에서는 벡터 없이도 가능하다.

즉:

- chunk는 만들되
- retrieval은 PostgreSQL FTS / metadata filter 기준으로 먼저 구현

완료 조건:

- broad query에서 정책 나열 대신 의도 분기 가능
- 근거 기반 답변 가능
- 정책 카드와 답변 이유 연결 가능

예상 시간:

- Codex 기준 `3~5일`

## 6.5 Phase 4 - pgvector / embedding foundation

세부 실행 문서: [postgres-phase-4-pgvector-spec.md](./postgres-phase-4-pgvector-spec.md)

목표:

- PostgreSQL 안에서 벡터 검색이 가능하도록 기반 추가

주요 추가 대상:

- `pgvector` extension
- `policy_chunks.embedding`
- embedding 생성/갱신 배치
- 질문 임베딩 생성
- vector similarity retrieval

필요 개념:

- 임베딩: 질문/정책 chunk를 숫자 벡터로 변환
- vector retrieval: 의미적으로 가까운 chunk 검색

권장 방식:

- embedding model: `text-embedding-3-small` 부터 시작
- chunk 단위 임베딩 저장
- 정책 변경 시 재생성 가능한 파이프라인 추가

완료 조건:

- 질문 임베딩 생성 가능
- PostgreSQL에서 유사 chunk 검색 가능
- FTS + vector 결과를 함께 사용할 수 있음

예상 시간:

- Codex 기준 `3~4일`

## 6.6 Phase 5 - 추천/챗봇 공통 탐색 엔진 정리

세부 실행 문서: [postgres-phase-5-shared-engine-spec.md](./postgres-phase-5-shared-engine-spec.md)

목표:

- 추천과 챗봇이 같은 retrieval/ranking 계층을 공유하도록 구조 정리

핵심 방향:

- 추천: 빠른 선제안
- 챗봇: 비교/설명/후속 탐색
- 내부 retrieval/filter/rerank 신호는 최대한 공통화

주요 대상:

- `recommend/` retrieval 계층
- `chat/` retrieval 계층
- 공통 policy exploration service
- 공통 metadata filter / semantic retrieval / rerank 유틸

완료 조건:

- 추천과 챗봇이 별개 엔진처럼 중복 구현되지 않음
- 공통 탐색 엔진 위에 UI만 다르게 올릴 수 있는 구조 확보

예상 시간:

- Codex 기준 `4~6일`

## 7. 총 예상 시간

Codex에게 전적으로 맡기고, 중간 의사결정/검토만 사람이 한다는 기준:

- `Phase 0 ~ 2` (`PostgreSQL 전환 + 검색 재작성`): `5~9일`
- `Phase 3` 포함 (`챗봇 retrieval foundation`): `8~14일`
- `Phase 4` 포함 (`pgvector 기반 벡터 검색`): `11~18일`
- `Phase 5` 포함 (`공통 엔진 정리`): `15~24일`

현실적으로는 아래 두 버전이 있다.

### 빠른 버전

- 목표: PostgreSQL + 검색 + broad query 분기 챗봇
- 예상: `약 2주`

### 완성형 1차 버전

- 목표: PostgreSQL + pgvector + grounded chatbot + 공통 탐색 엔진 기초
- 예상: `약 3~4주`

## 8. 현재 구현 상태 메모

아래 내용은 `2026-05-13` 로컬 Codex 테스트 환경 기준 현황이다.

### 구현 완료 범위

- `Phase 1 ~ 5` 핵심 구현 완료
- PostgreSQL 전환 완료
- MySQL FULLTEXT 제거, PostgreSQL FTS + `pg_trgm` 전환 완료
- `policy_chunks` + `pgvector` 기반 semantic retrieval 완료
- 추천/챗봇 공통 탐색 엔진 정리 완료
- 임베딩 refresh 운영 경로 연결 완료
- retrieval snapshot / evaluation / compare / CSV export 완료
- retrieval quality gate / category audit admin 경로 추가 완료

### 이번 변경이 큰 이유

이번 diff 가 큰 이유는 "DB 하나 바꾼 것"이 아니라 아래 축이 한 번에 같이 움직였기 때문이다.

- 런타임 DB를 `MySQL -> PostgreSQL` 로 교체
- 검색 구현을 `MATCH ... AGAINST` 에서 PostgreSQL FTS / `pg_trgm` 으로 교체
- 챗봇을 단순 후보 검색기에서 branch suggestion / grounded answer / semantic retrieval 구조로 확장
- 추천과 챗봇이 같은 탐색 엔진을 공유하도록 retrieval 경계를 재정리
- 임베딩 refresh, retrieval evaluation, quality gate, category audit 같은 운영 검증 경로를 추가

즉 파일 수가 많은 이유는 "기능이 여기저기 흩어져서 조금씩 수정된 것"이 아니라, 저장 구조 / 검색 / 챗봇 / 추천 / 운영 검증이 같은 전환 축에 묶여 있기 때문이다.

### 이번 변경 읽는 법

큰 파일군을 아래처럼 보면 된다.

1. PostgreSQL 기반 전환
- `docker-compose.yml`
- `.env.example`
- `backend/build.gradle`
- `backend/src/main/resources/application.yml`
- `backend/src/test/resources/application-integration.yml`
- `backend/src/main/resources/db/schema.sql`
- `deploy/postgres/**`

의미:
- 앱이 PostgreSQL 기준으로 뜨고, 테스트/로컬 compose 도 같은 기준으로 맞춰진다.

2. 검색 계층 전환
- `backend/src/main/java/com/example/welfare/policy/repository/WelfareServiceSearchRepository*`
- `backend/src/main/java/com/example/welfare/policy/service/PolicySearchService.java`
- `backend/src/main/java/com/example/welfare/global/util/SearchKeywordSupport.java`
- `backend/src/test/java/com/example/welfare/integration/PolicySearchRegionQueryIntegrationTest.java`

의미:
- MySQL FULLTEXT 제거
- PostgreSQL FTS / `pg_trgm` 기반 정책 검색

3. 챗봇 retrieval foundation + semantic
- `backend/src/main/java/com/example/welfare/chat/**`
- `backend/src/main/java/com/example/welfare/policy/entity/PolicyChunk.java`
- `backend/src/main/java/com/example/welfare/policy/repository/PolicyChunkRepository.java`
- `backend/src/test/java/com/example/welfare/integration/ChatPolicySearchIntegrationTest.java`
- `backend/src/test/java/com/example/welfare/integration/ChatSemanticVectorIntegrationTest.java`

의미:
- branch suggestion
- grounded answer
- `policy_chunks`
- embedding / semantic retrieval
- retrieval snapshot

4. 추천/챗봇 공통 탐색 엔진
- `backend/src/main/java/com/example/welfare/policy/service/PolicyExplorationService.java`
- `backend/src/main/java/com/example/welfare/recommend/repository/RecommendationCandidateReadRepositoryImpl.java`
- `backend/src/main/java/com/example/welfare/recommend/service/ReRankingService.java`
- `backend/src/main/java/com/example/welfare/recommend/service/RecommendationDiversityService.java`

의미:
- 추천과 챗봇이 같은 retrieval truth 를 공유
- diversity 보강 포함

5. 운영 검증 / 평가 / admin
- `backend/src/main/java/com/example/welfare/policy/controller/PolicyAdminController.java`
- `backend/src/main/java/com/example/welfare/policy/service/PolicyRetrievalEvaluationService.java`
- `backend/src/main/java/com/example/welfare/policy/service/PolicyRetrievalEvaluationExportService.java`
- `backend/src/main/java/com/example/welfare/policy/service/PolicyRetrievalQualityGateService.java`
- `backend/src/main/java/com/example/welfare/policy/service/PolicyCategoryAuditService.java`

의미:
- baseline evaluation
- tuning compare
- CSV export
- quality gate
- category audit

6. 수집 / 분류 보정 / 상세 계약 보강
- `backend/src/main/java/com/example/welfare/collect/**`
- `backend/src/main/java/com/example/welfare/policy/dto/PolicyDetailResponse.java`
- `backend/src/main/java/com/example/welfare/policy/dto/PolicySummaryResponse.java`
- `backend/src/main/java/com/example/welfare/policy/entity/WelfareServiceDetail.java`
- `frontend/src/pages/PoliciesPage.jsx`
- `frontend/src/pages/LoginPage.jsx`

의미:
- `복지문화` 계열 재분류
- detail/embedding refresh 연결
- 정책 상세/목록 응답 계약 보강
- 프론트 lint blocker 제거

### 코드 리뷰 때 특히 볼 포인트

- schema 변경이 entity / repository / DTO 까지 일관되게 연결됐는지
- 검색 쿼리가 PostgreSQL 전용 문법으로 안정화됐는지
- 챗봇 응답이 `branch -> retrieval -> grounded answer` 흐름으로 분기되는지
- semantic 검색이 fallback / blend 규칙 안에서만 섞이는지
- admin evaluation / gate / export 경로가 로컬 운영 검증에 실제로 쓰일 수 있는지

### 현재 로컬 데이터 적재 규모

- `welfare_services`: `3925`
- `search_youth_relevant = true`: `2544`
- `welfare_service_details`: `1356`
- `policy_chunks`: `14210`
- embedding 저장 완료 chunk: `14210`

### 현재 retrieval baseline

- dataset key: `retrieval-baseline-v2`
- `top1HitRate`: `1.0`
- `top3HitRate`: `1.0`
- `branchSuggestionHitRate`: `1.0`
- `emptyResultCount`: `0`
- quality gate: `passed=true`

### 현재 admin 운영/검증 경로

- `POST /api/admin/policies/search-youth-relevance/rebuild`
- `POST /api/admin/policies/embeddings/rebuild`
- `POST /api/admin/policies/retrieval-evaluations/run`
- `GET /api/admin/policies/retrieval-evaluations/export`
- `POST /api/admin/policies/retrieval-evaluations/compare`
- `POST /api/admin/policies/retrieval-evaluations/compare/export`
- `POST /api/admin/policies/retrieval-evaluations/gate`
- `GET /api/admin/policies/category-audit`

### 재적재 / 재검증 권장 절차

1. `docker compose down -v`
2. `docker compose up -d db redis`
3. 앱 기동 후 source collect 실행
4. 필요 시 `POST /api/admin/policies/embeddings/rebuild` 로 embedding backfill 실행
5. `POST /api/admin/policies/retrieval-evaluations/run` 으로 baseline 재평가
6. `POST /api/admin/policies/retrieval-evaluations/gate` 로 회귀 여부 확인
7. `GET /api/admin/policies/category-audit` 로 분류 분포 점검

### 현재 남은 실무 작업

- retrieval regression integration test 강화
- category audit 결과를 더 보기 쉬운 read model/리포트로 정리
- tuning preset batch compare가 정말 필요한지 데이터 규모 기준으로 재판단
- Git 안정 브랜치/태그 운영 상태 최종 점검

## 9. 검증 기준

각 단계마다 아래를 분리해서 확인한다.

### 공통

- `git status --short`
- `git diff --check`

### 백엔드 기본

- `cd backend && ./gradlew test`

### DB/Redis 포함

- `docker compose up -d db redis`
- `cd backend && ./gradlew integrationTest`

### 검색

- 정책 목록/검색/상세 API
- 정렬/필터/지역 조건
- 챗봇 후보 검색

### 챗봇

- 세션 CRUD
- broad query 분기
- grounded answer
- 정책 카드/참조 연결

### 추천

- refresh 동작
- recommendation_logs 기록
- click tracking
- fallback 유지

### 운영 검증 추가

- `POST /api/admin/policies/retrieval-evaluations/gate`
- `GET /api/admin/policies/category-audit`
- baseline 지표와 category 분포를 같이 확인

## 10. rollback 전략

언제든 아래 기준으로 되돌릴 수 있어야 한다.

- Git 기준: `release/mysql-stable` 또는 `mysql-stable-v1`
- 실행 기준: MySQL compose / 기존 smoke 문서

즉 PostgreSQL 브랜치가 실패해도 현재 데모/발표판은 그대로 남아 있어야 한다.

## 11. 최종 권장 실행 순서

실행 순서는 아래가 가장 안전하다.

1. 현재 MySQL 안정판 고정
2. PostgreSQL 전환
3. 검색 계층 재작성
4. 챗봇 broad query + chunk foundation
5. `pgvector` 추가
6. 추천/챗봇 공통 탐색 엔진 정리

이 순서를 깨고 바로 `RAG + vector`부터 들어가면, 검색/DB 전환/챗봇 품질 문제가 한꺼번에 섞여 디버깅이 매우 어려워진다.

## 12. 바로 다음 작업

이 문서를 기준으로 실제 착수할 때 첫 작업은 아래 순서로 진행한다.

1. [postgres-phase-0-freeze-spec.md](./postgres-phase-0-freeze-spec.md) 확인
2. `release/mysql-stable` 브랜치 생성
3. `mysql-stable-v1` 태그 생성
4. `refactor/common-postgres-chat-platform` 브랜치 생성
5. [postgres-phase-1-base-spec.md](./postgres-phase-1-base-spec.md) 기준으로 `refactor/backend-postgres-base` 착수
6. PostgreSQL 기준 앱 부팅/기본 API 복구 후 [postgres-phase-2-search-spec.md](./postgres-phase-2-search-spec.md) 진행

현재 로컬 구현은 위 착수 단계를 넘어선 상태다. 다음 우선순위는 신규 기능 추가보다 `회귀 방지 자동화`와 `운영 문서/검증 절차 고정`이다.

## 13. 최종 판단

이번 작업의 핵심은 "DB를 바꾸는 것" 자체가 아니다.

진짜 핵심은:

- MySQL 의존 검색 구조를 정리하고
- 챗봇을 얇은 정책 검색기에서 탐색 인터페이스로 바꾸고
- 장기적으로 추천과 챗봇이 공통 탐색 엔진을 공유하게 만드는 것

이다.

따라서 이 작업은 단순 migration이 아니라
`플랫폼 탐색 구조 리팩토링`으로 봐야 한다.
