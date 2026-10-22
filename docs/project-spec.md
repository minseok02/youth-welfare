# Project Spec

이 문서는 프로젝트 기본 메타 정보를 짧게 모아 둔 파일입니다.

## 프로젝트 개요

- 프로젝트명: 청년 복지 통합 플랫폼
- 유형: 졸업 프로젝트
- 팀 규모: 2명
- 현재 단계: 리팩토링 준비. 문서 정리·재검토 보완과 기능 개선 과제를 [current-state.md](current-state.md)에서 구분
- 서버 배포 설정: EC2 Docker Compose app + Redis, RDS PostgreSQL. 실제 가동 상태는 해당 서버 관측으로 확인

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
- DB 이름은 `youth_welfare`. 같은 DB 안에서 일반 데이터는 `public`, PII는 `youth_welfare_pii` schema로 분리

## 기존 로컬 개발 환경 확인값

아래 설치 버전은 기존 문서에 기록된 로컬 환경값입니다. 현재 머신의 설치 상태나 서버 실행 버전을 대신하지 않습니다. 실행 대상은 [환경 안내](core/environment-guide.md)를 확인합니다.

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
- 현재 작업: [current-state.md](current-state.md)
- 환경 구분: [environment-guide.md](core/environment-guide.md)
- 과거 안정화 인수인계: [stabilization-handoff.md](stabilization-handoff.md)
- 구조: [architecture.md](./architecture.md)
- 요구사항: [srs-v2.10.md](core/srs-v2.10.md)
- 로컬 검증: [local-validation-docs-index.md](core/local-validation-docs-index.md)
- 진행 상황: [phase-plan.md](./phase-plan.md)
