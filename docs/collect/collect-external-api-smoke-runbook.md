# Collect External API Smoke Runbook

문서군 진입점: [collect-docs-index.md](./collect-docs-index.md)

## 목적

외부 API 수집을 새로 실행하기 전에, 또는 실행 직후에 DB에 남은 수집 관측값을 compact하게 확인합니다.
이 smoke는 외부 API를 직접 호출하지 않고 아래 runtime table만 읽습니다.

- `api_sync_logs`
- `raw_api_payloads`
- `welfare_service_details`
- `collect_runtime_statuses`
- `collect_execution_locks`
- `welfare_services`
- `service_taxonomies`
- `service_taxonomy_terms`
- `service_taxonomy_summary_slots`
- `service_facts`

## 실행

```bash
bash deploy/smoke/run-local-collect-external-api-smoke.sh
```

서버/RDS 기준으로 읽을 때는 다른 smoke와 같이 DB/env를 지정합니다.

```bash
ENV_FILE=.env.production \
SMOKE_DB_MODE=postgres \
bash deploy/smoke/run-local-collect-external-api-smoke.sh
```

앱 컨테이너가 `SPRING_PROFILES_ACTIVE=prod` 이고 `DB_URL` 이 RDS를 가리키는 상태에서 이 값을 빼면, smoke가 로컬 Docker PostgreSQL을 읽어 앱 runtime과 다른 결론을 낼 수 있습니다. 앱 로그/화면의 정책 수와 smoke DB count가 다르면 먼저 DB mode를 맞춥니다.

## 산출물

- summary: `tmp/collect-external-api-smoke/latest-collect-external-api-smoke-summary.txt`
- json: `tmp/collect-external-api-smoke/latest-collect-external-api-smoke.json`
- note: `tmp/collect-external-api-smoke/latest-collect-external-api-smoke-note.md`
- raw detail: `tmp/collect-external-api-smoke/latest/`

## 판정

- `BASELINE_HEALTHY`: 최근 수집 로그, raw payload, circuit, lock이 정상 범주입니다.
- `NO_COLLECT_HISTORY`: 테이블은 있지만 `api_sync_logs`와 `raw_api_payloads`가 비어 있습니다. 새 로컬 DB나 아직 수집을 실행하지 않은 상태입니다.
- `ACTIVE_LOCK_REVIEW`: 아직 active collect lock이 있습니다. 다른 수집을 시작하기 전에 진행 중인지 stale인지 확인합니다.
- `ATTENTION_REQUIRED`: 최근 실패 수집 또는 open circuit이 있습니다.
- `PARTIAL_SUCCESS_REVIEW`: 최근 partial success가 있어 source별 `failed_count`, `error_code`, raw payload freshness를 같이 확인합니다.
- `STORAGE_PARITY_REVIEW`: 최근 source 수집 성공과 `saved_count`가 있는데 같은 source의 `raw_api_payloads` 또는 `welfare_services` row가 없습니다.
- `SIDECAR_PARITY_REVIEW`: 최근 source 수집 성공과 저장 row가 있는데 canonical sidecar가 비어 있거나, Gov24 list raw의 필수 summary slot이 비어 있습니다.
- `DETAIL_SUPPORT_COVERAGE_REVIEW`: 최근 detail/support 수집 성공과 `saved_count`가 있는데 해당 lane의 `DETAIL` raw, detail row, `SUPPORT` raw, 또는 support fact coverage가 비어 있습니다.
- `SOURCE_UNAVAILABLE`: 필수 수집 테이블이 없습니다. runtime schema patch를 먼저 적용합니다.

## 해석 순서

1. `source_available`과 `source_missing`을 먼저 봅니다.
2. `api_sync_log_runs_*d`, `api_sync_failed_runs_*d`, `api_sync_partial_runs_*d`를 봅니다.
3. `raw_payload_total`, `raw_payload_source_type_count`, `raw_payload_api_category_count`로 payload 저장 이력을 봅니다.
4. `storage_mismatch_sources`, `sources_with_api_success_without_raw_payload`, `sources_with_api_success_without_welfare_rows`로 source별 성공/저장 정합성을 봅니다.
5. `sidecar_mismatch_sources`, `sources_with_recent_success_without_taxonomy`, `sources_with_gov24_missing_required_summary_slots`로 저장 이후 canonical sidecar 정합성을 봅니다.
6. `detail_support_coverage_mismatch_lanes`, `lanes_with_recent_success_without_detail_raw`, `lanes_with_recent_success_without_detail_rows`, `lanes_with_recent_success_without_support_raw`, `lanes_with_recent_success_without_support_facts`로 detail/support coverage를 봅니다.
7. `open_collect_circuit_keys`와 `active_collect_lock_keys`를 확인합니다.
8. `decision_class`와 `next_action` 기준으로 다음 smoke나 runbook을 선택합니다.

`NO_COLLECT_HISTORY`는 실패가 아니라 검증 한계입니다. 이 상태에서는 source 품질이나 upstream 안정성을 결론 내리지 않고, 필요한 경우 bounded collect smoke를 별도로 실행합니다.
`STORAGE_PARITY_REVIEW`는 외부 API 재호출보다 DB 저장 경로 확인이 먼저입니다. 같은 source의 `api_sync_logs.saved_count`, `raw_api_payloads.source_type/api_category`, `welfare_services.source_type`을 비교합니다.
`SIDECAR_PARITY_REVIEW`는 정책 row 저장 이후 replay/recommendation으로 넘어가기 전 canonical sidecar를 먼저 맞춰야 하는 상태입니다. `bokjiro-sidecars-backfill`, `gov24-sidecars-backfill?scope=list&missingOnly=true`, `gov24-sidecars-backfill?scope=regions` 같은 bounded replay 경로를 우선 확인합니다.
`DETAIL_SUPPORT_COVERAGE_REVIEW`는 source별 list 저장 자체보다 후속 detail/support lane을 먼저 봐야 하는 상태입니다. Bokjiro는 `BOKJIRO_CENTRAL_DETAIL`/`BOKJIRO_LOCAL_DETAIL`, Gov24는 `GOV24_DETAIL`/`GOV24_SUPPORT`, YOUTH는 `YOUTH_DETAIL`로 분리해 봅니다.

## Bounded Detail 확인

대량 list 수집을 다시 태우지 않고 admin endpoint와 로그 경계만 확인할 때는 detail lane에 `maxCallsPerRun=1`을 둡니다.

```bash
ENV_FILE=.env.production \
SMOKE_DB_MODE=postgres \
ALLOW_ADMIN_JWT_MINT=true \
bash -lc 'source deploy/smoke/smoke-common.sh
smoke_resolve_admin_credentials "$PWD"
smoke_resolve_admin_access_token "$PWD"
response_file="$(mktemp)"
status="$(smoke_http_status POST "http://127.0.0.1:8082/api/admin/collect/gov24-details?maxCallsPerRun=1" "$response_file" -H "Authorization: Bearer ${ADMIN_ACCESS_TOKEN}")"
printf "status=%s\n" "$status"
cat "$response_file"
rm -f "$response_file"'
```

2026-06-25 RDS-backed local app 기준 bounded 확인 결과:

- `POST /api/admin/collect/gov24-details?maxCallsPerRun=1`
- 응답: `200`
- 결과: `requested=1 saved=1 skipped=282 failed=0`
- post-run smoke: `decision_class=BASELINE_HEALTHY`
- latest `api_sync_logs`: `GOV24_DETAIL success requested=1 saved=1 failed=0`
