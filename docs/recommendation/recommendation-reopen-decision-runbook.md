# recommendation reopen decision runbook

문서군 진입점: [recommendation-docs-index.md](./recommendation-docs-index.md)

## 목적

이 문서는 recommendation 트랙이

- 새 버그 수정으로 다시 열리는지
- local 청년 정책 노출 강화 같은 제품/모델링 과제로 다시 열리는지
- 아니면 아직 그대로 deferred 로 두는 편이 맞는지

를 고르는 decision runbook 입니다.

이 문서는 weight tuning 문서가 아닙니다.
핵심은 **무엇을 먼저 다시 열어야 하는지** 를 정하는 것입니다.

같이 보면 좋은 문서:

- [recommendation-real-user-recheck-checklist.md](./recommendation-real-user-recheck-checklist.md)
- [recommendation-pr-review-brief.md](./recommendation-pr-review-brief.md)
- [recommendation-pr-draft-exit-checklist.md](./recommendation-pr-draft-exit-checklist.md)
- [recommendation-post-merge-followup-checklist.md](./recommendation-post-merge-followup-checklist.md)
- [recommendation-real-user-baseline-runbook.md](./recommendation-real-user-baseline-runbook.md)

현재 기본 해석은 아래와 같습니다.

- `REAL_USER` readiness gate가 deferred면 이 문서를 바로 쓰지 않습니다.
- readiness는 열렸지만 full latest batch review gate가 historical example inertia에 묶여 있으면, current decision은 `USE_RECENT_WINDOW_AS_SUPPLEMENTAL_REVIEW_CONTEXT` 로 읽는 편이 맞습니다.
- current review gate interpretation class는 `HISTORICAL_PRIMARY_BLOCKER_CURRENT_WINDOW_CLEAR` 입니다.
- current review gate operating mode는 `PRIMARY_BASELINE_WITH_SUPPLEMENTAL_RECENT_WINDOW` 입니다.
- current review gate policy candidate status는 `RECENT_WINDOW_POLICY_CANDIDATE` 입니다.
- current review gate policy promotion status는 `REQUIRES_EXPLICIT_POLICY_CHANGE_REVIEW` 입니다.
- reopen 판단은 gate 확인 뒤에만 들어옵니다.

## 언제 이 문서를 쓰나

아래 중 하나가 생기면 이 문서를 봅니다.

1. 운영 `REAL_USER` gate 가 열렸고 recommendation 을 다시 손볼지 고민할 때
2. `2736` 류 local 청년 정책 노출이 약하다는 제품 요구가 생겼을 때
3. admin facet / concentration / readiness 결과를 보고 다음 recommendation 과제를 고를 때

실무에서는 이 문서로 바로 들어오기보다 먼저 아래 precheck를 태우는 편이 맞습니다.

```bash
APP_BASE_URL='http://127.0.0.1:8082' \
ADMIN_EMAIL='<local admin email>' \
ADMIN_PASSWORD='<local admin password>' \
bash deploy/smoke/run-local-recommendation-reopen-precheck.sh
```

이 wrapper가 `READY_FOR_REOPEN_DECISION` 이나 `SUPPLEMENTAL_REVIEW_ONLY` 를 반환할 때만
이 decision runbook 으로 들어오는 편이 맞습니다.

반대로 아래면 아직 이 문서를 쓸 단계가 아닙니다.

- 새 rank/cache/diagnostics mismatch 같은 재현 가능한 버그가 먼저 보일 때
- `REAL_USER` gate 가 아직 닫혀 있을 때

이 경우는 decision runbook 보다 bugfix 또는 readiness runbook 을 먼저 봅니다.

## 먼저 확인할 것

reopen 판단 전 최소한 아래 증거는 같이 봅니다.

1. [recommendation-real-user-baseline-runbook.md](./recommendation-real-user-baseline-runbook.md)
2. [recommendation-concentration-audit-runbook.md](./recommendation-concentration-audit-runbook.md)
3. admin dashboard `recommendation summary / breakdowns`
4. 필요하면 개별 정책군 audit
   - `run-local-no-priority-top1-sample.sh`
   - `run-local-no-priority-candidate-audit.sh`
   - `run-local-gov24-top2-competitor-audit.sh`
   - `run-local-gov24-fresh-upstream-audit.sh`
5. AI exclusion을 완화할지 고민하면
   - [recommendation-primary-audience-exclusion-decision-memo.md](./recommendation-primary-audience-exclusion-decision-memo.md)

특히 아래 둘은 분리해서 읽습니다.

- `gate 가 열렸는가`
- `열렸다면 무엇을 다시 열어야 하는가`

첫 번째는 readiness 문제이고, 두 번째가 이 문서의 범위입니다.

## 먼저 금지하는 것

아래는 recommendation reopen 의 기본 금지선입니다.

1. `REAL_USER` gate 가 닫힌 상태에서 global weight tuning
2. `2736` 한두 개 사례만 보고 source 전체를 직접 올리는 patch
3. admin-only 신호를 바로 public filter/scoring 으로 승격
4. family-level 증거 없이 `YOUTH`, `BOKJIRO_LOCAL`, `GOV24` 전체 source 일반론으로 가는 튜닝

즉 reopen 은 **증거 -> 과제 종류 선택 -> 최소 범위 실험** 순서로 갑니다.

## reopen 선택 사다리

권장 순서는 아래입니다.

### lane 0. 그대로 유지

아래면 recommendation 코드는 다시 열지 않습니다.

- `dashboard_real_user_gate` 가 아직 deferred
- 새 재현 버그가 없음
- local 청년 정책 노출 강화가 명시 목표로 승인되지 않음

이 경우 액션은:

- readiness baseline 유지
- admin facet / concentration 추이만 관찰
- 제품 요구가 생길 때까지 deferred 유지

### lane 1. local 신호 구조화

가장 먼저 고려할 reopen 입니다.

아래 상황이면 이 lane 이 맞습니다.

- 특정 local 청년 정책군이 retrieval 에는 들어오는데 final rank 에서 계속 약함
- `2736` 처럼 지역/청년 신호는 있으나 direct `interest/theme/benefit` 신호가 약함
- 문제를 source 전체가 아니라 **개별 정책군** 으로 설명할 수 있음

이 lane 의 예:

- local 청년 정책의 `interest/theme` 구조화 강화
- 지역 적합성 신호 강화
- direct benefit / program type 신호 보강
- 입력 구조 재정리

이 lane 에서 아직 안 하는 것:

- global weight patch
- source 전체 가산점
- AI prompt 대규모 재설계

### lane 2. diversity / balancing review

아래 상황이면 이 lane 을 고려합니다.

- `REAL_USER` gate 는 열렸음
- `READY_CONCENTRATED_TOP1_REVIEW` 또는 유사 상태가 계속 남음
- 특정 정책군이 아니라 top1 반복, fallback 무반응, source/category concentration 이 중심 문제임

이 lane 의 예:

- fallback / diversity 해석 보강
- source/category balancing 검토
- no-priority 세그먼트 분산 검토

이 lane 에서도 원칙은 같습니다.

- 개별 정책군 증거 없이 source 전체 가산점으로 바로 가지 않음
- latest batch concentration 과 top competitor 비교를 같이 남김

### lane 3. direct ranking / weight / prompt tuning

가장 마지막 reopen 입니다.

아래를 만족할 때만 고려합니다.

1. `REAL_USER` gate 가 열림
2. lane 1 또는 lane 2 로 설명 가능한 더 좁은 과제가 이미 검토됨
3. 그럼에도 global ranking/weight 조정이 필요하다는 증거가 남음

이 lane 의 예:

- priority weight 조정
- rule/AI blend 조정
- prompt/input ordering 조정

이 경우에도 바로 운영에 넣지 않고:

- bounded replay
- 비교 템플릿 기록
- success/fail 조건

을 먼저 고정합니다.

## primary audience exclusion 예외

현재 fresh runtime evidence 기준 `INCOME_MISMATCH`, `STUDENT_AUDIENCE_MISMATCH` 는 direct tuning으로 완화하기보다 **product exclusion 유지** 쪽이 기본값입니다.

즉 `savedAi=0` 이라고 해서 모두 tuning 후보가 되는 것은 아닙니다.

반대로 `LOW_DIRECT_HELP` 같은 bucket이 반복되면 그때는 완화 후보가 될 수 있습니다.

## 증상별 권장 lane

| 관측 | 먼저 볼 것 | 권장 lane |
|---|---|---|
| `REAL_USER` gate 가 닫혀 있음 | readiness runbook | reopen 금지 |
| 새 rank/cache/diagnostics mismatch | bug reproduction | bugfix |
| 특정 local 정책군이 retrieval 안에는 있는데 rank 가 약함 | candidate/top competitor/upstream audit | lane 1 |
| top1 반복, fallback 무반응, 분산 약함 | concentration + breakdowns | lane 2 |
| 이미 좁은 설명으로 안 풀리고 global blend 자체가 의심됨 | replay template + bounded experiment | lane 3 |

## `2736` 류 사례를 읽는 법

`2736` 같은 사례는 source 일반론보다 **정책군 사례** 로 읽는 편이 맞습니다.

즉 질문은 이렇게 바꿉니다.

- "`BOKJIRO_LOCAL` 을 올릴까?" 가 아니라
- "`인천 지역 청년 일자리/생활지원 정책군` 의 direct signal 을 더 구조화할까?"

이렇게 읽어야 reopen 이 lane 1 에 머물고,
바로 lane 3 global tuning 으로 과하게 점프하지 않습니다.

## reopen 승인 전 남길 최소 기록

1. 실행 시각
2. 기준 window / cohort
3. `dashboard_real_user_gate`
4. `dashboard_review_gate`
5. 관련 concentration 또는 facet 증거
6. 어떤 lane 으로 다시 여는지
7. 왜 더 좁은 lane 으로 안 되는지
8. 이번 단계에서 **안 건드리는 것**

## 요약

1. recommendation reopen 은 gate 확인과 과제 선택을 분리해서 읽습니다.
2. 기본 순서는 `유지 -> local 신호 구조화 -> diversity/balancing -> direct tuning` 입니다.
3. `2736` 류 사례는 source 전체가 아니라 정책군 사례로 읽습니다.
4. direct weight tuning 은 마지막 lane 입니다.
5. local current truth에서는 readiness는 열렸지만 full latest batch review gate가 stale historical example inertia에 묶여 있으므로, current 기본값은 `USE_RECENT_WINDOW_AS_SUPPLEMENTAL_REVIEW_CONTEXT` 로 읽는 편이 맞습니다.
6. 즉 reopen 전 product/engineering 결정도 raw gate 값보다 `HISTORICAL_PRIMARY_BLOCKER_CURRENT_WINDOW_CLEAR` / `PRIMARY_BASELINE_WITH_SUPPLEMENTAL_RECENT_WINDOW` 운영 클래스를 먼저 기준으로 읽는 편이 맞습니다.
7. 그리고 recent-window는 이미 `RECENT_WINDOW_POLICY_CANDIDATE` 이지만, 아직 `REQUIRES_EXPLICIT_POLICY_CHANGE_REVIEW` 상태이므로 primary gate를 자동 교체하는 단계는 아닙니다.
