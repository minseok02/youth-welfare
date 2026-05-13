# PostgreSQL Phase 0 Spec - 안정판 고정

문서군 진입점: [postgres-refactor-spec-index.md](./postgres-refactor-spec-index.md)

## 목표

현재 MySQL 기반 안정판을 복구 가능한 기준점으로 고정한다.

## 브랜치

- 권장 브랜치: `release/mysql-stable`
- 태그: `mysql-stable-v1`

## 범위

- 현재 working tree 정리
- 안정판 브랜치 생성
- 안정판 태그 생성
- 현재 실행 기준 문서화

## 비범위

- 코드 리팩토링
- PostgreSQL 설정 변경
- 검색/챗봇 구조 변경

## 작업 항목

1. 현재 변경 사항 확인
2. 안정판 기준 커밋 정리
3. `release/mysql-stable` 브랜치 생성
4. `mysql-stable-v1` 태그 생성
5. 현재 기준 smoke/test 실행 방법을 메모

## 완료 조건

- `release/mysql-stable` 브랜치가 존재한다.
- `mysql-stable-v1` 태그가 존재한다.
- 현재 MySQL 기준 복구 경로가 명확하다.

## 검증

- `git branch --list`
- `git tag --list | grep mysql-stable-v1`
- 현재 실행 기준:
  - `docker compose up -d db redis`
  - `cd backend && ./gradlew test`
  - 필요 시 `./gradlew integrationTest`

## 리스크

- 안정판 고정 전에 실험 브랜치 작업을 섞으면 복구 기준이 흐려질 수 있다.

## 다음 문서

Phase 0 완료 후 [postgres-phase-1-base-spec.md](./postgres-phase-1-base-spec.md)로 진행한다.
