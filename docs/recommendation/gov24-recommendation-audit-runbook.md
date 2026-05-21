# Gov24 Recommendation Audit Runbook

문서군 진입점: [recommendation-docs-index.md](./recommendation-docs-index.md)

## 목적

이 문서는 Gov24 추천 약세를 볼 때

- 어떤 순서로 audit을 돌릴지
- 어떤 smoke가 어떤 질문에 답하는지
- 어디까지가 구조 문제이고 어디부터가 사용자 맥락 문제인지

를 빠르게 정리한 runbook 입니다.

## 기본 원칙

1. `latest batch` 와 `fresh batch` 를 섞어 읽지 않습니다.
2. `ai_score=0`, `ai_score is null`, `ai_status` 를 같은 의미로 취급하지 않습니다.
3. source 전체 일반화보다 **개별 정책군 + 사용자 맥락** 해석을 우선합니다.

빠른 local baseline 재확인용 wrapper:

- `bash deploy/smoke/run-local-gov24-recommendation-suite.sh`

이 wrapper는 아래 4개만 순차 실행합니다.

- `run-local-gov24-recommend-surface-audit.sh`
- `run-local-gov24-recommend-score-audit.sh`
- `run-local-gov24-ai-status-audit.sh`
- `run-local-gov24-signal-suite.sh`

## 1. Surface 먼저

먼저 Gov24가 추천에 아예 안 뜨는지, 아니면 상위권에서만 약한지 봅니다.

실행:

- `bash deploy/smoke/run-local-gov24-recommend-surface-audit.sh`

확인:

- `top1/top2/top3/top5/top10 Gov24 share`
- `rank_source_distribution`
- `gov24_top_services`

현재 해석 기준:

- Gov24가 `top10` 에 충분히 보이면 visibility 부재는 아닙니다.
- `top1/top2` 만 약하면 다음은 score/ai_status 쪽입니다.

## 2. Score summary

Gov24가 상위권에서 왜 약한지 평균 점수 분포를 먼저 봅니다.

실행:

- `bash deploy/smoke/run-local-gov24-recommend-score-audit.sh`

확인:

- `top10_source_score_summary`
- `top2_source_score_summary`
- `rank_source_score_summary`

주의:

- `avg ai=0.00` 은 실제 0점일 수도 있고 `NULL 평균` 이 0처럼 보인 것일 수도 있습니다.
- 그래서 score summary 다음에는 반드시 `zero-ai/null-ai/ai_status` 축으로 이어집니다.

## 3. zero-ai / null-ai / ai_status 분리

### zero-ai

- `bash deploy/smoke/run-local-gov24-zero-ai-audit.sh`

질문:

- 실제로 `ai_score=0` 인 Gov24가 top2에 있는가

### null-ai

- `bash deploy/smoke/run-local-gov24-null-ai-audit.sh`

질문:

- `ai_score is null` 인 Gov24가 top2에도 있는가

### null-ai cause

- `bash deploy/smoke/run-local-gov24-null-ai-cause-audit.sh`

질문:

- `AI_TOP_N=15` 밖이라 원래 AI를 안 받은 것인가
- top15 안인데 `ai_score/ai_reason` 이 비어 저장된 것인가

### ai_status

- `bash deploy/smoke/run-local-gov24-ai-status-audit.sh`

질문:

- Gov24가 실제로 `NOT_REQUESTED / SCORED / PARTIAL_MISSING / CALL_FAILED / RULE_ONLY`
  중 어디에 몰리는가

주의:

- `e7e591a` 이전 old batch는 migration backfill artifact 때문에
  `ai_status=NOT_REQUESTED` 해석을 그대로 믿으면 안 됩니다.

## 4. fresh batch로 좁히기

old batch/latest-wide 해석이 섞이지 않게, fresh user + personal refresh 기준으로 봅니다.

실행:

- `TARGET_USER_KEY=... bash deploy/smoke/run-local-gov24-fresh-batch-audit.sh`
- `TARGET_USER_KEY=... bash deploy/smoke/run-local-gov24-fresh-score-breakdown-audit.sh`
- `TARGET_USER_KEY=... bash deploy/smoke/run-local-gov24-fresh-upstream-audit.sh`

질문:

- fresh batch에서 Gov24가 top2에 아예 없는가
- `rule_rank<=15` 인데도 약한가
- `base_blend` 부터 낮은가
- rival과 비교할 때 입력 신호가 무엇이 다른가

## 5. bounded signal smoke

source 전체 일반화 전에, 특정 맥락을 맞춰주면 Gov24가 실제로 상위권으로 올라오는지 봅니다.

기본 wrapper:

- `bash deploy/smoke/run-local-gov24-signal-suite.sh`

개별 실행:

- `bash deploy/smoke/run-local-gov24-housing-signal-smoke.sh`
- `bash deploy/smoke/run-local-gov24-education-signal-smoke.sh`

질문:

- `주거` 관심 + `HOUSING` priority를 주면 Gov24 주거 서비스가 top2까지 올라오는가
- `교육·직업훈련` 관심 + `EDUCATION` priority + `경기도/안산시` 맥락을 주면 교육 Gov24가 rank1/top2까지 올라오는가

현재 local bounded baseline:

- `housing`
  - `gov24_top2_rows=1`
  - `5728 주택금융공사 월세자금보증`
  - `rank2`, `rule=54`, `ai=70`, `final=0.63261`
- `education`
  - `gov24_top2_rows=1`
  - `16490 국가장학금 Ⅰ유형 (학생직접지원형)`
  - `rank1`, `rule=27`, `ai=80`, `final=1.01376`

읽는 법:

- 둘 다 green이면 Gov24 source 전체 억압으로 일반화하면 안 됩니다.
- 특정 bounded smoke만 약하면 그 정책군 규칙을 더 봅니다.

## 6. 현재 결론

`2026-05-17` 기준 Gov24 추천 추적의 practical 결론은 이렇습니다.

1. Gov24가 추천에 아예 안 뜨는 문제는 아닙니다.
2. fresh batch 기준으로는 `AI_TOP_N 밖`, `PARTIAL_MISSING` 이 주원인이 아닐 수 있습니다.
3. `4689` 류는 rule-side 약세로 읽는 편이 맞고, 현재 education bounded smoke는 `16490` 같은 교육 Gov24가 충분히 top2 안으로 들어옵니다. 예전 `7193` 류 해석은 historical dataset 기준으로만 남겨 둡니다.
4. bounded signal smoke까지 보면 Gov24 source 전체를 구조적으로 눌려 있다고 보긴 어렵습니다.
5. 다음 reopen이 필요하면 source 일반론보다 **주거/월세보증**, **지역 장학금**, **창업/소상공인** 같은 정책군 단위로 보는 편이 맞습니다.
