# Frontend Core User Flow Runbook

문서군 진입점: [frontend-qa-docs-index.md](./frontend-qa-docs-index.md)

## 목적

이 문서는 공개 사용자가 실제로 밟는 가장 짧은 핵심 흐름을 고정합니다.

현재 기본 흐름은 아래입니다.

1. 홈 진입
2. `/guide` 로 이동
3. `정책 검색하기`
4. 정책 상세 진입
5. 상세의 핵심 정보와 `정책 오류 제보` CTA 확인

즉 “서비스 소개 -> 검색 진입 -> 정책 상세 확인” 까지를 한 번에 보는 runbook 입니다.

## 현재 기준선

이 흐름은 로그인 없이도 막히지 않아야 합니다.

- 홈에서 `이용가이드` 진입 가능
- `/guide` 에서 `정책 검색하기` CTA 노출
- `/policies` 진입 가능
- 검색 결과에서 상세 진입 가능
- 상세에서 `요약 정보`, `지원지역`, `소관기관`, `신청기간`, `⚑ 정책 오류 제보` 가 보임

## Playwright 기준선

관련 smoke:

- `공개 사용자 핵심 흐름은 홈에서 가이드를 보고 정책 상세까지 이어진다`
- `정책 상세는 요약 정보와 오류 제보 CTA를 보여준다`

실행:

```bash
cd frontend
PLAYWRIGHT_GREP='공개 사용자 핵심 흐름은 홈에서 가이드를 보고 정책 상세까지 이어진다|정책 상세는 요약 정보와 오류 제보 CTA를 보여준다' npm run test:e2e
```

## 해석 기준

- 홈 -> guide -> policies 연결이 끊기면 onboarding/public navigation 회귀입니다.
- 상세 핵심 정보가 빠지면 search/detail read-model 회귀입니다.
- `⚑ 정책 오류 제보` CTA가 사라지면 정책 데이터 품질 신고 경로 회귀입니다.
