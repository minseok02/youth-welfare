# 테스트 실행 기준

## 기본 테스트

DB나 Redis 없이 빠르게 확인할 때 실행합니다.

```bash
cd backend
./gradlew test
```

기본 `test` 태스크는 `backend/src/test/java/com/example/welfare/integration` 아래 통합 테스트를 제외합니다.

## 통합 테스트

MySQL과 Redis까지 포함한 실제 흐름을 확인할 때 실행합니다.

```bash
docker compose up -d db redis

cd backend
./gradlew integrationTest
```

통합 테스트는 `integration` 프로필을 사용하며, 기본 연결 정보는
[application-integration.yml](../backend/src/test/resources/application-integration.yml)에 정의되어 있습니다.

## 언제 무엇을 실행할까

- 서비스/컨트롤러/리포지토리 단위 변경: `./gradlew test`
- 로그인, refresh token, Redis 저장 흐름 변경: `./gradlew integrationTest`
- 북마크, 추천 refresh/get 전체 흐름 변경: `./gradlew integrationTest`
- 배포 전 최종 확인: `./gradlew test` 실행 후 `docker compose up -d db redis` 상태에서 `./gradlew integrationTest`
