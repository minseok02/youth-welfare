# post-local closeout track split

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
- `아니면 운영 환경에서만 의미가 있는가`

를 다시 분리하는 것입니다.

## 결론

2026-05-01 현재 기준으로
**로컬에서 바로 계속 닫을 수 있는 active pending 은 없다** 고 봅니다.

남은 항목은 아래 두 트랙으로만 유지합니다.

1. `external blocked`
2. `ops-only`

즉 지금 상태는
“로컬에서 할 수 있는 것은 끝냈고,
남은 것은 외부 source/codebook 응답 또는 운영 환경이 있어야 다시 움직인다”
입니다.

## external blocked 트랙

아래는 현재 로컬에서 문서/코드만 더 쌓아도 unblock 되지 않는 항목입니다.

- `GOV24_SERVICE_FIELD / USER_TYPE / BENEFIT_TYPE` import/backfill SQL
- `GOV24_SUPPORT_CONDITION` full inventory/backfill
- `YOUTH_MID` stable code mapping SQL
- CTR 표본 추가 확보 후 rule/AI 가중치 및 프롬프트 재분석
- 카카오 알림톡 연동 2차

### 공통 성격

- provider/operator/codebook 응답이 필요하거나
- 운영 트래픽/실표본이 있어야 의미가 있거나
- 외부 심사/승인 상태에 묶여 있다

### 다시 active 로 올리는 조건

- provider/operator codebook 수신
- current API 기준 inventory/schema export 확보
- CTR 표본 누적 확보
- 알림톡 심사 완료

그 전까지는 backlog 유지가 기본입니다.

## ops-only 트랙

아래는 로컬에서 더 오래 잡고 있어도 실제 진행이 되지 않는 항목입니다.

- 운영 서버 Docker Compose 기동
- 운영 DB 계정 생성 및 datasource 전환
- 운영 `.env` / secret store 전환
- HTTPS/Nginx 적용
- 운영 DB migration / dual-write / revoke / drop-legacy smoke

### 공통 성격

- 실제 운영 인프라 상태를 바꾸는 작업이다
- 로컬 성공 이력만으로 완료 판정을 할 수 없다
- 실제 host / DB / secret / reverse proxy 가 있어야 의미가 있다

### 다시 active 로 올리는 조건

- “이제 운영으로 넘어간다”는 명시적 전환 결정
- 운영 host / DB / secret 접근 가능 상태 확보
- 로컬 기준 추가 수정 포인트가 더 없다는 현재 판단 유지

## 현재 권장 해석

현재 repo 상태는
`local-first closeout complete` 입니다.

따라서 다음 액션은 더 많은 로컬 설계나 임의 수정이 아니라,
아래 둘 중 하나입니다.

1. external blocked 응답을 기다리며 backlog 유지
2. 운영 전환 결정을 내리고 ops-only 트랙을 active 로 올리기

## 하지 않는 것

현재 단계에서는 아래를 기본 액션으로 보지 않습니다.

- blocked 항목에 대해 근거 없는 SQL 초안 더 만들기
- 운영 진입 결정을 안 한 채 deploy 문서를 더 확장하기
- 이미 green 인 로컬 smoke/test 를 이유 없이 반복 실행하기

## 요약

1. 로컬에서 직접 끝낼 수 있는 항목은 2026-05-01 기준으로 모두 닫혔다.
2. 남은 pending 은 `external blocked` 와 `ops-only` 두 트랙뿐이다.
3. 따라서 다음 active 작업은 새 로컬 구현이 아니라, 외부 응답 대기 또는 운영 전환 결정 중 하나다.
