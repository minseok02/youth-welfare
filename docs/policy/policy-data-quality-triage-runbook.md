# policy data quality triage runbook

## 목적

이 문서는 `정책 오류 제보`, `정책 링크 review`, `정책 중복 review` 를 운영자가 어떤 순서로 소화할지 한 장으로 고정합니다.

지금 목적은 새로운 품질 규칙을 계속 만드는 것이 아니라,
이미 만든 queue를 **작게, 반복 가능하게 줄이는 운영 루틴**을 만드는 것입니다.

## 우선순위

매일 또는 주기적으로 볼 때는 아래 순서를 기본값으로 둡니다.

1. `정책 오류 제보`
2. `정책 링크 review`
3. `정책 중복 review`

이유:

- `정책 오류 제보`는 실제 사용자 체감 문제일 가능성이 가장 큽니다.
- `정책 링크 review`는 현재 노출 가능한 `YOUTH` 링크 공백을 줄이는 작업입니다.
- `정책 중복 review`는 중요하지만, false positive와 source contract 해석이 더 많이 섞입니다.

## queue별 기본 기준

### 1. 정책 오류 제보

먼저 확인할 것:

- 지역 정보가 다릅니다
- 신청 기간이 다릅니다
- 자격조건 설명이 다릅니다
- 링크나 원문이 열리지 않습니다
- 중복 정책 같습니다

기본 원칙:

- `REVIEWED`는 “수정 완료”가 아니라 “운영자가 확인하고 메모를 남겼다”는 뜻입니다.
- 실제 source/data 수정은 후속 트랙으로 분리합니다.

### 2. 정책 링크 review

기본 순서:

1. `지원금/급부형`
2. `공고/모집형`
3. `프로그램형`
4. `행사/문화형`
5. `기타`

판단 기준:

- `지원금/급부형`
  - 실제 대표 링크 보완 가치가 가장 높습니다.
- `공고/모집형`, `프로그램형`
  - source contract상 대표 링크 부재 가능성을 먼저 봅니다.
- `행사/문화형`, `기타`
  - 안내성 row인지 먼저 보고, 보완 우선순위가 낮으면 note만 남기고 닫습니다.

### 3. 정책 중복 review

기본 순서:

1. `YOUTH exact duplicate`
2. `YOUTH mirror/channel variant`
3. `BOKJIRO_LOCAL title-only false positive`
4. `date/contract drift tail`

판단 기준:

- `YOUTH exact duplicate`
  - 같은 기관
  - 같은 기간
  - 같은 URL
  - 반복이면 진짜 수집 중복 후보로 먼저 봅니다.
- `YOUTH mirror/channel variant`
  - 같은 기관
  - 같은 기간
  - URL만 다르면 channel/mirror variant로 메모를 남기고 닫습니다.
- `BOKJIRO_LOCAL`
  - title만 같은 경우 false positive가 많아서 기본값은 `지역별 개별 사업 유지` 입니다.

## 1회 처리량 기준

한 번에 너무 많이 닫지 않습니다.

- `정책 오류 제보`: `3~5건`
- `정책 링크 review`: `5건`
- `정책 중복 review`: `5건`

이유:

- 운영 note 품질을 유지해야 합니다.
- 처리 기준이 흔들릴 때 바로 수정하기 쉽습니다.

## review note 기본값

### 정책 오류 제보

- `정책 정보 불일치 확인, source row 재검토 필요`
- `정책 링크/기간 정보 확인 필요`
- `지역/기관 차이 확인 필요`

### 정책 링크 review

- `급부형 정책, 대표 링크 보완 필요`
- `모집형 공고, source contract상 대표 링크 부재 가능`
- `프로그램형, 소개성 row인지 추가 확인`
- `행사 안내형, source contract상 대표 링크 부재 가능`
- `운영/안내성 row, 보완 우선순위 낮음`

### 정책 중복 review

- `수집 중복으로 판단, 동일 기관·동일 기간·동일 URL 반복`
- `mirror/channel variant로 판단, 동일 기관·동일 기간 반복이며 대표 링크만 상이`
- `지역별 개별 사업으로 유지: title-only false positive 가능성 높음`
- `기관/지역 차이 확인 필요`

## 현재 권장 운영 루프

1. admin dashboard attention feed를 본다.
2. `정책 오류 제보` open 건을 먼저 본다.
3. `정책 링크 review`는 `지원금/급부형`부터 5건 처리한다.
4. `정책 중복 review`는 `YOUTH exact/mirror` 또는 `BOKJIRO_LOCAL false positive` 중 하나를 5건 처리한다.
5. 처리 후 남은 open count와 대표 note만 짧게 남긴다.

## reopen 기준

raw audit 숫자만으로 broad parser rewrite나 대량 보정을 열지 않습니다. 아래 중 하나가 1 이상일 때만 운영 review를 재개합니다.

- `policy_error_open_reports`
- `policy_duplicate_open_groups`
- `policy_link_open_reviews`

raw duplicate/link 잔량은 데이터 수집 특성과 source contract가 섞인 후보군입니다. 실제 운영 작업은 `OPEN` queue로 승격된 항목만 이 문서의 우선순위대로 처리합니다.

## 현재 server/RDS 기준

2026-06-21 최신 policy data triage observation 기준으로 실제 운영 queue는 닫혀 있습니다.

- `policy_duplicate_open_groups=0`
- `policy_duplicate_open_rows=0`
- `policy_link_open_reviews=0`
- `decision_class=REVIEW_QUEUE_CLOSED_RAW_BACKLOG_REMAINS`

raw audit에는 아래 후보가 남아 있지만, 이 값만으로 새 review 작업을 열지 않습니다.

- `duplicate_groups_youth=85`
- `duplicate_groups_bokjiro_local=59`
- `active_visible_youth_total=161`

운영자는 `OPEN` queue가 다시 생길 때만 위 우선순위 루프를 재개합니다.

## 관련 문서

- [policy-link-review-queue-runbook.md](./policy-link-review-queue-runbook.md)
- [policy-duplicate-review-runbook.md](./policy-duplicate-review-runbook.md)
- [policy-link-review-sample-audit-runbook.md](./policy-link-review-sample-audit-runbook.md)
- [policy-youth-duplicate-candidate-audit-runbook.md](./policy-youth-duplicate-candidate-audit-runbook.md)
