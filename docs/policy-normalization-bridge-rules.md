# 정책 정규화 브릿지 / fallback 규칙 초안

이 문서는 [policy-normalization-schema-draft.md](./policy-normalization-schema-draft.md) 에 정의한 sidecar 스키마에 실제 값을 어떻게 채울지 정하기 위한 규칙 초안입니다.

다루는 범위:

1. `Gov24/보조금24 -> compatibility unifiedCategory / youth taxonomy bridge`
2. `복지로 list/detail text -> service_facts fallback extraction`

관련 문서:

- [policy-normalization-research.md](./policy-normalization-research.md)
- [policy-normalization-sample-spike.md](./policy-normalization-sample-spike.md)
- [policy-normalization-schema-draft.md](./policy-normalization-schema-draft.md)
- [recommendation-pipeline.md](./recommendation-pipeline.md)

## 1. 기본 원칙

### 1.1 official 과 derived 를 섞지 않는다

- source가 직접 준 분류/조건은 `authority=OFFICIAL`
- 규칙 기반으로 유도한 값은 `authority=SYSTEM_DERIVED` 또는 `RULE_DERIVED`
- AI가 추정한 값은 `authority=AI_ENRICHED`

즉 `Gov24 서비스분야` 와 `compat_unified_category` 는 같은 값이 될 수 있어도 같은 계층이 아닙니다.

### 1.2 hard filter 와 soft signal 을 분리한다

- hard filter 후보:
  - 연령
  - 소득 band
  - 지역
  - 명시적 학력/취업/가구특성
- soft signal 후보:
  - priority category
  - 청년 relevance bonus
  - 특수대상 가점
  - AI enrichment 설명

### 1.3 bridge 는 보수적으로 한다

- `youth major` 는 high-confidence일 때만 bridge
- `youth mid` 는 더 보수적으로 한다
- 확신이 낮으면 `compat_unified_category` 만 채우고 `youth_major/youth_mid` 는 비운다
- 반대로 `compat_unified_category=기타` 인 row에 canonical `youth_major` 가 채워졌다고 해서, 현재 priority/read-model 호환 레이어의 `compat` 값을 그 canonical major로 자동 치환하지도 않는다

## 2. Gov24 -> compatibility unifiedCategory 규칙

### 2.1 우선순위

Gov24 source에서 `compat_unified_category` 를 만드는 우선순위는 아래와 같습니다.

1. `서비스분야`
2. `지원유형`
3. `서비스명`
4. `서비스목적요약`

즉 가장 먼저 official field를 보고, 그래도 부족할 때만 title/summary keyword를 봅니다.

### 2.2 compatibility category 매핑표

| 조건 | compat_unified_category | confidence | 비고 |
|---|---|---:|---|
| `서비스분야` 가 `주거` 포함 | `주거` | 1.00 | direct bridge |
| `서비스분야` 가 `고용`, `취업`, `창업` 포함 | `일자리` | 1.00 | direct bridge |
| `서비스분야` 가 `교육`, `직업훈련`, `역량`, `장학` 포함 | `교육·직업훈련` | 0.95 | education/training 묶음 |
| `서비스분야` 가 `금융`, `생활`, `복지` 포함 | `금융·생활지원` | 0.85 | 범위가 넓어 slightly lower |
| `서비스분야` 가 `문화`, `예술`, `여가` 포함 | `문화·여가` | 0.85 | 현재 compatibility 유지용 |
| `서비스분야` 가 `보육`, `돌봄`, `출산`, `가족` 포함 | `가족·돌봄` | 0.85 | 현재 compatibility 유지용 |
| `서비스분야` 가 `참여`, `국제교류`, `권익` 포함 | `참여·기회` | 0.80 | youth UX bridge |
| `서비스분야` 가 `건강`, `의료` 포함 | `건강·의료` | 0.80 | current compatibility group |
| `서비스분야` 가 `안전`, `위기` 포함 | `안전·위기` | 0.80 | current compatibility group |
| `서비스분야` 만으로 불명확하고 `지원유형` 이 `대출`, `보증`, `융자` 계열이며 title/summary 에 `주거`, `전세`, `월세` 포함 | `주거` | 0.75 | benefit-type refinement |
| `서비스분야` 만으로 불명확하고 title/summary 에 `취업`, `창업`, `구직`, `일자리` 포함 | `일자리` | 0.75 | title keyword refinement |
| `서비스분야` 만으로 불명확하고 title/summary 에 `교육`, `훈련`, `장학`, `학습` 포함 | `교육·직업훈련` | 0.70 | title keyword refinement |

그 외:

- `compat_unified_category` 비움
- `authority=SYSTEM_DERIVED`
- `confidence <= 0.60` 인 값은 저장하지 않음

## 3. Gov24 -> youth taxonomy bridge 규칙

### 3.1 youth major

`youth_major_code` 는 아래 경우에만 채웁니다.

1. `compat_unified_category` 가 아래 5개 중 하나로 high-confidence bridge 됨
   - `주거`
   - `일자리`
   - `교육·직업훈련`
   - `금융·생활지원`
   - `참여·기회`
2. title/summary/target 에 `청년` signal 이 있거나,
3. `supportConditions` 에 청년과 가까운 연령 범위(`18~39`)가 있음

즉, category만 비슷하다고 youth taxonomy를 무조건 채우지 않습니다.

매핑:

| compat | youth_major_code | label |
|---|---|---|
| `주거` | `HOUSING` | 주거 |
| `일자리` | `JOB` | 일자리 |
| `교육·직업훈련` | `EDUCATION` | 교육 |
| `금융·생활지원` | `WELFARE_CULTURE` | 복지문화 |
| `참여·기회` | `PARTICIPATION_RIGHTS` | 참여권리 |

### 3.2 youth mid

`youth_mid_code` 는 이번 단계에서 보수적으로 둡니다.

허용:

- title/summary 에 명시 keyword가 있는 일부만
  - `월세`, `전세`, `임대` -> 주거 하위
  - `취업`, `구직`, `채용` -> 취업 하위
  - `창업` -> 창업 하위
  - `장학`, `등록금`, `교육비` -> 교육비지원 하위

불허:

- service field만 보고 youth mid를 채우는 것
- AI enrichment 결과로 youth mid를 `OFFICIAL` 로 저장하는 것

저장 규칙:

- `authority=SYSTEM_DERIVED`
- `confidence >= 0.85` 일 때만 저장

## 4. 복지로 text/detail -> facts fallback 규칙

### 4.1 source field 우선순위

복지로 facts fallback은 아래 필드 우선순위를 가집니다.

1. `targetDetail`
2. `selectionCriteria`
3. `description/servDgst`
4. `applyMethodDetail`
5. `supportDetail`

이유:

- 자격요건은 `대상`, `선정기준` 쪽이 가장 신뢰도가 높음
- 신청방법/지원내용은 eligibility보다 절차 설명일 가능성이 큼

### 4.2 허용하는 fact_group

초기 허용:

- `AGE`
- `INCOME`
- `RENT_CAP`
- `APPLY_END_DATE`
- `EMPLOYMENT`
- `EDUCATION`
- `HOUSEHOLD`
- `SPECIAL_GROUP`

초기 비허용:

- 복잡한 지역 residency 기간
- 가구원 수 상세 숫자
- 다중 조건 조합 논리
- “우대”, “가점” 같은 선발 가중치
- 법적 예외조항

### 4.3 hard filter 가능 / 불가능 구분

| fact_group | fallback 저장 허용 | retrieval hard filter 사용 | 비고 |
|---|---|---|---|
| `AGE` | 예 | 예 | explicit numeric pattern만 |
| `INCOME` | 예 | 아니오(초기) | percent/won scale 불일치 가능 |
| `RENT_CAP` | 예 | 아니오 | 주거 soft signal 우선 |
| `APPLY_END_DATE` | 예 | 아니오 | deadline scoring용 |
| `EMPLOYMENT` | 예 | 아니오(초기) | phrase ambiguity 존재 |
| `EDUCATION` | 예 | 아니오(초기) | phrase ambiguity 존재 |
| `HOUSEHOLD` | 예 | 아니오(초기) | exact phrase만 |
| `SPECIAL_GROUP` | 예 | 아니오(초기) | 가점/필터 분리 필요 |

즉 초기 retrieval hard filter로 바로 넘기는 fallback fact는 `AGE` 만 허용합니다.

### 4.4 explicit phrase 규칙

`RULE_DERIVED` facts는 아래처럼 **명시적 표현** 에만 한정합니다.

#### employment

- 허용 phrase:
  - `미취업`
  - `취업준비`
  - `재직`
  - `자영업`
  - `예비창업`
  - `창업자`

#### education

- 허용 phrase:
  - `대학생`
  - `대학원생`
  - `고등학생`
  - `재학생`
  - `졸업예정`

#### household

- 허용 phrase:
  - `1인가구`
  - `한부모`
  - `다자녀`
  - `무주택`

#### special group

- 허용 phrase:
  - `장애인`
  - `국가보훈`
  - `보훈대상자`
  - `다문화`
  - `북한이탈`
  - `농어촌`
  - `자립준비`
  - `보호종료`

### 4.5 confidence 기준

| 경우 | authority | confidence |
|---|---|---:|
| DTO 숫자 필드 direct | `OFFICIAL` | 1.00 |
| Gov24 supportConditions code direct | `OFFICIAL` | 1.00 |
| detail target/selection 에 explicit exact phrase | `RULE_DERIVED` | 0.90 |
| description 에 explicit exact phrase | `RULE_DERIVED` | 0.80 |
| apply/support text 에서 추출 | `RULE_DERIVED` | 0.70 |
| AI 자유서술 분류 | `AI_ENRICHED` | 모델별 |

저장 제한:

- `confidence < 0.75` 는 hard filter/read-model summary 에 쓰지 않음
- `confidence < 0.75` 는 only diagnostic/AI 보조용

## 5. 기존 추천 경로와의 연결

### 5.1 RetrievalService

1차 치환 후보:

- `AGE`

보류:

- `INCOME`
- `EMPLOYMENT`
- `EDUCATION`
- `HOUSEHOLD`
- `SPECIAL_GROUP`

이유:

- 현재 `findCandidates*` SQL이 `min_age/max_age`, `min_income/max_income` 과 `service_tags` 보조 신호에 묶여 있으므로,
- 먼저 age만 canonical facts로 바꾸고 나머지는 rule scoring 쪽에서 소비하는 것이 안전합니다.

### 5.2 RuleScoringService

1차 치환 후보:

- `EMPLOYMENT`
- `EDUCATION`
- `HOUSEHOLD`
- `SPECIAL_GROUP`
- `APPLY_END_DATE`

즉 rule scoring은 `service_tags` 와 병행하다가, structured facts가 충분히 쌓이면 점진적으로 facts 우선으로 옮깁니다.

### 5.3 DefaultPriorityMatcher

계속 `compat_unified_category` 를 사용합니다.

단:

- source가 official youth major를 주면 그걸 우선
- Gov24는 bridge 결과를 사용

즉 `compat_unified_category` 는 현재 프론트 계약을 유지하기 위한 read-model output일 뿐, canonical 원본은 아닙니다.

2026-04-30 결정:

- 당분간 `DefaultPriorityMatcher` 는 canonical taxonomy summary code/label(`youth_major_code`, `gov24_service_field_code`)을 직접 해석하지 않는다
- priority 가중치는 먼저 `RecommendationCandidateProjection.unifiedCategoryCompat` / `applyEndDate` 를 우선 읽는 호환 레이어만 사용한다
- canonical taxonomy summary code/label 직접 해석은 아래 조건이 갖춰질 때 별도 task로 연다
  - `service_taxonomies` summary가 실제 collect path에서 안정적으로 채워질 것
  - `compat_unified_category` 와 summary code 간 drift inventory가 확보될 것
  - 우선순위 코드(`HOUSING`, `JOB`, `EDUCATION` 등)와 summary code 집합의 매핑표가 문서/테스트로 고정될 것

이유:

- 현재 priority 옵션은 여전히 `주거`, `일자리`, `교육·직업훈련` 같은 호환 카테고리 문자열을 기준으로 검증돼 있다
- 이 상태에서 matcher가 summary code/label까지 바로 해석하면 `compat_unified_category` 와 canonical summary가 동시에 있는 row에서 우선순위 의미가 흔들릴 수 있다
- 따라서 1차 전환에서는 `compat_unified_category` 를 priority 호환 레이어로 유지하고, canonical summary는 이후 inventory 기반으로 분리 전환한다

## 6. 지금 결정한 것

- Gov24는 `official facts` source로 강하게 사용
- Gov24 -> `compat_unified_category` 는 rule bridge 허용
- Gov24 -> `youth_mid` 는 보수적으로 제한
- 복지로 fallback fact는 저장은 하되, 초기 hard filter는 `AGE` 만 허용
- `targetDetail/selectionCriteria` 가 fallback extraction의 주 근거

## 다음 작업

1. `NormalizedPolicyAggregate` 내부 DTO 초안 작성
2. sidecar 생성 migration SQL 초안 작성
3. `compat_unified_category` 브릿지를 read-model에서 계산할지 저장할지 최종 결정
4. `TextConstraintExtractor` 를 `service_facts` 저장 모델에 맞춘 출력 규격으로 재설계
