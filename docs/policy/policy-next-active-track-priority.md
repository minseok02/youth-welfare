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
- 지금도 존재하지만 active main track으로 다시 올릴 필요는 없는 server/infra/deploy 메모

중 무엇을 다음 active 메인 트랙으로 둘지 고정합니다.

## 결론

현재 `2026-05-18` 기준으로는 recommendation/collect 쪽은 기준선 유지가 맞고,
정책 트랙에서는 **`Gov24 canonical promotion` 설계 lane** 을 다시 여는 것이 우선입니다.

즉

- `YOUTH/Gov24 signal 소비` 는 admin facet까지 닫혔고
- `collect/runtime governance` 는 lane inventory, latest run, config summary까지 닫혔으며
- recommendation 은 제품 판단 대기 상태다
- 따라서 다음 active track은 `Gov24 canonical deferred` 를 막연한 backlog가 아니라
  **label-first canonical promotion 설계** 범위로 다시 여는 쪽이 맞습니다.

참고로 recommendation 트랙은 `2026-05-18` 기준 active main track으로 보지 않습니다. 운영 `REAL_USER` gate, retrieval local 우선화, diagnostics/rerank/AI score 해석까지 한 번 닫혔고, 남은 것은 `2736` 류 local 청년 정책 신호를 더 강하게 넣을지에 대한 제품/모델링 판단입니다. 즉 recommendation 은 새 재현 버그나 명시적 노출 강화 목표가 생길 때만 reopen 하는 편이 맞습니다.

즉 지금 기본 진행축은 아래 순서입니다.

1. 지금 닫힌 기준선을 유지한다
2. 새 재현 버그가 생기면 그 축만 다시 연다
3. 코드 작업을 더 하려면 deferred/product lane 중 하나를 명시적으로 선택한다

현재 다시 열 후보를 고른다면 아래 순서를 권장합니다.

1. `Gov24` canonical promotion 설계
2. recommendation 제품 판단
3. infra/server 확장

이유:

- `검색 로그`, `대시보드`, `userKey` 기준 개인 refresh 캐시는 현재 로컬 코드/데이터만으로 구현을 끝냈다.
- 개인 캐시는 recommendation payload 전체를 Redis에 넣는 대신, `non-personal refresh` 재계산을 잠시 억제하는 마커만 저장해 현재 persistence/log/bookmark 경계와 충돌을 줄였다.
- `2026-05-15` 기준 개인 캐시 회귀 검증은 `collect/replay/broad-suite` 기준선에서 다시 통과했다. 타깃 recommendation cache 테스트, `run-local-education-priority-replay.sh`, 전체 `./gradlew test integrationTest --no-daemon` 까지 모두 green이다.
- 따라서 지금 당장 practical task를 새로 잡으려면, 기존 기준선을 더 미세하게 닦기보다 어떤 deferred/product lane을 다시 열지 먼저 결정하는 편이 맞다.
- CTR 관련 다음 액션도 지금은 immediate active task가 아니라, recommendation reopen이 승인된 뒤에만 다시 본다.
- 군집 캐시는 실제 사용자 수와 요청 패턴이 충분히 커졌을 때 hit-rate / stale / invalidation 비용을 다시 계산하며 재검토하는 것이 맞다.
- `카카오 알림톡` 은 비즈니스 채널/발신 프로필/템플릿 심사와 사업자 증빙이 먼저라, 코드보다 운영 자격이 선행 조건이다.
- `2026-05-17` local CTR audit 기준 total logs는 `4143`, clicked logs는 `31` 까지 올라왔지만, 현재 origin 기준은 `example_logs/users=4119/458`, `bounded_local_logs/users=6/1`, `local_real_non_example_seed_logs/users=18/3`, `real_user_logs/users=0/0` 이다.
- 같은 wrapper를 `USER_COHORT=bounded_local` 로 다시 태우면 CTR은 `audit_scope_logs/users=6/1`, `DIAGNOSTIC_BOUNDED_LOCAL_TRAFFIC`, concentration은 `latest_batch_rows/users=6/1`, `BOUNDED_LOCAL_ONLY_COHORT` 이다.
- `USER_COHORT=local_real_non_example_seed` 로 다시 태우면 CTR은 `18/3`, `DEFERRED_CLICK_SAMPLE_THIN`, concentration은 `18/3`, `LOCAL_REAL_NON_EXAMPLE_SEED_ONLY_COHORT` 이다.
- `USER_COHORT=real_user` 로 다시 태우면 CTR은 `0/0`, `DEFERRED_EMPTY_REAL_USER_COHORT`, concentration은 `0/0`, `EMPTY_REAL_USER_COHORT` 이다.
- 이제 concentration audit도 별도 `real_user_cohort_gate` 를 같이 내보내고, current `USER_COHORT=all` 값은 `DEFERRED_NO_REAL_USER_COHORT` 다.
- 즉 all-cohort CTR readiness도 `DEFERRED_NO_REAL_USER_TRAFFIC`, concentration 해석 gate도 `DEFERRED_NO_REAL_USER_COHORT` 로 함께 막는다. non-example 표본은 생겼지만 전부 local synthetic seed이고 `REAL_USER` 는 아직 `0` 이기 때문이다.
- 즉 현재 phase의 실용적 다음 액션은 direct weight 변경보다 `bounded local seed와 local real-non-example seed를 넘어서는 실제 real-user 로그 기준선 확보 + recommendation concentration/diversity/fallback 경계 재해석` 이다. 군집 캐시는 장래 확장 포인트로 남긴다.
- 이 단계의 운영 실행/해석 경로는 이제 [recommendation-real-user-baseline-runbook.md](../recommendation/recommendation-real-user-baseline-runbook.md) 와 `deploy/smoke/run-local-real-user-readiness-check.sh` 로 고정한다. local bounded drill(`run-local-real-user-gate-drill-smoke.sh`) 은 gate 전이 로직 확인용이고, 운영 reopen 판단은 read-only real-user readiness check 기준으로만 읽는다.
- 별도로 `2026-05-15` local recommendation concentration audit 기준 latest batch는 `2394 rows / 132 users / 113 services`, top1 leader `2622` 가 `75 / 132 users (56.82%)` 를 차지한다. 최근 no-priority retrieval/rerank 보강 뒤 `3611` 이 top1로 올라오는 비중이 커졌지만, overall 판정은 아직 `CONCENTRATED_TOP1` 이다. 즉 priority가 완전히 무시되는 상태는 아니고, 현재 병목은 여전히 `priority 미반영` 보다는 `diversity / fallback / balancing 약함` 쪽으로 보는 편이 맞다.

반면 아래는 계속 blocked/backlog 또는 deferred 로 둡니다.

- `GOV24_SERVICE_FIELD / USER_TYPE / BENEFIT_TYPE` import/backfill SQL
- `GOV24_SUPPORT_CONDITION` full inventory/backfill
- `Gov24 supportConditions` 의 사업체/업종/창업 상태 code(`JA210*`, `JA220*/JA120*/JA1299/JA2299`, `JA110*`) fact 승격
- `YOUTH_MID` stable code mapping SQL

아래는 **현재 단계에서는 active main track으로 보지 않습니다.**

- 추가 서버/배포/infra 절차 확장
- 운영 DB 계정 / datasource 전환
- 운영 `.env` / secret store 전환
- HTTPS/Nginx
- 운영 DB migration / 배포 smoke

## 이유

## 1. `Gov24` 는 runtime closeout은 끝났고, 이제는 bounded canonical promotion 설계를 여는 단계다

지금까지 아래는 모두 닫혔다.

- current source 경로 확인
- page/meta visibility check
- label 3종 요청 템플릿
- `supportConditions` 요청 템플릿
- request package checklist
- runtime collect closeout (`list/detail/support raw=10942`)
- runtime audit / unmapped inventory 정리

즉 이제 `Gov24` 쪽 액션은 세 갈래로 나뉩니다.

1. `serviceField/userType/benefitType` 의 label-first canonical promotion 설계
2. hard import/backfill 쪽은 **provider/operator 발송 또는 응답 수신 대기**
3. runtime support gap 쪽은 **현재 제품이 사업체/업종 축을 실제로 소비하기 전까지 deferred 유지**

여기서 지금 당장 local 코드/문서로 끝낼 수 있는 것은 1번뿐입니다.
즉 현재 active lane은 stable code SQL 이 아니라,
무엇을 `service_taxonomy_terms` 로 올리고 무엇을 raw-only/deferred 로 남길지 경계를 먼저 고정하는 일입니다.

## 2. future infra/deploy 메모는 지금 active track이 아니다

운영 서버 smoke, 재기동, drift 체크 자체는 이미 실제로 확인했지만,
그렇다고 지금 main track을 deploy/infra 확장으로 다시 올릴 필요는 없다.

즉 현재 deferred 대상은

- 서버 자체의 존재 여부가 아니라
- bounded smoke와 drift 체크를 넘는 추가 운영 인프라 작업들

이다.

## 3. blocked SQL 은 여전히 blocked 이고, 이번 active lane은 그보다 한 단계 앞의 경계 고정이다

현재 blocked SQL 들은 모두 source-of-truth 부족이 핵심이다.

- `GOV24_*`
- `GOV24_SUPPORT_CONDITION`
- `YOUTH_MID`

여기서 내부 문서를 더 추가해도
reopen 조건이 충족되지는 않는다.

따라서 practical next action 기준으로는
blocked SQL 과 deferred Gov24 business-code 승격보다 먼저
**로컬에서 끝낼 수 있는 Gov24 canonical promotion 설계 트랙** 을 우선해야 한다.

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
2. `CTR tuning` 은 raw click 수와 별개로 현재도 bounded local seed 중심이라 바로 여는 active 작업이 아니다.
3. 지금 단계의 active main track은 local 기능/구조 검증, bounded runtime 반복 검증, 그에 따른 수정이다.
4. 프론트 연동 검증이 끝나기 전 deploy/infra 는 current 작업 기준에서 제외한다.

## deferred 정리

| 항목 | 지금 안 하는 이유 | 다시 열 조건 |
|---|---|---|
| recommendation 제품 판단 | bug closeout은 끝났고, 남은 것은 local 청년 정책 신호를 더 강하게 넣을지에 대한 제품/모델링 선택이다. | 새 재현 버그가 생기거나, local 청년 정책 노출 강화가 명시 목표로 승인될 때 |
| `Gov24` canonical promotion | `supportConditions` 는 partial runtime fact가 active 이고, `serviceField/userType/benefitType` raw inventory와 1차 내부 매핑 초안도 정리됐다. 현재 practical next action은 stable code SQL 이 아니라, exact label 유지 + allowlist token split 을 `service_taxonomy_terms` 중심 canonical 층으로 어디까지 올릴지 고정하는 것이다. | 현재 active |
| infra/server 확장 | 서버 reality는 확인됐지만, 현재 main track은 bounded runtime 기준선 유지와 drift 정리다. | 배포/운영 절차 고도화가 별도 active 목표로 승격될 때 |
