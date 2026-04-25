# 챗봇 구현 계획

이 문서는 현재 코드베이스를 기준으로 2차 기능인 챗봇을 어떻게 붙일지 정리한 설계 메모입니다.
기준 문서는 [architecture.md](./architecture.md), [srs-v2.10.md](./srs-v2.10.md), [phase-plan.md](./phase-plan.md)입니다.

## 현재 상태

- 백엔드는 `policy`, `user`, `recommend`, `notification` 모듈까지 구현되어 있습니다.
- OpenAI 호출은 추천 모듈의 [`RealtimeAiGateway`](../backend/src/main/java/com/example/welfare/recommend/gateway/RealtimeAiGateway.java)에서 이미 사용 중입니다.
- 인증은 JWT + HttpOnly refresh cookie 구조이며, 정책 조회 API는 비로그인 허용, 추천/마이페이지는 로그인 필수입니다.
- 프론트는 `/chat` 라우트 자리만 있고 실제 화면/백엔드 API는 아직 없습니다.
- SRS 기준 챗봇은 로그인 전용이며, 로그아웃 시 세션 삭제가 필요합니다.

## 설계 원칙

- `chat/` 모듈은 별도로 만들고 `policy/` 조회만 재사용합니다.
- `chat/`에서 `recommend/` 계산 로직을 직접 호출하지 않습니다.
- 답변은 "정책 검색/설명"에 집중하고 추천 재계산은 하지 않습니다.
- LLM에 개인 식별 정보는 보내지 않고, 필요한 경우 나이대·지역·소득분위 같은 범주값만 보냅니다.
- 답변에는 근거 정책 제목과 `serviceId`를 함께 포함해 프론트가 상세 페이지 링크를 만들 수 있게 합니다.

## 권장 MVP 범위

### 1. 사용자 경험

- 로그인 사용자가 `/chat`에서 질문을 입력합니다.
- 챗봇은 최대 3~5개의 관련 정책을 찾아 요약 설명합니다.
- 답변마다 관련 정책 카드 목록을 같이 내려줍니다.
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
5. LLM 응답을 구조화 JSON으로 파싱
6. 답변 본문과 참조 정책 ID를 저장
7. 프론트에 `answer + references + sessionId` 반환

## 데이터 모델 초안

### `chat_sessions`

- `id`
- `user_id`
- `title`
- `last_message_at`
- `created_at`
- `updated_at`

인덱스:

- `(user_id, last_message_at desc)`

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
- 응답: `sessionId`, `title`

### `GET /api/chat/sessions`

- 내 최근 세션 목록 조회

### `GET /api/chat/sessions/{sessionId}/messages`

- 세션 메시지 조회

### `POST /api/chat/sessions/{sessionId}/messages`

- 사용자 질문 전송
- 응답 예시:

```json
{
  "success": true,
  "data": {
    "sessionId": 12,
    "answer": "서울 거주 미취업 청년이라면 청년월세지원과 국민취업지원제도를 먼저 보세요.",
    "references": [
      {
        "serviceId": 1829,
        "title": "청년일자리 도약장려금"
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
2. DB 마이그레이션 추가
   - `chat_sessions`, `chat_messages` 테이블과 인덱스만 먼저 추가
3. 엔티티/리포지토리 골격 추가
   - JPA 엔티티, enum, repository만 만들어 CRUD 기반을 확보
4. 세션 CRUD부터 연결
   - 세션 생성/목록/삭제, 메시지 목록 조회를 AI 없이 먼저 연결
5. `ChatPolicyService` 골격 추가
   - 정책 검색 전용 조회 함수와 참조 정책 DTO를 먼저 고정

위 5개가 끝나면 그다음부터 `ChatAiGateway`, JSON 파서, 메시지 전송 API를 붙이는 순서가 자연스럽다.

## 먼저 하지 않을 것

- 추천 재계산 요청
- 자유 주제 일반 상담
- 외부 검색 연동
- 장문 히스토리 무제한 보관
- 추천 모듈 내부 점수 직접 노출

## 구현 전 체크포인트

- 챗봇 답변의 근거 정책이 항상 상세 페이지로 연결되는지
- 비로그인 접근 시 `/login`으로 유도할지, 403만 줄지 프론트 정책 통일
- 로그아웃과 회원탈퇴 모두 세션 삭제를 보장하는지
- 프롬프트 길이 제한 때문에 정책 후보 수와 대화 턴 수 상한이 필요한지
- 실패 시 "답변 생성 실패"만 보여주지 말고 정책 검색 결과라도 내려줄지
