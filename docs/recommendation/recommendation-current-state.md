# 추천 현재 동작과 확인 과제

문서군 진입점: [recommendation-docs-index.md](recommendation-docs-index.md)

## 적용 범위

이 문서는 구현 경로와 검토할 문제를 설명합니다. 로컬 seed·로컬 앱과 RDS·운영 사용자 관측은 [환경 안내](../core/environment-guide.md)에 따라 구분합니다. 과거 `KEEP_OBSERVING / reopen 금지`는 당시 운영 튜닝 방침이며 현재 승인된 리팩토링 작업을 금지하는 규칙이 아닙니다.

## 구현 경로

- 생성·조회·북마크의 API 경계는 `RecommendationGenerationService`, `RecommendationAccessService`, `RecommendationBookmarkCommandService`로 나뉩니다.
- `RetrievalService`는 후보 조회·후처리, `RuleScoringService`와 `AiScoringService`는 점수, `ReRankingService`는 최종 정렬을 담당합니다.
- 추천과 챗봇의 후보 조회는 [PolicyExplorationService](../../backend/src/main/java/com/example/welfare/policy/service/PolicyExplorationService.java)를 공유하지만 후처리와 출력 목적은 다릅니다.
- 개인 refresh 재사용 마커는 Redis, 저장 추천 결과는 DB를 사용합니다. 이를 전체 추천 payload Redis 캐시나 학습 모델로 설명하지 않습니다.
- 사용자에게 노출하는 AI 메모는 [추천 메모 계약](recommendation-ai-reason-memo-contract.md)을 따릅니다.
- [비슷한 사용자 조회](recommendation-similar-users-viewed-policy.md)는 메인 개인화 추천을 대체하지 않는 보조 API입니다.

단계별 기존 설명은 [recommendation-pipeline.md](recommendation-pipeline.md)를 참고하되, SQL·가중치·소득 의미의 정합성은 아래 확인 과제와 함께 봅니다.

## 보존해야 할 사용자 계약과 현재 위험

| 항목 | 기대하는 계약 | 코드 대조에서 확인할 부분 |
| --- | --- | --- |
| 북마크 | 추천 후보에서 빠져도 사용자가 저장한 정책은 유지 | [저장 서비스](../../backend/src/main/java/com/example/welfare/recommend/service/RecommendationPersistenceService.java)와 전체 교체 delete/insert 경계 |
| 행동 기록 | 생성·노출·클릭과 실패를 구분한 평가 근거 | [로그 서비스](../../backend/src/main/java/com/example/welfare/recommend/service/RecommendationLogService.java)의 미클릭 정리와 CTR 분모 |
| 가중치 | 품질 근거와 평가 결과로 판단 | [ScoreWeightService](../../backend/src/main/java/com/example/welfare/recommend/service/ScoreWeightService.java)의 전체 로그 건수 기반 단계 |
| 자격조건 | 값의 단위와 출처·미입력을 보존 | 소득 구간과 정책 금액·비율 비교, 미입력 기본값, 지역 fallback |
| 품질 평가 | 같은 corpus·프로필·조건의 재현 가능한 비교 | 고정 카테고리 hit, 합성 사용자 표본, 실제 사용자 행동의 구분 |

이 표는 수정 완료 보고가 아닙니다. 회귀 테스트와 설계 결정 없이 기존 동작을 정상 계약으로 확정하지 않습니다.

## 진단과 운영 조치

- 상태 관측: [observation runbook](recommendation-observation-runbook.md).
- 후보·점수·저장 단계 원인 조사: [문서 목차](recommendation-docs-index.md)의 증상별 진단.
- 지역 불일치: [repair runbook](recommendation-region-mismatch-repair-runbook.md). audit와 실제 refresh를 구분하고 북마크·행동 기록에 미치는 영향을 먼저 확인합니다.
- promotion approval smoke는 실제 승인 기록을 쓰고 지우므로 조회용 audit와 분리합니다.
- 수집·실제 AI 재평가·메일 발송을 동반하는 검사는 대상 환경과 비용을 확인한 뒤 별도 범위로 실행합니다.

## 실제 사용자와 테스트 표본

`account_origin=REAL_USER`라는 DB 값만으로 실제 사람의 운영 표본임을 증명할 수 없습니다. [cohort manifest](recommendation-real-user-cohort-library-manifest.md)의 generic-domain 계정은 인위적으로 만든 테스트 계정입니다. [운영 baseline 절차](recommendation-real-user-baseline-runbook.md)의 실제 사용자 관측과 분리하고, 코드의 현재 분류값과 평가에 사용할 표본의 성격을 함께 표시합니다.

## 과거 관측과 실험

기존 local/server/RDS 측정값, review gate 단계, closeout 결론은 [정리 전 추천 기록](../history/recommendation/recommendation-state-before-doc-cleanup.md)에 보존합니다. 그 기록의 상태값과 표본 수를 현재 운영 상태나 새로운 자동 승인 조건으로 사용하지 않습니다.
