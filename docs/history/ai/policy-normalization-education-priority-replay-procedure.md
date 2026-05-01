# `교육 -> 교육·직업훈련` priority 실험 sample replay 절차

2026-04-30 기준 `교육 -> 교육·직업훈련` narrow experiment를 실제로 구현한 뒤,
local 환경에서 `flag off` / `flag on` 결과를 같은 조건으로 비교하는 절차입니다.

관련 문서:

- [policy-normalization-education-priority-validation-criteria.md](./policy-normalization-education-priority-validation-criteria.md)
- [policy-normalization-education-priority-flag-scope.md](./policy-normalization-education-priority-flag-scope.md)
- [policy-normalization-education-priority-implementation-slot.md](./policy-normalization-education-priority-implementation-slot.md)
- [runtime-api-smoke-commands.md](../../runtime-api-smoke-commands.md)

자동 replay 초안:

- [deploy/smoke/run-local-education-priority-replay.sh](/home/minseok/youth-welfare/deploy/smoke/run-local-education-priority-replay.sh)

## 전제

이 절차는 **실험 구현이 이미 들어간 뒤** 실행합니다.

즉 아래 조건이 먼저 충족돼야 합니다.

1. `RuleScoringService` 에 education experiment helper / flag read 구현 완료
2. flag key `recommend.priority.education-canonical-bonus.enabled` 사용 가능
3. local DB에 canonical sidecar 데이터가 이미 적재돼 있음

## 원칙

비교는 반드시 **같은 user snapshot / 같은 candidate pool** 을 최대한 유지한 상태에서 합니다.

따라서 `off -> on` 비교 사이에 아래를 끼우지 않습니다.

- collect 재실행
- sidecar backfill 재실행
- user profile 수정
- user priorities 수정
- score weight 변경
- 다른 branch checkout

즉:

- 앱 설정만 바꿔 재기동
- 같은 user로 추천 refresh 재호출

만 허용합니다.

## 준비

수동 절차 대신 known positive sample 기준 자동 실행이 필요하면 아래 draft script를 우선 사용합니다.

```bash
deploy/smoke/run-local-education-priority-replay.sh
```

기본값:

- sample A: `education.replay.afterincome.a@example.com`
- sample B: `education.replay.afterincome.b@example.com`
- `regionCode=28110`
- age `25`
- `incomeLevel=5`
- sample A priority `["EDUCATION","JOB"]`
- sample B priority `["HOUSING","JOB"]`
- `USE_REAL_OPENAI_FOR_REPLAY=false`
- `RECOMMEND_AI_REPLAY_SEED=424242`
- `.env` 에 real `OPENAI_API_KEY` 가 있어도 기본값에서는 `OPENAI_API_KEY=invalid-for-rule-only-replay` 를 강제

스크립트는 아래를 자동 수행합니다.

1. local `db` / `redis` ensure
2. `flag off` host `bootRun`
3. sample A/B signup-or-login, profile/priority 정렬, refresh
4. `flag on` host `bootRun`
5. 같은 sample A/B refresh
6. `compat=기타 + youth_major=교육` target row top-10 진입 수 비교
7. sample A 개선 hard assert
8. sample B(control) drift는 기본 warning, 필요하면 `STRICT_CONTROL_ASSERT=true` 로 strict fail
9. `user_recommendations` off/on snapshot(`edu-a/b-*-scores.tsv`)도 함께 남겨 `rule_weighted_score` / `ai_score` / `final_score` 경계를 바로 비교
10. artifact에 `openai-mode.txt` 를 같이 남겨 `rule-only-invalid-key` / `real-openai` 모드를 명시
11. boot log에서 `[RealtimeAiGateway][replay-trace]`, `[RealtimeAiGateway][replay-trace-response]` 라인을 추출해 `edu-a/b-*-ai-trace.log`, `edu-a/b-*-ai-response-trace.log`, `ai-trace-*.log`, `ai-trace-response-*.log` 로 남기고 `candidateIds` / `candidateRuleScores` / `promptSha256` / `replaySeed` / `systemFingerprint` / `responseId` 를 비교
12. summary stdout에도 `A_FINGERPRINT ... same|different`, `B_FINGERPRINT ... same|different` 를 같이 출력해 artifact를 열기 전에도 `backend churn` 여부를 바로 볼 수 있게 한다
13. summary stdout의 `SUMMARY_METRIC` 한 줄에서 `A_top10_target`, `B_top10_target`, `A/B_target_total`, `A/B_fp` 를 먼저 보고 pass/warn 판단을 시작한다
14. nightly summary file에 append 할 때도 같은 축을 유지하고,
    최소 필드는 `ts`, `mode`, `A_top10_target`, `B_top10_target`,
    `A_target_total`, `B_target_total`, `A_fp`, `B_fp`, `artifact_dir` 로 제한한다
15. 실제 append 는 `REPLAY_SUMMARY_APPEND_FILE=/path/to/nightly-summary-YYYY-MM-DD.log`
    env 로 켜고, 필요하면 `REPLAY_SUMMARY_TS` 로 기록 시각을 wrapper 에서 명시한다
16. ops host nightly 실행은 직접 env 를 길게 붙이기보다
    `deploy/smoke/run-nightly-openai-replay.sh` wrapper 를 기본 진입점으로 쓴다
17. host cron 예시는 wrapper/cleanup 둘 다 절대경로 호출로 둔다
    - replay:
      - `10 1 * * * REPLAY_LOG_ROOT=/var/log/youth-welfare/openai-replay /home/minseok/youth-welfare/deploy/smoke/run-nightly-openai-replay.sh >> /var/log/youth-welfare/openai-replay/nightly-cron.log 2>&1`
    - cleanup:
      - `40 1 * * * REPLAY_LOG_ROOT=/var/log/youth-welfare/openai-replay SUMMARY_RETENTION_DAYS=30 ARTIFACT_RETENTION_DAYS=14 /home/minseok/youth-welfare/deploy/smoke/cleanup-openai-replay-artifacts.sh >> /var/log/youth-welfare/openai-replay/cleanup-cron.log 2>&1`

real OpenAI 호출이 정말 필요하면 아래처럼 명시적으로 opt-in 합니다.

```bash
USE_REAL_OPENAI_FOR_REPLAY=true deploy/smoke/run-local-education-priority-replay.sh
```

이 `real-openai` run의 기본 위치는
PR hard gate가 아니라 nightly/diagnostic 또는 수동 triage입니다.
즉 strict equality 실패만으로는 PR blocker로 해석하지 않습니다.

### 1. DB/Redis 기동

```bash
docker compose up -d db redis
```

### 1-1. host `bootRun` 전제

host에서 직접 `bootRun` 할 때는 아래를 같이 맞춥니다.

- `DB_URL`, `APP_PII_DB_URL`, `NOTIFICATION_PII_DB_URL` 를 host 접근 가능한 `127.0.0.1:3307` 기준으로 override
- `REDIS_HOST=127.0.0.1`
- `AES_SECRET_KEY` 를 비우지 않음
- PII datasource 계정은 local split-account(`app_pii_rw`, `notification_pii_ro`) 또는 동등 권한 계정 사용

이 전제를 빼면 signup/login 이전에
`AES encrypt failed(Empty key)` 나 `user_pii access denied` 로 smoke가 끊길 수 있습니다.

### 2. 실험 대상 사용자 준비

권장:

- sample A: `EDUCATION` priority 가 있고 `compat=기타 + youth_major=교육` 후보가 실제로 나오는 사용자
- sample B: 같은 환경에서 `EDUCATION` priority 가 없거나 target row가 없는 control 사용자

중요:

- sample A 는 DB inventory 상 후보 존재만으로는 부족합니다
- 실제 `POST /api/recommendations/refresh` 결과 집합 안에 `compat=기타 + youth_major=교육` row 가 최소 1건은 들어오는지 먼저 확인해야 합니다
- 그렇지 않으면 `flag off/on` 비교가 전부 동일하게 끝나도 helper/flag 문제가 아니라 **sample miss** 일 수 있습니다

2026-04-30 local snapshot 기준 known positive 예시는 아래입니다.

- sample A 성격:
  - `regionCode=28110`
  - age `25`
  - `incomeLevel=5`
  - `employmentStatus=미취업`
  - `interestFields=["교육"]`
  - `priorityCodes=["EDUCATION","JOB"]`
- sample B(control) 성격:
  - 같은 region/profile
  - `priorityCodes=["HOUSING","JOB"]`

2026-04-30 latest script smoke 결과:

- sample A top-10 target row: `1 -> 7`
- sample B top-10 target row: `0 -> 0`
- artifact dir 예시: `/tmp/tmp.pKVwRY4dlt`

2026-04-30 `rule-only-invalid-key` mode 재검증 결과:

- sample A top-10 target row: `5 -> 10`
- sample B top-10 target row: `2 -> 2`
- `edu-b-off-scores.tsv` / `edu-b-on-scores.tsv` diff 없음
- `edu-b-off-ai-trace.log` / `edu-b-on-ai-trace.log` 의 `candidateIds`, `candidateRuleScores`, `promptSha256` 도 동일
- artifact dir 예시: `/tmp/tmp.x4i74TN5Wv`

2026-04-30 `real-openai` mode 재검증 결과:

- 실행: `USE_REAL_OPENAI_FOR_REPLAY=true deploy/smoke/run-local-education-priority-replay.sh`
- sample A top-10 target row: `1 -> 7`
- sample B top-10 target row: `1 -> 0`
- sample B `edu-b-off-scores.tsv` / `edu-b-on-scores.tsv` diff 재현
- artifact dir 예시: `/tmp/tmp.EZBH319uNA`

2026-04-30 trace export 포함 `real-openai` mode 재검증 결과:

- 실행: `USE_REAL_OPENAI_FOR_REPLAY=true deploy/smoke/run-local-education-priority-replay.sh`
- sample B top-10 target row: `0 -> 1`
- `edu-b-off-ai-trace.log` / `edu-b-on-ai-trace.log` 의 `candidateIds`, `candidateRuleScores`, `promptSha256` 는 동일
- 그런데 `edu-b-off-scores.tsv` / `edu-b-on-scores.tsv` 의 `ai_score` / `final_score` 는 다시 달라짐
- artifact dir 예시: `/tmp/tmp.WoIyHuKtMd`

2026-04-30 replay seed / response fingerprint trace 포함 `real-openai` mode 재검증 결과:

- 실행: `USE_REAL_OPENAI_FOR_REPLAY=true KEEP_ARTIFACTS=true deploy/smoke/run-local-education-priority-replay.sh`
- sample B top-10 target row: `3 -> 1`
- `edu-b-off-ai-trace.log` / `edu-b-on-ai-trace.log` 의 `candidateIds`, `candidateRuleScores`, `promptSha256`, `replaySeed=424242` 는 동일
- 그런데 `edu-b-off-ai-response-trace.log` / `edu-b-on-ai-response-trace.log` 에서는 `systemFingerprint=fp_de7acce317 -> fp_ff247d5857`, `responseId` 도 다르게 찍혔고 `responseSeed=none` 이었다
- 같은 `promptSha256` / 같은 `replaySeed` 조건에서도 backend fingerprint 가 바뀌면 `ai_score` / `final_score` drift가 계속 남는다는 쪽으로 해석을 좁혔다
- artifact dir 예시: `/tmp/tmp.hisZmhuvuH`

2026-04-30 same `systemFingerprint` artifact 확보 결과:

- 실행: `USE_REAL_OPENAI_FOR_REPLAY=true KEEP_ARTIFACTS=true deploy/smoke/run-local-education-priority-replay.sh`
- sample B top-10 target row: `1 -> 1`
- `edu-b-off-ai-trace.log` / `edu-b-on-ai-trace.log` 의 `candidateIds`, `candidateRuleScores`, `promptSha256`, `replaySeed=424242` 는 동일
- `edu-b-off-ai-response-trace.log` / `edu-b-on-ai-response-trace.log` 의 `systemFingerprint=fp_ff247d5857` 도 동일했고, `responseId` 만 달랐다
- 그런데도 `edu-b-off-scores.tsv` / `edu-b-on-scores.tsv` 에서는 `ai_score` / `final_score` diff가 계속 남았다. 예를 들어 주거 row는 `404:85 -> 75`, `405:75 -> 85`, 교육 row는 `390:55 -> 70`, `364:65 -> 70` 식으로 바뀌었다
- 즉 current real-openai replay drift는 backend fingerprint churn만으로는 설명되지 않고, same fingerprint 안에서도 남는 live response variability 쪽으로 결론이 더 좁혀졌다
- artifact dir 예시: `/tmp/tmp.TpE5SaiHJu`

### 3. 공통 변수

```bash
export APP_BASE_URL="http://127.0.0.1:18082"

export SAMPLE_A_EMAIL="education.sample@example.com"
export SAMPLE_A_PASSWORD="Password123!"

export SAMPLE_B_EMAIL="control.sample@example.com"
export SAMPLE_B_PASSWORD="Password123!"

export COOKIE_A="$(mktemp)"
export COOKIE_B="$(mktemp)"
export RESP_A_OFF="$(mktemp)"
export RESP_A_ON="$(mktemp)"
export RESP_B_OFF="$(mktemp)"
export RESP_B_ON="$(mktemp)"
```

정리:

```bash
rm -f "$COOKIE_A" "$COOKIE_B" "$RESP_A_OFF" "$RESP_A_ON" "$RESP_B_OFF" "$RESP_B_ON"
```

## 1단계: flag `off` 로 앱 기동

```bash
cd backend
RECOMMEND_PRIORITY_EDUCATION_CANONICAL_BONUS_ENABLED=false \
SERVER_PORT=18082 \
./gradlew bootRun --no-daemon
```

주의:

- 실제 key 바인딩 이름은 구현 PR에서 정한 env alias에 맞춥니다
- 비교 중에는 이 앱 프로세스를 유지합니다

## 2단계: sample A 로그인 + 추천 refresh (`off`)

```bash
curl -sS \
  -c "$COOKIE_A" \
  -H 'Content-Type: application/json' \
  -X POST "$APP_BASE_URL/api/auth/login" \
  -d "{
    \"email\": \"$SAMPLE_A_EMAIL\",
    \"password\": \"$SAMPLE_A_PASSWORD\"
  }" > /dev/null

export ACCESS_TOKEN_A="$(curl -sS \
  -c "$COOKIE_A" \
  -H 'Content-Type: application/json' \
  -X POST "$APP_BASE_URL/api/auth/login" \
  -d "{
    \"email\": \"$SAMPLE_A_EMAIL\",
    \"password\": \"$SAMPLE_A_PASSWORD\"
  }" | python3 -c 'import json,sys; print(json.load(sys.stdin)["data"]["accessToken"])')"
```

```bash
curl -sS \
  -H "Authorization: Bearer $ACCESS_TOKEN_A" \
  -X POST "$APP_BASE_URL/api/recommendations/refresh" | tee "$RESP_A_OFF"
```

top-10 요약 추출:

```bash
python3 - "$RESP_A_OFF" <<'PY'
import json, sys
data = json.load(open(sys.argv[1], encoding="utf-8"))["data"][:10]
for i, row in enumerate(data, 1):
    print(i, row["serviceId"], row["title"], row["unifiedCategory"], row["finalScore"])
PY
```

## 3단계: sample B 로그인 + 추천 refresh (`off`)

```bash
export ACCESS_TOKEN_B="$(curl -sS \
  -c "$COOKIE_B" \
  -H 'Content-Type: application/json' \
  -X POST "$APP_BASE_URL/api/auth/login" \
  -d "{
    \"email\": \"$SAMPLE_B_EMAIL\",
    \"password\": \"$SAMPLE_B_PASSWORD\"
  }" | python3 -c 'import json,sys; print(json.load(sys.stdin)["data"]["accessToken"])')"
```

```bash
curl -sS \
  -H "Authorization: Bearer $ACCESS_TOKEN_B" \
  -X POST "$APP_BASE_URL/api/recommendations/refresh" | tee "$RESP_B_OFF"
```

## 4단계: 앱 종료 후 flag `on` 으로 재기동

앱만 재기동합니다. DB/Redis/데이터는 그대로 둡니다.

```bash
cd backend
RECOMMEND_PRIORITY_EDUCATION_CANONICAL_BONUS_ENABLED=true \
SERVER_PORT=18082 \
./gradlew bootRun --no-daemon
```

중요:

- 이 단계 사이에 collect/backfill/user 수정 금지
- 같은 DB snapshot 을 유지해야 함

## 5단계: sample A / B 추천 refresh (`on`)

```bash
curl -sS \
  -H "Authorization: Bearer $ACCESS_TOKEN_A" \
  -X POST "$APP_BASE_URL/api/recommendations/refresh" | tee "$RESP_A_ON"

curl -sS \
  -H "Authorization: Bearer $ACCESS_TOKEN_B" \
  -X POST "$APP_BASE_URL/api/recommendations/refresh" | tee "$RESP_B_ON"
```

## 6단계: 비교

비교 전에 한 번 더 아래를 확인합니다.

```bash
python3 - "$RESP_A_OFF" <<'PY'
import json, sys
rows = json.load(open(sys.argv[1], encoding="utf-8"))["data"]
target = [r for r in rows if r["unifiedCategory"] == "기타"]
print("other_rows", len(target))
for row in target[:10]:
    print(row["serviceId"], row["title"], row["unifiedCategory"], row["finalScore"])
PY
```

위 출력만으로는 `youth_major=교육` 여부가 보이지 않으므로, 필요하면 같은 `serviceId` 집합을 local DB의 `service_taxonomies.youth_major_label` 과 다시 대조합니다.

### sample A top-10 비교

```bash
python3 - "$RESP_A_OFF" "$RESP_A_ON" <<'PY'
import json, sys
off = json.load(open(sys.argv[1], encoding="utf-8"))["data"][:10]
on = json.load(open(sys.argv[2], encoding="utf-8"))["data"][:10]
print("OFF")
for i, row in enumerate(off, 1):
    print(i, row["serviceId"], row["title"], row["unifiedCategory"], row["finalScore"])
print("ON")
for i, row in enumerate(on, 1):
    print(i, row["serviceId"], row["title"], row["unifiedCategory"], row["finalScore"])
PY
```

### sample B top-10 비교

```bash
python3 - "$RESP_B_OFF" "$RESP_B_ON" <<'PY'
import json, sys
off = json.load(open(sys.argv[1], encoding="utf-8"))["data"][:10]
on = json.load(open(sys.argv[2], encoding="utf-8"))["data"][:10]
print("OFF")
for i, row in enumerate(off, 1):
    print(i, row["serviceId"], row["title"], row["unifiedCategory"], row["finalScore"])
print("ON")
for i, row in enumerate(on, 1):
    print(i, row["serviceId"], row["title"], row["unifiedCategory"], row["finalScore"])
PY
```

## 7단계: 판정

판정은 [policy-normalization-education-priority-validation-criteria.md](./policy-normalization-education-priority-validation-criteria.md) 기준으로 합니다.

핵심만 요약하면:

### 계속 진행 가능

- sample A 에서만 순위 변화 발생
- `compat=기타 + youth_major=교육` row 가 top-N 안으로 진입하거나 상승
- sample B 는 사실상 유지
- 응답 `unifiedCategory`, `aiReason` 의미 불변

### 보류

- sample B 도 흔들림
- `교육` 실험과 무관한 row 가 크게 연쇄 이동
- 응답 category 의미가 바뀜
- 사람이 보기에도 올라온 row 가 `교육·직업훈련` priority 와 잘 안 맞음
- sample A 결과 집합 안에 `compat=기타 + youth_major=교육` target row 자체가 없음

마지막 경우는 실험 실패가 아니라 **sample selection 실패** 로 취급합니다.
이때는 flag/helper 를 다시 만지기보다, 먼저 target row가 실제 retrieval/result set에 들어오는 user snapshot 을 다시 고릅니다.

## 캡처 권장 항목

비교 결과를 남길 때는 아래를 같이 기록합니다.

1. branch / commit
2. flag off/on 값
3. sample A / B user 식별자
4. top-10 serviceId / title / unifiedCategory / finalScore
5. target row 위치 변화
6. 이상 징후 여부

## 최종 정책

정리하면:

- replay 는 `flag off -> flag on` 두 번의 refresh 비교로만 한다
- 같은 DB snapshot / 같은 user snapshot 을 유지한다
- collect/backfill/user 수정은 사이에 넣지 않는다
- sample A 와 sample B 를 같이 봐야 한다

## 다음 작업

1. target row가 실제 결과 집합에 들어오는 replay sample inventory 작성
2. `참여권리` 의 `청년참여` subset bridge 여부 결정
3. 필요하면 위 절차를 자동화하는 local smoke script 초안 작성
