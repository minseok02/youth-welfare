# Frontend Retention Flow Runbook

문서군 진입점: [frontend-qa-docs-index.md](./frontend-qa-docs-index.md)

## 목적

이 문서는 로그인 사용자가 다시 돌아오는 개인 유지 흐름을 봅니다.

현재 범위는 아래입니다.

1. 알림함 빈 상태
2. `알림 설정 열기 -> /mypage?tab=3`
3. `/mypage?tab=2` 북마크 목록
4. 저장한 정책 재확인

즉 “알림 -> 마이페이지 설정 -> 북마크 재방문” 경계를 한 흐름으로 보는 runbook 입니다.

## 현재 기준선

아래 계약이 유지돼야 합니다.

- `/alerts` 빈 상태 CTA가 `/mypage?tab=3` 으로 이어져야 함
- `/mypage?tab=2` 에서 저장한 정책을 다시 볼 수 있어야 함
- 북마크와 알림함은 서로 끊기지 않고 같은 개인 유지 동선 안에서 연결돼야 함

## Playwright 기준선

관련 smoke:

- `알림함 빈 상태 CTA는 정책 목록과 알림 설정으로 이어진다`
- `알림함에서 unread 알림을 열면 읽음 처리 후 deeplink로 이동한다`
- `마이페이지 북마크 탭에서 상세로 갔다가 뒤로오면 tab query가 유지된다`
- `개인 유지 흐름은 알림함과 북마크 탭을 이어서 보여준다`

실행:

```bash
cd frontend
PLAYWRIGHT_GREP='알림함 빈 상태 CTA는 정책 목록과 알림 설정으로 이어진다|알림함에서 unread 알림을 열면 읽음 처리 후 deeplink로 이동한다|마이페이지 북마크 탭에서 상세로 갔다가 뒤로오면 tab query가 유지된다|개인 유지 흐름은 알림함과 북마크 탭을 이어서 보여준다' npm run test:e2e
```

## 해석 기준

- 알림함 빈 상태 CTA가 `/mypage?tab=3` 으로 가지 않으면 alert-to-settings 회귀입니다.
- `/mypage?tab=2` 북마크가 비정상적으로 비면 bookmark retention 회귀입니다.
- unread 알림 deeplink가 상세로 이어지지 않으면 notification engagement 회귀로 봅니다.
