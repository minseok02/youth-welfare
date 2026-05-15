# Frontend QA Template

문서군 진입점: [frontend-qa-docs-index.md](./frontend-qa-docs-index.md)

## 실행 정보

- 날짜:
- 환경:
- 브라우저:
- backend commit:
- frontend commit:
- 관련 smoke / bounded runtime baseline:

## 사전 조건

- 로그인 사용자:
- admin 사용자:
- 테스트 데이터/정책 snapshot:
- 시작 URL:
- 시작 query / state:

## 시나리오 결과

### 1. 공개 탐색

- 홈:
  - 진입 URL:
  - 네트워크/콘솔 이상:
- 정책 목록:
  - 진입 URL:
  - category/region/status/page query:
  - 네트워크/콘솔 이상:
- 검색/필터:
  - 입력/조작:
  - 기대 query:
  - 실제 query:
  - 복원 여부:
- 페이지네이션:
  - 이동 page:
  - 이동 후 query:
  - 복귀 시 query/상태 유지:

### 2. 뒤로가기 / 재진입

- 목록 -> 상세 -> 뒤로가기:
  - 목록 URL:
  - 상세 URL:
  - 뒤로가기 후 URL:
  - query/state 복원:
- 마이페이지 북마크 -> 상세 -> 뒤로가기:
  - 북마크 탭 URL:
  - 상세 URL:
  - 뒤로가기 후 URL:
  - `?tab=` 복원:
- 새로고침 후 상태:
  - 새로고침 전 URL:
  - 새로고침 후 URL:
  - 복원된 검색/필터/탭:

### 3. 로그인 / 리다이렉트

- `/chat` 비로그인 접근:
  - 진입 URL:
  - `/login` 이동 URL:
  - `reason` / `state.from`:
- 로그인 후 원위치 복귀:
  - 로그인 직후 URL:
  - 복귀된 원래 URL/query:
  - post-login action 자동 실행 여부:

### 4. 세션 만료

- refresh 실패 후 `/login` 이동:
  - 만료 재현 방식:
  - 실패한 보호 API:
  - 이동 후 URL / `reason`:
- expired 안내 메시지:
  - 실제 toast/message:
- 무한 loop 여부:
  - yes/no:
  - network 반복 여부:

### 5. 북마크

- 메인:
  - 정책 ID:
  - 토글 결과 / toast:
- 목록:
  - 동일 정책 상태 일치:
- 상세:
  - 동일 정책 상태 일치:
- 마이페이지 목록 반영:
  - 반영 여부:
  - 비로그인 진입이었다면 로그인 후 자동 실행 여부:

### 6. 마이페이지

- 프로필 저장:
  - 수정 필드:
  - 저장 결과 / 새로고침 유지:
- 우선순위 저장:
  - 변경 내용:
  - 저장 결과 / 추천 경로 반영:
- 비밀번호 변경:
  - 재로그인 요구 여부:
  - 복귀 URL/state 유지:
- 회원탈퇴:
  - 실패/성공 케이스:
  - 최종 이동 URL:

### 7. 추천

- 추천 조회:
  - 추천 개수:
  - empty state / CTA:
- 추천 refresh:
  - loading / 중복 클릭 방지:
  - 성공/실패 결과:
- 추천 상세/복귀:
  - 상세 URL:
  - 복귀 후 추천 문맥 유지:

### 8. 챗봇

- 세션 생성:
  - 생성 후 URL `?session=`:
- 메시지 전송:
  - 응답/에러:
  - 추천 정책 카드/참조 노출:
- 세션 삭제:
  - 삭제 대상 session:
  - 삭제 후 URL/query 정리:

## 정적 검증

- `npm run lint`:
- `npm run build`:
- build warning:

## 발견 이슈

- 이슈 1:
- 이슈 2:
- 재현 URL / 단계:

## 리스크 / 메모

- 필터 상태 유지:
- 세션 만료 UX:
- 성능/번들:
- 로그/스크린샷/네트워크 캡처 위치:

## 다음 액션

- 수정 필요:
- 운영 전 확인 필요:
