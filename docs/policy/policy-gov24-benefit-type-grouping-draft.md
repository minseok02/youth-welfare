# `Gov24` benefitType grouping draft

문서군 진입점: [policy-docs-index.md](./policy-docs-index.md)

관련 문서:

- [policy-gov24-canonical-mapping-draft.md](./policy-gov24-canonical-mapping-draft.md)
- [policy-gov24-canonical-promotion-plan.md](./policy-gov24-canonical-promotion-plan.md)
- [policy-gov24-lane-closeout.md](./policy-gov24-lane-closeout.md)
- [policy-gov24-runtime-audit-runbook.md](./policy-gov24-runtime-audit-runbook.md)

## 목적

사용자 제공 지원형태 표를 `Gov24` current benefitType 운영 기준과 비교해,

- 어디까지 제품/UX grouping 참고자료로 쓸 수 있는지
- 어디부터 current canonical token과 다르므로 hard seed로 쓰면 안 되는지
- 대표 서비스 샘플을 어떤 QA/audit 용도로 쓸지

를 고정합니다.

이 문서는 `GOV24_BENEFIT_TYPE_TOKEN` seed를 바꾸는 문서가 아닙니다.
현재 public filter/scoring/read-model source-of-truth 는 계속 current runtime `20` token inventory 입니다.

## 결론

제공 표는 **쓸 수 있습니다.**

다만 용도는 아래로 제한합니다.

1. UX 상위 그룹 label 초안
2. 대표 서비스 QA 샘플
3. current inventory와 외부 참고표의 drift를 설명하는 문서

아래 용도로는 쓰지 않습니다.

1. 공식 stable codebook
2. DB seed / import-backfill source-of-truth
3. `service_facts` hard eligibility fact
4. raw 조합값 collapse 규칙

이유:

- 제공 표의 총 서비스 수는 `1075`건 규모이고, current server Gov24 LIST는 `10954`건입니다.
- current benefitType은 multi-label token이라 서비스 수 합계가 전체 서비스 수보다 큽니다.
- 제공 표에는 `서비스(임대주택)` 처럼 current `GOV24_BENEFIT_TYPE_TOKEN` inventory에 없는 label이 있습니다.
- 제공 표의 `현금(보험/융자)`, `기타(교육, 상담 등)` 은 current token 여러 개를 묶은 UX group에 가깝습니다.

## current server benefitType token inventory

2026-06-02 server Docker DB 기준, `service_taxonomies.gov24_benefit_type_label` split 결과와
`service_taxonomy_terms.term_group='GOV24_BENEFIT_TYPE_TOKEN'` 결과는 같은 count를 냅니다.

| current token | 서비스 수 |
|---|---:|
| `현금` | 4493 |
| `현물` | 1329 |
| `기타` | 808 |
| `현금(감면)` | 651 |
| `이용권` | 629 |
| `서비스(의료)` | 609 |
| `시설이용` | 502 |
| `기타(교육)` | 482 |
| `현금(보험)` | 411 |
| `현금(장학금)` | 371 |
| `현금(융자)` | 306 |
| `기타(상담)` | 299 |
| `서비스(돌봄)` | 269 |
| `서비스(일자리)` | 156 |
| `의료지원` | 83 |
| `상담/법률지원` | 80 |
| `기술지원` | 67 |
| `문화/여가지원` | 25 |
| `민원` | 8 |
| `봉사/기부` | 4 |

따라서 current canonical allowlist는 계속 위 `20`개입니다.

## 제공 표와 current token 차이

| 제공 표 label | 제공 표 수 | current token 대응 | 판단 |
|---|---:|---|---|
| `현금` | 441 | `현금`, 일부 `현금(장학금)` | 대분류 group으로만 사용 |
| `현금(감면)` | 80 | `현금(감면)` | current token과 직접 대응 |
| `현금(보험)` | 14 | `현금(보험)` | current token과 직접 대응 |
| `현금(융자)` | 82 | `현금(융자)` | current token과 직접 대응 |
| `현금(장학금)` | 21 | `현금(장학금)` | current token과 직접 대응 |
| `현물` | 50 | `현물` | current token과 직접 대응 |
| `이용권` | 53 | `이용권` | current token과 직접 대응 |
| `서비스(돌봄)` | 38 | `서비스(돌봄)` | current token과 직접 대응 |
| `서비스(의료)` | 59 | `서비스(의료)`, `의료지원` | current는 두 token으로 분리 유지 |
| `서비스(일자리)` | 72 | `서비스(일자리)`, 일부 `기술지원` | current는 두 token으로 분리 유지 |
| `서비스(임대주택)` | 17 | 직접 token 없음. `시설이용`, `주거·자립`, 제목/본문 주거 signal로 확인 | benefitType seed로 추가하지 않음 |
| `기타(교육, 상담 등)` | 148 | `기타`, `기타(교육)`, `기타(상담)`, `상담/법률지원`, `문화/여가지원`, `민원`, `봉사/기부` | UX group으로만 사용 |

## UX grouping 초안

이 grouping은 public filter value를 대체하지 않습니다.
필터 API 값은 계속 current `20` token을 그대로 받습니다.

| UX group | 포함 current token |
|---|---|
| `현금성 지원` | `현금`, `현금(감면)`, `현금(보험)`, `현금(융자)`, `현금(장학금)` |
| `현물·이용권` | `현물`, `이용권` |
| `돌봄 서비스` | `서비스(돌봄)` |
| `의료 서비스` | `서비스(의료)`, `의료지원` |
| `일자리·기술 서비스` | `서비스(일자리)`, `기술지원` |
| `시설·주거 이용` | `시설이용` |
| `교육·상담·기타` | `기타`, `기타(교육)`, `기타(상담)`, `상담/법률지원`, `문화/여가지원`, `민원`, `봉사/기부` |

운영 원칙:

1. UX group은 display/helper grouping입니다.
2. API filter와 canonical term은 `GOV24_BENEFIT_TYPE_TOKEN` `20`개를 유지합니다.
3. `서비스(임대주택)` 은 current token이 아니므로 새 token으로 만들지 않습니다.
4. 주거성 서비스는 `서비스분야=주거·자립`, `시설이용`, 제목/본문 지역·주거 signal과 함께 봅니다.

## 대표 서비스 QA 샘플

사용자 제공 대표 서비스명 중 current server Gov24에서 title partial match로 확인한 샘플입니다.

| 제공 group | 제공 서비스명 | current matched title | current benefitType |
|---|---|---|---|
| `현금` | `가정양육수당` | `가정양육수당 지원` | `현금` |
| `현금(보험/융자)` | `농업인 건강보험료 지원` | `농업인 건강보험료 지원` | `현금(보험)` |
| `현금(보험/융자)` | `농업인 연금보험료 지원` | `농업인 연금보험료 지원` | `현금(보험)` |
| `현금(보험/융자)` | `장애인 자립자금 대여` | `장애인 자립자금 대여` | `현금(융자)` |
| `현금(보험/융자)` | `일반 상환 학자금대출` | `일반 상환 학자금대출` | `현금(융자)` |
| `서비스(돌봄)` | `국가보훈대상자 양로지원` | `국가보훈대상자 양로지원` | `서비스(돌봄)` |
| `서비스(의료)` | `난임부부 시술비 지원` | `난임부부 시술비 지원` | `서비스(의료)` |
| `서비스(의료)` | `치매 치료관리비 지원` | `치매 치료관리비 지원` | `의료지원||현금` |
| `서비스(의료)` | `학교 밖 청소년 건강검진 지원` | `학교 밖 청소년 건강검진 지원` | `서비스(의료)||의료지원` |
| `서비스(의료)` | `선천성 난청검사 및 보청기 지원` | `선천성 난청검사 및 보청기 지원` | `의료지원||현금` |
| `서비스(일자리)` | `국민내일배움카드` | `국민내일배움카드` | `서비스(일자리)` |
| `서비스(일자리)` | `농식품분야 해외인턴십 지원` | `농식품분야 해외인턴십 지원` | `서비스(일자리)` |
| `서비스(일자리)` | `장애인 일자리 지원` | `시각장애인 일자리 지원` | `서비스(일자리)` |
| `이용권` | `산모·신생아 건강관리 지원` | `산모·신생아 건강관리 지원` | `이용권` |
| `이용권` | `저소득층 기저귀·조제분유 지원` | `저소득층 기저귀·조제분유 지원` | `이용권` |
| `이용권` | `북한이탈주민 자립자활 지원` | `북한이탈주민 자립자활 지원` | `이용권` |
| `이용권` | `산림복지서비스이용권` | `산림복지서비스이용권(바우처) 지급` | `문화/여가지원||이용권` |

아래 제공 샘플은 current title partial match에서 바로 잡히지 않았습니다.
이 값들은 QA fixture로 쓰려면 sourceId를 별도로 확인해야 합니다.

- `긴급복지생계지원`
- `누리과정(유아학비) 지원`
- `방과후 보육료 지원`
- `출산 전후 휴가급여`
- `수산장비 구입 지원`
- `초등돌봄교실`
- `결식아동 지원`
- `노인맞춤 돌봄서비스`
- `아이 돌봄서비스`
- `취업특강 신청`

## 다음 구현 판단

바로 구현 가능한 것은 아래 정도입니다.

1. Admin/operator 화면이나 문서에서 benefitType을 위 UX group으로 접어 보여주는 helper
2. QA smoke에서 대표 서비스 샘플이 expected token/group에 남아 있는지 확인하는 read-only audit

아직 하지 않는 것:

1. public filter value를 `7`개 UX group으로 교체
2. `서비스(임대주택)` 을 새 `GOV24_BENEFIT_TYPE_TOKEN` 으로 seed
3. `현금(보험)` 과 `현금(융자)` 을 하나의 canonical token으로 병합
4. `기타(교육, 상담 등)` 을 단일 canonical token으로 생성

## 요약

사용자 제공 표는 current `Gov24` benefitType token을 검증하고 UX grouping을 설계하는 데 유용합니다.
하지만 current runtime truth는 `20`개 token allowlist이고, 제공 표는 그 위에 얹는 상위 grouping/샘플 자료입니다.
