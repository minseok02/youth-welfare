# Policy Application Navigator Feasibility

작성일: 2026-06-18

## 목적

이 문서는 정책별 신청을 직접 판정하거나 대행하는 기능이 아니라, 사용자가 직접 신청 가능 여부를 확인할 수 있도록 신청 조건, 서류, 링크, 문의처, 마감일을 순서대로 정리하는 `신청 내비게이터` 기능의 현실성을 검토한 기록입니다.

## 결론

`신청 내비게이터` MVP는 실제 구현 가능합니다.

단, 모든 정책에 같은 수준의 안내를 제공할 수는 없습니다. 출처별 데이터 품질 차이가 크므로 정책별 guide grade를 계산해 화면을 다르게 보여야 합니다.

- Gov24: 신청방법, 구비서류, 문의처, 온라인신청 URL이 구조화돼 있어 가장 강한 대상입니다.
- 온통청년: 신청방법과 신청/참고 URL은 있으나 제출서류 구조화가 약합니다.
- 복지로 지자체: 신청방법과 상세 링크는 쓸 수 있으나 제출서류 구조화가 없습니다.
- 복지로 중앙: 상세 링크와 대상/선정기준은 있으나 신청방법 텍스트가 약해 축소형 안내가 맞습니다.

따라서 기능 목표는 아래로 고정합니다.

> 신청 가능 여부를 확정하지 않고, 사용자가 직접 확정 판단을 할 수 있도록 확인 순서와 준비 항목을 정리한다.

## 하지 않을 것

- 신청 가능 여부 확정
- 선정 가능성 예측
- 서류 완비 확정
- 자동 신청
- 기관 접수 상태 확인
- 체크리스트 완료를 실제 신청 완료로 간주

## 현재 코드 재료

정책 기본 정보:

- `welfare_services.apply_method_name`
- `welfare_services.apply_start_date`
- `welfare_services.apply_end_date`
- `welfare_services.detail_url`
- `welfare_services.is_online_apply`

정책 상세 정보:

- `welfare_service_details.target_detail`
- `welfare_service_details.support_detail`
- `welfare_service_details.apply_method_detail`
- `welfare_service_details.selection_criteria`
- `welfare_service_details.contact_list`
- `welfare_service_details.form_files`
- `welfare_service_details.reference_urls_json`

기존 화면/API:

- 정책 상세는 `PolicyDetailService`에서 `WelfareService + WelfareServiceDetail + regions + tags`를 묶어 내려줍니다.
- 정책 상세 화면에는 이미 신청 방법, 관련 사이트 보기, 자격 확인 CTA가 있습니다.
- 알림에는 북마크 정책 마감 임박 알림 경로가 있습니다.
- 챗봇은 정책 후보/evidence 안에서만 답하는 JSON 기반 계약을 이미 갖고 있습니다.

## DB 확인 결과

운영 RDS read-only 계정으로 2026-06-18 기준 visible 정책을 집계했습니다. visible 기준은 `status IN ('ACTIVE','UPCOMING')` 입니다.

### 출처별 필드 커버리지

| source_type | total | visible | has_any_apply_text | has_form_files | has_any_link | has_contact | has_target_detail | has_selection_criteria | has_apply_end_date |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| BOKJIRO_CENTRAL | 162 | 162 | 0 | 0 | 162 | 161 | 162 | 162 | 0 |
| BOKJIRO_LOCAL | 1,228 | 1,218 | 1,093 | 0 | 1,228 | 0 | 1,224 | 1,224 | 0 |
| GOV24 | 10,957 | 10,673 | 10,957 | 10,955 | 10,957 | 10,954 | 10,955 | 1,054 | 441 |
| YOUTH | 2,642 | 1,440 | 1,412 | 0 | 2,083 | 0 | 0 | 0 | 1,321 |

해석:

- Gov24는 신청 도우미에 필요한 핵심 필드가 가장 많이 채워져 있습니다.
- 온통청년은 신청기간/신청방법/링크는 일부 좋지만 서류와 문의처가 약합니다.
- 복지로 지자체는 신청방법/상세 링크는 좋지만 서류와 문의처 구조화가 없습니다.
- 복지로 중앙은 상세 링크/대상/선정기준은 있으나 신청방법 텍스트가 비어 있어 원문 확인 중심입니다.

### 신청 방식 다양성

visible 정책에서 신청 방법 텍스트를 키워드로 분류했습니다. 한 정책이 여러 방식에 동시에 해당할 수 있습니다.

| source_type | visible | has_method_text | online_like | visit_like | email_like | postal_like | fax_like | phone_or_contact_like | ambiguous_like |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| BOKJIRO_CENTRAL | 162 | 0 | 16 | 0 | 0 | 0 | 0 | 0 | 0 |
| BOKJIRO_LOCAL | 1,218 | 1,083 | 283 | 764 | 26 | 31 | 11 | 59 | 322 |
| GOV24 | 10,673 | 10,673 | 3,054 | 8,544 | 345 | 528 | 312 | 1,251 | 1,924 |
| YOUTH | 1,440 | 781 | 535 | 367 | 114 | 54 | 6 | 115 | 226 |

실제 샘플에서 확인된 신청 방식:

- 온라인 신청
- 정부24/복지로/기관 홈페이지 신청
- 방문 신청
- 방문 또는 이메일 신청
- 이메일, 팩스, 방문 중 선택
- 우편 또는 방문
- 전화/상담 후 방문
- 구글폼 접수
- 읍/면/동 행정복지센터 방문 접수

따라서 단일 `신청하기` 버튼보다 `신청 경로 선택` 단계가 필요합니다.

### 서류 데이터 품질

visible 상세 기준 의미 있는 서류 텍스트를 키워드로 추정했습니다.

| source_type | visible_with_detail | has_docs | likely_meaningful_docs | docs_len_50_plus | contains_none | usable_docs_estimate |
|---|---:|---:|---:|---:|---:|---:|
| BOKJIRO_CENTRAL | 162 | 0 | 0 | 0 | 0 | 0 |
| BOKJIRO_LOCAL | 1,214 | 0 | 0 | 0 | 0 | 0 |
| GOV24 | 10,671 | 10,671 | 5,151 | 5,634 | 10,455 | 5,151 |
| YOUTH | 1,440 | 0 | 0 | 0 | 0 | 0 |

해석:

- 서류 준비 단계까지 강하게 만들 수 있는 것은 주로 Gov24입니다.
- Gov24도 `해당없음` 문구가 많아, `form_files` 존재만으로 서류가 있다고 보면 안 됩니다.
- 온통청년/복지로는 서류 단계에서 `원문/기관 확인 필요`를 기본값으로 두어야 합니다.

### 링크 타입

`reference_urls_json`의 타입 분류는 쓸 수 있습니다.

| source_type | visible | has_detail_url | has_refs | refs_apply | refs_detail | refs_reference | refs_extracted |
|---|---:|---:|---:|---:|---:|---:|---:|
| BOKJIRO_CENTRAL | 162 | 162 | 162 | 16 | 146 | 0 | 1 |
| BOKJIRO_LOCAL | 1,218 | 1,218 | 1,214 | 259 | 955 | 0 | 68 |
| GOV24 | 10,673 | 10,673 | 10,671 | 1,911 | 10,671 | 0 | 1,177 |
| YOUTH | 1,440 | 1,258 | 1,261 | 441 | 0 | 1,036 | 38 |

해석:

- 신청 링크(`APPLY`)가 있는 정책은 신청 단계에서 직접 CTA를 제공할 수 있습니다.
- 신청 링크가 없고 상세 링크만 있는 경우는 `원문에서 신청 경로 확인` 단계로 둡니다.
- 본문 추출 링크(`EXTRACTED_FROM_TEXT`)는 낮은 신뢰도 링크로 표시해야 합니다.

### Guide Grade 산정

신청 내비게이터 품질 등급을 아래처럼 나눴습니다.

- A: 신청방법 + 신청 링크 + 의미 있는 서류 + 문의처
- B: 신청방법 + 링크 + 의미 있는 서류
- C: 신청방법 + 링크, 서류 없음
- D: 링크 + 자격 설명, 신청방법 약함
- E: 신호 부족

결과:

| guide_grade | count | pct_visible |
|---|---:|---:|
| A_full_steps_docs_apply_contact | 911 | 6.8% |
| B_steps_docs_link | 4,240 | 31.4% |
| C_steps_link_no_docs | 7,303 | 54.1% |
| D_link_eligibility_no_method | 297 | 2.2% |
| E_low_signal | 742 | 5.5% |

출처별:

| source_type | guide_grade | count |
|---|---|---:|
| BOKJIRO_CENTRAL | D_link_eligibility_no_method | 162 |
| BOKJIRO_LOCAL | C_steps_link_no_docs | 1,083 |
| BOKJIRO_LOCAL | D_link_eligibility_no_method | 135 |
| GOV24 | A_full_steps_docs_apply_contact | 911 |
| GOV24 | B_steps_docs_link | 4,240 |
| GOV24 | C_steps_link_no_docs | 5,522 |
| YOUTH | C_steps_link_no_docs | 698 |
| YOUTH | E_low_signal | 742 |

해석:

- `A/B` 5,151건은 서류 준비 단계까지 실용적으로 만들 수 있습니다.
- `C` 7,303건은 신청 경로/순서 안내는 가능하지만 서류는 원문 확인 처리해야 합니다.
- `D/E` 1,039건은 축소형 또는 안내 미제공이 맞습니다.

## 기능 설계 판단

### 등급별 화면 전략

#### A/B: 전체 신청 내비게이터

- 신청 대상 확인
- 신청 기간 확인
- 서류 준비
- 신청 경로 선택
- 신청 링크/상세 링크/문의처
- 신청 후 접수번호/결과 확인 체크

#### C: 서류 없는 신청 내비게이터

- 신청 대상 확인
- 신청 방법 확인
- 신청 경로 선택
- 링크/기관 이동
- 서류는 `원문/기관 확인 필요`로 표시

#### D: 축소형 안내

- 원문 링크
- 자격/선정기준 확인 항목
- 신청방법은 원문 확인 필요

#### E: 내비게이터 미제공 또는 원문 확인 안내

- `이 정책은 신청 단계 자동 정리가 어렵습니다. 원문 공고를 확인하세요.`

## 추천 MVP

### Backend

신규 도메인 후보:

- `applicationguide` 또는 `applyguide`

신규 테이블:

- `policy_application_guides`
  - `service_id`
  - `source_fingerprint`
  - `guide_grade`
  - `guide_json`
  - `generation_status`
  - `generated_at`
  - `error_message`

- `user_application_progresses`
  - `user_key`
  - `service_id`
  - `status`
  - `current_step_key`
  - `memo`
  - `started_at`
  - `completed_at`

- `user_application_step_checks`
  - `progress_id`
  - `step_key`
  - `item_key`
  - `checked_at`

신규 API 후보:

- `GET /api/policies/{id}/application-guide`
- `POST /api/policies/{id}/application-progress`
- `PATCH /api/application-progress/{progressId}/checks`
- `GET /api/users/me/application-progress`

### Frontend

정책 상세:

- `신청 내비게이터` 섹션 추가
- guide grade에 따라 전체/축소/원문 확인 UI 분기
- `다음 할 일`을 최상단에 표시
- 서류는 `원문 명시`, `확인 필요`, `정보 없음`으로 분리
- 링크는 `신청 페이지`, `상세 공고`, `참고 링크`, `확인 필요 링크`로 분리

마이페이지 2차:

- `신청 준비 중` 탭
- 정책별 다음 할 일, 진행률, 마감일 표시

챗봇 3차:

- `이 단계 설명해줘`
- `서류 다시 정리해줘`
- `신청 경로가 여러 개인데 뭐부터 보면 돼?`

알림 3차:

- 마감 D-7/D-3/D-Day에 미완료 단계가 있으면 인앱/웹푸시 알림

## AI 사용 기준

AI는 판정자가 아니라 변환기입니다.

입력:

- 정책명
- 신청방법
- 신청기간
- 지원대상
- 선정기준
- 지원내용
- 제출서류
- 문의처
- 링크 목록
- 사용자 범주값 일부

출력:

- 단계 목록
- 체크 항목
- 서류 후보
- 링크 타입
- 확인 필요 항목
- 신뢰도
- 근거 필드

필수 출력 제약:

- JSON only
- `sourceField` 없는 항목은 확정 항목으로 표시 금지
- `evidenceText` 없는 서류는 `확인 필요`로 강등
- 자격/선정/지급 확정 표현 금지
- `신청 가능`, `자격 충족`, `선정 가능`, `이 서류만 준비하면 됨` 금지

## 주요 리스크와 대응

### 1. 사용자가 체크 완료를 신청 완료로 오해

대응:

- `준비 상태`와 `신청 상태`를 분리합니다.
- `외부 기관 접수 여부는 확인하지 않습니다` 문구를 고정 표시합니다.

### 2. AI가 없는 서류를 만들어냄

대응:

- 서류는 `SOURCE_CONFIRMED`, `NEEDS_SOURCE_CHECK`, `NO_DATA`로 나눕니다.
- 근거 필드와 evidence가 없으면 `NEEDS_SOURCE_CHECK`로만 표시합니다.

### 3. 출처별 품질 편차

대응:

- guide grade 기반 UI 분기.
- Gov24는 전체형, 온통청년/복지로는 보수형으로 시작합니다.

### 4. 마감일 오류

대응:

- 마감일 옆에 `원문 확인 필요` 단계 고정.
- 알림 문구도 `마감 예정으로 표시된 정책`처럼 보수적으로 표현합니다.

### 5. 링크 타입 오류

대응:

- `APPLY`만 신청 CTA로 강하게 표시합니다.
- `DETAIL`, `REFERENCE`, `EXTRACTED_FROM_TEXT`는 상세/참고/확인 필요 링크로 구분합니다.

## 다음 작업 제안

내일 이어서 할 경우 아래 순서가 좋습니다.

1. 이 문서를 기준으로 `신청 내비게이터 MVP 범위` 확정
2. guide JSON schema 초안 작성
3. DB migration 초안 작성
4. `ApplicationGuideService` read-only rule 기반 skeleton 구현
5. AI 없이 rule 기반 guide grade와 기본 단계 먼저 반환
6. 정책 상세 프론트에 guide grade별 UI skeleton 연결
7. 이후 AI JSON 생성/캐시를 붙임

첫 구현은 AI부터 붙이지 말고, DB 필드 기반 rule guide부터 시작하는 편이 안전합니다.
