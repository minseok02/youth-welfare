# Project Spec

이 문서는 프로젝트 기본 메타 정보를 짧게 모아 둔 파일입니다.

## 프로젝트 개요

- 프로젝트명: 청년 복지 통합 플랫폼
- 유형: 졸업 프로젝트
- 팀 규모: 2명
- 현재 단계: 기능 추가 중단 후 안정화/회귀 방지 단계
- 운영 서버: EC2 Docker Compose app, ElastiCache Valkey, RDS PostgreSQL

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
- ElastiCache Valkey 9.1.0, Redis protocol compatible
- Docker Compose
- `youth_welfare` / `youth_welfare_pii` 2 schema 분리

### 운영 캐시

- Service: Amazon ElastiCache
- Engine: Valkey 9.1.0
- Cluster mode: disabled
- Shards: 1
- Nodes: 1
- Node type: `cache.t4g.micro`
- Multi-AZ: disabled
- Automatic failover: disabled
- Encryption in transit: disabled
- Encryption at rest: enabled
- Parameter group: `default.valkey9`
- App connection: primary endpoint via `REDIS_HOST`, `REDIS_PORT=6379`
- Reader endpoint: app runtime에서 사용하지 않음. 인증 토큰, rate limit, 이메일 인증, 비밀번호 재설정 등 Redis write 경로가 있기 때문.
- Identifiers excluded from this spec: endpoint hostname, ARN, AWS account ID
- HA decision: 현재 단일 EC2 운영에서는 단일 노드로 유지한다. ALB/다중 EC2 전환 시 replica, Multi-AZ, automatic failover를 다시 연다.
- Security decision: 현재는 VPC 내부 접근 + at-rest encryption 기준으로 운영한다. TLS/Auth token을 켜는 경우 Spring Redis `ssl`, `password` env 지원을 먼저 추가한다.

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
- 인수인계: [stabilization-handoff.md](./stabilization-handoff.md)
- 구조: [architecture.md](./architecture.md)
- 요구사항: [srs-v2.10.md](core/srs-v2.10.md)
- 로컬 검증: [local-validation-docs-index.md](core/local-validation-docs-index.md)
- 진행 상황: [phase-plan.md](./phase-plan.md)
