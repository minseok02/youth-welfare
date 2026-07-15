# Post-Projection Current Baseline 2026-07-15

## Purpose

The policy list projection SQL optimization was accepted in `65f317e344df54ed3e5f1d8597e7112ec20d3460`.

This checkpoint remeasures the broader production shape before opening another optimization. The goal is to determine whether the next bottleneck is still policy-list-specific or has shifted to another endpoint or first-request/edge behavior.

## Runtime State

- branch: `refactor/admin-dashboard-sections`
- documentation HEAD at start: `e989fa1435ebef9d18559e06bf93f34c164e1f22`
- deployed app code: `65f317e344df54ed3e5f1d8597e7112ec20d3460`
- public domain: `https://youthmoa.kr`
- ALB target group: both targets healthy
- primary EC2: `i-0b8d95e454df5e0f0`
- secondary EC2: `i-0e8a4cc599c1148c8`

Ranking snapshot runtime state remains unchanged:

| Node | Scheduler | Snapshot read | Snapshot refresh | Candidate mode |
| --- | --- | --- | --- | --- |
| primary | true | true | true | false |
| secondary | false | true | false | false |

## Measurement Plan

Run the same broader checks used after the ranking snapshot, then add target-specific list checks because the last change targeted list presentation:

1. Local API latency on the primary loopback.
2. Edge API latency through `https://youthmoa.kr`.
3. DB representative query baseline with the app DB credentials.
4. Edge curl/header baseline.
5. Primary and secondary loopback list samples.
6. App timing logs for policy list slow segments.
7. Final public smoke and ALB health.

Accepted decision rule:

- Do not open another optimization only from one outlier.
- Prefer the next task only if repeated p95 or logs identify a stable app-side segment.
- If the system is broadly healthy and list warm p95 remains near the new accepted range, stop and keep observing.

## Commands

Local API latency:

```bash
RUNS=7 WARMUP_RUNS=1 INCLUDE_RATE_LIMIT_SENSITIVE_ENDPOINTS=true \
  API_LATENCY_ROOT=tmp/performance/post-projection-api-latency-local-20260715 \
  APP_BASE_URL=http://127.0.0.1:8082 \
  bash deploy/performance/run-local-api-latency-baseline.sh
```

Edge API latency:

```bash
RUNS=7 WARMUP_RUNS=1 INCLUDE_RATE_LIMIT_SENSITIVE_ENDPOINTS=true INCLUDE_ACTUATOR_HEALTH=false \
  API_LATENCY_ROOT=tmp/performance/post-projection-api-latency-edge-20260715 \
  APP_BASE_URL=https://youthmoa.kr \
  bash deploy/performance/run-local-api-latency-baseline.sh
```

DB query baseline:

```bash
DB_QUERY_USERNAME="$(awk -F= '$1=="DB_USERNAME"{print $2; exit}' .env.runtime.production)" \
DB_QUERY_PASSWORD="$(awk -F= '$1=="DB_PASSWORD"{print $2; exit}' .env.runtime.production)" \
ENV_FILE=.env.runtime.production SMOKE_DB_MODE=postgres \
  DB_BASELINE_ROOT=tmp/performance/post-projection-db-query-app-20260715 \
  bash deploy/performance/run-local-db-query-baseline.sh
```

Edge baseline:

```bash
EDGE_ROOT=tmp/performance/post-projection-edge-baseline-20260715 \
  EXTERNAL_BASE_URL=https://youthmoa.kr \
  bash deploy/performance/run-local-edge-baseline.sh
```

Target-specific list checks:

```bash
for i in $(seq 1 12); do
  curl -s -o /tmp/policy-list-primary-post-projection.json \
    -w "primary_post_projection sample=$i status=%{http_code} total_ms=%{time_total} ttfb_ms=%{time_starttransfer}\n" \
    'http://127.0.0.1:8082/api/policies?page=0&size=20'
  sleep 0.5
done
```

The same loop is run on the secondary through SSM, and on the edge with `https://youthmoa.kr`.

## Results

### API Latency

Artifacts:

- local: `tmp/performance/post-projection-api-latency-local-20260715/20260715T173638Z`
- edge: `tmp/performance/post-projection-api-latency-edge-20260715/20260715T173703Z`

Local primary loopback:

| Scenario | p50 | p95 | Max | Errors |
| --- | ---: | ---: | ---: | --- |
| policy list default | 30.3ms | 40.6ms | 43.5ms | 0/7 |
| policy list active only | 27.6ms | 211.5ms | 289.6ms | 0/7 |
| policy detail first | 33.6ms | 36.8ms | 36.9ms | 0/7 |
| policy suggestions | 40.6ms | 42.9ms | 43.6ms | 0/7 |
| policy search keyword | 17.6ms | 41.0ms | 47.6ms | 0/7 |
| policy search filtered | 16.5ms | 19.4ms | 20.2ms | 0/7 |
| policy trending | 17.0ms | 21.1ms | 21.5ms | 0/7 |
| policy ranking | 8.1ms | 9.6ms | 9.9ms | 0/7 |
| health | 17.8ms | 18.3ms | 18.4ms | 0/7 |

Edge:

| Scenario | p50 | p95 | Max | Errors |
| --- | ---: | ---: | ---: | --- |
| policy list default | 71.1ms | 110.0ms | 125.8ms | 0/7 |
| policy list active only | 66.3ms | 81.0ms | 83.7ms | 0/7 |
| policy detail first | 70.0ms | 115.9ms | 135.2ms | 0/7 |
| policy suggestions | 84.4ms | 99.0ms | 101.9ms | 0/7 |
| policy search keyword | 54.8ms | 106.4ms | 127.3ms | 0/7 |
| policy search filtered | 53.6ms | 63.8ms | 67.3ms | 0/7 |
| policy trending | 53.9ms | 56.2ms | 56.3ms | 0/7 |
| policy ranking | 47.1ms | 50.0ms | 51.0ms | 0/7 |

### DB Query

Artifact:

- `tmp/performance/post-projection-db-query-app-20260715/20260715T173730Z`

Representative DB results:

| Query | Execution | Planning | Notes |
| --- | ---: | ---: | --- |
| policy list created_at | 12.804ms | 1.511ms | seq scan |
| policy search keyword API shape | 166.438ms | 9.803ms | generated search path |
| policy search keyword legacy OR shape | 410.462ms | 3.800ms | legacy shape |
| policy detail first | 0.115ms | 1.892ms | index scan |
| recommendation logs recent window | 9.946ms | 0.616ms | seq scan |
| admin collect failures recent | 1.001ms | 0.535ms | seq scan |

Data profile at this checkpoint:

| Table | Rows |
| --- | ---: |
| welfare_services | 15106 |
| raw_api_payloads | 44979 |
| recommendation_logs | 32230 |
| search_logs | 2729 |
| recent_policy_views | 69 |
| chat_messages | 36 |

### Edge Baseline

Artifact:

- `tmp/performance/post-projection-edge-baseline-20260715/20260715T173730Z`

| Scenario | Status | Total |
| --- | ---: | ---: |
| home | 200 | 61.017ms |
| policies page | 200 | 61.777ms |
| policy list default | 200 | 107.589ms |
| policy search keyword | 200 | 217.014ms |

Header checks passed: HSTS, frame options, nosniff, CSP, and hidden nginx version token.

The sampled nginx tail still contained older `502` entries from app restarts during deployments. Current public smoke and ALB health were healthy after the measurement.

### Target-Specific List Check

Artifact:

- local primary/edge: `tmp/performance/post-projection-target-list-20260715`
- secondary: same artifact directory on the secondary node

| Path | Samples | p50 | p95 | Max |
| --- | ---: | ---: | ---: | ---: |
| primary loopback | 12 | 26.3ms | 59.2ms | 59.2ms |
| secondary loopback | 12 | 28.0ms | 34.6ms | 34.6ms |
| edge | 12 | 38.1ms | 61.9ms | 61.9ms |

App log observation:

- primary app logs for the checked window had no policy-list slow timing or error lines after the latest list loop
- secondary app logs for the checked window had no policy-list slow timing or error lines after the latest list loop
- this means most current list requests are below the slow timing thresholds introduced during instrumentation

### Smoke And Health

Final smoke:

| Endpoint | Status | Total |
| --- | ---: | ---: |
| `/` | 200 | 10.441ms |
| `/api/policies?page=0&size=20` | 200 | 50.565ms |
| `/api/policies/ranking?size=20` | 200 | 32.626ms |
| `/api/policies/search` keyword `청년` | 200 | 129.334ms |

ALB target health:

- `i-0e8a4cc599c1148c8`: healthy
- `i-0b8d95e454df5e0f0`: healthy

## Comparison

Compared with the post-snapshot broader baseline:

| Scenario | Previous p95 | Current p95 | p95 delta |
| --- | ---: | ---: | ---: |
| edge policy list default | 751.0ms | 110.0ms | 85.4% lower |
| local policy list default | 120.0ms | 40.6ms | 66.2% lower |
| edge policy ranking | 47.7ms | 50.0ms | 4.8% higher |
| local policy ranking | 10.2ms | 9.6ms | 5.9% lower |
| edge policy search keyword | 61.0ms | 106.4ms | 74.4% higher |
| local policy search keyword | 19.1ms | 41.0ms | 114.7% higher |

Compared with the direct timing-instrumentation list samples:

| Path | Previous p95 | Current target-specific p95 | Reduction |
| --- | ---: | ---: | ---: |
| primary loopback list | 197.3ms | 59.2ms | 70.0% |
| secondary loopback list | 171.7ms | 34.6ms | 79.8% |
| edge list | 728.7ms | 61.9ms | 91.5% |

The search keyword p95 is higher than the immediate post-snapshot API baseline, but it is not the current dominant user-facing p95 in this short rerun. DB search API shape is still relatively heavy at `166.438ms`, so search should stay on the watch list rather than be optimized immediately from this single run.

## Decision

Do not open another code optimization immediately.

The projection SQL change moved `policy_list_default` from the clear next bottleneck to an acceptable range:

- local API p95 `40.6ms`
- edge API p95 `110.0ms`
- target-specific edge p95 `61.9ms`
- no list slow timing logs in the final checked window

Current state:

- ranking remains healthy
- list default is no longer the dominant repeated bottleneck
- search DB remains comparatively heavier, but endpoint p95 is not yet enough by itself to justify another immediate change
- one local active-only list p95 outlier appeared in the API wrapper, but it did not reproduce in target-specific list checks

Next recommended action:

1. Stop code changes for this sequence.
2. Keep this as the current accepted post-projection baseline.
3. If continuing performance work, run a longer low-concurrency load/soak check before choosing the next target, with special attention to search keyword DB cost and active-only/list first-request tails.
