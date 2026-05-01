# Frontend QA Current State

## 목적

이 문서는 현재 프론트엔드 QA를 어떤 방식으로 봐야 하는지, 그리고 브라우저 사용자 흐름 기준으로 어디가 핵심 경계인지 빠르게 확인하기 위한 current-state 문서입니다.

## 현재 결론

- 현재 프론트엔드에는 `Playwright`, `Cypress`, `Puppeteer` 같은 브라우저 자동화 테스트 도구가 없습니다.
- 따라서 프론트 QA는 현재 단계에서 `정적 검증(build/lint) + 수동 브라우저 시나리오 검증` 기준으로 봅니다.
- 핵심 검증 축은 아래 네 가지입니다.
  - 라우팅/뒤로가기/재진입
  - 로그인 필요 경로와 로그인 후 복귀
  - 세션 만료 후 refresh 실패와 로그인 화면 복귀
  - 북마크/마이페이지/추천 흐름의 상태 일관성

## 현재 코드 기준 QA 핵심 경계

### 1. 로그인 필요 경로

- `/chat` 은 [RequireLogin.jsx](../frontend/src/components/RequireLogin.jsx) 로 보호됩니다.
- 비로그인 접근 시 `/login` 으로 이동하며 `reason=login-required`, `from=원래 위치` 가 `location.state` 로 전달됩니다.
- [LoginPage.jsx](../frontend/src/pages/LoginPage.jsx) 는 로그인 성공 후 `state.from` 으로 다시 복귀합니다.

### 2. 세션 만료 경로

- [axios.js](../frontend/src/lib/axios.js) 는 `401` 수신 시 `/api/auth/refresh` 를 1회 시도합니다.
- refresh 실패 시 local token 제거 후 `window.__authExpired` 를 호출합니다.
- [RequireLogin.jsx](../frontend/src/components/RequireLogin.jsx) 는 이 callback에서 `logout()` 후 `/login` 으로 이동시키며 `reason=expired` 를 남깁니다.
- [LoginPage.jsx](../frontend/src/pages/LoginPage.jsx) 는 이 reason을 감지해 “로그인 상태가 만료되어 다시 로그인해야 합니다.” toast를 띄웁니다.

### 3. 뒤로가기 경계

- [PolicyDetailPage.jsx](../frontend/src/pages/PolicyDetailPage.jsx) 는 명시적으로 `navigate(-1)` 뒤로가기를 제공합니다.
- `MainPage -> Detail`, `PoliciesPage -> Detail`, `MyPage bookmarks -> Detail` 경로는 모두 브라우저 history 기반 복귀 동작을 수동 확인해야 합니다.
- 특히 [PoliciesPage.jsx](../frontend/src/pages/PoliciesPage.jsx) 의 검색어/필터/페이지 상태는 URL query가 아니라 컴포넌트 local state 중심이라, 뒤로가기/새로고침에서 기대와 다르게 초기화될 가능성이 있습니다.

### 4. 북마크 경계

- [MainPage.jsx](../frontend/src/pages/MainPage.jsx)
  - 추천 카드: `/api/recommendations/{recommendationId}/bookmark`
  - 일반 정책 카드: `/api/policies/{id}/bookmark`
- [PoliciesPage.jsx](../frontend/src/pages/PoliciesPage.jsx)
  - 목록 카드 북마크 토글
- [PolicyDetailPage.jsx](../frontend/src/pages/PolicyDetailPage.jsx)
  - 상세 북마크 토글
- [MyPage.jsx](../frontend/src/pages/MyPage.jsx)
  - `/api/users/me/bookmarks` 로 최종 북마크 목록 확인

### 5. 마이페이지 경계

- [MyPage.jsx](../frontend/src/pages/MyPage.jsx)
  - 비로그인 시 `/login` 이동
  - 프로필 조회/저장
  - 우선순위 저장
  - 북마크 목록
  - 비밀번호 변경 후 재로그인
  - 회원탈퇴 후 로그아웃/홈 이동

## 현재 QA 기준선

2026-05-01 기준 프론트엔드 정적 기준선은 아래와 같습니다.

- `cd frontend && npm run build` 통과
- `cd frontend && npm run lint` 통과

추가 관찰:

- Vite production build에서 `dist/assets/index-*.js` 가 `500 kB` 경고를 넘습니다.
- 현재 이건 기능 실패가 아니라 성능/번들 분할 후보 신호로 봅니다.

## 현재 리스크

### 1. 브라우저 자동화 부재

- 실제 뒤로가기, 새로고침, 탭 복귀, 세션 만료 타이밍은 자동 회귀로 잡히지 않습니다.
- 따라서 지금은 수동 QA 체크리스트를 기준으로 확인해야 합니다.

### 2. 목록 필터 상태의 URL 비영속성

- `PoliciesPage` 검색/필터/페이지 상태는 대부분 URL이 아니라 컴포넌트 state에 있습니다.
- 상세 진입 후 복귀, 새로고침, 직접 URL 접근에서 기대 상태 유지 여부를 수동 확인해야 합니다.

### 3. 챗봇/추천의 외부 지연

- UI 자체의 라우팅은 빠르지만, recommendation refresh나 chat message 전송은 백엔드/AI 호출 시간 영향을 크게 받습니다.
- 따라서 UX QA에서는 “버튼 클릭 후 반응”, “spinner/disabled 상태”, “중복 클릭 방지” 도 함께 봐야 합니다.

## 관련 문서

- [frontend-qa-checklist.md](./frontend-qa-checklist.md)
- [frontend-qa-template.md](./frontend-qa-template.md)
- [runtime-api-smoke-commands.md](./runtime-api-smoke-commands.md)
- [recommendation-operation-checklist.md](./recommendation-operation-checklist.md)
- [auth-operation-checklist.md](./auth-operation-checklist.md)
