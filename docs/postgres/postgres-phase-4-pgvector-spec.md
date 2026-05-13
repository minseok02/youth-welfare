# PostgreSQL Phase 4 Spec - pgvector / Embedding Foundation

문서군 진입점: [postgres-refactor-spec-index.md](./postgres-refactor-spec-index.md)

## 목표

PostgreSQL 안에서 벡터 검색이 가능하도록 `pgvector`와 embedding 기반 retrieval foundation을 추가한다.

## 브랜치

- 권장 브랜치: `feat/backend-pgvector-foundation`

## 범위

- `pgvector` extension
- chunk embedding column
- embedding 생성/갱신 흐름
- 질문 임베딩 생성
- vector similarity retrieval

## 비범위

- 추천/챗봇 공통 엔진 완전 통합
- 대규모 외부 vector DB

## 주요 수정/추가 대상

- PostgreSQL schema / migration
- `policy_chunks.embedding`
- embedding 생성 서비스
- 질문 임베딩 retrieval 서비스
- 챗봇 retrieval 계층

## 설계 원칙

- embedding은 "찾기"용이다.
- 답변 생성과 구분한다.
- 먼저 `text-embedding-3-small`로 시작한다.
- 정책 변경 시 재생성 가능한 구조를 우선한다.

## 완료 조건

- `pgvector` 설치 및 마이그레이션 완료
- 정책 chunk 임베딩 저장 가능
- 질문 임베딩으로 유사 chunk 검색 가능
- FTS + vector를 함께 사용할 준비가 됨

## 검증

- 임베딩 생성 smoke
- chunk 적재 확인
- 유사 chunk 검색 수동 검증
- broad/semantic query 비교

## 리스크

- chunk 설계가 나쁘면 벡터 검색 품질도 나빠진다.
- 임베딩 재생성 배치와 정책 수집 동기화 경계가 필요하다.

## 다음 문서

Phase 4 완료 후 [postgres-phase-5-shared-engine-spec.md](./postgres-phase-5-shared-engine-spec.md)로 진행한다.
