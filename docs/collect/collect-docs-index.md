# 수집 문서 목차

[현재 수집 동작](collect-current-state.md)과 [실행·장애 판정](collect-ops.md)을 먼저 확인합니다. [환경 안내](../core/environment-guide.md)로 로컬 Docker·로컬 앱/RDS·서버를 구분하고, 수집/repair의 쓰기와 외부 호출을 조회용 감사에 섞지 않습니다.

## 목적별 진입점

| 목적 | 문서 |
| --- | --- |
| Entry·저장·list/detail·quota/config·YOUTH 링크/지역 | [현재 동작](collect-current-state.md) |
| 실행 전 확인·상한·부분 실패·정상/장애·복구 기록 | [수집 운영](collect-ops.md) |
| Scheduled list diff·forced detail·요일 rotation의 순서/예산 | [detail 실행 계약](collect-detail-execution-contract.md) |
| 외부 API 재호출 없이 DB 저장 이력 대조 | [external API DB smoke](collect-external-api-smoke-runbook.md) |
| Scheduled/rotation/manual·latest run·실패/partial/circuit | [governance 관측](collect-governance-observation-runbook.md) |
| Retry/rate-limit/duplicate guard·empty snapshot·설정 drift | [resilience audit](collect-source-resilience-audit-runbook.md) |
| YOUTH zipCd 누락과 전국형 해석·지역 의심 표본 구분 | [regionless audit](youth-regionless-audit-runbook.md) |
| Gov24 coverage·shape·missing fact | [Gov24 runtime audit](../policy/policy-gov24-runtime-audit-runbook.md) |
| Retrieval/category의 dataset/scenario/gate·분포 | [정책 quality summary](../policy/policy-quality-summary-runbook.md) |

DB 저장 감사는 `run-local-collect-external-api-smoke.sh`, async Gov24 검증은 `run-local-gov24-async-collect-smoke.sh`입니다. 후자는 실제 수집을 유발하므로 DB 감사와 같은 read-only 점검으로 실행하지 않습니다.

External DB smoke는 api_sync_logs·raw·정책/detail·taxonomy/summary/fact·runtime status·execution lock을 대조합니다. NO_COLLECT_HISTORY, 저장/sidecar parity와 detail/support coverage mismatch, 실제 실패를 나누며 앱 DB와 점검 DB가 같은지 먼저 확인합니다.

## 같이 볼 계약과 기록

- [Runtime API](../core/runtime-api-smoke-commands.md), [테스트](../core/testing.md).
- [Gov24 데이터·내부 코드·소비](../policy/policy-gov24-lane-closeout.md), [작업/검증](../policy/policy-gov24-implementation-checklist.md).
- 실행·장애는 [incident 템플릿](collect-incident-template.md), 현재 변경/검증은 [작업 현황](../phase-plan.md), 재사용할 원인·해결은 [문제 해결 기록](../core/troubleshooting-log.md)에 남깁니다.

과거 closeout 수치와 당시 local-first 우선순위는 현재 계약이나 서버 상태를 대신하지 않습니다. 특히 snapshot 테이블 누락·부분 지원조건 실패·embedding 미갱신은 [현재 동작](collect-current-state.md#현재-미해결-문제)의 별도 과제입니다.
