# 구현 현황

이 문서는 현재 구현 상태와 남은 1차 작업을 확인하기 위한 현황판입니다.
요구사항 원본은 [srs-v2.10.md](./srs-v2.10.md), 실행 방법은 [testing.md](./testing.md), 배포 절차는 [deployment.md](./deployment.md)를 봅니다.

## 현재 결론

백엔드 1차 핵심 기능은 구현 완료 상태입니다.
프론트 메인 페이지는 더미 데이터를 제거하고 정책 목록/검색/저장 추천/북마크 API를 사용하도록 1차 연결했습니다.
정책 상세 페이지도 실제 상세 API와 북마크 토글 API를 사용하도록 연결했습니다.
정책 목록 페이지도 실제 목록/검색/북마크 API를 사용하도록 연결했습니다.
메인 페이지 추천 모드에서는 추천 refresh API로 수동 재추천을 실행할 수 있습니다.
마이페이지 북마크 탭은 사용자 북마크 목록 API를 사용합니다.
정책 목록/검색/상세 응답은 로그인 사용자의 초기 북마크 상태를 함께 반환하도록 보완했습니다.
정책 검색 API는 총건수/총페이지/다음 페이지 여부를 반환하고 종료 포함 여부 파라미터를 지원하도록 보완했습니다.
정책 검색 totalCount 스캔 비용은 서비스 로그로 관측할 수 있게 보강했습니다.
정책 검색은 `search_youth_relevant` 저장 플래그 기반 SQL 필터로 이관해 Java 후처리 totalCount 스캔을 제거했습니다.
실제 Docker 앱 기준으로 넓은 단일 키워드 검색도 지역 조인 분리 후 30ms~140ms대로 내려와 500ms 목표 안으로 들어왔습니다.
인증/회원가입은 이메일 중복확인 API를 추가했고, "아이디 = 이메일", "비밀번호 재설정 메일은 후속 구현" 방향으로 정리했습니다.
통합 테스트는 MySQL/Redis 컨테이너 상태에서 실행 완료했습니다.
남은 1차 작업은 나머지 프론트 화면 API 연동, 실서버 배포/HTTPS 적용, 데모 시나리오 실행입니다.

## 완료된 백엔드 1차 범위

- 프로젝트 기본 설정: Spring Boot, Gradle, Dockerfile, Docker Compose, `.env.example`
- 공통 모듈: `ApiResponse`, `ErrorCode`, `CustomException`, `GlobalExceptionHandler`, `BaseTimeEntity`
- 인증/사용자: 회원가입, 로그인, refresh, logout, 프로필 수정, 우선순위 설정, 전화번호 AES 암호화
- 정책 조회: 목록, 검색, 필터, 상세, 조회수 중복 방지, 랭킹
- 공공 API 수집: 온통청년, 복지로 중앙, 복지로 지자체, 복지로 상세
- 수집 안정화: 429 재시도/중단, 중복 실행 방지, lock 재시도, 부분 성공 허용
- 수집 관측성: raw payload 저장, `api_sync_logs` 실행 결과 저장
- 추천 1차: `youth_all` 군집, Retrieval, Rule scoring, Realtime AI, ScoreWeight, ReRanking, 추천 저장
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
- 2026-04-24 `V2026_04_24_01`, `V2026_04_24_02` migration 적용 확인 후 `backend`에서 `./gradlew integrationTest --no-daemon`
  - `AuthRedisIntegrationTest`, `PolicyBookmarkIntegrationTest`, `RecommendationFlowIntegrationTest` 전체 통과
- 2026-04-24 Docker 앱 재빌드 후 가상 유저(`testuser@youth-welfare.dev`) end-to-end 검증
  - 회원가입 → 로그인 → 프로필 조회 → 우선순위 저장(HOUSING·JOB·EDUCATION·FINANCE·DEADLINE) → 추천 refresh(40건, AI reason 정상) → 북마크 토글 → 북마크 목록 조회 → 검색 결과 북마크 상태 확인 → Refresh Token 재발급 전 구간 정상

## 작업 추적

작업을 완료하면 이 섹션을 먼저 갱신합니다.
완료한 항목은 `완료`로 옮기고, 새로 발견한 작업은 `진행 예정`에 추가합니다.
작업 중 문제를 해결했거나 재발 가능성이 있는 판단을 했다면 [troubleshooting-log.md](./troubleshooting-log.md)에 `문제 / 해결 / 이유` 형식으로 추가합니다.

### 진행 예정

- [ ] 운영 서버 Docker Compose 기동
- [ ] HTTPS/Nginx 적용
- [ ] 데모 시나리오 전체 실행
- [ ] CTR 분석 쿼리 실행 결과 확보
- [ ] 로그인 전 비밀번호 재설정 메일/토큰 구현

### 완료

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

### 프론트 실제 연동

### 배포/데모

- EC2 또는 운영 서버에서 Docker Compose 기동
- HTTPS/Nginx 적용
- [demo-scenario.md](./demo-scenario.md) 전체 실행
- CTR 분석 쿼리 실행 결과 확보

## 2차로 분리된 항목

- Batch AI Gateway
- 나이대 x 소득분위 군집화
- p5~p95 정규화
- 카카오 알림톡
- 슬롯 배치 `[A, A, B?]`
- 챗봇
- 검색 로그
- 추천/수집 대시보드
