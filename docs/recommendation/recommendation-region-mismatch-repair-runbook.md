# recommendation region mismatch repair runbook

문서군 진입점: [recommendation-docs-index.md](./recommendation-docs-index.md)

## 목적

현재 추천 쿼리 기준으로는 다른 지역 `GOV24` 로컬 정책이 새로 추천되지 않더라도,
예전 `user_recommendations` 저장 batch 안에는 region mismatch row가 남아 있을 수 있습니다.

이 runbook은 그 잔량을 아래 순서로 다룹니다.

1. latest saved batch 안의 `REGION_MISMATCH` 잔량 audit
2. affected user를 `personal=true refresh` 로 재계산
3. re-audit 으로 감소 여부 확인

## 언제 쓴다

- 사용자가 “지역이 다른 정책이 추천된다”고 제보했을 때
- recommendation region fallback SQL을 조정한 뒤 old saved batch 영향이 남아 있는지 확인할 때
- 운영/RDS에서 stale `GOV24` 로컬 추천을 청소할 때

## 핵심 해석

- current query 재계산에서는 이미 빠지는데 saved batch에서만 보이면 `stale saved recommendation` 입니다.
- 최근 확인 기준 mismatch의 대부분은 `GOV24` 옛 batch였습니다.
- `BOKJIRO_LOCAL` 현재 쿼리 오추천과 saved stale batch는 구분해서 봐야 합니다.

## 1. audit

```bash
bash deploy/smoke/run-local-recommendation-region-mismatch-audit.sh
```

server/RDS:

```bash
ENV_FILE=.env.production SMOKE_DB_MODE=postgres APP_BASE_URL='http://127.0.0.1:8082' \
bash deploy/smoke/run-local-recommendation-region-mismatch-audit.sh
```

주요 출력:

- `affected_users`
- `mismatch_rows`
- `gov24_mismatch_rows`
- `bokjiro_local_mismatch_rows`
- `detail_out`

현재 closeout 시점 local 수치:

- `affected_users=454`
- `mismatch_rows=587`
- `gov24_mismatch_rows=587`
- `bokjiro_local_mismatch_rows=0`

즉 남은 잔량은 사실상 old `GOV24` saved batch 청소 문제로 봅니다.

## 2. dry-run repair target 확인

```bash
USER_LIMIT=25 DRY_RUN=true \
bash deploy/smoke/run-local-recommendation-region-mismatch-repair.sh
```

server/RDS:

```bash
ENV_FILE=.env.production SMOKE_DB_MODE=postgres APP_BASE_URL='http://127.0.0.1:8082' \
USER_LIMIT=25 DRY_RUN=true \
bash deploy/smoke/run-local-recommendation-region-mismatch-repair.sh
```

주요 출력:

- `target_user_count`
- `targets_out`

## 3. bounded repair 실행

한 번에 전체를 돌리기보다 bounded batch로 나눠 태우는 편이 안전합니다.

```bash
USER_LIMIT=25 DRY_RUN=false KEEP_ARTIFACTS=true \
bash deploy/smoke/run-local-recommendation-region-mismatch-repair.sh
```

server/RDS:

```bash
ENV_FILE=.env.production SMOKE_DB_MODE=postgres APP_BASE_URL='http://127.0.0.1:8082' \
USER_LIMIT=25 DRY_RUN=false KEEP_ARTIFACTS=true \
bash deploy/smoke/run-local-recommendation-region-mismatch-repair.sh
```

권장:

- 먼저 `25` 또는 `50`
- 응답 속도가 안정적이면 `100`
- 장시간 interactive 실행은 피함

주요 출력:

- `refresh_success_count`
- `refresh_failure_count`
- `targets_out`

## 4. re-audit

```bash
bash deploy/smoke/run-local-recommendation-region-mismatch-audit.sh
```

감소 여부를 바로 봅니다.

closeout 작업 중 local partial repair 결과:

- before: `affected_users=481`, `mismatch_rows=713`
- after: `affected_users=454`, `mismatch_rows=587`

즉 current query 버그가 아니라 stale saved batch 잔량이 실제로 줄어드는 경로가 확인됐습니다.

## 권장 운영 순서

1. `audit`
2. `repair` 를 `25~100` 명 단위로 실행
3. `re-audit`
4. `mismatch_rows` 가 충분히 줄 때까지 반복

## 산출물

- audit
  - `tmp/recommendation-region-mismatch-audit/*/recommendation-region-mismatch-summary.txt`
  - `tmp/recommendation-region-mismatch-audit/*/recommendation-region-mismatch-samples.tsv`
- repair
  - `tmp/recommendation-region-mismatch-repair/*/recommendation-region-mismatch-repair-summary.txt`
  - `tmp/recommendation-region-mismatch-repair/*/target-users.tsv`

## 결론

현재 recommendation region 오추천의 남은 주된 형태는
`현재 쿼리가 잘못 추천하는 문제`보다 `과거 saved batch 정리 문제`에 가깝습니다.

따라서 이 lane의 기본 대응은
`SQL 수정 -> stale batch audit -> bounded refresh repair -> re-audit`
순서로 고정합니다.
