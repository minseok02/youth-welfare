# 공공API 3종 → DB 컬럼 매핑

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
  - `sido`
  - `sgg`
  - `incomeLevel`
  - `employmentStatus`
  - `householdType`
- response

```json
{
  "success": true,
  "data": null
}
```

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

| DB 컬럼 | 온통청년 | 복지로 중앙 | 복지로 지자체 |
|---------|---------|------------|-------------|
| `source_id` | `정책번호` | `servId` | `servId` |
| `source_type` | `'YOUTH'` | `'BOKJIRO_CENTRAL'` | `'BOKJIRO_LOCAL'` |
| `title` | `정책명` | `servNm` | `servNm` |
| `description` | `정책소개내용` | `servDgst` | `servDgst` |
| `support_content` | `정책지원내용` | — | — |
| `category_main` | `정책대부류명` | — | — |
| `category_sub` | `정책중부류명` | — | — |
| `keyword` | `정책키워드명` | — | — |
| `unified_category` | 대부류 → 매핑 | `intrsThema` → 매핑 | `intrsThema` → 매핑 |
| `host_org` | `주관기관명` | `jurMnofNm` | — |
| `operating_org` | `이행기관명` | `jurOrgNm` | `bizChrDeptNm` |
| `life_stage` | — | `lifeArray` | `lifeNmArray` |
| `support_cycle` | — | `sprtCycNm` | `sprtCycNm` |
| `provision_type` | — | `srvPvsnNm` | `srvPvsnNm` |
| `apply_method_name` | `신청방법` | — | `aplyMtdNm` |
| `is_online_apply` | — | `onapPsbltYn` (Y→1) | — |
| `contact` | — | `rprsCtadr` | — |
| `detail_url` | — | `servDtlLink` | `servDtlLink` |
| `min_age` | `최소나이` | — | — |
| `max_age` | `최대나이` | — | — |
| `min_income` | `최소소득` | — | — |
| `max_income` | `최대소득` | — | — |
| `apply_start_date` | `신청기간` (시작) | — | — |
| `apply_end_date` | `신청기간` (종료) | — | — |
| `start_date` | `사업시작일` | — | — |
| `end_date` | `사업종료일` | — | — |
| `api_view_count` | `조회수` | `inqNum` | `inqNum` |
| `registered_at` | `등록일` | `svcfrstRegTs` | — |
| `last_modified_at` | `수정일` | — | `lastModYmd` |
| → `service_regions` | `지역코드` (콤마분해) | 전국 단위 (sido_name='전국' 1건) | `ctpvNm` + `sggNm` |
| → `service_tags` (INTEREST_THEME) | — | `intrsThemaArray` | `intrsThemaNmArray` |
| → `service_tags` (TARGET_GROUP) | — | `trgterIndvdlArray` | `trgterIndvdlNmArray` |
| → `service_tags` (LIFE_STAGE) | — | `lifeArray` | `lifeNmArray` |
| → `service_tags` (KEYWORD) | `정책키워드명` (콤마분해) | — | — |

---

## welfare_service_details ← 상세 API 필드

> 상세 API는 복지로에서만 제공. 리스트와 중복되는 필드는 제외.

| DB 컬럼 | 복지로 상세 API 필드 | 비고 |
|---------|-------------------|------|
| `target_detail` | `sprtTrgtCn` / `tgtrDtlCn` | 지원대상 상세 |
| `support_detail` | `alwServCn` | 지원내용 상세 |
| `apply_method_detail` | `aplyMtdCn` / `applmetList` | 신청방법 상세 |
| `selection_criteria` | `slctCritCn` | 선정기준 (빈값 多) |
| `contact_list` | `inqplCtadrList` | JSON 배열 [{name, phone}] |
| `homepage_url` | `inqplHmpgReldList` | 첫 번째 항목 |
| `related_law` | `baslawList` | 첫 번째 항목 |
| `form_files` | `basfrmList` | JSON 배열 [{name, url}] |

---

## unified_category 매핑 규칙

> 3개 API의 서로 다른 분류 체계를 단일 카테고리로 통합.
> `WelfareServiceMapper`에서 분기 처리.

| unified_category | 온통청년 대부류 | 복지로 intrsThema |
|-----------------|--------------|-----------------|
| `일자리` | 일자리 | 일자리 |
| `주거` | 주거 | 주거 |
| `교육·직업훈련` | 교육·직업훈련 | 교육 |
| `금융·생활지원` | 금융·복지·문화 | 민간금융, 생활지원 |
| `문화·여가` | 금융·복지·문화 (일부) | 문화·여가 |
| `건강·의료` | — | 신체건강, 정신건강 |
| `가족·돌봄` | — | 보육, 보호·돌봄, 임신·출산 |
| `안전·위기` | — | 안전·위기 |
| `참여·기회` | 참여·기회 | — |
| `기타` | 나머지 | 나머지 |

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
// 반드시 INSERT IGNORE 또는 ON DUPLICATE KEY UPDATE 사용
// 중복 삽입 시 rule_base_score 이중합산 버그 발생
String sql = """
    INSERT INTO service_tags (service_id, tag_type, tag_value)
    VALUES (?, ?, ?)
    ON DUPLICATE KEY UPDATE tag_value = tag_value
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
ON DUPLICATE KEY UPDATE
    title = VALUES(title),
    status = VALUES(status),
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
| 복지로 중앙/지자체 목록 | XML | 1,000건 |
| 복지로 상세 API (기능별) | XML | **100건** |

- 복지로 상세는 API 기능별 일일 100건 제한 기준으로 운영:
  - 기본 상한 95회(여유 5회)
  - 429 응답 즉시 중단
  - 5xx만 재시도
- API별 독립 실행 (하나 실패가 다른 API에 영향 없음)
- API 키는 `.env` + `application.yml` 참조 (하드코딩 금지)

---

## 프론트 연동용 응답 필드 (2026-04-17)

### `GET /api/policies/ranking`

- 주요 필드
  - `serviceId`
  - `title`
  - `unifiedCategory`
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

- 응답 형식
  - Spring Page 형식의 `data.content`, `data.totalElements`, `data.totalPages`
- `data.content[]` 주요 필드
  - `id`
  - `title`
  - `description`
  - `unifiedCategory`
  - `status`
  - `hostOrg`
  - `applyMethodName`
  - `applyStartDate`
  - `applyEndDate`
  - `isOnlineApply`
  - `bookmarked`
- `bookmarked`
  - 로그인 사용자면 최신 북마크 상태 기준
  - 비로그인이면 항상 `false`

### `GET /api/policies/search`

- 현재 지원 파라미터
  - `keyword` (필수)
  - `status`, `includeClosed`, `category`, `sourceType`, `onlineApply`
  - `sort` = `RELEVANCE|VIEWS|LATEST|NAME`
  - `page`, `size`
- 현재 응답 형식
  - `data.content`
  - `data.totalElements`
  - `data.totalPages`
  - `data.pageNumber`
  - `data.pageSize`
  - `data.hasNext`
- 상태 규칙
  - `status`를 직접 주면 해당 상태만 조회
  - `status`가 없고 `includeClosed=true`면 `ACTIVE`, `UPCOMING`, `CLOSED` 포함
  - `status`가 없고 `includeClosed`가 없거나 `false`면 `ACTIVE`, `UPCOMING`만 포함
- 기타
  - 각 항목의 `bookmarked`는 로그인 사용자면 최신 북마크 상태 기준, 비로그인이면 `false`
  - `totalElements`는 청년 후처리 필터가 적용된 최종 결과 기준

### `GET /api/policies/{id}`

- 조회수 정책
  - 24시간 dedup 적용
  - 로그인: `(user_id, service_id)` 기준
  - 비로그인: `(client_fingerprint, service_id)` 기준
- 주요 응답 필드
  - `id`, `title`, `description`, `unifiedCategory`, `status`, `sourceType`
  - `hostOrg`, `operatingOrg`, `minAge`, `maxAge`, `minIncome`, `maxIncome`
  - `supportContent`, `applyMethodName`, `applyStartDate`, `applyEndDate`
  - `targetDetail`, `supportDetail`, `applyMethodDetail`, `contactList`
  - `regions`, `tags`, `detailUrl`, `bookmarked`
