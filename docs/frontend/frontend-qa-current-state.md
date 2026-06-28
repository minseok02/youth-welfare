# Frontend QA Current State

문서군 진입점: [frontend-qa-docs-index.md](./frontend-qa-docs-index.md)

## 목적

이 문서는 현재 프론트엔드 QA를 어떤 방식으로 봐야 하는지, 그리고 브라우저 사용자 흐름 기준으로 어디가 핵심 경계인지 빠르게 확인하기 위한 current-state 문서입니다.

현재 daily operator entrypoint는 `bash deploy/smoke/run-local-frontend-observation-suite.sh` 입니다.
이 wrapper는 lint/build/Playwright smoke를 다시 읽어 `decision_class`, `enabled_smoke_steps`, `suite_duration_ms`, `next_action` 을 compact artifact로 남깁니다.
운영 서버 `deployed-origin` 기본 경계에서는 backend bootstrap으로 fresh e2e user와 검색 fixture를 먼저 준비하고, `@dev-only`, `@admin-required` 케이스를 제외한 browser smoke를 기본 기준선으로 봅니다. admin dashboard smoke는 실제 admin credential이 있을 때만 `RUN_FRONTEND_ADMIN_E2E=true` 로 opt-in 합니다. 이때 `ADMIN_EMAIL`/`ADMIN_PASSWORD` 또는 `E2E_ADMIN_EMAIL`/`E2E_ADMIN_PASSWORD` 가 필요하고, 기본 `admin@example.com` / `password123!` fallback은 `ALLOW_DEFAULT_ADMIN_CREDENTIALS=true` 를 명시한 local-only smoke에서만 허용합니다.
세부 브라우저 흐름을 직접 다시 따라갈 때만 [frontend-qa-checklist.md](./frontend-qa-checklist.md) 로 내려갑니다.

## 현재 결론

- 최신 PR closeout 검증(2026-06-28): `refactor/admin-dashboard-sections` / PR `#365` 에서 admin dashboard page orchestration, 상단 운영 패널, 정책 오류 신고 item 패널을 기능 단위로 분리했습니다. 로컬 기준 `npm run lint`, `npm run test:unit`(`50 pass`), `npm run build`, `git diff --check` 를 통과했습니다. 상세 작업 기록은 [frontend-qa-session-2026-06-28.md](./frontend-qa-session-2026-06-28.md) 를 봅니다.
- 최신 로컬 재검증(2026-06-26): `npm audit --audit-level=moderate`, `npm run lint`, `npm run test:unit`, `npm run build` 통과. wrapper 기준 기본 local-dev artifact `tmp/frontend-observation/20260626T104138Z` 는 `RUN_FRONTEND_ADMIN_E2E=false` 로 `@admin-required` 를 제외하고 lint/build/browser smoke `33 passed`, `playwright_grep_invert=@admin-required`, `decision_class=BASELINE_HEALTHY` 입니다. 별도 local-only admin opt-in artifact `tmp/frontend-observation/20260626T103616Z` 는 `RUN_FRONTEND_ADMIN_E2E=true ALLOW_DEFAULT_ADMIN_CREDENTIALS=true PLAYWRIGHT_GREP='admin dashboard'` 로 admin dashboard smoke `24 passed`, `decision_class=BASELINE_HEALTHY` 입니다.
- 현재 프론트엔드에는 repo-native `Playwright` browser smoke가 있습니다.
- 따라서 프론트 QA는 현재 단계에서 `정적 검증(build/lint) + Playwright smoke + 수동 브라우저 시나리오 검증` 기준으로 봅니다.
- 핵심 검증 축은 아래입니다.
  - 라우팅/뒤로가기/재진입
  - 로그인 필요 경로와 로그인 후 복귀
  - 세션 만료 후 refresh 실패와 로그인 화면 복귀
  - 북마크/마이페이지/추천 흐름의 상태 일관성
  - 로그인 직후 메인의 `가이드 배너 / 추천 보강 CTA`
  - 정책 검색 query / 필터 / 상세 read-model
  - 보호 경로 `/chat` / `/mypage` 복귀
  - reset-password query/hash/invalid token 경계
  - 알림함 / 북마크 재방문 유지
  - 비밀번호 변경 후 재로그인 / account tab 복귀
  - admin dashboard operator flow
  - 추천 / 챗봇 보조 흐름
  - `/guide` / `/support` / `정책 오류 제보` 도움 경로 구분

## 현재 코드 기준 QA 핵심 경계

### 1. 로그인 필요 경로

- `/chat`, `/mypage` 는 [RequireLogin.jsx](../../frontend/src/components/RequireLogin.jsx) 로 보호됩니다.
- 비로그인 접근 시 `/login` 으로 이동하며 `reason=login-required`, `from=원래 위치` 가 `location.state` 로 전달됩니다.
- [LoginPage.jsx](../../frontend/src/pages/LoginPage.jsx) 는 로그인 성공 후 `state.from` 으로 다시 복귀합니다.

### 2. 세션 만료 경로

- [axios.js](../../frontend/src/lib/axios.js) 는 `401` 수신 시 `/api/auth/refresh` 를 1회 시도합니다.
- refresh 실패 시 local token 제거 후 `notifyAuthExpired({ reason: "expired" })` 이벤트를 발행합니다.
- [AuthExpiryHandler.jsx](../../frontend/src/components/AuthExpiryHandler.jsx) 는 이 이벤트를 받아 session을 지우고 `/login` 으로 이동시키며 `reason=expired` 를 남깁니다.
- Playwright dev server에서는 로그인 상태일 때만 `window.__authExpired` 테스트 hook을 노출해 같은 이벤트 경로를 수동 트리거합니다. 운영 build에는 이 hook이 포함되지 않습니다.
- [LoginPage.jsx](../../frontend/src/pages/LoginPage.jsx) 는 이 reason을 감지해 “로그인 상태가 만료되어 다시 로그인해야 합니다.” toast를 띄웁니다.

### 3. 뒤로가기 경계

- [PolicyDetailPage.jsx](../../frontend/src/pages/PolicyDetailPage.jsx) 는 `location.state.from` 과 목록 target fallback을 같이 사용해 `MainPage -> Detail`, `PoliciesPage -> Detail`, `MyPage bookmarks -> Detail` 복귀 문맥을 유지합니다.
- [PoliciesPage.jsx](../../frontend/src/pages/PoliciesPage.jsx) 는 검색어/필터/정렬/페이지를 `searchParams` 와 동기화하고, 같은 페이지 안의 브라우저 back/forward 에서도 다시 local state로 복원합니다.
- [MyPage.jsx](../../frontend/src/pages/MyPage.jsx) 는 활성 탭을 `?tab=` query와 동기화합니다.
- [Header.jsx](../../frontend/src/components/Header.jsx), [FloatingNav.jsx](../../frontend/src/components/FloatingNav.jsx) 는 `chatFrom`, `from` 을 이용해 `/chat?session=...`, `/mypage?tab=...` 복귀 문맥을 재사용합니다.

### 4. 정책 상세 태그 필터링 (PolicyDetailPage)

- `visibleTags`는 `tagValue`가 전부 대문자/숫자/언더스코어로만 이루어진 내부 조건 코드(`COND_AGE_MAX_39`, `COND_INCOME_PCT_LE_100` 등)를 화면에서 제외합니다.
- 정규식 `/^[A-Z0-9_]+$/` 에 매칭되는 값은 렌더링하지 않습니다.
- 중복 태그 값도 `Set`으로 제거합니다.

### 5. 정책 상태 뱃지 (PolicyDetailPage)

- `formatStatusLabel`이 `applyEndDate`도 함께 확인합니다.
- 온통청년 정책은 DB `status=ACTIVE`지만 `applyEndDate`가 지난 경우 뱃지를 "종료"로 표시합니다.
- 수동 확인: 온통청년 출처 정책 중 마감일이 지난 항목이 "진행중"/"종료" 이중 뱃지 없이 "종료"만 표시되는지 확인해야 합니다.

### 6. 분류없음 필터 (PoliciesPage) — 임시

- [PoliciesPage.jsx](../../frontend/src/pages/PoliciesPage.jsx) `CATEGORIES` 목록 맨 아래에 `{ label: "분류없음", value: "기타" }` 항목이 추가되어 있습니다.
- `unified_category = '기타'` 인 미분류 정책을 점검하기 위한 임시 항목입니다.
- 현황 (2026-05-06 기준): 미분류 288건 — BOKJIRO_LOCAL 271건, BOKJIRO_CENTRAL 16건, YOUTH 1건.
  - BOKJIRO_LOCAL 기존 1,020건 중 794건은 `서민금융`(731건), `입양·위탁`(63건) 매핑 추가로 해소됨.
  - 잔여 271건은 원본 API에서 `intrsThemaNmArray` 필드 자체가 없는 케이스로 코드로 해결 불가.
- 전체 미분류 정리 완료 후 제거 예정.

### 7. 북마크 경계

- [MainPage.jsx](../../frontend/src/pages/MainPage.jsx)
  - 추천 카드: `/api/recommendations/{recommendationId}/bookmark`
  - 일반 정책 카드: `/api/policies/{id}/bookmark`
- [PoliciesPage.jsx](../../frontend/src/pages/PoliciesPage.jsx)
  - 목록 카드 북마크 토글
- [PolicyDetailPage.jsx](../../frontend/src/pages/PolicyDetailPage.jsx)
  - 상세 북마크 토글
- [MyPage.jsx](../../frontend/src/pages/MyPage.jsx)
  - `/api/users/me/bookmarks` 로 최종 북마크 목록 확인

### 8. 마이페이지 경계

- [MyPage.jsx](../../frontend/src/pages/MyPage.jsx)
  - 비로그인 시 `/login` 이동
  - 프로필 조회/저장
  - 우선순위 저장
  - 북마크 목록
  - 비밀번호 변경 후 재로그인
  - 회원탈퇴 후 로그아웃/홈 이동

### 9. 도움 경로 구분

- `/guide`
  - 헤더 `이용가이드`
  - 메인 CTA
  - 모바일 하단 `가이드`
  에서 공개 진입 가능합니다.
- `/support`
  - 서비스 사용 문의 전용 공개 페이지입니다.
  - `정책 오류 제보와는 다릅니다` 안내가 있어야 합니다.
- 정책 상세
  - `⚑ 정책 오류 제보` CTA가 보여야 합니다.

즉 현재 help surface는

- `/guide`: 어떻게 쓰는지
- `/support`: 서비스를 쓰다가 막힐 때
- 정책 상세 `오류 제보`: 정책 데이터 자체가 틀렸을 때

로 역할이 나뉘어야 합니다.

### 10. 로그인 직후 메인 보강 흐름

- [LoginPage.jsx](../../frontend/src/pages/LoginPage.jsx)
  - 로그인 성공 후 `postLoginRecommendationNudge`, `postLoginGuideNudge` 를 메인으로 넘깁니다.
- [MainPage.jsx](../../frontend/src/pages/MainPage.jsx)
  - guide nudge가 오면 `처음 시작 가이드` 배너가 보여야 합니다.
  - 우선순위/표준코드 공백 상태에 따라 `추천 품질 우선 개선`, `추천 정확도 보강` CTA가 보여야 합니다.
  - `맞춤 재추천 →` 는
    - 우선순위 공백이면 `/mypage?tab=1`
    - 표준코드 공백이면 `/mypage?tab=0`
    으로 이어져야 합니다.
  - `이용가이드 보기 →` 는 `/guide` 로 이어져야 합니다.

### 11. 정책 검색 / 필터 / 상세 읽기

- `PoliciesPage` 는 `search`, `category`, `region`, `subRegion`, `sort`, `statusFilter` 같은 상태를 URL query와 동기화합니다.
- 상세 진입 뒤 브라우저 back과 상세 `뒤로가기` 는 search query를 유지해야 합니다.
- 데스크톱 필터는 별도 적용 버튼 없이 즉시 URL에 반영돼야 합니다.
- 상세 read-model 핵심 필드는 `요약 정보`, `지원지역`, `소관기관`, `신청기간` 입니다.

### 12. 보호 사용자 핵심 흐름

- 보호 경로 핵심은 `/chat` 과 `/mypage?tab=2` 입니다.
- 비로그인 상태에서 `/chat` 진입 시 `/login` 으로 분기해야 합니다.
- 로그인 후에는 다시 `/chat` 으로 복귀해야 합니다.
- 이후 `/mypage?tab=2` 에서 북마크 상세 왕복 뒤에도 `?tab=2` 가 유지돼야 합니다.
- 이 경계는 보호 경로 복귀와 마이페이지 문맥 복귀가 한 흐름에서 동시에 유지되는지 보는 기준선입니다.

### 13. 세션 복구 / 비밀번호 재설정

- 세션 만료는 `window.__authExpired` 또는 보호 API `401 -> refresh 401` 경로로 재현됩니다.
- 두 경우 모두 `/login` 으로 이동하고, 재로그인 후 `/chat` 으로 복귀해야 합니다.
- `/reset-password?token=...` 는 hash token URL로 정규화된 뒤 새 비밀번호 설정이 가능해야 합니다.
- `/reset-password#token=...` deep link도 그대로 새 비밀번호 설정이 가능해야 합니다.
- invalid token은 만료 안내만 보여주고, 로그인으로 강제 이동시키지 않아야 합니다.

### 14. 알림 / 북마크 유지 흐름

- [AlertsPage.jsx](../../frontend/src/pages/AlertsPage.jsx)
  - 빈 상태에서는 `정책 보러가기`, `알림 설정 열기` 가 보여야 합니다.
- `알림 설정 열기` 는 `/mypage?tab=3` 으로 이어져야 합니다.
- [MyPage.jsx](../../frontend/src/pages/MyPage.jsx)
  - `/mypage?tab=2` 에서 북마크 목록이 다시 보여야 합니다.
- 즉 alerts -> mypage settings -> bookmark revisit 가 한 개인 유지 동선 안에서 끊기지 않아야 합니다.

### 15. 계정 라이프사이클

- [SignupPage.jsx](../../frontend/src/pages/SignupPage.jsx)
  - 회원가입 완료 후 `signup-complete` reason 으로 로그인 화면 안내를 띄웁니다.
- [LoginPage.jsx](../../frontend/src/pages/LoginPage.jsx)
  - `password-reset-complete`, `signup-complete` reason 을 받아 적절한 안내를 보여줍니다.
- [MyPage.jsx](../../frontend/src/pages/MyPage.jsx)
  - 비밀번호 변경 성공 후 `/login` 으로 이동시키고, 재로그인 후 `?tab=5` 로 복귀해야 합니다.
- 즉 계정 생성 후 비밀번호 변경/재설정이 로그인 복귀와 섞여도 흐름이 끊기지 않아야 합니다.

### 16. admin operator flow

- 일반 사용자는 `/admin/dashboard` 접근 시 홈으로 돌려보내야 합니다.
- admin 사용자는 dashboard 진입 후 recommendation overview, collect/search triage, attention queue를 봐야 합니다.
- queue 축은
  - 정책 오류 제보
  - 서비스 문의
  - 정책 중복 review
  - 정책 링크 review
  - stale notification target
  로 나뉘며, 각각 `OPEN / REVIEWED / ALL` 필터와 recent metric을 유지해야 합니다.
- summary/breakdown 한 섹션 실패가 페이지 전체 blank로 번지면 안 됩니다.

### 17. 추천 / 챗봇 보조 흐름

- 추천 보강은 로그인 직후 메인에서 시작됩니다.
- 우선순위/표준코드 공백 상태에 따라 `맞춤 재추천` CTA가 적절한 `mypage` 탭으로 가야 합니다.
- `/chat` 은 탐색 보조 경로라서 비로그인 분기, 세션 만료 복귀, refresh 실패 복귀까지 같이 봐야 합니다.
- 즉 recommendation assist 와 chatbot assist 는 별개가 아니라 “사용자 탐색 보조” 경계로 함께 보는 게 현재 기준입니다.

## 현재 QA 기준선

2026-05-29 기준 프론트엔드 QA 기준선은 아래와 같습니다.

- `cd frontend && npm run build` 통과
- `cd frontend && npm run lint` 통과
- `cd frontend && npm run test:e2e` 통과

현재 기본 Playwright smoke 범위:

- `/chat` 비로그인 접근 -> `/login` -> 로그인 후 원 경로 복귀
- 로그인된 `/chat` 에서 `authExpired` callback 실행 -> `/login` -> 재로그인 후 `/chat` 복귀
- `/chat` 에서 보호 API `401` + `/api/auth/refresh 401` -> `/login` -> 재로그인 후 `/chat` 복귀
- `/policies?search=...` -> 상세 -> 브라우저 back / 상세 뒤로가기에서 query 유지
- 홈 `이용가이드` -> `/guide` -> `서비스 문의` -> `/support` -> `이용가이드 보기` 복귀
- `/mypage?tab=2` 로그인 후 bookmark 목록 -> 상세 -> 뒤로가기에서 `?tab=2` 유지
- `/mypage?tab=5` 비밀번호 변경 성공 -> 강제 로그아웃 -> `/login` -> 재로그인 후 account tab 복귀
- 비로그인 정책 상세 bookmark 클릭 -> `/login` -> 로그인 후 bookmark `POST` 정확히 1회 + 최종 bookmarked 상태 반영
- `/reset-password?token=...` query token 진입 -> hash 정규화 -> 새 비밀번호 설정 -> 새 비밀번호 로그인 성공
- `/reset-password#token=...` hash 딥링크 진입 -> 새 비밀번호 설정 -> 새 비밀번호 로그인 성공
- `/reset-password#token=...` invalid token 제출 -> `A008` 만료 안내 노출 + reset-password 화면 유지
- 일반 사용자 `/admin/dashboard` 접근 차단
- `/support` 는 `정책 오류 제보와는 다릅니다` 안내를 노출
- 정책 상세는 `⚑ 정책 오류 제보` CTA 유지
- 로그인 직후 메인은 `처음 시작 가이드` 와 추천 보강 CTA를 동시에 유지

opt-in admin Playwright smoke 범위:

- 관리자 `/admin/dashboard` recommendation overview + triage 섹션 렌더
- admin dashboard quick jump -> recommendation breakdown 섹션 이동
- admin dashboard `summary` 강제 실패 -> `수집 실패 상세`, `검색 실패 상세` 유지
- admin dashboard `recommendation-breakdowns` 강제 실패 -> 상단 recommendation hero 유지 + 해당 섹션만 error card 전환

같은 날짜의 production build/preview Chromium 수동 검증 기준선:

- `/chat?session=...` 비로그인 접근 -> `/login` -> 로그인 후 원 세션 복귀
- `/mypage?tab=2` 비로그인 접근 -> `/login` -> 로그인 후 원 탭 복귀
- `/policies?search=청년&sourceType=GOV24&sort=latest` -> 상세 -> 브라우저 back 후 query 유지
- 비로그인 목록/상세 북마크 -> 로그인 후 bookmark `POST` 정확히 1회, 최종 bookmarks 반영
- Header/FloatingNav의 `/chat`, `/mypage`, `/policies` 이동 시 `state.from` / `chatFrom` 문맥 유지
- 세션 만료는 URL query가 아니라 router `state.reason=expired` 기준
- `MyPage` 알림함/북마크에서 정책 상세 진입 후 뒤로가기 시 `?tab=` 와 `from.state` 문맥 유지

2026-05-17 추가 manual QA 기준선:

- `<local admin email>` 로 `/admin/dashboard` 진입 시 recommendation hero, `수집 실패 상세`, `검색 실패 상세` 가 함께 렌더링됨
- 일반 사용자로 `/admin/dashboard` 접근 시 `/` redirect + `운영 대시보드는 관리자 계정만 접근할 수 있습니다.` toast 표시
- admin dashboard의 `요약 기간` 전환에서 `14 -> 7`, `7 -> 30` 은 `summary`, `recommendation-breakdowns`, `collect-failures`, `search-failures` 네 API 재호출 확인
- `30 -> 14` 전환은 `React Query staleTime=30000` 범위라 cache reuse 가능 동작으로 본다
- `summary` 강제 `500` 에서도 `수집 실패 상세`, `검색 실패 상세` 는 계속 렌더링되고, `recommendation-breakdowns` 강제 `500` 에서도 상단 recommendation summary/hero는 유지됨
- `2e3b9fa` 기준 mobile viewport 재검증에서도 헤더 브랜드 텍스트, 상단 버튼 wrap, 긴 gate/status 문자열 wrapping, 실패 카드 가독성이 모두 정상
- 같은 mobile QA에서 자동 overflow 검사 기준 화면 폭 밖으로 삐져나간 요소 없음

추가 관찰:

- 현재 정적 기준선에서는 route-level lazy loading + vendor chunk split 이후 기존 번들 크기 경고가 재현되지 않습니다.
- 따라서 현재 프론트 QA에서 더 중요한 건 번들 warning 숫자보다 브라우저 복귀/query/state 계약이 실제로 유지되는지입니다.

## 현재 리스크

### 1. 브라우저 smoke 범위의 한계

- 핵심 라우팅/query/tab 복귀, 실제 `401 -> refresh 실패 -> /login` 세션 만료 경로, 비밀번호 변경 후 재로그인 복귀, bookmark post-login 1회 실행, admin 부분 실패 분리는 Playwright smoke로 회귀 감시가 가능해졌습니다.
- 다만 아직 실제 메일 앱에서 링크를 클릭해 브라우저를 여는 외부 메일 클라이언트 환경 자체는 자동화하지 않았습니다.
- 따라서 지금도 QA 체크리스트와 QA template의 URL/query/state 증거 기록은 유지해야 합니다.

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
