# 아키텍처

Java 17 / Spring Boot 단일 백엔드가 React 프론트, PostgreSQL + pgvector, Redis와 연결됩니다. 공공 API 수집, OpenAI, SMTP·웹푸시는 각 gateway/client 경계를 사용합니다. 외부 Python 서비스나 FastAPI 분리는 현재 구성에 포함하지 않습니다.

## 로컬과 서버 구성

| 대상 | 앱과 저장소 | 설정 |
| --- | --- | --- |
| 로컬 Docker 전체 앱 | `app + db + redis`, PostgreSQL 내부 `db:5432` | [docker-compose.yml](../docker-compose.yml) |
| 로컬 통합 테스트 | 로컬 PostgreSQL `5433`, Redis `6379` 기본값 | [testing.md](core/testing.md) |
| 서버 배포 | EC2 `app + redis`, 외부 RDS PostgreSQL, nginx | [docker-compose.prod.yml](../docker-compose.prod.yml), [deployment.md](deployment.md) |
| 로컬 앱과 원격 DB | 실행 위치와 별개로 설정된 원격 DB 사용 | [환경 구분](core/environment-guide.md) |

로컬 Compose도 `prod` 프로필을 사용하고 로컬/서버 앱 모두 호스트 `8082`에 노출될 수 있습니다. 주소와 프로필만으로 환경을 판별하지 않습니다. 위 표는 저장소의 구성 기준이며 현재 서버 가동 상태나 sizing 측정 결과가 아닙니다.

## 백엔드 모듈

| 모듈 | 역할 |
| --- | --- |
| `global` | 설정·보안·응답·예외·암호화 |
| `user` | 인증·프로필·동의·우선순위·사용자 상태 |
| `policy` | 정책 조회·검색·상세·랭킹·공통 탐색 |
| `collect` | source별 list/detail/support 수집·저장·실행 기록 |
| `recommend` | 후보·규칙/AI 점수·정렬·추천 저장·북마크 |
| `chat` | 상담·질의 해석·검색·근거 답변·대화 이력 |
| `notification` | 인앱 알림·이메일·웹푸시·발송 이력·재시도 |

## 요청 경계

- 인증: `AuthController -> AuthAvailabilityService / AuthSignupService / AuthLoginService / AuthSessionService`. 인증 lookup과 PII 조회 경계를 분리합니다.
- 정책: `PolicyController -> PolicyListService / PolicyDetailService / PolicySearchService / PolicyRankingService / PolicyBookmarkCommandService`.
- 추천: `RecommendationController -> RecommendationGenerationService / RecommendationAccessService / RecommendationBookmarkCommandService`. 계산은 `RetrievalService -> RuleScoringService / AiScoringService -> ReRankingService -> RecommendationPersistenceService`로 이어집니다.
- 수집: admin/scheduler에서 source 실행 경계를 거쳐 list/detail/support client와 저장 경로를 사용합니다. [detail 실행 계약](collect/collect-detail-execution-contract.md)을 따릅니다.
- 알림: 예약·발송·재시도와 사용자 알림함·구독을 구분합니다. 현재 인앱/웹푸시 구현이 있으므로 과거 이메일 단일 채널 계획을 현재 상태로 읽지 않습니다.
- 관리자: 대시보드·수집·품질 queue·유지보수 기능은 admin 인가와 목적별 DB 권한을 사용합니다.

## 공통 탐색과 벡터 검색

[PolicyExplorationService](../backend/src/main/java/com/example/welfare/policy/service/PolicyExplorationService.java)는 추천과 챗봇 후보 조회가 함께 사용하는 경계입니다.

- 추천: `RecommendationCandidateReadRepositoryImpl`을 통해 지역·나이·소득 등의 후보 조회를 호출한 뒤 추천 전용 후처리와 점수 계산을 수행합니다.
- 챗봇: `ChatPolicyReadRepositoryImpl`을 통해 키워드·지역·semantic 검색을 조합합니다.
- `ChatSemanticSearchService`와 `PolicyChunkVectorRepository`는 질의 embedding과 정책 chunk를 연결합니다. 저장/질의 벡터의 모델·공간 일치와 장애 fallback은 [현재 확인 과제](current-state.md)의 검증 대상입니다.

공통 service가 있다는 것과 두 경로의 의미·품질이 동일하다는 것은 다릅니다. 과거 PostgreSQL Phase 계획의 완료 여부도 클래스 존재만으로 단정하지 않습니다.

## 데이터와 보안

- 일반 데이터와 PII schema, 사용자 식별자 `user_key`, 목적별 datasource의 접근 범위를 구분합니다.
- 현재 보안 경계는 [security-hardening-current-state.md](core/security-hardening-current-state.md), 설계 근거와 이행 이력은 [user-data-separation-design.md](core/user-data-separation-design.md)를 확인합니다.
- source 원문·정규화·호환 응답·AI 보완을 구분합니다. 상세 계약은 [정규화 현재 상태](policy/policy-normalization-current-state.md)를 확인합니다.
- DB 초기화와 기존 DB 변경은 [db-migration.md](core/db-migration.md)에서 로컬/서버 절차를 나눠 확인합니다.

## 후속 변경

추천 데이터 보존·로그·자격조건·수집 원문·정규화 갱신의 문제를 먼저 재현합니다. Java 전환, 별도 워커, Batch API, FastAPI는 각각 근거와 범위를 정한 별도 변경이며, 오래된 1차/2차 표를 자동 실행 순서로 사용하지 않습니다.
