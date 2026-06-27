# 정책 정규화 샘플 매핑 스파이크

이 문서는 [policy-normalization-research.md](./policy-normalization-research.md) 의 설계 가설이 실제 source 샘플에 맞물리는지 확인하기 위한 샘플 기반 검증 메모입니다.

목표는 두 가지입니다.

1. 현재 붙어 있는 source들이 `core / detail / taxonomy / facts / raw` 구조에 실제로 들어가는지 확인
2. 신규 source 후보인 `Gov24/보조금24` 가 최소한 추천 hard filter와 호환 가능한 구조적 필드를 주는지 확인

관련 내부 기준:

- [policy-normalization-research.md](./policy-normalization-research.md)
- [collect-ops.md](../../collect/collect-ops.md)
- [recommendation-pipeline.md](../../recommendation/recommendation-pipeline.md)
- [phase-plan.md](../../phase-plan.md)

## 검증 범위

이번 스파이크는 다음 4종을 기준으로 봤습니다.

1. 온통청년 list sample
2. 복지로 중앙 list + detail sample
3. 복지로 지자체 list + detail sample
4. Gov24/보조금24 `serviceList + serviceDetail + supportConditions` 대표 sample

주의:

- 온통청년/복지로는 현재 DTO와 mapper/test fixture를 기준으로 확인했습니다.
- Gov24는 현재 코드에 DTO가 없으므로, **공식 Swagger schema 기반 대표 sample** 로 검증했습니다.
- 즉 이번 결과는 “canonical 구조가 source field를 담을 수 있는가”에 대한 스파이크이며, 아직 production mapper 구현 검증은 아닙니다.

## 샘플 1. 온통청년

근거:

- [YouthApiDto.java](../../../backend/src/main/java/com/example/welfare/collect/dto/YouthApiDto.java)
- [WelfareServiceMapper.java](../../../backend/src/main/java/com/example/welfare/collect/mapper/WelfareServiceMapper.java)
- [PolicyNormalizationSampleCoverageTest.java](../../../backend/src/test/java/com/example/welfare/collect/mapper/PolicyNormalizationSampleCoverageTest.java)

대표 필드:

- `plcyNo`
- `plcyNm`
- `plcyExplnCn`
- `plcySprtCn`
- `lclsfNm`, `mclsfNm`
- `plcyKywdNm`
- `sprvsnInstCdNm`, `operInstCdNm`
- `sprtTrgtMinAge`, `sprtTrgtMaxAge`
- `earnMinAmt`, `earnMaxAmt`
- `aplyYmd`, `plcyAplyMthdCn`, `aplyUrlAddr`

canonical 적재:

- `core`
  - title, summary/description, support content, 기관, 기간, 신청 URL
- `taxonomy`
  - youth major, youth mid, youth keyword, provision-method 일부
- `facts`
  - age min/max, income min/max
- `detail`
  - 약함
  - 제출서류, 법적근거, 문의처, 상세 eligibility text는 부족함

추천 관점:

- hard filter: 강함
- priority/category: 강함
- AI summary: 보통
- detail 기반 보강: 약함

판단:

- 온통청년은 `youth taxonomy` 와 기본 eligibility facts의 기준 source로 적합
- 다만 상세 본문이 약해 `detail` 레이어의 기준 source로 삼기는 어려움

## 샘플 2. 복지로 중앙 list + detail

근거:

- [BokjiroCentralDto.java](../../../backend/src/main/java/com/example/welfare/collect/dto/BokjiroCentralDto.java)
- [BokjiroDetailClient.java](../../../backend/src/main/java/com/example/welfare/collect/gateway/BokjiroDetailClient.java)
- [WelfareServiceMapper.java](../../../backend/src/main/java/com/example/welfare/collect/mapper/WelfareServiceMapper.java)
- [PolicyNormalizationSampleCoverageTest.java](../../../backend/src/test/java/com/example/welfare/collect/mapper/PolicyNormalizationSampleCoverageTest.java)

대표 필드:

- list
  - `servId`, `servNm`, `servDgst`
  - `jurMnofNm`, `jurOrgNm`
  - `lifeArray`, `intrsThemaArray`, `trgterIndvdlArray`
  - `sprtCycNm`, `srvPvsnNm`, `onapPsbltYn`, `servDtlLink`
- detail
  - `tgtrDtlCn`, `alwServCn`, `aplyMtdCn`, `slctCritCn`
  - `inqplCtadrList`

canonical 적재:

- `core`
  - title, summary, 기관, 온라인 신청 여부, 대표 URL
- `detail`
  - target detail, support detail, apply method detail, selection criteria, contacts
- `taxonomy`
  - interest theme, life stage, target group
- `facts`
  - age는 텍스트 추출로 일부 가능
  - income/employment/education은 안정적인 구조화 값이 부족

추천 관점:

- hard filter: age 정도만 안정적
- rule scoring: interest theme / target group 신호는 충분
- AI enrichment 후보: 큼

판단:

- 복지로 중앙은 `detail` 과 `display taxonomy` 는 강하지만, structured facts는 약함
- 따라서 canonical에서는 `detail source` 로 강하고, `facts source` 로는 fallback extraction 또는 AI 보강이 필요함

## 샘플 3. 복지로 지자체 list + detail

근거:

- [BokjiroLocalDto.java](../../../backend/src/main/java/com/example/welfare/collect/dto/BokjiroLocalDto.java)
- [BokjiroDetailClient.java](../../../backend/src/main/java/com/example/welfare/collect/gateway/BokjiroDetailClient.java)
- [WelfareServiceMapper.java](../../../backend/src/main/java/com/example/welfare/collect/mapper/WelfareServiceMapper.java)
- [PolicyNormalizationSampleCoverageTest.java](../../../backend/src/test/java/com/example/welfare/collect/mapper/PolicyNormalizationSampleCoverageTest.java)

대표 필드:

- list
  - `servId`, `servNm`, `servDgst`
  - `bizChrDeptNm`
  - `lifeNmArray`, `intrsThemaNmArray`, `trgterIndvdlNmArray`
  - `sprtCycNm`, `srvPvsnNm`, `aplyMtdNm`, `servDtlLink`
  - `ctpvNm`, `sggNm`
- detail
  - `tgtrDtlCn`, `alwServCn`, `aplyMtdCn`, `slctCritCn`
  - `inqplCtadrList`

canonical 적재:

- `core`
  - title, summary, 운영기관, 신청방법, 지역, 기간
- `detail`
  - target/support/apply/selection/contact
- `taxonomy`
  - interest theme, life stage, target group
- `facts`
  - age는 본문 추출 가능
  - income/employment/education은 구조적으로 비어 있는 경우가 많음

추천 관점:

- hard filter: age와 지역은 어느 정도 가능
- rule scoring: 지역 + 관심주제 + 대상유형은 충분
- AI enrichment 후보: 큼

판단:

- 복지로 지자체는 `지역성`과 `detail` 은 좋지만, facts coverage는 여전히 약함
- 따라서 `facts` 레이어는 구조화 코드 source보다 보조적이다

## 샘플 4. Gov24/보조금24 `serviceList + serviceDetail + supportConditions`

근거:

- 공식 Swagger `serviceList_model`, `serviceDetail_model`, `supportConditions_model`
- [policy-normalization-research.md](./policy-normalization-research.md)
- [PolicyNormalizationSampleCoverageTest.java](../../../backend/src/test/java/com/example/welfare/collect/mapper/PolicyNormalizationSampleCoverageTest.java)

대표 sample은 공식 schema를 조합해 아래처럼 잡았습니다.

- list
  - `서비스ID`, `서비스명`, `서비스목적요약`, `지원대상`, `선정기준`, `지원내용`, `신청방법`, `신청기한`
  - `상세조회URL`, `소관기관명`, `부서명`, `지원유형`, `사용자구분`, `서비스분야`
- detail
  - `구비서류`, `문의처`, `온라인신청사이트URL`, `행정규칙`, `자치법규`, `법령`
- supportConditions
  - `JA0110`, `JA0111`
  - `JA0203`
  - `JA0320`, `JA0327`
  - `JA0412`

canonical 적재:

- `core`
  - 매우 강함
  - title, summary, target, support, apply method, deadline, org, url
- `detail`
  - 매우 강함
  - required documents, contact, online apply url, legal basis
- `taxonomy`
  - gov24 service field, user type, benefit type는 바로 저장 가능
  - youth major/mid category는 직접 주지 않음
- `facts`
  - age min/max
  - income band
  - education
  - employment
  - household
  - gender / disability / veteran 등

추천 관점:

- hard filter: 매우 강함
- rule scoring: 중간
  - gov24 service field만으로는 청년 priority와 1:1 대응이 약함
- AI enrichment: 선택적
  - source 고유 자유서술 보강용

판단:

- Gov24는 `facts` 와 `core/detail` 의 기준 source로 매우 좋음
- 반대로 `청년정책 taxonomy` 의 기준 source는 아님
- 따라서 Gov24를 붙일 때는 `youth taxonomy bridge` 또는 `compatibility unifiedCategory` 가 필요함

## 교차 비교

| source | core | detail | taxonomy | facts | 추천 즉시 활용성 | 핵심 공백 |
|---|---|---|---|---|---|---|
| 온통청년 | 강함 | 약함 | 매우 강함 | 강함 | hard filter + priority 강함 | 상세 서류/법적근거 부족 |
| 복지로 중앙 | 강함 | 강함 | 중간 | 약함 | rule scoring 강함 | 구조화 income/employment 약함 |
| 복지로 지자체 | 강함 | 강함 | 중간 | 약함 | 지역 + rule scoring 강함 | structured facts 약함 |
| Gov24/보조금24 | 매우 강함 | 매우 강함 | 중간 | 매우 강함 | hard filter 강함 | youth taxonomy 직접 부재 |

## 이번 스파이크 결론

1. 제안한 canonical 구조는 샘플 4종 모두를 담을 수 있다는 점에서 **구조 자체는 타당**합니다.
2. 다만 source마다 강한 레이어가 다릅니다.
   - 온통청년: taxonomy/facts
   - 복지로: detail/display taxonomy
   - Gov24: core/detail/facts
3. 따라서 canonical을 단일 source 기준으로 잡으면 안 됩니다.
4. 특히 Gov24는 hard filter용으로는 매우 좋지만, `청년정책 우선순위` 와 `unifiedCategory` 를 직접 대체하지 못합니다.
5. 다음 구현에서 꼭 필요한 브릿지는 아래 두 개입니다.
   - `Gov24 service field / user type / benefit type -> compatibility unifiedCategory`
   - `복지로 text/detail -> facts fallback extraction policy`

## 권장 다음 작업

1. `service_taxonomies / service_taxonomy_terms / service_facts` 스키마 초안 작성
2. `compatibility unifiedCategory` 를 어떤 규칙으로 유지할지 결정
3. `Gov24 -> youth taxonomy bridge` 를 rule 기반으로 둘지, `system_derived taxonomy` 로 분리할지 결정
4. 복지로 detail/text 에서 어떤 facts까지 규칙 추출로 허용할지 범위 고정
