# PostgreSQL Phase 3 Spec - 챗봇 Retrieval Foundation

문서군 진입점: [postgres-refactor-spec-index.md](./postgres-refactor-spec-index.md)

## 목표

챗봇을 단순 FULLTEXT 후보 검색기에서 broad query 분기와 grounded answer가 가능한 탐색 인터페이스로 바꾼다.

## 브랜치

- 권장 브랜치: `feat/backend-chat-retrieval-foundation`

## 범위

- broad query intent branch
- 정책 chunk 테이블 도입
- grounded answer 구조
- 질문 -> branch -> 후보 정책/근거 -> 답변 흐름 확립

## 비범위

- `pgvector`
- embedding 생성
- 추천/챗봇 공통 엔진 완전 통합

## 주요 수정/추가 대상

- `chat/` 서비스 계층
- `ChatConversationService`
- `ChatAiGateway`
- 신규 `policy_chunks` 테이블 및 관련 서비스
- broad query branch resolver

## 설계 원칙

- broad 질문은 바로 리스트 랭킹하지 않는다.
- 먼저 의도 분기를 제안한다.
- 사용자가 branch를 선택하면 해당 축에서 정책을 제안한다.
- 답변은 가능한 한 정책 근거를 가진다.

## 예시 branch

- 주거
  - 장기 주거 안정
  - 즉시 현금성 지원
  - 청약/입주 정보
- 일자리
  - 채용/인턴
  - 훈련/교육
  - 창업/금융

## 완료 조건

- broad query에서 branch 제안 가능
- 선택 branch 기준 정책 탐색 가능
- 답변과 정책 근거 카드 연결 가능
- 현재 추천과 다른 "탐색형" 경험이 생김

## 검증

- 챗 세션 CRUD
- broad query 수동 시나리오
- branch 선택 후 정책 카드 반환
- grounded answer fallback 확인

## 리스크

- branch taxonomy를 너무 자유롭게 만들면 일관성이 깨질 수 있다.
- branch는 가능하면 LLM 즉흥 생성보다 내부 정의 트리를 우선 사용해야 한다.

## 다음 문서

Phase 3 완료 후 [postgres-phase-4-pgvector-spec.md](./postgres-phase-4-pgvector-spec.md)로 진행한다.
