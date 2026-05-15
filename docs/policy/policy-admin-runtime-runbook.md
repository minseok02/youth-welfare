# 정책 admin runtime runbook

문서군 진입점: [policy-docs-index.md](./policy-docs-index.md)

## 목적

정책 admin 경로 중 지금 local/runtime 기준으로 실제 자주 다시 여는 작업은 아래입니다.

1. `referenceUrlsJson` rebuild
2. searchable youth relevance rebuild
3. embeddings rebuild
4. retrieval evaluation / quality gate
5. category audit

이 문서는 위 다섯 가지를 **한 장에서** 따라가게 정리한 current runbook입니다.

## 범위

이 문서가 다루는 것은 다음입니다.

- 로컬 app 기준 admin API 실행 순서
- 언제 어떤 경로를 먼저 쓰는지
- 응답에서 무엇을 봐야 하는지

이 문서가 다루지 않는 것은 다음입니다.

- 서버 배포/infra 절차
- blocked taxonomy import/backfill SQL reopen
- `Gov24` runtime collect 자체

## 사전 조건

앱/DB/Redis 기동:

```bash
SECURITY_ADMIN_EMAILS=admin@example.com docker compose up -d db redis app
```

health:

```bash
curl -sS http://127.0.0.1:8082/actuator/health
```

admin token 준비:

```bash
export APP_BASE_URL="http://127.0.0.1:8082"
export ADMIN_EMAIL="admin@example.com"
export ADMIN_PASSWORD="Password123!"
export ADMIN_LOGIN_RESPONSE="$(mktemp)"

curl -sS \
  -H 'Content-Type: application/json' \
  -X POST "$APP_BASE_URL/api/auth/login" \
  -d "{
    \"email\": \"$ADMIN_EMAIL\",
    \"password\": \"$ADMIN_PASSWORD\"
  }" | tee "$ADMIN_LOGIN_RESPONSE"
```

```bash
export ADMIN_ACCESS_TOKEN="$(python3 - "$ADMIN_LOGIN_RESPONSE" <<'PY'
import json
import sys

with open(sys.argv[1], "r", encoding="utf-8") as fp:
    payload = json.load(fp)

print(payload["data"]["accessToken"])
PY
)"
```

## 실행 순서

현재 local/runtime에서 가장 안전한 기본 순서는 아래입니다.

1. `reference-urls/rebuild`
2. 필요 시 `search-youth-relevance/rebuild`
3. 필요 시 `embeddings/rebuild`
4. `retrieval evaluation`
5. `quality gate`
6. `category audit`

이 순서가 좋은 이유:

- URL 후보 풀/검색 가중치/임베딩을 먼저 맞춘 뒤
- retrieval quality와 category distribution을 읽게 되기 때문입니다.

## 1. referenceUrlsJson rebuild

우선순위:

- detail row는 있는데 `reference_urls_json` 이 비어 있는 것처럼 보일 때
- canonical URL 후보를 raw detail 기준으로 다시 채우고 싶을 때

기본 안전 실행:

```bash
curl -sS \
  -H "Authorization: Bearer $ADMIN_ACCESS_TOKEN" \
  -X POST "$APP_BASE_URL/api/admin/policies/reference-urls/rebuild"
```

응답에서 볼 것:

- `missingOnly=true`
- `failedCount=0`
- `updatedCount > 0` 또는 이미 채워진 상태면 `skippedCount > 0`

샘플 실행:

```bash
curl -sS \
  -H "Authorization: Bearer $ADMIN_ACCESS_TOKEN" \
  -X POST "$APP_BASE_URL/api/admin/policies/reference-urls/rebuild?limitPerSource=20"
```

특정 source만:

```bash
curl -sS \
  -H "Authorization: Bearer $ADMIN_ACCESS_TOKEN" \
  -X POST "$APP_BASE_URL/api/admin/policies/reference-urls/rebuild?sourceType=YOUTH&sourceType=BOKJIRO_LOCAL"
```

전체 overwrite가 필요할 때만:

```bash
curl -sS \
  -H "Authorization: Bearer $ADMIN_ACCESS_TOKEN" \
  -X POST "$APP_BASE_URL/api/admin/policies/reference-urls/rebuild?missingOnly=false"
```

## 2. searchable youth relevance rebuild

우선순위:

- collect 이후 `search_youth_relevant` 재적용이 필요할 때
- 검색 relevance snapshot을 다시 정렬하고 싶을 때

실행:

```bash
curl -sS \
  -H "Authorization: Bearer $ADMIN_ACCESS_TOKEN" \
  -X POST "$APP_BASE_URL/api/admin/policies/search-youth-relevance/rebuild"
```

응답에서 볼 것:

- `processedCount`
- `updatedCount`
- `relevantCount`
- `excludedCount`

현재 local 재확인 기준:

- `processedCount=14863`
- `updatedCount=0`
- `relevantCount=3566`
- `excludedCount=11297`

## 3. embeddings rebuild

우선순위:

- `policy_chunks` 는 있는데 embedding refresh가 뒤처졌을 때
- semantic retrieval drift를 의심할 때

전체 rebuild:

```bash
curl -sS \
  -H "Authorization: Bearer $ADMIN_ACCESS_TOKEN" \
  -X POST "$APP_BASE_URL/api/admin/policies/embeddings/rebuild"
```

선택 rebuild:

```bash
curl -sS \
  -H "Authorization: Bearer $ADMIN_ACCESS_TOKEN" \
  -X POST "$APP_BASE_URL/api/admin/policies/embeddings/rebuild?serviceId=6790&serviceId=2622"
```

응답에서 볼 것:

- `scope`
- `requestedServiceCount`
- `scannedChunkCount`
- `refreshedChunkCount`

선택 rebuild local 재확인 예시 (`serviceId=6790,2622`):

- `scope=service_ids`
- `requestedServiceCount=2`
- `scannedChunkCount=7`
- `refreshedChunkCount=0`

## 4. retrieval evaluation

우선순위:

- retrieval baseline이 아직 유지되는지 볼 때
- compare/gate 전에 현재 후보 quality를 먼저 읽을 때

기본 실행:

```bash
curl -sS \
  -H "Authorization: Bearer $ADMIN_ACCESS_TOKEN" \
  -X POST "$APP_BASE_URL/api/admin/policies/retrieval-evaluations"
```

응답에서 볼 것:

- `top1HitRate`
- `top3HitRate`
- `branchSuggestionHitRate`
- `emptyResultCount`

현재 local baseline 문서 기준:

- `top1HitRate=1.0`
- `top3HitRate=1.0`
- `branchSuggestionHitRate=1.0`
- `emptyResultCount=0`

## 5. retrieval quality gate

우선순위:

- evaluation 결과를 운영 통과/미통과로 한 번에 보고 싶을 때

실행:

```bash
curl -sS \
  -H "Authorization: Bearer $ADMIN_ACCESS_TOKEN" \
  -X POST "$APP_BASE_URL/api/admin/policies/retrieval-evaluations/gate"
```

응답에서 볼 것:

- `passed=true`
- `failureReasons=[]`

이 경로가 가장 빠른 go/no-go 판정입니다.

## 6. category audit

우선순위:

- canonical category 분포가 어떻게 보이는지
- searchable ratio가 어느 정도인지
- 청년 broad category dominant mapping이 어디로 기우는지

실행:

```bash
curl -sS \
  -H "Authorization: Bearer $ADMIN_ACCESS_TOKEN" \
  "$APP_BASE_URL/api/admin/policies/category-audit"
```

응답에서 볼 것:

- `searchablePolicyRatio`
- top unified category summary
- youth broad dominant mapping

이건 튜닝 명령이 아니라 **분포/왜곡을 읽는 read-only 진단 경로**로 봅니다.

## 추천 기본 triage 순서

### 검색/챗 쪽 quality가 의심될 때

1. `retrieval-evaluations`
2. `retrieval-evaluations/gate`
3. 필요 시 `embeddings/rebuild`
4. 다시 `retrieval-evaluations`

### 상세 링크/신청 URL 쪽이 의심될 때

1. `reference-urls/rebuild`
2. 상세 API/프론트에서 `referenceUrlsJson` 확인

### category 분포/표시가 의심될 때

1. `category-audit`
2. 필요 시 collect snapshot / canonical sidecar 쪽으로 역추적

## 지금 기준으로 기억할 것

1. local/runtime에서 가장 자주 여는 bounded admin 경로는 `reference-urls/rebuild`, `embeddings/rebuild`, `retrieval-evaluations/gate`, `category-audit` 입니다.
2. `retrieval-evaluations/gate` 는 가장 빠른 통과/미통과 판정 경로입니다.
3. `category-audit` 는 수정 command가 아니라 분포를 읽는 audit 경로입니다.
4. `Gov24` 쪽은 이 문서가 아니라 [policy-gov24-runtime-audit-runbook.md](./policy-gov24-runtime-audit-runbook.md) 를 먼저 봅니다.
5. blocked taxonomy/import-backfill은 이 문서 범위가 아닙니다.
