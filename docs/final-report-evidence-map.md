# 결과보고서 기능 사실관계 및 API 근거표

이 문서는 결과보고서 본문 작성 시 코드 기준 사실관계를 맞추기 위한 근거표이다.

## 핵심 구현 축

| 영역 | 보고서에 쓸 핵심 내용 | 코드/문서 근거 |
|------|----------------------|----------------|
| 정책 데이터 수집 | 온통청년, 복지로 중앙, 복지로 지자체, Gov24 API에서 정책 목록과 상세 정보를 수집한다. | `backend/src/main/java/com/example/welfare/collect/gateway/*Client.java`, `docs/core/api-mapping.md` |
| 정책 정규화 | 출처별로 다른 정책명, 기관, 지역, 분야, 지원대상, 신청기간, 상세 정보를 내부 정책 모델로 통합한다. | `docs/core/api-mapping.md`, `backend/src/main/java/com/example/welfare/collect/mapper/WelfareServiceMapper.java` |
| 정책 분류 보정 | 원천 분류와 실제 지원 내용이 어긋나는 경우 명확한 오분류만 보수적으로 보정하고, 복합 정책은 수동 검토 대상으로 남긴다. | `backend/src/main/java/com/example/welfare/collect/support/CollectCategorySupport.java`, `backend/src/main/resources/db/migration/V2026_07_18_01__remap_policy_category_first_batch.sql`, `backend/src/main/resources/db/migration/V2026_07_18_02__remap_policy_category_second_batch.sql`, `backend/src/main/resources/db/migration/V2026_07_18_03__remap_policy_category_manual_review_batch.sql`, `docs/phase-plan.md` |
| 정책 검색 | 키워드, 지역, 분야, 신청 상태, 출처, 정렬 조건을 기반으로 정책 목록과 상세 정보를 조회한다. | `backend/src/main/java/com/example/welfare/policy/controller/PolicyController.java`, `frontend/src/pages/PoliciesPage.jsx`, `frontend/src/pages/PolicyDetailPage.jsx` |
| 사용자 인증 | 이메일 기반 회원가입, 로그인, refresh token, 로그아웃, 비밀번호 재설정을 제공한다. | `backend/src/main/java/com/example/welfare/user/controller/AuthController.java`, `docs/core/api-mapping.md` |
| 프로필/개인화 기준 | 연령, 지역, 소득, 취업상태, 가구형태, 관심분야, 우선순위를 추천 입력값으로 관리한다. | `backend/src/main/java/com/example/welfare/user/controller/UserController.java`, `frontend/src/pages/MyPage.jsx` |
| 개인 맞춤 추천 | 후보 추출, 룰 기반 점수, 우선순위 가중치, OpenAI 보조 점수/사유, 최종 점수 산출로 추천한다. | `docs/recommendation/recommendation-pipeline.md`, `backend/src/main/java/com/example/welfare/recommend/controller/RecommendationController.java`, `backend/src/main/java/com/example/welfare/recommend/gateway/RealtimeAiGateway.java` |
| 챗봇 상담 | 저장된 정책 후보와 evidence 안에서 OpenAI 답변을 생성하고, 실패 시 fallback 답변을 제공한다. | `backend/src/main/java/com/example/welfare/chat/controller/ChatSessionController.java`, `backend/src/main/java/com/example/welfare/chat/gateway/ChatAiGateway.java`, `docs/core/openai-runtime-contract.md` |
| 임베딩/의미 검색 | 정책 청크와 질문 임베딩을 활용해 챗봇 검색을 보조하며, 조회 경로는 로컬 fallback을 허용한다. | `backend/src/main/java/com/example/welfare/chat/gateway/OpenAiChatEmbeddingGateway.java`, `docs/core/openai-runtime-contract.md` |
| 알림 | 인앱 알림, 이메일 알림, 웹푸시 구독/발송 구조를 제공한다. | `backend/src/main/java/com/example/welfare/notification/controller/NotificationController.java`, `backend/src/main/java/com/example/welfare/notification/gateway/EmailClient.java`, `frontend/src/lib/webPush.js` |
| 관리자 대시보드 | 정책 수집 상태, 오류 제보, 중복 후보, 링크 검토, 추천/알림 상태를 운영자가 확인한다. | `backend/src/main/java/com/example/welfare/admin/dashboard/controller/AdminDashboardController.java`, `frontend/src/pages/AdminDashboardPage.jsx` |
| 배포/운영 | nginx, Docker Compose, RDS PostgreSQL, ElastiCache Valkey, EC2, ALB 준비 구조로 운영한다. | `docs/deployment.md`, `docs/stabilization-handoff.md`, `docker-compose.prod.elasticache.yml` |

## 외부 API 및 연동 서비스 사용 내역

| API/서비스 | 제공 기관/서비스 | 사용 목적 | 활용 데이터 | 구현 근거 |
|------------|----------------|----------|------------|----------|
| 온통청년 청년정책 API | 한국고용정보원 온통청년 | 청년 정책 목록 및 상세 정보 수집 | 정책번호, 정책명, 정책소개, 정책지원내용, 신청기간, 지역코드, 정책분류, 키워드 등 | `YouthApiClient`, `YouthApiDto`, `WelfareServiceMapper` |
| 복지로 중앙부처 복지서비스 API | 한국사회보장정보원/복지로 | 중앙부처 복지서비스 목록 및 상세 정보 수집 | 서비스 ID, 서비스명, 요약, 소관부처, 지원대상, 선정기준, 신청방법 등 | `BokjiroCentralClient`, `BokjiroDetailClient`, `BokjiroCentralDto` |
| 복지로 지자체 복지서비스 API | 한국사회보장정보원/복지로 | 지방자치단체 복지서비스 목록 및 상세 정보 수집 | 서비스 ID, 서비스명, 지역, 담당부서, 지원대상, 신청방법 등 | `BokjiroLocalClient`, `BokjiroDetailClient`, `BokjiroLocalDto` |
| Gov24 공공서비스 API | 행정안전부/공공데이터포털 | 공공서비스 목록, 상세, 지원조건 수집 | 서비스 ID, 서비스명, 지원내용, 신청방법, 지원대상, 지원조건 fact 등 | `Gov24Client`, `Gov24ServiceListDto`, `Gov24ServiceDetailDto`, `Gov24SupportConditionsDto` |
| OpenAI Chat Completions | OpenAI | 추천 후보 보조 평가, 추천 사유 생성, 챗봇 답변 생성 | 범주형 사용자 정보, 후보 정책 요약, 정책 evidence | `RealtimeAiGateway`, `ChatAiGateway`, `docs/core/openai-runtime-contract.md` |
| OpenAI Embeddings | OpenAI | 챗봇 의미 기반 검색과 정책 청크 임베딩 보조 | 질문/정책 텍스트 임베딩 벡터 | `OpenAiChatEmbeddingGateway`, `PolicyChunkEmbeddingService` |
| SMTP 메일 | Gmail SMTP 또는 AWS SES SMTP 설정 가능 | 비밀번호 재설정, 추천 알림 등 이메일 발송 | 수신자 이메일, 제목, 본문 | `EmailClient`, `EmailNotificationGateway`, `docs/core/testing.md` |
| Web Push | Browser Push API + VAPID | 브라우저 기반 알림 구독 및 발송 | push endpoint, p256dh, auth, device label | `NotificationController`, `WebPushDispatchService`, `frontend/src/lib/webPush.js` |

## 주요 내부 API 엔드포인트

| 기능 | 대표 엔드포인트 | 보고서 설명 |
|------|---------------|------------|
| 인증 | `POST /api/auth/signup`, `POST /api/auth/login`, `POST /api/auth/refresh`, `POST /api/auth/logout` | 회원가입, 로그인, 토큰 갱신, 로그아웃 흐름을 제공한다. |
| 사용자 프로필 | `GET /api/users/me`, `PUT /api/users/me`, `PUT /api/users/me/priorities` | 추천에 필요한 프로필과 우선순위를 관리한다. |
| 정책 조회 | `GET /api/policies`, `GET /api/policies/{id}`, `POST /api/policies/search` | 정책 목록, 상세, 검색 기능을 제공한다. |
| 추천 | `GET /api/recommendations`, `POST /api/recommendations/refresh`, `POST /api/recommendations/{id}/bookmark` | 저장된 추천 조회, 추천 재계산, 추천 정책 북마크를 제공한다. |
| 챗봇 | `POST /api/chat/sessions`, `GET /api/chat/sessions`, `POST /api/chat/sessions/{sessionId}/messages` | 상담 세션 생성, 세션 조회, 사용자 질문 전송 및 답변 생성을 제공한다. |
| 알림 | `GET /api/notifications/me`, `GET /api/notifications/me/unread-count`, `POST /api/notifications/push-subscriptions` | 인앱 알림 조회, 미확인 수 조회, 웹푸시 구독 등록을 제공한다. |
| 관리자 | `GET /api/admin/dashboard/summary`, `GET /api/admin/dashboard/collect-failures`, `GET /api/admin/dashboard/recommendation-breakdowns` | 운영 대시보드에서 수집, 추천, 알림, 정책 품질 상태를 확인한다. |
| 수집 관리 | `POST /api/admin/collect/all`, `POST /api/admin/collect/{sourceKey}`, `POST /api/admin/collect/{sourceKey}/async` | 관리자가 공공 API 수집을 수동 실행하거나 비동기 상태를 확인한다. |

## 보고서 표현 시 주의할 문장

| 피해야 할 표현 | 사용할 표현 |
|----------------|------------|
| AI가 사용자에게 가장 적합한 정책을 자동으로 판단한다. | 룰 기반 후보 선별 이후 OpenAI API를 활용해 후보 정책의 적합도와 추천 사유 생성을 보조한다. |
| 챗봇이 정책 신청 가능 여부를 판정한다. | 챗봇은 저장된 정책 정보와 후보 evidence를 기반으로 정책 탐색과 신청 준비를 보조한다. |
| 모든 공공데이터가 실시간 최신 상태로 유지된다. | 정기 수집과 관리자 검토 구조를 통해 정책 데이터 최신성과 품질을 관리한다. |
| 추천 결과가 실제 수급 자격을 보장한다. | 추천 결과는 정책 탐색을 돕는 정보이며, 최종 자격과 신청 가능 여부는 공식 안내를 확인해야 한다. |
| OpenAI에 사용자 개인정보를 전송한다. | 이름, 이메일, 생년월일 등 직접 식별자는 제외하고 연령대, 지역, 소득구간 등 범주형 정보를 중심으로 활용한다. |

## 화면 캡처 후보

| 화면 | 경로 | 본문 배치 |
|------|------|----------|
| 메인 화면 | `/` | 프로젝트 개요 또는 구현 화면 절 |
| 정책 검색 및 필터 | `/policies` | 정책 통합 검색 기능 설명 |
| 정책 상세 | `/policies/{id}` | 상세 조회 및 신청 준비 설명 |
| 회원가입/로그인 | `/signup`, `/login` | 인증 기능 설명 |
| 마이페이지 | `/mypage` | 프로필 및 우선순위 설정 설명 |
| 맞춤 추천 | `/` 또는 추천 섹션 | AI 기반 맞춤 추천 설명 |
| 챗봇 상담 | `/chat` | 챗봇 상담 및 신청 준비 코칭 설명 |
| 알림함 | `/alerts` | 알림 및 재방문 지원 설명 |
| 관리자 대시보드 | `/admin/dashboard` | 관리자 운영 및 데이터 품질 관리 설명 |

## 검증 결과 작성 기준

| 검증 구분 | 보고서에 쓸 내용 | 근거 |
|----------|----------------|------|
| 백엔드 단위 테스트 | 서비스 로직, API 처리, 추천/챗봇/보안 경계 테스트 | `cd backend && ./gradlew test` |
| 백엔드 통합 테스트 | PostgreSQL, Redis 포함 흐름 검증 | `cd backend && ./gradlew integrationTest` |
| 프론트 정적 검증 | ESLint와 Vite build 통과 여부 | `cd frontend && npm run lint && npm run build` |
| 브라우저 흐름 테스트 | Playwright 기반 주요 사용자 흐름 검증 | `cd frontend && npm run test:e2e` |
| 운영 smoke | 배포 환경 API, health, 인증, 추천, 북마크, 로그아웃 검증 | `deploy/smoke/run-prod-cutover-verification.sh` |
| 운영 관측 | 수집, 정책 품질, 알림, 추천, 챗봇 상태 확인 | `deploy/smoke/run-local-ops-observation-suite.sh` |
| 정책 분류 품질 검증 | 분류 의심 후보를 재검토하고 high-confidence 자동 보정 후보가 0건인지 확인 | `deploy/smoke/run-local-policy-category-suspect-review.sh`, `tmp/policy-category-suspect-review/20260718T175347Z`, `docs/phase-plan.md` |
| 최종 운영 closeout | ALB target 2대 healthy, log alert ok, DB lock/장기 query 0, no-cost ops check 통과 | `deploy/ops/run-no-cost-ops-check.sh`, `tmp/prod-post-deploy-smoke/20260718T175920Z`, `docs/phase-plan.md` |
