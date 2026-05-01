# 수집 실행/장애 기록 템플릿

관련 문서:

- [collect-current-state.md](./collect-current-state.md)
- [collect-operation-checklist.md](./collect-operation-checklist.md)

## 사용법

수집 실행 결과나 장애 상황을 남길 때 복사해서 씁니다.

권장 파일명 예시:

- `collect-run-<date>-<source>.md`
- `collect-incident-<date>-<source>.md`

---

## 1. 실행 정보

- date/time:
- environment:
- trigger:
  - `manual`
  - `scheduled`
- endpoint/job:
- source:

## 2. 실행 목적

- snapshot refresh
- detail coverage 확장
- sidecar backfill
- replay/downstream 검증
- 기타:

## 3. 응답 요약

- http status:
- app response summary:
- app log 핵심:

## 4. `api_sync_logs` 요약

- status:
- requested_count:
- saved_count:
- skipped_count:
- filtered_count:
- failed_count:
- error_code:
- error_message:

## 5. DB 기준선 변화

- `welfare_services`:
- `raw_api_payloads`:
- `service_taxonomies`:
- `service_taxonomy_terms`:
- `service_facts`:

## 6. 결과 판정

- `success`
- `partial success`
- `failed`
- `external variability`
- `blocked for follow-up`

## 7. 문제/경고

-
-
-

## 8. 해석

- external API 문제인지:
- 내부 저장/매핑 문제인지:
- downstream까지 영향 있는지:

## 9. 다음 액션

1.
2.
3.
