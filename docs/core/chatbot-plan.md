# 챗봇 구현 계획

전체 cross-cutting 구조/확장 설계 문서 진입점은 [system-docs-index.md](./system-docs-index.md)를 먼저 봅니다.
이 문서는 현재 코드베이스를 기준으로 챗봇 구조와 남은 확장 방향을 정리한 설계 메모입니다.
기준 문서는 [architecture.md](../architecture.md), [srs-v2.10.md](./srs-v2.10.md), [phase-plan.md](../phase-plan.md)입니다.
현재 OpenAI runtime/fallback/privacy 계약은 [openai-runtime-contract.md](./openai-runtime-contract.md) 를 source of truth로 같이 봅니다.

## 현재 상태

- 백엔드는 `policy`, `user`, `recommend`, `notification` 모듈까지 구현되어 있습니다.
- OpenAI 호출은 추천 모듈의 [`RealtimeAiGateway`](../backend/src/main/java/com/example/welfare/recommend/gateway/RealtimeAiGateway.java)에서 이미 사용 중입니다.
- 인증은 JWT + HttpOnly refresh cookie 구조이며, 정책 조회 API는 비로그인 허용, 추천/마이페이지는 로그인 필수입니다.
- 현재는 `/chat` 화면, `chat_sessions/chat_messages` 저장, branch suggestion, retrieval snapshot, grounding evidence, OpenAI JSON 응답 파싱까지 구현돼 있습니다.
- 챗봇은 로그인 전용이며, 로그아웃/회원탈퇴 시 세션 cleanup 경로도 따로 있습니다.
- 2026-06-03 기준 continuity 보강으로 짧은 후속 질문은 `직전 질문 + 직전 branch + 직전 추천 정책` 맥락을 retrieval/AI prompt에 bounded 하게 다시 싣습니다.
- 2026-06-03 기준 branch suggestion 다음의 자유 입력(`월세 쪽으로`, `청약으로`)도 최근 `branchSuggestionKeysJson` 안에서 leaf branch를 다시 해석해 retrieval branch로 계승합니다.
- 2026-06-03 기준 prompt에는 `최근 질문 흐름`, `최근 탐색 흐름`, `최근 제안 갈래`, `최근 추천 정책`을 묶은 bounded session summary memory도 같이 실립니다.
- 2026-06-03 기준 `chat_sessions.context_state_json` 에 주거 도메인 한정 구조화 세션 상태를 저장합니다. 현재 저장 범위는 `activeBranchKey`, `anchorQuestion`, `recentTopics`, `recentPolicyTitles/Ids`, `suggestedBranchKeys` 이고, 긴 자연어 요약 전체를 DB에 저장하는 방식은 아직 열지 않았습니다.
- 로컬 follow-up runtime QA는 [run-local-chat-followup-smoke.sh](/home/ubuntu/youth-welfare/deploy/smoke/run-local-chat-followup-smoke.sh:1) 로 `첫 질문 -> 후속 질문 -> messages 확인` 경로를 bounded 하게 재검증합니다.
- 실사용 판단용 follow-up 시나리오 QA는 [run-local-chat-followup-scenario-audit.sh](/home/ubuntu/youth-welfare/deploy/smoke/run-local-chat-followup-scenario-audit.sh:1) 로 `주거 follow-up`, `branch suggestion 자유 입력`, `혼합 주제`, `일자리 자유 입력`을 묶어 확인합니다.
- 같은 시나리오 audit 기준으로 주거 한정 구조화 memory 도입 전 결과는 `POLICY_GROUNDED 1 / CLARIFICATION 3 / decision=CONSIDER_LONG_TERM_MEMORY` 였고, 1차 도입 후에는 `POLICY_GROUNDED 3 / CLARIFICATION 1 / decision=HOLD_LONG_TERM_MEMORY` 로 개선됐습니다.
- 마지막 남은 `서울 월세 지원 알려줘 -> 그럼 전세는?` 케이스는
  - `전세는` 같은 조사 결합 토큰도 housing branch match에 걸리게 하고,
  - `월세 -> 전세`처럼 housing leaf branch가 바뀌면 retrieval query에서 이전 월세 topic/policy bias를 비우고,
  - 주거 follow-up에서 참조 정책과 구체 답변이 있으면 AI clarification flag를 bounded post-processing으로 정규화
  하는 방식으로 닫았습니다.
- 최종 시나리오 audit은 `POLICY_GROUNDED 4 / CLARIFICATION 0 / decision=HOLD_LONG_TERM_MEMORY` 입니다.
- 2026-06-19 기준 정책 상세에서 `/chat?coachPolicyId={serviceId}` 로 진입하는 `AI 신청 준비 코칭` 흐름을 추가했습니다. 일반 챗봇 검색 흐름은 그대로 두고, `coachPolicyId` 가 있을 때만 해당 정책을 고정 후보로 삼아 신청 대상/기간/방법/제출서류/공식·참고 링크를 단계별로 안내합니다.
- 신청 코칭 action link 생성은 별도 factory에서 담당합니다. `referenceUrlsJson` 본문 추출 URL에 `)`, 조사, 문장 꼬리표가 붙어 저장된 실제 데이터가 있어, 챗봇 CTA로 내리기 전에 `http/https` URL prefix와 host를 다시 확인하고 trailing noise를 제거합니다.
- 아직 하지 않은 것은 DB에 별도 저장되는 장기 세션 요약/압축(memory persistence)과 multi-turn 전용 ranking 재학습입니다.

## 왜 주거만 먼저 붙였는가

- 이번 continuity QA에서 실제로 깨지던 축이 주거 follow-up 이었습니다.
- `housing-followup`, `housing-branch-freeform`, `mixed-topic-memory` 는 기존 구조에서 마지막 턴이 `CLARIFICATION` 으로 끝났고, 반면 `job-branch-freeform` 은 `POLICY_GROUNDED` 로 유지됐습니다.
- 그래서 처음부터 전 도메인 long-term memory를 열기보다, 문제 구간이 분명한 `housing-*` branch에만 구조화 state를 먼저 붙였습니다.
- 이 선택은 구현 범위를 줄이기 위한 임의 축소가 아니라, 시나리오 측정 결과에 따라 memory 오염 범위를 통제하기 위한 의도적 제한입니다.
- 현재 판단 기준은 이렇습니다.
  - 주거 한정 state만으로도 `3/4` 시나리오가 grounded 되면 전 도메인 long-term memory는 보류
  - 그래도 housing follow-up 이 계속 clarification 위주면 그때만 다음 단계 memory를 검토

## 설계 원칙

- `chat/` 모듈은 별도로 만들고 `policy/` 조회만 재사용합니다.
- `chat/`에서 `recommend/` 계산 로직을 직접 호출하지 않습니다.
- 답변은 "정책 검색/설명"에 집중하고 추천 재계산은 하지 않습니다.
- LLM에 개인 식별 정보는 보내지 않고, 필요한 경우 나이대·지역·소득분위 같은 범주값만 보냅니다.
- 답변에는 근거 정책 제목과 `serviceId`를 함께 포함해 프론트가 상세 페이지 링크를 만들 수 있게 합니다.
- 신청 코칭은 실제 신청서 제출/대행이 아니라 공식 신청 전 준비 보조입니다. 공식 신청 링크와 공고/서류/관련 링크를 구분해서 보여주고, 최종 자격/서류는 운영기관 원문 또는 담당 기관 확인으로 안내합니다.

## 권장 MVP 범위

### 1. 사용자 경험

- 로그인 사용자가 `/chat`에서 질문을 입력합니다.
- 챗봇은 최대 3~5개의 관련 정책을 찾아 요약 설명합니다.
- 답변마다 관련 정책 카드 목록을 같이 내려줍니다.
- 정책 상세의 `AI와 신청 준비하기` 버튼은 새 채팅 세션을 열고 해당 정책을 고정한 신청 준비 코칭 메시지를 자동 전송합니다.
- 새 대화를 시작하면 새 세션을 만들고, 최근 대화 목록은 최대 N개만 보여줍니다.

### 2. 백엔드 흐름

```text
ChatController
  -> ChatService
      -> ChatSessionRepository / ChatMessageRepository
      -> ChatPolicyService
          -> WelfareServiceRepository
          -> WelfareServiceDetailRepository
      -> ChatAiGateway
          -> OpenAI API
```

권장 처리 순서:

1. 사용자 질문 저장
2. 질문에서 키워드/카테고리/지역 신호 추출
3. `welfare_services`에서 후보 정책 조회
4. 상위 후보 + 최근 대화 일부를 LLM에 전달
   - 신청 코칭 모드에서는 검색 후보 대신 `coachPolicyId` 정책 상세를 고정 후보로 전달
5. LLM 응답을 구조화 JSON으로 파싱
6. 답변 본문과 참조 정책 ID를 저장
7. 프론트에 `answer + references + sessionId` 반환
   - 신청 코칭 참조 정책에는 `actionLinks` 로 공식 신청/공고·서류/관련 사이트 링크를 함께 반환

## 데이터 모델 초안

### `chat_sessions`

- `id`
- `user_key`
- `user_id` (`legacy` 호환 컬럼, drop 전까지 임시 유지)
- `title`
- `last_message_at`
- `created_at`
- `updated_at`

인덱스:

- `(user_key, last_message_at desc)`

### `chat_messages`

- `id`
- `session_id`
- `role` (`USER`, `ASSISTANT`, `SYSTEM`)
- `content`
- `referenced_service_ids` JSON nullable
- `created_at`

인덱스:

- `(session_id, created_at)`

운영 기준:

- 로그아웃 시 해당 유저 세션/메시지 삭제
- 회원탈퇴 시도 같은 정리 로직 재사용
- 세션당 최근 20~30턴만 프롬프트에 포함

## API 초안

### `POST /api/chat/sessions`

- 새 세션 생성
- 요청: `title` optional
- 응답: `sessionId`, `title`, `lastMessageAt`, `createdAt`

### `GET /api/chat/sessions`

- 내 최근 세션 목록 조회
- 응답 항목: `sessionId`, `title`, `lastMessageAt`, `createdAt`

### `GET /api/chat/sessions/{sessionId}/messages`

- 세션 메시지 조회
- 응답 항목: `messageId`, `role`, `content`, `referencedServiceIds`, `createdAt`

### `POST /api/chat/sessions/{sessionId}/messages`

- 사용자 질문 전송
- 요청: `content`
- 응답 예시:

```json
{
  "success": true,
  "data": {
    "sessionId": 12,
    "answer": "서울 거주 미취업 청년이라면 청년월세지원과 국민취업지원제도를 먼저 보세요.",
    "needsClarification": false,
    "references": [
      {
        "serviceId": 1829,
        "title": "청년일자리 도약장려금",
        "reason": "질문의 취업 준비 상황과 직접 연결됩니다."
      }
    ]
  }
}
```

### `DELETE /api/chat/sessions/{sessionId}`

- 사용자가 특정 세션 삭제

## 프롬프트 방향

시스템 프롬프트 기본 원칙:

- 한국 청년 복지 정책 상담 보조 역할만 수행
- 제공된 정책 후보 안에서만 답변
- 모르면 모른다고 답변
- 자격/지급 확정 표현 금지
- 반드시 JSON만 응답

응답 스키마 예시:

```json
{
  "answer": "string",
  "references": [
    {
      "service_id": 1,
      "reason": "질문과 연결된 이유"
    }
  ],
  "needs_clarification": false
}
```

## 현재 코드에서 바로 재사용할 부분

- 인증/인가: `SecurityConfig`, JWT 필터
- OpenAI 호출 방식: `RealtimeAiGateway`의 `WebClient` + `openai.api-key`
- 정책 조회 엔티티/리포지토리: `WelfareServiceRepository`, `WelfareServiceDetailRepository`
- 상세 이동 경로: 프론트 `/policies/:id`

## 작은 task 단위 권장 순서

1. 문서/계약 확정: `chatbot-plan.md`, API 응답 형식, 세션 정책 확정
2. DB 마이그레이션: `chat_sessions`, `chat_messages` 추가
3. 백엔드 기본 CRUD: 세션 생성/조회/삭제, 메시지 조회
4. 정책 조회 전용 `ChatPolicyService` 추가
5. `ChatAiGateway`와 JSON 응답 파서 구현
6. `POST /api/chat/sessions/{id}/messages` 구현
7. 로그아웃/회원탈퇴 시 세션 삭제 연동
8. 프론트 `/chat` 화면, 로그인 가드, 세션 목록 UI 구현
9. 테스트: 세션 권한, 메시지 저장, 정책 참조, JSON 파싱 실패 fallback

## 바로 시작할 첫 task

챗봇은 아래 순서로 착수하면 한 번에 너무 넓게 열지 않고 진행할 수 있다.

1. 응답 DTO 확정
   - `POST /api/chat/sessions/{sessionId}/messages` 응답 필드 `sessionId`, `answer`, `references`, `needsClarification` 고정
   - 세션/메시지 목록 필드 `sessionId`, `messageId`, `referencedServiceIds`, `lastMessageAt` 고정
2. DB 마이그레이션 추가
   - `chat_sessions`, `chat_messages` 테이블과 인덱스만 먼저 추가
3. 엔티티/리포지토리 골격 추가
   - JPA 엔티티, enum, repository만 만들어 CRUD 기반을 확보
4. 세션 CRUD부터 연결
   - 세션 생성/목록/삭제, 메시지 목록 조회를 AI 없이 먼저 연결
5. `ChatPolicyService` 골격 추가
   - 정책 검색 전용 조회 함수와 참조 정책 DTO를 먼저 고정

위 5개가 끝나면 그다음부터 `ChatAiGateway`, JSON 파서, 메시지 전송 API를 붙이는 순서가 자연스럽다.

## 현재까지 완료된 선행 작업

- 응답 DTO/API 계약 고정
- DB 마이그레이션 추가 (`chat_sessions`, `chat_messages`)
- 엔티티/리포지토리 골격 추가
- 세션 CRUD API 구현 (`POST/GET/DELETE /api/chat/sessions`)
- 메시지 목록 조회 API 구현 (`GET /api/chat/sessions/{sessionId}/messages`)
- `ChatPolicyService` 골격 추가
- 메시지 전송 API 구현 (`POST /api/chat/sessions/{sessionId}/messages`)
- 챗봇 OpenAI 프롬프트/응답 스키마 구현
  - `ChatAiGateway` + JSON 파서 + 후보 정책 allowlist 검증 + fallback 연결
- 로그아웃/회원탈퇴 시 챗 세션 삭제 연동
  - `AuthService.logout*`, `UserService.withdraw`에서 공용 정리 서비스로 세션/메시지 삭제 보장
- 챗봇 요청 rate limit / abuse 방지
  - Redis fixed-window로 사용자별 메시지 전송 횟수를 제한하고 초과 시 `CH002` 429 반환
- 프론트 `/chat` 실제 화면 및 로그인 가드 구현
  - 세션 목록/메시지 목록/질문 전송/정책 상세 이동 연결
  - 비로그인 접근은 `/login`으로 리다이렉트하고, 로그인 후 원래 `/chat`으로 복귀
  - 프론트 로그아웃도 `/api/auth/logout`을 호출해 서버 세션 정리와 refresh cookie 무효화를 보장

## 다음 바로 할 작업

- 현재 필수 범위 완료. 이후는 운영 점검과 UI 미세조정 단계

## 먼저 하지 않을 것

- 추천 재계산 요청
- 자유 주제 일반 상담
- 외부 검색 연동
- 장문 히스토리 무제한 보관
- 추천 모듈 내부 점수 직접 노출

## 구현 전 체크포인트

- 챗봇 답변의 근거 정책이 항상 상세 페이지로 연결되는지
- 비로그인 접근 시 `/login`으로 유도할지, 403만 줄지 프론트 정책 통일
- 프롬프트 길이 제한 때문에 정책 후보 수와 대화 턴 수 상한이 필요한지
- 실패 시 "답변 생성 실패"만 보여주지 말고 정책 검색 결과라도 내려줄지
