# 추천 파이프라인 상세

문서군 진입점은 [추천 목차](recommendation-docs-index.md)입니다. 현재 호출·저장·로그 경계를 설명하며, 북마크 보존·소득 단위·평가 분모의 문제는 [추천 현재 상태](recommendation-current-state.md)의 미해결 과제로 구분합니다. 과거 1차/2차 구분은 새로운 작업의 금지 조건이나 확정된 실행 순서가 아닙니다.

## API와 실행 경계

| 요청 | 현재 처리 |
| --- | --- |
| `GET /api/recommendations` | 저장된 추천 조회. 조회 자체로 실시간 AI를 추가 호출하지 않음 |
| `POST /api/recommendations/refresh` | 생성 파이프라인. 기본 모드는 재사용 가능한 저장 배치를 반환할 수 있음 |
| `POST /api/recommendations/refresh?personal=true` | 개인 재사용 마커를 비우고 개인 프로필 기반 생성 경로 실행 |
| `POST /api/policies/{id}/recommendation-click` | 인증된 사용자의 클릭 기록. JSON body로 `logId` 전달 |

추천 생성은 사용자별 rate limit과 동시 실행 guard를 거칩니다. Redis는 개인 refresh 재사용 마커에 사용하고 추천 결과는 DB에서 읽습니다. 전체 추천 payload 캐시나 학습된 군집 모델과 같은 뜻이 아닙니다.

구현은 [RecommendationController](../../backend/src/main/java/com/example/welfare/recommend/controller/RecommendationController.java), [RecommendationGenerationService](../../backend/src/main/java/com/example/welfare/recommend/service/RecommendationGenerationService.java), [PolicyController](../../backend/src/main/java/com/example/welfare/policy/controller/PolicyController.java)를 확인합니다.

## 생성과 저장 순서

1. 사용자와 추천용 snapshot을 읽고 rate limit·동시 실행 여부를 검사합니다.
2. 기본 refresh는 Redis 마커가 가리키는 저장 배치를 재사용할 수 있습니다. 개인 refresh는 마커를 비웁니다.
3. 현재 `ClusterService.assignCluster(snapshot)`는 `youth_all`을 반환합니다. 이 군집의 AI 점수는 군집 캐시를 사용하지 않습니다.
4. `RetrievalService.retrieve(clusterId, snapshot)`에서 후보와 projection을 조회합니다.
5. `RuleScoringService`에서 규칙 점수를 계산하고 `RecommendationPostScoringFilterService`에서 특화 대상 불일치를 후처리합니다.
6. `AiScoringService`와 gateway에서 AI 결과·상태를 채운 뒤 `ReRankingService`에서 정렬합니다.
7. `RecommendationPersistenceService.save(user, candidates, weight)`에서 추천을 교체 저장합니다. `recommended_at`은 PostgreSQL `TIMESTAMP`이며 점수·AI 상태·사용한 가중치도 기록합니다.
8. 저장 배치를 다시 읽고 추천 로그를 갱신합니다. 로그 후처리가 실패하면 warning을 남기며, 기본 refresh는 재사용 마커를 기록합니다.

저장은 단순 INSERT가 아니라 전용 command datasource의 사용자별 delete/insert 교체 경로입니다. 새 후보에 포함된 정책의 기존 북마크 상태는 복사하지만, **후보 밖 북마크 보존까지 보장하는 구현은 아닙니다.** [저장 서비스](../../backend/src/main/java/com/example/welfare/recommend/service/RecommendationPersistenceService.java)의 이 경계를 별도 회귀 테스트와 설계로 개선해야 합니다.

## 후보 조회와 자격조건

[RetrievalService](../../backend/src/main/java/com/example/welfare/recommend/service/RetrievalService.java)는 기본 후보와 최근 후보를 별도로 읽습니다. 기본 조회량은 150건이며 지역 조건에 따라 300건으로 늘어납니다. projection·태그 기반 후처리와 후보 분산을 거친 뒤 기본 최대 50건, 최근 최대 5건을 중복 제거해 합치므로 최종 후보는 최대 55건입니다. 항상 신규 5건이 강제 포함된다는 뜻은 아닙니다.

실제 SQL 경계는 `RecommendationCandidateReadRepositoryImpl`과 공통 `PolicyExplorationService`입니다. 지역·나이·소득·청년 관련성·후보 창·후처리를 생략한 단일 `LIMIT 50` 예시는 전체 동작을 대표하지 않습니다.

- 사용자 소득 구간과 source의 금액·비율은 같은 단위라고 보장할 수 없습니다. 기존 숫자 비교를 검증된 자격판정으로 설명하지 않습니다.
- `YOUTH min_income=0 AND max_income=0`은 현재 direct filter에서 미지정 sentinel로 통과시키는 구현입니다. 배경은 [소득 sentinel 정책](../history/policy/policy-normalization-youth-income-zero-policy.md)을 확인합니다.
- 미입력 기본값, source별 누락, SQL과 후처리의 조건 차이는 [정규화 확인 과제](../policy/policy-normalization-current-state.md#리팩토링-확인-과제)에 포함됩니다.

## 규칙 점수와 우선순위

[RuleScoringService](../../backend/src/main/java/com/example/welfare/recommend/service/RuleScoringService.java)는 관심분야·키워드·대상 태그·마감뿐 아니라 우선순위 불일치, 특화 대상, 주거 프로필, 청년 관련성 등을 반영합니다. `PriorityMatcher`와 projection 기반 신호도 사용하므로 단순한 네 가지 가점만으로 현재 점수를 재현할 수 없습니다.

규칙 점수 `rule_base_score`와 우선순위를 반영한 `rule_weighted_score`를 구분합니다. source가 YOUTH라는 이유만으로 자격 충족을 확정하지 않으며, 구조화되지 않은 지원금을 고정 금액 가점처럼 설명하지 않습니다.

## 가중치와 최종 정렬

[ScoreWeightService](../../backend/src/main/java/com/example/welfare/recommend/service/ScoreWeightService.java)는 전체 추천 로그 건수와 DB의 활성 `score_weights`를 읽고, 건수 이하에서 가장 큰 `min_log_count` 단계를 고릅니다. 아래는 [신규 schema의 seed](../../backend/src/main/resources/db/schema.sql)이며 현재 서버 DB의 실효값을 조회한 결과는 아닙니다.

| 단계 | 로그 건수 기준 | rule | AI |
| --- | --- | --- | --- |
| COLD_START | 0 이상 | 0.80 | 0.20 |
| GROWTH | 100 이상 | 0.60 | 0.40 |
| STABLE | 500 이상 | 0.40 | 0.60 |

로그 건수 증가가 모델 학습이나 품질 개선을 의미하지 않습니다. 실제 사용자 표본과 합성 계정의 구분, 클릭·노출의 분모 문제를 먼저 확인하며, 기존 [CTR readiness](recommendation-ctr-readiness-runbook.md)의 관측과 데이터 보존 결함 수정은 구분합니다.

[ReRankingService](../../backend/src/main/java/com/example/welfare/recommend/service/ReRankingService.java)의 현재 계산은 다음과 같습니다.

- 규칙 점수는 후보의 최솟값이 아니라 **하한 0과 후보 최대 rule weighted score** 사이에서 정규화합니다.
- `aiStatus=SCORED`이고 AI 점수가 있을 때 AI를 0~100 기준으로 정규화합니다.
- 우선순위/관심분야 불일치에 따라 AI 가중치를 줄이고 줄인 몫을 rule 쪽으로 옮깁니다. 불일치가 없는 후보만 기본 가중치의 단순 혼합과 같습니다.
- AI를 사용할 수 없는 후보는 정규화한 rule 점수만 사용합니다.
- 이후 우선순위 순번 조정, 다양성 조정, 우선순위 미설정 사용자의 상위 후보 분산·재배열을 거칩니다.

따라서 단순한 `norm_rule × rule_weight + norm_ai × ai_weight`만으로 최종 순서를 재현할 수 없습니다. p5~p95 clipping은 과거 확장 제안이며 현재 구현이나 의무적인 다음 단계가 아닙니다.

## AI 결과와 응답 표현

AI 요청과 상태 구분은 [OpenAI 런타임 계약](../core/openai-runtime-contract.md), 사용자 메모는 [AI 사유 계약](recommendation-ai-reason-memo-contract.md)을 따릅니다.

- 이름·이메일·전체 생년월일·전화번호 같은 직접 식별자를 전송하지 않고 추천용 범주값을 사용합니다.
- `SCORED`, `RULE_ONLY`, `CALL_FAILED`, `PARTIAL_MISSING`, `NOT_REQUESTED`를 구분합니다. 모든 실패를 같은 null 점수라고 설명하지 않습니다.
- 점수와 사유는 best-effort 결과이며 동일 입력 재실행의 완전 일치를 보장하지 않습니다. 기존 배경은 [제품 정책](../history/ai/openai-ai-score-product-policy.md)에 있습니다.
- `RecommendationResponse.unifiedCategory`는 canonical summary가 아닌 legacy compat 대표값 계약을 유지합니다. [호환 응답 정책](../history/policy/policy-normalization-unified-category-response-bridge.md)을 확인합니다.

## 로그 생성과 클릭 기록

[RecommendationLogService](../../backend/src/main/java/com/example/welfare/recommend/service/RecommendationLogService.java)는 두 경로에서 사용됩니다.

- 추천 생성 후 `refreshLogs`: 사용자별 기존 미클릭 로그를 삭제한 뒤 새 추천 로그를 저장합니다.
- 알림 준비 중 `NotificationRecommendationService.prepareDispatch`: 선택한 후보의 로그를 저장합니다. 외부 발송 성공이나 실제 화면 노출이 확인된 시점만의 기록은 아닙니다.

클릭은 `POST /api/policies/{id}/recommendation-click`에 JSON `{"logId": 123}`을 보내 기록합니다. 인증 사용자·정책·로그 소유 관계를 검사합니다. 정책 상세 GET이나 URL의 `log_id`/token으로 클릭 상태를 변경하지 않습니다.

미클릭 로그 삭제, 생성과 알림 준비 로그의 혼합 때문에 현재 테이블은 불변 노출 이력이 아닙니다. 로그 수를 그대로 발송 수·노출 수·CTR 분모로 쓰지 않습니다.

## 저장된 로그의 진단 쿼리

아래 PostgreSQL 조회는 **남아 있는 로그의 클릭 표시 비율**을 보여 줍니다. 실제 노출 CTR, 알림 발송 성공률, 가중치 개선 효과를 증명하지 않습니다. 로그 생성 경로의 혼합과 미클릭 삭제, 테스트 계정 혼입의 영향을 따로 검토해야 합니다.

```sql
SELECT is_fallback,
       COUNT(*) AS log_rows,
       COUNT(*) FILTER (WHERE is_clicked) AS clicked_rows,
       ROUND(100.0 * COUNT(*) FILTER (WHERE is_clicked)
             / NULLIF(COUNT(*), 0), 1) AS recorded_click_pct
FROM recommendation_logs
GROUP BY is_fallback;

SELECT rule_weight_used, ai_weight_used,
       COUNT(*) AS log_rows,
       COUNT(*) FILTER (WHERE is_clicked) AS clicked_rows,
       ROUND(100.0 * COUNT(*) FILTER (WHERE is_clicked)
             / NULLIF(COUNT(*), 0), 1) AS recorded_click_pct
FROM recommendation_logs
GROUP BY rule_weight_used, ai_weight_used;
```

실행 위치가 로컬이어도 원격 DB를 조회할 수 있습니다. [환경 안내](../core/environment-guide.md)에서 앱/DB와 점검 계정의 권한을 확인합니다.

## 현재 알림 슬롯 배치

[NotificationRecommendationService](../../backend/src/main/java/com/example/welfare/notification/service/NotificationRecommendationService.java)가 저장된 추천 후보를 읽고 [NotificationSlotSelector](../../backend/src/main/java/com/example/welfare/notification/service/NotificationSlotSelector.java)로 최대 3건을 고릅니다.

- A 슬롯은 사용자 최소 final score를 통과한 상위 개인화 추천 2건입니다.
- B 슬롯은 수집 후 24시간 이내이고 `rule_base_score >= 0.5`인 정책 후보입니다.
- 정책 ID 중복을 제거하고 B가 부족하면 A 후보로 채웁니다. 유효한 후보가 부족하면 3건보다 적을 수 있습니다.

이는 추천 전체 저장 순서와 별개인 알림 준비 규칙입니다.

## 후속 변경 후보

군집 분할, Batch API, clipping, 알림 채널/슬롯 확장은 과거의 확장 후보입니다. 존재하지 않는 `BatchAiGateway`나 batch scheduler를 현재 구성으로 가정하지 않습니다. 새 도입은 원문·사용자 데이터 보존·재현 가능한 평가를 먼저 확인하고 기대 효과와 실패 경계를 정한 별도 작업으로 진행합니다.
