# 프론트 QA 문서 목차

[환경 안내](../core/environment-guide.md)에서 로컬 dev·로컬 Docker·배포 origin을 구분합니다. 배포된 웹을 대상으로 실행한 브라우저 테스트를 로컬 단위 테스트와 혼동하지 않습니다.

## 현재 계약과 실행 절차

| 목적 | 문서 |
| --- | --- |
| 인증 복귀·URL/state·화면 경계 | [frontend-qa-current-state.md](frontend-qa-current-state.md) |
| 수동 QA 순서 | [frontend-qa-checklist.md](frontend-qa-checklist.md) |
| 공개 탐색·검색·로그인·세션·계정·알림·운영자·추천·도움 흐름 | [사용자 흐름 검증](frontend-user-flow-runbook.md) |
| lint/build/browser 관측 결과 | [frontend-observation-runbook.md](frontend-observation-runbook.md) |
| SEO와 공유 메타 | [frontend-seo-current-state.md](frontend-seo-current-state.md) |
| 접근성 landmark | [frontend-landmark-structure-current-state.md](frontend-landmark-structure-current-state.md) |
| 결과 작성 | [frontend-qa-template.md](frontend-qa-template.md) |

사용자 흐름별 기대 동작과 테스트 선택은 한 문서의 해당 절에서 관리합니다. 로그인/비밀번호 변경/문의/북마크/동의 철회 같은 write 흐름, 관리자 opt-in, `@dev-only` 제외 규칙은 유지합니다.

## 로컬과 서버 준비

- 단위·정적 검사: [testing.md](../core/testing.md)의 `test:unit`, lint, build.
- 로컬 앱/fixture: 실제 앱·DB 대상을 확인하고 테스트용 계정/정책을 준비합니다. fixture 준비 스크립트는 서버 운영 데이터용이 아닙니다.
- 배포 origin: public URL과 API, 관리자 credential, 선택 테스트의 상태 변경 범위를 확인합니다.
- 런타임 문제: [server-runtime-drift-checklist.md](../core/server-runtime-drift-checklist.md). 같은 포트라도 앱 DB와 점검 DB가 다를 수 있습니다.

## 과거 QA 기록

- [2026-05-15 세션](frontend-qa-session-2026-05-15.md)
- [2026-05-17 세션](frontend-qa-session-2026-05-17.md)

위 세션은 날짜가 고정된 관측/준비 기록입니다. 당시 URL과 기대 상태를 현재 구현의 계약이나 최신 통과 결과로 재사용하지 않습니다. 현재 실행 결과는 [phase-plan.md](../phase-plan.md)에 남깁니다.
