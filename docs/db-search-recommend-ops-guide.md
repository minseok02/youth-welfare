# DB / 검색 / 추천 운영 가이드

이 문서는 현재 프로젝트 기준으로 아래 3가지를 정리한다.

1. 바로 추가를 검토할 인덱스 DDL
2. EXPLAIN으로 확인할 체크 포인트
3. `MySQL 한 대 시작안` 과 `EC2 + RDS 시작안` 비교

관련 코드:

- 검색 쿼리: [WelfareServiceRepository](/home/minseok/youth-welfare/backend/src/main/java/com/example/welfare/policy/repository/WelfareServiceRepository.java:103)
- 검색 서비스: [PolicySearchService](/home/minseok/youth-welfare/backend/src/main/java/com/example/welfare/policy/service/PolicySearchService.java:40)
- 추천 후보 조회: [RetrievalService](/home/minseok/youth-welfare/backend/src/main/java/com/example/welfare/recommend/service/RetrievalService.java:37)
- 현재 스키마: [schema.sql](/home/minseok/youth-welfare/backend/src/main/resources/db/schema.sql:77)
- 로컬 MySQL ngram 설정: [docker-compose.yml](/home/minseok/youth-welfare/docker-compose.yml:38)

## 1. 추가 검토할 인덱스 DDL

2026-04-27 로컬 Docker MySQL(`welfare_services` 3,604건 / `service_regions` 117,640건) 기준으로 EXPLAIN을 다시 확인했다.
결론은 `service_regions` 복합 인덱스는 즉시 적용, `welfare_services` 정렬 보조 인덱스는 보류다.
다만 `sido/sgg` 기반 지역 검색은 옵티마이저가 새 복합 인덱스를 항상 선택하지 않아 후속 재측정이 필요하다.

### A. 지역 검색 / 지역 추천 보강

현재 지역 조건은 아래 두 패턴을 많이 탄다.

- 추천 후보 조회
  - `LEFT JOIN service_regions`
  - `sr.regionCode = ? OR sr.sidoName = ?`
- 검색 필터
  - `EXISTS (service_id = ws.id AND sido_name = ? AND sgg_name = ?)`

적용 확정 DDL:

```sql
ALTER TABLE service_regions
    ADD INDEX idx_sr_service_sido_sgg (service_id, sido_name, sgg_name),
    ADD INDEX idx_sr_service_region_code (service_id, region_code);
```

2026-04-27 확인 결과:

- migration 파일 `V2026_04_27_01__add_service_region_compound_indexes.sql` 적용과 `SHOW INDEX` 확인 완료
- 추천 지역 후보 쿼리는 `LEFT JOIN + DISTINCT` 대신 `EXISTS/NOT EXISTS` 구조로 바꿔 임시 테이블 dedup 비용을 제거
- 지역 검색 `sido/sgg` 서브쿼리는 재적용 후 EXPLAIN에서 `idx_sr_service` 를 계속 선택하는 케이스가 있어 후속 재검토가 필요

의도:

- `EXISTS ... service_id + sido_name + sgg_name` 패턴 최적화
- `service_id + region_code` 패턴 최적화

주의:

- 이미 `idx_sr_service`, `idx_sr_region_code`, `idx_sr_sido` 가 있다.
- 위 복합 인덱스 추가 후 단일 인덱스는 EXPLAIN 확인 뒤 정리할 수 있다.

### B. 최신순 후보 추출 보강

현재 추천 후보 쿼리 중 하나는 `status` 필터 후 `createdAt DESC` 정렬을 쓴다.

보류:

```sql
ALTER TABLE welfare_services
    ADD INDEX idx_ws_status_created (status, created_at DESC);
```

- 현재 데이터에선 `status='ACTIVE'` 비중이 거의 전부라 선택도가 낮다.
- `findLatestCandidates` EXPLAIN 기준 `welfare_services` 전체 스캔 3,604건, `actual time=14.7ms`
- 추천 최신순은 쿼리 구조보다 후보 수 자체가 아직 작아 체감 이득이 작다.

원래 의도:

- 최신 정책 M건 보강용 쿼리
- 최신순 검색 정렬

### C. 조회수 정렬 보강

현재 검색 정렬 옵션 중 `VIEWS` 가 있다.

보류:

```sql
ALTER TABLE welfare_services
    ADD INDEX idx_ws_status_view_created (status, view_count DESC, created_at DESC);
```

- FULLTEXT 검색 결과 1,567건 정렬 케이스(`+청년 +지원`, `sort=VIEWS`)도 `actual time=4.2ms` 수준이었다.
- 현재 병목은 `status/view_count` 정렬보다 FULLTEXT 후보 집합과 지역 조건 쪽이다.

원래 의도:

- `status` 필터 후 조회수 정렬 최적화
- 동점일 때 최신순 정렬 보조

### D. 검색 필터 보조 인덱스는 과하게 늘리지 않는다

아래 컬럼은 언뜻 복합 인덱스를 많이 추가하고 싶어지지만, 현재 검색의 핵심이 `MATCH ... AGAINST` 라서 효과가 제한적일 수 있다.

- `unified_category`
- `source_type`
- `is_online_apply`
- `search_youth_relevant`

현재 이미 있는 인덱스:

- `idx_ws_unified_cat`
- `idx_ws_search_youth`
- `idx_ws_source_type`

결론:

- 여기서 복합 인덱스를 무리하게 늘리기보다 먼저 EXPLAIN을 본다.
- FULLTEXT 경로가 메인이면 보조 인덱스 효과가 생각보다 작을 수 있다.

### E. 보류 항목

아래는 지금 당장은 보류를 권장한다.

```sql
-- 보류: FULLTEXT 대체를 위한 일반 인덱스
-- title prefix index, keyword prefix index 등
```

이유:

- 한국어 검색의 핵심은 `ngram FULLTEXT`
- 일반 prefix 인덱스는 부분 매칭 품질이 기대보다 낮다

## 2. EXPLAIN 체크 포인트

## 2-1. 2026-04-27 측정 요약

- 일반 검색 본문/카운트는 `ft_ws_search` 를 정상 사용했다.
- `service_regions` 복합 인덱스 migration 추가와 `SHOW INDEX` 검증은 완료했다.
- 추천 지역 후보는 기존 `LEFT JOIN service_regions + DISTINCT` 구조에서 임시 테이블과 dedup 비용이 있었다.
- 추천 지역 후보 JPQL은 `EXISTS/NOT EXISTS` 구조로 바꿔 같은 정책이 여러 지역 row를 가질 때도 중복 제거용 임시 테이블을 만들지 않도록 정리했다.
- 지역 검색 `sido/sgg` 서브쿼리의 복합 인덱스 선택 안정성은 운영 데이터 기준으로 다시 확인한다.
- `idx_ws_status_created`, `idx_ws_status_view_created` 는 이번 데이터 규모와 분포에선 이득이 작아 보류한다.

배포 전 최소한 아래 쿼리는 `EXPLAIN ANALYZE` 또는 `EXPLAIN FORMAT=JSON` 으로 확인하는 것이 좋다.

## A. 검색 쿼리

대상:

- `searchByKeywordWithFiltersNoRegion`
- `searchByKeywordWithFiltersWithRegion`

확인할 것:

1. `MATCH ... AGAINST` 가 실제로 FULLTEXT 인덱스를 타는지
2. `countQuery` 가 지나치게 비싼지
3. 지역 필터 `EXISTS` 서브쿼리가 `service_regions` 인덱스를 타는지
4. `Using temporary`, `Using filesort` 가 과도한지

예시:

```sql
EXPLAIN FORMAT=JSON
SELECT ws.*
FROM welfare_services ws
WHERE ws.search_youth_relevant = 1
  AND MATCH(ws.title, ws.description, ws.support_content, ws.keyword)
      AGAINST ('+청년 +취업' IN BOOLEAN MODE)
ORDER BY ws.created_at DESC
LIMIT 20 OFFSET 0;
```

지역 포함 예시:

```sql
EXPLAIN FORMAT=JSON
SELECT ws.*
FROM welfare_services ws
WHERE ws.search_youth_relevant = 1
  AND (
        NOT EXISTS (
            SELECT 1 FROM service_regions sr1
            WHERE sr1.service_id = ws.id
        )
        OR EXISTS (
            SELECT 1 FROM service_regions sr2
            WHERE sr2.service_id = ws.id
              AND sr2.sido_name = '서울특별시'
              AND sr2.sgg_name = '강남구'
        )
      )
  AND MATCH(ws.title, ws.description, ws.support_content, ws.keyword)
      AGAINST ('+청년 +취업' IN BOOLEAN MODE)
LIMIT 20 OFFSET 0;
```

판단 기준:

- 작은 데이터셋에선 `filesort` 가 보여도 괜찮다.
- 문제는 `rows examined` 가 결과 대비 너무 큰 경우다.
- `countQuery` 가 본문보다 훨씬 느리면 검색 total count 정책을 바꾸는 것도 검토 대상이다.

## B. 추천 후보 쿼리

대상:

- `findCandidates`
- `findCandidatesWithRegion`
- `findLatestCandidates`
- `findLatestCandidatesWithRegion`

확인할 것:

1. `status` 인덱스를 타는지
2. `LEFT JOIN service_regions` 이후 `DISTINCT` 때문에 비용이 커지는지
3. `ORDER BY created_at DESC` 가 정렬 비용을 많이 쓰는지
4. `rows` 가 `FETCH_SIZE=150` 대비 과도하게 큰지

예시:

```sql
EXPLAIN FORMAT=JSON
SELECT DISTINCT ws.*
FROM welfare_services ws
LEFT JOIN service_regions sr ON sr.service_id = ws.id
WHERE ws.status IN ('ACTIVE', 'UPCOMING')
  AND (ws.min_age IS NULL OR ws.min_age <= 25)
  AND (ws.max_age IS NULL OR ws.max_age >= 25)
  AND (
        ws.source_type <> 'YOUTH'
        OR (
            (ws.min_income IS NULL OR ws.min_income <= 5)
            AND (ws.max_income IS NULL OR ws.max_income >= 5)
        )
      )
  AND (sr.id IS NULL OR sr.region_code = '11680' OR sr.sido_name = '서울특별시')
ORDER BY ws.created_at DESC
LIMIT 20;
```

판단 기준:

- 지금 규모에선 완벽한 인덱스 플랜보다 `LIMIT 150` 안에서 충분히 빠르면 된다.
- 다만 데이터가 커질수록 `OR`, `LEFT JOIN`, `DISTINCT` 조합은 비용이 뛸 수 있다.

## C. FULLTEXT ngram 확인

반드시 확인할 것:

1. `ft_ws_search` 인덱스가 실제 생성됐는지
2. `ngram_token_size=2` 가 실제 적용됐는지
3. RDS 전환 시 custom parameter group 에 같은 값이 들어갔는지

체크 예시:

```sql
SHOW INDEX FROM welfare_services;
SHOW VARIABLES LIKE 'ngram_token_size';
```

주의:

- 로컬은 `docker-compose.yml` 실행 옵션으로 `--ngram-token-size=2` 를 준다.
- RDS 가면 코드가 아니라 파라미터 그룹으로 옮겨야 한다.

## 3. MySQL 한 대 시작안 vs EC2 + RDS 시작안

현재 프로젝트는 둘 다 가능하다.

## A. MySQL 한 대 시작안

구조:

```text
EC2 1대
  - nginx
  - spring boot
  - redis
  - mysql
    - youth_welfare
    - youth_welfare_pii
```

장점:

- 가장 싸다
- 배포/구성이 단순하다
- `ngram_token_size` 같은 설정을 직접 넣기 쉽다
- 지금 운영 데이터가 없으니 빠르게 시작 가능하다

단점:

- DB 백업/복구를 직접 책임져야 한다
- 앱과 DB가 같은 머신이라 장애 격리가 없다
- 디스크/메모리 경합이 생길 수 있다
- 개인정보가 들어가기 시작하면 운영 부담이 커진다

적합한 경우:

- 진짜 초기
- 테스트 사용자 위주
- 비용이 최우선

## B. EC2 + RDS 시작안

구조:

```text
EC2 1대
  - nginx
  - spring boot
  - redis

RDS MySQL 1대
  - youth_welfare
  - youth_welfare_pii
```

장점:

- DB 운영 부담이 크게 줄어든다
- 백업/스토리지/복구가 편하다
- 앱과 DB 리소스 경합이 줄어든다
- PII 저장을 시작할 때 더 안전하다

단점:

- 월 비용이 올라간다
- `ngram_token_size` 는 파라미터 그룹 설계가 필요하다
- 네트워크/SG 설정이 추가된다

적합한 경우:

- 실제 사용자 데이터를 받을 계획
- 분리 설계를 초반부터 유지하고 싶음
- DB 운영 리스크를 줄이고 싶음

## C. 둘 중 무엇을 추천하나

현재 프로젝트 상황을 기준으로 하면:

- 운영 데이터가 아직 없고
- 빠른 시작이 우선이면
  - `MySQL 한 대 시작안` 도 충분히 가능
- 하지만
  - 개인정보 분리 설계를 이미 하기로 했고
  - 이메일/생년월일/전화번호 저장이 들어갈 예정이면
  - 나는 `EC2 + RDS 시작안` 을 더 추천한다

이유:

- 지금 병목은 추천/검색보다 운영 안정성과 백업/복구 쪽에서 먼저 날 가능성이 크다
- RDS 비용이 추가되지만, 그 돈으로 DB 운영 리스크를 줄일 수 있다

## D. 최종 추천

### 비용 최우선

- EC2 1대
- mysql + redis + app 동거
- schema 2개

### 운영 안정성 우선

- EC2 1대
- RDS 1대
- redis 는 EC2 내부
- schema 2개

지금 질문 흐름 기준으로는 후자를 추천한다.
