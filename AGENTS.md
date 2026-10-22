# 청년복지 프로젝트 작업 지침

공통 환경·보존 계약은 이 파일에서 관리한다. 아래 링크는 필요한 절을 찾는 경로이며 연결된 모든 문서를 재귀적으로 읽으라는 뜻은 아니다. 이미 확인했고 변경되지 않은 지침은 불필요하게 다시 읽지 않는다.

## 환경과 보존 계약

- 현재 빌드는 Java 17 / Spring Boot / PostgreSQL + pgvector / Redis다. 기술 도입·버전 전환은 요청된 변경 범위에서만 다룬다.
- 로컬 전체 앱은 `docker-compose.yml`의 `app + db + redis`, 서버 배포 설정은 `docker-compose.prod.yml`의 `app + redis + RDS`다.
- 두 Compose가 `prod` 프로필과 호스트 `8082`를 사용할 수 있다. 로컬 앱이 RDS를 사용하는 경우도 있으므로 [환경 안내](docs/core/environment-guide.md)에서 실제 대상을 확인한다.
- 서버에서 로컬 DB를 띄우거나 통합 테스트 fixture를 운영 DB에 쓰지 않는다.
- API 키, 비밀번호, 토큰, 개인정보와 전체 env 파일을 출력하거나 문서에 복사하지 않는다.
- Controller는 전용 application service를 호출하고 외부 API는 gateway/client 계층에 둔다.
- 추천 API는 생성·조회·북마크 경계로 나뉜다. 과거 `RecommendationFacade`를 존재하는 유일 진입점으로 가정하지 않는다.
- 추천과 챗봇은 공통 탐색 서비스를 사용하지만 같은 평가·출력 계약이라는 뜻은 아니다.
- PostgreSQL의 실제 schema/patch와 저장소 구현을 확인한다. MySQL `INSERT IGNORE` / `ON DUPLICATE KEY UPDATE` 예시는 현재 실행 지침이 아니다.
- PII·일반 데이터·목적별 datasource의 권한 경계를 보존한다.
- 사용자 북마크와 행동 기록을 보호하고, 자격조건의 단위·출처·미입력을 구분한다.
- AI 점수나 전체 로그 수 기반 가중치가 올바른 자격판정·학습·품질 개선을 보장한다고 가정하지 않는다.
- 과거 1차/2차 단계표나 안정화 동결 지시로 현재 승인된 리팩토링을 막지 않는다. 미구현·제안과 실제 구현을 구분한다.
- 코드에 버그가 있으면 요구사항을 낮추지 말고 불일치와 재현 결과를 기록한다.

## 작업별 대표 문서

- 문서 진입점과 현재 과제: [docs/README.md](docs/README.md), [docs/current-state.md](docs/current-state.md).
- 작업 범위, 요구사항 확인, 오류 조사, 코드 패턴, 규칙 예외 승인: [docs/work-guide.md](docs/work-guide.md).
- 브랜치, 기존 변경 보존, 커밋, 푸시, PR, 충돌·CI 실패 대응: [docs/github-workflow.md](docs/github-workflow.md).
- 검증 명령과 실행 환경: [docs/core/testing.md](docs/core/testing.md), [docs/core/environment-guide.md](docs/core/environment-guide.md).

변경 작업에서 실제로 실행한 검증과 미확인 범위는 [작업 현황](docs/phase-plan.md)에, 재사용할 문제 해결 근거만 [문제 해결 기록](docs/core/troubleshooting-log.md)에 남긴다. 푸시는 요청이 있을 때만 한다. 상세 절차는 대표 문서에서 관리하며 이 파일과 CLAUDE.md에 별도 버전을 만들지 않는다.
