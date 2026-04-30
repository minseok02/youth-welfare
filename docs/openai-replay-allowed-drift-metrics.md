# OpenAI Replay Allowed Drift Metrics

`real-openai` replay에서 strict equality 대신 어떤 수치를 비교 기준으로 둘지 정리한 문서입니다.

관련 문서:

- [openai-replay-validation-policy.md](./openai-replay-validation-policy.md)
- [policy-normalization-education-priority-replay-procedure.md](./policy-normalization-education-priority-replay-procedure.md)
- [policy-normalization-education-control-drift-analysis.md](./policy-normalization-education-control-drift-analysis.md)

## 결론

현재 단계에서 gate metric 우선순위는 아래가 맞습니다.

1. `top-N target row count`
2. `top-N target row presence/absence`
3. `explanation drift` 는 수동 review
4. `score delta` 는 자동 gate에서 제외

즉 `real-openai` replay의 allowed drift는
`점수 exact match` 가 아니라
`실험이 target row 유입/상승이라는 제품 목적을 유지하는가`
로 봅니다.

## 왜 `score delta` 를 버리나

실측 기준으로 same `promptSha256` + same `replaySeed` + same `systemFingerprint`
조건에서도 `ai_score` 는 달라졌습니다.

예:

- `/tmp/tmp.TpE5SaiHJu`
- sample B
- `404: 85 -> 75`
- `405: 75 -> 85`
- `390: 55 -> 70`

그런데 이 run에서도:

- sample B `top10_target_count` 는 `1 -> 1`
- target row가 완전히 새로 유입되거나 사라진 건 아니었음

즉 지금 `score delta` 는
실험 회귀보다 live response variability에 더 민감합니다.

그래서 자동 gate로 쓰면 노이즈가 너무 큽니다.

## 왜 `top-N target row count` 를 우선으로 보나

이 실험의 목적은:

- `compat=기타 + youth_major=교육`
- row가
- `priority=EDUCATION`
  사용자에서
- 더 잘 보이게 하는 것

입니다.

따라서 가장 직접적인 성공 지표는:

- target row가 top-N에 더 많이 들어왔는가
- 적어도 사라지지 않았는가

입니다.

이건 current artifact들에서도 비교적 안정적입니다.

### sample A

- rule-only / real-openai 모두 `flag on` 에서 target row top-10 count 증가를 반복 확인

### sample B

- exact rank / `final_score` 는 흔들려도
- target row count는 `0 -> 0`, `1 -> 1` 같은 식으로 더 안정적

## metric별 판정

### 1. top-10 target row count

자동 gate에 사용합니다.

- sample A:
  - `on >= off + 1` 기대
- sample B:
  - `on > off` 면 **warning**
  - 현재는 control sample 성격상 fail 로 승격하지 않음

이 metric이 현재 1순위입니다.

### 2. top-10 target row presence/absence

자동 gate에 사용합니다.

count와 거의 비슷하지만,
sample 수가 작을 때는 “0/1 존재 여부”도 같이 보는 편이 해석이 쉽습니다.

예:

- target row가 top-10에 있었는지
- 완전히 사라졌는지

### 3. explanation drift

수동 review 항목으로 둡니다.

이유:

- 현재 `aiReason` 은 live 모델 응답이라 문장 변형이 잦음
- 자동 gate로 두면 false positive가 많아짐

따라서 지금은:

- target row가 올라왔을 때
- explanation이 완전히 부적절해졌는지

만 샘플 review로 봅니다.

### 4. score delta

자동 gate에서 제외합니다.

제외 이유:

- same fingerprint에서도 흔들림
- row 간 상대순위 재배치만으로도 delta가 커짐
- 실험 목적과 직접 연결된 지표가 아님

## 현재 권장 gate

### rule-only

- hard gate 유지
- score snapshot diff 없음 기대

### real-openai

- hard gate 아님
- 아래만 본다
  - sample A top-10 target count improvement 유지
  - sample B target count 비정상 증가 시 warning
  - trace/artifact 완전성 확보

## 왜 sample B `unexpected increase` 를 warning 으로 두나

실측 artifact를 보면 sample B는 아래처럼 흔들렸습니다.

- `/tmp/tmp.WoIyHuKtMd`: `0 -> 1`
- `/tmp/tmp.EZBH319uNA`: `1 -> 0`
- `/tmp/tmp.TpE5SaiHJu`: `1 -> 1`

즉 sample B target count는 이미

- 증가
- 감소
- 유지

를 모두 보였고,
same `promptSha256` + same `replaySeed` + same `systemFingerprint`
조건에서도 `ai_score` drift가 남았습니다.

이 상황에서 `unexpected increase` 하나만 fail 로 두면:

- live response variability를 코드 회귀로 과대 판정할 수 있고
- 같은 조건의 `unexpected decrease` 와도 비대칭이 됩니다

따라서 현재 단계에서는:

- `unexpected increase` 는 warning
- trace/artifact review 필수
- `rule-only` hard gate 유지

가 더 일관된 기준입니다.

## 다음 작업

1. replay script summary를 `target count metric 중심` 으로 더 명시적으로 출력할지 결정
2. sample B warning을 어떤 artifact 조건에서만 출력할지 정교화
3. 필요하면 `real-openai` replay를 nightly/diagnostic lane으로 분리
