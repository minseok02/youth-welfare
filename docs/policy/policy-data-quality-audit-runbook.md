# policy data quality audit runbook

## 목적

이 문서는 지역 외 정책 데이터 품질 축을 compact하게 다시 읽는 절차입니다.

현재 핵심 질문은 아래입니다.

1. source contract 때문에 비는 필드와 실제 품질 이슈를 어떻게 구분할 것인가
2. 지금 운영자가 먼저 봐야 할 데이터 품질 큐는 무엇인가
3. 중복 title/host 묶음이 실제 리뷰 대상 수준으로 남아 있는가

## 실행

```bash
bash deploy/smoke/run-local-policy-data-quality-audit.sh
```

server/RDS에서는 아래를 사용합니다.

```bash
ENV_FILE=.env.production SMOKE_DB_MODE=postgres \
bash deploy/smoke/run-local-policy-data-quality-audit.sh
```

## 요약 항목

- `missing_detail_url_youth`
- `missing_apply_period_gov24`
- `missing_apply_period_bokjiro_local`
- `missing_apply_period_bokjiro_central`
- `missing_apply_period_youth`
- `missing_host_org_bokjiro_local`
- `inactive_status_gov24`
- `inactive_status_youth`
- `duplicate_groups_youth`
- `duplicate_groups_bokjiro_local`
- `decision_class`

## 해석

- `DUPLICATE_REVIEW_PRIORITY`
  - 현재 품질 이슈 중에서 실제 리뷰 가치가 큰 큐는 duplicate title/host groups 입니다.
  - field missing은 source contract 영향이 더 커서 broad parser failure로 읽지 않습니다.
- `STRUCTURAL_GAPS_ONLY`
  - 현재 보이는 누락은 주로 source contract 수준이고, 즉시 리뷰할 broad data regression은 두드러지지 않습니다.
