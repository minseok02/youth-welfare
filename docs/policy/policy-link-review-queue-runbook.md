# policy link review queue runbook

## 목적

이 문서는 관리자 대시보드의 `정책 링크 review queue`를 실제로 처리할 때 쓰는 운영 기준입니다.

지금 이 queue는 broad parser bug를 찾기 위한 것이 아니라,
**현재 사용자에게 노출될 수 있는 `YOUTH` 링크 공백 정책**을 빠르게 triage 하기 위한 용도입니다.

## 어디서 보나

- admin API
  - `GET /api/admin/dashboard/policy-link-reviews?status=OPEN|REVIEWED|ALL`
  - `POST /api/admin/dashboard/policy-link-reviews/{serviceId}/review`
- admin dashboard
  - `정책 링크 review queue`

## 먼저 볼 기준

현재는 `MIXED_LINK_REVIEW_PRIORITY` 상태라, 아래 순서로 보는 게 맞습니다.

1. `지원금/급부형`
2. `공고/모집형`
3. `프로그램형`
4. `행사/문화형`
5. `기타`

이유:

- `지원금/급부형`은 실제 사용자가 상세 진입 후 바로 원문/신청 링크를 기대할 가능성이 큽니다.
- `공고/모집형`, `프로그램형`은 source contract상 대표 링크 없이 운영되는 케이스가 더 자주 섞입니다.
- `기타`는 운영/정책 성격이 섞여 있어 마지막에 묶어서 봐도 됩니다.

## bucket별 review 기준

### `지원금/급부형`

아래에 해당하면 **실제 링크 보완 우선순위가 높다**고 봅니다.

- `지원금`, `수당`, `장학금`, `이자 지원`, `응시료`, `바우처`, `급여`, `보조금`
- title만 봐도 신청/안내 원문이 있어야 자연스러운 정책
- 기관명이 구체적이고, 다른 source row나 외부 공고가 존재할 가능성이 높은 정책

권장 review note 예시:

- `급부형 정책, 대표 링크 보완 필요`
- `원문/신청 링크 확인 필요`
- `급부형인데 source 대표 링크 없음`

### `공고/모집형`

아래에 해당하면 **source contract성 page 부재 가능성**을 먼저 의심합니다.

- `모집`, `공고`, `선발`, `추가모집`, `참여자`, `수강생`
- 지자체가 시기별 모집 공고만 올리고 별도 대표 landing page를 두지 않는 케이스

권장 review note 예시:

- `모집형 공고, source contract상 대표 링크 부재 가능`
- `개별 공고형, 별도 링크 없는 source row로 보임`

### `프로그램형`

아래에 해당하면 `급부형`보다는 한 단계 낮게 보되, 실제 정보 접근성이 필요한지 확인합니다.

- `프로그램`, `교육`, `아카데미`, `캠프`, `멘토링`, `기획단`, `탐방`, `실험실`, `강좌`
- 운영 기관 소개형 row인지, 실제 참가 신청형 row인지 구분이 필요

권장 review note 예시:

- `프로그램형, 소개성 row인지 추가 확인`
- `참가 신청형이면 링크 보완 필요`

### `행사/문화형`

아래에 해당하면 **행사 안내성 row**일 가능성이 높습니다.

- `행사`, `축제`, `페스티벌`, `전시`, `공연`, `대회`

권장 review note 예시:

- `행사 안내형, source contract성 페이지 부재 가능`
- `행사형이라 별도 대표 링크 없음`

### `기타`

아래는 케이스별로 직접 판단합니다.

- title만으로 급부형/공고형/프로그램형이 명확하지 않음
- 운영성 row, 위원회/네트워크/시설 운영처럼 안내 성격이 강함

권장 review note 예시:

- `운영/안내성 row, 대표 링크 보완 우선순위 낮음`
- `기타 분류, 외부 원문 존재 여부 추가 확인 필요`

## 처리완료 기준

다음 중 하나면 `REVIEWED`로 닫아도 됩니다.

1. source contract상 대표 링크가 없어도 자연스럽다고 확인됨
2. 실제로 보완 가치가 높은 row라고 판단했고, 후속 보완 큐로 넘길 메모를 남김
3. duplicate/운영성/행사성 row라 즉시 보완 우선순위가 낮다고 판단됨

즉 `REVIEWED`는 “고쳤다”가 아니라
**운영자가 한 번 판단을 내렸고, note를 남겼다**는 뜻입니다.

## 메모 템플릿

- `급부형 정책, 대표 링크 보완 필요`
- `모집형 공고, source contract상 대표 링크 부재 가능`
- `프로그램형, 신청형 여부 추가 확인 필요`
- `행사 안내형, 대표 링크 없는 row로 유지`
- `운영/안내성 row, 보완 우선순위 낮음`

## 현재 해석

- `active_visible_youth_total=172`
- `benefit_support=30`
- `announcement_recruitment=12`
- `program_event=9`
- `event_culture=5`
- `other=108`
- `decision_class=MIXED_LINK_REVIEW_PRIORITY`

즉 지금은 한 가지 규칙으로 밀어붙이지 말고,
`급부형`과 `공고/프로그램형`, 그리고 `other` tail을 나눠서 review 하는 편이 맞습니다.
