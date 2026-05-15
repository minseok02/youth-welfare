# 다음 active track 우선순위

문서군 진입점: [policy-docs-index.md](./policy-docs-index.md)

관련 문서:

- [phase-plan.md](../phase-plan.md)
- [policy-gov24-blocked-track-status.md](./policy-gov24-blocked-track-status.md)
- [policy-normalization-blocked-sql-reopen-priority.md](../history/policy/policy-normalization-blocked-sql-reopen-priority.md)
- [policy-normalization-gov24-request-package-checklist.md](../history/policy/policy-normalization-gov24-request-package-checklist.md)

## 목적

현재 남은 pending 중

- 로컬에서 바로 구현/검증 가능한 트랙
- 외부 source/codebook 응답이 있어야 다시 열 수 있는 blocked SQL/doc 트랙
- 서버가 생긴 뒤에만 의미가 있는 future infra/deploy 메모

중 무엇을 다음 active 메인 트랙으로 둘지 고정합니다.

## 결론

현재 next active main track은 **로컬에서의 기능/구조 추가 검증과 bounded runtime 반복 검증** 입니다.

즉 다음 기본 진행축은 아래 순서입니다.

1. 실제 기능이 끝까지 이어지는지 로컬에서 다시 검증하기
2. 새 source 추가 구조가 collector -> raw -> sidecar -> read-model 경계에서 버티는지 확인하기
3. 검증 중 드러난 수정 포인트를 반영하기
4. 그 다음 최적화/보안 정리
5. 프론트 연동 검증
6. 마지막에만 infra/deploy 검토

현재 2차 기능/구조 후보 중에서는 아래 순서를 권장한다.

1. bounded policy admin runtime 경로 반복 검증과 one-shot summary smoke 기준선 유지
2. broader local 기능/구조 검증에서 드러나는 drift나 dead runbook 설명 정리
3. CTR readiness audit 재확인 + 클릭 표본 확충
4. readiness가 `READY_FOR_WEIGHT_REVIEW` 로 바뀐 뒤 추천 품질 재조정
5. 개인 캐시 운영 관측치가 더 쌓인 뒤 TTL/eviction 세부 튜닝
6. 사용자 규모 증가 시 군집 캐시 재검토
7. 카카오 알림톡 blocked 재검토

이유:

- `검색 로그`, `대시보드`, `userKey` 기준 개인 refresh 캐시는 현재 로컬 코드/데이터만으로 구현을 끝냈다.
- 개인 캐시는 recommendation payload 전체를 Redis에 넣는 대신, `non-personal refresh` 재계산을 잠시 억제하는 마커만 저장해 현재 persistence/log/bookmark 경계와 충돌을 줄였다.
- `2026-05-15` 기준 개인 캐시 회귀 검증은 `collect/replay/broad-suite` 기준선에서 다시 통과했다. 타깃 recommendation cache 테스트, `run-local-education-priority-replay.sh`, 전체 `./gradlew test integrationTest --no-daemon` 까지 모두 green이다.
- 따라서 지금 당장 다시 열 수 있는 practical task는 `CTR tuning` 자체보다, bounded runtime 경로와 local closeout 기준선이 문서/스크립트/실행 결과에서 계속 같은 truth를 유지하는지 반복 검증하는 쪽이다.
- CTR 관련 다음 액션은 여전히 `CTR readiness audit` 를 기준으로 표본이 실제 튜닝 가능한 상태인지 재확인하고, 충분한 click sample이 쌓인 뒤에만 추천 품질 가중치를 다시 조정하는 쪽이다.
- 군집 캐시는 실제 사용자 수와 요청 패턴이 충분히 커졌을 때 hit-rate / stale / invalidation 비용을 다시 계산하며 재검토하는 것이 맞다.
- `카카오 알림톡` 은 비즈니스 채널/발신 프로필/템플릿 심사와 사업자 증빙이 먼저라, 코드보다 운영 자격이 선행 조건이다.
- `2026-05-15` local CTR audit 기준 total logs는 `1583` 이지만 clicked logs는 `14`, overall CTR은 `0.88%`, fallback clicked는 `0`, clicked service는 `2`개뿐이라 현재 readiness 판정은 `DEFERRED_CLICK_SAMPLE_THIN` 이다.
- 따라서 현재 phase에서 더 진행할 실용적 후보는 `CTR readiness audit + 표본 확충` 이고, 실제 weight tuning은 readiness가 올라간 뒤에만 연다. 군집 캐시는 장래 확장 포인트로 남긴다.

반면 아래는 계속 blocked/backlog 또는 deferred 로 둡니다.

- `GOV24_SERVICE_FIELD / USER_TYPE / BENEFIT_TYPE` import/backfill SQL
- `GOV24_SUPPORT_CONDITION` full inventory/backfill
- `Gov24 supportConditions` 의 사업체/업종/창업 상태 code(`JA210*`, `JA220*/JA120*/JA1299/JA2299`, `JA110*`) fact 승격
- `YOUTH_MID` stable code mapping SQL

아래는 **현재 단계에서는 active track으로 보지 않습니다.**

- 서버가 생긴 뒤의 배포/infra 절차
- 운영 DB 계정 / datasource 전환
- 운영 `.env` / secret store 전환
- HTTPS/Nginx
- 운영 DB migration / 배포 smoke

## 이유

## 1. `Gov24` 는 runtime closeout은 끝났고, 남은 것은 external blocked 또는 deferred 판단이다

지금까지 아래는 모두 닫혔다.

- current source 경로 확인
- page/meta visibility check
- label 3종 요청 템플릿
- `supportConditions` 요청 템플릿
- request package checklist
- runtime collect closeout (`list/detail/support raw=10937`)
- runtime audit / unmapped inventory 정리

즉 이제 `Gov24` 쪽 남은 액션은 두 갈래다.

1. hard import/backfill 쪽은 **provider/operator 발송 또는 응답 수신 대기**
2. runtime support gap 쪽은 **현재 제품이 사업체/업종 축을 실제로 소비하기 전까지 deferred 유지**

추가 문서가 바로 unblock을 만들지는 않지만, 현재 deferred 판단과 reopen 조건은 이미 active 문서에 고정해 두는 편이 맞다.

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
blocked SQL 과 deferred Gov24 business-code 승격보다 먼저
**로컬에서 끝낼 수 있는 검증/수정 트랙** 을 우선해야 한다.

## 현재 phase의 local-first 우선순위

### first lane

- 실제 기능 end-to-end 로컬 검증
- bounded policy admin runtime 반복 검증
- 신규 source onboarding 구조 검증
- 검증 중 드러나는 수정 포인트 반영

### second lane

- 운영 보고서 관점 요약 강화
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

1. `Gov24` 는 runtime closeout과 audit 기준선까지 닫혔고, 남은 것은 external blocked 또는 deferred 판단이다.
2. `CTR tuning` 은 readiness가 아직 `DEFERRED_CLICK_SAMPLE_THIN` 이므로 바로 여는 active 작업이 아니다.
3. 지금 단계의 active main track은 local 기능/구조 검증, bounded runtime 반복 검증, 그에 따른 수정이다.
4. 프론트 연동 검증이 끝나기 전 deploy/infra 는 current 작업 기준에서 제외한다.
