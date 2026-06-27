# OpenAI Runtime Contract

이 문서는 현재 코드 기준 OpenAI 관련 runtime 계약을 한 장으로 고정합니다.

active 문서 기준 현재 recommendation 트랙은 [recommendation-current-state.md](../recommendation/recommendation-current-state.md) 의
`KEEP_OBSERVING / WAIT_FOR_REAL_USER_TRAFFIC` 상태입니다.
즉 recommendation prompt retune은 지금 active lane이 아니고, 이 문서는 **장애/빈 키/응답 이상 시 각 경로가 어떻게 동작해야 하는지** 만 정리합니다.

## 목적

- recommendation / chat / semantic retrieval / embedding rebuild 의 OpenAI 계약을 같은 언어로 읽게 합니다.
- `blank key`, `timeout`, `response parse failure`, `embedding unavailable` 같은 장애를 경로별로 다르게 해석하지 않게 합니다.
- prompt/privacy 경계가 어디까지 active contract인지 고정합니다.

## 공통 원칙

1. OpenAI는 best-effort 외부 의존성입니다.
2. 직접 식별자는 prompt/embedding query에 보내지 않습니다.
3. recommendation 과 chat 은 목적이 다르므로 fallback 계약도 분리해서 읽습니다.
4. recommendation prompt는 현재 product-deferred lane이라, 기능 튜닝보다 계약 고정이 우선입니다.

## 외부 제공자 데이터 처리 기준

공식 OpenAI 문서 기준으로 API platform 입력/출력은 기본적으로 모델 학습이나 개선에 사용되지 않습니다.
다만 API 사용 시 abuse monitoring log 는 기본 생성될 수 있고, 이 로그에는 prompt/response 같은 customer content 가 포함될 수 있으며 기본 retention 은 최대 30일로 안내됩니다.
따라서 이 프로젝트의 보안 기준은 외부 제공자 정책에만 기대지 않고, **OpenAI로 나가는 payload 자체에서 직접 식별자를 제거하거나 범주값만 보내는 것** 입니다.

참고:

- https://openai.com/business-data/
- https://developers.openai.com/api/docs/guides/your-data#default-usage-policies-by-endpoint

## 경로별 계약

### 1. recommendation AI scoring

구현:

- [RealtimeAiGateway.java](../../backend/src/main/java/com/example/welfare/recommend/gateway/RealtimeAiGateway.java)

입력 원칙:

- 사용자 범주값만 전송
  - 나이대
  - 거주지역(`sido`)
  - 소득분위
  - 취업상태
- 이름, 이메일, 생년월일, 전화번호 같은 직접 식별자는 전송하지 않음

장애 계약:

- `openai.api-key` blank / sentinel / force-rule-only
  - OpenAI 호출 생략
  - 요청된 후보는 `RULE_ONLY`
- HTTP 실패 / timeout / 응답 파싱 실패
  - 요청된 후보는 `CALL_FAILED`
- partial result 누락
  - 누락된 후보는 `PARTIAL_MISSING`
- top-N 밖 후보
  - `NOT_REQUESTED`

주의:

- 현재 recommendation prompt는 local 청년 정책 가중 강화 같은 product tuning을 직접 표현하지 않습니다.
- 이 경계는 [recommendation-primary-audience-exclusion-decision-memo.md](../recommendation/recommendation-primary-audience-exclusion-decision-memo.md) 기준으로 유지합니다.

### 2. chat answer generation

구현:

- [ChatAiGateway.java](../../backend/src/main/java/com/example/welfare/chat/gateway/ChatAiGateway.java)

입력 원칙:

- 최근 대화 / 현재 질문은 [SensitiveTextRedactor.java](../../backend/src/main/java/com/example/welfare/global/util/SensitiveTextRedactor.java) 를 거쳐 전송
- 정책 후보와 evidence 안에서만 답변
- 후보 밖 `service_id` 생성 금지

응답 원칙:

- JSON only
- 모르면 모른다고 답
- 자격/지급 확정 표현 금지
- 불확실하면 확인 필요 항목을 명시

장애 계약:

- `openai.api-key` blank
  - `generateAnswer(...) -> null`
  - 상위 service 가 policy-grounded fallback answer 사용
- HTTP 실패 / timeout / parse 실패
  - `generateAnswer(...) -> null`
  - 상위 service 가 fallback answer 사용

### 3. semantic retrieval query embedding

구현:

- [ChatSemanticSearchService.java](../../backend/src/main/java/com/example/welfare/chat/service/ChatSemanticSearchService.java)
- [OpenAiChatEmbeddingGateway.java](../../backend/src/main/java/com/example/welfare/chat/gateway/OpenAiChatEmbeddingGateway.java)

입력 원칙:

- 질문은 redaction 후 embedding query 생성
- preferred terms 는 검색 품질용 보조 신호로 뒤에 붙임

장애 계약:

- OpenAI embedding key blank 또는 force-local-fallback
  - local deterministic embedding 사용
- embedding HTTP 실패 / timeout
  - local deterministic embedding 사용

즉 semantic retrieval 은 **서비스 연속성 우선** 경로입니다.

### 4. policy chunk embedding rebuild

구현:

- [PolicyChunkEmbeddingService.java](../../backend/src/main/java/com/example/welfare/chat/service/PolicyChunkEmbeddingService.java)
- [OpenAiChatEmbeddingGateway.java](../../backend/src/main/java/com/example/welfare/chat/gateway/OpenAiChatEmbeddingGateway.java)

장애 계약:

- rebuild 는 `embedDocumentsStrict(...)` 만 사용
- OpenAI unavailable / request failure 시 예외로 중단
- local fallback vector 를 DB에 저장하지 않음

즉 embedding rebuild 는 **서비스 연속성보다 데이터 무결성 우선** 경로입니다.

### 5. collect batch embedding refresh

구현:

- [PolicyEmbeddingRefreshRequestService.java](../../backend/src/main/java/com/example/welfare/policy/service/PolicyEmbeddingRefreshRequestService.java)
- [CollectSourceExecutionService.java](../../backend/src/main/java/com/example/welfare/collect/service/CollectSourceExecutionService.java)

장애 계약:

- 수집 저장 자체는 source별 collect transaction/retry 경계에서 판정
- batch scope 종료 후 embedding refresh는 저장 후처리
- strict embedding refresh가 OpenAI unavailable 로 실패하면 warning 으로 기록
- 이미 성공한 collect 응답을 embedding 후처리 실패 때문에 500으로 바꾸지 않음

즉 collect batch embedding refresh 는 **수집 저장 성공을 보존하는 best-effort 후처리** 입니다.

## 개인정보 최소화 현재 범위

현재 redaction 범위:

- 이메일
- 휴대전화
- `YYYY-MM-DD`, `YYYY.MM.DD`, `YYYY/MM/DD`, `YYYYMMDD`, `YYYY년 M월 D일` 형태 생년월일
- 주민등록번호 / 외국인등록번호 형태
- 계좌번호 라벨형 표현
- 이름 라벨형 표현
- 주소 라벨형 표현
- 학교/회사/근무지/소속 라벨형 표현

주의:

- 자유서술 전체를 완전히 PII-free 로 만드는 것은 아닙니다.
- 현재 계약은 “라벨이 붙은 자기소개형 값”과 직접 식별자를 우선 제거하는 수준입니다.
- 한국어 날짜/국제번호/외국인등록번호 같은 변형은 `SensitiveTextRedactorTest` 에 corpus로 고정합니다.

## 기본 검증

- recommendation prompt guard
  - [RealtimeAiGatewayTest.java](../../backend/src/test/java/com/example/welfare/recommend/gateway/RealtimeAiGatewayTest.java)
- chat prompt / request guard
  - [ChatAiGatewayTest.java](../../backend/src/test/java/com/example/welfare/chat/gateway/ChatAiGatewayTest.java)
- semantic query redaction
  - [ChatSemanticSearchServiceTest.java](../../backend/src/test/java/com/example/welfare/chat/service/ChatSemanticSearchServiceTest.java)
- embedding strict refresh
  - [PolicyChunkEmbeddingServiceTest.java](../../backend/src/test/java/com/example/welfare/chat/service/PolicyChunkEmbeddingServiceTest.java)
- collect batch embedding refresh
  - [PolicyEmbeddingRefreshRequestServiceTest.java](../../backend/src/test/java/com/example/welfare/policy/service/PolicyEmbeddingRefreshRequestServiceTest.java)
  - `bash deploy/smoke/run-local-gov24-collect-embedding-boundary-smoke.sh`
- redactor corpus
  - `SensitiveTextRedactorTest`

## 재배포 판단

이 문서만 바뀌면 재배포 불필요입니다.

아래가 바뀌면 `app` 재배포가 필요합니다.

- `RealtimeAiGateway`
- `ChatAiGateway`
- `OpenAiChatEmbeddingGateway`
- `PolicyChunkEmbeddingService`
- `PolicyEmbeddingRefreshRequestService`
- `SensitiveTextRedactor`
- `application.yml`

## 한 줄 정리

- recommendation: **rule-only / call-failed / partial-missing 상태를 구분하면서 현재 product contract 유지**
- chat: **grounded answer + fallback 허용**
- semantic query: **local fallback 허용**
- embedding rebuild: **local fallback 저장 금지, strict fail**
- collect batch embedding refresh: **수집 저장 성공 보존, 후처리 실패 warning-only**
