# 배포 가이드

현재 백엔드는 Docker Compose 기준으로 `app + db + redis` 3개 컨테이너를 사용한다.
운영 서버는 EC2 `t4g.large`(ARM) 기준으로 정리한다.

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

## 3. 기존 DB 업그레이드

기존 운영 DB는 `schema.sql`만으로 최신화되지 않는다.
최근 누적 변경분은 아래 SQL을 먼저 적용한 뒤 앱을 재배포해야 한다.

- 파일: [`backend/src/main/resources/db/migration/V2026_04_17_01__recent_schema_updates.sql`](../backend/src/main/resources/db/migration/V2026_04_17_01__recent_schema_updates.sql)
- 상세 절차: [`docs/db-migration.md`](db-migration.md)

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
