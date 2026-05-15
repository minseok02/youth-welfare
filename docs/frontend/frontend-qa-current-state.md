# Frontend QA Current State

문서군 진입점: [frontend-qa-docs-index.md](./frontend-qa-docs-index.md)

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

- `/chat`, `/mypage` 는 [RequireLogin.jsx](../frontend/src/components/RequireLogin.jsx) 로 보호됩니다.
- 비로그인 접근 시 `/login` 으로 이동하며 `reason=login-required`, `from=원래 위치` 가 `location.state` 로 전달됩니다.
- [LoginPage.jsx](../frontend/src/pages/LoginPage.jsx) 는 로그인 성공 후 `state.from` 으로 다시 복귀합니다.

### 2. 세션 만료 경로

- [axios.js](../frontend/src/lib/axios.js) 는 `401` 수신 시 `/api/auth/refresh` 를 1회 시도합니다.
- refresh 실패 시 local token 제거 후 `window.__authExpired` 를 호출합니다.
- [RequireLogin.jsx](../frontend/src/components/RequireLogin.jsx) 는 이 callback에서 `logout()` 후 `/login` 으로 이동시키며 `reason=expired` 를 남깁니다.
- [LoginPage.jsx](../frontend/src/pages/LoginPage.jsx) 는 이 reason을 감지해 “로그인 상태가 만료되어 다시 로그인해야 합니다.” toast를 띄웁니다.

### 3. 뒤로가기 경계

- [PolicyDetailPage.jsx](../frontend/src/pages/PolicyDetailPage.jsx) 는 `location.state.from` 과 목록 target fallback을 같이 사용해 `MainPage -> Detail`, `PoliciesPage -> Detail`, `MyPage bookmarks -> Detail` 복귀 문맥을 유지합니다.
- [PoliciesPage.jsx](../frontend/src/pages/PoliciesPage.jsx) 는 검색어/필터/정렬/페이지를 `searchParams` 와 동기화하고, 같은 페이지 안의 브라우저 back/forward 에서도 다시 local state로 복원합니다.
- [MyPage.jsx](../frontend/src/pages/MyPage.jsx) 는 활성 탭을 `?tab=` query와 동기화합니다.
- [Header.jsx](../frontend/src/components/Header.jsx), [FloatingNav.jsx](../frontend/src/components/FloatingNav.jsx) 는 `chatFrom`, `from` 을 이용해 `/chat?session=...`, `/mypage?tab=...` 복귀 문맥을 재사용합니다.

### 4. 정책 상세 태그 필터링 (PolicyDetailPage)

- `visibleTags`는 `tagValue`가 전부 대문자/숫자/언더스코어로만 이루어진 내부 조건 코드(`COND_AGE_MAX_39`, `COND_INCOME_PCT_LE_100` 등)를 화면에서 제외합니다.
- 정규식 `/^[A-Z0-9_]+$/` 에 매칭되는 값은 렌더링하지 않습니다.
- 중복 태그 값도 `Set`으로 제거합니다.

### 5. 정책 상태 뱃지 (PolicyDetailPage)

- `formatStatusLabel`이 `applyEndDate`도 함께 확인합니다.
- 온통청년 정책은 DB `status=ACTIVE`지만 `applyEndDate`가 지난 경우 뱃지를 "종료"로 표시합니다.
- 수동 확인: 온통청년 출처 정책 중 마감일이 지난 항목이 "진행중"/"종료" 이중 뱃지 없이 "종료"만 표시되는지 확인해야 합니다.

### 6. 분류없음 필터 (PoliciesPage) — 임시

- [PoliciesPage.jsx](../frontend/src/pages/PoliciesPage.jsx) `CATEGORIES` 목록 맨 아래에 `{ label: "분류없음", value: "기타" }` 항목이 추가되어 있습니다.
- `unified_category = '기타'` 인 미분류 정책을 점검하기 위한 임시 항목입니다.
- 현황 (2026-05-06 기준): 미분류 288건 — BOKJIRO_LOCAL 271건, BOKJIRO_CENTRAL 16건, YOUTH 1건.
  - BOKJIRO_LOCAL 기존 1,020건 중 794건은 `서민금융`(731건), `입양·위탁`(63건) 매핑 추가로 해소됨.
  - 잔여 271건은 원본 API에서 `intrsThemaNmArray` 필드 자체가 없는 케이스로 코드로 해결 불가.
- 전체 미분류 정리 완료 후 제거 예정.

### 7. 북마크 경계

- [MainPage.jsx](../frontend/src/pages/MainPage.jsx)
  - 추천 카드: `/api/recommendations/{recommendationId}/bookmark`
  - 일반 정책 카드: `/api/policies/{id}/bookmark`
- [PoliciesPage.jsx](../frontend/src/pages/PoliciesPage.jsx)
  - 목록 카드 북마크 토글
- [PolicyDetailPage.jsx](../frontend/src/pages/PolicyDetailPage.jsx)
  - 상세 북마크 토글
- [MyPage.jsx](../frontend/src/pages/MyPage.jsx)
  - `/api/users/me/bookmarks` 로 최종 북마크 목록 확인

### 8. 마이페이지 경계

- [MyPage.jsx](../frontend/src/pages/MyPage.jsx)
  - 비로그인 시 `/login` 이동
  - 프로필 조회/저장
  - 우선순위 저장
  - 북마크 목록
  - 비밀번호 변경 후 재로그인
  - 회원탈퇴 후 로그아웃/홈 이동

## 현재 QA 기준선

2026-05-15 기준 프론트엔드 정적 기준선은 아래와 같습니다.

- `cd frontend && npm run build` 통과
- `cd frontend && npm run lint` 통과

같은 날짜의 production build/preview Chromium 수동 검증 기준선:

- `/chat?session=...` 비로그인 접근 -> `/login` -> 로그인 후 원 세션 복귀
- `/mypage?tab=2` 비로그인 접근 -> `/login` -> 로그인 후 원 탭 복귀
- `/policies?search=청년&sourceType=GOV24&sort=latest` -> 상세 -> 브라우저 back 후 query 유지
- 비로그인 목록/상세 북마크 -> 로그인 후 bookmark `POST` 정확히 1회, 최종 bookmarks 반영
- Header/FloatingNav의 `/chat`, `/mypage`, `/policies` 이동 시 `state.from` / `chatFrom` 문맥 유지
- 세션 만료는 URL query가 아니라 router `state.reason=expired` 기준
- `MyPage` 알림함/북마크에서 정책 상세 진입 후 뒤로가기 시 `?tab=` 와 `from.state` 문맥 유지

추가 관찰:

- 현재 정적 기준선에서는 route-level lazy loading + vendor chunk split 이후 기존 번들 크기 경고가 재현되지 않습니다.
- 따라서 현재 프론트 QA에서 더 중요한 건 번들 warning 숫자보다 브라우저 복귀/query/state 계약이 실제로 유지되는지입니다.

## 현재 리스크

### 1. 브라우저 자동화 부재

- 실제 뒤로가기, 새로고침, 탭 복귀, 세션 만료 타이밍은 자동 회귀로 잡히지 않습니다.
- 따라서 지금은 수동 QA 체크리스트와 QA template의 URL/query/state 증거 기록을 기준으로 확인해야 합니다.

### 2. 목록 필터 상태의 URL 비영속성

- `PoliciesPage` 검색/필터/정렬/페이지 상태는 현재 URL query와 동기화됩니다.
- 따라서 상세 진입 후 복귀, 새로고침, 직접 URL 접근에서도 같은 query면 같은 상태가 복원되는 것을 기본 정상으로 봅니다.
- `statusFilter`, `sort`, `page`, `pageSize`, `category`, `region`, `subRegion`, `sourceType`, `income`, `targetGroup`은 URL query에 동기화됩니다.
  단 기본값(statusFilter="신청가능" 등)은 URL에 포함되지 않으므로, 새로고침 후 기본값으로 복귀하는 건 정상입니다.
- 지역 Select 변경 시 sort가 자동 전환됩니다: 지역 선택 → `latest`, 전체 복귀 → `views`.

### 9. 소득분위 필터 + 특화조건 칩 (PoliciesPage)

- **소득분위 필터**: 소득수준 선택 시 `incomeLevel` 파라미터로 전달. 백엔드에서 선택 분위보다 낮은 분위 전용 정책을 WHERE로 제외 (하드 필터).
  - 예: 7분위 선택 → `max_income ≤ 9,285만원`인 정책 숨김 (1~5분위 전용 정책 제외)
  - 적용 기준: YOUTH 데이터 `max_income` 필드 (총 24건에만 실제 값 있음, 3,500~10,000만원 범위)
  - 복지로는 `max_income` 데이터 없음 → 필터 대상 아님 (항상 표시)
  - 1분위 또는 미선택 → null 전달 → 필터 없음 (전체 표시)
- **소득수준 UI**: `1~2분위 (하위 20%)` ~ `9~10분위 (상위 20%)` 5단계, 분위 기준만 표시 (소득금액 병기 제거)
- **특화조건 칩**: 장애인 / 한부모·조손 / 다문화·탈북민 / 보훈대상자 / 다자녀 — `targetGroup` 파라미터로 전달. TARGET_GROUP 태그가 일치하는 정책만 WHERE 필터로 표시 (하드 필터).
  - 복지로 `trgterIndvdlNmArray` 기반 태그 756건에만 적용 (YOUTH는 TARGET_GROUP 태그 없음)
  - 군인은 복지로/YOUTH 모두 구조화 데이터 없어 칩 미제공
- 소득분위·특화조건 모두 기존 카테고리/지역/상태 필터와 AND 결합 (하드 필터)

### 3. 챗봇/추천의 외부 지연

- UI 자체의 라우팅은 빠르지만, recommendation refresh나 chat message 전송은 백엔드/AI 호출 시간 영향을 크게 받습니다.
- 따라서 UX QA에서는 “버튼 클릭 후 반응”, “spinner/disabled 상태”, “중복 클릭 방지” 도 함께 봐야 합니다.

## 관련 문서

- [frontend-qa-checklist.md](./frontend-qa-checklist.md)
- [frontend-qa-template.md](./frontend-qa-template.md)
- [runtime-api-smoke-commands.md](../core/runtime-api-smoke-commands.md)
- [recommendation-operation-checklist.md](../recommendation/recommendation-operation-checklist.md)
- [auth-operation-checklist.md](../auth/auth-operation-checklist.md)
