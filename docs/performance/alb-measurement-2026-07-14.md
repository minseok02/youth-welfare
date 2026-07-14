# ALB 2-EC2 측정 기록 2026-07-14

## 목적

2026-07-13 ALB 전환 기준선 이후, 최신 배포 커밋과 2대 EC2 구성이 실제로 안정화된 상태에서 다시 측정한다.
측정 전 인프라/앱/운영 조건을 먼저 고정해, 이후 반복 측정에서 구성 변화와 성능 변화를 분리한다.

## 측정 전 스냅샷

기록 시각: `2026-07-14T08:25:02Z`

| 항목 | 값 |
| --- | --- |
| Git branch | `refactor/admin-dashboard-sections` |
| Git head | `9a9f36ec248b9f11f418656863e3d140679e5373` |
| Commit | `Document final report assets and refine suggestions` |
| Public base URL | `https://youthmoa.kr` |
| ALB | `youth-welfare-alb` |
| ALB DNS | `youth-welfare-alb-1121910292.ap-northeast-2.elb.amazonaws.com` |
| ALB state | `active` |
| ALB scheme/type | `internet-facing` / `application` |
| Target group | `youth-welfare-web-tg` |
| Target protocol/port | `HTTP:80` |
| Health check | `/alb-health`, interval `15s`, healthy threshold `2`, unhealthy threshold `2`, matcher `200` |
| RDS | `youth-welfare-prod-db.cnqsmges40i1.ap-northeast-2.rds.amazonaws.com:5432/youth_welfare` |
| Redis | ElastiCache Valkey via runtime `REDIS_HOST` |
| Runtime compose | `docker-compose.prod.elasticache.yml` |
| App container | `youth-welfare-app` |

## EC2 노드 상태

| 노드 | Instance ID | AZ | Type | Private IP | Public IP | State | Scheduler | App health | App image | Root disk |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| primary | `i-0b8d95e454df5e0f0` | `ap-northeast-2b` | `t3.medium` | `172.31.25.51` | `3.38.21.132` | `running` | `true` | `healthy` | `sha256:4a34f0e97340cb42995a16a7ac4a438faaa7d632ba504fd01d074d1825b6c669` | `19G total / 15G used / 3.8G available / 80%` |
| secondary | `i-0e8a4cc599c1148c8` | `ap-northeast-2c` | `t3.medium` | `172.31.44.73` | `13.209.8.203` | `running` | `false` | `healthy` | `sha256:8be0184678b36ef58d5b55ee61f9cc07c080b053883ec2c78409c2d35b3b83c1` | `19G total / 7.3G used / 12G available / 40%` |

두 노드는 모두 같은 git head `9a9f36ec248b9f11f418656863e3d140679e5373` 를 checkout하고 있다.

리소스 quick snapshot:

| 노드 | uptime | load average | memory available | app memory | app CPU 순간값 |
| --- | --- | --- | --- | --- | --- |
| primary | `2 days, 3:36` | `0.08, 0.08, 0.05` | `2.1Gi` | `571.2MiB / 1GiB` | `0.16%` |
| secondary | `1 day, 3:54` | `0.00, 0.00, 0.00` | `2.5Gi` | `571.2MiB / 1GiB` | `0.33%` |

## ALB 및 DNS 상태

AWS ELB target health:

| Target | Port | State |
| --- | ---: | --- |
| `i-0e8a4cc599c1148c8` | 80 | `healthy` |
| `i-0b8d95e454df5e0f0` | 80 | `healthy` |

Route53 public authoritative 응답:

| Name | Response |
| --- | --- |
| `youthmoa.kr` | `13.209.214.158`, `54.116.109.58` |
| `www.youthmoa.kr` | `54.116.109.58`, `13.209.214.158` |

VPC resolver `172.31.0.2` 응답:

| Name | Response | 해석 |
| --- | --- | --- |
| `youthmoa.kr` | `13.209.214.158`, `54.116.109.58` | ALB |
| `www.youthmoa.kr` | `3.38.21.132` | stale/direct 응답 관찰됨 |

VPC에 연결된 private hosted zone은 없고, Route53 Resolver rule은 기본 internet recursive rule만 확인됐다.
따라서 public 사용자 경로는 ALB가 맞지만, EC2 내부에서 `www.youthmoa.kr` 를 직접 조회하는 측정은 결과 해석에서 제외하거나 `--resolve` 로 ALB IP를 명시한다.

## 측정 방법 기준

오늘 측정은 운영에 큰 부하를 주지 않는 정상 상태 관찰만 수행한다.
장애 드릴(`docker stop`, 인스턴스 stop, target deregister)은 별도 승인 후 수행한다.

사용 기준:

| 영역 | 방법 | 측정값 |
| --- | --- | --- |
| ALB 상태 | AWS ELB target health, CloudWatch ALB metrics | healthy target count, target response time, 4xx/5xx |
| HTTP latency | `curl -w` 반복 샘플 | DNS, connect, TLS, TTFB, total, status, p50/p95/p99/max |
| API latency | 실제 운영 API 계약 기준 순차 샘플 | error rate, p50/p95/p99/max |
| Browser interaction | 기존 Playwright interaction baseline | action wall time, max event duration |
| Edge/security | nginx header/blocked path 검증 | HSTS/CSP/guard status |
| 리소스 | EC2/Docker/로그/DB 관측 스크립트 | CPU, memory, disk, app log p95, nginx p95 |

판정 기준:

- error rate는 `0%` 를 기대한다.
- 반복 latency는 평균보다 `p95`, `p99`, `max` 를 우선한다.
- ALB target은 2개 모두 `healthy` 여야 한다.
- 자동완성은 2026-07-13 기준선 p95 `378.3ms` 대비 개선 여부를 본다.
- 정책 목록은 2026-07-13 기준선 p95 `178.8ms` 를 비교 기준으로 둔다.
- `www.youthmoa.kr` 내부 DNS stale 응답 때문에, EC2 내부에서 public URL을 측정할 때는 ALB IP 강제 경로를 별도 표기한다.

## 오늘 측정 항목

1. 정상 상태 topology/health 재검증
2. ALB target 분산과 target별 `/alb-health`, `/api/policies` 직접 검증
3. public edge/header 검증
4. 실제 운영 API 계약 기준 latency baseline
5. Web interaction baseline
6. EC2/Docker 리소스 snapshot
7. CloudWatch ALB 최근 구간 metrics snapshot
8. 결과를 2026-07-13 기준선과 비교

## 결과

### Artifact

| 항목 | 경로 |
| --- | --- |
| 운영 API latency | `tmp/performance/alb-valid-api-20260714/20260714T082705Z` |
| Web interaction | `tmp/performance/web-interaction-20260714/20260714T082826Z` |
| CloudWatch ALB metric snapshot | `tmp/performance/alb-cloudwatch-20260714-20260714T082927Z-result.json` |
| DB query baseline | `tmp/performance/db-query-20260714/20260714T083016Z` |
| nginx log observability | `tmp/performance/nginx-log-observability-20260714/20260714T083016Z` |
| app log observability | `tmp/performance/app-log-observability-20260714/20260714T083016Z` |

### ALB 상태와 분산

AWS target health API 기준:

| Target | State |
| --- | --- |
| `i-0e8a4cc599c1148c8` | `healthy` |
| `i-0b8d95e454df5e0f0` | `healthy` |

ALB IP를 명시한 `GET /` 60회 샘플:

| 노드 | access log hit |
| --- | ---: |
| primary `172.31.25.51` | 29 |
| secondary `172.31.44.73` | 31 |
| total | 60 |

두 target 모두 `/alb-health` 는 `UP`, `/api/policies?page=0&size=1` 은 정상 응답했다.

### 운영 API latency

측정 조건:

- `https://youthmoa.kr`
- ALB IP `13.209.214.158`, `54.116.109.58` 를 `curl --resolve` 로 교차 사용
- 시나리오별 warmup 2회, 측정 30회
- 운영 rate limit을 피하기 위해 순차 요청만 사용

| 시나리오 | 성공 | 오류 | p50 | p95 | p99 | max | 비고 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| `/` | 30 | 0 | 35.0ms | 40.6ms | 40.9ms | 40.9ms | 정적 프론트 |
| `/alb-health` | 30 | 0 | 50.5ms | 59.0ms | 61.5ms | 61.8ms | ALB health path |
| `GET /api/policies` | 30 | 0 | 145.6ms | 165.2ms | 194.1ms | 205.9ms | 목록 기본 |
| `GET /api/policies?sort=DEADLINE` | 25 | 5 | 140.3ms | 165.6ms | 186.4ms | 193.0ms | 반복 측정 중 429 발생 |
| `GET /api/policies/{id}` | 18 | 12 | 71.0ms | 91.6ms | 100.5ms | 102.7ms | 같은 상세 반복으로 429 발생 |
| `GET /api/policies/ranking` | 28 | 2 | 42.3ms | 47.0ms | 47.5ms | 47.6ms | 반복 측정 중 429 발생 |
| `GET /api/policies/search/trending` | 28 | 2 | 48.0ms | 69.0ms | 71.2ms | 72.0ms | 반복 측정 중 429 발생 |
| `POST /api/policies/search` | 30 | 0 | 50.0ms | 61.2ms | 70.3ms | 72.3ms | 키워드 검색 |
| `POST /api/policies/search` deadline | 26 | 4 | 49.9ms | 57.6ms | 301.8ms | 383.2ms | 반복 측정 중 429 및 1회 tail |
| `POST /api/policies/search/suggestions` | 30 | 0 | 70.6ms | 84.9ms | 87.8ms | 88.3ms | 자동완성 |

해석:

- 자동완성 p95는 2026-07-13 기준선 `378.3ms` 에서 `84.9ms` 로 개선됐다.
- 기본 목록 p95는 2026-07-13 기준선 `178.8ms` 에서 `165.2ms` 로 소폭 개선됐다.
- 429가 발생한 시나리오는 성능 실패라기보다 동일 출발지/동일 endpoint 반복 측정으로 rate limit이 개입한 결과다. 다음 반복 측정에서는 endpoint별 run 수를 낮추거나 `X-Forwarded-For` 를 조작하지 않는 범위에서 측정 간격을 늘린다.

### Edge/security 검증

`deploy/nginx/verify-edge-baseline.sh` 결과:

- `root_status=200`
- external `/actuator/health=403`
- `/actuator`, `/swagger-ui`, `/v3/api-docs` 차단
- dotfile/env/log/sql/archive류 대표 경로 차단
- `Strict-Transport-Security`, `X-Frame-Options`, `X-Content-Type-Options`, `Content-Security-Policy` 존재
- `Server` 헤더는 `nginx` 로 버전 문자열을 노출하지 않음

`deploy/performance/run-local-edge-baseline.sh` 는 실패했다. 원인은 현재 API 계약과 맞지 않는 legacy `GET /api/policies/search` 케이스와, 직전 latency 측정으로 인한 429가 섞였기 때문이다. edge 보안 검증은 별도 wrapper에서 통과했으므로 운영 edge 상태는 정상으로 본다.

### Web interaction

| 흐름 | 상태 | action wall | max event duration | 비고 |
| --- | ---: | ---: | ---: | --- |
| 정책 검색 입력 후 Enter | 200 | 687ms | 80ms | 실제 브라우저 플로우 |
| 로그인 폼 입력 | 200 | 312ms | 0ms | 비로그인 화면 |

2026-07-13 기준 `정책 검색 입력 후 Enter 696ms`, `로그인 폼 입력 315ms` 와 거의 같다.

### CloudWatch ALB 최근 1시간

| Metric | 값 |
| --- | --- |
| `HealthyHostCount` average/latest | `2.0` / `2.0` |
| `UnHealthyHostCount` max/latest | `0.0` / `0.0` |
| `RequestCount` sum | `2032` |
| `TargetResponseTime` p95 latest | `0.124813s` |
| `TargetResponseTime` p99 latest | `0.444797s` |
| `HTTPCode_ELB_5XX_Count` | datapoint 없음 |
| `HTTPCode_Target_5XX_Count` | datapoint 없음 |

### 로그 관측

nginx access log 최근 10,000 lines:

| 항목 | 값 |
| --- | ---: |
| status 200 | 9694 |
| status 3xx | 210 |
| status 4xx | 92 |
| status 5xx | 4 |
| request_time p50 | 0.000s |
| request_time p95 | 0.035s |
| request_time p99 | 0.093s |
| request_time max | 9.834s |

app log 최근 샘플:

| 항목 | 값 |
| --- | ---: |
| raw error lines | 0 |
| raw warn lines | 0 |
| API request count | 265 |
| API status 200 | 251 |
| API status 401 | 1 |
| API status 429 | 13 |
| API duration p50 | 56ms |
| API duration p95 | 125ms |
| API duration p99 | 786ms |
| API duration max | 1492ms |

429는 이번 반복 측정의 영향으로 보며, app error/warn은 관측되지 않았다.

### DB query baseline

| 쿼리 | execution | planning | 비고 |
| --- | ---: | ---: | --- |
| `admin_collect_failures_recent` | 0.635ms | 0.759ms | seq scan |
| `policy_detail_first` | 0.120ms | 2.092ms | index scan |
| `policy_list_created_at` | 14.756ms | 4.152ms | seq scan |
| `policy_search_keyword_api_shape` | 366.874ms | 3.901ms | index scan |
| `recent_policy_views_user` | 0.174ms | 0.618ms | seq scan |
| `recommendation_logs_recent_window` | 11.293ms | 0.735ms | seq scan |

테이블 규모:

| 테이블 | rows |
| --- | ---: |
| `welfare_services` | 15101 |
| `raw_api_payloads` | 44952 |
| `recommendation_logs` | 31985 |
| `search_logs` | 1727 |
| `recent_policy_views` | 69 |
| `chat_messages` | 36 |

`policy_search_keyword_api_shape` 는 여전히 DB query baseline에서 무겁지만, 실제 운영 `POST /api/policies/search` p95는 `61.2ms` 로 빠르다. 이후 확인 결과 이 시점의 DB baseline SQL은 실제 generated-field fast path와 완전히 같지 않았다.

## 최신 generated-search rebaseline

실행 기준:

- commit: `0ce85660ed656f91ee71ad2e5a93aadd1dfb1346`
- 긴 load test 제외
- local API/DB/edge/app log/nginx log 관측

초기 재측정:

| 항목 | 결과 |
| --- | ---: |
| local API `policy_search_keyword` p95 | 18.9ms |
| local API `policy_list_default` p95 | 76.7ms |
| DB `policy_search_keyword_api_shape` | 249.283ms |
| DB `policy_search_keyword_legacy_or_shape` | 290.632ms |
| edge `POST /api/policies/search` | 300.144ms |
| app log API p95 | 207ms |
| nginx upstream p95 | 84ms |

판정:

- local API 검색은 public search cache warm 영향이 있어 DB uncached 비용과 직접 비교하면 안 된다.
- 기존 DB `policy_search_keyword_api_shape` representative는 raw `to_tsvector(...)`/`lower(...)` 계산이 남아 있어 현재 앱 fast path와 어긋났다.
- `run-local-edge-baseline.sh` 는 이미 현재 계약인 `POST /api/policies/search` 를 사용하고 있다.

대표 DB baseline 정렬 후:

| 항목 | 전 | 후 | 판정 |
| --- | ---: | ---: | --- |
| DB `policy_search_keyword_api_shape` | 249.283ms | 105.263ms | generated-field path로 정렬됨 |
| DB `policy_search_keyword_legacy_or_shape` | 290.632ms | 398.957ms | raw 비교 경로로 유지 |

최신 artifact:

- suite: `tmp/performance/latest-rebaseline-suite-20260714/20260714T170401Z`
- edge: `tmp/performance/latest-rebaseline-edge-20260714/20260714T170523Z`
- app log: `tmp/performance/latest-rebaseline-app-log-20260714/20260714T170523Z`
- nginx log: `tmp/performance/latest-rebaseline-nginx-log-20260714/20260714T170523Z`
- aligned DB: `tmp/performance/db-query-generated-aligned-20260714/20260714T170706Z`

## 오늘 판정

| 항목 | 판정 |
| --- | --- |
| ALB target 2대 healthy | 통과 |
| ALB 분산 | 통과 |
| public Route53 -> ALB | 통과 |
| EC2 내부 DNS | 주의: `www.youthmoa.kr` stale direct 응답 관찰 |
| edge security baseline | 통과 |
| 자동완성 개선 효과 | 통과 |
| 기본 목록 latency | 통과 |
| Web interaction | 유지 |
| app/nginx log | 통과, 측정 유발 429 제외 |
| 장애 드릴 | 미실행, 별도 승인 필요 |

## 다음 측정 때 조정

1. rate limit 영향이 있는 endpoint는 run 수를 `10~15` 로 낮추거나 측정 간격을 늘린다.
2. 검색 측정은 cache cold/warm을 분리한다.
3. uncached search 대표 쿼리의 최종 rank/window 비용을 줄일 수 있는지 별도 계획으로 비교한다.
4. `GET /api/policies/ranking` tail latency를 더 큰 샘플로 확인한다.
5. 장애 드릴은 점검창에 EC2-1/EC2-2 각각 app stop 방식으로 분리해서 측정한다.
6. EC2 내부 `www.youthmoa.kr` DNS stale 응답은 계속 관찰하되, 운영 사용자 기준 측정은 authoritative/public 또는 `--resolve` ALB IP 기준으로 수행한다.
