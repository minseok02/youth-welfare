# 청년복지 프로젝트 작업 안내

프로젝트 공통 진입점은 [docs/README.md](docs/README.md)입니다. 현재 작업 방향은 [current-state.md](docs/current-state.md), 구현 정보는 [project-spec.md](docs/project-spec.md)와 [architecture.md](docs/architecture.md)를 확인합니다.

## 환경

- 현재 빌드는 Java 17 / Spring Boot / PostgreSQL + pgvector / Redis입니다. Java 21 전환과 FastAPI 도입은 이번 문서 정리에서 수행하지 않습니다.
- 로컬 전체 앱은 `docker-compose.yml`의 `app + db + redis`, 서버 배포 설정은 `docker-compose.prod.yml`의 `app + redis + RDS`입니다.
- 두 Compose가 `prod` 프로필과 호스트 `8082`를 사용할 수 있습니다. 로컬 앱이 RDS를 사용하는 경우도 있으므로 [환경 안내](docs/core/environment-guide.md)에서 실제 대상을 확인합니다.
- 서버에서 로컬 DB를 띄우거나 통합 테스트 fixture를 운영 DB에 쓰지 않습니다.
- API 키, 비밀번호, 토큰, 개인정보와 전체 env 파일을 출력하거나 문서에 복사하지 않습니다.

## 구현과 계약

- Controller는 전용 application service를 호출하고 외부 API는 gateway/client 계층에 둡니다.
- 추천 API는 생성·조회·북마크 경계로 나뉩니다. 과거 `RecommendationFacade`를 존재하는 유일 진입점으로 가정하지 않습니다.
- 추천과 챗봇은 공통 탐색 서비스를 사용하지만 같은 평가·출력 계약이라는 뜻은 아닙니다.
- PostgreSQL의 실제 schema/patch와 저장소 구현을 확인합니다. MySQL `INSERT IGNORE` / `ON DUPLICATE KEY UPDATE` 예시는 현재 실행 지침이 아닙니다.
- PII·일반 데이터·목적별 datasource의 권한 경계를 보존합니다.
- 사용자 북마크와 행동 기록을 보호하고, 자격조건의 단위·출처·미입력을 구분합니다.
- AI 점수나 전체 로그 수 기반 가중치가 올바른 자격판정·학습·품질 개선을 보장한다고 가정하지 않습니다.
- 과거 1차/2차 단계표나 안정화 동결 지시로 현재 승인된 리팩토링을 막지 않습니다. 미구현·제안과 실제 구현을 구분합니다.
- 요구사항 확인, 코드 패턴의 사용 조건과 규칙 예외 승인은 [작업 가이드](docs/work-guide.md)를 따릅니다.

## 검증과 기록

[작업 가이드](docs/work-guide.md), [테스트 실행 기준](docs/core/testing.md), [Git 규칙](docs/github-workflow.md)을 따릅니다. 코드에 버그가 있으면 요구사항을 낮추지 말고 불일치와 재현 결과를 기록합니다.

실제로 실행한 검증과 미확인 범위를 [작업 현황](docs/phase-plan.md)에 남깁니다. 재사용할 문제 해결 근거만 [문제 해결 기록](docs/core/troubleshooting-log.md)에 추가합니다. 푸시는 요청이 있을 때만 합니다.
