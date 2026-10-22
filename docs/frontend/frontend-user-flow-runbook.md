# 프론트 사용자 흐름 검증

[QA 목차](frontend-qa-docs-index.md)와 [환경 안내](../core/environment-guide.md)를 확인한 뒤 필요한 흐름만 선택합니다. 아래 명령은 `frontend/`에서 실행하며, 같은 화면 흐름이라도 로컬 dev와 배포 origin의 준비·권한·영향은 다릅니다.

## 실행 환경과 공통 준비

- 로컬 dev/로컬 Docker: 대상 앱과 테스트 DB를 확인합니다. [fixture 준비 스크립트](../../frontend/scripts/bootstrap-playwright-smoke-data.sh)는 대상 앱에 계정·정책 데이터를 쓰므로 격리된 로컬 환경에서만 준비합니다.
- 배포 origin: `FRONTEND_E2E_MODE=deployed-origin`, `FRONTEND_PUBLIC_BASE_URL` 등의 실제 설정을 확인합니다. 가입·비밀번호 변경·동의 철회·북마크·문의 접수는 서버 데이터를 변경할 수 있습니다.
- 관리자 시나리오는 명시적인 테스트 계정과 권한이 필요합니다. `@dev-only` 테스트와 admin opt-in은 실행 스크립트의 환경별 제외 규칙을 유지합니다.
- 테스트명과 실행 구현은 [smoke.spec.js](../../frontend/e2e/smoke.spec.js), [run-playwright-smoke.sh](../../frontend/scripts/run-playwright-smoke.sh)를 확인합니다. 아래 필터가 선택한 범위만 검증한 것으로 기록합니다.

필터를 바꿀 때는 `npm run test:e2e -- --list`로 선택된 테스트명을 먼저 확인합니다. `|`로 나열한 항목 중 일부가 오래된 이름이어도 나머지만 실행되어 성공할 수 있습니다. 환경별 제외 규칙을 적용한 뒤 빠진 항목과 이유도 기록합니다. `--list`는 테스트 목록 확인이며 브라우저 동작 검증이 아닙니다.

| 흐름 | 확인 영역 |
| --- | --- |
| [공개 탐색](#public-core) | Public core |
| [검색과 상세](#search-detail) | Search detail |
| [로그인 후 안내](#authenticated-home) | Authenticated home |
| [보호 경로 복귀](#protected-routes) | Protected routes |
| [세션과 비밀번호 복구](#session-recovery) | Session recovery |
| [알림과 북마크 재방문](#retention) | Retention |
| [계정 생명주기](#account-lifecycle) | Account lifecycle |
| [운영자 화면](#admin-operator) | Admin operator |
| [추천과 챗봇](#recommendation-chat) | Recommendation chat |
| [도움과 오류 제보](#help-surface) | Help surface |

## Public core

공개 탐색의 기대 동작입니다.

이 흐름은 로그인 없이도 막히지 않아야 합니다.

- 홈에서 `이용가이드` 진입 가능
- `/guide` 에서 `정책 검색하기` CTA 노출
- `/policies` 진입 가능
- 검색 결과에서 상세 진입 가능
- 상세에서 `요약 정보`, `지원지역`, `소관기관`, `신청기간`, `⚑ 정책 오류 제보` 가 보임

### 테스트 선택

```bash
PLAYWRIGHT_GREP='공개 사용자 핵심 흐름은 홈에서 가이드를 보고 정책 상세까지 이어진다|정책 상세는 요약 정보와 오류 제보 CTA를 보여준다' npm run test:e2e
```

### 실패 해석

- 홈 -> guide -> policies 연결이 끊기면 onboarding/public navigation 회귀입니다.
- 상세 핵심 정보가 빠지면 search/detail read-model 회귀입니다.
- `⚑ 정책 오류 제보` CTA가 사라지면 정책 데이터 품질 신고 경로 회귀입니다.

## Search detail

검색과 상세의 기대 동작입니다.

아래 계약이 유지돼야 합니다.

- 검색어는 URL query에 노출하지 않고 `policySearchKeyword` 라우터 상태로 상세 왕복 뒤에도 유지돼야 함
- 데스크톱 필터는 별도 적용 버튼 없이 즉시 URL에 반영돼야 함
- 상세에는 최소
  - `요약 정보`
  - `지원지역`
  - `소관기관`
  - `신청기간`
  - `⚑ 정책 오류 제보`
  가 보여야 함

### 테스트 선택

```bash
PLAYWRIGHT_GREP='정책 목록 검색 상태는 URL query 없이 상세 진입 후 브라우저 back과 상세 뒤로가기에서 유지된다|정책 상세는 요약 정보와 오류 제보 CTA를 보여준다|정책 필터는 데스크톱에서 선택 즉시 반영되고 별도 적용 버튼을 요구하지 않는다|공개 사용자 핵심 흐름은 홈에서 가이드를 보고 정책 상세까지 이어진다' npm run test:e2e
```

### 실패 해석

- 검색어 상태가 사라지거나 URL의 `search` query로 다시 노출되면 search state/개인정보 최소화 회귀입니다.
- 필터가 즉시 URL에 반영되지 않으면 filter interaction 회귀입니다.
- 상세 핵심 필드가 비면 detail read-model 회귀입니다.
- `⚑ 정책 오류 제보` 가 사라지면 data quality feedback 경로 회귀로 봅니다.

## Authenticated home

로그인 후 안내의 기대 동작입니다.

로그인 직후 메인에서는 아래가 동시에 끊기지 않아야 합니다.

- 우선순위/선택 프로필 상태에 맞는 추천 nudge toast
- `처음 시작 가이드` 배너
- 메인 추천 보강 CTA

현재 high-signal 경계는 아래입니다.

- 우선순위가 없으면 `맞춤 재추천 → /mypage?tab=1`
- 선택 프로필 공백이 크면 `맞춤 재추천 → 추천 전 확인 → 선택 정보 보완하기 → /mypage?tab=0`
- 첫 로그인/가입 직후면 `이용가이드 보기 → /guide`

### 테스트 선택

```bash
PLAYWRIGHT_GREP='메인 재추천 CTA는 우선순위가 없으면 마이페이지 우선순위 탭으로 이동한다|메인 개인 맞춤 재추천 CTA는 선택 프로필 공백이 크면 확인 CTA를 거쳐 마이페이지 내 정보 탭으로 이동한다|로그인 직후 메인에서는 우선순위와 선택 프로필 공백에 대한 추천 nudge를 한 번 보여준다|로그인 사용자 핵심 흐름은 메인에서 가이드와 추천 보강으로 이어진다' npm run test:e2e
```

### 실패 해석

- 로그인 직후 guide 배너가 사라지면 onboarding 회귀입니다.
- `맞춤 재추천` CTA가 잘못된 탭으로 가면 recommendation improvement UX 회귀입니다.
- 메인에서 `/guide` 와 `/mypage` 둘 다 이어지지 않으면 로그인 사용자 핵심 흐름 회귀로 봅니다.

## Protected routes

보호 경로 복귀의 기대 동작입니다.

아래 계약이 동시에 유지돼야 합니다.

- 보호 경로는 비로그인 상태에서 `/login` 으로 보내야 함
- 로그인 후 `state.from` 기준으로 원래 경로로 복귀해야 함
- `/chat` 세션 진입이 정상이어야 함
- `/mypage?tab=2` 의 bookmark 탭 문맥이 상세 왕복 뒤에도 유지돼야 함

### 테스트 선택

```bash
PLAYWRIGHT_GREP='비로그인 chat 접근 후 로그인하면 원래 chat 경로로 복귀한다|마이페이지 북마크 탭에서 상세로 갔다가 뒤로오면 tab query가 유지된다|보호 사용자 핵심 흐름은 로그인 요구 경로와 마이페이지 복귀를 유지한다' npm run test:e2e
```

### 실패 해석

- `/chat -> /login -> /chat` 복귀가 깨지면 protected routing 회귀입니다.
- `/mypage?tab=2` 가 상세 왕복 후 유지되지 않으면 mypage tab/query 복귀 회귀입니다.
- 두 경계가 한 흐름에서 동시에 깨지면 로그인 후 사용자 핵심 동선 회귀로 봅니다.

## Session recovery

세션과 비밀번호 복구의 기대 동작입니다.

아래 계약이 유지돼야 합니다.

- 세션 만료 시 `/login` 으로 이동
- `reason=expired` 안내가 노출
- 재로그인 후 원래 보호 경로(`/chat`)로 복귀
- reset-password는 query token이든 hash token이든 정상 처리
- invalid reset token은 로그인으로 튕기지 않고 reset-password 화면에 머물러야 함

### 테스트 선택

```bash
PLAYWRIGHT_GREP='로그인된 chat 세션이 만료되면 로그인으로 이동하고 재로그인 후 chat으로 복귀한다|chat 보호 API가 401 후 refresh도 실패하면 로그인으로 이동하고 재로그인 후 chat으로 복귀한다|reset-password query token 진입은 새 비밀번호 설정 후 새 비밀번호로 로그인할 수 있다|reset-password hash token 딥링크 진입은 새 비밀번호 설정 후 새 비밀번호로 로그인할 수 있다|reset-password invalid token 진입은 만료 안내를 보여주고 로그인으로 이동하지 않는다' npm run test:e2e
```

### 실패 해석

- 세션 만료 후 `/login` 복귀가 깨지면 auth recovery 회귀입니다.
- refresh 실패 후 무한 loop가 생기면 high-risk 회귀입니다.
- reset-password invalid token이 로그인으로 튕기면 reset deep-link 회귀입니다.
- reset-password query/hash가 다르게 동작하면 이메일 링크 진입 회귀로 봅니다.

## Retention

알림과 북마크 재방문의 기대 동작입니다.

아래 계약이 유지돼야 합니다.

- `/alerts` 빈 상태 CTA가 `/mypage?tab=3` 으로 이어져야 함
- `/mypage?tab=2` 에서 저장한 정책을 다시 볼 수 있어야 함
- 북마크와 알림함은 서로 끊기지 않고 같은 개인 유지 동선 안에서 연결돼야 함

### 테스트 선택

```bash
PLAYWRIGHT_GREP='알림함 빈 상태 CTA는 정책 목록과 알림 설정으로 이어진다|알림함에서 unread 알림을 열면 읽음 처리 후 deeplink로 이동한다|마이페이지 북마크 탭에서 상세로 갔다가 뒤로오면 tab query가 유지된다|개인 유지 흐름은 알림함과 북마크 탭을 이어서 보여준다' npm run test:e2e
```

### 실패 해석

- 알림함 빈 상태 CTA가 `/mypage?tab=3` 으로 가지 않으면 alert-to-settings 회귀입니다.
- `/mypage?tab=2` 북마크가 비정상적으로 비면 bookmark retention 회귀입니다.
- unread 알림 deeplink가 상세로 이어지지 않으면 notification engagement 회귀로 봅니다.

## Account lifecycle

계정 생명주기의 기대 동작입니다.

아래 계약이 유지돼야 합니다.

- 회원가입 완료 후 로그인 페이지에서 안내가 보여야 함
- reset-password query/hash token 이 모두 정상 동작해야 함
- invalid reset token 은 로그인으로 튕기지 않아야 함
- 마이페이지 비밀번호 변경 후 `/login` 으로 이동하고, 재로그인 뒤 원래 account tab 으로 복귀해야 함

### 테스트 선택

```bash
PLAYWRIGHT_GREP='마이페이지 비밀번호 변경 후 로그인으로 이동하고 재로그인하면 account tab으로 복귀한다|reset-password query token 진입은 새 비밀번호 설정 후 새 비밀번호로 로그인할 수 있다|reset-password hash token 딥링크 진입은 새 비밀번호 설정 후 새 비밀번호로 로그인할 수 있다|reset-password invalid token 진입은 만료 안내를 보여주고 로그인으로 이동하지 않는다' npm run test:e2e
```

### 실패 해석

- 비밀번호 변경 후 account tab 복귀가 깨지면 account recovery 회귀입니다.
- reset-password query/hash가 다르게 동작하면 이메일 링크 진입 회귀입니다.
- invalid token이 로그인으로 튕기면 reset error handling 회귀입니다.

## Admin operator

운영자 화면의 기대 동작입니다.

아래 계약이 유지돼야 합니다.

- 일반 사용자는 `/admin/dashboard` 접근 시 홈으로 돌려보내고 경고 toast를 보여야 함
- admin 진입 시 recommendation overview, collect/search triage, attention queue가 보여야 함
- quick jump와 attention action은 올바른 섹션/focus target으로 이동해야 함
- attention queue와 상단 운영 알림 카드는 각 item의 `nextAction`을 “다음 조치”로 보여줘야 함
- queue 섹션들은 `OPEN / REVIEWED / ALL` 과 recent metrics를 유지해야 함
- `summary` 또는 `breakdown` 한 섹션 실패가 페이지 전체 blank로 번지면 안 됨

### 테스트 선택

```bash
PLAYWRIGHT_GREP='일반 사용자로 admin dashboard 접근 시 홈으로 리다이렉트되고 경고 toast가 보인다|admin dashboard는 recommendation overview와 triage 섹션을 함께 보여준다|admin dashboard quick jump는 recommendation breakdown 섹션으로 이동한다|admin dashboard 운영 알림 카드는 상위 주의 항목을 스크롤 없이 보여준다|admin dashboard 주의 항목 큐는 collect와 표준코드 backlog를 함께 보여준다|admin dashboard summary 실패 시 collect/search triage는 유지된다|admin dashboard breakdown 실패 시 recommendation hero는 유지되고 해당 섹션만 실패한다|admin dashboard 정책 오류 제보 섹션은 열린 제보 recent queue를 보여준다|admin dashboard 서비스 문의 섹션은 열린 문의 recent queue를 보여준다|admin dashboard 정책 중복 review 섹션은 duplicate queue를 보여준다|admin dashboard 정책 링크 review 섹션은 열린 링크 review queue를 보여준다|admin dashboard stale notification target 섹션은 오래된 unread target cluster를 보여준다' npm run test:e2e
```

### 실패 해석

- non-admin 차단이 깨지면 access control 회귀입니다.
- quick jump / attention jump가 틀리면 operator navigation 회귀입니다.
- queue 섹션이 비거나 필터가 깨지면 운영 triage 회귀입니다.
- 한 API 실패가 페이지 전체 blank로 번지면 admin resilience 회귀입니다.

## Recommendation chat

추천과 챗봇의 기대 동작입니다.

아래 계약이 유지돼야 합니다.

- 로그인 직후 메인에서 추천 nudge와 guide 배너가 보여야 함
- `맞춤 재추천 →` 는 우선순위/선택 프로필 상태에 맞게 우선순위 탭 또는 확인 모달을 통한 내 정보 보완으로 이어져야 함
- 비로그인 `/chat` 은 `/login` 으로 분기해야 함
- 세션 만료 또는 `401 -> refresh 401` 뒤에도 재로그인 후 `/chat` 으로 복귀해야 함
- 정책 상세 `AI와 신청 준비하기` 는 새 채팅 세션을 만들고 해당 정책을 고정한 `APPLICATION_COACHING` 답변을 보여야 함
- 신청 코칭 답변의 연결 정책 카드에는 공식 신청/공고·서류/관련 사이트 링크가 있으면 같이 보여야 함
- 마이페이지 동의 철회는 확인 모달을 거친 뒤 선택정보/민감정보 체크 해제뿐 아니라 관련 입력값 초기화까지 보여야 함

### 테스트 선택

```bash
PLAYWRIGHT_GREP='메인 재추천 CTA는 우선순위가 없으면 마이페이지 우선순위 탭으로 이동한다|메인 개인 맞춤 재추천 CTA는 선택 프로필 공백이 크면 확인 CTA를 거쳐 마이페이지 내 정보 탭으로 이동한다|로그인 직후 메인에서는 우선순위와 선택 프로필 공백에 대한 추천 nudge를 한 번 보여준다|로그인 사용자 핵심 흐름은 메인에서 가이드와 추천 보강으로 이어진다|비로그인 chat 접근 후 로그인하면 원래 chat 경로로 복귀한다|로그인된 chat 세션이 만료되면 로그인으로 이동하고 재로그인 후 chat으로 복귀한다|chat 보호 API가 401 후 refresh도 실패하면 로그인으로 이동하고 재로그인 후 chat으로 복귀한다|정책 상세에서 AI 신청 준비 코칭으로 진입하면 연결 정책과 신청 링크를 보여준다|마이페이지 동의 철회는 선택정보와 민감정보 철회 API를 호출하고 체크 상태를 해제한다 @dev-only' npm run test:e2e
```

### 실패 해석

- 추천 보강 CTA가 잘못된 탭으로 가면 recommendation assist 회귀입니다.
- 로그인 직후 guide/nudge가 사라지면 onboarding assist 회귀입니다.
- `/chat` 진입/복귀가 깨지면 chatbot assist 회귀입니다.
- router state의 `coachPolicyId` 진입이 기존 검색 후보로 흘러가 해당 정책을 놓치면 application coaching 회귀입니다.
- 신청 코칭 링크가 `APPLY` 와 참고 링크를 구분하지 못하면 신청 UX/데이터 신뢰도 회귀입니다.
- 세션 만료 뒤 `/chat` 복귀가 깨지면 session recovery 가 아니라 assistive flow 회귀로도 같이 봅니다.

## Help surface

도움과 오류 제보의 기대 동작입니다.

#### 1. `/guide`

- 헤더 `이용가이드`
- 메인 CTA
- 모바일 하단 `가이드`

에서 항상 진입 가능해야 합니다.

`/guide` 에서는 최소 아래가 보여야 합니다.

- `청년복지플랫폼을 가장 빠르게 쓰는 방법`
- 빠른 시작 4단계
- 기능별 카드
- `서비스 문의` CTA

#### 2. `/support`

`/support` 는 정책 데이터 오류가 아니라 **서비스 사용 문의**를 받는 공개 페이지입니다.

최소 아래 구분이 보여야 합니다.

- `서비스 사용 문의 / 의견 제안`
- `서비스 사용 문의`
- `정책 오류 제보와는 다릅니다`
- `정책 상세 페이지의 정책 오류 제보` 안내

즉 support는

- 로그인/계정
- 추천/챗봇
- 알림/북마크
- 검색/필터
- 기타 의견/제안

같은 사용 문의를 받는 경로이고, 정책 데이터 자체 오류는 정책 상세의 별도 경로로 보내야 합니다.

#### 3. 정책 상세 `정책 오류 제보`

정책 상세에는 `⚑ 정책 오류 제보` 버튼이 보여야 합니다.

이 경로는 다음 유형을 받습니다.

- 지역 정보가 다름
- 신청 기간이 다름
- 자격조건 설명이 다름
- 링크/원문이 열리지 않음
- 중복 정책 같음
- 기타

즉 support와 policy error report는 역할이 겹치면 안 됩니다.

### 테스트 선택

```bash
PLAYWRIGHT_GREP='이용가이드와 서비스 문의는 공개 진입면에서 서로 연결된다|서비스 문의 페이지는 정책 오류 제보와 역할을 구분해 안내한다|서비스 문의 페이지는 공개 문의를 접수하고 성공 안내를 보여준다|비로그인 정책 상세 오류 제보는 로그인으로 분기한다|정책 상세는 요약 정보와 오류 제보 CTA를 보여준다' npm run test:e2e
```

### 실패 해석

- `/guide` 진입점이 사라지면 도움 경로 접근성 회귀로 봅니다.
- `/support` 가 policy error report와 역할을 분리하지 못하면 UX 회귀로 봅니다.
- 정책 상세 `오류 제보` CTA가 없으면 정책 데이터 품질 신고 경로 회귀로 봅니다.
