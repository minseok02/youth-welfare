# policy host org quality audit runbook

## 목적

이 문서는 기관명 품질을 `host_org`, `operating_org` 기준으로 다시 읽는 절차입니다.

핵심 질문은 아래입니다.

1. 기관명 공백이 broad parser failure인지 source contract 특성인지
2. `host_org` 없이 `operating_org` 만 내려오는 source가 어디인지
3. placeholder 기관명이 실제 노출 품질을 해치고 있는지

## 실행

```bash
bash deploy/smoke/run-local-policy-host-org-quality-audit.sh
```

server/RDS에서는 아래를 사용합니다.

```bash
ENV_FILE=.env.production SMOKE_DB_MODE=postgres \
bash deploy/smoke/run-local-policy-host-org-quality-audit.sh
```

## 요약 항목

- `missing_host_bokjiro_local`
- `missing_host_youth`
- `missing_operating_youth`
- `missing_operating_bokjiro_local`
- `same_host_operating_youth`
- `same_host_operating_gov24`
- `placeholder_host_total`
- `decision_class`

## 해석

- `SOURCE_CONTRACT_DOMINANT`
  - broad parser failure보다 source contract 해석 이슈가 큽니다.
  - 특히 `BOKJIRO_LOCAL` 은 `host_org` 가 비고 `operating_org` 만 내려오는 구조로 읽는 편이 맞습니다.
- `PLACEHOLDER_HOST_REVIEW_PRIORITY`
  - 실제 placeholder 기관명이 누적돼 있으면 sample review가 먼저입니다.
