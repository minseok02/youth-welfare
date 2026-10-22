# 로컬 검증 문서 목차

실행 위치가 로컬이어도 원격 앱·RDS·외부 API를 대상으로 할 수 있습니다. [환경 안내](environment-guide.md)에서 대상과 부작용을 먼저 확인합니다.

## 빠른 검사

- [testing.md](testing.md): backend 단위/통합, frontend 단위/lint/build, 브라우저와 실제 SMTP의 구분.
- `deploy/smoke/preflight-integration-runtime.sh`: 통합 테스트용 로컬 PostgreSQL/Redis와 계정 준비 상태.
- 문서 정리: 로컬 링크·앵커·문서 포인터·관련 계약 테스트·`git diff --check`.

## 앱을 사용하는 검사

| 목적 | 진입점 | 영향 |
| --- | --- | --- |
| auth/session | [인증 체크리스트](../auth/auth-operation-checklist.md), `run-local-auth-session-smoke.sh` | 로그인·세션·무효화 상태 변경 가능 |
| API별 확인 | [runtime-api-smoke-commands.md](runtime-api-smoke-commands.md) | 조회와 write 명령을 절별로 확인 |
| 브라우저 흐름 | [프론트 QA](../frontend/frontend-qa-docs-index.md) | 가입·프로필·북마크·문의 등 변경 가능 |
| schema/credential/date drift | [server-runtime-drift-checklist.md](server-runtime-drift-checklist.md) | 로컬 볼륨과 서버 RDS의 절차가 다름 |
| 운영 상태 | [ops baseline](ops-baseline-runbook.md), [관측](ops-observation-runbook.md) | 명령별 인증·DB 조회·artifact 생성 확인 |
| 정책 품질 | [quality summary](../policy/policy-quality-summary-runbook.md), [admin runtime](../policy/policy-admin-runtime-runbook.md) | audit와 rebuild/backfill을 구분 |
| Gov24 | [runtime audit](../policy/policy-gov24-runtime-audit-runbook.md) | 조회와 실제 수집 호출을 구분 |
| 추천 | [추천 목차](../recommendation/recommendation-docs-index.md), [CTR readiness](../recommendation/recommendation-ctr-readiness-runbook.md) | 합성 seed·refresh·클릭과 실제 사용자 관측을 구분 |
| 성능 | [성능 목차](../performance/performance-docs-index.md) | 운영 부하·외부 호출·관측 시점 확인 |

## 전체 validation wrapper

`deploy/smoke/run-local-validation-from-env.sh --quick|--full`은 env·admin 계정·앱 URL을 정규화하는 기존 진입점입니다. `run-local-validation-suite.sh`는 설정을 이미 정규화한 경우의 보조 진입점입니다. 두 wrapper 모두 기본 단위 테스트와 다르며 상태 변경 검사를 포함할 수 있습니다.

운영에서 로컬 기본 `.env`나 DB 자동 선택에 기대지 않습니다. 서버 명령은 [배포 절차](../deployment.md)를 따릅니다.

## 챗봇 runtime 검사

- 대화 branch/후속 질의: `deploy/smoke/run-local-chat-followup-scenario-audit.sh`
- 대화·코칭·snapshot: `deploy/smoke/run-local-chat-continuity-coaching-smoke.sh`
- 코칭 링크: `deploy/smoke/run-local-chat-application-coaching-matrix-audit.sh`
- 운영 표본: `deploy/smoke/run-local-chat-real-user-quality-sample-audit.sh`
- 운영 지표: `deploy/smoke/run-local-chat-observability-audit.sh`

follow-up/coaching 검사는 대표 정책 corpus에 의존합니다. `INSUFFICIENT_POLICY_CORPUS` skip을 정상 품질 검증으로 해석하지 않습니다. 실제 호출·저장·AI 비용은 각 스크립트 범위를 확인합니다.

## 데모와 기록

[demo-scenario.md](demo-scenario.md)는 발표·기능 검수용 시나리오이며 현재 운영 상태의 검증 결과가 아닙니다. 실행한 범위와 미실행 이유는 [phase-plan.md](../phase-plan.md)에 남깁니다.
