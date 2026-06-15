# similar-users-viewed audit runbook

## 목적

`GET /api/recommendations/similar-users-viewed` 배포 뒤 결과가 비는 이유를 read-only aggregate로 분해합니다.
이 runbook은 기능 튜닝을 바로 열기 위한 문서가 아니라, 표본이 부족한지, 유사 사용자 gate가 빡빡한지,
정책 후보가 필터에서 사라지는지, 중복 제외 때문에 비는지를 먼저 구분하기 위한 운영 관찰 기준입니다.

## 실행

로컬 Docker DB:

```bash
KEEP_ARTIFACTS=true bash deploy/smoke/run-local-similar-users-viewed-audit.sh
```

서버/RDS:

```bash
ENV_FILE=.env.production \
SMOKE_DB_MODE=postgres \
KEEP_ARTIFACTS=true \
bash deploy/smoke/run-local-similar-users-viewed-audit.sh
```

기본 파라미터:

- `SIMILAR_USERS_VIEWED_WINDOW_DAYS=30`
- `SIMILAR_USERS_VIEWED_MIN_SIMILAR_USERS=2`
- `SIMILAR_USERS_VIEWED_MIN_SIMILARITY_SCORE=3.0`
- `SIMILAR_USERS_VIEWED_TARGET_USER_LIMIT=50`

## 산출물

`KEEP_ARTIFACTS=true` 기준:

- `tmp/similar-users-viewed-audit/latest-similar-users-viewed-summary.txt`
- `tmp/similar-users-viewed-audit/latest-similar-users-viewed-summary.json`
- `tmp/similar-users-viewed-audit/latest-similar-users-viewed-note.md`
- `tmp/similar-users-viewed-audit/latest/`

stdout도 `key=value` summary를 그대로 출력합니다.

## 주요 필드

- `active_real_users`: 활성 `REAL_USER` 수
- `profiles_with_similarity_signals`: 유사도 계산에 쓸 프로필 신호가 있는 사용자 수
- `recent_view_users_window`: 최근 조회 window 안에서 정책을 본 `REAL_USER` 수
- `sampled_target_users`: audit 대상 target 사용자 수
- `sampled_targets_with_eligible_similar_users`: 유사도 threshold를 넘는 peer가 있는 target 수
- `candidate_policy_groups_before_exclusions`: 정책 상태/청년/나이/소득 필터까지 통과한 후보 group 수
- `candidate_policy_groups_min_sample`: 정책별 최소 유사 사용자 수 gate를 통과한 후보 group 수
- `excluded_by_own_recent_view_groups`: 현재 사용자 최근 조회 중복으로 제외될 후보 group 수
- `excluded_by_latest_recommendation_groups`: 최신 추천 batch 중복으로 제외될 후보 group 수
- `result_policy_groups`: 실제 endpoint 결과 후보가 될 수 있는 group 수
- `expected_index_count`: read path index 5개 존재 여부

이 audit은 user key, email, 개별 조회 row를 출력하지 않습니다.

## decision_class 해석

- `MIGRATION_INDEX_MISSING`
  - migration 적용 상태부터 확인합니다.
- `REAL_USER_SAMPLE_THIN`
  - `REAL_USER` 자체가 유사 사용자 표본을 만들 만큼 충분하지 않습니다.
- `PROFILE_SIGNAL_THIN`
  - 프로필 신호가 부족합니다. UI 입력 유도 또는 프로필 sync를 먼저 봅니다.
- `RECENT_VIEW_SAMPLE_THIN`
  - 최근 조회 표본이 부족합니다. 신규 기능 배포 직후 가장 흔한 정상 상태입니다.
- `SIMILAR_USER_SAMPLE_THIN`
  - 프로필 신호는 있으나 similarity threshold를 넘는 사용자 쌍이 부족합니다.
- `POLICY_CANDIDATE_EMPTY_AFTER_FILTERS`
  - 유사 사용자 조회는 있지만 정책 상태/청년/나이/소득 필터 뒤 후보가 없습니다.
- `MIN_SIMILAR_USERS_GATE_EMPTY`
  - 정책별 최소 표본 `2명` gate에서 비었습니다.
- `DUPLICATE_OR_LATEST_BATCH_EXCLUSION_EMPTY`
  - 후보는 있으나 현재 사용자 최근 조회 또는 최신 추천 batch 중복 제외 뒤 비었습니다.
- `OBSERVE_NONEMPTY_READY`
  - 결과 후보가 생성될 수 있는 표본입니다. empty rate와 클릭/북마크 반응을 관찰합니다.

## 운영 판단

초기 배포 직후 `RECENT_VIEW_SAMPLE_THIN` 또는 `REAL_USER_SAMPLE_THIN` 은 정상 관찰 상태입니다.
이 경우 `minSimilarUsers`, similarity threshold, 30일 window를 바로 튜닝하지 않습니다.

튜닝 검토는 아래가 동시에 보일 때만 엽니다.

1. `active_real_users` 와 `recent_view_users_window` 가 충분히 증가했다.
2. `expected_index_count=5` 이다.
3. smoke는 계속 통과한다.
4. audit이 반복적으로 `SIMILAR_USER_SAMPLE_THIN`, `MIN_SIMILAR_USERS_GATE_EMPTY`, 또는 `DUPLICATE_OR_LATEST_BATCH_EXCLUSION_EMPTY` 를 낸다.

metrics actuator 노출은 별도 운영 정책 작업입니다.
이 audit은 actuator metrics가 닫힌 환경에서도 DB read-only로 empty 원인을 분해하기 위한 보조 경로입니다.
