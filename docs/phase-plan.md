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

## 작업 추적

작업을 완료하면 이 섹션을 먼저 갱신합니다.
완료한 항목은 `완료`로 옮기고, 새로 발견한 작업은 `진행 예정`에 추가합니다.
작업 중 문제를 해결했거나 재발 가능성이 있는 판단을 했다면 [troubleshooting-log.md](./troubleshooting-log.md)에 `문제 / 해결 / 이유` 형식으로 추가합니다.

### 진행 예정

- [ ] 정책 검색 totalCount 계산 비용 관측 및 필요시 SQL 레벨 청년 필터 이관 검토
- [ ] 아이디/비밀번호 찾기, 이메일 중복확인 처리 방향 확정
- [ ] 운영 서버 Docker Compose 기동
- [ ] HTTPS/Nginx 적용
- [ ] 데모 시나리오 전체 실행
- [ ] CTR 분석 쿼리 실행 결과 확보

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

- 아이디/비밀번호 찾기, 이메일 중복확인 처리 방향 확정
- 정책 검색 totalCount 계산 비용 관측 및 필요시 SQL 레벨 청년 필터 이관 검토

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
