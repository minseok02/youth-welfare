# 알림 채널 확장 설계 메모

## 목적

현재 프로젝트의 알림은 사실상 이메일 1채널만 동작합니다.

- 추천/마감 알림을 앱 안에서도 확인할 수 있게 하고
- 모바일 사용자에게는 웹푸시로 즉시성을 보강하며
- 기존 이메일 발송/재시도 구조는 유지하는

확장 방향을 현재 코드 기준으로 정리합니다.

## 현재 코드 기준 사실

### 1. 발송 경계는 이메일 전용에 가깝다

- `NotificationGateway` 는 현재 `send(String to, String subject, String text)` 한 메서드만 가집니다.
- 구현체는 `EmailNotificationGateway` 하나뿐입니다.
- `NotificationDispatchService` 는 `NotificationGateway.send(...)` 를 바로 호출하고, 예약/발송/실패 이력은 `NotificationHistoryService` 가 `notifications` / `notification_services` 에 저장합니다.

즉 현재 구조는 이름만 generic 이고, 실제 payload/flow 는 이메일 중심입니다.

### 2. `notifications` 는 사용자 알림함보다 발송 이력에 가깝다

현재 `Notification` 엔티티와 `notifications` 테이블은 아래 성격이 강합니다.

- `dispatchKey`
- `channel`
- `periodType`
- `status`
- `subject`
- `messageText`
- `retryCount`
- `nextRetryAt`
- `sentAt`

이건 "사용자에게 어떤 알림을 보여줄까"보다
"이 발송이 성공/실패했고 재시도는 어떻게 할까"에 더 맞습니다.

즉 현재 `notifications` 를 그대로 인앱 알림함 row 로 재사용하면

- 읽음/안읽음
- 숨김
- deep link
- 알림 종류
- 사용자 액션 상태

같은 UX 필드가 섞이기 시작합니다.

### 3. 대상 사용자 모델도 이메일 전용이다

`NotificationTarget` 은 현재 아래 값만 가집니다.

- `userId`
- `userKey`
- `email`
- `notificationPeriod`
- `notificationMinScore`
- `displayCount`

푸시 구독, 채널별 선호도, 알림 종류별 opt-in 은 없습니다.

### 4. 프론트의 알림 설정도 실제론 이메일 설정이다

`frontend/src/pages/MyPage.jsx` 기준 현재 저장되는 것은:

- `notificationYn`
- `notificationPeriod`
- `notificationMinScore`
- `displayCount`

뿐입니다.

UI 문구에 `이메일·푸시` 가 보이지만, 실제로는 이메일 수신 여부/주기/점수/발송 개수만 저장됩니다.

### 5. 구독 해지 경로도 이메일 전용이다

`NotificationController` 는 `/api/notifications/unsubscribe` 만 제공하고,
`NotificationMessageService` 가 이메일 본문에 unsubscribe 링크를 넣습니다.

즉 현재 "채널별 수신 관리"가 아니라 "이메일 수신 거부"에 가깝습니다.

### 6. 채널 enum 에 `kakao` 는 있지만 실제 구현은 없다

`Notification.NotificationChannel` 에 `email`, `kakao` 가 있지만

- 카카오 gateway 없음
- 카카오용 payload/템플릿 없음
- 검수/운영 플로우 없음

상태입니다.

이 프로젝트 단계에서는 카카오 알림톡보다
`인앱 알림함 + 웹푸시` 가 훨씬 현실적입니다.

### 7. 현재 알림 이벤트는 "추천 digest" 하나에 가깝다

현재 `NotificationScheduleService -> NotificationDispatchService -> NotificationRecommendationService` 흐름은

- 일간/주간 대상 사용자 조회
- 추천 pool 조회
- `NotificationSlotSelector` 로 `[A, A, B?]` 슬롯 선택
- 이메일 본문 생성/발송

을 수행합니다.

즉 현재 코드가 바로 지원하는 알림 종류는
"맞춤 추천 digest" 에 가깝습니다.

반대로 아래는 아직 별도 이벤트 소스가 없습니다.

- 북마크 정책 마감 임박 알림
- 특정 정책의 상태 변화 알림
- 새로 수집된 Gov24/복지로 정책 중 사용자 조건과 강하게 맞는 신규 정책 즉시 알림

따라서 채널 확장 문서는 "지금 있는 추천 digest를 인앱/웹푸시로도 보낸다"는 1차와,
"deadline reminder 자체를 새로 만든다"는 2차를 분리해서 봐야 합니다.

현재 `deadline reminder` 는 여기서 한 단계 더 나아가

- current-user manual dispatch
- `NotificationScheduleService.sendDailyDeadlineReminders()`

까지는 닫혔지만, runtime 범위는 아직 `DAILY` 만 active 입니다. 마감 임박 이벤트 성격상 weekly를 같은 강도로 여는 건 아직 scope 밖으로 두는 편이 현재 구조/검증 범위와 맞습니다.

## 현재 프로젝트 단계에서의 현실 제약

- 현재 상태 문서 기준으로 운영 서버는 아직 없습니다.
- 로컬 검증/구조 검증 단계가 우선입니다.
- 웹푸시는 실사용 기준으로 HTTPS 와 실제 도메인이 필요합니다.

즉 이 문서에서 말하는 웹푸시는

- 지금 바로 로컬 단계에서 끝까지 완성할 1순위 기능이라기보다
- 인앱 알림함 이후 운영 전환에 맞춰 여는 2단계 채널

로 보는 편이 현재 프로젝트 단계에 맞습니다.

## 왜 인앱 알림함이 먼저인가

웹푸시는 "도착했다"를 알려주는 채널이지, 보관함이 아닙니다.

푸시만 먼저 구현하면:

- 놓친 알림을 다시 확인할 곳이 없고
- 브라우저 권한을 거부한 사용자에게는 남는 것이 없고
- 이메일과 푸시 사이의 중복/누락을 판단하기 어렵습니다.

그래서 먼저 필요한 것은:

1. 알림 이벤트를 저장하는 앱 내부 기준 레코드
2. 사용자가 앱 안에서 다시 보는 인앱 알림함
3. 그 위에 웹푸시/이메일을 얹는 구조

입니다.

## 권장 구조

### 1. 발송 이력과 사용자 알림함을 분리한다

권장:

- `notifications`
  - 발송 이력 / retry / dispatch lock / 채널 결과
- `user_alerts`
  - 사용자에게 보여줄 알림 원본

즉 현재 `notifications` 는 유지하고,
새로운 사용자 알림함 테이블을 둡니다.

이 판단은 현재 `notifications` 가

- dispatch reservation
- send/fail/retry
- `notification_services` 를 통한 발송 item 이력

에 맞춰져 있기 때문입니다.

추천 digest 발송 로그와 사용자가 앱 안에서 읽는 알림 row 는
수명과 상태 전이가 다르므로 분리하는 편이 안전합니다.

### 2. `user_alerts` 최소 필드

추천 최소 필드:

- `id`
- `user_key`
- `kind`
  - `RECOMMENDATION_DIGEST`
  - `DEADLINE_REMINDER`
  - `SYSTEM`
- `title`
- `body`
- `deeplink_url`
- `status`
  - `UNREAD`, `READ`, `HIDDEN`
- `event_key`
  - 같은 이벤트 dedupe 용
- `metadata_json`
  - 추천 정책 id 목록, 로그 id 목록, score summary 등
- `read_at`
- `hidden_at`
- `created_at`

핵심은 `dispatch status` 와 `user read status` 를 분리하는 것입니다.

### 3. 웹푸시 구독 테이블 분리

`web_push_subscriptions`

- `id`
- `user_key`
- `endpoint`
- `p256dh`
- `auth`
- `user_agent`
- `device_label`
- `enabled`
- `last_seen_at`
- `last_sent_at`
- `last_error_at`
- `last_error_message`
- `created_at`
- `updated_at`

주의:

- 사용자 1명당 여러 기기/브라우저 구독 허용
- invalid subscription 은 soft-disable

### 4. 채널 선호 설정은 별도 확장

현재 `notificationYn`, `notificationPeriod`, `notificationMinScore` 만으로는 부족합니다.

확장 최소안:

- `notificationEmailYn`
- `notificationInAppYn`
- `notificationWebPushYn`
- `deadlineReminderYn`
- `recommendationAlertYn`

초기에는 `user_profiles` 확장으로 충분합니다.

단, 너무 세밀한 채널 정책이 필요해지면
`user_notification_preferences` 별도 테이블로 분리합니다.

## 이벤트 흐름 권장안

### 현재

1. 추천 후보 계산
2. 이메일 본문 생성
3. 이메일 발송
4. 발송 이력 저장

### 권장

1. 추천/마감 이벤트 발생
2. `user_alerts` 생성
3. 채널별 dispatch policy 판단
4. 인앱 알림함은 항상 저장
5. 이메일/web push 는 사용자 설정과 cooldown 기준으로 발송
6. 각 채널 발송 결과는 `notifications` 에 저장

즉 "알림 이벤트"와 "채널 발송"을 분리합니다.

## API 최소안

### 인앱 알림함

- `GET /api/notifications/me`
  - 내 알림 목록
- `GET /api/notifications/me/unread-count`
  - unread count
- `PATCH /api/notifications/{id}/read`
  - 읽음 처리
- `PATCH /api/notifications/{id}/hide`
  - 숨기기

### 웹푸시 구독

- `POST /api/notifications/push-subscriptions`
  - 구독 등록/갱신
- `DELETE /api/notifications/push-subscriptions/{id}`
  - 구독 해제
- `GET /api/notifications/push-public-key`
  - VAPID public key 제공

## 프론트 최소안

### 1단계: 인앱 알림함

- 헤더 bell 아이콘
- unread badge
- 마이페이지 알림함 탭 또는 알림 drawer
- 알림 클릭 시 `deeplink_url` 로 이동

### 2단계: 웹푸시

- 사용자가 가치 있는 순간에만 권한 요청
  - 북마크 직후
  - 마감 임박 정책 확인 직후
  - 추천을 1회 이상 받은 뒤
- 첫 방문 직후 permission prompt 는 피함

### deep link 규칙

웹푸시/인앱 알림은 아래 경로로 연결 가능해야 합니다.

- `/policies/{id}`
- `/policies?search=...`
- `/chat?session=...`
- `/mypage?tab=bookmarks`

프론트는 이미 `state.from`, `?tab=`, `?session=` 복원 경계를 많이 보강했으므로
알림 deep link 도 이 복귀 계약 위에서 설계해야 합니다.

## 중복 방지 원칙

같은 이벤트가 아래처럼 중복되면 안 됩니다.

- 인앱 1개
- 푸시 2개
- 이메일 1개
- retry 1개

필수 규칙:

- `event_key` 기준 dedupe
- 채널별 cooldown
- 같은 이벤트의 channel fan-out 정책 명시

예:

- 추천 digest: 인앱 + 이메일
- 마감 임박: 인앱 + 웹푸시
- 시스템 공지: 인앱만

단, 현재 코드에서 바로 가능한 것은 첫 줄인 "추천 digest" 입니다.
"마감 임박" 은 별도 이벤트 생성기 구현이 선행되어야 합니다.

## 추천 채널 정책

현재 프로젝트에는 이 정책이 가장 맞습니다.

### 1단계

- 인앱 알림함
- 이메일 유지

### 2단계

- 웹푸시 추가
  - 먼저 추천 digest 또는 추천 refresh 완료 이벤트에 연결
  - 이후 마감 임박 이벤트를 새로 만들면 그때 연결

### 보류

- 카카오 알림톡
  - 광고성/정보성 경계와 검수 부담 큼
- SMS
  - 비용과 광고성 규제 부담 큼

## 구현 순서 권장

### Phase 1

1. `user_alerts` 추가
2. 현재 추천 digest 발송 시 `user_alerts` 도 함께 생성
3. 인앱 알림 목록/unread/read API
4. 헤더 badge + 알림함 UI

### Phase 2

5. `web_push_subscriptions` 추가
6. VAPID key / service worker / subscription API
7. 웹푸시 발송기 추가
8. 먼저 추천 digest 이벤트에 웹푸시 연결

### Phase 3

9. 채널 선호 세분화
10. deadline reminder 같은 별도 이벤트 생성기 추가
11. 이벤트 종류별 발송 정책 분리

## 주의점

1. `notifications` 를 그대로 알림함으로 재사용하지 말 것
2. 푸시 권한 요청을 첫 방문 즉시 띄우지 말 것
3. 인앱 알림함 없이 웹푸시만 먼저 열지 말 것
4. deep link 후 복귀 문맥(`state.from`, `?tab=`, `?session=`)을 유지할 것
5. 이벤트 dedupe 와 channel cooldown 을 먼저 설계할 것
6. 카카오/SMS는 현재 단계에서 우선순위를 낮게 둘 것

## 현재 결론

이 프로젝트에서 이메일 외 알림 보완의 첫 단계는

- `인앱 알림함`
- 그 다음 `웹푸시`

가 맞습니다.

즉 "채널을 하나 더 붙이는 일"이 아니라

1. 알림 이벤트 원본을 앱 안에 저장하고
2. 사용자가 다시 볼 수 있게 만들고
3. 현재 이미 있는 추천 digest 이벤트를 먼저 인앱으로 확장하고
4. 운영 서버/HTTPS 전제가 생기면 그 위에 푸시를 얹는

순서로 가야 합니다.
