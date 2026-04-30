# `compat_unified_category` 저장 / 계산 정책

이 문서는 canonical 정규화 전환 중

- `compat_unified_category`

를 계속 저장 필드로 둘지,
아니면 read-model 계산값으로만 바꿀지 고정합니다.

관련 문서:

- [policy-normalization-recommendation-read-model.md](./policy-normalization-recommendation-read-model.md)
- [policy-normalization-bridge-rules.md](./policy-normalization-bridge-rules.md)
- [policy-normalization-compat-category-drift-inventory.md](./policy-normalization-compat-category-drift-inventory.md)
- [policy-normalization-compat-other-youth-major-policy.md](./policy-normalization-compat-other-youth-major-policy.md)

## 결론

**현재 phase에서는 `compat_unified_category` 를 저장 필드로 유지합니다.**

구체적으로는 아래 두 층을 같이 둡니다.

1. `welfare_services.unified_category`
   - legacy 추천/응답 계약용 compat 값
2. `service_taxonomies.compat_unified_category_code/label`
   - canonical sidecar summary 안에 보존하는 compat mirror

즉 지금은 `compat_unified_category` 를
read-model에서 매번 재계산하는 방식으로 바꾸지 않습니다.

## 왜 저장 필드로 유지하는가

### 1. 현재 제품 계약이 이미 `compat` 위에 서 있다

지금 추천 파이프라인에서

- `DefaultPriorityMatcher`
- priority bonus
- response category
- replay/sample 비교 기준

은 모두 `compat_unified_category` 의미를 전제로 움직입니다.

여기서 compat를 read-model 계산값으로 바꾸면,
canonical summary나 bridge 규칙의 작은 수정이
곧바로 현재 제품 계약 변경으로 이어질 수 있습니다.

### 2. canonical summary가 아직 compat를 완전히 대체하지 못한다

local inventory 기준으로:

- `compat_unified_category_label`: `3634 / 3634`
- `youth_major_label`: `2248 / 3634`
- `youth_mid_label`: `0 / 3634`
- `gov24_service_field_label`: `0 / 3634`

즉 canonical summary는 아직 source coverage와 축 안정화가 덜 끝났습니다.

이 상태에서 compat를 계산-only로 돌리면,
빈 summary나 future bridge 보정이
추천 의미를 조용히 흔들 가능성이 큽니다.

### 3. `compat=기타 + canonical youth_major 채움` 집합이 아직 정책 대상이다

현재도 `compat=기타` 인데 canonical `youth_major` 가 채워진 `YOUTH` row가 남아 있고,
이 집합은

- 자동 override 금지
- `교육`만 narrow experiment 후보
- `참여권리` subset bridge는 future candidate

정도로만 정리돼 있습니다.

즉 canonical layer만으로 compat를 역산하는 규칙은
아직 제품 계약으로 고정되지 않았습니다.

### 4. writer와 read-model을 동시에 계산 경로로 열면 drift 원인이 늘어난다

지금은 compat가 저장돼 있으므로,

- collect/write 시점 문제인지
- read-model projection 문제인지
- scoring/priority 문제인지

를 더 분리해서 볼 수 있습니다.

반대로 compat를 read-model 계산값으로 바꾸면,
canonical summary 정제와 projection 계산이 한 경로로 합쳐져
drift triage가 더 어려워집니다.

## 현재 구조에서의 역할 분리

### 1. `welfare_services.unified_category`

역할:

- legacy 추천/응답 계약 유지
- 기존 query/retrieval base row 호환
- canonical 전환 중 fallback 기준점

### 2. `service_taxonomies.compat_unified_category_*`

역할:

- canonical sidecar 안에서 compat layer도 같이 보존
- future read-model cut-over 시 sidecar 기준 mirror 제공
- drift inventory / backfill 검증 기준

### 3. canonical summary (`youth_major_*`, `gov24_*`)

역할:

- official/canonical taxonomy 보존
- explanation / inventory / future experiment 용 secondary hint
- 아직 compat 계약을 직접 대체하지 않음

## 현재 read-model 정책

현재 read-model은 `compat` 를 직접 계산하지 않습니다.

원칙은 아래와 같습니다.

1. compat는 저장된 값을 읽는다
2. canonical summary는 보조 힌트로 함께 읽을 수 있다
3. `compat=기타` 를 canonical major로 조용히 치환하지 않는다

즉 read-model은
`stored compat + canonical hint`
구조를 유지합니다.

## 지금 하지 않는 것

현재 phase에서 금지:

- `compat_unified_category` 를 read-model 계산값으로 치환
- `youth_major` 만 보고 compat를 역산
- `gov24_service_field` 만 보고 compat를 실시간 계산
- `compat=기타` 를 canonical summary로 자동 override
- priority matcher가 canonical summary만 읽도록 교체

## 언제 재검토할 수 있는가

아래 조건이 충족될 때만 저장 필드 정책을 다시 엽니다.

1. canonical summary coverage가 compat 대체를 검토할 만큼 안정화
2. `YOUTH_MID`, `GOV24_*`, `supportConditions` source-of-truth 확보
3. `compat=기타` 집합에 대한 explicit bridge/experiment 정책 정리
4. `unifiedCategory` 응답 계약을 canonical read-model 기반으로 바꿀 migration 순서 확정

즉 compat 계산-only 전환은
canonical 전환의 마지막 단계에 가깝습니다.

## 최종 정책

따라서 현재는:

- `compat_unified_category` 를 저장 필드로 유지한다
- `welfare_services.unified_category` 와 `service_taxonomies.compat_unified_category_*` 를 함께 둔다
- read-model은 저장된 compat를 읽고 canonical summary는 secondary hint로만 쓴다
- 계산-only 전환은 후속 전환 단계까지 보류한다

## 다음 작업

1. `unifiedCategory` 응답 계약을 유지하면서 taxonomy/read-model 로 브릿지하는 호환 전략 작성
2. `welfare_services.unified_category` 와 `service_taxonomies.compat_unified_category_*` 의 장기 정리 순서 설계
3. canonical summary coverage 안정화 후 compat 계산-only 전환 필요성 재검토
