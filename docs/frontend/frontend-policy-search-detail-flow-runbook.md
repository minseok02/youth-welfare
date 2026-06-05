# Frontend Policy Search Detail Flow Runbook

문서군 진입점: [frontend-qa-docs-index.md](./frontend-qa-docs-index.md)

## 목적

이 문서는 정책 탐색에서 가장 중요한 `검색 -> 필터 -> 상세` 읽기 흐름을 한 묶음으로 봅니다.

현재 범위는 아래입니다.

1. `/policies?search=...`
2. 카테고리/필터 즉시 반영
3. 정책 상세 진입
4. 브라우저 back / 상세 뒤로가기
5. 상세 핵심 read-model 확인

즉 “찾기 -> 읽기 -> 다시 돌아오기” 경계를 하나의 search/detail flow로 보는 runbook 입니다.

## 현재 기준선

아래 계약이 유지돼야 합니다.

- `search` query는 상세 왕복 뒤에도 유지돼야 함
- 데스크톱 필터는 별도 적용 버튼 없이 즉시 URL에 반영돼야 함
- 상세에는 최소
  - `요약 정보`
  - `지원지역`
  - `소관기관`
  - `신청기간`
  - `⚑ 정책 오류 제보`
  가 보여야 함

## Playwright 기준선

관련 smoke:

- `정책 목록 검색 query는 상세 진입 후 브라우저 back과 상세 뒤로가기에서 유지된다`
- `정책 상세는 요약 정보와 오류 제보 CTA를 보여준다`
- `정책 필터는 데스크톱에서 선택 즉시 반영되고 별도 적용 버튼을 요구하지 않는다`
- `공개 사용자 핵심 흐름은 홈에서 가이드를 보고 정책 상세까지 이어진다`

실행:

```bash
cd frontend
PLAYWRIGHT_GREP='정책 목록 검색 query는 상세 진입 후 브라우저 back과 상세 뒤로가기에서 유지된다|정책 상세는 요약 정보와 오류 제보 CTA를 보여준다|정책 필터는 데스크톱에서 선택 즉시 반영되고 별도 적용 버튼을 요구하지 않는다|공개 사용자 핵심 흐름은 홈에서 가이드를 보고 정책 상세까지 이어진다' npm run test:e2e
```

## 해석 기준

- 검색 query가 사라지면 search state 회귀입니다.
- 필터가 즉시 URL에 반영되지 않으면 filter interaction 회귀입니다.
- 상세 핵심 필드가 비면 detail read-model 회귀입니다.
- `⚑ 정책 오류 제보` 가 사라지면 data quality feedback 경로 회귀로 봅니다.
