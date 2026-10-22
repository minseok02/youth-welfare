# 신규 정책 소스 검토 템플릿

[온보딩 가이드](policy-source-onboarding-checklist.md)에 따라 새 source의 검토 결과를 작성합니다. 동일 source의 기존 note를 먼저 갱신하고, 별도 기록이 필요하면 `policy-source-<name>-onboarding-note.md` 또는 `policy-source-<name>-inventory.md`로 저장합니다. 확인하지 못한 항목은 빈칸 대신 미확인·보류 이유를 남깁니다.

## 1. 대상과 판정

- Source/provider:
- 검토일·검토자:
- Row가 뜻하는 것과 근거:
- 유형: `policy | listing | reference | mixed`
- 현재 상태: `draft | metadata-only | blocked | ready for implementation`
- 최종 판정: `진행 | 일부 필드 보류 | metadata-only | blocked`
- 허용 필드 / 보류 필드 / 필요한 provider 응답:

## 2. Endpoint와 저장

- List / detail / support / codebook·schema endpoint와 근거:
- 인증·API key·quota / 갱신 주기 / 외부 ID·source namespace:
- 관측 환경·앱/DB 대상·시각 / 대표 sample 범위:
- Payload 저장 가능 여부: `yes | no | partial`
- HTTP 원문 또는 DTO 재직렬화 / 누락·변환·덮어쓰기:
- Category별 연결 키·저장 시각·hash·sync log:
- 저장 실패 처리 / 재수집·detail refresh·backfill 경계:
- 정책·listing·reference 저장 대상과 분리 이유:

## 3. 필드와 근거

필요한 필드마다 아래 표에 행을 추가합니다. 금액·비율·구간·기준 연도·가구 기준과 미지정/null을 구분합니다.

| Canonical 후보 | 원본 field·값·근거 | 단위·연산자·누락 의미 | Authority·규칙 버전 | 진행·보류 이유 |
| --- | --- | --- | --- | --- |
| title / summary / provider / URL | | | | |
| deadline / age / income / target group / provision method | | | | |

- Canonical 직접 적재: `yes | partial | no`
- List/detail 필드 소유권 / 사라진 fact·삭제·수동 보정 보존:
- Official code/label·codebook 있음: `yes | no | partial`
- 관측 label inventory 범위·날짜 / 검토 근거:
- Mapping basis: `official-codebook | reviewed-label-internal-code | metadata-only`
- 내부 코드와 공식 코드 구분 / raw label 보존 / 규칙 버전:
- Unknown label 처리 / 재검토·provider 응답 조건:
- AI 제안의 원문 근거·불확실성·검수 기준:
- Hard filter에 허용할 근거와 soft signal 전용 필드:

## 4. 호환과 코드 연결

- `unifiedCategory` 응답·compat bridge 영향과 위험도:
- 추천 연결: `yes | no | later` / sidecar 보조 신호만 사용할지:
- Adapter·client/DTO·source enum/분기·DB 제약 변경:
- Raw / mapper·saver / aggregate·sidecar 연결:
- Read-model·retrieval·scoring·priority matcher 영향:
- 코드 위치는 [가이드의 진입점](policy-source-onboarding-checklist.md#6-코드-진입점)에서 선택:

## 5. 검증과 다음 작업

- Sample 20~50건과 누락·unknown·변경/삭제·혼합 grain·부분 실패 사례:
- 실제 요청/저장/실패 집계·sidecar 적재·후속 상세/support 결과:
- Upsert·재처리·보정 보존·API/추천 회귀 검사와 결과:
- 실행 권한·외부 호출량/비용·PII·운영 영향과 복구 범위:
- 지금 할 일 / 하지 않을 일 / 완료 조건:
- 남은 blocker·승인/자료 필요 사항 / 다음 액션:
