# Final Report Submission Checklist 2026-07-16

## Purpose

This checklist closes the project documentation set before final report submission or presentation.

Latest report synchronization: `2026-07-18`. The original checklist was created on `2026-07-16`, but the accepted performance numbers below now reflect the post-category-remap final regression closeout.

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
| [final-report-performance-summary-2026-07-16.md](./performance/final-report-performance-summary-2026-07-16.md) | performance table source, updated with the 2026-07-18 final closeout values |
| [final-report-evidence-map.md](./final-report-evidence-map.md) | evidence mapping for features, tests, and figures |
| [final-report-figure-plan.md](./final-report-figure-plan.md) | figure list and placement |
| [final-report-references.md](./final-report-references.md) | reference list |
| [final-production-operations-runbook-2026-07-16.md](./core/final-production-operations-runbook-2026-07-16.md) | final production operations handoff |

## Consistency Checks

### Figures And Screenshots

Expected image assets:

- diagrams: [figure-01-system-architecture.png](./final-report-assets/diagrams/figure-01-system-architecture.png) through [figure-07-deployment-topology.png](./final-report-assets/diagrams/figure-07-deployment-topology.png)
- screenshots: [screen-08-main.png](./final-report-assets/screenshots/screen-08-main.png) through [screen-15-admin-dashboard.png](./final-report-assets/screenshots/screen-15-admin-dashboard.png)

Checked on `2026-07-18`:

- all diagram PNG files exist at `3200 x 1800`
- all screenshot PNG files exist at `1440 x 1050`
- figure numbers `1` through `15` match the working draft captions
- implementation screenshots belong under `제2장 제4절 주요 구현 내용`

### Performance Numbers

Use [final-report-performance-summary-2026-07-16.md](./performance/final-report-performance-summary-2026-07-16.md) as the official source for report-grade performance numbers. That document keeps the original `2026-07-16` file name for link stability, but its accepted table values were synchronized on `2026-07-18`.

Accepted report-grade values:

| Area | Report value |
| --- | --- |
| public read API | endpoint-only p95 `42ms` or below |
| auth flow | login p95 `200.7ms`, refresh p95 `47.7ms` |
| recommendation stored read | max `312.8ms` in the final flow |
| recommendation refresh | shared refresh `4065.0ms`, cached refresh `437.8ms`, personal refresh `4552.8ms` |
| chatbot message send | max `4225.4ms`, `POLICY_GROUNDED` 2 and `APPLICATION_COACHING` 1 |
| integrated user journey | `18/18` steps, total API time `8941.9ms`, non-AI `1861.6ms`, AI-backed `7080.4ms` |
| read-only load boundary | clean public point about `2.4 rps`; first 429 boundary about `4.8 rps` |

Do not reintroduce the older `2026-07-16` report-grade latency table into the current report draft. The current report uses the `2026-07-18` post-remap closeout values above.

### Functional Correctness

Use [phase-plan.md](./phase-plan.md) top entry and the final closeout artifacts as the latest correctness evidence. [final-functional-regression-check-2026-07-16.md](./performance/final-functional-regression-check-2026-07-16.md) remains historical evidence for the earlier closeout.

Accepted final regression values:

- `18/18` steps passed
- errors `0`
- rate-limit responses `0`
- final recommendation flow passed `6/6` steps
- stored recommendation read returned `20` rows
- category high-confidence remap candidates remained `0`
- chatbot flow passed 3 representative questions
- ALB target health remained `2` healthy targets
- DB waiting locks remained `0`
- JVM restart count remained `0`
- final no-cost ops check ended with `ok_count=9`, `fail_count=0`

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

- report draft and HWP paste draft use the synchronized `2026-07-18` performance numbers above
- final functional regression remains passed
- production post-deploy smoke remains passed
- no new code changes are pending
- cost-bearing HA/restore/distributed-load items are described as future work, not completed work

## Final Documentation Review 2026-07-18

Final review result:

- working draft and HWP paste draft use the same synchronized `2026-07-18` performance numbers
- reference markers used in the report body are `[1]` through `[11]`, and the reference section includes project links as `[12]` and `[13]`
- figure captions `1` through `15` have matching PNG assets
- diagram assets exist at `3200 x 1800`
- screenshot assets exist at `1440 x 1050`
- implementation screenshot insertion guide points to `제2장 제4절 주요 구현 내용`
- recent policy category normalization work is reflected in the report body and latest validation narrative
- no remaining stale insertion-location drift was found in the report asset guide or report drafts
