# ALB 현재 성능 기준선

작성일: 2026-07-13

## 목적

ALB + EC2 2대 구성으로 전환한 뒤, 최적화 전 비교 기준선을 고정한다.
이 문서는 이후 병목 개선 작업의 before 값으로 사용한다.

## 측정 조건

- 외부 기준 URL: `https://youthmoa.kr`
- 라우팅: Route53 `A/AAAA Alias` 아님, `A Alias` 로 ALB 연결
- ALB: `youth-welfare-alb`
- 대상 그룹: `youth-welfare-web-tg`
- 대상: 기존 EC2 + 신규 EC2, HTTP 80, health check `/alb-health`
- Git: `ee08c19370f5` (`Update HA handoff notes`)
- 측정 방식: 운영 부하를 크게 만들지 않는 순차 요청 위주

## Artifact

| 항목 | 경로 |
| --- | --- |
| 전체 baseline suite | `tmp/performance/alb-baseline-20260713T170309Z/latest` |
| 실제 API 계약 기준 외부 API 측정 | `tmp/performance/alb-valid-api-baseline-20260713T170710Z` |
| Web interaction 측정 | `tmp/performance/alb-baseline-web-20260713T170415Z/latest` |

## 외부 API 기준선

실제 프론트엔드가 사용하는 API 계약에 맞춰 별도 측정했다.

| 항목 | 성공 | 오류 | p50 | p95 | p99 | max | 비고 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| `/` | 20 | 0 | 8.8ms | 9.5ms | 9.8ms | 9.9ms | 정적 프론트 |
| `/alb-health` | 20 | 0 | 8.9ms | 11.2ms | 12.9ms | 13.3ms | ALB health path |
| `GET /api/policies` | 20 | 0 | 116.8ms | 178.8ms | 196.4ms | 200.8ms | 목록 기본 |
| `GET /api/policies?sort=DEADLINE` | 20 | 0 | 111.5ms | 151.3ms | 156.9ms | 158.2ms | 마감임박 |
| `GET /api/policies/{id}` | 18 | 2 | 31.4ms | 41.1ms | 44.1ms | 44.8ms | 동일 상세 반복으로 429 발생 |
| `GET /api/policies/ranking` | 20 | 0 | 15.1ms | 18.8ms | 20.8ms | 21.4ms | 랭킹 |
| `GET /api/policies/search/trending` | 20 | 0 | 19.2ms | 31.9ms | 37.4ms | 38.8ms | 인기 검색어 |
| `POST /api/policies/search` | 20 | 0 | 20.9ms | 26.7ms | 31.3ms | 32.5ms | 키워드 검색 |
| `POST /api/policies/search` deadline | 20 | 0 | 21.6ms | 30.2ms | 40.8ms | 43.5ms | 키워드 검색 + 마감순 |
| `POST /api/policies/search/suggestions` | 20 | 0 | 275.2ms | 378.3ms | 392.9ms | 396.6ms | 자동완성 |

## 기존 baseline suite 요약

`deploy/performance/run-local-performance-baseline-suite.sh` 실행 결과:

- `performance_baseline_suite=passed`
- API latency: 11.697s
- DB query baseline: 47.702s
- Redis baseline: 0.066s

API latency script 중 일부 케이스는 현재 API 계약과 맞지 않아 실패로 기록됐다.
이 실패는 운영 장애가 아니라 측정 케이스의 불일치다.

- `GET /api/policies/search`: 현재 서버는 `POST /api/policies/search` 사용
- `GET /api/policies/search/suggestions`: 현재 서버는 `POST /api/policies/search/suggestions` 사용
- `statusFilter=open`: 현재 서버는 `ACTIVE_ONLY`, `ALL`, `EXPIRED_ONLY` 사용
- 외부 `/actuator/health`: nginx에서 외부 공개하지 않는 것이 정상

## DB 기준선

주요 DB query baseline:

| 쿼리 | execution | planning | 비고 |
| --- | ---: | ---: | --- |
| `policy_detail_first` | 0.862ms | 1.488ms | index scan |
| `policy_list_created_at` | 12.446ms | 1.791ms | seq scan |
| `policy_search_keyword_api_shape` | 349.602ms | 7.343ms | 검색 랭킹 계산이 무거움 |
| `recommendation_logs_recent_window` | 10.654ms | 2.154ms | seq scan |

`policy_search_keyword_api_shape` 는 `to_tsvector`, `LIKE`, `similarity`, 랭킹 계산을 함께 수행하며 top-N sort까지 포함한다.
다만 실제 외부 `POST /api/policies/search` 측정은 p95 26.7ms로 빠르게 나왔으므로, 이 DB baseline 쿼리가 현재 프론트 요청의 정확한 hot path인지 먼저 재확인해야 한다.

## Web interaction 기준선

| 흐름 | 상태 | action wall | max event duration | 비고 |
| --- | ---: | ---: | ---: | --- |
| 정책 검색 입력 후 Enter | 200 | 696ms | 72ms | 실제 브라우저 플로우 |
| 로그인 폼 입력 | 200 | 315ms | 0ms | 비로그인 화면 |

## 서버 스냅샷

기존 EC2에서 측정한 현장 스냅샷:

| 항목 | 값 |
| --- | --- |
| uptime | 1 day, 12:19 |
| load average | 0.26, 0.28, 0.13 |
| memory | 3.7Gi total / 2.2Gi available |
| root disk | 19G total / 14G used / 4.9G available / 74% |
| app container | `youth-welfare-app` healthy |
| app memory | 676.8MiB / 1GiB |
| app CPU 순간값 | 15.13% |
| nginx/docker | active |
| 최근 nginx status | 198건 200, 2건 429 |

AWS CLI 역할에는 `elasticloadbalancing:DescribeTargetGroups` 권한이 없어 target health를 CLI artifact로 저장하지 못했다.
콘솔 기준으로는 두 target 모두 healthy 상태에서 측정했다.

## 병목 후보

1. 자동완성 API
   - `POST /api/policies/search/suggestions` p95 378.3ms.
   - 우선 최적화 후보로 본다.

2. 정책 목록 API
   - `GET /api/policies` p95 178.8ms.
   - 기본 목록은 사용자가 가장 자주 밟는 경로라 개선 가치가 크다.

3. 정책 검색 DB baseline
   - DB 기준 `policy_search_keyword_api_shape` 는 349.602ms.
   - 실제 API 측정과 차이가 크므로, 현재 production code path와 baseline SQL이 같은지 확인한 뒤 조정한다.

4. 디스크 사용량
   - root disk 74%.
   - 성능 병목은 아니지만 artifact, Docker image, 로그가 쌓이면 운영 리스크가 된다.

## 다음 개선-재측정 순서

1. 자동완성 쿼리와 repository 코드 확인
2. 자동완성 단일 개선 적용
3. 같은 명령으로 `POST /api/policies/search/suggestions` 재측정
4. p50/p95/p99/max와 오류율 비교
5. 목록 API 개선은 자동완성 개선 후 별도 작업으로 분리

비교할 핵심 before 값:

- 자동완성 p95: 378.3ms
- 목록 기본 p95: 178.8ms
- 정책 검색 입력 브라우저 action wall: 696ms
