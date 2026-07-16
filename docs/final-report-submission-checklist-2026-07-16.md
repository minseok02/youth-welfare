# Final Report Submission Checklist 2026-07-16

## Purpose

This checklist closes the project documentation set before final report submission or presentation.

It separates:

- report-ready claims
- operational evidence
- final functional regression evidence
- cost/risk items that were intentionally not executed

## Report Documents

| Document | Use |
| --- | --- |
| [final-report-working-draft.md](./final-report-working-draft.md) | main Markdown report draft |
| [final-report-hwp-paste-draft.txt](./final-report-hwp-paste-draft.txt) | HWP paste version |
| [final-report-performance-summary-2026-07-16.md](./performance/final-report-performance-summary-2026-07-16.md) | source of performance tables and report-ready text |
| [final-report-evidence-map.md](./final-report-evidence-map.md) | evidence mapping for features, tests, and figures |
| [final-report-figure-plan.md](./final-report-figure-plan.md) | figure list and placement |
| [final-report-references.md](./final-report-references.md) | reference list |
| [final-production-operations-runbook-2026-07-16.md](./core/final-production-operations-runbook-2026-07-16.md) | final production operations handoff |

## Consistency Checks

### Performance Numbers

Use [final-report-performance-summary-2026-07-16.md](./performance/final-report-performance-summary-2026-07-16.md) as the official source for report-grade performance numbers.

Accepted report-grade values:

| Area | Report value |
| --- | --- |
| public read API | endpoint-only average p95 below `75ms`; mixed worst p95 `190.7ms` |
| auth flow | login p95 `151.0ms`, refresh p95 `130.8ms` |
| recommendation stored read | max `116.4ms` |
| recommendation personal refresh | `3933.2ms` |
| chatbot message send | p95 `4113.8ms` |
| integrated user journey | `18/18` steps, total API time `8935.8ms`, non-AI `980.9ms`, AI-backed `7954.9ms` |
| read-only load boundary | clean public point about `2.4 rps`; first 429 boundary about `4.8 rps` |

Do not mix the later final functional regression latency (`11724.4ms`) into the report-grade performance table. That later run is correctness evidence after documentation/closeout work, not the official report-grade latency baseline.

### Functional Correctness

Use [final-functional-regression-check-2026-07-16.md](./performance/final-functional-regression-check-2026-07-16.md) as the final correctness evidence.

Accepted final regression values:

- `18/18` steps passed
- errors `0`
- rate-limit responses `0`
- recommendation refresh returned `40` rows
- stored recommendation read returned `20` rows
- bookmark on/off state was verified
- chatbot response was `POLICY_GROUNDED`
- ALB target health remained `2` healthy targets
- DB waiting locks remained `0`
- JVM restart count remained `0`

### Operations

Use [final-production-operations-runbook-2026-07-16.md](./core/final-production-operations-runbook-2026-07-16.md) for the final operations state.

Accepted operations claims:

- public DNS is Route53 public hosted zone alias to the ALB
- public path is Route53 -> ALB -> EC2 targets -> nginx -> Spring Boot
- primary and secondary EC2 targets are healthy behind ALB
- direct public EC2 `80/443` access is blocked
- primary scheduler is enabled and secondary scheduler is disabled
- RDS remains single-AZ and ElastiCache remains single-node by intentional cost/risk decision
- SNS email subscription and test notification were confirmed

## Items Intentionally Not Claimed

Do not claim:

- absolute maximum real-world user capacity
- multi-region load-test capacity
- RDS Multi-AZ failover validation
- ElastiCache automatic failover validation
- RDS restore rehearsal completion
- production webhook alert channel completion

Reason:

- those require extra AWS cost, operational risk, or a different distributed test setup
- the current measured capacity is a same-source synthetic boundary with rate limiting

## Final Pre-Submission Commands

Run from the production primary node or a matching repository checkout:

```bash
git status --short --branch
git diff --check
```

If production health needs to be rechecked:

```bash
cd /home/ubuntu/youth-welfare
RUN_ALB_TARGET_HEALTH=true bash deploy/smoke/run-prod-post-deploy-smoke.sh
```

If final correctness needs to be rechecked:

```bash
cd /home/ubuntu/youth-welfare
ENV_FILE=.env.runtime.production \
SMOKE_DB_MODE=postgres \
APP_BASE_URL=https://youthmoa.kr \
APP_HEALTH_URL=http://127.0.0.1:8082/actuator/health \
INTEGRATED_JOURNEY_ROOT=tmp/performance/final-functional-regression \
JOURNEY_REQUEST_DELAY_SECONDS=0.5 \
  bash deploy/performance/run-local-integrated-user-journey-baseline.sh
```

The final correctness command mutates bounded smoke data and may call OpenAI-backed paths.

## Final Decision

The project can be closed for report submission when:

- report draft and HWP paste draft use the report-grade performance numbers above
- final functional regression remains passed
- production post-deploy smoke remains passed
- no new code changes are pending
- cost-bearing HA/restore/distributed-load items are described as future work, not completed work
