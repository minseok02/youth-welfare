# 추천 파이프라인 상세

문서군 진입점: [recommendation-docs-index.md](./recommendation-docs-index.md)

> 1차 구현 기준. 2차 전환 시 교체 지점 명시.

---

## 1차 파이프라인 흐름

```
[추천 요청 (사용자 접속 시)]
  ① ClusterService.assignCluster(user)
     → 1차: 항상 "youth_all" 반환

  ② RetrievalService.retrieve(clusterId, user)
     → SQL WHERE 필터 (나이/지역 우선, 소득은 구조화 값 있는 경우만 직접 적용)
     → 상위 K=50건 선별
     → 신규 정책 가미 (24시간 이내 M=5건 강제 포함)

  ③ RuleScoringService.score(candidates, user)
     → if-else 기본 가점 → rule_base_score
     → 우선순위 가중치 적용 → rule_weighted_score

  ④ AiScoringService.score(clusterId, topCandidates, user)
     → RealtimeAiGateway → OpenAI 실시간 호출
     → ai_score(0~100), ai_reason 반환
     → 실패 시 null 반환 (NULL-safe)

  ⑤ ScoreWeightService.getWeight()
     → recommendation_logs 전체 건수 조회
     → score_weights 테이블에서 단계 결정

  ⑥ ReRankingService.rerank(candidates, weight)
     → normalize(rule_weighted_score) × rule_weight
        + normalize(ai_score) × ai_weight
     → ai_score NULL이면 norm_rule만 사용
     → final_score 기준 정렬

  ⑦ RecommendationPersistenceService.save(results)
     → user_recommendations INSERT
     → recommended_at(DATETIME), rule_weight_used, ai_weight_used 필수 기록

[사용자 조회]
  → user_recommendations DB 조회만 (실시간 AI 추가 호출 없음)
  → final_score 기준 정렬 + ai_reason + 우선순위 태그 + "신규" 뱃지 표시
  → `RecommendationResponse.unifiedCategory` 는 canonical summary가 아니라 계속 legacy compat category 계약을 유지
```

---

## SQL 필터 조건 (RetrievalService)

```sql
SELECT ws.*
FROM welfare_services ws
WHERE ws.status IN ('ACTIVE', 'UPCOMING')
  -- 나이 필터 (NULL이면 통과)
  AND (ws.min_age IS NULL OR ws.min_age <= :userAge)
  AND (ws.max_age IS NULL OR ws.max_age >= :userAge)
  -- 소득 필터 (현재는 YOUTH처럼 구조화 값이 있는 경우만 직접 의미가 있음)
  AND (ws.min_income IS NULL OR ws.min_income <= :userIncome)
  AND (ws.max_income IS NULL OR ws.max_income >= :userIncome)
  -- 지역 필터 (service_regions 연결, 전국이면 통과)
  -- 취업상태/저소득층 등은 service_tags TARGET_GROUP 매핑으로 보조 반영
ORDER BY ws.view_count DESC
LIMIT 50;
```

주의:
- 실제 DB 기준으로 `min_income/max_income`은 `YOUTH`에만 대부분 존재한다.
- `YOUTH` 의 `min_income=0 AND max_income=0` 은 2026-04-30 정책상 “미지정” sentinel로 보고 retrieval 에서는 direct filter pass-through 로 해석한다. query semantics 상세는 [policy-normalization-youth-income-zero-policy.md](./history/policy/policy-normalization-youth-income-zero-policy.md)를 따른다.
- `BOKJIRO_CENTRAL/LOCAL`은 현재 소득 구조화 값이 거의 없어 SQL에서 사실상 pass-through 된다.
- 따라서 소득은 1차에서 강한 pass/fail이라기보다 `YOUTH 직접 필터 + 복지로 대상 태그 보조 신호` 수준이다.

---

## if-else 기본 가점 (RuleScoringService)

```java
int score = 0;

// 관심분야 일치 (user_attributes INTEREST_FIELD ↔ service_tags INTEREST_THEME)
if (interestMatches(user, service)) score += 15;

// 관심분야 키워드 일치 (온통청년 보완)
if (keywordMatches(user, service)) score += 10;

// 대상유형 태그 일치 (취업상태/가구유형/저소득층 등)
if (targetGroupMatches(user, service)) score += 10;

// 마감임박 (apply_end_date 기준 7일 이내)
if (isDeadlineSoon(service)) score += 5;

return score; // rule_base_score
```

제외된 항목:
- `onlineApply`: API별 의미가 일관되지 않아 가점 제거
- `청년전용(sourceType=YOUTH)`: 출처와 자격 의미가 동일하지 않아 제거
- `지원금 100만+`: 현재 구조화 지원금 필드가 없어 제거

---

## 우선순위 가중치 (RuleScoringService)

```java
// user_priorities에서 사용자 우선순위 조회
// 우선순위 옵션별 rule_base_score에 곱할 배율
double weight = switch (priorityRank) {
    case 1 -> 2.0;
    case 2 -> 1.6;
    case 3 -> 1.3;
    case 4 -> 1.1;
    case 5 -> 1.0;
    default -> 1.0; // 미설정
};

// HOUSING 우선순위 1위 → 주거 관련 정책 score × 2.0
// 복수 우선순위 항목이 매칭될 경우 최고 배율 하나만 적용 (이중합산 방지)
double ruleWeightedScore = rule_base_score * maxApplicableWeight;
```

---

## 정규화 (ScoreNormalizer)

### 1차: 단순 min-max
```java
public double normalize(double value, double min, double max) {
    if (max == min) return 0.5;
    return (value - min) / (max - min);
}
// 배치 내 후보 전체의 min/max 기준으로 정규화
```

### 2차: p5~p95 클리핑 (1차에서 구현 금지)
```
Step 1: 배치 전체 분포에서 p5, p95 계산
Step 2: clipped = max(p5, min(p95, value))
Step 3: norm = (clipped - p5) / (p95 - p5)
         예외: p95 == p5 → norm = 0.5
```

---

## Cold Start 가중치 단계

```
recommendation_logs 전체 건수 (전역 기준):
  0 ~ 99건   → COLD_START: rule 0.80 / ai 0.20
  100 ~ 499건 → GROWTH:    rule 0.60 / ai 0.40
  500건 이상  → STABLE:    rule 0.40 / ai 0.60
```

```java
// ScoreWeightService
public ScoreWeight getActiveWeight() {
    long totalLogCount = recommendationLogRepository.count();
    return scoreWeightRepository.findByMinLogCountLessThanEqualAndIsActive(
        totalLogCount, true
    ).stream()
     .max(Comparator.comparing(ScoreWeight::getMinLogCount))
     .orElseThrow();
}
```

---

## final_score 계산 (ReRankingService)

```java
ScoreWeight weight = scoreWeightService.getActiveWeight();

double finalScore;
if (aiScore != null) {
    double normRule = normalizer.normalize(ruleWeightedScore, minRule, maxRule);
    double normAi   = normalizer.normalize(aiScore, 0, 100);  // ai_score는 0~100
    finalScore = normRule * weight.getRuleWeight()
               + normAi   * weight.getAiWeight();
} else {
    // ai_score NULL → rule만 사용
    finalScore = normalizer.normalize(ruleWeightedScore, minRule, maxRule);
}
```

---

## AI 프롬프트 (RealtimeAiGateway)

```
시스템: 청년 복지 정책 평가 전문가. JSON만 응답.
군집 특성: {age_group}, {region_sido}, {income_range}, {employment}
정책 목록: {policy_list}
응답: {"results": [{"service_id": 1001, "score": 85, "reason": "1문장"}]}
```

- `reason` → `user_recommendations.ai_reason` 저장
- 개인 식별 정보 전송 금지 (NFR-02-12): 군집 범주값만 전송
- `ai_score` / `ai_reason` 는 current run의 live best-effort 결과로 보고, same input 재실행 시 exact equality는 제품 보장 범위에 두지 않는다. 정책 경계는 [openai-ai-score-product-policy.md](./history/ai/openai-ai-score-product-policy.md)를 따른다.
- `unifiedCategory` 응답 계약은 canonical taxonomy 전환 중에도 계속 compat layer를 대표값으로 유지한다. 세부 정책은 [policy-normalization-unified-category-response-bridge.md](./history/policy/policy-normalization-unified-category-response-bridge.md)를 따른다.

---

## 추천 로그 기록 (RecommendationLogService)

알림 발송 시점에 `recommendation_logs` INSERT:

```java
RecommendationLog log = RecommendationLog.builder()
    .userId(userId)
    .serviceId(serviceId)
    .finalScore(finalScore)
    .ruleWeightUsed(weight.getRuleWeight())   // 발송 시점 가중치 기록
    .aiWeightUsed(weight.getAiWeight())
    .isFallback(aiScore == null)              // AI 없으면 fallback
    .isCicked(false)
    .build();
```

클릭 추적:
```java
// 알림 링크: https://domain.com/policy/{serviceId}?log_id={logId}

// PolicyController
@GetMapping("/policy/{serviceId}")
public ResponseEntity<ApiResponse<PolicyDetailResponse>> getDetail(
        @PathVariable Long serviceId,
        @RequestParam(required = false) Long logId) {
    if (logId != null) {
        recommendationLogService.markClicked(logId);
    }
    return ResponseEntity.ok(ApiResponse.success(policyService.getDetail(serviceId)));
}
```

---

## 분석용 쿼리 (포트폴리오 데모)

```sql
-- AI vs Fallback CTR 비교
SELECT is_fallback,
       COUNT(*) AS total_sent,
       SUM(is_clicked) AS clicked,
       ROUND(SUM(is_clicked) * 100.0 / COUNT(*), 1) AS ctr_pct
FROM recommendation_logs
GROUP BY is_fallback;

-- 가중치 단계별 CTR (Cold Start 효과 분석)
SELECT rule_weight_used, ai_weight_used,
       COUNT(*) AS total_sent,
       ROUND(SUM(is_clicked) * 100.0 / COUNT(*), 1) AS ctr_pct
FROM recommendation_logs
GROUP BY rule_weight_used, ai_weight_used;
```

---

## 현재 알림 슬롯 배치

현재 알림 후보 선택은 단순 top 3가 아니라 `[A, A, B?]` 패턴입니다.

- A 슬롯: `final_score` 기준 상위 개인화 추천 2건
- B 슬롯: 수집 후 24시간 이내 + `rule_base_score >= 0.5` 를 통과한 신규 정책 1건
- B 슬롯이 없으면 `[A, A, A]` fallback

현재 구현 위치:

- `NotificationSlotSelector`
- `NotificationDispatchService.sendTopRecommendations()`

즉 추천 저장/정렬 로직은 그대로 두고, 알림 발송 시점에만 후보 3건을 별도 규칙으로 다시 고릅니다.

---

## 2차 전환 시 교체 지점

| 항목 | 1차 | 2차 교체 방법 |
|------|-----|---------------|
| 군집화 | `ClusterService` → "youth_all" | 내부 로직만 교체 (인터페이스 동일) |
| AI 호출 | `RealtimeAiGateway` | `BatchAiGateway`로 교체 (Gateway 인터페이스 유지) |
| 정규화 | `ScoreNormalizer` min-max | p5~p95 로직 추가 (메서드 오버로드) |
| 알림 후보 | `NotificationSlotSelector` `[A,A,B?]` | 카카오 채널/실험 슬롯 정책으로 확장 |
| 배치 스케줄러 | 없음 | `BatchSubmitService`, `BatchPollingScheduler`, `HardDeadlineScheduler` 추가 |
