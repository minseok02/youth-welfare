# `YOUTH category_main -> service_taxonomies.youth_major_*` summary 정제 규칙

2026-04-30 local drift inventory 기준으로,
현재 `service_taxonomies.youth_major_label` 은 raw `category_main` 복사 흔적과
comma/duplicate 다중값을 그대로 품고 있습니다.

이 문서는 `service_taxonomies.youth_major_code`, `youth_major_label`
summary 를 언제 채우고 언제 비울지 정하는 기준입니다.

관련 문서:

- [policy-normalization-schema-draft.md](./policy-normalization-schema-draft.md)
- [policy-normalization-compat-category-drift-inventory.md](./policy-normalization-compat-category-drift-inventory.md)
- [policy-normalization-youth-mid-alias-rules.md](./policy-normalization-youth-mid-alias-rules.md)

## 목표

summary row는 service당 **single canonical value** 만 가져야 합니다.

즉:

- raw `category_main` 값은 `service_taxonomy_terms` 나 raw payload에 남겨도 되지만
- `service_taxonomies.youth_major_*` 는
  - `일자리`
  - `주거`
  - `교육`
  - `복지문화`
  - `참여권리`

중 정확히 하나로 안정적으로 collapse 가능한 경우에만 채웁니다.

## canonical major 집합

| code | label | 허용 raw label |
|---|---|---|
| `JOB` | `일자리` | `일자리` |
| `HOUSING` | `주거` | `주거` |
| `EDUCATION` | `교육` | `교육`, `교육지원`, `교육·직업훈련`, `교육･직업훈련` |
| `WELFARE_CULTURE` | `복지문화` | `복지문화`, `금융·복지·문화`, `금융･복지･문화` |
| `PARTICIPATION_RIGHTS` | `참여권리` | `참여권리`, `참여·기반`, `참여･기반` |

핵심:

- summary label은 raw label을 그대로 쓰지 않고 canonical label로 재기록
- punctuation variant(`·`, `･`) 차이는 canonical label 단계에서 제거

## 정제 규칙

### 1. exact single raw label

입력:

- `category_main` 이 단일 토큰이고
- 위 표의 허용 raw label 중 하나와 exact match

출력:

- `youth_major_code` 채움
- `youth_major_label` canonical label 채움

예:

| raw `category_main` | summary code | summary label |
|---|---|---|
| `일자리` | `JOB` | `일자리` |
| `금융･복지･문화` | `WELFARE_CULTURE` | `복지문화` |
| `참여·기반` | `PARTICIPATION_RIGHTS` | `참여권리` |

### 2. comma-delimited multi-value

입력:

- `category_main` 이 쉼표 포함 multi-value
- split/trim 후 토큰 여러 개

규칙:

1. 각 token을 위 canonical major 집합으로 normalize
2. normalize 결과의 distinct canonical code 수를 본다

결과:

- distinct canonical code 가 `1`이면
  - summary 채움
  - duplicate token은 collapse
- distinct canonical code 가 `2` 이상이면
  - summary `NULL`
  - raw richness는 summary가 아니라 term/raw 계층에서만 보존

예:

| raw `category_main` | normalized tokens | summary |
|---|---|---|
| `일자리,일자리,일자리` | `JOB` | `JOB / 일자리` |
| `주거,주거` | `HOUSING` | `HOUSING / 주거` |
| `참여･기반,참여·기반` | `PARTICIPATION_RIGHTS` | `PARTICIPATION_RIGHTS / 참여권리` |
| `일자리,교육` | `JOB`, `EDUCATION` | `NULL` |

### 3. non-mappable raw label

입력:

- exact single token 이지만 canonical major 집합에 없음
- 또는 split 결과 일부 token을 canonical로 매핑할 수 없음

출력:

- `youth_major_code = NULL`
- `youth_major_label = NULL`

이 경우:

- compat layer는 기존 `compat_unified_category` 유지
- raw source truth는 term/raw 계층에서만 보존

## 왜 `기타` 와 summary를 직접 맞추지 않는가

현재 `compat_unified_category` 는 우선순위/응답 호환용 레이어입니다.

반면 `youth_major_*` summary는
온통청년 raw `category_main` 을 canonical major 집합으로 정제한 결과여야 합니다.

따라서:

- `compat=기타` 인데 `youth_major=복지문화` 인 row가 있을 수 있고
- 이건 drift 라기보다 역할 차이입니다

여기서 중요한 건 summary 값 자체가 단일 canonical value로 안정적이어야 한다는 점입니다.

## 구현 기준

다음 backfill / writer 구현은 아래 순서를 따라야 합니다.

1. raw `category_main` split/trim
2. punctuation normalize
   - `·` / `･` 차이 제거
3. token -> canonical major code/label normalize
4. distinct canonical code 수 판단
5. `1`개면 summary 채움, `2+`면 summary `NULL`

## 비목표

- 이번 단계에서 multi-major service를 강제로 하나의 major로 축약하지 않음
- `compat_unified_category` 를 summary source로 역사용하지 않음
- `youth_major_*` summary에 raw multi-value 문자열을 그대로 두지 않음

## 다음 작업

1. `DeferredNormalizedPolicySidecarWriter` summary upsert 에 같은 정제 규칙 반영
2. `V2026_04_30_02__seed_policy_normalization_codes.sql` backfill 초안에 같은 정제 규칙 반영
3. local DB에 재적용 후 `compat vs summary` drift 재측정
