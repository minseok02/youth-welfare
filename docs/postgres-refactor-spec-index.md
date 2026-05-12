# PostgreSQL 리팩토링 Phase Spec Index

문서군 진입점: [system-docs-index.md](./system-docs-index.md)

이 문서는 [postgres-chat-refactor-playbook.md](./postgres-chat-refactor-playbook.md)를 실제 작업 단위로 실행할 때 참조하는 Phase별 스펙 문서 모음이다.

사용 순서:

1. 전체 방향 확인: [postgres-chat-refactor-playbook.md](./postgres-chat-refactor-playbook.md)
2. 현재 진행할 Phase 선택
3. 해당 Phase spec 문서 확인
4. 브랜치 생성 후 작업 진행
5. 완료 조건/검증 기준 통과 여부 확인

## Phase 문서 목록

- Phase 0 안정판 고정: [postgres-phase-0-freeze-spec.md](./postgres-phase-0-freeze-spec.md)
- Phase 1 PostgreSQL 기반 전환: [postgres-phase-1-base-spec.md](./postgres-phase-1-base-spec.md)
- Phase 2 검색 계층 재작성: [postgres-phase-2-search-spec.md](./postgres-phase-2-search-spec.md)
- Phase 3 챗봇 retrieval foundation: [postgres-phase-3-chat-foundation-spec.md](./postgres-phase-3-chat-foundation-spec.md)
- Phase 4 pgvector / embedding foundation: [postgres-phase-4-pgvector-spec.md](./postgres-phase-4-pgvector-spec.md)
- Phase 5 추천/챗봇 공통 탐색 엔진 정리: [postgres-phase-5-shared-engine-spec.md](./postgres-phase-5-shared-engine-spec.md)

## 작업 규칙

- Phase 시작 전 해당 spec 문서를 먼저 읽는다.
- 해당 Phase spec에 없는 작업은 기본적으로 하지 않는다.
- 범위가 커지면 플레이북과 해당 spec를 먼저 갱신한 뒤 작업한다.
- 커밋/브랜치/검증 기준은 [github-workflow.md](./github-workflow.md)를 따른다.
