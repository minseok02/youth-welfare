# `YOUTH_MID` stable code source plan

이 문서는 온통청년 `YOUTH_MID` stable code mapping SQL을 **언제 다시 열 수 있는지**와, 어떤 근거는 충분하고 어떤 근거는 불충분한지를 정리하기 위한 메모입니다.

관련 문서:

- [policy-normalization-youth-mid-live-inventory.md](./policy-normalization-youth-mid-live-inventory.md)
- [policy-normalization-youth-mid-alias-rules.md](./policy-normalization-youth-mid-alias-rules.md)
- [policy-normalization-schema-draft.md](./policy-normalization-schema-draft.md)
- [db-migration.md](./db-migration.md)
- [phase-plan.md](./phase-plan.md)
- [troubleshooting-log.md](./troubleshooting-log.md)

## 현재 상태

2026-04-30 live inventory 기준:

- authenticated 온통청년 목록 응답에는 `srchPolyBizSecd` 필드가 직접 보이지 않았다.
- 대신 `plcyMajorCd`, `jobCd`, `schoolCd`, `sbizCd` 같은 code-like field는 보였지만, `mclsfNm` 과 1:1 대응하는 stable mid-category key로 보기 어려웠다.
- 공개 오픈 API 소개 문서에는 `srchPolyBizSecd=003002001,003002002` 예시만 있고, 전체 inventory는 없다.

따라서 현재는 `YOUTH_MID = label-only taxonomy` 가 유지 기준이다.

## reopen 조건

아래 중 하나가 확보되어야 `YOUTH_MID` stable code mapping SQL 초안을 다시 연다.

### 1. authenticated metadata source

다음과 같은 **직접 metadata** 가 필요하다.

- `srchPolyBizSecd -> label` 전체 inventory
- 각 code의 official label
- 가능하면 sort order / active 여부

예:

- 로그인 세션 기반 open API metadata endpoint
- 마이페이지 오픈 API 관리 화면에서 내려받는 code inventory
- 내부 운영 화면/응답이 직접 code-label mapping을 제공하는 경우

### 2. operator-provided official export

다음도 허용 가능한 source다.

- 온통청년 운영 측이 제공한 CSV/XLSX/PDF 코드정의서
- 관리자 화면 export
- 정책 데이터 운영 담당자가 전달한 공식 codebook

핵심은 `모든 YOUTH_MID code` 와 `official label` 이 source 문서 안에서 직접 대응되어야 한다는 점이다.

## reopen 불가 근거

아래 근거만으로는 stable code mapping SQL을 열지 않는다.

### 1. 공개 HTML 예시

- `srchPolyBizSecd=003002001,003002002` 같은 요청 예시 두세 개
- API 소개 페이지의 파라미터 샘플

이건 전체 inventory가 아니라 example일 뿐이다.

### 2. live 목록 payload의 broad code-like field

- `plcyMajorCd`
- `jobCd`
- `schoolCd`
- `sbizCd`

이 값들은 여러 `mclsfNm` 에 걸쳐 반복되거나 multi-code가 섞여 있어, `YOUTH_MID` stable code 대체 축으로 보기 어렵다.

### 3. `mclsfNm` label 자체에서의 역추론

- `청년참여 -> 어떤 code일 것` 식 추정
- combo/alias를 split 해서 code를 임의 배정하는 방식

이건 label inventory 확인에는 쓸 수 있어도 stable code 확정 근거로는 부족하다.

## source 우선순위

현재 단계에서 다음 순서로 source를 찾는다.

1. authenticated 온통청년 open API metadata/testbed
2. 온통청년 마이페이지 OPEN API 관리 화면/다운로드 경로
3. 운영 담당자 제공 공식 codebook/export

이 중 어느 경로에서도 `code -> label` 전체 inventory가 확보되지 않으면:

- `normalization_codes(YOUTH_MID)` 는 계속 비운다
- `service_taxonomy_terms(term_code='')` label-only 전략을 유지한다
- `YOUTH_MID_RAW_ALIAS` 보존 정책도 그대로 유지한다

## 실무 의미

`YOUTH_MID stable code mapping SQL` 은 단순 SQL 작성 문제가 아니다. 먼저 **reliable source of truth** 가 있어야 한다.

지금 상태에서 가장 안전한 판단은:

1. live payload inventory는 이미 충분히 확인했다
2. stable code source는 아직 확보되지 않았다
3. 따라서 다음 액션은 SQL 작성이 아니라 metadata/codebook source 확보다
