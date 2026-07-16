# AWS instance specs

작성 기준일: 2026-07-12

이 문서는 제출/시연용 HA 구성과 제출 후 1대 비용 절감 운영을 위해 EC2 web node 스펙을 고정한다.
비밀번호, API key, RDS/Redis endpoint 전체값은 이 문서에 남기지 않는다.

## 운영 모드

| 모드 | 목적 | EC2 target | scheduler |
| --- | --- | --- | --- |
| 제출/시연 HA | ALB 뒤 web node 2대와 장애 대응 증명 | EC2-1, EC2-2 running | EC2-1 only |
| 제출 후 저비용 | 비용 절감 운영 | EC2-1 running, EC2-2 stopped/deregistered | EC2-1 only |
| 재확장 | 사용자 증가 또는 운영 경험 | EC2-1, EC2-2 running | EC2-1 only |

## EC2 web nodes

| 항목 | EC2-1 current | EC2-2 launch 기준 |
| --- | --- | --- |
| Name tag | `youthmoa-prod-ec2` | `youthmoa-prod-ec2-2` |
| 역할 | primary web node, scheduler node | secondary web node, no scheduler |
| Instance type | `t3.medium` | `t3.medium` |
| vCPU / memory | 2 vCPU / 4 GiB class | 2 vCPU / 4 GiB class |
| OS | Ubuntu 24.04 LTS | Ubuntu Server 24.04 LTS |
| AMI | `ami-0765f9741eedf9c7b` | `ami-0e4ab31f1847c850c` |
| Architecture | x86_64 | x86_64 |
| VPC | `vpc-093662d691f85e844` | `vpc-093662d691f85e844` |
| AZ | `ap-northeast-2b` | `ap-northeast-2c` |
| Subnet | `subnet-018e1db2c13f63c3c` | `subnet-0c7127a0767ebc2e9` |
| Security group | `youthmoa-ec2-sg` | `youthmoa-ec2-sg` |
| Key pair | 기존 운영 key | `youthmoa-new-key` |
| Public IPv4 auto-assign | current public IPv4 attached | enabled |
| Root volume | 20 GiB gp3, 3000 IOPS | 20 GiB gp3, 3000 IOPS |
| EBS encryption | disabled | disabled, EC2-1과 맞춤 |
| Termination protection | enabled | enabled |
| Stop protection | disabled | disabled |
| IMDS | v2 only | v2 only |
| Metadata hop limit | 2 | 2 |
| Docker role | `docker-compose.prod.elasticache.yml` app | `docker-compose.prod.elasticache.yml` app |
| Edge role | current direct HTTPS nginx, ALB 전환 후 HTTP target | ALB HTTP target nginx |
| App bind | host `127.0.0.1:8082` -> container `8080` | host `127.0.0.1:8082` -> container `8080` |
| Runtime DB | RDS PostgreSQL 16 | same RDS PostgreSQL 16 |
| Runtime Redis | ElastiCache Valkey primary endpoint | same ElastiCache Valkey primary endpoint |
| `APP_SCHEDULER_ENABLED` | `true` | `false` |

EC2-1 current snapshot:

- private/public IPv4: AWS console 또는 instance metadata에서 확인한다. public IPv4는 stop/start 또는 재할당 시 바뀔 수 있으므로 문서 기준값으로 고정하지 않는다.
- root filesystem: 19 GiB mounted at `/`
- latest checked root usage: 8.2 GiB used / 11 GiB available / 45% after runtime artifact/cache cleanup
- memory visible to OS: 3.7 GiB

## Security group target state

현재 EC2-2 bootstrap 전에는 `youthmoa-ec2-sg` 에 direct HTTP/HTTPS 규칙이 남아 있을 수 있다.
ALB 전환 완료 뒤의 목표 inbound는 아래와 같다.

| Port | Source | 목적 |
| --- | --- | --- |
| TCP 80 | ALB security group | ALB -> nginx target traffic |
| TCP 22 | operator fixed IP | SSH 운영 접속 |
| TCP 22 | EC2 Instance Connect prefix list | AWS console connect path |

전환 안정화 뒤 제거할 규칙:

- TCP 80 from `0.0.0.0/0`
- TCP 443 from `0.0.0.0/0`

Rollback/debug 기간에는 direct HTTP/HTTPS 규칙을 임시 유지할 수 있지만, 제출용 HA 완료 기준은 ALB SG에서만 EC2 HTTP target에 접근하는 것이다.

## Runtime env 기준

EC2-1:

```env
APP_SCHEDULER_ENABLED=true
DB_POOL_MAX_SIZE=5
DB_POOL_MIN_IDLE=0
DB_APP_PII_POOL_MAX_SIZE=2
DB_APP_PII_POOL_MIN_IDLE=0
DB_ADMIN_RO_POOL_MAX_SIZE=1
DB_ADMIN_RO_POOL_MIN_IDLE=0
DB_NOTIFICATION_PII_RO_POOL_MAX_SIZE=1
DB_NOTIFICATION_PII_RO_POOL_MIN_IDLE=0
```

EC2-2:

```env
APP_SCHEDULER_ENABLED=false
DB_POOL_MAX_SIZE=5
DB_POOL_MIN_IDLE=0
DB_APP_PII_POOL_MAX_SIZE=2
DB_APP_PII_POOL_MIN_IDLE=0
DB_ADMIN_RO_POOL_MAX_SIZE=1
DB_ADMIN_RO_POOL_MIN_IDLE=0
DB_NOTIFICATION_PII_RO_POOL_MAX_SIZE=1
DB_NOTIFICATION_PII_RO_POOL_MIN_IDLE=0
```

EC2-2는 app/API traffic을 받을 수 있지만 자동 scheduler는 실행하지 않는다.
수동 admin/API 실행 경로는 `APP_SCHEDULER_ENABLED=false` 로 막지 않는다.

## Disk 운영 메모

제출용 EC2-1/EC2-2는 기존 운영 서버와 맞춰 root volume 20 GiB로 둔다.
Docker build cache와 image가 쌓이면 여유가 빠르게 줄 수 있으므로 배포 전후 아래를 확인한다.

```bash
df -h /
docker system df
```

정리 명령:

```bash
DRY_RUN=true FORCE=true bash deploy/ops/cleanup-runtime-disk-artifacts.sh
DRY_RUN=false FORCE=true bash deploy/ops/cleanup-runtime-disk-artifacts.sh
```

정기 정리 cron과 보존 정책은 [runtime-disk-cleanup-runbook.md](./runtime-disk-cleanup-runbook.md) 를 기준으로 한다.
이 정리 스크립트는 `tmp/performance`, npm cache, Docker builder cache만 대상으로 하고 Docker volumes는 지우지 않는다.

root disk 사용률이 80% 이상으로 유지되면 다음 증설 또는 재생성 시 30 GiB gp3를 우선 검토한다.

2026-07-16 follow-up:

- primary runtime disk cleanup cron installed locally.
- secondary root disk checked at `11G / 19G`, `59%` used; app remained `healthy`.
- secondary had `5.616GB` Docker build cache, so the same cleanup script/cron should be synced there.

## 관련 문서

- [ALB 기반 EC2 2대 전환 계획](./alb-multi-ec2-rollout-plan.md)
- [HA 전환 전후 측정 계획](./ha-before-after-measurement-plan.md)
- [EC2 + RDS Deployment](../deployment.md)
