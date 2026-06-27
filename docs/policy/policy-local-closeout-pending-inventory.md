# 로컬 closeout pending inventory

문서군 진입점: [policy-docs-index.md](./policy-docs-index.md)

관련 문서:

- [phase-plan.md](../phase-plan.md)
- [policy-next-active-track-priority.md](./policy-next-active-track-priority.md)
- [policy-gov24-blocked-track-status.md](./policy-gov24-blocked-track-status.md)
- [testing.md](../core/testing.md)
- [runtime-api-smoke-commands.md](../core/runtime-api-smoke-commands.md)

## 목적

현재 시점에서

- 로컬에서 바로 끝낼 수 있는 항목
- 외부 source/codebook 응답이 있어야 열리는 항목
- active main track으로 올릴 필요는 없는 deferred infra/server 항목

을 다시 분리해서,
local-first closeout 기준의 실제 다음 액션을 고정합니다.

## 결론

현재 `phase-plan` 의 미완 항목을 그대로 보면
대부분이 아래 두 부류입니다.

1. external blocked
2. future infra/deploy memo

즉 **새로 설계/구현할 로컬 pending은 거의 남지 않았고**,
지금 로컬에서 할 수 있는 핵심은
이미 만든 기능/스크립트/경계의 **최종 closeout 검증** 입니다.

`Gov24` 도 같은 분류로 정리됩니다.

- runtime collect closeout: 완료
- runtime audit / unmapped inventory: 완료
- `supportConditions` full-scope runtime fact gap: 완료
- internal taxonomy code seed/backfill: 완료
- 외부 공식 codebook 기반 hard taxonomy/import-backfill: external blocked

## 1. external blocked 항목

아래는 로컬에서 문서만 더 쌓아도 unblock 되지 않습니다.

- 외부 공식 codebook 기반 `GOV24_SERVICE_FIELD`, `GOV24_USER_TYPE`, `GOV24_BENEFIT_TYPE` 재수입
- raw 조합값 전체를 hard eligibility fact로 쓰는 `service_facts` backfill 초안 작성
- `YOUTH_MID` stable code mapping SQL 초안 작성

다만 `serviceField/userType/benefitType` 는 raw inventory와 internal code/backfill이 이미 있다.
지금 막힌 것은 “값을 전혀 모른다”가 아니라 외부 공식 codebook을 source-of-truth로 다시 받을지 여부다.

즉 이들은 아래 중 하나가 있어야 다시 연다.

- stable code/schema source-of-truth 확보
- 또는 raw 조합값 전체를 hard eligibility fact로 쓰는 제품 요구 승인

## 2. future infra/deploy memo

아래는 서버가 없어서가 아니라, 현재 bounded smoke와 drift 체크를 넘는 추가 운영 절차라서 지금 active main track으로 올리지 않는 항목입니다.

- 서버 기동 절차
- DB 계정 생성 및 datasource 전환
- `.env` / secret store 전환
- HTTPS/Nginx 적용
- migration / 배포 smoke

즉 local-first 원칙상 지금 당장 main track 으로 올리지 않고,
실제 서버가 생길 때 다시 정의합니다.

## 3. 지금 로컬에서 바로 할 수 있는 것

현재 남은 로컬 actionable work는
“새 기능 설계”가 아니라
기존 구현의 closeout 검증입니다.

### A. auth/session revoke regression

대상:

- logout exact-token revoke
- withdraw access/refresh revoke
- admin forced logout
- legacy admin token `A006`

기준:

- 단위 테스트
- integration test
- 필요 시 local runtime smoke

### B. PII split-account / sync local smoke

대상:

- reduced-grant local compose
- request dual-write / sync queue / replay
- withdraw cleanup / admin replay 경계

기준:

- `deploy/smoke/run-local-pii-sync-cutover-smoke.sh`
- 관련 integration tests

현재 주의:

- `run-local-pii-sync-cutover-smoke.sh` 는 이제 로컬 `.env` 의 password/URL 값은 읽되, smoke 기본 split-account username(`app_core_rw`, `migration_admin`, `app_pii_rw`, `notification_pii_ro`) 은 그대로 유지합니다.
- 즉 `.env` 가 아직 `DB_USERNAME=root` 여도 smoke 자체는 split-account 경계로 기동/검증되도록 맞춰진 상태입니다.

### C. recommendation / replay local smoke

대상:

- `education` priority experiment rule-only replay
- canonical read-model bridge regression
- replay trace / summary artifact 경계

기준:

- `deploy/smoke/run-local-education-priority-replay.sh`
- 관련 repository/service/integration tests

현재 주의:

- 이 smoke는 local policy snapshot과 canonical read-model schema(`service_taxonomies`)가 적재된 DB를 전제로 한다.
- `SMOKE_RESET_DB=true` 직후 replay script는 [apply-local-policy-sidecar-draft.sh](../../deploy/mysql/apply-local-policy-sidecar-draft.sh) 를 먼저 호출해 현재 PostgreSQL integrated schema 존재 여부를 preflight로 확인한다.
- 이 스크립트는 이제 예전 MySQL draft SQL을 다시 auto-apply 하는 경로가 아니다. schema가 비어 있으면 PostgreSQL bootstrap/collect flow 또는 snapshot restore가 필요하다고 명시적으로 실패한다.
- 즉 replay smoke는 integrated schema와 local policy snapshot이 이미 채워져 있다는 전제 위에서만 self-heal 되고, fresh reset 뒤 `welfare_services` snapshot 자체가 비어 있으면 collect 또는 snapshot restore는 여전히 선행되어야 한다.

### D. admin/runtime local smoke

대상:

- login / refresh / bookmarks / recommendation refresh
- forced logout admin API
- 필요 시 admin collect/status 계열

기준:

- local boot 후 curl smoke 또는 integration test

## practical next action

현재 local-first 기준의 다음 액션은
새 문서/새 설계가 아니라
아래 closeout 검증 세트를 실제로 돌리는 것입니다.

1. auth/session revoke regression suite
2. local PII cutover smoke
3. education replay smoke(rule-only)
4. 필요 시 local runtime API smoke

`Gov24` 기준으로는 위 closeout 검증 세트 밖에 남은 active 작업이 없고,
남은 건 `deferred` 또는 `external blocked` 로 분리된 상태입니다.

## 현재 상태

2026-05-15 현재 로컬 기준선은 다시 닫힌 상태다.

1. auth/session revoke regression: 통과
2. PII split-account local smoke: 통과
3. education replay smoke(rule-only): 통과
4. runtime API smoke(signup -> login -> refresh -> recommendations -> logout -> refresh invalidation / presented access revoke): 통과
5. broad backend regression (`./gradlew test integrationTest --no-daemon`): 통과
6. personal refresh-cache regression validation(`collect/replay/broad-suite`): 통과
7. `Gov24` runtime collect / runtime audit closeout: 통과

추가로 fresh reset 뒤 local canonical read-model schema가 비어 있어 replay가 곧바로 막히던 공백은
[deploy/mysql/apply-local-policy-sidecar-draft.sh](../../deploy/mysql/apply-local-policy-sidecar-draft.sh)
와 replay script preflight 경계로 로컬 smoke 수준에서는 더 일찍 감지되도록 보강했다.
이 helper는 현재 PostgreSQL integrated schema 존재 여부와 replay precondition을 확인하는 보조 경계로 읽고,
예전 MySQL draft SQL을 다시 auto-apply 하는 경로로 해석하지 않는다.

현재 broad-suite 기준 replay baseline은

- `A_top10_target=9->9`
- `B_top10_target=1->1`
- `A_fp=same`
- `B_fp=same`
- `reason_changed=0`

이다.

즉 이 문서에서 replay를 현재 closeout 완료로 판단하는 기준은 예전 `known-positive replay 복구(0->1 / 0->0)` 메모가 아니라,
최신 local broad-suite 재검증에서 target visibility regression이 없고 fingerprint/reason membership도 안정적이라는 점이다.

## local closeout 완료 조건

아래를 만족하면
“로컬에서 가능한 것은 끝났다”로 봅니다.

1. 핵심 regression test 통과
2. 핵심 smoke script 통과
3. broad backend suite에서도 hidden regression이 없음
4. 남은 일은 external blocked 또는 future infra memo 뿐임

## 요약

1. 현재 미완 항목 대부분은 external blocked 또는 future infra memo 이다.
2. closeout 검증 세트(auth/session, PII cutover, education replay, runtime smoke, broad backend suite)는 current 워크트리 기준으로 다시 모두 통과했다.
3. 따라서 지금 남은 일은 blocked source 응답, deferred Gov24 business-code 판단 재개, 또는 운영 환경이 생겼을 때만 다시 열리는 트랙들이다.
