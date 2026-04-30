# `compat=기타 + canonical youth_major 채움` 세부 inventory

2026-04-30 local DB `service_taxonomies` summary 재적재 후,
`YOUTH` source에서:

- `compat_unified_category = 기타`
- `youth_major_label IS NOT NULL`

인 `421`건을 canonical major별로 다시 쪼갠 결과입니다.

관련 문서:

- [policy-normalization-compat-other-youth-major-policy.md](./policy-normalization-compat-other-youth-major-policy.md)
- [policy-normalization-compat-category-drift-inventory.md](./policy-normalization-compat-category-drift-inventory.md)

## 요약

분포는 아래 5개입니다.

| youth_major_label | count |
|---|---:|
| `복지문화` | `171` |
| `참여권리` | `130` |
| `교육` | `102` |
| `일자리` | `15` |
| `주거` | `3` |

핵심 해석:

1. `복지문화 / 참여권리 / 교육` 3개가 `403 / 421`로 거의 전부다
2. `일자리 / 주거` 는 소수이며, 다수는 raw `category_main` duplicate collapse 결과다
3. 즉 bridge table 검토 우선순위도
   - `복지문화`
   - `참여권리`
   - `교육`
   순으로 보는 게 맞다

## major별 세부 분포

### 1. `복지문화` (`171`)

대표 `category_sub`:

| category_sub | count |
|---|---:|
| `취약계층 및 금융지원` | `71` |
| `문화활동 및 생활지원` | `65` |
| `건강` | `26` |
| `예술인지원` | `8` |

대표 샘플:

| service_id | title | category_main | category_sub |
|---|---|---|---|
| `1` | `(접수 마감)2026년 11기 광주 청년 13(일+삶)통장` | `금융･복지･문화` | `취약계층 및 금융지원` |
| `16` | `2026년 삼삼오오 이웃돌봄 참여자 모집` | `금융･복지･문화` | `문화활동 및 생활지원` |
| `29` | `2026년 청년 면접정장 무료대여 지원` | `금융･복지･문화` | `취약계층 및 금융지원` |
| `45` | `제주 일자리 재형저축 사업` | `금융･복지･문화` | `취약계층 및 금융지원` |

관찰:

- canonical major는 `복지문화`로 안정적이지만,
  현 priority bucket의 `금융·생활지원`과 항상 같은 UX 의미인지 바로 단정하기는 어렵다
- 특히 `문화활동 및 생활지원`, `예술인지원`, `건강`이 섞여 있어
  broad `금융·생활지원` 으로 치환하면 정보 손실 가능성이 있다

### 2. `참여권리` (`130`)

대표 `category_sub`:

| category_sub | count |
|---|---:|
| `청년참여` | `83` |
| `정책인프라구축` | `20` |
| `청년참여,정책인프라구축` | `20` |
| `청년국제교류` | `4` |

대표 샘플:

| service_id | title | category_main | category_sub |
|---|---|---|---|
| `6` | `2026 광산구 홍보파트너 모집` | `참여･기반` | `청년참여` |
| `18` | `2026 청년마을만들기 사업 공모 안내` | `참여･기반` | `청년참여` |
| `23` | `2026년 서구 청년자율공간 참여 사업자 모집` | `참여･기반` | `청년참여` |
| `25` | `2026년 광주광역시 외국인 유학생 서포터즈 모집` | `참여･기반` | `청년참여` |

관찰:

- `참여권리 -> 참여·기회` bridge는 상대적으로 자연스럽다
- 다만 `정책인프라구축`, `청년국제교류` 같은 sub-bucket이 섞여 있어
  직접 priority 승격 전에는 row-level 샘플 재검토가 한 번 더 필요하다

### 3. `교육` (`102`)

대표 `category_sub`:

| category_sub | count |
|---|---:|
| `미래역량강화` | `71` |
| `교육비지원` | `16` |
| `온·오프라인교육 ,미래역량강화` | `6` |
| `온·오프라인교육` | `5` |

대표 샘플:

| service_id | title | category_main | category_sub |
|---|---|---|---|
| `13` | `[남구] 월간 청년밋업 강연(1월)` | `교육･직업훈련` | `교육비지원` |
| `49` | `서울영커리언스 인턴십Ⅰ(여름학기)` | `교육･직업훈련` | `미래역량강화` |
| `92` | `대학일자리플러스센터(졸업생특화프로그램 포함) 운영` | `교육･직업훈련` | `미래역량강화` |
| `96` | `지역특화 청년 무역전문가 양성사업(GTEP)` | `교육･직업훈련` | `미래역량강화` |

관찰:

- `교육 -> 교육·직업훈련` bridge도 비교적 자연스럽다
- 다만 `교육비지원` 과 `미래역량강화` 가 섞여 있어
  priority 하나로만 볼지, future explanation/badge에서 분리할지 검토 여지가 있다

### 4. `일자리` (`15`)

대표 `category_sub`:

| category_sub | count |
|---|---:|
| `취업,취업` | `6` |
| `재직자,권익보호` | `2` |
| `창업,취업` | `2` |
| `취업,창업` | `2` |

대표 샘플:

| service_id | title | category_main | category_sub |
|---|---|---|---|
| `44` | `신성장산업-청년인재플러스사업` | `일자리,일자리,일자리` | `취업,재직자,권익보호` |
| `56` | `브릿지보증 (실패보장제) 강화` | `일자리,일자리` | `창업,취업` |
| `772` | `진주시 청년 자격증 응시료 지원` | `일자리,일자리` | `취업,취업` |

관찰:

- 이 집합은 canonical major가 새 의미를 준다기보다
  duplicate/raw multi-value collapse 결과인 경우가 많다
- 우선순위 관점에서는 bridge table 검토 우선순위가 낮다

### 5. `주거` (`3`)

대표 `category_sub`:

| category_sub | count |
|---|---:|
| `주택 및 거주지,전월세 및 주거급여 지원` | `3` |

대표 샘플:

| service_id | title | category_main | category_sub |
|---|---|---|---|
| `106` | `청년월세 지원` | `주거,주거` | `주택 및 거주지,전월세 및 주거급여 지원` |
| `107` | `신혼부부 등 주택전세 연월세자금 대출이자 지원` | `주거,주거` | `주택 및 거주지,전월세 및 주거급여 지원` |
| `109` | `청년 및 주거취약계층 주택 중개수수료 지원사업` | `주거,주거` | `주택 및 거주지,전월세 및 주거급여 지원` |

관찰:

- 이것도 new bridge 후보라기보다 duplicate collapse 잔여다
- priority 관점에서 별도 정책 필요성은 낮다

## 우선순위 결론

bridge table 필요성 검토 우선순위:

1. `참여권리 -> 참여·기회`
2. `교육 -> 교육·직업훈련`
3. `복지문화 -> 금융·생활지원`
4. `일자리`, `주거`
   - 우선순위는 낮고, 사실상 summary duplicate collapse 결과 확인용에 가깝다

## 다음 작업

1. `참여권리`, `교육`, `복지문화` 3개에 대해 legacy priority bucket 승격이 실제로 필요한지 판단
2. 필요하면 canonical `youth_major` -> priority explicit bridge table 초안 작성
3. 필요 없으면 canonical `youth_major` 는 explanation/read-model hint 레벨에서만 유지
