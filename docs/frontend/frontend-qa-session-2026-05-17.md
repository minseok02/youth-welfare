# Frontend QA Session - 2026-05-17

문서군 진입점: [frontend-qa-docs-index.md](./frontend-qa-docs-index.md)

이 문서는 `admin dashboard` 수동 QA 결과를 오늘 기준 local baseline으로 남기는 세션 시트입니다.

## 실행 정보

- 날짜: `2026-05-17`
- 시간: `2026-05-17 19:56:07 KST`
- 환경: `local`
- 브라우저: `Playwright (container, mcr.microsoft.com/playwright:v1.54.1-jammy)`
- backend/frontend commit: `6c7af67`
- 관련 baseline:
  - `cd frontend && npm run lint` 통과
  - `cd frontend && npm run build` 통과
  - `ADMIN_PASSWORD=<local admin password> ./deploy/smoke/run-local-admin-dashboard-smoke.sh` 통과
  - `ADMIN_PASSWORD=<local admin password> ./deploy/smoke/run-local-admin-recommendation-breakdowns-smoke.sh` 통과

## 사전 조건

- admin 사용자: `<local admin email>`
- 일반 사용자: `recommend.cl.af8b89b472bd4b@example.com`
- 시작 URL: `http://127.0.0.1:5173`
- backend API origin: `http://app:8080` (container network 기준)

## 실행 시나리오

### 1. admin `/admin/dashboard` 진입

- 진입 URL: `/login` -> `/` -> `/admin/dashboard`
- 기대:
  - `운영 추천 대시보드` heading 표시
  - `수집 실패 상세`, `검색 실패 상세` triage section 표시
  - recommendation review gate 배너 표시
- 결과:
  - `pass`
  - 최종 URL: `/admin/dashboard`
  - 빈 화면/깨진 레이아웃/무한 spinner 없음

### 2. non-admin `/admin/dashboard` 접근 차단

- 진입 URL: `/login` -> `/` -> `/admin/dashboard`
- 기대:
  - 홈(`/`)으로 redirect
  - `운영 대시보드는 관리자 계정만 접근할 수 있습니다.` toast 표시
- 결과:
  - `pass`
  - 최종 URL: `/`
  - toast 표시 확인

### 3. 요약 기간 전환

- 조작:
  - `최근 14일 -> 최근 7일`
  - `최근 7일 -> 최근 30일`
  - `최근 30일 -> 최근 14일`
- 기대:
  - `summary`
  - `recommendation-breakdowns`
  - `collect-failures`
  - `search-failures`
  네 API가 기간 전환에 맞춰 갱신
- 결과:
  - `14 -> 7`, `7 -> 30` 에서는 네 API 재호출 확인
  - `30 -> 14` 는 `React Query staleTime=30000` 범위라 cache reuse 가능 동작으로 관찰
  - UI 전환 자체는 정상

### 4. 섹션별 부분 실패 분리

- 조작:
  - `summary` API를 강제로 `500` 으로 주입
  - `recommendation-breakdowns` API를 강제로 `500` 으로 주입
- 기대:
  - 한 API 실패가 페이지 전체 blank/error로 번지지 않을 것
  - `summary` 실패 시에도 `수집 실패 상세`, `검색 실패 상세` 는 계속 렌더링될 것
  - `recommendation-breakdowns` 실패 시에도 상단 recommendation summary/hero는 계속 렌더링될 것
- 결과:
  - `pass`
  - `summary forced failure` 에서 warning banner + summary error card가 표시되고 collect/search triage는 그대로 유지됨
  - `breakdown forced failure` 에서 recommendation summary/hero는 유지되고, breakdown 섹션만 error card + retry로 분리됨

### 5. 모바일 viewport 재검증

- 기준 커밋: `2e3b9fa`
- 환경: `server`
- 기대:
  - `청년복지플랫폼` 헤더 텍스트가 모바일 폭에서 세로로 깨지지 않을 것
  - 상단 버튼이 wrap되어 겹치거나 화면 밖으로 밀리지 않을 것
  - `DEFERRED_NO_REAL_USER_TRAFFIC` 같은 긴 상태값이 카드 밖으로 넘치지 않을 것
  - 실패 카드(`... 로드 실패`, `다시 시도`, 에러 메시지)가 모바일에서도 읽힐 것
- 결과:
  - `pass`
  - 헤더 브랜드 텍스트는 정상 한 줄로 표시됨
  - 상단 버튼은 모바일에서 두 줄로 wrap되며 겹침/밀림 없음
  - 긴 gate/status 문자열은 카드 내부에서 여러 줄로 감싸짐
  - 실패 카드와 에러 메시지도 모바일에서 정상 가독성 유지
  - 자동 overflow 검사 기준 화면 폭 밖으로 삐져나간 요소 없음

## 관찰 메모

- local host 브라우저가 아니라 container browser로 검증했다.
- backend prod profile의 기본 CORS 허용 origin은 `5173` 이므로, container 내부 dev server도 `5173` 으로 맞춰야 로그인까지 정상 동작했다.
- 부분 실패 분리는 UI에서 직접 API를 끊는 대신, container Playwright runner가 `summary`, `recommendation-breakdowns` 요청만 route fulfill `500` 으로 바꿔 확인했다.
- 이후 server 기준 mobile viewport 재검증까지 추가로 닫았다.

## 발견 이슈

- 없음

## 다음 액션

- 없음
