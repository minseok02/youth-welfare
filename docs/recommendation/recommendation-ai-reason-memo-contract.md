# 추천 메모 계약

## 목적

이 문서는 사용자에게 보이는 `추천 메모`가 어느 코드 경로에서 만들어지고, 어떤 품질 기준을 통과해야 하는지 고정합니다.

프론트의 표시명은 `추천 메모` 이지만 API 필드는 `aiReason` 입니다. 정책 설명 본문인 `description` / `summary` 와 섞어 읽지 않습니다.

## 현재 코드 경로

1. `RealtimeAiGateway`
   - OpenAI 응답 JSON의 `reason` 을 읽습니다.
   - `RecommendationAiReasonSanitizer.sanitize(...)` 로 한 줄 20자 이내로 정규화한 뒤 `ScoredCandidate.aiReason` 에 넣습니다.
2. `AiScoringService`
   - 군집 캐시 hit 시 cache `aiReason` 도 다시 sanitize 합니다.
   - cache miss 뒤 새 cache write 에도 sanitize 된 reason 만 저장합니다.
3. `JpaClusterAiScoreCache`
   - `cluster_ai_results.ai_reason` read/write 양쪽에서 sanitize 합니다.
4. `RecommendationPersistenceService`
   - `user_recommendations.ai_reason` 저장 전 sanitize 합니다.
5. `RecommendationResponse`
   - 기존 DB row 에 긴 값이나 개행이 남아 있어도 API 응답에서 sanitize 합니다.
6. `MainPage`
   - `aiReason` 이 있으면 `추천 메모`로 표시합니다.
   - `aiReason` 이 없으면 `youthMidLabel`, `provisionMethodLabel`, `gov24BenefitTypeLabel`, `gov24ServiceFieldLabel` 같은 canonical summary field 기반 fallback 문구를 표시합니다.

## 품질 기준

- blank / whitespace-only reason 은 `null` 로 내려보냅니다.
- 개행, 탭, 중복 공백은 단일 공백으로 줄입니다.
- 사용자 카드에 보이는 문구는 20 code point 이내로 제한합니다.
- DB 컬럼은 `VARCHAR(500)` 이지만, 제품 메모 계약은 DB 한도가 아니라 prompt 계약인 `20자 이내` 를 기준으로 합니다.
- `NOT_REQUESTED` 후보는 OpenAI reason 이 없는 것이 정상일 수 있으므로, 이를 AI 품질 실패로 바로 해석하지 않습니다.
- 메모 품질 이슈를 다시 볼 때는 먼저 `ai_status` 분포를 봅니다. `NOT_REQUESTED` 비중이 높으면 문구 튜닝보다 AI 요청 top-N / 상단 후보 composition 문제가 우선입니다.

## 테스트 기준

관련 단위 테스트:

- `RecommendationAiReasonSanitizerTest`
- `RecommendationResponseTest`
- `AiScoringServiceTest`
- `JpaClusterAiScoreCacheTest`
- `RecommendationPersistenceServiceTest`
- `RealtimeAiGatewayTest`

권장 검증:

```bash
cd backend
./gradlew test --tests 'com.example.welfare.recommend.*'
./gradlew test
```

프론트 표시 경로까지 정적 검증할 때:

```bash
cd frontend
npm run lint
npm run build
```

## 트러블슈팅

### 메모가 안 보인다

1. `/api/recommendations` 응답의 `aiReason` 이 null 인지 봅니다.
2. `aiStatus` 가 `NOT_REQUESTED` 이면 fallback 메모가 나오는 것이 정상입니다.
3. `SCORED` 인데 `aiReason` 이 null 이면 OpenAI 응답 reason 이 blank 였거나 sanitizer에서 blank 처리된 것입니다.

### 메모가 너무 길거나 개행이 보인다

현재 계약에서는 API 응답 전에 `RecommendationResponse` 에서 다시 sanitize 하므로, 최신 코드 기준으로는 긴 값/개행이 화면까지 내려오면 안 됩니다.
이 경우 먼저 배포된 app runtime 이 최신 이미지인지 확인합니다.

### 같은 사용자인데 메모가 다르게 보인다

`personal=false` refresh 는 같은 사용자 최근 refresh 마커가 살아 있으면 저장 row 를 재사용할 수 있습니다.
`personal=true` 는 군집 캐시를 무시하고 개인 프로필 기반 실시간 AI 경로를 탑니다.
비교 시에는 refresh mode, `recommendedAt`, `aiStatus`, `aiReason` 을 같이 봅니다.
