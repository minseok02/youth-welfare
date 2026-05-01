# 다음 active track 우선순위

관련 문서:

- [phase-plan.md](./phase-plan.md)
- [policy-normalization-blocked-sql-reopen-priority.md](./history/policy/policy-normalization-blocked-sql-reopen-priority.md)
- [policy-normalization-gov24-request-package-checklist.md](./history/policy/policy-normalization-gov24-request-package-checklist.md)

## 목적

현재 남은 pending 중

- 로컬에서 바로 구현/검증 가능한 트랙
- 외부 source/codebook 응답이 있어야 다시 열 수 있는 blocked SQL/doc 트랙
- 운영 환경이 있어야만 진행되는 deploy 트랙

중 무엇을 다음 active 메인 트랙으로 둘지 고정합니다.

## 결론

현재 next active main track은 **로컬에서 끝낼 수 있는 구현/검증 pending 정리** 입니다.

즉 다음 기본 진행축은 아래 순서입니다.

1. 로컬에서 재현/검증 가능한 pending 추리기
2. 로컬에서 테스트/스모크/수동확인까지 끝내기
3. 로컬 기준으로 더 손볼 게 있는지 정리하기
4. 남은 것이 external dependency 또는 운영 dependency뿐일 때만 운영/deploy 로 이동

반면 아래는 계속 blocked/backlog 로 둡니다.

- `GOV24_SERVICE_FIELD / USER_TYPE / BENEFIT_TYPE` import/backfill SQL
- `GOV24_SUPPORT_CONDITION` full inventory/backfill
- `YOUTH_MID` stable code mapping SQL

아래는 **로컬 정리 완료 전까지 defer** 합니다.

- 운영 서버 Docker Compose 기동
- 운영 DB 계정 / datasource 전환
- 운영 `.env` / secret store 전환
- HTTPS/Nginx
- 운영 DB migration / 배포 smoke

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

## 2. 운영/deploy pending은 “지금 당장 할 수 있다”와 “지금 해야 한다”가 다르다

운영 pending은 실행하면 state는 바뀌지만,
지금 방향에서는 그것만으로 우선순위가 되지 않는다.

현재 기준은 아래다.

- 먼저 로컬에서 구현/검증 가능한 것 전부 마무리
- 로컬 기준 정상작동 확인
- 추가 수정이 없거나 남은 것이 운영 의존뿐일 때만 운영 이동

즉 deploy lane은 available 하더라도
**local-first closeout 이후** 로 미룬다.

## 3. blocked SQL 은 지금 더 파도 reopen 조건 자체는 바뀌지 않는다

현재 blocked SQL 들은 모두 source-of-truth 부족이 핵심이다.

- `GOV24_*`
- `GOV24_SUPPORT_CONDITION`
- `YOUTH_MID`

여기서 내부 문서를 더 추가해도
reopen 조건이 충족되지는 않는다.

따라서 practical next action 기준으로는
blocked SQL 보다 먼저
**로컬에서 끝낼 수 있는 검증/수정 트랙** 을 우선해야 한다.

## 현재 phase의 local-first 우선순위

### first lane

- 현재 구현된 기능의 로컬 테스트/스모크/회귀 확인
- 로컬에서 추가 수정이 필요한지 확인
- 남은 pending 중 external/ops 의존이 아닌 항목 우선 처리

### second lane

- blocked source 응답 대기
- external dependency 없는 문서/코드 보정

### last lane

- 운영 서버 Docker Compose 기동
- 운영 DB 계정 생성
- 앱 datasource 전환
- HTTPS/Nginx
- PII datasource / revoke / legacy user id migration smoke

## blocked 트랙 유지 조건

아래 중 하나가 생기면 `Gov24` blocked SQL 을 다시 active로 올린다.

- provider/operator codebook 응답 수신
- current Swagger/schema export 추가 확보
- 운영자가 current API 기준 inventory를 전달

그 전까지는 blocked/backlog 유지가 기본이다.

## 운영으로 넘어가는 조건

아래를 모두 만족할 때만 운영/deploy lane을 active로 올린다.

1. 로컬에서 가능한 구현/수정이 끝남
2. 로컬 테스트/스모크 기준 정상작동 확인
3. 남은 작업이 external dependency 또는 운영 dependency 중심임

## 요약

1. `Gov24` 문서 트랙은 지금 단계에서 external response boundary까지 이미 내려왔다.
2. 하지만 그 다음 active main track은 곧바로 운영/deploy 가 아니라 local-first closeout 이다.
3. practical next action 기준으로는 로컬에서 끝낼 수 있는 검증/수정 항목을 먼저 닫고, 더 손볼 게 없을 때만 운영으로 넘어간다.
