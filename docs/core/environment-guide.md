# 로컬과 서버 환경 구분

명령을 실행하기 전에 **실행 위치, 앱 대상, DB 대상, 실행 영향**을 각각 확인합니다. 파일명에 `local`이 있거나 주소가 `127.0.0.1`이라는 이유만으로 로컬 개발 DB를 대상으로 한다고 판단하지 않습니다.

## 환경별 기준

| 구분 | 앱과 데이터 대상 | 설정과 진입점 | 주의점 |
| --- | --- | --- | --- |
| 로컬 단위 테스트 | 테스트가 구성한 대역, 기본 backend test는 integration 제외 | [테스트 실행 기준](testing.md) | 실행되지 않은 통합 흐름까지 검증됐다고 해석하지 않음 |
| 로컬 통합 테스트 | 로컬 PostgreSQL `127.0.0.1:5433`, Redis `127.0.0.1:6379` 기본값 | [application-integration.yml](../../backend/src/test/resources/application-integration.yml), `INTEGRATION_*` | fixture를 쓰고 지우는 테스트. 운영 URL 사용 금지 |
| 로컬 Docker 전체 앱 | `app + db + redis`, 앱은 내부 `db:5432` 접속 | [docker-compose.yml](../../docker-compose.yml), `.env` | `ALLOW_LOCAL_DOCKER_DB=true` 명시. 외부 API는 별도 설정에 따라 실제 호출 가능 |
| 운영 EC2와 RDS | EC2의 `app + redis`, 별도 RDS PostgreSQL | [docker-compose.prod.yml](../../docker-compose.prod.yml), [배포 절차](../deployment.md) | 운영 Compose에는 `db` 서비스가 없음. 실제 앱 설정은 `.env.runtime.production` 계열 |
| 로컬 앱과 원격 DB | 로컬 프로세스나 컨테이너가 설정된 원격 PostgreSQL 접속 | 해당 앱의 실제 datasource 설정 | 실행 위치가 로컬이어도 대상이 운영 RDS이면 운영 데이터 작업 |
| 배포된 프론트 검증 | public origin과 연결된 실제 API | `FRONTEND_E2E_MODE=deployed-origin`, `FRONTEND_PUBLIC_BASE_URL` | 브라우저 테스트의 가입·프로필·북마크·클릭 등이 대상 앱에 반영될 수 있음 |
| CI | workflow가 준비한 테스트 서비스 | [ci.yml](../../.github/workflows/ci.yml) | 로컬/운영 관측 결과와 분리. workflow가 실행한 검사만 통과 범위 |

로컬 Compose도 `SPRING_PROFILES_ACTIVE=prod`이고 앱을 호스트 `8082`에 노출합니다. 운영 Compose도 기본 `prod`와 호스트 `8082`를 사용합니다. **프로필명과 포트만으로 로컬/운영을 구분할 수 없습니다.**

## 환경 변수의 역할

| 설정 | 의미 |
| --- | --- |
| `ENV_FILE` | 해당 smoke/운영 스크립트가 읽을 설정 파일. 앱 컨테이너의 env 파일을 자동으로 뜻하지 않음 |
| `APP_BASE_URL` | 해당 스크립트가 호출할 앱. EC2의 `127.0.0.1:8082`는 운영 앱일 수 있음 |
| `SMOKE_DB_MODE=docker` | `DB_CONTAINER_NAME` / `POSTGRES_CONTAINER_NAME` 대상 컨테이너의 DB 사용 |
| `SMOKE_DB_MODE=postgres` | `DB_DIRECT_URL` / `DB_URL` 등에 따른 직접 접속. 로컬·RDS 모두 가능 |
| `SMOKE_DB_MODE=auto` | DB 컨테이너가 보이면 docker, 아니면 postgres 선택. 운영에서는 자동 선택에 기대지 않음 |
| `INTEGRATION_*_DB_URL` | 통합 테스트 전용. Gradle은 로컬 PostgreSQL 주소만 허용하고 운영 URL fallback은 사용하지 않음 |
| `COMPOSE_FILE` / `-f` | 사용할 Compose. 명령의 `-f`로 로컬/운영 파일을 명시 |
| `APP_RUNTIME_ENV_FILE` | 운영 앱 컨테이너에 전달할 제한된 runtime env 파일. 운영 Compose 기본값은 `.env.runtime.production` |

DB 선택 구현은 [smoke-common.sh](../../deploy/smoke/smoke-common.sh)를 확인합니다. 개별 스크립트가 같은 설정을 모두 지원한다고 가정하지 않습니다.

## 로컬 Docker와 통합 테스트

아래는 개발 컴퓨터의 로컬 DB/Redis를 기동하는 명령입니다. EC2/RDS 서버에서 실행하지 않습니다.

```bash
ALLOW_LOCAL_DOCKER_DB=true docker compose --env-file .env -f docker-compose.yml up -d db redis
```

그다음 [testing.md](testing.md)의 preflight와 통합 테스트를 실행합니다. 기존 볼륨에 drift가 있으면 [DB 변경 절차](db-migration.md)를 확인하며, 볼륨 삭제나 schema patch를 기본 테스트 단계로 실행하지 않습니다.

## 서버와 RDS 점검

운영 설정 준비·runtime env 생성·기동·권한 검증은 [deployment.md](../deployment.md)를 따릅니다. `.env.production`은 운영 설정 원본이고, 앱에 전달되는 `.env.runtime.production`과 역할이 다릅니다.

운영 관측 명령은 앱과 DB가 같은 대상을 보도록 설정한 뒤 사용합니다. runtime env는 최소권한 원칙에 따라 migration/query 계정 비밀을 포함하지 않을 수 있습니다. `smoke_db_query`는 query 계정이 없으면 `migration_admin`으로 fallback하므로, runtime env의 앱 비밀번호만으로 실행하면 인증이 어긋날 수 있습니다. 승인된 점검용 계정은 별도 운영 설정이나 inline `DB_QUERY_USERNAME` / `DB_QUERY_PASSWORD`로 전달하며, 이 문제를 해결하려고 migration 비밀을 앱 runtime env에 추가하지 않습니다.

다음은 실행 형태이며, 실제 계정 권한·명령의 영향은 해당 runbook에서 먼저 확인합니다.

```bash
ENV_FILE=.env.runtime.production SMOKE_DB_MODE=postgres APP_BASE_URL='http://127.0.0.1:8082' \
DB_QUERY_USERNAME='<approved query role>' DB_QUERY_PASSWORD='<query password>' \
bash deploy/smoke/run-local-ops-baseline-suite.sh
```

`run-local-*` 이름은 실행 환경을 강제하지 않습니다. 로컬 DB와 RDS의 row 수를 비교하기 전에 두 명령이 실제 같은 데이터를 읽는지 확인합니다. 설정 확인 과정에서 비밀번호, 토큰, 전체 env 파일을 로그에 출력하지 않습니다.

## 명령의 영향

- 조회/진단: 실제로 조회만 하는지 확인합니다. 로그인·토큰 발급·로컬 artifact 생성이 부수적으로 있을 수 있습니다.
- 상태 변경: 추천 refresh, backfill, repair, 사용자 seed, approval record smoke, schema patch는 앱/DB 상태를 변경할 수 있습니다.
- 외부 호출: 수집, 실제 OpenAI, SMTP, 웹푸시는 호출 예산·과금·외부 발송 여부를 확인합니다.
- 부하/브라우저 시험: 운영에 부하를 주거나 테스트 계정·행동 데이터를 남길 수 있습니다.

같은 문서에 여러 종류가 있으면 섹션별로 환경과 영향을 구분합니다. 로컬과 서버의 절차 차이는 중복으로 제거하지 않습니다.

## 관측값의 해석

관측 결과에는 실행 시점, 커밋, 실행 위치, 앱/DB 대상, 데이터 표본, 실제 실행한 검사를 함께 봅니다. 과거의 `PASS`, `BASELINE_HEALTHY`, `KEEP_OBSERVING`은 당시 관측값이지 현재 환경의 상태나 새로운 작업 금지 지시가 아닙니다. 날짜가 없는 `tmp/*/latest*`도 다른 환경의 최신 결과로 대체하지 않습니다.
