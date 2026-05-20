# recommendation PR draft exit checklist

문서군 진입점: [recommendation-docs-index.md](./recommendation-docs-index.md)

## 목적

이 문서는 현재 draft PR인 recommendation / Gov24 closeout 브랜치를

- 언제 계속 draft로 두고
- 언제 reviewer-ready 또는 merge-ready로 올릴지

를 한 장으로 고정합니다.

현재 기준 질문은 이것입니다.

- `REAL_USER` traffic/cohort blocker가 아직 남아 있는가
- active/current/handoff 문서와 observability entrypoint가 닫혔는가
- 지금 PR이 제품 판단 reopen 없이도 review 가능한 closeout 상태인가

## 현재 기본 판정

현재 recommendation closeout PR은 아래 이유로 **draft 유지**가 기본값입니다.

- PR review readiness status:
  - `REVIEWER_READY`
- PR draft maintenance status:
  - `DRAFT_MAINTAINED_BY_POLICY_GATE`

- full latest batch review gate:
  - `DEFERRED_NON_REAL_LEADER_SIGNAL`
- full latest batch reading:
  - historical `EXAMPLE_SMOKE` latest batch inertia
- recent-window supplemental gate:
  - `RECENT_WINDOW_CLEARS_HISTORICAL_2622_DOMINANCE`
- review gate interpretation class:
  - `HISTORICAL_PRIMARY_BLOCKER_CURRENT_WINDOW_CLEAR`
- review gate operating mode:
  - `PRIMARY_BASELINE_WITH_SUPPLEMENTAL_RECENT_WINDOW`
- gate policy status:
  - `PRIMARY_BLOCKED_SUPPLEMENTAL_CLEAR`
- gate policy reason:
  - `HISTORICAL_PRIMARY_BLOCKER_CURRENT_WINDOW_CLEAR`
- current next step:
  - `USE_RECENT_WINDOW_AS_SUPPLEMENTAL_REVIEW_CONTEXT`
- latest 관찰은 계속 `VOLATILE_ONLY_DRIFT`
- 즉 남은 blocker는 코드 결함보다 **review gate 해석을 stale historical latest batch와 current recent-window signal로 분리해서 읽는 문제** 입니다.
- 즉 현재 브랜치는 **review는 바로 받을 수 있지만, undraft는 policy gate가 아직 막는 상태** 입니다.

## draft 유지 조건

아래 중 하나라도 true면 draft를 유지합니다.

1. `REAL_USER` readiness gate가 아직 deferred
2. `latest-overview` / `latest-status` / `latest-gate` / readiness runbook이 active 문서와 어긋남
3. reviewer가 full latest batch gate와 recent-window supplemental gate를 문서만 보고 구분해 읽기 어려움
4. current PR 범위가 closeout보다 새 제품 판단 reopen 쪽으로 번짐

## reviewer-ready 조건

아래가 모두 true면 draft를 풀고 reviewer-ready로 올릴 수 있습니다.

1. active/current/support/handoff 문서가 지금 truth와 맞음
2. recommendation daily one-shot entrypoint가 `latest-overview` 로 고정됨
3. reviewer brief가 범위를 `Gov24 closeout / AI exclusion observability / docs hygiene` 로 설명함
4. `REAL_USER` recheck checklist가 traffic 발생 후 재실행 순서를 고정함
5. 남은 credential-like inventory가 intentional scope로 분류돼 있음
6. 워킹트리와 branch diff-check가 clean
7. full latest batch gate와 recent-window supplemental gate 역할이 문서에 고정돼 있음
8. review gate decision class(`HISTORICAL_PRIMARY_BLOCKER_CURRENT_WINDOW_CLEAR`)와 operating mode(`PRIMARY_BASELINE_WITH_SUPPLEMENTAL_RECENT_WINDOW`)가 reviewer 문서/PR surface에도 같이 고정돼 있음

주의:

- reviewer-ready는 `REAL_USER` gate가 열린 상태를 뜻하지 않습니다.
- 현재처럼 blocker가 `REAL_USER` traffic 부재인 경우에도, “현재 closeout 범위를 검토할 수 있다”는 의미로 reviewer-ready는 가능할 수 있습니다.

## merge-ready 조건

아래 중 하나를 만족해야 merge-ready 판단으로 올립니다.

### 1. closeout PR만 먼저 merge

- reviewer가 현재 closeout 범위를 승인
- `REAL_USER` blocker는 “후속 운영 관찰 과제”로 분리
- current-state / phase-plan / PR 본문에 그 분리가 명시됨
- merge 뒤 follow-up 기준은 [recommendation-post-merge-followup-checklist.md](./recommendation-post-merge-followup-checklist.md) 로 넘김

### 2. `REAL_USER` 확인까지 보고 merge

- `REAL_USER` traffic/cohort가 실제로 생김
- [recommendation-real-user-recheck-checklist.md](./recommendation-real-user-recheck-checklist.md) 순서 재실행 완료
- stable baseline drift 여부까지 다시 확인

## 지금 추천하는 운영 방식

현재는 아래 순서가 맞습니다.

1. PR은 `reviewer-ready but draft-maintained` 로 읽고 draft 유지
2. reviewer는 closeout 범위를 먼저 review
3. `REAL_USER` traffic/cohort가 생기면 recheck checklist 재실행
4. 그 결과로 draft 해제 또는 후속 reopen PR 분리 결정

## draft 해제 전 최소 확인 명령

```bash
bash deploy/smoke/run-local-recommendation-ai-exclusion-latest-overview.sh
```

가능하면 readiness 포함:

```bash
APP_BASE_URL='http://127.0.0.1:8082' \
ADMIN_EMAIL='<local admin email>' \
ADMIN_PASSWORD='<local admin password>' \
INCLUDE_REAL_USER_READINESS=true \
bash deploy/smoke/run-local-recommendation-ai-exclusion-latest-overview.sh
```

그리고 아래를 같이 확인합니다.

- `git diff --check`
- `git status --short`

## 해석 규칙

### 1. basic gate pass + strict gate fail + readiness deferred

- current 기본 해석:
  - draft 유지
- 이유:
  - stable baseline 회귀보다 stale historical latest batch와 current recent-window signal 해석 정리가 main blocker
  - 현재 운영 클래스는 `HISTORICAL_PRIMARY_BLOCKER_CURRENT_WINDOW_CLEAR` 이고, operating mode는 `PRIMARY_BASELINE_WITH_SUPPLEMENTAL_RECENT_WINDOW` 입니다.

### 1a. basic gate pass + gate policy status `PRIMARY_BLOCKED_SUPPLEMENTAL_CLEAR`

- current 기본 해석:
  - draft 유지
- 이유:
  - drift gate는 통과했지만 운영 정책 gate는 아직 primary historical blocker 상태입니다.
  - 즉 `PASS` 는 “artifact drift 없음”이지 “undraft 가능”을 뜻하지 않습니다.
  - 이 조합에서는 `READ_PRIMARY_AND_SUPPLEMENTAL_REVIEW_GATES` 와 `USE_RECENT_WINDOW_AS_SUPPLEMENTAL_REVIEW_CONTEXT` 를 먼저 읽는 편이 맞습니다.
  - 현재 PR status는 계속 `REVIEWER_READY + DRAFT_MAINTAINED_BY_POLICY_GATE` 입니다.

### 2. readiness opened + stable baseline unchanged

- current 기본 해석:
  - review는 가능
  - merge는 추가 관찰 후 결정

### 3. readiness opened + stable baseline changed

- current 기본 해석:
  - closeout PR만으로 닫지 말고 reopen 판단 문서까지 같이 본다

## 한 줄 요약

현재 draft PR은 **코드 미완성 때문이 아니라 primary historical gate와 supplemental current-live gate를 같이 읽는 current 운영 클래스가 아직 남아 있기 때문에 draft 유지**가 기본값이고, reviewer-ready와 merge-ready는 분리해서 판단하는 편이 맞습니다.
