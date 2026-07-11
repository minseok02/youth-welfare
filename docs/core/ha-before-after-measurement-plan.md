# HA 전환 전후 측정 계획

작성 기준일: 2026-07-07

## 목적

이 문서는 `EC2 1대 + RDS PostgreSQL + ElastiCache Valkey 단일 노드` 에서 `ALB + EC2 2대 + RDS PostgreSQL + ElastiCache Valkey HA 구성` 으로 전환하기 전후를 비교하기 위한 측정 기준을 고정한다.

기존 [performance-baseline-current.md](../performance/performance-baseline-current.md) 는 API/DB/Redis 성능 최적화 비교용 기준선이다. 이 문서는 고가용성 전환의 체감 효과, 장애 시나리오, 운영 복잡도 증가를 함께 본다.

핵심 질문은 아래다.

- EC2 web 노드 1대가 내려가도 사용자가 사이트/API를 계속 쓸 수 있는가?
- 로그인, refresh, logout, password reset, email verification 같은 Redis 의존 흐름이 노드 간에 이어지는가?
- scheduler와 cron성 작업이 2대에서 중복 실행되지 않는가?
- ALB 도입 뒤 평상시 latency가 받아들일 수 있는 범위에 남는가?
- 운영자가 장애를 감지하고 복구하는 데 걸리는 시간이 줄었는가?

## 현재 토폴로지 기준

현재 실행 앱 기준:

- App datasource: RDS PostgreSQL `youth-welfare-prod-db...ap-northeast-2.rds.amazonaws.com`
- Redis: ElastiCache Valkey primary endpoint via `REDIS_HOST`, `REDIS_PORT=6379`
- Edge: 단일 EC2 nginx가 직접 HTTPS 종료 후 `127.0.0.1:8082` 로 proxy
- App container: `youth-welfare-app`
- Redis container: 없음. local Redis compose는 rollback/로컬 개발 경로로만 남긴다.
- Local DB container: `youth-welfare-db` 가 떠 있을 수 있으나, 현재 app runtime datasource는 RDS다.

비교 대상 전환 계획은 [alb-multi-ec2-rollout-plan.md](./alb-multi-ec2-rollout-plan.md) 를 기준으로 한다.

## 측정 원칙

- 측정마다 `timestamp_utc`, `git_head`, `topology`, `base_url`, `node`, `artifact_dir` 를 남긴다.
- internal `http://127.0.0.1:8082` 와 external `https://youthmoa.kr` 를 분리한다.
- 정상 상태 측정과 장애 드릴 측정을 섞지 않는다.
- 장애 드릴은 운영 점검창에서만 실행한다.
- DB/RDS schema나 사용자 데이터를 바꾸는 측정은 별도 side-effecting smoke로 분리한다.
- 전환 후 비교는 평균보다 `p95`, `max`, `error rate`, `RTO`, `사용자 재로그인 필요 여부` 를 우선한다.

## 현재 빠른 스냅샷

2026-07-11T12:04Z 기준 quick sample:

| 항목 | 값 |
| --- | --- |
| git head | `6f1010776cfab2e26d96d70394ccb64747e8c857` |
| EC2 uptime | `3 days, 23 hours, 30 minutes` |
| root disk | `19G total / 13G used / 5.8G available / 69%` |
| memory | `3.7Gi total / 2.4Gi available` |
| app container | `Up 7 hours (healthy)` |
| local DB container | 없음 |
| redis container | 없음 |
| app memory | `603.3MiB / 1GiB` |
| redis runtime | ElastiCache Valkey primary endpoint |
| internal health | `200`, avg `20.6ms`, p95 `31.5ms`, max `33.4ms` |
| external `/` | `200`, avg `71.8ms`, p95 `101.1ms`, max `109.0ms` |
| external `/api/policies` | `200`, avg `158.2ms`, p95 `220.7ms`, max `253.1ms` |

이 값은 짧은 현장 스냅샷이다. 전환 전 최종 기준선은 아래의 full command set으로 다시 저장한다.

## 전환 전 필수 측정

### 1. 토폴로지와 단일 장애점

기록할 값:

- EC2 instance id, AZ, type
- nginx active 여부
- Docker container health
- app runtime DB endpoint
- Redis endpoint
- RDS backup/deletion protection/Multi-AZ 여부
- Route53 record target
- ALB/target group 존재 여부

현재 기대 판정:

| 항목 | 현재 | HA 이후 목표 |
| --- | --- | --- |
| web node | 1대 | 2대 |
| web node 장애 | 전체 서비스 중단 | 다른 target으로 계속 응답 |
| Redis | ElastiCache Valkey 단일 노드 | ElastiCache replica/Multi-AZ shared Redis |
| session/logout/revoke | 단일 web node + 공유 Redis | 노드 간 공유 유지 |
| scheduler | 단일 app process | EC2-1만 enabled, EC2-2 disabled |
| TLS 종료 | EC2 nginx | ALB |
| app target health | 없음 | ALB target health 2개 |

### 2. 정상 상태 latency

짧은 curl sample:

```bash
for url in \
  http://127.0.0.1:8082/actuator/health \
  https://youthmoa.kr/ \
  https://youthmoa.kr/api/policies
do
  printf 'URL %s\n' "$url"
  for i in $(seq 1 20); do
    curl -o /dev/null -sS \
      -w '%{http_code} %{time_connect} %{time_starttransfer} %{time_total}\n' \
      --max-time 15 "$url"
  done
done
```

정식 API baseline:

```bash
ENV_FILE=.env.production \
SMOKE_DB_MODE=postgres \
APP_BASE_URL='http://127.0.0.1:8082' \
RUNS=7 \
bash deploy/performance/run-local-api-latency-baseline.sh
```

전환 후에는 같은 명령을 아래 기준으로 각각 실행한다.

```bash
APP_BASE_URL='https://youthmoa.kr'
APP_BASE_URL='http://127.0.0.1:8082' # 각 EC2에서 local node 확인
```

비교 기준:

- ALB 경유 external p95가 기존 external p95 대비 20% 이상 악화되면 원인 확인
- error rate는 0이어야 한다.
- `/actuator/health` 는 외부에 공개하지 않고 `/alb-health` 만 ALB health check로 사용한다.

### 3. 장애 체감 측정

전환 전에는 운영 중 실제 EC2 stop을 기본 측정으로 쓰지 않는다. 단일 EC2 stop은 곧 전체 장애이므로, 점검창이 아니면 실행하지 않는다.

전환 전 대체 드릴:

```bash
docker stop youth-welfare-app
curl -I --max-time 5 https://youthmoa.kr/ || true
docker compose --env-file .env.production -f docker-compose.prod.elasticache.yml up -d app
curl -fsS http://127.0.0.1:8082/actuator/health
```

기록할 값:

- 장애 시작 시각
- 외부 요청 실패 시작 시각
- 복구 명령 시각
- health `UP` 복귀 시각
- 외부 `https://youthmoa.kr/` 200 복귀 시각
- 총 RTO

현재 기대 판정:

- app stop 중에는 대체 target이 없으므로 외부 서비스가 실패한다.
- 복구는 수동 app restart에 의존한다.

전환 후 장애 드릴:

```bash
# EC2-1에서
docker stop youth-welfare-app

# 관찰
curl -I https://youthmoa.kr/
```

확인:

- ALB target EC2-1 unhealthy
- ALB target EC2-2 healthy
- `https://youthmoa.kr/` 200 유지
- login/refresh/logout smoke 통과

같은 방식으로 EC2-2도 중지한다.

목표:

| 지표 | 전환 전 | 전환 후 목표 |
| --- | --- | --- |
| web node app stop 중 public `/` | 실패 | 200 유지 |
| web node app stop 중 public `/api/policies` | 실패 | 200 유지 |
| failover RTO | 수동 복구 시간 | ALB health check 1분 이내 |
| 사용자 재로그인 필요 | 단일 web node 장애 시 요청 실패 가능, Redis 장애 시 필요 가능 | 단일 web node 장애 시 불필요 |

### 4. Redis/session 연속성

현재 Redis는 이미 ElastiCache Valkey라 단일 EC2 단계에서도 app 외부에 있다. HA 전환 전후에는 같은 ElastiCache 상태 저장소를 두 web node가 일관되게 쓰는지 아래 흐름으로 확인한다.

측정 흐름:

1. 로그인
2. refresh
3. logout
4. logout 이전 access token 재사용 시 `401/A006`
5. password reset request/confirm
6. email verification request/confirm
7. recommendation refresh lock/rate limit

명령:

```bash
ENV_FILE=.env.production \
APP_BASE_URL='https://youthmoa.kr' \
bash deploy/smoke/run-local-runtime-api-smoke.sh
```

전환 후 장애 중 검증:

- 로그인 후 EC2-1 app stop
- 같은 cookie/token으로 refresh/logout
- EC2-2가 정상 응답하는지 확인

목표:

- 단일 web node 장애 중 refresh/logout/revoke 계약이 유지된다.
- `older token after logout` 경계가 기존과 동일하게 유지된다.
- Redis failover 중 일시 실패가 있으면 실패 시각, 회복 시각, 실패 endpoint를 별도 기록한다.

### 5. Scheduler 단일 실행

2대 app에서 scheduler가 모두 켜지면 수집/알림/정리 작업이 중복 실행될 수 있다. 전환 후에는 `APP_SCHEDULER_ENABLED=true` 노드가 정확히 1대여야 한다.

대상:

- `CollectBatchService`
- `StatusUpdateService`
- `NotificationScheduleService`
- `UserPiiSyncRetryScheduler`
- `UserPiiSyncQueueRetentionService`
- `RecommendationRetentionService`
- `OperationalLogRetentionService`
- `AdminPolicyRegionAuditService`

전환 전 기록:

- 현재 scheduler는 단일 app process에서만 실행된다.
- 중복 실행 위험은 없지만, EC2 장애 시 scheduler도 함께 멈춘다.

전환 후 기록:

```bash
docker exec youth-welfare-app sh -lc 'printenv APP_SCHEDULER_ENABLED'
docker logs --since 24h youth-welfare-app | grep -E 'CollectBatchService|NotificationScheduleService|RetentionService|PolicyRegionAudit'
```

목표:

- EC2-1: `APP_SCHEDULER_ENABLED=true`
- EC2-2: `APP_SCHEDULER_ENABLED=false`
- 같은 scheduled job이 같은 시간대에 두 노드에서 시작한 흔적이 없어야 한다.

### 6. 운영 관측과 backlog 영향

전환 전후 같은 wrapper를 실행한다.

```bash
ENV_FILE=.env.production \
SMOKE_DB_MODE=postgres \
APP_BASE_URL='https://youthmoa.kr' \
bash deploy/smoke/run-local-ops-observation-suite.sh
```

비교할 값:

- `decision_class`
- collect failed/partial/circuit count
- notification failed/retry/stale backlog
- recommendation observation decision
- chat observability decision
- attention warning count

목표:

- 전환 후에도 `BASELINE_HEALTHY`
- collect/notification/recommendation backlog가 전환 때문에 증가하지 않음

### 7. 리소스와 비용

전환 전후 아래 값을 기록한다.

EC2:

```bash
uptime
df -h /
free -h
docker stats --no-stream youth-welfare-app
```

현재 ElastiCache 운영 경로에서는 `youth-welfare-redis` 컨테이너가 없으므로 EC2 측정은 app container만 보고, Redis 지표는 CloudWatch ElastiCache metric으로 본다.

RDS:

- DatabaseConnections
- CPUUtilization
- FreeStorageSpace
- ReadLatency/WriteLatency
- deadlocks
- long running transaction

Redis:

- EngineCPUUtilization
- DatabaseMemoryUsagePercentage
- Evictions
- CurrConnections
- ReplicationLag
- Failover event

ALB:

- HealthyHostCount
- UnHealthyHostCount
- HTTPCode_ELB_5XX_Count
- HTTPCode_Target_5XX_Count
- TargetResponseTime p95/p99

비용 비교:

| 항목 | 전환 전 | 전환 후 |
| --- | ---: | ---: |
| EC2 | 1대 | 2대 |
| ALB | 없음 | 1개 |
| Redis | ElastiCache Valkey 단일 노드 | ElastiCache replication group |
| RDS | 기존 | 기존 |
| 운영 복잡도 | 낮음 | 중간 |
| web node 장애 내성 | 없음 | 있음 |

## 전환 후 완료 판정표

| 항목 | 통과 기준 |
| --- | --- |
| 정상 상태 public `/` | 200, p95가 전환 전 대비 20% 이내 |
| 정상 상태 public `/api/policies` | 200, p95가 전환 전 대비 20% 이내 |
| ALB target health | EC2-1, EC2-2 모두 healthy |
| EC2-1 app stop | public `/` 와 핵심 API 200 유지 |
| EC2-2 app stop | public `/` 와 핵심 API 200 유지 |
| auth/session | login/refresh/logout/revoke smoke 통과 |
| Redis 공유 | node failover 뒤 재로그인 없이 refresh/logout 가능 |
| scheduler | enabled 노드 1대만 존재 |
| edge guard | `/actuator`, `/swagger-ui`, `/v3/api-docs` 외부 차단 |
| ops observation | `BASELINE_HEALTHY` |
| rollback | 기존 EC2 direct route 복구 절차 보존 |

## 비교 기록 템플릿

| Date | Topology | Git Head | Scenario | Before | After | Delta | Decision |
| --- | --- | --- | --- | ---: | ---: | ---: | --- |
| TBD | single EC2 | TBD | public `/` p95 | TBD | TBD | TBD | TBD |
| TBD | single EC2 | TBD | public `/api/policies` p95 | TBD | TBD | TBD | TBD |
| TBD | single EC2 | TBD | web node app stop RTO | full outage | TBD | TBD | TBD |
| TBD | ALB 2 EC2 | TBD | login-refresh-logout during node failure | N/A | TBD | TBD | TBD |
| TBD | ALB 2 EC2 | TBD | scheduler duplicate count | N/A | TBD | TBD | TBD |

## 운영자 해석

이 전환의 성공은 평상시 속도 향상이 아니다. 성공 기준은 아래에 가깝다.

- 장애 중에도 사용자가 계속 사이트를 볼 수 있다.
- 로그인 세션이 단일 web node 장애 때문에 끊기지 않는다.
- 운영자가 수동으로 EC2를 되살리는 동안에도 public traffic은 살아 있다.
- 그 대가로 생긴 ALB/ElastiCache/두 번째 EC2 비용과 운영 복잡도가 관측 가능하다.
