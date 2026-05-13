# 추천 / Replay 기록 템플릿

문서군 진입점: [recommendation-docs-index.md](./recommendation-docs-index.md)

관련 문서:

- [recommendation-current-state.md](./recommendation-current-state.md)
- [recommendation-operation-checklist.md](./recommendation-operation-checklist.md)

## 사용법

추천 확인이나 replay 결과를 남길 때 복사해서 씁니다.

권장 파일명 예시:

- `recommendation-replay-<date>-rule-only.md`
- `recommendation-replay-<date>-real-openai.md`

---

## 1. 실행 정보

- date/time:
- environment:
- mode:
  - `rule-only`
  - `real-openai`
- command/script:

## 2. 전제 상태

- `welfare_services` count:
- `service_taxonomies` count:
- `service_facts` count:
- target rows count:
- collect/snapshot 상태:

## 3. 결과 요약

- `A_top10_target`:
- `B_top10_target`:
- `A_target_total`:
- `B_target_total`:
- artifact dir:

## 4. trace 정보

- request trace present:
- response trace present:
- `A_fp`:
- `B_fp`:

## 5. 판정

- `success`
- `diagnostic drift only`
- `needs follow-up`
- `failed`

## 6. 문제/경고

-
-
-

## 7. 해석

- rule-only baseline 기준 문제인지:
- real-openai variability 범주인지:
- sidecar/projection 문제인지:
- collect snapshot 문제인지:

## 8. 다음 액션

1.
2.
3.
