# 시스템 문서 묶음

## 목적

cross-cutting 문서가 흩어져 있어도

- 프로젝트 기본 메타 정보
- 구조와 API contract
- 데이터/마이그레이션 설계
- 후속 확장 설계 메모

를 한 문서에서 바로 찾게 정리합니다.

## 지금 먼저 볼 문서

### 기본 구조와 계약

- [project-spec.md](../project-spec.md)
- [architecture.md](../architecture.md)
- [api-mapping.md](./api-mapping.md)

### 데이터/마이그레이션

- [db-migration.md](./db-migration.md)
- [user-data-separation-design.md](./user-data-separation-design.md)

### 후속 확장 메모

- [chatbot-plan.md](./chatbot-plan.md)
- [notification-channel-expansion-plan.md](./notification-channel-expansion-plan.md)
- [notification-channel-expansion-checklist.md](./notification-channel-expansion-checklist.md)

## 문서 역할

### 1. 프로젝트 기본 메타

- [project-spec.md](../project-spec.md)

이 문서는

- 기술 스택
- 실행 환경
- 외부 연동
- 현재 단계

를 짧게 보는 entry spec 입니다.

### 2. 구조 설명

- [architecture.md](../architecture.md)

이 문서는 현재 모듈 구조와 계층 분리를 큰 그림으로 설명합니다.

### 3. API contract

- [api-mapping.md](./api-mapping.md)

이 문서는 프론트/외부 연동 기준 API 요청/응답 contract를 정리합니다.

### 4. DB migration / schema 메모

- [db-migration.md](./db-migration.md)

이 문서는 현재 PostgreSQL mainline 기준 schema/migration truth를 보조하는 **legacy migration / draft sidecar 메모** 를 정리합니다.
현재 실행 판단은 `db-migration.md` 단독보다 `current-state.md`, `testing.md`, 관련 runbook을 먼저 봅니다.

### 5. 사용자 데이터 분리 설계

- [user-data-separation-design.md](./user-data-separation-design.md)

이 문서는 `user_key`, `auth_users`, `user_profiles`, `user_pii`, `2 schema` 기준의 PII 분리 배경과 cut-over 원칙을 정리합니다.

### 6. 챗봇 후속 설계

- [chatbot-plan.md](./chatbot-plan.md)

이 문서는 아직 active 구현은 아니지만, 챗봇 2차 기능을 어떤 구조로 붙일지 정리한 설계 메모입니다.

### 7. 알림 채널 확장 설계

- [notification-channel-expansion-plan.md](./notification-channel-expansion-plan.md)

이 문서는 현재 이메일 중심 알림 구조를 기준으로 `인앱 알림함 + 웹푸시` 를 어떻게 붙일지 정리한 설계 메모입니다.

### 8. 알림 채널 확장 체크리스트

- [notification-channel-expansion-checklist.md](./notification-channel-expansion-checklist.md)

이 문서는 알림 채널 확장 작업을 단계별로 어디까지 할지 짧게 고정하는 작업 체크리스트입니다.

## 읽는 순서

### 구조/계약을 빨리 확인할 때

1. [project-spec.md](../project-spec.md)
2. [architecture.md](../architecture.md)
3. [api-mapping.md](./api-mapping.md)

### DB/데이터 구조 판단이 필요할 때

1. [db-migration.md](./db-migration.md)
2. [user-data-separation-design.md](./user-data-separation-design.md)

### 후속 확장 메모를 볼 때

1. [chatbot-plan.md](./chatbot-plan.md)
2. [notification-channel-expansion-plan.md](./notification-channel-expansion-plan.md)
3. [notification-channel-expansion-checklist.md](./notification-channel-expansion-checklist.md)
4. 필요하면 [phase-plan.md](../phase-plan.md)

## 요약

1. 구조와 contract는 [project-spec.md](../project-spec.md), [architecture.md](../architecture.md), [api-mapping.md](./api-mapping.md) 부터 봅니다.
2. DB/schema 판단은 [db-migration.md](./db-migration.md) 과 [user-data-separation-design.md](./user-data-separation-design.md) 를 봅니다.
3. 챗봇은 아직 active 구현이 아니라 [chatbot-plan.md](./chatbot-plan.md) 설계 메모로 봅니다.
4. 알림 채널 확장은 [notification-channel-expansion-plan.md](./notification-channel-expansion-plan.md) 부터 봅니다.
5. 실제 작업 범위는 [notification-channel-expansion-checklist.md](./notification-channel-expansion-checklist.md) 로 고정합니다.
