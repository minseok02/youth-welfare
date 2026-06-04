# youth regionless audit runbook

문서군 진입점: [collect-docs-index.md](./collect-docs-index.md)

## 목적

이 문서는 `YOUTH` source의 남은 `regionless` row를 다시 읽는 compact audit 절차입니다.

현재 핵심 질문은 아래 셋입니다.

1. `YOUTH regionless` 가 `zipCd` 누락 때문인가
2. 아니면 전국형 `zipCd` 해석 때문에 `host_org` 추정으로 넘어간 결과인가
3. 남은 row 중 실제로 수동 검토가 필요한 locally suspicious subset이 얼마나 되는가

## 실행

```bash
bash deploy/smoke/run-local-youth-regionless-audit.sh
```

server/RDS에서는 아래를 사용합니다.

```bash
ENV_FILE=.env.production SMOKE_DB_MODE=postgres APP_HEALTH_URL='http://127.0.0.1:8082/actuator/health' \
bash deploy/smoke/run-local-youth-regionless-audit.sh
```

## 요약 항목

- `regionless_services`
- `regionless_empty_zip_count`
- `regionless_csv_zip_count`
- `regionless_nationwide_zip_count`
- `regionless_non_nationwide_zip_count`
- `regionless_centralish_host_count`
- `regionless_localish_host_count`
- `regionless_unclassified_host_count`
- `local_suspicious_count`
- `decision_class`

## 해석

- `NATIONWIDE_DOMINANT_BASELINE`
  - 남은 `regionless` 의 대부분이 전국형 `zipCd` + 중앙/전국성 `host_org` 추정 패턴입니다.
  - broad parser failure보다는 bounded manual review 대상만 남은 상태로 읽습니다.
- `LOCAL_REVIEW_REQUIRED`
  - locally suspicious subset이 커졌습니다.
  - `host_org` 수동 검토나 bounded override rule 검토가 필요한 상태로 읽습니다.

## 현재 해석 포인트

- `YOUTH` 는 `zipCd` 가 비어서 regionless가 되는 경우보다, 긴 CSV 코드 목록이 전국형으로 해석되는 경우가 훨씬 큽니다.
- 따라서 이 audit는 `missing field` 문제보다 `nationwide zipCd interpretation` 문제를 다시 읽는 용도입니다.

## 현재 local 수동 판정 메모

- `2025년 울산광역시 남구 청년도전 지원사업(단기·중기)`
  - title과 설명 모두 `울산광역시 남구` 지역 사업으로 읽혀 **true local override 후보**로 본다.
- `"노벨 문학도시 장흥을 즐겨라!" 지역탐방 프로그램 운영`
  - title과 설명 모두 `장흥` 현장 프로그램으로 읽혀 **전라남도 장흥군 local override 후보**로 본다.

즉 현재 `local_suspicious_count=2` 는 broad parser failure가 아니라, bounded manual review/override 후보 두 건으로 읽는 것이 맞다.
