# Frontend QA Session - 2026-06-28

문서군 진입점: [frontend-qa-docs-index.md](./frontend-qa-docs-index.md)

이 문서는 `admin dashboard` 프론트 구조 분리 작업을 오늘 기준 PR closeout 세션으로 남기는 시트입니다.

## 실행 정보

- 날짜: `2026-06-28`
- 시간: `2026-06-28 16:44:19 UTC`
- 환경: `local`
- 브랜치: `refactor/admin-dashboard-sections`
- PR: `https://github.com/minseok02/youth-welfare/pull/365`
- 최종 커밋:
  - `1991e991 split admin policy error report item panels`
  - `fe724676 split admin dashboard top section`
  - `495b2007 refactor admin dashboard into feature sections`

## 작업 범위

### 1. AdminDashboardPage 기능별 분리

- `AdminDashboardPage.jsx` 를 page orchestration 중심으로 줄였습니다.
- API/query/action/filter/jump/derived 계산을 `frontend/src/lib/adminDashboard*.js`, `useAdminDashboard*.js` 로 분리했습니다.
- 대시보드 섹션은 `frontend/src/components/adminDashboard/` 아래 기능 단위 컴포넌트로 분리했습니다.
- section failure 집계와 refresh 상태에 `recommendationRunSummaryQuery`, `notificationAttemptSummaryQuery` 를 포함하도록 보정했습니다.
- local `section-failures` attention item 의 jump target을 `admin-attention-queue` 로 고정했습니다.

### 2. 상단 운영 패널 분리

- `AdminDashboardTopSection.jsx` 는 조립 컴포넌트로 줄였습니다.
- `AdminDashboardTopPanels.jsx` 를 추가해 아래 UI 책임을 나눴습니다.
  - 상단 제목 / 기간 선택 / 전체 새로고침
  - 운영 알림 카드
  - 운영 스냅샷 / quick jump / 부분 실패 alert

### 3. 정책 오류 신고 아이템 분리

- `AdminPolicyErrorReportItem.jsx` 는 개별 item 조립과 노출 조건만 담당하게 줄였습니다.
- `AdminPolicyErrorReportPanels.jsx` 를 추가해 아래 UI 책임을 나눴습니다.
  - 신고 헤더
  - 제보자 메모
  - 처리 완료 상태
  - 지역 보정 패널
  - 필드 보정 패널
  - 운영 메모 / 처리완료 컨트롤

## 검증 결과

아래 검증을 각 후속 분리 단계에서 통과했습니다.

```bash
cd frontend && npm run lint
cd frontend && npm run test:unit
cd frontend && npm run build
git diff --check
```

최종 unit test 결과:

- `tests 50`
- `pass 50`
- `fail 0`

## PR / Git 상태

- PR은 draft 상태로 유지합니다.
- 오늘 작업 커밋은 원격 브랜치 `origin/refactor/admin-dashboard-sections` 에 푸시했습니다.
- 이 문서 정리 커밋까지 푸시한 뒤 워킹트리는 clean 이어야 합니다.

## 다음 액션

오늘 작업은 여기서 closeout 합니다.
다음 세션에서 계속 구조 정리를 한다면 `AdminDashboardUi.jsx`, `useAdminDashboardQueries.js`, `useAdminDashboardActions.js` 순서로 다시 판단합니다.
