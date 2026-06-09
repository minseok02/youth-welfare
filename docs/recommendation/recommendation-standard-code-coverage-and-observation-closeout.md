# 표준코드 입력률 / 관측 closeout

문서군 진입점: [recommendation-docs-index.md](./recommendation-docs-index.md)

## 목적

이 문서는 `2026-06-03` 라운드에서 진행한 아래 세 축을 한 장으로 묶습니다.

- 사용자 표준코드 입력 유도 UX 확장
- recommendation/ops observation에 표준코드 adoption 지표 추가
- 서버에서 바로 돌릴 수 있는 nightly wrapper 정리

즉 이번 문서는 “표준코드 기반 추천 연결 이후, 실제 입력률과 운영 관측을 어떻게 닫았는가”를 빠르게 다시 읽는 closeout 메모입니다.

## 이번 라운드에서 한 일

### 1. 정책 목록 화면까지 표준코드 입력 유도 확장

기존에는 아래 화면에만 표준코드 유도가 있었습니다.

- `MainPage`
- `MyPage`
- `SignupPage`

이번에는 [PoliciesPage.jsx](../../frontend/src/pages/PoliciesPage.jsx)에 로그인 사용자용 `주거·복지 표준코드` 유도 배너를 추가했습니다.

핵심 동작:

- `GET /api/users/me` 로 현재 사용자 프로필을 읽음
- `houseTenureCode / housingTypeCode / basicLivingRecipientTypeCode / disabilityGradeCode`
  4개 중 비어 있는 항목을 계산
- `주거·복지 표준코드 x/4개 입력됨` 상태를 정책 목록 상단에서 바로 노출
- CTA는 `/mypage?tab=0` 으로 바로 이동

의도:

- 추천 홈에서만 보이는 유도보다, 실제 정책을 뒤지는 사용자가 바로 보게 하는 편이 입력 전환 가능성이 더 높다
- “정책은 보이는데 자격조건 매칭은 아직 덜 정확할 수 있다”는 메시지를 검색/필터 흐름 안에서 직접 보여 주려는 목적이다

### 2. recommendation observation에 adoption audit 추가

기존 observation은 표준코드 효과를 아래 두 artifact로 확인했습니다.

- `housing standard code effect`
- `welfare standard code matrix`

이 둘은 “표준코드가 들어갔을 때 점수가 움직이는가”는 보여 주지만,
“실제로 표준코드가 채워진 사용자가 recommendation latest batch 안에 얼마나 있는가”는 보여 주지 못했습니다.

그래서 새 audit [run-local-recommendation-standard-code-adoption-audit.sh](../../deploy/smoke/run-local-recommendation-standard-code-adoption-audit.sh)를 추가했습니다.

이 audit는 latest `user_recommendations` batch 기준으로 아래를 계산합니다.

- latest batch user 수
- 표준코드 1개 이상 입력 사용자 수
- 표준코드 4개 모두 입력 사용자 수
- 표준코드 전부 비어 있는 사용자 수
- 위 사용자 비율
- `filled_count > 0` 사용자와 `filled_count = 0` 사용자의 평균 `final_score`

이 결과는 [run-local-recommendation-observation-suite.sh](../../deploy/smoke/run-local-recommendation-observation-suite.sh) 에 편입했고,
상위 [run-local-ops-observation-suite.sh](../../deploy/smoke/run-local-ops-observation-suite.sh) 까지 승격했습니다.

즉 이제 운영자가 summary/json 만 읽어도 아래를 동시에 봅니다.

- 표준코드가 추천 점수를 움직이는지
- 표준코드가 실제 latest recommendation 사용자군에 얼마나 퍼져 있는지

### 3. nightly wrapper 추가

서버/RDS 기준으로 표준코드 관측을 주기 실행할 수 있도록
[run-nightly-standard-code-observation.sh](../../deploy/smoke/run-nightly-standard-code-observation.sh) 를 추가했습니다.

기본 계약:

- `ENV_FILE=.env.production`
- `SMOKE_DB_MODE=postgres`
- `APP_BASE_URL=http://127.0.0.1:8082`
- `FRONTEND_E2E_MODE=deployed-origin`
- `FRONTEND_PUBLIC_BASE_URL=https://youthmoa.kr`

wrapper가 하는 일:

1. `run-local-ops-observation-suite.sh`
2. `run-local-current-priority-suite.sh`

를 순서대로 실행하고,

- `/var/log/youth-welfare/standard-code-observation/artifacts/<timestamp>/`
- `nightly-summary-YYYY-MM-DD.log`

에 compact handoff를 남깁니다.

즉 이 wrapper는 “표준코드 coverage / attention feed / recommendation standard-code observation”을 한 번에 다시 보는 nightly entrypoint입니다.

## 이번 라운드 산출물

코드:

- [PoliciesPage.jsx](../../frontend/src/pages/PoliciesPage.jsx)
- [run-local-recommendation-standard-code-adoption-audit.sh](../../deploy/smoke/run-local-recommendation-standard-code-adoption-audit.sh)
- [run-local-recommendation-observation-suite.sh](../../deploy/smoke/run-local-recommendation-observation-suite.sh)
- [run-local-ops-observation-suite.sh](../../deploy/smoke/run-local-ops-observation-suite.sh)
- [run-nightly-standard-code-observation.sh](../../deploy/smoke/run-nightly-standard-code-observation.sh)

문서 연결:

- [recommendation-observation-runbook.md](./recommendation-observation-runbook.md)
- [ops-baseline-runbook.md](../core/ops-baseline-runbook.md)
- [current-state.md](../current-state.md)
- [start.md](../start.md)

## 현재 읽는 법

사람이 먼저 볼 때:

1. `tmp/recommendation-observation/latest-recommendation-observation-note.md`
2. `tmp/ops-observation/latest-ops-observation-note.md`
3. 필요하면 `recommendation-standard-code-adoption.out`

자동 파싱/운영 handoff:

1. `tmp/recommendation-observation/latest-recommendation-observation-summary.txt`
2. `tmp/ops-observation/latest-ops-observation-summary.txt`
3. 같은 이름의 `json`

## 최신 server/RDS 기준 핵심 수치

2026-06-09 server/RDS nightly handoff 재실행 뒤 최종 검증 값은 아래입니다.

- `recommendation_standard_code_adoption_status=ok`
- `user_profile_standard_code_total_users=476`
- `user_profile_standard_code_users_with_any_standard_code=187`
- `user_profile_standard_code_users_missing_all_standard_codes=289`
- `user_profile_standard_code_safe_reconcile_candidate_rows=0`
- `user_profile_standard_code_conflicting_value_gap_rows=0`
- `recommendation_standard_code_adoption_latest_batch_user_count=261`
- `recommendation_standard_code_adoption_latest_batch_users_with_any_standard_code=187`
- `recommendation_standard_code_adoption_latest_batch_users_missing_all_standard_codes=74`
- `recommendation_standard_code_adoption_latest_batch_users_with_any_standard_code_share_pct=71.65`
- `recommendation_standard_code_adoption_latest_batch_users_missing_all_standard_codes_share_pct=28.35`

해석:

- 자동 reconcile 후보와 conflict gap은 없으므로 DB 보정 작업을 열지 않는다.
- latest batch 안의 표준코드 보유 비중은 올라왔지만, 여전히 `74명` 은 전부 비어 있다.
- 즉 현재 병목은 추천 점수 로직보다 입력률과 real-user sample이다.

## 검증

이번 라운드에서 직접 확인한 명령:

```bash
cd frontend && npm run build

bash -n \
  deploy/smoke/run-local-recommendation-standard-code-adoption-audit.sh \
  deploy/smoke/run-local-recommendation-observation-suite.sh \
  deploy/smoke/run-local-ops-observation-suite.sh \
  deploy/smoke/run-nightly-standard-code-observation.sh

bash deploy/smoke/run-local-recommendation-standard-code-adoption-audit.sh

APP_BASE_URL='http://127.0.0.1:8082' \
bash deploy/smoke/run-local-recommendation-observation-suite.sh

APP_BASE_URL='http://127.0.0.1:8082' \
bash deploy/smoke/run-local-ops-observation-suite.sh

git diff --check
```

## 남은 다음 일

이번 라운드로 “표준코드 입력률을 어디서 보고 어떻게 관측하는가”는 닫혔습니다.
남은 핵심은 입력률 자체를 올리는 것입니다.

실질 우선순위:

1. 추천 새로고침 직전 CTA 보강
2. 저장 후 리마인드/완성도 메시지 보강
3. 로그인 직후/알림 기준의 표준코드 입력 유도 실험

즉 다음 단계는 더 많은 관측 helper를 추가하는 것이 아니라,
실제 사용자 입력률을 움직이는 UX 실험입니다.
