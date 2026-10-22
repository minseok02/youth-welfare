# Gov24 데이터와 소비 계약

[정책 목차](policy-docs-index.md)에서 들어오는 현재 계약 안내입니다. 코드상 수집·label-first taxonomy·public filter·soft scoring은 구현돼 있습니다. 과거 closeout은 현재 서버의 적재 완전성이나 자격판정 정확성을 보장하지 않습니다. 작업·실행 조건은 [작업 체크리스트](policy-gov24-implementation-checklist.md), 전체 결함과 새 우선순위는 [현재 상태](../current-state.md)를 봅니다.

## 수집과 저장

`serviceList → serviceDetail → supportConditions`는 같은 `서비스ID`로 연결하고 목록·상세·지원조건을 별도 저장/재처리합니다. `PUBLIC_DATA_PORTAL_API_KEY` 설정을 사용하며 detail/support에는 lightweight retry가 있습니다. support payload snapshot은 `{"서비스ID","서비스명","conditions":{...}}` 형태입니다. DTO 재직렬화 snapshot과 실제 HTTP 원문은 구분합니다.

`GOV24_SUPPORT_CONDITION` fact extractor의 범위는 다음과 같습니다. 코드가 존재하는 것과 특정 DB에 전건 저장된 것은 별개입니다.

| 축 | 코드 |
| --- | --- |
| 성별·연령 | `JA0101/JA0102`, `JA0110/JA0111` |
| 소득·교육·고용 | `JA0201~JA0205`, `JA0317~JA0320`, `JA0326/JA0327` |
| 가구·특수대상 | `JA0401~JA0404`, `JA0411~JA0414`, `JA0328~JA0330` |
| 산업 종사자·창업 | `JA0313~JA0316`, `JA1101~JA1103` |
| 업종·사업체 | `JA1201/JA1202/JA1299`, `JA2201~JA2203/JA2299`, `JA2101~JA2103` |
| 해당사항없음 | `JA0322/JA0410`을 `NO_OP` fact로 보존 |

세부 결측·미매핑 조사는 [support inventory](policy-gov24-support-unmapped-inventory.md), shape·coverage 확인은 [runtime audit](policy-gov24-runtime-audit-runbook.md)을 따릅니다. 수집 후 embedding refresh 실패는 warning만 남고 응답이 성공할 수 있으므로 둘을 따로 판정합니다.

## Label과 내부 코드

공식 분류 label의 출처와 내부 code의 출처를 구분합니다. 기존 2026-05-19 Swagger 조사에서 `서비스분야/사용자구분/지원유형`은 string 필드로 관측됐으며, 그 기록을 현재 외부 API schema 확인으로 대체하지 않습니다. 원문·조사 근거는 [공공데이터 조사](policy-gov24-public-data-source-findings.md)에 있습니다.

| 축 | 저장 규칙 | 기존 관리 범위 |
| --- | --- | --- |
| `GOV24_SERVICE_FIELD` | exact-label term, split하지 않음 | 10 labels |
| `GOV24_USER_TYPE_TOKEN` | allowlist token만 additive multi-term | 개인·가구·법인/시설/단체·소상공인 4 tokens |
| `GOV24_BENEFIT_TYPE_TOKEN` | allowlist token만 additive multi-term | 20 tokens |

전체 label/code 목록은 [Gov24TaxonomyCodeSupport](../../backend/src/main/java/com/example/welfare/collect/support/Gov24TaxonomyCodeSupport.java)와 [seed migration](../../backend/src/main/resources/db/migration/V2026_06_01_01__seed_gov24_taxonomy_codes.sql)을 기준으로 대조합니다. 날짜 없는 live `tmp/*/latest*`를 다른 환경의 최신 결과로 사용하지 않습니다.

`개인||가구`, `현금||서비스(의료)` 같은 조합 원문과 `gov24ServiceFieldLabel/gov24UserTypeLabel/gov24BenefitTypeLabel` summary는 보존하고, canonical은 `service_taxonomy_terms`와 summary slot으로 읽습니다. token을 승격해도 조합 전체를 자격조건 fact로 만들지 않습니다. UX grouping은 [지원유형 grouping 초안](policy-gov24-benefit-type-grouping-draft.md), 세부 규칙은 [mapping 초안](policy-gov24-canonical-mapping-draft.md)을 참고합니다.

## 검색과 추천

- `GET /api/policies`와 `POST /api/policies/search`는 `gov24ServiceField/gov24UserType/gov24BenefitType`을 지원합니다. 검색 필드는 JSON body로 전달합니다. canonical term을 우선하고 legacy row만 summary/split fallback을 사용합니다.
- 상세 태그는 `/policies?sourceType=GOV24&gov24ServiceField=...` 등의 필터 링크로 연결합니다. user/benefit label이 없을 때도 managed token 범위의 tag/provisionType fallback만 사용합니다.
- 넓은 검색 첫 페이지 발견성 보정은 keyword 있음, offset 0, RELEVANCE, pageSize ≥ 10, 출처·Gov24 필터 없음일 때의 후보 window 최대 100건에 한정합니다. 첫 페이지에 Gov24가 없고 window에는 있으면 1건을 중간부에 배치합니다. 후속 페이지·명시 필터·다른 정렬로 일반화하지 않습니다.
- Gov24 taxonomy는 category-aligned/managed benefit/audience soft bonus와 priority bucket에 연결됩니다. 기존 bounded bonus cap은 10입니다. 점수가 높다는 이유로 신청 자격을 확정하지 않습니다.
- `Gov24 → YOUTH_MID`는 표시·education priority narrow boost용 파생 bridge입니다. 원래 `unifiedCategory` 호환 응답을 무조건 바꾸는 규칙이 아닙니다.

| 서비스분야 | 파생 청년 분류 |
| --- | --- |
| 주거·자립 | 주거 / 주택 및 거주지 |
| 고용·창업 | 일자리 / 취업·재직자·창업 (title/summary/benefit 신호로 선택) |
| 보육·교육 | 교육 / 미래역량강화·교육비지원·온라인교육 |
| 생활안정 | 복지문화 / 취약계층 및 금융지원 |
| 문화·환경 | 복지문화 / 문화활동 |
| 보건·의료, 임신·출산 | 복지문화 / 건강 |
| 보호·돌봄 | 복지문화 / 권익보호 |
| 행정·안전 | 참여권리 / 정책인프라구축 |
| 농림축산어업 | 일자리 / 재직자 |

정확한 원문 label과 분기 순서는 위 Java bridge를 확인합니다. rule/AI·조회/생성·북마크/로그 보존 경계는 [추천 파이프라인](../recommendation/recommendation-pipeline.md)을 따릅니다.

## 지역 보강

명시 지역코드가 부족해 기관·접수기관·부서·서비스명·목적요약·지원대상·선정기준·지원내용·신청방법에서 지역을 추론합니다. 명확한 시도+시군구/전국 유일 시군구는 5자리 코드로, 시도 단위 신호는 시도 전체로 확장합니다. 전국은 지역 row를 만들지 않는 계약입니다.

세종은 `36110/세종시`, 전국 유일 시군구의 접미사 생략(`재단법인안산인재육성재단 → 안산시`)도 처리합니다. 지방 기관유형의 bare 시도명은 확장하지만, `서울올림픽기념국민체육진흥공단` 같은 공공기관 명칭에는 같은 추론을 적용하지 않습니다. 기관코드 lookup 보강은 고성교육재단 `5420000 → 48820`, 한국전통문화전당 `4641000 → 52111/52113` 사례가 있습니다.

이는 추천의 텍스트 fallback을 대체하지 않는 보조 경로입니다. 기존 row의 지역 교체는 외부 API 없이 LIST snapshot을 재생할 수 있지만 **DB 쓰기 작업**입니다. 실행은 [작업 체크리스트](policy-gov24-implementation-checklist.md#실행과검증)를 따릅니다.

## 보류와 재검토

외부 공식 stable codebook 재수입과 raw 조합값의 hard eligibility 승격은 기존 내부 코드·runtime fact와 다른 설계입니다. codebook 부재만으로 수집/label-first를 막지 않으며, 반대로 내부 inventory를 공식 enum이라고 부르지 않습니다.

새 목표에는 변경할 lane·제외 범위·원본 근거·migration/backfill·호환/복구 계획을 정합니다. 공식 field/code/label과 API 버전을 확인하고, 외부 자료는 taxonomy 3축(track A)과 supportConditions(track B)를 별도로 판정합니다. 요청 자료는 [패키지 체크리스트](../history/policy/policy-normalization-gov24-request-package-checklist.md), [codebook 요청](../history/policy/policy-normalization-gov24-codebook-request-template.md), [조건 요청](../history/policy/policy-normalization-gov24-support-condition-request-template.md)을 참고합니다.

과거 reopen 기본값은 당시 제품 확장 범위의 방침이지 버그·데이터 유실·보안 수정 금지 지시가 아닙니다. 현재 오류는 별도 재현 작업으로 처리하며 외부 codebook을 기다리지 않습니다.

## 날짜가 있는 관측과 초기 계획

아래는 원래 관측의 날짜·환경에 한정됩니다. 전건 coverage·green 기록을 현재 환경의 보장으로 읽지 않습니다.

| 시점·환경 | 원래 기록 |
| --- | --- |
| 2026-06-01 로컬 seed/backfill 후 | list/detail/support raw 각 10,945, support fact 194,622, 서비스 coverage 10,945/10,945. missing-no-raw/all-null/unmapped-only/mapped-signal 모두 0 |
| 2026-06-02 서버 Docker support audit | raw 10,954, fact 194,819, coverage 10,954/10,954, nested 10,954/flat 0 |
| 2026-06-02 서버 missing-list repair | 필수 slot 결측 10,954 → 0, service elapsed 16,118ms / suite step 15,377ms, 이전 generic writer 308,290ms |
| 2026-06-02 서버 합성 추천 | housing fresh 44건, education fresh 32건 모두 Gov24. 5개 smoke user top10 50건, top1/2/10 share 100%; 실제 사용자 품질 근거 아님. acceptance 10 steps / 71,712ms |
| 2026-06-03 로컬 region backfill | list/scanned/upserted 10,954, missing/failed 0, endpoint 5,209ms, region rows 36,545, coverage 9,816/10,954 (89.61%), regionless 1,138, non-central 448, true-local 1 |
| 2026-10-21 로컬 수집 | 후속 support 48/50 저장·2건 실패, snapshot 누락·strict embedding 미갱신. [실행 기록](../phase-plan.md#2026-10-21-로컬-수집-실행-확인) |

6월 교육 합성 예시는 `청년 학자금대출 장기연체자 학자금상환 지원`, `화성시 학생 장학금 지원`이며, region true residual은 `온라인 신청 시연 테스트(운영)` 1건이었습니다.

5월 우선순위/pending 문서는 당시 local regression·bounded baseline·external blocked·future infra를 나누던 계획이었습니다. 이후 support fact·내부 code·YOUTH_MID/priority bridge는 구현됐고 서버/RDS 환경도 존재합니다. “로컬 할 일 거의 없음”, “서버 생긴 뒤”, “관찰만 유지”를 현재 지침으로 재사용하지 않습니다. 과거 합성 cohort/CTR·개인 캐시·replay(9→9/1→1, fingerprint same, reason_changed 0)·회귀 기록은 [추천 이력](../history/recommendation/recommendation-state-before-doc-cleanup.md), [작업 이력](../history/core/phase-plan-before-doc-cleanup.md)을 봅니다.

Batch AI·군집 캐시는 호출량·표본·운영 복잡도에 대한 별도 설계 후보이고, 알림톡은 운영 자격·심사 조건을 확인해야 하는 외부 연동 후보입니다. 인프라/PII/secret/HTTPS 작업의 현재 절차는 [배포](../deployment.md), [테스트](../core/testing.md), [PII cutover](../core/user-data-separation-design.md)에서 확인합니다. 당시의 비용·일정 가정은 새로운 요구를 자동 금지하거나 승인하지 않습니다.
