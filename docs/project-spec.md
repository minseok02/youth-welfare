# Project Spec

이 문서는 프로젝트 기본 메타 정보를 짧게 모아 둔 파일입니다.

## 프로젝트 개요

- 프로젝트명: 청년 복지 통합 플랫폼
- 유형: 졸업 프로젝트
- 팀 규모: 2명
- 현재 단계: 로컬 기능/구조 검증 단계
- 운영 서버: 아직 없음

## 주요 기술

### Backend

- Java 17
- Spring Boot 3.5.14
- Gradle 8.7
- JPA / Spring Security / Redis / WebClient

### Frontend

- React 19
- Vite 8
- MUI 7
- React Query 5
- Zustand 5

### Data / Infra

- PostgreSQL 16 + `pgvector`
- Redis
- Docker Compose
- `youth_welfare` / `youth_welfare_pii` 2 schema 분리

## 로컬 개발 환경 기준

- OS: Ubuntu 24.04 (WSL2 기준 확인)
- Shell: bash
- Node: v24.11.1
- npm: 11.6.2

## 외부 연동

- OpenAI API
- 온통청년 API
- 복지로 API
- Gov24 API
- SMTP 메일 발송 (`MAIL_*` 설정, 현재 기본값은 Gmail SMTP)

## 참고 문서

- 시스템 문서군 진입점: [system-docs-index.md](core/system-docs-index.md)
- 구조: [architecture.md](./architecture.md)
- 요구사항: [srs-v2.10.md](core/srs-v2.10.md)
- 로컬 검증: [local-validation-docs-index.md](core/local-validation-docs-index.md)
- 진행 상황: [phase-plan.md](./phase-plan.md)
