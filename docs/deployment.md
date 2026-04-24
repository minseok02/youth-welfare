# 배포 가이드

현재 백엔드는 Docker Compose 기준으로 `app + db + redis` 3개 컨테이너를 사용한다.
운영 서버는 EC2 ARM 인스턴스 기준으로 정리하며, 현재 Compose 3컨테이너 동시 운영 기준 권장 최소 사양은 `t4g.medium`(2vCPU, 4GB RAM)이다.

## 0. 권장 사양과 실측 근거

2026-04-25 로컬 Docker 실측 기준:

- 대상 구성: `app + db + redis`
- `idle`
  - `app` 약 `765.6MiB`
  - `db` 약 `392.9MiB`
  - `redis` 약 `7.4MiB`
- 공개 조회 API 부하 후
  - `app` 약 `1.05GiB`
  - `db` 약 `452MiB`
- `POST /api/admin/collect/all` 실행 중
  - `app` 약 `1.12 ~ 1.126GiB`
  - `db` 약 `475 ~ 498MiB`

앱 프로세스(`youth-welfare-app`) 관측값:

- `idle` RSS 약 `695MB`, `VmHWM` 약 `707MB`
- 조회 부하 후 RSS 약 `1.08GB`, `VmHWM` 약 `1.11GB`
- 수집 배치 중 RSS 약 `1.15GB`, `VmHWM` 약 `1.18GB`

해석:

- 현재처럼 `app + db + redis`를 같은 호스트에 두면 총 메모리 사용량이 이미 `1.6GiB` 안팎까지 올라간다.
- 여기에 OS, Docker 오버헤드까지 포함하면 `t4g.small`(2GB)는 운영 여유가 부족하다.
- 발표/데모, 수집 배치, 추천 기능을 함께 고려하면 `t4g.medium` 이상을 권장한다.
- DB를 RDS로 분리한 `app-only` 구조라면 그때 `t4g.small`을 별도로 재검토할 수 있다.

## 1. 사전 준비

- `.env.example`을 복사해서 `.env` 생성
- 실제 운영 값 채우기:
  - `DB_PASSWORD`
  - `JWT_SECRET`
  - `AES_SECRET_KEY`
  - `GMAIL_USERNAME`, `GMAIL_PASSWORD`
  - `OPENAI_API_KEY`
  - `YOUTH_API_KEY`
  - `BOKJIRO_API_KEY`
  - `APP_BASE_URL`

```bash
cp .env.example .env
```

## 2. 최초 기동

```bash
docker compose -f docker-compose.yml up -d db redis
docker compose -f docker-compose.yml up -d --build app
```

- 앱 컨테이너는 내부에서 `db`, `redis` 서비스 이름으로 접속한다.
- 외부 노출 포트는 `8082 -> 8080`이다.
- DB 신규 초기화는 `backend/src/main/resources/db/schema.sql`로 처리된다.
- Compose 3컨테이너를 한 서버에서 함께 돌릴 때는 `t4g.medium` 이상을 권장한다.

## 3. 기존 DB 업그레이드

기존 운영 DB는 `schema.sql`만으로 최신화되지 않는다.
최근 누적 변경분은 아래 SQL을 먼저 적용한 뒤 앱을 재배포해야 한다.

- 상세 절차와 최신 SQL 목록: [db-migration.md](db-migration.md)

권장 순서:

1. `db`, `redis`만 기동
2. 마이그레이션 SQL 적용
3. 앱 이미지 재빌드 후 `app` 기동

## 4. 운영 확인

체크 포인트:

- `http://서버IP:8082/actuator/health`
- 로그인 / refresh / 로그아웃
- 정책 목록 필터
- 추천 북마크
- 알림 수신 거부 링크

컨테이너 확인:

```bash
docker ps
docker logs youth-welfare-app --tail 200
docker logs youth-welfare-db --tail 100
docker logs youth-welfare-redis --tail 100
```

## 5. HTTPS / 리버스 프록시

운영 기준 Nginx 설정 예시는 [`deploy/nginx/youth-welfare.conf`](../deploy/nginx/youth-welfare.conf)에 추가했다.

구성 기준:

- `80 -> 443` 리다이렉트
- `443 -> 127.0.0.1:8082` 프록시
- Let’s Encrypt 인증서 경로 사용
- `X-Forwarded-*` 헤더 전달
- HSTS 적용

적용 순서 예시:

```bash
sudo mkdir -p /var/www/certbot
sudo cp deploy/nginx/youth-welfare.conf /etc/nginx/sites-available/youth-welfare.conf
sudo ln -sf /etc/nginx/sites-available/youth-welfare.conf /etc/nginx/sites-enabled/youth-welfare.conf
sudo nginx -t
sudo systemctl reload nginx
```

인증서 발급 전에는 `ssl_certificate` 경로가 없어서 `nginx -t`가 실패할 수 있다.
이 경우 먼저 certbot으로 인증서를 발급하거나, 80포트 전용 설정으로 1차 기동 후 인증서를 발급한다.

최소 요구:

- HTTPS 종단은 Nginx 또는 ALB에서 처리
- `APP_BASE_URL`은 실제 HTTPS 도메인으로 설정
- Refresh Token `Secure` 쿠키가 동작하도록 HTTPS 환경 유지

## 6. 주의사항

- `.env`는 커밋 금지
- DB 볼륨이 이미 존재하면 `schema.sql`은 다시 자동 적용되지 않음
- 기존 운영 DB는 배포 전에 마이그레이션 SQL을 선적용해야 함
- Gmail 앱 비밀번호 미설정 시 알림 발송은 실패함
- Nginx 설정의 도메인/인증서 경로는 실제 운영 도메인에 맞게 수정해야 함
- 현재 Compose 구성(`app + db + redis`)을 한 EC2에 함께 올릴 경우 `t4g.small`은 비권장
