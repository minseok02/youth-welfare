# policy application period quality audit runbook

## 목적

이 문서는 신청기간 필드를 source contract 누락과 실제 status/date 이상값으로 분리해서 읽는 절차입니다.

핵심 질문은 아래입니다.

1. 신청기간 누락이 source contract 영향인지 broad parser failure인지
2. `ACTIVE/UPCOMING` 인데 이미 마감된 row가 남아 있는지
3. `CLOSED` 인데 미래 마감일을 가진 row가 있는지

## 실행

```bash
bash deploy/smoke/run-local-policy-application-period-quality-audit.sh
```

server/RDS에서는 아래를 사용합니다.

```bash
ENV_FILE=.env.production SMOKE_DB_MODE=postgres \
bash deploy/smoke/run-local-policy-application-period-quality-audit.sh
```

## 요약 항목

- `missing_any_youth`
- `missing_any_bokjiro_local`
- `missing_any_bokjiro_central`
- `missing_any_gov24`
- `invalid_range_total`
- `active_past_end_youth`
- `active_past_end_gov24`
- `active_past_end_youth_future_end_tail`
- `active_past_end_youth_true_review`
- `active_past_end_gov24_true_review`
- `closed_future_end_total`
- `decision_class`

## 해석

- `STATUS_DATE_REVIEW_PRIORITY`
  - 단순 누락보다 status/date 불일치가 더 actionable 합니다.
  - `active_past_end_*_true_review > 0` 이면 `ACTIVE/UPCOMING` + 과거 `apply_end_date` row를 먼저 review 합니다.
  - 이 값이 `0` 이고 `closed_future_end_total > 0` 이면, 실제 남은 조치 대상은 `CLOSED + 미래 apply_end_date` 입니다.
- `EXPECTED_WINDOW_CLOSED_TAIL`
  - `ACTIVE/UPCOMING + 과거 apply_end_date` 가 남아 보여도, `end_date` 가 아직 미래인 source tail 입니다.
  - 현재 사용자-facing `ACTIVE_ONLY` 검색/목록에서는 이미 숨겨지므로 broad 장애로 보기보다 bounded source 해석 이슈로 읽는 편이 맞습니다.
- `SOURCE_CONTRACT_DOMINANT`
  - broad 문제보다 source contract 누락이 주된 상태입니다.
