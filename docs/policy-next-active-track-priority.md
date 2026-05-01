# 다음 active track 우선순위

관련 문서:

- [phase-plan.md](./phase-plan.md)
- [policy-normalization-blocked-sql-reopen-priority.md](./policy-normalization-blocked-sql-reopen-priority.md)
- [policy-normalization-gov24-request-package-checklist.md](./policy-normalization-gov24-request-package-checklist.md)

## 목적

현재 남은 pending 중

- 외부 source/codebook 응답이 있어야 다시 열 수 있는 blocked SQL/doc 트랙
- 지금 바로 실행 가능한 운영/deploy 트랙

중 무엇을 다음 active 메인 트랙으로 둘지 고정합니다.

## 결론

현재 next active main track은 **운영/deploy pending** 입니다.

즉 다음 기본 진행축은 아래 순서입니다.

1. 운영 서버 Docker Compose 기동
2. 운영 DB 계정 / datasource 전환
3. 운영 `.env` / secret store 전환
4. HTTPS/Nginx
5. PII sync queue / revoke / legacy user id drop 배포 smoke

반면 아래는 계속 blocked/backlog 로 둡니다.

- `GOV24_SERVICE_FIELD / USER_TYPE / BENEFIT_TYPE` import/backfill SQL
- `GOV24_SUPPORT_CONDITION` full inventory/backfill
- `YOUTH_MID` stable code mapping SQL

## 이유

## 1. `Gov24` 문서 트랙은 현재 external response boundary까지 이미 내려왔다

지금까지 아래는 모두 닫혔다.

- current source 경로 확인
- page/meta visibility check
- label 3종 요청 템플릿
- `supportConditions` 요청 템플릿
- request package checklist

즉 이제 `Gov24` 쪽 다음 액션은
내부 문서 추가가 아니라
**provider/operator 발송 또는 응답 수신 대기** 이다.

문서만 더 쌓아도 실제 unblock은 일어나지 않는다.

## 2. 운영/deploy pending은 외부 응답 없이 바로 움직일 수 있다

현재 phase-plan 상의 운영 pending은
대부분 로컬/운영 환경 준비와 배포 검증 문제다.

예:

- Docker Compose
- DB 계정 생성
- datasource 전환
- HTTPS/Nginx
- migration 적용 smoke

이 축은 external codebook 응답을 기다릴 필요가 없고,
실행하면 바로 state가 바뀐다.

즉 small-step 진행 효율이 더 높다.

## 3. blocked SQL 은 지금 더 파도 reopen 조건 자체는 바뀌지 않는다

현재 blocked SQL 들은 모두 source-of-truth 부족이 핵심이다.

- `GOV24_*`
- `GOV24_SUPPORT_CONDITION`
- `YOUTH_MID`

여기서 내부 문서를 더 추가해도
reopen 조건이 충족되지는 않는다.

따라서 practical next action 기준으로는
운영/deploy 트랙이 우선이다.

## 현재 phase의 운영 우선순위

### first lane

- 운영 서버 Docker Compose 기동
- 운영 DB 계정 생성
- 앱 datasource 전환

### second lane

- HTTPS/Nginx
- PII datasource / revoke / legacy user id migration smoke

### later lane

- CTR 표본 추가 확보 후 재분석
- 카카오 알림톡 2차

## blocked 트랙 유지 조건

아래 중 하나가 생기면 `Gov24` blocked SQL 을 다시 active로 올린다.

- provider/operator codebook 응답 수신
- current Swagger/schema export 추가 확보
- 운영자가 current API 기준 inventory를 전달

그 전까지는 blocked/backlog 유지가 기본이다.

## 요약

1. `Gov24` 문서 트랙은 지금 단계에서 external response boundary까지 이미 내려왔다.
2. 그래서 다음 active main track은 blocked SQL 이 아니라 운영/deploy pending 이다.
3. practical next action 기준으로 가장 먼저 움직일 것은 Docker Compose / DB 계정 / datasource 전환이다.
