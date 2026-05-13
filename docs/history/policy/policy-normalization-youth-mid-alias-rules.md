# `YOUTH_MID` alias normalization 규칙

이 문서는 온통청년 `category_sub` / `mclsfNm` 계열 값 중, 공개 코드정의서의 official `정책중분류` 와 완전히 일치하지 않는 alias/composite 값을 어떻게 다룰지 정리하기 위한 규칙 메모입니다.

관련 문서:

- [policy-normalization-schema-draft.md](./policy-normalization-schema-draft.md)
- [db-migration.md](../../core/db-migration.md)
- [phase-plan.md](../../phase-plan.md)
- [troubleshooting-log.md](../../core/troubleshooting-log.md)

## 목적

`YOUTH_MID` 는 현재 stable code inventory 가 확정되지 않았고, 로컬 DB의 `welfare_services.category_sub` 에는 아래 세 종류가 섞여 있습니다.

1. official 단일 라벨
2. official 라벨의 comma-delimited 조합
3. 공개 코드정의서에 없는 non-official variant

canonical `YOUTH_MID` 를 오염시키지 않으면서도, 향후 stable code import 와 호환되는 처리 기준을 먼저 고정하는 것이 목적입니다.

## 확인한 로컬 DB token

`YOUTH` row의 `category_sub` 를 split/trim 한 결과, official 17개 라벨 외에 현재 확인된 non-official variant는 아래 2개입니다.

| token | count | 비고 |
|---|---:|---|
| `온·오프라인교육` | 11 | 교육 delivery mode를 묶은 표현 |
| `문화활동 및 생활지원` | 66 | `문화활동` + 생활지원 계열이 합쳐진 표현 |

official exact label은 아래 17개입니다.

- `취업`
- `재직자`
- `창업`
- `주택 및 거주지`
- `기숙사`
- `전월세 및 주거급여 지원`
- `미래역량강화`
- `교육비지원`
- `온라인교육`
- `취약계층 및 금융지원`
- `건강`
- `예술인지원`
- `문화활동`
- `청년참여`
- `정책인프라구축`
- `청년국제교류`
- `권익보호`

## 규칙

### 1. exact official label은 그대로 적재

- 공개 `정책중분류` 시트의 official 17개 라벨과 exact match 하는 token만 canonical `YOUTH_MID` 로 적재한다.
- 현재는 `service_taxonomy_terms.term_group='YOUTH_MID'`, `term_code=''`, `authority='OFFICIAL'` 로 저장한다.
- `service_taxonomies.youth_mid_label` summary 필드는 **단일 exact official token 1개일 때만** 채우고, comma 조합이나 raw alias가 섞이면 `NULL` 로 둔다.

### 2. comma-delimited 조합은 split 후 official token만 개별 적재

예:

- `취업,재직자` -> `취업`, `재직자`
- `주택 및 거주지,전월세 및 주거급여 지원` -> `주택 및 거주지`, `전월세 및 주거급여 지원`

규칙:

- 쉼표 기준 split
- trim
- empty token 제거
- official label과 exact match 하는 token만 적재

### 3. non-official variant는 canonical `YOUTH_MID` 에 자동 매핑하지 않는다

현재 보류 대상:

- `온·오프라인교육`
- `문화활동 및 생활지원`

보류 이유:

- `온·오프라인교육` 은 official `온라인교육` 과 유사하지만 동일 의미라고 단정할 수 없다.
- `문화활동 및 생활지원` 은 official `문화활동` 과 `취약계층 및 금융지원` 또는 다른 생활지원 축이 섞인 composite 표현일 수 있다.
- 지금 이 값을 억지로 official 단일 라벨에 붙이면 향후 stable code import 또는 authenticated inventory 와 충돌할 수 있다.

현재 처리:

- canonical `YOUTH_MID` backfill 에서는 skip
- 다만 future sidecar 에서는 별도 raw alias bucket으로 보존한다
  - `term_group='YOUTH_MID_RAW_ALIAS'`
  - `code_set_key=NULL`
  - `term_code=''`
  - `term_label=<raw alias token>`
  - `authority='OFFICIAL'`
  - `source_field='category_sub'`
- 추천/read-model/canonical taxonomy summary 는 기본적으로 `YOUTH_MID_RAW_ALIAS` 를 읽지 않는다
- 원문 보존은 기존 `welfare_services.category_sub` / `raw_api_payloads` 도 계속 유지한다

## 현재 결정

이번 단계에서 alias normalization 은 아래 수준으로 고정합니다.

1. split
2. trim
3. official exact-label filter
4. skipped variant는 canonical `YOUTH_MID` 에 넣지 않고 `YOUTH_MID_RAW_ALIAS` 로 별도 보존

아래는 아직 하지 않습니다.

1. non-official variant -> official label 강제 매핑
2. `YOUTH_MID` stable code 임의 생성
3. alias 값의 자동 bridge scoring

## 후속 작업

1. 로그인 가능한 testbed/live payload 에서 `srchPolyBizSecd` 전체 inventory 확보
2. `온·오프라인교육`, `문화활동 및 생활지원` 이 실제 API inventory 상 별도 코드인지 확인
3. 별도 코드가 아니면 alias normalization table 또는 rule 작성
4. `DeferredNormalizedPolicySidecarWriter` / backfill SQL 이 skipped alias를 `YOUTH_MID_RAW_ALIAS` 로 실제 저장하도록 연결
