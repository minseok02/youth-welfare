# 문서 목차

이 파일은 `docs/` 문서의 시작점입니다.  
작업할 때 아래 상황에 맞는 문서를 먼저 열어보면 됩니다.

## 작업별로 보기

### 전체 구조를 파악할 때

- [architecture.md](./architecture.md)  
  백엔드, 프론트엔드, DB, 외부 API가 어떻게 연결되는지 확인합니다.

- [srs-v2.10.md](./srs-v2.10.md)  
  프로젝트 요구사항과 기능 범위를 확인합니다.

### API를 연결하거나 화면을 붙일 때

- [api-mapping.md](./api-mapping.md)  
  프론트엔드 화면과 백엔드 API 매핑을 확인합니다.

- [recommendation-pipeline.md](./recommendation-pipeline.md)  
  추천 API, 추천 저장, 점수 계산 흐름을 확인합니다.

### 공공 API 수집을 수정하거나 운영할 때

- [collect-ops.md](./collect-ops.md)  
  수집 배치, 429 대응, 중복 실행 방지, 부분 성공 기준을 확인합니다.

- [troubleshooting-log.md](./troubleshooting-log.md)  
  이전에 발생한 장애와 해결 과정을 확인합니다.

### DB 스키마나 마이그레이션을 건드릴 때

- [db-migration.md](./db-migration.md)  
  DB 변경 사항, 마이그레이션 기준, 스키마 반영 방법을 확인합니다.

### 테스트를 실행하거나 실패 원인을 볼 때

- [testing.md](./testing.md)  
  기본 테스트와 MySQL/Redis 통합 테스트 실행 방법을 확인합니다.

### GitHub로 협업할 때

- [github-workflow.md](./github-workflow.md)  
  2명 기준 프론트/백 담당 분리, 브랜치, 커밋, 푸시, PR 규칙을 확인합니다.

### 배포하거나 서버 설정을 볼 때

- [deployment.md](./deployment.md)  
  Docker, nginx, 운영 환경 변수, 배포 절차를 확인합니다.

### 일정, 단계, 데모 흐름을 볼 때

- [phase-plan.md](./phase-plan.md)  
  현재 구현 상태와 남은 1차 작업을 확인합니다.

- [demo-scenario.md](./demo-scenario.md)  
  시연 흐름과 데모용 사용자 시나리오를 확인합니다.

## 전체 문서 목록

- [api-mapping.md](./api-mapping.md)
- [architecture.md](./architecture.md)
- [collect-ops.md](./collect-ops.md)
- [db-migration.md](./db-migration.md)
- [demo-scenario.md](./demo-scenario.md)
- [deployment.md](./deployment.md)
- [github-workflow.md](./github-workflow.md)
- [phase-plan.md](./phase-plan.md)
- [recommendation-pipeline.md](./recommendation-pipeline.md)
- [srs-v2.10.md](./srs-v2.10.md)
- [testing.md](./testing.md)
- [troubleshooting-log.md](./troubleshooting-log.md)

## 보관 문서

- [archive/project-plan-v11.md](./archive/project-plan-v11.md)  
  과거 프로젝트 플랜 원본입니다. 현재 작업 기준은 `phase-plan.md`를 봅니다.
