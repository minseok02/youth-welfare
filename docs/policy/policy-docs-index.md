# 정책 데이터 문서 목차

현재 계약과 미해결 과제는 [정규화 현재 상태](policy-normalization-current-state.md), 전체 작업은 [current-state](../current-state.md)를 봅니다. 실행 전 [환경 안내](../core/environment-guide.md)로 앱/DB와 쓰기·외부 호출 영향을 확인합니다. 과거 active/deferred/closeout·관측값은 현재 기능 동결이나 서버 상태를 대신하지 않습니다.

## 현재 계약과 작업

| 목적 | 문서 |
| --- | --- |
| Gov24 저장·label·코드·소비·과거 관측 | [데이터 계약](policy-gov24-lane-closeout.md) |
| Gov24 변경·검증·자료 수신 | [작업 체크리스트](policy-gov24-implementation-checklist.md) |
| Status filter·마감 처리 | [필터 설계](policy-status-filter-design.md) |
| 신규 source 분류·근거·코드·검증 | [온보딩 가이드](policy-source-onboarding-checklist.md), [검토 템플릿](policy-source-onboarding-template.md) |
| 공식 코드 자산·변환 | [제공 코드북](policy-user-provided-official-codebooks.md) |
| Gov24 근거·세부 규칙 | [공공데이터 조사](policy-gov24-public-data-source-findings.md), [mapping 초안](policy-gov24-canonical-mapping-draft.md), [UX grouping](policy-gov24-benefit-type-grouping-draft.md) |
| 추천 저장·projection·응답 호환 | [read-model 설계](policy-normalization-recommendation-read-model.md), [이행 순서](policy-normalization-recommendation-migration-order.md), [응답 bridge 배경](../history/policy/policy-normalization-unified-category-response-bridge.md) |
| 필터 option/codebook 동기화 제안 | [sync plan](policy-filter-codebook-sync-plan.md) |

## 품질 조회와 증거

| 볼 문제 | 문서 |
| --- | --- |
| Gov24 coverage·raw shape·지원조건 결측 | [runtime audit](policy-gov24-runtime-audit-runbook.md), [support inventory](policy-gov24-support-unmapped-inventory.md) |
| Retrieval/category daily 관측·분포 | [quality summary](policy-quality-summary-runbook.md); `run-local-policy-quality-observation-suite.sh`의 summary/note/JSON에서 dataset_key·scenario_count·gate·category를 확인 |
| 필드 누락·중복 후보 | [data quality](policy-data-quality-audit-runbook.md) |
| 링크 공백·참고 URL | [link quality](policy-link-quality-audit-runbook.md), [YOUTH 표본](policy-link-review-sample-audit-runbook.md) |
| 신청기간·상태 mismatch | [기간 감사](policy-application-period-quality-audit-runbook.md) |
| 기관명 누락·source contract | [기관 감사](policy-host-org-quality-audit-runbook.md) |
| YOUTH 실제 중복 후보 | [후보 감사](policy-youth-duplicate-candidate-audit-runbook.md): 기관·기간·same/mirror URL을 구분 |
| Triage compact 관측 | [observation](policy-data-triage-observation-runbook.md) |

wrapper `run-local-policy-quality-summary.sh`, `run-local-gov24-quality-audit.sh`도 환경·권한을 확인한 뒤 사용합니다. 저장 coverage와 실제 추천 정확성은 다릅니다.

## 상태 변경과 운영 처리

조회 결과가 있다는 이유로 write를 자동 실행하지 않습니다.

- 오류 제보·중복·링크의 운영 처리: [triage](policy-data-quality-triage-runbook.md).
- 중복 queue와 review: [duplicate review](policy-duplicate-review-runbook.md).
- 링크 queue와 상태 처리: [link review queue](policy-link-review-queue-runbook.md).
- 만료 상태 재정렬: [status sync](policy-status-sync-runbook.md).
- reference URL·embedding·retrieval gate·category admin: [bounded runtime](policy-admin-runtime-runbook.md).

실행 결과는 wrapper/command·환경/대상·query override·핵심 data 필드·baseline 차이·실패/skip으로 남깁니다. raw 잔량과 실제 OPEN queue를 구분하고 처리 순서는 triage의 현재 계약을 따릅니다.

## 설계 배경과 제품 제안

[신청 내비게이터 검토](policy-application-navigator-feasibility.md)는 Gov24 전체형과 온통청년/복지로 보수형 guide grade를 나누는 제안입니다. 현재 구현으로 가정하지 않습니다.

`history/policy/policy-normalization-*`의 taxonomy·compat·fact/schema 결정, education/OpenAI replay 실험, 복지로 detail/gap-fill 조사와 [listing 초안](../history/policy/policy-listing-source-schema-draft.md)·[장학금 참조행렬](../history/policy/policy-scholarship-reference-matrix-draft.md)은 고유 근거가 필요할 때 읽습니다. 이력 진입점은 [history 목차](../history-docs-index.md)입니다.

외부 codebook 재수입·raw hard eligibility와 현재 runtime/내부 코드의 차이는 [Gov24 보류 경계](policy-gov24-lane-closeout.md#보류와-재검토)를 따릅니다. 초기 정책 우선순위·pending·track split은 [날짜가 있는 계획](policy-gov24-lane-closeout.md#날짜가-있는-관측과-초기-계획)으로 통합됐으며 새 작업 승인이나 버그 수정 금지 규칙이 아닙니다.
