# PostgreSQL Phase 5 Spec - 추천/챗봇 공통 탐색 엔진

문서군 진입점: [postgres-refactor-spec-index.md](./postgres-refactor-spec-index.md)

## 목표

추천과 챗봇이 공통 retrieval/ranking 계층을 공유하게 만들어, 서로 중복된 후보 검색/정렬 로직을 줄인다.

## 브랜치

- 권장 브랜치: `refactor/backend-shared-policy-engine`

## 범위

- 공통 policy exploration service
- 공통 metadata filter / vector retrieval / rerank 신호 정리
- 추천 UI와 챗봇 UI의 역할 분리 원칙 정리

## 비범위

- 프론트 전체 개편
- 추천 기능 완전 제거
- 챗봇 단독 엔진화

## 핵심 방향

- 추천: 빠른 선제안
- 챗봇: 깊은 탐색/비교/설명
- 내부 엔진: 최대한 공통

## 주요 수정 대상

- `recommend/` retrieval 계층
- `chat/` retrieval 계층
- 공통 exploration service
- rerank 신호 정리

## 설계 원칙

- 추천과 챗봇은 별도 엔진 두 개로 키우지 않는다.
- 공통 retrieval/ranking을 만든다.
- 출력 계층만 다르게 둔다.

## 완료 조건

- 추천과 챗봇이 공통 후보 검색 계층을 사용한다.
- broad query와 선제 추천이 같은 정책 truth를 공유한다.
- 추천은 현금성/즉시성 편향만 남지 않도록 버킷형 노출 전략을 검토할 수 있다.

## 검증

- 추천 refresh 정상 동작
- 챗봇 branch 탐색 정상 동작
- 공통 retrieval 회귀 테스트
- recommendation_logs / chat 흐름 영향 확인

## 리스크

- 너무 이른 통합은 오히려 두 기능을 동시에 흔들 수 있다.
- 공통 엔진화 전에 각 기능의 역할 차이가 먼저 선명해야 한다.

## 다음 단계

이 단계가 끝나면 이후 문서는 기능 확장 문서가 된다.

- 추천 다양성/버킷 전략
- 챗봇 branch taxonomy 고도화
- 오프라인 평가/CTR 비교
