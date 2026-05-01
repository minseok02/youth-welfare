# 신규 Policy Source 온보딩 체크리스트

관련 문서:

- [policy-source-onboarding-architecture.md](./policy-source-onboarding-architecture.md)
- [policy-source-onboarding-playbook.md](./policy-source-onboarding-playbook.md)
- [policy-source-canonical-onboarding-priority.md](./policy-source-canonical-onboarding-priority.md)
- [policy-normalization-current-state.md](./policy-normalization-current-state.md)

## 목적

이 문서는 신규 API/source를 받았을 때
실제로 무엇부터 확인하고,
어디서 계속 가고,
어디서 멈춰야 하는지를 빠르게 정리한 runbook 입니다.

긴 설계 배경은 [policy-source-onboarding-architecture.md](./policy-source-onboarding-architecture.md) 를 보고,
실무에서는 이 문서만 따라가면 됩니다.

## 0. 먼저 답해야 하는 질문

새 source를 받으면 제일 먼저 아래 세 질문에 답합니다.

1. 이 row는 `정책` 인가, `listing` 인가, `reference` 인가
2. official field 만으로 핵심 의미를 설명할 수 있는가
3. codebook/schema 없이는 절대 열면 안 되는 필드가 있는가

이 세 개가 안 풀리면 구현부터 시작하지 않습니다.

## 1. 1차 분류

아래 중 하나로 먼저 분류합니다.

### A. 정책형

조건:

- row 하나가 정책/지원제도/프로그램 1건 의미
- 추천 카드 1개로 보여도 의미가 유지됨
- 대상, 지원내용, 기관, 신청기한 같은 정책 필드가 존재

다음 단계:

- `welfare_services` 후보로 진행
- `raw_api_payloads`
- `service_taxonomies`
- `service_facts`

### B. listing형

조건:

- row 하나가 공고/채용건/회차/모집건 inventory
- 정책 자체보다 모집/공급/공고 속성이 중심

다음 단계:

- `welfare_services` 에 바로 넣지 않음
- listing domain 분리 검토
- 정책 추천 lane과 분리

### C. reference matrix형

조건:

- row 하나가 제도 안내용 표/매트릭스/상태표
- 정책 카드보다 참조 데이터 성격이 강함

다음 단계:

- reference table 분리
- 정책 row 보강용만 허용

## 2. 최소 inventory

아래 항목을 먼저 적습니다.

- source name
- list endpoint
- detail endpoint
- codebook/schema endpoint
- external id field
- update cadence
- row grain 메모

이 단계에서 아직 코드는 건드리지 않습니다.

## 3. raw 저장 가능 여부

다음이 가능해야 합니다.

- list/category/detail payload raw 저장
- external id 기준 재수집 가능
- sync log 남김 가능

체크:

- raw payload json 그대로 보존 가능한가
- list/detail 구분이 가능한가
- source_type 을 명확히 줄 수 있는가

안 되면 canonical 전에 raw ingest 경계부터 고칩니다.

## 4. canonical 승격 후보 확인

정책형 source만 이 단계로 갑니다.

우선 추출할 필드:

- title
- summary
- provider/organization
- detail url
- deadline
- age
- income
- target group
- provision method

판정:

- official field로 바로 쓸 수 있는가
- 아니면 text/rule-derived fallback이 필요한가
- 아예 official source가 없어서 blocked 로 둬야 하는가

## 5. taxonomy / codebook 판정

아래 중 하나면 바로 blocked 또는 metadata-only 입니다.

- official code/label 대응이 없음
- finite inventory가 안 보임
- codebook 없이는 label 의미를 확정할 수 없음

즉:

- `codebook needed`
- `provider response needed`
- `metadata-only for now`

중 하나로 명시합니다.

무리해서 `hard mapping SQL` 을 열지 않습니다.

## 6. compat / recommendation 영향 확인

정책형 source가 current recommendation lane에 들어갈 경우만 확인합니다.

체크:

- `unifiedCategory` 를 current response 계약에 맞게 유지할 수 있는가
- canonical taxonomy는 sidecar hint로만 둘 수 있는가
- read-model / retrieval / scoring 을 흔들지 않는가

원칙:

- canonical이 생겨도 바로 response 대표 category를 바꾸지 않음
- compat bridge 가 필요하면 bridge layer로만 둠

## 7. 여기서 멈춰야 하는 경우

아래면 지금 단계에서는 구현을 멈추는 게 맞습니다.

- row grain이 섞여 있음
- codebook 없이는 taxonomy를 열 수 없음
- listing형인데 정책형처럼 넣으려 하고 있음
- reference matrix인데 정책 row로 늘리려 하고 있음
- official field 없이 title keyword만으로 hard mapping 하려 하고 있음

멈추는 것은 실패가 아니라 정상 판정입니다.

## 8. 최종 판정 템플릿

신규 source마다 아래 형식으로 남깁니다.

```md
## <SOURCE_NAME>

- type: policy | listing | reference
- raw ingest: yes | no
- canonical direct onboarding: yes | partial | no
- compat bridge needed: yes | no
- codebook needed: yes | no
- blocked reason: ...
- next action: ...
```

## 9. practical next action

새 source를 받으면 실제 순서는 이겁니다.

1. `정책형 / listing형 / reference형` 분류
2. minimal inventory 작성
3. raw ingest 가능 여부 확인
4. 정책형이면 canonical 승격 후보 확인
5. codebook 필요 여부 판정
6. recommendation 영향 확인
7. `진행 / metadata-only / blocked` 중 하나로 종료

## 요약

1. source를 받으면 바로 구현하지 않습니다.
2. 먼저 row grain 을 분류합니다.
3. raw 저장 경계를 먼저 확보합니다.
4. official field가 있을 때만 canonical을 강하게 올립니다.
5. codebook 없으면 멈춥니다.
6. compat/recommendation 영향은 마지막에 봅니다.
