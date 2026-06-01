# Gov24 Reopen Checklist

문서군 진입점: [policy-docs-index.md](./policy-docs-index.md)

관련 문서:

- [policy-gov24-canonical-promotion-plan.md](./policy-gov24-canonical-promotion-plan.md)
- [policy-gov24-blocked-track-status.md](./policy-gov24-blocked-track-status.md)
- [policy-normalization-current-state.md](./policy-normalization-current-state.md)
- [policy-next-active-track-priority.md](./policy-next-active-track-priority.md)
- [../collect/collect-current-state.md](../collect/collect-current-state.md)

## 목적

이 문서는 `Gov24 canonical` 을 다시 열지 말지를

- 기준선이 유지되는지
- reopen 목표가 진짜 명시됐는지
- 열더라도 어느 lane 만 여는지

순서대로 확인하는 checklist 입니다.

현재 기본값은 **reopen 하지 않음** 입니다.

## 지금 기본 해석

현재 코드/문서 기준으로 닫힌 것은 아래입니다.

1. `Gov24` label-first canonical term 경계
2. `Gov24 sourceId detail` self-heal
3. collect/backfill residual closeout
4. admin/read-model/presentation 경계
5. supportConditions full-scope runtime fact gap
6. `Gov24 -> YOUTH_MID` / priority bucket bridge
7. internal taxonomy code seed/backfill

즉 지금 남은 것은 구현 누락보다 **새 목표 승인 여부** 입니다.

## reopen 전에 먼저 볼 것

1. `bash deploy/smoke/run-local-active-baseline-suite.sh`
2. `bash deploy/smoke/run-local-collect-legacy-repair-suite.sh`
3. [policy-gov24-canonical-promotion-plan.md](./policy-gov24-canonical-promotion-plan.md)
4. [policy-normalization-current-state.md](./policy-normalization-current-state.md)

위 둘이 깨져 있으면 `Gov24 canonical` reopen 이 아니라 baseline repair가 먼저입니다.

## reopen 허용 조건

아래 둘 중 하나가 **명시적으로 승인** 됐을 때만 reopen 합니다.

1. 외부 공식 stable codebook 기반 import/backfill
2. raw 조합값 전체를 hard eligibility fact로 승격

반대로 아래만으로는 reopen 하지 않습니다.

1. “Gov24도 뭔가 더 하면 좋겠다”
2. raw label이 이미 저장돼 있다는 사실
3. collect가 green이라는 사실

## reopen 순서

### 1. baseline 유지 확인

```bash
bash deploy/smoke/run-local-active-baseline-suite.sh
```

기대:

- backend test green
- frontend lint/build/e2e green
- ops baseline green
- collect legacy repair green

### 2. reopen 목표를 하나만 고른다

다음 중 하나만 선택합니다.

1. `external stable codebook import/backfill`
2. `raw combination hard eligibility`

한 번에 둘 이상 열면 현재 문서/검증 기준이 무너집니다.

### 3. 목표별로 문서를 고른다

- `external stable codebook import/backfill`
  - [policy-gov24-blocked-track-status.md](./policy-gov24-blocked-track-status.md)
  - [policy-gov24-canonical-promotion-plan.md](./policy-gov24-canonical-promotion-plan.md)
- `raw combination hard eligibility`
  - [policy-gov24-canonical-promotion-plan.md](./policy-gov24-canonical-promotion-plan.md)
  - recommendation/product 문서군

### 4. reopen 범위를 명시한다

최소 기록:

1. reopen 목표
2. 이번에 여는 lane
3. 이번에 안 여는 lane
4. baseline 실행 결과
5. code/source-of-truth 전제

## 지금 안 하는 것

현재 checklist 기준으로는 아래를 자동으로 열지 않습니다.

1. 외부 공식 codebook 재수입
2. raw 조합값 전체의 `service_facts` 직접 승격
3. `service_facts` 를 통한 recommendation hard gate

## 요약

1. 지금 기본값은 `Gov24 canonical reopen 없음` 입니다.
2. 먼저 baseline을 다시 확인합니다.
3. reopen 하려면 목표를 하나만 고릅니다.
4. 목표가 승인되지 않으면 현재 label-first canonical 경계를 유지합니다.
