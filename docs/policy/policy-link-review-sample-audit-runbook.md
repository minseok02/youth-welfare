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

- `NO_ACTIVE_VISIBLE_LINK_REVIEW_CANDIDATES`
  - 현재 노출되는 `YOUTH` 링크 공백 후보가 없습니다.
  - bucket 우선순위를 잡지 않고 review queue를 새로 열지 않습니다.
- `BENEFIT_LINK_FIX_PRIORITY`
  - 현재 노출되는 링크 공백 후보에서 급부/지원형 비중이 가장 큽니다.
  - 실제 CTA 보완 review를 먼저 보는 편이 맞습니다.
- `CONTRACT_STYLE_REVIEW_PRIORITY`
  - 공고/모집/프로그램형 비중이 더 큽니다.
  - source contract성 페이지 부재와 실제 누락을 먼저 구분하는 편이 맞습니다.
- `MIXED_LINK_REVIEW_PRIORITY`
  - 급부형과 공고/프로그램형이 섞여 있습니다.
  - `정책 링크 review queue`를 bucket 기준으로 나눠 review 하는 편이 맞습니다.

## 현재 메모

- 최근 운영 review 기준으로 `출산가정 산후조리비용 지원`, `임신축하금 지원사업`, `군복무 청년 상해보험 가입` 같은 제목은 `other` 가 아니라 `지원금/급부형` 으로 보는 편이 맞습니다.
- 따라서 bucket 분류는 `축하금`, `조리비(용) 지원`, `보험 가입/지원` 같은 급부형 title 패턴을 포함하도록 유지합니다.

## 현재 server/RDS 기준

2026-06-09 최신 server/RDS sample 기준:

- `active_visible_youth_total=172`
- `benefit_support_count=33`
- `announcement_recruitment_count=13`
- `program_event_count=10`
- `event_culture_count=5`
- `other_count=111`
- `decision_class=MIXED_LINK_REVIEW_PRIORITY`

다만 policy data triage wrapper 기준 `policy_link_open_reviews=0` 이므로, 현재는 raw 후보 관찰 단계입니다.
새 `OPEN` link review가 생길 때만 이 bucket 순서로 다시 처리합니다.
