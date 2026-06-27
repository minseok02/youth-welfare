# Frontend Admin Operator Flow Runbook

문서군 진입점: [frontend-qa-docs-index.md](./frontend-qa-docs-index.md)

## 목적

이 문서는 운영자가 `/admin/dashboard` 에서 실제로 밟는 핵심 흐름을 한 묶음으로 봅니다.

현재 범위는 아래입니다.

1. 일반 사용자 접근 차단
2. admin 진입 후 recommendation overview / triage 섹션 확인
3. quick jump / attention jump 확인
4. 운영 queue 확인
   - 정책 오류 제보
   - 서비스 문의
   - 정책 중복 review
   - 정책 링크 review
   - stale notification target
5. 부분 실패 시 섹션 격리 확인

즉 “운영자만 진입 가능해야 하고, 들어간 뒤에는 summary / attention / queue가 부분 실패 없이 유지되는가” 를 보는 runbook 입니다.

## 현재 기준선

아래 계약이 유지돼야 합니다.

- 일반 사용자는 `/admin/dashboard` 접근 시 홈으로 돌려보내고 경고 toast를 보여야 함
- admin 진입 시 recommendation overview, collect/search triage, attention queue가 보여야 함
- quick jump와 attention action은 올바른 섹션/focus target으로 이동해야 함
- attention queue와 상단 운영 알림 카드는 각 item의 `nextAction`을 “다음 조치”로 보여줘야 함
- queue 섹션들은 `OPEN / REVIEWED / ALL` 과 recent metrics를 유지해야 함
- `summary` 또는 `breakdown` 한 섹션 실패가 페이지 전체 blank로 번지면 안 됨

## Playwright 기준선

관련 smoke:

- `일반 사용자로 admin dashboard 접근 시 홈으로 리다이렉트되고 경고 toast가 보인다`
- `admin dashboard는 recommendation overview와 triage 섹션을 함께 보여준다`
- `admin dashboard quick jump는 recommendation breakdown 섹션으로 이동한다`
- `admin dashboard 운영 알림 카드는 상위 주의 항목을 스크롤 없이 보여준다`
- `admin dashboard 주의 항목 큐는 collect와 표준코드 backlog를 함께 보여준다`
- `admin dashboard summary 실패 시 collect/search triage는 유지된다`
- `admin dashboard breakdown 실패 시 recommendation hero는 유지되고 해당 섹션만 실패한다`
- `admin dashboard 정책 오류 제보 섹션은 열린 제보 recent queue를 보여준다`
- `admin dashboard 서비스 문의 섹션은 열린 문의 recent queue를 보여준다`
- `admin dashboard 정책 중복 review 섹션은 duplicate queue를 보여준다`
- `admin dashboard 정책 링크 review 섹션은 열린 링크 review queue를 보여준다`
- `admin dashboard stale notification target 섹션은 오래된 unread target cluster를 보여준다`

실행:

```bash
cd frontend
PLAYWRIGHT_GREP='일반 사용자로 admin dashboard 접근 시 홈으로 리다이렉트되고 경고 toast가 보인다|admin dashboard는 recommendation overview와 triage 섹션을 함께 보여준다|admin dashboard quick jump는 recommendation breakdown 섹션으로 이동한다|admin dashboard 운영 알림 카드는 상위 주의 항목을 스크롤 없이 보여준다|admin dashboard 주의 항목 큐는 collect와 표준코드 backlog를 함께 보여준다|admin dashboard summary 실패 시 collect/search triage는 유지된다|admin dashboard breakdown 실패 시 recommendation hero는 유지되고 해당 섹션만 실패한다|admin dashboard 정책 오류 제보 섹션은 열린 제보 recent queue를 보여준다|admin dashboard 서비스 문의 섹션은 열린 문의 recent queue를 보여준다|admin dashboard 정책 중복 review 섹션은 duplicate queue를 보여준다|admin dashboard 정책 링크 review 섹션은 열린 링크 review queue를 보여준다|admin dashboard stale notification target 섹션은 오래된 unread target cluster를 보여준다' npm run test:e2e
```

## 해석 기준

- non-admin 차단이 깨지면 access control 회귀입니다.
- quick jump / attention jump가 틀리면 operator navigation 회귀입니다.
- queue 섹션이 비거나 필터가 깨지면 운영 triage 회귀입니다.
- 한 API 실패가 페이지 전체 blank로 번지면 admin resilience 회귀입니다.
