# ALB + Route53 Cutover Record 2026-07-13

이 문서는 2026-07-13에 수행한 새 EC2 연결, ALB 구성, Route53 전환, 장애 조치 결과를 기록한다.

## 목적

- 기존 `EC2 1대 + RDS + ElastiCache` 운영을 유지한다.
- 시연 시 `ALB + EC2 2대` 로 전환할 수 있게 한다.
- 시연 후 비용 절감을 위해 다시 `EC2 1대` 운영으로 복구할 수 있게 한다.

## 최종 상태

2026-07-13 작업 종료 시점의 운영 구조:

```text
User
  -> Route53 public hosted zone
      -> A Alias youthmoa.kr / www.youthmoa.kr
          -> ALB youth-welfare-alb
              -> target group youth-welfare-web-tg
                  -> EC2-1 nginx:80 -> app 127.0.0.1:8082
                  -> EC2-2 nginx:80 -> app 127.0.0.1:8082
                      -> RDS PostgreSQL
                      -> ElastiCache Valkey
```

현재 실제 트래픽은 ALB를 탄다.

## 리소스

### EC2

| 구분 | Instance ID | Private IP | Public IP | AZ | Scheduler |
| --- | --- | --- | --- | --- | --- |
| EC2-1 기존 운영 | `i-0b8d95e454df5e0f0` | `172.31.25.51` | `3.38.21.132` | `ap-northeast-2b` | enabled |
| EC2-2 신규 web | `i-0e8a4cc599c1148c8` | `172.31.44.73` | `13.209.8.203` | `ap-northeast-2c` | disabled |

EC2-2 runtime:

- OS: Ubuntu 24.04.4 LTS
- Instance type: `t3.medium`
- Docker: 29.1.3
- Docker Compose: 2.40.3
- nginx: 1.24.0
- psql: 16.14
- node: v20.20.2
- npm: 10.8.2

### ALB

- Name: `youth-welfare-alb`
- DNS: `youth-welfare-alb-1121910292.ap-northeast-2.elb.amazonaws.com`
- Scheme: internet-facing
- IP type: IPv4
- VPC: `vpc-093662d691f85e844`
- Subnets:
  - `subnet-018e1db2c13f63c3c` / `ap-northeast-2b`
  - `subnet-0c7127a0767ebc2e9` / `ap-northeast-2c`
- SG: `sg-06ac84b1d37d48409` / `youth-welfare-alb-sg`

Listeners:

- `HTTP:80` -> `HTTPS:443` redirect, status `301`
- `HTTPS:443` -> `youth-welfare-web-tg`

### Target group

- Name: `youth-welfare-web-tg`
- Target type: Instances
- Protocol/port: `HTTP:80`
- Health check path: `/alb-health`
- Success code: `200`
- Interval: `15s`
- Timeout: `5s`
- Healthy threshold: `2`
- Unhealthy threshold: `2`
- Targets:
  - `i-0b8d95e454df5e0f0:80`
  - `i-0e8a4cc599c1148c8:80`

### ACM

- Certificate ID: `9c6cae15-619a-47b7-9e23-e540434dd004`
- ARN: `arn:aws:acm:ap-northeast-2:857721769929:certificate/9c6cae15-619a-47b7-9e23-e540434dd004`
- Domains:
  - `youthmoa.kr`
  - `www.youthmoa.kr`
- Status: issued
- Validation: DNS CNAME through Gabia, then copied to Route53

Validation records:

| Name | Type | Value |
| --- | --- | --- |
| `_4c65a3826543ae811c9032a7ddc98577` | CNAME | `_6ece8ec2c3b047853abd2cf28e7ca990.jkddzztszm.acm-validations.aws` |
| `_c7110a6dc614caae86758a0801d30c23.www` | CNAME | `_aa5388951edfd6f871c3edfed9b45b4b.jkddzztszm.acm-validations.aws` |

### Route53

Route53 public hosted zone was created for `youthmoa.kr`.

Nameservers delegated from Gabia:

- `ns-1977.awsdns-55.co.uk`
- `ns-359.awsdns-44.com`
- `ns-1384.awsdns-45.org`
- `ns-787.awsdns-34.net`

Important Route53 records at cutover:

- `A youthmoa.kr` Alias -> `dualstack.youth-welfare-alb-1121910292.ap-northeast-2.elb.amazonaws.com`
- `A www.youthmoa.kr` Alias -> `dualstack.youth-welfare-alb-1121910292.ap-northeast-2.elb.amazonaws.com`
- ACM CNAME 2개
- SES DKIM CNAME 3개
- `MX mail`
- `TXT mail`
- `TXT _dmarc`

Gabia nameserver change initially failed when Route53 NS values were entered with a trailing dot. Gabia accepted the same values without the trailing dot.

## 수행 내용

### 1. 신규 EC2 준비

- Repo cloned to `/home/ubuntu/youth-welfare`
- Commit checked out: `ee08c19370f5a43b802fbac7ba52d77255ddf85f`
- `.env.production`, `.env.runtime.production` copied from existing EC2 through base64 transfer
- Both env files set:

```env
APP_SCHEDULER_ENABLED=false
```

- RDS runtime privilege verification passed
- Redis `PING` passed
- app container started from `docker-compose.prod.elasticache.yml`
- app health:

```text
http://127.0.0.1:8082/actuator/health -> {"status":"UP"}
```

### 2. 신규 EC2 nginx

- `deploy/nginx/youth-welfare.alb.conf` installed as `/etc/nginx/sites-available/youth-welfare`
- `/etc/nginx/sites-enabled/youth-welfare` symlink enabled
- default nginx site disabled
- `/alb-health` returns app actuator health
- nginx active and enabled

### 3. 기존 EC2 nginx 보정

기존 EC2는 직접 HTTPS 운영용 nginx 설정만 있었기 때문에 `/alb-health`가 처음에는 `301` redirect였다.

기존 EC2 nginx를 겸용으로 보정했다.

- Direct HTTPS: `https://youthmoa.kr` 유지
- Direct HTTP: HTTPS redirect 유지
- ALB health: `GET /alb-health` -> app actuator health `200`
- ALB forwarded request: `X-Forwarded-Proto: https` 일 때 HTTP:80에서 frontend/API 처리

검증:

```bash
curl -i -H 'Host: youthmoa.kr' http://127.0.0.1/alb-health
curl -I https://youthmoa.kr/
```

### 4. ALB 생성

- ALB SG created
- Target group created
- New EC2 first registered and confirmed healthy
- Existing EC2 registered after nginx `/alb-health` 보정
- Both targets reached healthy
- HTTP listener created, then changed to HTTPS redirect
- HTTPS listener created with ACM certificate

### 5. Route53 이전

처음에는 Route53 hosted zone에 기존 Gabia DNS records를 복사하고, root/www A records는 기존 EC2 IP `3.38.21.132` 로 유지했다.

그 뒤 Gabia nameservers를 Route53 NS 4개로 변경했다.

확인:

```bash
dig +trace NS youthmoa.kr
dig @ns-359.awsdns-44.com +short A youthmoa.kr
dig @ns-359.awsdns-44.com +short A www.youthmoa.kr
```

Route53 위임이 확인된 뒤 `A youthmoa.kr`, `A www.youthmoa.kr` 를 ALB Alias로 변경했다.

### 6. 장애와 해결

#### 증상

폰/시크릿창에서 흰 화면.

#### 원인

두 EC2의 frontend 정적 파일 버전이 달랐다.

```text
기존 EC2 index.html -> /assets/index-DZy1JXjP.js
신규 EC2 index.html -> /assets/index-DveppKF9.js
```

ALB가 요청별로 target을 나누면서, HTML은 한 노드에서 받고 JS asset은 다른 노드에서 받는 경우가 생겼다. 없는 asset 경로가 SPA fallback으로 `index.html` 을 반환해 브라우저 module script 로딩이 실패했다.

#### 조치

기존 EC2 frontend 배포물을 신규 EC2가 내려주는 버전과 맞췄다.

작업 중 임시 디렉터리 권한 `700` 이 그대로 복사되어 nginx가 `Permission denied` 로 500을 냈고, 아래 권한으로 보정했다.

```bash
sudo find /var/www/youth-welfare/frontend -type d -exec chmod 755 {} +
sudo find /var/www/youth-welfare/frontend -type f -exec chmod 644 {} +
```

검증:

```text
target=172.31.25.51 checked=29 bad=0
target=172.31.44.73 checked=29 bad=0
https://youthmoa.kr/api/policies?page=0&size=1 -> success true
```

## 최종 검증

DNS:

```text
Route53 authoritative:
youthmoa.kr      -> 13.209.214.158, 54.116.109.58
www.youthmoa.kr  -> 13.209.214.158, 54.116.109.58
```

HTTP/HTTPS:

```text
http://youthmoa.kr/  -> 301 HTTPS redirect
https://youthmoa.kr/ -> 200
https://www.youthmoa.kr/ -> 200
```

API:

```text
https://youthmoa.kr/api/policies?page=0&size=1 -> success true
```

Frontend:

- Phone browser normal
- Incognito browser normal
- Both targets serve `/assets/index-DveppKF9.js`
- Lazy chunks checked on both targets: 29, bad 0

## 남은 운영 주의사항

1. 현재는 ALB + EC2 2대 운영 상태라 ALB 비용과 새 EC2 비용이 발생한다.
2. 비용 절감 모드로 돌아갈 때는 반드시 Route53을 기존 EC2 IP로 되돌린 뒤 ALB를 삭제한다.
3. Route53 hosted zone은 삭제하지 않는다. 이후 전환 편의를 위해 계속 DNS manager로 둔다.
4. 새 EC2는 항상 `APP_SCHEDULER_ENABLED=false` 로 유지한다.
5. 프론트 배포 시 두 EC2의 `/var/www/youth-welfare/frontend` 를 같은 artifact로 맞춘다.
6. ALB 임시 DNS origin에서 POST CORS가 403이면 정상이다. 실제 운영 origin은 `https://youthmoa.kr` 이다.

