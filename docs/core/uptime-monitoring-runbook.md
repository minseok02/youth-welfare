# uptime monitoring runbook

문서군 진입점: [system-docs-index.md](./system-docs-index.md)

## 목적

운영 서버가 로그인 세션이나 브라우저 접속과 무관하게 계속 살아 있는지 확인하고, 장애가 이어지면 외부 알림을 받는 경로를 고정합니다.

구성은 3단계입니다.

1. Docker healthcheck: 컨테이너 내부에서 `/actuator/health` 가 `UP` 인지 확인
2. 서버 watchdog cron: EC2 내부에서 2분마다 `http://127.0.0.1:8082/actuator/health` 확인, 실패 누적 시 app 컨테이너 restart
3. 외부 알림: Healthchecks.io ping 미수신 알림 또는 Route53 health check + CloudWatch alarm

## 서버 watchdog 설치

Healthchecks.io에서 새 check를 만들고 ping URL을 복사합니다. 그 다음 운영 서버에서 아래를 실행합니다.

```bash
cd /home/ubuntu/youth-welfare
bash deploy/ops/configure-healthchecks-url.sh 'https://hc-ping.com/<uuid>'
bash deploy/ops/install-basic-ops-cron.sh
```

설정 파일은 기본적으로 아래 위치에 저장됩니다.

```bash
~/.config/youth-welfare/ops.env
```

watchdog은 이 파일의 `HEALTHCHECKS_PING_URL` 을 읽습니다. 필요하면 같은 파일에 webhook도 추가할 수 있습니다.

```bash
ALERT_WEBHOOK_URL='https://example.com/webhook'
```

## 동작 방식

`deploy/ops/app-watchdog.sh` 는 아래 순서로 동작합니다.

1. `APP_HEALTH_URL` 호출
2. JSON 응답의 `status` 가 `UP` 이면 fail count를 0으로 초기화하고 Healthchecks.io 성공 ping 전송
3. 실패하면 fail count를 올리고 Healthchecks.io `/fail` ping 전송
4. 실패가 `FAIL_THRESHOLD` 이상이면 `docker compose restart app` 실행
5. 재시작은 기본 10분에 한 번만 허용

주요 환경변수:

- `ROOT_DIR`: repo 경로, 기본 `/home/ubuntu/youth-welfare`
- `OPS_ENV_FILE`: watchdog secret/env 파일
- `ENV_FILE`: compose env 파일, 기본 `.env.production`
- `COMPOSE_FILE`: compose 파일, 기본 `docker-compose.prod.yml`
- `COMPOSE_SERVICE`: restart 대상 서비스, 기본 `app`
- `APP_HEALTH_URL`: 내부 health URL, 기본 `http://127.0.0.1:8082/actuator/health`
- `FAIL_THRESHOLD`: restart 전 실패 횟수, 기본 `2`
- `MIN_RESTART_INTERVAL_SECONDS`: restart 최소 간격, 기본 `600`

수동 점검:

```bash
ROOT_DIR=/home/ubuntu/youth-welfare bash deploy/ops/app-watchdog.sh
tail -n 50 /var/log/youth-welfare/ops/app-watchdog.log
```

## AWS 알람

EC2 자체 문제는 아래로 SNS + CloudWatch alarm을 만듭니다.

```bash
ALERT_EMAIL='ops@example.com' \
AWS_REGION='ap-northeast-2' \
bash deploy/ops/create-aws-basic-alarms.sh
```

공개 도메인 기준 외부 uptime 확인은 Route53 health check를 씁니다. Route53 health check metric alarm은 `us-east-1` 리전에 생성됩니다.

```bash
ALERT_EMAIL='ops@example.com' \
DOMAIN='youthmoa.kr' \
PATH_TO_CHECK='/' \
bash deploy/ops/create-route53-uptime-healthcheck.sh
```

SNS 이메일 구독 확인 메일을 눌러야 실제 알림이 옵니다.

비용 사고 방지용 billing alarm은 us-east-1 CloudWatch Billing metric으로 만듭니다.

```bash
THRESHOLD_USD=5 bash deploy/ops/create-aws-billing-alarm.sh
```

Billing metric은 몇 시간 단위로 갱신되므로 생성 직후에는 `INSUFFICIENT_DATA` 일 수 있습니다. 알림은 기존 `youth-welfare-ops-alerts` SNS topic을 사용합니다.

## IAM 권한 축소

초기 구성 때 `CloudWatchFullAccess`, `AmazonSNSFullAccess`, `AmazonRoute53FullAccess` 같은 관리형 정책을 임시로 붙였다면 알람 생성 후 아래 파일 기준의 inline policy로 줄입니다.

```text
deploy/ops/aws-ops-monitor-role-policy.json
```

콘솔 적용 순서:

1. IAM -> 역할 -> `youth-welfare-ops-monitor-role`
2. 권한 추가 -> 인라인 정책 생성
3. JSON 탭에 `deploy/ops/aws-ops-monitor-role-policy.json` 내용을 붙여넣기
4. 정책 이름: `youth-welfare-ops-monitor-inline`
5. 저장 후 기존 FullAccess 관리형 정책 제거

서버에서 검증:

```bash
aws sts get-caller-identity --region ap-northeast-2
aws cloudwatch describe-alarms --region ap-northeast-2 --alarm-names youth-welfare-ec2-status-check-failed
aws cloudwatch describe-alarms --region us-east-1 --alarm-names youth-welfare-route53-uptime-unhealthy
aws route53 get-health-check --health-check-id 5645ef15-90f4-4b7d-899f-52879da4f61d
```

## 확인 기준

설치 후 아래를 확인합니다.

```bash
crontab -l | sed -n '/youth-welfare basic ops/,/<<< youth-welfare basic ops/p'
curl -fsS http://127.0.0.1:8082/actuator/health
tail -n 20 /var/log/youth-welfare/ops/app-watchdog.log
```

기대값:

- cron에 `app-watchdog.sh` 가 2분 주기로 등록됨
- health 응답이 `{"status":"UP"}` 계열
- 로그에 `health=UP`
- Healthchecks.io dashboard의 last ping이 갱신됨

## 로그 관리

watchdog과 docker prune 로그는 `/var/log/youth-welfare/ops/*.log` 에 쌓입니다. 운영 서버에는 아래로 logrotate 설정을 설치합니다.

```bash
bash deploy/ops/install-ops-logrotate.sh
```

기본 정책:

- daily 회전
- 14개 보관
- 압축
- 빈 로그는 회전하지 않음

장애 알림 경로 테스트는 운영 트래픽 시간대를 피해서 수행합니다. 실제 컨테이너를 멈추는 테스트 대신 아래처럼 잘못된 URL과 임시 상태/로그 디렉터리를 주면 재시작 없이 실패 카운트 경로만 확인할 수 있습니다.

```bash
tmp_state="$(mktemp -d)"
tmp_log="$(mktemp -d)"
STATE_DIR="${tmp_state}" \
LOG_DIR="${tmp_log}" \
APP_HEALTH_URL='http://127.0.0.1:1/health' \
FAIL_THRESHOLD=99 \
HEALTHCHECKS_PING_URL='' \
ALERT_WEBHOOK_URL='' \
bash deploy/ops/app-watchdog.sh
cat "${tmp_log}/app-watchdog.log"
rm -rf "${tmp_state}" "${tmp_log}"
```

실제 Healthchecks.io 알림까지 확인할 때만 `HEALTHCHECKS_PING_URL` override를 빼고 실행합니다.
