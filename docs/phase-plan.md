# 구현 현황

이 문서는 현재 구현 상태와 남은 1차 작업을 확인하기 위한 현황판입니다.
요구사항 원본은 [srs-v2.10.md](./srs-v2.10.md), 실행 방법은 [testing.md](./testing.md), 배포 절차는 [deployment.md](./deployment.md)를 봅니다.

## 현재 결론

백엔드/프론트 1차 핵심 기능 구현 및 연동 완료 상태입니다.
마이페이지 내 정보/우선순위/알림 설정/계정(비밀번호 변경, 회원탈퇴) 탭 전체 API 연동이 완료됐습니다.
정책 목록/검색/상세/랭킹은 비로그인 허용, 추천/북마크/마이페이지는 로그인 필수로 분리됐습니다.
AI 추천 품질 점검 후 프롬프트 개선, 중복 추천 제거, 노이즈 정책 필터, CTR 로그 구조를 보완했습니다.
데모 시나리오 전체 실행 완료 및 발견된 문제 수정됐습니다.
남은 작업은 운영 배포/운영성 검증(운영 서버 Docker Compose, HTTPS/Nginx, CTR 분석, PII 분리)과 2차 확장 기능(군집 캐시 추천, 카카오 알림톡, 검색 로그, 대시보드)입니다.

## 완료된 백엔드 1차 범위

- 프로젝트 기본 설정: Spring Boot, Gradle, Dockerfile, Docker Compose, `.env.example`
- 공통 모듈: `ApiResponse`, `ErrorCode`, `CustomException`, `GlobalExceptionHandler`, `BaseTimeEntity`
- 인증/사용자: 회원가입, 로그인, refresh, logout, 프로필 수정, 우선순위 설정, 전화번호 AES 암호화
- 정책 조회: 목록, 검색, 필터, 상세, 조회수 중복 방지, 랭킹
- 공공 API 수집: 온통청년, 복지로 중앙, 복지로 지자체, 복지로 상세
- 수집 안정화: 429 재시도/중단, 중복 실행 방지, lock 재시도, 부분 성공 허용
- 수집 관측성: raw payload 저장, `api_sync_logs` 실행 결과 저장
- 추천 1차: `youth_all` 군집, Retrieval, Rule scoring, Realtime AI, ScoreWeight, ReRanking, 추천 저장
- 검색/추천 운영 튜닝: 일반 검색 지역 조인 분리, EXPLAIN 재검증, `service_regions` 복합 인덱스 확정
- 북마크: 정책 기준 북마크, 추천 북마크 상태 유지, 200건 제한
- 알림 1차: 이메일 발송, 발송 이력 저장, 실패 재시도, 수신 거부 링크
- DB 운영: 신규 schema, 기존 DB용 수동 migration SQL, migration 문서
- 테스트 분리: 기본 테스트 `test`, MySQL/Redis 통합 테스트 `integrationTest`

## 검증 완료

- `./gradlew compileJava --no-daemon`
- `./gradlew test --no-daemon`
- `npm run lint`
- `npm run build`
- 2026-04-23 메인 페이지 API 연동 후 `frontend`에서 `npm run lint`
- 2026-04-23 메인 페이지 API 연동 후 `frontend`에서 `npm run build`  
  - Vite 번들 크기 경고 발생. 빌드는 성공했으며 기능 실패는 아님.
- 2026-04-23 GitHub 작업 규칙 문서 링크 확인
- 2026-04-23 GitHub push 전 `frontend`에서 `npm run lint`
- 2026-04-23 GitHub push 전 `frontend`에서 `npm run build`  
  - Vite 번들 크기 경고 발생. 빌드는 성공했으며 기능 실패는 아님.
- 2026-04-23 GitHub push 전 `backend`에서 `./gradlew test --no-daemon`
- 2026-04-24 `docker compose up -d db redis`
- 2026-04-24 테스트 DB에 `V2026_04_23_01__add_api_sync_logs.sql` 적용
- 2026-04-24 테스트 DB `api_sync_logs.status`를 ENUM으로 보정 후 `backend`에서 `./gradlew integrationTest --no-daemon`
- 2026-04-24 기존 Docker DB에 최신 migration 3종 재적용
- 2026-04-24 `api_sync_logs` 테이블/인덱스/ENUM 컬럼 확인
- 2026-04-24 추천 refresh 같은 초 중복 저장 방지 후 `backend`에서 `./gradlew test --no-daemon`
- 2026-04-24 추천 refresh 같은 초 중복 저장 방지 후 `backend`에서 `./gradlew integrationTest --no-daemon`
- 2026-04-24 Docker 앱 최신 이미지 재빌드 후 `POST /api/admin/collect/youth` 실행
  - 외부 API 400으로 `COL001` 응답
  - `api_sync_logs`에 `job_name=YOUTH`, `status=failed`, `failed_count=1`, `error_code=CustomException` 기록 확인
- 2026-04-24 실제 온통청년 API key로 `POST /api/admin/collect/youth` smoke test 재실행
  - API 응답 200
  - `api_sync_logs`에 `job_name=YOUTH`, `status=success`, `requested_count=2266`, `saved_count=2266`, `failed_count=0` 기록 확인
  - `welfare_services`의 `YOUTH` 정책 2266건, `raw_api_payloads`의 `YOUTH` 원문 2266건 확인
- 2026-04-24 Gmail SMTP 실제 발송 smoke test 실행
  - `.env`의 `GMAIL_USERNAME`, `GMAIL_PASSWORD` 환경변수 로딩 확인
  - `RUN_SMTP_SMOKE=true ./gradlew test --tests com.example.welfare.notification.gateway.GmailSmtpSmokeTest --no-daemon`
  - 발신 계정 자신에게 테스트 메일 1건 발송 성공
- 2026-04-24 Gmail SMTP smoke test 수신자 분리 실행
  - `.env`의 `SMTP_SMOKE_TO` 환경변수 로딩 확인
  - `RUN_SMTP_SMOKE=true ./gradlew test --tests com.example.welfare.notification.gateway.GmailSmtpSmokeTest --no-daemon --rerun-tasks`
  - 지정 수신자 대상으로 테스트 메일 1건 발송 성공
- 2026-04-24 Gmail SMTP smoke test 추가 후 `backend`에서 `./gradlew test --no-daemon`
- 2026-04-24 정책 상세 페이지 API 연동 후 `frontend`에서 `npm run lint`
- 2026-04-24 정책 상세 페이지 API 연동 후 `frontend`에서 `npm run build`
  - Vite 번들 크기 경고 발생. 빌드는 성공했으며 기능 실패는 아님.
- 2026-04-24 정책 목록 페이지 API 연동 후 `frontend`에서 `npm run lint`
- 2026-04-24 정책 목록 페이지 API 연동 후 `frontend`에서 `npm run build`
  - Vite 번들 크기 경고 발생. 빌드는 성공했으며 기능 실패는 아님.
- 2026-04-24 추천 refresh API 연동 후 `frontend`에서 `npm run lint`
- 2026-04-24 추천 refresh API 연동 후 `frontend`에서 `npm run build`
  - Vite 번들 크기 경고 발생. 빌드는 성공했으며 기능 실패는 아님.
- 2026-04-24 마이페이지 북마크 목록 API 연동 후 `frontend`에서 `npm run lint`
- 2026-04-24 마이페이지 북마크 목록 API 연동 후 `frontend`에서 `npm run build`
  - Vite 번들 크기 경고 발생. 빌드는 성공했으며 기능 실패는 아님.
- 2026-04-24 정책 목록/검색/상세 초기 북마크 상태 계약 보완 후 `backend`에서 `./gradlew test --no-daemon`
- 2026-04-24 정책 목록/검색/상세 초기 북마크 상태 계약 보완 후 `backend`에서 `./gradlew integrationTest --no-daemon`
- 2026-04-24 정책 목록/검색/상세 초기 북마크 상태 계약 보완 후 `frontend`에서 `npm run lint`
- 2026-04-24 정책 목록/검색/상세 초기 북마크 상태 계약 보완 후 `frontend`에서 `npm run build`
  - Vite 번들 크기 경고 발생. 빌드는 성공했으며 기능 실패는 아님.
- 2026-04-24 정책 검색 총건수/종료 포함 계약 보완 후 `backend`에서 `./gradlew test --no-daemon`
- 2026-04-24 정책 검색 총건수/종료 포함 계약 보완 후 `backend`에서 `./gradlew integrationTest --no-daemon`
- 2026-04-24 정책 검색 총건수/종료 포함 계약 보완 후 `frontend`에서 `npm run lint`
- 2026-04-24 정책 검색 총건수/종료 포함 계약 보완 후 `frontend`에서 `npm run build`
  - Vite 번들 크기 경고 발생. 빌드는 성공했으며 기능 실패는 아님.
- 2026-04-24 정책 검색 비용 관측 로그 추가 후 `backend`에서 `./gradlew test --no-daemon`
- 2026-04-24 정책 검색 비용 관측 로그 추가 후 `backend`에서 `./gradlew integrationTest --no-daemon`
- 2026-04-24 정책 검색 비용 관측 로그 추가 후 `frontend`에서 `npm run lint`
- 2026-04-24 정책 검색 비용 관측 로그 추가 후 `frontend`에서 `npm run build`
  - Vite 번들 크기 경고 발생. 빌드는 성공했으며 기능 실패는 아님.
- 2026-04-24 실제 Docker 앱에서 인증된 검색 요청으로 비용 관측 로그 수집
  - `keyword=청년&status=ACTIVE&sort=NAME&page=0&size=20`: `elapsedMs=9956`, `rawScanned=1687`, `batches=9`
  - `keyword=청년&includeClosed=true&sort=LATEST&page=0&size=20`: `elapsedMs=9991`, `rawScanned=1687`, `batches=9`
  - `keyword=지원&sort=RELEVANCE&page=0&size=20`: `elapsedMs=23861`, `rawScanned=2854`, `filtered=1783`, `batches=15`
  - `keyword=지원&sort=RELEVANCE&page=3&size=20`: `elapsedMs=23896`, `rawScanned=2854`, `filtered=1783`, `batches=15`
  - `keyword=사업&includeClosed=true&sort=RELEVANCE&page=5&size=20`: `elapsedMs=6111`, `rawScanned=1553`, `filtered=1090`, `batches=8`
  - `keyword=교육&category=교육·직업훈련&sort=RELEVANCE&page=0&size=20`: `elapsedMs=278`, `rawScanned=147`, `filtered=87`, `batches=1`
- 2026-04-24 실제 검색 비용 관측 후 `backend`에서 `./gradlew test --no-daemon`
- 2026-04-24 실제 검색 비용 관측 후 `backend`에서 `./gradlew integrationTest --no-daemon`
- 2026-04-24 실제 검색 비용 관측 후 `frontend`에서 `npm run lint`
- 2026-04-24 실제 검색 비용 관측 후 `frontend`에서 `npm run build`
  - Vite 번들 크기 경고 발생. 빌드는 성공했으며 기능 실패는 아님.
- 2026-04-24 Docker MySQL에 `V2026_04_24_01__add_search_youth_relevance.sql` 적용
- 2026-04-24 `POST /api/admin/policies/search-youth-relevance/rebuild` 실행
  - `processedCount=3598`, `updatedCount=1325`, `relevantCount=2273`, `excludedCount=1325`
- 2026-04-24 정책 검색 SQL 레벨 청년 필터 이관 후 실제 Docker 앱에서 인증 검색 재측정
  - `keyword=청년&status=ACTIVE&sort=NAME&page=0&size=20`: `elapsedMs=1300`, `total=1929`
  - `keyword=청년&includeClosed=true&sort=LATEST&page=0&size=20`: `elapsedMs=1291`, `total=1929`
  - `keyword=지원&sort=RELEVANCE&page=0&size=20`: `elapsedMs=1292`, `total=2031`
  - `keyword=지원&sort=RELEVANCE&page=3&size=20`: `elapsedMs=1293`, `total=2031`
  - `keyword=사업&includeClosed=true&sort=RELEVANCE&page=5&size=20`: `elapsedMs=600`, `total=1271`
  - `keyword=교육&category=교육·직업훈련&sort=RELEVANCE&page=0&size=20`: `elapsedMs=152`, `total=90`
- 2026-04-24 정책 검색 SQL 레벨 청년 필터 이관 후 `backend`에서 `./gradlew compileJava --no-daemon`
- 2026-04-24 정책 검색 SQL 레벨 청년 필터 이관 후 `backend`에서 `./gradlew test --no-daemon`
- 2026-04-24 정책 검색 SQL 레벨 청년 필터 이관 후 `backend`에서 `./gradlew integrationTest --no-daemon`
- 2026-04-24 정책 검색 SQL 레벨 청년 필터 이관 후 `frontend`에서 `npm run lint`
- 2026-04-24 정책 검색 SQL 레벨 청년 필터 이관 후 `frontend`에서 `npm run build`
  - Vite 번들 크기 경고 발생. 빌드는 성공했으며 기능 실패는 아님.
- 2026-04-24 넓은 단일 키워드 검색 추가 튜닝 전 `EXPLAIN ANALYZE`로 병목 확인
  - `+지원` 일반 검색 본문 조회에서 `LEFT JOIN service_regions + DISTINCT`로 `72923`행 중간 결과와 임시 테이블 정렬이 발생했고 `actual time=929ms`
  - `+청년` 일반 검색 본문 조회에서도 같은 구조로 `69408`행 중간 결과와 `actual time=853ms`
  - count query 자체는 `+지원` 기준 `actual time=32.8ms`로 병목이 아님을 확인
- 2026-04-24 일반 검색에서 지역 조인을 제거하고 지역 검색만 `EXISTS` 기반으로 분리
- 2026-04-24 지역 조인 분리 후 실제 Docker 앱에서 인증 검색 재측정
  - `keyword=청년&status=ACTIVE&sort=NAME&page=0&size=20`: `elapsedMs=85`, 응답 `0.138s`, `total=1929`
  - `keyword=청년&includeClosed=true&sort=LATEST&page=0&size=20`: `elapsedMs=31`, 응답 `0.042s`, `total=1929`
  - `keyword=지원&sort=RELEVANCE&page=0&size=20`: `elapsedMs=38`, 응답 `0.051s`, `total=2031`
  - `keyword=지원&sort=RELEVANCE&page=3&size=20`: `elapsedMs=35`, 응답 `0.048s`, `total=2031`
  - `keyword=사업&includeClosed=true&sort=RELEVANCE&page=5&size=20`: `elapsedMs=22`, 응답 `0.033s`, `total=1271`
  - `keyword=교육&category=교육·직업훈련&sort=RELEVANCE&page=0&size=20`: 응답 `0.030s`, `total=90`
  - `keyword=지원&sido=서울특별시&sgg=관악구&sort=RELEVANCE&page=0&size=20`: 응답 `0.085s`, `total=17`
- 2026-04-24 넓은 단일 키워드 검색 추가 튜닝 후 `backend`에서 `./gradlew compileJava --no-daemon`
- 2026-04-24 넓은 단일 키워드 검색 추가 튜닝 후 `backend`에서 `./gradlew test --no-daemon`
- 2026-04-24 넓은 단일 키워드 검색 추가 튜닝 후 `backend`에서 `./gradlew integrationTest --no-daemon`
- 2026-04-24 이메일 중복확인 API 및 회원가입/로그인 계약 정리 후 `backend`에서 `./gradlew test --no-daemon`
- 2026-04-24 이메일 중복확인 API 및 회원가입/로그인 계약 정리 후 `backend`에서 `./gradlew integrationTest --no-daemon`
- 2026-04-24 이메일 중복확인 API 및 회원가입/로그인 계약 정리 후 `frontend`에서 `npm run lint`
- 2026-04-24 이메일 중복확인 API 및 회원가입/로그인 계약 정리 후 `frontend`에서 `npm run build`
  - Vite 번들 크기 경고 발생. 빌드는 성공했으며 기능 실패는 아님.
- 2026-04-24 `PolicySearchService` Java 후처리 루프 제거 및 `search_youth_relevant` SQL 필터 기반 단순화 후 `backend`에서 `./gradlew test --no-daemon`
  - `PolicySearchServiceTest` 2건 (지역 없는 검색, 지역 있는 검색) 포함 전체 통과
- 2026-04-24 `priority_options` 재설계 및 `UserPriorityRepository` flush 수정 후 `backend`에서 `./gradlew test --no-daemon`
- 2026-04-24 `priority_options` 재설계 후 `frontend`에서 `npm run lint` 및 `npm run build`
  - Vite 번들 크기 경고 발생. 빌드는 성공했으며 기능 실패는 아님.
- 2026-04-24 마이페이지 내 정보/우선순위/알림 설정 API 연동 후 `frontend`에서 `npm run lint` 및 `npm run build`
  - Vite 번들 크기 경고 발생. 빌드는 성공했으며 기능 실패는 아님.
- 2026-04-24 마이페이지 API 연동 후 실제 Docker 앱에서 프로필 저장·알림 저장·우선순위 저장 검증 완료
- 2026-04-24 비밀번호 변경 API 구현 후 `backend`에서 `./gradlew test --no-daemon`
- 2026-04-24 비밀번호 변경 API 구현 후 실제 Docker 앱에서 틀린 비밀번호 거부·변경 성공·새 비밀번호 로그인 검증 완료
- 2026-04-24 비밀번호 변경 프론트 연동 후 `frontend`에서 `npm run lint` 및 `npm run build`
  - Vite 번들 크기 경고 발생. 빌드는 성공했으며 기능 실패는 아님.
- 2026-04-24 정책 비로그인 허용 후 `backend`에서 `./gradlew test --no-daemon`
- 2026-04-24 정책 비로그인 허용 후 실제 Docker 앱에서 비로그인 정책 목록·검색·상세 200, 추천 403 확인
- 2026-04-24 AI 프롬프트 개선, CTR 로그 생성 경로 보완, logId 응답 포함 후 `backend`에서 `./gradlew test --no-daemon`
- 2026-04-24 AI 프롬프트 개선 후 실제 Docker 앱에서 추천 refresh 검증
  - logId 응답 포함 확인 (serviceId=7776 logId=1)
  - `recommendation_logs` 40건 생성 확인
  - is_fallback=1 행은 rule_base_score=0 + AI 미호출 케이스로 정상
- 2026-04-24 AI 프롬프트 system/user 분리 + TOP_N 조정 + 중복 추천 수정 후 `backend`에서 `./gradlew test --no-daemon`
- 2026-04-24 개선 후 실제 Docker 앱에서 추천 refresh 재검증
  - AI 점수 15건/15건 전부 반환 (이전 20건 중 8건 누락 → 개선)
  - `user_recommendations` total=unique=40 (중복 해소)
- 2026-04-24 추천 품질 2차 점검 후 수정
  - `backend`에서 `./gradlew test --no-daemon` 통과
  - final_score 범위 0.000~0.980 → 0.287~0.960 (zero 제거)
  - 노이즈 정책(병역/농촌/다문화) 추천에서 제거됨
  - 로그 누적 해소: 24건(미클릭) → refresh 시 정리 후 신규 24건만 유지
- 2026-04-24 `V2026_04_24_01`, `V2026_04_24_02` migration 적용 확인 후 `backend`에서 `./gradlew integrationTest --no-daemon`
  - `AuthRedisIntegrationTest`, `PolicyBookmarkIntegrationTest`, `RecommendationFlowIntegrationTest` 전체 통과
- 2026-04-24 Docker 앱 재빌드 후 가상 유저(`testuser@youth-welfare.dev`) end-to-end 검증
  - 회원가입 → 로그인 → 프로필 조회 → 우선순위 저장(HOUSING·JOB·EDUCATION·FINANCE·DEADLINE) → 추천 refresh(40건, AI reason 정상) → 북마크 토글 → 북마크 목록 조회 → 검색 결과 북마크 상태 확인 → Refresh Token 재발급 전 구간 정상
- 2026-04-25 챗봇 설계 문서 추가 후 `docs` 링크 점검
  - `docs/README.md`, `docs/chatbot-plan.md`, `docs/phase-plan.md` 상호 링크 확인
- 2026-04-25 `/chat` 자리표시자 문구 정리 후 `frontend`에서 `npm run lint`
- 2026-04-25 `/chat` 자리표시자 문구 정리 후 `frontend`에서 `npm run build`
  - Vite 번들 크기 경고 발생. 빌드는 성공했으며 기능 실패는 아님.
- 2026-04-25 챗봇 엔티티/리포지토리 골격 추가 후 `backend`에서 `./gradlew test --no-daemon`
- 2026-04-25 챗봇 엔티티/리포지토리 골격 추가 후 `backend`에서 `./gradlew integrationTest --no-daemon`
- 2026-04-26 챗봇 세션 CRUD API 구현 후 `backend`에서 `./gradlew test --no-daemon`
- 2026-04-26 챗봇 세션 CRUD API 구현 후 `backend`에서 `./gradlew integrationTest --no-daemon`
- 2026-04-26 챗봇 메시지 목록 조회 API 구현
  - `ChatMessageService`, `GET /api/chat/sessions/{sessionId}/messages` 추가
  - `ChatMessageServiceTest`, `ChatMessageApiIntegrationTest`로 소유권/정렬/참조 정책 ID 응답 검증
- 2026-04-26 챗봇 메시지 목록 조회 API 구현 후 `backend`에서 `./gradlew test --no-daemon`
- 2026-04-26 챗봇 메시지 목록 조회 API 구현 후 `backend`에서 `./gradlew integrationTest --no-daemon`
- 2026-04-26 챗봇 정책 조회 전용 서비스 구현
  - `ChatPolicyService`, `ChatPolicyCandidate` 추가
  - 질문 FULLTEXT 후보 조회 후 무결과/기호-only 입력 시 인기 청년 정책 fallback 규칙 고정
- 2026-04-26 챗봇 정책 조회 전용 서비스 구현 후 `backend`에서 `./gradlew test --no-daemon`
- 2026-04-26 챗봇 정책 조회 전용 서비스 구현 후 `backend`에서 `./gradlew integrationTest --no-daemon`
- 2026-04-26 챗봇 메시지 전송 API 구현
  - `POST /api/chat/sessions/{sessionId}/messages` 추가
  - 질문 저장, 정책 후보 기반 임시 답변 저장, 세션 제목/`last_message_at` 갱신 연결
- 2026-04-26 챗봇 메시지 전송 API 구현 후 `backend`에서 `./gradlew test --no-daemon`
- 2026-04-26 챗봇 메시지 전송 API 구현 후 `backend`에서 `./gradlew integrationTest --no-daemon`
- 2026-04-26 챗봇 OpenAI 프롬프트/응답 스키마 구현
  - `ChatAiGateway`, `ChatAiResult` 추가
  - JSON 응답 파서, 후보 정책 `service_id` allowlist 검증, AI 실패 fallback 연결
- 2026-04-26 챗봇 OpenAI 프롬프트/응답 스키마 구현 후 `backend`에서 `./gradlew test --no-daemon`
- 2026-04-26 챗봇 OpenAI 프롬프트/응답 스키마 구현 후 `backend`에서 `./gradlew integrationTest --no-daemon`
- 2026-04-26 로그아웃/회원탈퇴 시 챗 세션 삭제 연동
  - `ChatSessionCleanupService` 추가
  - `AuthService.logout`, `AuthService.logoutByRefreshToken`, `UserService.withdraw`에서 사용자 세션/메시지 정리 연결
  - `AuthRedisIntegrationTest`, `UserWithdrawChatCleanupIntegrationTest`로 로그아웃/탈퇴 시 세션 cascade 삭제 확인
- 2026-04-26 로그아웃/회원탈퇴 시 챗 세션 삭제 연동 후 `backend`에서 `./gradlew test --no-daemon`
- 2026-04-26 `docker compose up -d db redis`
- 2026-04-26 로그아웃/회원탈퇴 시 챗 세션 삭제 연동 후 `backend`에서 `./gradlew integrationTest --no-daemon`
- 2026-04-26 챗봇 요청 rate limit / abuse 방지 구현
  - `ChatRateLimitService` 추가
  - Redis fixed-window로 사용자별 `POST /api/chat/sessions/{sessionId}/messages` 요청 상한 적용
  - 초과 시 `CH002` 429 반환, 차단 요청은 USER/ASSISTANT 메시지를 저장하지 않도록 고정
- 2026-04-26 챗봇 요청 rate limit / abuse 방지 구현 후 `backend`에서 `./gradlew test --no-daemon`
- 2026-04-26 `docker compose up -d db redis`
- 2026-04-26 챗봇 요청 rate limit / abuse 방지 구현 후 `backend`에서 `./gradlew integrationTest --no-daemon`
- 2026-04-26 프론트 `/chat` 실제 화면 및 로그인 가드 구현
  - `RequireLogin`, `ChatPage` 추가
  - 세션 목록/메시지 목록/질문 전송/정책 상세 이동 UI 연결
  - 로그인 후 원래 경로 복귀, 프론트 로그아웃의 서버 `/api/auth/logout` 연동 추가
- 2026-04-26 프론트 `/chat` 실제 화면 및 로그인 가드 구현 후 `frontend`에서 `npm run lint`
- 2026-04-26 프론트 `/chat` 실제 화면 및 로그인 가드 구현 후 `frontend`에서 `npm run build`
  - Vite 번들 크기 경고는 남았지만 빌드는 성공
- 2026-04-26 로그인 전 비밀번호 재설정 메일/토큰 구현
  - `POST /api/auth/password-reset/request`, `POST /api/auth/password-reset/confirm` 추가
  - Redis 30분 토큰, 사용자당 최신 토큰 1개만 유효, 메일 발송 실패 시 토큰 즉시 정리
  - 프론트 `/reset-password` 공개 화면과 메일 링크 연결
- 2026-04-26 로그인 전 비밀번호 재설정 메일/토큰 구현 후 `backend`에서 `./gradlew test --no-daemon`
- 2026-04-26 `docker compose up -d db redis`
- 2026-04-26 로그인 전 비밀번호 재설정 메일/토큰 구현 후 `backend`에서 `./gradlew integrationTest --no-daemon`
- 2026-04-26 로그인 전 비밀번호 재설정 메일/토큰 구현 후 `frontend`에서 `npm run lint`
- 2026-04-26 로그인 전 비밀번호 재설정 메일/토큰 구현 후 `frontend`에서 `npm run build`
  - Vite 번들 크기 경고는 남았지만 빌드는 성공
- 2026-04-27 운영 admin 계정 수동 생성 절차 문서화
  - `docs/admin-account-runbook.md` 추가
  - `docs/deployment.md`, `docs/README.md`에 admin 계정 bootstrap/revoke 절차 링크 반영
- 2026-04-27 운영 admin 계정 수동 생성 절차 문서화 후 `backend`에서 `./gradlew integrationTest --no-daemon --tests com.example.welfare.integration.AdminSecurityIntegrationTest`
- 2026-04-27 운영 admin 계정 수동 생성 절차 문서화 후 `git diff --check`
- 2026-04-27 검색/추천 지역 쿼리 EXPLAIN 재검증 및 `service_regions` 복합 인덱스 확정
  - Docker MySQL 기준 `welfare_services=3604`, `service_regions=117640`에서 `SHOW VARIABLES LIKE 'ngram_token_size'` = `2` 확인
  - `V2026_04_27_01__add_service_region_compound_indexes.sql` 적용 및 `SHOW INDEX FROM service_regions` 확인
  - 추천 지역 후보 JPQL을 `LEFT JOIN DISTINCT` 대신 `EXISTS/NOT EXISTS` 로 변경
  - 지역 검색 `sido/sgg` EXPLAIN은 복합 인덱스를 항상 선택하지 않아 후속 재측정 항목으로 분리
- 2026-04-27 검색/추천 지역 쿼리 EXPLAIN 재검증 후 `backend`에서 `./gradlew test --no-daemon`
- 2026-04-27 `docker compose` 기동 상태에서 `V2026_04_27_01__add_service_region_compound_indexes.sql` 적용 후 `ANALYZE TABLE service_regions`
- 2026-04-27 검색/추천 지역 쿼리 EXPLAIN 재검증 후 `backend`에서 `./gradlew integrationTest --no-daemon --tests com.example.welfare.integration.RecommendationRegionQueryIntegrationTest`
- 2026-04-27 검색/추천 지역 쿼리 EXPLAIN 재검증 후 `git diff --check`
- 2026-04-27 지역 검색 `sido-only` / `sido+sgg` 쿼리 분리
  - `PolicySearchService`가 `sgg` 유무에 따라 `searchByKeywordWithFiltersWithSido`, `searchByKeywordWithFiltersWithSidoSgg`로 분기
  - `+청년 +취업`, `서울특별시/강남구` count query `actual time=20.8ms -> 3.4ms`
  - `+청년 +취업`, `서울특별시/강남구` 본문 query `actual time=15.2ms -> 3.5ms`
- 2026-04-27 지역 검색 쿼리 분리 후 `backend`에서 `./gradlew test --no-daemon --tests com.example.welfare.policy.service.PolicySearchServiceTest`
- 2026-04-27 지역 검색 쿼리 분리 후 `backend`에서 `./gradlew integrationTest --no-daemon --tests com.example.welfare.integration.PolicySearchRegionQueryIntegrationTest --tests com.example.welfare.integration.RecommendationRegionQueryIntegrationTest`
- 2026-04-27 추천 지역 후보 `regionCode` / `sido` 쿼리 분리
  - `RetrievalService`가 `regionCode` 유무에 따라 `findCandidatesWithRegionCode` / `findCandidatesWithSido` 와 최신순 쿼리로 분기
  - 추천 기본 후보 50건 조회는 `regionCode` 경로 `4.8ms`, `sido` 경로 `3.4ms`
  - 추천 최신순 후보 20건 조회는 `regionCode` 경로 `16.1ms`, `sido` 경로 `13.3ms`
- 2026-04-27 추천 지역 후보 쿼리 분리 후 `backend`에서 `./gradlew test --no-daemon --tests com.example.welfare.recommend.service.RetrievalServiceTest`
- 2026-04-27 추천 지역 후보 쿼리 분리 후 `backend`에서 `./gradlew integrationTest --no-daemon --tests com.example.welfare.integration.RecommendationRegionQueryIntegrationTest`
- 2026-04-25 운영/설계 보조 문서 링크 및 작업 추적 정합성 점검
  - `docs/README.md`에 `db-search-recommend-ops-guide.md`, `user-data-separation-design.md` 링크 추가
  - `docs/phase-plan.md`의 완료/진행 예정/남은 작업 간 상태 충돌 정리
  - `docs/troubleshooting-log.md`에 문서 추적 누락 재발 방지 기록 추가
- 2026-04-25 `/api/admin/**` 인증/권한 강화 후 `backend`에서 `./gradlew test --no-daemon`
  - `AdminSecurityWebMvcTest`로 비인증 401, 일반 사용자 403, 관리자 200 확인
- 2026-04-25 `docker compose up -d db redis`
- 2026-04-25 `/api/admin/**` 인증/권한 강화 후 `backend`에서 `./gradlew integrationTest --no-daemon`
  - `AdminSecurityIntegrationTest`로 관리자 예약 이메일 signup 차단, 관리자 로그인/refresh 후 관리자 API 200 확인
- 2026-04-25 챗봇 응답 DTO/API 계약 초안 고정
  - `docs/api-mapping.md`, `docs/chatbot-plan.md`에 세션/메시지/답변 필드 계약 반영
  - `backend`에 `chat/dto` request/response 골격 추가
- 2026-04-25 챗봇 응답 DTO/API 계약 고정 후 `backend`에서 `./gradlew test --no-daemon`
- 2026-04-25 테스트 DB에 `V2026_04_25_01__add_chat_tables.sql` 적용
- 2026-04-25 `chat_sessions`, `chat_messages` 테이블 및 인덱스 확인
  - `idx_cs_user_last_message`, `idx_cm_session_created`, FK cascade 확인
- 2026-04-25 챗봇 DB migration 추가 후 `backend`에서 `./gradlew integrationTest --no-daemon`
- 2026-04-25 챗봇 DB migration 추가
  - `schema.sql`, `V2026_04_25_01__add_chat_tables.sql`, `docs/db-migration.md` 반영
- 2026-04-25 테스트 DB에 `V2026_04_25_01__add_chat_tables.sql` 적용
  - `chat_sessions`, `chat_messages` 테이블 및 인덱스 확인
- 2026-04-25 챗봇 엔티티/리포지토리 골격 추가
  - `ChatSession`, `ChatMessage`, `ChatMessageRole`, repository 2종 추가
  - `ChatRepositoryIntegrationTest`로 세션 최신순 조회, 메시지 정렬, cascade 삭제 검증
- 2026-04-25 챗봇 엔티티/리포지토리 골격 추가 후 `backend`에서 `./gradlew test --no-daemon`
- 2026-04-25 챗봇 엔티티/리포지토리 골격 추가 후 `backend`에서 `./gradlew integrationTest --no-daemon`
- 2026-04-26 챗봇 세션 CRUD API 구현
  - `ChatSessionController`, `ChatSessionService`, `CH001` 추가
  - `POST/GET/DELETE /api/chat/sessions` 구현
  - `ChatSessionApiIntegrationTest`로 생성/목록/삭제/소유권 검증
- 2026-04-26 챗봇 세션 CRUD API 구현 후 `backend`에서 `./gradlew test --no-daemon`
- 2026-04-26 챗봇 세션 CRUD API 구현 후 `backend`에서 `./gradlew integrationTest --no-daemon`

## 작업 추적

작업을 완료하면 이 섹션을 먼저 갱신합니다.
완료한 항목은 `완료`로 옮기고, 새로 발견한 작업은 `진행 예정`에 추가합니다.
작업 중 문제를 해결했거나 재발 가능성이 있는 판단을 했다면 [troubleshooting-log.md](./troubleshooting-log.md)에 `문제 / 해결 / 이유` 형식으로 추가합니다.

### 진행 예정

- [ ] 운영 서버 Docker Compose 기동
- [ ] HTTPS/Nginx 적용
- [ ] CTR 분석 쿼리 실행 결과 확보
- [ ] 사용자 PII 분리 이행안 확정 (`users` 책임 분리, 서비스 계정 권한 분리)
- [ ] 카카오 알림톡 연동 (2차, 심사 완료 후)

### 완료

- [x] 운영 데이터 기준 추천 지역 후보 `regionCode` / `sido` 쿼리 분리 및 EXPLAIN 재검증
- [x] 운영 데이터 기준 지역 검색 `sido-only` / `sido+sgg` 쿼리 분리 및 EXPLAIN 재검증
- [x] 검색/추천 쿼리 EXPLAIN 검증 및 인덱스 적용 여부 확정
- [x] 운영 admin 계정 수동 생성 절차 문서화 (`SECURITY_ADMIN_EMAILS`, DB 계정 준비)
- [x] 로그인 전 비밀번호 재설정 메일/토큰 구현
- [x] 프론트 `/chat` 실제 화면 및 로그인 가드 구현
- [x] 챗봇 요청 rate limit / abuse 방지
- [x] 로그아웃/회원탈퇴 시 챗 세션 삭제 연동
- [x] 챗봇 OpenAI 프롬프트/응답 스키마 및 근거 정책 참조 구현
- [x] 챗봇 메시지 전송 API 구현 (`POST /api/chat/sessions/{sessionId}/messages`)
- [x] 챗봇 정책 조회 전용 서비스 구현 (`chat -> policy`, `chat -> recommend` 금지)
- [x] 챗봇 메시지 목록 조회 API 구현 (`GET /api/chat/sessions/{sessionId}/messages`)
- [x] 챗봇 세션 CRUD API 구현 (`POST/GET/DELETE /api/chat/sessions`)
- [x] 챗봇 엔티티/리포지토리 골격 추가
- [x] 챗봇 DB migration 추가 (`chat_sessions`, `chat_messages`)
- [x] 챗봇 응답 DTO/API 계약 고정 (`sessionId`, `answer`, `references`, `needsClarification`)
- [x] `/api/admin/**` JWT 권한 기반 보호 + 관리자 예약 이메일 공개 signup 차단
- [x] 검색/추천 운영 가이드 `docs/db-search-recommend-ops-guide.md` 추가
- [x] 사용자 데이터 분리 설계 `docs/user-data-separation-design.md` 추가
- [x] 문서 목차 `docs/README.md`에 운영/설계 보조 문서 링크 추가
- [x] `docs/phase-plan.md` 작업 추적과 본문 상태 정합성 점검
- [x] 챗봇 구현 설계 문서 `docs/chatbot-plan.md` 추가
- [x] 문서 목차 `docs/README.md`에 챗봇 설계 문서 링크 추가
- [x] 프론트 `/chat` 자리표시자 문구를 현재 계획(2차)과 일치하도록 정리
- [x] 문서 목차 `docs/README.md` 추가
- [x] 기본 테스트와 통합 테스트 태스크 분리
- [x] 테스트 실행 기준 `docs/testing.md` 추가
- [x] 수집 실행 로그 `api_sync_logs` 추가
- [x] 수집 운영 기준 `docs/collect-ops.md` 추가
- [x] 문서 변경이력 제거 및 보관 문서 정리
- [x] `phase-plan.md`를 현재 구현 현황판으로 정리
- [x] 프론트 메인 페이지 더미 데이터 제거
- [x] 메인 페이지 정책 목록/검색 API 연결
- [x] 메인 페이지 저장 추천 get API 연결
- [x] 메인 페이지 정책/추천 북마크 API 연결
- [x] GitHub 커밋/푸시/PR 작업 규칙 문서 추가
- [x] 통합 테스트 실행 환경 확인 후 `./gradlew integrationTest` 실행
- [x] 기존 DB에 최신 migration 적용
- [x] `api_sync_logs` 기록 확인
- [x] 실제 공공 API key로 수집 smoke test
- [x] Gmail SMTP 실제 발송 smoke test
- [x] 정책 상세 API 연결
- [x] 정책 목록 페이지 더미 데이터 제거 및 API 연결
- [x] 추천 refresh API 연결
- [x] 마이페이지 북마크 목록 API 연결
- [x] 정책 목록/상세 초기 북마크 상태 조회 계약 보완
- [x] 정책 검색 API의 총건수/종료 포함 여부 계약 보완
- [x] 정책 검색 totalCount 계산 비용 관측 로그 추가
- [x] 실제 앱 로그로 정책 검색 비용 관측 및 SQL 이관 필요 여부 판단
- [x] 정책 검색 SQL 레벨 청년 필터 설계 및 이관
- [x] 넓은 단일 키워드 검색의 1초대 응답 추가 튜닝 여부 판단
- [x] 정책 조회 API 인증 요구사항과 데모/가이드 문서 일관성 점검
- [x] 아이디/비밀번호 찾기, 이메일 중복확인 처리 방향 확정
- [x] `PolicySearchService` Java 후처리 배치 루프 제거 — `search_youth_relevant` SQL 필터 기반 단순 Page 쿼리로 교체
- [x] `PolicySearchServiceTest` 새 계약(NoRegion/WithRegion 분기) 기반으로 작성 및 통과
- [x] `priority_options` 재설계 — `ONLINE`·`YOUTH_ONLY` 제거, `EDU_JOB`→`EDUCATION`, `AMOUNT`→`FINANCE`, `JOB`·`PARTICIPATION`·`FAMILY` 추가
- [x] `DefaultPriorityMatcher` 신규 코드 반영
- [x] `UserPriorityRepository.deleteByUserId` `@Modifying` JPQL로 교체 (flush 순서 보장)
- [x] 가상 유저로 회원가입→우선순위→추천 refresh→북마크 end-to-end 검증 완료
- [x] 마이페이지 내 정보 탭 프로필 조회/저장 API 연동 (`GET/PUT /api/users/me`)
- [x] 마이페이지 우선순위 탭 저장 API 연동 (`PUT /api/users/me/priorities`)
- [x] 마이페이지 알림 설정 탭 저장 API 연동 (`PUT /api/users/me` notificationYn/notificationPeriod)
- [x] 비밀번호 변경 API 구현 (`PATCH /api/users/me/password`) + 프론트 계정 탭 연동
- [x] 정책 목록/검색/상세/랭킹 비로그인 허용 (`permitAll`) — 추천/북마크/마이페이지는 로그인 유지
- [x] CORS `PATCH` 메서드 누락 추가
- [x] AI 프롬프트에 정책 description 앞 100자 추가 (카테고리 추측 의존 개선)
- [x] 추천 refresh 시 `recommendation_logs` CTR 로그 생성 (알림 발송 의존 제거)
- [x] 추천 refresh 응답에 `logId` 포함 — 프론트 카드 클릭 시 `?log_id=` 전달
- [x] AI 프롬프트 system/user 역할 분리 + 전체 평가 필수 명시
- [x] AI_TOP_N 20→15 조정 (누락 없이 15건 전부 점수 반환 확인)
- [x] 중복 추천 행 문제 수정 — `deleteAllByUserId`로 교체, 북마크 상태만 새 행에 이전
- [x] `final_score=0` 문제 수정 — 정규화 하한을 min→0 고정으로 변경
- [x] 노이즈 정책 컷오프 — `rule_base_score > 8.0` 필터 추가 (병역/농촌/다문화 등 제거)
- [x] CTR 로그 누적 문제 수정 — refresh 전 미클릭 로그 삭제, 클릭 로그는 보존
- [x] 노이즈 필터 방식 개선 — 숫자 임계값(>8.0) → `hasSpecialTargetMismatch` 플래그 기반으로 교체
- [x] 특수 대상 신호 목록에 "현역병", "병역" 추가
- [x] 회원탈퇴 다이얼로그 비밀번호 입력 필드 추가 (기존: 빈 값으로 API 호출 → 탈퇴 불가)
- [x] `GET /api/recommendations`에 `logId` 누락 수정
- [x] 데모 시나리오 전체 실행 완료 — 발견된 문제 수정 및 문서 갱신

## 통합 테스트 실행 방법

아래 테스트는 MySQL/Redis 컨테이너가 떠 있는 상태에서 실행합니다.

```bash
docker compose up -d db redis

cd backend
./gradlew integrationTest
```

대상:

- `AuthRedisIntegrationTest`
- `PolicyBookmarkIntegrationTest`
- `RecommendationFlowIntegrationTest`

## 남은 1차 작업

### 배포/데모

- EC2 또는 운영 서버에서 Docker Compose 기동
- HTTPS/Nginx 적용
- CTR 분석 쿼리 실행 결과 확보
- 사용자 PII 분리 이행안 확정 (`users` 책임 분리, 서비스 계정 권한 분리)

## 2차로 분리된 항목

- Batch AI Gateway
- 나이대 x 소득분위 군집화
- p5~p95 정규화
- 카카오 알림톡
- 슬롯 배치 `[A, A, B?]`
- 검색 로그
- 추천/수집 대시보드
