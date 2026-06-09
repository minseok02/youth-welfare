# `Gov24` 공공데이터포털 source findings

문서군 진입점: [policy-docs-index.md](./policy-docs-index.md)

관련 문서:

- [policy-gov24-blocked-track-status.md](./policy-gov24-blocked-track-status.md)
- [policy-gov24-canonical-mapping-draft.md](./policy-gov24-canonical-mapping-draft.md)
- [policy-gov24-support-unmapped-inventory.md](./policy-gov24-support-unmapped-inventory.md)
- [policy-normalization-current-state.md](./policy-normalization-current-state.md)
- [policy-normalization-gov24-schema-acquisition-path.md](../history/policy/policy-normalization-gov24-schema-acquisition-path.md)

## 목적

이 문서는 `Gov24` 관련 public source 탐색 결과를 한 장에 고정합니다.

이번 정리의 질문은 세 가지입니다.

1. 공공데이터포털에서 현재 `Gov24` official source-of-truth를 어디까지 확보했는가
2. `serviceField / userType / benefitType` 을 public codebook 없이도 어디까지 확정할 수 있는가
3. 추가로 받은 행정안전부 코드 파일들 중 무엇이 현재 `Gov24` 막힘 포인트와 직접 연결되는가

stable artifact:

- `tmp/gov24-public-data-source-findings/latest-gov24-public-data-source-findings.json`
- `tmp/gov24-agency-code-validation/latest-gov24-agency-code-validation.json`
- `tmp/gov24-axis-frequency/latest-gov24-axis-frequency.json`

## 결론

2026-06-02 기준 practical 결론은 아래와 같습니다.

1. `supportConditions` 의 `JA*` 코드는 public Swagger에서 **공식 설명**을 확보했다.
2. `serviceField / userType / benefitType` 은 public Swagger 기준 **enum/codebook 필드가 아니라 string 필드**다.
3. 따라서 위 3축은 현재 단계에서 external finite codebook을 기다리기보다 **live API inventory를 source-of-truth로 삼는 label-first 경계**가 맞다.
4. 추가로 받은 `xlsx` 9개는 현재 `Gov24` 3축 codebook 대체재가 아니다.
5. `법정동코드 전체자료.txt` 는 지역 보강 후보이지만, 현재 시스템이 쓰는 `5자리 시군구 코드`에 바로 꽂히지는 않는다.
6. `소관기관코드` 는 current `Gov24` runtime 기준 `390`개 고유값 전부가 기관코드 전체자료의 **현행 기관코드와 direct match** 된다.

## 확보한 official source

### 1. current dataset page

- `행정안전부_대한민국 공공서비스(혜택) 정보`
- <https://www.data.go.kr/data/15113968/openapi.do>

이 페이지에서 확인되는 것:

- current dataset page 자체
- `schema.org`: `/catalog/15113968/openapi.json`
- `DCAT`: `/dcat/metadata/15113968`
- Swagger UI 로드 스크립트

### 2. hidden Swagger JSON

dataset page HTML에서 실제 Swagger JSON endpoint를 확인했다.

- <https://infuser.odcloud.kr/api/stages/44436/api-docs?1684891964110>

이 Swagger에서 current operation set 은 아래 세 개다.

- `/gov24/v3/serviceList`
- `/gov24/v3/serviceDetail`
- `/gov24/v3/supportConditions`

### 3. current change notice

- <https://www.data.go.kr/bbs/ntc/selectNotice.do?originId=NOTICE_0000000004156>

이 공지에서 2025-06-13 기준 `/gov24/v3/serviceDetail` 변경 공지와
담당 부서 `행정안전부 행정서비스통합추진단 정부포털운영팀` 을 확인했다.

### 4. 기관코드 dataset page

- `행정안전부_행정표준코드_기관코드`
- <https://www.data.go.kr/data/15077870/openapi.do>

이 dataset의 참고문서와 페이지 본문에서 아래를 확인했다.

- 요청주소: `http://apis.data.go.kr/1741000/StanOrgCd2/getStanOrgCdList2`
- 응답 필드: `org_cd`, `full_nm`, `high_cd`, `typebig_nm`, `typemid_nm`, `typesml_nm` 등

즉 `Gov24` 의 `소관기관코드` 를 행안부 기관코드와 crosswalk 할 **공식 후보 source** 는 존재한다.
공공데이터포털 openapi 자체는 이 세션에서 `403/Unauthorized` 로 막혀 있었지만,
사용자가 제공한 `기관코드 전체자료` 로 current `Gov24` runtime 과의 direct match 여부는 별도로 검증했다.

### 5. public web search 재확인

2026-06-02 기준 아래 public web source도 다시 확인했다.

- `행정안전부` 보도자료
- `정부24` / `혜택알리미` 공개 UI
- `공공데이터포털` dataset / 공지
- `행정표준코드관리시스템`

재확인 결과:

1. `서비스분야 / 사용자구분 / 지원유형` 의 **공개 finite codebook** 은 찾지 못했다.
2. current public API / Swagger 에서는 위 3개가 여전히 `string` 필드다.
3. 2021-09-14 개편 공지 기준 old operation set 에 있던 `category`, `category-code` 는 current public operation set 에서 빠졌다.
4. `정부24/혜택알리미` UI 는 분류 라벨을 보여 주지만, 별도 codebook download 또는 standard code reference 는 노출하지 않는다.
5. `행정표준코드관리시스템` 에서도 위 3개와 직접 대응하는 공개 표준코드는 확인하지 못했다.

따라서 current public web source 전체를 기준으로 봐도,
위 3축은 **official code field** 보다는 **runtime label field** 로 읽는 편이 맞다.

## Swagger 기준으로 확정된 것

## 1. `serviceList` 3축은 string field다

Swagger `serviceList_model` 기준 아래 필드는 모두 `type=string` 이고 enum 정의가 없다.

- `서비스분야`
- `사용자구분`
- `지원유형`

즉 public Swagger만으로는

- `field name + code + official label`

형태의 finite codebook을 얻지 못한다.

현재 단계의 해석은 단순하다.

- 이 3축은 current API 기준 **code field가 아니라 live label field** 로 보는 편이 맞다.

## 2. `supportConditions` 는 공식 code description이 있다

Swagger `supportConditions_model` 에는 `JA*` 필드 설명이 직접 들어 있다.

대표 예시:

- `JA0101`: `남성`
- `JA0102`: `여성`
- `JA0110`: `대상연령(시작)`
- `JA0111`: `대상연령(종료)`
- `JA0328`: `장애인`
- `JA0329`: `국가보훈대상자`
- `JA0330`: `질병/질환자`
- `JA1101`: `예비창업자`
- `JA1102`: `영업중`
- `JA1103`: `생계곤란/폐업예정자`
- `JA1201`: `음식적업`
- `JA1202`: `제조업`
- `JA1299`: `기타업종`
- `JA2101`: `중소기업`
- `JA2102`: `사회복지시설`
- `JA2103`: `기관/단체`
- `JA2201`: `제조업`
- `JA2202`: `농업,임업 및 어업`
- `JA2203`: `정보통신업`
- `JA2299`: `기타업종`

따라서 `supportConditions` 쪽은 더 이상
“public source가 없어서 코드 의미를 전혀 모른다” 상태가 아니다.

## 3. `소관기관코드` 는 현행 기관코드와 direct match 한다

추가 확인 경로:

- `/mnt/c/Users/82103/Downloads/코드들/기관코드 전체자료/기관코드 전체자료.txt`
- `/mnt/c/Users/82103/Downloads/코드들/기관코드 전체자료/기관코드 전체자료(유형분류 의미추가).txt`

파일 성격:

- `cp949` 인코딩
- 탭 구분 텍스트
- 핵심 컬럼: `기관코드`, `전체기관명`, `대표기관코드`, `최상위기관코드`, `유형분류_*`, `존폐여부`

live `Gov24` 검증 결과:

- date: `2026-06-02`
- endpoint: `/gov24/v3/serviceList`
- `totalCount=10954`
- `소관기관코드` 고유값: `390`
- direct active match: `390/390`
- unmatched: `0`

즉 current `Gov24` 의 `소관기관코드` 는

- 별도 변환표가 필요한 unknown code가 아니라
- 사용자가 제공한 기관코드 전체자료의 **현행 기관코드와 1:1 direct match 되는 값**

으로 읽는 편이 맞다.

current runtime에서 보이는 기관 분포도 중앙부처만이 아니라

- `기초자치단체` `226`
- `중앙행정기관` `33`
- `정부출연기관` `29`
- `광역자치단체` `17`
- `시.도 교육청` `17`

처럼 넓다.

따라서 `소관기관코드` 쪽은 더 이상
“공식 코드체계를 몰라 blocked” 상태로 둘 이유가 약하다.
현 시점 practical source-of-truth 는

1. `Gov24 serviceList` 의 raw `소관기관코드`
2. 기관코드 전체자료의 `기관코드`

직접 매칭 조합으로 잡아도 된다.

## live API inventory 추출 결과

로컬 `.env` 의 `PUBLIC_DATA_PORTAL_API_KEY` 로 current API를 직접 호출했다.

기준:

- date: `2026-06-02`
- endpoint: `/gov24/v3/serviceList`
- `totalCount=10954`

## 1. `서비스분야`

exact label inventory는 `10개`다.

| label | 서비스 수 |
|---|---:|
| `생활안정` | 2277 |
| `농림축산어업` | 1674 |
| `보육·교육` | 1507 |
| `보건·의료` | 1221 |
| `임신·출산` | 916 |
| `고용·창업` | 846 |
| `문화·환경` | 666 |
| `보호·돌봄` | 640 |
| `행정·안전` | 637 |
| `주거·자립` | 570 |

## 2. `사용자구분`

raw 조합은 `14개` 이고, `||` split 기준 atomic token은 `4개`다.

atomic token:

| token | 서비스 수 |
|---|---:|
| `개인` | 9502 |
| `법인/시설/단체` | 1042 |
| `가구` | 701 |
| `소상공인` | 365 |

즉 practical canonical 관점에서는
`개인 / 가구 / 소상공인 / 법인/시설/단체` 네 축이 current runtime 기준선이다.

대표 raw 조합:

| raw label | 서비스 수 |
|---|---:|
| `개인` | 8988 |
| `법인/시설/단체` | 723 |
| `가구` | 425 |
| `소상공인` | 235 |
| `개인||가구` | 232 |
| `개인||법인/시설/단체` | 206 |
| `소상공인||법인/시설/단체` | 63 |

## 3. `지원유형`

raw 조합은 `170개` 이고, `||` split 기준 atomic token은 `20개`다.

atomic token:

| token | 서비스 수 |
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

즉 `지원유형` 은 raw 조합은 많지만,
current runtime token inventory는 이미 충분히 좁다.

대표 raw 조합:

| raw label | 서비스 수 |
|---|---:|
| `현금` | 4336 |
| `현물` | 1247 |
| `기타` | 717 |
| `이용권` | 604 |
| `현금(감면)` | 578 |
| `서비스(의료)` | 520 |
| `시설이용` | 449 |
| `현금(보험)` | 403 |
| `기타(교육)` | 378 |
| `현금(장학금)` | 364 |

따라서 external source 수집/정규화 관점의 current rule 은 아래처럼 읽는 편이 맞다.

1. `서비스분야`: raw exact label 유지
2. `사용자구분`: raw label 유지 + `||` split token 저장
3. `지원유형`: raw label 유지 + `||` split token 저장

## 받은 윈도우 코드 파일 평가

확인 경로:

- `/mnt/c/Users/82103/Downloads/코드들`

포함 파일:

- `가옥(주거형태)코드 조회자료.xlsx`
- `근거법령코드 조회자료.xlsx`
- `기초생활수급권자코드 조회자료.xlsx`
- `보훈대상자코드 조회자료.xlsx`
- `장애등급코드 조회자료.xlsx`
- `주택유형구분코드 조회자료.xlsx`
- `직군코드 조회자료.xlsx`
- `직종코드 조회자료.xlsx`
- `직종세분류코드 조회자료.xlsx`
- `법정동코드 전체자료.txt`

## 1. `xlsx` 9개

이 파일들은 모두 `코드값 / 코드값의미` 성격의 표이지만,
현재 `Gov24` 막힘 포인트와는 직접 대응하지 않는다.

직접 대응하지 않는 축:

- `서비스분야`
- `사용자구분`
- `지원유형`
- `supportConditions JA*`
- `소관기관코드`

즉 이번 `xlsx` 묶음은
현재 `Gov24` canonical blocked track을 푸는 official codebook 대체재로 쓰지 않는다.

## 2. `법정동코드 전체자료.txt`

이 파일은 `법정동코드 / 법정동명 / 폐지여부` 구조의 탭 구분 텍스트다.

기준:

- line count: `50100`
- sample: `1111000000 서울특별시 종로구 존재`

이 파일은 region 보강 source로는 쓸 수 있다.
다만 현재 시스템은 [RegionCodeUtil.java](/home/ubuntu/youth-welfare/backend/src/main/java/com/example/welfare/global/util/RegionCodeUtil.java:1) 기준으로
`5자리 시군구 코드`와 수기 alias map을 사용한다.

따라서 이 파일을 바로 꽂으려면 적어도 아래 중 하나가 필요하다.

1. `10자리 법정동코드 -> 5자리 시군구 코드` 축약 규칙 고정
2. 시도/시군구명 정규화 규칙과 충돌 없는지 검증
3. 기존 `SGG_CODE_MAP` 수기 매핑을 generated source로 바꿀지 결정

즉 usable candidate는 맞지만, 바로 current `Gov24` blocked track 해결책은 아니다.

## practical decision

현재 practical action은 아래 셋이다.

1. `supportConditions` 는 public Swagger description을 official source-of-truth로 사용
2. `serviceField / userType / benefitType` 은 live inventory 기반 label-first canonical 경계를 유지
3. `소관기관코드` crosswalk 는 기관코드 dataset을 follow-up source로 남기되, 실응답 검증 전까지는 hard dependency로 올리지 않음

이번 탐색으로 닫힌 것:

- `supportConditions` unmapped code가 public source 없이 완전히 불명확한 상태는 아님
- `서비스분야 / 사용자구분 / 지원유형` 은 현재 API 기준 codebook field가 아님
- 받은 `xlsx` 9개는 현재 `Gov24` 3축 source-of-truth가 아님

이번 탐색으로 아직 안 닫힌 것:

- `serviceField / userType / benefitType` stable code 발급
- `소관기관코드 -> 행정표준 기관코드` 실응답 검증
- `법정동코드.txt` 를 generated `RegionCodeUtil` source로 전환할지 여부
