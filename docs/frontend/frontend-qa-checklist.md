# Frontend QA Checklist

> [환경 안내](../core/environment-guide.md)에서 대상 앱/DB를 확인합니다. 가입·프로필·비밀번호·북마크·문의·동의 철회 등 상태 변경 시나리오는 배포 origin에 실제 반영될 수 있습니다. 자동 테스트명은 [사용자 흐름 검증](frontend-user-flow-runbook.md)에서 선택합니다.

문서군 진입점: [frontend-qa-docs-index.md](./frontend-qa-docs-index.md)

## 목적

이 문서는 브라우저 관점에서 실제 사용자 흐름을 수동 검증할 때 쓰는 runbook입니다.

## 사전 조건

- frontend dev/prod local 실행 가능 상태
- backend local API 정상 기동
- 일반 사용자 1명
- 필요 시 admin 권한이 있는 사용자 1명
- 브라우저 devtools network 확인 가능

실행 원칙:

- 각 단계에서 pass/fail만 보지 말고 `현재 URL`, 공개 필터/`?tab=` query, `state.reason/from/activeChatSessionId`의 복원 여부를 구분해 기록합니다. 토큰·비밀번호와 원문 개인 식별자는 캡처하지 않습니다.
- 이상 징후가 보이면 같은 단계 안에서 network/console 증거도 같이 남깁니다.

## 권장 실행 순서

브라우저 수동 QA는 아래 순서로 돌리는 것을 권장합니다.

### 1차 고위험 동선

1. `/policies` 검색/필터/페이지네이션 -> 상세 -> 상세 `뒤로가기` -> 브라우저 back
2. `/chat` 비로그인 접근 -> `/login` (`location.state.reason=login-required`) -> 로그인 후 `/chat` 복귀. 기존 선택 세션이 있으면 라우터 state로 확인하고 URL에는 세션 ID를 남기지 않음
3. `/mypage?tab=` 진입 -> 상세 진입 -> 뒤로가기 -> `?tab=` 복원
4. 메인/목록/상세/마이페이지 북마크 on/off 일관성
5. 세션 만료 재현 -> `/login` (`location.state.reason=expired`) -> 재로그인 후 복귀

이 다섯 개는 `searchParams`, `state.from`, `chatFrom`, `postLoginAction`, `activeChatSessionId`, `?tab=` 계약이 함께 걸린 고위험 동선입니다. 세션 ID를 query로 복원하도록 코드를 되돌리지 않습니다.

### 2차 보강 동선

6. 공개 홈/정책 목록/검색 empty state
7. 로그인 직후 메인 `가이드 배너 / 추천 보강 CTA`
8. 정책 검색 라우터 state / 필터 query / 상세 read-model
9. 보호 경로 `/chat` 로그인 복귀 -> `/mypage?tab=2` 복귀
10. 세션 만료 -> `/login` -> 재로그인 / reset-password deep link
11. 알림함 빈 상태 / unread 알림 / 북마크 재방문
12. 계정 생성 / 비밀번호 변경 후 재로그인
13. admin dashboard 진입 / queue / 부분 실패 격리
14. 추천 보강 / `/chat` 진입 / 세션 복귀
15. `/guide` -> `/support` -> 정책 상세 `오류 제보` 역할 구분
16. 프로필/우선순위 저장 후 새로고침 유지
17. 추천 refresh/loading/중복 클릭 방지
18. 챗 세션 생성/메시지 전송/삭제와 `activeChatSessionId` 갱신·세션 query 비노출
19. 비밀번호 변경 / 회원탈퇴

즉 시간이 제한되면 1차 고위험 동선부터 먼저 돌리고, 2차 보강 동선은 그 다음에 확인합니다.

## 1. 공개 탐색

### 홈

- `/` 진입
- 정책 카드 목록 로드 확인
- 추천 모드 토글/안내 메시지 확인

기대 결과:

- 초기 진입에서 깨진 레이아웃, 무한 spinner, 콘솔 에러가 없어야 함

### 정책 목록

- `/policies` 진입
- 카테고리 변경
- 지역 필터 변경
- 검색 실행
- 페이지네이션 이동

기대 결과:

- 각 조작 후 목록이 갱신됨
- 빈 결과여도 에러 대신 empty state 또는 0건 상태가 보여야 함

### 도움 경로

- 홈 또는 헤더에서 `/guide` 진입
- `/guide` 에서 `서비스 문의` CTA 진입
- `/support` 에서 `이용가이드 보기` 복귀

기대 결과:

- 공개 도움 경로가 서로 끊기지 않아야 함
- `/support` 는 서비스 사용 문의와 정책 오류 제보의 역할을 분리해 설명해야 함

## 2. 뒤로가기 / 재진입

### 목록 -> 상세 -> 뒤로가기

- `MainPage` 또는 `PoliciesPage` 에서 정책 상세 진입
- 상세 페이지의 `뒤로가기` 버튼 클릭
- 브라우저 back 버튼도 별도로 확인

기대 결과:

- 이전 화면으로 복귀
- 복귀 직후 브라우저 오류/blank page 없음

추가 확인:

- `PoliciesPage` 에서 검색/필터/페이지 상태가 기대대로 유지되는지 확인
- 검색어는 `location.state.policySearchKeyword`, 필터·정렬·페이지는 URL query로 복원돼야 합니다. 유지되지 않으면 회귀/오류 후보로 기록합니다.
- `뒤로가기 전/후 URL`, 필터 query, 라우터 검색 state를 구분해 기록합니다. 공유 URL만으로 검색어까지 복원되는 계약은 아닙니다.

### 마이페이지 북마크 -> 상세 -> 뒤로가기

- `/mypage` 북마크 탭 진입
- 북마크 상세 진입
- 뒤로가기

기대 결과:

- 다시 북마크 탭으로 복귀

## 3. 로그인 / 리다이렉트

### 로그인 필요 경로

- 비로그인 상태에서 `/chat` 직접 접근

기대 결과:

- `/login` 으로 이동
- 로그인 화면 안내 메시지 표시
- `location.state.reason=login-required`, `state.from` 이 실제로 남았는지 확인 (URL reason query 아님)

### 로그인 후 원래 위치 복귀

- 위 상태에서 로그인 수행

기대 결과:

- 다시 `/chat` 으로 복귀
- 로그인 직후·복귀 후 URL과 라우터 state를 구분해 기록

### 세션과 일회성 토큰 URL 비노출

- `/chat`에서 세션 선택 → 다른 화면 → 복귀와 새로고침을 확인하고, 선택 세션이 남아 있으면 `location.state.activeChatSessionId`를 대조합니다.
- legacy `session/coachPolicyId` query 진입은 값을 읽은 뒤 URL에서 제거돼야 합니다. 정상 이동은 라우터 state를 사용하고 공유 URL로 개인 세션을 복원하지 않습니다.
- 로그아웃·탈퇴 등으로 서버에서 삭제된 세션의 복원을 기대하지 않습니다.
- reset-password의 query/hash 토큰과 unsubscribe의 hash 토큰은 읽은 뒤 URL에서 제거돼야 합니다. 뒤로가기·앞으로가기에서도 토큰이 다시 노출되지 않는지 확인합니다.
- 증적에는 URL 정리 여부·상태 코드·마스킹한 상태 비교만 남기고 실제 token/cookie/Authorization 값은 저장하지 않습니다.

## 4. 정책 검색 / 필터 / 상세

### 검색어 state 유지와 URL 비노출

- `/policies`에서 검색어 입력 후 검색 실행
- 검색 결과 상세 진입
- 브라우저 back과 상세 `뒤로가기`를 각각 별도 왕복으로 확인
- 과거 `/policies?search=...` URL로 진입하면 query가 정리되는지도 별도 확인

기대 결과:

- 두 복귀 경로에서 검색 입력값·검색 결과가 `location.state.policySearchKeyword`로 유지돼야 함
- URL에 `search`/검색어 query를 남기지 않으며 검색 API는 POST JSON body를 사용해야 함
- 필터·정렬·페이지 query는 검색어 state와 별도로 유지돼야 함

### 필터 / 상세 read-model

- 데스크톱 카테고리/필터 선택
- 상세 진입

기대 결과:

- 필터는 즉시 URL에 반영돼야 함
- 상세에서 `요약 정보`, `지원지역`, `소관기관`, `신청기간` 이 보여야 함

### 보호 경로 핵심 흐름

- 비로그인 상태에서 `/chat` 접근
- 로그인 후 `/chat` 복귀
- `/mypage?tab=2` 진입
- 북마크 상세 진입 후 뒤로가기

기대 결과:

- 보호 경로 로그인 복귀와 마이페이지 tab 복귀가 한 흐름에서 동시에 유지돼야 함
- `/mypage?tab=2` 문맥이 상세 왕복 뒤에도 남아야 함

## 4. 세션 만료

### 만료 후 자동 로그인 화면 이동

- 로그인된 상태에서 access token/refresh token 만료 또는 무효 상태를 재현
- 보호 API 호출 유도

기대 결과:

- refresh 1회 시도 후 실패하면 `/login` 이동
- “로그인 상태가 만료되어 다시 로그인해야 합니다.” 안내 확인

추가 확인:

- local token 제거
- 무한 refresh loop 없음
- `/login`의 `location.state.reason=expired`와 실제 toast/message를 같이 기록 (reason URL query 아님)

### reset-password deep link

- `/reset-password?token=...`
- `/reset-password#token=...`
- invalid token

기대 결과:

- query/hash 토큰을 읽은 뒤 query·hash 없는 `/reset-password`로 정리돼야 함. query→hash 이동이 아님
- 두 진입 모두 같은 페이지에서 새 비밀번호 설정이 가능해야 함. 새로고침으로 메모리 토큰이 복원되는 계약은 아님
- invalid token은 만료 안내를 유지하고 로그인으로 보내지 않아야 함
- invalid token과 수신거부 링크도 히스토리 왕복 뒤 토큰 URL이 다시 나타나지 않아야 함

### 계정 재로그인 복귀

- `/mypage?tab=5` 진입
- 비밀번호 변경
- `/login` 이동
- 재로그인

기대 결과:

- 로그인 후 다시 `/mypage?tab=5` 로 복귀해야 함

## 7. admin operator flow

### admin dashboard

- 일반 사용자 `/admin/dashboard`
- admin 사용자 `/admin/dashboard`
- queue 섹션 확인
- quick jump / attention jump
- summary/breakdown 부분 실패

기대 결과:

- 일반 사용자는 접근 차단
- admin은 queue/attention/summary를 볼 수 있어야 함
- 한 섹션 실패가 전체 blank로 번지면 안 됨

## 8. 추천 / 챗봇 보조 흐름

### recommendation assist

- 로그인 후 메인 진입
- guide 배너 / 추천 nudge 확인
- `맞춤 재추천 →` 클릭

기대 결과:

- 우선순위/표준코드 상태에 맞는 `mypage` 탭으로 분기해야 함

### chatbot assist

- `/chat` 비로그인 진입
- 로그인 후 `/chat` 복귀
- 세션 만료 또는 `401 -> refresh 401`

기대 결과:

- `/chat` 복귀가 유지돼야 함
- 세션 만료 뒤에도 assistive flow가 끊기지 않아야 함

## 5. 북마크

### 메인/목록/상세 일관성

- 메인 카드에서 북마크 on/off
- 정책 목록 카드에서 같은 정책 북마크 상태 확인
- 정책 상세에서 같은 정책 북마크 상태 확인
- 마이페이지 북마크 목록 확인

기대 결과:

- 같은 정책에 대해 화면 간 상태가 크게 어긋나지 않아야 함
- 토글 실패 시 toast가 떠야 함
- 비로그인 상태에서 시작했으면 로그인 후 자동 실행 여부까지 기록

## 6. 알림 / 북마크 재방문

### 알림함 유지 흐름

- `/alerts` 진입
- 빈 상태면 `알림 설정 열기`
- unread 알림이 있으면 `열기`

기대 결과:

- 빈 상태 CTA는 `/mypage?tab=3` 으로 이어져야 함
- unread 알림은 읽음 처리 후 deeplink 로 이동해야 함

### 북마크 재방문

- `/mypage?tab=2` 진입
- 저장한 정책 확인

기대 결과:

- alerts 경로를 거친 뒤에도 북마크 목록이 정상 표시돼야 함

## 6. 마이페이지

### 프로필

- `/mypage` 진입
- 프로필 조회 확인
- 일부 필드 수정 후 저장

기대 결과:

- 저장 성공 toast
- 새로고침 후 반영 유지

### 우선순위

- 우선순위 추가/삭제/순서 변경
- 저장

기대 결과:

- 저장 성공
- 새로 진입 후 반영 유지

### 비밀번호 변경

- 현재 비밀번호/새 비밀번호 입력 후 변경

기대 결과:

- 성공 시 재로그인 요구 흐름 진입

### 회원탈퇴

- 탈퇴 modal 진입
- 잘못된 비밀번호 / 올바른 비밀번호 각각 확인

기대 결과:

- 잘못된 비밀번호면 실패 안내
- 정상 탈퇴면 로그아웃 후 홈 또는 지정 경로 이동

## 7. 추천

### 추천 조회

- 로그인 후 메인 추천 모드 진입
- 추천 목록 로드 확인

### 추천 새로고침

- 추천 refresh 버튼 실행

기대 결과:

- 중복 클릭 방지 또는 loading 상태 확인
- 성공 시 갱신 toast
- 실패 시 에러 toast

### 로그인 직후 메인 보강

- `/login` 로그인
- 메인 진입 직후 `처음 시작 가이드` 배너 확인
- 추천 보강 CTA 확인
- `이용가이드 보기 →` 진입
- 다시 메인으로 돌아와 `맞춤 재추천 →` 클릭

기대 결과:

- guide 배너와 추천 보강 CTA가 동시에 깨지지 않아야 함
- `/guide` 와 `/mypage?tab=0|1` 분기가 기대대로 이어져야 함

### 추천 -> 상세 -> 복귀

- 추천 카드 상세 진입
- 뒤로가기

기대 결과:

- 추천 화면 복귀

## 8. 챗봇

### 세션 생성 / 메시지 전송 / 삭제

- `/chat` 진입
- 세션 생성
- 메시지 전송
- 세션 목록 재확인
- 세션 삭제

기대 결과:

- 세션/메시지 CRUD가 끊기지 않아야 함
- 메시지 전송 중에는 중복 전송 UX가 없어야 함
- 세션 생성/선택/삭제 전후 `location.state.activeChatSessionId` 갱신·제거와 URL의 `session/coachPolicyId` 비노출을 확인

### 답변 본문 렌더링

- 답변에 링크가 포함되는 질문 전송(예: 정책 상세 → `AI와 신청 준비하기` 코칭)

기대 결과:

- 마크다운 링크 `[텍스트](url)` 가 클릭 가능한 링크로 렌더되어야 함(날것 텍스트로 노출 금지). `http/https` 만 링크화, 그 외/파싱 불가 링크는 원문 텍스트로 폴백
- 답변 본문에 내부 마스킹 토큰(`[REDACTED_*]`)이 노출되지 않아야 함

## 9. 정적 기준선

매 QA 라운드 뒤 최소 확인:

- `cd frontend && npm run lint`
- `cd frontend && npm run build`

현재 관찰:

- `npm run build` 는 통과하고, route-level lazy loading + vendor chunk split 이후 기존 번들 크기 경고도 재현되지 않음
- 브라우저 QA는 여전히 필요하지만, 현재 정적 기준선 자체는 green 상태로 본다

## 10. 정책 오류 제보

- 정책 상세 진입
- `⚑ 정책 오류 제보` CTA 확인

기대 결과:

- 정책 데이터 오류는 상세에서 바로 제보 가능해야 함
- `/support` 와 역할이 섞이지 않아야 함

## 11. 기록

결과 기록은 [frontend-qa-template.md](./frontend-qa-template.md) 를 사용합니다.
요약만 남기지 말고 URL/query/state 증거를 먼저 채운 뒤, 필요할 때만 `phase-plan` / `troubleshooting-log` 로 올립니다.
