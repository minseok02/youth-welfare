# 정책 정규화 현재 동작과 확인 과제

문서군 진입점: [policy-docs-index.md](policy-docs-index.md)

## 구현과 환경

수집 결과의 source별 의미를 `service_taxonomies`, `service_taxonomy_terms`, `service_facts`와 summary slot에 보존하고 추천·화면의 호환 표현과 연결합니다. 상세 구현은 [DeferredNormalizedPolicySidecarWriter](../../backend/src/main/java/com/example/welfare/collect/normalization/DeferredNormalizedPolicySidecarWriter.java), [추천 read-model](../../backend/src/main/java/com/example/welfare/recommend/repository/CanonicalRecommendationReadModelRepository.java)을 확인합니다.

데이터 건수와 coverage는 로컬 DB·로컬 앱과 RDS·운영 서버에서 다를 수 있습니다. [환경 구분](../core/environment-guide.md) 없이 서로의 수치를 현재 기준선으로 대체하지 않습니다.

## 보존할 의미와 출처

- 원래 source의 필드·코드·라벨과 내부 분류를 구분합니다. 공식 코드가 없는 관측 라벨의 내부 code를 외부 공식 code로 설명하지 않습니다.
- `OFFICIAL`, 파생 규칙, AI 보완 결과의 출처와 근거를 구분합니다. AI 판단을 근거 없이 확정 자격조건으로 승격하지 않습니다.
- taxonomy·fact 저장과 기존 `unifiedCategory` 응답 호환은 서로 다른 경계입니다.
- list와 detail이 제공하는 필드 범위가 다릅니다. 한 endpoint의 누락만으로 다른 endpoint가 제공한 fact를 전부 삭제하지 않습니다.
- 원본 재처리, 단위·기준 시점·미지정, source 변경과 수동 보정값의 우선순위는 명시적으로 검토합니다.

## 현재 연결된 범위

- YOUTH major/mid 라벨과 raw alias, Gov24 분야·대상·지원유형 term, support condition fact를 수집·조회 경로에서 사용합니다.
- Gov24의 문자열 라벨과 내부 seed/code map은 [Gov24TaxonomyCodeSupport](../../backend/src/main/java/com/example/welfare/collect/support/Gov24TaxonomyCodeSupport.java)와 migration으로 관리합니다. 외부 공식 codebook과 동일하다는 뜻은 아닙니다.
- 추천 projection·priority matcher·표현 계층은 저장 데이터의 서로 다른 부분을 읽습니다. 저장 coverage가 곧 자격판정 정확성이나 추천 품질을 뜻하지 않습니다.
- `YOUTH 0/0 income` 해석의 과거 근거는 [소득 sentinel 정책](../history/policy/policy-normalization-youth-income-zero-policy.md)에 있습니다. 사용자 소득 구간과 source 금액·비율이 같은 단위라는 가정은 별도 검증 대상입니다.

## 리팩토링 확인 과제

| 과제 | 확인할 내용 |
| --- | --- |
| 원문과 이력 | DTO 재직렬화가 아닌 응답 원문 보존 여부, 재수집 덮어쓰기와 재처리 가능성 |
| fact 갱신 | endpoint 소유 범위, set-like union으로 사라진 조건이 남는지, 누락과 삭제 구분 |
| 운영 보정 | 지역 보정 보호와 일반 필드 보정의 재수집 우선순위 차이 |
| 자격조건 | 금액·비율·구간·연도·가구 기준·미입력을 함께 표현할 수 있는지 |
| source 변경 | schema/codebook 변경 감지, 부분 수집과 전체 inventory 완료의 구분 |
| AI 보완 | 원문 근거, 판단 불확실성, 검토·재처리 및 평가 기준 |

이 항목은 문제 재현과 개선 작업을 위한 목록이며 이번 문서 정리에서 기능 수정은 수행하지 않습니다.

## 신규 source와 운영

- 분류·근거·실행 순서: [온보딩 가이드](policy-source-onboarding-checklist.md).
- 검토 결과 작성: [온보딩 템플릿](policy-source-onboarding-template.md).
- 공식 코드 자산: [코드북 출처와 변환](policy-user-provided-official-codebooks.md).
- 운영 queue: [품질 triage](policy-data-quality-triage-runbook.md).
- Gov24 개별 범위: [blocked/deferred 상태](policy-gov24-lane-closeout.md#보류와-재검토). 실제 수집과 외부 codebook 대기를 하나의 blocked 상태로 묶지 않습니다.

## 과거 조사와 관측

로컬 inventory·서버 coverage·단계별 실험·closeout 기록은 [정리 전 정규화 기록](../history/policy/normalization-state-before-doc-cleanup.md)에 보존합니다. 상세 설계와 운영 명령은 환경·구현 상태를 확인한 뒤 사용합니다. 현재 작업 방향은 [current-state.md](../current-state.md)를 따릅니다.
