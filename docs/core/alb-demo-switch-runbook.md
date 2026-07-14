# ALB Demo Switch Runbook

작성 기준일: 2026-07-13

이 문서는 평소 `EC2 1대 + RDS/ElastiCache` 운영과 시연용 `ALB + EC2 2대` 운영을 빠르게 오가기 위한 실행 절차다.

현재 도메인 등록은 가비아에 남겨두고, DNS authoritative nameserver는 Route53으로 이전했다. 따라서 운영 트래픽 전환은 Route53 `A` 레코드를 `기존 EC2 public IP` 와 `ALB Alias` 사이에서 바꾸는 방식으로 한다.

## 현재 확정 리소스

### 도메인/DNS

- Domain: `youthmoa.kr`
- DNS manager: Route53 public hosted zone
- Route53 nameservers:
  - `ns-1977.awsdns-55.co.uk`
  - `ns-359.awsdns-44.com`
  - `ns-1384.awsdns-45.org`
  - `ns-787.awsdns-34.net`
- Registrar: Gabia
- Gabia 역할: domain registration + Route53 NS delegation only

### 기존 1대 운영 경로

- Existing EC2 public IP: `3.38.21.132`
- Existing EC2 private IP: `172.31.25.51`
- Existing EC2 instance id: `i-0b8d95e454df5e0f0`
- Scheduler/ops 기준 노드: existing EC2
- `APP_SCHEDULER_ENABLED=true`

### 시연용 2대/ALB 경로

- New EC2 instance id: `i-0e8a4cc599c1148c8`
- New EC2 private IP: `172.31.44.73`
- New EC2 public IP: `13.209.8.203`
- New EC2 scheduler: disabled
- `APP_SCHEDULER_ENABLED=false`
- ALB name: `youth-welfare-alb`
- ALB DNS: `youth-welfare-alb-1121910292.ap-northeast-2.elb.amazonaws.com`
- ALB hosted zone id: `ZWKZPGTI48KDX`
- Target group: `youth-welfare-web-tg`
- Target group protocol/port: `HTTP:80`
- Health check path: `/alb-health`
- Health check success code: `200`
- HTTP listener: `80 -> HTTPS:443 redirect`
- HTTPS listener: `443 -> youth-welfare-web-tg`
- ACM certificate id: `9c6cae15-619a-47b7-9e23-e540434dd004`
- ACM certificate domains:
  - `youthmoa.kr`
  - `www.youthmoa.kr`

### Security groups

- EC2 SG: `sg-01b664b3af6ad95a2` / `youthmoa-ec2-sg`
  - `HTTP 80` from `0.0.0.0/0`
  - `HTTP 80` from ALB SG
  - `HTTPS 443` from `0.0.0.0/0`
  - `SSH 22` from operator IP / EC2 Instance Connect prefix list
- ALB SG: `sg-06ac84b1d37d48409` / `youth-welfare-alb-sg`
  - `HTTP 80` from `0.0.0.0/0`
  - `HTTPS 443` from `0.0.0.0/0`

## 운영 모드

### 평소 1대 운영

```text
Route53
  youthmoa.kr A      -> 3.38.21.132
  www.youthmoa.kr A  -> 3.38.21.132

User -> existing EC2 nginx HTTPS -> app 127.0.0.1:8082 -> RDS/ElastiCache
```

이 모드에서는 ALB를 삭제해도 된다. 새 EC2도 stop 가능하다.

### 시연/HA 운영

```text
Route53
  youthmoa.kr A Alias      -> youth-welfare-alb
  www.youthmoa.kr A Alias  -> youth-welfare-alb

User -> ALB -> target group -> existing EC2 + new EC2 -> RDS/ElastiCache
```

이 모드에서는 두 EC2가 모두 running이고, target group target 상태가 둘 다 `healthy` 여야 한다.

## 시연 모드로 전환

### 1. 새 EC2 start

AWS 콘솔에서 `i-0e8a4cc599c1148c8` 를 start한다.

새 EC2에서 확인:

```bash
curl -fsS http://127.0.0.1:8082/actuator/health
curl -i http://127.0.0.1/alb-health
grep '^APP_SCHEDULER_ENABLED=' /home/ubuntu/youth-welfare/.env.production /home/ubuntu/youth-welfare/.env.runtime.production
```

기대값:

```text
{"status":"UP"}
APP_SCHEDULER_ENABLED=false
```

### 2. ALB가 없으면 재생성

ALB를 삭제해둔 상태라면 아래 설정으로 다시 만든다.

Application Load Balancer:

- Name: `youth-welfare-alb`
- Scheme: internet-facing
- IP address type: IPv4
- VPC: `vpc-093662d691f85e844`
- Subnets:
  - `subnet-018e1db2c13f63c3c` / `ap-northeast-2b`
  - `subnet-0c7127a0767ebc2e9` / `ap-northeast-2c`
- Security group: `youth-welfare-alb-sg`

Target group:

- Name: `youth-welfare-web-tg`
- Target type: Instances
- Protocol/port: `HTTP:80`
- VPC: `vpc-093662d691f85e844`
- Protocol version: HTTP1
- Health check protocol: HTTP
- Health check path: `/alb-health`
- Health check port: traffic port
- Success code: `200`
- Healthy threshold: `2`
- Unhealthy threshold: `2`
- Timeout: `5s`
- Interval: `15s`
- Targets:
  - `i-0b8d95e454df5e0f0:80`
  - `i-0e8a4cc599c1148c8:80`

Listeners:

- `HTTP:80` -> redirect to `HTTPS:443`, status `301`
- `HTTPS:443` -> forward to `youth-welfare-web-tg`
- Certificate: ACM `youthmoa.kr` certificate `9c6cae15-619a-47b7-9e23-e540434dd004`

### 3. Target health 확인

AWS 콘솔:

```text
EC2 -> Target Groups -> youth-welfare-web-tg -> Targets
```

두 target이 모두 `healthy` 인지 확인한다.

서버에서 직접 확인:

```bash
curl -i -H 'Host: youthmoa.kr' -H 'X-Forwarded-Proto: https' http://172.31.25.51/alb-health
curl -i -H 'Host: youthmoa.kr' -H 'X-Forwarded-Proto: https' http://172.31.44.73/alb-health
```

### 4. Frontend 정적 파일 동기화 확인

ALB는 요청 단위로 target을 바꾸므로 두 EC2의 `/var/www/youth-welfare/frontend` 가 같은 배포물이어야 한다.
다르면 HTML은 한 노드에서 받고 JS asset은 다른 노드에서 받아 흰 화면이 날 수 있다.

두 노드가 같은 index asset을 내려주는지 확인:

```bash
for host in 172.31.25.51 172.31.44.73; do
  echo "target=${host}"
  curl -sS --max-time 5 \
    -H 'Host: youthmoa.kr' \
    -H 'X-Forwarded-Proto: https' \
    "http://${host}/" \
    | sed -n 's/.*src="\(\/assets\/index-[^"]*\.js\)".*/\1/p'
done
```

같지 않으면 한쪽 배포물을 다른 쪽으로 맞춘다. 배포 후 권한은 아래처럼 고정한다.

```bash
sudo find /var/www/youth-welfare/frontend -type d -exec chmod 755 {} +
sudo find /var/www/youth-welfare/frontend -type f -exec chmod 644 {} +
```

권장 방식은 한 번 빌드한 `frontend/dist` artifact를 두 노드의 `/var/www/youth-welfare/frontend` 에 같은 내용으로 배포하는 것이다.
임시 디렉터리를 `rsync` 할 때는 source 디렉터리 권한이 `700` 으로 복사될 수 있으므로, 배포 뒤에는 위 권한 보정을 반드시 실행한다.

모든 lazy chunk가 실제 JavaScript로 내려오는지 확인:

```bash
python3 - > /tmp/current-asset-list.txt <<'PY'
import glob, re, sys
path=glob.glob('/var/www/youth-welfare/frontend/assets/index-*.js')[0]
text=open(path, encoding='utf-8').read()
assets={path.split('/')[-1]}
for m in re.findall(r'(?:\.\/|assets\/)([A-Za-z0-9_.-]+\.js)', text):
    assets.add(m)
for name in sorted(assets):
    print(name)
PY

for host in 172.31.25.51 172.31.44.73; do
  bad=0
  total=0
  while IFS= read -r asset; do
    total=$((total+1))
    out="$(curl -sS --max-time 5 \
      -H 'Host: youthmoa.kr' \
      -H 'X-Forwarded-Proto: https' \
      -o /tmp/chunk.out \
      -w '%{http_code} %{content_type}' \
      "http://${host}/assets/${asset}")"
    if ! [[ "${out}" == "200 application/javascript"* ]]; then
      echo "BAD target=${host} asset=${asset} ${out}"
      bad=$((bad+1))
    fi
  done < /tmp/current-asset-list.txt
  echo "target=${host} checked=${total} bad=${bad}"
done
```

### 5. Route53 전환

Route53 -> Hosted zones -> `youthmoa.kr` 에서 아래 두 레코드를 편집한다.

`A youthmoa.kr`:

- Alias: Yes
- Route traffic to: `Application and Classic Load Balancer`
- Region: `ap-northeast-2`
- Load balancer: `dualstack.youth-welfare-alb-1121910292.ap-northeast-2.elb.amazonaws.com`
- Routing policy: Simple
- Evaluate target health: No

`A www.youthmoa.kr` 도 같은 값으로 바꾼다.

### 6. 전환 검증

DNS:

```bash
dig @ns-359.awsdns-44.com +short A youthmoa.kr
dig @ns-359.awsdns-44.com +short A www.youthmoa.kr
dig @8.8.8.8 +short A youthmoa.kr
dig @8.8.8.8 +short A www.youthmoa.kr
```

ALB IP가 보여야 한다. 예:

```text
13.209.214.158
54.116.109.58
```

HTTP/HTTPS:

```bash
curl -I http://youthmoa.kr/
curl -I https://youthmoa.kr/
curl -I https://www.youthmoa.kr/
curl -sS 'https://youthmoa.kr/api/policies?page=0&size=1' | jq -r '.success'
```

기대값:

- HTTP: `301` to HTTPS
- HTTPS root: `200`
- API success: `true`

브라우저:

- `https://youthmoa.kr`
- `https://www.youthmoa.kr`
- 정책 검색
- 정책 상세
- 로그인 페이지
- 비로그인 `/mypage` -> `/login` redirect

## 평소 1대 운영으로 복구

ALB를 삭제하기 전에 반드시 DNS를 기존 EC2로 되돌린다.

### 1. Route53 복구

Route53 -> Hosted zones -> `youthmoa.kr`:

`A youthmoa.kr`:

- Alias: No
- Value: `3.38.21.132`
- TTL: `300` 또는 `60`
- Routing policy: Simple

`A www.youthmoa.kr` 도 같은 값으로 복구한다.

### 2. 기존 EC2 경로 확인

```bash
dig @ns-359.awsdns-44.com +short A youthmoa.kr
dig @ns-359.awsdns-44.com +short A www.youthmoa.kr
curl -I https://youthmoa.kr/
curl -sS 'https://youthmoa.kr/api/policies?page=0&size=1' | jq -r '.success'
```

기대값:

```text
3.38.21.132
HTTP 200
true
```

### 3. ALB 삭제

기존 EC2 경로가 정상인 것을 확인한 뒤 삭제한다.

삭제 순서:

1. Load Balancer `youth-welfare-alb` 삭제
2. Target Group `youth-welfare-web-tg` 삭제
3. 필요하면 ALB SG `youth-welfare-alb-sg` 삭제
4. 새 EC2 `i-0e8a4cc599c1148c8` stop

주의:

- DNS가 ALB Alias인 상태에서 ALB를 삭제하면 사이트가 끊긴다.
- DNS resolver 캐시 때문에 일부 사용자는 몇 분간 이전 경로를 볼 수 있다.
- Route53 hosted zone은 삭제하지 않는다. Route53은 이후 전환 편의를 위해 계속 DNS manager로 둔다.

## 장애 대응

### 흰 화면

가장 먼저 두 target의 frontend asset 버전을 확인한다.

```bash
for host in 172.31.25.51 172.31.44.73; do
  echo "target=${host}"
  curl -sS --max-time 5 \
    -H 'Host: youthmoa.kr' \
    -H 'X-Forwarded-Proto: https' \
    "http://${host}/" \
    | sed -n 's/.*src="\(\/assets\/index-[^"]*\.js\)".*/\1/p'
done
```

다르면 정적 파일 동기화가 필요하다. 배포 디렉터리 권한도 같이 본다.

```bash
ls -ld /var/www/youth-welfare/frontend /var/www/youth-welfare/frontend/assets
ls -l /var/www/youth-welfare/frontend/index.html
```

권한 기대값:

```text
directory: drwxr-xr-x
file:      -rw-r--r--
```

### 일부 환경만 접속 안 됨

DNS 전파/캐시를 확인한다.

```bash
dig @8.8.8.8 +short A youthmoa.kr
dig @1.1.1.1 +short A youthmoa.kr
dig @ns-359.awsdns-44.com +short A youthmoa.kr
```

전파 중에는 일부 resolver가 기존 EC2 IP를 볼 수 있다. 이 기간에는 기존 EC2 `HTTPS 443 from 0.0.0.0/0` 을 닫지 않는다.

### Target unhealthy

각 target에서 직접 확인한다.

```bash
curl -i -H 'Host: youthmoa.kr' -H 'X-Forwarded-Proto: https' http://172.31.25.51/alb-health
curl -i -H 'Host: youthmoa.kr' -H 'X-Forwarded-Proto: https' http://172.31.44.73/alb-health
```

EC2 SG에 `HTTP 80 from ALB SG` 가 있는지 확인한다.

### CORS 오해 방지

ALB 임시 DNS로 브라우저 POST를 테스트하면 CORS 403이 날 수 있다.

```text
http://youth-welfare-alb-1121910292.ap-northeast-2.elb.amazonaws.com
```

앱은 운영 origin만 허용한다.

```text
https://youthmoa.kr
https://www.youthmoa.kr
```

실제 Route53 전환 뒤 사용자는 `https://youthmoa.kr` 로 접속하므로 CORS 문제는 아니다.
