# 공공 API → DB 컬럼 매핑

API / 응답 contract 문서군 진입점은 [system-docs-index.md](./system-docs-index.md)를 먼저 봅니다.

> 수집 시 `WelfareServiceMapper`에서 참조.
> `unified_category` 매핑 및 `service_tags` 분류 포함.

---

## 인증/회원가입 계약

- 로그인 아이디는 별도 username이 아니라 `email`이다.
- 따라서 1차 범위에서는 `아이디 찾기` API를 만들지 않는다. 로그인 화면에서는 "아이디 = 가입한 이메일"로 안내한다.
- 이메일 중복확인은 회원가입 전에만 사용하고, 응답은 사용 가능 여부 boolean만 반환한다.
- 회원가입 `POST /api/auth/signup`은 토큰을 바로 발급하지 않는다. 프론트는 가입 성공 후 `POST /api/auth/login`을 한 번 더 호출해 세션을 만든다.
- 로그인 전 비밀번호 재설정은 `request -> 메일 링크 -> confirm` 2단계로 처리한다.
- 재설정 요청은 존재하지 않는 이메일이어도 동일 성공 응답을 반환해 계정 존재 여부를 노출하지 않는다.

### `GET /api/auth/check-email`

- query
  - `email`
- response

```json
{
  "success": true,
  "data": {
    "available": true
  }
}
```

### `POST /api/auth/signup`

- request 주요 필드
  - `email`
  - `password`
  - `name`
  - `birthDate`
  - `privacyNoticeConfirmed` (필수, `true`)
  - `optionalProfileConsentAgreed` (선택정보 저장 시 `true`)
  - `sensitiveInfoConsentAgreed` (민감정보 저장 시 `true`)
  - `sido`, `sgg`, `incomeLevel`, `employmentStatus`, `householdType`
    - 추천용 선택정보이며 `optionalProfileConsentAgreed=true` 일 때만 저장 가능
  - `houseTenureCode`, `housingTypeCode`, `basicLivingRecipientTypeCode`
    - 추천용 선택정보이며 `optionalProfileConsentAgreed=true` 일 때만 저장 가능
  - `disabilityGradeCode`
    - 민감정보로 취급하며 `sensitiveInfoConsentAgreed=true` 일 때만 저장 가능
- 만 14세 미만 생년월일은 현재 가입을 거부한다.
- 성공 시 `user_consents` 에 개인정보 처리 안내 확인 동의를 기록하고, 선택/민감정보 동의 플래그가 `true` 인 경우 해당 동의도 함께 기록한다.
- response

```json
{
  "success": true,
  "data": null
}
```

### `PUT /api/users/me`

- request 주요 필드
  - `name`
  - `birthDate`
  - `sido`, `sgg`, `regionCode`, `incomeLevel`, `employmentStatus`, `householdType`
  - `houseTenureCode`, `housingTypeCode`, `basicLivingRecipientTypeCode`
  - `disabilityGradeCode`
  - `optionalProfileConsentAgreed`
  - `sensitiveInfoConsentAgreed`
  - `interestFields`, `targetTypes`
  - 알림 설정 필드
- 선택 프로필 정보나 추천 선호 정보(`sido`, `incomeLevel`, `householdType`, 주거 코드, `basicLivingRecipientTypeCode`, `interestFields`, `targetTypes` 등)를 새로 저장하려면 기존 `OPTIONAL_PROFILE` 동의가 있거나 이번 요청의 `optionalProfileConsentAgreed=true` 가 필요하다.
- 민감정보인 `disabilityGradeCode` 를 새로 저장하려면 기존 `SENSITIVE_INFO` 동의가 있거나 이번 요청의 `sensitiveInfoConsentAgreed=true` 가 필요하다.
- 이번 요청에서 동의 플래그를 받은 경우 서버는 `user_consents` 에 동의 상태를 기록한 뒤 프로필 저장을 진행한다.
- 필수 가입 개인정보 동의(`PRIVACY_NOTICE`)는 회원가입 시 기록하며, 프로필 수정에서 반복 요구하지 않는다.

### `PUT /api/users/me/priorities`

- request 주요 필드
  - `priorityCodes` (1~5개)
  - `optionalProfileConsentAgreed`
- 우선순위는 추천용 선택정보다. 기존 `OPTIONAL_PROFILE` 동의가 없으면 이번 요청의 `optionalProfileConsentAgreed=true` 가 필요하다.
- 우선순위 저장 시 서버는 우선순위 코드에서 관심분야를 파생해 함께 저장하므로, 프로필의 선택정보 동의 규칙과 동일하게 관리한다.
- 동의가 없으면 `U004(CONSENT_REQUIRED)` 를 반환한다.

### `DELETE /api/users/me/consents/{consentType}`

- path
  - `consentType`: `OPTIONAL_PROFILE` 또는 `SENSITIVE_INFO`
- `OPTIONAL_PROFILE` 철회 시 추천용 선택 프로필(`sido`, `sgg`, `incomeLevel`, `employmentStatus`, `householdType`, 주거/수급 코드), 관심분야, 특화 대상, 우선순위를 함께 비운다.
- `SENSITIVE_INFO` 철회 시 민감정보인 `disabilityGradeCode` 를 비운다.
- 철회 후 서버는 추천 캐시를 무효화하고 `user_profiles` projection을 다시 동기화한다.
- `PRIVACY_NOTICE` 는 필수 가입 처리 안내 확인이므로 개별 철회 대상이 아니며, 계정 삭제 흐름에서만 함께 철회 처리한다.

### `POST /api/auth/login`

- request 주요 필드
  - `email`
  - `password`
- response 주요 필드
  - `data.accessToken`
  - refresh token은 HttpOnly cookie

```json
{
  "success": true,
  "data": {
    "accessToken": "..."
  }
}
```

### `POST /api/auth/password-reset/request`

- request 주요 필드
  - `email`
- 현재 구현 메모
  - 활성 사용자면 Redis 30분 토큰을 발급하고 재설정 링크 메일을 보낸다
  - 없는 이메일이나 탈퇴 계정이어도 동일 성공 응답으로 끝낸다
- response

```json
{
  "success": true,
  "data": null
}
```

### `POST /api/auth/password-reset/confirm`

- request 주요 필드
  - `token`
  - `newPassword`
- 현재 구현 메모
  - 토큰이 유효하면 비밀번호를 변경하고 로그인 실패 횟수를 초기화한다
  - 사용 완료 시 reset token과 refresh token을 함께 폐기한다
  - 토큰이 만료되었거나 최신 토큰이 아니면 `A008`
- response

```json
{
  "success": true,
  "data": null
}
```

- 에러 응답 예시

```json
{
  "success": false,
  "message": "유효하지 않거나 만료된 비밀번호 재설정 토큰입니다.",
  "errorCode": "A008"
}
```

---

## 챗봇 API 계약 (2차 준비)

- 챗봇은 로그인 사용자 전용이다.
- `chat` 모듈은 정책 조회 결과를 요약해 답변하며, 추천 재계산은 하지 않는다.
- 응답 필드명은 프론트와 백엔드 모두 아래 계약으로 고정한다.

### `POST /api/chat/sessions`

- 용도
  - 새 채팅 세션 생성
- request 주요 필드
  - body optional
  - `title` optional
- response 주요 필드
  - `sessionId`
  - `title`
  - `lastMessageAt`
  - `createdAt`

```json
{
  "success": true,
  "data": {
    "sessionId": 12,
    "title": "서울 청년 주거 상담",
    "lastMessageAt": "2026-04-25T23:40:00",
    "createdAt": "2026-04-25T23:40:00"
  }
}
```

### `GET /api/chat/sessions`

- 용도
  - 내 최근 세션 목록 조회
- 현재 구현 기준 최근 20개 세션 반환
- response 주요 필드
  - `sessionId`
  - `title`
  - `lastMessageAt`
  - `createdAt`

```json
{
  "success": true,
  "data": [
    {
      "sessionId": 12,
      "title": "서울 청년 주거 상담",
      "lastMessageAt": "2026-04-25T23:41:12",
      "createdAt": "2026-04-25T23:40:00"
    }
  ]
}
```

### `GET /api/chat/sessions/{sessionId}/messages`

- 용도
  - 세션 메시지 조회
- 권한
  - 본인 세션만 조회 가능
  - 존재하지 않거나 소유하지 않은 세션은 `CH001`
- response 주요 필드
  - `messageId`
  - `role`
  - `content`
  - `referencedServiceIds`
  - `createdAt`

```json
{
  "success": true,
  "data": [
    {
      "messageId": 101,
      "role": "USER",
      "content": "서울에서 월세 지원 받을 수 있는 정책 있어?",
      "referencedServiceIds": [],
      "createdAt": "2026-04-25T23:40:10"
    },
    {
      "messageId": 102,
      "role": "ASSISTANT",
      "content": "서울 거주 청년이라면 청년월세지원과 청년전세임대 정책을 먼저 확인해보세요.",
      "referencedServiceIds": [1829, 2451],
      "createdAt": "2026-04-25T23:40:12"
    }
  ]
}
```

### `POST /api/chat/sessions/{sessionId}/messages`

- 용도
  - 사용자 질문 전송
- 현재 구현 메모
  - USER/ASSISTANT 메시지 2건을 저장한다
  - 세션 제목이 비어 있으면 첫 질문 앞부분으로 자동 채운다
  - 본인 세션이 아니면 `CH001`
  - 사용자별 fixed-window rate limit을 적용하고 초과 시 `CH002` 429를 반환한다
  - OpenAI JSON 응답 파싱 실패 시 정책 후보 기반 fallback 답변으로 내려간다
  - AI가 반환한 `service_id`는 서버가 전달한 후보 정책 allowlist 안에서만 채택한다
- request 주요 필드
  - `content`
- response 주요 필드
  - `sessionId`
  - `answer`
  - `needsClarification`
  - `references[].serviceId`
  - `references[].title`
  - `references[].reason`

```json
{
  "success": true,
  "data": {
    "sessionId": 12,
    "answer": "서울 거주 미취업 청년이라면 청년월세지원과 국민취업지원제도를 먼저 확인해보세요.",
    "needsClarification": false,
    "references": [
      {
        "serviceId": 1829,
        "title": "청년월세 한시 특별지원",
        "reason": "서울 거주 청년의 주거비 부담 완화와 직접 연결됩니다."
      }
    ]
  }
}
```

- 에러 응답 예시

```json
{
  "success": false,
  "message": "짧은 시간에 너무 많은 챗 요청이 발생했습니다. 잠시 후 다시 시도하세요.",
  "errorCode": "CH002"
}
```

### `DELETE /api/chat/sessions/{sessionId}`

- 용도
  - 사용자가 특정 세션 삭제
- response

```json
{
  "success": true,
  "data": null
}
```

---

## DB 컬럼 ← API 필드 매핑표

| DB 컬럼 | 온통청년 | 복지로 중앙 | 복지로 지자체 | Gov24 |
|---------|---------|------------|-------------|-------|
| `source_id` | `정책번호` | `servId` | `servId` | `서비스ID` |
| `source_type` | `'YOUTH'` | `'BOKJIRO_CENTRAL'` | `'BOKJIRO_LOCAL'` | `'GOV24'` |
| `title` | `정책명` | `servNm` | `servNm` | `서비스명` |
| `description` | `정책소개내용` | `servDgst` | `servDgst` | `서비스목적요약` |
| `support_content` | `정책지원내용` | — | — | `지원내용` |
| `category_main` | `정책대부류명` | — | — | — |
| `category_sub` | `정책중부류명` | — | — | — |
| `keyword` | `정책키워드명` | — | — | — |
| `unified_category` | 대부류 → 매핑 | `intrsThema` → 매핑 | `intrsThema` → 매핑 | label-based compat 매핑 |
| `host_org` | `주관기관명` | `jurMnofNm` | — | `소관기관명` |
| `operating_org` | `이행기관명` | `jurOrgNm` | `bizChrDeptNm` | `부서명` |
| `life_stage` | — | `lifeArray` | `lifeNmArray` | — |
| `support_cycle` | — | `sprtCycNm` | `sprtCycNm` | — |
| `provision_type` | — | `srvPvsnNm` | `srvPvsnNm` | `지원유형` |
| `apply_method_name` | `신청방법` | — | `aplyMtdNm` | `신청방법` |
| `is_online_apply` | — | `onapPsbltYn` (Y→1) | — | — |
| `contact` | — | `rprsCtadr` | — | `전화문의` |
| `detail_url` | — | `servDtlLink` | `servDtlLink` | `상세조회URL` |
| `min_age` | `최소나이` | — | — | — |
| `max_age` | `최대나이` | — | — | — |
| `min_income` | `최소소득` | — | — | — |
| `max_income` | `최대소득` | — | — | — |
| `apply_start_date` | `신청기간` (시작) | — | — | — |
| `apply_end_date` | `신청기간` (종료) | — | — | — |
| `start_date` | `사업시작일` | — | — | — |
| `end_date` | `사업종료일` | — | — | — |
| `api_view_count` | `조회수` | `inqNum` | `inqNum` | `조회수` |
| `registered_at` | `등록일` | `svcfrstRegTs` | — | `등록일시` |
| `last_modified_at` | `수정일` | — | `lastModYmd` | `수정일시` |
| → `service_regions` | `지역코드` (콤마분해) | 전국 단위 (sido_name='전국' 1건) | `ctpvNm` + `sggNm` | — |
| → `service_tags` (INTEREST_THEME) | — | `intrsThemaArray` | `intrsThemaNmArray` | — |
| → `service_tags` (TARGET_GROUP) | — | `trgterIndvdlArray` | `trgterIndvdlNmArray` | — |
| → `service_tags` (LIFE_STAGE) | — | `lifeArray` | `lifeNmArray` | — |
| → `service_tags` (KEYWORD) | `정책키워드명` (콤마분해) | — | — | — |

---

## welfare_service_details ← 상세 API 필드

> 상세 API는 현재 복지로와 Gov24에서 사용. 리스트와 중복되는 필드는 제외.

| DB 컬럼 | 복지로 상세 API 필드 | Gov24 상세 API 필드 | 비고 |
|---------|-------------------|----------------------|------|
| `target_detail` | `sprtTrgtCn` / `tgtrDtlCn` | `지원대상` | 지원대상 상세 |
| `support_detail` | `alwServCn` | `지원내용` | 지원내용 상세 |
| `apply_method_detail` | `aplyMtdCn` / `applmetList` | `신청방법` | 신청방법 상세 |
| `selection_criteria` | `slctCritCn` | `선정기준` | 빈값 가능 |
| `contact_list` | `inqplCtadrList` | `문의처` | JSON/문자열에서 contact list 추출 |
| `homepage_url` | `inqplHmpgReldList` | `온라인신청사이트URL` | 대표 URL |
| `related_law` | `baslawList` | `행정규칙` / `자치법규` / `법령` | 관련 법령/규정 요약 |
| `form_files` | `basfrmList` | `구비서류` / `공무원확인구비서류` / `본인확인필요구비서류` | JSON 배열 또는 결합 문자열 |

---

## Gov24 supportConditions → service_facts

> `Gov24` 는 별도 `supportConditions` endpoint를 통해 지원조건을 fact로 보강합니다.

- source: `GET /gov24/v3/supportConditions`
- fact code set: `GOV24_SUPPORT_CONDITION`
- 현재 active runtime scope:
  - 성별 `JA0101`, `JA0102`
  - 연령 `JA0110`, `JA0111`
  - 소득 `JA0201~JA0205`
  - 개인/가구/특수대상 축 일부 (`JA03**`, `JA04**`)
- 현재 deferred scope:
  - 사업체/기관/업종/창업 상태 축 (`JA210*`, `JA220*`, `JA120*`, `JA1299`, `JA2299`, `JA110*`)

---

## unified_category 매핑 규칙

> 주요 공공 API의 서로 다른 분류 체계를 단일 카테고리로 통합.
> `WelfareServiceMapper`에서 분기 처리.

| unified_category | 온통청년 대부류 | 복지로 intrsThema |
|-----------------|--------------|-----------------|
| `일자리` | 일자리 | 일자리 |
| `주거` | 주거 | 주거 |
| `교육·직업훈련` | 교육·직업훈련 | 교육 |
| `금융·생활지원` | 금융·복지·문화 | 민간금융, 서민금융, 생활지원 |
| `문화·여가` | 금융·복지·문화 (일부) | 문화·여가 |
| `건강·의료` | — | 신체건강, 정신건강 |
| `가족·돌봄` | — | 보육, 보호·돌봄, 임신·출산, 입양·위탁 |
| `안전·위기` | — | 안전·위기 |
| `참여·기회` | 참여·기회 | — |
| `기타` | 나머지 | 나머지 |

> **복지로 매핑 추가 이력**
> - `서민금융`: 복지로 지자체 API 실데이터 확인 결과 `민간금융` 대신 `서민금융` 키워드로 전달되는 케이스가 731건 존재. 동일 계열이므로 `금융·생활지원`으로 매핑 추가.
> - `입양·위탁`: 복지로 지자체 API 응답에 존재하지만 초기 맵 구성 시 누락된 항목. 63건이 `기타`로 분류되던 것을 `가족·돌봄`으로 추가.

```java
// WelfareServiceMapper 구현 예시
public String mapUnifiedCategory(String sourceType, String rawCategory) {
    return switch (sourceType) {
        case "YOUTH" -> mapFromYouthCategory(rawCategory);
        case "BOKJIRO_CENTRAL", "BOKJIRO_LOCAL" -> mapFromIntrsThema(rawCategory);
        default -> "기타";
    };
}
```

---

## 수집 시 주의사항

### 소득 필드 해석
- `min_income`, `max_income`는 현재 **온통청년(YOUTH)의 구조화 소득값 전용**으로 취급한다.
- 복지로 계열의 `중위소득 %`, `연/월소득 금액`, `저소득층` 문구는 같은 축이 아니므로 이 컬럼에 직접 매핑하지 않는다.
- 추천 후보 SQL의 직접 소득 필터도 `YOUTH`에만 적용하고, 복지로 계열은 `TARGET_GROUP`/`KEYWORD` 태그를 보조 신호로만 사용한다.

### service_tags UPSERT (중복 삽입 금지)
```java
// PostgreSQL 기준 ON CONFLICT 사용
// 중복 삽입 시 rule_base_score 이중합산 버그 발생
String sql = """
    INSERT INTO service_tags (service_id, tag_type, tag_value)
    VALUES (?, ?, ?)
    ON CONFLICT (service_id, tag_type, tag_value) DO NOTHING
    """;
```

### HTML strip (Jsoup)
```java
// 수집 후 저장 전 반드시 HTML 제거
String clean = Jsoup.clean(rawText, Whitelist.none());
```

### XML XXE 비활성화 (복지로 API)
```java
// BokjiroCentralClient, BokjiroLocalClient
XmlMapper xmlMapper = new XmlMapper();
xmlMapper.configure(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
xmlMapper.configure(XMLInputFactory.SUPPORT_DTD, false);
```

### UPSERT 키 (source_type + source_id)
```sql
-- 중복 수집 방지
INSERT INTO welfare_services (source_type, source_id, ...)
VALUES (?, ?, ...)
ON CONFLICT (source_type, source_id) DO UPDATE SET
    title = EXCLUDED.title,
    status = EXCLUDED.status,
    updated_at = NOW();
```

### 매일 새벽 3시: CLOSED 처리 + ai_score NULL 리셋
```java
// StatusUpdateService
// 1. 만료 정책 CLOSED 처리
@Transactional
public void closeExpiredServices() {
    welfareServiceRepository.closeExpired(LocalDate.now());
}

// 2. CLOSED 정책의 user_recommendations.ai_score NULL 리셋
@Transactional
public void resetAiScoreForClosed() {
    userRecommendationRepository.nullifyAiScoreForClosedServices();
}
```

---

## API 엔드포인트 정보

| API | 포맷 | 일일 제한(현재 운영 기준) |
|-----|------|--------------------------|
| 온통청년 목록 | JSON | 1,000건 |
| 복지로 중앙 목록 | XML | **100,000건** |
| 복지로 중앙 상세 | XML | **100,000건** |
| 복지로 지자체 목록 | XML | **100,000건** |
| 복지로 지자체 상세 | XML | **100,000건** |

- 복지로는 2026-05-10 운영 계정 기준으로 `중앙 list`, `중앙 detail`, `지자체 list`, `지자체 detail` 을 각각 독립 quota로 운영:
  - API 키는 같아도 공공데이터포털 신청 단위가 달라 quota는 공유하지 않음
  - 코드 안전 상한은 `중앙 list 1회 10,000 items`, `지자체 list 1회 10,000 items`
  - 코드 안전 상한은 `중앙 detail 1회 10,000 calls`, `지자체 detail 1회 10,000 calls`
  - detail 기본 pacing 은 `300ms`, 연속 `429` `2회`까지만 허용
  - 5xx만 재시도
- API별 독립 실행 (하나 실패가 다른 API에 영향 없음)
- API 키는 `.env` + `application.yml` 참조 (하드코딩 금지)

---

## 프론트 연동용 응답 필드 (2026-04-17)

### `GET /api/recommendations`

- 주요 필드
  - `id`
  - `serviceId`
  - `logId`
  - `title`
  - `description`
  - `unifiedCategory`
  - `youthMajorLabel` (nullable, canonical summary)
  - `youthMidLabel` (nullable, canonical summary)
  - `provisionMethodLabel` (nullable, canonical summary)
  - `gov24ServiceFieldLabel` (nullable, canonical summary)
  - `gov24UserTypeLabel` (nullable, canonical summary)
  - `gov24BenefitTypeLabel` (nullable, canonical summary)
  - `status`
  - `finalScore`
  - `aiScore`
  - `aiReason`
    - 프론트 표시명은 `추천 메모`
    - blank reason 은 null 로 내려갈 수 있으며, 이 경우 프론트는 추천 메모를 표시하지 않는다 (상세는 recommendation-ai-reason-memo-contract.md "메모 노출 정책")
    - 서버 응답 기준 개행/중복 공백은 제거되고 20 code point 이내로 제한된다
  - `bookmarked`
  - `recommendedAt`

### `POST /api/recommendations/refresh`

- 응답 구조는 `GET /api/recommendations` 와 동일
- additive canonical summary field
  - `youthMajorLabel`
  - `youthMidLabel`
  - `provisionMethodLabel`
  - `gov24ServiceFieldLabel`
  - `gov24UserTypeLabel`
  - `gov24BenefitTypeLabel`

### `GET /api/recommendations/similar-users-viewed`

- 메인 추천 row를 바꾸지 않는 집계형 보조 추천 API
- query
  - `size` default `6`, max `20`
- response 주요 필드
  - `policy`: `PolicySummaryResponse`
  - `reasonLabel`
- 표본이나 유사도 신호가 부족하면 빈 배열을 반환한다
- 현재 사용자가 최근 본 정책과 최신 추천 batch에 이미 포함된 정책은 제외한다
- 상세 계약은 [recommendation-similar-users-viewed-policy.md](../recommendation/recommendation-similar-users-viewed-policy.md)

### `GET /api/policies/ranking`

- 주요 필드
  - `serviceId`
  - `title`
  - `unifiedCategory`
  - `youthMajorLabel` (nullable, canonical summary)
  - `youthMidLabel` (nullable, canonical summary)
  - `provisionMethodLabel` (nullable, canonical summary)
  - `gov24ServiceFieldLabel` (nullable, canonical summary)
  - `gov24UserTypeLabel` (nullable, canonical summary)
  - `gov24BenefitTypeLabel` (nullable, canonical summary)
  - `sourceType`
  - `uniqueViewCount7d` (최근 7일 고유조회수)
  - `viewCount` (내부 누적 조회수)
  - `apiViewCount` (외부 API 조회수)
  - `rankingScore`

```json
{
  "success": true,
  "data": [
    {
      "serviceId": 1829,
      "title": "청년일자리 도약장려금",
      "unifiedCategory": "일자리",
      "youthMajorLabel": "주거",
      "youthMidLabel": "취업",
      "provisionMethodLabel": "온라인",
      "gov24ServiceFieldLabel": "보육",
      "gov24UserTypeLabel": "청년",
      "gov24BenefitTypeLabel": "서비스",
      "sourceType": "YOUTH",
      "uniqueViewCount7d": 1,
      "viewCount": 2,
      "apiViewCount": 187756,
      "rankingScore": 0.9792
    }
  ]
}
```

### `GET /api/policies`

- 지원 파라미터
  - `category`, `sourceType`, `status`, `statusFilter`, `sido`, `sgg`, `onlineApply`, `sort`
  - `incomeLevel` (1~10 정수): 소득분위 → 연소득 상한 변환 후 `max_income` 해당 정책을 ORDER BY 우선 표시. YOUTH 데이터 24건에만 실제 값 있음. 10분위는 null 처리(부스팅 없음).
  - `targetGroup` (문자열): 장애인 / 한부모·조손 / 다문화·탈북민 / 보훈대상자 / 다자녀. TARGET_GROUP 태그 일치 정책을 ORDER BY 우선 표시. 복지로 데이터 756건에만 적용.
  - 두 파라미터 모두 하드 필터가 아닌 소프트 부스팅 (조건 미해당 정책도 뒤에 표시)
- 응답 형식
  - Spring Page 형식의 `data.content`, `data.totalElements`, `data.totalPages`
- `data.content[]` 주요 필드
  - `id`
  - `title`
  - `description`
  - `unifiedCategory`
  - `status`
  - `hostOrg`
  - `sido` (nullable — BOKJIRO_LOCAL만 값 있음, YOUTH·BOKJIRO_CENTRAL은 null)
  - `applyMethodName`
  - `youthMajorLabel` (nullable, canonical summary)
  - `youthMidLabel` (nullable, canonical summary)
  - `provisionMethodLabel` (nullable, canonical summary)
  - `gov24ServiceFieldLabel` (nullable, canonical summary)
  - `gov24UserTypeLabel` (nullable, canonical summary)
  - `gov24BenefitTypeLabel` (nullable, canonical summary)
  - `applyStartDate`
  - `applyEndDate`
  - `isOnlineApply`
  - `bookmarked`
- `bookmarked`
  - 로그인 사용자면 최신 북마크 상태 기준
  - 비로그인이면 항상 `false`
- `sido` 추가 배경 (2026-05-04)
  - 카드 source 필드가 hostOrg 없을 때 applyMethodName("방문, 인터넷")으로 폴백해 의미 없는 값을 표시하던 문제
  - 상세 API는 service_regions 테이블을 JOIN해 지역명이 나오지만 목록 API는 포함하지 않았음
  - BOKJIRO_LOCAL 1,223개 정책이 sido_name 있고 hostOrg 없음 → 해당 정책의 카드에만 실효
  - 프론트 source 폴백 순서: hostOrg → sido → applyMethodName

### `GET /api/policies/search`

- 현재 지원 파라미터
  - `keyword` (필수)
  - `status`, `statusFilter`, `category`, `sourceType`, `onlineApply`, `sido`, `sgg`
  - `sort` = `RELEVANCE|VIEWS|LATEST|DEADLINE` (NAME은 API 코드만 유지, UI 노출 없음)
  - `incomeLevel`, `targetGroup` — 목록 API와 동일한 소프트 부스팅 동작
  - `page`, `size`
- 현재 응답 형식
  - `data.content`
  - `data.totalElements`
  - `data.totalPages`
  - `data.pageNumber`
  - `data.pageSize`
  - `data.hasNext`
- 상태 규칙
  - `status`를 직접 주면 해당 DB status(`ACTIVE|UPCOMING|CLOSED`)만 조회
  - `statusFilter` = `ACTIVE_ONLY`(기본): 신청가능·예정 + 마감일 미도래
  - `statusFilter` = `EXPIRED_ONLY`: CLOSED 또는 applyEndDate 지남 (온통청년 마감 포함)
  - `statusFilter` = `ALL`: 모든 상태
- 기타
  - 각 항목의 `bookmarked`는 로그인 사용자면 최신 북마크 상태 기준, 비로그인이면 `false`
  - `totalElements`는 청년 후처리 필터가 적용된 최종 결과 기준
  - `youthMajorLabel`, `youthMidLabel`, `provisionMethodLabel`, `gov24ServiceFieldLabel`, `gov24UserTypeLabel`, `gov24BenefitTypeLabel` 은 additive field이며, canonical projection이 있으면 그 값을 우선 사용
  - 검색 기본 sort는 `RELEVANCE` (목록 기본 `LATEST`와 다름)

### `GET /api/policies/{id}`

- 조회수 정책
  - 24시간 dedup 적용
  - 로그인: `(user_key, service_id)` 기준
  - 비로그인: `(client_fingerprint, service_id)` 기준
- 주요 응답 필드
  - `id`, `title`, `description`, `unifiedCategory`, `status`, `sourceType`
  - `hostOrg`, `operatingOrg`, `minAge`, `maxAge`, `minIncome`, `maxIncome`
  - `supportContent`, `applyMethodName`, `youthMajorLabel`, `youthMidLabel`, `provisionMethodLabel`, `gov24ServiceFieldLabel`, `gov24UserTypeLabel`, `gov24BenefitTypeLabel`, `applyStartDate`, `applyEndDate`
  - `targetDetail`, `supportDetail`, `applyMethodDetail`, `contactList`
  - `regions`, `tags`, `detailUrl`, `bookmarked`

### `GET /api/users/me/bookmarks`

- 각 항목은 `GET /api/policies` 목록 응답과 같은 `PolicySummaryResponse` 구조
- 주요 additive field
  - `youthMajorLabel`
  - `youthMidLabel`
  - `provisionMethodLabel`
  - `gov24ServiceFieldLabel`
  - `gov24UserTypeLabel`
  - `gov24BenefitTypeLabel`

## canonical summary additive field 메모 (2026-05-02)

- `youthMajorLabel`
  - compat `unifiedCategory` 와 별도로 내려가는 canonical major summary
  - 현재는 projection이 있으면 응답에 실리고, 없으면 `null`
- `youthMidLabel`
  - source-neutral canonical summary
  - 현재는 projection이 있으면 응답에 실리고, 없으면 `null`
- `provisionMethodLabel`
  - canonical summary 기준 제공방법명
  - 현재는 projection이 있으면 응답에 실리고, 없으면 `null`
- `gov24ServiceFieldLabel`
  - 복지로/정부24 계열 canonical 서비스 분야 summary
  - 현재는 projection이 있으면 응답에 실리고, 없으면 `null`
- `gov24UserTypeLabel`
  - 복지로/정부24 계열 canonical 이용대상 summary
  - 현재는 projection이 있으면 응답에 실리고, 없으면 `null`
- `gov24BenefitTypeLabel`
  - 복지로/정부24 계열 canonical 지원유형 summary
  - 현재는 projection이 있으면 응답에 실리고, 없으면 `null`
- 기존 필드(`applyMethodName`, `unifiedCategory`)를 대체하지 않음
  - additive contract로만 먼저 노출
