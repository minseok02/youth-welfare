# policy link quality audit runbook

## 목적

이 문서는 정책 상세 진입에서 실제로 쓸 수 있는 대표 링크가 source별로 얼마나 비어 있는지 compact하게 다시 읽는 절차입니다.

핵심 질문은 아래입니다.

1. `detail_url` 과 `reference_urls_json` 을 같이 봤을 때 완전히 비는 row가 어느 source에 몰려 있는가
2. broad parser failure인지, source contract 영향인지
3. 실제 정책 상세 CTA 품질 관점에서 먼저 review 할 source는 무엇인가

## 실행

```bash
bash deploy/smoke/run-local-policy-link-quality-audit.sh
```

server/RDS에서는 아래를 사용합니다.

```bash
ENV_FILE=.env.production SMOKE_DB_MODE=postgres \
bash deploy/smoke/run-local-policy-link-quality-audit.sh
```

## 요약 항목

- `missing_any_link_youth`
- `missing_any_link_bokjiro_local`
- `missing_any_link_bokjiro_central`
- `missing_any_link_gov24`
- `decision_class`

## 해석

- `LINK_REVIEW_PRIORITY`
  - broad regression보다 source contract 때문에 대표 링크가 비는 sample review가 더 중요합니다.
  - 현재 local 기준으로는 `YOUTH` missing-any-link row가 먼저 보입니다.
- `BASELINE_HEALTHY`
  - detail/reference 기준 broad 링크 품질 드리프트는 두드러지지 않습니다.
