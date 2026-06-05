# Frontend Protected User Flow Runbook

문서군 진입점: [frontend-qa-docs-index.md](./frontend-qa-docs-index.md)

## 목적

이 문서는 로그인 요구 경로와 로그인 후 복귀가 실제 사용자 흐름에서 끊기지 않는지 확인합니다.

현재 기본 흐름은 아래입니다.

1. 비로그인 상태에서 `/chat` 진입
2. `/login` 으로 분기
3. 로그인 후 `/chat` 복귀
4. `/mypage?tab=2` 진입
5. 북마크 상세 진입 후 다시 `/mypage?tab=2` 복귀

즉 “보호 경로 진입 -> 로그인 -> 보호 경로 복귀 -> 마이페이지 복귀” 를 한 번에 보는 runbook 입니다.

## 현재 기준선

아래 계약이 동시에 유지돼야 합니다.

- 보호 경로는 비로그인 상태에서 `/login` 으로 보내야 함
- 로그인 후 `state.from` 기준으로 원래 경로로 복귀해야 함
- `/chat` 세션 진입이 정상이어야 함
- `/mypage?tab=2` 의 bookmark 탭 문맥이 상세 왕복 뒤에도 유지돼야 함

## Playwright 기준선

관련 smoke:

- `비로그인 사용자는 /chat 접근 시 로그인 후 원래 경로로 복귀한다`
- `마이페이지 북마크 탭에서 상세로 갔다가 뒤로오면 tab query가 유지된다`
- `보호 사용자 핵심 흐름은 로그인 요구 경로와 마이페이지 복귀를 유지한다`

실행:

```bash
cd frontend
PLAYWRIGHT_GREP='비로그인 사용자는 /chat 접근 시 로그인 후 원래 경로로 복귀한다|마이페이지 북마크 탭에서 상세로 갔다가 뒤로오면 tab query가 유지된다|보호 사용자 핵심 흐름은 로그인 요구 경로와 마이페이지 복귀를 유지한다' npm run test:e2e
```

## 해석 기준

- `/chat -> /login -> /chat` 복귀가 깨지면 protected routing 회귀입니다.
- `/mypage?tab=2` 가 상세 왕복 후 유지되지 않으면 mypage tab/query 복귀 회귀입니다.
- 두 경계가 한 흐름에서 동시에 깨지면 로그인 후 사용자 핵심 동선 회귀로 봅니다.
