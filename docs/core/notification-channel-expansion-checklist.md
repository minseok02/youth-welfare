# 알림 채널 확장 체크리스트

문서군 진입점: [system-docs-index.md](./system-docs-index.md)

관련 문서:

- [notification-channel-expansion-plan.md](./notification-channel-expansion-plan.md)
- [current-state.md](../current-state.md)
- [architecture.md](../architecture.md)

## 목적

이 문서는 알림 채널 확장 작업을 할 때

- 이번 턴 범위
- 이번 턴에서 하지 않을 것
- 단계별 완료 조건

을 짧게 고정하기 위한 작업 계약입니다.

현재 프로젝트 단계에 맞는 기본 순서는:

1. 인앱 알림함 모델
2. 현재 추천 digest 이벤트 연결
3. 웹푸시

입니다.

## 사용 규칙

알림 채널 확장 작업을 시작할 때는 먼저 아래 두 줄을 말하고 시작합니다.

1. 이번 턴 범위
2. 이번 턴에서 하지 않을 것

예:

```text
알림 채널 체크리스트 기준으로 이번 턴 범위는 user_alerts 스키마와 읽기 API까지입니다.
이번 턴에서는 웹푸시, 프론트 알림함 UI, deadline reminder 이벤트는 하지 않습니다.
```

## 현재 기본 원칙

1. 현재 실제 알림 이벤트는 추천 digest 1종에 가깝다.
2. `notifications` 는 발송 이력/재시도 테이블로 유지한다.
3. 사용자 알림함은 별도 `user_alerts` 로 분리한다.
4. 1차는 인앱 알림함과 현재 추천 digest 연결까지다.
5. 웹푸시는 운영 서버/HTTPS 전제 이후 2단계다.
6. deadline reminder 는 별도 이벤트 생성기로 분리한다.

## 절대 하지 말 것

1. `notifications` 를 그대로 읽음함 테이블처럼 재정의하지 않는다.
2. 웹푸시를 인앱 알림함보다 먼저 열지 않는다.
3. deadline reminder 를 "채널 추가"처럼 섞지 않는다.
4. 채널 설정을 지금 단계에서 과하게 세분화하지 않는다.
5. 카카오 알림톡/SMS를 1차 범위에 넣지 않는다.

## 단계별 체크리스트

## 1단계. `user_alerts` 최소 백엔드

완료 조건:

- `user_alerts` 테이블이 생긴다.
- 추천 digest 발송 결과와 별도로 사용자 알림 row 를 저장할 수 있다.
- 내 알림 목록 / unread count / read / hide API 가 생긴다.

이번 단계에서 허용:

- schema / migration
- entity / repository
- read / command service
- `NotificationController` 의 authenticated API 확장
- 현재 추천 digest 이벤트와의 최소 연결

이번 단계에서 금지:

- 웹푸시
- 프론트 알림함 UI
- deadline reminder

## 2단계. 인앱 알림함 UI

완료 조건:

- 헤더 unread badge
- 알림 목록
- 읽음/숨김 반영

이번 단계에서 금지:

- 웹푸시
- SMS / 카카오

## 3단계. 웹푸시

완료 조건:

- subscription 저장
- 푸시 발송
- 추천 digest 또는 refresh 완료 이벤트에 연결
- local smoke 또는 동등한 검증으로 채널 fan-out 분기가 실제 DB 흔적까지 확인된다.

이번 단계에서 금지:

- 알림톡
- SMS

## 4단계. 별도 이벤트 추가

들어가기 전 확인:

- 현재 추천 digest 인앱/푸시가 안정적인가
- 운영 서버/HTTPS 전제가 준비됐는가

여기서만 검토:

- deadline reminder
- 북마크 정책 상태 변화 알림
- 신규 수집 정책 조건 매칭 즉시 알림

## 요약

1. 1차는 `user_alerts` 와 현재 추천 digest 연결이다.
2. `notifications` 는 발송 이력이고, 읽음함은 별도다.
3. 웹푸시는 인앱 알림함 다음 단계다.
4. deadline reminder 는 별도 이벤트로 나중에 본다.
5. 채널 분기는 문서/단위 테스트만이 아니라 `digest-test-dispatch + DB delta` 기준 local smoke까지 닫아야 한다.
