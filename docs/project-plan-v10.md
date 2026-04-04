# 청년복지 통합 플랫폼 — 프로젝트 플랜 v10

| 항목 | 내용 |
|------|------|
| 문서 버전 | v10.0 |
| 작성일 | 2026-04-03 |
| 변경 이력 | v9→v10: **확장형 MVP 구조 도입** → 1차(11개 테이블) / 2차(9개 테이블) 분리. **Cold Start 전략** → `score_weights` 테이블 신규 추가, 추천 이력 기반 가중치 자동 전환(rule 0.8→0.4 / ai 0.2→0.6). **AI 점수 위치 수정** → `welfare_services.ai_score` 제거, AI 점수는 `user_recommendations`(유저×서비스 단위)에만 존재. **스키마 무결성 강화** → `service_tags` UNIQUE KEY 추가, `user_attributes.attr_type` ENUM→VARCHAR(30). **컬럼 수정** → `batch_date DATE` → `recommended_at DATETIME`, `reason` → `ai_reason`, `rule_weight_used`·`ai_weight_used` 추가. **`unified_category`** → 3개 API 카테고리 통합 필터용 컬럼 추가 |

---

## 1. 핵심 기능 6가지

1. 로그인 / 회원가입
2. 추천 우선순위 설정 (최대 5개)
3. 2단계 맞춤 추천 (Retrieval → Re-ranking)
4. 조회수 기반 랭킹
5. 키워드·필터 검색
6. 카카오 알림톡 (슬롯 배치)

---

## 2. 기술 스택

| 영역 | 기술 |
|------|------|
| Backend | Spring Boot 3.x (단독) |
| Frontend | React + MUI |
| Database | MySQL 8.0+ |
| AI API | 1차: OpenAI GPT-4o-mini 실시간 호출 / 2차: Batch API 전환 |
| 배포 | EC2 **t4g.large** (2vCPU, 8GB RAM, ARM Graviton2) + Docker Compose 2개 |
| 알림 | 카카오 알림톡 (CoolSMS) + Gmail 폴백 |

---

## 3. 확장형 MVP 구조

### 핵심 원칙

> 서비스 경계는 최종 설계와 동일하게. 내부 구현만 단순화.

```
Controller
 └── RecommendationFacade
     ├── RetrievalService          # SQL 필터 + 기본 가점
     ├── RuleScoringService        # if-else 가점 + 우선순위 가중치
     ├── AiScoringService
     │   └── AiRecommendationGateway
     │       ├── RealtimeAiGateway (1차)
     │       └── BatchAiGateway    (2차 교체)
     ├── ReRankingService          # score_weights + final_score
     └── RecommendationPersistenceService
```

이 구조의 장점: 1차에서 내부만 단순하게, 2차에서 구현체만 교체 가능.

### 1차 / 2차 대응표

| 항목 | 1차 구현 | 2차 확장 |
|---|---|---|
| 군집 | `youth_all` 고정 | 나이대×소득 2D |
| AI 호출 | 실시간 단건 | Batch API |
| 정규화 | 단순 min-max | p5~p95 + Min-Max |
| 알림 후보 | top 3 | A/B 타입 + 슬롯 |
| 가중치 | `score_weights` 테이블 이미 존재 | 값만 튜닝 |
| AI 캐시 | 없음 | `cluster_ai_results` |
| 배치 상태 | 없음 | `batch_jobs` |

---

## 4. 구현 상세

### 4.1 Cold Start 전략 → 가중치 테이블리드 추천

**문제**: 서비스 초기엔 사용자 행동 데이터 부족 → AI 추천 신뢰도 낮음.
AI에 높은 가중치를 주면 오히려 추천 품질이 떨어짐 (Data Sparsity 문제).

**해결**: `score_weights` 테이블에서 추천 이력 수에 따라 자동 전환.

| 단계 | 기준 (`recommendation_logs` count) | rule 가중치 | ai 가중치 |
|---|---|---|---|
| COLD_START | 0 ~ 99건 | **0.80** | 0.20 |
| GROWTH | 100 ~ 499건 | 0.60 | 0.40 |
| STABLE | 500건 이상 | 0.40 | **0.60** |

```java
// ReRankingService.java
ScoreWeight weight = scoreWeightRepository.findActiveWeight(totalLogCount);

double finalScore;
if (aiScore != null) {
    double normRule = normalize(ruleWeightedScore);
    double normAi   = normalize(aiScore);
    finalScore = normRule * weight.getRuleWeight()
               + normAi   * weight.getAiWeight();
} else {
    // ai_score NULL → rule만 사용 (NULL-safe)
    finalScore = normalize(ruleWeightedScore);
}
```

---

### 4.2 군집화 → 1차: youth_all / 2차: 2D

**1차**: `ClusterService.assignCluster()` 항상 `"youth_all"` 반환.
**2차**: 아래 로직으로 교체 (서비스 경계는 동일, 내부만 교체).

```java
// ClusterService.java (2차 구현)
public String assignCluster(User user) {
    String clusterId = buildClusterId(getAgeGroup(user), getIncomeGroup(user));
    if (countUsersInCluster(clusterId) >= minClusterSize) return clusterId;

    String fallbackId = "age_" + getAgeGroup(user);
    if (countUsersInCluster(fallbackId) >= minClusterSize) return fallbackId;

    return "youth_all";
}
```

**군집 예시 (2차)**
```
age20s_early_low / age20s_early_high
age20s_mid_low   / age20s_mid_high
age20s_late_low  / age20s_late_high
age30s_low       / age30s_high
폴백1: age20s_early / ... / age30s
폴백2: youth_all
```

졸업 전 10~50명이면 대부분 `youth_all`로 수렴. 괜찮다.

---

### 4.3 Batch Fallback → 2차 구현 (재시도 없음)

```java
// HardDeadlineScheduler.java (2차)
@Scheduled(cron = "0 0 6 * * *")
public void hardDeadlineFallback() {
    batchJobRepo.findByStatusIn(List.of("in_progress", "submitted"))
        .forEach(job -> {
            job.setStatus("fallback");
            reRankingService.runWithRuleOnly(job.getClusterId());
            batchJobRepo.save(job);
        });
}
```

`batch_jobs` 테이블: `status` 컬럼만으로 관리. `retry_count`, `next_retry_at` 컬럼 없음.

---

### 4.4 recommendation_logs → 포트폴리오 무기

```
발표 때 이 데이터 하나로 차별화 가능:
  "AI 추천과 룰 추천의 클릭률(CTR) 차이는 X%였습니다"
  "Cold Start 구간(rule 80%)과 성장기(AI 60%)의 CTR 변화는 Y%였습니다"
  "적용된 가중치 단계별 추천 품질 비교 가능"
```

**테이블 DDL**

```sql
CREATE TABLE recommendation_logs (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    user_id          BIGINT      NOT NULL,
    service_id       BIGINT      NOT NULL,
    notification_id  BIGINT,                    -- 2차에서 FK 추가 예정
    final_score      DECIMAL(6,5),
    rule_weight_used DECIMAL(3,2),              -- 발송 시점 적용 가중치 (COLD_START/GROWTH/STABLE 분석용)
    ai_weight_used   DECIMAL(3,2),
    is_fallback      TINYINT(1)  NOT NULL DEFAULT 0,   -- TRUE=rule만, FALSE=AI포함
    is_clicked       TINYINT(1)  NOT NULL DEFAULT 0,
    sent_at          DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    clicked_at       DATETIME,
    PRIMARY KEY (id),
    KEY idx_rl_user    (user_id),
    KEY idx_rl_service (service_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
-- 영구 보관 (CTR + 가중치 단계별 품질 분석)
```

**클릭 추적**

```java
// 알림톡 링크 생성
String trackingUrl = "https://service.com/policy/" + serviceId + "?log_id=" + logId;

// PolicyDetailController.java
@GetMapping("/policy/{serviceId}")
public PolicyDetailResponse getDetail(
        @PathVariable Long serviceId,
        @RequestParam(required = false) Long logId) {
    if (logId != null) {
        recommendationLogRepo.markClicked(logId, LocalDateTime.now());
    }
    return policyService.getDetail(serviceId);
}
```

**발표용 쿼리**

```sql
-- AI vs Fallback CTR
SELECT is_fallback,
       COUNT(*)                                     AS total_sent,
       SUM(is_clicked)                              AS clicked,
       ROUND(SUM(is_clicked) * 100.0 / COUNT(*), 1) AS ctr_pct
FROM recommendation_logs
GROUP BY is_fallback;

-- 가중치 단계별 CTR (Cold Start 효과 분석)
SELECT rule_weight_used, ai_weight_used,
       COUNT(*)                                     AS total_sent,
       ROUND(SUM(is_clicked) * 100.0 / COUNT(*), 1) AS ctr_pct
FROM recommendation_logs
GROUP BY rule_weight_used, ai_weight_used;
```

---

### 4.5 점수 정규화 → 1차: 단순 min-max / 2차: p5~p95

**1차**: 단순 min-max (ScoreNormalizer 클래스는 동일, 내부만 단순화)

**2차 예시**
```
Step 1: p5, p95 계산 (배치 전체 분포)
Step 2: clipped = max(p5, min(p95, x))
Step 3: norm = (clipped - p5) / (p95 - p5)
        예외: p95 == p5 → norm = 0.5
```

```java
// ScoreNormalizer.java (2차 구현)
public double normalize(double value, double p5, double p95) {
    if (p95 == p5) return 0.5;
    double clipped = Math.max(p5, Math.min(p95, value));
    return (clipped - p5) / (p95 - p5);
}
```

**신선도는 슬롯에서만**: 점수 곱에 신선도 없음. 신규 정책 노출은 슬롯 [3]에서만 처리.

---

### 4.6 알림 슬롯 배치 → 2차 구현

```java
// NotificationSlotAssigner.java (2차)
// [A, A, B?] 3슬롯 하드코딩
// B타입 선정기준: base_score >= 0.5
```

**1차**: `selectNotificationCandidates(user)` → top 3 반환.
**2차**: A/B 타입 로직으로 교체.

---

### 4.7 AI 프롬프트

```
시스템: 청년 복지 정책 평가 전문가. JSON만 응답.
군집 특성: {age_group}, {region_sido}, {income_range}, {employment}
정책 목록: {policy_list}
응답: {"results": [{"service_id": 1001, "score": 85, "reason": "1문장"}]}
```

응답의 `reason` → `user_recommendations.ai_reason`에 저장.

---

## 5. 배치 타임라인 (2차 기준)

| 시간 | 작업 | 클래스 |
|------|------|--------|
| 새벽 1:00 | 군집(2D) + JSONL + Batch 제출 | `BatchSubmitService` |
| 새벽 1~6시 | 점진적 폴링 (재시도 없음) | `BatchPollingScheduler` |
| 새벽 2:00 | 공공 API 수집 | `CollectService` |
| 새벽 3:00 | 만료 갱신 + user_recommendations.ai_score NULL 처리 | `StatusUpdateService` |
| Batch 완료 즉시 | Re-ranking | 폴링 핸들러 |
| 새벽 6:00 | 하드 데드라인 Fallback | `HardDeadlineScheduler` |
| 오전 8:00 | 슬롯 배치 알림 발송 | `NotificationService` |

---

## 6. 서비스 클래스 구조

```
service/
├── RecommendationFacade.java       # 추천 파이프라인 지휘자 (얇게)
├── RetrievalService.java           # SQL 필터 + 기본 가점 + 신규 가점 포함
├── RuleScoringService.java         # if-else 가점 + 우선순위 가중치
├── AiScoringService.java           # AiRecommendationGateway 호출
│   └── gateway/
│       ├── AiRecommendationGateway.java      (인터페이스)
│       ├── RealtimeAiGateway.java            (1차 구현)
│       └── BatchAiGateway.java               (2차 구현)
├── ScoreWeightService.java         # recommendation_logs count → 가중치 단계 결정
├── ReRankingService.java           # score_weights 적용 + final_score
├── RecommendationPersistenceService.java
├── ClusterService.java             # 1차: youth_all / 2차: 2D 군집
├── ScoreNormalizer.java            # 1차: min-max / 2차: p5~p95
├── NotificationService.java        # 알림 발송
├── RecommendationLogService.java   # is_fallback·is_clicked·weight 기록
├── CollectService.java             # 공공 API 수집
├── BatchSubmitService.java         # 2차: JSONL + Batch 제출
├── BatchPollingScheduler.java      # 2차: 점진적 폴링
├── HardDeadlineScheduler.java      # 2차: 새벽 6시 Fallback
└── StatusUpdateService.java        # 만료 갱신
```

**설계 원칙**

| 원칙 | 내용 |
|---|---|
| Controller는 얇게 | 추천 계산은 RecommendationFacade에서 |
| 점수 로직 분리 | RuleScoringService / AiScoringService / ReRankingService |
| 외부 API는 Gateway | OpenAI·공공API·알림톡 직접 호출 금지 |
| NULL-safe | ai_score NULL → rule만 / cluster 없으면 youth_all |

---

## 7. DB 테이블 목록

### 1차 구현 (11개) → 지금 바로 만들 것

| # | 테이블 | 역할 | 주요 변경 |
|---|---|---|---|
| 1 | `users` | 회원 기본정보 | |
| 2 | `user_attributes` | 관심분야·대상 선택 | attr_type **VARCHAR(30)** (ENUM 제거) |
| 3 | `user_priorities` | 우선순위 설정 | |
| 4 | `priority_options` | 선택지 마스터 | |
| 5 | `welfare_services` | 정책 통합 (FULLTEXT) | **ai_score 제거**, unified_category 추가 |
| 6 | `welfare_service_details` | 정책 상세 | |
| 7 | `service_regions` | 정책-지역 다대다 | |
| 8 | `service_tags` | 정책 태그 | **UNIQUE KEY uq_st 추가** |
| 9 | `user_recommendations` | 추천 결과 | **recommended_at DATETIME**, ai_reason, rule/ai_weight_used 추가 |
| 10 | `recommendation_logs` | 추천 클릭 추적 | rule/ai_weight_used 추가 |
| 11 | `score_weights` | Cold Start 가중치 설정 | **신규** |

### 2차 확장 (9개)

| # | 테이블 | 역할 | 추가 시점 |
|---|---|---|---|
| 12 | `user_clusters` | 사용자↔군집 매핑 | 군집화 구현 시 |
| 13 | `cluster_ai_results` | 군집×정책 AI 결과 (7일 TTL) | Batch AI 전환 시 |
| 14 | `batch_jobs` | Batch 제출 이력·상태 | Batch AI 전환 시 |
| 15 | `normalization_stats` | 배치별 p5·p95 | 정규화 고도화 시 |
| 16 | `notifications` | 알림 헤더 | 알림 시스템 구현 시 |
| 17 | `notification_services` | 알림-정책 매핑 | 알림 시스템 구현 시 |
| 18 | `api_sync_logs` | 수집 배치 이력 | 배치 안정화 후 |
| 19 | `search_logs` | 검색 키워드 | 검색 기능 안정화 후 |
| 20 | `service_view_logs` | 조회수 중복 방지 | 조회수 정교화 시 |

---

## 8. 역할 분담

| 역할 | 담당 |
|------|------|
| **A** | CollectService, StatusUpdateService, RetrievalService, ClusterService, NotificationService, 인증, AWS 배포 |
| **B** | RuleScoringService, AiScoringService, ScoreWeightService, ReRankingService, RecommendationPersistenceService, RecommendationLogService, ScoreNormalizer, React 전체 |
| **공통** | RecommendationFacade, Docker, DB 스키마, 테스트, 발표 |

---

## 9. 개발 마일스톤 (4/1~6/30, 13주)

### Phase 1: 기반 (W1~W3)
- [ ] data.go.kr 인증키 3개 신청
- [ ] 카카오 알림톡 채널 + 템플릿 사전 제출
- [ ] Docker Compose (Spring + MySQL)
- [ ] DB 스키마 **1차 11개 테이블** (score_weights 초기 데이터 포함)
- [ ] 회원가입 / 로그인 (JWT, HttpOnly 쿠키)
- [ ] 수집 배치 + Jsoup strip + unified_category 매핑

### Phase 2: 핵심 기능 (W4~W6)
- [ ] 정책 목록 + 검색 (FULLTEXT) + 필터 (unified_category 포함)
- [ ] 정책 상세 (welfare_service_details JOIN)
- [ ] 우선순위 선택 UI
- [ ] 북마크, 마이페이지, 조회수 랭킹

### Phase 3: 추천 1차 (W7~W8)
- [ ] RetrievalService (SQL 필터 + 기본 가점, youth_all 고정)
- [ ] RuleScoringService (if-else 가점 + 우선순위 가중치)
- [ ] RealtimeAiGateway (소수 N개 실시간 호출 → ai_score, ai_reason)
- [ ] ScoreWeightService (Cold Start 가중치 결정)
- [ ] ReRankingService (final_score 계산 + recommended_at 저장)
- [ ] 추천 카드 UI (ai_reason + 우선순위 태그)
- [ ] recommendation_logs 클릭 추적 (`?log_id=`)

### Phase 4: 알림 + 2차 확장 (W9~W11)
- [ ] NotificationService 1차 (top 3 발송)
- [ ] 카카오 알림톡 + 이메일 폴백
- [ ] 2차 테이블 추가 (user_clusters, batch_jobs, cluster_ai_results 등)
- [ ] BatchAiGateway 교체 (실시간 → Batch API)
- [ ] ClusterService 2차 (youth_all → 2D 군집)
- [ ] ScoreNormalizer 2차 (min-max → p5~p95)
- [ ] 슬롯 배치 [A, A, B?]

### Phase 5: 마무리 (W12~W13)
- [ ] AWS EC2 배포 + HTTPS
- [ ] 보안 항목 (Rotation, 잠금, 탈퇴 비식별화)
- [ ] 통합 테스트 + 버그 수정
- [ ] 발표 자료 + 데모 시나리오 (CTR 분석 쿼리 포함)

---

## 10. 설계 결정 요약

| 결정 | 선택 | 이유 |
|------|------|------|
| 인프라 | t4g.large (8GB ARM) | t3.medium 대비 RAM 2배, 비용 약 10% 저렴 |
| 확장형 MVP | 1차/2차 구조 분리 | 처음부터 서비스 경계 확정 → 재작성 없이 내부만 교체 |
| Cold Start | score_weights 테이블 (rule 0.8→0.4) | 초기 AI 데이터 부족 → rule 우선, 이력 추적 후 AI 비중 증가 |
| ai_score 위치 | user_recommendations에만 | 유저×서비스 단위 점수. welfare_services에 두면 모두 공유 → 개인화 불가 |
| user_attributes.attr_type | VARCHAR(30) | ENUM은 신규 속성 추가 시 ALTER TABLE 필요 → 경직성 문제 |
| service_tags UNIQUE KEY | uq_st (service_id, tag_type, tag_value) | 중복 태그 → rule_base_score 이중 합산 버그 방지 |
| batch_date → recommended_at | DATETIME | 실시간 방식에서 DATE만으로 중복 구분 불가. DATETIME이 배치 시간도 포괄 |
| ai_reason | user_recommendations에 저장 | 유저별 AI 추천 이유. 추천 UI 및 알림톡 템플릿에 직접 표시 |
| unified_category | welfare_services 별도 컬럼 | 3개 API 카테고리 체계 불일치. 필터 UI 통합용 수집 시 매핑. 원본 category_main/service_tags 보존 |
| 군집화 | 1차 youth_all → 2차 나이대×소득 2D | 졸업 전 사용자 소수면 2D는 youth_all 수렴. ClusterService 경계 유지로 2차 교체 용이 |
| Freshness | 슬롯 제어만 (점수 곱셈 없음) | 점수 곱셈은 정규화 체계 개입. 슬롯 [A,A,B?]로 충분 |
| Batch Fallback | 재시도 없음, 6시 하드 데드라인 | 재시도 후 구현·디버깅 비용 > 단순 Fallback |
| 슬롯 배치 | [A,A,B?] 3슬롯 하드코딩 | 사용자 피로도 감소. 동적 계산 제거로 버그 추적 쉬움 |
| recommendation_logs | rule/ai_weight_used 포함 | 가중치 단계별 CTR 분석 → 포트폴리오 핵심 데이터 |
