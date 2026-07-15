# Post-Projection Load Soak 2026-07-15

## Purpose

The post-projection broader baseline concluded that no immediate code optimization should be opened from short samples.

This checkpoint runs a longer low-concurrency load/soak before choosing any next target. The goal is to find repeated p95/p99 behavior under mild sustained traffic, not deployment cold-start noise.

## Runtime State

- branch: `refactor/admin-dashboard-sections`
- documentation HEAD at start: `9df20430ab71033888c952cb96332369c277dac9`
- deployed app code: `65f317e344df54ed3e5f1d8597e7112ec20d3460`
- public domain: `https://youthmoa.kr`
- primary EC2: `i-0b8d95e454df5e0f0`
- secondary EC2: `i-0e8a4cc599c1148c8`
- ALB target group: both targets healthy before measurement

## Plan

Run sustained but modest load against both app nodes directly:

- duration: `120s`
- concurrency: `3`
- request delay per worker: `0.15s`
- timeout: `10s`
- scenarios: health, policy list, search keyword, search filtered, suggestions, ranking, first detail

Primary uses loopback from the primary node:

```bash
APP_BASE_URL=http://127.0.0.1:8082 \
API_LOAD_ROOT=tmp/performance/post-projection-load-soak-primary-20260715 \
DURATION_SECONDS=120 CONCURRENCY=3 API_LOAD_REQUEST_DELAY_SECONDS=0.15 \
  bash deploy/performance/run-local-api-load-baseline.sh
```

Secondary uses the same command through SSM on `i-0e8a4cc599c1148c8`.

The public edge is checked with post-load smoke and target list samples instead of the generic load script, because that script includes `/actuator/health`, which is intentionally blocked by nginx on the public domain.

## Acceptance Criteria

Accept current performance and stop code work if:

- no load scenario has request errors
- no rate limiting appears
- policy list p95 remains near the accepted post-projection range
- logs do not show a repeated app-side slow segment
- ALB targets stay healthy

Open another investigation only if:

- the same endpoint repeatedly dominates p95/p99 on both nodes, or
- logs identify a stable app-side segment rather than one-off outliers.

## Results

### Initial Attempt

The first primary run used the original low-concurrency proposal:

- duration: `120s`
- concurrency: `3`
- request delay: `0.15s`
- total throughput: `17.201 rps`
- artifact: `tmp/performance/post-projection-load-soak-primary-20260715/20260715T175959Z`

Result: failed due to rate limiting.

| Scenario | Requests | p95 | Max | Errors | Rate limited |
| --- | ---: | ---: | ---: | ---: | ---: |
| policy list default | 296 | 38.4ms | 290.2ms | 176 | 176 |
| policy detail first | 294 | 37.0ms | 293.9ms | 254 | 254 |
| policy search keyword | 297 | 21.9ms | 291.1ms | 237 | 237 |
| policy search filtered | 295 | 21.8ms | 2764.0ms | 235 | 235 |
| policy ranking | 294 | 10.9ms | 64.3ms | 234 | 234 |
| policy suggestions | 295 | 74.9ms | 604.5ms | 0 | 0 |
| health | 295 | 37.9ms | 227.5ms | 0 | 0 |

Interpretation:

- This run is not an accepted performance baseline.
- It is still useful as a rate-limit boundary check: about `17 rps` from one client fingerprint is too high for the public policy API rate-limit posture.
- The accepted soak was rerun at a lower rate.

### Accepted Low-Rate Soak

Accepted settings:

- duration: `180s`
- concurrency: `1`
- request delay: `1.2s`
- expected throughput: about `0.8 rps`

Primary artifact:

- `tmp/performance/post-projection-load-soak-primary-lowrate-20260715/20260715T180211Z`

Secondary artifact:

- `tmp/performance/post-projection-load-soak-secondary-lowrate-20260715/20260715T180520Z` on `i-0e8a4cc599c1148c8`

Primary result:

| Scenario | Requests | p50 | p95 | p99 | Max | Errors | Rate limited |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| policy search filtered | 21 | 13.2ms | 210.9ms | 223.9ms | 227.2ms | 0 | 0 |
| policy search keyword | 21 | 13.0ms | 117.9ms | 122.4ms | 123.5ms | 0 | 0 |
| policy suggestions | 21 | 36.2ms | 42.0ms | 42.5ms | 42.6ms | 0 | 0 |
| health | 21 | 26.8ms | 33.5ms | 52.4ms | 57.1ms | 0 | 0 |
| policy list default | 21 | 22.4ms | 33.1ms | 40.8ms | 42.7ms | 0 | 0 |
| policy detail first | 20 | 23.2ms | 31.3ms | 46.1ms | 49.8ms | 0 | 0 |
| policy ranking | 21 | 7.3ms | 17.4ms | 23.5ms | 25.1ms | 0 | 0 |

Secondary result:

| Scenario | Requests | p50 | p95 | p99 | Max | Errors | Rate limited |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| policy search filtered | 21 | 16.1ms | 277.6ms | 300.4ms | 306.1ms | 0 | 0 |
| policy search keyword | 21 | 17.3ms | 130.9ms | 254.9ms | 285.9ms | 0 | 0 |
| policy suggestions | 21 | 40.7ms | 100.6ms | 106.5ms | 108.0ms | 0 | 0 |
| policy list default | 21 | 28.0ms | 46.5ms | 53.7ms | 55.5ms | 0 | 0 |
| policy detail first | 20 | 31.7ms | 38.6ms | 64.7ms | 71.2ms | 0 | 0 |
| health | 21 | 20.8ms | 29.9ms | 52.8ms | 58.6ms | 0 | 0 |
| policy ranking | 21 | 9.4ms | 19.3ms | 23.0ms | 23.9ms | 0 | 0 |

### Logs And Health

Primary:

- app health stayed `UP`
- ALB target stayed healthy
- load logs showed repeated search requests in the `100ms` to `225ms` range
- no accepted low-rate soak errors or 429s were observed

Secondary:

- app logs in the checked window had no `ERROR`, `Exception`, `status=429`, `status=5xx`, or policy-list slow timing lines
- ALB target stayed healthy

Final public smoke:

| Endpoint | Status | Total |
| --- | ---: | ---: |
| `/` | 200 | 26.065ms |
| `/api/policies?page=0&size=20` | 200 | 50.766ms |
| `/api/policies/ranking?size=20` | 200 | 28.506ms |
| `/api/policies/search` keyword `청년` | 200 | 165.737ms |

ALB target health after measurement:

- `i-0e8a4cc599c1148c8`: healthy
- `i-0b8d95e454df5e0f0`: healthy

## Decision

Do not open another policy-list optimization.

Accepted low-rate soak confirms:

- policy list default is stable on both nodes
- ranking remains stable
- no rate limiting appears at about `0.8 rps`
- no health or ALB regression was observed

The next plausible performance investigation, if continuing, is search:

- `policy_search_filtered` was the top p95 on both nodes:
  - primary p95 `210.9ms`
  - secondary p95 `277.6ms`
- `policy_search_keyword` was second:
  - primary p95 `117.9ms`
  - secondary p95 `130.9ms`
- this matches the post-projection DB baseline where `policy_search_keyword_api_shape` still cost `166.438ms`

Recommended next step:

1. Stop code changes now.
2. Treat this as the accepted load/soak checkpoint.
3. If the next performance round is opened, start with search query instrumentation/planning, not policy list.
