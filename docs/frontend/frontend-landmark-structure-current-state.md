# Frontend Landmark Structure Current State

문서군 진입점: [README.md](./README.md)

## 목적

이 문서는 프론트엔드가 페이지 구조를 보조기술(스크린리더 등)에 어떻게 전달하는지 —
즉 시맨틱 랜드마크(`<main>`)를 어디서 어떻게 관리하는지 빠르게 확인하기 위한 current-state 문서입니다.

> 범위: **구조(정보 전달) 규칙만** 다룹니다. 색상·간격 같은 시각 디자인 판단은 이 문서 대상이 아닙니다.

## 배경

`<main>` 랜드마크는 스크린리더 사용자가 반복되는 헤더/내비게이션을 건너뛰고
**본문으로 바로 이동**하는 기준점입니다. 기존에는 이 랜드마크가 페이지마다 제각각이었습니다.

- 일부 페이지만 `<main>` 을 직접 두고(GuidePage, SupportPage, TermsPage, PrivacyPolicyPage, PolicyDetailPage, NotFoundPage)
- 나머지 페이지(MainPage, PoliciesPage, MyPage, AlertsPage, LoginPage, SignupPage 등)에는 `<main>` 이 없었음

→ 페이지마다 본문 랜드마크 유무가 달라 보조기술 탐색 기준이 일관되지 않았습니다.

## 현재 결론

- 본문 랜드마크는 **레이아웃 한 곳(`NavLayout`)에서 단일 `<main>` 으로 중앙 관리**합니다.
- 모든 라우트가 `NavLayout` 을 거치므로, 전 페이지가 **정확히 하나의 `<main>`** 을 갖습니다.
- 페이지가 개별적으로 `<main>` 을 두지 않습니다(중첩 landmark 방지).

## 구현 내용

### 1. 단일 `<main>` 중앙화 — [NavLayout.jsx](../../frontend/src/components/NavLayout.jsx)

- 페이지 본문(`children`)을 `<main>` 으로 감쌈
- 전역 알림 배너(`ServerErrorBanner`)와 하단 내비(`MobileBottomNav`)는 `<main>` 바깥에 유지 (본문이 아니므로)

### 2. 페이지 개별 `<main>` 제거 — `<div>` 로 전환

아래 6개 페이지는 자체 `<main>` 을 두고 있어 중앙 `<main>` 과 중첩되므로 `<div>` 로 변경(스타일 유지):

- [GuidePage.jsx](../../frontend/src/pages/GuidePage.jsx), [SupportPage.jsx](../../frontend/src/pages/SupportPage.jsx),
  [TermsPage.jsx](../../frontend/src/pages/TermsPage.jsx), [PrivacyPolicyPage.jsx](../../frontend/src/pages/PrivacyPolicyPage.jsx),
  [PolicyDetailPage.jsx](../../frontend/src/pages/PolicyDetailPage.jsx), [NotFoundPage.jsx](../../frontend/src/pages/NotFoundPage.jsx)

### 3. 적용 경계 — [router/index.jsx](../../frontend/src/router/index.jsx)

- `NavLayout` 을 거치는 모든 라우트가 자동으로 단일 `<main>` 을 가짐
- 예외: `/notifications/unsubscribe` 는 `NavLayout` 을 거치지 않는 일회용 토큰 페이지(색인 제외 대상)라 본문 랜드마크 없음

## 남은 과제 (후속)

- 보조 랜드마크 보강 검토: 상단 내비를 `<nav>`, 하단 탭을 `<nav aria-label>` 로 명시
- 페이지별 `<h1>` 단일성/heading 순서 점검
