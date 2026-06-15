# 비슷한 사용자 조회 기반 보조 추천

## 목적

`GET /api/recommendations/similar-users-viewed` 는 메인 개인화 추천을 대체하지 않는 보조 탐색 API입니다.
현재 사용자와 프로필이 가까운 `REAL_USER` 사용자들이 최근 확인한 정책을 집계해 반환합니다.

## 현재 계약

- 응답은 개인 사용자나 개별 행동을 노출하지 않고 정책 카드와 짧은 reason label만 반환합니다.
- `EXAMPLE_SMOKE`, `BOUNDED_LOCAL`, `LOCAL_REAL_NON_EXAMPLE_SEED` 계정은 후보 사용자에서 제외합니다.
- 유사 사용자는 지역, 나이대, 소득 근접, 관심분야, 대상유형, 우선순위 코드 overlap으로 점수를 계산합니다.
- 최근 조회 window는 30일입니다.
- 정책별 유사 사용자 표본이 2명 미만이면 해당 정책은 노출하지 않습니다.
- 현재 사용자가 이미 최근 본 정책은 제외합니다.
- 현재 사용자의 최신 `user_recommendations` batch에 이미 들어간 정책도 제외합니다.
- 노출 정책은 `ACTIVE/UPCOMING`, `search_youth_relevant=true`, 현재 사용자 나이/소득 조건을 다시 통과해야 합니다.
- 표본이나 프로필 신호가 부족하면 빈 목록을 반환하고 프론트는 섹션을 숨깁니다.

## 구현 위치

- API: `RecommendationController#getSimilarUsersViewedPolicies`
- service: `SimilarUsersViewedPolicyReadService`
- SQL read model: `SimilarUsersViewedPolicyReadRepositoryImpl`
- frontend: `MainPage` 의 `SimilarUsersViewedRail`

## 관측 지표

Micrometer 지표는 아래 이름으로 기록합니다.

- `recommendation.similar_users_viewed.requests`
  - tag: `outcome=returned|empty|no_signal`
- `recommendation.similar_users_viewed.candidates`
  - SQL 후보 정책 수
- `recommendation.similar_users_viewed.results`
  - 최종 응답 정책 수

초기 운영 판단은 `empty` 비율, 후보 대비 결과 수, 클릭/북마크 후속 전환을 같이 봅니다.
이 지표는 메인 추천 review gate의 source of truth가 아니라 보조 섹션의 노출 가능성과 실효성을 보는 용도입니다.

## 성능 기준

`V2026_06_16_01__add_similar_users_viewed_indexes.sql` 에서 아래 read path 인덱스를 추가합니다.

- `idx_users_real_active_user_key`
- `idx_rpv_last_viewed_user_service`
- `idx_ua_type_value_user_key`
- `idx_up_option_user_key`
- `idx_ur_user_key_recommended_service`

로컬 PostgreSQL volume에서 대표 쿼리를 `EXPLAIN (ANALYZE, BUFFERS)` 로 확인했을 때 실행 시간은 약 `0.29ms` 였고,
`users` 는 `idx_users_real_active_user_key`, 최신 추천 제외는 `idx_ur_user_key_recommended_service` 를 탔습니다.
`recent_policy_views` 는 로컬 표본이 작으면 seq scan이 더 싸게 나올 수 있으므로, 운영 판단은 row 수와 실행 계획을 같이 봅니다.

## Smoke

로컬 런타임 계약은 아래 스크립트로 확인합니다.

```bash
APP_BASE_URL=http://127.0.0.1:8082 \
APP_HEALTH_URL=http://127.0.0.1:8082/actuator/health \
deploy/smoke/run-local-similar-users-viewed-smoke.sh
```

이 smoke는 `signup -> login -> /api/recommendations/similar-users-viewed` 를 확인합니다.
로컬 표본이 얇으면 결과 수 `0` 도 정상입니다. 단, 응답은 `success=true`, `data=[]` 또는 `policy/reasonLabel` 계약을 지켜야 합니다.

## Read-only Audit

운영에서 `result_count=0` 이 반복될 때는 바로 threshold를 낮추지 말고 아래 aggregate audit을 먼저 봅니다.

```bash
ENV_FILE=.env.production \
SMOKE_DB_MODE=postgres \
KEEP_ARTIFACTS=true \
bash deploy/smoke/run-local-similar-users-viewed-audit.sh
```

해석 기준은 [recommendation-similar-users-viewed-audit-runbook.md](./recommendation-similar-users-viewed-audit-runbook.md) 를 봅니다.
이 audit은 user key, email, 개별 조회 row를 출력하지 않습니다.

## 운영 해석

이 기능은 클릭/조회 기반 탐색 힌트입니다.
조회 이력은 정책 자격 충족이나 신청 의사를 직접 의미하지 않으므로, 메인 추천 점수나 review gate 판단의 source of truth로 쓰지 않습니다.
실사용자 표본이 얇은 동안에는 결과가 비거나 소수 사용자 행동에 흔들릴 수 있습니다.

### 2026-06-15 운영 관찰 기록

서버/RDS 기준 `ba651649d4dfcdbec6bad45dc141eb35f50d3d57` audit 결과는 정상 관찰 상태입니다.

- `expected_index_count=5` 로 read path 인덱스 5개가 모두 적용되어 있습니다.
- `similar_users_viewed_audit=passed` 입니다.
- `active_real_users=2` 이므로 현재 계약인 `minSimilarUsers=2` 를 구조적으로 만족하기 어렵습니다.
- `candidate_policy_groups_before_exclusions=22` 이므로 후보 생성 경로는 살아 있습니다.
- `candidate_policy_groups_min_sample=0`, `result_policy_groups=0` 은 `REAL_USER` 표본 부족에 따른 정상 결과로 봅니다.
- `decision_class=REAL_USER_SAMPLE_THIN` 이며, similar-users-viewed는 관찰 상태를 유지합니다.
- `minSimilarUsers`, similarity threshold, 30일 window, 기능 로직은 변경하지 않습니다.
- actuator metrics 노출은 별도 운영 정책 작업으로 유지합니다.

다음 audit은 `REAL_USER` 가 최소 3명 이상, 가능하면 최근 조회가 있는 `REAL_USER` 가 5~10명 이상 쌓인 뒤 다시 실행합니다.

## 확장 포인트

1. 유사 사용자 최소 표본을 운영 설정으로 분리
2. `bookmarked` 또는 `clicked recommendation` 신호를 별도 가중치로 추가
3. 같은 정책 반복 노출을 줄이는 source/category diversity cap 추가
4. admin dashboard에 표본 수, empty rate, click-through 관찰 지표 추가
