# live AI reason 변화 패턴 메모

2026-05-02 기준 artifact:

- cache clear real-openai replay: `/tmp/tmp.TBDFrxfGqo`

전제:

- 실행:
  - `USE_REAL_OPENAI_FOR_REPLAY=true`
  - `KEEP_ARTIFACTS=true`
  - `CLEAR_CLUSTER_AI_CACHE_BEFORE_REPLAY=true`
  - `deploy/smoke/run-local-education-priority-replay.sh`
- 핵심 summary:
  - `A_reason_text_changed=15`
  - `B_reason_text_changed=15`
  - `A_reason_membership_changed=8`
  - `B_reason_membership_changed=0`
  - `A_top10_target=3->5`
  - `B_top10_target=0->2`

## 1. sample A 패턴

sample A는 `text_changed 15 + entered/exited 8` 이 섞였습니다.

`entered` 정책군:

- `지역형 마이스 전문인력 양성 프로그램`
- `산업혁신 인재성장 지원(해외연계)`
- `화이트바이오산업 전문인력 양성`
- `반도체 특성화대학 지원사업`

공통점:

- 모두 `compat=기타 + youth_major=교육` 계열 교육 타깃군이다.
- off 에서는 top snapshot 밖에 있었고 on 에서는 안쪽으로 진입했다.
- canonical summary prompt가 직접 문장을 바꿨다기보다, 교육 target row가 상위권으로 더 들어오면서 reason artifact에도 같이 나타난 케이스다.

`text_changed` 문장 패턴:

- `도움이 될 수 있음` -> `실질적인 도움이 됨`
- `특정 분야에 한정/국한` 식의 제약 서술 강화
- `관심이 있는 청년` / `특정 기술이 요구됨` 같이 분야 적합성 강조
- `취업 준비에 도움이 될 수 있음` 처럼 실무/취업 연결 표현 재서술

예:

- `인천 청년 고용안심 지원사업`
  - off: `매우 유용할 것으로 판단됨`
  - on: `실질적인 도움이 됨`
- `청년콘텐츠 우수인재양성`
  - off: `창의적 실무인재 양성`
  - on: `창의성을 발휘할 수 있는 기회`
- `친환경 자원순환센터...`
  - off: `일반적인 취업 지원에는 제한적`
  - on: `관심이 있는 청년에게 실무 경험 제공`

해석:

- sample A에서는 canonical summary prompt가 교육/분야 적합성 표현을 더 직접적으로 드러내는 쪽으로 움직였을 가능성이 있다.
- 다만 entered/exited 8건이 같이 섞여 있어서, pure text effect만 따로 보면 과대해석하면 안 된다.

## 2. sample B 패턴

sample B는 `text_changed 15`, `membership_changed 0` 이었습니다.

즉:

- top snapshot 구성은 유지됐고,
- 같은 정책군에서 이유 문장 wording만 바뀌었다.

반복 패턴:

- `연관성이 낮을 수 있음` -> `연관성이 낮음`
- `도움이 될 수 있음` -> `큰 도움이 될 것임`
- `특정 분야에 국한` 유지 + 앞 문장 구조만 재배치
- 주거 정책에서 `주거비 부담 완화` / `매우 중요함` 같은 직접 효익 강조

예:

- `청년 주택임차보증금 이자 지원 대출연장`
  - off: `실질적인 도움이 될 수 있음`
  - on: `큰 도움이 될 것임`
- `인천시 청년월세 지원사업`
  - off: `매우 중요한 정책임`
  - on: `매우 중요함`
- `청년콘텐츠 우수인재양성`
  - off: `직접적인 연관성이 낮을 수 있음`
  - on: `직접적인 연관성이 낮음`

해석:

- sample B에서도 같은 규모의 text drift가 발생했기 때문에,
- 현재 run만으로는 “canonical summary prompt가 sample A를 특별히 개선했다”보다
  “live AI 자체가 wording을 꽤 크게 재작성한다”는 해석이 먼저다.

## 3. 현재 결론

이 artifact는 아래 둘을 동시에 보여 준다.

1. canonical summary prompt를 넣으면 교육 target row가 sample A에서 상위권으로 더 올라갈 수 있다.
2. 하지만 live AI는 control sample B에서도 대규모 wording drift를 만든다.

따라서 현재 단계 결론은:

- `prompt 영향 가능성 있음`
- `하지만 control drift와 분리되지 않았음`
- `진단 증거로만 유지`

이 맞다.
