# Policy List Timing Instrumentation 2026-07-15

## Purpose

The post-snapshot baseline selected `GET /api/policies?page=0&size=20` as the next bottleneck.

This checkpoint adds timing instrumentation before another optimization so the next change targets the real slow segment, not a guessed one.

## Runtime State

- branch: `refactor/admin-dashboard-sections`
- instrumentation commit: `0dd5fac7562c33902c95f1d519e84c77fda3c939`
- public domain: `https://youthmoa.kr`
- ALB target group: both targets healthy after deployment
- primary EC2: `i-0b8d95e454df5e0f0`
- secondary EC2: `i-0e8a4cc599c1148c8`

The instrumentation does not change policy ordering, filters, cache keys, response shape, or ranking snapshot behavior.

## Instrumented Segments

Controller:

- client fingerprint resolution
- rate limit check
- policy list service call
- total controller time

Service:

- read repository
- presentation mapping
- total service time

Repository:

- active-only fast path rows query
- active count cache read
- active count DB fallback
- active count cache write
- general path total time

Presentation:

- bookmark lookup
- recommendation projection lookup
- region label lookup
- DTO build and page mapping

## Measurement Commands

Edge samples:

```bash
for i in $(seq 1 12); do
  curl -k -s -o /tmp/policy-list-edge.json \
    -w "edge_list sample=$i status=%{http_code} total_ms=%{time_total} ttfb_ms=%{time_starttransfer}\n" \
    'https://youthmoa.kr/api/policies?page=0&size=20'
  sleep 0.5
done | tee tmp/performance/policy-list-timing-instrumentation-20260715/edge-list-samples.txt
```

Primary loopback samples:

```bash
for i in $(seq 1 12); do
  curl -s -o /tmp/policy-list-primary.json \
    -w "primary_list sample=$i status=%{http_code} total_ms=%{time_total} ttfb_ms=%{time_starttransfer}\n" \
    'http://127.0.0.1:8082/api/policies?page=0&size=20'
  sleep 0.5
done | tee tmp/performance/policy-list-timing-instrumentation-20260715/primary-list-samples.txt
```

Secondary loopback samples were collected through SSM against `i-0e8a4cc599c1148c8`.

## Request Results

| Path | Samples | p50 | p95 | Max | Notes |
| --- | ---: | ---: | ---: | ---: | --- |
| edge `https://youthmoa.kr` | 12 | 121.2ms | 728.7ms | 728.7ms | first sample was the tail |
| primary loopback | 12 | 110.1ms | 197.3ms | 197.3ms | warm samples mostly 80ms to 141ms after first few |
| secondary loopback | 12 | 104.4ms | 171.7ms | 171.7ms | warm samples mostly 82ms to 109ms |

Raw artifacts:

- `tmp/performance/policy-list-timing-instrumentation-20260715/edge-list-samples.txt`
- `tmp/performance/policy-list-timing-instrumentation-20260715/primary-list-samples.txt`

## App Timing Findings

Primary first tail:

| Segment | Time |
| --- | ---: |
| repository total | 195ms |
| repository rows query | 162ms |
| count DB fallback | 19ms |
| presentation total | 135ms |
| projection lookup | 79ms |
| region label lookup | 49ms |
| service total | 373ms |
| controller total | 404ms |

Secondary first tail:

| Segment | Time |
| --- | ---: |
| repository total | 203ms |
| repository rows query | 198ms |
| count source | Redis hit |
| presentation total | 222ms |
| projection lookup | 155ms |
| region label lookup | 64ms |
| service total | 451ms |
| controller total | 520ms |

Warm repeated pattern:

- repository usually fell to about `14ms` to `40ms`
- region labels usually fell to about `2ms` to `10ms`
- projection lookup repeatedly stayed about `47ms` to `114ms`
- presentation time was mostly projection time
- controller rate-limit time was usually small, except one secondary first-tail sample at `61ms`

## Interpretation

The first cold/tail request is mixed:

- rows query can spike to about `160ms` to `200ms`
- region label lookup can add about `50ms` to `65ms`
- projection lookup can add about `80ms` to `155ms`
- rate-limit check can occasionally add visible time

The sustained warm cost is narrower:

- repository rows/count are not the main repeated cost
- region lookup becomes small after warmup
- projection lookup is the dominant repeated app-side cost for the list response

The edge first sample (`728.7ms`) is higher than the primary controller log (`404ms`) and secondary controller log (`520ms`). That remaining gap is outside the measured app substeps, so keep ALB/nginx/network/serialization as secondary observation areas. However, the repeated app-side bottleneck is already clear enough to choose the next code-level investigation.

## Next Recommendation

Do not optimize the default list query first.

Next code-level work should inspect and optimize the list presentation projection lookup path, specifically the dependency used by `PolicyPresentationReadService` to attach recommendation projection data to the 20 listed policies.

Expected improvement range before implementation:

| Case | Current repeated cost | Plausible target | Expected endpoint effect |
| --- | ---: | ---: | --- |
| warm list projection lookup | 47ms to 114ms | under 20ms if query/cache shape is fixed | about 30ms to 90ms lower per warm list request |
| first-tail projection lookup | 79ms to 155ms | under 30ms if the same fix applies cold | about 50ms to 125ms lower on app-side first tail |

This estimate excludes the separate first-tail rows query spike and edge/app gap. Those should remain follow-up checks if the projection fix does not materially lower p95.

## Validation For Next Change

Before changing the projection path:

1. Inspect the projection repository query shape and indexes.
2. Check whether the list path can use one batch query keyed by policy IDs.
3. Confirm the response still returns the same projection fields for the same 20 policies.

After changing it:

1. Run the affected unit tests.
2. Re-run 12 edge, primary loopback, and secondary loopback samples.
3. Compare `[PolicyListPresentationTiming] projectionMs` before and after.
4. Smoke `root`, `policy list`, `policy search`, and `ranking`.
5. Keep both ALB targets healthy.
