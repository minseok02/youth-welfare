# 공공API 3종 → DB 컬럼 매핑

> 수집 시 `WelfareServiceMapper`에서 참조.
> `unified_category` 매핑 및 `service_tags` 분류 포함.

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

| API | 포맷 | 일일 제한 |
|-----|------|-----------|
| 온통청년 | JSON | 1,000건 |
| 복지로 중앙 | XML | 1,000건 |
| 복지로 지자체 | XML | 1,000건 |

- 수집 실패 시: 1시간 후 재시도, 최대 2회
- API별 독립 실행 (하나 실패가 다른 API에 영향 없음)
- API 키는 `.env` + `application.yml` 참조 (하드코딩 절대 금지)
