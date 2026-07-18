# Final Report Performance Summary 2026-07-16

## Purpose

This document condenses the performance evidence into report-ready tables.

It should be used as the main source for the final report's performance section. The file name remains `2026-07-16` for link stability, but the accepted report table was synchronized on `2026-07-18` after the final policy category remap closeout. Detailed raw measurement records remain in the individual report-grade documents and the latest closeout artifacts.

## Measurement Context

- latest accepted measurement date: `2026-07-18`
- earlier report-grade baseline date: `2026-07-16`
- public service URL: `https://youthmoa.kr`
- topology: ALB -> 2 EC2 app nodes -> nginx -> Spring Boot -> RDS PostgreSQL / ElastiCache Valkey
- production OpenAI: enabled
- public read API tests: controlled low-concurrency ALB measurements
- auth/recommendation/chat/integrated journey tests: bounded test accounts with dedicated prefixes

Interpretation rules:

- Read API and auth latency are ordinary API latency.
- Recommendation refresh and chatbot message latency include OpenAI latency and cost.
- Capacity values are same-source synthetic load results, not a hard real-world maximum user count.
- 429 responses in load tests are expected rate-limit protection signals, not CPU/DB/ALB saturation by themselves.

## Current Report-Grade Results

| Area | Scope | Official result | Errors / rate limits | Main interpretation |
| --- | --- | ---: | ---: | --- |
| Public read API | policy list/search/suggestions/ranking/detail, public ALB sample | endpoint-only p95 `42ms` or below; latest ranking log p95 `20ms` after the outlier window cleared | `0` errors, `0` 429 in report read baseline | Normal public read traffic is consistently fast in the controlled sample. |
| Auth flow | login, refresh, profile read, logout across `12` accounts | login p95 `200.7ms`, refresh p95 `47.7ms` | `0` errors, `0` 429/A010 | Normal authentication path is stable below auth rate-limit boundary. |
| Recommendation flow | stored read, shared refresh, cached refresh, personal refresh | stored read max `312.8ms`; shared refresh `4065.0ms`; cached refresh `437.8ms`; personal refresh `4552.8ms` | `0` errors, `0` 429/R004 | Stored recommendation reads are fast; generation is AI-influenced and cost-bearing. |
| Chatbot flow | one account, 3 representative questions | message send `2411.5/3962.2/4225.4ms`; `POLICY_GROUNDED` 2, `APPLICATION_COACHING` 1 | `0` errors, `0` rate limits | Chat latency is dominated by OpenAI-backed answer generation and grounding. |
| Integrated user journey | signup -> login -> profile -> read API -> recommendation -> bookmark -> chat -> logout | `18/18` steps passed; total measured API time `8941.9ms`; non-AI `1861.6ms`; AI-backed `7080.4ms` | `0` errors, `0` rate limits | End-to-end journey is functionally stable; most measured latency remains AI-backed work. |
| Read-only capacity boundary | same-source public ALB mixed read profile | clean point about `2.4 rps`, worst p95 below `100ms`; first 429 boundary about `4.8 rps` | clean step `0` errors; boundary step returns 429 | Current limiting factor under this profile is configured rate-limit posture, not infrastructure saturation. |

## Improvement Highlights

These values come from documented optimization checkpoints. They are the safest improvement claims because the before/after scope was recorded with the same endpoint family.

| Area | Before | After | Reduction | Note |
| --- | ---: | ---: | ---: | --- |
| Ranking cold p95, local target | `1185.5ms` | `30.1ms` | `97.5%` | Redis precomputed ranking snapshot for `size=20`. |
| Ranking cold p95, public edge | `828.5ms` | `49.6ms` | `94.0%` | Public edge checkpoint after snapshot. |
| Ranking cold p95, earlier ALB cache-tail edge | `1893.9ms` | `49.6ms` | `97.4%` | Earlier ALB tail compared with accepted edge snapshot. |
| Final public clean read-load worst p95 | `1887.6ms` ranking-heavy short load p95 | `90.9ms` accepted clean public mixed-load worst p95 | `95.2%` | Directional capacity closeout comparison; scenario mix differs, so report as operational improvement, not strict A/B. |

## Initial Baseline vs Earlier 2026-07-16 Reference

The following comparison is useful for explaining the direction of improvement, but the conditions differ: the initial accepted baseline was internal loopback in May, while these reference values are public ALB measurements from the earlier `2026-07-16` report-grade run. Use this table as supporting context, not as the latest official performance table and not as a strict laboratory A/B result.

| Endpoint family | Initial p95 | Earlier 2026-07-16 reference p95 | Directional reduction |
| --- | ---: | ---: | ---: |
| Policy search keyword | `631.2ms` | `74.2ms` average endpoint-only p95 | `88.2%` |
| Policy search filtered | `534.3ms` | `38.0ms` average endpoint-only p95 | `92.9%` |
| Policy suggestions | `287.4ms` | `44.3ms` average endpoint-only p95 | `84.6%` |
| Policy ranking | `566.2ms` | `34.1ms` average endpoint-only p95 | `94.0%` |
| Policy list default | `56.8ms` | `41.1ms` average endpoint-only p95 | `27.6%` |

Policy detail is omitted from this comparison because the later detail p95 was measured through the public ALB and against a different first-policy sample. It remains fast in absolute terms, but it is not a good improvement-ratio candidate.

## AI vs Non-AI Latency

The integrated journey makes the current latency profile clear:

| Segment | Measured API time | Share |
| --- | ---: | ---: |
| Non-AI API steps | `1861.6ms` | about `20.8%` |
| AI-backed steps | `7080.4ms` | about `79.2%` |
| Total measured API time | `8941.9ms` | `100%` |

Interpretation:

- The non-AI API surface is no longer the main latency bottleneck in the measured journey.
- Recommendation generation and chatbot answer generation are intentionally AI-backed and therefore include external model latency.
- These AI-backed paths should be reported separately from ordinary read/auth API latency.

## Stability Evidence

Across report-grade measurements and post-checks:

- ALB target group stayed at `2` healthy targets.
- user-facing nginx 5xx stayed at `0` in post-checks.
- DB waiting locks stayed at `0`.
- DB active queries over 5 minutes stayed at `0`.
- JVM restart count stayed at `0`.
- log alert returned `ok` after AI-backed latency paths were classified separately from ordinary API p95.
- Direct EC2 public 80/443 access remained blocked; public traffic goes through ALB.

## Report-Ready Text

본 프로젝트는 운영 배포 환경에서 공개 ALB를 경유한 대표 성능 측정을 수행하였다. 최종 재측정 기준 정책 조회 및 검색 중심의 읽기 API는 오류와 429 응답 없이 endpoint-only p95가 42ms 이하로 확인되었다. 인증 흐름은 12개 계정 기준 로그인, 토큰 갱신, 프로필 조회, 로그아웃 전 단계가 성공했으며 로그인 p95는 200.7ms였다.

AI가 포함된 기능은 별도로 측정하였다. 추천 저장 조회는 312.8ms 이하로 응답했고, 추천 재계산과 챗봇 답변은 OpenAI 연동을 포함하므로 각각 수 초의 시간이 소요되었다. 통합 사용자 여정에서는 18개 단계가 모두 성공했고 총 측정 API 시간은 8941.9ms였으며, 이 중 비-AI API 구간은 1861.6ms, AI 기반 추천 및 챗봇 구간은 7080.4ms였다. 따라서 현재 지연의 주요 원인은 일반 API 처리보다 AI 기반 생성 작업에 있음을 확인하였다.

읽기 API 부하 측정에서는 동일 출처의 혼합 읽기 시나리오 기준 약 2.4 rps까지 오류 없이 처리되었고, 약 4.8 rps 지점에서 429 rate-limit 응답이 발생하였다. 측정 후 ALB 대상 2대는 모두 healthy 상태였고, 사용자 영향 5xx, DB 대기 lock, 5분 초과 active query, JVM 재시작은 확인되지 않았다. 따라서 관측된 한계는 인프라 포화가 아니라 현재 설정된 rate-limit 보호 정책에 의한 경계로 해석하였다.

## Source Documents

- [report-grade-read-api-measurement-2026-07-16.md](./report-grade-read-api-measurement-2026-07-16.md)
- [report-grade-auth-flow-measurement-2026-07-16.md](./report-grade-auth-flow-measurement-2026-07-16.md)
- [report-grade-recommendation-flow-measurement-2026-07-16.md](./report-grade-recommendation-flow-measurement-2026-07-16.md)
- [report-grade-chat-flow-measurement-2026-07-16.md](./report-grade-chat-flow-measurement-2026-07-16.md)
- [report-grade-integrated-user-journey-measurement-2026-07-16.md](./report-grade-integrated-user-journey-measurement-2026-07-16.md)
- [final-load-capacity-check-2026-07-16.md](./final-load-capacity-check-2026-07-16.md)
- [performance-baseline-current.md](./performance-baseline-current.md)
- latest closeout artifacts: `tmp/prod-post-deploy-smoke/20260718T175920Z`, `tmp/policy-category-suspect-review/20260718T175347Z`, `tmp/performance/recommendation-flow/20260718T175347Z`, `tmp/performance/chat-flow/20260718T175347Z`
