# 다음 active track 우선순위

문서군 진입점: [policy-docs-index.md](./policy-docs-index.md)

관련 문서:

- [phase-plan.md](./phase-plan.md)
- [policy-gov24-blocked-track-status.md](./policy-gov24-blocked-track-status.md)
- [policy-normalization-blocked-sql-reopen-priority.md](./history/policy/policy-normalization-blocked-sql-reopen-priority.md)
- [policy-normalization-gov24-request-package-checklist.md](./history/policy/policy-normalization-gov24-request-package-checklist.md)

## 목적

현재 남은 pending 중

- 로컬에서 바로 구현/검증 가능한 트랙
- 외부 source/codebook 응답이 있어야 다시 열 수 있는 blocked SQL/doc 트랙
- 서버가 생긴 뒤에만 의미가 있는 future infra/deploy 메모

중 무엇을 다음 active 메인 트랙으로 둘지 고정합니다.

## 결론

현재 next active main track은 **로컬에서의 기능/구조 추가 검증** 입니다.

즉 다음 기본 진행축은 아래 순서입니다.

1. 실제 기능이 끝까지 이어지는지 로컬에서 다시 검증하기
2. 새 source 추가 구조가 collector -> raw -> sidecar -> read-model 경계에서 버티는지 확인하기
3. 검증 중 드러난 수정 포인트를 반영하기
4. 그 다음 최적화/보안 정리
5. 프론트 연동 검증
6. 마지막에만 infra/deploy 검토

현재 2차 기능 후보 중에서는 아래 순서를 권장한다.

1. 검색 로그
2. 추천/수집 대시보드
3. 카카오 알림톡

이유:

- `검색 로그`, `대시보드` 는 현재 로컬 코드/데이터만으로도 직접 구현·검증 가능하다.
- 반면 `카카오 알림톡` 은 비즈니스 채널/발신 프로필/템플릿 심사와 사업자 증빙이 먼저라, 코드보다 운영 자격이 선행 조건이다.
- 따라서 현재 phase에서 2차 기능을 더 진행한다면 `대시보드` 가 `알림톡` 보다 practical next step 이다.

반면 아래는 계속 blocked/backlog 로 둡니다.

- `GOV24_SERVICE_FIELD / USER_TYPE / BENEFIT_TYPE` import/backfill SQL
- `GOV24_SUPPORT_CONDITION` full inventory/backfill
- `YOUTH_MID` stable code mapping SQL

아래는 **현재 단계에서는 active track으로 보지 않습니다.**

- 서버가 생긴 뒤의 배포/infra 절차
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

## 2. future infra/deploy 메모는 지금 active track이 아니다

현재는 운영 서버 자체가 없으므로,
deploy/infra 메모는 “나중에 서버가 생기면 다시 만들 주제”일 뿐
지금 당장 진행할 트랙이 아니다.

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

- 실제 기능 end-to-end 로컬 검증
- 신규 source onboarding 구조 검증
- 검증 중 드러나는 수정 포인트 반영

### second lane

- 최적화와 보안 정리
- 프론트 연동 이후 통합 검증

### deferred lane

- external blocked 응답 대기
- 서버가 생긴 뒤의 infra/deploy 절차 재정의

## blocked 트랙 유지 조건

아래 중 하나가 생기면 `Gov24` blocked SQL 을 다시 active로 올린다.

- provider/operator codebook 응답 수신
- current Swagger/schema export 추가 확보
- 운영자가 current API 기준 inventory를 전달

그 전까지는 blocked/backlog 유지가 기본이다.

## infra/deploy 를 다시 문서화할 조건

아래를 모두 만족할 때만 별도 deploy/infrastructure runbook을 다시 만든다.

1. 실제 서버/DB/secret 경계가 생김
2. 로컬 기준 추가 수정이 끝남
3. 배포 절차를 문서화할 실제 대상이 있음

## 요약

1. `Gov24` 문서 트랙은 지금 단계에서 external response boundary까지 이미 내려왔다.
2. 지금 단계의 active main track은 local 기능/구조 검증과 그에 따른 수정이다.
3. 프론트 연동 검증이 끝나기 전 deploy/infra 는 current 작업 기준에서 제외한다.
