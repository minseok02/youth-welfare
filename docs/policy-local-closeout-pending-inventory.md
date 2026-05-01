# 로컬 closeout pending inventory

관련 문서:

- [phase-plan.md](./phase-plan.md)
- [policy-next-active-track-priority.md](./policy-next-active-track-priority.md)
- [testing.md](./testing.md)
- [runtime-api-smoke-commands.md](./runtime-api-smoke-commands.md)

## 목적

현재 시점에서

- 로컬에서 바로 끝낼 수 있는 항목
- 외부 source/codebook 응답이 있어야 열리는 항목
- 운영 환경이 있어야 진행되는 항목

을 다시 분리해서,
local-first closeout 기준의 실제 다음 액션을 고정합니다.

## 결론

현재 `phase-plan` 의 미완 항목을 그대로 보면
대부분이 아래 두 부류입니다.

1. external blocked
2. ops-only

즉 **새로 설계/구현할 로컬 pending은 거의 남지 않았고**,
지금 로컬에서 할 수 있는 핵심은
이미 만든 기능/스크립트/경계의 **최종 closeout 검증** 입니다.

## 1. external blocked 항목

아래는 로컬에서 문서만 더 쌓아도 unblock 되지 않습니다.

- `GOV24_SERVICE_FIELD`, `GOV24_USER_TYPE`, `GOV24_BENEFIT_TYPE` 공식 label inventory import/backfill SQL 초안 작성
- `GOV24_SUPPORT_CONDITION` 전체 code inventory 확장 및 `service_facts` backfill 초안 작성
- `YOUTH_MID` stable code mapping SQL 초안 작성

이들은 current source/codebook 응답이 와야만 다시 열 수 있습니다.

## 2. ops-only 항목

아래는 로컬이 아니라 실제 운영 환경이 있어야 의미가 있습니다.

- 운영 서버 Docker Compose 기동
- 운영 DB 계정 생성 및 datasource 전환
- 운영 `.env` / secret store 전환
- HTTPS/Nginx 적용
- 운영 DB migration / 배포 smoke

즉 local-first 원칙상 지금 당장 main track 으로 올리지 않습니다.

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
- `SMOKE_RESET_DB=true` 로 base schema만 다시 띄운 직후에는 추천 대상 데이터가 비어 있어 smoke가 의미 있게 통과하지 않는다.
- 따라서 local closeout에서는 "script가 precondition 부족을 명확히 감지하는가"와 "snapshot이 있는 DB에서 replay가 동작하는가"를 구분해서 본다.

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

## 현재 상태

2026-05-01 현재 로컬 기준선은 다시 복구됐다.

1. auth/session revoke regression: 통과
2. PII split-account local smoke: 통과
3. education replay smoke(rule-only): 통과
4. runtime API smoke: 통과

추가로 `education replay` 복구 과정에서
`service_taxonomies.provision_method_label VARCHAR(100)` 이
온통청년 live payload 기준으로 너무 짧아 canonical sidecar collect를 깨뜨리는 문제를 확인했고,
draft sidecar DDL을 `TEXT` 로 보정한 뒤
known-positive replay가 다시 `A_top10_target=0->1`, `B_top10_target=0->0` 으로 복구되는 것까지 확인했다.

## local closeout 완료 조건

아래를 만족하면
“로컬에서 가능한 것은 끝났다”로 봅니다.

1. 핵심 regression test 통과
2. 핵심 smoke script 통과
3. 수동으로 더 보강할 로컬 수정 포인트가 없음
4. 남은 일은 external blocked 또는 ops-only 뿐임

## 요약

1. 현재 미완 항목 대부분은 external blocked 또는 ops-only 이다.
2. closeout 검증 세트(auth/session, PII cutover, education replay, runtime smoke)는 current 워크트리 기준으로 다시 모두 통과했다.
3. 따라서 지금 남은 일은 blocked source 응답이나 운영 환경이 필요할 때만 다시 열리는 트랙들이다.
