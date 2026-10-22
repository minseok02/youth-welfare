# DB 백업 복구 리허설 런북

## 목적

운영 DB 장애나 잘못된 schema 변경에 대비해 백업에서 별도 DB로 복원하고, DB 감사 뒤 격리 앱으로 검증하는 절차를 고정한다.

## 원칙

- 운영 DB에는 복원 테스트를 직접 수행하지 않는다.
- 리허설 대상은 별도 RDS instance 또는 격리된 PostgreSQL이어야 한다.
- 복원 DB는 외부 트래픽을 받지 않는 보안 그룹에 둔다.
- 앱 연결 전에는 DB 감사를 수행한다. runtime smoke는 별도 앱을 복원 DB에 연결하고 실제 datasource 격리를 확인한 **뒤에만** 수행한다.
- runtime smoke는 가입·추천 생성·북마크·로그아웃 등 쓰기 작업이다. `ENV_FILE`은 실행 중인 앱을 재설정하거나 DB 연결을 바꾸지 않는다.
- PII 포함 DB이므로 dump 파일은 암호화된 저장소에 두고 리허설 후 삭제한다.

## 사전 조건

- ops monitor 역할에 `rds:DescribeDBInstances` 권한이 적용되어 있어야 한다.
- 복원 대상 DB는 운영과 다른 endpoint, 다른 DB identifier를 사용한다.
- 복원 대상 DB 접속 정보는 별도 `.env.restore` 같은 파일에 둔다.
- 운영 앱의 `DB_URL`은 리허설 중 변경하지 않는다.

2026-06-27 당시 관측된 blocker (현재 AWS 권한 상태는 재확인 필요):

- 실행 역할 `arn:aws:sts::857721769929:assumed-role/youth-welfare-ops-monitor-v2-role/...` 에 실제 `rds:DescribeDBInstances` 권한이 아직 없다.
- 같은 역할로 `iam:PutRolePolicy` 도 `AccessDenied` 이므로 현재 세션에서 직접 정책 반영이 불가능하다.
- repo의 `deploy/ops/aws-ops-monitor-role-policy.json` 에 `rds:DescribeDBInstances` 는 추가되어 있으므로, privileged AWS actor가 실제 `youth-welfare-ops-monitor-v2-role` 정책에 반영한 뒤 이 런북을 진행한다.

## 1. 운영 RDS 백업 상태 확인

```bash
aws rds describe-db-instances \
  --db-instance-identifier youth-welfare-prod-db \
  --query 'DBInstances[0].{Status:DBInstanceStatus,BackupRetentionPeriod:BackupRetentionPeriod,LatestRestorableTime:LatestRestorableTime,DeletionProtection:DeletionProtection,MultiAZ:MultiAZ,StorageEncrypted:StorageEncrypted}' \
  --output table
```

확인 기준:

- `BackupRetentionPeriod`가 1 이상
- `LatestRestorableTime`이 최근 시각
- `DeletionProtection` 활성
- `StorageEncrypted` 활성

## 2. 별도 DB로 복원

RDS point-in-time restore 또는 snapshot restore로 별도 identifier를 만든다.

```bash
aws rds restore-db-instance-to-point-in-time \
  --source-db-instance-identifier youth-welfare-prod-db \
  --target-db-instance-identifier youth-welfare-restore-rehearsal-YYYYMMDD \
  --use-latest-restorable-time \
  --db-subnet-group-name '<restore-subnet-group>' \
  --vpc-security-group-ids '<restore-security-group-id>'
```

복원 DB가 `available` 이 될 때까지 기다린다.

## 3. 복원 DB 접속 정보 준비

`.env.restore` 는 운영 `.env.production` 을 복사하지 말고 DB 접속 관련 값만 별도로 둔다.

필수 값:

- `DB_URL`
- `DB_MIGRATION_USERNAME`
- `DB_MIGRATION_PASSWORD`

이 파일은 **DB 감사 전용**이다. 이것만으로 앱 runtime preflight를 통과하거나 앱을 실행할 수 없다.

## 4. 복원 DB 감사

```bash
ENV_FILE=.env.restore deploy/postgres/audit-operational-db-state.sh
```

통과 기준:

- 핵심 테이블 `OK`
- `schema_migration_history` `OK`
- `chat_snapshots_nonnull_orphan_session=0`
- `active_queries_over_5m=0`
- `waiting_locks=0`
- `user_pii_sync_pending_or_failed=0`

알려진 관찰 항목:

- `auth_without_users`, `profiles_without_users`, `pii_without_users` 가 0이 아니면 사용자 core projection 불일치 정리 전까지 FK 추가를 보류한다.
- `active_users_without_pii` 는 0이어야 한다. 0이 아니면 PII sync/backfill 누락으로 보고 복구한다.
- `withdrawn_or_inactive_users_without_pii` 는 탈퇴/비활성 계정에서 PII 삭제가 완료된 상태일 수 있으므로 별도 경고로 보지 않는다.
- `users_without_pii` 는 위 두 값을 합친 총량으로만 본다.

## 5. 앱 연결 리허설

DB 감사만 필요하면 여기서 멈춰도 된다. 앱 검증을 추가하려면 운영 앱이 아닌 별도 일회성 앱을 준비한다.

준비·확인 순서:

1. 별도 `.env.restore-runtime`을 준비한다. [환경 안내](environment-guide.md)와 [배포 절차](../deployment.md)의 runtime role/PII/JWT/암호화 설정을 참고하되 운영 환경 파일을 그대로 복사하지 않는다. 복원된 암호문 해독에 필요한 키는 승인된 비밀 관리 경로로만 제공한다.
2. **모든** core/PII/cleanup/admin/recommendation datasource가 복원 DB endpoint를 향하는지 확인한다. 기본 `DB_URL` 하나만 바꾸는 것으로 충분하지 않다. 앱과 smoke 보조 DB 접속도 같은 복원 DB여야 한다.
3. Redis도 별도 인스턴스로 격리하고, 스케줄러·수집·AI·메일·푸시 등 외부 호출/발송을 차단한다. 전체 background job을 끄는 단일 스위치가 있다고 가정하지 말고 실행 설정과 egress를 확인한다. 복원된 실사용자에게 알림이 나가거나 운영 Redis가 변경되면 안 된다. 가입 검증 helper는 env의 Redis URL이 아니라 `REDIS_CONTAINER_NAME`에 `docker exec`하므로 앱과 같은 격리 Redis 컨테이너 이름을 **명시**해야 한다.
4. 별도 Compose project/network/컨테이너 이름·포트 또는 독립 프로세스로 기동한다. 기존 Compose의 고정 `container_name`/포트를 공유해 운영 앱을 교체하지 않는다. 포트 차이만으로 DB 격리가 입증되지는 않는다.
5. 실행 중인 리허설 앱의 실제 datasource·Redis 대상과 background job 차단을 확인하고, 승인된 테스트 계정/정책으로만 쓰기 검증한다. 비밀값·PII 원문은 증적에 남기지 않는다.

운영과 동일한 보안/role 계약을 구성한 경우의 정적 preflight 예시:

```bash
ENV_FILE=.env.restore-runtime SKIP_COMPOSE_CONFIG=true PRINT_SUMMARY=true \
  bash deploy/smoke/preflight-runtime-cutover-env.sh
```

이는 env 값 검사일 뿐 앱 기동·접속 검증이 아니다. 로컬 HTTP/cookie 설정 차이는 별도 확인하고, 전체 runtime 값을 준비할 수 없으면 API smoke를 생략한다.

아래 명령은 작업자가 `RESTORE_APP_BASE_URL`, `PRODUCTION_APP_BASE_URL`, `RESTORE_REDIS_CONTAINER_NAME`을 명시하고 실제 격리를 확인한 뒤 `RESTORE_ISOLATION_CONFIRMED=true`를 설정해야 실행된다. 리허설 앱은 이 예시에서 loopback의 별도 포트로만 접근한다. 원격 앱은 승인된 터널 등으로 접근하고, 기본 앱 포트 `8082`와 운영 origin은 거부한다. Docker helper도 리허설 Redis와 같은 호스트에서 실행한다. 준비할 수 없으면 API smoke를 생략한다.

```bash
(
  set -euo pipefail
  : "${RESTORE_APP_BASE_URL:?격리 앱 URL을 명시하세요. 기본값은 없습니다}"
  : "${PRODUCTION_APP_BASE_URL:?비교할 운영 앱 URL을 명시하세요}"
  : "${RESTORE_REDIS_CONTAINER_NAME:?앱과 같은 격리 Redis 컨테이너 이름이 필요합니다}"
  : "${RESTORE_ISOLATION_CONFIRMED:?실제 앱/모든 DB/Redis/외부 호출 격리 확인이 필요합니다}"
  [[ "${RESTORE_ISOLATION_CONFIRMED}" == "true" ]] || exit 1
  [[ "${RESTORE_REDIS_CONTAINER_NAME}" != "youth-welfare-redis" ]] || exit 1
  python3 - "${RESTORE_APP_BASE_URL}" "${PRODUCTION_APP_BASE_URL}" <<'PY'
import sys
from urllib.parse import urlsplit

def origin(value):
    url = urlsplit(value)
    if (url.scheme not in ("http", "https") or not url.hostname
            or url.username or url.password or url.query or url.fragment
            or url.path not in ("", "/")):
        raise SystemExit("앱 origin만 허용합니다 (인증정보/경로/query 제외)")
    host = url.hostname.lower()
    if host in ("localhost", "127.0.0.1", "::1"):
        host = "loopback"
    return url.scheme, host, url.port or (443 if url.scheme == "https" else 80)

restore, production = map(origin, sys.argv[1:3])
if (restore[1] != "loopback" or restore[2] in (80, 443, 8082)
        or restore == production or restore[1:] == production[1:]):
    raise SystemExit("격리된 loopback 별도 포트가 필요합니다. 기본/운영 대상은 거부합니다")
PY
  ENV_FILE=.env.restore-runtime SMOKE_DB_MODE=postgres \
    REDIS_CONTAINER_NAME="${RESTORE_REDIS_CONTAINER_NAME}" \
    APP_BASE_URL="${RESTORE_APP_BASE_URL%/}" \
    APP_HEALTH_URL="${RESTORE_APP_BASE_URL%/}/actuator/health" \
    bash deploy/smoke/run-local-runtime-api-smoke.sh
)
```

이 guard는 주소 실수를 줄일 뿐 잘못 연결된 앱의 DB/Redis를 자동 판별하지 않는다. 운영 앱 환경·endpoint를 리허설용으로 바꾸지 않으며, 격리 증거가 없으면 쓰기 smoke를 실행하지 않는다.

## 6. 종료

- 리허설 결과와 복원 DB identifier, 복원 시각을 기록한다.
- 결과 확인 뒤 dump·임시 `.env.restore`/`.env.restore-runtime`과 격리 앱/Redis를 정리한다. 비밀값은 artifact에 보존하지 않는다.
- 비용 정리 시 삭제 대상이 **기록한 복원 DB identifier**인지 다시 확인한다. 운영 DB·최종 백업 삭제와 혼동하지 않는다.
- 실패한 경우 `docs/core/server-runtime-drift-checklist.md` 기준으로 schema drift부터 분리한다.
