# policy link review sample audit runbook

## 목적

이 문서는 `YOUTH active visible missing link` 후보를 제목 성격 기준으로 먼저 묶어서,
운영자가 `정책 링크 review queue`를 어떤 순서로 볼지 정하는 절차입니다.

핵심 질문은 아래입니다.

1. 현재 노출될 수 있는 `YOUTH` 링크 공백 후보가 주로 어떤 성격인가
2. `공고/모집형`, `프로그램형`, `지원금/급부형` 중 무엇이 많은가
3. source contract review를 먼저 할지, 실제 CTA 보완 review를 먼저 할지

## 실행

```bash
bash deploy/smoke/run-local-policy-link-review-sample-audit.sh
```

server/RDS에서는 아래를 사용합니다.

```bash
ENV_FILE=.env.production SMOKE_DB_MODE=postgres \
bash deploy/smoke/run-local-policy-link-review-sample-audit.sh
```

## 요약 항목

- `active_visible_youth_total`
- `announcement_recruitment_count`
- `benefit_support_count`
- `program_event_count`
- `event_culture_count`
- `other_count`
- `decision_class`

## 해석

- `BENEFIT_LINK_FIX_PRIORITY`
  - 현재 노출되는 링크 공백 후보에서 급부/지원형 비중이 가장 큽니다.
  - 실제 CTA 보완 review를 먼저 보는 편이 맞습니다.
- `CONTRACT_STYLE_REVIEW_PRIORITY`
  - 공고/모집/프로그램형 비중이 더 큽니다.
  - source contract성 페이지 부재와 실제 누락을 먼저 구분하는 편이 맞습니다.
- `MIXED_LINK_REVIEW_PRIORITY`
  - 급부형과 공고/프로그램형이 섞여 있습니다.
  - `정책 링크 review queue`를 bucket 기준으로 나눠 review 하는 편이 맞습니다.
