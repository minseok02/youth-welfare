# recommendation ai exclusion latest overview runbook

문서군 진입점: [recommendation-docs-index.md](./recommendation-docs-index.md)

## 목적

이 문서는 현재 recommendation AI exclusion latest 상태를

- latest export 갱신
- latest status
- latest gate
- strict latest gate
- 필요하면 `REAL_USER` readiness

순서로 한 번에 다시 보는 daily operator entrypoint 입니다.

개별 wrapper를 따로 치기 전에 지금 상태를 한 화면에서 다시 확인하고 싶을 때 씁니다.

## 기본 스크립트

```bash
bash deploy/smoke/run-local-recommendation-ai-exclusion-latest-overview.sh
```

기본값은 stale JSON이면 자동으로 다시 갱신하는 쪽입니다.

```bash
AUTO_REFRESH_STATUS_JSON_IF_STALE=true \
bash deploy/smoke/run-local-recommendation-ai-exclusion-latest-overview.sh
```

기본 `auto` 모드에서는 `APP_BASE_URL` 이 비어 있으면 로컬 기본값 `http://127.0.0.1:8082` 를 사용하고, admin 자격을 발견하면 readiness도 같이 포함하려고 시도합니다.

`REAL_USER` readiness도 같이 묶고 싶으면:

```bash
APP_BASE_URL='http://127.0.0.1:8082' \
ADMIN_EMAIL='<local admin email>' \
ADMIN_PASSWORD='<local admin password>' \
INCLUDE_REAL_USER_READINESS=true \
bash deploy/smoke/run-local-recommendation-ai-exclusion-latest-overview.sh
```

## 주요 출력

- `auto_refresh_status_json_if_stale`
- `[latest status export]`
- `[latest status]`
- `[latest gate]`
- `[latest gate (strict)]`
- `strict_gate_exit_code`
- `overview_artifact_dir`
- `overview_summary`
- `overview_note`
- `overview_json`
- `latest_artifact_link`
- `latest_summary_link`
- `latest_note_link`
- `latest_json_link`
- `[real-user readiness]`

## 읽는 법

- `latest status` 에서 `operator_next_step=WAIT_FOR_REAL_USER_TRAFFIC`
  - 지금 다음 실질 행동은 real-user traffic/cohort 확보라는 뜻입니다.
- `latest gate` 가 `PASS`
  - stable baseline/interpretation 기준으로는 현재 latest 상태가 허용 범위입니다.
- `latest gate (strict)` 가 `LATEST_OBSERVATION_CHANGED`
  - fresh window 관찰값 흔들림까지 막는 strict 정책에서는 아직 fail 이라는 뜻입니다.
- `strict_gate_exit_code=1`
  - strict gate fail을 wrapper가 요약한 값입니다. overview 자체는 여기서 중단하지 않습니다.
- `latest-overview-summary.txt`
  - daily handoff용 compact artifact 입니다. 핵심 gate/status/next-step 값만 다시 읽고 싶을 때 이 파일을 먼저 보면 됩니다.
- `latest-overview-note.md`
  - 사람용 handoff note 입니다.
- `latest-overview.json`
  - 후속 자동화나 machine-readable 확인이 필요할 때 읽는 artifact 입니다.
- `real_user_readiness_included=true`
  - overview에 readiness 결과도 같이 포함됐다는 뜻입니다.
- `real_user_readiness_skip_reason=AUTO_SKIP_MISSING_ADMIN_EMAIL`, `AUTO_SKIP_MISSING_ADMIN_PASSWORD`
  - 기본 `auto` 모드에서 무엇이 빠져 readiness를 생략했는지 더 구체적으로 보여 줍니다.
- `real_user_readiness_next_action=SET_ADMIN_PASSWORD_OR_ADMIN_PASSWORD_FILE`
  - auto discovery가 어디까지 됐는지 본 뒤 바로 다음 조치까지 한 줄로 안내합니다.
- `real_user_readiness_detected_admin_email`, `real_user_readiness_has_admin_password`
  - auto discovery가 어디까지 성공했는지 바로 확인하는 값입니다.
