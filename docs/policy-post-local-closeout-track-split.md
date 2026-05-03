# post-local closeout track split

문서군 진입점: [policy-docs-index.md](./policy-docs-index.md)

관련 문서:

- [phase-plan.md](./phase-plan.md)
- [policy-local-closeout-pending-inventory.md](./policy-local-closeout-pending-inventory.md)
- [policy-next-active-track-priority.md](./policy-next-active-track-priority.md)

## 목적

로컬에서 직접 구현/검증 가능한 closeout 세트가 끝난 뒤,
남은 pending 을 어떤 성격으로 유지할지 고정합니다.

핵심은

- `아직 로컬에서 더 손볼 것이 남았는가`
- `지금 남은 것은 외부 응답이 있어야 다시 열리는가`
- `아니면 아직 실제 대상이 없는 future infra 메모인가`

를 다시 분리하는 것입니다.

## 결론

2026-05-01 현재 기준으로
기본 local closeout 세트는 통과했지만,
**기능/구조 추가 검증은 여전히 active pending** 으로 봅니다.

closeout 이후 남은 항목은 아래 세 묶음으로 유지합니다.

1. `local feature / structure verification`
2. `external blocked`
3. `future infra/deploy memo`

즉 지금 상태는
“기본 회귀는 확인했지만,
기능/구조 검증과 그에 따른 수정은 계속 진행하고,
외부 응답과 infra는 그 다음으로 미룬다”
입니다.

## local feature / structure verification

현재 active 로 보는 검증은 아래입니다.

- 실제 collect 이후 검색/상세/추천/북마크/챗봇/auth 흐름 점검
- 신규 source 추가 구조가 adapter/raw/sidecar/read-model 경계에서 버티는지 확인
- 검증 중 드러난 수정 포인트 반영
- 이후 최적화/보안 정리

### 공통 성격

- 로컬에서 바로 재현/수정 가능하다
- 프론트 완성 전에도 backend/API 기준으로 충분히 검증 가능하다
- 현재 가장 먼저 움직여야 하는 트랙이다
입니다.

## external blocked 트랙

아래는 현재 로컬에서 문서/코드만 더 쌓아도 unblock 되지 않는 항목입니다.

- `GOV24_SERVICE_FIELD / USER_TYPE / BENEFIT_TYPE` import/backfill SQL
- `GOV24_SUPPORT_CONDITION` full inventory/backfill
- `YOUTH_MID` stable code mapping SQL
- CTR 표본 추가 확보 후 rule/AI 가중치 및 프롬프트 재분석
- `Batch AI Gateway`
- 카카오 알림톡 연동 2차
- 사용자 규모 확대 전의 군집 캐시 추천

### 공통 성격

- provider/operator/codebook 응답이 필요하거나
- 운영 트래픽/실표본이 있어야 의미가 있거나
- 외부 심사/승인 상태에 묶여 있다

특히 `카카오 알림톡` 은 현재 repo 기준으로

- 사업자등록증/비즈니스 채널 전환/템플릿 심사 같은 외부 운영 요건이 먼저이고
- 3개월 내외 졸업 프로젝트 운영 범위에서는 구현보다 심사/반려/명의 관리 리스크가 더 크며
- 학생 개인 신분만으로는 practical하게 unblock 되기 어렵다

는 점에서, "개발 backlog" 보다는 "운영 자격 충족 시 reopen" 성격이 더 강하다.

`Batch AI Gateway` 도 현재 프로젝트 규모에서는

- 실시간 개인화로도 현재 트래픽/비용을 감당 가능하고
- batch polling / deadline fallback / partial completion 같은 운영 복잡도가 먼저 커지며
- 사용자 규모와 야간 사전계산 수요가 실제로 생기기 전까지는 얻는 이득보다 시스템 복잡도 증가가 더 크다

는 점에서, 즉시 active 기능이라기보다 "규모 확대 또는 비용 압박이 생길 때 재검토할 2차 기능"으로 둔다.

비슷하게 `군집 캐시 추천` 도 현재 프로젝트 규모에서는

- 군집 설계보다 `userKey` 기준 개인 캐시가 더 단순하고
- 첫 사용자 miss 비용, hit-rate, stale invalidation 관리 비용을 감안하면
- 사용자 수가 실제로 커지기 전에는 과설계가 될 가능성이 높다

는 점에서, 즉시 active 기능이라기보다 "규모가 커졌을 때 재평가할 확장 포인트"에 더 가깝다.

### 다시 active 로 올리는 조건

- provider/operator codebook 수신
- current API 기준 inventory/schema export 확보
- CTR 표본 누적 확보
- 알림톡 심사 완료

그 전까지는 backlog 유지가 기본입니다.

## future infra/deploy memo

아래는 서버/배포 대상이 생긴 뒤에야 의미가 생기는 메모입니다.

- 서버 기동 절차
- DB 계정 생성 및 datasource 전환
- `.env` / secret store 전환
- HTTPS/Nginx 적용
- migration / dual-write / revoke / drop-legacy smoke

### 공통 성격

- 로컬 성공 이력만으로는 의미가 없다
- 실제 host / DB / secret / reverse proxy 가 생겨야 한다
- 지금은 문서보다 대상 환경이 먼저다

### 다시 active 로 올리는 조건

- 실제 서버/DB/secret 경계가 생김
- 그 시점에 맞춰 절차를 다시 만들기로 결정함

## 현재 권장 해석

현재 repo 상태는
`local regression baseline restored, but verification track still active` 입니다.

따라서 다음 액션은 아래 순서입니다.

1. local feature / structure verification 계속 진행
2. 검증 중 발견되는 수정/최적화/보안 정리
3. 프론트 연동 후 통합 검증
4. 그 다음 external blocked 또는 infra/deploy 재검토

## 하지 않는 것

현재 단계에서는 아래를 기본 액션으로 보지 않습니다.

- blocked 항목에 대해 근거 없는 SQL 초안 더 만들기
- 실제 대상이 없는데 deploy 문서를 더 확장하기
- 이미 green 인 로컬 smoke/test 를 이유 없이 반복 실행하기

## 요약

1. 기본 local closeout 세트는 2026-05-01 기준으로 통과했다.
2. 하지만 기능/구조 추가 검증은 아직 active pending 이다.
3. external blocked 와 future infra/deploy memo 는 그 다음 우선순위다.
