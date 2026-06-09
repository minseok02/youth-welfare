# policy youth duplicate candidate audit runbook

## 목적

이 문서는 `YOUTH` duplicate queue를 다시 볼 때, broad `title + host_org` 묶음 전체가 아니라
**실제 수집 중복 후보**를 먼저 좁히기 위한 기준입니다.

핵심은 아래 둘을 분리하는 것입니다.

- `BOKJIRO_LOCAL` 의 title 기준 false positive
- `YOUTH` 의 같은 기관/같은 기간/같은 URL 반복

## 실행

로컬:

```bash
bash deploy/smoke/run-local-youth-duplicate-candidate-audit.sh
```

server/RDS:

```bash
ENV_FILE=.env.production SMOKE_DB_MODE=postgres \
bash deploy/smoke/run-local-youth-duplicate-candidate-audit.sh
```

## 분류 기준

이 audit는 `YOUTH` duplicate group을 세 bucket으로 나눕니다.

1. `exact_duplicate_candidate`
- 같은 `title`
- 같은 `host_org`
- 같은 `operating_org`
- 같은 `apply_start_date / apply_end_date`
- 같은 `detail_url`

가장 먼저 review 해야 할 진짜 수집 중복 후보입니다.

2. `mirror_or_channel_variant_candidate`
- 같은 `title`
- 같은 `host_org`
- 같은 `operating_org`
- 같은 `apply_start_date / apply_end_date`
- `detail_url` 만 여러 개

같은 정책이 미러/채널 URL로 중복 수집됐는지 보는 bucket입니다.

3. `date_or_contract_drift_candidate`
- 위 둘에 안 들어가는 나머지
- 날짜가 서로 다르거나 source contract 차이로 보이는 경우

이 bucket은 바로 dedupe 하지 말고 tail review로 보는 게 맞습니다.

## 운영 해석

현재 duplicate review에서 중요한 건 다음입니다.

- `BOKJIRO_LOCAL`
  - title 기준 false positive가 많음
  - 기본값은 `지역별 개별 사업 유지`
- `YOUTH`
  - 같은 기관 + 같은 기간 + 같은 URL 반복이면 진짜 수집 중복 가능성이 높음
  - URL만 다르면 mirror/channel variant인지 추가 확인

즉 queue를 볼 때는 아래 순서가 맞습니다.

1. `YOUTH exact_duplicate_candidate`
2. `YOUTH mirror_or_channel_variant_candidate`
3. `BOKJIRO_LOCAL` 과 `YOUTH` tail review

## 판단 메모 예시

- `수집 중복으로 판단, 동일 기관·동일 기간·동일 URL 반복`
- `수집 중복 의심, mirror/channel URL 변형`
- `지역별 개별 사업으로 유지`
- `date drift/source contract 차이로 유지`

## 현재 판단

- `BOKJIRO_LOCAL` 은 title-only duplicate를 바로 dedupe 후보로 보면 안 됩니다.
- `YOUTH` 는 same-org/same-period 반복이 있으면 별도 true duplicate candidate audit를 먼저 보고 판단하는 편이 맞습니다.

## 현재 server/RDS 기준

2026-06-09 최신 server/RDS duplicate candidate audit 기준:

- `duplicate_groups_total=69`
- `duplicate_rows_total=142`
- `exact_duplicate_groups=12`
- `exact_duplicate_rows=24`
- `mirror_variant_groups=16`
- `mirror_variant_rows=32`
- `date_or_contract_drift_groups=41`
- `date_or_contract_drift_rows=86`
- `decision_class=YOUTH_TRUE_DUPLICATE_REVIEW_PRIORITY`

다만 policy data triage wrapper 기준 `policy_duplicate_open_groups=0`, `policy_duplicate_open_rows=0` 이므로,
현재는 raw 후보 관찰 단계입니다. 새 `OPEN` duplicate queue가 생길 때만 exact -> mirror -> drift 순서로 review를 재개합니다.
