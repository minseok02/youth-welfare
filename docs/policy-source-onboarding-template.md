# 신규 Policy Source 온보딩 템플릿

관련 문서:

- [policy-source-onboarding-architecture.md](./policy-source-onboarding-architecture.md)
- [policy-source-onboarding-checklist.md](./policy-source-onboarding-checklist.md)
- [policy-source-code-entrypoints.md](./policy-source-code-entrypoints.md)

## 사용법

새 source를 검토할 때 이 파일을 복사해서 초안 문서로 씁니다.

권장 파일명 예시:

- `policy-source-<source-name>-onboarding-note.md`
- `policy-source-<source-name>-inventory.md`

이 템플릿은:

- row grain 판정
- raw ingest 가능 여부
- canonical 승격 범위
- blocked 이유
- 다음 액션

을 한 번에 남기기 위한 최소 형식입니다.

---

## 1. Source 기본 정보

- source name:
- owner/provider:
- source kind:
  - `policy`
  - `listing`
  - `reference`
- current status:
  - `draft`
  - `metadata-only`
  - `blocked`
  - `ready for implementation`

## 2. Endpoint inventory

- list endpoint:
- detail endpoint:
- codebook/schema endpoint:
- auth/api key required:
- update cadence:
- external id field:

## 3. Row grain 판정

### 판정

- 최종 분류:
  - `policy`
  - `listing`
  - `reference`

### 이유

- row 하나가 의미하는 것:
- `welfare_services` row로 넣어도 의미가 유지되는가:
- listing/reference로 분리해야 하는 이유:

## 4. Raw ingest 판정

- raw payload 저장 가능 여부: `yes | no | partial`
- list/detail/category 구분 가능 여부:
- 재수집 키(external id) 안정성:
- `raw_api_payloads` 저장 전략 메모:

## 5. Canonical direct onboarding 판정

- canonical direct onboarding:
  - `yes`
  - `partial`
  - `no`

### official field inventory

- title:
- summary:
- provider:
- detail url:
- deadline:
- age:
- income:
- target group:
- provision method:

### 메모

- official field만으로 충분한 것:
- text/rule-derived fallback 필요한 것:
- 아직 확정 못 하는 것:

## 6. Taxonomy / codebook 판정

- official code/label 있음: `yes | no | partial`
- finite inventory 보임: `yes | no | partial`
- codebook required: `yes | no`
- provider/operator response needed: `yes | no`

### blocked 이유

- 예:
  - `serviceField finite inventory not visible`
  - `official code-label mapping missing`
  - `row grain mixed`

## 7. Compat / recommendation 영향

- current recommendation lane 직접 연결 대상인가: `yes | no | later`
- compat bridge needed: `yes | no`
- `unifiedCategory` 흔들릴 위험: `low | medium | high`

### 메모

- current response contract 영향:
- canonical sidecar만 먼저 쓸 수 있는가:
- recommendation 연동은 지금 열어도 되는가:

## 8. 코드 진입점 후보

### collect

- `CollectSource` 추가 필요 여부:
- adapter 추가 후보:
- client/dto 추가 후보:

### raw ingest

- `RawApiPayloadService` 사용 방식:

### canonical

- `WelfareServiceMapper` 확장 필요 여부:
- `NormalizedPolicyAggregate` 생성 필요 여부:
- `DeferredNormalizedPolicySidecarWriter` 연동 범위:

### recommendation

- `CanonicalRecommendationReadModelRepository` 영향:
- retrieval/scoring 영향:

## 9. 최종 판정

- type: `policy | listing | reference`
- raw ingest: `yes | no | partial`
- canonical direct onboarding: `yes | no | partial`
- compat bridge needed: `yes | no`
- codebook needed: `yes | no`
- blocked reason:
- next action:

## 10. 지금 당장 할 일

1.
2.
3.

## 11. 지금 하지 않을 일

1.
2.
3.
