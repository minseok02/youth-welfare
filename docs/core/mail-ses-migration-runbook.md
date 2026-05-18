# AWS SES SMTP 전환 런북

## 목적

현재 메일 발송은 Spring `JavaMailSender` 기반 SMTP 구조입니다.  
코드는 이미 provider-neutral 하므로, AWS SES 전환은 코드보다 운영 설정과 검증 절차가 핵심입니다.

## 전환 대상

- 비밀번호 재설정 메일
- 이메일 인증 메일
- 추천/마감 임박 알림 메일

## 전환 이유

- Google Workspace 구독비 대신 건당 과금 구조를 사용
- 추천/마감 알림처럼 잦은 발송에 더 자연스러운 운영 모델
- SMTP 기반이라 현재 코드 변경이 거의 없음

## 사전 준비

1. AWS SES 사용할 리전 결정
   - 예: `ap-northeast-2`
2. 발신 도메인 준비
   - 예: `example.com`
3. SES에서 domain identity 생성
4. DNS에 SES 검증 레코드 추가
   - DKIM
   - MAIL FROM / SPF가 필요하면 해당 레코드도 추가
5. SES SMTP credentials 생성
   - AWS access key와 다름

공식 문서:
- SMTP credentials: https://docs.aws.amazon.com/ses/latest/dg/smtp-credentials.html
- SMTP endpoint: https://docs.aws.amazon.com/ses/latest/dg/smtp-connect.html
- DKIM: https://docs.aws.amazon.com/ses/latest/dg/send-email-authentication-dkim.html
- SPF: https://docs.aws.amazon.com/ses/latest/dg/send-email-authentication-spf.html

## 서버 env 기준

```env
MAIL_HOST=email-smtp.ap-northeast-2.amazonaws.com
MAIL_PORT=587
MAIL_USERNAME=<ses smtp username>
MAIL_PASSWORD=<ses smtp password>
MAIL_PROVIDER=aws-ses-smtp
MAIL_FROM_ADDRESS=no-reply@example.com
MAIL_REPLY_TO=support@example.com
MAIL_SMTP_AUTH=true
MAIL_SMTP_STARTTLS_ENABLE=true
```

기존 `GMAIL_USERNAME`, `GMAIL_PASSWORD` 는 제거 가능하지만, 전환 직전 rollback 대비를 위해 한 턴 정도 병행 보관해도 됩니다.

## 서버 반영 순서

1. 서버 env에 `MAIL_*` 추가
2. 앱 재빌드/재기동
3. `SmtpSmokeTest` 로 실제 발송 검증
4. 인증 메일 또는 비밀번호 재설정 메일 실제 API smoke

## SMTP smoke

```bash
cd backend
MAIL_HOST=email-smtp.ap-northeast-2.amazonaws.com \
MAIL_PORT=587 \
MAIL_USERNAME='<ses smtp username>' \
MAIL_PASSWORD='<ses smtp password>' \
MAIL_PROVIDER=aws-ses-smtp \
MAIL_FROM_ADDRESS='no-reply@example.com' \
MAIL_REPLY_TO='support@example.com' \
SMTP_SMOKE_TO='receiver@example.com' \
RUN_SMTP_SMOKE=true \
./gradlew test --tests com.example.welfare.notification.gateway.SmtpSmokeTest --rerun-tasks
```

## API smoke 권장

- 비밀번호 재설정 요청 1회
- 이메일 인증 요청 1회

둘 다 실제 수신까지 확인합니다.

## 판정 기준

- `SmtpSmokeTest` 성공
- 발신자/Reply-To 헤더 정상
- 스팸함이 아닌 inbox 도착 여부 확인
- 앱 로그에 `EmailClient 발송 성공 provider=aws-ses-smtp` 확인

## rollback

SES 전환 실패 시 아래만 되돌리면 됩니다.

```env
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=<gmail account>
MAIL_PASSWORD=<gmail app password>
MAIL_PROVIDER=gmail-smtp
MAIL_FROM_ADDRESS=<gmail address>
MAIL_REPLY_TO=<gmail address>
```

코드 rollback은 필요 없습니다.
