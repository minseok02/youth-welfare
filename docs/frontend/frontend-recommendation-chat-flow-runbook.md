# Frontend Recommendation And Chat Flow Runbook

문서군 진입점: [frontend-qa-docs-index.md](./frontend-qa-docs-index.md)

## 목적

이 문서는 추천과 챗봇처럼 사용자의 탐색을 직접 보조하는 흐름을 한 묶음으로 봅니다.

현재 범위는 아래입니다.

1. 로그인 후 메인 추천 보강
2. `맞춤 재추천` CTA 분기
3. `/chat` 보호 경로 진입
4. 세션 만료 후 `/chat` 복귀

즉 “추천 보강 -> 챗봇 진입/복귀” 를 하나의 assistive flow로 보는 runbook 입니다.

## 현재 기준선

아래 계약이 유지돼야 합니다.

- 로그인 직후 메인에서 추천 nudge와 guide 배너가 보여야 함
- `맞춤 재추천 →` 는 우선순위/표준코드 상태에 맞는 `mypage` 탭으로 가야 함
- 비로그인 `/chat` 은 `/login` 으로 분기해야 함
- 세션 만료 또는 `401 -> refresh 401` 뒤에도 재로그인 후 `/chat` 으로 복귀해야 함

## Playwright 기준선

관련 smoke:

- `메인 재추천 CTA는 우선순위가 없으면 마이페이지 우선순위 탭으로 이동한다`
- `메인 개인 맞춤 재추천 CTA는 표준코드 공백이 크면 마이페이지 내 정보 탭으로 이동한다`
- `로그인 직후 메인에서는 우선순위와 표준코드 공백에 대한 추천 nudge를 한 번 보여준다`
- `로그인 사용자 핵심 흐름은 메인에서 가이드와 추천 보강으로 이어진다`
- `비로그인 chat 접근 후 로그인하면 원래 chat 경로로 복귀한다`
- `로그인된 chat 세션이 만료되면 로그인으로 이동하고 재로그인 후 chat으로 복귀한다`
- `chat 보호 API가 401 후 refresh도 실패하면 로그인으로 이동하고 재로그인 후 chat으로 복귀한다`

실행:

```bash
cd frontend
PLAYWRIGHT_GREP='메인 재추천 CTA는 우선순위가 없으면 마이페이지 우선순위 탭으로 이동한다|메인 개인 맞춤 재추천 CTA는 표준코드 공백이 크면 마이페이지 내 정보 탭으로 이동한다|로그인 직후 메인에서는 우선순위와 표준코드 공백에 대한 추천 nudge를 한 번 보여준다|로그인 사용자 핵심 흐름은 메인에서 가이드와 추천 보강으로 이어진다|비로그인 chat 접근 후 로그인하면 원래 chat 경로로 복귀한다|로그인된 chat 세션이 만료되면 로그인으로 이동하고 재로그인 후 chat으로 복귀한다|chat 보호 API가 401 후 refresh도 실패하면 로그인으로 이동하고 재로그인 후 chat으로 복귀한다' npm run test:e2e
```

## 해석 기준

- 추천 보강 CTA가 잘못된 탭으로 가면 recommendation assist 회귀입니다.
- 로그인 직후 guide/nudge가 사라지면 onboarding assist 회귀입니다.
- `/chat` 진입/복귀가 깨지면 chatbot assist 회귀입니다.
- 세션 만료 뒤 `/chat` 복귀가 깨지면 session recovery 가 아니라 assistive flow 회귀로도 같이 봅니다.
