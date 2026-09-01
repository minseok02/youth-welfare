# 인프라 삭제 실행 체크리스트

기준 시점은 `2026-09-01` 이다.

이 문서는 teardown 직전에 실제로 무엇을 보존하고, 무엇을 순서대로 삭제하고, 삭제 후 무엇을 기록해야 하는지에만 집중한다.

상태 고정 문서는 [restart-and-teardown-handoff-2026-09-01.md](./restart-and-teardown-handoff-2026-09-01.md) 를 본다.

## 1. 고정 기준점

- branch: `refactor/admin-dashboard-sections`
- handoff commit: `94d0fea8`
- preserved snapshot commit: `338bcd86`
- primary EC2: `i-0b8d95e454df5e0f0` (`youthmoa-prod-ec2`)
- secondary EC2: `i-0e8a4cc599c1148c8` (`youthmoa-prod-ec2-2`)
- RDS: `youth-welfare-prod-db`
- ElastiCache Valkey: `youth-welfare-prod-redis-valkey`
- ALB: `youth-welfare-alb`
- target group: `youth-welfare-web-tg`
- hosted zone: `youthmoa.kr` / `Z02264322U3S9LJ356G2Y`

## 2. 삭제 전에 반드시 보존할 것

### 필수

- Git 기준점
  - commit `94d0fea8`
  - commit `338bcd86`
  - tag `teardown-ready-2026-09-01`
- 운영 env 파일
  - `/home/ubuntu/youth-welfare/.env.production`
  - `/home/ubuntu/youth-welfare/.env.runtime.production`
  - `/home/ubuntu/.config/youth-welfare/ops.env`
- edge 설정
  - `/etc/nginx/sites-available/youth-welfare`

### 선택

- DB 복구 수단
  - final manual RDS snapshot
  - `pg_dump` logical backup
- `/var/log/youth-welfare` 에서 필요한 로그만 추출
- `/home/ubuntu/youth-welfare/tmp` 에서 최종 보고/검증 근거만 선별 보존
- Route53 record inventory export

## 3. 삭제 전에 내가 이미 정리한 것

- suspended runtime 상태와 AWS 식별자를 handoff 문서에 기록했다.
- `env.production.example` 에 실제 운영에서 쓰던 누락 key를 보강했다.
- restart 경로를 local-only / existing AWS / full rebuild 기준으로 정리했다.
- worktree 정리본은 원격 브랜치에 push 된 상태다.

즉 지금 남은 핵심은 코드 수정이 아니라 env 보존 확인과 실제 AWS 자원 삭제다.

## 4. 삭제 전에 사람이 직접 해야 하는 일

### 4-1. Git tag 확인

```bash
git tag -l 'teardown-ready-2026-09-01'
git rev-parse teardown-ready-2026-09-01
```

기대값:

- tag 이름이 보여야 한다.
- tag가 `94d0fea8` 를 가리켜야 한다.

### 4-2. 운영 secret 외부 백업

아래 파일은 repo 밖의 안전한 저장소에 따로 보관한다.

- `/home/ubuntu/youth-welfare/.env.production`
- `/home/ubuntu/youth-welfare/.env.runtime.production`
- `/home/ubuntu/.config/youth-welfare/ops.env`
- `/etc/nginx/sites-available/youth-welfare`

체크 기준:

- 파일이 열리는지 확인
- 값이 비어 있지 않은지 확인
- 백업 위치를 기록

### 4-3. DB 보존 여부 최종 확정

현재 프로젝트 결정:

1. 실사용자 없음
2. 운영 데이터 보존 우선순위 낮음
3. RDS final snapshot 생성 없이 삭제 가능
4. `pg_dump` 도 생략 가능

삭제 전에 확인할 것:

1. 꼭 남겨야 할 테스트/데모 데이터가 없는지 마지막 확인
2. 과거 DB 상태 그대로 재현할 요구가 없는지 확인
3. 삭제 후에는 migration + 수집/테스트 데이터 재생성으로 다시 시작한다는 점을 합의

아래는 DB를 남기고 싶을 때만 참고한다.

- final manual RDS snapshot
- optional `pg_dump`

AWS 참고:

- 수동 스냅샷은 DB 삭제 후에도 남는다.
- 스냅샷 restore 는 새 DB 인스턴스를 만든다.

- https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/USER_WorkingWithAutomatedBackups.html
- https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/USER_RestoreFromSnapshot.html

### 4-4. 도메인 유지 여부 결정

둘 중 하나를 먼저 정한다.

- `youthmoa.kr` 를 나중에 다시 쓸 예정이면 Route53/ACM 유지
- 도메인 운영도 완전히 접을 거면 Route53/ACM까지 정리

이 결정을 먼저 하지 않으면 삭제 범위가 애매해진다.

## 5. 삭제 당일 실행 순서

### 5-1. 런타임 종료

1. cron / watchdog / 자동 복구성 작업 중지
2. app container 중지
3. nginx 종료 여부 결정

현재 기준 public frontend 는 `200`, `/api` 는 RDS 정지로 `502` 상태였으므로, 앱 유지 이점은 거의 없다.

### 5-2. AWS 자원 삭제 순서

1. secondary EC2 `i-0e8a4cc599c1148c8` 삭제
2. ALB listener 삭제
3. ALB `youth-welfare-alb` 삭제
4. target group `youth-welfare-web-tg` 삭제
5. ElastiCache `youth-welfare-prod-redis-valkey` 삭제
6. primary EC2 `i-0b8d95e454df5e0f0` 삭제
7. RDS `youth-welfare-prod-db` 삭제
   - 현재 결정 기준으로 final snapshot 없이 삭제 가능
8. 필요 시 security group / subnet group 정리

주의:

- DB를 보존하지 않는 방향이면 final snapshot 없이 삭제해도 된다.
- Route53 alias record가 ALB를 가리키고 있으므로 ALB 삭제 후 DNS 정리 여부를 결정한다.
- Redis는 snapshot retention이 `0` 이었으므로 별도 보존 수단이 없다. 캐시 데이터는 복구 대상이 아니다.

### 5-3. 삭제 대상에서 보통 제외할 것

- GitHub repository
- docs/
- 도메인을 재사용할 계획이 있으면 Route53 hosted zone
- 같은 이유로 ACM certificate metadata

## 6. 삭제 후 바로 남길 기록

아래 값은 문서 또는 별도 ops 메모에 적는다.

- env 백업 저장 위치
- 삭제 완료 날짜
- 실제 삭제한 리소스 이름/ID
- 유지한 리소스 이름/ID

최소 예시:

```text
date: 2026-09-01
git_tag: teardown-ready-2026-09-01
handoff_commit: 94d0fea8
snapshot_commit: 338bcd86
rds_final_snapshot: skipped
pg_dump_backup: skipped
env_backup_location: <fill>
route53_kept: yes|no
acm_kept: yes|no
```

## 7. 나중에 다시 만들 때 시작 순서

1. repo checkout: `94d0fea8`
2. preserved env 복원
3. 새 RDS 생성
4. migration/bootstrap 실행
5. 필요하면 수집 또는 테스트 데이터 재생성
6. Redis 생성
7. EC2 생성
8. nginx/frontend 복원
9. app 기동
10. ALB / Route53 연결
11. `/alb-health` 와 주요 API smoke 확인

상세 절차는 아래 문서를 기준으로 본다.

- [restart-and-teardown-handoff-2026-09-01.md](./restart-and-teardown-handoff-2026-09-01.md)
- [production-ops-quickstart.md](./production-ops-quickstart.md)

## 8. 마지막 판단 기준

아래 4개가 충족되면 삭제 진행 가능으로 본다.

- tag `teardown-ready-2026-09-01` 확인
- env / nginx 설정 외부 백업 완료
- DB 보존 포기 결정 확인
- 삭제 후 재생성 문서를 처음부터 읽었을 때 막히는 빈칸이 없음
