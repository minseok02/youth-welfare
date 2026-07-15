# Post-Snapshot Current Baseline 2026-07-15

## Purpose

This checkpoint remeasures the current production shape after enabling the guarded Redis ranking snapshot.

The goal is to choose the next bottleneck from current data, not from pre-snapshot ranking numbers.

## Runtime State

- branch: `refactor/admin-dashboard-sections`
- documentation HEAD during this checkpoint: `d2ceb9a62a4ec1b8d891024b04d4d6b2107f4dd2`
- deployed app code: `ad16e288c2f8688b420b7b8d0a968053a4a78c1b`
- public domain: `https://youthmoa.kr`
- ALB target group: both targets healthy

Snapshot runtime flags were formalized into `.env.runtime.production` on both web nodes.

| Node | Instance | Scheduler | Snapshot read | Snapshot refresh | Backup |
| --- | --- | --- | --- | --- | --- |
| primary | `i-0b8d95e454df5e0f0` | true | true | true | `.env.runtime.production.bak-snapshot-20260715T160904Z` |
| secondary | `i-0e8a4cc599c1148c8` | false | true | false | `.env.runtime.production.bak-snapshot-20260715T160805Z` |

Both nodes keep `POLICY_RANKING_CANDIDATE_ENABLED=false`.

## Measurements

### API Latency

Commands:

```bash
RUNS=7 WARMUP_RUNS=1 INCLUDE_RATE_LIMIT_SENSITIVE_ENDPOINTS=true \
  API_LATENCY_ROOT=tmp/performance/current-snapshot-api-latency-local-20260715 \
  APP_BASE_URL=http://127.0.0.1:8082 \
  bash deploy/performance/run-local-api-latency-baseline.sh

RUNS=7 WARMUP_RUNS=1 INCLUDE_RATE_LIMIT_SENSITIVE_ENDPOINTS=true INCLUDE_ACTUATOR_HEALTH=false \
  API_LATENCY_ROOT=tmp/performance/current-snapshot-api-latency-edge-20260715 \
  APP_BASE_URL=https://youthmoa.kr \
  bash deploy/performance/run-local-api-latency-baseline.sh
```

Artifacts:

- local: `tmp/performance/current-snapshot-api-latency-local-20260715/20260715T160432Z`
- edge: `tmp/performance/current-snapshot-api-latency-edge-20260715/20260715T160458Z`

Local result:

| Scenario | p50 | p95 | Max | Errors |
| --- | ---: | ---: | ---: | --- |
| policy suggestions | 51.2ms | 120.6ms | 146.1ms | 0/7 |
| policy list default | 90.4ms | 120.0ms | 127.2ms | 0/7 |
| policy list active only | 88.5ms | 106.6ms | 108.1ms | 0/7 |
| policy detail first | 39.1ms | 42.1ms | 42.6ms | 0/7 |
| policy search keyword | 15.3ms | 19.1ms | 20.2ms | 0/7 |
| policy search filtered | 15.6ms | 18.7ms | 19.6ms | 0/7 |
| policy ranking | 8.5ms | 10.2ms | 10.5ms | 0/7 |

Edge result:

| Scenario | p50 | p95 | Max | Errors |
| --- | ---: | ---: | ---: | --- |
| policy list default | 226.1ms | 751.0ms | 927.8ms | 0/7 |
| policy list active only | 137.2ms | 256.1ms | 276.1ms | 0/7 |
| policy detail first | 69.4ms | 98.6ms | 105.6ms | 0/7 |
| policy suggestions | 78.1ms | 85.5ms | 86.3ms | 0/7 |
| policy search keyword | 54.0ms | 61.0ms | 61.2ms | 0/7 |
| policy search filtered | 53.9ms | 59.5ms | 60.6ms | 0/7 |
| policy ranking | 46.4ms | 47.7ms | 48.0ms | 0/7 |

### DB Query

The first DB query attempt used the default migration account path and produced empty EXPLAIN values due to password authentication failure. That run is not accepted.

Accepted command:

```bash
DB_QUERY_USERNAME="$(awk -F= '$1=="DB_USERNAME"{print $2; exit}' .env.runtime.production)" \
DB_QUERY_PASSWORD="$(awk -F= '$1=="DB_PASSWORD"{print $2; exit}' .env.runtime.production)" \
ENV_FILE=.env.runtime.production SMOKE_DB_MODE=postgres \
  DB_BASELINE_ROOT=tmp/performance/current-snapshot-db-query-app-20260715 \
  bash deploy/performance/run-local-db-query-baseline.sh
```

Artifact:

- `tmp/performance/current-snapshot-db-query-app-20260715/20260715T160630Z`

Representative DB results:

| Query | Execution | Planning | Notes |
| --- | ---: | ---: | --- |
| policy list created_at | 15.113ms | 1.517ms | seq scan |
| policy search keyword API shape | 73.420ms | 6.602ms | generated search path |
| policy search keyword legacy OR shape | 288.471ms | 4.071ms | legacy shape |
| policy detail first | 0.116ms | 1.941ms | index scan |
| recommendation logs recent window | 15.137ms | 1.296ms | seq scan |

### Edge Baseline

Command:

```bash
EDGE_ROOT=tmp/performance/current-snapshot-edge-baseline-20260715 \
  EXTERNAL_BASE_URL=https://youthmoa.kr \
  bash deploy/performance/run-local-edge-baseline.sh
```

Artifact:

- `tmp/performance/current-snapshot-edge-baseline-20260715/20260715T160652Z`

Result:

| Scenario | Status | Total |
| --- | --- | ---: |
| home | 200 | 38.695ms |
| policies page | 200 | 34.079ms |
| policy list default | 200 | 144.225ms |
| policy search keyword | 200 | 229.688ms |

Header checks passed: HSTS, frame options, nosniff, CSP, and hidden nginx version token.

The nginx tail still contained older `502`/upstream health lines from earlier app restarts. The latest access tail during this checkpoint showed current API and health requests returning `200`.

### Target-Specific List Check

Because edge `policy_list_default` had a high p95, the list endpoint was checked directly on each instance loopback.

Primary local samples:

```text
755ms, 159ms, 165ms, 158ms, 105ms, 126ms, 98ms, 165ms, 134ms, 136ms
```

Secondary local samples:

```text
157ms, 134ms, 103ms, 118ms, 105ms, 119ms, 129ms, 181ms, 126ms, 102ms
```

Interpretation:

- The edge list tail is not caused only by ALB/network.
- A primary local cold/tail sample reached about `755ms`.
- The warm local list path is usually around `100ms` to `180ms`, still materially slower than ranking/search warm paths.

## Bottleneck Decision

Current ranking is no longer the next bottleneck.

| Candidate | Current reading | Decision |
| --- | --- | --- |
| ranking `size=20` | edge p95 `47.7ms`, local p95 `10.2ms` | hold |
| search keyword | edge p95 `61.0ms`, local p95 `19.1ms`; DB API shape `73.4ms` | hold |
| search filtered | edge p95 `59.5ms`, local p95 `18.7ms` | hold |
| suggestions | local p95 `120.6ms`, edge p95 `85.5ms` | observe |
| policy list default | edge p95 `751.0ms`, local p95 `120.0ms`, direct primary cold sample `755ms` | next bottleneck |
| policy list active only | edge p95 `256.1ms`, local p95 `106.6ms` | secondary candidate |

Next recommended work:

1. Analyze policy list default cold/tail before changing code.
2. Separate local app timing, Redis shared cache hit/miss, DB query, and response serialization for `/api/policies?page=0&size=20`.
3. Check whether default list cache key is being missed after refresh/restart or across status/sort aliases.
4. Only optimize after the list-tail source is known.

