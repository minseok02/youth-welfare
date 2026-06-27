# EC2 단일 구조에서 EC2 + RDS 구조로 전환하는 상세 근거 보고서

작성 기준일: 2026-05-21
측정 기준 시각: 2026-05-21 12:41 UTC 전후
대상 서비스: `youth-welfare`
현재 운영 구조: 단일 EC2 내부 Docker Compose 기반 `Spring Boot app + PostgreSQL(pgvector) + Redis`

## 1. 목적

현재 서비스는 하나의 EC2 인스턴스 안에서 애플리케이션, 데이터베이스, Redis, Docker image, Docker build cache, 로그, 운영 파일이 함께 동작하는 구조다. 앞으로 EC2 + RDS 구조로 변경하려고 하지만, 단순히 “RDS가 더 좋아 보인다”는 이유만으로 변경하면 안 된다. 구조 변경은 비용, 운영 방식, 네트워크, 보안 그룹, 백업 정책, 장애 대응 방식까지 바꾸는 일이기 때문에 현재 구조의 한계와 전환 필요성을 측정값과 운영 리스크로 설명할 수 있어야 한다.

이 문서는 다음 목적을 가진다.

- 현재 EC2 단일 구조의 실제 상태를 기록한다.
- 성능, 디스크, DB 연결, 운영 안정성 관점에서 구조 변경 근거를 남긴다.
- “지금 당장 CPU가 터지고 있어서 RDS로 옮긴다”는 과장된 주장을 피하고, 실제 수치에 맞는 정직한 결론을 도출한다.
- 나중에 RDS 전환 전후 비교 기준으로 다시 사용할 수 있는 baseline을 남긴다.
- 팀원, 리뷰어, 운영자, 미래의 본인이 왜 이 변경을 했는지 확인할 수 있도록 상세히 서술한다.

## 2. 현재 구조 요약

현재 `docker-compose.yml` 기준으로 서비스는 다음 3개 컨테이너로 구성되어 있다.

| 컨테이너 | 역할 | 이미지/구성 | 외부 노출 |
| --- | --- | --- | --- |
| `youth-welfare-app` | Spring Boot API 서버 | 자체 빌드 이미지 `youth-welfare-app` | `127.0.0.1:8082 -> 8080` |
| `youth-welfare-db` | PostgreSQL + pgvector | `pgvector/pgvector:pg16` | `127.0.0.1:5433 -> 5432` |
| `youth-welfare-redis` | Redis | `redis:7-alpine` | `127.0.0.1:6379 -> 6379` |

Nginx는 외부 HTTPS 요청을 받아 `/api/` 경로를 `http://127.0.0.1:8082`로 프록시한다. 즉 사용자의 API 요청은 다음 경로를 지난다.

```text
Client
  -> Nginx on EC2
  -> Spring Boot container on same EC2
  -> PostgreSQL container on same EC2
  -> Docker volume on same EC2 disk
```

현재 애플리케이션은 Docker 내부 DNS 이름인 `db`로 PostgreSQL에 접속한다.

```yaml
DB_URL: jdbc:postgresql://db:5432/youth_welfare?sslmode=disable
APP_PII_DB_URL: jdbc:postgresql://db:5432/youth_welfare?sslmode=disable&currentSchema=youth_welfare_pii
NOTIFICATION_PII_DB_URL: jdbc:postgresql://db:5432/youth_welfare?sslmode=disable&currentSchema=youth_welfare_pii
```

PostgreSQL 데이터는 Docker volume에 저장된다.

```yaml
volumes:
  - postgres_data:/var/lib/postgresql/data
```

따라서 현재 DB의 실제 지속 저장소는 RDS, 별도 EBS 볼륨, 관리형 DB가 아니라 EC2 내부 Docker volume이다. EC2 루트 디스크, Docker storage, DB volume, 앱 배포 산출물, build cache가 모두 같은 인스턴스 자원을 공유한다.

## 3. 측정 방법

측정은 운영 중인 EC2 내부에서 수행했다. 외부 네트워크 지연, 사용자의 실제 브라우저, CloudFront 같은 별도 경로는 포함하지 않았다. API 지연시간은 EC2 내부 loopback 주소인 `127.0.0.1:8082`로 측정했다.

사용한 주요 명령은 다음과 같다.

```bash
df -hT
free -h
uptime
docker ps
docker stats --no-stream
docker system df -v
docker exec -i youth-welfare-db psql -U postgres -d youth_welfare
curl -sS -o /dev/null -w '%{time_total}\n'
```

API 성능 측정 대상은 공개 GET 엔드포인트인 정책 목록 API다.

```text
GET http://127.0.0.1:8082/api/policies?page=0&size=20
```

이 엔드포인트를 선택한 이유는 다음과 같다.

- 인증 없이 호출 가능하다.
- 조회수 증가 같은 명시적인 쓰기성 상세 API보다 부작용이 작다.
- 실제 사용자 화면에서 사용되는 주요 정책 조회 경로다.
- DB 조회를 포함하므로 앱 단독 응답이 아니라 앱 + DB 경로의 기준값을 볼 수 있다.

주의할 점도 있다.

- 이 측정은 아주 정교한 부하 테스트가 아니다.
- `wrk`, `k6`, `JMeter` 같은 전문 도구로 긴 시간 동안 측정한 값이 아니다.
- 운영 중인 작은 샘플 측정이므로 절대적인 SLA 확정값으로 사용하면 안 된다.
- 다만 현재 구조의 병목 가능성과 전환 필요성을 판단하는 초기 근거로는 충분하다.

## 4. 인스턴스 리소스 측정 결과

### 4.0 EC2 인스턴스 스펙

EC2 metadata와 OS에서 확인한 현재 인스턴스 스펙은 다음과 같다.

| 항목 | 값 |
| --- | --- |
| EC2 instance type | `t3.medium` |
| Availability Zone | `ap-northeast-2b` |
| CPU architecture | `x86_64` |
| vCPU | 2 |
| CPU model | Intel(R) Xeon(R) Platinum 8259CL CPU @ 2.50GHz |
| Core/socket | 1 |
| Thread/core | 2 |
| OS에서 인식한 메모리 | 약 3.7GiB |
| Swap | 없음 |
| 루트 디스크 | 19GB |

해석:

- 현재 EC2는 `t3.medium`으로, 2 vCPU와 약 4GiB 계열 메모리를 가진 burstable 타입이다.
- OS에서 실제 사용 가능 메모리는 약 3.7GiB로 관측되었다.
- 이 2 vCPU/3.7GiB 메모리 안에서 Nginx, Spring Boot JVM, PostgreSQL, Redis, Docker daemon, OS page cache가 모두 함께 동작한다.
- `t3` 계열은 burstable 인스턴스이므로 CPU credit 상태에 따라 지속 부하에서 성능 특성이 달라질 수 있다. 이번 측정에서는 CloudWatch의 CPU credit 잔량까지는 확인하지 않았다.
- 현재 평균 CPU 사용률은 낮게 관측되었지만, 2 vCPU 안에서 앱 처리, DB query, DB checkpoint, autovacuum, Redis, Docker 작업이 동시에 돌아간다는 점이 구조적 제약이다.

이 스펙은 RDS 전환 근거에서 중요하다. 현재 구조는 애플리케이션 서버와 데이터베이스 서버가 각각 독립된 자원을 쓰는 구조가 아니라, `t3.medium` 한 대의 2 vCPU와 약 3.7GiB 메모리를 공유하는 구조다. 따라서 평상시에는 낮은 사용률로 보이더라도 배포, 수집, 추천, 검색, 동시 사용자 요청, DB maintenance 작업이 겹칠 때 서로 영향을 줄 수 있다.

### 4.1 디스크 상태

측정 결과 EC2 루트 파일시스템은 다음 상태였다.

```text
Filesystem  Type  Size  Used  Avail  Use%  Mounted on
/dev/root   ext4   19G   15G   4.0G   79%  /
```

해석:

- 루트 디스크는 19GB이고, 이미 15GB를 사용하고 있다.
- 사용률은 79%다.
- 남은 공간은 약 4.0GB다.
- PostgreSQL DB 자체는 아직 423MB 수준이지만, Docker image, build cache, volume, 로그, 과거 volume이 모두 같은 디스크를 사용한다.
- 단일 EC2 구조에서는 앱 배포나 Docker build cache 증가만으로도 DB write 여유 공간에 영향을 줄 수 있다.

이 지점이 중요하다. 지금 DB 크기만 보면 “DB가 423MB밖에 안 되니 아직 괜찮다”고 볼 수 있다. 하지만 실제 운영 리스크는 DB 크기만으로 결정되지 않는다. 같은 디스크에 아래 항목들이 함께 존재한다.

- 애플리케이션 Docker image
- PostgreSQL Docker image
- Redis Docker image
- Docker build cache
- PostgreSQL data volume
- 과거 MySQL data volume
- 컨테이너 로그
- 시스템 로그
- 배포 산출물
- 임시 파일

단일 EC2 구조에서는 이 모든 항목이 같은 19GB 루트 볼륨을 놓고 경쟁한다. 이 구조에서 가장 위험한 장애 유형은 “트래픽이 많아서 CPU가 잠깐 높다”가 아니라 “디스크가 가득 차서 DB가 쓰지 못하는 상태”다.

PostgreSQL은 디스크 여유 공간이 부족해지면 WAL 기록, temp file 생성, vacuum, index update, insert/update 작업이 실패할 수 있다. 애플리케이션도 로그 기록, 파일 생성, 컨테이너 재시작, 이미지 pull/build 과정에서 실패할 수 있다. 즉 하나의 디스크 full이 앱 장애와 DB 장애를 동시에 일으킬 수 있다.

### 4.2 메모리 상태

측정 결과 메모리는 다음과 같았다.

```text
total:      3.7GiB
used:       1.8GiB
free:       422MiB
buff/cache: 2.0GiB
available: 1.9GiB
swap:       0B
```

해석:

- EC2는 약 3.7GiB 메모리를 가진다.
- swap은 없다.
- 사용 가능 메모리는 약 1.9GiB로 보이지만, 앱 JVM, PostgreSQL, Redis, Docker daemon, OS page cache가 모두 같은 메모리를 공유한다.
- PostgreSQL은 자체 shared buffer뿐 아니라 OS page cache의 영향을 크게 받는다.
- 앱 JVM heap, PostgreSQL buffer/cache, Redis memory가 같은 인스턴스 안에서 경쟁한다.

현재 순간만 보면 메모리가 즉시 부족한 상태는 아니다. 그러나 단일 구조에서는 다음 상황에서 메모리 압박이 빠르게 커질 수 있다.

- 정책 수집 배치 실행
- 추천/검색/임베딩 관련 작업 실행
- 동시 사용자 증가
- JVM heap 증가
- DB sort/hash/temp 작업 증가
- Docker build 또는 배포 과정
- 로그 수집/압축/백업 작업

swap이 없기 때문에 메모리 압박이 커지면 성능 저하를 swap으로 완충하기 어렵다. 최악의 경우 OOM killer가 프로세스를 종료할 수 있고, 이때 앱 또는 DB가 같이 영향을 받을 수 있다.

### 4.3 컨테이너별 리소스 상태

`docker stats --no-stream` 측정 결과는 다음과 같았다.

| 컨테이너 | CPU | 메모리 사용량 | 메모리 비율 | Block I/O | Network I/O |
| --- | ---: | ---: | ---: | ---: | ---: |
| `youth-welfare-app` | 약 0.19-0.22% | 약 704-707MiB / 3.744GiB | 약 18.4% | 15.3-15.5MB / 47.5-47.6MB | 226-232MB / 169-173MB |
| `youth-welfare-db` | 약 0.00-6.34% | 약 221-275MiB / 3.744GiB | 약 5.8-7.2% | 1.91GB / 8.56GB | 1.65GB / 3.93GB |
| `youth-welfare-redis` | 약 0.66-3.22% | 약 7.7MiB / 3.744GiB | 약 0.2% | 39.3MB / 5.83MB | 2.84MB / 1.6MB |

해석:

- 현재 CPU가 항상 높은 상태는 아니다.
- 앱 메모리 사용량은 약 700MiB 수준이다.
- DB 메모리 사용량은 200MiB대지만, PostgreSQL은 OS page cache를 함께 활용하므로 컨테이너 메모리만 보고 DB 영향이 작다고 판단하면 안 된다.
- DB 컨테이너의 누적 Block I/O는 read 1.91GB / write 8.56GB로, 앱보다 훨씬 큰 저장소 I/O를 발생시키고 있다.
- 단일 EC2에서는 DB write I/O가 앱 배포, 로그, Docker build cache, OS 작업과 같은 디스크 계층을 공유한다.

중요한 점은 “현재 CPU가 낮으니 RDS가 필요 없다”가 아니다. 현재 관측된 주된 리스크는 CPU 포화보다 저장소 격리, 장애 격리, 백업/복구, 동시성 증가 시 응답 지연이다.

### 4.4 Docker 디스크 사용량

`docker system df -v` 기준 주요 값은 다음과 같다.

| 항목 | 크기 |
| --- | ---: |
| `youth-welfare-app` image | 약 544MB |
| `eclipse-temurin:17-jdk-jammy` image | 약 635MB |
| `pgvector/pgvector:pg16` image | 약 621MB |
| `redis:7-alpine` image | 약 57.8MB |
| `mysql:8.0` image | 약 1.1GB |
| Docker build cache | 약 4.984GB |
| `youth-welfare_postgres_data` volume | 약 621MB |
| `youth-welfare_mysql_data` volume | 약 751MB |

해석:

- DB 자체보다 Docker build cache가 훨씬 크다.
- 과거 MySQL volume도 남아 있다.
- 운영 서버에서 빌드/배포를 반복하면 build cache가 계속 증가할 수 있다.
- 단일 EC2 구조에서는 빌드 캐시 정리 누락도 DB 저장소 장애로 이어질 수 있다.

RDS로 분리하면 적어도 PostgreSQL 데이터 파일과 WAL은 애플리케이션 서버의 Docker storage와 분리된다. EC2의 Docker cache가 증가해도 RDS의 DB storage를 직접 압박하지 않는다.

## 5. PostgreSQL 측정 결과

### 5.1 PostgreSQL 기본 정보

측정된 PostgreSQL 버전은 다음과 같다.

```text
PostgreSQL 16.13 (Debian 16.13-1.pgdg12+1)
```

현재 DB 크기는 다음과 같다.

```text
youth_welfare: 423MB
```

사용자 relation 총량은 다음과 같다.

```text
user_relation_total: 388MB
```

해석:

- DB는 아직 아주 큰 규모는 아니다.
- 그러나 서비스 데이터는 이미 정책, raw payload, 정규화 결과, chunk, taxonomy, 추천/검색 로그, 사용자/PII 관련 스키마 등 여러 성격의 데이터를 포함한다.
- 크기가 작다고 해서 운영 DB를 앱 서버 내부 Docker volume에 계속 두는 것이 안전하다는 의미는 아니다.
- 오히려 크기가 아직 작을 때 RDS로 옮기는 편이 마이그레이션 리스크가 낮다.

### 5.2 DB 연결 상태

측정 시점의 DB 연결은 다음과 같다.

```text
active_connections: 31
```

상세 상태:

| 사용자 | application_name | 상태 | 개수 |
| --- | --- | --- | ---: |
| `app_core_rw` | PostgreSQL JDBC Driver | idle | 10 |
| `app_pii_rw` | PostgreSQL JDBC Driver | idle | 10 |
| `notification_pii_ro` | PostgreSQL JDBC Driver | idle | 10 |
| `postgres` | psql | active | 1 |

PostgreSQL 설정:

```text
max_connections: 100
```

해석:

- 실제 요청이 많지 않은 시점에도 앱은 기본적으로 30개의 idle connection을 유지한다.
- 이는 datasource가 3개이고, 각 datasource의 Hikari pool 기본값이 사실상 10개씩 잡힌 영향으로 볼 수 있다.
- 현재 구조에서 PostgreSQL `max_connections` 100 중 30개가 기본 점유된다.
- 운영자 접속, 배치 작업, admin 기능, 수집 작업, 추천 작업, 테스트 작업 등이 겹치면 connection 여유가 줄어든다.

이 수치는 RDS 전환 시 매우 중요하다. RDS로 옮기더라도 애플리케이션의 connection pool 설정을 그대로 두면 RDS에도 기본 연결 30개가 생긴다. 따라서 구조 전환과 별개로 datasource별 Hikari maximum pool size를 명시적으로 검토해야 한다.

예를 들면 다음 관점이 필요하다.

- `app_core_rw`는 일반 API 트래픽 기준으로 pool size 산정
- `app_pii_rw`는 PII 작업 빈도 기준으로 더 작게 제한 가능
- `notification_pii_ro`는 알림 조회/발송 작업 기준으로 제한 가능
- admin/cleanup 전용 datasource도 추가되어 있다면 각각 별도 제한 필요
- RDS 인스턴스의 `max_connections`와 앱 인스턴스 수를 함께 계산

현재 EC2 단일 구조에서는 앱과 DB가 한 인스턴스에 있어 connection 증가가 곧 DB 메모리와 EC2 메모리 부담으로 이어진다. RDS로 분리하면 DB connection 부담은 RDS 측으로 이동하지만, connection 수 관리는 여전히 필요하다.

### 5.3 PostgreSQL 주요 설정

측정된 주요 설정은 다음과 같다.

| 설정 | 값 |
| --- | ---: |
| `max_connections` | 100 |
| `shared_buffers` | 128MB |
| `effective_cache_size` | 4GB |
| `work_mem` | 4MB |
| `maintenance_work_mem` | 64MB |
| `wal_level` | replica |
| `max_wal_size` | 1GB |
| `checkpoint_timeout` | 300s |

해석:

- 현재 PostgreSQL 설정은 컨테이너 기본값에 가까운 형태로 보인다.
- `shared_buffers`가 128MB로 작다.
- DB 규모가 현재는 작기 때문에 즉시 문제는 아니지만, 검색/추천/수집 데이터가 늘면 DB 전용 튜닝이 필요하다.
- RDS로 분리하면 인스턴스 클래스, parameter group, storage type, IOPS, autovacuum 설정을 DB 용도에 맞게 별도로 관리할 수 있다.

### 5.4 PostgreSQL cache hit, temp file, deadlock

`pg_stat_database` 기준 주요 값은 다음과 같다.

| 항목 | 값 |
| --- | ---: |
| transaction commit | 611,650 |
| transaction rollback | 252 |
| blocks read | 1,814,769 |
| blocks hit | 72,167,359 |
| cache hit ratio | 97.55% |
| rows returned | 144,437,383 |
| rows fetched | 43,996,060 |
| rows inserted | 1,554,463 |
| rows updated | 605,586 |
| rows deleted | 986,567 |
| temp files | 17 |
| temp bytes | 92MB |
| deadlocks | 0 |

해석:

- cache hit ratio는 97.55%로 나쁘지 않다.
- deadlock은 0으로, 현재 관측상 lock contention이 심하다고 보기는 어렵다.
- temp file 17개, 92MB가 발생했다. 현재 규모에서는 크지 않지만, sort/hash/query 규모가 커지면 temp I/O가 증가할 수 있다.
- insert/update/delete 누적량이 꽤 있으며, 수집/정규화/추천/로그성 데이터가 계속 변하는 구조임을 보여준다.

이 결과는 “DB가 이미 망가져 있다”는 근거가 아니다. 오히려 현재 DB 상태는 큰 오류 없이 동작 중이다. 그러나 RDS 전환 근거는 장애가 이미 터진 뒤의 복구가 아니라, 운영 책임 분리와 장애 예방에 있다.

### 5.5 테이블 크기와 dead tuple

상위 테이블은 다음과 같다.

| 테이블 | 전체 크기 | live rows | dead rows | 특징 |
| --- | ---: | ---: | ---: | --- |
| `policy_chunks` | 97MB | 57,961 | 256 | 정책 chunk/검색/임베딩 관련 데이터 |
| `service_facts` | 76MB | 186,162 | 25,251 | 정규화 fact 데이터, dead tuple 약 11.9% |
| `raw_api_payloads` | 75MB | 44,310 | 4,565 | 외부 API 원문 payload 저장 |
| `welfare_services` | 39MB | 14,874 | 2,532 | 정책 서비스 핵심 테이블, dead tuple 약 14.6% |
| `service_taxonomy_summary_slots` | 27MB | 51,216 | 0 | taxonomy summary |
| `service_taxonomy_terms` | 27MB | 50,036 | 6,275 | taxonomy term, dead tuple 약 11.1% |
| `welfare_service_details` | 19MB | 14,874 | 0 | 정책 상세 |
| `service_tags` | 10MB | 49,932 | 7,242 | tag, dead tuple 약 12.7% |
| `service_regions` | 6.7MB | 19,491 | 1,223 | region, dead tuple 약 5.9% |
| `service_taxonomies` | 6.0MB | 14,874 | 1,117 | taxonomy, dead tuple 약 7.0% |

해석:

- 정책 수집/정규화 계열 테이블이 DB 대부분을 차지한다.
- 일부 테이블은 dead tuple 비율이 10% 이상이다.
- PostgreSQL은 MVCC 구조이므로 update/delete가 많은 테이블은 autovacuum과 analyze가 중요하다.
- 현재처럼 앱과 DB가 같은 EC2에 있으면 autovacuum, checkpoint, WAL write, index maintenance가 앱 API와 같은 CPU/메모리/디스크를 사용한다.

RDS 전환의 장점은 단순히 “DB가 빨라진다”가 아니다. DB 유지보수 작업이 앱 서버의 런타임과 분리된다는 점이 중요하다. 수집 배치가 돌고 autovacuum이 발생하고 WAL write가 늘어도, 적어도 애플리케이션 서버의 루트 디스크와 Docker daemon에는 직접적인 저장소 압박을 주지 않는다.

### 5.6 인덱스 상태에서 볼 수 있는 특징

상위 인덱스 중 일부는 크기는 있지만 `idx_scan`이 0으로 측정되었다.

예:

| 인덱스 | 크기 | idx_scan |
| --- | ---: | ---: |
| `idx_ws_search_document_fts` | 약 10MB | 0 |
| `idx_raw_hash` | 약 6.9MB | 0 |
| `idx_ws_title_trgm` | 약 5.9MB | 0 |
| `uq_policy_chunk_scope` | 약 5.0MB | 0 |
| `service_facts_pkey` | 약 4.6MB | 0 |

주의:

- `idx_scan = 0`이라고 해서 무조건 불필요한 인덱스라는 뜻은 아니다.
- 통계 reset 이후 해당 측정 구간에서 사용되지 않았을 수 있다.
- unique constraint, primary key, 특정 배치 작업, rare query에서 필요한 인덱스일 수 있다.

다만 이 결과는 DB 운영 관측성이 필요하다는 근거가 된다. RDS 전환 후에는 Performance Insights, CloudWatch, `pg_stat_statements` 등을 통해 실제 쿼리와 인덱스 사용 패턴을 더 안정적으로 수집할 수 있다.

## 6. API 지연시간 측정 결과

### 6.1 단건 health check

```text
GET /actuator/health
http_code=200
time_total=0.012595s
```

해석:

- 앱 자체의 아주 단순한 health 응답은 약 12.6ms다.
- JVM이나 Nginx local proxy 자체가 기본적으로 1초씩 걸리는 상태는 아니다.
- 정책 목록 API의 지연은 단순 앱 생존 확인보다 DB 조회, 직렬화, 서비스 로직, pagination 등 실제 처리 경로의 영향이 크다.

### 6.2 정책 목록 API 순차 요청

측정 대상:

```text
GET /api/policies?page=0&size=20
```

순차 50회 측정 결과:

| count | avg | p50 | p90 | p95 | min | max |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 50 | 101.6ms | 88.7ms | 131.0ms | 174.7ms | 83.1ms | 304.3ms |

해석:

- EC2 내부 loopback 기준으로 순차 요청 p95는 약 175ms다.
- 이 값만 보면 현재 단건 성능은 크게 나쁘지 않다.
- 하지만 이 값은 외부 네트워크, TLS, 브라우저 렌더링, 실제 사용자 환경을 포함하지 않는다.
- 운영 SLA 판단에는 외부 경로 측정이 추가로 필요하다.

### 6.3 정책 목록 API 동시성 10 요청

동시성 10으로 총 50회 호출한 결과:

| count | avg | p50 | p90 | p95 | min | max |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 50 | 818.7ms | 834.9ms | 982.3ms | 1,026.8ms | 422.3ms | 1,125.3ms |

해석:

- 같은 API가 순차 요청에서는 p95 약 175ms였지만, 동시성 10에서는 p95 약 1.03초로 증가했다.
- 평균도 약 102ms에서 약 819ms로 증가했다.
- 단순 health check가 12.6ms인 점을 고려하면, 실제 DB 포함 API 경로에서 동시성 영향이 크게 나타난다.

이 결과를 과장해서 해석하면 안 된다. “DB가 무조건 병목이다”라고 단정할 수는 없다. 원인은 다음 요소가 섞여 있을 수 있다.

- Spring Boot thread 처리
- DB connection pool 대기
- PostgreSQL query 처리
- JSON serialization
- CPU scheduling
- Docker network
- OS page cache
- 동시 curl 실행 방식

그러나 구조 변경 근거로는 충분히 의미가 있다. 앱과 DB가 같은 작은 EC2 안에서 동작할 때, 동시 요청 증가가 응답시간 증가로 빠르게 이어지는 baseline이 관측되었다. RDS 전환 후 같은 방식으로 재측정하면 구조 분리 효과를 비교할 수 있다.

## 7. 현재 구조의 주요 리스크

### 7.1 단일 장애 지점

현재 구조에서는 EC2 하나가 다음 역할을 모두 가진다.

- 외부 요청 수신
- Nginx
- Spring Boot API 서버
- PostgreSQL
- Redis
- DB 저장소
- Docker image/cache 저장소
- 로그 저장소
- 배포 작업 공간

즉 EC2 하나가 장애 나면 앱도 DB도 같이 장애가 난다. RDS로 분리하면 EC2 장애와 DB 장애를 완전히 없앨 수는 없지만, 최소한 애플리케이션 서버 장애와 DB 저장소 장애를 분리할 수 있다.

예를 들어 현재 구조에서는 다음 상황이 모두 전체 서비스 장애로 이어질 수 있다.

- EC2 디스크 full
- Docker daemon 문제
- EC2 재부팅 실패
- 실수로 Docker volume 삭제
- 컨테이너 재생성 과정에서 volume mount 실수
- 배포 중 이미지/캐시가 디스크를 가득 채움
- PostgreSQL container crash
- OS level 장애

RDS로 분리하면 DB 데이터는 EC2의 Docker lifecycle과 분리된다. 앱 컨테이너를 재배포하거나 EC2를 교체해도 DB 데이터는 RDS에 남아 있다.

### 7.2 디스크 full 리스크

현재 루트 디스크 사용률 79%는 운영 관점에서 이미 높은 편이다. 남은 공간 4GB는 다음 이벤트로 빠르게 줄어들 수 있다.

- Docker build cache 증가
- 새 Docker image pull/build
- 컨테이너 로그 증가
- PostgreSQL WAL 증가
- 대량 수집 작업
- 대량 index 생성 또는 rebuild
- 백업 파일 임시 생성
- 장애 상황에서 로그 폭증

PostgreSQL은 디스크 full에 취약하다. WAL을 쓸 수 없으면 transaction commit이 실패할 수 있고, temp file을 만들 수 없으면 query가 실패할 수 있다. 운영 중 디스크 full이 발생하면 단순히 “앱이 느려짐”이 아니라 데이터베이스 write failure로 이어질 수 있다.

RDS 전환은 이 리스크를 줄인다. RDS storage는 EC2 루트 디스크와 별도로 관리되고, CloudWatch metric과 storage autoscaling 설정을 통해 사전에 감지하거나 완화할 수 있다.

### 7.3 앱과 DB의 자원 경쟁

현재 앱 JVM과 PostgreSQL은 같은 EC2 CPU, memory, page cache, disk I/O를 공유한다.

평소에는 문제가 없어 보여도 다음 상황이 겹치면 응답시간이 튈 수 있다.

- 트래픽 증가
- 정책 수집 배치 실행
- 추천 생성 작업 실행
- 검색/임베딩 관련 작업 실행
- admin dashboard 조회
- autovacuum 실행
- checkpoint 발생
- Docker image build/pull
- 로그 증가

특히 PostgreSQL은 메모리와 디스크 I/O 특성이 중요하다. 앱 JVM이 메모리를 많이 쓰면 OS page cache가 줄어 DB read 성능에 영향을 줄 수 있고, DB write가 많아지면 앱 로그나 배포 작업과 I/O를 경쟁할 수 있다.

RDS로 분리하면 앱 서버와 DB 서버가 서로 다른 자원 풀을 사용하게 된다. 앱은 API 처리에 집중하고, DB는 query, WAL, vacuum, cache에 집중할 수 있다.

### 7.4 백업과 복구 체계 부족

현재 PostgreSQL은 EC2 내부 Docker volume에 저장된다. 이 구조에서는 백업과 복구를 직접 설계하고 운영해야 한다.

확인해야 하는 질문은 다음과 같다.

- 정기 `pg_dump`가 설정되어 있는가?
- 백업 파일은 EC2 밖에 저장되는가?
- 백업 파일 암호화는 되어 있는가?
- 복구 리허설을 해본 적이 있는가?
- 특정 시점 복구가 가능한가?
- Docker volume 손상 시 복구 절차가 문서화되어 있는가?
- 운영자가 실수로 volume을 삭제했을 때 되돌릴 수 있는가?

RDS를 사용하면 자동 백업, snapshot, point-in-time recovery, CloudWatch metric, maintenance window, minor version patch 관리 등의 기본 운영 기능을 활용할 수 있다. 이 기능들은 직접 EC2에서 PostgreSQL을 운영할 때도 만들 수 있지만, 만들고 검증하고 유지하는 비용이 든다.

현재 서비스 규모에서 RDS 전환의 가장 큰 이유는 “쿼리 하나가 몇 ms 빨라진다”보다 “백업과 복구의 책임을 관리형 DB로 넘기고 장애 격리를 확보한다”에 가깝다.

### 7.5 운영 관측성 부족

현재도 `pg_stat_*` view를 직접 조회하면 많은 정보를 얻을 수 있다. 하지만 운영적으로 지속 관측하려면 다음이 필요하다.

- CPU, memory, disk I/O 추세
- DB connection 추세
- slow query
- lock wait
- deadlock
- checkpoint 빈도
- WAL 증가량
- storage 사용량
- read/write IOPS
- query별 latency
- vacuum/autovacuum 상태

EC2 내부 컨테이너 PostgreSQL에서도 Prometheus, Grafana, postgres_exporter, log collector 등을 붙이면 가능하다. 하지만 별도 구축/운영 부담이 있다.

RDS는 CloudWatch, Performance Insights, Enhanced Monitoring 등을 통해 DB 관측성을 더 쉽게 확보할 수 있다. 특히 구조 전환 후에는 “앱이 느린가, DB가 느린가, 네트워크가 느린가”를 분리해서 볼 수 있다.

### 7.6 스케일 아웃 제약

현재 구조에서는 앱과 DB가 같은 EC2에 묶여 있다. 앱 트래픽이 늘어나서 앱 인스턴스를 늘리고 싶어도 DB가 같은 컨테이너에 붙어 있으면 구조가 복잡해진다.

예를 들어 EC2를 하나 더 추가한다고 가정하면 다음 문제가 생긴다.

- 새 앱 인스턴스가 어느 DB에 붙을 것인가?
- DB가 기존 EC2 내부 Docker network에만 있으면 외부 앱 인스턴스 접근을 어떻게 열 것인가?
- 보안 그룹과 DB 인증은 어떻게 할 것인가?
- DB 백업과 failover는 어떻게 할 것인가?
- Redis도 같이 분리할 것인가?
- 배포 중 어떤 인스턴스가 DB migration을 실행할 것인가?

RDS로 분리하면 앱 인스턴스는 stateless에 더 가까워진다. 이후 EC2를 여러 대로 늘리거나, ALB를 붙이거나, 컨테이너 오케스트레이션으로 옮길 때 구조가 단순해진다.

## 8. 왜 지금 전환을 검토해야 하는가

현재 DB 크기는 423MB로 작다. 그래서 “나중에 커지면 옮기자”고 판단할 수도 있다. 하지만 마이그레이션은 DB가 작을 때가 더 쉽다.

지금 전환을 검토해야 하는 이유:

- DB 크기가 작아 `pg_dump`/restore 시간이 짧다.
- cutover 리허설을 하기 쉽다.
- rollback 계획을 세우기 쉽다.
- 데이터 정합성 확인 범위가 상대적으로 작다.
- 운영 트래픽이 더 커지기 전에 connection, security group, parameter group을 정리할 수 있다.
- 서비스가 더 복잡해지기 전에 앱과 DB의 책임 경계를 분리할 수 있다.

DB가 수십 GB, 수백 GB가 된 뒤에 옮기면 downtime, replication, migration window, 검증 비용이 훨씬 커진다. 현재 규모는 RDS 전환 리허설과 안정화에 적합한 시점이다.

## 9. 반론과 답변

### 반론 1. 지금 CPU가 낮은데 왜 RDS로 옮기는가?

답변:

CPU 포화가 유일한 구조 변경 이유는 아니다. 현재 측정에서 CPU가 항상 높은 상태는 아니었다. 따라서 “CPU가 부족해서 RDS로 간다”는 주장은 정확하지 않다.

정확한 이유는 다음이다.

- 앱과 DB의 장애 지점 분리
- EC2 루트 디스크와 DB storage 분리
- 백업/복구 체계 강화
- DB maintenance workload 격리
- 앱 scale-out 준비
- 운영 관측성 개선
- 동시성 증가 시 응답 지연 완화 가능성 확보

### 반론 2. DB가 423MB밖에 안 되는데 RDS는 과하지 않은가?

답변:

DB 크기만 보면 아직 작다. 하지만 운영 DB의 위험은 크기만으로 결정되지 않는다. 현재는 DB가 Docker volume에 있고, EC2 루트 디스크 사용률이 79%다. Docker build cache만 약 4.98GB다. 앱 배포와 DB 저장소가 같은 디스크를 쓰는 구조 자체가 위험하다.

또한 DB가 작을 때 옮기는 편이 안전하다. DB가 커진 뒤에는 migration 시간이 길어지고 rollback이 어려워진다.

### 반론 3. RDS로 옮기면 네트워크 hop이 생겨 더 느려질 수도 있지 않은가?

답변:

맞다. RDS는 같은 프로세스/같은 Docker network 안의 DB보다 네트워크 hop이 추가된다. 따라서 단건 latency가 무조건 줄어든다고 보장할 수 없다.

하지만 구조 변경의 목표는 단건 local latency 최적화만이 아니다. 목표는 다음이다.

- DB 자원 격리
- 저장소 안정성
- 자동 백업과 복구
- 장애 격리
- DB 전용 모니터링
- 앱 scale-out 기반 확보

RDS 전환 후에는 같은 API 부하 테스트를 다시 수행하여 단건 latency와 동시성 latency를 비교해야 한다. 만약 네트워크 지연이 커진다면 connection pool, query tuning, RDS instance class, same-AZ 배치, parameter group, RDS Proxy 여부를 검토한다.

### 반론 4. EC2 안 PostgreSQL을 잘 관리하면 되지 않는가?

답변:

가능하다. EC2 안 PostgreSQL도 별도 EBS volume, 정기 백업, WAL archive, monitoring, postgres_exporter, failover, 복구 리허설을 잘 구성하면 운영할 수 있다.

하지만 이 서비스의 현재 구조는 그런 관리형 자체 운영 구조가 아니다. Docker volume 기반이고, 앱과 DB가 같은 작은 EC2 안에서 동작한다. RDS는 이 운영 부담 중 상당 부분을 관리형 서비스로 넘겨준다.

직접 운영을 선택하려면 최소한 다음을 구현해야 한다.

- DB 전용 EBS 분리
- 자동 snapshot
- 외부 저장소 백업
- PITR 유사 체계
- 장애 복구 리허설
- 모니터링/알림
- PostgreSQL patch 계획
- disk full 방지 정책

이를 직접 만들고 유지하는 비용과 RDS 비용을 비교해야 한다.

## 10. RDS 전환의 기대 효과

### 10.1 저장소 안정성

RDS는 DB storage를 EC2 루트 디스크에서 분리한다. EC2의 Docker image, build cache, 로그가 증가해도 RDS storage를 직접 압박하지 않는다.

### 10.2 백업/복구 개선

RDS 자동 백업, snapshot, point-in-time recovery를 사용할 수 있다. Docker volume 기반 수동 백업보다 운영 안정성이 높다.

### 10.3 장애 격리

EC2 앱 서버가 장애 나도 DB 데이터는 RDS에 남는다. 반대로 DB 이슈가 발생해도 앱 서버 디스크와 Docker daemon 문제와 분리해서 볼 수 있다.

### 10.4 관측성 개선

CloudWatch, Performance Insights, Enhanced Monitoring 등을 통해 DB 지표를 분리해서 볼 수 있다. 앱 문제와 DB 문제를 구분하기 쉬워진다.

### 10.5 확장성 개선

앱 인스턴스를 추가하거나 ALB를 붙이는 구조로 갈 때 DB endpoint가 외부화되어 있어 구조가 단순해진다.

### 10.6 운영 책임 경계 명확화

현재는 앱 배포와 DB 운영이 같은 서버 안에 섞여 있다. RDS 전환 후에는 앱 서버 운영과 DB 운영의 책임 경계가 명확해진다.

## 11. RDS 전환 시 주의할 점

RDS 전환은 장점만 있는 변경이 아니다. 반드시 다음을 검토해야 한다.

### 11.1 네트워크 latency

현재 앱과 DB는 같은 Docker network 안에 있다. RDS로 옮기면 네트워크 hop이 생긴다. 같은 VPC, 가능하면 같은 AZ 배치, 보안 그룹 최소 개방, DNS endpoint 안정성을 고려해야 한다.

### 11.2 connection pool 설정

현재 앱은 datasource 3개로 기본 idle connection 30개를 유지한다. RDS 전환 후에도 그대로면 RDS connection을 불필요하게 많이 점유할 수 있다.

검토 항목:

- datasource별 Hikari `maximum-pool-size`
- datasource별 `minimum-idle`
- connection timeout
- idle timeout
- max lifetime
- 앱 인스턴스 수 증가 시 총 connection 계산
- RDS `max_connections`
- 필요 시 RDS Proxy

### 11.3 pgvector 지원

현재 DB image는 `pgvector/pgvector:pg16`이다. RDS PostgreSQL에서 사용하는 버전과 `pgvector` extension 지원 여부를 반드시 확인해야 한다.

확인 항목:

- RDS PostgreSQL version
- `CREATE EXTENSION vector` 가능 여부
- 현재 schema에서 사용하는 vector type, index type
- extension version 차이
- restore 후 query 동작 확인

### 11.4 보안 그룹과 접근 제어

현재 DB는 `127.0.0.1:5433`으로만 노출되어 있다. RDS로 옮기면 VPC 내부 접근이 필요하다.

원칙:

- RDS public access는 비활성화
- EC2 security group에서만 RDS inbound 허용
- DB port 5432 최소 개방
- admin 접속은 bastion/SSM/터널 등 별도 절차 사용
- 운영 secret은 `.env` 직접 관리보다 AWS Secrets Manager 또는 Parameter Store 검토

### 11.5 마이그레이션 downtime

현재 DB가 작기 때문에 `pg_dump`/restore 방식이 가능할 가능성이 높다. 그래도 cutover 동안 쓰기 요청이 발생하면 데이터 차이가 생길 수 있다.

검토 항목:

- maintenance window 설정
- 앱 write 중지 또는 점검 모드
- `pg_dump` 시점 이후 변경 데이터 처리
- restore 검증
- DNS/env 변경
- 앱 재기동
- rollback 기준

### 11.6 비용

RDS는 EC2 내부 컨테이너 DB보다 비용이 증가할 수 있다.

비용 판단 시 고려할 항목:

- RDS instance class
- storage size
- storage autoscaling
- backup retention
- Multi-AZ 여부
- Performance Insights retention
- data transfer
- snapshot 보관

비용이 증가하더라도 백업/복구, 장애 격리, 운영 시간 절감이 더 중요하다면 정당화할 수 있다. 반대로 비용이 최우선이면 EC2 내부 DB를 유지하되 별도 EBS, 백업, 모니터링을 강화하는 대안도 비교해야 한다.

## 12. 전환 전 권장 체크리스트

### 12.1 현재 DB inventory 확인

- DB size
- schema list
- extension list
- table count
- index count
- role/user list
- privilege
- sequence 상태
- view/function/trigger 존재 여부
- PII schema 존재 여부
- pgvector 사용 위치

### 12.2 백업 리허설

- 운영 DB에서 `pg_dump` 수행
- 별도 임시 PostgreSQL 또는 RDS staging에 restore
- restore 소요 시간 기록
- row count 비교
- 주요 API smoke test
- schema validation
- extension validation

### 12.3 성능 baseline 재측정

현재 기록된 baseline:

| 항목 | 값 |
| --- | ---: |
| `/actuator/health` 단건 | 12.6ms |
| `/api/policies?page=0&size=20` 순차 p95 | 174.7ms |
| `/api/policies?page=0&size=20` 동시성 10 p95 | 1,026.8ms |

RDS staging 또는 production cutover 후 동일 조건으로 다시 측정한다.

추가 측정 권장 API:

- 정책 목록
- 정책 검색
- 정책 상세
- 추천 조회
- 로그인
- 마이페이지
- admin dashboard summary
- 수집/정규화 배치 일부

### 12.4 앱 설정 변경 준비

변경 대상:

- `DB_URL`
- `APP_PII_DB_URL`
- `NOTIFICATION_PII_DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`
- datasource별 username/password
- SSL mode
- Hikari pool 설정
- RDS endpoint

RDS 전환 시 `sslmode`를 어떻게 둘지 결정해야 한다. 현재는 `sslmode=disable`이다. RDS 운영에서는 SSL 사용 여부를 검토해야 한다.

### 12.5 운영 알림 설정

RDS 전환 후 최소 알림:

- CPUUtilization
- FreeableMemory
- FreeStorageSpace
- DatabaseConnections
- ReadIOPS / WriteIOPS
- ReadLatency / WriteLatency
- DiskQueueDepth
- Deadlocks
- ReplicaLag, 읽기 복제본 사용 시
- BurstBalance, gp2 사용 시

### 12.6 rollback 계획

전환 실패 시 되돌리는 절차를 미리 정해야 한다.

예:

1. 앱 점검 모드 진입
2. 기존 EC2 PostgreSQL container 유지
3. RDS restore 실패 또는 smoke test 실패 시 `.env`를 기존 DB URL로 되돌림
4. 앱 재기동
5. 주요 API 확인
6. 실패 원인 기록

주의:

- RDS로 전환한 뒤 쓰기 트래픽이 발생한 상태에서 기존 DB로 rollback하면 데이터 divergence가 생긴다.
- 따라서 rollback 가능 시간을 짧게 잡거나, cutover 중 쓰기를 막아야 한다.

## 13. 전환 후 검증 항목

전환이 끝난 뒤에는 단순히 앱이 켜졌는지만 보면 안 된다. 다음을 확인해야 한다.

### 13.1 기능 검증

- 회원가입
- 로그인
- refresh token
- 로그아웃
- 정책 목록 조회
- 정책 검색
- 정책 상세 조회
- 추천 조회
- 북마크
- 알림 조회
- admin dashboard
- 수집 관련 admin API
- PII 조회/수정/동기화

### 13.2 데이터 검증

- 주요 테이블 row count 비교
- sequence current value 확인
- schema별 권한 확인
- PII schema 접근 확인
- read-only role 확인
- cleanup role 확인
- extension 확인
- vector column/index 확인

### 13.3 성능 검증

- 동일 API latency 재측정
- 동시성 10, 30, 50 단계별 측정
- DB connection 수 확인
- slow query 확인
- RDS CPU/memory/storage 확인
- 앱 thread/connection pool 대기 확인

### 13.4 운영 검증

- RDS 자동 백업 활성화 확인
- snapshot 생성 테스트
- CloudWatch metric 확인
- alarm 설정 확인
- maintenance window 확인
- parameter group 적용 확인
- security group 최소 권한 확인

## 14. 현재 측정값 기반 결론

현재 수치만 놓고 보면 CPU가 항상 포화 상태인 것은 아니다. DB cache hit ratio도 97.55%로 나쁘지 않고, deadlock도 관측되지 않았다. 따라서 “현재 DB 성능이 이미 한계라서 무조건 RDS로 옮겨야 한다”는 표현은 부정확하다.

그러나 다음 사실은 명확하다.

- 애플리케이션, PostgreSQL, Redis가 같은 EC2에서 실행 중이다.
- 현재 EC2는 `t3.medium`이며, OS 기준 2 vCPU와 약 3.7GiB 메모리를 앱/DB/Redis/Docker가 공유한다.
- DB는 EC2 내부 Docker volume에 저장된다.
- EC2 루트 디스크는 19GB 중 15GB를 사용하고 있으며 사용률은 79%다.
- Docker build cache만 약 4.98GB다.
- PostgreSQL volume은 약 621MB, DB size는 423MB다.
- 과거 MySQL volume도 약 751MB 남아 있다.
- DB 컨테이너는 누적 read 1.91GB / write 8.56GB의 Block I/O를 발생시켰다.
- 앱은 기본적으로 PostgreSQL connection 30개를 idle 상태로 유지한다.
- 정책 목록 API는 순차 p95 약 175ms지만, 동시성 10에서는 p95 약 1.03초까지 증가했다.
- 현재 구조에서는 디스크 full, Docker volume 문제, EC2 장애가 앱 장애와 DB 장애를 동시에 일으킬 수 있다.

따라서 EC2 + RDS 전환의 핵심 근거는 다음과 같이 정리할 수 있다.

```text
현재 단일 EC2 구조는 앱과 DB가 같은 CPU, 메모리, 디스크, Docker runtime을 공유한다.
DB 자체 크기는 아직 작지만 루트 디스크 사용률은 79%이고,
Docker build cache와 DB volume, 로그, 배포 산출물이 같은 저장소를 사용한다.
또한 동시성 10의 주요 조회 API에서 p95가 약 1초까지 증가하는 baseline이 관측되었다.

따라서 RDS 전환은 단순한 성능 개선 목적만이 아니라,
DB 저장소 격리, 백업/복구 체계 확보, 장애 영향 범위 축소,
DB maintenance workload 분리, 앱 scale-out 기반 마련,
운영 관측성 개선을 위한 구조 개선으로 정당화된다.
```

## 15. 최종 판단

RDS 전환은 “지금 당장 서버가 버티지 못해서 하는 긴급 조치”라기보다는 “서비스를 계속 운영하고 확장하기 위해 단일 EC2 구조의 위험을 줄이는 선제적 구조 개선”으로 보는 것이 맞다.

현재 상태에서 가장 설득력 있는 변경 사유는 다음 순서다.

1. DB 데이터를 EC2 Docker volume에서 분리하여 저장소 안정성을 높인다.
2. EC2 디스크 full이 DB 장애로 이어지는 위험을 낮춘다.
3. 자동 백업, snapshot, point-in-time recovery 기반을 확보한다.
4. 앱과 DB의 CPU/메모리/I/O 경쟁을 줄인다.
5. DB metric과 query 성능을 별도로 관측할 수 있게 한다.
6. 향후 앱 인스턴스 scale-out 구조를 준비한다.
7. 현재 작은 DB 크기에서 마이그레이션을 수행해 전환 리스크를 낮춘다.

즉, RDS 전환의 핵심 메시지는 다음과 같다.

```text
현재 구조는 작게 시작하기에는 단순하고 빠르지만,
운영 서비스로 계속 가져가기에는 앱과 DB의 장애 지점이 과도하게 결합되어 있다.
측정 결과 디스크 여유가 크지 않고, DB I/O와 앱 런타임이 같은 EC2 자원을 공유하며,
동시 요청에서 주요 API 지연시간 증가도 관측되었다.

따라서 EC2 + RDS 구조로 전환하여 DB를 관리형 저장소와 백업 체계 안으로 분리하고,
애플리케이션 서버는 stateless에 가깝게 운영할 수 있는 기반을 만드는 것이 타당하다.
```

## 16. RDS 전환 후 EC2를 `t3.small`로 낮추는 경우와 `t3.medium`을 유지하는 경우 비교

이 섹션은 PostgreSQL을 RDS로 분리한 뒤, 애플리케이션이 올라가는 EC2를 현재 `t3.medium`으로 유지할지, `t3.small`로 낮출지 비교하기 위한 것이다.

### 16.1 공식 스펙 비교

AWS T3 공식 스펙 기준으로 `t3.small`과 `t3.medium`의 차이는 다음과 같다.

| 항목 | `t3.small` | `t3.medium` | 차이 |
| --- | ---: | ---: | --- |
| vCPU | 2 | 2 | 동일 |
| Memory | 2GiB | 4GiB | `t3.medium`이 2배 |
| Baseline CPU performance / vCPU | 20% | 20% | 동일 |
| CPU credits earned / hour | 24 | 24 | 동일 |
| Network burst bandwidth | 최대 5Gbps | 최대 5Gbps | 동일 |
| EBS burst bandwidth | 최대 2,085Mbps | 최대 2,085Mbps | 동일 |
| On-Demand 가격 | `t3.medium`의 약 1/2 | `t3.small`의 약 2배 | `t3.small`이 저렴 |

중요한 점은 `t3.small`과 `t3.medium`은 CPU 관점에서는 거의 같은 등급이라는 점이다. 둘 다 2 vCPU이고, baseline CPU performance도 vCPU당 20%로 같다. CPU credit 획득량도 시간당 24 credit으로 같다.

따라서 `small`과 `medium` 선택의 핵심은 CPU가 아니라 메모리다.

```text
t3.small  = 2 vCPU / 2GiB memory
t3.medium = 2 vCPU / 4GiB memory
```

현재 운영 서버에서 OS가 인식한 메모리는 약 3.7GiB다. `t3.small`로 낮추면 OS에서 사용할 수 있는 메모리는 대략 1.8-1.9GiB 수준으로 줄어들 가능성이 높다. 즉 메모리 여유가 현재의 절반 이하가 된다.

### 16.2 현재 측정값 기준 메모리 관점 비교

현재 `t3.medium` 단일 EC2에서 관측된 메모리 상태는 다음과 같다.

```text
total:      3.7GiB
used:       1.8GiB
free:       422MiB
buff/cache: 2.0GiB
available: 1.9GiB
swap:       0B
```

현재 컨테이너 메모리 사용량은 다음과 같다.

| 컨테이너 | 현재 메모리 사용량 |
| --- | ---: |
| `youth-welfare-app` | 약 704-707MiB |
| `youth-welfare-db` | 약 221-275MiB |
| `youth-welfare-redis` | 약 7.7MiB |

RDS 전환 후에는 EC2 안의 PostgreSQL 컨테이너가 사라지므로 단순히 컨테이너 기준으로는 약 220-275MiB 정도가 줄어든다. 또한 PostgreSQL 관련 page cache, WAL write, DB data file I/O도 EC2에서 빠진다. 이 점은 `t3.small` 전환에 유리한 요소다.

그러나 `t3.small`은 전체 메모리가 약 2GiB 계열이다. 현재 앱 컨테이너만 약 700MiB를 사용하고 있고, 여기에 아래 항목들이 추가로 필요하다.

- OS 기본 메모리
- Nginx
- Docker daemon/containerd
- Redis
- Spring Boot JVM의 추가 heap 증가 여지
- JVM metaspace, thread stack, native memory
- TLS/proxy 처리
- 로그 처리
- 배포/재시작 중 일시적 메모리
- OS page cache
- 운영 접속/관리 프로세스

따라서 PostgreSQL을 RDS로 빼더라도 `t3.small`은 메모리 여유가 크지 않다. 서비스가 단순 조회 위주이고, 동시 접속이 낮고, 배포 중 빌드를 EC2에서 직접 하지 않고, JVM heap을 제한한다면 가능할 수 있다. 반대로 추천, 검색, 수집, 알림, admin dashboard, 배포 작업이 같은 EC2에서 계속 돈다면 `t3.small`은 여유가 부족할 수 있다.

### 16.3 CPU 관점 비교

CPU 관점에서는 `t3.small`과 `t3.medium`이 거의 동일하다.

- 둘 다 2 vCPU다.
- 둘 다 baseline CPU performance가 vCPU당 20%다.
- 둘 다 CPU credit earned/hour가 24다.
- 둘 다 burstable 타입이다.

즉 RDS 전환 후 앱 서버 CPU 성능만 놓고 보면 `t3.small`로 낮춰도 `t3.medium` 대비 큰 차이가 없을 가능성이 있다.

하지만 T3 계열은 burstable 인스턴스다. CPU credit이 충분할 때는 순간적으로 높은 CPU를 사용할 수 있지만, 지속적으로 높은 CPU를 사용하면 credit 정책에 영향을 받는다. 현재 측정에서는 CPU가 지속 포화 상태는 아니었으므로, CPU만 보면 `t3.small`도 가능성이 있다.

다만 실제 운영에서는 다음 작업이 CPU를 순간적으로 사용할 수 있다.

- Spring Boot 요청 처리
- JSON serialization/deserialization
- JWT 검증
- 암호화/복호화
- 추천 로직
- 검색 결과 가공
- 알림 처리
- 배치성 admin 작업
- 배포 후 warm-up

CPU는 두 타입이 같기 때문에, 이 영역은 `small`과 `medium`의 결정 기준이 아니라 “T3 계열 자체가 적절한가”의 문제다.

### 16.4 디스크와 I/O 관점 비교

RDS 전환 후에는 PostgreSQL data file, WAL, autovacuum, checkpoint I/O가 EC2에서 빠진다. 이 점은 `t3.small` 전환에 유리하다.

현재 DB 컨테이너 누적 Block I/O는 다음과 같았다.

```text
youth-welfare-db Block I/O: read 1.91GB / write 8.56GB
```

RDS로 분리하면 이 DB I/O는 EC2 루트 디스크가 아니라 RDS storage에서 처리된다. 따라서 EC2는 다음 역할에 집중하게 된다.

- Nginx
- Spring Boot API
- Redis, 유지한다면
- 로그
- Docker image/container
- 정적 파일 제공

이 관점에서는 `t3.small`도 현재보다 안정적일 수 있다. 다만 `t3.small`로 낮추더라도 루트 디스크 크기가 그대로 작고 Docker build cache를 EC2에서 계속 쌓는다면 disk full 리스크는 여전히 남는다. RDS 전환은 DB 저장소를 분리하는 것이지, EC2 Docker storage 관리 문제를 자동으로 없애지는 않는다.

따라서 `t3.small` 전환을 고려한다면 다음도 같이 해야 한다.

- EC2에서 직접 Docker build를 오래 누적하지 않기
- 불필요한 Docker build cache 정리 정책 수립
- 과거 MySQL volume 제거 여부 검토
- 컨테이너 로그 rotation 설정
- 루트 디스크 사용률 알림 설정

### 16.5 네트워크 관점 비교

RDS 전환 후 앱과 DB 사이에는 네트워크 hop이 생긴다. `t3.small`과 `t3.medium` 모두 T3 공식 스펙상 network burst bandwidth는 최대 5Gbps로 같다. 따라서 네트워크 burst 스펙만 보면 둘 사이에 큰 차이는 없다.

다만 실제 성능은 다음에 영향을 받는다.

- EC2와 RDS가 같은 AZ인지
- 보안 그룹 경로
- RDS instance class
- RDS storage latency
- connection pool 설정
- query 수와 payload 크기
- TLS 사용 여부

즉 네트워크 관점에서도 `small`과 `medium`의 차이는 크지 않고, 더 중요한 것은 RDS 배치와 DB 튜닝이다.

### 16.6 운영 안정성 관점 비교

#### `t3.small`로 낮추는 경우

장점:

- EC2 비용이 줄어든다.
- CPU 스펙은 `t3.medium`과 동일하므로 낮은 트래픽에서는 체감 성능 차이가 작을 수 있다.
- RDS로 DB를 분리한 뒤라면 현재보다 EC2 I/O 부담이 줄어든다.
- 앱 서버를 가볍게 유지하고 DB는 RDS가 담당하는 구조로 비용 최적화가 가능하다.

단점:

- 메모리가 4GiB 계열에서 2GiB 계열로 줄어든다.
- Spring Boot 앱이 이미 약 700MiB를 사용하므로 여유가 크지 않다.
- 배포, warm-up, admin 작업, 트래픽 증가, JVM heap 증가 시 OOM 리스크가 커진다.
- OS page cache 여유가 줄어 정적 파일 제공, 로그 처리, Docker 작업에서 여유가 감소한다.
- swap이 없으면 메모리 부족 시 프로세스 종료로 바로 이어질 수 있다.
- 장애 분석이나 긴급 운영 작업을 같은 서버에서 수행할 여유가 줄어든다.

적합한 조건:

- 운영 트래픽이 낮다.
- EC2에서는 앱만 실행하고 DB는 RDS, 가능하면 Redis도 별도 관리형으로 분리한다.
- EC2 안에서 Docker build를 하지 않거나 매우 제한한다.
- JVM heap maximum을 명시적으로 제한한다.
- CloudWatch memory/disk 알림을 설정한다.
- 동시성 테스트에서 p95가 목표 안에 들어온다.
- 비용 절감이 운영 여유보다 더 중요하다.

#### `t3.medium`을 유지하는 경우

장점:

- 현재 검증된 인스턴스 크기를 유지하므로 변경 리스크가 작다.
- RDS 전환으로 DB 부담이 빠진 만큼 앱 서버 메모리/디스크 여유가 늘어난다.
- Spring Boot JVM, Nginx, Redis, Docker daemon, OS page cache를 위한 여유가 더 크다.
- 배포 중 일시적 메모리 증가나 트래픽 spike를 더 잘 흡수한다.
- 장애 분석, 로그 확인, 운영 작업을 수행할 여유가 있다.
- RDS 전환 후 성능 baseline을 안정적으로 측정하기 좋다.

단점:

- `t3.small` 대비 EC2 비용이 약 2배다.
- DB가 RDS로 빠진 뒤에는 일부 시간대에 리소스가 남을 수 있다.
- 비용 최적화 관점에서는 과할 수 있다.

적합한 조건:

- RDS 전환 직후 안정성이 중요하다.
- 아직 실제 RDS 전환 후 메모리/latency 데이터가 없다.
- 추천/검색/수집/admin 작업이 계속 같은 앱 서버에서 돈다.
- 서비스 장애 비용이 EC2 비용 절감보다 크다.
- 향후 트래픽 증가 가능성이 있다.
- 운영자가 자주 서버에서 점검 작업을 수행한다.

### 16.7 예상 성능 비교

RDS 전환 후 예상 비교는 다음과 같다.

| 항목 | `t3.small` 전환 | `t3.medium` 유지 |
| --- | --- | --- |
| CPU 성능 | 거의 동일 | 거의 동일 |
| 메모리 여유 | 낮음 | 높음 |
| OOM 리스크 | 상대적으로 높음 | 상대적으로 낮음 |
| 동시 요청 처리 안정성 | 낮은 트래픽에서는 가능, spike에 약함 | 더 안정적 |
| 배포/warm-up 안정성 | 주의 필요 | 더 안정적 |
| 운영 작업 여유 | 낮음 | 높음 |
| DB I/O 영향 | RDS 분리로 크게 감소 | RDS 분리로 크게 감소 |
| 비용 | 낮음 | 높음 |
| 권장 용도 | 비용 최적화, 낮은 트래픽 | 안정 운영, 전환 직후 baseline 확보 |

성능 자체만 보면 CPU는 동일하므로 `t3.small`도 가능성이 있다. 하지만 운영 안정성까지 포함하면 `t3.medium` 유지가 더 안전하다. 특히 현재 Spring Boot 앱이 약 700MiB를 사용하고 있고, 운영 서버에는 Docker daemon, Nginx, Redis, OS cache도 필요하므로 2GiB 메모리는 빠듯하다.

### 16.8 권장 판단

권장 순서는 다음과 같다.

1. RDS 전환 직후에는 `t3.medium`을 유지한다.
2. RDS 전환 후 최소 며칠에서 1-2주 동안 실제 메트릭을 수집한다.
3. 메모리, CPU credit, API p95, error rate, OOM 여부, disk 사용률을 확인한다.
4. 충분히 여유가 있으면 `t3.small`로 낮추는 실험을 짧은 maintenance window에 진행한다.
5. `t3.small` 전환 후 p95와 메모리 여유가 기준을 넘지 않으면 유지하고, 아니면 즉시 `t3.medium`으로 되돌린다.

즉, RDS 전환과 EC2 downsizing을 동시에 하는 것은 권장하지 않는다. 두 변경을 동시에 하면 문제가 생겼을 때 원인을 구분하기 어렵다.

```text
권장: 1단계 RDS 전환 -> t3.medium 유지 -> 안정화/측정 -> 2단계 t3.small 실험
비권장: RDS 전환과 동시에 t3.small 변경
```

### 16.9 `t3.small`로 낮추기 전 통과 기준

`t3.small`로 낮추기 전에 최소한 다음 기준을 만족하는지 확인하는 것이 좋다.

| 기준 | 권장값 |
| --- | --- |
| EC2 memory available | 평상시 500MiB 이상 |
| swap 사용 | 없음, 또는 swap 구성 시 지속 swap-in/out 없음 |
| app container memory | 안정적으로 1GiB 이하 |
| CPUUtilization | 평상시 낮고 spike 후 회복 |
| CPUCreditBalance | 지속 감소하지 않음 |
| API p95 | RDS 전환 전 baseline보다 악화되지 않음 |
| OOM/restart | 발생 없음 |
| root disk 사용률 | 70% 이하 권장 |
| Docker build cache | 운영 서버에서 누적 관리 |

현재 기준으로는 `t3.small`을 바로 확정하기보다, RDS 전환 후 DB 컨테이너 제거 상태에서 실제 앱 메모리 사용량을 다시 측정해야 한다.

### 16.10 최종 결론

RDS 전환 후 EC2를 `t3.small`로 낮추는 것은 비용 절감 관점에서는 가능성이 있다. `t3.small`과 `t3.medium`은 vCPU, CPU baseline, CPU credit, network burst, EBS burst 스펙이 같고 메모리만 2GiB 대 4GiB로 다르기 때문이다.

하지만 현재 서비스는 Spring Boot 앱만으로 약 700MiB를 사용하고 있으며, 같은 서버에서 Nginx, Redis, Docker daemon, OS page cache가 필요하다. RDS로 PostgreSQL을 빼면 메모리와 I/O 부담은 줄지만, `t3.small`의 2GiB 메모리는 운영 여유가 크지 않다.

따라서 운영 관점의 결론은 다음과 같다.

```text
RDS 전환 직후에는 t3.medium을 유지하는 것이 안전하다.
RDS 전환으로 DB 부담이 빠진 상태에서 실제 메모리, CPU credit, API p95를 며칠 이상 측정한 뒤
비용 최적화 단계로 t3.small을 실험하는 것이 좋다.

즉, t3.small은 "가능성 있는 비용 절감안"이고,
t3.medium 유지는 "전환 직후 안정 운영안"이다.
```

## 17. RDS 전환 시 코드 수정 필요 여부

결론부터 말하면, 현재 코드 구조 기준으로는 RDS 전환을 위해 Java 비즈니스 로직을 크게 수정할 필요는 없다. 이 프로젝트는 이미 PostgreSQL JDBC URL, username, password를 환경변수로 받도록 구성되어 있다. 따라서 RDS 전환의 핵심은 코드 수정이 아니라 운영 설정, DB 초기화, 권한, 네트워크, 보안 그룹, connection pool 설정 변경이다.

다만 “코드 수정이 전혀 없다”라고 단정하면 위험하다. 애플리케이션 코드 로직은 그대로 갈 수 있지만, 배포 설정과 DB 준비 절차는 반드시 바뀐다.

### 17.1 현재 코드가 RDS 전환에 유리한 부분

현재 `application.yml`과 `application-prod.yml`은 DB 접속 정보를 하드코딩하지 않고 환경변수로 받는다.

주요 설정:

```yaml
spring:
  datasource:
    url: ${DB_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
```

PII용 datasource도 환경변수로 분리되어 있다.

```yaml
app:
  datasource:
    pii-rw:
      url: ${APP_PII_DB_URL}
      username: ${DB_APP_PII_USERNAME}
      password: ${DB_APP_PII_PASSWORD}
    notification-pii-ro:
      url: ${NOTIFICATION_PII_DB_URL}
      username: ${DB_NOTIFICATION_PII_RO_USERNAME}
      password: ${DB_NOTIFICATION_PII_RO_PASSWORD}
```

즉 RDS endpoint가 생기면 애플리케이션 코드에서 repository, service, controller를 고치는 것이 아니라 아래 환경변수를 RDS endpoint로 바꾸면 된다.

```bash
DB_URL=jdbc:postgresql://<rds-endpoint>:5432/youth_welfare?sslmode=require
APP_PII_DB_URL=jdbc:postgresql://<rds-endpoint>:5432/youth_welfare?sslmode=require&currentSchema=youth_welfare_pii
NOTIFICATION_PII_DB_URL=jdbc:postgresql://<rds-endpoint>:5432/youth_welfare?sslmode=require&currentSchema=youth_welfare_pii
DB_USERNAME=app_core_rw
DB_PASSWORD=<password>
DB_APP_PII_USERNAME=app_pii_rw
DB_APP_PII_PASSWORD=<password>
DB_NOTIFICATION_PII_RO_USERNAME=notification_pii_ro
DB_NOTIFICATION_PII_RO_PASSWORD=<password>
```

현재 코드에는 PostgreSQL URL 형식을 검증하는 guard도 있다. 특히 PII datasource는 `currentSchema`가 반드시 있어야 한다.

```text
app.datasource.pii-rw.url
app.datasource.notification-pii-ro.url
```

따라서 RDS로 갈 때도 PII URL에서 `currentSchema=youth_welfare_pii`를 빼면 안 된다.

### 17.2 Java 비즈니스 로직 수정은 원칙적으로 불필요

다음 계층은 RDS 전환 때문에 직접 수정할 가능성이 낮다.

- Controller
- Service
- Repository query 로직
- DTO
- Entity
- 인증/인가 로직
- 추천/정책/알림 기능 로직

이유:

- 현재도 PostgreSQL을 사용 중이다.
- RDS도 PostgreSQL이다.
- JDBC driver도 그대로 `org.postgresql.Driver`를 사용한다.
- Hibernate 설정은 `ddl-auto: validate`이므로 앱이 운영 DB schema를 임의 생성하지 않는다.
- query는 PostgreSQL 기준으로 이미 동작하고 있다.

즉 RDS 전환은 MySQL에서 PostgreSQL로 바꾸는 종류의 DB migration이 아니다. 이미 PostgreSQL에서 PostgreSQL로 endpoint와 운영 방식이 바뀌는 전환이다.

### 17.3 반드시 바뀌어야 하는 배포 설정

현재 `docker-compose.yml`은 앱이 Docker 내부 DB 컨테이너를 바라보도록 되어 있다.

```yaml
DB_URL: jdbc:postgresql://db:5432/youth_welfare?sslmode=disable
APP_PII_DB_URL: jdbc:postgresql://db:5432/youth_welfare?sslmode=disable&currentSchema=youth_welfare_pii
NOTIFICATION_PII_DB_URL: jdbc:postgresql://db:5432/youth_welfare?sslmode=disable&currentSchema=youth_welfare_pii
```

RDS 전환 시에는 이 값이 RDS endpoint로 바뀌어야 한다.

예:

```yaml
DB_URL: jdbc:postgresql://<rds-endpoint>:5432/youth_welfare?sslmode=require
APP_PII_DB_URL: jdbc:postgresql://<rds-endpoint>:5432/youth_welfare?sslmode=require&currentSchema=youth_welfare_pii
NOTIFICATION_PII_DB_URL: jdbc:postgresql://<rds-endpoint>:5432/youth_welfare?sslmode=require&currentSchema=youth_welfare_pii
```

그리고 `app` 서비스의 `depends_on`에서 `db` 의존성은 제거하거나, 로컬 개발용 compose와 운영용 compose를 분리해야 한다. RDS 전환 후 운영 환경에서는 더 이상 `youth-welfare-db` 컨테이너가 필수 dependency가 아니다.

현재:

```yaml
depends_on:
  db:
    condition: service_healthy
  redis:
    condition: service_healthy
```

RDS 전환 후 운영 compose에서는 대략 다음 형태가 되어야 한다.

```yaml
depends_on:
  redis:
    condition: service_healthy
```

또는 Redis도 ElastiCache 등으로 분리한다면 `redis` dependency도 운영 compose에서 제거한다.

### 17.4 `schema.sql`은 RDS에서 자동 실행되지 않는다

현재 로컬 PostgreSQL 컨테이너는 Docker entrypoint를 통해 schema를 자동 초기화한다.

```yaml
volumes:
  - ./backend/src/main/resources/db/schema.sql:/docker-entrypoint-initdb.d/01-schema.sql:ro
  - ./deploy/postgres/init/z90-create-runtime-db-users.sh:/docker-entrypoint-initdb.d/z90-create-runtime-db-users.sh:ro
```

이 방식은 Docker PostgreSQL 컨테이너를 처음 만들 때만 동작한다. RDS에서는 이 entrypoint 방식이 없다. 따라서 RDS 전환 시에는 schema와 role을 별도로 생성해야 한다.

필수 작업:

- RDS database 생성
- `pgcrypto` extension 생성
- `pg_trgm` extension 생성
- `vector` extension 생성
- `youth_welfare_pii` schema 생성
- 테이블 생성
- 인덱스 생성
- sequence 확인
- runtime role 생성
- role별 권한 부여
- 기존 데이터 restore

현재 schema에는 다음 extension이 필요하다.

```sql
CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS vector;
```

AWS RDS for PostgreSQL은 pgvector를 지원한다. AWS 공지 기준으로 pgvector 0.8.0은 RDS PostgreSQL 16.5 이상에서 사용 가능하다. 현재 로컬 DB는 PostgreSQL 16.13이므로, RDS도 PostgreSQL 16 계열 최신 minor version을 선택하고 `vector` extension 생성 가능 여부를 반드시 확인해야 한다.

주의:

- RDS에서는 OS level superuser가 아니다.
- extension 생성은 RDS가 지원하는 extension에 한해서 가능하다.
- migration 전에 staging RDS에서 `CREATE EXTENSION vector;`를 직접 검증해야 한다.

### 17.5 role/password/권한 준비가 필요하다

현재 애플리케이션은 여러 DB role을 사용한다.

주요 role:

- `app_core_rw`
- `app_pii_rw`
- `notification_pii_ro`
- `admin_dashboard_ro`
- `cluster_ai_cleanup_rw`
- `recommendation_retention_cleanup_rw`
- `collect_execution_lock_cleanup_rw`
- `web_push_subscription_cleanup_rw`

로컬 Docker DB에서는 `deploy/postgres/init/z90-create-runtime-db-users.sh`가 초기 role 생성에 관여한다. RDS에서는 이 스크립트가 자동 실행되지 않는다. 따라서 RDS에 role을 수동 또는 별도 SQL로 생성해야 한다.

또한 `application.yml`에는 여러 보조 datasource가 있다. 운영에서 해당 기능을 쓴다면 이 role들의 password 환경변수도 맞춰야 한다.

예:

```bash
DB_ADMIN_RO_USERNAME=admin_dashboard_ro
DB_ADMIN_RO_PASSWORD=<password>
RECOMMENDATION_REVIEW_GATE_COMMAND_DB_URL=jdbc:postgresql://<host>:5432/youth_welfare?sslmode=require
DB_RECOMMENDATION_REVIEW_GATE_COMMAND_USERNAME=recommendation_review_gate_command_rw
DB_RECOMMENDATION_REVIEW_GATE_COMMAND_PASSWORD=<password>
RECOMMENDATION_PERSISTENCE_COMMAND_DB_URL=jdbc:postgresql://<host>:5432/youth_welfare?sslmode=require
DB_RECOMMENDATION_PERSISTENCE_COMMAND_USERNAME=recommendation_persistence_command_rw
DB_RECOMMENDATION_PERSISTENCE_COMMAND_PASSWORD=<password>
DB_CLUSTER_AI_CLEANUP_USERNAME=cluster_ai_cleanup_rw
DB_CLUSTER_AI_CLEANUP_PASSWORD=<password>
DB_RECOMMENDATION_RETENTION_CLEANUP_USERNAME=recommendation_retention_cleanup_rw
DB_RECOMMENDATION_RETENTION_CLEANUP_PASSWORD=<password>
DB_COLLECT_EXECUTION_LOCK_CLEANUP_USERNAME=collect_execution_lock_cleanup_rw
DB_COLLECT_EXECUTION_LOCK_CLEANUP_PASSWORD=<password>
DB_WEB_PUSH_SUBSCRIPTION_CLEANUP_USERNAME=web_push_subscription_cleanup_rw
DB_WEB_PUSH_SUBSCRIPTION_CLEANUP_PASSWORD=<password>
```

운영 profile, RDS bootstrap/verify, runtime cutover preflight는 recommendation command role env 누락을 `DB_URL`/`DB_PASSWORD`로 fallback하지 않는다. role별 password를 명시하지 않으면 운영 preflight 또는 bootstrap이 먼저 실패해야 한다.

### 17.6 connection pool 설정은 수정하는 것이 좋다

현재 측정에서 앱은 idle DB connection을 30개 유지했다.

```text
app_core_rw: idle 10
app_pii_rw: idle 10
notification_pii_ro: idle 10
```

이것은 datasource 3개가 각각 Hikari 기본 pool size를 사용하기 때문으로 보인다. RDS로 가면 connection은 더 중요한 자원이 된다. 특히 EC2를 여러 대로 늘릴 경우 총 connection 수가 앱 인스턴스 수만큼 곱해진다.

따라서 RDS 전환 시 Java 로직 수정은 아니지만, `application-prod.yml` 또는 환경변수로 Hikari pool을 명시하는 것이 좋다.

예:

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 10
      minimum-idle: 2
      connection-timeout: 3000
      idle-timeout: 600000
      max-lifetime: 1800000

app:
  datasource:
    pii-rw:
      hikari:
        maximum-pool-size: 3
        minimum-idle: 1
    notification-pii-ro:
      hikari:
        maximum-pool-size: 3
        minimum-idle: 1
```

운영값은 실제 트래픽과 RDS instance class에 맞춰 조정해야 한다. 핵심은 datasource가 많기 때문에 기본값을 그대로 두면 connection이 불필요하게 많아질 수 있다는 점이다.

### 17.7 SSL 설정 검토가 필요하다

현재 로컬 DB URL은 `sslmode=disable`이다.

```text
jdbc:postgresql://db:5432/youth_welfare?sslmode=disable
```

RDS 운영에서는 최소한 `sslmode=require`를 검토하는 것이 좋다.

예:

```text
jdbc:postgresql://<rds-endpoint>:5432/youth_welfare?sslmode=require
```

더 엄격하게 하려면 `verify-full`과 AWS RDS CA 인증서를 검토할 수 있다. 다만 이 경우 인증서 파일 배포와 JVM trust 설정까지 필요할 수 있으므로 전환 난이도가 올라간다.

현실적인 1차 전환안:

```text
sslmode=require
```

강화안:

```text
sslmode=verify-full
```

### 17.8 보안 그룹과 네트워크는 코드가 아니라 인프라 변경이다

RDS 전환 시 애플리케이션 코드보다 더 중요한 것은 네트워크다.

필수 조건:

- RDS는 private subnet 권장
- Public access 비활성화 권장
- RDS inbound 5432는 EC2 security group에서만 허용
- EC2에서 RDS endpoint DNS resolve 가능해야 함
- 같은 VPC 또는 VPC peering/routing 구성 필요
- NACL과 route table 확인

코드는 맞는데 보안 그룹이 막혀 있으면 앱은 DB connection timeout으로 뜨지 않는다.

### 17.9 RDS 전환 전 smoke/preflight 스크립트 활용

이미 repo에는 runtime DB URL을 검증하는 스크립트가 있다.

```text
deploy/smoke/preflight-runtime-cutover-env.sh
```

이 스크립트는 다음을 검증한다.

- `DB_URL` 존재 여부
- `APP_PII_DB_URL` 존재 여부
- `NOTIFICATION_PII_DB_URL` 존재 여부
- PostgreSQL JDBC URL 형식
- PII datasource가 `currentSchema`를 포함하는지
- secondary datasource가 core DB URL과 완전히 동일하게 collapse되지 않는지

RDS 전환 전에 이 스크립트를 RDS용 환경변수로 실행하는 것이 좋다.

### 17.10 정리: 필요한 변경과 불필요한 변경

| 구분 | 수정 필요 여부 | 설명 |
| --- | --- | --- |
| Java Controller/Service/Repository | 대체로 불필요 | 이미 PostgreSQL 사용 중이고 JDBC URL만 바뀜 |
| Entity/DTO | 불필요 | DB 종류가 바뀌는 것이 아님 |
| SQL query | 대체로 불필요 | PostgreSQL -> PostgreSQL 전환 |
| `DB_URL` | 필요 | RDS endpoint로 변경 |
| `APP_PII_DB_URL` | 필요 | RDS endpoint + `currentSchema=youth_welfare_pii` 유지 |
| `NOTIFICATION_PII_DB_URL` | 필요 | RDS endpoint + `currentSchema=youth_welfare_pii` 유지 |
| `docker-compose.yml` 운영 설정 | 필요 | `db` service dependency 제거 또는 운영/로컬 compose 분리 |
| RDS schema 생성 | 필요 | Docker entrypoint 자동 실행이 없어짐 |
| Extension 생성 | 필요 | `pgcrypto`, `pg_trgm`, `vector` |
| Runtime DB role 생성 | 필요 | 앱이 사용하는 role들을 RDS에 생성해야 함 |
| Hikari pool 설정 | 권장 | RDS connection 수 관리 |
| SSL 설정 | 권장 | `sslmode=require` 이상 검토 |
| 보안 그룹 | 필요 | EC2 -> RDS 5432 허용 |
| 백업/restore 절차 | 필요 | `pg_dump`/restore 또는 migration 방식 결정 |

최종 결론:

```text
RDS 전환 때문에 애플리케이션 비즈니스 코드를 크게 고칠 필요는 없다.
하지만 운영 설정은 반드시 바뀐다.

핵심 변경은 DB_URL 계열 환경변수를 RDS endpoint로 바꾸는 것,
RDS에 schema/extension/role/권한을 미리 생성하는 것,
docker-compose의 로컬 db 의존성을 운영 배포에서 제거하는 것,
그리고 Hikari connection pool과 SSL 설정을 운영 기준으로 조정하는 것이다.
```

## 18. 전체 수집 실행 중 성능 측정 결과

작성 기준: 2026-05-21
측정 구간: 2026-05-21 13:40:09 UTC - 2026-05-21 13:55:53 UTC
측정 산출물:

```text
/tmp/youth-welfare-collect-perf-20260521T134004Z
/tmp/youth-welfare-collect-perf-20260521T134004Z/manual-lanes-20260521T134407Z
```

이 섹션은 실제로 전체 수집성 작업을 실행하면서 EC2 단일 구조에서 앱과 DB가 어떤 부하를 받는지 측정한 결과다. RDS 전환 근거에서 매우 중요한 부분이다. 일반 API 조회만 측정하면 서비스가 가볍게 보일 수 있지만, 이 서비스는 외부 공공 API 수집, 정규화, raw payload 저장, 상세 데이터 보강, 검색/추천용 데이터 갱신이 같이 존재한다. 따라서 수집 작업 중 성능을 봐야 실제 운영 부하를 더 잘 이해할 수 있다.

### 18.1 측정 방식

먼저 `/api/admin/collect/all`을 실행했다.

```text
POST http://127.0.0.1:8082/api/admin/collect/all
```

실행 결과 이 endpoint는 이름상 전체 수집처럼 보이지만, 코드 기준으로는 `CollectSource.executionOrder()`에 포함된 예약 배치 대상만 실행한다.

실제로 `/api/admin/collect/all`에서 실행된 source는 다음 4개였다.

```text
YOUTH
BOKJIRO_CENTRAL
BOKJIRO_LOCAL
BOKJIRO_DETAIL
```

이후 “모든 API 수집”에 가깝게 보기 위해 수동 lane을 추가로 실행했다.

추가 실행한 endpoint:

```text
POST /api/admin/collect/gov24
POST /api/admin/collect/gov24-details
POST /api/admin/collect/gov24-support-conditions
POST /api/admin/collect/youth-details
POST /api/admin/collect/bokjiro-details-refresh
```

측정 중 수집한 항목:

- endpoint별 curl total time
- `api_sync_logs` source별 requested/saved/skipped/filtered/failed
- Docker container CPU/memory/Block I/O/Network I/O
- PostgreSQL `pg_stat_database` before/after
- root disk 사용률 before/after
- 전체 수집 전/후 정책 목록 API latency

관리자 인증은 운영 앱의 JWT 설정으로 관리자 권한 토큰을 생성해서 내부 loopback으로 호출했다. 외부에 JWT secret이나 token 값은 출력하지 않았다.

### 18.2 `/api/admin/collect/all` 측정 결과

`/api/admin/collect/all` 호출 결과:

```text
http_code=200
time_total=150.113492s
elapsed_seconds=150
completedWithFailures=false
requestedSourceCount=4
succeededSourceCount=4
failedSourceCount=0
```

source별 결과:

| Source | requested | saved | skipped | filtered | failed | duration |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| `YOUTH` | 2,600 | 2,600 | 0 | 0 | 0 | 56.689s |
| `BOKJIRO_CENTRAL` | 415 | 135 | 0 | 280 | 0 | 9.258s |
| `BOKJIRO_LOCAL` | 4,565 | 1,223 | 0 | 3,342 | 0 | 82.742s |
| `BOKJIRO_DETAIL` | 1 | 1 | 1,357 | 0 | 0 | 1.277s |

해석:

- 예약 배치 대상 4개는 총 150초에 성공했다.
- `BOKJIRO_LOCAL`이 82.7초로 가장 오래 걸렸다.
- `YOUTH`는 2,600건을 저장하면서 56.7초가 걸렸다.
- `BOKJIRO_CENTRAL`, `BOKJIRO_LOCAL`은 청년 대상 필터링으로 저장 수보다 filtered 수가 많다.
- `BOKJIRO_DETAIL`은 대부분 이미 상세가 있어 skip되었고 1건만 요청/저장했다.

### 18.3 추가 수동 lane 측정 결과

추가 수동 lane 전체 결과:

| Endpoint | http_code | time_total | 결과 |
| --- | ---: | ---: | --- |
| `/api/admin/collect/gov24` | 200 | 200.439926s | 성공 |
| `/api/admin/collect/gov24-details` | 200 | 3.529461s | 성공 |
| `/api/admin/collect/gov24-support-conditions` | 200 | 6.547088s | 성공 |
| `/api/admin/collect/youth-details` | 200 | 20.336449s | 성공 |
| `/api/admin/collect/bokjiro-details-refresh` | 200 | 472.388211s | 성공 |

DB 로그 기준 상세 결과:

| Source | requested | saved | skipped | filtered | failed | duration |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| `GOV24` | 10,945 | 10,945 | 0 | 0 | 0 | 188.672s |
| `GOV24_DETAIL` | 8 | 8 | 10,941 | 0 | 0 | 5.200s |
| `GOV24_SUPPORT_CONDITIONS` | 8 | 8 | 10,941 | 0 | 0 | 6.510s |
| `GOV24_DETAIL` 재실행 | 0 | 0 | 10,949 | 0 | 0 | 3.506s |
| `GOV24_SUPPORT_CONDITIONS` 재실행 | 0 | 0 | 10,949 | 0 | 0 | 6.526s |
| `YOUTH_DETAILS` | 25 | 25 | 2,576 | 0 | 0 | 20.313s |
| `BOKJIRO_DETAIL_REFRESH` | 1,358 | 1,358 | 0 | 0 | 0 | 472.358s |

주의:

- `/api/admin/collect/gov24` 내부에서 `GOV24`, `GOV24_DETAIL`, `GOV24_SUPPORT_CONDITIONS`가 함께 실행되었다.
- 이후 별도로 `gov24-details`, `gov24-support-conditions`를 다시 호출했기 때문에 DB 로그에는 Gov24 detail/support 조건 재실행 기록이 추가로 남았다.
- 재실행 시점에는 이미 대상 데이터가 채워져 있어 requested 0, skipped 10,949로 빠르게 종료되었다.

해석:

- 전체 수동 lane 중 가장 긴 작업은 `BOKJIRO_DETAIL_REFRESH`로 약 472초, 즉 약 7분 52초가 걸렸다.
- `GOV24` 목록 수집은 약 189초가 걸렸고 10,945건을 저장했다.
- `YOUTH_DETAILS`는 25건만 요청했고, 2,576건은 이미 상세가 있어 skip되었다.
- Gov24 detail/support 조건은 신규 대상 8건만 처리했고 이후 재실행에서는 전부 skip되었다.

### 18.4 전체 실행 시간 요약

전체 실행 구간:

```text
시작: 2026-05-21 13:40:09 UTC
종료: 2026-05-21 13:55:53 UTC
총 소요: 약 15분 44초
```

전체 source 처리 결과를 합치면 다음과 같다.

| 구분 | 값 |
| --- | ---: |
| 실패 source | 0 |
| deadlock | 0 |
| 가장 오래 걸린 작업 | `BOKJIRO_DETAIL_REFRESH` |
| 가장 DB 부하가 컸던 구간 | `GOV24` 저장/정규화 구간 |
| 가장 많은 저장 건수 | `GOV24` 10,945건 |

이 결과는 단일 EC2 구조에서 일반 API 요청보다 수집/정규화 작업이 훨씬 큰 부하를 만든다는 것을 보여준다.

### 18.5 Docker container peak 리소스

`/api/admin/collect/all` 실행 중 peak:

| Container | max CPU | max memory |
| --- | ---: | ---: |
| `youth-welfare-app` | 54.11% | 759.7MiB |
| `youth-welfare-db` | 161.11% | 287.6MiB |
| `youth-welfare-redis` | 4.21% | 7.9MiB |

추가 수동 lane 실행 중 peak:

| Container | max CPU | max memory |
| --- | ---: | ---: |
| `youth-welfare-app` | 84.44% | 994.8MiB |
| `youth-welfare-db` | 169.48% | 286.7MiB |
| `youth-welfare-redis` | 3.95% | 7.9MiB |

해석:

- DB 컨테이너 CPU가 160-170% 수준까지 올라갔다.
- 현재 EC2는 2 vCPU이므로 PostgreSQL이 순간적으로 2 vCPU 중 상당 부분을 사용했다는 의미다.
- 앱 컨테이너도 수동 lane 중 최대 약 995MiB까지 상승했다.
- Redis는 이 작업에서 의미 있는 병목이 아니었다.
- 수집 작업 중 특히 DB CPU가 많이 상승하므로, 앱과 DB가 같은 EC2에 있으면 일반 API 처리와 수집/정규화가 CPU를 공유하게 된다.

이 측정은 RDS 전환 근거로 중요하다. RDS로 PostgreSQL을 분리하면 최소한 DB query/write/autovacuum/checkpoint 성격의 CPU와 I/O 부하가 EC2 앱 서버에서 빠진다. EC2는 Spring Boot, Nginx, Redis, Docker 관리에 더 집중할 수 있다.

### 18.6 Docker before/after 리소스 변화

`/api/admin/collect/all` 전후:

| Container | before memory | after memory | before Block I/O | after Block I/O |
| --- | ---: | ---: | ---: | ---: |
| `youth-welfare-app` | 705.7MiB | 755.8MiB | 17.3MB / 48.4MB | 17.6MB / 48.5MB |
| `youth-welfare-db` | 203.5MiB | 287.7MiB | 1.91GB / 8.56GB | 1.92GB / 8.83GB |
| `youth-welfare-redis` | 7.6MiB | 7.8MiB | 39.3MB / 5.86MB | 39.3MB / 5.86MB |

추가 수동 lane 전후:

| Container | before memory | after memory | before Block I/O | after Block I/O |
| --- | ---: | ---: | ---: | ---: |
| `youth-welfare-app` | 756.0MiB | 898.5MiB | 17.6MB / 48.5MB | 17.9MB / 48.8MB |
| `youth-welfare-db` | 204.4MiB | 221.6MiB | 1.92GB / 8.85GB | 1.94GB / 9.99GB |
| `youth-welfare-redis` | 7.7MiB | 7.6MiB | 39.3MB / 5.86MB | 39.3MB / 5.86MB |

해석:

- 전체 수집 후 앱 메모리는 약 705MiB에서 약 898MiB 수준까지 올라갔다.
- 수동 lane 중 앱 peak는 약 995MiB였다.
- DB write I/O는 `/api/admin/collect/all` 구간에서 약 8.56GB -> 8.83GB로 증가했고, 수동 lane 이후 9.99GB까지 증가했다.
- 즉 전체 측정 동안 DB 컨테이너 누적 write I/O가 약 1.43GB 증가했다.

### 18.7 PostgreSQL before/after 변화

전체 측정 전 DB 상태:

```text
xact_commit  = 612,466
xact_rollback = 252
blks_read    = 1,814,885
blks_hit     = 72,989,787
tup_inserted = 1,554,463
tup_updated  = 605,586
tup_deleted  = 986,567
temp_files   = 17
temp_bytes   = 96,010,240
deadlocks    = 0
```

전체 측정 후 DB 상태:

```text
xact_commit  = 698,628
xact_rollback = 252
blks_read    = 1,927,299
blks_hit     = 80,961,092
tup_inserted = 1,727,481
tup_updated  = 690,990
tup_deleted  = 1,158,343
temp_files   = 18
temp_bytes   = 99,483,648
deadlocks    = 0
```

변화량:

| 항목 | 증가량 |
| --- | ---: |
| `xact_commit` | +86,162 |
| `xact_rollback` | +0 |
| `blks_read` | +112,414 |
| `blks_hit` | +7,971,305 |
| `tup_inserted` | +173,018 |
| `tup_updated` | +85,404 |
| `tup_deleted` | +171,776 |
| `temp_files` | +1 |
| `temp_bytes` | +3,473,408 bytes |
| `deadlocks` | +0 |

해석:

- 전체 수집/정규화 과정에서 insert/update/delete가 모두 크게 발생했다.
- `tup_deleted`가 17만 이상 증가한 것은 수집/정규화 과정에서 교체성 데이터 또는 관계 테이블 재구성이 발생한다는 의미로 볼 수 있다.
- `tup_inserted`도 17만 이상 증가했다.
- deadlock은 발생하지 않았다.
- temp file은 1개 증가했고 temp bytes도 약 3.47MB 증가했다.
- DB cache hit도 크게 증가했지만, 동시에 read block도 11만 이상 증가했다.

이 변화량은 수집 작업이 단순 HTTP fetch가 아니라 PostgreSQL write-heavy 작업이라는 것을 보여준다. 현재처럼 앱과 DB가 같은 EC2에 있으면 이 write-heavy 작업이 사용자 API와 같은 CPU/디스크 자원을 공유한다.

### 18.8 정책 목록 API latency 변화

측정 대상:

```text
GET /api/policies?page=0&size=20
```

측정 결과:

| 시점 | count | avg | p50 | p90 | p95 | min | max |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 수집 전 | 20 | 92.4ms | 88.6ms | 109.5ms | 112.7ms | 81.9ms | 132.9ms |
| `/api/admin/collect/all` 직후 | 20 | 230.8ms | 219.9ms | 276.3ms | 279.7ms | 141.9ms | 361.3ms |
| 모든 수동 lane 완료 후 | 20 | 93.0ms | 86.1ms | 103.8ms | 115.7ms | 81.8ms | 141.9ms |

해석:

- 수집 전 정책 목록 p95는 약 112.7ms였다.
- `/api/admin/collect/all` 직후 p95는 약 279.7ms로 상승했다.
- 모든 수동 lane 완료 후에는 p95 약 115.7ms로 baseline에 가깝게 돌아왔다.

즉 수집 작업 중 또는 직후에는 일반 조회 API latency가 악화될 수 있다. 작업이 끝나면 회복되지만, 사용자 트래픽이 있는 시간대에 수집/정규화가 겹치면 체감 성능 저하가 발생할 수 있다.

### 18.9 디스크와 메모리 변화

전체 측정 전:

```text
root disk: 19GB 중 15GB 사용, 79%
available: 4.0GB
memory available: 약 1.9GiB
swap: 없음
```

전체 측정 후:

```text
root disk: 19GB 중 15GB 사용, 80%
available: 3.8GB
memory available: 약 1.7GiB
swap: 없음
```

해석:

- 수집 작업 후 root disk 사용률은 79%에서 80%로 증가했다.
- 남은 공간은 약 4.0GB에서 3.8GB로 줄었다.
- 한 번의 전체 수집성 작업만으로도 Docker/DB/log/volume 영향이 누적되어 디스크 여유가 줄어든다.
- 현재 root disk가 19GB로 작고 사용률이 이미 높기 때문에, 운영 중 반복 수집과 로그/캐시 누적이 계속되면 disk full 리스크가 커진다.

RDS로 분리하면 PostgreSQL data/WAL/write I/O는 RDS storage로 이동한다. EC2 root disk에는 여전히 Docker image/cache/log 관리가 필요하지만, DB write와 DB storage 증가가 EC2 root disk를 직접 압박하는 문제는 줄어든다.

### 18.10 RDS 전환 근거로서의 해석

이번 전체 수집 측정에서 가장 중요한 관찰은 다음이다.

```text
일반 조회 API만 볼 때는 EC2 단일 구조가 크게 문제 없어 보일 수 있다.
하지만 전체 수집/정규화/상세 refresh를 실행하면 PostgreSQL 컨테이너 CPU가 160-170%까지 상승하고,
DB write I/O와 insert/update/delete가 대량 발생하며,
일반 정책 목록 API p95도 일시적으로 112.7ms에서 279.7ms까지 상승했다.
```

RDS 전환의 성능상 의미:

- DB CPU 부하를 EC2 앱 서버에서 분리한다.
- DB write I/O를 EC2 root disk에서 분리한다.
- 수집 작업 중 앱 API와 DB가 같은 CPU/디스크를 놓고 경쟁하는 구조를 완화한다.
- 수집/정규화 부하를 DB 전용 metric으로 관측할 수 있다.
- 향후 수집 작업과 사용자 API를 시간대/리소스 관점에서 분리 운영하기 쉬워진다.

RDS 전환 후 기대할 수 있는 변화:

- EC2 앱 서버에서 PostgreSQL 컨테이너 CPU 사용량이 사라진다.
- EC2 root disk의 DB write 증가가 줄어든다.
- 수집 중 앱 컨테이너는 여전히 CPU와 메모리를 사용하지만, DB와 같은 인스턴스에서 경쟁하지 않는다.
- RDS 쪽 CPU/IOPS/latency 지표로 수집 병목을 별도로 추적할 수 있다.

주의할 점:

- RDS로 옮긴다고 수집 자체가 무조건 빨라지는 것은 아니다.
- 외부 API 호출 대기, rate limit, 네트워크 latency는 여전히 존재한다.
- RDS 네트워크 hop이 추가되므로 단건 DB latency는 일부 증가할 수 있다.
- 하지만 수집/정규화 중 앱 서버와 DB 서버의 리소스 경쟁을 분리하는 효과가 크다.

### 18.11 최종 결론

이번 전체 수집 측정은 EC2 + RDS 전환 근거를 더 강하게 만든다.

기존 근거가 “디스크 사용률, 단일 장애 지점, 백업/복구, 일반 API 동시성” 중심이었다면, 이번 측정은 실제 운영성 배치인 수집 작업에서 다음을 확인했다.

- 전체 수집성 작업은 약 15분 44초 동안 실행되었다.
- 모든 source는 실패 없이 성공했다.
- DB 컨테이너 CPU는 최대 169.48%까지 상승했다.
- 앱 컨테이너 메모리는 최대 약 994.8MiB까지 상승했다.
- 전체 측정 동안 DB write I/O는 약 1.43GB 증가했다.
- PostgreSQL row insert/update/delete가 각각 대량 발생했다.
- 일반 정책 목록 API p95가 수집 직후 약 2.5배 상승했다.
- root disk 사용률은 79%에서 80%로 증가했다.

따라서 RDS 전환의 메시지는 다음처럼 보강할 수 있다.

```text
현재 구조는 평상시 조회 트래픽만 보면 버틸 수 있어 보이지만,
실제 운영에 필요한 전체 수집/정규화/상세 refresh 작업을 수행하면
PostgreSQL이 2 vCPU EC2의 CPU와 디스크 I/O를 크게 점유한다.

수집 작업 중 일반 API latency도 일시적으로 악화되며,
DB write-heavy 작업이 EC2 root disk 사용률을 계속 밀어 올린다.

따라서 DB를 RDS로 분리하는 것은 단순한 인프라 취향이 아니라,
수집 배치 부하와 사용자 API 부하를 격리하고,
DB write I/O와 backup/recovery 책임을 관리형 DB로 넘기기 위한 운영상 필요한 구조 개선이다.
```
