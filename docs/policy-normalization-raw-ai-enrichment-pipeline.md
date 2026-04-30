# 신규 source-specific 필드의 raw + AI batch enrichment 파이프라인

이 문서는 신규 정책형 source를 붙일 때

- official `core/detail/taxonomy/facts` 로 바로 떨어지지 않는 필드

를 어떻게 다룰지 정리합니다.

관련 문서:

- [policy-source-onboarding-playbook.md](./policy-source-onboarding-playbook.md)
- [policy-normalization-schema-draft.md](./policy-normalization-schema-draft.md)
- [policy-normalization-research.md](./policy-normalization-research.md)
- [policy-normalization-bridge-rules.md](./policy-normalization-bridge-rules.md)

## 문제

신규 source를 붙이면 항상 아래 두 종류가 같이 들어옵니다.

1. 공식 축으로 바로 매핑되는 값
   - 제목
   - 기관
   - 신청기한
   - 공식 코드/분류
   - 구조화 eligibility
2. source-specific 자유서술/보조 필드
   - source만의 상세 설명 블록
   - 문장형 대상/유의사항/부가조건
   - 표/목록/비정형 문구
   - 공식 코드로 환원되지 않은 특수 상태

현재는 2번을 다루는 경계가 약하면

- 일부는 `welfare_services` 에 억지로 flatten 되고
- 일부는 raw payload 안에만 묻히고
- 일부는 추천에 쓰이지 못한 채 버려집니다.

## 결론

신규 source-specific 필드는 아래 3단계로 흡수합니다.

1. **raw payload 보존**
2. **official / rule-derived canonical 우선 추출**
3. **남은 자유서술만 AI batch enrichment 후보로 승격**

즉 AI는 신규 source 필드의 1차 저장 경로가 아니라,
official/rule-derived 정규화 뒤에 남는 잔여 신호를
배치로 보강하는 마지막 단계입니다.

## 단계별 파이프라인

## 1단계: raw payload 보존

모든 신규 source-specific 필드는 먼저 raw로 남깁니다.

기준:

- `raw_api_payloads` 에 원문 JSON 저장
- source row id / fetch 시각 / api category 보존
- 필요한 경우 field-level path inventory 를 문서화

원칙:

- unknown field를 collect 단계에서 버리지 않는다
- `welfare_services` / sidecar schema에 즉시 없는 값도 raw에 남긴다

이 단계의 목적은
“모델이 아직 못 읽는 값도 재처리 가능하게 남기는 것”입니다.

## 2단계: official / rule-derived canonical 추출

raw payload 중 다음은 AI 없이 먼저 canonical로 옮깁니다.

### 2-1. official core/detail

예:

- 제목
- 요약
- 지원내용
- 신청방법
- 신청기한
- 기관
- URL

저장 위치:

- `welfare_services`
- `welfare_service_details`

### 2-2. official taxonomy

예:

- source가 직접 준 분류 코드
- 사용자구분
- 지원유형
- 제공방법

저장 위치:

- `service_taxonomies`
- `service_taxonomy_terms`

authority:

- `OFFICIAL`

### 2-3. official / rule-derived facts

예:

- 연령
- 소득
- 학력
- 취업
- 가구특성
- 신청마감

저장 위치:

- `service_facts`

authority:

- source 구조화 값이면 `OFFICIAL`
- 고신뢰 패턴 추출이면 `RULE_DERIVED`

즉 AI batch는 여기까지 끝난 뒤에만 들어옵니다.

## 3단계: AI batch enrichment 후보 선별

다음 조건을 만족하는 필드만 AI batch 후보로 올립니다.

1. raw에는 남아 있음
2. official 코드/필드로 직접 환원되지 않음
3. rule-based extractor로도 안정적 hard fact가 안 됨
4. 그래도 검색/설명/soft ranking에는 가치가 있음

예:

- source-specific beneficiary 설명
- 주거/장학/훈련 제도의 자유서술 eligibility 힌트
- 지원방식/프로그램 성격의 보조 설명
- 추천 설명에 유용한 short rationale 후보

반대로 아래는 AI batch 후보가 아닙니다.

- 이미 official code가 있는 값
- hard filter pass/fail을 결정해야 하는 값
- source가 직접 준 taxonomy
- `compat_unified_category` 를 대체할 대표 category

## AI batch enrichment 출력 원칙

AI batch 결과는 canonical 원본을 대체하지 않습니다.

허용 저장 위치:

### 1. `service_facts(authority=AI_ENRICHED)`

용도:

- soft signal
- 보조 필터
- explanation 근거

예:

- `fact_group=SPECIAL_GROUP`
- `fact_code=YOUTH_NEWLY_MARRIED_HINT`
- `authority=AI_ENRICHED`

조건:

- official/rule-derived 동일 슬롯이 이미 있으면 overwrite 금지
- merge 충돌 시 가장 낮은 precedence

### 2. `service_taxonomy_terms(authority=AI_ENRICHED)`

용도:

- source-specific 관심주제/대상군 보조 라벨
- 향후 inventory 검토용 soft taxonomy

조건:

- current public response 대표 category에는 직접 연결하지 않음
- priority contract를 직접 바꾸지 않음

### 3. enrichment summary / debug trace

필요하면 별도 summary/debug artifact로 보관할 수 있습니다.

예:

- AI가 읽은 raw field names
- prompt version
- extracted rationale
- confidence / model metadata

## source-specific 필드 triage 규칙

신규 source field를 보면 아래 순서로 판정합니다.

1. 이 값은 `core/detail` 로 직접 매핑되는가
2. 이 값은 official taxonomy/codebook이 있는가
3. 이 값은 rule-derived fact로 안정적으로 추출 가능한가
4. 그럼에도 남는다면 AI batch 후보인가
5. 아니면 raw-only로 남겨도 되는가

즉 `AI로 읽어보자` 는 항상 4번째 질문입니다.

## 권장 raw-to-enrichment 경계

### raw-only로 남겨도 되는 것

- source 내부 표시용 note
- 사람이 읽는 긴 설명문인데 현재 제품 가치가 낮은 것
- reference/listing 전용 보조 메타데이터

### rule-first가 맞는 것

- 연령 범위
- 소득 임계
- 날짜 범위
- 신청마감
- 명시 beneficiary whitelist

### AI batch 후보가 맞는 것

- 정책 대상의 문장형 nuance
- source-specific benefit style 설명
- hard filter는 아니지만 추천 설명엔 유용한 맥락
- 향후 codebook 후보가 될 수 있는 soft clustering signal

## 신규 source 온보딩 체크리스트

정책형 source를 붙일 때 추가로 확인:

1. raw payload에 source-specific field를 누락 없이 남겼는가
2. official codebook / schema로 먼저 흡수할 수 있는 값과 분리했는가
3. rule-derived extractor로 갈 수 있는 값과 분리했는가
4. AI batch 후보 필드 목록을 명시했는가
5. AI_ENRICHED 결과가 current priority/response contract를 직접 바꾸지 않게 막았는가

## 지금 하지 않는 것

현재 단계에서 바로 하지 않는 것:

- AI batch 실행 스케줄/잡 구현
- prompt 상세 설계
- enrichment 결과용 별도 DB 테이블 추가
- AI 결과를 retrieval hard filter에 직접 연결
- AI 결과로 `unifiedCategory` / compat layer override

이번 문서는 파이프라인 경계만 고정합니다.

## 최종 정책

따라서 신규 source-specific 필드는

- 먼저 raw로 보존하고
- official / rule-derived canonical을 우선 채우고
- 남는 자유서술만 AI batch enrichment fact/term 후보로 올립니다

즉 canonical 전환에서 AI는
“미정의 source-specific 필드의 마지막 보강 단계”이지,
공식 축을 대신하는 1차 저장 경로가 아닙니다.
