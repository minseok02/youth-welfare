# 서비스 클래스 아키텍처

> 1차/2차 구분 포함. 1차에서 2차 클래스 의존 금지.

---

## 추천 파이프라인 클래스 흐름

```
RecommendationController
  └── RecommendationFacade          ← 유일한 비즈니스 진입점
        ├── ClusterService           # 1차: 항상 "youth_all" 반환
        ├── RetrievalService         # SQL 필터 + 기본 가점 + 신규 정책 가미
        ├── RuleScoringService       # if-else 가점 + 우선순위 가중치
        ├── AiScoringService
        │     └── AiRecommendationGateway (인터페이스)
        │           └── RealtimeAiGateway  (1차 구현체)
        │           └── BatchAiGateway     (2차 교체용, 1차에서 구현 금지)
        ├── ScoreWeightService       # score_weights 테이블 조회 → 단계 결정
        ├── ReRankingService         # final_score 계산
        ├── RecommendationPersistenceService  # user_recommendations 저장
        └── RecommendationLogService          # recommendation_logs 기록
```

---

## 각 서비스 역할

### RecommendationFacade
- 추천 파이프라인 조율자 (오케스트레이터)
- Controller에서 유일하게 호출하는 클래스
- 개별 Service를 순서대로 호출하고 결과를 조합

### ClusterService
- **1차**: `assignCluster(User)` → 항상 `"youth_all"` 반환
- **2차**: 나이대×소득 2D 군집 ID 반환 + `min_cluster_size` 미만이면 단계적 fallback

```java
// 1차 구현
public String assignCluster(User user) {
    return "youth_all";
}
```

### RetrievalService
- SQL WHERE 필터: 나이/지역 pass/fail 우선
- 소득은 `YOUTH` 구조화 값만 직접 적용, 복지로는 대상 태그 기반 보조 신호
- 기본 가점 계산 후 상위 K=50건 선별
- 신규 정책 가미: 수집 후 24시간 이내 + rule_base_score 최소값 M=5건 강제 포함

### RuleScoringService
- if-else 기본 가점:
  - 관심분야 태그 일치 +15, 키워드 일치 +10, 대상유형 태그 일치 +10
  - 마감임박 +5
- 제외:
  - `onlineApply`, `sourceType=YOUTH`, 구조화 지원금 필드는 신뢰도 부족으로 미사용
- 우선순위 가중치 적용 → `rule_weighted_score`
  - 1순위×2.0 / 2순위×1.6 / 3순위×1.3 / 4순위×1.1 / 5순위×1.0 / 미설정×1.0

### AiScoringService
- `AiRecommendationGateway` 호출 (인터페이스)
- ai_score, ai_reason 반환 → `user_recommendations`에 저장
- NULL-safe: Gateway 실패 시 null 반환, 이후 단계에서 rule만 사용

### AiRecommendationGateway (인터페이스)
```java
public interface AiRecommendationGateway {
    List<AiScoreResult> score(String clusterId, List<PolicyCandidate> candidates, UserProfile profile);
}
```
- **1차 구현체**: `RealtimeAiGateway` — OpenAI 실시간 단건 호출
- **2차 구현체**: `BatchAiGateway` — OpenAI Batch API (1차에서 구현 금지)

### ScoreWeightService
- `recommendation_logs` 전체 건수 조회
- `score_weights` 테이블에서 해당 단계(COLD_START/GROWTH/STABLE) 조회
- 가중치 하드코딩 절대 금지

### ReRankingService
- `final_score` 계산:
  ```
  ai_score 있음: final_score = normalize(rule_weighted_score) × rule_weight
                              + normalize(ai_score) × ai_weight
  ai_score NULL: final_score = normalize(rule_weighted_score)
  ```
- **1차 정규화**: 단순 min-max `ScoreNormalizer`

### ScoreNormalizer
```java
// 1차: 단순 min-max
public double normalize(double value, double min, double max) {
    if (max == min) return 0.5;
    return (value - min) / (max - min);
}

// 2차: p5~p95 (1차에서 구현 금지)
```

### RecommendationPersistenceService
- `user_recommendations` INSERT
- 필수 필드: `recommended_at(DATETIME)`, `rule_weight_used`, `ai_weight_used`

### RecommendationLogService
- `recommendation_logs` INSERT (알림 발송 시점에 기록)
- `is_fallback`: TRUE=rule만, FALSE=AI 포함
- 클릭 추적: `?log_id={id}` 파라미터로 `is_clicked = TRUE` 업데이트

---

## 수집 배치 클래스

### CollectService
- 매일 새벽 2시 3개 공공API 수집
- 각 API Client는 `collect/gateway/` 패키지에 분리

### 공공API Client (외부 API 직접 호출 금지, 반드시 Client 클래스 사용)
```
YouthApiClient          # 온통청년 JSON API
BokjiroCentralClient    # 복지로 중앙 XML API (XXE 비활성화 필수)
BokjiroLocalClient      # 복지로 지자체 XML API
```

### WelfareServiceMapper
- 3종 DTO → `WelfareService` Entity 공통 변환
- `unified_category` 매핑 반드시 포함 (매핑 규칙 → [`api-mapping.md`](api-mapping.md))
- Jsoup HTML strip 후 저장
- service_tags: UPSERT (INSERT IGNORE 또는 ON DUPLICATE KEY)

### StatusUpdateService
- 매일 새벽 3시: 만료 정책 `status = CLOSED`
- 해당 정책의 `user_recommendations.ai_score = NULL` 리셋

---

## 알림 서비스 클래스

### NotificationService
- **1차**: `selectNotificationCandidates(user)` → top 3 반환
- **2차**: [A, A, B?] 슬롯 배치 로직으로 교체 (1차에서 구현 금지)

### 외부 알림 Client (직접 호출 금지)
```
KakaoAlimtalkClient    # CoolSMS SDK 래핑
EmailClient            # Spring Mail + Gmail SMTP
```
- 알림 발송 실패 시: 30분/2시간 후 최대 2회 재시도
- 카카오 실패 시 이메일 폴백

---

## 배치 스케줄러 (Spring @Scheduled)

| 시간 | 클래스 | 역할 |
|------|--------|------|
| 새벽 2:00 | `CollectService` | 공공API 수집 |
| 새벽 3:00 | `StatusUpdateService` | 만료 처리 + ai_score NULL 리셋 |
| 오전 8:00 | `NotificationService` | 알림 발송 |

> 2차 추가 스케줄러: `BatchSubmitService`(새벽 1시), `BatchPollingScheduler`, `HardDeadlineScheduler`(새벽 6시)

---

## 글로벌 공통

### ApiResponse<T>
```java
public class ApiResponse<T> {
    private boolean success;
    private T data;
    private String message;
    private String errorCode;
}
```

### GlobalExceptionHandler
- `@RestControllerAdvice`
- `CustomException` → `ApiResponse` 변환
- 예상치 못한 Exception → 500 + 로그

### BaseTimeEntity
```java
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseTimeEntity {
    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}
```
