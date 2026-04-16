# 청년복지 통합 플랫폼 — 프로젝트 플랜 v11

| 항목 | 내용 |
|------|------|
| 문서 버전 | v11.2 |
| 작성일 | 2026-04-17 |
| 변경 이력 | v11.1→v11.2: **조회수·랭킹 고도화 반영** — `service_view_logs` 테이블/엔티티 추가, 정책 상세 조회 시 24시간 중복 조회 차단(로그인: user_id, 비로그인: fingerprint). 랭킹은 `uniqueViewCount7d`(최근 7일 고유조회) + `view_count` + `api_view_count` + 최신성으로 계산하고, 신규 정책 탐색 슬롯(최근 14일, top>=10 시 최대 2개) 적용. `PolicyRankingResponse`에 `uniqueViewCount7d` 필드 추가. 추천/랭킹/검색/상세 E2E 실데이터 검증 완료. v11.0→v11.1: **복지로 상세 수집 운영 제약 반영** — 공공데이터포털 상세 API 기능별 일일 트래픽 100 기준으로 수정. `BokjiroDetailCollectService`/`BokjiroDetailClient` 반영(요청 간격, 타임아웃, 429 보호, API별 호출 상한). 상세 수집 호출 카운트 기준을 "정책 건수"가 아닌 "실제 HTTP 요청 수(재시도 포함)"로 명확화. 추천은 현재 **동작 가능(룰+AI fallback)** 상태이나 품질 고도화는 후속 단계로 분리. v10→v11: **챗봇 모듈 설계 반영** — welfare/ 패키지(WelfareService) 신규 추가. chat/ 모듈 역할 명확화(로그인 전용, 로그아웃 시 데이터 삭제). 의존 방향 원칙 구체화(chat→welfare 허용, chat→recommendation 금지, chat→user_recommendations 허용). chat/ 내부 클래스 2차 확장 타깃 명확화(ChatService, ChatRepository). 2차 확장 테이블 9개→11개(`chat_sessions`, `chat_messages` 추가). v9→v10: **확장형 MVP 구조 도입** — 1차(11개 테이블) / 2차(9개 테이블) 분리. **Cold Start 전략** — `score_weights` 테이블 신규 추가, 추천 이력 기반 가중치 자동 전환(rule 0.8→0.4 / ai 0.2→0.6). **AI 점수 위치 수정** — `welfare_services.ai_score` 제거, AI 점수는 `user_recommendations`(유저×서비스 단위)에만 존재. **스키마 무결성 강화** — `service_tags` UNIQUE KEY 추가, `user_attributes.attr_type` ENUM→VARCHAR(30). **컬럼 수정** — `batch_date DATE` → `recommended_at DATETIME`, `reason` → `ai_reason`, `rule_weight_used`·`ai_weight_used` 추가. **`unified_category`** — 3개 API 카테고리 통합 필터용 컬럼 추가 |

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

### 운영 제약 (2026-04-16 확인)

- 공공데이터포털 복지로 상세 API는 **기능별 일일 트래픽 100**.
- 운영 기본값: API별 일일 최대 95회(안전 여유 5회).
- 429 응답 시 해당 API 수집 즉시 중단(한도 보호), 5xx만 재시도.

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

**문제**: 서비스 초기엔 사용자 행동 데이터 부족 → AI 점수 신뢰도 낮음.
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

---

### 4.4 recommendation_logs → 포트폴리오 무기

```
발표 데이 이 데이터 하나로 차별화 가능:
  "AI 추천과 룰 추천의 클릭률(CTR) 차이는 X%였습니다"
  "Cold Start 구간(rule 80%)과 안정기(AI 60%)의 CTR 변화는 Y%였습니다"
  "적용된 가중치 단계별 추천 품질 비교 가능"
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

---

### 4.6 알림 슬롯 배치 → 2차 구현

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
| 새벽 1~6시 | 폴링 (재시도 없음) | `BatchPollingScheduler` |
| 새벽 2:00 | 공공 API 수집 | `CollectService` |
| 새벽 3:00 | 만료 갱신 + user_recommendations.ai_score NULL 처리 | `StatusUpdateService` |
| 새벽 6:00 | 하드 데드라인 Fallback | `HardDeadlineScheduler` |
| 오전 8:00 | 슬롯 배치 알림 발송 | `NotificationService` |

---

## 6. 서비스 클래스 구조

```
service/
├── recommendation/                              # 추천 파이프라인 모듈
│   ├── RecommendationFacade.java               # 추천 파이프라인 지휘자 (유일)
│   ├── RetrievalService.java                   # SQL 필터 + 기본 가점 + 신규 가점 포함
│   ├── RuleScoringService.java                 # if-else 가점 + 우선순위 가중치
│   ├── AiScoringService.java                   # AiRecommendationGateway 호출
│   │   └── gateway/
│   │       ├── AiRecommendationGateway.java    (인터페이스)
│   │       ├── RealtimeAiGateway.java          (1차 구현)
│   │       └── BatchAiGateway.java             (2차 구현)
│   ├── ScoreWeightService.java                 # recommendation_logs count → 가중치 단계 결정
│   ├── ReRankingService.java                   # score_weights 적용 + final_score
│   ├── RecommendationPersistenceService.java
│   ├── ClusterService.java                     # 1차: youth_all / 2차: 2D 군집
│   ├── ScoreNormalizer.java                    # 1차: min-max / 2차: p5~p95
│   └── RecommendationLogService.java           # is_fallback·is_clicked·weight 기록
├── welfare/                                     # 정책 단위 조회 모듈
│   └── WelfareService.java                     # welfare_services 단위 조회 전용 (챗봇·공통용)
│                                               # RetrievalService의 역할 분리:
│                                               # RetrievalService → 추천 전용 필터·가점 쿼리
│                                               # WelfareService  → 정책 원본 단위 조회
├── chat/                                        # ── 2차 확장 시 구현 ──
│   ├── ChatService.java                        # 2차: 대화 히스토리 + OpenAI 호출 + WelfareService 연동
│   └── ChatRepository.java                     # 2차: chat_sessions·chat_messages DB 접근 전담
│                                               # 로그아웃 시 해당 유저 세션·메시지 전체 삭제 처리
├── NotificationService.java                    # 알림 발송
├── CollectService.java                         # 공공 API 수집 (목록 + 상세 트리거)
├── BokjiroDetailCollectService.java            # 복지로 상세 수집 (API별 상한/429 보호)
├── BatchSubmitService.java                     # 2차: JSONL + Batch 제출
├── BatchPollingScheduler.java                  # 2차: 폴링
├── HardDeadlineScheduler.java                  # 2차: 새벽 6시 Fallback
└── StatusUpdateService.java                    # 만료 갱신
```

**설계 원칙**

| 원칙 | 내용 |
|---|---|
| Controller는 얇게 | 추천 계산은 RecommendationFacade에서 |
| 점수 로직 분리 | RuleScoringService / AiScoringService / ReRankingService |
| 외부 API는 Gateway | OpenAI·공공API·알림톡 직접 호출 금지 |
| NULL-safe | ai_score NULL → rule만 / cluster 없으면 youth_all |
| 모듈 경계 분리 | chat/ → welfare/ 허용 (정책 단위 조회). chat/ → user_recommendations 허용 (개인화 데이터 조회). chat/ → recommendation/ 금지 (추천 계산 로직 의존 금지). Controller 레이어는 React 화면 설계 완료 후 API 엔드포인트 기준으로 별도 정의 |
| 트래픽 확장 시 | welfare/WelfareService와 recommendation/RetrievalService가 같은 테이블 접근 → 규모 확장 시 WelfareRepository 추가하여 통합 검토 |

---

## 7. DB 테이블 목록

### 1차 구현 (12개) — 지금 바로 만들 것

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
| 12 | `service_view_logs` | 조회수 중복 방지(24h dedup) | **신규** |

### 2차 확장 (11개)

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
| 20 | `service_view_logs` | 조회수 집계 고도화(윈도우/장치/세션 확장) | 1차 이후 정교화 시 |
| 21 | `chat_sessions` | 챗봇 대화 세션 | 챗봇 모듈 구현 시 |
| 22 | `chat_messages` | 챗봇 대화 메시지 히스토리 | 챗봇 모듈 구현 시 |

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
- [ ] Docker Compose (Spring + MySQL)
- [ ] DB 스키마 **1차 11개 테이블** (score_weights 초기 데이터 포함)
- [ ] 회원가입 / 로그인 (JWT, HttpOnly 쿠키)
- [ ] 수집 배치 + Jsoup strip + unified_category 매핑
- [ ] 복지로 상세 수집 보호로직 (기능별 100/일 기준, API별 상한/429 차단)

### Phase 2: 핵심 기능 (W4~W6)
- [x] 정책 목록 + 검색 (FULLTEXT) + 필터 (unified_category 포함)
- [x] 정책 상세 (welfare_service_details JOIN)
- [ ] 우선순위 선택 UI
- [ ] 북마크, 마이페이지
- [x] 조회수 랭킹 (고유조회 7일 + 탐색 슬롯)

### Phase 3: 추천 1차 (W7~W8)
- [x] RetrievalService (SQL 필터 + 기본 가점, youth_all 고정)
- [x] RuleScoringService (if-else 가점 + 우선순위 가중치)
- [x] RealtimeAiGateway (상위 N개 실시간 호출 → ai_score, ai_reason)
- [x] ScoreWeightService (Cold Start 가중치 결정)
- [x] ReRankingService (final_score 계산 + recommended_at 저장)
- [ ] 추천 카드 UI (ai_reason + 우선순위 태그)
- [x] recommendation_logs 클릭 추적 (`?log_id=`)
- [ ] 품질 고도화는 별도 트랙으로 분리 (1차 목표: 추천 안정 동작/실패 없는 fallback)

### Phase 4: 알림 + 2차 확장 (W9~W11)
- [ ] NotificationService 1차 (top 3 발송)
- [ ] 2차 테이블 추가 (user_clusters, batch_jobs, cluster_ai_results 등)
- [ ] BatchAiGateway 교체 (실시간 → Batch API)
- [ ] ClusterService 2차 (youth_all → 2D 군집)
- [ ] ScoreNormalizer 2차 (min-max → p5~p95)
- [ ] 슬롯 배치 [A, A, B?]

### Phase 5: 마무리 (W12~W13)
- [ ] AWS EC2 배포 + HTTPS
- [ ] 보안 항목 (Rotation, 잠금, 취약점 비식별화)
- [ ] 통합 테스트 + 버그 수정
- [ ] 발표 자료 + 데모 시나리오 (CTR 분석 쿼리 포함)

---

## 10. 설계 결정 요약

| 결정 | 선택 | 이유 |
|------|------|------|
| 인프라 | t4g.large (8GB ARM) | t3.medium 대비 RAM 2배, 비용 약 10% 저렴 |
| 확장형 MVP | 1차/2차 구조 분리 | 처음부터 서비스 경계 확정 → 재설계 없이 내부만 교체 |
| Cold Start | score_weights 테이블 (rule 0.8→0.4) | 초기 AI 데이터 부족 → rule 우선, 이력 축적 후 AI 비중 증가 |
| ai_score 위치 | user_recommendations에만 | 유저×서비스 단위 점수. welfare_services에 두면 모두 공유 → 개인화 불가 |
| user_attributes.attr_type | VARCHAR(30) | ENUM은 새 속성 추가 시 ALTER TABLE 필요 → 경직성 문제 |
| service_tags UNIQUE KEY | uq_st (service_id, tag_type, tag_value) | 중복 태그 → rule_base_score 이중 합산 버그 방지 |
| recommendation_logs | rule/ai_weight_used 포함 | 가중치 단계별 CTR 분석 → 포트폴리오 핵심 데이터 |
| FastAPI | 제거 (Spring 단일화) | 딥러닝 서빙 없는 2인 졸업작품에서 불필요. Gateway 인터페이스로 분리 가능한 구조 유지 |
| 챗봇 | 2차 구현 | 1차에서 구조만 잡아두고 시간 여유 시 구현. 로그인 전용, 로그아웃 시 세션 삭제. chat/ → welfare/ 허용, recommendation/ 금지 |
