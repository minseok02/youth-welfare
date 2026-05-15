# Frontend QA Session - 2026-05-15

문서군 진입점: [frontend-qa-docs-index.md](./frontend-qa-docs-index.md)

이 문서는 오늘 기준 브라우저 수동 QA를 바로 시작할 수 있게, 고위험 동선과 기대 URL/state를 미리 채운 세션 시트입니다.

## 실행 정보

- 날짜: `2026-05-15`
- 환경: `local`
- 브라우저:
- backend commit: `9fd9769`
- frontend commit: `9fd9769`
- 관련 baseline:
  - `deploy/smoke/run-local-validation-from-env.sh --full` 통과
  - `deploy/smoke/run-local-pii-sync-cutover-smoke.sh` 통과
  - `deploy/smoke/run-local-policy-quality-summary.sh` baseline 유지
  - `deploy/smoke/run-local-gov24-quality-audit.sh` baseline 유지
  - `deploy/smoke/run-local-ctr-readiness-audit.sh` 최신 snapshot `1923 / 19 / clicked_services=2`

## 사전 조건

- 로그인 사용자:
- admin 사용자:
- 테스트 데이터/정책 snapshot: `local 2026-05-15`
- 시작 URL: `http://127.0.0.1:5173`
- 시작 query / state:

## 1차 고위험 동선

### 1. `/policies` 검색/필터/페이지네이션 -> 상세 -> 상세 뒤로가기 -> 브라우저 back

- 진입 URL: `/policies`
- 조작:
  - 검색어 입력
  - category 변경
  - region/subRegion 변경
  - page 이동
  - 상세 진입
  - 상세 `뒤로가기`
  - 브라우저 back
- 기대:
  - `PoliciesPage` 는 `searchParams` 와 local state가 동기화됨
  - 복귀 후 `search`, `category`, `region`, `subRegion`, `page`, `sort`, `statusFilter` 중 URL에 있던 값이 유지돼야 함
- 기록:
  - 목록 URL:
  - 상세 URL:
  - 상세 뒤로가기 후 URL:
  - 브라우저 back 후 URL:
  - query/state 복원:
  - network/console 이상:

### 2. 비로그인 `/chat` -> `/login?reason=login-required` -> 로그인 후 `/chat?session=...` 복귀

- 진입 URL: `/chat`
- 기대:
  - `RequireLogin` 이 `/login` 으로 보냄
  - `location.state.from.pathname === "/chat"`
  - `reason === "login-required"`
  - 로그인 후 다시 `/chat` 으로 복귀
- 추가 기대:
  - 세션 생성 후에는 URL에 `?session=` 이 붙음
  - 이후 다시 로그인 동선을 거칠 때도 `chatFrom` 이 유지되면 같은 세션 target을 재사용
- 기록:
  - 비로그인 진입 URL:
  - 로그인 이동 URL:
  - `reason`:
  - `state.from`:
  - 로그인 후 복귀 URL:
  - 세션 생성 후 URL:
  - network/console 이상:

### 3. `/mypage?tab=` -> 상세 -> 뒤로가기 -> `?tab=` 복원

- 진입 URL 예시: `/mypage?tab=2`
  - `TAB_IDS` 기준 `2 = bookmark`
- 기대:
  - `MyPage` 활성 탭은 `?tab=` 와 동기화됨
  - 북마크 상세 진입 후 뒤로가기 하면 다시 같은 탭으로 복귀
  - 전역 `마이페이지` 버튼도 현재 `?tab=` target을 재사용
- 기록:
  - 마이페이지 진입 URL:
  - 상세 URL:
  - 뒤로가기 후 URL:
  - `?tab=` 복원:
  - network/console 이상:

### 4. 메인/목록/상세/마이페이지 북마크 on/off 일관성

- 대상 정책 ID:
- 기대:
  - 메인 추천/정책 카드
  - `PoliciesPage`
  - `PolicyDetailPage`
  - `/mypage?tab=2`
  에서 같은 정책의 bookmarked 상태가 일관해야 함
- 비로그인 시작 시 추가 기대:
  - `/login`
  - `reason=login-required`
  - `postLoginAction.type === "toggle-bookmark"`
  - 로그인 후 자동 토글 1회 실행
- 기록:
  - 정책 ID:
  - 메인 상태:
  - 목록 상태:
  - 상세 상태:
  - 마이페이지 반영:
  - 로그인 후 자동 실행 여부:
  - toast / network 이상:

### 5. 세션 만료 -> `/login?reason=expired` -> 재로그인 후 복귀

- 재현 방식:
  - 로그인 후 token 무효화 또는 refresh 실패 유도
- 기대:
  - axios refresh 1회 시도
  - 실패 시 `window.__authExpired`
  - `/login` 이동
  - `reason=expired`
  - 안내 toast 노출
  - 재로그인 후 원래 `state.from` 기준 복귀
- 기록:
  - 만료 전 URL:
  - 실패 API:
  - 로그인 이동 URL:
  - `reason`:
  - toast/message:
  - 재로그인 후 복귀 URL:
  - 무한 loop 여부:

## 2차 보강 동선

### 6. 공개 홈 / 정책 목록 / 검색 empty state

- 홈 진입:
- 정책 목록 진입:
- 검색어:
- empty state / 0건 처리:
- console/network 이상:

### 7. 프로필 / 우선순위 저장 후 새로고침 유지

- `/mypage?tab=0` 프로필 저장:
- `/mypage?tab=1` 우선순위 저장:
- 새로고침 후 유지:

### 8. 추천 refresh / loading / 중복 클릭 방지

- 추천 개수:
- refresh 전 URL:
- refresh 후 URL:
- loading/disabled 동작:
- toast 결과:

### 9. 챗 세션 생성 / 메시지 전송 / 삭제 / `?session=` 정리

- 생성 후 URL:
- 메시지 전송 결과:
- 삭제 대상 session:
- 삭제 후 URL/query:

### 10. 비밀번호 변경 / 회원탈퇴

- 비밀번호 변경 결과:
- 재로그인 요구 여부:
- 회원탈퇴 실패 케이스:
- 회원탈퇴 성공 후 이동:

## 정적 검증

- `cd frontend && npm run lint`: `pass`
- `cd frontend && npm run build`: `pass`

## 발견 이슈

- 이슈 1:
- 이슈 2:

## 리스크 / 메모

- URL/query/state 복원:
- `reason/state.from`:
- `?session=` / `?tab=`:
- network/console 캡처 위치:

## 다음 액션

- 수정 필요:
- 서버 2차 검증 시 다시 볼 항목:
