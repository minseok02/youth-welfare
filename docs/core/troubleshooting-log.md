# 트러블슈팅 로그 (작업 중 문제/해결 기록)

## 336) 챗 메시지에 `references` 는 있는데 `referencedServiceIds` 가 비어 있으면, 연결 정책 카드가 통째로 사라질 수 있다
- 문제: `ChatPage` 는 연결 정책 카드 렌더링 조건을 `message.referencedServiceIds.length > 0` 에만 걸고 있었다. 그래서 레거시/부분 데이터처럼 `references` 배열은 존재하지만 `referencedServiceIds` 가 비어 있는 메시지가 오면, 제목/근거가 충분히 있어도 카드 섹션 자체가 렌더링되지 않을 수 있었다
- 해결: `mapMessage()` 단계에서 `referencedServiceIds` 가 비어 있으면 `references[].serviceId` 를 fallback으로 채우도록 정리했다. 이제 메시지 응답이 두 필드 중 하나만 채워도 연결 정책 카드를 계속 렌더링할 수 있다. 이후 `cd frontend && npm run lint`, `cd frontend && npm run build` 를 다시 통과시켰다
- 이유: 프론트는 같은 의미를 가진 중복 필드가 있을 때 더 풍부한 필드를 활용해 복원력을 가져야 한다. 특히 레거시 데이터나 부분 응답이 섞일 수 있는 대화 기록 화면은 엄격한 단일 필드 의존이 쉽게 UI 공백으로 이어진다

## 335) 메인의 `맞춤 재추천`/`새로고침` 이 우선순위 없이 toast만 띄우고 끝나면, 사용자는 막힌 원인을 알아도 바로 해결 경로로 못 간다
- 문제: `MainPage` 의 추천 갱신 액션은 `user?.hasPriorities` 가 없으면 `마이페이지에서 우선순위를 먼저 설정해주세요` toast만 띄우고 종료됐다. 하지만 이 버튼들은 해결 행동을 기대하고 누르는 액션이라, 원인을 알려주기만 하고 실제 해결 탭으로 보내지 않으면 흐름이 끊긴다
- 해결: 두 액션 모두 우선순위가 없을 때 `마이페이지 우선순위 탭으로 이동합니다.` toast를 띄운 뒤 바로 `/mypage?tab=1` 로 이동하게 맞췄다. 이후 `cd frontend && npm run lint`, `cd frontend && npm run build` 를 다시 통과시켰다
- 이유: 재시도/갱신 버튼은 사용자가 문제 해결을 기대하고 누르는 CTA다. 막힌 이유를 설명하는 것만으로는 부족하고, 즉시 해결 가능한 위치까지 연결해야 UX가 끊기지 않는다

## 334) 메인 추천 카드의 북마크 toast가 이전 상태를 읽으면, 실제 토글 결과와 안내 문구가 반대로 뜰 수 있다
- 문제: `MainPage` 의 추천 카드 북마크 토글은 `setRecommendations()` 로 상태를 뒤집은 뒤, toast 문구는 기존 `recommendations` 배열에서 같은 카드의 이전 `bookmarked` 값을 다시 읽고 있었다. 이 상태에서는 UI는 저장됐는데 toast는 `해제`, 또는 UI는 해제됐는데 toast는 `저장`이라고 말하는 역전이 생길 수 있었다
- 해결: 토글 전에 현재 카드 상태를 읽어 `nextBookmarked` 를 계산하고, state 업데이트와 toast 문구가 모두 그 동일한 다음 상태를 기준으로 움직이게 맞췄다. 이후 `cd frontend && npm run lint`, `cd frontend && npm run build` 를 다시 통과시켰다
- 이유: 상태 토글 후 안내 문구는 “이전 상태를 보고 추론”하면 쉽게 어긋난다. 사용자에게 보여주는 UI와 toast는 같은 next state를 단일 기준으로 공유해야 한다

## 333) 메인 추천 empty state가 `우선순위 설정하러 가기` 라고 말하면서 기본 마이페이지 탭으로 보내면, 안내 문구와 실제 목적지가 다르다
- 문제: `MainPage` 의 맞춤 추천 영역은 `user?.hasPriorities` 가 없을 때 `우선순위 설정하러 가기` 버튼을 보여주지만, 실제 이동 대상은 `/mypage` 기본 탭이었다. 이 상태에서는 사용자가 추천 부족 원인을 해결하려고 버튼을 눌러도 정작 필요한 `우선순위` 탭이 아니라 `내 정보` 탭부터 보게 된다
- 해결: 해당 버튼의 이동 대상을 `/mypage?tab=1` 로 바꿔, 클릭 즉시 우선순위 탭으로 들어가도록 맞췄다. 이후 `cd frontend && npm run lint`, `cd frontend && npm run build` 를 다시 통과시켰다
- 이유: 해결 행동을 유도하는 CTA는 사용자를 바로 해결 지점으로 데려가야 한다. 특히 추천 부족처럼 원인이 분명한 상태에서는 “비슷한 화면”이 아니라 정확한 탭까지 맞춰야 체감이 산다

## 332) 챗에서 연결 정책 상세로 갔다가 돌아올 때 세션 id를 남기지 않으면, 같은 대화 문맥으로 복귀하지 못한다
- 문제: `ChatPage` 의 연결 정책 카드는 상세 페이지로 이동할 때 `from` 을 전혀 넘기지 않았고, 챗 화면 자체도 현재 `activeSessionId` 를 URL에 남기지 않았다. 이 상태에서는 `챗 -> 연결 정책 상세 -> 뒤로가기` 흐름에서 정책 상세의 상단 복귀 버튼이 챗으로 돌아갈 기준이 없고, 브라우저 back으로 돌아와도 어떤 세션을 보던 중이었는지 잃을 수 있었다
- 해결: `ChatPage` 가 현재 세션 id를 `?session=` query에 동기화하도록 맞추고, 연결 정책 카드에서 상세로 이동할 때 현재 `pathname` 과 `?session=${activeSessionId}` 를 `state.from` 으로 넘기게 정리했다. 이제 정책 상세에서 다시 챗으로 돌아와도 같은 세션을 복원할 수 있다. 이후 `cd frontend && npm run lint`, `cd frontend && npm run build` 를 다시 통과시켰다
- 이유: 챗은 단순 목록 화면이 아니라 현재 대화 상태가 핵심인 화면이다. 이런 화면은 “어느 페이지에서 왔는지”보다 “어느 세션을 보고 있었는지”까지 복구해야 체감상 같은 작업을 이어가는 흐름이 된다

## 331) 메인 `CTASection` 에서만 직접 로그인/회원가입 CTA가 옛 경로를 쓰면, 같은 화면 안에서도 인증 복귀 계약이 갈라진다
- 문제: `MainPage` 의 주요 비로그인 CTA들은 이미 `authState.from` 을 들고 인증 화면으로 가도록 바뀌었지만, 하단 `CTASection` 의 `1분만에 추천받기` 와 `로그인` 버튼은 여전히 `/signup`, `/login` 으로만 이동했다. 이 상태에서는 같은 메인 화면 안에서도 어떤 CTA를 눌렀느냐에 따라 복귀 문맥 보존 여부가 달라졌다
- 해결: `CTASection` 도 `authState` 를 받아 하단 `회원가입/로그인` 버튼이 현재 `pathname/search` 를 함께 들고 인증 화면으로 이동하게 맞췄다. 이후 `cd frontend && npm run lint`, `cd frontend && npm run build` 를 다시 통과시켰다
- 이유: 같은 페이지에 있는 동등한 CTA들은 같은 계약을 따라야 한다. 일부만 최신 복귀 규칙을 쓰고 일부는 예전 규칙을 쓰면, 사용자는 버튼마다 결과가 달라지는 비일관성을 먼저 체감하게 된다

## 330) `login-required` 안내가 모든 진입점을 `챗봇`으로만 설명하면, 사용자는 왜 로그인 화면에 왔는지 실제 문맥과 다르게 이해한다
- 문제: `LoginPage` 는 `reason === "login-required"` 일 때 항상 `챗봇은 로그인 후 이용 가능합니다.` 라는 toast를 띄웠다. 하지만 실제 진입점은 `마이페이지`, 정책 상세의 자격 확인, 기타 보호 액션도 포함하고 있었기 때문에, 로그인 화면 안내 문구가 실제 목적지와 자주 어긋났다
- 해결: `LoginPage` 가 `location.state.from.pathname` 을 읽어 `/chat` 은 챗봇 전용 문구, `/mypage` 는 마이페이지 전용 문구, 그 외는 일반 `로그인 후 이용 가능한 기능입니다.` 안내를 띄우게 정리했다. 이후 `cd frontend && npm run lint`, `cd frontend && npm run build` 를 다시 통과시켰다
- 이유: 로그인 유도 문구는 사용자가 지금 무엇을 하려다가 막혔는지 정확히 설명해야 한다. 진입 문맥과 맞지 않는 고정 문구는 복귀 UX를 오히려 더 혼란스럽게 만든다

## 329) 마이페이지가 비로그인 시 `/login` 으로만 떨어지면, 강제 로그인 후 원래 탭과 계정 복구 문맥이 끊긴다
- 문제: `MyPage` 는 보호 페이지인데도 `isLoggedIn` 이 false가 되면 `navigate("/login")` 만 호출했다. 이 상태에서는 직접 `/mypage?tab=...` 로 들어오거나 비밀번호 변경 후 재로그인이 필요한 경우에도, 로그인 화면이 현재 마이페이지 탭 문맥을 잃고 일반 진입처럼 보이게 된다
- 해결: 비로그인 강제 이동 시 현재 `pathname/search` 와 `reason: "login-required"` 를 함께 `/login` state로 넘기도록 맞췄고, 비밀번호 변경 후 재로그인도 현재 계정 탭을 `from` 으로 보존하면서 `reason: "password-reset-complete"` 와 이메일을 같이 전달하게 정리했다. 이후 `cd frontend && npm run lint`, `cd frontend && npm run build` 를 다시 통과시켰다
- 이유: 보호 페이지와 계정 복구 플로우는 현재 위치를 가장 강하게 보존해야 하는 구간이다. 특히 탭형 화면은 어떤 탭에서 인증이 끊겼는지 잃어버리면 복귀 체감이 크게 나빠진다

## 328) 메인 비로그인 CTA가 `/login`/`/signup` 으로만 이동하면, 자발적으로 인증을 시작한 사용자도 현재 탐색 문맥을 잃는다
- 문제: `MainPage` 의 비로그인 히어로 `맞춤 추천 받기`, 추천 섹션의 `로그인하기`/`회원가입` 버튼은 모두 인증 화면으로 직접 이동했지만, 현재 메인 화면의 `pathname/search` 를 state로 넘기지 않았다. 이 상태에서는 사용자가 메인에서 로그인이나 회원가입을 시작해도 이후 복귀 흐름이 일반 direct entry와 구분되지 않았다
- 해결: 메인 페이지에서 인증 화면으로 이동하는 비로그인 CTA들이 공통 `authState.from` 을 들고 가도록 바꿨다. 이제 로그인/회원가입을 거친 뒤에도 현재 탐색 세션을 같은 문맥으로 이어갈 수 있다. 이후 `cd frontend && npm run lint`, `cd frontend && npm run build` 를 다시 통과시켰다
- 이유: 보호 기능 진입뿐 아니라 사용자가 스스로 시작한 인증 플로우도 결국 현재 세션 위에서 이어지는 행동이다. 인증 화면으로 들어가는 모든 CTA가 같은 복귀 계약을 따라야 동작 규칙이 예측 가능해진다

## 327) 헤더의 직접 `로그인` 버튼이 현재 위치를 기억하지 않으면, 전역 네비게이션만 복귀 규칙이 다른 예외가 된다
- 문제: `Header` 의 비로그인 `로그인` 버튼은 단순히 `/login` 으로만 이동했고, 같은 헤더 안의 `마이페이지`/`챗봇` 버튼은 이미 별도 `from` 계약을 갖고 있었다. 이 상태에서는 공개 페이지에서 헤더로 로그인한 사용자만 현재 화면 문맥을 잃는 예외가 생길 수 있었다
- 해결: `Header` 가 현재 `location.pathname/search` 를 읽어 직접 `로그인` 버튼도 `state.from` 과 함께 `/login` 으로 이동하게 맞췄다. 이후 `cd frontend && npm run lint`, `cd frontend && npm run build` 를 다시 통과시켰다
- 이유: 전역 헤더는 어느 화면에서나 같은 규칙으로 동작해야 한다. 어떤 진입점은 현재 페이지를 기억하고 어떤 진입점은 잃어버리면, 인증 복귀 UX가 화면마다 들쭉날쭉해진다

## 326) 비밀번호 재설정 완료 후 `/login` 으로만 돌아가면, 원래 보호 페이지 복귀 문맥과 새 비밀번호 안내가 함께 끊긴다
- 문제: `ResetPasswordPage` 는 로그인 화면에서 넘어온 이메일/`from` 문맥을 보존하지 않았고, 비밀번호 변경이 끝나도 단순히 `/login` 으로만 이동했다. 이 상태에서는 사용자가 특정 페이지 진입 도중 비밀번호 재설정을 거쳐도 원래 목적지 복귀가 끊기고, 로그인 화면도 일반 진입과 구분되지 않았다
- 해결: `ResetPasswordPage` 가 `location.state.email` 로 이메일을 미리 채우고, `로그인으로 돌아가기` 및 비밀번호 변경 완료 후 `/login` 이동 시 `from`, `email`, `reason: "password-reset-complete"` 를 함께 넘기도록 정리했다. `LoginPage` 도 이 reason을 읽어 새 비밀번호 로그인 안내 toast를 보여주게 맞췄다. 이후 `cd frontend && npm run lint`, `cd frontend && npm run build` 를 다시 통과시켰다
- 이유: 비밀번호 재설정은 인증 실패의 복구 절차이므로, 끝난 뒤 다시 일반 로그인 진입처럼 취급하면 사용자는 왜 여기로 왔는지 맥락을 잃는다. 복구 플로우도 원래 보호 페이지 복귀 계약 안에서 움직여야 한다

## 325) 로그인 화면에서 회원가입으로 갔다가 돌아올 때 원래 `from` 을 잃으면, 가입 후 로그인해도 처음 보던 화면으로 못 돌아간다
- 문제: `LoginPage` 의 `회원가입`/`비밀번호 찾기` 버튼은 현재 로그인 화면이 들고 있던 `from` 을 다음 화면으로 넘기지 않았고, `SignupPage` 도 가입 완료 후 `/login` 으로 돌아올 때 `reason`, `email`, `signupPriorities` 만 남기고 원래 목적지를 버렸다. 그래서 `정책 상세 -> 로그인 -> 회원가입 -> 로그인` 흐름에서는 가입이 끝난 뒤에도 원래 정책 문맥이 사라질 수 있었다
- 해결: `LoginPage` 는 `회원가입`/`비밀번호 찾기` 이동 시 현재 `from` 을 전달하고, `SignupPage` 는 가입 완료 및 하단 `로그인` 이동 시 그 `from` 을 다시 `/login` state로 넘기도록 맞췄다. 이제 가입을 거친 뒤에도 로그인 완료 시 원래 목적지로 복귀할 수 있다. 이후 `cd frontend && npm run lint`, `cd frontend && npm run build` 를 다시 통과시켰다
- 이유: 로그인은 인증 단계일 뿐이고, 회원가입은 그 전 단계일 뿐이다. 둘 사이를 오가는 동안 원래 탐색 목적지를 잃지 않아야 사용자가 긴 우회 플로우를 거쳐도 같은 작업을 이어갈 수 있다

## 324) 정책 상세의 `자격 확인하기` CTA가 비로그인 사용자를 그냥 `/login` 으로만 보내면, 로그인 뒤 원래 보던 정책 문맥이 끊긴다
- 문제: `PolicyDetailPage` 우측 사이드바의 `자격 확인하기` 버튼은 로그인 사용자를 `/mypage` 로 보내지만, 비로그인 사용자는 현재 상세 경로를 기억하지 않고 단순히 `/login` 으로만 이동시켰다. 이 상태에서는 사용자가 특정 정책을 보다가 자격 확인을 위해 로그인해도, 로그인 완료 후 원래 정책으로 복귀하지 못하고 홈으로 떨어질 수 있었다
- 해결: 비로그인 상태에서 이 CTA를 누르면 현재 상세의 `pathname/search` 를 `from` 으로 넘기고 `reason: "login-required"` 도 함께 전달하게 바꿨다. 이제 로그인 후에는 원래 보던 정책 상세로 복귀한다. 이후 `cd frontend && npm run lint`, `cd frontend && npm run build` 를 다시 통과시켰다
- 이유: 상세 페이지의 CTA는 현재 보고 있던 정책 맥락 안에서 이어지는 행동이다. 로그인은 그 흐름의 중간 단계여야지, 탐색 세션을 초기화하는 종착점이 되면 안 된다

## 323) 헤더의 비로그인 `마이페이지` 버튼이 `/login` 으로만 가면, 로그인 후 사용자가 기대한 보호 페이지 복귀가 끊긴다
- 문제: `Header` 의 비로그인 `마이페이지` 버튼은 안내 toast 뒤 `/login` 으로만 이동했다. 반면 `챗봇` 버튼과 `RequireLogin` 은 이미 `from` 과 `reason` 을 함께 넘기고 있었기 때문에, 헤더 안에서도 보호 기능별 로그인 복귀 계약이 서로 달랐다
- 해결: 비로그인 `마이페이지` 이동도 `/login` 으로 보낼 때 `from: { pathname: "/mypage" }`, `reason: "login-required"` 를 같이 넘기도록 맞췄다. 이후 `cd frontend && npm run lint`, `cd frontend && npm run build` 를 다시 통과시켰다
- 이유: 같은 전역 네비게이션 안에서는 보호 기능 진입 계약이 일관돼야 한다. 어떤 버튼은 로그인 후 원래 목적지로 돌아가고, 어떤 버튼은 홈으로 떨어지면 사용자는 동작 규칙을 신뢰하기 어렵다

## 322) 정책 상세에서 연관 정책으로 연속 진입할 때 `from` 을 넘기지 않으면, 뒤로가기 기준이 원래 목록이 아니라 중간에 끊기거나 `/policies` 로 떨어진다
- 문제: `PolicyDetailPage` 는 목록/메인/마이페이지에서 들어올 때는 `location.state.from` 을 받아 상단 `뒤로가기` 를 복원하지만, 상세 하단 `비슷한 정책` 카드는 다음 상세로 이동할 때 이 기준점을 다시 넘기지 않았다. 그래서 `목록 -> 상세 A -> 비슷한 정책 B` 흐름에서는 B 상세가 원래 목록 query를 잃고, direct entry detail에서는 이전 detail 체인도 복원되지 않았다
- 해결: 연관 정책 이동 시 기존 `backTarget` 이 있으면 그대로 계승하고, 없으면 현재 상세의 `pathname/search` 를 fallback `from` 으로 넘기게 바꿨다. 이제 chained detail 이동에서도 `뒤로가기` 기준점이 유지된다. 이후 `cd frontend && npm run lint`, `cd frontend && npm run build` 를 다시 통과시켰다
- 이유: 상세 간 이동은 새 화면 진입이지만 사용자는 여전히 같은 탐색 세션 안에 있다고 느낀다. 이런 경우 최초 복귀 기준점이나 직전 상세를 끊지 않고 계승해야 브라우저 체감과 맞는다

## 321) 정책 검색이 query string은 쓰면서 브라우저 `back/forward` 로 바뀐 URL을 다시 읽지 않으면, 필터/정렬/페이지 복원이 깨진다
- 문제: `PoliciesPage` 는 검색어, 카테고리, 지역, 정렬, 페이지를 URL query로 내보내고 있었지만, 컴포넌트 state는 최초 mount 때만 `searchParams` 를 읽었다. 이 상태에서는 같은 페이지 안에서 브라우저 `back/forward` 로 query가 바뀌어도 화면 state와 fetch 기준이 그대로 남아 `URL과 실제 목록/필터 UI가 어긋나는` 회귀가 생길 수 있었다
- 해결: `PoliciesPage` 에 `searchParams -> local state` 동기화 effect를 추가해, URL이 바뀌면 검색어/필터/정렬/페이지/페이지 크기를 다시 읽어 로컬 state와 fetch 기준을 맞추도록 정리했다. 이후 `cd frontend && npm run lint`, `cd frontend && npm run build` 를 다시 통과시켰다
- 이유: query string 을 공식 상태로 노출하는 화면은 write path만으로는 부족하다. 브라우저 history가 바꾼 URL도 같은 우선순위로 read 해야 뒤로가기/재진입 QA를 통과할 수 있다

## 320) 마이페이지 `연결된 계정` 버튼이 실제 연동 경로 없이 `연결하기` 로 보이면, 사용자는 준비되지 않은 기능을 실제 액션으로 오해한다
- 문제: `MyPage` 계정 탭은 Google/네이버/카카오 항목마다 `연결하기` 버튼을 렌더링했지만, 이 버튼에는 `onClick` 도 연동 API도 없었다. 즉 사용자는 눌러서 연결할 수 있다고 믿지만 실제로는 아무 반응도 없는 전형적인 fake action UI였다
- 해결: `연결된 계정` 섹션 설명을 현재 계약에 맞게 `연결 상태만 안내, 연동 기능은 준비 중` 으로 바꾸고, 각 버튼도 `준비 중` 비활성 상태로 내려 실제 capability와 화면 표현을 맞췄다. 이후 `cd frontend && npm run lint`, `cd frontend && npm run build` 를 다시 통과시켰다
- 이유: 아직 없는 기능은 감추거나 비활성화해야 한다. 빈 버튼을 남겨 두면 QA에서는 클릭 실패로 기록되고, 사용자 관점에서는 기능 고장과 준비 중 상태를 구분할 수 없게 된다

## 319) 마이페이지 `reloginModal` 이 열리는 경로 없이 남아 있으면, 보안 UX처럼 보이지만 실제로는 도달 불가 dead UI다
- 문제: `MyPage` 에는 `reloginModal` state, `handleRelogin()`, 재로그인 안내 `Dialog` 가 있었지만, 상태를 `true` 로 바꾸는 경로가 코드 어디에도 없었다. 비밀번호 변경은 이미 toast 후 logout redirect로 끝나므로, 이 모달은 수동 QA에서도 절대 볼 수 없는 죽은 분기였다
- 해결: 사용되지 않는 `reloginModal` state, handler, dialog markup을 제거해 실제 세션 갱신 계약과 화면 코드를 일치시켰다. 이후 `cd frontend && npm run lint`, `cd frontend && npm run build` 를 다시 통과시켰다
- 이유: 보안 관련 UI는 특히 “언젠가 쓰일 것 같은 잔여 코드”를 남기면 안 된다. 현재 계약이 즉시 로그아웃 복귀라면, 도달 불가 모달보다 명시적인 단일 흐름이 유지보수와 검증에 낫다

## 318) 마이페이지 `필터 기본값` 이 store에만 저장되고 `/policies` 기본 상태가 그 값을 안 읽으면, “다음 접속부터 적용”이라는 안내가 거짓이 된다
- 문제: `MyPage` 필터 탭은 `includeExpired` 를 로컬 store에 저장하고 “저장된 설정은 다음 접속부터 적용돼요”라고 안내하지만, `PoliciesPage` 는 초기 `statusFilter` 를 항상 `"신청가능"` 으로만 시작했다. 이 상태에서는 사용자가 `종료된 정책 보기` 를 켜고 저장해도 다음에 정책 검색에 들어가면 아무 변화가 없어, 저장 UI가 실제로는 dead write가 된다
- 해결: `PoliciesPage` 가 `useAuthStore().filterSettings.includeExpired` 를 읽어, query param이 없을 때 기본 `statusFilter` 를 `전부표기` 또는 `신청가능` 으로 시작하게 연결했다. 필터 초기화도 같은 기본값을 따르게 맞췄고, 이후 `cd frontend && npm run lint`, `cd frontend && npm run build` 를 다시 통과시켰다
- 이유: 로컬 설정은 서버 저장이 아니어도 괜찮지만, 최소한 실제 소비처 하나는 있어야 한다. 특히 “다음 접속부터 적용”처럼 사용자 기대를 만드는 UI는 read path가 연결되지 않으면 바로 신뢰를 잃는다

## 317) 마이페이지 북마크의 정렬/보기 토글이 상태만 바꾸고 렌더링엔 반영되지 않으면, 사용자는 조작이 먹었다고 믿지만 화면은 그대로다
- 문제: `MyPage` 북마크 탭에는 `최신순/마감임박순`, `list/grid` 컨트롤이 있었지만, 실제 렌더링은 항상 원본 `bookmarks` 배열을 같은 list 레이아웃으로만 그렸다. 이 상태에서는 UI가 반응해 보여도 결과 화면이 바뀌지 않아 설정이 저장되거나 적용된 것처럼 오해하기 쉽다
- 해결: 북마크 표시용 `displayedBookmarks` 정렬 경로를 추가해 `마감임박순`일 때 `D-Day -> 긴급 D-n -> 나머지` 순으로 먼저 정렬하고, 기본은 applyEndDate 최신순으로 정리했다. 또 `bookmarkView === "grid"` 일 때는 실제 카드 grid 레이아웃으로 렌더링하도록 분기해 토글이 눈에 보이는 변화로 이어지게 맞췄다. 이후 `cd frontend && npm run lint`, `cd frontend && npm run build` 를 다시 통과시켰다
- 이유: 사용자 설정 UI는 실제 표시 결과를 바꾸지 않으면 “고장난 기능”보다 더 나쁘다. 조작은 되는데 효과가 없으면 QA도 통과시키기 어렵고, 저장/복원 계약까지 흐릿해진다

## 316) 정책 검색에 고용상태 필터 UI가 있는데 API 요청엔 안 들어가면, 사용자는 필터가 적용됐다고 믿고 전혀 다른 결과를 보게 된다
- 문제: `PoliciesPage` 는 `취업상태` 필터를 state, URL query, active chip까지 모두 유지하고 있었지만, 실제 `/api/policies`, `/api/policies/search` 요청에는 이 값이 한 번도 전달되지 않았다. 즉 화면상으로는 필터가 걸린 것처럼 보여도 결과 목록은 그대로라서 전형적인 fake filter 상태였다
- 해결: 현재 backend 목록/검색 계약이 지원하지 않는 `취업상태` 필터 UI와 관련 query param/state/chip 로직을 `PoliciesPage` 에서 제거했다. 이제 정책 검색 화면은 실제 API가 이해하는 필터만 노출하고, 사용자가 적용된 줄 착각하는 가짜 조건은 남지 않는다. 변경 후 `cd frontend && npm run lint`, `cd frontend && npm run build` 를 다시 통과시켰다
- 이유: 지원하지 않는 필터를 “일단 UI만” 남겨두는 건 빈 화면보다 더 위험하다. 결과가 달라졌다고 사용자가 믿는 순간, 검색 품질 문제인지 UI 문제인지 구분도 어려워진다

## 315) 북마크 일관성을 말로만 확인하면, `추천/검색/상세/마이페이지` 중 한 화면이 상태를 놓쳐도 다음 리팩토링에서 다시 새어 나간다
- 문제: 북마크는 같은 serviceId 기준으로 메인 추천 카드, 정책 검색 목록, 정책 상세, 마이페이지 북마크 목록이 같은 상태를 보여야 한다. 하지만 기존 local validation suite에는 이 전파를 자동으로 보는 step이 없어서, 화면 하나가 `bookmarked` 필드를 놓쳐도 실제 회귀는 브라우저 수동 확인 전까지 드러나지 않을 수 있었다
- 해결: `deploy/smoke/run-local-bookmark-consistency-smoke.sh` 를 추가해 `signup -> login -> priorities update -> recommendations refresh -> first recommendation bookmark on/off -> recommendations/search/detail/bookmarks` 를 한 번에 검증하게 만들고, `run-local-validation-suite.sh` quick/full 기본 프로필에도 `bookmark consistency smoke` 단계를 편입했다. 검증으로 `deploy/smoke/run-local-validation-from-env.sh --only bookmark`, `--quick` 를 다시 실행해 모두 통과했고, runtime smoke 문서도 현재 순서에 맞게 갱신했다
- 이유: 북마크는 write는 한 번인데 read surface는 여러 곳이다. 이런 상태 전파는 UI 한 군데만 보고는 안전하지 않아서, serviceId 기준 계약을 smoke로 고정해 두는 편이 리팩토링 회귀를 가장 빨리 잡는다

## 314) 메인 추천 카드가 backend의 `isBookmarked` 를 버리면, 추천 화면만 북마크 상태를 모르는 별도 세계가 된다
- 문제: `RecommendationResponse` 는 이미 `isBookmarked` 를 내려주는데, `frontend/src/pages/MainPage.jsx` 의 `mapRec()` 는 이 필드를 버리고 있었다. 그 결과 메인 추천 카드에서는 같은 정책이 검색/상세/마이페이지에서 북마크된 상태여도 표시나 토글이 없어서, QA checklist의 `메인/목록/상세/마이페이지 일관성` 요구를 충족하지 못했다
- 해결: 메인 추천 카드 모델에 `bookmarked` 를 포함시키고, 카드 우측에 북마크 토글 버튼을 추가했다. 토글은 `POST /api/policies/{serviceId}/bookmark` 를 사용해 다른 화면과 같은 command 경계를 타도록 맞췄고, 성공 시 로컬 추천 카드 상태도 즉시 뒤집히게 정리했다. 이후 `cd frontend && npm run lint`, `cd frontend && npm run build` 를 다시 통과시켰다
- 이유: 같은 `serviceId` 를 여러 화면이 소비할 때 한 화면만 `bookmarked` 필드를 무시하면, 사용자는 "저장은 됐는데 왜 여기만 다르지?" 상태가 된다. 이런 경우는 backend보다 frontend projection 누락이 문제이므로, read model을 그대로 살려주는 편이 맞다

## 313) `__authExpired` 핸들러를 보호 라우트에서만 설치하면, 공개 화면에서 북마크 같은 인증 호출이 만료될 때 로그인 복귀 없이 그냥 실패로 끝난다
- 문제: `axios` refresh 실패 시 `window.__authExpired?.()` 를 호출하지만, 이 핸들러는 기존에 `RequireLogin` 안에서만 설치됐다. 그래서 `/policies`, `/policies/:id`, 메인처럼 공개 라우트에 머무르는 로그인 사용자가 북마크 등 인증 API를 호출하다 세션이 만료되면, local token만 지워지고 공개 페이지에 남은 채 "북마크 처리 실패"처럼 보일 수 있었다
- 해결: `frontend/src/components/AuthExpiryHandler.jsx` 를 추가하고, `frontend/src/router/index.jsx` 의 모든 라우트 element를 이 전역 핸들러로 감쌌다. 이제 로그인 사용자는 공개/보호 라우트 구분 없이 refresh 실패 시 현재 위치를 `from` 으로 들고 `/login` 으로 이동하며, `reason: "expired"` toast도 같은 기준으로 노출된다. 변경 후 `cd frontend && npm run lint`, `cd frontend && npm run build` 를 다시 통과시켰다
- 이유: 세션 만료 UX는 "보호 페이지에서만 맞는" 부분 구현으로 두면 안 된다. 로그인 상태에서 인증 API를 칠 수 있는 모든 화면이 같은 만료 계약을 따라야 사용자가 어디서든 동일하게 복구할 수 있다

## 312) 정책 상세에 실제 뒤로가기 경로가 없고 마이페이지 탭 상태도 URL에 안 남으면, `목록/북마크 -> 상세 -> 복귀` 요구를 만족시킬 수 없다
- 문제: `PolicyDetailPage` 에는 `navigate(-1)` 또는 원위치 복귀 버튼이 없었고, `MainPage`/`PoliciesPage`/`MyPage` 는 상세 진입 시 출발 위치를 넘기지 않았다. 특히 `MyPage` 는 active tab을 내부 state로만 들고 있어 `북마크 탭 -> 상세 -> 복귀`를 명시적으로 복원할 URL 기준점도 없었다
- 해결: `MainPage`, `PoliciesPage`, `MyPage` 가 상세 진입 시 현재 `pathname/search` 를 `location.state.from` 으로 넘기도록 바꾸고, `PolicyDetailPage` 상단에 이 값을 사용하는 `뒤로가기` 버튼을 추가했다. 동시에 `MyPage` 는 활성 탭을 `?tab=` 쿼리와 양방향 동기화해 북마크 탭이 URL로도 유지되게 맞췄다. 이후 `cd frontend && npm run lint`, `cd frontend && npm run build` 를 다시 통과시켰다
- 이유: 브라우저 back에만 기대면 앱 내부 CTA, 새 탭 진입, 마이페이지 탭 상태 복원 요구를 제대로 만족시키기 어렵다. 상세 페이지가 출발 위치를 알고 있어야 checklist 수준의 복귀 UX를 안정적으로 보장할 수 있다

## 311) QA checklist엔 있는데 local validation suite엔 `공개 정책 탐색 / profile·priorities / chat CRUD` 가 빠져 있으면, 핵심 사용자 흐름이 다시 수동 검증으로만 남는다
- 문제: 기존 `run-local-validation-suite.sh` 는 auth/session, recommendation click, admin dashboard, replay는 자동화했지만, 브라우저 QA 체크리스트의 핵심 구간인 공개 정책 list/search/detail, 프로필 조회/우선순위 저장, 챗 세션 생성/메시지 전송/삭제는 자동 검증에 포함하지 않았다. 이 상태면 리팩토링 뒤에도 가장 자주 만지는 화면 흐름 일부가 사람 손 회귀에만 의존하고, 체크리스트와 실제 validation baseline이 점점 어긋난다
- 해결: `deploy/smoke/run-local-public-profile-chat-smoke.sh` 를 추가해 `public policies list -> public search -> public detail -> signup -> login -> profile get -> priorities update -> chat create/list/send/get/delete` 를 한 번에 검증하게 만들고, `run-local-validation-suite.sh` 의 기본 quick/full 프로필에 새 `public-chat` 단계를 편입했다. 이후 `deploy/smoke/run-local-validation-from-env.sh --only public-chat` 과 `--quick` 를 다시 실행해 둘 다 통과했고, `docs/core/runtime-api-smoke-commands.md`, `docs/frontend/frontend-qa-checklist.md` 도 현재 기준선에 맞게 동기화했다
- 이유: 로컬 서비스 단계에서는 운영 트래픽보다 회귀 탐지 속도가 더 중요하다. 공개 탐색, 프로필, 챗은 프론트/백엔드 계약이 자주 만나는 경계라서, smoke 기준선에서 빠지면 리팩토링 안정성이 급격히 떨어진다

## 310) 프론트 production build가 계속 단일 chunk 경고를 내면, 정적 검사는 green이어도 초기 로드 비용과 배포 기준선이 불안정하게 남는다
- 문제: `cd frontend && npm run build` 는 통과했지만, 기존 구조에서는 주요 페이지가 `index` 번들에 한꺼번에 묶여 Vite의 chunk size warning이 남아 있었다. 로컬 validation과 smoke는 전부 통과한 상태였지만, 이 경고를 그대로 두면 리팩토링 이후에도 "정적 검사는 성공인데 배포 번들 기준선은 아직 미정리" 상태가 계속 남는다
- 해결: `frontend/src/router/index.jsx` 의 페이지 진입점을 `React.lazy` 기반 route split 구조로 바꾸고, `frontend/src/router/LazyRoute.jsx`, `frontend/src/router/lazy-pages.jsx` 로 suspense/lazy 경계를 분리했다. 동시에 `frontend/vite.config.js` 에 `manualChunks` 를 추가해 `mui-vendor`, `react-vendor`, `data-vendor`, `vendor` 로 vendor 청크를 나눴다. 이후 `cd frontend && npm run lint`, `cd frontend && npm run build` 를 다시 돌려 lint/build 모두 통과했고, build output에서도 페이지별 청크와 vendor 분리가 확인되며 기존 chunk size warning이 사라졌다
- 이유: 지금은 아직 유저 없는 로컬 서비스라 성능 미세튜닝보다 기능 검증이 우선이지만, 이미 경고가 재현되는 상태라면 리팩토링 검증 루프 안에서 같이 닫아 두는 편이 맞다. 라우트 단위 split과 vendor 분리는 기능 계약을 건드리지 않으면서도 초기 번들 리스크를 가장 직접적으로 줄인다

## 309) full validation suite의 replay 단계가 상위 wrapper의 `APP_BASE_URL=8082` 를 물고 들어가 자기 전용 app 종료를 기다리다 멈췄음
- 문제: `run-local-validation-from-env.sh --full` 은 quick 구간까지 통과했지만 replay 단계에서 `app did not shut down within 30s` 로 멈췄다. 원인은 `run-local-validation-from-env.sh` 가 validation용 API base URL로 `APP_BASE_URL=http://127.0.0.1:8082` 를 export 하고, `run-local-validation-suite.sh` 가 이 값을 replay 단계에도 그대로 넘긴 데 있었다. `run-local-education-priority-replay.sh` 는 원래 독립 `bootRun` 인스턴스를 `18082` 에 띄우도록 설계돼 있는데, preset `APP_BASE_URL` 이 있으면 그 값을 우선 사용하므로 기존 Docker app `8082` 와 자기 프로세스를 구분하지 못했다
- 해결: `run-local-validation-suite.sh` 가 replay 단계 실행 전에 기본적으로 `APP_BASE_URL`, `APP_HEALTH_URL` 을 unset 하도록 바꿨다. 필요하면 `REPLAY_APP_BASE_URL` 로 replay 전용 URL만 별도 주입할 수 있게 했고, help와 runtime smoke 문서에도 이 계약을 반영했다. 이후 `run-local-validation-from-env.sh --only replay` 와 `--full` 을 다시 돌려 둘 다 통과했다
- 이유: quick smoke와 replay smoke는 같은 “validation suite” 안에 있어도 런타임 전제가 다르다. quick는 이미 떠 있는 API app을 치고, replay는 자기 own app을 띄운다. 상위 wrapper가 하나의 `APP_BASE_URL` 을 전역으로 강제하면 두 실행 모델이 충돌한다

## 308) admin dashboard smoke가 `windowDays` KeyError로 깨졌지만 실제 원인은 smoke 스크립트보다 오래된 Docker app 컨테이너였음
- 문제: `run-local-validation-from-env.sh --quick` 에서 auth/session, recommendation click은 통과했지만 admin dashboard 단계가 `KeyError: 'windowDays'` 로 깨졌다. 저장소의 `AdminDashboardResponse` / `AdminDashboardSummaryService` 는 `collect.windowDays` 계약을 이미 쓰고 있었는데, 실제 `http://127.0.0.1:8082/api/admin/dashboard/summary` 응답은 `collect.failureWindowDays` 를 내려주고 있어 소스와 런타임이 어긋나 있었다
- 해결: 로컬 Docker app 컨테이너가 이전 빌드로 떠 있던 것으로 보고 `docker compose up -d --build app` 으로 최신 소스를 다시 반영했다. 이후 `run-local-validation-from-env.sh --only dashboard` 와 `--quick` 이 모두 통과해 dashboard contract가 현재 소스 기준으로 다시 맞는 것을 확인했다
- 이유: integration test는 로컬 Gradle 컨텍스트로 최신 코드를 직접 태우지만, runtime smoke는 `8082`의 Docker app을 본다. 백엔드 contract가 바뀐 뒤 app 이미지를 재기동하지 않으면 “코드는 맞는데 smoke만 틀리는” 가짜 회귀가 생긴다

## 307) `.env` 의 `APP_BASE_URL` 이 프론트 origin일 때 `run-local-validation-from-env.sh` 가 smoke health check를 `5173/actuator/health` 로 보내고 있었음
- 문제: 로컬 `.env` 에 `APP_BASE_URL=http://127.0.0.1:5173` 이 들어 있는데, `run-local-validation-from-env.sh` 가 이를 그대로 export 한 채 smoke wrapper를 실행했다. 그 결과 quick suite 첫 단계에서 health check가 백엔드 `8082` 가 아니라 프론트 dev origin `5173` 를 쳐 `curl: (7) Failed to connect` 로 바로 실패했다
- 해결: wrapper가 시작 시 `APP_BASE_URL` preset 여부를 먼저 기억하고, 사용자가 명시적으로 주지 않은 경우에는 validation 전용 기본값 `http://127.0.0.1:8082` 또는 `VALIDATION_APP_BASE_URL` 을 `APP_BASE_URL` 로 다시 고정하게 바꿨다. `docs/core/runtime-api-smoke-commands.md` 에도 `.env`의 프론트 `APP_BASE_URL` 과 로컬 API smoke base URL 을 구분하는 기준을 추가했다
- 이유: 앱 런타임에서 `APP_BASE_URL` 은 프론트 링크/redirect 용으로도 쓰이지만, smoke는 백엔드 API endpoint가 필요하다. `.env` 를 그대로 주입하면 두 의미가 충돌하므로 validation wrapper가 명시적으로 분리해야 한다

## 306) 로컬 PostgreSQL 볼륨이 오래돼 chat schema 일부가 빠진 상태라 integration runtime이 살아 있어도 broad suite가 전부 막혔음
- 문제: runtime preflight를 통과한 뒤 `./gradlew integrationTest --no-daemon` 를 다시 돌리자 연결 실패 대신 Hibernate schema validation 단계에서 전부 막혔다. 첫 원인은 `chat_messages.references_json` 누락이었고, 이를 맞춘 뒤에는 `chat_retrieval_snapshots.needs_clarification` 누락이 이어서 드러났다. 즉 현재 broad integration 실패는 코드 로직보다 “기존 Docker PostgreSQL 볼륨이 최신 `schema.sql` 이후 추가된 chat 컬럼을 아직 갖고 있지 않다”는 local schema drift였다
- 해결: `deploy/postgres/patches/` 에 idempotent PostgreSQL patch SQL을 추가하고, `deploy/postgres/apply-local-runtime-schema-patch.sh` 로 로컬 `youth-welfare-db` 컨테이너에 순서대로 적용하게 정리했다. 이번 라운드에서는 `V2026_05_14_01__add_chat_message_references_json.sql`, `V2026_05_14_02__add_chat_retrieval_snapshot_needs_clarification.sql` 를 적용한 뒤 `./gradlew integrationTest --no-daemon` 가 다시 전체 통과했다
- 이유: 현재 PostgreSQL main은 신규 초기화에 `schema.sql` 을 쓰지만, 이미 생성된 로컬 볼륨에는 그 변경이 자동 반영되지 않는다. broad suite를 살리려면 엔티티/`schema.sql`/기존 볼륨 drift를 함께 맞추는 로컬 patch 경로가 필요하다

## 305) integration runtime 부재가 있을 때 `integrationTest` 가 62건 테스트 실패처럼 보여 원인 파악 비용이 너무 컸음
- 문제: `backend`의 `integrationTest` 는 PostgreSQL/Redis runtime 전제 없이는 애플리케이션 컨텍스트가 뜨지 않는데도, 실행 초반에는 바로 막지 않고 auth/chat/recommendation/policy 전 영역 테스트가 연쇄 실패했다. 특히 WSL 세션에서 `docker` 명령 자체가 없거나 `127.0.0.1:5433`, `127.0.0.1:6379` 리스너가 비어 있을 때도 결과는 “62 tests failed” 로 보여 실제 코드 회귀처럼 읽히기 쉬웠다
- 해결: `backend/build.gradle` 에 `integrationRuntimePreflight` 태스크를 추가하고 `integrationTest` 가 여기에 먼저 의존하게 바꿨다. 이 preflight 는 `backend/src/test/resources/application-integration.yml` 기준 기대 runtime(`PostgreSQL 127.0.0.1:5433`, `Redis 127.0.0.1:6379`)에 소켓 연결을 먼저 확인하고, 없으면 `docker compose up -d db redis`, WSL Docker Desktop integration, 우회 플래그(`SKIP_INTEGRATION_RUNTIME_PREFLIGHT`, `-PskipIntegrationRuntimePreflight=true`)까지 포함한 명확한 안내와 함께 즉시 실패한다. `docs/core/testing.md` 에도 현재 기준선을 반영했다
- 이유: integration suite는 환경 전제 미충족과 실제 애플리케이션 회귀를 먼저 분리해야 한다. fail-fast 경계가 없으면 디버깅 시간이 전부 테스트 목록 정리에 소모되고, 같은 환경 실수를 반복하게 된다

## 304) ordered validation 라운드에서 integration 전체 실패처럼 보였지만 실제 원인은 로컬 PostgreSQL/Redis runtime 부재였음
- 문제: 리팩토링 후 검증 순서를 `backend test -> backend integrationTest -> frontend lint/build` 로 고정해 다시 돌렸더니 `./gradlew test --no-daemon` 은 통과했지만 `./gradlew integrationTest --no-daemon` 는 62건이 한 번에 실패했다. 실패 클래스는 auth/chat/recommendation/policy 전 영역에 퍼져 있었지만, 첫 원인은 모두 `jdbc:postgresql://127.0.0.1:5433/youth_welfare` 연결 단계의 `ConnectException` 이었다. 같은 세션에서 `docker` 명령 자체가 없었고, `postgres`/`pg_ctl`/`initdb`/`redis-server` 바이너리도 없었으며, `ss -ltn` 기준 `5433`, `6379` 리스너도 없었다
- 해결: 이번 라운드에서는 mass failure를 코드 회귀로 오판하지 않고 환경 차단으로 분리했다. 기준선은 `backend test` green, `frontend npm run lint` green, `frontend npm run build` green으로 확보하고, integration은 “로컬 PostgreSQL/Redis runtime 제공 전까지 blocked” 상태로 기록했다. 다음 실행 전에는 Docker Desktop WSL integration 또는 동일 포트의 로컬 PostgreSQL/Redis 기동 여부를 먼저 preflight 해야 한다
- 이유: integration suite는 도메인 폭이 넓어서 runtime이 없으면 전 영역이 동시에 붕괴한다. 이 상태를 애플리케이션 회귀로 읽으면 불필요한 코드 수정으로 이어지므로, 먼저 runtime availability를 고정해야 실제 리팩토링 문제를 구분할 수 있다

## 295) replay smoke와 auth/session smoke를 병렬로 돌리면 DB 재기동 간섭으로 거짓 `500/C002` 가 날 수 있었음
- 문제: `run-local-education-priority-replay.sh` 는 내부에서 `youth-welfare-db` 컨테이너를 재기동한다. 이걸 `run-local-auth-session-smoke.sh` 와 같은 타이밍에 돌리면 앱 쪽에서 `Connection is closed`, `Unable to rollback against JDBC Connection` 이 튀고, 실제 추천/API 회귀가 없어도 auth smoke가 `500/C002` 로 깨질 수 있었음
- 해결: 로컬 검증 기준을 `auth/session -> recommendation click -> replay` 순차 실행으로 다시 고정했다. 이후 순차 재실행에서는 auth/session smoke, recommendation click smoke, replay smoke가 모두 통과했다
- 이유: 지금 단계는 운영 검증이 아니라 로컬 검증 루프이므로, smoke 자체가 서로 실행 환경을 깨지 않는 순서가 중요하다. replay는 DB 재기동형이라 독립 실행으로 다뤄야 한다

## 296) `/api/admin/dashboard/summary` 가 한 번 `500/C002` 로 보였지만 실제 원인은 코드보다 로컬 admin allowlist 실행 조건이었음
- 문제: 대시보드 실응답을 보려 했을 때 `admin@example.com` login은 성공했지만 JWT `roles` 에 `ROLE_ADMIN` 이 빠져 있었다. fresh rebuild 뒤 로컬 Docker app 컨테이너의 `SECURITY_ADMIN_EMAILS` 가 비어 있었고, 이 상태에서는 `admin@example.com` 이 일반 사용자로만 로그인되어 `/api/admin/dashboard/summary` 검증이 실패할 수 있었음
- 해결: `SECURITY_ADMIN_EMAILS=admin@example.com docker compose up -d --force-recreate app` 로 app을 다시 띄운 뒤 대시보드 응답을 재확인했고, 이를 반복 가능하게 `run-local-admin-dashboard-smoke.sh` 로 묶었다. 이 smoke는 로그인 후 JWT `ROLE_ADMIN` 존재 여부를 먼저 확인하고, 누락 시 allowlist 재기동 명령을 바로 안내한다
- 이유: 현재 문제는 대시보드 로직 자체보다 “로컬 Docker app이 어떤 env로 떠 있느냐”에 좌우된다. 이 조건을 smoke가 먼저 체크해야 같은 혼선을 반복하지 않는다

## 297) 대시보드 추세가 1/7/30 고정이면 특정 기간의 collect/search/recommendation 패턴을 바로 보기 어려웠음
- 문제: `/api/admin/dashboard/summary` 는 trend를 `1/7/30` 윈도로만 내려줘 기본 관측은 충분했지만, 로컬 검증 중 `3일`, `14일` 같은 중간 창을 바로 비교해 보고 싶으면 코드를 바꾸거나 DB 쿼리를 직접 날려야 했다
- 해결: `trendWindowDays` query param을 추가해 요청이 들어온 기간 창만 계산하게 했고, `run-local-admin-dashboard-smoke.sh` 도 `TREND_WINDOW_DAYS_CSV` 를 받아 custom window 계약을 같이 검증하도록 맞췄다
- 이유: summary 계약 기본값은 유지하되, 운영/로컬 관측이 필요할 때만 창을 좁혀 보는 편이 cross-domain dashboard 구조를 흔들지 않고도 실용성이 높다

## 298) 대시보드의 일부 collect/search/recommendation 필드는 여전히 7일 고정이라 trend만 가변이고 summary는 고정이라는 어색한 계약이 남아 있었음
- 문제: `trendWindowDays` 는 커스터마이즈할 수 있게 됐지만, `collect.latestFailuresLast7d`, `recommendation.sentLast7d/clickedLast7d/fallbackLast7d/weightBucketsLast7d`, `search.zeroResultSearchesLast7d/topKeywordsLast7d` 는 여전히 7일 고정이었다. 그래서 같은 응답 안에서 trend는 `3/14일`로 보면서 summary는 항상 `7일`로 읽어야 하는 계약 불일치가 생겼다
- 해결: `summaryWindowDays` query param을 추가해 collect/search/recommendation summary window를 함께 제어하게 바꿨다. DTO 필드도 `latestFailuresInWindow`, `sentInWindow`, `zeroResultSearchesInWindow`, `weightBucketsInWindow` 같은 형태로 일반화했고, `run-local-admin-dashboard-smoke.sh` 도 `SUMMARY_WINDOW_DAYS` 를 받아 summary/trend 계약을 같이 검증하도록 확장했다
- 이유: 대시보드 응답은 “현재 요약 + 기간별 추세”를 같이 보여 주는 용도이므로, summary 기간 자체도 요청자가 선택 가능해야 trend와 함께 해석하기 쉽다

## 299) summary window를 도입한 뒤에도 notification 섹션만 7일 고정 명명과 해석을 유지해 대시보드 계약이 완전히 일관되지 않았음
- 문제: `summaryWindowDays` 를 추가한 뒤에도 `notification.sentLast7d/failedLast7d` 는 그대로 남아 있어, 같은 summary 응답에서 collect/recommendation/search는 요청한 기간으로 읽고 notification만 7일 고정으로 읽어야 하는 어색함이 남았다
- 해결: notification 섹션도 `windowDays`, `sentInWindow`, `failedInWindow` 로 바꾸고 `fetchNotificationSummary` SQL alias도 같은 window semantics로 맞췄다. 이로써 `/api/admin/dashboard/summary` 의 모든 summary 섹션이 같은 기간 파라미터 해석을 따르게 됐다
- 이유: 운영 대시보드는 특정 기간 창을 맞춰 놓고 섹션 간 비교를 해야 의미가 있다. notification만 별도 고정 기간이면 해석 비용이 다시 생긴다

## 300) recommendation 섹션에 active weight와 클릭 수는 있었지만 “다음 단계까지 얼마나 남았는지”가 없어 CTR/가중치 튜닝 readiness를 한 번에 읽기 어려웠음
- 문제: `/api/admin/dashboard/summary` recommendation 섹션은 이미 `activeWeightKey`, `totalLogs`, `clickedInWindow`, `weightBucketsInWindow` 를 내려주고 있었지만, 운영자가 지금 단계가 `GROWTH` 인지 아는 것과 “그럼 `STABLE` 까지 몇 로그가 더 필요한지”를 아는 것은 별개였다. 결국 `score_weights.min_log_count` 를 다시 떠올리거나 DB를 조회해야 했다
- 해결: `ScoreWeightService` 에 진행도 계산을 추가하고, recommendation 섹션에 `nextWeightKey`, `nextWeightMinLogCount`, `remainingLogsUntilNextWeight`, `topWeightStage` 를 노출했다. dashboard는 이제 score weight 규칙을 직접 재해석하지 않고 도메인 서비스가 계산한 진행도만 읽는다
- 이유: CTR/가중치 재조정 판단은 “최근 클릭 수”와 “현재 추천 로그 총량”이 같이 보여야 한다. 다음 단계까지 남은 로그 수를 바로 보여 주면 표본 부족과 weight stage 부족을 같은 화면에서 구분할 수 있다

## 301) recommendation weight progress를 대시보드에 추가한 뒤에도 로컬 smoke가 그 필드를 아예 검증하지 않아 계약 이탈을 놓칠 수 있었음
- 문제: `/api/admin/dashboard/summary` recommendation 섹션에 `nextWeightKey`, `remainingLogsUntilNextWeight`, `topWeightStage` 를 추가했지만, `run-local-admin-dashboard-smoke.sh` 는 여전히 window/카운트/추세만 확인하고 있었다. 이 상태면 DTO나 서비스가 progress 필드를 깨도 smoke는 계속 green 일 수 있었다
- 해결: admin dashboard smoke도 recommendation weight progress 계약을 같이 보게 바꿨다. top stage면 `nextWeight*` 가 `null` 이어야 하고, 아니면 `nextWeightKey`/`nextWeightMinLogCount`/`remainingLogsUntilNextWeight>=0` 가 있어야 한다는 조건을 추가했다
- 이유: 운영 관측 필드는 테스트만 통과해도 되는 게 아니라 로컬 smoke에서도 실제 JSON 계약을 다시 확인해야 한다. 그래야 대시보드 실응답과 테스트 fixture가 어긋날 때 바로 잡힌다

## 302) admin dashboard smoke가 앱 재빌드 직후 startup race를 흡수하지 못해 `curl: (56) Recv failure: Connection reset by peer` 로 자주 끊겼음
- 문제: `docker compose up -d --build app` 직후 `run-local-admin-dashboard-smoke.sh` 를 바로 실행하면, 앱 컨테이너는 `Started` 상태여도 Tomcat/JPA 초기화 막바지라 `/actuator/health` 에서 한두 번 `connection reset` 이 날 수 있었다. 스크립트는 health check를 단발로만 때려 이런 타이밍 이슈를 그대로 실패로 처리했다
- 해결: `HEALTH_RETRY_COUNT`, `HEALTH_RETRY_DELAY_SECONDS` 를 추가하고 health check를 짧게 재시도하게 바꿨다. 마지막 시도까지 실패한 경우에만 stderr와 health 응답을 같이 출력하도록 정리했다
- 이유: 이 문제는 대시보드 코드 버그가 아니라 로컬 Docker startup race다. smoke가 최소한의 retry를 가져야 반복 검증에서 불필요한 거짓 실패를 줄일 수 있다

## 303) 같은 startup race가 runtime/withdraw/recommendation-click/admin-forced-logout smoke에도 반복될 수 있었음
- 문제: `run-local-runtime-api-smoke.sh`, `run-local-recommendation-click-smoke.sh`, `run-local-admin-forced-logout-smoke.sh`, `run-local-withdraw-smoke.sh` 도 모두 `/actuator/health` 를 단발로만 확인하고 있어서, 앱 재기동 직후에는 admin dashboard smoke와 똑같이 `curl 56` 으로 바로 죽을 수 있었다
- 해결: 네 스크립트에도 같은 `wait_for_health` retry helper와 `HEALTH_RETRY_COUNT`, `HEALTH_RETRY_DELAY_SECONDS` env를 추가했다
- 이유: 이건 개별 smoke의 비즈니스 로직 문제가 아니라 공통적인 로컬 startup race다. 자주 쓰는 smoke들끼리는 같은 회복력 기준을 가져야 반복 검증이 덜 흔들린다

## 294) broad-suite self-heal cleanup이 안정화됐지만 user-prefix integration 테스트들에 거의 같은 정리 코드가 복제돼 다시 drift할 가능성이 있었음
- 문제: `AuthRedisIntegrationTest`, `AdminSecurityIntegrationTest`, `UserCoreDualWriteIntegrationTest`, `UserMetadataUserKeyBackfillIntegrationTest`, `UserPiiBackfillIntegrationTest`, `UserPiiSyncReplayIntegrationTest`, `UserPiiSyncRetrySchedulerIntegrationTest`, `RecommendationFlowIntegrationTest` 는 모두 “email prefix로 user를 찾고 userKey 파생 cleanup 후 user delete” 구조가 거의 같았는데, 세부 차이가 조금씩 있어 다음 hardening 때 일부 클래스만 갱신될 위험이 있었음
- 해결: test 전용 `IntegrationCleanupSupport` 를 추가하고 user-prefix cleanup, service-prefix cleanup 진입점을 공통화했다. 각 클래스는 이제 “무슨 추가 정리가 필요한가”만 람다로 넘기고, 사용자 탐색/삭제 흐름 자체는 한 곳에서 처리한다
- 이유: broad-suite 재현성 hardening은 한 번 패턴을 정했으면 다음 수정이 같은 방향으로 퍼질 수 있어야 한다. cleanup 보일러플레이트를 줄여야 drift와 누락 가능성도 같이 줄어든다

## 292) chat/withdraw integration 테스트 일부가 broad suite 중단 뒤 잔존 user/chat/refresh 상태를 전제로 다시 깨질 수 있었음
- 문제: `ChatSessionApiIntegrationTest`, `ChatMessageApiIntegrationTest`, `ChatRepositoryIntegrationTest`, `UserWithdrawChatCleanupIntegrationTest`, `UserWithdrawAccessTokenBaselineIntegrationTest` 는 prefix 기반 fixture를 쓰면서도 cleanup이 사실상 `@AfterEach` 또는 생성 중 추적한 ID 목록에 기대고 있었다. 이전 broad suite가 중간에 끊기면 stale user/chat session/`withdrawn_` email/legacy `refresh:{userId}` key 가 남아 다음 실행을 오염시킬 수 있었음
- 해결: 다섯 클래스 모두 `@BeforeEach` 에서 prefix 스캔 기반 self-heal cleanup을 먼저 돌리도록 맞췄고, withdraw 계열은 `withdrawn_` email 과 `refresh:{userKey}`, `refresh:{userId}` 레거시 키까지 같이 제거하게 정리했음
- 이유: broad suite 재현성 hardening의 핵심은 “이번 JVM에서 만든 ID만” 지우는 것이 아니라, 이전 실패 실행의 찌꺼기까지 시작 시점에 스스로 복구하는 구조다

## 293) sidecar/status integration 테스트 일부도 단일 생성 ID/목록 기반 cleanup만 써서 broad suite 중단 뒤 self-heal 성질이 부족했음
- 문제: `NormalizedPolicySidecarPersistenceIntegrationTest`, `BokjiroSidecarMergeIntegrationTest`, `UserPiiSyncStatusIntegrationTest` 는 각각 단일 `sourceId` 또는 생성한 `userKey` 목록만 `@AfterEach` 에서 치웠다. 이전 실행이 중간에 죽으면 prefix fixture가 DB에 남아 broad suite 재실행 때 다시 영향을 줄 수 있었음
- 해결: `YOUTH` sidecar는 `IT-SIDECAR-`, `BOKJIRO_LOCAL` sidecar는 `IT-BK-`, sync status queue는 `itpiis_` prefix 스캔 기반 cleanup으로 바꾸고 `@BeforeEach` self-heal cleanup을 추가했다. 작업 중 `user_key` 32자 제한 때문에 처음 쓴 긴 prefix가 `DataIntegrityViolationException` 을 내서, prefix를 7자로 줄이고 UUID suffix를 24자로 잘라 다시 통과시켰음
- 이유: broad suite 재현성 hardening은 source/status fixture에도 같은 규칙을 적용해야 한다. 시작 전에 prefix 전체를 스캔해 찌꺼기를 복구하는 편이 단일 생성 ID 추적보다 실패 복원력이 높다

## 291) `srs-v2.10.md`, `demo-scenario.md`, `archive/README.md` 도 아직 개별 direct 문서 중심이라 문서군 entrypoint 독법과 완전히 맞지 않음
- 문제: cross-cutting 독법은 거의 `docs-index` 중심으로 맞췄지만, 요구사항 원본인 `srs-v2.10.md`, 로컬 검증용 `demo-scenario.md`, 보관용 `archive/README.md` 는 각각 상위 entrypoint를 전혀 드러내지 않아 새 사용자가 같은 층위 문서를 찾을 때 다시 파일명을 직접 알아야 했음
- 해결: 각 문서 상단에 `system-docs-index.md`, `local-validation-docs-index.md`, `history-docs-index.md` 진입점을 먼저 보게 한 줄씩 추가했다
- 이유: 요구사항/데모/보관 문서도 현재 독법과 같은 패턴을 가져야 예외가 줄고, 문서군 단위로 읽는 흐름이 끝까지 유지된다

## 290) `db-migration.md`, `user-data-separation-design.md`, `chatbot-plan.md` 도 direct link 중심이라 system docs entrypoint 독법과 완전히 맞지 않음
- 문제: `project-spec.md`, `architecture.md`, `api-mapping.md` 는 `system-docs-index.md` 기준으로 맞췄지만, 나머지 cross-cutting reference 문서인 `db-migration.md`, `user-data-separation-design.md`, `chatbot-plan.md` 는 여전히 개별 direct link 중심이라 같은 문서군 안에서 진입 방식이 또 달라졌음
- 해결: 세 문서 상단에 `system-docs-index.md` entrypoint를 먼저 보게 한 줄씩 추가해 cross-cutting 문서군 전체의 독법을 맞췄음
- 이유: 구조/계약/DB/확장 설계 문서는 같은 층위에서 읽히므로, 일부만 docs-index를 알면 오히려 예외가 생긴다. 시스템 문서군 전체가 같은 entrypoint 규칙을 가져야 다음 사용자가 문서군을 한 번에 인식할 수 있다

## 289) `architecture.md`, `api-mapping.md` 도 직접 링크 중심이라 docs-index 독법과 완전히 맞지 않음
- 문제: top-level, `work-guide.md`, `project-spec.md` 는 `docs-index` 중심 독법으로 정리됐지만, `architecture.md` 와 `api-mapping.md` 는 여전히 개별 문서 direct link만 보여 줘 cross-cutting 진입점이 한 단계 덜 드러났음
- 해결: 두 문서 상단에 `system-docs-index.md` entrypoint를 먼저 보게 한 줄씩 추가해 현재 독법과 맞췄음
- 이유: 구조/API contract 문서도 같은 진입 패턴을 따라야 다음 사용자가 cross-cutting 문서를 찾을 때 예외를 따로 기억하지 않게 된다

## 288) `project-spec.md` 참고 문서는 아직 직접 파일 링크 중심이라 현재 docs-index 독법과 완전히 맞지 않음
- 문제: top-level과 work-guide는 `docs-index -> current-state/checklist/template` 기준으로 정리됐지만, `project-spec.md` 참고 문서는 여전히 `architecture.md`, `testing.md` 직접 링크 중심이라 cross-cutting/spec 문서에서 읽기 진입점이 한 단계 뒤처져 있었음
- 해결: `project-spec.md` 참고 문서에 `system-docs-index.md` 와 `local-validation-docs-index.md` 를 우선 노출해 현재 독법과 맞췄음
- 이유: 메타 문서인 `project-spec.md` 도 같은 독법을 가져야 새 사용자가 구조/검증 문서를 찾을 때 예외 규칙을 따로 기억하지 않게 된다

## 287) `work-guide.md` 는 여전히 개별 current-state 문서 중심 표현이라 현재 docs-index 구조를 충분히 반영하지 못함
- 문제: top-level entrypoint와 문서군 index를 여러 개 추가했는데도 `work-guide.md` 는 여전히 “관련 current-state 문서” 표현만 써서, 실제 시작 순서가 `docs-index -> current-state/checklist/template` 로 바뀐 점이 드러나지 않았음
- 해결: `work-guide.md` 시작 순서와 문서 규칙에 `*-docs-index.md` 우선 기준을 명시해 현재 문서 구조와 맞췄음
- 이유: 사용자가 다음 작업을 열 때 top-level entrypoint와 work-guide가 같은 독법을 가져야 문서 진입 순서가 흔들리지 않는다

## 286) design history / archive 문서도 묶인 entrypoint가 없어 current-state와 배경 문서 경계가 약함
- 문제: `docs/history/*` 와 `archive/README.md` 는 실제 계약 문서가 아니라 설계 배경/실험 기록/보관 문서인데, top-level 진입점에서는 별도 묶음이 없어 current-state 문서와 같은 층위로 보이기 쉬웠음
- 해결: `history-docs-index.md` 를 추가하고 `start.md`, `current-state.md`, `README.md`, `documentation-map.md` 에 연결해 history/archive 문서를 별도 entrypoint 로 분리했음
- 이유: 먼저 current-state/index 문서를 보고, 배경이 필요할 때만 history로 내려가는 순서를 문서 구조로 드러내야 다음 작업 판단과 handoff가 쉬워진다

## 285) 구조/API/DB/후속설계 문서도 top-level에서는 흩어져 있어 cross-cutting 진입점이 약함
- 문제: `project-spec.md`, `architecture.md`, `api-mapping.md`, `db-migration.md`, `user-data-separation-design.md`, `chatbot-plan.md` 는 중요도가 높지만 도메인 index처럼 묶인 진입점이 없어 cross-cutting 문서를 찾을 때 다시 전체 맵을 뒤져야 했음
- 해결: `system-docs-index.md` 를 추가하고 `start.md`, `current-state.md`, `README.md`, `documentation-map.md` 에 연결해 구조/계약/DB/후속설계 문서도 별도 entrypoint 로 정리했음
- 이유: local current-state 문서군과 별개로 시스템 전반 reference 문서가 반복적으로 열리므로, cross-cutting 문서도 한 묶음 진입점이 있어야 handoff와 다음 작업 판단이 빨라진다

## 284) 공통 로컬 검증 문서도 `testing` / `runtime smoke` / `demo` 가 흩어져 있어 top-level 진입점이 약함
- 문제: `testing.md`, `runtime-api-smoke-commands.md`, `demo-scenario.md` 는 중요도가 높은 공통 검증 문서인데도 별도 묶음 index가 없어, 도메인별 index는 늘었지만 공통 local validation entrypoint 는 비어 있었음
- 해결: `local-validation-docs-index.md` 를 추가하고 `start.md`, `current-state.md`, `README.md`, `documentation-map.md` 에 연결해 공통 로컬 검증 문서군도 독립 entrypoint 로 정리했음
- 이유: 로컬 smoke/test/demo 문서는 개별 도메인보다 상위에서 자주 열리므로, 공통 검증 기준을 한 entrypoint 에서 바로 찾게 하는 편이 다음 작업 해석과 handoff 속도를 높인다

## 282) 추천 문서가 current-state/checklist/pipeline/replay template로 흩어져 있어 진입점이 약함
- 문제: `recommendation-current-state.md`, `recommendation-operation-checklist.md`, `recommendation-pipeline.md`, `recommendation-replay-template.md` 가 있었지만 top-level 진입점에는 별도 index가 없어 auth/collect/policy 대비 recommendation 묶음만 덜 보였음
- 해결: `recommendation-docs-index.md` 를 추가하고 `start.md`, `current-state.md`, `README.md`, `documentation-map.md` 에 연결해 추천 문서군도 같은 entrypoint 패턴으로 맞췄음
- 이유: recommendation 도 로컬 refresh/get/replay 와 current-state/pipeline 문서를 같이 봐야 하므로, 흩어진 개별 문서보다 묶음 진입점이 있어야 다음 작업 해석이 빨라진다

## 283) 프론트 QA 문서도 current-state/checklist/template가 흩어져 있어 top-level 진입점이 약함
- 문제: `frontend-qa-current-state.md`, `frontend-qa-checklist.md`, `frontend-qa-template.md` 는 있었지만 auth/collect/recommendation/policy처럼 별도 index가 없어 프론트 QA 묶음만 문서 진입점 패턴에서 빠져 있었음
- 해결: `frontend-qa-docs-index.md` 를 추가하고 `start.md`, `current-state.md`, `README.md`, `documentation-map.md` 에 연결해 프론트 QA 문서군도 같은 entrypoint 패턴으로 맞췄음
- 이유: 프론트 연동 전후 QA는 브라우저 검증 기준 문서를 묶어서 봐야 하므로, 현재 상태/체크리스트/기록 템플릿을 한 entrypoint 에서 바로 찾게 하는 편이 다음 작업 해석과 handoff에 유리하다

## 281) 문서가 `운영 전환` 을 실제 다음 트랙처럼 가정하면, 현재 로컬 검증 우선순위와 어긋나 다음 작업 해석이 틀어질 수 있음
- 문제: 현재 실제 상태는 `운영 서버 없음`, `로컬 테스트만 진행`, `프론트 후 운영` 인데 일부 current 문서가 `ops-only`, `deploy`, `운영 전환` 을 다음 active track처럼 안내하고 있었음
- 해결: pure ops/runbook 문서는 삭제하고, current 문서에서는 우선순위를 `로컬 기능 검증 -> 구조 검증 -> 수정 -> 최적화/보안 -> 프론트 연동 검증 -> 마지막 infra/deploy` 로 다시 고정했음
- 이유: 지금 단계에서 중요한 건 실제 기능/구조가 로컬에서 끝까지 버티는지 확인하는 것이고, deploy 문서는 실제 서버가 생긴 뒤 다시 만드는 편이 오해를 줄인다

## 1) `JAVA_HOME` 미설정으로 테스트/빌드 실패
- 문제: 테스트 실행 시 Java 환경 변수 미설정으로 Gradle 실행 불가
- 해결: `JAVA_HOME`, `PATH`, `GRADLE_USER_HOME`를 명시해 실행

## 2) 루트 경로에서 `./gradlew` 없음
- 문제: 저장소 루트에서 Gradle wrapper를 찾지 못함
- 해결: `backend/gradlew` 기준으로 작업 디렉토리를 `backend`로 통일

## 3) Gradle 에러: `Could not determine a usable wildcard IP`
- 문제: 샌드박스 제약 환경에서 Gradle 네트워크/호스트 정보 인식 실패
- 해결: escalated 권한으로 빌드/테스트 실행

## 4) Bash 인라인 환경변수 지정 구문 에러
- 문제: PATH 내 특수문자(`(`) 포함으로 인라인 할당 시 파싱 실패
- 해결: 인라인 할당 대신 `export` 방식으로 변수 설정

## 5) Docker MySQL 인증 실패
- 문제: `root/root` 계정으로 접속 시 `Access denied`
- 해결: `.env`의 실제 DB 계정/비밀번호로 접속

## 6) `@WebMvcTest` 컨텍스트 로딩 실패 (`JPA metamodel must not be empty`)
- 문제: Web MVC 슬라이스 테스트에서 JPA Auditing 빈 생성 충돌
- 해결: 테스트에서 `JpaMetamodelMappingContext`를 `@MockBean`으로 주입

## 7) 테스트에서 `@AuthenticationPrincipal`이 `null`
- 문제: WebMvc 테스트 환경에서 principal 주입값과 mock 기대값 불일치
- 해결: mock 매칭을 `isNull()` 조건으로 조정

## 8) 조회수 dedup 도입 후 앱 부팅 실패 (`char` vs `varchar`)
- 문제: `service_view_logs.client_fingerprint` 타입 불일치로 JPA validate 실패
- 해결: 코드/스키마/실DB 컬럼을 `VARCHAR(64)`로 통일

## 9) 알림 이력 테이블 추가 후 앱 부팅 실패 (`varchar` vs `enum`)
- 문제: `notifications.channel/period_type/status`가 JPA enum 기대와 불일치
- 해결: 스키마와 실DB 컬럼을 MySQL `ENUM`으로 변경

## 10) API별 조회수 신호 편차 (외부 조회수 필드 유무/품질 차이)
- 문제: 소스마다 `api_view_count` 신뢰도/존재 여부가 달라 랭킹 왜곡 가능
- 해결: source별 외부조회수 정규화 + 외부신호 미존재 시 가중치 재정규화

## 11) 조회수 dedup 검증의 불확실성
- 문제: API 응답만 보면 dedup 동작 여부 확인이 어려움
- 해결: API 호출 결과 + DB(`service_view_logs`, `welfare_services.view_count`) 교차 검증

## 12) 프론트 연동 요청 대비 프론트 코드베이스 부재
- 문제: 저장소에 React 프론트 코드가 없어 직접 API 연동 수정 불가
- 해결: `api-mapping.md`와 당시 프로젝트 플랜을 최신 스펙으로 갱신해 연동 기준 제공

## 13) 액세스 토큰 만료 후 로그아웃 불가
- 문제: `/api/auth/logout`가 인증 principal에 의존해 access token이 만료되면 refresh token 무효화와 쿠키 삭제를 정상 처리할 수 없었음
- 해결: refresh token header/cookie만 있어도 로그아웃 가능하도록 변경하고, 보안 설정에서 `/api/auth/logout`을 permit 처리
- 이유: 실제 운영에서는 "만료된 access token + 살아있는 refresh token" 상태가 자주 발생하며, 이 경우 로그아웃이 막히면 사용자 경험과 보안(서버 저장 토큰 정리) 둘 다 나빠짐

## 14) 정책 북마크 API 계약 불일치
- 문제: 문서에는 `POST /api/policies/{id}/bookmark`가 있었지만 구현은 추천 엔드포인트 기준 북마크만 존재했음
- 해결: 정책 기준 북마크 엔드포인트를 추가하고, 추천 이력이 없는 정책도 placeholder recommendation을 생성해 북마크 가능하게 처리
- 이유: 프론트/외부 연동은 정책 상세나 목록에서 바로 북마크하는 흐름이 자연스럽고, 문서 계약과 구현이 다르면 연동 리스크가 커지기 때문

## 15) 기존 DB와 최신 코드 스키마 불일치
- 문제: 새 컬럼/테이블(`notifications.retry_count`, `next_retry_at`, `service_view_logs` 등) 반영 후 기존 DB에서는 `schema.sql`만으로 최신화되지 않아 validate 실패 가능성이 있었음
- 해결: `db/migration/V2026_04_17_01__recent_schema_updates.sql`을 추가하고 운영 적용 절차를 `docs/core/db-migration.md`에 문서화
- 이유: 신규 초기화와 기존 운영 DB 업데이트는 경로가 달라야 하며, 수동 마이그레이션 기준이 없으면 배포 때마다 코드와 DB가 다시 어긋나기 때문

## 16) 통합 테스트용 DB가 코드보다 뒤처져 실패
- 문제: MySQL+Redis 통합 테스트를 붙인 뒤 실제 컨테이너 DB 스키마가 최신 코드보다 뒤처져 테스트가 실패했음
- 해결: 테스트 전에 `V2026_04_17_01__recent_schema_updates.sql`을 실제 Docker MySQL에 적용한 뒤 통합 테스트를 실행
- 이유: mock 기반 테스트만으로는 배포 리스크를 잡기 어렵고, 실제 DB/Redis와 맞물린 검증을 하려면 테스트 환경 스키마도 코드와 동일해야 함

## 17) `onlineApply`를 URL 존재만으로 추론하면 오판 가능
- 문제: 온통청년/복지로의 URL은 실제 신청 링크가 아니라 상세 안내 페이지일 수 있어, URL만 보고 `온라인 신청 가능`으로 판단하면 추천이 왜곡됨
- 해결: 추천 가점과 우선순위에서 `onlineApply` 의존을 제거하고, 필드는 표시/필터 용도로만 유지
- 이유: 잘못 채운 boolean은 `null`보다 위험하다. API마다 링크 의미가 달라 일관된 해석이 불가능하면 추천 신호로 쓰지 않는 편이 안전함

## 18) `sourceType=YOUTH`를 청년전용 정책으로 간주한 과대추론
- 문제: 추천 초기 로직에서 `YOUTH` 출처 정책에 `청년전용 +20` 가점을 부여했지만, 수집 출처와 자격 조건은 동일한 의미가 아니었음
- 해결: `sourceType=YOUTH` 기반 가점과 `YOUTH_ONLY` 우선순위 매칭을 제거
- 이유: 출처 메타데이터를 자격 신호처럼 쓰면 추천 설명 가능성과 정확도가 모두 떨어진다. 직접 자격 근거가 없는 값은 점수에서 제외하는 게 맞음

## 19) 소득 조건 표현 체계가 API마다 달라 직접 비교가 불안정
- 문제: 사용자 입력은 `incomeLevel(1~10 분위)` 하나인데, 정책 데이터는 소득분위/중위소득 %/월·연소득 금액/저소득층 문구가 섞여 있어 같은 축으로 비교할 수 없었음
- 해결: 추천 후보 SQL의 직접 소득 필터를 `YOUTH` 구조화 값에만 적용하고, 복지로 계열은 `TARGET_GROUP`/`KEYWORD` 태그를 보조 신호로만 사용하도록 변경
- 이유: 다른 체계의 값을 무리하게 같은 숫자로 비교하면 잘못된 탈락/통과가 발생한다. 같은 타입끼리만 직접 비교하고 나머지는 약한 신호로 남기는 편이 안전함

## 20) 신뢰도 낮은 필드를 빼면 동점 후보가 늘어날 수 있음
- 문제: `onlineApply`, `YOUTH_ONLY` 같은 신호를 제거하면 추천 점수 조합이 단순해져 동점이 많아질 우려가 있었음
- 해결: `ReRankingService`에 동점 해소 정렬을 추가해 `AI 점수 → 마감임박 여부 → applyEndDate → 내부/외부 조회수 → 최신성 → serviceId` 순으로 보조 정렬
- 이유: 신뢰도 낮은 필드를 억지로 점수에 넣는 것보다, 신뢰도 높은 핵심 점수는 유지하고 deterministic tie-breaker를 두는 편이 품질과 설명 가능성 모두 낫기 때문

## 21) 복지로 전체 수집 시 비청년 정책이 과다 적재됨
- 문제: 복지로 중앙/지자체 목록을 그대로 적재하면 검색/추천/AI 후보에 비청년 일반 복지가 대량 포함되어 노이즈가 커졌음
- 해결: `BokjiroYouthFilter`를 추가해 `청년 직접 신호`, `청년 생애주기`, `청년 범위 연령 조건`이 있는 정책만 정규화 테이블에 저장하도록 변경
- 이유: 프로젝트 목적이 청년 정책 중심 추천이므로, 저장 단계에서 후보 풀을 먼저 줄이는 편이 검색/추천 품질과 운영 비용을 동시에 낮추기 때문

## 22) 수집 규칙 변경 시 기존 데이터 정리가 어려움
- 문제: 기존 구조는 API 응답을 곧바로 정규화 테이블에 저장해, 필터/매핑 규칙이 바뀌면 원문 재해석 없이 과거 데이터를 복구하거나 재처리하기 어려웠음
- 해결: `raw_api_payloads` 테이블을 추가해 목록/상세 원문 payload를 별도로 보관하고, 이후 정규화 재처리가 가능하도록 변경
- 이유: MVP 단계에서는 수집 규칙과 추천 feature가 자주 바뀌므로, 원문을 남겨두는 편이 전체 재수집 비용과 운영 리스크를 줄이기 때문

## 23) 추천 갱신 시 동일 정책이 반복 노출됨
- 문제: `user_recommendations`에 추천 이력이 계속 누적되는데 조회 쿼리가 최신 세트를 기준으로 제한하지 않아 같은 정책이 여러 번 응답에 섞였음
- 해결: 추천 조회를 `user_id + service_id` 기준 최신 `recommended_at` 1건만 반환하도록 바꾸고, 새 추천 저장 전 비북마크 이력을 정리하도록 변경
- 이유: 추천 이력 보존과 사용자 응답 품질은 분리해야 한다. 이력은 남기되 API는 최신 추천 세트만 보여주는 편이 UX와 운영 관리 모두 낫기 때문

## 24) 청년 관련성 필터만으로는 특수 대상 정책 노이즈가 남음
- 문제: `농촌출신대학생학자금융자`, `공공산림가꾸기`처럼 청년 신호는 있지만 특정 대상에만 맞는 정책이 일반 사용자 추천 상위에 남았음
- 해결: 프로필에 `targetTypes`를 추가하고, 추천 점수에서 `농어촌`, `자립준비`, `장애`, `한부모`, `조손`, `보훈` 등 특수 대상이 실제 사용자와 맞을 때만 보너스를 주고, 불일치 시 패널티를 주도록 변경
- 이유: 청년 여부만으로는 추천 정밀도가 부족하다. 특수 대상 정책은 해당 사용자에게는 강하게 올리고, 아닌 사용자에게는 내려야 설명 가능성과 체감 품질이 함께 좋아지기 때문

## 25) Docker 빌드가 외부 Gradle 다운로드에 과하게 의존함
- 문제: Docker 이미지 빌드 시 `./gradlew bootJar`가 매번 외부에서 Gradle 배포본을 받으려 해 `timeout`, `504` 같은 네트워크 실패가 자주 발생했음
- 해결: Dockerfile에 BuildKit cache mount(`--mount=type=cache,target=/root/.gradle`)를 추가해 Gradle wrapper/의존성 캐시를 재사용하도록 변경
- 이유: 코드 문제를 빌드 인프라 불안정과 분리해야 한다. 캐시를 붙이면 외부 네트워크 의존이 줄어 재빌드 속도와 성공률이 같이 좋아진다

## 26) Docker 빌드에서 로컬 전용 JDK 경로를 참조해 실패
- 문제: `gradle.properties`의 `org.gradle.java.home=/home/minseok/youth-welfare/.jdk/...` 설정이 컨테이너 안에서는 존재하지 않아 `Java home supplied is invalid` 오류가 발생했음
- 해결: Docker 빌드 컨텍스트에서는 `gradle.properties`를 복사하지 않도록 변경해, 컨테이너 내부 JDK(`eclipse-temurin:17-jdk-jammy`)를 그대로 사용하게 함
- 이유: 로컬 개발 편의용 설정과 Docker 빌드 환경 설정은 분리해야 한다. 컨테이너는 자체 JDK를 이미 가지므로 호스트 경로를 강제로 주입할 필요가 없기 때문

## 27) 프론트 메인 페이지 정렬/검색 UI와 백엔드 API 계약 차이
- 문제: 메인 페이지 더미 데이터는 마감임박 정렬과 전체 검색 페이지 수를 로컬 계산했지만, 백엔드 정책 목록 API는 `LATEST/VIEWS/NAME`만 지원하고 검색 API는 총건수/총페이지를 반환하지 않음
- 해결: 메인 페이지 정렬 옵션을 백엔드가 지원하는 `조회수순/최신순/이름순`으로 맞추고, 검색 결과는 응답 길이 기준으로 다음 페이지 존재 여부만 보수적으로 표시
- 이유: 프론트에서 지원하지 않는 정렬을 보내면 `INVALID_INPUT`이 발생한다. 검색 페이징 계약은 백엔드 응답 스펙을 보완하기 전까지 프론트가 정확한 총페이지를 단정하면 안 됨

## 28) `api_sync_logs.status` 타입 불일치로 통합 테스트 실패
- 문제: 통합 테스트 DB에 `api_sync_logs` 테이블이 없었고, 최초 migration 적용 후에도 `status`가 `VARCHAR(30)`라 Hibernate schema validation이 MySQL `ENUM('running','success','partial_success','failed','skipped')`를 기대하며 실패했음
- 해결: `schema.sql`과 `V2026_04_23_01__add_api_sync_logs.sql`의 `status` 컬럼을 ENUM으로 수정하고, 테스트 DB에도 동일하게 반영한 뒤 `./gradlew integrationTest --no-daemon`을 재실행해 통과 확인
- 이유: 이 프로젝트는 MySQL 환경에서 `@Enumerated(EnumType.STRING)` 컬럼을 실제 `ENUM` 타입으로 검증한다. 신규 테이블 migration도 기존 enum 컬럼 정책과 맞춰야 배포/테스트 DB에서 JPA validate가 실패하지 않음

## 29) 같은 초 안의 추천 refresh 재실행 시 유니크 키 충돌
- 문제: `user_recommendations`의 유니크 키가 `(user_id, service_id, recommended_at)`인데 MySQL `DATETIME`이 초 단위로 저장되어, 북마크 보존 상태에서 같은 초 안에 추천 refresh를 다시 호출하면 동일 정책 insert가 중복될 수 있었음
- 해결: 추천 저장 시 기존 최신 추천의 `recommended_at`이 현재 초보다 같거나 늦으면 새 추천 저장 시각을 기존 최신 시각보다 1초 뒤로 조정
- 이유: 빠른 재시도, 더블클릭, 통합 테스트 반복 실행처럼 같은 초 안에 refresh가 재호출되는 상황은 실제로 발생할 수 있다. 저장 시각을 단조 증가시키면 이력 보존과 북마크 유지 정책을 깨지 않고 유니크 키 충돌을 피할 수 있음

## 30) 수집은 성공했지만 `api_sync_logs`가 비어 있음
- 문제: `POST /api/admin/collect/youth`가 200으로 완료됐는데 `api_sync_logs` 테이블에는 기록이 남지 않았음
- 해결: 실행 중인 `youth-welfare-app` 이미지가 최신 코드 이전 빌드임을 확인하고 `docker compose up -d --build app`으로 재빌드/재기동한 뒤 다시 수집 요청을 실행해 실패 로그 기록을 확인
- 이유: DB migration만 적용해도 앱 컨테이너가 최신 코드가 아니면 `ApiSyncLogService` 호출 경로가 반영되지 않는다. 수집/관측성 변경 검증은 DB 스키마와 앱 이미지 버전을 같이 맞춘 뒤 해야 함

## 31) 온통청년 수집 중 일시적 400 응답
- 문제: 실제 API key로 `POST /api/admin/collect/youth`를 실행했을 때 온통청년 page 10에서 한 차례 400 응답이 발생해 `COL001`로 종료됐음
- 해결: 같은 key와 같은 page 조건을 직접 호출해 200 응답을 확인한 뒤 앱 수집을 재실행했고, 2266건 요청/저장 및 `api_sync_logs.status=success`를 확인
- 이유: 외부 공공 API는 같은 요청도 일시적으로 실패할 수 있다. 실패 로그가 남는지 먼저 확인하고, 재시도 또는 재실행 시 성공하는지 DB 저장 건수와 `api_sync_logs`를 같이 봐야 함

## 32) Gmail SMTP smoke test 환경변수 미로딩
- 문제: Gmail 계정 정보를 입력했다고 판단했지만 실제 `.env`의 `GMAIL_USERNAME`, `GMAIL_PASSWORD` 값이 빈 문자열이라 smoke test가 환경변수 검증 단계에서 실패했음
- 해결: `.env` 저장 상태를 길이 기준으로 확인한 뒤 값을 다시 저장했고, `RUN_SMTP_SMOKE=true`로 실제 Gmail SMTP 발송 테스트를 재실행해 성공 확인
- 이유: `.env`는 IDE에서 입력 후 저장되지 않았거나 다른 파일을 수정하면 실행 프로세스에 반영되지 않는다. 비밀값은 출력하지 말고 길이/존재 여부만 확인해 설정 반영 상태를 검증해야 함

## 33) Gmail SMTP smoke test가 Gradle 캐시로 재발송되지 않음
- 문제: `SMTP_SMOKE_TO`를 추가한 뒤 smoke test를 다시 실행했지만 Gradle이 `:test UP-TO-DATE`로 판단해 실제 메일 발송이 다시 수행되지 않았음
- 해결: Gmail SMTP smoke test 실행 명령에 `--rerun-tasks`를 붙여 테스트를 강제로 재실행했고, 지정 수신자 대상으로 발송 성공을 확인
- 이유: smoke test는 외부 SMTP 발송이라는 부수효과를 확인하는 작업이라 Gradle 캐시가 켜지면 검증 의미가 사라질 수 있다. 실제 재발송 확인 시에는 `--rerun-tasks`를 사용해야 함

## 34) 정책 목록/상세 첫 렌더에서 북마크 상태가 항상 꺼져 보임
- 문제: 정책 북마크 토글 API는 있었지만 목록/검색/상세 조회 응답에 사용자별 `bookmarked` 값이 없어, 로그인 사용자의 기존 북마크도 프론트 첫 렌더에서는 항상 `false`로 보였음
- 해결: 정책 목록/검색/상세 응답 DTO에 `bookmarked` 필드를 추가하고, 최신 추천 이력 기준 북마크 상태를 함께 내려주도록 수정한 뒤 프론트가 초기 상태로 그대로 사용하게 변경
- 이유: 토글 API만 있고 초기 상태 조회 계약이 없으면 목록-상세-마이페이지 간 북마크 표시가 쉽게 어긋난다. 사용자별 상태성 필드는 읽기 API와 쓰기 API를 같이 맞춰야 재발을 막을 수 있음

## 35) 검색 API 총건수는 DB count만으로는 정확하지 않음
- 문제: 정책 검색은 DB FULLTEXT 결과에 청년 관련성 후처리 필터를 한 번 더 적용하므로, 원본 검색 SQL의 count만 쓰면 프론트가 보는 실제 결과 수와 `totalElements`가 어긋날 수 있었음
- 해결: 검색 응답을 `content + totalElements + totalPages + hasNext` 구조로 바꾸고, 검색 전체 결과를 배치 단위로 스캔하면서 청년 후처리 필터 적용 뒤 최종 total count를 계산하도록 수정
- 이유: 페이지 메타데이터가 실제 화면 결과와 다르면 UX가 바로 깨진다. 현재 데이터 규모에서는 정확도를 우선하고, 비용이 커지면 이후 SQL 레벨 청년 필터 이관을 검토하는 편이 안전함

## 36) 정확한 검색 totalCount 계산은 성능 비용이 숨기기 쉬움
- 문제: 정확한 `totalElements`를 위해 검색 결과를 배치 스캔하도록 바꾸면 기능은 맞아도, 배치 수와 후처리 비용이 로그에 드러나지 않으면 운영에서 느려진 시점을 놓치기 쉬움
- 해결: `PolicySearchService`에 검색 스캔 배치 수, 원본 검색 건수, 청년 필터 후 건수, 페이지 크기, 응답 시간을 남기는 관측 로그를 추가하고, 일정 임계치를 넘기면 `warn`으로 올리도록 변경
- 이유: 정확도 보강은 끝이 아니라 관측 가능성이 같이 있어야 유지된다. 성능 문제가 생겼을 때 SQL 레벨 청년 필터 이관이 필요한지 판단하려면 먼저 현재 비용이 얼마나 드는지 로그로 보이는 상태여야 함

## 37) 실제 넓은 검색어에서는 후처리 totalCount 계산 비용이 과도함
- 문제: 실제 Docker 앱에서 인증된 검색 요청으로 관측한 결과, `청년`, `지원`, `사업` 같은 넓은 단일 키워드는 6초~24초, 8~15배치 스캔, 원본 1.5k~2.8k건 조회까지 올라가 현재 Java 후처리 기반 total count 계산 비용이 너무 컸음
- 해결: 현재 구조는 정확도 유지용으로 두되, 다음 작업을 `정책 검색 SQL 레벨 청년 필터 설계 및 이관`으로 승격하고 운영 로그 기준으로 병목 원인을 문서화
- 이유: 이 정도 응답 시간은 검색 UX에 직접 영향을 준다. 관측 결과가 이미 충분히 나왔으므로, 이제는 “더 지켜본다”보다 필터를 SQL 또는 검색 전용 컬럼으로 이관하는 설계 작업으로 넘어가는 편이 맞음

## 38) 데모 문서의 정책 조회 예시는 현재 보안 설정과 불일치했음
- 문제: `demo-scenario.md`의 정책 목록/검색/랭킹/상세 예시는 인증 헤더 없이 호출하도록 되어 있었지만, 실제 `SecurityConfig`는 `/actuator/health` 외 대부분 GET 요청도 인증을 요구해 익명 요청 시 `403`이 발생했음
- 해결: 데모 문서의 정책 조회 예시에 `Authorization: Bearer ${ACCESS_TOKEN}`를 추가해 현재 보안 설정과 맞춤
- 이유: 검증 문서가 실제 보안 정책과 다르면 기능 자체는 정상이더라도 시연과 smoke test가 실패한 것으로 오해된다. 운영/검수 문서는 현재 보안 규칙과 반드시 같이 움직여야 함

## 39) 검색 totalCount 정확도를 Java 후처리에 맡기면 넓은 키워드에서 너무 느려짐
- 문제: `청년`, `지원`, `사업` 같은 넓은 검색어는 Java 후처리 청년 필터와 배치 스캔 기반 `totalElements` 계산 때문에 실제 앱에서 6초~24초까지 올라갔음
- 해결: `welfare_services.search_youth_relevant` 저장 플래그와 인덱스를 추가하고, 수집 저장/복지로 상세 fallback/관리자 백필에서 값을 갱신하도록 바꾼 뒤 검색 SQL과 count query가 이 플래그를 직접 사용하게 변경
- 이유: 요청마다 청년 관련성을 다시 계산하면 정확도는 맞아도 런타임 비용이 너무 크다. 검색용 파생 값은 저장 시점에 계산해 두고, 운영 DB에는 백필 절차를 같이 가져가야 응답 속도와 결과 일관성을 함께 지킬 수 있음

## 40) 지역 필터가 없는 일반 검색에서 `service_regions` 조인이 불필요한 병목이었음
- 문제: SQL 레벨 청년 필터 이관 뒤에도 일반 검색은 `LEFT JOIN service_regions + DISTINCT`를 항상 수행해 `+지원` 기준 중간 결과 72923행, 임시 테이블 dedup/sort 929ms가 발생했고 전체 API 응답이 1초대에 머물렀음
- 해결: 일반 검색은 `welfare_services`만 조회하는 쿼리로 분리하고, 지역 필터가 있을 때만 `NOT EXISTS/EXISTS` 기반 지역 판정 쿼리를 사용하도록 검색 repository와 service를 분기
- 이유: 지역 조건이 없는 요청에서 지역 테이블을 조인하면 결과 정확도 이득 없이 중복행과 정렬 비용만 커진다. 선택적 조인 또는 `EXISTS` 분기는 검색처럼 호출 빈도가 높은 쿼리에서 먼저 적용해야 함

## 41) `GET /api/recommendations` 응답에 `logId` 누락
- 문제: `POST /api/recommendations/refresh`는 응답에 `logId`를 포함했지만 `GET /api/recommendations`는 `RecommendationResponse.from(rec)`만 호출해 `logId=null`이 반환됐음. 프론트가 저장된 추천 목록을 조회할 때 CTR 클릭 추적이 불가능했음
- 해결: `getRecommendations()`에도 `recommendationLogService.findLatestLogIdMap()`로 serviceId → logId 맵을 조회해 응답에 포함하도록 수정
- 이유: refresh와 get 두 엔드포인트가 같은 데이터를 반환하므로 응답 계약이 동일해야 한다. 한쪽에만 필드가 있으면 프론트가 어느 엔드포인트를 쓰느냐에 따라 동작이 달라져 버그를 찾기 어렵다

## 42) CTR 클릭 추적 파라미터명 혼동 (`log_id` vs `logId`)
- 문제: 데모 시나리오에서 `?log_id=153`으로 테스트했지만 백엔드 `@RequestParam`은 `logId`(camelCase)를 기대해 클릭이 기록되지 않았음. 프론트는 axios params로 `{ logId: ... }`를 넘겨 `?logId=`로 변환하므로 실제 동작은 정상이었으나, 직접 curl 테스트에서 혼동이 발생했음
- 해결: 데모 시나리오 curl 예시를 `?logId=`로 수정하고, 프론트-백엔드 연동 흐름(URL `log_id` → searchParams.get → axios params `logId` → 백엔드 `@RequestParam logId`)을 문서화
- 이유: 파라미터명 규칙(camelCase vs snake_case)을 컨트롤러-문서-프론트 세 곳에서 일관되게 유지해야 한다. 직접 curl 테스트와 프론트 axios 동작이 다를 수 있으므로 데모 문서는 프론트 기준이 아닌 백엔드 계약 기준으로 작성해야 함

## 43) 추천 AI 점수 누락 — 20건 중 8건만 응답
- 문제: GPT에 20건을 한 번에 보냈을 때 "모두 평가하라"는 지시가 없어 GPT가 자체 판단으로 일부만 응답함. 20건 중 8건만 AI 점수를 받아 나머지 12건은 rule-only로 처리됐음
- 해결: system/user 역할 분리, 프롬프트에 "반드시 N개 전부 평가" 명시, TOP_N 20→15로 축소. 이후 15건 전부 응답 확인
- 이유: LLM은 명시적 지시가 없으면 긴 목록을 자의적으로 줄인다. 전체 평가를 보장하려면 개수를 줄이거나 프롬프트에 명시적 제약을 걸어야 한다

## 44) 추천 중복 행 누적 — 북마크 정책이 refresh마다 쌓임
- 문제: `deleteUnbookmarkedByUserId`는 북마크된 행을 남겨두는데, 북마크된 정책도 새 추천에 포함되면 같은 service_id 행이 중복으로 쌓였음. 테스트 유저 기준 total=43, unique=40으로 3건 중복 확인
- 해결: `deleteAllByUserId`로 전체 삭제 후 북마크 상태를 Map으로 보존해 새 행에 이전하도록 변경
- 이유: 북마크 보존은 "데이터를 남기는 것"이 아니라 "상태를 이전하는 것"이어야 한다. 행을 남기면 같은 정책이 여러 recommended_at으로 중복 존재하게 된다

## 45) 특수 대상 노이즈 필터가 숫자 임계값에 의존해 취약했음
- 문제: `rule_base_score > 8.0` 조건으로 병역/농촌/다문화 정책을 제거했지만, 이는 점수 공식 변경 시 자동으로 깨지는 구조였음. 또한 "현역병", "병역" 신호가 `hasSpecialTargetSignal()` 목록에 없어 필터를 우회했음
- 해결: `ScoredCandidate`에 `hasSpecialTargetMismatch` 플래그 추가, `RuleScoringService`가 계산 시점에 명시적으로 설정, Facade에서 플래그로 필터링. 특수 신호 목록에 "현역병", "병역" 추가
- 이유: 도메인 의미(특수 대상 불일치)는 점수 계산 로직이 가장 잘 알고 있다. 숫자 임계값 대신 의미 기반 플래그로 표현해야 점수 공식이 바뀌어도 필터가 유지된다

## 311) `auth-*`, `policy-*` 문서가 너무 많아 현재 상태와 설계 배경이 섞여 보였음
- 문제: local-first로 작은 task를 계속 닫으면서 `auth-*`, `policy-*` 문서가 많이 쪼개졌고, current-state 문서와 design history 문서를 처음 보는 사람이 바로 구분하기 어려웠음
- 해결: [auth-docs-index.md](../auth/auth-docs-index.md), [policy-docs-index.md](../policy/policy-docs-index.md) 를 추가해 왜 문서가 많아졌는지와 어디부터 읽어야 하는지 문서군 단위로 정리
- 이유: 파일을 무리하게 대이동하면 링크/맥락이 깨질 수 있다. 먼저 읽기 경로를 줄이고 current-state 와 design history 를 분리하는 편이 안전하다

## 312) fresh local reset 뒤 policy sidecar draft schema가 자동 bootstrap 되지 않아 collect 성공과 canonical downstream 검증이 분리됐다
- 문제: `SMOKE_RESET_DB=true` 로 로컬 DB를 초기화한 뒤에는 `welfare_services` 같은 base schema만 살아 있고, `service_taxonomies` / `service_facts` 같은 canonical sidecar draft schema는 자동으로 올라오지 않았다. 그래서 실제 `POST /api/admin/collect/youth` 는 성공해도 replay 같은 canonical downstream 검증은 곧바로 재현되지 않았다
- 해결: local verification 기준으로는 draft sidecar schema/backfill을 따로 적용한 뒤 replay를 재실행하도록 정리했고, 관련 측정과 현재 caveat를 [local-feature-performance-check-2026-05-01.md](./local-feature-performance-check-2026-05-01.md)에 남겼다
- 이유: 현재 sidecar는 아직 draft migration 경로라 runtime bootstrap과 intentionally 분리돼 있다. 이 경계를 모르고 collect 성공만 보면 downstream도 바로 되는 것으로 오해할 수 있다

## 313) `V2026_04_30_02__seed_policy_normalization_codes.sql` 가 MySQL 8.0에서 `CTE + INSERT` 문법 오류로 실행되지 않았음
- 문제: draft SQL이 `WITH ... INSERT INTO ... SELECT ...` 순서를 사용하고 있어, local MySQL 8.0.45 에서 `line 68` 문법 오류가 발생했다
- 해결: [V2026_04_30_02__seed_policy_normalization_codes.sql](../backend/src/main/resources/db/migration-draft/V2026_04_30_02__seed_policy_normalization_codes.sql) 을 `INSERT INTO ... WITH ... SELECT ...` 순서로 수정했고, 수정 후 `service_taxonomies=2363`, `education_target_rows=110` 기준으로 실제 적용을 다시 확인했다
- 이유: draft SQL이라도 로컬 reset 복구와 replay 검증에 실제로 쓰이는 순간이 있다. 실행 불가능한 초안 상태로 두면 canonical closeout 검증이 다시 흔들린다

## 314) 실제 `collect/youth` 는 내부 코드보다 upstream 상태가 더 큰 변동 요인이었다
- 문제: 실제 runtime matrix에서 첫 `POST /api/admin/collect/youth` 는 `129.638s` 에 성공했지만, 곧바로 다시 실행한 두 번째 collect는 `page=9` 에서 upstream `403` 이 나며 `500` 으로 실패했다
- 해결: 이번 round에서는 코드를 바꾸기보다 이 현상을 local measurement 결과로 명시하고, collect 병목/불안정성을 internal regression 이 아니라 external dependency variability 로 분리해 기록했다
- 이유: 동일 코드/동일 로컬 환경에서도 외부 수집 API 상태에 따라 collect 성공 여부와 시간이 크게 흔들린다. 지금 단계에서 이 구간을 내부 코드 병목으로만 해석하면 원인을 잘못 잡게 된다

## 315) fresh reset 뒤 canonical sidecar draft schema 공백은 runtime bootstrap이 아니라 local replay self-heal로 먼저 메웠다
- 문제: `SMOKE_RESET_DB=true` 이후 base schema만 살아 있는 상태에서는 `service_taxonomies` 가 없어 replay가 `education replay precondition unmet` 로 끊겼다
- 해결: [deploy/mysql/apply-local-policy-sidecar-draft.sh](../deploy/mysql/apply-local-policy-sidecar-draft.sh) 를 추가했고, [run-local-education-priority-replay.sh](../deploy/smoke/run-local-education-priority-replay.sh) 가 missing sidecar schema 또는 zero education target row를 감지하면 local draft create/seed SQL을 자동 재적용하도록 연결했다
- 이유: 지금 필요한 것은 runtime migration 구조를 당장 바꾸는 것이 아니라, local closeout과 replay smoke가 fresh reset 이후에도 스스로 복구되게 만드는 것이다. draft sidecar를 runtime bootstrap에 편입하는 것과 local smoke self-heal은 분리해서 다루는 편이 안전하다

## 316) `Gov24` 같은 이름을 따라가다 보면 “특정 API 추가” 와 “신규 source를 계속 받는 구조” 를 혼동하기 쉽다
- 문제: `Gov24` 관련 문서가 많다 보니, 현재 목표가 `Gov24 API key 확보 후 바로 수집 구현` 처럼 보일 수 있었다
- 해결: [policy-source-onboarding-architecture.md](../policy/policy-source-onboarding-architecture.md) 를 추가해 현재 진짜 목표는 `Gov24` 구현이 아니라, 어떤 정책 API가 들어와도 `정책형 / listing형 / reference matrix형` 으로 분류하고 `raw -> canonical 승격 -> compat bridge -> blocked 판정` 으로 처리하는 공통 구조를 고정하는 것임을 current-state 기준으로 명시했다
- 이유: source별 사례집과 공통 구조 문서를 분리해야 다음 API를 붙일 때마다 `Gov24를 먼저 해야 하나` 같은 불필요한 오해를 줄일 수 있다

## 317) 공통 구조 문서만 있으면 실제 작업자는 “다음 질문이 뭐지” 를 다시 헤맬 수 있다
- 문제: [policy-source-onboarding-architecture.md](../policy/policy-source-onboarding-architecture.md) 는 구조 기준은 잘 설명하지만, 새 source를 실제로 받았을 때 바로 따라가는 짧은 실행 절차로는 길 수 있었다
- 해결: [policy-source-onboarding-checklist.md](../policy/policy-source-onboarding-checklist.md) 를 추가해 `분류 -> minimal inventory -> raw ingest -> canonical 후보 -> codebook/blocked -> recommendation 영향` 순서만 따로 분리했다
- 이유: 구조 설명 문서와 실무 체크리스트를 분리해야, 다음 source onboarding 때 설계 배경을 다시 다 읽지 않고도 같은 판단 순서를 재사용할 수 있다

## 318) 구조 문서와 체크리스트만으로는 “실제 어떤 클래스를 열어야 하지?” 가 여전히 남을 수 있다
- 문제: source onboarding 공통 구조와 실무 체크리스트는 정리됐지만, 실제 코드 작업에 들어가면 collect entry, raw payload 저장, sidecar writer, recommendation read-model 중 어디부터 열어야 하는지 다시 찾게 될 수 있었다
- 해결: [policy-source-code-entrypoints.md](../policy/policy-source-code-entrypoints.md) 를 추가해 [CollectAdminController.java](/home/minseok/youth-welfare/backend/src/main/java/com/example/welfare/collect/controller/CollectAdminController.java), [CollectService.java](/home/minseok/youth-welfare/backend/src/main/java/com/example/welfare/collect/service/CollectService.java), [RawApiPayloadService.java](/home/minseok/youth-welfare/backend/src/main/java/com/example/welfare/collect/service/RawApiPayloadService.java), [CollectItemSaver.java](/home/minseok/youth-welfare/backend/src/main/java/com/example/welfare/collect/service/CollectItemSaver.java), [DeferredNormalizedPolicySidecarWriter.java](/home/minseok/youth-welfare/backend/src/main/java/com/example/welfare/collect/normalization/DeferredNormalizedPolicySidecarWriter.java), [CanonicalRecommendationReadModelRepository.java](/home/minseok/youth-welfare/backend/src/main/java/com/example/welfare/recommend/repository/CanonicalRecommendationReadModelRepository.java) 순서로 코드 진입점을 묶었다
- 이유: 구조 설명, 실행 체크리스트, 코드 진입점 문서를 분리해야 다음 source를 붙일 때 조사와 구현을 섞지 않고 바로 필요한 레이어로 들어갈 수 있다

## 319) 구조/체크리스트/코드 진입점만 있어도 새 source를 받을 때 다시 빈 문서부터 쓰게 된다
- 문제: source onboarding에 필요한 판단 순서와 코드 진입점은 정리됐지만, 실제 새 source가 들어오면 다시 “무슨 항목을 적지?” 부터 시작하게 될 수 있었다
- 해결: [policy-source-onboarding-template.md](../policy/policy-source-onboarding-template.md) 를 추가해 source name, endpoint inventory, row grain 판정, raw ingest, canonical direct onboarding, codebook 필요 여부, recommendation 영향, next action 을 한 번에 채우는 복붙용 note 템플릿을 만들었다
- 이유: 구조 설명 문서와 체크리스트, 코드 진입점 다음에 바로 사용할 실행 템플릿이 있어야 다음 source onboarding 때 문서 작성 비용을 줄이고 판단 형식을 표준화할 수 있다

## 320) 프론트엔드 QA는 로컬 API smoke와 달리 “뒤로가기/복귀/세션 만료 UX” 를 자동 회귀로 잡지 못한다
- 문제: backend smoke와 integration test는 충분히 정리됐지만, 브라우저에서 실제로 보이는 뒤로가기, 로그인 후 원위치 복귀, 세션 만료 후 `/login` 이동, 북마크 화면 간 일관성은 별도 브라우저 자동화가 없어 회귀 기준이 흐릴 수 있었다
- 해결: [frontend-qa-current-state.md](../frontend/frontend-qa-current-state.md), [frontend-qa-checklist.md](../frontend/frontend-qa-checklist.md), [frontend-qa-template.md](../frontend/frontend-qa-template.md) 를 추가해 현재 프론트 QA를 `build/lint + 수동 브라우저 시나리오` 기준으로 고정하고, route-level 수동 검증 절차를 분리했다
- 이유: 지금 단계에서는 Playwright/Cypress를 바로 도입하는 것보다, 실제 코드가 가진 라우팅/세션 경계를 빠르게 검증할 수 있는 manual runbook을 먼저 고정하는 편이 비용 대비 효율이 높다

## 321) `PoliciesPage` 검색/필터 상태는 URL이 아니라 local state 중심이라 뒤로가기/새로고침에서 기대와 다르게 보일 가능성이 있다
- 문제: [PoliciesPage.jsx](../frontend/src/pages/PoliciesPage.jsx) 의 검색어, 카테고리, 지역, 정렬, 페이지 상태는 주로 컴포넌트 local state에 있고 URL query로 영속화되지 않는다
- 해결: 지금 round에서는 기능 수정 대신 [frontend-qa-current-state.md](../frontend/frontend-qa-current-state.md) 와 [frontend-qa-checklist.md](../frontend/frontend-qa-checklist.md) 에 이 구간을 명시적 QA 포인트와 리스크로 기록했다
- 이유: 이건 즉시 버그라고 단정할 문제보다 UX 기대와 현재 구현 경계의 차이로 봐야 한다. 먼저 수동 검증 기준에 올려두고, 실제 사용성 이슈가 확인되면 URL state persist 개선을 여는 편이 맞다

## 322) 프론트 production build는 통과하지만 단일 chunk 크기 경고가 남아 있다
- 문제: `cd frontend && npm run build` 는 통과했지만 `dist/assets/index-*.js` 가 `500 kB` 경고를 넘었다
- 해결: 현재는 기능 실패가 아니라 후속 성능 개선 후보로만 기록하고, frontend QA current-state 문서에 기준선으로 반영했다
- 이유: 지금 우선순위는 브라우저 기능 흐름 검증과 운영 전 정상동작 확인이다. bundle split은 중요하지만, 현재 장애나 기능 실패를 일으키는 즉시 이슈는 아니다

## 320) `collect-ops.md` 하나만으로는 현재 기준과 실행 절차와 장애 기록 형식이 섞여 보일 수 있다
- 문제: collect 관련 문서는 있었지만, 현재 collect 동작 기준, 실제 실행 순서, 장애 기록 양식이 한 문서 안에 섞여 있어 바로 쓰기 어려울 수 있었다
- 해결: [collect-current-state.md](../collect/collect-current-state.md), [collect-operation-checklist.md](../collect/collect-operation-checklist.md), [collect-incident-template.md](../collect/collect-incident-template.md) 를 추가해 current-state, runbook, 복붙 템플릿으로 역할을 분리했다
- 이유: source onboarding 쪽과 같은 방식으로 collect 운영 문서도 층을 나눠야, 평소에는 current-state 를 보고 실제 실행 시 checklist 를 쓰고, 이슈가 나면 template로 기록하는 흐름이 선명해진다

## 321) recommendation/replay 도 설계 문서와 실험 기록이 많아 현재 계약과 실행 절차가 바로 안 보일 수 있다
- 문제: 추천 쪽은 [recommendation-pipeline.md](../recommendation/recommendation-pipeline.md), `policy-normalization-education-*`, `openai-replay-*` 문서가 많아, 현재 코드 기준 계약과 실제 replay 해석 순서를 빠르게 찾기 어려울 수 있었다
- 해결: [recommendation-current-state.md](../recommendation/recommendation-current-state.md), [recommendation-operation-checklist.md](../recommendation/recommendation-operation-checklist.md), [recommendation-replay-template.md](../recommendation/recommendation-replay-template.md) 를 추가해 current-state, 실행 runbook, 기록 템플릿으로 역할을 분리했다
- 이유: recommendation/replay 도 collect/source onboarding 과 같은 방식으로 문서 층을 나눠야, 현재 계약 확인과 실험 기록 작성이 덜 섞이고 local/diagnostic 검증도 반복하기 쉬워진다

## 322) auth도 current-state는 있었지만 실행 순서와 기록 양식은 별도 문서가 없었다
- 문제: [auth-session-revocation-current-state.md](../auth/auth-session-revocation-current-state.md) 로 현재 계약은 확인할 수 있었지만, 실제로 logout/withdraw/allowlist revoke/forced logout을 어떤 순서로 점검하고 무엇을 기록할지는 별도 문서가 약했다
- 해결: [auth-operation-checklist.md](../auth/auth-operation-checklist.md) 와 [auth-incident-template.md](../auth/auth-incident-template.md) 를 추가해 auth도 current-state / checklist / template 구조를 맞췄다
- 이유: auth도 collect/recommendation 처럼 현재 계약 문서와 실행 runbook, 기록 템플릿을 분리해야 local/runtime 확인과 후속 triage가 반복 가능해진다

## 42) priority_options 코드가 추천 로직과 UI 코드 사이에서 따로 놀았음
- 문제: DB `priority_options`에는 `ONLINE`, `YOUTH_ONLY`, `EDU_JOB`, `AMOUNT` 코드가 있었지만, `DefaultPriorityMatcher`에서 `ONLINE`과 `YOUTH_ONLY`는 이미 항상 false였고, 프론트는 `JOB`, `EDUCATION`, `FINANCE`, `HEALTH`, `SAFETY` 등 DB에 없는 코드를 전송해 `C001` 오류가 났음
- 해결: `ONLINE`, `YOUTH_ONLY` 제거, `EDU_JOB`→`EDUCATION`, `AMOUNT`→`FINANCE` 코드 변경, `JOB`, `PARTICIPATION`, `FAMILY` 추가. DB migration, `DefaultPriorityMatcher`, 프론트 `PRIORITY_OPTIONS` 세 곳을 동시에 맞춤
- 이유: 우선순위 코드는 DB(마스터), 추천 로직(매처), UI(선택지) 세 곳이 항상 같아야 한다. 한 곳만 바꾸면 나머지가 다시 어긋난다

## 43) `deleteByUserId` 파생 쿼리가 같은 트랜잭션 내 INSERT 전 flush를 보장하지 않음
- 문제: `updatePriorities` 내에서 `userPriorityRepository.deleteByUserId()` 후 새 우선순위를 `save()`할 때, JPA가 DELETE를 DB에 즉시 반영하지 않아 `(user_id, priority_option_id)` 유니크 키 충돌이 발생했음
- 해결: `deleteByUserId`를 `@Modifying` + `@Query` JPQL로 교체해 DELETE를 즉시 DB에 반영되도록 변경
- 이유: Spring Data 파생 `deleteBy` 메서드는 내부적으로 엔티티를 조회 후 삭제하므로 flush 시점이 트랜잭션 끝까지 미뤄질 수 있다. DELETE 후 같은 유니크 컬럼에 INSERT가 오는 경우엔 `@Modifying` JPQL로 즉시 반영해야 한다

## 41) 회원가입/로그인 화면이 실제 인증 계약과 어긋나 있었음
- 문제: 프론트는 `signup`이 토큰과 user를 바로 반환한다고 가정했고, 이메일 중복확인은 미구현 API를 임시 우회하고 있었으며, 우선순위 저장 payload도 백엔드 계약과 달랐음
- 해결: `GET /api/auth/check-email`을 추가하고, 프론트는 `signup -> login -> /api/users/me` 순서로 세션을 만들도록 수정했으며, 우선순위 저장은 `{ priorityCodes: [...] }`로 맞춤. 동시에 "아이디 = 이메일", "비밀번호 재설정 메일은 후속 구현" 방향을 문서화
- 이유: 인증 화면은 응답 포맷 하나만 어긋나도 전체 가입 흐름이 무너진다. 특히 계정 복구처럼 보안 민감한 기능은 구현보다 먼저 정책과 계약을 명확히 고정해야 재작업이 줄어든다

## 46) 챗봇 일정 표기가 프론트와 문서에서 불일치
- 문제: `frontend/src/router/index.jsx`의 `/chat` 자리표시자는 `Phase 4 예정`이었지만, SRS와 `phase-plan.md`는 챗봇을 2차 작업으로 정의하고 있었음
- 해결: `/chat` 자리표시자를 `챗봇 (2차 예정)`으로 수정하고, `docs/core/chatbot-plan.md`에 현재 기준 설계/작업 순서를 문서화
- 이유: 구현 전 단계 기능일수록 화면 문구와 계획 문서가 같아야 우선순위 오해와 작업 누락이 줄어든다

## 47) 신규 문서가 목차와 작업 추적에서 빠져 상태 파악이 어긋남
- 문제: 운영/설계 보조 문서가 추가돼도 `docs/README.md`와 `docs/phase-plan.md` 작업 추적에 함께 반영되지 않아, 문서는 존재하지만 현재 상태와 후속 작업 목록에서 누락될 수 있었음
- 해결: `docs/README.md`에 신규 문서 링크를 추가하고, `docs/phase-plan.md`에서 완료/진행 예정/남은 작업을 실제 문서 상태와 다시 맞춤
- 이유: 이 저장소는 문서가 구현 계획과 운영 기준의 단일 진입점 역할을 하므로, 새 문서는 본문 작성만으로 끝내지 말고 목차와 추적 문서까지 함께 갱신해야 재발을 막을 수 있음

## 48) read cut-over 시 `user_attributes` / `user_priorities.user_key` 누락으로 프로필·추천 데이터가 비어 보일 수 있었음
- 문제: 프로필/추천 read path를 `user_profiles` 중심으로 바꾸는 과정에서, 보조 테이블 `user_attributes`, `user_priorities`의 기존 row 상당수가 아직 `user_key` 없이 `user_id`만 채워진 상태라 `where user_key = ?` 조회만 쓰면 관심분야/우선순위가 통째로 누락될 수 있었음
- 해결: 이번 전환에서는 read query를 `user_id -> users.user_key` 조인 기준으로 바꿔 기존 데이터를 안전하게 읽도록 고정했고, 후속 작업으로 `user_key` write sync 및 backfill을 별도 항목으로 추가
- 이유: cut-over 단계에서는 "새 키 기준 이상형"보다 "기존 운영 데이터가 빠짐없이 읽히는 것"이 우선이다. 보조 테이블 backfill 전까지는 조인 보정이 있어야 추천/프로필 품질 저하를 막을 수 있음

## 49) migration backfill만 하고 저장 경로를 안 바꾸면 새 `user_attributes` / `user_priorities` row가 다시 `user_key=NULL` 로 쌓였을 수 있음
- 문제: `V2026_04_27_02__add_user_key_columns.sql` 로 기존 row는 한 번 채울 수 있어도, JPA 저장 경로가 계속 `user_id`만 쓰면 이후 프로필 수정/우선순위 저장 때 새 row는 다시 `user_key` 없이 적재되어 read cut-over 이후 같은 문제가 반복될 수 있었음
- 해결: `UserAttribute`, `UserPriority` 엔티티에 `userKey` 컬럼을 매핑하고 `UserService.updateProfile`, `UserService.updatePriorities` 가 저장 시 `user_key` 를 함께 쓰도록 변경했으며, 남아 있는 null row는 `/api/admin/users/metadata-user-key-backfill` 로 마무리하는 경로를 추가
- 이유: cut-over 안정성은 "과거 데이터 1회 backfill"과 "미래 데이터 지속 동기화"가 둘 다 있어야 생긴다. 저장 경로를 같이 바꾸지 않으면 backfill은 일회성 복구에 그친다

## 48) 관리자 이메일 allowlist만 두면 공개 signup으로 권한 선점이 가능했음
- 문제: 이메일 인증이 없는 현재 구조에서 `SECURITY_ADMIN_EMAILS`만 두고 `/api/admin/**` 권한을 주면, 해당 이메일 주소가 DB에 없을 때 누구나 공개 회원가입으로 관리자 계정을 선점할 수 있었음
- 해결: 관리자 예약 이메일은 `signup`에서 차단하고, 운영자가 DB에 수동 생성한 계정만 관리자 권한을 얻도록 변경
- 이유: 관리자 권한은 "누가 그 문자열 이메일을 먼저 입력했는가"가 아니라 운영자가 통제한 계정 생성 절차에 의해 부여돼야 한다

## 49) refresh 재발급 시 역할 claim을 다시 넣지 않으면 관리자 권한이 사라짐
- 문제: access token에만 관리자 역할을 넣고 `refresh`에서는 userId만으로 새 access token을 만들면, 관리자도 재발급 직후 `ROLE_ADMIN` 없이 일반 사용자처럼 떨어질 수 있었음
- 해결: `refresh` 시 DB의 사용자 이메일과 `SECURITY_ADMIN_EMAILS`를 다시 확인해 새 access token에 역할 claim을 재주입하도록 변경
- 이유: 권한 정보는 최초 로그인 한 번이 아니라 access token을 새로 만들 때마다 같은 기준으로 재계산돼야 일관성이 유지된다

## 280) forced logout 1차 hardening은 revoke correctness와 audit 확장을 분리해 닫아야 다음 pending으로 자연스럽게 넘어갈 수 있음
- 문제: `admin forced logout` 구현과 smoke가 이미 완료된 뒤에도 actor 로그, DB audit table, action history 같은 후속 감사 항목을 같은 트랙에 계속 붙이면 “현재 1차 hardening이 끝났는지”가 모호해지고, 다음 canonical/source onboarding pending으로 넘어가는 시점이 흐려질 수 있었음
- 해결: [auth-admin-forced-logout-closeout.md](../auth/auth-admin-forced-logout-closeout.md) 로 현재 phase의 완료 범위를 `old access 즉시 차단 + old refresh 즉시 차단 + relogin 회복 + legacy token A006` 까지로 닫고, actor audit/persistent audit/account lock 결합은 explicit reopen 조건으로만 남겼음
- 이유: revoke correctness와 감사/운영성 확장은 같은 축이 아니다. 1차 hardening은 “보호 경계가 실제로 작동하는가”를 닫는 일이고, audit 확장은 별도 요구가 생길 때 다시 여는 편이 작업 추적과 우선순위 관리에 더 맞다

## 281) listing형 source를 canonical 정책 스키마에 같이 눌러 담으면 row grain과 추천 의미가 동시에 깨진다
- 문제: `고용24/워크넷 채용정보`, `마이홈포털 공공주택 모집공고/단지/예비입주자 대기현황` 같은 listing형 source도 신규 source라는 이유만으로 `welfare_services + service_facts` 에 같이 넣으려 하면, 정책 제도 row와 공고/단지/상태 feed row가 한 테이블에서 섞여 `unifiedCategory`, 북마크, CTR, 추천 lane 의미가 모두 흐려질 수 있었음
- 해결: [policy-listing-source-schema-draft.md](../history/policy/policy-listing-source-schema-draft.md) 에서 listing형 source는 `listing_items` 공통 header와 `job_listings`, `housing_recruitments`, `housing_complexes`, `housing_waitlist_stats` detail table로 분리하고, raw truth / listing inventory truth / 정책 canonical truth를 서로 다른 층으로 두는 방향을 고정했음
- 이유: source onboarding의 핵심은 “새 row를 어디엔가 저장하는 것”이 아니라 “그 row grain에 맞는 도메인으로 받는 것”이다. listing inventory를 정책 canonical로 강제 정규화하면 이후 read-model과 추천 semantics가 더 큰 비용으로 무너진다

## 282) 정책형 source도 canonical 적합도와 live validation 비용이 다르므로 한 번에 병렬 확장하면 기준 source 없이 설계가 흔들릴 수 있다
- 문제: `Gov24/보조금24`, `정부지원일자리정보`, `구직자취업역량 강화프로그램` 모두 정책형 source 후보이긴 하지만, `official facts` 강도와 compat bridge 필요도가 서로 달라 한 번에 같이 열면 “어느 source를 canonical 기준선으로 삼는지”가 흐려질 수 있었음
- 해결: [policy-source-canonical-onboarding-priority.md](../policy/policy-source-canonical-onboarding-priority.md) 에서 정책형 source canonical onboarding 우선순위를 `Gov24/보조금24 -> 정부지원일자리정보 -> 구직자취업역량 강화프로그램` 순서로 고정하고, live validation 도 같은 순서로 밟도록 정리했음
- 이유: canonical 확장은 source 수를 늘리는 속도보다 기준선을 먼저 세우는 것이 중요하다. `Gov24` 처럼 `core/detail/facts` 강도가 높은 source를 먼저 붙여야 이후 일자리/프로그램형 source 해석도 덜 흔들린다

## 283) 장학금 reference를 제도 row에 flatten 하면 정책 1건 의미와 상세 matrix 둘 다 잃기 쉽다
- 문제: 국가장학금/학자금 계열에서 지원가능대학, 학기별 금액표, 지원구간 경곗값 같은 matrix를 `welfare_services` row로 직접 flatten 하면 대학/학기별 파생 row가 과도하게 늘고, 반대로 전부 `service_facts` 로만 밀어 넣으면 장학금 상품 1건에 붙는 세부 variation을 잃기 쉬웠음
- 해결: [policy-scholarship-reference-matrix-draft.md](../history/policy/policy-scholarship-reference-matrix-draft.md) 에서 장학금 상품은 canonical 정책 row로 유지하고, 세부 대학/학기/구간 정보는 `scholarship_reference_sets + scholarship_reference_rows` reference matrix로 분리하는 초안을 고정했음
- 이유: 장학금 계열은 “추천 카드로 보여줄 제도 row”와 “상세 안내를 위한 variation matrix”를 분리해야 한다. 그래야 추천/북마크 의미를 보존하면서도 대학/학기별 상세 정보 손실을 막을 수 있다

## 284) 복지로 live detail 검증은 `detail coverage` 와 `fact coverage` 를 한 번에 보지 말고 순서를 고정해야 해석 오류를 줄일 수 있다
- 문제: 복지로 쪽은 `stored detail payload coverage`, `welfare_service_details` 저장 여부, `service_facts density`, `optional fact` 판단이 서로 다른 층인데, 이를 한 번에 보면 extractor 문제인지 payload ceiling인지, 또는 detail 저장과 fact 저장 중 어디가 비는지 쉽게 섞여 보일 수 있었음
- 해결: [policy-bokjiro-detail-validation-rehearsal.md](../history/policy/policy-bokjiro-detail-validation-rehearsal.md) 에서 리허설 순서를 `stored/raw coverage baseline -> detail payload shape / welfare_service_details -> service_facts density -> residual sample 재분류` 로 고정하고, `BK_APPLY_END_DATE` 는 이번 단계에서도 optional fact 전제로 확인한다고 정리했음
- 이유: 복지로 validation은 “얼마나 많이 받았는가”와 “받은 것 중 무엇을 hard fact로 승격할 수 있는가”를 분리해서 봐야 한다. 이 순서를 고정해야 gap-fill, extractor, optional soft signal 판단이 서로 덜 엉킨다

## 285) 복지로 detail budget observability 는 계산값이 있다는 이유만으로 바로 admin response 계약에 올리면 multi-round 의미가 흐려질 수 있다
- 문제: `BokjiroDetailCollectService` 는 이미 `centralBudget/localBudget` 을 계산하고 `metadataJson` / service log에도 남기지만, 이 값을 곧바로 `bokjiro-details-gap-fill` admin response에 넣으면 “마지막 round budget인지, 합계인지, round별 array인지” 해석이 애매해질 수 있었다
- 해결: [policy-bokjiro-detail-budget-observability-policy.md](../history/policy/policy-bokjiro-detail-budget-observability-policy.md) 에서 현재 phase에서는 budget metadata를 service 내부 metadata/log 에만 유지하고, admin API response는 rounds/saved/failed 같은 summary 중심 계약으로 유지한다고 고정했음
- 이유: observability는 “이미 계산하는 값이면 다 응답에 올린다”가 아니라, 운영자가 어떤 결정을 위해 어떤 granularity가 필요한지에 맞춰야 한다. 지금 단계의 1차 운영 지표는 budget 자체보다 coverage/fact density라 response 확장을 서두르지 않는 편이 안전하다

## 286) 복지로 gap-fill 은 `95/API` 상한이 있다고 해서 그 값을 기본 운영값으로 삼으면 coverage 실험과 catch-up run이 구분되지 않는다
- 문제: `bokjiro-details-gap-fill` 는 per-run `95/API` cap 을 유지하지만, 그 상한을 바로 기본값처럼 쓰면 “작은 예산으로 coverage slope를 보는 단계”와 “backlog를 실제로 밀어내는 catch-up 단계”가 운영상 구분되지 않을 수 있었다
- 해결: [policy-bokjiro-gap-fill-budget-strategy.md](../history/policy/policy-bokjiro-gap-fill-budget-strategy.md) 에서 current phase 기본 시작점을 `2 rounds x 20 calls`, 다음 증분을 `2 rounds x 40 calls`, `95/API` 는 catch-up 용 상한으로만 쓰는 전략으로 고정했음
- 이유: gap-fill 은 API를 최대한 많이 태우는 작업이 아니라, coverage ceiling과 fact density 효율을 함께 보는 작업이다. 작은 round/budget step을 먼저 두어야 payload coverage 증가와 fact 증가를 덜 헷갈리고, `95/API` 는 정말 backlog drain 이 필요할 때만 쓰게 된다

## 50) 새 챗 세션의 `last_message_at`가 NULL이면 최근 세션 정렬이 흔들릴 수 있음
- 문제: 챗봇 세션은 생성 직후 메시지가 없을 수 있는데 `last_message_at`를 nullable로 두면 세션 목록 최신순 정렬에서 DB별 NULL 정렬 차이 때문에 방금 만든 세션이 뒤로 밀릴 수 있었음
- 해결: `chat_sessions.last_message_at`를 `NOT NULL DEFAULT CURRENT_TIMESTAMP`로 설계하고 `(user_id, last_message_at DESC)` 인덱스를 함께 추가
- 이유: 세션 생성 직후에도 "최근 대화" 목록의 기준시각이 필요하다. 정렬 기준 컬럼은 가능한 NULL을 피해야 목록 UX와 쿼리 계획이 안정적이다

## 51) MySQL JSON 컬럼은 문자열 포맷을 그대로 보존하지 않을 수 있음
- 문제: `chat_messages.referenced_service_ids`를 JSON 컬럼으로 저장하면 DB가 공백/표현을 정규화할 수 있어, 테스트에서 raw 문자열을 완전히 동일 비교하면 실패할 수 있었음
- 해결: 통합 테스트는 JSON 문자열 전체 포맷이 아니라 포함된 서비스 ID와 빈 배열 여부처럼 의미 기준으로 검증하도록 변경
- 이유: JSON 컬럼 검증은 직렬화 포맷이 아니라 의미값 기준으로 해야 DB 엔진별 정규화 차이에도 안정적이다

## 52) 제목 optional 세션 생성인데 request body를 필수로 두면 첫 진입 흐름이 400으로 막힘
- 문제: `POST /api/chat/sessions`는 `title`이 optional인데 컨트롤러에서 request body 자체를 필수로 받으면, 프론트가 제목 없이 새 세션을 만들 때 빈 body 요청이 400으로 실패할 수 있었음
- 해결: `@RequestBody(required = false)`로 받고 서비스에서 제목을 `trim -> blank면 null`로 정규화하도록 변경
- 이유: "새 대화 시작"은 최소 입력 없이 바로 열려야 한다. optional 필드는 body optional 처리까지 같이 가야 실제 UX와 API 계약이 맞는다

## 53) 챗 세션 권한 오류를 구분해 응답하면 다른 사용자의 세션 존재를 추측할 수 있음
- 문제: 메시지 목록 조회에서 "없는 세션"과 "남의 세션"을 다른 오류로 나누면, 응답 차이만으로 다른 사용자의 세션 ID 존재 여부를 유추할 수 있었음
- 해결: `GET /api/chat/sessions/{sessionId}/messages`도 삭제 API와 동일하게 `findByIdAndUserId`를 사용하고, 존재하지 않거나 소유하지 않은 경우 모두 `CH001` 404로 통일
- 이유: 본인 리소스만 다루는 API는 권한 실패와 미존재를 같은 외부 응답으로 감싸는 편이 안전하다. 내부 구현보다 리소스 존재 노출 최소화가 우선이다

## 54) 챗봇 질문 원문을 그대로 Boolean FULLTEXT에 넣으면 기호성 입력에서 검색어 품질이 흔들릴 수 있음
- 문제: 챗봇 질문에는 `?`, `!`, `/`, 조사 섞인 짧은 문장처럼 FULLTEXT Boolean Mode에 그대로 넣기 애매한 토큰이 자주 들어오는데, 이를 정리하지 않으면 후보 조회가 불안정하거나 의미 없는 검색으로 흘러갈 수 있었음
- 해결: `ChatPolicyService`에서 `[0-9A-Za-z가-힣]+` 토큰만 추려 `+token` 검색어를 만들고, 검색 가능한 토큰이 하나도 없으면 즉시 인기 청년 정책 fallback 조회로 넘기도록 고정
- 이유: 챗봇 입력은 검색창보다 노이즈가 많다. 질문 원문을 그대로 SQL 검색어로 쓰지 말고, 최소한의 토큰 정규화와 fallback 기준을 먼저 고정해야 이후 LLM 연결 시에도 후보 품질이 흔들리지 않는다

## 55) 제목 없는 세션에 첫 메시지까지 지나가면 최근 대화 목록에서 구분이 어려워짐
- 문제: `POST /api/chat/sessions`는 제목 없이 세션을 만들 수 있는데, 이후 메시지 전송 단계에서도 제목을 계속 비워 두면 최근 대화 목록이 여러 개의 빈 제목 세션으로 쌓여 사용자가 구분하기 어려워질 수 있었음
- 해결: `POST /api/chat/sessions/{sessionId}/messages`에서 세션 제목이 비어 있으면 첫 사용자 질문 앞부분을 제목으로 자동 채우도록 변경
- 이유: 새 대화 시작은 무입력으로 열 수 있어야 하지만, 첫 질문 이후에는 최소한의 식별 제목이 있어야 세션 목록 UX가 유지된다

## 56) 통합 테스트용 정책 fixture도 운영 스키마 제약을 그대로 맞춰야 함
- 문제: 챗 메시지 전송 통합 테스트에서 임시 `welfare_services` 데이터를 넣을 때 `api_view_count NOT NULL`, `source_id VARCHAR(50)` 제약을 놓치면, 기능 코드와 무관하게 저장 단계에서 테스트가 깨질 수 있었음
- 해결: 테스트 fixture에 `apiViewCount=0`을 명시하고, `source_id`는 별도 짧은 prefix + 축약 UUID로 생성하도록 수정
- 이유: 검색/추천/챗봇 테스트는 실제 정책 테이블을 공유한다. 임시 row라도 운영 스키마와 동일한 제약을 만족하도록 만들지 않으면 이후 다른 테스트에서도 같은 종류의 실패가 반복된다

## 57) LLM이 후보 밖의 정책 ID를 만들어내면 잘못된 상세 링크와 환각 응답이 생길 수 있음
- 문제: 챗봇이 OpenAI 응답의 `service_id`를 그대로 신뢰하면, 모델이 후보 목록에 없는 정책 ID를 만들어낼 때 프론트 상세 링크와 근거 정책 카드가 잘못 연결될 수 있었음
- 해결: `ChatAiGateway`에서 JSON 응답을 파싱한 뒤 서버가 전달한 후보 정책 allowlist와 대조해 일치하는 `service_id`만 참조 목록에 남기도록 검증
- 이유: LLM 응답은 텍스트 생성 결과이지 DB foreign key가 아니다. 근거 정책 연결은 모델이 아니라 서버가 최종 검증해야 보안과 정합성이 유지된다

## 58) 로그아웃은 토큰만 지우고 챗 히스토리를 남겨두면 재로그인 후 이전 대화가 그대로 노출될 수 있음
- 문제: 로그아웃/회원탈퇴 흐름이 refresh token만 정리하고 `chat_sessions`, `chat_messages`를 건드리지 않으면, 로그인 전용 챗봇 요구사항과 달리 재로그인 시 이전 세션이 그대로 남을 수 있었음
- 해결: `ChatSessionCleanupService`를 두고 `AuthService.logout*`, `UserService.withdraw`에서 공용으로 호출해 사용자별 세션을 삭제하고, FK cascade로 메시지까지 함께 정리하도록 고정
- 이유: 보안 기대치가 높은 개인화 상담 히스토리는 인증 종료 시점과 계정 삭제 시점에 같이 정리되어야 한다. 정리 로직을 서비스 하나로 모아야 logout/withdraw 경로가 늘어나도 누락이 줄어든다

## 59) rate limit를 메시지 저장 뒤에 걸면 차단된 요청도 USER 메시지나 세션 갱신이 남을 수 있음
- 문제: 챗봇 abuse 방지를 뒤늦게 적용하면, 상한을 넘긴 요청이 거절되더라도 이미 USER 메시지 저장이나 `last_message_at` 갱신이 일부 반영돼 세션 이력이 어그러질 수 있었음
- 해결: `ChatMessageService.sendMessage()`에서 활성 사용자 확인 직후 `ChatRateLimitService`를 먼저 호출하고, `CH002`가 나면 세션 조회와 메시지 저장을 시작하지 않도록 순서를 고정
- 이유: rate limit는 단순 에러 응답이 아니라 쓰기 트랜잭션 진입 자체를 막는 보호장치다. 차단 시점이 늦으면 보안뿐 아니라 데이터 정합성도 같이 흔들린다

## 60) 프론트 로그아웃이 로컬 토큰만 지우면 백엔드 logout 정리 로직이 실행되지 않음
- 문제: 프론트가 `localStorage`와 Zustand 상태만 비우고 `/api/auth/logout`을 호출하지 않으면, refresh cookie 무효화와 챗 세션 정리 같은 서버 종료 처리들이 실제 UI 로그아웃 경로에서는 빠질 수 있었음
- 해결: 공용 `performServerLogout()`를 두고 Header 로그아웃, 비밀번호 변경 후 재로그인, 회원탈퇴 후 종료 흐름에서 모두 서버 `/api/auth/logout`을 먼저 호출한 뒤 로컬 상태를 비우도록 통일
- 이유: 로그아웃은 단순 클라이언트 상태 초기화가 아니라 서버 세션 종료 절차다. 프론트와 백엔드가 다른 정의를 가지면 refresh 재발급, 챗 히스토리 정리, 보안 감사 포인트가 모두 어긋난다

## 61) 비밀번호 재설정 요청에서 "없는 이메일"을 에러로 돌리면 계정 존재 여부를 추측할 수 있음
- 문제: 비밀번호 재설정 요청 API가 가입된 이메일과 미가입 이메일에 서로 다른 응답을 주면, 공격자가 응답 차이만으로 계정 존재 여부를 수집할 수 있었음
- 해결: `POST /api/auth/password-reset/request`는 활성 사용자에게만 실제 메일을 보내되, 없는 이메일이나 탈퇴 계정이어도 항상 동일한 성공 응답을 반환하도록 고정
- 이유: 비밀번호 재설정은 대표적인 계정 열거 지점이다. 인증 전 단계에서는 "성공/실패"보다 계정 정보 비노출이 우선이다

## 62) `SECURITY_ADMIN_EMAILS`만 바꾸고 앱을 재기동하지 않으면 운영 admin 권한 반영이 누락될 수 있음
- 문제: 관리자 이메일 allowlist는 `AuthService` 시작 시 1회 로딩되므로, `.env`의 `SECURITY_ADMIN_EMAILS`만 수정하고 앱을 재기동하지 않으면 DB에 admin row를 넣어도 로그인/refresh 시 `ROLE_ADMIN`이 반영되지 않을 수 있었음
- 해결: 운영 admin 계정 런북에 "allowlist 변경 -> 앱 재기동 -> DB row 생성/검증" 순서를 고정하고, 배포 가이드에도 재기동 주의사항을 명시
- 이유: 이 문제는 DB 데이터 오류처럼 보이지만 실제 원인은 stale app config다. 운영 절차에 재기동 단계를 명시해야 같은 혼선을 반복하지 않는다

## 63) 지역 추천 후보를 `LEFT JOIN + DISTINCT`로 조회하면 중복 제거 임시 테이블 비용이 커질 수 있음
- 문제: `service_regions` row가 많은 상태에서 추천 지역 후보를 `LEFT JOIN service_regions` 후 `DISTINCT`로 정리하면, 매칭 여부 판정보다 dedup 임시 테이블 비용이 먼저 커져 실행 계획이 불필요하게 무거워질 수 있었음
- 해결: 추천 지역 후보 JPQL을 `NOT EXISTS/EXISTS` 기반으로 바꾸고, `service_regions(service_id, sido_name, sgg_name)` 및 `(service_id, region_code)` 복합 인덱스를 추가해 지역 매칭 판정을 join 결과 정리가 아닌 존재 여부 검사로 고정
- 이유: 이 도메인의 지역 판정은 "어떤 row가 붙었는가"가 아니라 "매칭 row가 존재하는가"가 핵심이다. 존재 여부 조건으로 표현해야 중복 결과와 임시 테이블 비용을 함께 줄일 수 있다

## 64) 새 인덱스를 추가해도 MySQL 통계가 stale 하면 기대한 실행 계획이 바로 나오지 않을 수 있음
- 문제: `service_regions` 복합 인덱스를 다시 적용한 뒤 EXPLAIN을 돌렸을 때, 인덱스가 실제로 존재해도 옵티마이저가 계속 기존 `idx_sr_service` 경로를 선택해 검증 결과가 흔들릴 수 있었음
- 해결: 마이그레이션 적용 직후 `SHOW INDEX`로 생성 여부를 확인하고, 이어서 `ANALYZE TABLE service_regions`까지 실행하도록 운영 절차와 migration 문서에 반영
- 이유: 인덱스 추가와 통계 갱신은 같은 성능 변경 절차로 봐야 한다. 생성만 확인하고 EXPLAIN을 바로 믿으면 환경마다 다른 플랜을 보고도 원인을 놓치기 쉽다

## 65) optional 지역 파라미터를 한 SQL에 섞으면 `sido+sgg` 경로의 복합 인덱스 선택이 흔들릴 수 있음
- 문제: 지역 검색을 `sr2.sido_name = :sido AND (:sgg IS NULL OR sr2.sgg_name = :sgg)` 한 쿼리로 처리하면, `sgg`가 실제로 주어진 요청에서도 옵티마이저가 `service_id+sido_name+sgg_name` 복합 인덱스를 안정적으로 고르지 못해 성능이 흔들릴 수 있었음
- 해결: 검색 쿼리를 `searchByKeywordWithFiltersWithSido` 와 `searchByKeywordWithFiltersWithSidoSgg` 두 개로 분리하고, 서비스에서 `sgg` 유무에 따라 분기하도록 변경
- 이유: optional 조건은 API 레벨에선 편하지만, SQL 레벨에선 실행 계획을 흐리게 만들 수 있다. 지역처럼 호출이 잦고 인덱스 선택이 중요한 조건은 optional 분기를 서비스에서 나누는 편이 안전하다

## 66) 추천 지역 후보에서 `regionCode OR sidoName` 을 한 EXISTS에 묶으면 프로필 조건과 맞는 인덱스를 직접 타기 어려울 수 있음
- 문제: 추천 후보 쿼리를 `sr2.region_code = :regionCode OR sr2.sido_name = :sidoName` 한 조건으로 유지하면, 사용자가 실제로는 `regionCode`를 가지고 있어도 옵티마이저가 `region_code` 복합 인덱스를 직접 타지 못하거나 불필요한 OR 평가를 같이 안고 갈 수 있었음
- 해결: 추천 후보 쿼리를 `findCandidatesWithRegionCode` / `findCandidatesWithSido` 와 최신순 대응 쿼리로 분리하고, `RetrievalService`에서 `regionCode` 유무에 따라 분기하도록 변경
- 이유: 추천은 사용자 프로필이 이미 정해진 상태에서 호출되므로 optional 분기를 SQL 안에 남길 이유가 적다. 서비스에서 분기하면 쿼리 의미가 단순해지고 인덱스 선택도 예측 가능해진다

## 67) CTR 로그가 적은 초기 구간에서는 AI 품질 결론보다 baseline 관측에 집중해야 함
- 문제: 현재 `recommendation_logs`는 `46건 / 클릭 1건 / fallback 16건 / 가중치 0.80:0.20 단일 구간 / 하루치 2명 사용자` 수준이라, 이 수치만으로 "AI가 rule보다 낫다"거나 프롬프트/가중치를 조정할 근거로 쓰면 오판할 수 있었음
- 해결: 이번 작업에서는 CTR 분석 쿼리 실행 결과를 baseline으로만 문서화하고, `CTR 표본 추가 확보 후 rule/AI 가중치 및 프롬프트 재분석`을 후속 작업으로 분리
- 이유: CTR은 로그가 존재하는 것만으로 충분하지 않고 표본 규모와 분포가 같이 필요하다. 특히 fallback 비교와 가중치 단계 비교는 다일자/다사용자 데이터가 쌓이기 전까지는 운영 판단보다 관측 지표로 다루는 편이 안전하다

## 68) `user_key` 선행 없이 바로 PII 분리 테이블로 갈아타면 `user_id` 연관 전반이 한 번에 깨질 수 있음
- 문제: 현재 구조는 `AuthService`, `UserService`, `NotificationService`, 추천/채팅/로그 테이블이 모두 `users.id` 와 `@ManyToOne User` 에 묶여 있어, `auth_users/user_profiles/user_pii` 를 먼저 만들더라도 공용 식별자 없이 곧바로 cut-over 하면 JWT, Redis key, FK, JPA 연관이 동시에 흔들릴 수 있었음
- 해결: PII 분리 이행안에서 `users.user_key` 및 하위 테이블 `user_key` backfill을 1단계 선행 작업으로 확정하고, 그 다음에 `dual-write -> read cut-over -> legacy 제거` 순서로 진행하도록 문서화
- 이유: 테이블 분리의 실제 리스크는 schema 생성보다 식별자 전환이다. 호환 기간 동안 `user_id` 와 `user_key` 를 함께 유지해야 로그/추천/알림/챗 세션 같은 주변 테이블을 안전하게 단계별로 넘길 수 있다

## 69) `user_key` migration은 기존 row backfill만 하고 끝내면 다음 가입자부터 다시 NULL 이 생길 수 있음
- 문제: `users.user_key` 를 nullable로 추가하고 기존 사용자만 UPDATE 하면, dual-write 릴리스 전까지 새 회원가입 row는 다시 `user_key=NULL` 로 들어가 이후 하위 테이블 backfill과 cut-over 기준이 흔들릴 수 있었음
- 해결: `users.user_key` 를 `CHAR(32) NOT NULL DEFAULT (REPLACE(UUID(), '-', ''))` 로 추가하고, 기존 row는 1회 backfill 후 unique key를 걸도록 migration과 schema를 작성
- 이유: 식별자 선행 단계는 "과거 데이터 정리"와 "이행 기간 신규 데이터 보호"를 같이 해야 의미가 있다. 새 가입자가 다시 NULL 상태로 들어오면 단계적 전환의 전제가 바로 깨진다

## 70) 기존 행 `UUID()` 대량 UPDATE는 MySQL binlog safety에 걸릴 수 있음
- 문제: `UPDATE users SET user_key = REPLACE(UUID(), '-', '')` 형태로 기존 사용자를 backfill 하면, MySQL이 "replica에서 값이 달라질 수 있는 시스템 함수"로 보고 실행을 막거나 경고할 수 있었음
- 해결: 기존 사용자 backfill은 `SUBSTRING(SHA2(CONCAT('user:', id), 256), 1, 32)` 같은 deterministic 식으로 바꾸고, 새 가입자 자동 채움은 `DEFAULT (REPLACE(UUID(), '-', ''))` 를 유지하되 migration 세션 시작 시 `SET SESSION sql_log_bin = 0`을 실행하도록 수정
- 이유: migration의 목적은 호환 식별자를 안정적으로 심는 것이다. 과거 데이터는 결정적 값으로 채우고, 신규 데이터는 insert-time default를 쓰되, 수동 마이그레이션 세션에서만 binlog safety 제약을 우회하는 편이 현재 운영 방식과 가장 잘 맞는다

## 71) `user_pii` 초기 backfill에서 이메일/이름/생년월일을 SQL로 바로 암호화하려 들면 앱 포맷과 어긋날 수 있음
- 문제: 현재 애플리케이션의 PII 암호화는 Java `AesEncryptUtil` 포맷을 기준으로 동작하는데, migration SQL만으로 `email/name/birth_date`를 같은 포맷으로 안전하게 변환하려 하면 향후 복호화 호환성이나 키 관리 기준이 어긋날 수 있었음
- 해결: `V2026_04_27_03__add_user_core_split_tables.sql`에서는 `user_pii` 테이블과 `phone_enc` seed만 먼저 만들고, `email_enc/name_enc/birth_date_enc` backfill은 후속 dual-write 릴리스에서 앱 레벨 암호화로 채우도록 분리
- 이유: 스키마 준비와 암호화 포맷 전환은 분리하는 편이 안전하다. 특히 이미 서비스 코드가 가진 암호화 규칙이 있을 때는 SQL 편의 변환보다 애플리케이션 동일 경로를 재사용하는 것이 재현성과 복구 가능성 면에서 낫다

## 72) MySQL cross-schema 테이블은 JPA에서 `schema`보다 `catalog` 매핑이 더 직접 맞을 수 있음
- 문제: `youth_welfare_pii.user_pii` 테이블이 실제로 존재해도, `@Table(schema = "youth_welfare_pii")` 로만 매핑하면 Hibernate validate 단계에서 missing table로 판단할 수 있었음
- 해결: `UserPii` 엔티티를 `@Table(name = "user_pii", catalog = "youth_welfare_pii")` 로 바꾸고 통합 테스트로 검증
- 이유: MySQL에서 database와 schema 개념이 사실상 catalog로 취급되는 경로가 있다. cross-schema entity는 DB 엔진 용어에 맞춰 catalog 매핑을 우선 확인하는 편이 안전하다

## 73) `auth_users` 읽기 전환을 조회만 바꾸고 상태 동기화를 빼면 계정 잠금 기준이 stale 해질 수 있음
- 문제: 로그인/비밀번호 재설정 조회를 `auth_users.email_lookup_hash` 기준으로 바꾸기만 하고, 로그인 실패 횟수와 `locked_until` 갱신은 계속 legacy `users` 에만 남겨두면 다음 로그인부터 `auth_users` 의 잠금 상태가 stale 해져 잘못된 허용/차단이 발생할 수 있었음
- 해결: 로그인 성공/실패 후에는 legacy `users` 를 먼저 갱신하고, 즉시 `UserCoreSyncService.syncFromUser` 를 호출해 `auth_users` 의 `password_hash`, `login_fail_count`, `locked_until`, `is_active` 를 다시 맞추도록 고정
- 이유: dual-write 단계의 read cut-over 는 "어느 테이블에서 읽느냐"만이 아니라 "그 테이블이 언제 최신 상태로 유지되느냐"까지 포함한다. 인증처럼 상태 기반 분기가 있는 경로는 읽기 전환과 상태 재동기화를 한 작업으로 묶어야 재발을 막을 수 있다

## 74) 앱 레벨 PII backfill도 원본 `users` 값이 이미 비어 있으면 복구할 수 없음
- 문제: `user_pii.email_enc/name_enc/birth_date_enc` 를 앱 레벨 암호화로 채우더라도, 일부 row는 source of truth 인 `users.name` 또는 `users.birth_date` 가 이미 `NULL` 이어서 암호문을 다시 만들 수 없었음
- 해결: 백필 서비스는 누락 암호문만 채우되, 원본 값이 없는 row는 overwrite 시도 없이 `skippedCount` 로 남기고 검증 결과에 별도로 기록
- 이유: 이 단계의 backfill은 기존 원문이 남아 있는 범위만 안전하게 옮기는 작업이다. 이미 scrub 된 값까지 복원하려고 들면 잘못된 placeholder 데이터를 넣거나 탈퇴/테스트 fixture 상태를 오염시킬 수 있다

## 75) 비밀번호 재설정 메일 발송이 계속 `users.email` 을 읽으면 PII read path 전환 후에도 legacy 의존이 남음
- 문제: 로그인/비밀번호 재설정 조회는 이미 `auth_users` 기준으로 넘어갔는데, reset 메일 발송 주소만 계속 `users.email` 을 사용하면 `user_pii.email_enc` backfill 이후에도 인증 경로가 legacy PII 컬럼에 의존하게 되어 read cut-over 경계가 흐려질 수 있었음
- 해결: `AuthService.requestPasswordReset` 이 `auth_users -> user_key -> user_pii.email_enc` 경로에서 발송 주소를 읽고, `AesEncryptUtil` 로 복호화한 값으로 메일을 보내도록 변경
- 이유: PII 분리 단계에서는 "누가 사용자 식별을 담당하는가"와 "어디서 원문 연락처를 읽는가"를 같이 끊어야 한다. 비밀번호 재설정은 인증 전 기능이지만 실제 발송 주소는 PII 저장소에서만 읽도록 고정해야 이후 `users.email` 제거가 가능하다

## 76) `user_key` 컬럼은 migration만 `CHAR(32)` 로 맞추고 JPA 매핑을 기본값으로 두면 통합 테스트 부팅에서 바로 깨질 수 있음
- 문제: `chat_sessions`, `notifications`, `recommendation_logs`, `service_view_logs`, `users` 의 `user_key` 컬럼은 migration에서 `CHAR(32)` 로 생성됐는데, 엔티티 매핑을 `length = 32` 만 두면 Hibernate validate 가 `VARCHAR(32)` 로 기대해 애플리케이션 부팅이 실패할 수 있었음
- 해결: 새로 매핑한 `user_key` 필드 전부에 `columnDefinition = "CHAR(32)"` 를 명시하고 `./gradlew integrationTest --no-daemon` 로 schema validate 까지 확인
- 이유: `user_key` 는 앞으로 공용 식별자이기 때문에 한 테이블만 타입이 어긋나도 배포 즉시 부팅 실패로 이어진다. migration과 JPA 매핑을 항상 한 쌍으로 맞춰야 재발을 막을 수 있다

## 77) split-table 도입 뒤 테스트나 수동 정리에서 `users` 만 지우면 `auth_users/user_profiles/user_pii` orphan 이 남아 중복 충돌을 만들 수 있음
- 문제: `users` row 만 삭제하고 split table row 를 그대로 두면, 같은 이메일로 다시 fixture 를 만들 때 `auth_users.email_lookup_hash` unique 충돌이 발생할 수 있었음
- 해결: 고정 이메일 fixture 를 쓰는 `AdminSecurityIntegrationTest` 에서 `UserCoreSyncService` 기반 생성과 `auth_users/user_profiles/user_pii` 동시 정리 루틴을 추가
- 이유: dual-write 단계에서는 legacy 테이블 하나만 source of truth 라고 가정하면 안 된다. 테스트 fixture, 수동 운영 스크립트, admin bootstrap 모두 split table 동시 정리 기준을 따라야 재시도 가능성이 유지된다

## 78) `user_key` 기준 소유권/cleanup 으로 넘어간 뒤 직접 insert fixture 가 `user_key` 를 빼먹으면 새 경로 검증이 어긋날 수 있음
- 문제: `chat_sessions`, `notifications`, `recommendation_logs` 를 `user_key` 기준으로 조회/cleanup 하도록 바꾼 뒤에도 테스트나 수동 SQL fixture 가 `user_id` 만 채우고 `user_key` 를 비워두면, 로그아웃 cleanup, 채팅 소유권 검증, 알림 retry 가 실제 런타임 경로와 다르게 동작할 수 있었음
- 해결: 관련 integration/unit test 의 직접 insert fixture 를 `userId + userKey` 동시 기록 기준으로 정리하고, 새 코드에서는 `user_key` 누락 시 fallback 하지 않도록 고정
- 이유: cut-over 단계에서 가장 위험한 상태는 "코드는 새 식별자를 쓰는데 fixture 만 옛 식별자를 쓰는 경우"다. 테스트 데이터도 운영 경로와 같은 식별자 계약을 강제해야 재발을 막을 수 있다

## 79) legacy refresh token fallback 제거는 배포 시점에 구형 토큰을 즉시 무효화한다
- 문제: refresh token subject 숫자 fallback 과 `refresh:<userId>` Redis key fallback 을 제거하면, cut-over 이전 형식으로 발급된 refresh token 은 재발급에 실패하게 된다
- 해결: 현재 코드는 `user_key` subject 와 `refresh:<userKey>` 저장분만 허용하도록 정리했고, full 검증은 `./gradlew test --no-daemon`, `./gradlew integrationTest --no-daemon` 로 확인
- 이유: fallback 을 오래 끌수록 `user_id` 제거가 다시 어려워진다. 이 단계에서는 호환성보다 식별자 계약 단일화가 더 중요하고, 구형 refresh token 은 재로그인으로 회복 가능하므로 의도적 정리로 보는 편이 맞다

## 80) `@WebMvcTest(addFilters = false)` 슬라이스에서는 custom principal 로 바꿔도 `@AuthenticationPrincipal` 이 계속 `null` 일 수 있음
- 문제: JWT filter 에서 `AuthenticatedUser` principal 을 심도록 바꾼 뒤에도, 기존 `@WebMvcTest(addFilters = false)` 슬라이스 테스트는 Security filter chain 을 타지 않아 `@AuthenticationPrincipal` 이 계속 `null` 로 들어와 controller NPE 가 발생할 수 있었음
- 해결: controller 쪽 principal 해석을 `null` 안전하게 정리하고, WebMvc 슬라이스 테스트는 기존처럼 `null` principal 기준 mock 기대값을 유지한 채 별도로 filter/security 경로는 `AdminSecurityWebMvcTest` 와 integration test 로 검증
- 이유: principal 객체 전환 자체와 Spring MVC 슬라이스에서 보안 resolver 가 실제 runtime 과 다르게 동작하는 문제를 분리해야 한다. controller 를 null-safe 하게 두고, 인증 해석 검증은 filter 를 실제로 태우는 테스트에서 확인하는 편이 재발 방지에 더 안정적이다

## 81) `users.user_key` 가 DB default 로 채워지는 구조에서는 freshly saved `User` 엔티티가 즉시 `userKey` 를 들고 있다고 가정하면 안 됨
- 문제: `users.user_key` 는 DB default 와 `insertable = false, updatable = false` 매핑을 쓰기 때문에, 저장 직후 in-memory `User` 엔티티의 `getUserKey()` 는 아직 `null` 일 수 있었음. 이 상태에서 `user_recommendations` placeholder 나 북마크 fixture 가 엔티티 필드만 믿고 `userKey` 를 기록하면 누락 row 가 생길 수 있었음
- 해결: 추천/북마크 경로와 integration test fixture 는 `userRepository.findUserKeyById()` 로 다시 읽은 값만 사용해 `user_recommendations.user_key` 를 채우도록 고정
- 이유: DB default 기반 식별자는 "저장 성공"과 "엔티티 메모리 값 가시화"가 같은 시점이 아니다. cut-over 단계에서는 식별자 컬럼을 직접 참조하는 write path와 fixture가 항상 persisted 값을 다시 읽는 편이 안전하다

## 82) read path가 `user_key` 로 바뀌었다고 바로 모든 `user_id` 컬럼을 drop 하면 쓰기 경로가 깨질 수 있음
- 문제: `user_recommendations`, `chat_sessions`, `notifications` 같은 runtime 테이블은 이미 `user_key` 기준 read/write 로 넘어갔지만, `user_attributes` 와 `user_priorities` 는 아직 `deleteByUserId`, `findByUserId*`, `@ManyToOne User` 저장 경로가 남아 있어 모든 `user_id` 컬럼/FK를 한 번에 제거하면 프로필 수정, 우선순위 저장, 회원탈퇴 정리 로직이 바로 깨질 수 있었음
- 해결: legacy `user_id` drop 설계를 2단계로 분리해, 먼저 `user_attributes` / `user_priorities` 의 write/delete 경로를 `user_key` 기준으로 바꾸고 그 다음에 runtime 테이블의 `user_id` 인덱스/FK/컬럼 제거 SQL을 적용하도록 문서화
- 이유: read cut-over와 write cut-over는 같은 완료 조건이 아니다. 컬럼 제거는 "최신 저장/삭제 경로까지 새 식별자를 쓴다"는 것이 확인된 뒤에만 안전하다

## 83) `@Modifying` delete query 는 서비스 트랜잭션 밖에서 바로 호출하면 테스트 cleanup 에서 터질 수 있음
- 문제: `user_attributes`, `user_priorities` 정리 경로를 `deleteByUserKey` `@Modifying` JPQL로 바꾼 뒤, 통합 테스트 `@AfterEach` cleanup 에서 이를 직접 호출하자 `TransactionRequiredException` 이 발생했음
- 해결: 서비스 본문은 기존처럼 `@Transactional` 경계 안에서 `deleteByUserKey` 를 사용하고, 테스트 cleanup 은 `findByUserKey* -> deleteAll(...)` 경로로 분리해 정리
- 이유: 즉시 DELETE 보장은 쓰기 트랜잭션 안에서는 필요하지만, 테스트 정리 코드는 같은 제약을 공유하지 않는다. 운영 경로와 cleanup 경로를 같은 메서드로 억지로 맞추기보다 각 경계에 맞는 삭제 방식을 쓰는 편이 안전하다

## 84) runtime 테이블에서 `user_id` FK를 제거하면 테스트의 `userRepository.delete()` 정리 가정이 깨질 수 있음
- 문제: `chat_sessions`, `user_recommendations`, `recommendation_logs`, `notifications` 의 `user_id` FK를 제거한 뒤에도 기존 integration test cleanup 이 `userRepository.delete(user)` 만 호출하면, 런타임 row가 그대로 남아 다음 테스트의 서비스 삭제나 상태 검증을 오염시킬 수 있었음
- 해결: chat/recommendation 관련 integration test cleanup 을 `user_key` 기준 자식 row 정리 후 사용자 삭제 순서로 바꾸고, `ChatSessionRepository.findAllByUserKey`, `UserRecommendationRepository.findByUserKey`, `RecommendationLogRepository.findByUserKey` 같은 cleanup용 조회를 추가
- 이유: legacy FK 제거 이후의 정리 기준은 DB cascade가 아니라 애플리케이션 식별자 계약이다. 테스트도 운영과 같은 `user_key` 기반 정리 순서를 따라야 새 스키마에서 안정적으로 반복 실행된다

## 85) Compose에서 앱 DB 계정만 `root`에서 바꾸고 MySQL 초기화 grant를 같이 안 넣으면 신규 환경이 바로 부팅 실패할 수 있음
- 문제: `docker-compose.yml`과 `.env`에서 앱 datasource 계정을 `app_core_rw`로 바꾸기만 하면, 새 Docker 볼륨이나 새 운영 DB에는 해당 계정 자체가 없어 앱이 `Access denied`로 시작조차 못 할 수 있었음
- 해결: `deploy/mysql/init/z90-create-runtime-db-users.sh`를 추가해 신규 DB 초기화 시 `app_core_rw`, `app_pii_rw`, `notification_pii_ro`, `migration_admin` 계정과 최소 grant를 함께 생성하고, 기존 DB는 수동 전환이 필요하다는 기준을 배포/마이그레이션 문서에 같이 남겼음
- 이유: 런타임 `root` 제거는 애플리케이션 설정 변경만으로 끝나지 않는다. 계정 생성, 권한 부여, 앱 env 전환, 기존 DB 예외 처리를 한 묶음으로 다뤄야 같은 실수가 반복되지 않는다

## 86) MySQL `docker-entrypoint-initdb.d` 의 `.sh`는 실행 권한이 없으면 `source` 되어 shell option 이 상위 엔트리포인트에 새어 나갈 수 있음
- 문제: 초기화 스크립트에 `set -euo pipefail` 을 넣은 상태로 파일이 non-executable 이면 MySQL entrypoint 가 이 파일을 `source` 해서 읽고, `set -u` 가 상위 엔트리포인트까지 남아 `MYSQL_ONETIME_PASSWORD: unbound variable` 로 init 전체가 깨질 수 있었음
- 해결: `deploy/mysql/init/z90-create-runtime-db-users.sh` 를 executable 로 두고, smoke test 에서 entrypoint 로그가 `running /docker-entrypoint-initdb.d/z90-create-runtime-db-users.sh` 형태로 분리 실행되는 것을 확인했음
- 이유: Docker init 스크립트는 내용뿐 아니라 실행 방식도 배포 결과를 바꾼다. shell option 을 강하게 쓰는 스크립트는 반드시 독립 프로세스로 실행되게 해야 다른 init 단계에 부작용을 남기지 않는다

## 87) 기존 볼륨에서는 드러나지 않던 `schema.sql` 말단 쉼표가 fresh init 에서만 신규 DB 부팅을 막을 수 있음
- 문제: 로컬에 이미 생성된 MySQL 볼륨을 재사용하면 `schema.sql` 이 다시 돌지 않아 숨어 있었지만, fresh init smoke 에서는 `notifications`, `chat_sessions` 정의 끝의 말단 쉼표 때문에 SQL 1064 로 초기화가 중단됐음
- 해결: `backend/src/main/resources/db/schema.sql` 에서 해당 말단 쉼표 2건을 제거하고, 임시 MySQL 8.0 컨테이너로 fresh init 을 다시 검증했음
- 이유: 신규 서버/새 볼륨 부팅 경로는 기존 개발 DB 재사용 경로와 다르다. 배포/초기화 작업을 건드릴 때는 항상 fresh init smoke 를 같이 돌려야 숨은 schema 문법 오류 재발을 막을 수 있다

## 88) 기존 운영 DB에서 계정 전환 시 `CREATE USER IF NOT EXISTS`만 믿으면 예전 비밀번호나 과권한이 그대로 남을 수 있음
- 문제: 신규 볼륨 init 스크립트 방식에 익숙해져 기존 운영 DB에서도 `CREATE USER IF NOT EXISTS`만 실행하면, 이미 존재하던 `app_core_rw`류 계정의 비밀번호와 grant가 그대로 남아 실제 앱 cutover 때 인증 실패나 과권한 상태가 이어질 수 있었음
- 해결: 기존 운영 DB용 템플릿은 `CREATE USER IF NOT EXISTS` 뒤에 `ALTER USER`, `REVOKE ALL PRIVILEGES, GRANT OPTION`, 재부여 `GRANT`를 한 세트로 넣고, 별도 runbook에서 `.env` cutover와 `SHOW GRANTS` 검증까지 같이 수행하도록 정리했음
- 이유: 신규 init과 기존 운영 계정 보정은 성격이 다르다. 기존 DB는 “있으면 생성”이 아니라 “있어도 현재 기준으로 덮어쓰기”가 핵심이라, 비밀번호/권한 재동기화 절차를 문서와 SQL 템플릿에 동시에 고정해야 재발을 막을 수 있다

## 89) `notification_pii_ro` 분리는 계정만 바꾸면 끝나지 않음
- 문제: 기존 알림 대상 조회 SQL은 `user_profiles`, `users`, `auth_users`, `user_pii`를 한 번에 조인하고 있어, 그대로 `notification_pii_ro` datasource로 옮기면 보조 계정에 main schema SELECT 또는 과도한 권한을 다시 줘야 했음
- 해결: 알림 대상 조회를 `core 대상 메타데이터 조회`와 `user_pii.email_enc 조회` 두 단계로 분리하고, `notification_pii_ro` 는 `user_pii(user_key, email_enc)` 읽기만 담당하게 재구성
- 이유: 다중 datasource 분리는 credential 추가만이 아니라 query shape 분리까지 같이 해야 최소권한이 유지된다. cross-schema 조인을 남겨두면 결국 더 넓은 grant가 다시 필요해져 분리 효과가 사라진다

## 90) secondary datasource bean을 직접 추가하면 Spring Boot 기본 `dataSource` 자동 구성이 뒤로 물러날 수 있음
- 문제: `notification_pii_ro`, `app_pii_rw` datasource bean을 직접 추가하자 Spring Boot의 기본 `spring.datasource` 자동 구성이 빠지고, 컨텍스트가 secondary datasource들만 보고 `PlatformTransactionManager` 를 만들지 못하거나 잘못된 datasource를 주 datasource처럼 취급할 수 있었음
- 해결: `PrimaryDataSourceConfig` 로 `spring.datasource` 기반 `dataSource` bean 을 명시적으로 `@Primary` 로 등록하고, secondary datasource는 qualifier 기반 보조 경로로만 사용하도록 고정
- 이유: 다중 datasource는 secondary bean 몇 개를 더 만드는 것으로 끝나지 않는다. 기본 datasource를 어떤 bean이 책임지는지 명시하지 않으면 JPA/트랜잭션/자동 구성 조건이 쉽게 무너진다

## 91) `app_pii_rw` write 분리는 read 분리와 달리 cross-datasource transaction 전략이 먼저 필요함
- 문제: 프로필 sync나 `user_pii` backfill write 를 바로 `app_pii_rw` datasource로 옮기면, legacy `users` / `auth_users` / `user_profiles` 와 `user_pii` 가 서로 다른 connection pool에서 갱신되어 현재 `@Transactional` 경계만으로는 원자성을 보장할 수 없었음
- 해결: 이번 단계는 프로필 조회와 비밀번호 재설정 수신 주소 조회 같은 read path만 `app_pii_rw` 로 먼저 이동하고, write 경로는 별도 transaction 전략 정리 task 뒤로 미뤘음
- 이유: read path는 권한 분리를 바로 얻어도 consistency 리스크가 낮지만, write path는 실패 시 split-table 간 불일치가 바로 남는다. 그래서 read-first, write-later 순서가 안전하다

## 92) `user_pii` write 분리도 요청 경로와 admin batch 경로를 같은 난이도로 보면 일정이 불필요하게 커짐
- 문제: `UserCoreSyncService` 의 요청 dual-write 와 `UserPiiBackfillService` 의 admin batch write 를 같은 “write 분리”로 묶어 보면, backfill 처럼 독립 row update 로 충분한 경로도 cross-datasource 원자성 문제 때문에 같이 멈춰 버릴 수 있었음
- 해결: 이번 단계에서는 `UserPiiBackfillService` 의 target selection 은 기존 primary query 를 유지하되 실제 update 만 `app_pii_rw` 로 옮겨 admin batch write 를 먼저 분리하고, 요청 dual-write 경로는 별도 transaction 전략 task 로 남겼음
- 이유: 같은 write 라도 consistency 요구 수준이 다르다. 요청 경로는 원자성이 중요하지만, admin backfill 은 재실행 가능한 batch 성격이라 먼저 분리해 권한 축소를 앞당길 수 있다

## 93) persistent Docker DB를 쓰는 통합 테스트는 새 migration을 자동 적용하지 않아 schema 검증에서 바로 실패할 수 있음
- 문제: 저장소에는 Flyway 자동 적용이 없어서, `user_pii_sync_queue` 엔티티와 migration 파일만 추가한 뒤 곧바로 통합 테스트를 돌리면 기존 Docker MySQL이 옛 스키마를 유지한 채 `ddl-auto: validate` 단계에서 실패할 수 있었음
- 해결: `V2026_04_28_02__add_user_pii_sync_queue.sql` 을 테스트 Docker MySQL에 수동 적용한 뒤 통합 테스트를 재실행했고, [db-migration.md](./db-migration.md)에 기존 DB/통합 테스트 DB는 migration 수동 적용이 필요하다는 점을 명시
- 이유: 이 저장소의 schema 변경 완료 조건은 `schema.sql` 수정만이 아니라 “기존 DB용 migration SQL 반영 + persistent 테스트 DB 반영”까지 포함된다. 자동 적용이 없는 환경에서는 이 절차를 문서와 검증 루틴에 같이 고정해야 재발을 막을 수 있다

## 94) derived delete repository 메서드를 테스트 cleanup에서 직접 쓸 때는 트랜잭션 의미를 메서드 시그니처에 명시해야 함
- 문제: `UserPiiSyncQueueRepository.deleteByUserKey()` 를 `@AfterEach` cleanup에서 직접 호출하자, 서비스 트랜잭션 밖이라 `TransactionRequiredException` 이 발생했음
- 해결: `UserPiiSyncQueueRepository.deleteByUserKey()` 에 `@Modifying`, `@Transactional` 을 명시해 cleanup에서도 독립 write query로 실행되도록 고정
- 이유: split-table/queue 전환이 늘어날수록 테스트 정리 루틴도 전용 repository delete 메서드를 자주 쓰게 된다. 이 경로는 서비스 본문처럼 이미 트랜잭션 안에 있다고 가정하면 깨지기 쉬우므로, cleanup에서 직접 쓰는 write 메서드는 스스로 트랜잭션 의미를 가져야 안전하다

## 95) Spring Data derived query 이름에 `...StatusInOrderBy...` 를 쓰면 단일 enum 인자를 `IN` 조건으로 오해해 컨텍스트가 뜨기 전에 죽을 수 있음
- 문제: `UserPiiSyncQueueRepository` 에서 실패/대기 queue 정렬 조회를 `findByStatusInOrderBy...` 형태로 추가했는데, 실제 시그니처는 `UserPiiSyncQueueStatus` 단일 인자라 Spring Data가 `status IN (?)` 으로 해석하려다 `Collection argument` 예외로 컨텍스트 초기화 자체가 실패했음
- 해결: 단일 status 조회는 `findByStatusOrderBy...` 로 바꾸고, bulk replay는 서비스에서 `FAILED` 와 `PENDING` 조회를 따로 수행한 뒤 userKey snapshot을 합쳐 재처리하도록 정리했음
- 이유: repository 메서드명 파싱 오류는 컴파일로는 잡히지 않고 부팅 시점에만 터진다. enum 단일값 정렬 조회는 `In` 을 붙이지 않는 쪽이 안전하고, 여러 상태를 합치는 로직은 서비스 계층에서 명시적으로 조합하는 편이 디버깅도 쉽다

## 96) queue retry scheduler 를 바로 켜면 integration test 가 백그라운드 재처리와 경합해 상태 검증이 흔들릴 수 있음
- 문제: `user_pii_sync_queue` 자동 retry 를 `@Scheduled` 로 추가한 뒤 integration test profile 에서도 기본 delay 값을 그대로 쓰면, 테스트가 queue 상태를 조작하는 중간에 백그라운드 스케줄러가 먼저 실행되어 `FAILED/PENDING` 상태 검증이나 replay 순서 검증이 비결정적으로 흔들릴 수 있었음
- 해결: `application-integration.yml` 에서는 `USER_PII_SYNC_RETRY_FIXED_DELAY_MS`, `USER_PII_SYNC_RETRY_INITIAL_DELAY_MS` 를 충분히 크게 고정하고, 통합 테스트는 스케줄러 메서드를 직접 호출해 retry 시점을 명시적으로 제어하도록 정리했음
- 이유: 상태 기반 retry 로직은 "언제 실행됐는가"가 검증 결과 자체를 바꾼다. 테스트 환경에서는 background scheduler를 사실상 멈추고, 필요한 시점에만 직접 실행하는 편이 재현성과 디버깅 비용 모두에서 안전하다

## 97) queue 상태 조회 API가 실패 폭주 때 sample limit 없이 열려 있으면 운영 확인 자체가 불필요한 read 부하가 될 수 있음
- 문제: `user_pii_sync_queue` 운영 상태를 보기 위한 admin read API에서 실패 sample 개수를 무제한으로 받으면, 장애 상황처럼 실패 row가 많을 때 운영 확인 요청이 곧 큰 정렬/조회 부하로 바뀔 수 있었음
- 해결: `GET /api/admin/users/pii-sync-status` 의 `failedSampleLimit` 를 `1..20` 범위로 clamp 하고, 문서 기본값도 `5` 로 고정했음
- 이유: 장애 대응용 조회는 가장 바쁜 순간에 호출된다. 운영 read API는 필요한 정보만 작은 크기로 제한해야 장애 원인 확인 과정이 시스템 부하를 더 키우지 않는다

## 98) Compose 앱이 기본 datasource만 `db` 서비스명으로 덮어쓰고 secondary datasource는 `localhost` fallback을 타면 cut-over smoke가 컨테이너 안에서 즉시 깨질 수 있음
- 문제: `docker-compose.yml` 에서 `DB_URL` 만 `db:3306` 으로 강제하고 `APP_PII_DB_URL`, `NOTIFICATION_PII_DB_URL` 은 비워 두면, 로컬 `.env` 에 secondary URL 이 없거나 `localhost` 기준일 때 앱 컨테이너가 `app_pii_rw` / `notification_pii_ro` datasource를 컨테이너 자기 자신으로 붙으려 해 connection refused 를 일으킬 수 있었음
- 해결: Compose 앱 environment 에 `APP_PII_DB_URL`, `NOTIFICATION_PII_DB_URL`, secondary credential 기본값을 같이 명시해 secondary datasource도 기본적으로 `db` 서비스명과 앱 계정으로 뜨도록 고정했고, `deploy/smoke/user-pii-sync-cutover-smoke.sh` 로 이 경로를 바로 검증할 수 있게 함
- 이유: 다중 datasource를 넣은 뒤에는 primary datasource만 컨테이너 친화적으로 맞춰도 충분하지 않다. compose 환경은 secondary datasource까지 모두 서비스명 기준으로 덮어써야 실제 운영 smoke와 로컬 재현이 같은 경로를 탄다

## 99) `user_pii` cut-over smoke는 queue/dual-write만 보는 테스트처럼 보여도 실제로는 앱의 `AES_SECRET_KEY` 준비 상태를 함께 요구함
- 문제: `deploy/smoke/user-pii-sync-cutover-smoke.sh` 는 회원가입과 프로필 수정을 통해 request-path PII sync를 검증하므로, 앱이 `AES_SECRET_KEY` 없이 떠 있으면 queue 로직 이전에 암호화 단계에서 `C002` 500으로 중단될 수 있었음
- 해결: smoke 스크립트 사용 문서에 `AES_SECRET_KEY` 사전 확인을 명시하고, 검증 결과에도 local `.env` 의 빈 secret 때문에 full smoke가 중단될 수 있음을 남겼음
- 이유: PII cut-over smoke는 DB migration만의 문제가 아니라 앱 secret 주입까지 포함한 end-to-end 경로다. 운영 실행 전에 secret 상태를 먼저 확인해야 migration 문제와 암호화 환경 문제를 헷갈리지 않는다

## 100) secondary datasource를 도입한 뒤에도 primary JPA가 `user_pii` 엔티티/리포지토리를 계속 들고 있으면 최소권한 회수가 막힘
- 문제: 프로필 조회, 알림, backfill, request sync를 이미 `app_pii_rw` / `notification_pii_ro` 로 나눴어도 `UserPii` JPA 엔티티와 `UserPiiRepository` 가 기본 persistence unit에 남아 있으면, 런타임 코드가 다시 primary datasource로 `user_pii` 를 읽거나 쓰기 쉬웠고 `app_core_rw` 의 `user_pii` DML 권한도 자신 있게 회수하기 어려웠음
- 해결: `UserPii` / `UserPiiRepository` 를 제거하고, `UserPiiBackfillService` 는 primary `users` source 조회와 `app_pii_rw` 의 `user_pii` 누락 암호문 조회/수정 2단계로 재구성했으며, integration test도 같은 `UserPiiReadWriteRepository` 경로를 사용하도록 정리했음
- 이유: 다중 datasource 분리의 마지막 단계는 "코드가 그 권한을 정말 더 이상 쓰지 않는가"를 구조로 보장하는 것이다. primary JPA에 PII 엔티티가 남아 있으면 실수로 우회 경로가 다시 생기므로, 엔티티/리포지토리 자체를 제거하는 편이 재발 방지에 확실하다

## 101) `app_core_rw` 의 `user_pii` grant만 먼저 회수하면 secondary datasource fallback이 여전히 기본 계정을 바라봐 운영 경로가 즉시 깨질 수 있음
- 문제: grant 템플릿에서 `app_core_rw -> youth_welfare_pii.user_pii` 권한을 제거하더라도 `docker-compose.yml` / `application.yml` 의 secondary datasource username fallback 이 계속 `DB_USERNAME` 을 따라가면, 프로필 조회·비밀번호 재설정·알림·PII sync가 여전히 `app_core_rw` 로 붙으려 해 권한 오류가 발생할 수 있었음
- 해결: compose/app 설정의 secondary datasource 기본 username 을 `app_pii_rw` / `notification_pii_ro` 로 고정하고, `deploy/smoke/user-pii-sync-cutover-smoke.sh` 에 cross-schema query account scope preflight 를 추가해 `migration_admin` 또는 `DB_QUERY_*` 가 필요할 때 조기에 실패하도록 정리했음
- 이유: 최소권한 회수는 SQL 한 줄로 끝나지 않는다. grant 모델과 애플리케이션 fallback을 같이 바꾸지 않으면 코드 경계는 맞아도 실제 런타임 연결 계정이 예전 값으로 남아 바로 장애로 이어진다

## 102) secondary datasource 계정은 username만 분리해도 충분하지 않고, JDBC URL의 기본 database/schema도 `youth_welfare_pii` 로 바꿔야 함
- 문제: `app_pii_rw` / `notification_pii_ro` 권한을 `youth_welfare_pii.user_pii` 로만 줄인 뒤에도 `APP_PII_DB_URL`, `NOTIFICATION_PII_DB_URL` 이 계속 `.../youth_welfare` 를 가리키면, MySQL이 연결 시점에 `Access denied for user 'app_pii_rw'@'%' to database 'youth_welfare'` 로 거부해 앱이 reduced-grant 상태에서 부팅/요청 처리 중 바로 깨질 수 있었음
- 해결: `.env.example`, `docker-compose.yml`, `application.yml` 의 secondary datasource 기본 URL을 `youth_welfare_pii` schema로 교정하고, `deploy/smoke/run-local-pii-sync-cutover-smoke.sh` 로 fresh init + app boot + one-shot smoke까지 실제 검증했으며, 운영 runbook에도 `APP_PII_DB_URL`, `NOTIFICATION_PII_DB_URL` 전환 단계를 추가했음
- 이유: 최소권한 설계는 grant 범위와 connection target이 함께 맞아야 성립한다. SQL이 fully-qualified table을 써도 JDBC connection 자체는 기본 database에 먼저 붙기 때문에, URL이 잘못되면 권한 모델이 맞아도 연결 단계에서 즉시 실패한다

## 103) fresh init 검증용 DB seed가 runtime `DB_USERNAME` 을 따라가면 stale local `.env` 하나로 split-account 테스트가 다시 root 의존으로 돌아갈 수 있음
- 문제: `docker-compose.yml` 의 DB init용 `MYSQL_APP_USERNAME` 이 runtime `DB_USERNAME` 을 그대로 따라가면, 로컬 `.env` 가 아직 `root` 인 상태에서 fresh init 할 때 `app_core_rw` 대신 root 성격 계정만 만들어져 integration profile이나 reduced-grant smoke가 실제 운영 계정 구조를 검증하지 못할 수 있었음
- 해결: DB init용 기본값을 `MYSQL_APP_USERNAME=app_core_rw` 로 runtime `DB_USERNAME` 과 분리하고, `application-integration.yml` 도 `app_core_rw` / `app_pii_rw` / `notification_pii_ro` 기준으로 정리한 뒤 fresh init + `AuthRedisIntegrationTest` + container smoke로 다시 검증했음
- 이유: 런타임 앱이 어떤 계정으로 붙는지와 fresh init 시 어떤 계정을 seed할지는 별개다. init 계정 생성이 stale local env에 끌려가면 테스트가 실제 운영 설계가 아니라 개발자 개인 `.env` 상태를 검증하게 되므로, seed 기본값은 문서화된 split-account 기준으로 고정하는 편이 재발 방지에 안전하다

## 104) 운영 env 전환은 앱 startup validation만 믿으면 재기동까지 기다린 뒤에야 실수를 발견하게 됨
- 문제: `APP_PII_DB_URL`, `NOTIFICATION_PII_DB_URL`, split datasource username을 잘못 넣어도 앱 startup validation은 재기동 후에야 터지므로, 운영 cutover 직전에 `.env` / secret store 값을 안전하게 검토하는 빠른 preflight가 없으면 배포 시점에만 문제를 발견하게 될 수 있었음
- 해결: `deploy/smoke/preflight-runtime-cutover-env.sh` 를 추가해 `.env` 또는 export된 env를 기준으로 필수 변수, 기대 username, core/PII schema 분리, secondary URL 오배치, 가능하면 `docker compose config` 렌더링까지 앱 기동 전에 확인하도록 정리하고 runbook/deployment 문서에 선행 단계로 반영했음
- 이유: 운영 전환 검증은 “앱이 실패하면 알 수 있다”가 아니라 “앱을 띄우기 전에 틀린 값을 걸러낸다”가 더 안전하다. 특히 secret store 갱신과 재기동 사이의 피드백 루프를 줄여야 cutover 시간을 짧게 유지할 수 있다

## 105) `.env` 를 shell `source`로 직접 읽으면 JDBC URL의 `&` 때문에 값이 잘리거나 background job이 생겨 cutover/smoke 명령이 조용히 잘못될 수 있음
- 문제: `.env` 의 `DB_URL`, `APP_PII_DB_URL`, `NOTIFICATION_PII_DB_URL` 에는 `allowPublicKeyRetrieval=true&characterEncoding=UTF-8...` 같은 `&` 가 들어가는데, 이를 `source .env` 로 읽으면 shell이 `&` 를 명령 분리자로 해석해 변수 값이 중간에서 끊기고 뒤쪽 문자열을 별도 job처럼 실행할 수 있었음
- 해결: `deploy/smoke/preflight-runtime-cutover-env.sh` 와 `deploy/smoke/user-pii-sync-cutover-smoke.sh` 가 `.env` 를 직접 파싱하도록 바꾸고, 문서 예시도 `ENV_FILE=.env ...` 형태로 교체했음
- 이유: `.env` 는 shell script가 아니라 key-value 파일이다. URL query string처럼 shell meta character가 포함될 수 있으므로, 운영 스크립트와 문서는 `source`에 기대지 않고 파일을 안전하게 직접 읽는 쪽이 재발 방지에 맞다

## 106) `ENV_FILE` 을 읽는 스크립트가 caller의 explicit env override보다 파일 값을 우선하면, stale `.env` 를 임시로 우회해야 하는 smoke/preflight에서 계정 override가 무시될 수 있음
- 문제: local `.env` 가 아직 `DB_USERNAME=root` 인 상태에서 `ENV_FILE=.env DB_QUERY_USERNAME=migration_admin ... deploy/smoke/user-pii-sync-cutover-smoke.sh` 처럼 override를 주더라도, 스크립트의 `.env` 파서가 나중에 파일 값을 다시 export해 caller가 준 `DB_QUERY_*` / `DB_MIGRATION_*` override를 덮어쓸 수 있었음
- 해결: `deploy/smoke/user-pii-sync-cutover-smoke.sh` 와 `deploy/smoke/preflight-runtime-cutover-env.sh` 의 `.env` 파서를 수정해, caller가 이미 넘긴 env key는 파일에서 다시 덮어쓰지 않도록 바꿨음
- 이유: 운영 cut-over나 local smoke에서는 `ENV_FILE` 을 기본값 묶음으로 쓰고 일부 key만 명시 override하는 경우가 자주 생긴다. 이때 가장 의도가 명확한 값은 caller가 명령 앞에 준 env이므로, precedence도 그 순서를 따라야 재시도와 우회가 단순해진다

## 107) pass/fail만 나오는 env preflight는 운영 secret cutover 직전에 “어떤 값으로 검증됐는지”를 바로 확인하기 어려워, key 이름 실수나 예상치 못한 override를 놓치기 쉬움
- 문제: split-account 전환 직전에는 preflight 통과 여부뿐 아니라 `DB_URL`, `APP_PII_DB_URL`, `NOTIFICATION_PII_DB_URL` 이 실제로 어떤 host/schema 조합으로 해석됐는지와, username/password key가 모두 채워졌는지를 운영자가 바로 눈으로 다시 확인할 필요가 있었음. pass/fail만 있으면 secret store 값과 effective env를 대조할 때 한 번 더 수작업이 필요했음
- 해결: `deploy/smoke/preflight-runtime-cutover-env.sh` 에 `PRINT_SUMMARY=true` 옵션을 추가해 redacted summary를 출력하도록 보강하고, runbook/deployment 문서에도 cutover 직전 이 모드 실행을 체크리스트로 반영했음
- 이유: 운영 전환은 “검증이 통과했다”보다 “무슨 값으로 검증이 통과했는지 확인했다”가 더 안전하다. 특히 split datasource처럼 key 수가 늘어난 경우에는 effective config를 짧게 요약해서 눈으로 확인할 수 있어야 재시도와 롤백 판단이 빨라진다

## 108) 운영 cutover 단계가 계정 runbook, migration 문서, deployment 문서에 흩어져 있으면 실제 전환 창에서 순서가 섞이거나 smoke를 빼먹기 쉬움
- 문제: split-account 전환은 `계정 SQL`, `migration`, `preflight summary`, `app 재기동`, `핵심 smoke` 가 모두 필요하지만, 세부 설명이 여러 문서에 나뉘어 있어 실제 운영 전환 때는 어떤 순서로 실행해야 하는지 다시 조합해야 했음
- 해결: `docs/runtime-cutover-checklist.md` 를 추가해 실제 cutover 창에서 따라갈 순서를 한 페이지로 압축하고, `deployment.md`, `db-account-cutover-runbook.md`, `README.md` 에서 바로 링크되도록 정리했음
- 이유: 운영 작업은 정보의 양보다 실행 순서의 명확성이 더 중요하다. 긴 설명 문서는 예외 처리에 좋지만, 전환 창에서는 한 페이지 checklist가 있어야 누락 없이 빠르게 진행할 수 있다

## 109) cutover 후 증적이 터미널 출력, 채팅, 메모에 흩어지면 rollback 판단과 사후 검토가 느려지고, 다음 전환 때 재사용할 기준도 남지 않음
- 문제: split-account cutover는 `SHOW GRANTS`, migration 적용 결과, preflight summary, health check, 핵심 API smoke, optional one-shot smoke까지 확인 대상이 많아서, 결과를 즉석에서만 보고 지나가면 성공/실패 판정 근거가 문서로 남지 않을 수 있었음
- 해결: `docs/runtime-cutover-log-template.md` 를 추가해 실제 운영 전환 직후 결과를 한 문서에 기록하도록 하고, checklist/runbook/deployment 문서에서 바로 링크되도록 정리했음
- 이유: 운영 전환은 “실행했다”보다 “무엇을 실행했고 어떤 결과였는지 남겼다”가 중요하다. 특히 rollback 여부를 빠르게 판단하거나 다음 차수 cutover를 반복할 때는 동일한 증적 형식이 있어야 비교와 회고가 쉬워진다

## 110) 운영 smoke 명령을 현장에서 다시 조합하면 cookie jar, Bearer token, bookmark path id 종류를 헷갈려 false negative를 만들기 쉬움
- 문제: cutover 직후 확인해야 할 로그인, refresh, 추천, 북마크, admin status는 단순해 보여도 `refresh_token` cookie 저장, `Authorization: Bearer` 헤더, 추천 bookmark path에 recommendation `id` 를 넣는 규칙처럼 자주 헷갈리는 포인트가 있어, 운영자가 그때그때 curl을 다시 만들면 smoke 자체가 틀릴 수 있었음
- 해결: `docs/core/runtime-api-smoke-commands.md` 를 추가해 공통 env, cookie jar, access token 추출, 추천 응답에서 recommendation `id` 추출, admin status 호출까지 copy-paste 가능한 명령 묶음으로 정리하고 checklist/log template에서 바로 링크되도록 반영했음
- 이유: 운영 smoke는 코드 이해를 시험하는 자리가 아니라 배포 안전성을 확인하는 절차다. 반복되는 인증/토큰/ID 타입 함정은 문서화된 명령으로 고정하는 편이 false negative를 줄이고 cutover 시간을 단축한다

## 111) 실행 로그 템플릿만 있으면 “어느 정도 상세도로 채워야 하는지”가 애매해, 실제 전환 때는 다시 짧거나 들쭉날쭉한 기록으로 흘러가기 쉬움
- 문제: `runtime-cutover-log-template.md` 로 기록 항목은 정리됐지만, 실제로는 어느 수준의 결과 문장과 redaction을 넣어야 하는지 감이 없으면 작업자마다 기록 품질이 달라질 수 있었음
- 해결: `docs/archive/runtime-cutover-log-sample.md` 를 추가해 redacted된 sample cutover 기록을 함께 두고, checklist/template 문서에서 바로 참고할 수 있게 링크를 연결했음
- 이유: 운영 기록도 코드처럼 예시가 있어야 품질이 안정된다. 템플릿만 있으면 최소 항목은 맞춰도 실제 서술 수준이 제각각이 되기 쉬우므로, sample을 함께 두는 편이 다음 차수 cutover 반복성과 비교 가능성을 높인다

## 112) 수동 migration 예시 순서가 문서마다 어긋나면 로컬/운영 리허설에서 “무엇을 먼저 적용해야 하는지”를 다시 판단하게 되어 실수 여지가 생김
- 문제: `runtime-cutover-checklist.md` 는 최신 2종 migration을 `V2026_04_28_01 -> V2026_04_28_02` 순서로 안내하고 있었지만, `db-migration.md` 의 수동 적용 예시는 older migration과 최신 migration이 뒤섞여 있어 local `migration_admin` 리허설 시 실제 적용 순서를 다시 해석해야 했음
- 해결: pre-28 schema 임시 MySQL 8.0에서 `migration_admin` 으로 `V2026_04_28_01 -> V2026_04_28_02` 를 직접 적용해 검증한 뒤, `docs/core/db-migration.md` 의 mysql/docker 예시를 dependency 기준 순서로 재정렬하고 최신 2종은 같은 순서로 명시했음
- 이유: 수동 cut-over 문서는 실행 예시끼리 같은 순서를 유지해야 작업자가 맥락 없이 복사해도 안전하다. 특히 최신 migration 2종처럼 같은 날 추가된 파일은 체크리스트, runbook, migration 가이드가 한 순서를 공유해야 재시도와 회고가 단순해진다

## 113) `docker exec` 로 SQL 파일을 리다이렉션할 때 `-i` 를 빼면 명령이 성공처럼 보여도 migration 본문이 컨테이너 mysql에 전달되지 않을 수 있음
- 문제: 로컬 smoke 중 `docker exec ... mysql ... < migration.sql` 형태로 수동 migration을 다시 태우다가 `-i` 를 빠뜨리면, host shell의 리다이렉션 파일이 컨테이너 stdin으로 전달되지 않아 `mysql` 이 빈 입력으로 종료하고도 겉보기에는 명령이 조용히 끝날 수 있었음
- 해결: 수동 migration 예시와 실제 실행 모두 `docker exec -i ... mysql ... < migration.sql` 형태로 맞추고, 적용 직후 `SHOW TABLES` / `SHOW COLUMNS` 같은 후속 검증 쿼리로 schema 변경이 실제 반영됐는지 바로 확인함
- 이유: 컨테이너 안의 mysql 클라이언트가 host 쪽 리다이렉션 내용을 읽으려면 stdin이 열린 상태여야 한다. 수동 cut-over는 실행 성공 여부보다 결과 schema를 즉시 검증하는 습관이 있어야 동일한 실수를 빨리 잡을 수 있다

## 114) 로그인 직후 즉시 `refresh` 하면 새 access token 문자열이 기존 로그인 token과 같을 수 있어, “토큰 값이 바뀌었는지”를 smoke 성공 기준으로 잡으면 false negative가 생길 수 있음
- 문제: admin refresh smoke 중 로그인 직후 바로 `POST /api/auth/refresh` 를 호출하자 응답은 성공이었지만 새 access token 문자열이 로그인 응답의 token과 같아, 단순 문자열 비교를 성공 기준으로 두면 refresh 실패로 오해할 수 있었음
- 해결: refresh smoke와 관련 문서의 확인 기준을 “refreshed token으로 보호 API를 다시 호출해 권한이 유지되는지”로 맞추고, `docs/core/runtime-api-smoke-commands.md` 에도 같은 주의를 추가했음
- 이유: 현재 JWT는 같은 초 안에서 같은 subject/userId/roles로 다시 발급되면 동일한 토큰 문자열이 나올 수 있다. 이 경우 핵심은 토큰 문자열 변화가 아니라 refresh 응답 성공과 그 token으로 실제 보호 API가 계속 통과하는지다

## 115) logout 직후 refresh 실패를 기대할 때는 Redis reuse 오류보다 cookie clear 이후의 `A001 INVALID_TOKEN` 경로가 먼저 보일 수 있음
- 문제: admin logout smoke에서 `POST /api/auth/logout` 뒤 바로 `POST /api/auth/refresh` 를 호출했더니, Redis에 저장된 refresh token이 지워졌으므로 재사용 탐지 성격의 오류를 기대하기 쉬웠지만 실제 응답은 `401`, `errorCode=A001` 이었음
- 해결: smoke 기대값과 문서를 `logout -> refresh cookie clear -> 직후 refresh는 A001` 기준으로 정리하고, 성공 판단은 이후 재로그인으로 admin 보호 API가 다시 회복되는지까지 포함하도록 맞췄음
- 이유: 현재 logout 응답은 `Set-Cookie` 로 `refresh_token` 자체를 먼저 비우므로, 클라이언트는 재호출 시 토큰이 없는 상태로 `/api/auth/refresh` 를 치게 된다. 이 경우 서버는 reuse 검출보다 앞단의 “토큰 없음/비어 있음” 경로에서 `INVALID_TOKEN` 을 반환하는 것이 자연스럽다

## 116) logout 은 refresh token만 회수하므로 이미 발급된 access token은 만료 전까지 보호 API에 계속 통과할 수 있음
- 문제: pre-28 migrated DB 기준 로컬 smoke에서 `POST /api/auth/logout` 뒤 refresh cookie는 정상적으로 비워지고 직후 `POST /api/auth/refresh` 도 `401 / A001` 로 막혔지만, logout 전에 받은 old admin access token으로 `GET /api/admin/users/pii-sync-status`, `POST /api/admin/users/pii-sync-replay?userKey=<USER_KEY>` 를 다시 호출하면 둘 다 계속 `200` 으로 통과했음
- 해결: 현재 동작을 `docs/phase-plan.md`, `docs/core/runtime-api-smoke-commands.md` 에 명시하고, “logout 후 access token 즉시 무효화 전략 검토/구현” 을 별도 hardening task로 작업 추적에 추가했음
- 이유: 현재 `JwtAuthenticationFilter` 는 bearer access token의 서명/만료만 검증하고 별도 Redis blacklist나 logout cutoff를 조회하지 않으며, `logout` 구현도 refresh token 삭제와 cookie clear에만 집중한다. 따라서 이미 발급된 access token은 만료 전까지 stateless 하게 유효한 것이 현재 설계상 자연스러운 결과다

## 117) 수집 저장이 `service_tags` 를 upsert-only 로 누적하면 외부 API에서 제거된 tag 가 DB에 영구 잔존할 수 있음
- 문제: `CollectItemSaver` 는 새 tag만 `upsert` 하고 기존 tag 삭제 경로가 없어, 정책 source의 키워드/대상/생애주기 값이 바뀌거나 빠져도 `service_tags` 에는 예전 값이 계속 남을 수 있었음
- 해결: 수집 저장 시 `service_id` 기준 기존 tag를 먼저 지우고, 현재 source에서 계산한 tag 집합을 `TagType + tagValue` 기준으로 dedupe 한 뒤 다시 저장하도록 변경했음. tag가 비면 기존 tag를 전부 제거하고 빈 목록으로 relevance 계산을 다시 수행하도록 맞췄음
- 이유: 추천/검색 필터는 현재 source snapshot에 수렴해야 한다. append-only tag 저장은 source가 변할수록 오염 데이터가 누적되므로, 목록 수집 save는 region처럼 tag도 “현재 상태로 교체”하는 쪽이 안전하다

## 118) 상세 수집이 `existsByServiceId` 만 보고 skip하면 upstream 상세 본문 변경이 영구 반영되지 않을 수 있음
- 문제: 복지로 상세 수집은 기존 `welfare_service_details` row가 있는지만 보고 바로 skip하므로, 외부 상세 API의 지원내용/신청방법/문의처가 바뀌어도 기존 row가 남아 있는 한 다시 fetch/merge 할 수 있는 경로가 없었음
- 해결: 기본 상세 수집은 그대로 두되, `BOKJIRO_DETAIL_REFRESH` 전용 source와 `/api/admin/collect/bokjiro-details-refresh` 수동 endpoint를 추가해 refresh 모드에서는 기존 row가 있어도 다시 fetch 후 같은 row id로 merge 저장하도록 분리했음
- 이유: 상세 수집은 호출 단가가 높아 기본 배치와 refresh 동작을 분리하는 편이 안전하다. missing-row 채우기와 full refresh를 같은 경로에 섞으면 호출량과 정합성 기대가 충돌하므로, 운영자가 의도를 명시할 수 있는 별도 경로가 필요하다

## 119) 관리자 수동 수집 경로가 source별 controller/service 메서드 fan-out으로 늘어나면 새 데이터 API 추가 때 endpoint 연결 누락이 다시 생기기 쉬움
- 문제: 수집 adapter registry로 본문 orchestration은 줄였지만, 관리자 수동 수집은 여전히 `/collect/youth`, `/collect/bokjiro-central` 식의 개별 controller 메서드와 `CollectService.collectYouth()` 같은 source별 public 메서드가 남아 있어 새 source를 붙일 때 같은 fan-out을 다시 추가해야 했음
- 해결: `CollectAdminController` 를 `/api/admin/collect/{sourceKey}` 단일 endpoint로 정리하고, `CollectSource` enum 에 path key / trigger label / success message를 올린 뒤 `CollectService.collect(CollectSource source)` 단일 진입점으로 dispatch 하도록 줄였음. invalid source는 `C001` 로 바로 거절하도록 테스트도 고정했음
- 이유: 신규 데이터 API 추가 난이도를 낮추려면 source metadata와 adapter 등록만으로 관리자 수동 실행까지 이어져야 한다. orchestration만 추상화하고 admin trigger fan-out을 남겨두면 실제 확장 시 누락 지점이 다시 controller/service로 분산된다

## 120) local persistent MySQL volume 이 현재 split-account 기본값과 어긋난 상태면 `docker compose up -d db redis` 직후 integration test 가 코드와 무관하게 `app_core_rw` 인증 실패로 막힐 수 있음
- 문제: 관리자 수동 수집 generic dispatch 검증 중 `docker compose up -d db redis` 뒤 `./gradlew integrationTest --tests com.example.welfare.integration.AdminSecurityIntegrationTest` 를 실행하자, 애플리케이션 context 초기화 단계에서 `Access denied for user 'app_core_rw'` 가 발생해 테스트가 기동조차 되지 않았음
- 해결: 이번 task의 코드 회귀 여부는 `CollectServiceTest`, `AdminSecurityWebMvcTest` 로 확인하고, integration 실패 원인은 local persistent MySQL volume 의 계정 상태가 현재 `application-integration.yml` / split-account 기본값과 drift 된 환경 문제로 분리 기록했음. 이 경우 fresh init smoke 또는 known password 기준 계정 재정렬 후 다시 integration 을 태워야 함
- 이유: Compose DB 컨테이너를 recreate 해도 volume 은 유지되므로, 예전 root/app 계정 비밀번호나 grant 상태가 남아 있으면 현재 문서/설정 기본값과 달라도 자동으로 맞춰지지 않는다. split-account 전환 이후에는 “컨테이너 재기동 = 계정 재초기화”라고 가정하면 재발하기 쉽다

## 121) 상세 수집의 `429` 중단, empty payload skip, partial success(save failure 후 계속) 규칙이 테스트로 고정돼 있지 않으면 리팩터링 중 제어 흐름이 쉽게 흔들릴 수 있음
- 문제: 상세 수집은 운영상 중요한 edge case 규칙이 이미 코드에 들어 있었지만, 기존 테스트는 “existing row skip” 과 “refresh update” 정도만 확인하고 있어 `429` 연속 중단 기준, empty payload 무시, 일부 저장 실패 후 다음 정책 계속 처리 같은 동작이 리팩터링 중 바뀌어도 바로 드러나지 않을 수 있었음
- 해결: `BokjiroDetailCollectServiceTest` 에 연속 `429` 임계치 중단, empty payload skip, save failure 후 다음 정책 계속 처리 3개 케이스를 추가해 `requested/saved/failed` 집계와 후속 호출 여부를 고정했음
- 이유: 신규 데이터 API 추가를 쉽게 만들려면 수집 파이프라인 구조를 바꾸더라도 기존 운영 계약은 테스트로 붙잡아야 한다. 특히 상세 수집은 호출 제한과 부분 성공 규칙이 얽혀 있어, 회귀가 나면 증상이 늦게 보이므로 단위 테스트로 먼저 막는 편이 안전하다

## 122) local DB 계정 drift 복구 스크립트가 inherited shell env 나 현재 `.env` 의 `DB_USERNAME=root` 를 그대로 따라가면, 정작 필요한 `app_core_rw` 계정은 복구되지 않고 integration 이 계속 `Access denied` 로 막힐 수 있음
- 문제: 로컬 Docker `mysql_data` volume의 split-account drift를 복구하려고 스크립트를 만들었지만, 초안은 기존 shell env 값을 보존하고 `DB_USERNAME` 을 그대로 app 계정명으로 써서, 작업 세션에 숨은 `DB_PASSWORD` 가 있거나 로컬 `.env` 가 아직 `DB_USERNAME=root` 인 경우 `app_core_rw` 대신 root만 다시 맞추는 잘못된 복구로 이어질 수 있었음
- 해결: `deploy/mysql/reconcile-local-runtime-db-accounts.sh` 는 기본적으로 `ENV_FILE` 값을 shell env보다 우선으로 읽고, 대상 username도 `app_core_rw / app_pii_rw / notification_pii_ro / migration_admin` 으로 고정하도록 바꿨음. 이후 현재 volume에 재적용해 `app_core_rw`, `app_pii_rw` TCP 로그인과 `AdminSecurityIntegrationTest` 통과까지 확인했음
- 이유: 로컬 drift 복구의 목적은 “현재 앱/runtime split-account 기준으로 다시 맞추기”이지, 이미 drift 된 `.env` runtime username을 그대로 재현하는 것이 아니다. hidden env나 legacy `root` username을 따라가면 복구 스크립트가 성공처럼 끝나도 integration 경로는 계속 깨질 수 있으므로, file precedence와 target username을 의도적으로 고정해야 한다

## 123) 신규 데이터 API 확장성을 위해 `온통청년` 대분류만 canonical schema로 유지하면, 범정부 서비스 공통 메타데이터와 공식 지원조건 코드를 충분히 흡수하지 못해 source가 늘수록 정규화가 다시 ad-hoc 해질 수 있음
- 문제: 현재 구조는 `온통청년` 이 가장 구조화가 잘 된 source라는 이유로 `unifiedCategory`, `categoryMain/categorySub`, 일부 `minIncome/maxIncome` 같은 축이 사실상 `온통청년` 중심으로 설계돼 있다. 하지만 외부 기준을 다시 확인해보니 `정부24/보조금24` 는 `serviceList`, `serviceDetail`, `supportConditions` 로 공통 서비스 메타데이터와 지원조건을 공식적으로 분리하고 있고, `온통청년` 운영 코드북도 별도의 `정책대분류/중분류/키워드/제공방법/취업·학력·특화 요건코드` 를 갖고 있어 단일 소스 축으로 다 덮는 방식은 장기적으로 맞지 않았음
- 해결: 정규화 구조 조사 문서 `docs/policy-normalization-research.md` 를 추가해 canonical 기준을 `범정부 공공서비스 core + 온통청년 taxonomy/codebook + 구조화 eligibility facts + AI enrichment` 4계층으로 재정리하고, 다음 작업을 `core / taxonomy / fact` 스키마 초안과 code table 설계로 분리했음
- 이유: 신규 API가 늘수록 “공식 공통축”과 “도메인 특화축”을 분리해야 mapping 비용이 내려간다. 공통 메타데이터는 `Gov24` 축으로, 청년정책 분류는 `온통청년` 축으로, source 고유 필드는 raw/AI enrichment로 분리해야 추후 추천 hard filter와 soft signal을 안정적으로 확장할 수 있다

## 124) 정규화 4계층 구조를 바로 기존 `WelfareService` 와 `ServiceTag` 를 대체하는 빅뱅 교체로 밀어붙이면, 추천 SQL·응답 계약·실시간 AI 입력이 한 번에 깨질 위험이 큼
- 문제: 현재 코드에서 `WelfareServiceRepository.findCandidates*` 는 `minAge/maxAge`, `minIncome/maxIncome`, `sourceType`, `status`, `unifiedCategory` 를 직접 쓰고 있고, `RetrievalService` / `RuleScoringService` / `YouthPolicyFilter` 는 `ServiceTag` 4종 enum에 강하게 묶여 있다. 또 `DefaultPriorityMatcher`, `PolicyDetailResponse`, `RecommendationResponse` 는 `unifiedCategory` 문자열을 그대로 노출하거나 비교하고, `RealtimeAiGateway` 도 제목·카테고리·요약만 읽는다. 이런 상태에서 sidecar 없이 새 canonical 구조로 바로 치환하면 추천 후보 추출, 후처리, 응답 계약, AI 호출이 동시에 흔들릴 수 있었음
- 해결: `docs/policy-normalization-research.md` 에 feasibility 검토를 추가해 전환 방식을 `sidecar 테이블 추가 -> 저장 경계 확장 -> read-model 추가 -> 추천 hard filter 일부 이관 -> AI enrichment` 순서의 점진 이행으로 고정하고, 후속 작업도 `service_taxonomies/service_facts` 스키마 초안, 추천 의존부 이행 순서 설계, `unifiedCategory` 호환 전략으로 쪼갰음
- 이유: 새 canonical 구조 도입 자체는 타당하지만, 현재 시스템은 추천 SQL과 응답 계약이 이미 운영 규약처럼 굳어 있다. 이런 상태에서 빅뱅 교체를 하면 “정규화 설계는 맞는데 런타임 기능이 깨지는” 상황이 생기므로, sidecar 기반 점진 이행으로 기존 read/write 계약을 한동안 병행 유지하는 편이 재발 방지에 안전하다

## 125) Gov24/보조금24는 `core/detail/facts` 는 강하지만 `청년정책 taxonomy` 를 직접 주지 않아서, 곧바로 현재 `unifiedCategory` / priority 체계를 대체하려 하면 추천 의미가 흐려질 수 있음
- 문제: 샘플 기반으로 다시 보니 Gov24 `serviceList/serviceDetail/supportConditions` 는 서비스 메타데이터, 서류/법령, 연령/소득/학력/취업/가구특성 같은 structured fact는 매우 잘 주지만, 현재 추천/우선순위가 쓰는 `주거/일자리/교육·직업훈련/금융·생활지원` 같은 청년정책 분류축을 직접 제공하지 않는다. `서비스분야` 하나만으로는 청년정책 major/mid category나 우선순위 의미를 그대로 복원하기 어려웠음
- 해결: `docs/policy-normalization-sample-spike.md` 에 Gov24 sample 결과를 별도 정리하고, 후속 작업을 `Gov24 service field / user type / benefit type -> compatibility unifiedCategory / youth taxonomy bridge` 규칙 초안 작성으로 분리했음. canonical에서도 Gov24 taxonomy는 official layer로 저장하되, 기존 priority/read-model 호환은 별도 bridge 또는 `system_derived` 분류층에서 처리하는 방향으로 고정했음
- 이유: hard filter 축과 도메인 taxonomy 축은 다르다. Gov24가 facts를 많이 준다고 해서 청년정책 분류까지 자동으로 대체할 수는 없으므로, official source field와 서비스 호환 분류를 같은 계층으로 섞지 않는 편이 재발 방지에 안전하다

## 126) 외부 코드북(`Gov24 supportConditions`, `온통청년` 운영 코드)을 Java enum으로 고정하면 source 코드 변경이 곧 배포 이슈가 되어 정규화 확장성이 다시 떨어질 수 있음
- 문제: 새 canonical 구조를 실제 스키마로 내리면서 보니, `JA0203`, `JA0327` 같은 Gov24 조건 코드와 온통청년 대분류/중분류/제공방법/취업·학력·특화 코드들은 source 문서 개정 시 추가/비활성/라벨 수정이 발생할 수 있다. 이걸 Java enum으로 박아두면 코드북 변경이 곧 애플리케이션 릴리스와 1:1로 묶이고, 코드/DB/문서가 다시 쉽게 어긋날 수 있었음
- 해결: `docs/policy-normalization-schema-draft.md` 에서 코드 저장 결정을 `normalization_code_sets`, `normalization_codes` DB code table 방식으로 고정하고, 서비스별 값은 `service_taxonomies` / `service_taxonomy_terms` / `service_facts` 에 authority와 함께 저장하는 구조로 정리했음
- 이유: 외부 코드북은 애플리케이션 상수라기보다 운영 데이터에 가깝다. 코드셋 메타데이터와 실제 코드값을 DB에 분리 보관해야 라벨 변경, parent 관계 추가, deprecated 처리, source 버전 추적을 무중단에 가깝게 관리할 수 있어 재발 방지에 안전하다

## 127) Gov24 공식 분류와 서비스 호환용 `compat_unified_category` 를 같은 계층으로 취급하면, official taxonomy와 derived bridge가 뒤섞여 추천/응답 의미가 흐려질 수 있음
- 문제: Gov24는 `서비스분야`, `사용자구분`, `지원유형` 같은 official taxonomy를 주지만, 현재 서비스는 여전히 `unifiedCategory` 와 priority matcher에 크게 의존한다. 이때 `Gov24 서비스분야` 를 곧바로 `unifiedCategory` 로 저장해버리면 official 필드와 시스템 호환 분류가 섞여, 나중에 “이 값이 source가 준 것인지 우리가 파생한 것인지”를 구분하기 어려워질 수 있었음
- 해결: `docs/policy-normalization-bridge-rules.md` 에서 `compat_unified_category` 는 `SYSTEM_DERIVED` bridge 로만 다루고, Gov24 official taxonomy는 `service_taxonomies` 의 별도 official layer로 저장하는 규칙을 고정했음
- 이유: external taxonomy와 compatibility read-model은 역할이 다르다. source truth와 시스템 파생값을 같은 칼럼 의미로 섞으면 디버깅과 후속 추천 튜닝이 어려워지므로, authority를 분리한 브릿지층으로 관리하는 편이 재발 방지에 안전하다

## 128) 복지로 text/detail fallback fact를 충분한 신뢰도 구분 없이 곧바로 retrieval hard filter에 쓰면, 본문 표현 차이만으로 정책 후보가 과도하게 탈락할 수 있음
- 문제: 복지로는 structured facts보다 설명문과 상세 본문 비중이 높아서, `미취업`, `대학생`, `1인가구` 같은 표현을 규칙 기반으로 어느 정도 추출할 수는 있다. 하지만 이런 fallback fact를 곧바로 hard filter에 쓰면 source 문구 차이나 모호한 안내문 때문에 후보가 과하게 빠지거나, 반대로 잘못 남을 위험이 있었음
- 해결: `docs/policy-normalization-bridge-rules.md` 에서 fallback fact는 저장은 허용하되 초기 hard filter는 `AGE` 만 허용하고, 나머지 `INCOME/EMPLOYMENT/EDUCATION/HOUSEHOLD/SPECIAL_GROUP` 은 `RULE_DERIVED` + confidence 기반 보조 signal로만 쓰도록 제한했음
- 이유: retrieval의 false negative는 추천 품질 저하를 넘어 “사용자가 받아야 할 정책이 아예 안 보이는” 문제로 이어진다. 복지로 text fallback은 유용하지만 source 문구 편차가 크므로, 초기에는 보수적으로 soft signal로만 소비하는 편이 재발 방지에 안전하다

## 129) local Docker 앱 기준 live collect 검증은 `docker compose up app` 만으로는 충분하지 않고, split-account DB 계정과 prod profile secret이 모두 맞아야 의미 있는 적재 스냅샷이 나온다
- 문제: 실제 DB 적재 검증을 다시 하려 했을 때 local Docker 앱은 계정 drift와 빈 `AES_SECRET_KEY` 때문에 부팅/회원가입이 코드와 무관하게 막혔고, `.env` 를 쓰는 Compose 앱은 shell override만으로는 필요한 secret이 기대한 방식으로 반영되지 않아 live collect 검증 루프 자체가 흐려질 수 있었음
- 해결: 먼저 `deploy/mysql/reconcile-local-runtime-db-accounts.sh` 로 local MySQL split-account 계정을 복구한 뒤, prod profile `bootRun` 을 explicit env(`DB_URL`, `APP_PII_DB_URL`, `NOTIFICATION_PII_DB_URL`, `AES_SECRET_KEY`, dummy `OPENAI_API_KEY`) 기준으로 직접 띄워 `/actuator/health` 와 admin collect를 검증 기준으로 삼았음
- 이유: 실제 적재 검증의 목적은 “현재 수집/정규화 코드가 어떤 row를 만들었는지”를 보는 것이다. Compose/env 문제로 앱 자체가 다른 이유로 실패하면 정규화 판단과 환경 문제를 구분할 수 없으므로, live collect 검증 경로는 split-account와 prod-profile secret을 명시한 known-good boot 경로로 고정하는 편이 재발 방지에 안전하다

## 130) `고용24 채용정보` 나 `마이홈 공공주택 모집공고/단지/대기현황` 같은 listing형 source를 곧바로 `welfare_services` 에 flatten 하면 정책 row grain과 추천 의미가 같이 깨질 수 있음
- 문제: 신규 source 확장 논의를 실제 공개 source 기준으로 다시 보니, Work24 `채용정보/채용행사/공채속보` 와 MyHome `공공주택 모집공고/단지정보/예비입주자 대기현황` 은 “지원 제도 1건” 이 아니라 빠르게 변하는 listing/inventory 또는 상태 feed 성격이 강했다. 이를 기존 정책 row 테이블에 그대로 넣으면 북마크, CTR, 추천 후보, 정책 상세의 의미가 뒤섞일 수 있었음
- 해결: [policy-source-onboarding-playbook.md](../policy/policy-source-onboarding-playbook.md) 에 source를 `정책형 / listing형 / reference형` 으로 먼저 분기하는 규칙을 추가하고, listing형 source는 `welfare_services` 가 아니라 별도 도메인(`job_listings`, `housing_recruitments`, `housing_complexes`, `housing_waitlist_stats`) 후보로 분리하는 방향을 고정했음
- 이유: source 확장성의 핵심은 mapper 추가보다 row grain 보존이다. 정책형 row와 listing row를 같은 canonical에 억지로 밀어 넣으면 단기적으로는 빨라 보여도, 추천/북마크/로그 의미가 무너져 이후 비용이 더 커진다

## 131) 실제 DB에서 복지로 주거/장학/일자리 title이 다수 `기타` 로 남는 상태라면, compat 분류를 title keyword 보정만으로 버티는 방식은 source가 늘수록 빠르게 한계에 부딪힌다
- 문제: 2026-04-29 실제 DB 스냅샷에서 `대전 청년 월세지원`, `대학생 학자금 대출이자 지원(경기도)`, `취업청년정착수당` 같은 `BOKJIRO_LOCAL` row가 다수 `unified_category=기타` 로 남아 있었다. 이 상태에서 신규 source를 더 붙이면 category 누수를 keyword rule 몇 개로 계속 메우게 되어 분류 기준이 다시 ad-hoc 해질 위험이 컸음
- 해결: 실제 DB 사례를 [policy-source-onboarding-playbook.md](../policy/policy-source-onboarding-playbook.md)에 남기고, `official taxonomy + compat_unified_category bridge + service_facts` 구조를 우선 강화하며, listing형 source는 아예 정책 canonical 밖으로 분리하는 쪽으로 다음 작업을 정리했음
- 이유: 실데이터에서 이미 누수가 보이는 상태면 rule patch를 더 쌓기보다 분류 계층 자체를 분리하는 편이 맞다. source가 늘수록 `기타` 예외처리 비용이 커지므로, 지금처럼 유저가 없는 시점에는 구조를 바로잡는 쪽이 장기적으로 안전하다

## 132) 기존 `ServiceTag.KEYWORD` 는 display keyword와 규칙 기반 constraint token(`COND_AGE_*`, `COND_INCOME_*`) 이 섞여 있어, canonical taxonomy term으로 그대로 옮기면 분류 계층이 오염될 수 있음
- 문제: `tagsFromYouth`, `tagsFromBokjiro*` 는 기존 검색/추천 보조를 위해 `KEYWORD` 안에 일반 키워드와 규칙 기반 constraint token을 함께 넣고 있다. 새 canonical aggregate 초안에서 이 값을 그대로 `taxonomyTerms` 로 재활용하면 `월세`, `청년주거` 같은 분류어와 `COND_AGE_MAX_34` 같은 eligibility token이 같은 계층으로 섞일 수 있었음
- 해결: `NormalizedPolicyAggregate` 매핑에서는 taxonomy term을 raw source 필드(`plcyKywdNm`, `lifeArray`, `intrsThemaArray`, `trgterIndvdlArray`) 기준으로 따로 만들고, 구조화 조건은 `facts` 로 분리해 저장하도록 `WelfareServiceMapper` 초안을 정리했음
- 이유: taxonomy와 facts를 mapper 단계에서부터 분리하지 않으면, 이후 `service_taxonomy_terms` / `service_facts` sidecar 저장 시점에 데이터 의미를 다시 추론해야 한다. 현재처럼 source field 기준으로 분리해 두는 편이 점진 이행과 후속 추천 read-model 전환에서 재발 방지에 안전하다

## 133) `NormalizedPolicyAggregate` 를 처음부터 JPA sidecar entity나 migration 스키마에 직접 맞춰 버리면, DTO 초안 단계부터 persistence 세부구조에 잠겨 점진 이행이 어려워질 수 있음
- 문제: 이번 단계에서 `service_taxonomies`, `service_taxonomy_terms`, `service_facts` 실테이블이 아직 없는데 aggregate를 곧바로 entity 집합으로 만들면, collect mapper 리팩터링이 곧 DB migration 선행조건이 되고 `CollectItemSaver` 병행 연결 같은 작은 단계 진행이 막힐 수 있었음
- 해결: `NormalizedPolicyAggregate` 는 record 기반의 persistence-agnostic 내부 DTO로 두고, `WelfareServiceMapper` 에서 source별 canonical 값만 먼저 채우도록 초안을 분리했음. 실제 sidecar entity/SQL 연결은 다음 task로 남겼음
- 이유: 지금 목표는 big-bang 저장 전환이 아니라 source별 canonical 계약을 먼저 고정하는 것이다. DTO 계층을 저장 구현과 느슨하게 두어야 기존 `WelfareService` write path를 유지하면서도 mapper, saver, migration 을 작은 task로 나눠 이행할 수 있어 재발 방지에 안전하다

## 134) source adapter 가 canonical aggregate를 만들지 않고 saver 내부에서만 재구성하게 두면, 이후 sidecar 저장/상세 보강이 추가될 때 collect 경계가 다시 legacy entity 중심으로 굳어질 수 있음
- 문제: `NormalizedPolicyAggregate` 초안을 만든 직후에도 adapter가 여전히 raw DTO만 saver에 넘기고 saver가 legacy mapper만 호출하는 상태를 유지하면, canonical 구조는 테스트용 DTO로만 남고 실제 수집 경계는 계속 `WelfareService` 중심으로 굳을 수 있었음
- 해결: `Youth/BokjiroCentral/BokjiroLocal` source adapter가 mapper에서 만든 aggregate를 saver overload까지 같이 넘기도록 바꾸고, saver에서는 aggregate source identity가 실제 item과 일치하는지 검증하도록 정리했음
- 이유: sidecar 저장으로 가기 전 단계라도 “canonical aggregate는 collect 경계에서 이미 만들어진다”는 사실을 코드 경로에 남겨야 이후 `service_taxonomies/service_facts` 저장과 추천 이행이 작은 task로 이어진다. 그렇지 않으면 다음 단계에서 다시 adapter/saver 양쪽을 한 번에 뜯어야 해 재발 위험이 커진다

## 135) 복지로 list 수집만으로 canonical `detail/facts` 를 완결시키려 하면, 실제 상세 payload가 나중에 도착하는 구조와 충돌해 중복 규칙이나 잘못된 덮어쓰기가 생길 수 있음
- 문제: 이번 task에서 `BokjiroCentral/Local` adapter는 canonical aggregate를 병행 전달하게 되었지만, list 수집 시점에는 상세 payload가 없어 `detail` 대부분이 `null` 또는 list fallback 값만 가진다. 이 상태를 완결 canonical로 간주하면 나중에 `BokjiroDetailCollectService` 가 들어올 때 어느 필드를 authoritative 하게 덮어쓸지 다시 모호해질 수 있었음
- 해결: adapter 경로에서는 일단 `null detail payload` 기반 aggregate를 넘기고, 다음 작업을 `BokjiroDetailCollectService / detail refresh 경로에서 NormalizedPolicyAggregate detail/facts 후속 보강 연결` 로 분리해 기록했음
- 이유: 복지로는 list와 detail의 수집 cadence와 정보 밀도가 다르다. list 수집 경로는 canonical 초안/identity 확보까지, detail 수집 경로는 세부 facts 보강까지 담당을 나누는 편이 점진 이행과 덮어쓰기 규칙 관리에 안전하다

## 136) 복지로 상세 수집이 raw payload를 직접 `WelfareServiceDetail` / 서비스 fallback으로 저장하면, list collect가 만든 canonical 규칙과 detail collect 규칙이 쉽게 drift 날 수 있음
- 문제: `NormalizedPolicyAggregate` 를 collect flow에 태운 뒤에도 `BokjiroDetailCollectService` 가 여전히 `DetailPayload` 원문을 직접 `WelfareServiceDetail` 과 `applyDetailFallbacks` 에 넣고 있으면, list collect는 mapper canonical 규칙을 따르고 detail collect는 service 내부 규칙을 따르는 이중 경로가 남게 된다. 이렇게 되면 `AGE`, `APPLY_END_DATE`, `onlineApply` 같은 보강값이 mapper와 service에서 서로 다르게 해석될 수 있었음
- 해결: `WelfareServiceMapper.toNormalizedBokjiroDetail(service, detailPayload)` 를 추가하고, `BokjiroDetailCollectService` 가 aggregate `detail` 로 detail entity를 만들고 aggregate `facts` 로 서비스 fallback을 적용하도록 경로를 통일했음
- 이유: canonical 전환 중에는 “무엇을 source truth로 해석하는지”가 한 곳에 모여 있어야 한다. 복지로 상세처럼 list/detail cadence가 다른 source일수록 mapper를 단일 해석 경계로 두는 편이 후속 sidecar 저장과 추천 read-model 전환에서 재발 방지에 안전하다

## 137) 복지로 list aggregate 와 detail aggregate 가 같은 semantic fact를 다른 `fact_code` 로 내보내면, future `service_facts` upsert 에서 detail 이 list fallback 을 대체하지 못하고 중복 row가 누적될 수 있음
- 문제: 현재 canonical 초안에서는 복지로 list 쪽에 `TEXT_AGE`, detail 쪽에 `DETAIL_TEXT_AGE` 같은 phase-specific 코드가 남아 있다. 이 상태로 `service_facts` 를 저장하면 같은 `AGE` 슬롯이어도 서로 다른 row로 인식되어, detail collect 가 list fallback 을 교체해야 하는 경우에도 단순 중복 insert 로 끝날 위험이 있었음
- 해결: [policy-normalization-fact-merge-rules.md](../history/policy/policy-normalization-fact-merge-rules.md) 를 추가해 `fact_code` 와 별도로 `fact_merge_key` 를 두고, `BK_AGE_ELIGIBILITY`, `BK_APPLY_END_DATE` 같은 stable logical key 기준으로 merge/upsert 하도록 규칙을 고정했음. 이어서 next task로 mapper fact code 를 stable merge key 체계로 정리하도록 작업 추적에 추가했음
- 이유: list/detail cadence 가 다른 source에서는 “무슨 fact인가”와 “어느 phase에서 나왔는가”를 같은 코드값에 섞으면 deterministic upsert 를 만들기 어렵다. logical merge key 를 먼저 고정해야 migration SQL, saver, read-model 이 같은 fact slot 개념을 공유할 수 있어 재발 방지에 안전하다

## 138) `fact_merge_key` 가 문서에만 있고 aggregate 계약에는 없으면, mapper 테스트는 통과해도 future sidecar saver 단계에서 같은 semantic fact slot 을 안정적으로 단언할 수 없음
- 문제: `policy-normalization-fact-merge-rules.md` 로 merge key 규칙을 먼저 고정했더라도, `NormalizedPolicyAggregate.Fact` 가 여전히 `factCode` 만 들고 있으면 현재 mapper 출력과 future `service_facts` saver 사이에 merge slot 정보가 사라진다. 이 상태에서는 list/detail 이 같은 semantic fact 를 내보내는지 테스트 레벨에서 고정할 수 없고, sidecar 저장 단계에서 다시 phase-specific 코드에 의존할 위험이 있었음
- 해결: `NormalizedPolicyAggregate.Fact` 에 `factMergeKey` 필드를 추가하고, `WelfareServiceMapper` 가 복지로 list/detail facts 에 공통 `BOKJIRO_RULE_AGE` / `BOKJIRO_RULE_APPLY_END_DATE` 와 stable `BK_AGE_ELIGIBILITY` / `BK_APPLY_END_DATE` merge key 를 함께 채우도록 정리했음. `NormalizedPolicyAggregateTest` 로 youth/복지로 facts 의 merge key 도 같이 검증하게 바꿨음
- 이유: canonical aggregate 는 future persistence 계약의 가장 가까운 전단계다. merge slot 정보가 aggregate부터 살아 있어야 migration SQL, saver upsert, retrieval/read-model 이 같은 semantic fact 개념을 일관되게 공유할 수 있어 재발 방지에 안전하다

## 139) merge 규칙이 문서와 mapper에만 있고 executable merge utility가 없으면, future `service_facts` saver 구현 시 precedence 로직이 다시 분산되어 drift 날 수 있음
- 문제: `fact_merge_key` 와 복지로 공통 fact code를 맞춘 뒤에도 실제 `service_facts` saver 가 아직 없으면, 나중에 저장 경로를 만들 때 `authority -> confidence -> sourceField` 우선순위를 각 saver/repository 에서 다시 손으로 구현하게 될 수 있다. 그러면 문서와 현재 테스트가 있어도 실제 upsert 동작이 미묘하게 달라질 위험이 있었음
- 해결: `NormalizedFactMergeSupport` 를 추가해 `fact_merge_key` 기준 merge/upsert precedence 를 코드 utility로 먼저 고정했고, `NormalizedFactMergeSupportTest` 에 `list -> detail overwrite`, `detail -> list no-op`, `set-like union` 케이스를 추가해 future saver 가 그대로 재사용할 계약을 마련했음
- 이유: merge 규칙은 “문서 + mapper + persistence” 3층에서 동일해야 한다. 저장 경로가 아직 없어도 precedence 자체는 executable utility와 테스트로 먼저 한 곳에 고정해 둬야 이후 sidecar saver 가 같은 계약을 재사용하며 구현돼 재발 방지에 안전하다

## 140) sidecar writer 훅이 collect list/detail 경로에 연결되지 않으면, 나중에 DB writer 를 붙여도 일부 aggregate 는 계속 legacy entity write path 에서만 끝나 canonical sidecar 가 부분 적재될 수 있음
- 문제: `NormalizedPolicyAggregate`, `factMergeKey`, `NormalizedFactMergeSupport` 를 준비해도 실제 collect 경로가 sidecar writer 를 호출하지 않으면, future `service_taxonomies/service_facts` 저장을 붙일 때 adapter/list/detail 경로 중 일부만 새 writer 를 타고 나머지는 계속 `WelfareService` / `WelfareServiceDetail` write 에서 끝날 수 있다. 그러면 source별 canonical 적재 커버리지가 다시 경로마다 갈라질 위험이 있었음
- 해결: `NormalizedPolicySidecarWriter` 인터페이스와 `DeferredNormalizedPolicySidecarWriter` 기본 구현을 추가하고, `CollectItemSaver` 와 `BokjiroDetailCollectService` 가 aggregate 저장 경로에서 모두 writer 를 호출하도록 연결했음. 테스트에서도 youth list path 와 bokjiro detail path 모두 writer 호출을 검증하도록 고정했음
- 이유: canonical 전환은 mapper 규칙만 맞춘다고 끝나지 않고, collect 실행 경로 전체가 같은 저장 훅을 지나야 한다. sidecar DB writer 가 아직 없어도 호출 지점을 먼저 고정해야 이후 persistence 구현을 한 곳에 꽂을 수 있어 재발 방지에 안전하다

## 141) `service_taxonomy_terms` 에서 `term_code` 를 nullable 로 두고 unique key 만 걸면, MySQL 이 `NULL` 중복을 허용해 term dedupe 계약이 깨질 수 있음
- 문제: `service_taxonomy_terms` 는 `(service_id, term_group, term_code, term_label, authority)` 기준으로 dedupe 해야 하는데, MySQL unique key 는 nullable 컬럼의 `NULL` 중복을 막지 않는다. 코드가 없는 taxonomy term 을 그대로 `NULL` 로 두면 SQL draft 상 unique key 가 있어도 동일 term 이 중복 적재될 위험이 있었음
- 해결: `V2026_04_30_01__create_policy_sidecars.sql` draft 와 `policy-normalization-schema-draft.md` 에서 코드가 없는 term 은 `term_code=''` 로 normalize 하도록 고정했고, `db-migration.md` 에도 이 규칙을 같이 기록했음
- 이유: canonical sidecar 는 term dedupe 를 DB 제약으로도 최대한 보조해야 한다. MySQL nullable unique semantics 를 초안 단계에서 반영하지 않으면, 나중에 persistence writer/backfill 에서 중복 정리 로직이 불필요하게 복잡해져 재발 방지에 불리하다

## 142) official codebook 이 아직 없는 단계에서 `YOUTH_MID` 나 `GOV24_*` 코드를 임의 생성해 seed 하면, 초기 backfill 은 돌아가도 이후 공식 import 와 코드 호환이 깨질 수 있음
- 문제: `normalization_code_sets / normalization_codes` seed/backfill draft 를 만들 때 `service_taxonomies` summary backfill 을 빨리 끝내려고 `YOUTH_MID` 나 `GOV24_SERVICE_FIELD` 코드를 내부 규칙으로 임의 생성해 넣어버리면, 나중에 온통청년/Gov24 공식 코드북을 가져올 때 같은 라벨이 다른 code 로 중복되거나 기존 summary row 와 호환되지 않을 위험이 있었음
- 해결: `V2026_04_30_02__seed_policy_normalization_codes.sql` draft 에서는 `SYSTEM_COMPAT_UNIFIED_CATEGORY`, `YOUTH_MAJOR` 만 대표 code seed 로 넣고, `YOUTH_MID`, `GOV24_*`, `GOV24_SUPPORT_CONDITION` 은 metadata set만 먼저 생성한 뒤 공식 code import/backfill SQL을 후속 task로 분리했음
- 이유: canonical code table 은 한 번 잘못 seed 하면 이후 backfill/persistence/read-model 전부가 그 코드를 따라가게 된다. 공식 근거가 없는 계층은 metadata까지만 먼저 열고 code 값은 공식 source 확보 후 넣는 편이 호환성과 재발 방지에 안전하다

## 143) 같은 온통청년 공개 코드정의서 안에서도 어떤 집합은 stable code 값이 있고(`jobCd`, `schoolCd` 등), 어떤 집합은 라벨/정렬만 공개(`정책중분류`)되어 있어 import 전략을 한 파일로 뭉개면 다시 임의 code 생성이 섞일 수 있음
- 문제: 이번에 official code import draft 를 이어서 만들 때, `jobCd=0013003`, `schoolCd=0049005` 처럼 실제 code 가 공개된 집합과 `정책중분류=취업/재직자/...` 처럼 번호+라벨만 보이는 집합을 같은 방식으로 `normalization_codes` 에 넣으려 하면 결국 `YOUTH_MID` 에 내부 surrogate code 를 급하게 발급하게 될 위험이 있었음
- 해결: `V2026_04_30_03__seed_policy_official_code_subsets.sql` 에서는 stable code 가 확인된 온통청년 집합과 representative `GOV24_SUPPORT_CONDITION` 만 `normalization_codes` 로 seed 하고, `YOUTH_MID` 는 `service_taxonomy_terms.term_code=''` label-only backfill 로만 처리하도록 분리했음
- 이유: source granularity 가 다른 집합을 한 import 전략으로 묶으면 공식 코드와 임의 코드가 같은 테이블에서 구분 없이 섞인다. 지금처럼 code-bearing subset 과 label-only subset 을 분리해야 후속 `YOUTH_MID` stable code 정책 결정이 독립적으로 가능하고 재발 방지에 안전하다

## 144) `YOUTH.category_sub` 를 raw string 그대로 `service_taxonomy_terms` 에 적재하면, multi-value와 non-official variant가 official 중분류 term과 한 row에 섞여 canonical taxonomy가 오염될 수 있음
- 문제: 로컬 DB를 다시 확인해 보니 `YOUTH.category_sub` 는 `취업`, `재직자` 같은 단일 official label만 있는 게 아니라 `취업,재직자`, `취업,창업`, `온·오프라인교육`, `문화활동 및 생활지원` 같은 multi-value/variant 문자열도 함께 들어 있었다. 이 값을 split 없이 그대로 `YOUTH_MID` term으로 적재하면 official 중분류 집합과 raw source noise가 같은 계층에 섞일 위험이 있었음
- 해결: `V2026_04_30_03__seed_policy_official_code_subsets.sql` 의 `YOUTH_MID` backfill 을 `split + trim + exact official label filter` 방식으로 바꾸고, 공개 시트의 17개 official label과 일치하는 token만 `service_taxonomy_terms(term_code='')` 에 적재하도록 수정했음. `온·오프라인교육`, `문화활동 및 생활지원` 같은 variant는 alias normalization 후속 task로 분리했음
- 이유: `YOUTH_MID` 는 아직 stable code 가 없기 때문에 term_label 자체가 canonical key 역할을 일부 대신한다. 따라서 raw combo string 을 그대로 넣지 말고, 최소한 official label inventory와 exact match 하는 값만 먼저 넣어야 taxonomy 정합성과 재발 방지에 안전하다

## 145) 공개 문서에 `srchPolyBizSecd=003002001,003002002` 예시가 보이더라도, 상세 metadata inventory 를 비로그인 상태에서 가져올 수 없으면 그 두 값만으로 `YOUTH_MID` stable code 체계를 확정하면 안 됨
- 문제: 온통청년 공개 API 문서 HTML에서는 `srchPolyBizSecd=003002001,003002002` 예시가 노출되지만, 실제 파라미터/응답 메타데이터를 주는 `/sur/link/openApiIntro/46`, `/sur/link/openInfoChcApi` 는 비로그인 상태에서 모두 `Unauthorized` 를 반환했다. 이 상태에서 보이는 두 코드만 근거로 `YOUTH_MID` 전체 stable code를 역추론해 seed 하면, 나머지 중분류와의 체계가 뒤틀릴 위험이 있었음
- 해결: 이번 단계에서는 `YOUTH_MID` stable code import 를 보류하고 `service_taxonomy_terms(term_code='')` label-only 전략을 유지하기로 정책을 고정했음. 후속 task는 로그인 가능한 testbed/live payload 에서 `srchPolyBizSecd` 전체 inventory 를 먼저 수집한 뒤 stable code mapping SQL 초안을 쓰는 것으로 다시 쪼갰음
- 이유: partial example 과 전체 inventory 는 다르다. 공개 페이지 예시 몇 개만으로 코드 체계를 미리 확정하면 이후 authenticated source 에서 실제 inventory 가 드러났을 때 기존 seed/backfill 과 충돌하기 쉬워 재발 방지에 불리하다

## 146) `온·오프라인교육`, `문화활동 및 생활지원` 같은 non-official `YOUTH_MID` variant를 성급히 official 단일 라벨로 접어버리면, taxonomy 의미 손실이나 잘못된 우선순위 브릿지가 생길 수 있음
- 문제: 로컬 DB split 결과를 보면 `온·오프라인교육` 11건, `문화활동 및 생활지원` 66건이 존재한다. 전자는 `온라인교육` 과 유사하지만 offline 범위를 포함할 수 있고, 후자는 `문화활동` 과 생활지원 축이 결합된 composite 표현일 수 있어 하나의 official 중분류로 단정하기 어렵다
- 해결: [policy-normalization-youth-mid-alias-rules.md](../history/policy/policy-normalization-youth-mid-alias-rules.md) 에서 alias 처리 기준을 따로 고정하고, exact official token만 canonical `YOUTH_MID` 로 적재하며 non-official variant는 현재 단계에서 skip 하도록 명시했음. 별도 보존이 필요하면 future sidecar 에 `YOUTH_MID_RAW_ALIAS` 같은 term group을 둘지 후속 task로 분리했음
- 이유: stable code 가 없는 상태에서는 `term_label` 자체가 canonical key 일부 역할을 대신한다. 애매한 alias를 섣불리 official 단일 라벨로 접으면 이후 stable code import, 추천 브릿지, 분석 집계가 모두 왜곡될 수 있어 재발 방지에 불리하다

## 147) skipped `YOUTH_MID` alias를 raw payload에만 남기면, canonical sidecar 기준의 디버깅/backfill/read-model 경로가 다시 raw string 재파싱에 의존하게 됨
- 문제: `온·오프라인교육`, `문화활동 및 생활지원` 을 canonical `YOUTH_MID` 에 넣지 않는 것은 맞지만, 이 값을 `welfare_services.category_sub` / `raw_api_payloads` 에만 두면 나중에 sidecar 기준 분석, backfill, read-model 검증 때마다 raw string 을 다시 파싱해야 한다. 그러면 official exact token과 skipped alias의 구분 기준이 writer/backfill 마다 다시 흩어질 위험이 있었다
- 해결: skipped alias는 canonical `YOUTH_MID` 에 넣지 않되, future sidecar 에 `service_taxonomy_terms(term_group='YOUTH_MID_RAW_ALIAS', code_set_key=NULL, term_code='', term_label=<raw alias>, authority='OFFICIAL', source_field='category_sub')` 로 별도 보존하기로 정책을 고정했음. canonical taxonomy summary / recommendation read-model 은 이 term group을 기본적으로 읽지 않도록 같이 명시했음
- 이유: canonical taxonomy를 오염시키지 않으면서도 source가 실제로 준 비정규 label을 잃지 않는 것이 중요하다. raw alias를 별도 bucket으로 남겨두면 나중에 stable code inventory가 확보되었을 때 재매핑이 쉬워지고, collect writer/backfill/read-model 이 같은 기준을 재사용할 수 있어 재발 방지에 유리하다

## 148) `YOUTH_MID` raw alias bucket만 분리하고 summary `youthMid` 필드에 raw combo/alias 문자열을 그대로 남기면, sidecar term은 정규화돼도 summary read-model 이 다시 canonical taxonomy를 오염시킬 수 있음
- 문제: `YOUTH_MID_RAW_ALIAS` 를 `service_taxonomy_terms` 에 따로 보존하더라도, `taxonomy.youthMid` 나 `service_taxonomies.youth_mid_label` 이 기존처럼 raw `category_sub` 문자열(`취업,재직자`, `온·오프라인교육`)을 그대로 유지하면 summary/read-model 이 계속 비정규 값에 묶이게 된다. 그러면 canonical term과 summary가 서로 다른 기준을 보게 되어 후속 추천 브릿지와 검증 쿼리가 다시 흔들릴 위험이 있었다
- 해결: `WelfareServiceMapper.toNormalizedYouth()` 와 `V2026_04_30_02__seed_policy_normalization_codes.sql` 모두 `YOUTH_MID` summary 는 exact official 단일 token일 때만 채우고, comma 조합이나 raw alias가 섞이면 `NULL` 로 두도록 규칙을 맞췄다. 동시에 `DeferredNormalizedPolicySidecarWriter` 에 raw alias bucket이 있을 때 summary `youthMid` 가 비어 있어야 한다는 invariant 검증을 추가했다
- 이유: canonical taxonomy는 summary 와 repeated term row가 같은 정합성 규칙을 공유해야 한다. raw alias bucket을 도입한 뒤에도 summary 를 그대로 두면 정규화 이득이 절반만 남으므로, collect aggregate / backfill SQL / writer invariant 를 같은 기준으로 고정해 재발을 막는 편이 안전하다

## 149) `detail` aggregate 의 `taxonomyTerms` 가 비어 있다는 이유만으로 source별 known term group 을 전부 지우면, 복지로 list 수집이 적재한 taxonomy가 detail refresh 한 번에 사라질 수 있음
- 문제: 실제 JDBC sidecar writer 로 바꾸면서 source별 term group refresh 삭제를 추가했는데, 이 로직을 `BokjiroDetail` aggregate 에도 그대로 적용하면 detail aggregate 는 `taxonomyTerms` 가 비어 있으므로 기존 `LIFE_STAGE`, `INTEREST_THEME`, `TARGET_GROUP` row를 통째로 삭제해 버릴 수 있었다
- 해결: `DeferredNormalizedPolicySidecarWriter` 는 `taxonomyTerms` 가 비어 있는 aggregate 에 대해서는 taxonomy term refresh 자체를 건너뛰도록 했고, `DeferredNormalizedPolicySidecarWriterTest` 에서 detail aggregate(`BOKJIRO_CENTRAL`, empty `taxonomyTerms`)가 summary/facts upsert 는 수행하되 `DELETE FROM service_taxonomy_terms` 는 호출하지 않는 계약을 추가했다
- 이유: list collect 와 detail collect 는 canonical aggregate 의 역할이 다르다. detail 경로는 facts/detail enrichment 전용이고 list taxonomy를 authoritative 하게 대체하지 않으므로, 같은 sourceType 이라도 “term payload 가 비어 있는 detail aggregate” 는 delete semantics 를 다르게 가져가야 기존 수집 결과를 보존할 수 있어 재발 방지에 안전하다

## 150) actual sidecar writer 를 도입해도 아직 local/runtime schema 에 sidecar 테이블이 없으면 collect 호출 시 SQL 예외로 전체 수집이 깨질 수 있음
- 문제: `DeferredNormalizedPolicySidecarWriter` 를 실제 JDBC upsert writer 로 바꾼 뒤에도 sidecar 스키마는 아직 `schema.sql` 과 정식 `db/migration/` 에 들어가지 않았다. 이 상태에서 테이블 존재 여부를 확인하지 않고 곧바로 `service_taxonomies/service_facts` 에 쓰면, 현재 로컬/운영 DB 상당수는 해당 테이블이 없어 collect 경로 전체가 SQL 예외로 중단될 위험이 있었다
- 해결: writer 시작 시 `information_schema.tables` 를 조회해 `normalization_code_sets`, `service_taxonomies`, `service_taxonomy_terms`, `service_facts` 네 테이블이 모두 있을 때만 actual upsert 를 수행하고, 없으면 debug 로그만 남기고 안전하게 skip 하도록 변경했다
- 이유: canonical sidecar 전환은 collect 코드와 DB rollout 이 완전히 동시에 끝나지 않는다. rollout 이전 단계에서 writer 가 no-op fallback 을 유지해야 현재 수집 흐름을 지키면서도, sidecar 테이블이 준비된 환경에서는 같은 코드가 즉시 actual persistence 로 전환될 수 있어 점진 이행과 재발 방지에 모두 유리하다

## 151) secondary datasource용 `NamedParameterJdbcTemplate` 만 수동 등록한 상태에서 primary named template를 명시하지 않으면, actual sidecar writer 가 켜지는 시점에 Spring context 가 `NoUniqueBeanDefinitionException` 으로 부팅 실패할 수 있음
- 문제: `DeferredNormalizedPolicySidecarWriter` 가 실제 JDBC upsert writer 로 전환되면서 `NamedParameterJdbcTemplate` 를 주입받기 시작했는데, 애플리케이션에는 `appPiiReadWriteNamedParameterJdbcTemplate`, `notificationPiiReadNamedParameterJdbcTemplate` 두 개만 명시 등록돼 있었다. 이 상태에서는 Spring Boot 기본 primary named template auto-config가 더 이상 유일 후보가 아니어서, local integration smoke에서 context 가 부팅 단계에서 실패했다
- 해결: `PrimaryDataSourceConfig` 에 primary datasource 기반 `primaryNamedParameterJdbcTemplate` bean을 명시적으로 추가해, collect sidecar writer 가 항상 core schema datasource를 사용하도록 고정했다
- 이유: secondary datasource를 늘리는 순간 “기본 named JDBC bean이 자동으로 하나 있을 것”이라는 가정은 깨질 수 있다. primary writer 가 어떤 datasource를 써야 하는지 코드로 못 박아야 actual collect path smoke와 runtime이 같은 wiring을 공유해 재발 방지에 안전하다

## 152) `fact_merge_key` 가 같고 authority/confidence/sourceField 우선순위도 같은 refresh fact는 incoming 값으로 덮어써야 하는데, 기존 merge 규칙이 동률에서 old value를 유지해 stale sidecar facts가 남을 수 있었음
- 문제: local MySQL smoke에서 같은 `source_id` 를 두 번 저장해 보니 `NormalizedFactMergeSupport` 가 동일 우선순위 tie에서 기존 fact를 유지하고 있어, `YOUTH_AGE_ELIGIBILITY`, `YOUTH_INCOME_MAX`, `YOUTH_APPLY_END_DATE` 같은 fact가 refresh 후에도 이전 값에 머무를 수 있었다
- 해결: `NormalizedFactMergeSupport` 의 tie-breaker를 `incoming wins on exact precedence tie` 로 바꾸고, `NormalizedFactMergeSupportTest.merge_overwritesExistingWhenRefreshHasSamePrecedence` 로 같은 merge key의 refresh overwrite 계약을 추가했다
- 이유: list/detail merge에서는 stronger source 우선순위가 필요하지만, 같은 source의 재수집 refresh에서는 최신 aggregate가 authoritative 해야 한다. 동률이면 새 값으로 덮어쓰도록 고정해야 sidecar가 stale해지지 않아 재발 방지에 안전하다

## 153) 같은 `service_id` 에 대해 tag replace를 연속 수행할 때 delete가 flush 되기 전에 insert batch가 들어가면 `service_tags.uq_st` 충돌이 날 수 있음
- 문제: local sidecar smoke에서 같은 청년정책 row를 두 번 저장했을 때 `service_tags` 교체 경로가 `deleteByServiceId -> saveAll` 로 이어지면서, delete가 DB에 먼저 확정되기 전에 같은 `(service_id, tag_type, tag_value)` insert가 들어가 `Duplicate entry ... for key 'service_tags.uq_st'` 가 발생했다
- 해결: `CollectItemSaver.replaceTags()` 에서 `tagRepository.deleteByServiceId(service.getId())` 직후 `tagRepository.flush()` 를 호출해 delete를 먼저 DB에 반영한 뒤 새 tag 집합을 저장하도록 바꿨다
- 이유: tag refresh는 sidecar smoke처럼 같은 row를 같은 테스트/수집 창에서 연속 갱신할 때 바로 드러난다. replace semantics를 기대하는 경로에서는 delete와 insert의 flush 순서를 명시적으로 고정해야 unique constraint 재발을 막을 수 있다

## 154) 복지로 detail refresh smoke를 작은 `maxCalls` 로 재현할 때는 실제 repository 전체 scan 대신 isolated target 집합을 주입하지 않으면 local source merge 경로를 안정적으로 고정하기 어렵다
- 문제: `collectBokjiroDetailsRefreshResult(maxCalls)` 는 central/local budget 을 함께 계산하고 repository 전체 `findBySourceType(...)` 결과를 순회한다. 로컬 DB에는 이미 복지로 row가 많이 적재돼 있어, sidecar merge smoke에서 특정 1건의 `BOKJIRO_LOCAL` list/detail 경로만 검증하려고 해도 전체 적재 데이터 순서와 `maxCallsPerApiPerRun` 분배에 따라 다른 row가 먼저 선택될 수 있었다
- 해결: integration smoke에서는 real DB에 list aggregate를 먼저 적재한 뒤, 테스트 안에서 isolated `BokjiroDetailCollectService` 를 새로 구성해 mocked `WelfareServiceRepository.findBySourceType(...)` 가 대상 local row만 반환하도록 제한했다. 이 smoke는 canonical sidecar merge 계약만 검증하고, detached entity에 기대는 legacy `welfare_services` fallback 필드 값까지 assertion 범위에 넣지 않도록 좁혔다
- 이유: 이번 단계의 목적은 운영 batch 전체가 아니라 `service_taxonomy_terms` 보존과 `service_facts` merge key overwrite가 actual JDBC sidecar writer에 연결되는지 확인하는 것이다. 전체 repository scan과 batch budget까지 동시에 테스트에 끌고 오면 sidecar contract 회귀와 unrelated source ordering이 섞여 재현성이 떨어지므로, isolated target 집합으로 contract를 먼저 고정하는 편이 재발 방지에 안전하다

## 155) 복지로 detail refresh budget 을 `central 먼저, 남으면 local` 로 고정하면 low `maxCalls` 에서 local source 가 구조적으로 0 budget이 되어 sidecar/backfill 검증과 실제 local 상세 갱신이 계속 굶을 수 있음
- 문제: 기존 `collectBokjiroDetailsResult(maxCalls, ...)` 는 `centralBudget = min(cap, maxCalls)`, `localBudget = min(cap, maxCalls - centralBudget)` 구조였다. 이 규칙에서는 `maxCalls=1`, `maxCalls=2` 같은 low budget에서 중앙 source target이 하나라도 존재하면 local은 항상 `0` budget이 되어, 실제 로컬 상세가 더 많이 쌓여 있어도 refresh와 backfill이 계속 미뤄질 수 있었다
- 해결: `BokjiroDetailCollectService` 가 먼저 중앙/지자체 target backlog를 읽고, `maxCallsPerApiPerRun` cap 안에서 backlog 비율대로 `centralBudget` / `localBudget` 을 나누도록 바꿨다. 한쪽 source가 비어 있으면 다른 쪽이 전체 budget을 가져가고, regression test로 `central 없음 -> local full budget`, `local backlog 우세 -> maxCalls=1 에서 local 호출` 을 고정했다
- 이유: 복지로 detail 수집은 중앙/지자체 모두 canonical sidecar enrichment의 source다. low budget 상황에서도 한쪽 source가 구조적으로 굶지 않게 해야 local 중심 데이터셋에서 refresh/backfill과 smoke가 의미 있게 유지되므로 재발 방지에 안전하다

## 156) 복지로 detail raw payload 를 그대로 JSON 저장하면 Lombok `isEmpty()` getter 때문에 `empty=false` 가 섞여, 나중에 `DetailPayload` 로 다시 읽는 backfill/replay 경로가 깨질 수 있음
- 문제: `BokjiroDetailClient.DetailPayload` 는 `isEmpty()` convenience method 를 갖고 있어 Jackson 직렬화 시 `empty=false` 프로퍼티가 함께 저장될 수 있었다. 이후 `raw_api_payloads` 에서 이 JSON을 다시 읽어 `DetailPayload` 로 역직렬화하면, 클래스에는 `empty` 필드가 없어 `Unrecognized field "empty"` 예외가 발생해 canonical sidecar backfill/replay가 실패했다
- 해결: `DetailPayload` 에 `@JsonIgnoreProperties(ignoreUnknown = true)` 를 붙이고 `isEmpty()` 에 `@JsonIgnore` 를 추가해, 신규 저장에서는 `empty` 가 빠지고 기존 raw JSON 에 `empty=false` 가 남아 있어도 무시하고 다시 읽을 수 있게 맞췄다. 동시에 `NormalizedPolicySidecarBackfillServiceTest` 로 detail raw replay 경로를 고정했다
- 이유: raw payload 재사용 경로는 “예전 JSON도 읽히고, 앞으로 쌓일 JSON도 깨끗해야” 안전하다. helper getter 하나 때문에 backfill이 전부 막히면 stored payload의 가치가 사라지므로, 직렬화/역직렬화 양방향 계약을 같이 잠가 재발을 막는 편이 안전하다

## 157) canonical sidecar raw replay를 기존 `CollectSource` fan-out 에 섞으면, 외부 API fetch와 stored payload replay 책임이 뒤섞여 manual collect 경로가 다시 비대해질 수 있음
- 문제: `NormalizedPolicySidecarBackfillService` 를 수동 실행 경로로 노출할 때 기존 `/api/admin/collect/{sourceKey}` 와 `CollectSource` enum에 `BOKJIRO_SIDECARS_BACKFILL` 같은 값을 추가하면, 외부 API를 다시 호출하는 collect orchestration과 이미 저장된 `raw_api_payloads` 를 replay 하는 maintenance 경로가 같은 fan-out 체계에 섞이게 된다. 이 방식은 새 replay/backfill 기능이 늘 때마다 `CollectSource` 와 `CollectService` 책임을 다시 키울 위험이 있었다
- 해결: backfill은 `POST /api/admin/collect/bokjiro-sidecars-backfill` exact path로 별도 노출하고, `scope=all|list|detail` 만 받아 `NormalizedPolicySidecarBackfillService` 를 직접 호출하도록 분리했다. generic collect route 는 여전히 외부 API fetch source dispatch만 맡는다
- 이유: collect enum/registry는 “새 데이터를 외부에서 가져오는 경로”에 집중해야 한다. stored payload replay까지 같은 축에 태우면 orchestration 의미가 흐려지고 controller/service fan-out debt가 다시 커지므로, manual maintenance path를 명시적으로 분리하는 편이 재발 방지에 안전하다

## 158) builder-only nested DTO는 단위 테스트용 `ObjectMapper` 에서는 우연히 통과해도, 실제 Spring `ObjectMapper` 로 raw payload replay를 돌리면 생성자 부재로 전체 detail backfill 이 0건 실패할 수 있음
- 문제: `BokjiroDetailClient.DetailPayload` 는 `@Builder` 와 getter만 있고 기본 생성자가 없었다. unit test에서는 직렬화된 테스트 JSON을 같은 로컬 `ObjectMapper` 로 읽는 경로만 봐서 지나갔지만, local integration smoke에서 stored detail payload 190건을 Spring `ObjectMapper` 로 다시 읽자 `Cannot construct instance ... no Creators` 예외가 모든 row에서 발생해 `detailResult.upsertedCount=0` 이 됐다
- 해결: `DetailPayload` 에 `@NoArgsConstructor` / `@AllArgsConstructor` 를 추가해 field-based 역직렬화 경로를 열고, `NormalizedPolicySidecarBackfillDensityIntegrationTest` 로 실제 local DB의 stored detail payload replay가 다시 통과하는지 고정했다
- 이유: raw payload replay는 테스트 fixture가 아니라 실제 저장 JSON을 대상으로 한다. nested DTO가 builder-only 면 단위 테스트가 놓친 역직렬화 계약 차이가 local/운영 replay에서 한꺼번에 터질 수 있으므로, Spring context 기준 실데이터 smoke와 생성자 계약을 같이 잠가 재발을 막는 편이 안전하다

## 159) 복지로 detail raw payload 에서 `BK_APPLY_END_DATE` fact 가 0건이라고 해서 바로 extractor 버그로 단정하면, source payload 자체에 deadline signal이 없는 상태를 잘못 해석할 수 있음
- 문제: local density smoke 직후 `service_facts` 를 보니 `BK_AGE_ELIGIBILITY` 는 81건인데 `BK_APPLY_END_DATE` 는 0건이었다. 처음엔 detail extractor 누락으로 보일 수 있지만, 실제 `raw_api_payloads` 를 확인해 보니 detail `applyMethodDetail` non-null 은 77건이어도 date-like token은 0건이었다
- 해결: `NormalizedPolicySidecarBackfillDensityIntegrationTest` 는 `BK_APPLY_END_DATE > 0` 을 고정 assertion으로 두지 않고, raw payload 에 date-like `applyMethodDetail` 이 존재할 때만 deadline fact 존재를 요구하도록 바꿨다. 동시에 `phase-plan` 과 `db-migration` 에 현재 local snapshot 수치(`detail payload 190`, `age facts 81`, `deadline facts 0`)를 남기고 후속 보강 과제를 별도로 분리했다
- 이유: density 측정의 목적은 추출기 결함과 source signal 부재를 구분하는 데 있다. payload 자체에 날짜 단서가 없는데 deadline fact를 기대하면 smoke가 잘못된 요구사항을 테스트하게 되므로, 현재 source 특성을 문서와 테스트에 같이 고정해야 재발 해석이 흔들리지 않는다

## 160) 복지로 detail 의 다른 필드(`targetDetail`, `selectionCriteria`, `supportDetail`)에 date-like token이 조금 보인다고 해서 deadline fallback 을 바로 넓히면, 출생연도 범위나 혜택 적용기간을 신청마감으로 오인할 수 있음
- 문제: local raw detail payload를 더 확인해 보니 `targetDetail/supportDetail/selectionCriteria` 에도 date-like token이 `4 / 1 / 1` 건 있었다. 하지만 샘플을 보면 `1976.1.1 ~ 2005.12.31` 같은 출생연도 범위, `2024.7.1 ~ 2024.10.31` 같은 할인 적용기간이 섞여 있어 이 값을 `BK_APPLY_END_DATE` 로 쓰면 잘못된 신청마감 fact가 생길 위험이 컸다
- 해결: 현재 canonical collect path 에서는 `BK_APPLY_END_DATE` 를 optional fact 로 유지하고, fallback 범위를 `applyMethodDetail/supportDetail` 밖으로 넓히지 않기로 결정했다. 후속 작업은 live detail 응답에서 신청마감 explicit field가 실제로 있는지 재확인하는 것으로 분리했다
- 이유: 복지로 detail 날짜 텍스트는 “신청마감”과 “생년월일/기준기간”이 같은 포맷으로 섞여 있다. 신호가 애매한 필드까지 규칙 기반 fallback 을 넓히면 precision이 급격히 떨어지므로, 현재는 누락을 감수하고 오탐을 막는 편이 canonical fact 품질에 더 안전하다

## 161) 복지로 age range 는 첫 번째 bound 뒤에 `세` 가 빠진 비대칭 표기(`만 20 ~ 49세`)가 실제로 존재하므로, `\\d+세 ~ \\d+세` 형태만 가정하면 sidecar age fact coverage가 눈에 띄게 깎일 수 있음
- 문제: local 복지로 raw detail/backfill density가 `81 / 1335` 에서 멈춰 있던 원인을 다시 보니, 실제 본문에는 `만 20 ~ 49세 여성`, `15세~39세 청년`, `만 19세 이상 ~ 34세 이하` 처럼 첫 번째 나이 뒤 `세` 가 빠지거나 `이상 ~ 이하` 조합인 문구가 섞여 있었다. 기존 `TextConstraintExtractor.AGE_RANGE` 는 사실상 `\\d+세 ... \\d+세` 패턴만 안정적으로 잡아 이런 row가 누락될 수 있었다
- 해결: `AGE_RANGE` 정규식을 `(\\d{1,2})(?:\\s*세)? ... (\\d{1,2})\\s*세` 형태로 넓히고, `TextConstraintExtractorTest` 에 asymmetric range / 이상~이하 / 기존 세~세 케이스를 추가했다. 이후 local replay integration 을 다시 태운 결과 복지로 `BK_AGE_ELIGIBILITY` 는 `81 -> 99` rows(`BOKJIRO_CENTRAL 49`, `BOKJIRO_LOCAL 50`)로 증가했다
- 이유: 복지로/지자체 서술형 본문은 표기 일관성이 약해서 “첫 번째 bound에도 단위가 항상 붙는다”는 가정이 쉽게 깨진다. 텍스트 extractor는 대칭 표기만 가정하지 말고 실제 source snapshot에서 반복되는 비대칭 표기를 허용해야 coverage regress를 줄일 수 있다

## 162) 복지로 detail 한 번 실행의 `95/API` cap 은 안전장치로는 맞지만, 이 상태를 모른 채 sidecar density를 보면 extractor 문제가 아니라 stored detail coverage ceiling을 잘못 해석할 수 있음
- 문제: local DB를 다시 보니 복지로 서비스는 `1335`건인데 detail raw payload는 정확히 `190`건이었다. 이는 `95/API` cap 이 있는 기본 detail 수집을 한 번만 돌린 흔적과 맞아떨어지며, `raw detail 없는 서비스 1145건`, `detail row는 있지만 raw가 없는 서비스 0건` 이라 sidecar replay density는 본문 추출기뿐 아니라 detail fetch coverage ceiling에도 강하게 묶여 있었다
- 해결: `BokjiroDetailCollectService` 에 multi-round `collectBokjiroDetailGapFillResult(rounds, maxCallsPerRound)` 를 추가하고, `CollectAdminController` 에 `POST /api/admin/collect/bokjiro-details-gap-fill` exact admin path 를 노출해 per-run cap은 유지한 채 여러 라운드로 missing detail backlog 를 점진적으로 더 메울 수 있게 정리했다
- 이유: rate limit 안전장치와 backlog drain 속도는 같은 문제가 아니다. 단일 실행 cap 때문에 생긴 coverage ceiling을 extractor 한계로 오해하면 잘못된 regex/heuristic만 계속 손보게 되므로, “여러 라운드로 안전하게 더 가져오는 수동 경로”를 따로 두는 편이 재발 해석과 운영 절차 모두에 안전하다

## 163) `bokjiro-details-gap-fill` 로 raw detail coverage 를 늘려도 새 payload 수와 `service_facts` 증가는 1:1 이 아닐 수 있으므로, coverage 개선과 fact density 개선을 별도 지표로 봐야 함
- 문제: local live smoke에서 `collectBokjiroDetailGapFillResult(2, 20)` 를 태우자 detail raw payload 는 `190 -> 199`, missing detail service 는 `1145 -> 1136` 으로 개선됐지만, `service_facts` 는 `99 -> 103` 으로 `4`건만 늘었다. 새 raw payload `9`건 전부가 곧바로 age/deadline fact로 이어질 것이라고 가정하면 gap fill 효과를 과대평가하거나 extractor 문제를 잘못 짚을 수 있었다
- 해결: `phase-plan` 과 `db-migration` 에 gap fill 결과를 `raw detail coverage` 와 `service_facts density` 두 축으로 나눠 기록하고, 다음 작업도 `추가 라운드/예산 계획` 과 `fact 미생성 sample 분석` 으로 분리했다
- 이유: detail 확보와 fact 추출은 서로 다른 단계다. raw payload 가 늘어도 본문에 eligibility signal이 없으면 canonical facts 는 그대로일 수 있으므로, 운영/리팩터링 판단은 coverage와 density를 분리해 봐야 재발 해석이 흔들리지 않는다

## 164) gap fill 후 남은 no-fact 복지로 detail 대부분은 extractor 실패가 아니라 연령 eligibility 자체가 없는 일반복지 항목이므로, 소수 예외 패턴만 보고 regex를 과하게 넓히면 precision을 해칠 수 있음
- 문제: gap fill 이후 `detail raw payload 는 있지만 service_facts 는 없는 복지로 서비스` 를 다시 샘플링해 보니 `130`건 중 `income-like` 는 `40`, `date-like` 는 `2`, strict `age-like` residual candidate 는 사실상 `1`건(`청년내일저축계좌` 의 `만 15세~만 40세`)뿐이었다. 그 외 다수는 저소득층 위문, 보훈 행사, 장애인 수리 지원처럼 연령 eligibility가 없는 일반복지 항목이었다
- 해결: `phase-plan` 과 `db-migration` 에 no-fact sample 분포를 기록하고, 후속 작업을 광범위한 age regex 확대가 아니라 `second-bound 만` 패턴 처리 여부와 `income-like` soft fact 후보 검토로 좁혔다
- 이유: sample 분포를 보지 않고 “facts가 없으니 regex를 더 넓힌다”로 가면 일반복지 본문에서 잘못된 age/income fact가 더 생길 수 있다. 남은 미생성 집합의 대부분이 비연령 항목이라면 precision을 지키면서 소수 패턴만 선택적으로 다루는 편이 재발 방지에 안전하다

## 165) 복지로 age range 는 두 번째 bound 앞에도 `만` 이 붙는 실제 residual 패턴(`만 15세~만 40세`)이 있어, 첫 번째 bound만 보강한 상태로 멈추면 소수지만 반복 가능한 age fact 누락이 남을 수 있음
- 문제: gap fill 후 no-fact sample 분석에서 residual `age-like` candidate 는 사실상 `청년내일저축계좌` 한 건뿐이었지만, 본문 패턴이 `만 15세~만 40세` 였다. 기존 `AGE_RANGE` 는 첫 번째 bound 앞의 `만` 만 허용하고 두 번째 bound는 `40세` 형태만 잡았기 때문에, precision을 해치지 않는 좁은 보강 여지가 남아 있었다
- 해결: `TextConstraintExtractor.AGE_RANGE` 를 `(?:만\\s*)?(\\d{1,2})(?:\\s*세)? ... (?:만\\s*)?(\\d{1,2})\\s*세` 형태로 보강하고 `TextConstraintExtractorTest` 회귀 케이스를 추가했다. 이후 stored raw payload replay integration 을 다시 태워 local DB에서 복지로 `service_facts` 가 `103 -> 104`, `BK_AGE_ELIGIBILITY` 가 `BOKJIRO_CENTRAL 52`, `BOKJIRO_LOCAL 52` 로 증가한 것을 확인했다
- 이유: 광범위한 regex 확장은 피해야 하지만, 실측 sample 하나가 실제 청년 정책 행이고 패턴도 매우 좁게 정의될 수 있다면 그런 residual은 선택적으로 흡수하는 편이 precision/coverage 균형에 맞다. “남은 게 거의 없으니 아예 안 건드린다”와 “광범위하게 다 넓힌다” 사이에서 source snapshot에 맞는 최소 보강을 택하는 것이 재발 방지에 안전하다

## 166) 복지로 no-fact detail 의 income-like signal 은 숫자 threshold와 수급자/차상위 자격이 한 버킷에 섞여 있고, threshold 쪽도 분기 조건이 많아 현재 canonical `INCOME_*` hard fact 로 바로 승격하면 의미가 쉽게 깨질 수 있음
- 문제: `second-bound 만` 보강 뒤 남은 no-fact 복지로 detail 을 다시 분류해 보니 income/beneficiary soft candidate 가 `49`건이었고, 구성은 `beneficiary_only 28`, `threshold_like 13`, `low_income_label 7`, `won_threshold 1`, `other 2` 였다. `threshold_like 13` 건도 `신혼/2자녀/출산/맞벌이/우대형/일반형/개별심사` 같은 분기 케이스가 `4`건, 다중 `% 이하` threshold 가 `3`건, 다중 `만원 이하` threshold 가 `1`건 섞여 있어 단일 `INCOME_PCT` 또는 `INCOME_WON` 값으로 접기 어려웠다
- 해결: 이번 단계에서는 복지로 income-like row를 canonical hard fact 로 승격하지 않고, `phase-plan` 과 `db-migration` 에 “`beneficiary_only` 는 soft taxonomy 검토”, “`threshold_like` 는 optional soft signal 검토”로 작업을 분리해 기록했다
- 이유: 현재 canonical facts 는 추천 hard filter 로도 재사용될 수 있어, 조건 분기와 자격 라벨이 섞인 source 신호를 성급히 숫자 fact 로 평탄화하면 false negative/false positive 둘 다 커질 수 있다. 먼저 의미가 안정적인 soft bucket 으로 나눠 설계하는 편이 재발 방지와 추천 품질 측면에서 안전하다

## 167) 복지로 detail 본문에서 `기초생활수급자` / `차상위계층` 같은 explicit beneficiary label 은 canonical `TARGET_GROUP` soft taxonomy 로 보존할 가치가 있지만, 현재 sidecar writer refresh contract 로는 detail term 추가가 list taxonomy 를 지워버릴 수 있음
- 문제: local snapshot 재분류 기준 `beneficiary_only` bucket 은 `기초생활수급자` 계열 `24`, `차상위계층` 계열 `5` 로 의미가 비교적 안정적이었고 `TARGET_GROUP` soft taxonomy 후보로 적절했다. 하지만 현재 `DeferredNormalizedPolicySidecarWriter` 는 복지로 aggregate 에 taxonomy term 이 하나라도 있으면 `LIFE_STAGE`, `INTEREST_THEME`, `TARGET_GROUP` 전체 refresh group 을 삭제 후 재삽입한다. 그래서 detail aggregate 에 beneficiary `TARGET_GROUP` 만 추가하면 기존 list aggregate 의 `청년`, `1인가구` 같은 term 도 같이 사라질 수 있었다
- 해결: 이번 단계에서는 `기초생활수급자` / `차상위계층` whitelist 와 label normalize 규칙만 문서로 고정하고, 구현 선행조건으로 `phase-aware term refresh` 분리를 `phase-plan` 에 추가했다
- 이유: semantic decision 이 맞더라도 writer contract 가 그 source-phase 구분을 표현하지 못하면 실제 적재 시 side effect 가 더 크다. detail-derived soft taxonomy 는 list-derived taxonomy 와 공존해야 하므로, refresh 경계를 먼저 분리하는 편이 재발 방지에 안전하다

## 168) sidecar term refresh 를 `term_group` 단위로만 잡으면 복지로처럼 list phase와 detail phase가 같은 `TARGET_GROUP` 을 공유하는 source에서 detail soft taxonomy 한 줄을 넣는 순간 기존 list taxonomy 까지 같이 지워질 수 있음
- 문제: 복지로 list aggregate 는 `TARGET_GROUP(source_field=trgterIndvdlNmArray)` 로 `청년`, `1인가구` 같은 공식 term 을 저장하고, 향후 detail aggregate 는 `TARGET_GROUP(source_field=targetDetail/selectionCriteria)` 로 `기초생활수급자` 같은 derived term 을 넣게 된다. 기존 writer는 `term_group='TARGET_GROUP'` 전체를 delete 후 재삽입했기 때문에, detail phase upsert 한 번으로 list phase `TARGET_GROUP` 까지 같이 사라질 수 있었다
- 해결: `DeferredNormalizedPolicySidecarWriter` 의 term replace contract 를 incoming term의 `(term_group, source_field)` scope 로 좁혔다. unit test 에서 delete SQL/args 를 검증했고, integration 에서는 list aggregate 뒤 detail-derived `TARGET_GROUP` 를 직접 upsert 해도 기존 `청년`, `1인가구` 와 공존하는 것을 확인했다
- 이유: canonical term 은 source와 의미만 아니라 “어느 phase/list/detail 에서 왔는가”도 같이 관리해야 한다. 같은 `term_group` 안에서도 source_field가 phase 경계를 표현하고 있다면 refresh scope 도 거기에 맞춰 좁혀야, 향후 beneficiary whitelist 같은 detail soft taxonomy 를 안전하게 추가할 수 있다

## 169) 복지로 detail 본문에서 beneficiary soft taxonomy 를 넓게 잡으면 `취업취약계층`, `정보 소외계층`, `저소득 한부모가족` 같은 broad label 이 canonical `TARGET_GROUP` 으로 섞여 taxonomy 정밀도가 급격히 떨어질 수 있음
- 문제: local snapshot 재분류에서 `beneficiary_only` bucket 안에도 `기초생활수급자` / `차상위계층` 처럼 의미가 안정적인 label 과, `취업취약계층`, `정보 소외계층`, `저소득 한부모가족` 처럼 범위가 넓거나 다른 축과 겹치는 label 이 함께 나왔다. 이들을 동일 규칙으로 `TARGET_GROUP` 에 넣으면 복지로 detail soft taxonomy 가 broad vulnerable-group bucket 으로 오염될 위험이 있었다
- 해결: `WelfareServiceMapper.toNormalizedBokjiroDetail()` 는 `국민기초생활보장수급자`, `기초생활수급자`, `생계/의료/주거/교육급여 수급자`, `수급권자` 만 `기초생활수급자` 로, `차상위*` 표현만 `차상위계층` 으로 정규화해 `TARGET_GROUP(source_field=targetDetail/selectionCriteria, authority=SYSTEM_DERIVED)` term 으로 적재하고, broad label 은 계속 skip 하도록 unit/integration test 로 고정했다
- 이유: beneficiary soft taxonomy 의 목적은 hard income fact 대체가 아니라 “의미가 안정적인 취약계층 eligibility signal” 보존이다. canonical `TARGET_GROUP` 은 추천 read-model 에 재사용될 가능성이 있으므로, broad label 까지 같이 적재하면 precision 손실이 더 크다

## 170) 같은 테스트 클래스를 대상으로 `gradlew test` 와 `gradlew integrationTest` 를 병렬 실행하면 Gradle XML result writer 가 동일 결과 파일을 동시에 만지면서 가짜 실패를 낼 수 있음
- 문제: 이번 task 검증에서 `NormalizedPolicyAggregateTest` / `BokjiroSidecarMergeIntegrationTest` 를 두 Gradle 세션으로 거의 동시에 돌리자, 테스트 본문은 통과했는데 `Could not write XML test results ... TEST-com.example.welfare.integration.BokjiroSidecarMergeIntegrationTest.xml` 예외로 한 세션이 실패했다
- 해결: 같은 테스트 클래스나 동일 result directory 를 쓰는 검증은 병렬로 돌리지 않고, unit -> integration 순서로 순차 재실행해 결과를 확정했다
- 이유: 코드 결함과 빌드 산출물 write collision 은 원인이 다르다. 결과 파일 경합을 테스트 실패로 오해하지 않으려면 같은 타깃 검증은 순차 실행으로 고정하는 편이 안전하다

## 171) 복지로 beneficiary whitelist density 는 `payload 수 == service 수 == term 수` 로 1:1 대응하지 않을 수 있으므로 row count 와 distinct service count 를 분리해 봐야 함
- 문제: local draft migration 상태에서 beneficiary-like detail raw payload candidate 는 `42`건이었는데, replay 후 whitelist `TARGET_GROUP(source_field=targetDetail/selectionCriteria)` term 은 `59 rows / 42 services` 로 적재됐다. `기초생활수급자` `38`, `차상위계층` `21` 이라 일부 서비스는 두 label을 동시에 갖고 있었고, 단순 term row count 만 보면 coverage가 실제 서비스 수보다 커 보일 수 있었다
- 해결: beneficiary density 측정은 `NormalizedPolicySidecarBeneficiaryTermDensityIntegrationTest` 와 문서에서 항상 `term row count` 와 `distinct service count` 를 같이 기록하도록 정리했다
- 이유: beneficiary soft taxonomy 는 hard fact 한 줄과 달리 한 서비스에 복수 term 이 공존할 수 있다. row count 만 보면 중복으로 과대평가되고, service count 만 보면 label richness가 사라지므로 두 지표를 같이 봐야 재발 해석이 정확하다

## 172) 복지로 beneficiary whitelist overlap service 는 `기초생활수급자` 와 `차상위계층` 을 collapse 하기보다 multi-term 으로 유지해야 source 의미를 보존할 수 있음
- 문제: local snapshot 에서 beneficiary whitelist overlap service 가 `17`건 있었고, `여성청소년 생리용품 지원`, `통합문화이용권`, `자활근로(기초, 차상위)`, `재난적의료비 지원 사업` 처럼 source 자체가 두 집단을 함께 명시한 사례가 다수였다. 이를 하나의 상위 label로 collapse 하면 “두 집단 모두 대상”이라는 원문 의미가 사라질 수 있었다
- 해결: current canonical 정책은 `기초생활수급자` 와 `차상위계층` 을 상하위 collapse 하지 않고 multi-term 으로 그대로 유지하도록 문서에 고정했다. 대신 이후 recommendation/read-model 단계에서만 중복 가중치 dedupe 를 따로 설계하는 것으로 후속 작업을 분리했다
- 이유: beneficiary bucket 은 hard fact 가 아니라 soft taxonomy 이므로, source가 제공한 대상군 richness를 보존하는 편이 안전하다. collapse 는 나중 read-model에서 언제든 할 수 있지만, 저장 단계에서 잃은 정보는 되돌리기 어렵다

## 173) 복지로 beneficiary soft taxonomy 는 저장 단계에서 multi-term 을 보존하되, 추천 단계에서는 `BENEFICIARY_SUPPORT` 같은 dedupe bucket 으로 max-one bonus 를 주는 편이 current rule 구조와 가장 자연스럽게 맞는다
- 문제: beneficiary whitelist 를 multi-term 으로 남기면 원문 의미는 보존되지만, future canonical read-path 가 raw `TARGET_GROUP` term 두 개를 그대로 scoring 에 태우면 같은 혜택축에서 double-count 가 생길 수 있다. 반대로 저장 단계에서 collapse 하면 나중에 세부 라벨을 설명/배지/UI 에 쓰기 어렵다
- 해결: persistence 는 raw multi-term 유지, read-model 은 `BENEFICIARY_SUPPORT` dedupe bucket 별도 생성, scoring 은 bucket 기준 서비스당 최대 1회 bonus 라는 전략으로 문서에 고정했다. 현재 `RuleScoringService.targetGroupMatches(...)` 도 boolean 매칭 기반이라 동일 축 다중 라벨을 바로 2배 가산하지 않는다는 점을 근거로 future sidecar read-path 도 같은 `max-one bonus` 규칙을 유지하기로 했다
- 이유: 현재 추천 구조는 합계형 점수이지만 target group 매칭은 본질적으로 “해당 축 매칭 여부”에 가깝다. soft taxonomy 의 raw richness 는 read-model 밖에서 보존하고, scoring 에서는 bucket 단위로 dedupe 하는 쪽이 precision 과 explainability 를 같이 지키기 쉽다

## 174) beneficiary dedupe 는 repository SQL에서 바로 collapse 하기보다 canonical recommendation read-model projection 에서 raw term과 dedupe bucket을 함께 만드는 편이 retrieval/scoring/response 책임을 덜 섞는다
- 문제: beneficiary raw term을 SQL 단계에서 곧바로 `BENEFICIARY_SUPPORT` 하나로 접어버리면 explanation/UI용 raw label, source drift 분석, 향후 bucket 정책 변경 여지가 함께 사라진다. 반대로 raw term만 그대로 넘기면 scoring 단계에서 중복 가산 위험이 남는다
- 해결: `RecommendationCandidateProjection` 류의 canonical recommendation read-model projection 을 두고, 여기서만 `targetGroupsRaw`, `beneficiaryTerms`, `targetGroupBuckets` 를 동시에 구성하는 경계를 문서로 고정했다. retrieval 은 base entity query 뒤 projection hydrate, scoring 은 projection의 deduped bucket만 사용, response/UI는 raw term을 그대로 참조하는 구조다
- 이유: persistence / projection / scoring / response 책임을 분리하면 저장 단계는 source truth를 보존하고, 추천 단계는 scoring-friendly 구조를 소비하며, UI는 설명 가능성을 유지할 수 있다. 이 경계를 먼저 고정해야 이후 sidecar read-path 구현이 다시 raw table join과 rule logic을 뒤섞지 않는다

## 175) canonical read-model 초안은 실제 추천 파이프라인 연결 전에 repository 단계에서 bucket 조립 규칙을 먼저 고정해야 retrieval/service 경계가 다시 흔들리지 않음
- 문제: 문서로만 `RecommendationCandidateProjection` 경계를 정해두면, 실제 구현 시 `RetrievalService` 나 `RuleScoringService` 안에서 raw sidecar row를 즉석으로 묶거나 beneficiary bucket을 서비스 레이어에서 임시 조립하는 식으로 다시 책임이 흩어질 수 있었다
- 해결: `RecommendationCandidateProjection` DTO와 `CanonicalRecommendationReadModelRepository` 초안을 먼저 추가해 `service_id IN (...) -> projection hydrate -> BENEFICIARY_SUPPORT dedupe bucket 생성` 규칙을 repository 단계에서 코드로 고정했다. unit test로 `기초생활수급자` + `차상위계층` raw term이 있어도 bucket은 1개만 생기는 것을 검증했다
- 이유: retrieval/scoring 전환은 단계적으로 갈 수 있어도, projection 조립 위치는 초기에 잘못 잡으면 이후 리팩터링 비용이 커진다. 추천기가 raw term을 직접 만지지 않도록 repository boundary를 먼저 코드화하는 편이 재발 방지에 유리하다

## 176) canonical recommendation read-model 전환은 retrieval hydrate 와 scoring 소비를 한 번에 묶지 않고 분리해야 legacy 추천 품질 회귀를 좁은 범위로 막을 수 있음
- 문제: `RetrievalService` 에 canonical projection hydrate 를 붙이는 단계에서 곧바로 `RuleScoringService` 입력 구조까지 함께 바꾸면, 후보 SQL/후처리 필터 변경과 점수 회귀가 한 PR에 섞여 원인 분리가 어려워진다. 특히 지금 scoring/persistence 흐름은 아직 `List<WelfareService>` 를 기준으로 안정적으로 동작 중이라, 첫 연결부터 scoring까지 건드리면 실패 반경이 커질 수 있었다
- 해결: `RetrievedRecommendationCandidates` DTO를 추가해 retrieval 단계가 legacy `WelfareService` 후보와 canonical projection map 을 함께 반환하게만 바꾸고, `RecommendationFacade` 는 당분간 `candidates()` 만 꺼내 써서 scoring/persistence 경로를 그대로 유지했다. 다음 단계에서만 `RuleScoringService` 가 projection bucket을 병행 소비하도록 분리했다
- 이유: canonical read-model 전환은 retrieval, scoring, response 경계를 순차적으로 옮기는 편이 디버깅과 회귀 검증에 유리하다. hydrate 연결과 scoring 전환을 분리하면 “후보 추출 회귀”와 “점수 계산 회귀”를 각각 독립적으로 검증할 수 있다

## 177) canonical beneficiary bucket 은 별도 가산 슬롯을 새로 만들기보다 기존 `targetGroupMatches` boolean bonus 슬롯에 OR 로 연결해야 current rule score 의미를 덜 흔든다
- 문제: `BENEFICIARY_SUPPORT` bucket 을 scoring 에 연결할 때 새 보너스 항목으로 따로 더하면, 기존 `TARGET_GROUP` 보너스와 함께 같은 축을 이중 가산할 수 있었다. 특히 `기초생활수급자` 와 `차상위계층` raw term을 multi-term 으로 보존하는 현재 정책과 겹치면 “dedupe bucket 도 있고 broad target match 도 있다”는 이유로 점수 회귀가 생길 수 있었다
- 해결: 첫 단계 브리지는 `RuleScoringService.score(RetrievedRecommendationCandidates, ...)` 오버로드만 추가하고, canonical `BENEFICIARY_SUPPORT` bucket 은 기존 `targetGroupMatches(...)` boolean 슬롯에 OR 로 연결했다. 동시에 `beneficiaryTerms` 로 `기초생활수급자<=1`, `차상위계층<=3` threshold 를 좁게 걸어 서비스당 최대 1회 bonus 만 허용했다
- 이유: beneficiary bucket 의 목적은 새로운 축을 더 만드는 것이 아니라 raw multi-term 에서 중복 가산을 막으면서 기존 target-group 의도를 canonical read-model 로 옮기는 것이다. 기존 bonus 슬롯에 병행 연결하는 편이 legacy score 의미를 덜 흔들고 회귀 범위도 좁다

## 178) canonical broad term(`interestThemes`, `keywordTags`, `targetGroupsRaw`) 도 beneficiary와 마찬가지로 새 가산 항목을 만들기보다 legacy boolean matcher에 OR 로 연결해야 점수식 폭증을 막을 수 있음
- 문제: retrieval hydrate 뒤 projection term을 읽기 시작할 때, 기존 `ServiceTag` 기반 bonus 위에 canonical term bonus 를 새로 더하면 같은 관심분야/대상군 신호가 legacy tag 와 projection 양쪽에서 동시에 들어오는 순간 점수가 불필요하게 커질 수 있었다. 특히 `targetGroupsRaw` 는 sidecar backfill 이 진행될수록 legacy tag 와 겹칠 가능성이 높았다
- 해결: `RuleScoringService` 의 `interestThemeMatches`, `keywordMatches`, `targetGroupMatches` 가 projection 값을 별도 가산 슬롯으로 더하지 않고 기존 boolean matcher 안에서 OR 로만 읽도록 바꿨다. `factKeys` 는 이번 단계에서 `isDeadlineSoon` helper 경계에만 먼저 연결하고, bonus 규칙 자체는 기존 `apply_end_date` 의미를 유지했다
- 이유: canonical read-model 브리지는 “같은 의미의 신호를 다른 저장소에서 읽는 것”이지 새 점수 축을 추가하는 작업이 아니다. 기존 matcher 슬롯을 재사용해야 legacy path 와 canonical path 가 공존하는 동안에도 score inflation 없이 회귀를 좁게 통제할 수 있다

## 179) priority 가중치도 새 taxonomy 해석기를 따로 만들기보다 먼저 `compat_unified_category` / `applyEndDate` read-model summary 를 우선 읽게 연결해야 `DefaultPriorityMatcher` 회귀를 좁게 막을 수 있음
- 문제: `DefaultPriorityMatcher` 는 아직 `service.getUnifiedCategory()` 와 `service.getApplyEndDate()` 에 직접 묶여 있어, retrieval/scoring 이 canonical projection 을 병행 읽기 시작한 뒤에도 priority 가중치만 legacy entity 값을 계속 보면 read-model summary 와 우선순위 가중치가 서로 다른 값을 볼 수 있었다. 반대로 첫 단계부터 canonical taxonomy summary code/label 해석까지 matcher 안에 같이 넣으면 회귀 반경이 다시 커질 수 있었다
- 해결: `PriorityMatcher.matches(priority, service, projection)` 오버로드를 추가하고, `DefaultPriorityMatcher` 는 먼저 `RecommendationCandidateProjection.unifiedCategoryCompat` 와 `applyEndDate` 를 우선 읽도록만 좁게 바꿨다. `RuleScoringService.applyPriorityWeight(...)` 도 projection 을 함께 넘기게 맞췄고, canonical taxonomy summary code/label 직접 해석은 후속 task 로 분리했다
- 이유: priority 가중치는 현재도 `unifiedCategory` 호환 레이어를 전제로 동작한다. 첫 단계는 legacy entity와 canonical read-model summary 간 불일치를 줄이는 것이 목적이고, category code 체계 자체를 바꾸는 일은 별도 결정으로 분리해야 원인 분리가 쉽다

## 180) `service_taxonomies` summary가 아직 완전히 안정화되기 전에는 `DefaultPriorityMatcher` 가 canonical summary code/label을 직접 해석하지 말고 `compat_unified_category` 를 priority 호환 레이어로 유지하는 편이 안전함
- 문제: canonical read-model 브리지가 진행되면서 `DefaultPriorityMatcher` 를 바로 `youth_major_code`, `gov24_service_field_code` 같은 summary code/label 직독 방식으로 바꾸고 싶어질 수 있다. 하지만 현재는 `service_taxonomies` 실제 적재 coverage, `compat_unified_category` 와 summary code 간 drift, priority 옵션 코드와 summary code 매핑표가 아직 확정되지 않아, matcher가 이를 직접 읽기 시작하면 우선순위 의미가 조용히 바뀔 위험이 있었다
- 해결: 이번 단계에서 `compat_unified_category` 를 계속 priority 호환 레이어로 유지하기로 문서에 고정하고, `DefaultPriorityMatcher` 는 당분간 `RecommendationCandidateProjection.unifiedCategoryCompat` / `applyEndDate` 만 우선 읽게 유지했다. canonical summary code/label 직독 전환은 `service_taxonomies` summary 안정화, drift inventory, priority 매핑표가 준비된 뒤 별도 task로 넘겼다
- 이유: priority는 추천 점수의 배율을 직접 바꾸므로, category 해석 기준이 흔들리면 회귀가 조용히 커진다. 호환 레이어를 한동안 유지하면 현재 UX 의미를 보존한 채 canonical summary 데이터 품질을 먼저 검증할 수 있다

## 181) local `service_taxonomies` snapshot 기준 현재 drift는 “compat와 canonical 직접 충돌”보다 `youth_major_label` summary의 raw 복사/다중값 흔적이 더 큰 문제라, matcher를 summary 직독으로 옮기기 전에 summary 정제가 먼저 필요함
- 문제: 실제 local DB를 집계해 보니 `compat_unified_category_label` 은 `3634 / 3634` rows에서 채워졌지만 `youth_major_label` 은 `2298 / 3634`, `youth_mid_label` 과 `gov24_service_field_label` 은 `0` 이었다. 특히 `YOUTH` 에서 `compat=기타` + `youth_major_label` 채움이 `471`, `youth_major_label` comma 포함이 `102` 였고, 샘플을 보면 `일자리,일자리`, `주거,주거`, `금융･복지･문화` 같은 raw `category_main` 복사 흔적이 남아 있었다
- 해결: 이 결과를 별도 inventory 문서로 고정하고, 다음 작업을 `YOUTH category_main -> youth_major summary 정제 규칙`, `comma/duplicate collapse`, `summary 재적재 후 drift 재측정` 으로 다시 쪼갰다. 현재는 `compat_unified_category` 를 priority 호환 레이어로 유지하고 summary 직독은 계속 보류한다
- 이유: 지금 단계의 핵심 문제는 compat와 canonical이 서로 다른 카테고리를 가리키는 충돌보다, canonical summary 자체가 아직 single normalized summary로 안정화되지 않았다는 점이다. 정제 전 summary를 matcher에 바로 연결하면 drift보다 summary 품질 문제가 더 크게 우선순위를 흔들 수 있다

## 182) `service_taxonomies.youth_major_*` summary는 raw `category_main` 문자열을 그대로 복사하지 말고, single canonical major로 안정적으로 collapse 가능한 경우에만 채워야 함
- 문제: drift inventory를 더 들여다보니 `youth_major_label` 에는 `금융･복지･문화`, `참여･기반` 같은 raw label 뿐 아니라 `일자리,일자리`, `주거,주거`, `일자리,교육` 같은 comma-delimited 문자열도 그대로 들어가 있었다. 이 값을 summary에 남겨두면 `service_taxonomies` 가 “서비스당 1행 canonical summary” 역할을 못 하고 raw multi-value dump와 다를 바 없어졌다
- 해결: `YOUTH category_main -> service_taxonomies.youth_major_*` 규칙을 문서로 고정해, raw `category_main` 은 split/trim 후 canonical major 집합(`일자리`, `주거`, `교육`, `복지문화`, `참여권리`)으로 normalize 하고, distinct canonical code 가 `1`개일 때만 summary를 채우기로 했다. `2+` distinct code 가 나오면 summary는 `NULL` 로 두고 raw richness는 term/raw 계층에만 남기도록 정리했다
- 이유: summary row는 read-model과 matcher가 빠르게 읽는 single canonical 값이어야 한다. raw multi-value를 그대로 두면 drift보다 summary semantics 자체가 무너지므로, collapse 가능한 경우만 채우고 나머지는 `NULL` 로 두는 편이 안전하다

## 183) `DeferredNormalizedPolicySidecarWriter` 도 youth major summary 규칙을 그대로 따라야지, mapper에서 받은 raw `taxonomy.youthMajor()` 문자열을 다시 summary에 그대로 쓰면 drift가 즉시 재발함
- 문제: `YOUTH category_main -> youth_major_*` 정제 규칙을 문서로만 고정해 두고 writer가 계속 `taxonomy.youthMajor()` raw string을 그대로 `service_taxonomies` 에 적재하면, backfill이 아니라 live collect/upsert 경로에서 곧바로 `금융･복지･문화`, `주거,주거`, `일자리,교육` 같은 raw/duplicate/multi-major 문자열이 summary 컬럼에 다시 들어가 drift inventory가 재발할 수 있었다
- 해결: `DeferredNormalizedPolicySidecarWriter` 에 writer-side normalize helper를 추가해 comma split, punctuation normalize(`･ -> ·`), canonical label collapse를 먼저 적용하고, distinct canonical major가 `1`개일 때만 `youth_major_code`, `youth_major_label` 을 채우도록 바꿨다. variant normalize, duplicate collapse, multi-major null summary는 `DeferredNormalizedPolicySidecarWriterTest` 로 고정했다
- 이유: summary 정제 규칙은 backfill SQL뿐 아니라 live collect path에도 동일하게 걸려야 한다. writer가 raw summary를 다시 써 버리면 canonical read-model과 priority layer가 보는 값이 계속 흔들리므로, 적재 경계에서 single canonical summary invariant를 강제하는 편이 안전하다

## 184) draft backfill SQL도 writer와 같은 youth major collapse 규칙을 써야지, 한쪽만 정제하면 collect refresh와 초기 backfill이 서로 다른 summary를 남겨 drift가 반복됨
- 문제: writer는 single canonical major만 summary에 남기도록 정제됐지만, `V2026_04_30_02__seed_policy_normalization_codes.sql` 이 여전히 `ws.category_main` exact match / raw label 복사 방식이면 초기 backfill 직후에는 `금융･복지･문화`, `참여･기반`, `일자리,일자리` 같은 값이 들어가고, 이후 live collect refresh가 돌 때만 일부 row가 정제되는 식으로 경로별 summary semantics가 달라질 수 있었다
- 해결: draft SQL에 `JSON_TABLE` 기반 token split, punctuation normalize, canonical major collapse CTE를 추가해 `distinct canonical major = 1` 일 때만 `youth_major_code`, `youth_major_label` 을 채우도록 바꿨다. local YOUTH snapshot 재집계 결과 `2299 total / 2248 filled / summary_with_comma=0 / raw_variant_labels=0` 으로 정제 효과를 확인했다
- 이유: canonical summary는 “누가 적재했느냐”와 무관하게 같은 규칙으로 채워져야 한다. writer와 backfill이 다른 collapse 규칙을 쓰면 drift inventory가 데이터 품질 문제가 아니라 경로 차이 문제로 오염되므로, 적재 경로 둘 다 같은 single-major invariant를 공유해야 한다

## 185) `youth_major` summary를 실제로 재적재하고 나니 남은 drift의 본질은 raw multi-value가 아니라 `compat=기타` 와 canonical major 공존 문제였음
- 문제: 기존 inventory에서는 `youth_major_label` 의 comma/duplicate/raw variant가 너무 커서, `compat_unified_category` 와 canonical summary 사이의 진짜 남은 차이가 “summary 품질”인지 “호환 레이어 설계”인지 분리하기 어려웠다
- 해결: local DB `service_taxonomies` 의 `YOUTH` summary를 collapse 규칙으로 다시 써넣고 재집계했다. 그 결과 `youth_major_with_comma=0`, `raw_variant_labels=0` 으로 summary 품질 문제는 사라졌고, 대신 `compat=기타 + canonical youth_major 채움` 집합이 `421`건 남는다는 점이 핵심 잔여 drift로 분리됐다
- 이유: summary가 single canonical value로 안정화된 뒤에는 더 이상 “정제 먼저”가 아니라 “`compat` 를 계속 저장할지, YOUTH canonical major를 어디까지 priority/read-model에 반영할지”가 다음 설계 질문이 된다. 문제 성격이 달라졌으므로 후속 task도 summary 정제에서 호환 레이어 해석 정책으로 옮기는 편이 맞다

## 186) `compat=기타 + canonical youth_major 채움` 집합은 지금 바로 canonical major로 override 하지 말고, priority/read-model 에서는 secondary hint로만 남겨야 기존 제품 의미를 덜 흔든다
- 문제: summary 정제 후에도 `compat=기타 + youth_major 채움` 이 `421`건 남았기 때문에, 이를 보고 `DefaultPriorityMatcher` 나 response category를 즉시 canonical `복지문화/참여권리/교육` 등으로 치환하고 싶어질 수 있다. 하지만 현재 priority 옵션(`금융·생활지원`, `참여·기회`, `교육·직업훈련`)은 아직 canonical major와 1:1 브리지 표가 없고, `compat` 는 실제 제품 계약으로 동작 중이라 조용한 의미 변경이 생길 위험이 컸다
- 해결: 이번 단계에서는 `compat=기타` 를 canonical `youth_major` 로 자동 override 하지 않기로 고정했다. priority/scoring/response category는 계속 `compat_unified_category` 만 기준으로 유지하고, canonical `youth_major` 는 read-model에서 inventory/explanation/future experiment 용 보조 힌트로만 남긴다
- 이유: explicit bridge table 없이 canonical major를 legacy priority bucket으로 승격하면, 저장 계층의 정규화 성공이 곧바로 UX 의미 변경으로 번진다. 현재는 raw truth와 호환 레이어를 분리해 둔 장점을 유지하고, 별도 inventory와 매핑표가 준비된 뒤에만 bridge를 명시적으로 열어 두는 편이 안전하다

## 187) `compat=기타 + canonical youth_major` 집합도 한 덩어리로 보면 안 되고, major별 분포를 먼저 쪼개야 bridge table 검토 우선순위를 제대로 잡을 수 있음
- 문제: `421`건을 단순 총량으로만 보면 `canonical youth_major -> legacy priority` 브리지를 하나의 결정처럼 다루게 되지만, 실제로는 `복지문화 171`, `참여권리 130`, `교육 102`, `일자리 15`, `주거 3` 으로 분포가 크게 달랐다. 각 major 안의 `category_sub` 조합도 서로 달라, 같은 bridge 정책을 한 번에 적용하면 과도하게 일반화될 위험이 있었다
- 해결: local DB에서 major별 count, 대표 `category_sub`, 샘플 row를 다시 뽑아 별도 inventory 문서로 고정했다. 그 결과 bridge 검토 우선순위를 `참여권리`, `교육`, `복지문화` 3개에 집중하고, `일자리`, `주거` 는 duplicate collapse 잔여로 간주해 우선순위를 낮췄다
- 이유: bridge table은 저장 계층의 canonical major를 UX priority bucket으로 승격시키는 규칙이므로, 총량이 아니라 major별 의미 분포를 기준으로 검토해야 한다. 먼저 분포를 쪼개야 어디가 “실제 새 bridge 후보”이고 어디가 “정제 잔여”인지 구분할 수 있다

## 188) major별 분포를 봐도 bridge 승격 판단은 다시 row-level sample로 좁혀야 하고, `교육 / 참여권리 / 복지문화` 는 서로 다른 판정이 필요함
- 문제: `복지문화 171`, `참여권리 130`, `교육 102`처럼 큰 major만 놓고 보면 셋 다 bridge 후보처럼 보일 수 있다. 하지만 실제 sample을 보면 `교육` 은 `교육비지원/미래역량강화/온·오프라인교육` 중심으로 현재 `교육·직업훈련` bucket과 가깝고, `참여권리` 는 `청년참여`와 `정책인프라구축` 이 섞여 있으며, `복지문화` 는 `건강/문화활동/예술인지원` 이 많이 섞여 의미가 다르다
- 해결: row-level review를 통해 `교육` 은 “가장 유력한 후속 후보”, `참여권리` 는 “subset bridge만 조건부 검토”, `복지문화` 는 “현 시점 보류”로 판정을 갈랐다. 즉 bridge table이 필요하더라도 전집합 일괄 도입이 아니라 candidate별로 다른 정책을 써야 한다고 문서로 고정했다
- 이유: canonical major를 legacy priority bucket으로 승격시키는 규칙은 category label 하나만 맞는다고 끝나지 않는다. 실제 row-level 행동 가능성, 운영/인프라 성격 혼입 여부, 현재 priority bucket 의미를 함께 봐야 하므로 candidate별 판정을 분리하는 편이 안전하다

## 189) explicit bridge table은 candidate review가 끝났다고 바로 만드는 게 아니라, 실제 실험/전환이 시작될 때만 도입해야 함
- 문제: `교육` 이 가장 유력한 후보라는 결론이 나오면, 이를 이유로 `youth_major -> priority bucket` explicit bridge table을 미리 만들어 두고 싶어질 수 있다. 하지만 현재 review 결과는 “후보 우선순위”일 뿐이고, `참여권리` 는 subset 조건부, `복지문화` 는 보류라 전체 table 스키마를 먼저 확정하면 오히려 부분적으로만 유효한 규칙을 시스템 계약처럼 굳혀 버릴 위험이 있었다
- 해결: 이번 단계에서는 explicit bridge table을 만들지 않기로 고정했다. bridge table은 실제 전환 대상이 생길 때, 예를 들어 `교육 -> 교육·직업훈련` 단일 후보 실험이나 `청년참여` subset bridge를 시작할 때만 도입하는 artifact로 정의했다
- 이유: bridge table은 문서 보조물이 아니라 matcher/read-model/response 의미를 바꾸는 실행 계약이다. 확정되지 않은 후보들을 한 표에 먼저 넣으면 “지금은 안 쓴다” 해도 나중에 암묵 계약처럼 소비될 수 있으므로, 실제 사용 시점 직전에 가장 작은 범위로 도입하는 편이 안전하다

## 190) `교육 -> 교육·직업훈련` 은 bridge candidate 중 가장 안전하지만, 그래도 기본값 승격이 아니라 narrow experiment 후보로만 승인해야 함
- 문제: `교육` 집합은 sample 기준으로 가장 자연스러운 후보라 바로 `compat=기타` 를 `교육·직업훈련` 으로 승격하고 싶어질 수 있다. 하지만 여기에는 `교육비지원`, `미래역량강화`, `온·오프라인교육` 이 함께 섞여 있어, “교육 canonical major가 보이면 곧바로 제품 category도 바꾼다”로 가면 실험 없이 기본 동작을 바꾸는 문제가 생긴다
- 해결: 이번 단계에서는 `교육 -> 교육·직업훈련` 을 **첫 실험 후보**로만 승인했다. 실험 범위는 `compat=기타 + youth_major=교육` 집합에 한정하고, 기본 matcher/response category는 유지한 채 필요하면 priority bonus 경계에서만 좁게 실험하도록 문서로 고정했다
- 이유: `교육`은 bridge 후보 중 가장 안전하지만, “후보”와 “기본값”은 다르다. 먼저 가장 작은 범위에서 효과와 부작용을 볼 수 있게 해야 나머지 `참여권리`, `복지문화` 판단에도 같은 기준을 적용할 수 있다

## 191) `교육 -> 교육·직업훈련` 실험은 matcher에 넣지 말고 scoring bonus 경계에만 둬야 priority 계약 자체가 흔들리지 않음
- 문제: `교육`을 첫 실험 후보로 승인한 뒤, 이를 `DefaultPriorityMatcher` 에 직접 넣으면 `EDUCATION` priority의 match semantics 자체가 바뀌어 버린다. 그러면 feature flag를 꺼도 matcher 의미와 response/category 해석 경계가 같이 얽혀 rollback 단위가 커질 수 있다
- 해결: 실험 삽입 위치를 `RuleScoringService` 의 narrow priority bonus 경계로 고정했다. `DefaultPriorityMatcher` 는 계속 `compat_unified_category` 기반 category contract만 유지하고, `compat=기타 + youth_major=교육` 실험은 scoring layer additive bonus + flag로만 제어한다
- 이유: matcher 는 stable product contract, scoring 은 좁은 실험 레이어다. bridge candidate 실험은 additive bonus 경계에서 먼저 검증하는 편이 on/off, rollback, 영향 범위 설명이 모두 쉽다

## 192) `교육` bridge 실험은 이미 row/user 조건이 좁기 때문에 rollout percent나 user allowlist까지 겹치면 오히려 회귀 원인 분리가 어려워짐
- 문제: `교육 -> 교육·직업훈련` 실험을 실제로 켜는 시점을 상정하면 percentage rollout, 특정 사용자 allowlist, 환경별 다중 토글 같은 제어 장치를 같이 넣고 싶어질 수 있다. 하지만 이번 실험은 애초에 `compat=기타 + youth_major=교육 + user priority=EDUCATION + priority bonus 경계` 로 이미 후보와 사용자 범위가 좁아, 토글 축을 더 늘리면 왜 순위가 바뀌었는지 설명이 오히려 어려워질 수 있다
- 해결: flag 범위를 `recommend.priority.education-canonical-bonus.enabled` 전역 boolean 1개로 고정했다. 기본값은 `false` 이고, effect scope 는 계속 narrow candidate/user 조건이 담당하게 두며, flag 는 실험 전체를 켜고 끄는 역할만 맡긴다
- 이유: bridge candidate 첫 실험은 “효과가 있는가”를 보는 단계이지 rollout 시스템을 만드는 단계가 아니다. 이미 좁은 실험에 control plane까지 복잡하게 얹으면 rollback과 원인 분석이 더 어려워지므로, 가장 작은 boolean toggle부터 쓰는 편이 안전하다

## 193) 이 정도로 좁은 bridge 실험에 config class나 experiment service까지 먼저 만들면 실제 실험 범위보다 제어 구조가 더 커져 제거 비용만 늘어남
- 문제: `교육 -> 교육·직업훈련` 실험을 코드에 넣을 때, 별도 `@ConfigurationProperties` 객체나 `ExperimentPolicyService` 같은 공용 레이어를 먼저 만들고 싶어질 수 있다. 하지만 현재 실험은 key 1개, boolean 1개, read site 1곳만 필요해 제어 구조를 먼저 키우면 오히려 “좁은 검증”이 “정식 시스템 계약”처럼 굳어질 위험이 있다
- 해결: config/helper 경계를 `RuleScoringService` 내부 `@Value` boolean 주입 + private helper 1개로 고정했다. `DefaultPriorityMatcher`, repository, facade 계층에는 flag branching 을 퍼뜨리지 않고, 실제 bonus 적용 여부 판단도 scoring 레이어 안에서만 닫히도록 정리했다
- 이유: bridge candidate 첫 실험은 가장 작은 diff와 가장 쉬운 rollback 이 중요하다. 토글이 한 군데서만 읽히면 영향 범위와 제거 비용을 같이 낮출 수 있고, 이후 실험이 커질 때만 별도 config/service로 승격하면 된다

## 194) narrow priority experiment는 “점수가 달라졌다”만 보면 안 되고, non-target sample 안정성과 response semantics 불변까지 같이 봐야 함
- 문제: `교육 -> 교육·직업훈련` 같은 좁은 bonus 실험은 target row가 위로 올라오기만 하면 성공처럼 보일 수 있다. 하지만 실제로는 non-target sample 까지 흔들리거나, `RecommendationResponse.unifiedCategory` / `aiReason` 의미가 같이 변하면 실험 범위를 넘은 부작용인데도 놓치기 쉽다
- 해결: 검증 기준을 `flag off/on 동일 snapshot 비교`, `target sample 1개 이상 + control sample 1개 이상`, `response unifiedCategory / aiReason 불변` 조건으로 문서화했다. 즉 top-N 개선만이 아니라 “비대상 안정성”과 “응답 의미 불변”까지 함께 통과해야 실험 계속 진행으로 본다
- 이유: 이 실험은 category contract를 바꾸는 작업이 아니라 scoring bonus 하나를 좁게 여는 작업이다. 따라서 검증도 target gain과 non-target safety를 함께 봐야 하고, response semantics가 흔들리면 그건 이미 다른 종류의 변경이다

## 195) 문서 task 중 `git commit` 직전에 `.git/index.lock` 충돌이 나면 바로 지우기보다 실제 git 프로세스 존재 여부부터 확인해야 함
- 문제: 이번 문서 작업 커밋 단계에서 `fatal: Unable to create '.git/index.lock': File exists.` 가 한 번 발생했다. 이런 경우 습관적으로 lock 파일을 바로 지우면, 실제로 다른 git 프로세스가 아직 돌고 있을 때 index 손상 위험이 있다
- 해결: 먼저 `ps -ef | rg "git (commit|add|status|push)"` 로 실제 git 프로세스 유무를 확인하고, 이어 `.git/index.lock` 존재 여부를 다시 확인했다. 이번 경우는 이미 transient 상태로 정리돼 있었고 재시도만으로 진행 가능했다
- 이유: `index.lock` 은 stale lock일 수도 있지만 active git 작업 보호 장치일 수도 있다. 먼저 프로세스 유무를 확인하면 불필요한 강제 삭제를 피하고, 안전하게 재시도 여부를 판단할 수 있다

## 196) `교육` bridge 실험을 `calcBaseScore(...)` 나 matcher 쪽에 넣지 말고 `applyPriorityWeight(...)` filter 경계에서만 좁게 OR 하는 편이 diff와 회귀 반경이 가장 작음
- 문제: 실제 구현에 들어가면 helper를 `calcBaseScore(...)`, `DefaultPriorityMatcher`, facade, repository 등 여러 곳에 흩뿌리고 싶어질 수 있다. 하지만 이 실험은 category contract 변경이 아니라 `EDUCATION priority` 에 한해 weight candidate를 하나 더 인정하는 수준이라, 경계를 넓히면 실험보다 코드 영향 범위가 더 커질 수 있다
- 해결: helper 이름은 `matchesEducationCanonicalPriorityExperiment(...)` 로 고정하고, 삽입 위치도 `RuleScoringService.applyPriorityWeight(...)` 의 `priorities.stream().filter(...)` 경계로 제한했다. 구현 권장 형태는 `priorityMatcher.matches(...) || helper(...)` 이고, 나머지 scoring slot은 건드리지 않는다
- 이유: 현재 priority 가중치는 `match -> maxWeight -> base * maxWeight` 구조로 닫혀 있다. 이 구조에서 filter 조건만 좁게 확장하면 실험의 의미가 가장 잘 보존되고, regression test도 matcher/response 의미를 건드리지 않은 채 최소 범위로 작성할 수 있다

## 197) narrow priority experiment replay는 app flag만 바꿔 재기동해야지, 중간에 collect/backfill/user 변경을 끼우면 flag 효과와 데이터 변화가 섞여 버림
- 문제: `교육 -> 교육·직업훈련` 실험을 검증할 때 `flag off` 와 `flag on` 사이에 collect 재실행, sidecar backfill, user priority 수정, score weight 변경까지 같이 하면 top-N 차이가 생겨도 원인이 flag인지 데이터 변화인지 분리하기 어렵다
- 해결: replay 절차를 `flag off 앱 기동 -> sample A/B refresh -> flag on 앱 재기동 -> 같은 sample A/B refresh` 순서로 고정했다. 비교 사이에는 collect/backfill/user 수정/score weight 변경을 금지하고, 같은 DB snapshot / 같은 user snapshot 유지까지 명시했다
- 이유: 이 replay는 데이터 품질 재검증이 아니라 scoring bonus 실험 효과만 보는 절차다. 따라서 바뀌는 변수는 flag 하나여야 하며, control sample까지 같이 보는 편이 non-target 회귀를 빠르게 잡기 쉽다

## 198) canonical bridge 실험 helper만 넣고 read-model hydrate에 `youthMajorLabel`을 안 실어 주면 flag를 켜도 실험이 영원히 비활성처럼 보일 수 있음
- 문제: 이번 `교육 -> 교육·직업훈련` narrow experiment는 `RuleScoringService` 의 helper가 `compat=기타 + youth_major=교육` 을 읽어야 동작한다. 그런데 retrieval이 sidecar projection에서 `youthMajorLabel` 을 hydrate 하지 않으면 helper 입력이 항상 `null` 이라, flag를 켜도 “실험이 먹지 않는” 조용한 no-op 상태가 될 수 있다
- 해결: `RecommendationCandidateProjection` / `CanonicalRecommendationReadModelRepository` 에 `youthMajorLabel` 을 추가하고, `RuleScoringServiceTest` 와 repository 테스트로 `youthMajorLabel` hydrate + `flag off/on`, `education / non-education` 회귀를 같이 고정했다
- 이유: canonical bridge 실험은 flag 토글보다 먼저 read-model 입력이 실제로 scoring 경계까지 도달해야 의미가 있다. 실험 helper와 hydrate 경로를 같은 PR에서 같이 검증해야 “flag는 켰는데 왜 변화가 없지?” 같은 묵묵한 실패를 줄일 수 있다

## 199) narrow priority replay가 off/on 동일하게 끝났다고 바로 helper/flag 실패로 보면 안 되고, 먼저 target row가 실제 결과 집합에 들어왔는지 확인해야 함
- 문제: 이번 local replay에서 `flag off` 와 `flag on` 결과가 sample A/B, C/D 모두 동일하게 나왔다. 이때 helper 구현이나 config wiring이 잘못됐다고 바로 판단하면, 실제 원인이 `compat=기타 + youth_major=교육` target row가 retrieval/result set에 전혀 들어오지 않은 sample miss였다는 점을 놓칠 수 있다
- 해결: replay 절차에 `sample A` 는 DB inventory 상 후보 존재가 아니라 실제 `POST /api/recommendations/refresh` 결과 집합 안에 target row가 최소 1건 들어오는 사용자여야 한다는 preflight를 추가했다. 이번 결과는 “실험 무해성”까지만 통과로 기록하고, 후속 작업을 `target row replay sample inventory 재작성` 으로 분리했다
- 이유: narrow priority experiment는 scoring bonus를 추가하는 구조라, target row가 candidate/result set에 없으면 flag를 켜도 반드시 no-op다. 이 경우 구현 실패와 sample selection 실패를 구분하지 않으면 잘못된 디버깅으로 이어진다

## 200) `교육` target row inventory를 많이 찾았다고 곧바로 positive replay sample이 생기는 건 아니고, `0/0 age` 같은 retrieval gate 때문에 DB pool과 실제 결과 집합 사이에 큰 단절이 생길 수 있음
- 문제: local DB에는 `compat=기타 + youth_major=교육` row가 `102`건 있고, 일부 region은 age-pass target pool도 `9~12`건씩 있었다. 하지만 `16 regions × 2 ages` replay scan에서 결과 집합 hit는 전부 `0` 이었다. 단순히 “target row가 region에 많다”는 inventory만 보면 다음 sample을 더 찍으면 될 것처럼 보이지만, 실제로는 `min_age=0 AND max_age=0` literal gate와 candidate/result 조성 경계 때문에 DB pool과 결과 집합 사이가 크게 끊겨 있었다
- 해결: 별도 inventory 문서로 `target_total / age-pass / zero-zero` 분포와 replay scan 결과(`32`조합 전부 `target_hits=0`)를 같이 고정했다. 동시에 다음 작업도 `sample 추가 탐색`이 아니라 `RetrievalService -> candidate pool -> youth filter -> final saved recommendations` 경계 추적으로 승격했다
- 이유: narrow experiment는 target row가 결과 집합에 들어와야만 효과를 볼 수 있다. DB inventory만 보고 sample을 더 찍는 방식은 비용만 들고 원인 분리에 도움이 적으므로, gate/후처리 경계를 먼저 추적하는 편이 더 정확하다

## 201) `교육` target row는 age-pass pool이 있어도 `YOUTH min_income/max_income = 0/0` semantics 때문에 repository candidate 단계에서 이미 전부 빠질 수 있음
- 문제: `16 regions × 2 ages` replay scan에서 `target_hits=0` 이 계속 나와서 처음엔 youth filter나 rerank 문제처럼 보일 수 있었다. 하지만 representative region을 직접 쿼리해 보니 age-pass target row는 존재하는데, 모두 `source_type=YOUTH + min_income=0 + max_income=0` 이어서 `incomeLevel=5` sample에선 repository `findCandidatesWithRegionCode(...)` 조건 `min_income <= 5 AND max_income >= 5` 를 하나도 통과하지 못했다
- 해결: local DB에서 `age-pass target pool > 0 && income5-pass = 0` 인 representative region을 동적으로 고른 뒤, raw repository candidate hit와 `RetrievalService` hit가 모두 `0` 인 걸 integration test로 고정했다. 후속 작업도 broad candidate composition 추적에서 더 좁혀 `YOUTH 0/0 income semantics 결정`으로 재정의했다
- 이유: scoring/priority 실험을 계속 보기 전에, retrieval query가 canonical target row를 애초에 후보로 올릴 수 있는지부터 확인해야 한다. 특히 `0/0` 같은 source normalization 잔여값은 “미지정”인지 “실제 gate”인지 해석 정책이 없으면 추천 실험 이전 단계에서 후보를 모두 잃게 된다

## 202) `YOUTH min_income/max_income = 0/0` 는 user input 계약상 실제 소득 gate보다 미지정 sentinel로 보는 편이 맞고, retrieval 에서는 `NULL/NULL` 과 같은 pass-through 로 해석해야 함
- 문제: 온통청년 row 대부분(`2269 / 2299`)이 `min_income=0 AND max_income=0` 인데, 현재 retrieval SQL은 이 값을 그대로 비교해 `incomeLevel=5` 같은 일반 사용자에게서 거의 전부 탈락시킨다. 하지만 사용자 입력 계약은 이미 `1~10` 분위만 허용하므로, `0/0` 을 “실제 0분위 전용”으로 읽는 해석은 source 전체를 비정상적으로 축소시키는 결과가 된다
- 해결: `0/0` 은 retrieval 에서 `NULL/NULL` 과 같은 “미지정/pass-through” sentinel로 해석하고, 저장값 자체는 당장 바꾸지 않되 `WelfareServiceRepository.findCandidates* / findLatestCandidates*` query semantics 먼저 수정하는 정책을 문서로 고정했다
- 이유: 이 문제는 scoring이나 priority보다 앞단의 candidate composition 문제다. source 대부분을 retrieval 단계에서 잃어버리면 이후 canonical bridge 실험과 read-model 검증까지 전부 왜곡되므로, 먼저 query semantics를 바로잡는 편이 맞다

## 203) `YOUTH 0/0 income` 같은 retrieval semantics 변경은 문서 정책만 정하고 끝내면 안 되고, repository hit와 `RetrievalService` hit를 같이 고정해야 재발을 막을 수 있음
- 문제: `0/0 => pass-through` 정책을 문서로만 정한 상태에서는 실제 JPQL이 그대로 남아 있어도 겉으로는 합의가 끝난 것처럼 보일 수 있다. 특히 이번 `교육` 실험처럼 target row가 scoring 이전 repository 단계에서 이미 사라지는 경우, query semantics 미반영을 helper/flag 문제로 오해하기 쉽다
- 해결: `WelfareServiceRepository.findCandidates* / findLatestCandidates*` 6개 query 모두에 `source_type=YOUTH AND min_income=0 AND max_income=0` direct pass-through 조건을 실제로 넣고, representative region integration test를 `raw repository candidate hit > 0` 과 `RetrievalService hit > 0` 두 단계 모두 검증하는 형태로 뒤집어 고정했다
- 이유: candidate composition 문제는 문서 결정과 구현 반영 사이에 가장 쉽게 drift가 난다. repository hit와 retrieval hit를 같이 묶어 두면 “정책은 맞는데 후보가 안 올라오는” 조용한 실패를 빠르게 잡을 수 있다

## 204) host `bootRun` 기반 recommendation replay smoke는 추천 로직과 무관한 `AES/PII datasource` 전제가 비어 있어도 signup 단계에서 바로 깨질 수 있으니, sample 비교 전에 런타임 전제를 먼저 고정해야 함
- 문제: 이번 representative region replay를 다시 태울 때 처음 시도는 `AES_SECRET_KEY` 가 비어 있어 signup 이 `AES encrypt failed(Empty key)` 로 500을 냈고, 이어 root 계정으로 PII datasource 에 붙으려다 `user_pii access denied` 로그도 함께 발생했다. 이 상태에선 `flag off/on` 차이가 아니라 sample seed 자체가 불안정해져 추천 실험 결과를 해석할 수 없다
- 해결: host `bootRun` 전제를 `AES_SECRET_KEY` 명시, `DB_URL/APP_PII_DB_URL/NOTIFICATION_PII_DB_URL` host override, `REDIS_HOST=127.0.0.1`, split-account PII 계정(`app_pii_rw`, `notification_pii_ro`) 사용으로 고정했다. 그 뒤 representative sample `28110 / age25 / income5 / priority=EDUCATION` 으로 replay를 다시 실행해 sample A 는 top-10 target row `5 -> 10`, sample B(control)는 unchanged 를 확인했다
- 이유: local replay smoke는 scoring 실험이 본체여도, 실제 실패 지점은 그보다 앞선 runtime wiring일 수 있다. 같은 known positive sample을 계속 재현하려면 추천 helper가 아니라 app 기동 전제부터 먼저 고정해 두는 편이 재발 방지에 더 효과적이다

## 205) known positive sample이 생긴 뒤에는 수동 curl 재조립보다 `flag off/on + sample seed + top-10 비교`를 한 스크립트로 묶어 두는 편이 regression 반복 비용을 훨씬 줄임
- 문제: `28110 / age25 / income5 / priority=EDUCATION` known positive sample을 찾은 뒤에도, 매번 `bootRun` 두 번 기동, sample A/B login, profile/priorities 정렬, refresh, target row count 계산을 수동으로 다시 치면 quoting/토큰 추출/전제 누락 같은 비본질 오류가 먼저 끼어든다
- 해결: `deploy/smoke/run-local-education-priority-replay.sh` 초안을 추가해 `db/redis ensure -> flag off/on bootRun -> sample A/B refresh -> target row top-10 diff/control stability assert` 를 한 번에 실행하도록 묶었다. 스크립트는 artifact dir 에 결과 JSON과 app log를 남겨 후속 비교에도 재사용할 수 있게 했다
- 이유: 이 smoke는 이제 “sample을 찾는 절차”가 아니라 “known positive sample을 기준으로 bridge regression을 재확인하는 절차”다. 반복성이 핵심이므로, 수동 문서보다 실행 가능한 스크립트로 고정하는 편이 더 실무적이다

## 206) host `bootRun` off/on replay를 자동화할 때는 이전 앱이 완전히 내려가기 전에 다음 phase health를 보면 false-positive startup success가 생길 수 있고, control sample exact top-10 불변도 local snapshot에선 과도하게 strict할 수 있음
- 문제: 첫 script run은 두 번째 `bootRun` 전에 이전 `off` 앱이 아직 health `UP` 인 순간을 `on` phase 준비 완료로 오인해 `sample B` refresh에서 바로 connection refused가 났다. 이어 race를 막은 뒤에는 control sample의 exact top-10 id 비교가 local snapshot에서 계속 깨졌는데, 실제 수치를 보면 `sample A` target row top-10 은 `1 -> 7`로 개선되지만 `sample B` 는 target row top-10 count `0 -> 0` 을 유지한 채 동점권 reorder만 남는 경우가 있었다
- 해결: `deploy/smoke/run-local-education-priority-replay.sh` 에 `wait_for_app_down()` 과 health artifact reset을 넣어 phase 전환 race를 제거했다. 또 기본 검증은 `sample A improvement` 만 hard assert 하고, control sample은 `target row top-10 count` 를 출력/보존하되 exact-top10 drift는 warning-by-default로 남기고 `STRICT_CONTROL_ASSERT=true` 일 때만 strict fail 하도록 바꿨다
- 이유: local replay smoke의 1차 목적은 known positive sample에서 canonical `교육` bridge가 실제로 살아 있는지 빠르게 재검증하는 것이다. control sample exact ordering은 tie/reorder 영향이 크므로 기본 자동화 조건으로 두기보다, artifact를 남기고 필요 시 strict mode로 재현하는 편이 더 실용적이다

## 207) sample B(control)의 `finalScore` drift는 current snapshot 기준으로 helper 오작동보다 request-local score normalization 영향으로 보는 쪽이 더 타당함
- 문제: latest replay artifact를 뜯어보면 sample B는 top-10 id set과 target row top-10 count `0 -> 0` 은 유지되는데도, 주거 row 몇 개의 `finalScore` 가 `±0.02 ~ ±0.06` 수준으로 바뀌며 순서가 흔들렸다. 이걸 그대로 “education helper가 control sample에도 잘못 발동했다”로 해석하면 원인 지점을 잘못 잡을 수 있다
- 해결: sample B의 latest artifact(`/tmp/tmp.pKVwRY4dlt`)를 기준으로 top-10 delta를 다시 분해하고, [policy-normalization-education-control-drift-analysis.md](../history/ai/policy-normalization-education-control-drift-analysis.md)에 결과를 고정했다. 현재 판단은 [ReRankingService.java](/home/minseok/youth-welfare/backend/src/main/java/com/example/welfare/recommend/service/ReRankingService.java)의 `normalize(ruleWeightedScore, 0, ruleMax)` request-local 정규화가 더 유력한 원인이라는 점이며, 그래서 local smoke 기본 모드는 계속 warning-by-default를 유지한다
- 이유: control sample에서 exact top-10/`finalScore` 불변을 hard gate로 두기 전에, drift가 target row 유입 때문인지 normalization 재스케일링 때문인지 먼저 분리해야 한다. 그래야 다음 작업을 helper 수정이 아니라 `ruleWeightedScore` snapshot 검증이나 normalization 안정화 검토 쪽으로 올바르게 이어갈 수 있다

## 208) sample B(control)를 단독 direct replay 했을 때 `ruleWeightedScore` / `finalScore` snapshot이 `off/on` 동일하면, 이전 drift 가설은 full replay context로 다시 한정해서 봐야 함
- 문제: `sample B finalScore drift -> normalization 영향` 가설을 세운 뒤 실제로 `ruleWeightedScore` snapshot까지 확인하지 않으면, 잘못된 가설을 문서에 굳힐 수 있다
- 해결: same sample B(`education.replay.afterincome.b@example.com`)로 `flag off` / `flag on` host `bootRun` 을 각각 띄우고, `POST /api/recommendations/refresh` 직후 `user_recommendations` top-15를 `/tmp/edu-control-off.tsv`, `/tmp/edu-control-on.tsv` 로 직접 덤프했다. 결과는 두 파일이 완전히 동일했고, response JSON top-15도 동일했다. 이 결과는 [policy-normalization-education-control-ruleweighted-snapshot.md](../history/ai/policy-normalization-education-control-ruleweighted-snapshot.md)에 고정했다
- 이유: direct snapshot 기준으로 drift가 안 보이면, 이전 artifact drift는 helper 자체나 `ruleWeightedScore` 단계보다는 `sample A -> sample B` 순서가 포함된 full replay 문맥에서 다시 재현/분석해야 한다. 가설과 사실을 분리해 두는 편이 다음 디버깅 경계를 더 정확하게 잡는다

## 209) full replay script에서 `user_recommendations.rule_weighted_score` snapshot을 같이 남기면 drift가 점수 어느 층위에서 생겼는지 바로 분리할 수 있음
- 문제: direct snapshot에서는 sample B `off/on` 차이가 없었지만, full replay artifact에선 여전히 `final_score` drift가 남아 있었다. 이 상태에서 response JSON만 보면 drift가 helper 때문인지, `ruleWeightedScore` 때문인지, normalization 때문인지 또 추측으로 돌아가게 된다
- 해결: `deploy/smoke/run-local-education-priority-replay.sh` 가 sample A/B 각각의 `edu-*-off-scores.tsv`, `edu-*-on-scores.tsv` 를 artifact로 같이 남기도록 바꿨다. 최신 artifact에서는 `rule_weighted_score`, `rule_weight_used`, `ai_weight_used` 는 그대로인데 `ai_score` 와 `final_score` 가 함께 달라졌고, 이 결과를 [policy-normalization-education-control-drift-analysis.md](../history/ai/policy-normalization-education-control-drift-analysis.md) 와 [policy-normalization-education-control-ruleweighted-snapshot.md](../history/ai/policy-normalization-education-control-ruleweighted-snapshot.md)에 반영했다
- 이유: replay 스크립트가 response와 persisted score snapshot을 동시에 남기면, 다음 분석은 “왜 drift가 생겼는가” 하나만 보면 된다. 즉 디버깅 경계가 helper -> weighted score -> normalized final score 순서로 더 짧고 명확해진다

## 210) full replay context에서 `rule_weighted_score` 가 그대로인데 `ai_score` 가 바뀌면, 다음 디버깅 경계는 normalization이 아니라 AI score layer여야 함
- 문제: direct sample B replay는 `off/on` 동일했고, full replay artifact에서도 `rule_weighted_score` / `rule_weight_used` / `ai_weight_used` 는 그대로였다. 그런데 sample B 일부 row는 `ai_score` 가 `85 -> 80`, `75 -> 70`, `70 -> 80` 식으로 바뀌며 `final_score` 도 같이 흔들렸다. 이 상태에서 계속 `ruleMax` 나 request-local normalization만 파면 원인 경계를 잘못 잡게 된다
- 해결: score snapshot export 컬럼을 `ai_score`, `rule_weight_used`, `ai_weight_used` 까지 넓히고, latest artifact(`/tmp/tmp.Yhv9aqgHiM`) 기준 drift가 `AI score layer` 에서 이미 발생한다는 결론으로 문서와 phase plan을 갱신했다. 다음 작업도 `RealtimeAiGateway` / persistence path 추적으로 옮겼다
- 이유: 같은 weighted score와 같은 weight 비율 위에서 `ai_score` 만 바뀌면, normalization은 결과 증폭 요인일 수 있어도 최초 drift source는 아니다. 따라서 디버깅은 가장 먼저 달라진 층위에서 시작해야 한다

## 211) `rule-only replay` 스크립트가 `.env` 의 real `OPENAI_API_KEY` 를 상속하면 AI drift 분석 자체가 오염됨
- 문제: local `.env` 에 real `OPENAI_API_KEY` 가 non-empty 인 상태에서 replay 스크립트가 `OPENAI_API_KEY="${OPENAI_API_KEY:-invalid-for-rule-only-replay}"` 만 쓰고 있으면, 이름/문서상으론 `rule-only replay` 여도 실제론 real OpenAI 호출이 섞인다. 그러면 full replay artifact의 `ai_score` drift가 helper/normalization bug 인지, live AI 응답 변동인지 분리할 수 없다
- 해결: 스크립트 기본값을 `USE_REAL_OPENAI_FOR_REPLAY=false` 로 고정하고, 이 경우 `.env` 값과 상관없이 항상 `OPENAI_API_KEY=invalid-for-rule-only-replay` 를 강제하도록 수정했다. real AI 호출은 `USE_REAL_OPENAI_FOR_REPLAY=true` 로만 opt-in 하게 바꾸고, artifact에는 `openai-mode.txt` 를 같이 남긴다
- 이유: `rule-only` smoke의 목적은 AI layer를 배제한 채 rule/priority 변화만 비교하는 것이다. real AI를 보고 싶다면 그것도 하나의 별도 실험이므로, 같은 스크립트 안에서도 mode를 명시적으로 분리해야 결과 해석이 섞이지 않는다

## 212) `rule-only-invalid-key` 기본 모드에서 sample B drift가 사라지면, 이전 `ai_score` drift artifact는 real OpenAI 문맥으로 재분류해야 함
- 문제: 이전 artifact에서는 sample B의 `rule_weighted_score` 는 그대로인데 `ai_score` / `final_score` 가 흔들려서 `RealtimeAiGateway` / persistence path를 더 파야 하는 상태처럼 보였다. 하지만 그 artifact가 사실상 real OpenAI 호출을 포함한 문맥이었다면, 같은 현상을 rule-only 디버깅 경계로 계속 해석하면 안 된다
- 해결: default rule-only mode(`openai-mode.txt=rule-only-invalid-key`)로 replay script를 다시 실행했고, artifact(`/tmp/tmp.x4i74TN5Wv`)에서 sample B `edu-b-off-scores.tsv` / `edu-b-on-scores.tsv` diff가 사라지는 것을 확인했다. 이제 이전 `ai_score` drift artifact는 `real-openai` mode에서만 관찰된 현상으로 재분류하고, 다음 작업도 intentional real OpenAI replay 재현으로 좁혔다
- 이유: 동일 스크립트라도 mode가 다르면 해석해야 하는 문제 종류가 달라진다. default rule-only mode가 안정화됐으면, 남은 drift는 core recommendation bug가 아니라 live AI variability / gateway behavior 실험으로 분리하는 편이 맞다

## 213) `real-openai` mode에서 drift가 다시 재현되면, 다음 경계는 `RealtimeAiGateway` 입력 drift와 live AI nondeterminism 구분이어야 함
- 문제: default rule-only mode에선 sample B score diff가 사라졌지만, `USE_REAL_OPENAI_FOR_REPLAY=true` 로 intentional replay를 다시 태운 artifact(`/tmp/tmp.EZBH319uNA`)에서는 sample B top-10 target row가 `1 -> 0` 으로 줄고 `edu-b-off-scores.tsv` / `edu-b-on-scores.tsv` 의 `ai_score` / `final_score` drift도 재현됐다. 이 상태에선 “real-openai mode에서 drift가 실제로 남는가”는 확인됐지만, 그 원인이 `RealtimeAiGateway` 로 넘어가는 topCandidates/prompt ordering 변화인지, 같은 입력에서도 생기는 live AI 응답 변동인지 아직 분리되지 않았다
- 해결: phase-plan 다음 작업을 `RealtimeAiGateway` 입력 증적(topCandidates id/order, prompt hash 또는 prompt dump) export 추가로 좁혔다. 먼저 입력이 같은지부터 확인해야 AI 응답 변동과 입력 drift를 구분할 수 있다
- 이유: AI 연동 문제는 “입력이 달랐는지”와 “같은 입력인데 출력이 달랐는지”를 분리하지 않으면 디버깅이 길어진다. persisted score snapshot만으로는 후자를 단정할 수 없으므로, 다음 단계는 gateway 입력 증적 확보가 맞다

## 214) `RealtimeAiGateway` 입력 증적을 artifact로 남기면 `input drift` 와 `AI nondeterminism` 을 분리할 기준점이 생김
- 문제: persisted `ai_score` / `final_score` snapshot만으로는 sample B drift가 “입력이 달라서”인지 “같은 입력인데 AI가 달리 답해서”인지 분리할 수 없다
- 해결: `RealtimeAiGateway` 가 `candidateIds`, `candidateRuleScores`, `promptSha256` 를 `[replay-trace]` 로그로 남기고, replay 스크립트가 이를 `edu-a/b-*-ai-trace.log` artifact로 추출하도록 추가했다. default rule-only artifact(`/tmp/tmp.BGxKfcPnvT`) 기준 sample B `off/on` trace는 완전히 동일했다
- 이유: 먼저 안정한 기준점이 하나 있어야 real-openai mode에서 어떤 층이 처음 달라졌는지 비교할 수 있다. rule-only mode에서 input trace가 같다면, 이후 real-openai mode 차이는 입력 drift인지 live AI 응답 변동인지 더 좁혀 볼 수 있다

## 215) `real-openai` mode에서 `candidateIds` / `promptSha256` 가 같아도 `ai_score` 가 달라지면, 다음 경계는 입력 drift가 아니라 live AI 응답 변동성이다
- 문제: trace export를 켠 intentional real-openai replay artifact(`/tmp/tmp.WoIyHuKtMd`)에서 sample B `edu-b-off-ai-trace.log` / `edu-b-on-ai-trace.log` 는 `candidateIds=356,399,403,404,405,407,359,364,365,371,375,381,383,384,390`, `promptSha256=f7e810...` 로 완전히 동일했다. 그런데도 `edu-b-off-scores.tsv` / `edu-b-on-scores.tsv` 의 `ai_score` 와 `final_score` 는 다시 달라졌고 top-10 target row도 `0 -> 1` 로 바뀌었다
- 해결: phase-plan 다음 작업을 `prompt/input drift 추적`에서 `같은 입력에서도 생기는 live AI 응답 변동성 완화 전략(temperature, seed 지원 여부, cache/replay 방식)` 검토로 다시 좁혔다
- 이유: 동일 trace인데 결과가 달라졌다면, 더 이상 retrieval/prompt 조성 버그를 먼저 의심할 단계는 아니다. 이제는 live model nondeterminism을 제품/운영 관점에서 어떻게 다룰지로 넘어가야 한다

## 216) `real-openai` replay drift 대응에선 prompt caching 보다 `seed + system_fingerprint` 증적이 먼저다
- 문제: same `candidateIds` / same `promptSha256` 인데도 `ai_score` 가 달라지는 상태에서, latency/cost용 기능과 determinism 보조 기능을 구분하지 않으면 대응 우선순위가 흐려진다
- 해결: 공식 OpenAI 문서 기준으로 선택지를 다시 정리했고, [openai-replay-stability-options.md](../history/ai/openai-replay-stability-options.md)에 `seed` 는 best-effort determinism 수단, `system_fingerprint` 는 backend 변화 추적용, Prompt Caching 은 latency/cost 최적화용이라는 경계를 고정했다. 다음 구현 우선순위도 optional replay `seed` 와 `system_fingerprint` / response id trace 추가로 좁혔다
- 이유: prompt caching 은 output generation 자체를 안정화하는 기능이 아니므로, 지금 같은 replay drift 분석에는 원인 분리력이 약하다. 반대로 `seed + system_fingerprint` 는 “같은 입력 + 같은 seed + 같은 backend 조건”을 증명하는 최소 증적이어서 다음 디버깅 단계의 정보 가치가 더 높다

## 217) same `promptSha256` 만으로는 부족하고 replay artifact엔 `replaySeed` 와 `system_fingerprint` 도 같이 남겨야 한다
- 문제: real-openai replay에서 `candidateIds` / `candidateRuleScores` / `promptSha256` 가 같아도 `ai_score` drift가 남는다는 것까진 확인했지만, 이 상태만으로는 “같은 seed 조건인지”와 “backend fingerprint 도 같았는지”를 분리할 수 없다
- 해결: `RealtimeAiGateway` request body에 optional `seed` 를 추가하고, request trace에 `replaySeed`, response trace에 `responseId`, `systemFingerprint`, `responseSeed`, `resultsCount` 를 남기도록 확장했다. replay script도 `RECOMMEND_AI_REPLAY_SEED` env와 `edu-a/b-*-ai-response-trace.log` artifact를 추가해 다음 real-openai replay에서 same seed/same fingerprint 조건을 바로 확인할 수 있게 했다
- 이유: determinism 보조 기능을 켰는지와 backend 상태가 같았는지를 먼저 증명해야, 그 다음에야 residual drift를 live model nondeterminism으로 해석할 수 있다. 같은 `promptSha256` 만으로는 그 경계가 아직 부족하다

## 218) same `promptSha256` + same `replaySeed` 인데 `system_fingerprint` 가 바뀌면, 이번 run의 drift 원인은 backend churn과 분리해서 봐야 한다
- 문제: `USE_REAL_OPENAI_FOR_REPLAY=true KEEP_ARTIFACTS=true` 로 replay seed를 고정한 artifact(`/tmp/tmp.hisZmhuvuH`)에서도 sample B `ai_score` drift가 남았다. 하지만 response trace를 보면 request trace는 `promptSha256=f7e810...`, `replaySeed=424242` 로 동일했어도 `systemFingerprint=fp_de7acce317 -> fp_ff247d5857` 로 바뀌었고 `responseId` 도 달랐다
- 해결: 이번 결과는 “same prompt + same seed + same backend” 조건이 아직 성립하지 않은 run으로 분류하고, phase-plan 다음 작업을 same `system_fingerprint` artifact 확보로 다시 좁혔다
- 이유: `seed` 는 best-effort determinism 수단일 뿐이고, OpenAI 문서도 backend 변화는 `system_fingerprint` 로 같이 보라고 안내한다. fingerprint 가 달라진 run까지 한데 묶어 버리면 residual nondeterminism과 backend churn을 구분할 수 없다

## 219) same `promptSha256` + same `replaySeed` + same `system_fingerprint` 에서도 `ai_score` 가 달라지면, 지금 남은 건 live response variability 로 봐야 한다
- 문제: 반복 real-openai replay 끝에 artifact(`/tmp/tmp.TpE5SaiHJu`)에서 sample B `off/on` request trace는 `candidateIds`, `candidateRuleScores`, `promptSha256=f7e810...`, `replaySeed=424242` 가 같고, response trace도 `systemFingerprint=fp_ff247d5857` 로 같았다. 그런데도 `edu-b-off-scores.tsv` / `edu-b-on-scores.tsv` 에서는 `404:85 -> 75`, `405:75 -> 85`, `390:55 -> 70` 같은 `ai_score` diff가 계속 남았다
- 해결: phase-plan 결론을 “same fingerprint artifact 확보”에서 “same fingerprint 안에서도 남는 live response variability 를 제품적으로 어떻게 다룰지 결정”으로 넘겼다. 다음 작업도 strict equality 완화 기준, rule-only 기본선 유지, replay cache 같은 제품 대응 검토로 옮겼다
- 이유: 이 시점부터는 retrieval/prompt drift, backend churn, score normalization을 먼저 의심할 근거가 약하다. 같은 request 조건과 같은 fingerprint에서 결과가 갈리면, 남은 문제는 live model nondeterminism을 검증/운영에서 어떻게 흡수할지다

## 220) same fingerprint 에서도 drift가 남는 단계부터는 `rule-only` 와 `real-openai` 를 같은 pass/fail gate로 두면 안 된다
- 문제: same `promptSha256` + same `replaySeed` + same `system_fingerprint` 조건에서도 `ai_score` drift가 남는다면, `real-openai` replay strict equality를 PR hard gate로 두는 순간 live model variability가 코드 회귀와 같은 수준의 blocker가 된다
- 해결: [openai-replay-validation-policy.md](../history/ai/openai-replay-validation-policy.md)에 현재 정책을 고정했다. `rule-only-invalid-key` replay는 코드 안정성 hard gate로 유지하고, `real-openai` replay는 trace/artifact 완전성을 보는 exploratory gate로 둔다. 즉 `real-openai` strict equality 실패만으로는 PR을 막지 않는다
- 이유: 지금 단계에서 product가 통제 가능한 것은 retrieval/rule/priority/canonical bridge와 artifact quality이지, live model의 미세한 응답 변동 자체는 아니다. 검증선과 관측선을 분리해야 PR gate가 불필요하게 불안정해지지 않는다

## 221) same fingerprint 에서도 `ai_score` 가 흔들리면, `score delta` 는 gate metric보다 설명 지표에 가깝다
- 문제: `/tmp/tmp.TpE5SaiHJu` 같은 same fingerprint artifact에서도 sample B `ai_score` 는 `404:85 -> 75`, `405:75 -> 85`, `390:55 -> 70` 식으로 흔들렸다. 이런 상태에서 `score delta` 자체를 gate로 쓰면 live variability가 바로 fail 조건이 된다
- 해결: [openai-replay-allowed-drift-metrics.md](../history/ai/openai-replay-allowed-drift-metrics.md)에 `real-openai` allowed drift metric 우선순위를 고정했다. 자동 gate는 `top-N target row count` 와 `target row presence/absence` 중심으로 두고, `score delta` 는 artifact 설명용 지표로만 남긴다
- 이유: 이번 실험의 목적은 target 교육 row가 더 잘 보이게 되는지 확인하는 것이다. 점수 exact match는 그 목적과 직접 연결되지 않고, same fingerprint 안에서도 흔들리므로 자동 gate로 쓰기엔 정보 가치보다 노이즈가 크다

## 222) sample B `unexpected target count increase` 는 지금 단계에선 fail 보다 warning 이 더 맞다
- 문제: current real-openai artifact 분포를 보면 sample B는 `/tmp/tmp.WoIyHuKtMd` 에서 `0 -> 1`, `/tmp/tmp.EZBH319uNA` 에서 `1 -> 0`, `/tmp/tmp.TpE5SaiHJu` 에서 `1 -> 1` 처럼 증가/감소/유지를 모두 보였다. 이 상태에서 increase만 fail 로 고정하면 live variability를 코드 회귀로 과대 판정할 위험이 크다
- 해결: [openai-replay-allowed-drift-metrics.md](../history/ai/openai-replay-allowed-drift-metrics.md), [openai-replay-validation-policy.md](../history/ai/openai-replay-validation-policy.md)에 sample B `unexpected increase` 는 현재 warning 으로만 취급하는 정책을 고정했다. hard gate는 계속 `rule-only` 와 trace/artifact 완전성에 둔다
- 이유: sample B control drift 자체가 same fingerprint 안에서도 흔들리는 상태라면, count increase 하나만 fail 조건으로 쓰는 건 비대칭적이다. 지금은 “관측 신호”로 남기고 artifact review로 연결하는 쪽이 더 안정적이다

## 223) sample B warning은 `same fingerprint` run에만 한정하지 말고, 모든 `real-openai` replay에서 같은 규칙으로 띄우는 편이 낫다
- 문제: same fingerprint 여부는 drift 원인을 좁히는 데는 중요하지만, warning 자체를 그 조건에만 묶어 버리면 `different fingerprint` run에선 control sample 이상 징후를 놓치게 된다
- 해결: [openai-replay-allowed-drift-metrics.md](../history/ai/openai-replay-allowed-drift-metrics.md), [openai-replay-validation-policy.md](../history/ai/openai-replay-validation-policy.md)에 sample B `unexpected target count increase` warning은 모든 `real-openai` replay에서 동일하게 띄우고, `systemFingerprint` 동일 여부는 warning 이후 triage 정보로만 쓰는 정책을 반영했다
- 이유: warning 조건은 “control sample 이상 징후가 있었는가”를 알려주는 1차 신호이고, fingerprint는 그 다음 분석 단계다. 둘을 섞으면 조건이 복잡해지고 운영자가 artifact를 다시 볼 타이밍을 놓치기 쉽다

## 224) `same fingerprint` 여부는 replay summary에 바로 찍어 주는 편이 triage 속도가 더 빠르다
- 문제: warning은 모든 `real-openai` replay에 동일하게 띄우기로 했지만, 매번 artifact를 열어 `systemFingerprint` 를 확인해야 하면 `backend churn` 여부 판단이 느려진다
- 해결: `deploy/smoke/run-local-education-priority-replay.sh` summary가 `A_FINGERPRINT ... same|different`, `B_FINGERPRINT ... same|different` 를 같이 출력하도록 바꿨다. warning 조건은 그대로 두고, fingerprint relation은 stdout summary에서 바로 보이게 분리했다
- 이유: warning 발생 여부와 warning 해석 근거를 섞지 않으면서도, 운영자가 `same fingerprint` / `different fingerprint` 를 한눈에 확인할 수 있다. summary-only triage 라벨이 가장 단순하다

## 225) replay summary는 긴 top10 dump보다 `SUMMARY_METRIC` 한 줄을 먼저 보여주는 편이 판단 속도가 빠르다
- 문제: 기존 summary는 top10 row dump가 먼저 나와서, 실제 gate에 쓰는 `sample A/B top10 target count` 와 fingerprint relation을 눈으로 빨리 찾기 어려웠다
- 해결: `deploy/smoke/run-local-education-priority-replay.sh` summary 상단에 `SUMMARY_METRIC A_top10_target=... B_top10_target=... A_target_total=... B_target_total=... A_fp=... B_fp=...` 한 줄을 추가했다
- 이유: replay smoke의 1차 판단값은 상세 row보다 target count/fingerprint relation이다. gate metric을 먼저 보고, 필요할 때만 아래 top10 dump를 읽는 구조가 더 빠르고 일관된다

## 226) same fingerprint 안에서도 live variability가 남는 동안 `real-openai` replay는 PR hard gate보다 nightly/diagnostic lane으로 분리하는 편이 맞다
- 문제: same `promptSha256` + same `replaySeed` + same `systemFingerprint` 조건에서도 `ai_score` / `final_score` drift가 남는데, 이 replay를 PR hard gate에 그대로 두면 live model variability가 코드 회귀와 같은 blocker가 된다
- 해결: [openai-replay-validation-policy.md](../history/ai/openai-replay-validation-policy.md) 와 [policy-normalization-education-priority-replay-procedure.md](../history/ai/policy-normalization-education-priority-replay-procedure.md)에 `rule-only-invalid-key` 는 PR hard gate, `real-openai` replay는 nightly/diagnostic 또는 수동 triage lane이라는 운영 경계를 명시했다
- 이유: 지금 제품이 통제할 수 있는 것은 deterministic한 non-AI 경계와 trace/artifact 품질이지, live OpenAI 응답의 미세한 변동 자체는 아니다. 검증선과 진단선을 분리해야 PR gate가 과민해지지 않는다

## 227) `real-openai` nightly lane은 repo 기본 CI보다 secret-bearing diagnostic runner 쪽에 붙이는 편이 맞다
- 문제: 현재 repo에는 `.github/workflows` 도 없고, `real-openai` replay는 OpenAI secret, local DB/Redis, artifact retention 을 함께 요구한다. 이걸 기본 PR CI에 바로 얹으면 secret 범위와 flaky surface가 같이 커진다
- 해결: 당시 `openai replay diagnostic lane plan` 문서에 현재 권장 실행 위치를 `manual diagnostic first, scheduled diagnostic later` 로 고정하고, future 자동화 후보를 `self-hosted GitHub Actions runner` 또는 `ops cron host` 로 한정했다
- 이유: 지금 필요한 건 deterministic PR gate가 아니라 별도 secret-bearing 진단 lane이다. 기본 CI와 같은 lane에 두는 것보다, 격리된 runner/host에서 artifact 중심으로 돌리는 편이 운영/보안/노이즈 측면에서 더 안전하다

## 228) `.github/workflows` 가 아직 없고 host 기반 smoke 절차가 이미 있으면, 첫 scheduled diagnostic lane은 self-hosted runner보다 ops cron host가 더 짧은 경로다
- 문제: `real-openai` replay 자동화를 열어야 하지만, 현재 repo는 GitHub Actions workflow 자체가 없고, 바로 self-hosted runner를 붙이면 runner 운영/secret 주입/CI wiring 작업이 먼저 커진다
- 해결: 당시 `openai replay diagnostic lane plan` 문서에 현재 우선순위를 `ops cron host -> self-hosted runner` 로 고정하고, 다음 작업도 cron 주기와 artifact 공유 위치 결정으로 좁혔다
- 이유: 이미 `deploy/smoke/run-local-education-priority-replay.sh` 와 운영 host/compose 중심 문서가 있으므로, periodic diagnostic artifact를 얻는 가장 짧은 경로는 ops host에서 cron으로 먼저 돌리는 것이다. self-hosted runner는 가시성/연동 이점이 있지만 지금 당장 가장 작은 다음 단계는 아니다

## 229) `real-openai` diagnostic replay는 PR마다나 짧은 주기로 반복하기보다, ops host에서 매일 1회 + 필요 시 수동 실행이 더 맞다
- 문제: `real-openai` replay는 비용과 live variability가 있어, 짧은 간격으로 자주 돌릴수록 merge 판단보다 노이즈 수집이 늘어날 수 있다
- 해결: 당시 `openai replay diagnostic lane plan` 문서에 기본 스케줄을 `ops cron host nightly once` 로 두고, 추천/AI 관련 큰 변경 후에만 수동 on-demand replay를 추가하는 정책을 반영했다
- 이유: 지금 목적은 deterministic gate가 아니라 drift 분포 관찰이다. 매일 1회면 fingerprint 분포와 sample A/B target count 변화를 보기엔 충분하고, 운영 부담과 API 비용도 가장 보수적으로 제어할 수 있다

## 230) nightly replay summary는 외부 chat/email보다 host-local append-only file을 1차 채널로 두는 편이 현재 단계에선 더 안전하다
- 문제: nightly `real-openai` replay 결과를 어디로 공유할지 정해야 하지만, 현재 repo/운영 문서에는 Slack, ChatOps, replay 전용 메일 alias 같은 외부 채널 전제가 없다
- 해결: 당시 `openai replay diagnostic lane plan` 문서에 1차 채널을 `ops cron host` 의 append-only summary file로 두고, 상세 증적은 artifact dir에서 확인하는 정책을 반영했다
- 이유: live variability가 남는 diagnostic lane을 외부 알림으로 바로 밀면 false positive가 곧바로 알림 피로로 이어질 수 있다. 먼저 host-local summary file과 artifact dir로 경계를 좁히고, 이후 운영 채널이 준비되면 그때 바깥으로 확장하는 편이 더 안전하다

## 231) nightly replay는 summary와 artifact를 같은 host root 아래 두되, 보존기간은 다르게 가져가는 편이 수동 triage에 유리하다
- 문제: nightly `real-openai` replay를 host-local로 운영하기로 했으면, summary와 artifact를 어디에 두고 얼마나 보관할지 기본값이 없으면 cron 구현 때 경로가 흔들리고 cleanup도 제각각이 된다
- 해결: 당시 `openai replay diagnostic lane plan` 문서에 기본 경로를 `/var/log/youth-welfare/openai-replay/` 아래로 모으고, `nightly-summary-YYYY-MM-DD.log` 는 `30일`, `artifacts/<timestamp>/` 는 `14일` 보관으로 고정했다
- 이유: summary는 drift 추세 비교용이라 더 오래 남겨야 하고, artifact는 상세 triage용이라 용량 대비 보존 가치가 더 빨리 떨어진다. 같은 root 아래 두되 역할별 보존기간을 나누는 편이 운영과 정리에 모두 단순하다

## 232) nightly summary line은 drift 판단에 직접 쓰는 값만 남기고, row-level/response-level 값은 artifact로 보내는 편이 낫다
- 문제: nightly summary file 한 줄에 너무 많은 필드를 넣으면 grep/scan 은 쉬워지지 않고, 오히려 `ai_score`, `responseId`, raw fingerprint 같은 노이즈가 늘어나 첫 판단이 느려질 수 있다
- 해결: 당시 `openai replay diagnostic lane plan` 문서와 [policy-normalization-education-priority-replay-procedure.md](../history/ai/policy-normalization-education-priority-replay-procedure.md)에 summary line 최소 필드를 `ts`, `mode`, `A/B_top10_target`, `A/B_target_total`, `A/B_fp`, `artifact_dir` 로 고정했다
- 이유: 현재 운영 판단은 target count와 fingerprint relation이 먼저고, row-level score/response metadata는 warning 이후 artifact에서 보는 것이 맞다. summary line은 “한 줄 triage” 에 집중해야 한다

## 233) retention cleanup은 replay cron 후단보다 별도 cron으로 분리하는 편이 실패 원인과 정리 책임을 더 깔끔하게 나눈다
- 문제: replay 실행과 cleanup 삭제를 같은 cron 후단에 묶으면, cleanup 실패가 replay 자체 실패처럼 보이거나, replay 실패 시 cleanup이 건너뛰어 보존 정책이 흔들릴 수 있다
- 해결: 당시 `openai replay diagnostic lane plan` 문서에 cleanup을 `별도 daily cleanup cron` 으로 분리하는 정책을 반영했고, phase-plan 다음 작업도 cleanup cron 명령/경로 계약 정리로 좁혔다
- 이유: replay cron은 artifact 생성과 summary append에만 집중하고, cleanup cron은 보존기간 enforcement에만 집중해야 운영자가 실패 원인을 바로 분리할 수 있다. 역할을 나누는 편이 재실행과 디버깅도 단순하다

## 234) nightly summary line format은 문서만으로 두지 말고, 스크립트 env contract로 바로 노출하는 편이 wrapper 구현 때 덜 흔들린다
- 문제: summary line 필드 집합을 문서로만 정하면, 실제 cron wrapper를 만들 때 파일 경로와 timestamp를 어느 env로 줄지 다시 논의하게 되어 계약이 흔들릴 수 있다
- 해결: [run-local-education-priority-replay.sh](/home/minseok/youth-welfare/deploy/smoke/run-local-education-priority-replay.sh)에 `REPLAY_SUMMARY_APPEND_FILE`, `REPLAY_SUMMARY_TS` env contract를 추가하고, 당시 `openai replay diagnostic lane plan` 문서와 [policy-normalization-education-priority-replay-procedure.md](../history/ai/policy-normalization-education-priority-replay-procedure.md)에 같은 이름으로 고정했다
- 이유: cron wrapper가 최소한의 glue code로 붙으려면, summary append를 켜는 방법과 timestamp override 방법이 스크립트에 바로 있어야 한다. env contract를 먼저 고정하는 편이 다음 단계 명령 초안 작성이 훨씬 단순하다

## 235) cleanup retention 계약도 문서만이 아니라 별도 스크립트로 고정해야 replay cron 과 역할이 안 섞인다
- 문제: cleanup 을 별도 cron 으로 분리하기로 했어도, 실제 명령/경로/env 계약이 없으면 다음 단계에서 replay wrapper 안으로 다시 밀어 넣거나, host마다 다른 `find`/`rm` 명령을 쓰게 될 수 있다
- 해결: [cleanup-openai-replay-artifacts.sh](/home/minseok/youth-welfare/deploy/smoke/cleanup-openai-replay-artifacts.sh) 를 추가하고, 당시 `openai replay diagnostic lane plan` 문서에 `REPLAY_LOG_ROOT`, `SUMMARY_RETENTION_DAYS`, `ARTIFACT_RETENTION_DAYS`, `DRY_RUN` 계약과 실행 예시를 같이 고정했다
- 이유: replay cron 과 cleanup cron 의 책임을 실제 파일 단위로 분리해 두어야 역할이 다시 섞이지 않는다. cleanup 도 스크립트로 고정해야 운영자가 dry-run, retention 변경, 수동 재실행을 같은 계약으로 다룰 수 있다

## 236) nightly replay도 cron entry에서 긴 env/경로 조합을 직접 쓰기보다 wrapper 스크립트로 한 번 감싸는 편이 안전하다
- 문제: nightly replay는 `USE_REAL_OPENAI_FOR_REPLAY`, `KEEP_ARTIFACTS`, `ARTIFACT_DIR`, `REPLAY_SUMMARY_APPEND_FILE`, `REPLAY_SUMMARY_TS` 등을 같이 맞춰야 해서, cron line에 직접 길게 쓰면 host마다 오타/경로 불일치가 나기 쉽다
- 해결: [run-nightly-openai-replay.sh](/home/minseok/youth-welfare/deploy/smoke/run-nightly-openai-replay.sh) 를 추가해 nightly 기본값을 wrapper가 계산하도록 하고, 당시 `openai replay diagnostic lane plan` 문서와 [policy-normalization-education-priority-replay-procedure.md](../history/ai/policy-normalization-education-priority-replay-procedure.md)에 이를 기본 진입점으로 고정했다
- 이유: cleanup 과 replay 둘 다 cron에서 바로 호출될 예정이면, 각자의 env/경로 계약이 스크립트에 모여 있어야 운영자가 cron line에서 “무엇을 호출하는지”만 보면 된다. wrapper를 두는 편이 host 간 drift를 줄인다

## 237) 현재 단계에서는 systemd timer보다 system cron이 더 작은 운영 진입 경로다
- 문제: nightly replay와 cleanup을 host에서 주기 실행해야 하지만, 지금 단계에서 `.service`/`.timer` unit까지 같이 열면 운영 절차가 갑자기 systemd 중심으로 커지고 문서/스크립트 경계가 다시 넓어진다
- 해결: 당시 `openai replay diagnostic lane plan` 문서에 현재 우선순위를 `system cron -> 필요 시 systemd timer` 로 고정하고, 다음 작업도 cron entry 예시 작성으로 좁혔다
- 이유: 이미 wrapper 스크립트 둘이 있고 운영 문서도 shell/compose 중심이다. 가장 작은 다음 단계는 crontab에서 wrapper를 부르는 것이고, systemd timer는 observability나 표준화 필요가 생겼을 때 뒤에서 붙여도 늦지 않다

## 238) replay/cleanup cron 예시도 절대경로 호출과 별도 runtime log redirection까지 같이 고정해야 host별 drift가 덜 난다
- 문제: `system cron` 으로 운영한다고만 적어 두면 host마다 긴 env 조합을 다시 풀거나, summary file과 cron stderr/stdout를 같은 파일에 섞어 적는 식으로 운영 방식이 갈라질 수 있다
- 해결: 당시 `openai replay diagnostic lane plan` 문서와 [policy-normalization-education-priority-replay-procedure.md](../history/ai/policy-normalization-education-priority-replay-procedure.md)에 replay/cleanup crontab 예시를 추가하고, wrapper/cleanup 스크립트를 절대경로로 호출하며 `nightly-cron.log`, `cleanup-cron.log` 로 runtime log를 분리하는 기준을 고정했다
- 이유: summary file은 metric one-line append 용도이고, cron runtime log는 shell/app failure triage 용도다. 두 경로를 분리해야 nightly replay 해석이 단순해지고 host별 cron line drift도 줄어든다

## 239) cron 예시 다음에는 `crontab -e` 적용 순서와 사전 수동 검증까지 묶은 runbook이 있어야 운영자가 문서 사이를 덜 왕복한다
- 문제: lane plan에 cron line이 있어도 실제 운영자는 `언제 수동 replay를 먼저 돌릴지`, `등록 직후 무엇을 확인할지`, `문제 생기면 cron에서 무엇부터 지울지` 를 여러 문서에서 다시 조합해야 한다
- 해결: 당시 `openai replay cron runbook` 문서를 추가해 사전 조건, 수동 replay/cleanup dry-run, `crontab -e` block, 등록 직후 확인, 다음날 확인 포인트, 롤백 절차를 한 장으로 정리했고, 당시 `deployment` 문서와 [README.md](./README.md) 에서 바로 링크되도록 맞췄다
- 이유: ops host 적용은 설계 문서보다 runbook이 더 중요하다. 실제 명령과 확인 순서가 한 문서에 있어야 운영 drift와 누락이 줄어든다

## 240) replay cron 적용 다음에는 `cron user` 권한과 `.env` / OpenAI secret 경계를 별도 메모로 고정해 두는 편이 안전하다
- 문제: runbook 에서 `cron user` 가 `.env`, Docker, OpenAI secret 접근 권한을 가진다고만 적어 두면, 실제 운영자가 이를 root cron 이나 broad sudo 계정으로 해석해 권한 범위를 과하게 넓힐 수 있다
- 해결: 당시 `openai replay cron security boundary` 문서를 추가해 `non-root ops cron user`, `.env` read-only, host-local artifact 접근, broad sudo 비권장, root crontab 비기본값 원칙을 따로 고정했고, 당시 `openai replay cron runbook`, `openai replay diagnostic lane plan`, `deployment` 문서와 [README.md](./README.md) 에서 바로 링크되게 맞췄다
- 이유: nightly replay는 diagnostic lane이지만 OpenAI secret 과 host-local artifact를 함께 다루므로, 실행 절차와 권한 경계는 분리해서 적는 편이 운영 해석이 덜 흔들린다

## 241) 권한 경계 문장만으로는 부족하고, host 적용 전 `test -r/-w`, `stat`, `id`, `crontab -l` 같은 최소 체크 명령까지 runbook에 있어야 실제 오적용을 줄일 수 있다
- 문제: `non-root ops user`, `.env read-only` 같은 원칙이 있어도 운영자는 실제 host에서 무엇을 실행해 확인할지 다시 추정해야 하고, 그 과정에서 root 계정으로 그냥 돌리거나 log root 권한 부족을 늦게 발견할 수 있다
- 해결: 당시 `openai replay cron runbook` 문서에 `id`, `crontab -l`, `ls -l .env`, `test -r .env`, `test -w /var/log/...`, `stat -c '%A %U:%G %n' ...` 를 추가해 사전 수동 검증과 등록 직후 확인에 바로 쓸 수 있게 했다
- 이유: 권한 경계는 문장보다 명령으로 확인하는 편이 운영 drift를 줄인다. 특히 `.env readable` 과 `log root writable` 은 replay 성공 조건이라 cron 등록 전에 바로 확인하는 게 맞다

## 242) same prompt/seed/fingerprint 에도 `ai_score` drift가 남는다면, 제품 계약은 exact equality가 아니라 `target visibility + traceability` 에 두는 편이 맞다
- 문제: same `promptSha256` + same `replaySeed` + same `systemFingerprint` 조건에서도 `ai_score` / `final_score` drift가 남는데, 이걸 그대로 제품 품질 계약으로 들고 가면 false positive 회귀 판정과 운영 노이즈가 커진다
- 해결: [openai-ai-score-product-policy.md](../history/ai/openai-ai-score-product-policy.md) 를 추가해 `ai_score` 를 deterministic truth가 아니라 `variable-but-traceable rerank signal` 로 정의하고, 제품 보장 범위를 `rule-only 기준선`, `target row visibility`, `artifact traceability` 로 고정했다. [recommendation-pipeline.md](../recommendation/recommendation-pipeline.md) 에도 같은 경계를 링크로 반영했다
- 이유: 현재 제품 목적은 exact 점수 재현이 아니라 target 정책 노출 개선과 drift 추적 가능성이다. live OpenAI 계층을 soft signal로 해석하는 편이 실제 운영과 더 맞다

## 243) `참여권리` 안에서 `청년참여` subset만 따로 bridge 후보로 보는 것은 가능하지만, 현재 단계에서는 교육 실험 다음 순번의 future candidate로만 두는 편이 맞다
- 문제: row-level review에서 `청년참여` sample은 `참여·기회` 와 비교적 가깝게 보이기 때문에, `참여권리` 전체 승격 대신 subset만 바로 narrow bonus로 열고 싶어질 수 있다. 하지만 지금은 이미 `교육 -> 교육·직업훈련` narrow experiment가 active candidate이고, `청년참여` 도 모집/공모/파트너/공간 참여처럼 내부 의미가 완전히 균질하진 않다
- 해결: [policy-normalization-participation-subset-bridge-policy.md](../history/policy/policy-normalization-participation-subset-bridge-policy.md) 를 추가해 `참여권리` 전체 승격은 계속 금지하고, `청년참여` subset도 immediate implementation 대상이 아니라 2순위 future candidate로만 유지한다고 고정했다. 관련 bridge review/policy 문서도 같은 결론으로 링크를 맞췄다
- 이유: 현재 stage에서 예외 bridge 축을 늘리면 `compat=기타` 집합에 narrow rule이 빠르게 늘어난다. 교육 실험 효과를 먼저 본 뒤, 필요할 때 `청년참여` subset만 별도로 다시 inventory/replay sample로 좁히는 편이 더 안전하다

## 244) 복지로 `threshold_like` income signal 은 `beneficiary_only` 와 달리 hard fact 나 taxonomy 로 억지 승격하지 말고 optional soft signal 후보로만 남기는 편이 맞다
- 문제: no-fact 복지로 detail 재분류에서 `threshold_like 13`건은 `% 이하`, `만원 이하`, `신혼`, `맞벌이`, `우대형`, `일반형`, `개별심사` 같은 branch/context 가 함께 섞여 있었다. 이를 canonical `INCOME_PCT` / `INCOME_WON` hard fact 로 flatten 하면 의미 손실이 크고, retrieval hard filter 로 오용될 위험도 있었다
- 해결: [policy-normalization-income-threshold-soft-signal-policy.md](../history/policy/policy-normalization-income-threshold-soft-signal-policy.md) 를 추가해 `threshold_like` 는 현재 hard fact 로 적재하지 않고, future 저장이 필요해도 raw/context 를 보존하는 optional soft signal 계층으로만 다루도록 고정했다. [db-migration.md](./db-migration.md) 와 [README.md](./README.md) 에도 같은 경계를 반영했다
- 이유: `beneficiary_only` 는 안정적인 label 기반 soft taxonomy 로 분리 가능했지만, `threshold_like` 는 숫자와 branch 조건이 함께 섞인 해석 신호다. current canonical 단계에선 eligibility fact보다 weaker한 계층으로 남기는 편이 더 안전하다

## 245) 복지로 신청마감은 live detail 재확인에서도 explicit field 증거가 없으면 계속 optional fact로 두는 편이 맞다
- 문제: 이전 분석에서는 local stored raw payload 기준으로 `applyMethodDetail` 에 date-like token이 거의 없고 다른 필드의 날짜도 출생연도 범위/적용기간 성격이어서 `BK_APPLY_END_DATE` fallback 확대를 보류했다. 그래도 live endpoint에만 explicit deadline key가 새로 생겼을 가능성은 다시 확인할 필요가 있었다
- 해결: 2026-04-30 기준 local DB의 복지로 `DETAIL` raw payload key를 다시 집계한 결과 `targetDetail`, `supportDetail`, `applyMethodDetail`, `selectionCriteria`, `contactList`, `supportCycle`, `provisionType` 외에 deadline 전용 key는 없었고, `applyEndDate/aplyEndDt/deadline/rcptEndDt` 류 key 존재 건수도 `0` 이었다. 같은 날 당시 local runtime의 공공데이터포털 key로 중앙/지자체 live detail endpoint를 직접 다시 호출했지만 둘 다 `HTTP 429` 로 막혀 신규 raw schema는 확보하지 못했다. 이 상태와 public data.go.kr 설명을 함께 근거로 `BK_APPLY_END_DATE` 는 계속 optional fact로 유지한다고 정리했다
- 이유: explicit field 증거 없이 fallback 범위만 넓히면 출생연도/적용기간을 신청마감으로 오인할 위험이 계속 남는다. live 재확인에서도 확증이 없으면 보수적으로 optional fact를 유지하는 쪽이 더 안전하다

## 246) authenticated live 목록 응답에도 `srchPolyBizSecd` 가 직접 안 보인다면, broad code-like field를 억지로 `YOUTH_MID` stable code로 승격하면 안 된다
- 문제: 공개 HTML 예시와 비로그인 `Unauthorized` 제약 때문에 `YOUTH_MID` stable code를 계속 보류하고 있었는데, authenticated live payload를 실제로 다시 스캔했을 때도 `srchPolyBizSecd` 필드는 직접 보이지 않았다. 대신 `plcyMajorCd`, `jobCd`, `schoolCd`, `sbizCd` 같은 code-like field는 있었지만, 서로 다른 `mclsfNm` 에도 같은 broad/default-like 값이 반복되고 일부는 multi-code로 들어와 stable mid-category key로 보기 어려웠다
- 해결: 2026-04-30 live inventory 결과를 [policy-normalization-youth-mid-live-inventory.md](../history/policy/policy-normalization-youth-mid-live-inventory.md) 로 별도 정리하고, `YOUTH_MID` 는 계속 label-only taxonomy + `YOUTH_MID_RAW_ALIAS` 정책을 유지하기로 했다. pending도 `live inventory 수집 완료 -> stable code mapping SQL 초안은 계속 보류` 상태로 다시 분리했다
- 이유: live payload에 보이는 아무 code field나 `YOUTH_MID` code로 채택하면, later official metadata 확보 시 canonical code set과 충돌하거나 같은 `mclsfNm` 이 다른 broad code 축과 뒤섞일 위험이 있다. 지금은 label inventory를 더 강하게 확인한 것으로 만족하고, stable code는 metadata source를 따로 확보한 뒤 다시 여는 편이 안전하다

## 247) `YOUTH_MID stable code mapping SQL` 은 SQL부터 쓰는 게 아니라, 먼저 어떤 source를 truth로 인정할지 못 박아야 다시 흔들리지 않는다
- 문제: live inventory까지 끝난 뒤에도 pending에는 여전히 `YOUTH_MID stable code mapping SQL 초안 작성` 이 남아 있었다. 하지만 현재 확보된 근거는 `label inventory` 와 broad code-like field뿐이고, 여기서 바로 SQL을 쓰기 시작하면 다시 임의 surrogate code 생성이나 `plcyMajorCd/jobCd/schoolCd/sbizCd` 오용으로 기울 위험이 있었다
- 해결: [policy-normalization-youth-mid-stable-code-source-plan.md](../history/policy/policy-normalization-youth-mid-stable-code-source-plan.md) 를 추가해, stable code mapping을 다시 열 수 있는 source를 `authenticated metadata inventory`, `마이페이지/운영 export`, `operator-provided official codebook` 으로 제한하고, 공개 HTML example·broad code-like field·label 역추론만으로는 reopen하지 않는 기준을 고정했다
- 이유: `YOUTH_MID` 는 지금 label-only taxonomy로도 수집/정규화/read-model 경계가 유지된다. 따라서 다음 단계는 “억지 SQL 작성”이 아니라 “어떤 source를 truth로 인정할지”를 먼저 고정하는 것이고, 그 기준이 있어야 후속 mapping SQL도 다시 흔들리지 않는다

## 248) source 우선순위와 실제 다음 액션은 다를 수 있고, 지금 `YOUTH_MID` 는 운영 담당자 export/codebook 확보가 더 현실적이다
- 문제: source plan상으로는 `authenticated metadata/testbed -> 마이페이지 OPEN API 관리 화면 -> 운영 담당자 export` 순서를 적어 둘 수 있지만, 실제 로컬 저장소에는 `YOUTH_API_KEY` 외에 member login/session 자동화 단서가 없다. 이 상태에서 “다음 작은 task”를 계속 로그인 자동화 쪽으로 밀면 근거 없는 크롤링/세션 파헤치기로 새기 쉬웠다
- 해결: [policy-normalization-youth-mid-stable-code-source-plan.md](../history/policy/policy-normalization-youth-mid-stable-code-source-plan.md)에 `현재 가장 현실적인 다음 액션` 절을 추가해, 지금은 운영 담당자 제공 export/codebook 확보를 먼저 시도하고, 마이페이지 로그인 자동화는 credential/세션 구조가 준비되기 전까지 보류한다고 고정했다
- 이유: `YOUTH_MID stable code` 문제의 병목은 SQL 작성이 아니라 source 확보다. 그런데 그 source도 지금 당장 자동 수집 가능한 경로와 수동 확보가 더 빠른 경로가 다르다. 이 차이를 문서로 못 박아야 다음 작업이 다시 인증 우회/역추론으로 새지 않는다

## 249) 운영 담당자 export를 받기로 했더라도, 어떤 컬럼이 있어야 sufficient source인지 먼저 못 박지 않으면 label list나 캡처본만 받아 다시 멈출 수 있다
- 문제: `운영 담당자 export/codebook 우선`으로 방향을 잡아도, 요청 스펙이 없으면 상대가 `label 목록만 있는 시트`, `요청 예시 캡처`, `mclsfNm 모음` 같은 불충분한 자료를 줄 수 있다. 그러면 다시 “이걸로 stable code mapping SQL을 열 수 있나”를 재판단해야 한다
- 해결: [policy-normalization-youth-mid-stable-code-source-plan.md](../history/policy/policy-normalization-youth-mid-stable-code-source-plan.md)에 `운영 담당자 요청 스펙` 절을 추가해 최소 필수 컬럼을 `code`, `official label` 로 고정하고, `sort_order`, `active 여부` 를 권장 컬럼으로 정리했다. 동시에 sufficient example / insufficient example / 요청 문구 초안까지 같이 적었다
- 이유: stable code source 확보는 “무언가 받기”가 아니라 “mapping SQL을 열 수 있을 정도로 직접 대응되는 inventory 받기”가 목적이다. 요청 스펙을 먼저 고정해야 운영 커뮤니케이션이 한 번에 끝나고, 다시 label-only 상태에서 맴도는 일을 줄일 수 있다

## 250) `GOV24_SERVICE_FIELD` / `USER_TYPE` / `BENEFIT_TYPE` 는 old `category` 계열 endpoint를 source로 재활용하면 안 되고, current API 기준 source를 다시 잡아야 한다
- 문제: `GOV24_*` import SQL을 빨리 쓰려면 과거 `category` / `category-code` endpoint나 예전 문서 캡처를 가져와 label inventory처럼 쓰고 싶어질 수 있다. 하지만 공공데이터포털 2021 개편 공지는 기존 5종 operation에 현행화되지 않은 정보가 있었다고 밝히고, 2021-09-15부터는 `serviceList`, `serviceDetail`, `supportConditions` 3종만 current source로 남겼다
- 해결: [policy-normalization-gov24-label-source-plan.md](../history/policy/policy-normalization-gov24-label-source-plan.md) 를 추가해, `GOV24_SERVICE_FIELD` / `USER_TYPE` / `BENEFIT_TYPE` import SQL은 current Swagger/schema export 또는 provider-provided official codebook이 있어야만 reopen하고, deprecated `category` / `category-code` 응답이나 sample payload 역추론만으로는 열지 않도록 기준을 고정했다
- 이유: `supportConditions` 는 current 공식 code subset을 이미 일부 확인했지만, 나머지 `GOV24_*` taxonomy는 current finite inventory source가 다르다. deprecated 분류 endpoint를 재사용하면 official taxonomy와 stale 문서가 다시 섞이므로, 먼저 current source-of-truth를 고정하는 편이 안전하다

## 251) `GOV24_*` 도 source plan만으로는 부족하고, 요청 시 어떤 형식이면 sufficient source인지 먼저 못 박아야 다시 sample 캡처만 받게 되는 일을 줄일 수 있다
- 문제: `GOV24_SERVICE_FIELD` / `USER_TYPE` / `BENEFIT_TYPE` 는 current source 기준으로 다시 받기로 했더라도, 요청 스펙이 없으면 제공기관이나 운영 담당자가 Swagger 캡처, sample payload, label 목록만 보내는 식으로 끝날 수 있다. 그러면 다시 “이걸로 import SQL을 열 수 있나”를 재판단해야 한다
- 해결: [policy-normalization-gov24-label-source-plan.md](../history/policy/policy-normalization-gov24-label-source-plan.md)에 sufficient example / insufficient example / 요청 문구 초안을 추가해, 최소 요구를 `field name + code + official label` 로 고정하고 `active/use 여부`, `sort_order`, `설명` 을 권장 필드로 정리했다
- 이유: `GOV24_* import/backfill SQL` 의 목적은 current finite inventory를 공식 코드테이블에 적재하는 것이다. 요청 스펙을 먼저 고정해야 sample 중심 자료와 actual codebook을 구분할 수 있고, 다시 bridge 결과나 deprecated endpoint로 미끄러지는 일을 줄일 수 있다

## 252) `GOV24_SUPPORT_CONDITION` 은 representative subset 근거와 full inventory 근거를 분리해서 봐야 한다
- 문제: 현재는 `JA0101`, `JA0110`, `JA0201~0205`, `JA0320`, `JA0327`, `JA0412` 같은 대표 code는 공식 근거가 있어 seed 했지만, 이걸 그대로 “supportConditions 전체 codebook도 사실상 확보된 것”처럼 확대 해석하면 sample 관찰 범위를 full inventory와 혼동하게 된다
- 해결: [policy-normalization-gov24-support-condition-source-plan.md](../history/policy/policy-normalization-gov24-support-condition-source-plan.md) 를 추가해, representative subset seed는 유지하되 full inventory/backfill은 current Swagger/schema export 또는 provider codebook 확보 전까지 보류한다고 고정했다. 동시에 sufficient/insufficient source 기준과 요청 스펙도 분리했다
- 이유: `supportConditions` 는 `GOV24_*` taxonomy보다 구조화가 강하지만, representative subset을 몇 개 확인한 것과 전체 finite code inventory를 확보한 것은 다른 단계다. 이 경계를 분리해 두어야 subset seed와 full import가 다시 섞이지 않는다

## 253) `compat_unified_category` 를 너무 일찍 read-model 계산값으로 바꾸면 canonical 정규화와 현재 제품 계약 변경이 한 번에 묶여 drift 원인을 분리하기 어려워진다
- 문제: writer는 이미 `service_taxonomies.compat_unified_category_*` 를 저장하고 있고, canonical summary도 조금씩 붙고 있다 보니 `compat_unified_category` 를 아예 read-model 계산값으로만 바꾸고 싶어질 수 있다. 하지만 현재 priority/response/replay 계약은 여전히 `compat` 를 기준으로 서 있고, canonical summary coverage도 아직 `youth_mid/gov24_*` 공백과 `compat=기타 + youth_major 채움` 정책 이슈를 안고 있다
- 해결: [policy-normalization-compat-storage-policy.md](../history/policy/policy-normalization-compat-storage-policy.md) 를 추가해, 현재 phase에서는 `compat_unified_category` 를 저장 필드로 유지하고 `welfare_services.unified_category` 와 `service_taxonomies.compat_unified_category_*` 를 함께 두기로 고정했다. read-model은 저장된 compat를 읽고 canonical summary는 secondary hint로만 소비한다
- 이유: 지금 compat를 계산-only로 바꾸면 collect/write, sidecar summary 정제, read-model projection, priority 계약 변경이 한 경로로 합쳐져 drift triage가 어려워진다. canonical 전환이 끝나기 전까지는 stored compat를 기준점으로 두는 편이 더 안전하다

## 254) `unifiedCategory` 는 추천 내부 필드가 아니라 검색/상세/랭킹/추천 전반의 공개 응답 계약이라, canonical taxonomy 전환 중에도 조용히 의미를 바꾸면 UI와 API 소비자 해석이 함께 흔들린다
- 문제: canonical read-model과 compat storage 정책을 정리하다 보면 `RecommendationResponse.unifiedCategory` 만 먼저 canonical summary로 바꾸고 싶어질 수 있다. 하지만 실제 코드를 보면 `PolicySummaryResponse`, `PolicyDetailResponse`, `PolicyRankingResponse`, `RecommendationResponse` 가 모두 `WelfareService.unifiedCategory` 를 직접 노출하고 있어, 이 필드는 이미 public contract 전체에 퍼져 있다
- 해결: [policy-normalization-unified-category-response-bridge.md](../history/policy/policy-normalization-unified-category-response-bridge.md) 를 추가해, 현재 phase에서는 응답 `unifiedCategory` 의미를 계속 legacy compat category로 유지하고 canonical taxonomy는 inventory/explanation/future experiment 용 secondary hint로만 쓰기로 고정했다
- 이유: canonical taxonomy는 아직 priority/read-model 보조 힌트 단계이고, `compat=기타 + canonical youth_major 채움` 같은 집합도 explicit bridge 정책이 덜 끝났다. 이 상태에서 응답 category를 먼저 canonical로 바꾸면 추천/검색/상세/UI 필터 의미가 한 번에 바뀌어 원인 분리가 더 어려워진다

## 255) 추천 본체 canonical 이행에서 matcher나 response부터 먼저 건드리면 회귀 원인이 SQL 후보 풀 문제인지, retrieval hydrate 문제인지, scoring bridge 문제인지 분리하기 어려워진다
- 문제: 현재 추천 본체는 이미 `RetrievedRecommendationCandidates`, projection hydrate, scoring bridge, matcher bridge가 일부 들어와 있어 다음 작업을 아무 데서나 이어붙이기 쉬운 상태다. 하지만 `WelfareServiceRepository.findCandidates*` 가 pass/fail 후보 집합을 쥐고 있고, `RetrievalService` 는 hydrate/후처리, `RuleScoringService` 는 rank ordering, `DefaultPriorityMatcher` 는 category contract를 쥐고 있어 순서를 어기면 회귀 원인이 섞인다
- 해결: [policy-normalization-recommendation-migration-order.md](../policy/policy-normalization-recommendation-migration-order.md) 를 추가해, 추천 본체 이행 순서를 `repository semantics -> retrieval hydrate -> scoring bridge -> matcher bridge` 로 고정했다. repository는 source-specific sentinel과 candidate pool semantics 정리, retrieval은 legacy 후보 유지 + projection hydrate, scoring은 OR 병행 소비, matcher는 가장 마지막에 stored compat만 좁게 읽는 단계로 분리했다
- 이유: 실제 `YOUTH 0/0 income` 문제도 scoring이 아니라 repository semantics에서 먼저 막혔고, beneficiary/interest/priority bridge도 모두 “후보 집합은 이미 맞다”는 전제가 있어야 안전하게 들어간다. 즉 점진 이행은 기능 단위가 아니라 실패 반경 단위로 순서를 고정해야 한다

## 256) `TextConstraintExtractor` 가 계속 `COND_*` 문자열 토큰과 `ConstraintSummary` 를 주 계약으로 유지하면 canonical `service_facts` 저장 직전에 다시 parsing/의미 보정을 해야 해서 경계가 흐려진다
- 문제: 현재 extractor는 legacy `service_tags.KEYWORD` 용 문자열 토큰과 singleton `ConstraintSummary` 를 함께 제공한다. 하지만 `service_facts` 저장은 `fact_group`, `fact_merge_key`, typed value, `sourceField`, `authority`, `confidence`, `raw/evidence` 를 필요로 하므로, 지금 출력만으로는 persistence 직전에 다시 한 번 해석 로직이 커질 수밖에 없다
- 해결: [policy-normalization-text-constraint-output-model.md](../history/policy/policy-normalization-text-constraint-output-model.md) 를 추가해, 다음 extractor 주 계약을 `SourceText(sourceField, text)` 입력과 `ExtractedFactCandidate` 목록 출력으로 재설계한다고 고정했다. legacy `COND_*` 토큰은 필요 시 별도 adapter helper에서만 파생하고, 본체 extractor는 `AGE / INCOME / RENT_CAP / APPLY_END_DATE` typed fact candidate 쪽으로 수렴시킨다
- 이유: canonical sidecar 단계에서는 “토큰을 다시 읽는 유틸”보다 “사실 슬롯을 직접 표현하는 typed candidate”가 더 안정적이다. 이 경계를 먼저 못 박아야 mapper/saver가 extractor 결과를 다시 문자열로 되감지 않고 바로 `service_facts` 규격으로 연결할 수 있다

## 257) 신규 source-specific 필드를 canonical 스키마에 바로 없는 이유로 버리거나 `welfare_services` 에 억지 flatten 하면, 나중에 official/rule/AI 경계를 다시 분리하기 어려워진다
- 문제: 신규 정책형 source를 붙일 때는 항상 official core/facts 외에 source-specific 자유서술 필드가 남는다. 이 값을 collect 단계에서 그냥 버리면 재처리가 막히고, 반대로 `welfare_services` 나 대표 category에 억지로 섞어 넣으면 나중에 “official canonical”, “rule-derived fact”, “AI 보강” 경계를 다시 분리하기 어렵다
- 해결: [policy-normalization-raw-ai-enrichment-pipeline.md](../history/policy/policy-normalization-raw-ai-enrichment-pipeline.md) 를 추가해, 신규 source-specific 필드는 `raw payload 보존 -> official/rule-derived canonical 우선 추출 -> 남는 자유서술만 AI batch enrichment 후보 승격` 순서로 처리한다고 고정했다. AI 결과는 `AI_ENRICHED` authority 보조 signal로만 저장하고, official/rule-derived 슬롯 overwrite나 `unifiedCategory` 대체에는 쓰지 않는다
- 이유: canonical 전환에서 AI는 1차 저장 경로가 아니라 마지막 보강 단계여야 한다. 그래야 source-specific 필드를 잃지 않으면서도, official 축과 soft enrichment 축이 다시 섞이지 않는다

## 258) logout 즉시 무효화는 refresh 삭제만으로는 안 되고, logout 요청에 실린 현재 bearer access token을 filter 앞단에서 별도 revoke 검사해야 한다
- 문제: 기존 구현은 logout 시 `refresh:{userKey}` 만 지우고 chat session만 정리했기 때문에, 이미 발급된 access token은 만료 전까지 `/api/admin/**` 같은 보호 API를 계속 통과했다. 즉 `POST /api/auth/logout` 성공과 “즉시 권한 차단”이 서로 다른 계약이었다
- 해결: Redis 기반 `AccessTokenRevocationService` 를 추가해 logout 요청에 실린 bearer access token을 남은 만료 시간 TTL로 `access-revoked:*` key에 저장하고, `JwtAuthenticationFilter` 가 인증 세팅 전에 revoke 여부를 먼저 확인하도록 바꿨다. `/api/auth/logout` 는 refresh cookie/header만으로도 계속 성공하지만, 같은 요청에 bearer token이 있으면 그 token은 즉시 차단된다
- 이유: user-level cutoff timestamp 방식은 JWT `iat` 정밀도와 재로그인 경계 이슈가 남고, 전체 token tracking은 scope가 커진다. 이번 단계에선 “logout에 사용한 현재 access token 즉시 차단”을 exact token blacklist로 고정하는 편이 가장 작은 diff로 실제 위험을 줄인다

## 259) `cookie-only logout` 까지 user-level cutoff로 넓히면 브라우저 refresh 종료와 전 세션 access-token 회수 의미가 섞여, 현재 제품 계약보다 더 큰 설계 변경이 된다
- 문제: bearer-present logout revoke를 넣은 뒤에는, refresh cookie만 실린 `cookie-only logout` 도 같은 방식으로 “모든 access token 즉시 차단”까지 해줘야 하는 것처럼 보일 수 있다. 하지만 이 경로는 현재 요청에 어떤 access token이 살아 있었는지 서버가 직접 보지 못하고, 다중 로그인/재로그인/`iat` 경계까지 함께 풀어야 한다
- 해결: [auth-logout-revocation-scope-policy.md](../history/auth/auth-logout-revocation-scope-policy.md) 를 추가해 현재 phase의 계약을 `bearer-present exact token revoke` 와 `cookie-only refresh-only` 로 분리하고, user-level cutoff는 별도 reopen 조건이 생길 때만 다시 열기로 고정했다
- 이유: 지금 필요한 건 “logout에 사용한 현재 token의 즉시 차단”이지, 전체 세션 모델 재정의가 아니다. `cookie-only logout` 을 조용히 넓히면 브라우저 logout, 모바일/다중 세션, admin 강제 로그아웃 의미가 한 번에 섞여 실패 반경이 커진다

## 260) future user-level revoke는 generic logout보다 `withdraw` 와 `admin forced logout` 같은 더 강한 보안 이벤트부터 여는 편이 실패 반경을 더 잘 통제할 수 있다
- 문제: `cookie-only logout` 을 refresh-only 계약으로 고정한 뒤에도, “그럼 다음에 user-level cutoff를 어디서부터 다시 열 것인가”가 남는다. 이걸 generic logout부터 다시 열면 브라우저 UX, multi-device sign-out, 재로그인 경계가 한 번에 엮인다
- 해결: [auth-revocation-reopen-order.md](../history/auth/auth-revocation-reopen-order.md) 를 추가해 reopen 우선순위를 `withdraw -> admin forced logout -> generic cookie-only logout` 으로 고정했다
- 이유: 탈퇴와 운영 강제 로그아웃은 계정 폐기/권한 회수라는 더 강한 이벤트라 제품 의미가 분명하다. 반면 generic logout은 세션 UX 의미가 더 커서, 같은 cutoff 기술을 쓰더라도 가장 나중에 여는 편이 안전하다

## 261) `withdraw` 가 다음 revoke 후보라고 해도, 구현부터 열면 상태 masking/채팅 정리/인증 차단이 한 번에 섞이므로 baseline smoke를 먼저 남기는 편이 안전하다
- 문제: `withdraw` 는 `logout` 보다 강한 보안 이벤트라 다음 revoke 후보로는 맞지만, 현재 `UserService.withdraw(...)` 는 attribute/priorities 삭제, chat cleanup, withdrawn state 반영까지 함께 수행한다. 이 상태에서 바로 cutoff 구현을 넣으면 실패 원인이 revoke인지, withdrawn state 처리인지 분리하기 어렵다
- 해결: [auth-withdraw-revocation-next-step.md](../history/auth/auth-withdraw-revocation-next-step.md) 를 추가해 다음 액션을 `withdraw` 직전 old access token baseline smoke/inventory 확보로 고정하고, 그 결과를 본 뒤에만 전용 revoke 구현을 다시 열기로 정리했다
- 이유: logout hardening도 먼저 `old access token after logout smoke` 를 남겼기 때문에 이후 정책 변경을 분리할 수 있었다. `withdraw` 도 같은 순서를 따라야 실패 반경이 작다

## 262) `withdraw` 는 logout보다 강한 보안 이벤트이므로, old access token의 business-layer 차단만 두지 말고 현재 bearer token revoke와 refresh key 정리까지 같이 가져가는 편이 더 일관된다
- 문제: baseline smoke 결과 회원탈퇴 전 old access token은 사용자 보호 API에서 `WITHDRAWN_USER` 로 막혔지만, filter 단계 revoke는 없었고 refresh token 정리도 명시돼 있지 않았다. 즉 탈퇴 계정이 “service layer에서만 막히는 상태”가 남아 있었다
- 해결: `UserService.withdraw(...)` 에 refresh key 삭제와 presented bearer access token revoke를 추가하고, `AuthService.refresh(...)` 는 withdrawn user를 만나면 stored refresh token 존재 여부와 무관하게 `WITHDRAWN_USER` 로 중단하도록 보강했다. integration smoke도 같은 token의 `/api/users/me/bookmarks -> 401 / A006`, stale refresh의 `/api/auth/refresh -> 410 / U003` 으로 뒤집어 고정했다
- 이유: 탈퇴는 generic logout보다 강한 terminal event다. 따라서 “old token이 business layer까지 도달한 뒤 막힌다”는 baseline보다, 최소한 현재 탈퇴에 사용한 token과 refresh 재발급은 즉시 정리하는 쪽이 제품 의미와 더 잘 맞는다

## 263) 현재 admin 권한 회수는 forced logout이 아니라 `SECURITY_ADMIN_EMAILS` + 앱 재기동 기반 role revoke가 기본 경계라는 점을 먼저 분리해야 한다
- 문제: `admin forced logout` 을 다음 revoke 후보로 보기 시작하면, 현재 운영에서 실제로 admin 권한을 어떻게 회수하는지와 “이미 발급된 admin token을 즉시 끊는가”가 한 문제처럼 섞이기 쉽다. 하지만 지금 role source of truth는 config allowlist이고, forced logout/session revoke는 아직 별도 기능이 아니다
- 해결: [auth-admin-revoke-boundary-policy.md](../history/auth/auth-admin-revoke-boundary-policy.md) 를 추가해 현재 admin revoke 기본 경로를 `SECURITY_ADMIN_EMAILS` 변경 + 앱 재기동으로 고정하고, role revoke와 future forced logout/token revoke를 분리했다
- 이유: stale config 문제와 stale token 문제는 원인과 대응이 다르다. 이 경계를 먼저 고정해야 다음 baseline도 “allowlist 제거 후 재기동” 과 “old token 지속성” 으로 나눠서 볼 수 있다

## 264) admin allowlist 제거는 old access token을 바로 무효화하지 않고, refresh로 새로 만든 token부터만 `ROLE_ADMIN` 을 떨어뜨리는 현재 경계를 baseline으로 고정해야 forced logout 필요 범위를 설명할 수 있다
- 문제: `SECURITY_ADMIN_EMAILS` 제거 + 앱 재기동이 기본 revoke라 해도, 실제로 old access token과 기존 refresh token이 어디서 갈리는지 baseline이 없으면 forced logout 필요성을 추상적으로만 이야기하게 된다
- 해결: `AdminSecurityIntegrationTest` 에서 `AuthService` allowlist를 비운 뒤 old admin access token은 계속 `/api/admin/collect/youth` 를 통과하고, 같은 refresh token으로 발급한 새 access token부터 `ROLE_ADMIN` 이 빠져 `403 / C003` 이 되는 시나리오를 추가해 현재 경계를 고정했다
- 이유: 이 baseline은 config-based role revoke가 “future token issuance” 에만 작동하고 “already-issued access token 회수” 와는 다른 문제임을 보여준다. 그래서 `admin forced logout` 이 별도 hardening 후보로 남을 이유도 선명해진다

## 265) allowlist 제거 후 old admin refresh token까지 바로 끊으면 config-based role revoke와 forced logout을 다시 한 경로로 합치게 되므로, 현재 phase에서는 새 token부터 role만 제거하는 편이 더 낫다
- 문제: baseline이 생기고 나면 “그럼 old admin refresh token도 즉시 막아야 하지 않나”는 질문이 다시 생긴다. 하지만 그렇게 바꾸면 allowlist 기반 role revoke와 existing token/session revoke를 다시 같은 기능으로 묶게 된다
- 해결: [auth-admin-refresh-revoke-policy.md](../history/auth/auth-admin-refresh-revoke-policy.md) 를 추가해, 현재 allowlist 제거의 의미를 “refresh token 즉시 차단”이 아니라 “새 access token부터 `ROLE_ADMIN` 제거”로 고정했다
- 이유: 현재 구조의 최소 계약은 future token issuance에서 admin role이 더 이상 나오지 않는 것이다. refresh token 자체 즉시 차단은 incident response/offboarding 성격의 `admin forced logout` 문제로 남겨 두는 편이 경계가 더 분명하다

## 266) `admin forced logout` 을 열 때 old access만 끊을지, refresh까지 끊을지 애매하게 두면 allowlist revoke/account lock과 다시 섞이므로 baseline success criteria를 먼저 고정해야 한다
- 문제: allowlist 제거 baseline과 refresh 정책을 닫고 나면, 다음 hardening 후보인 `admin forced logout` 이 “운영자가 버튼을 누르면 뭔가 더 세게 막는 것” 정도로만 남기 쉽다. 이 상태에서 구현을 먼저 열면 `old access 즉시 차단`, `old refresh 즉시 차단`, `계정 영구 차단 여부`가 다시 한 기능으로 뒤섞인다
- 해결: [auth-admin-forced-logout-baseline-policy.md](../history/auth/auth-admin-forced-logout-baseline-policy.md) 를 추가해 future forced logout baseline을 `old access immediate fail + old refresh immediate fail + account lock과 분리` 로 고정했다
- 이유: forced logout은 existing session revoke이고, allowlist revoke는 future role issuance revoke다. 둘의 제품 의미를 다시 섞지 않으려면 implementation보다 success criteria를 먼저 박아 두는 편이 안전하다

## 267) `admin forced logout` 진입점을 DB/Redis 수동 조작으로 열면 운영 우회와 제품 계약이 섞이므로, 운영자 액션과 즉시 revoke source를 분리해서 고정해야 한다
- 문제: forced logout baseline을 고정한 뒤 바로 구현을 열면, 운영자가 어디서 이 기능을 누르는지와 revoke state를 어디에 저장하는지가 다시 뒤섞인다. DB 직접 수정은 의미가 너무 크고, Redis 수동 key 주입은 운영 우회에 가깝다
- 해결: [auth-admin-forced-logout-entrypoint-policy.md](../history/auth/auth-admin-forced-logout-entrypoint-policy.md) 를 추가해 1차 운영자 진입점을 admin API로, 즉시 revoke source를 Redis cutoff/revocation key로 고정했다
- 이유: admin API는 제품 의미와 감사 가능성이 가장 분명하고, Redis는 기존 logout/refresh revoke 경계와 가장 잘 맞는다. 역할을 이렇게 나눠야 allowlist/account state와 session revoke가 다시 섞이지 않는다

## 268) `admin forced logout` API가 role revoke/account lock까지 뜻하는 것처럼 열리면 구현 범위가 다시 커지므로, request/response 계약을 session revoke only로 먼저 고정해야 한다
- 문제: entrypoint를 admin API로 정한 뒤에도 path, target identifier, success 의미를 바로 고정하지 않으면 이 API가 `ROLE_ADMIN` 제거, user 비활성화, account lock까지 같이 하는 것처럼 확장되기 쉽다
- 해결: [auth-admin-forced-logout-api-contract.md](../history/auth/auth-admin-forced-logout-api-contract.md) 를 추가해 1차 계약을 `POST /api/admin/users/forced-logout`, body `userKey`, idempotent by effect, success=`existing access/refresh revoke intent accepted` 로 고정했다
- 이유: 운영자 액션 API는 범위를 애매하게 열수록 나중에 rollback이 어려워진다. session/token revoke only 라는 경계를 path/body/success 의미에서 먼저 못 박아야 구현이 작게 유지된다

## 269) `admin forced logout` 을 logout과 같은 exact token blacklist로만 풀면 multi-session/admin offboarding 요구를 못 담으므로, Redis shape를 user cutoff 기준으로 분리해야 한다
- 문제: 현재 revoke 구현은 `logout` 의 presented bearer token 1개를 `access-revoked:{token}` 로 막는 방식이라 범위가 작다. 이 패턴을 그대로 forced logout에 가져오면 운영자는 old access token 원문을 모르는 상태에서 여러 세션을 한 번에 정리할 수 없다
- 해결: [auth-admin-forced-logout-redis-shape.md](../history/auth/auth-admin-forced-logout-redis-shape.md) 를 추가해 forced logout Redis state를 `refresh:{userKey}` delete + `access-cutoff:{userKey}` 기록으로 고정하고, logout의 exact token blacklist와 역할을 분리했다
- 이유: logout은 current presented token revoke, forced logout은 userKey 기준 existing session revoke다. 둘을 같은 key shape로 처리하면 범위가 모자라거나 구현이 과도하게 복잡해진다

## 270) `admin forced logout` cutoff를 표준 JWT `iat` 초 단위만으로 비교하면 same-second relogin에서 old/new token 경계가 흔들리므로, millis precision claim을 별도로 둬야 한다
- 문제: forced logout은 `access-cutoff:{userKey}` 와 token 발급시각을 비교해 old token만 막고 fresh login token은 통과시켜야 한다. 그런데 현재 `JwtUtil` 은 표준 `issuedAt(now)` 만 기록하므로, old token과 new token이 같은 초에 발급되면 `iat` 만으로는 cutoff 전후를 안전하게 가르기 어렵다
- 해결: [auth-admin-forced-logout-issued-at-policy.md](../history/auth/auth-admin-forced-logout-issued-at-policy.md) 를 추가해 forced logout cutoff 비교는 표준 `iat` 만으로 하지 않고, access token에 custom millis precision claim `iatm` 을 추가하는 방향으로 고정했다
- 이유: logout exact blacklist와 달리 forced logout은 before/after ordering이 핵심이다. incident/offboarding 경계에서 false allow/false deny를 줄이려면 second precision보다 finer-grained claim이 필요하다

## 271) `iatm` 을 도입해도 legacy access token에서 다시 `iat` fallback을 허용하면 same-second ambiguity가 재발하므로, forced logout helper는 새 claim을 강하게 요구하는 편이 낫다
- 문제: millis precision claim 필요성을 정한 뒤에도 `JwtUtil` helper에서 legacy token에 대해 `iat * 1000` fallback을 허용하면, forced logout 경계가 다시 초 단위 비교로 되돌아간다
- 해결: [auth-admin-forced-logout-jwt-helper-policy.md](../history/auth/auth-admin-forced-logout-jwt-helper-policy.md) 를 추가해 access token에는 `iatm` write를 필수로 두고, `getIssuedAtMillis(...)` / `getIssuedAtMillisAllowExpired(...)` helper는 missing `iatm` 을 정상 fallback으로 보지 않는 방향으로 고정했다
- 이유: forced logout은 운영 hardening 기능이라 legacy token 호환성보다 ordering correctness가 우선이다. access cutoff에서 fallback을 넓히면 old/new token 경계가 다시 불명확해진다

## 272) forced logout rollout에서 `iatm` 없는 legacy admin access token까지 compatibility target으로 잡으면 helper 정책과 충돌하므로, 운영 계약을 재로그인 요구 쪽으로 먼저 고정해야 한다
- 문제: `iatm` write/read helper와 no-fallback 정책을 정한 뒤에도, rollout 단계에서 legacy admin access token을 계속 “웬만하면 통과”시키려 하면 구현이 다시 이중 계약이 된다. 그러면 forced logout 경계가 새 token contract와 legacy 호환 둘 다 떠안게 된다
- 해결: [auth-admin-forced-logout-legacy-token-rollout-policy.md](../history/auth/auth-admin-forced-logout-legacy-token-rollout-policy.md) 를 추가해 forced logout 기능 on 이후 `iatm` 없는 legacy admin access token은 compatibility target이 아니라 재로그인 요구 대상으로 본다고 고정했다
- 이유: admin forced logout은 운영 보안 기능이므로, rollout의 핵심은 old token을 오래 살리는 것이 아니라 new token contract를 분명히 하는 것이다. legacy 호환을 줄여야 ordering correctness와 incident 대응 의미가 유지된다

## 273) forced logout 보호 경계에서 `iatm` 없는 legacy admin access token을 `A001 INVALID_TOKEN` 으로 보내면 malformed token과 의미가 섞이므로, revoke 계열과 같은 `401 / A006` 으로 통일하는 편이 낫다
- 문제: rollout 정책을 정한 뒤에도 legacy admin access token을 어떤 에러로 노출할지가 남는다. 여기서 `A001` 을 쓰면 “토큰 형식이 깨졌다”와 “이 보호 경계에서 더 이상 인증된 세션으로 보지 않는다”가 같은 의미처럼 보이게 된다
- 해결: [auth-admin-forced-logout-legacy-error-policy.md](../history/auth/auth-admin-forced-logout-legacy-error-policy.md) 를 추가해 forced logout 보호 경계의 legacy admin access token은 `401 / A006` 으로 통일한다고 고정했다
- 이유: logout revoke와 withdraw old token도 이미 `A006` 으로 수렴한다. forced logout도 같은 보호 API 차단 계열로 맞춰야 운영/테스트/문서가 덜 갈라지고, 사용자 의미도 “재로그인 필요”로 더 자연스럽다

## 274) forced logout 비교를 controller/service guard로 내리면 보호 경로별 누락과 business/auth 경계 혼합이 생기므로, 판단 시점은 filter에 두고 로직만 helper로 분리하는 편이 낫다
- 문제: Redis shape, `iatm`, legacy/error 정책까지 정한 뒤에도 구현 위치를 애매하게 두면 `/api/admin/**`, `/api/users/**`, `/api/recommendations/**` 경로마다 forced logout guard가 다시 흩어질 수 있다
- 해결: [auth-admin-forced-logout-implementation-location.md](../history/auth/auth-admin-forced-logout-implementation-location.md) 를 추가해 차단 판단 시점은 `JwtAuthenticationFilter`, 세부 비교 로직은 dedicated helper/service 로 두는 방향으로 고정했다
- 이유: forced logout은 auth-layer concern이라 SecurityContext 세우기 전에 봐야 하고, 동시에 filter 본문에 Redis/JWT 비교 세부를 모두 넣으면 비대해진다. 시점과 로직을 이렇게 분리해야 누락 surface와 복잡도를 같이 줄일 수 있다

## 275) forced logout helper가 `isRevoked(token)` / `isCutoff(token)` 같은 세부 메서드를 바깥에 노출하면 filter가 다시 구현 세부에 묶이므로, read는 최종 allow/deny 하나로 좁히는 편이 낫다
- 문제: 구현 위치를 filter + helper로 정한 뒤에도 helper 인터페이스를 세부 규칙 단위로 열어 두면, `JwtAuthenticationFilter` 가 exact revoke, cutoff, legacy token 판단 순서를 다시 직접 알아야 한다
- 해결: [auth-admin-forced-logout-helper-interface.md](../history/auth/auth-admin-forced-logout-helper-interface.md) 를 추가해 1차 인터페이스를 `boolean isAccessAllowed(String accessToken)` + `void revokeUserSessions(String userKey, long cutoffMillis)` 로 고정했다
- 이유: filter는 최종 allow/deny만 알고, admin API는 user 단위 revoke intent write만 알면 된다. 세부 Redis/JWT 비교 규칙을 helper 내부에 가둬야 경계가 덜 새고 이후 확장도 쉬워진다

## 276) forced logout helper 이름을 `Guard` 나 `Cutoff` 중심으로 두면 기존 `AccessTokenRevocationService` 와 역할 차이가 흐려지므로, user-session revoke 의미를 이름에서 먼저 고정해야 한다
- 문제: 인터페이스를 정한 뒤에도 이름을 `AdminForcedLogoutGuard` 나 `AccessSessionCutoffService` 로 두면, admin API 전용 guard처럼 보이거나 cutoff 구현 세부만 강조돼 현재 책임 범위가 흐려질 수 있다
- 해결: [auth-admin-forced-logout-helper-name-policy.md](../history/auth/auth-admin-forced-logout-helper-name-policy.md) 를 추가해 새 helper/service 이름을 `UserSessionRevocationService` 로 고정하고, 기존 `AccessTokenRevocationService` 와는 exact-token revoke vs user-session revoke로 역할을 분리했다
- 이유: 이름은 이후 구현과 테스트의 경계를 오래 끌고 간다. current phase에서는 “admin 기능”보다 “user session revoke service”라는 책임 표현이 더 안정적이다

## 277) helper 이름을 정한 뒤 메서드명까지 `cutoff`/`forcedLogout` 세부로 바꾸면 filter와 admin API가 다시 구현 세부를 알게 되므로, `allow/revoke sessions` 수준으로 유지하는 편이 낫다
- 문제: `UserSessionRevocationService` 라는 이름을 고정한 뒤에도 메서드명을 `isTokenPastCutoff`, `forceLogoutUser`, `applyUserCutoff` 처럼 세부 동작 중심으로 바꾸면, 바깥 호출자가 helper 내부 규칙을 다시 알아야 하는 형태가 된다
- 해결: [auth-admin-forced-logout-helper-method-name-policy.md](../history/auth/auth-admin-forced-logout-helper-method-name-policy.md) 를 추가해 read/write 메서드명을 `isAccessAllowed` 와 `revokeUserSessions` 로 그대로 유지한다고 고정했다
- 이유: filter는 최종 allow/deny만, admin API는 user 단위 revoke intent write만 알면 된다. 메서드명까지 구현 세부를 드러내지 않아야 helper 경계가 안정적으로 유지된다

## 278) forced logout session revoke를 기존 `AccessTokenRevocationService` 에 흡수하면 exact-token blacklist와 user-session cutoff 의미가 다시 섞이므로, 새 클래스로 분리하고 composition으로 엮는 편이 낫다
- 문제: helper 이름과 메서드명을 정한 뒤에도 구조를 성급하게 합치면 `revoke(token)` 과 `revokeUserSessions(userKey, cutoffMillis)` 가 같은 서비스에 놓여 책임 경계가 흐려질 수 있다
- 해결: [auth-admin-forced-logout-service-structure-policy.md](../history/auth/auth-admin-forced-logout-service-structure-policy.md) 를 추가해 `UserSessionRevocationService` 를 새 클래스로 두고, 기존 `AccessTokenRevocationService` 와는 composition 관계를 유지한다고 고정했다
- 이유: logout/withdraw exact-token revoke는 이미 안정화된 경계이고, forced logout은 별도 user-session revoke 경계다. 구현 diff를 작게 유지하려면 sibling 서비스 + composition이 가장 안전하다

## 279) forced logout 서비스에 repository/chat cleanup 같은 dependency를 처음부터 많이 넣으면 revoke 핵심 경계가 다시 커지므로, package는 기존 `user.service` 에 두되 생성자 dependency는 Redis/JWT/exact-revoke로 최소화하는 편이 낫다
- 문제: 새 `UserSessionRevocationService` 구조를 정한 뒤에도 package를 새로 뽑거나 `UserRepository`, `ChatSessionCleanupService`, `UserCoreSyncService` 같은 dependency를 한 번에 넣으면 이번 hardening task가 다시 구조 개편으로 번질 수 있다
- 해결: [auth-admin-forced-logout-package-dependencies-policy.md](../history/auth/auth-admin-forced-logout-package-dependencies-policy.md) 를 추가해 package는 `user.service`, 최소 dependency는 `RedisTemplate<String, String>`, `JwtUtil`, `AccessTokenRevocationService` 로 고정했다
- 이유: 1차 forced logout baseline의 본질은 access/refresh revoke다. DB state 변경이나 chat cleanup까지 같이 열지 말고, 기존 auth/user service 층 안에서 최소 dependency로 시작해야 구현 diff와 회귀 범위를 줄일 수 있다

## 280) forced logout 구현을 service skeleton부터 열면 `iatm` claim 계약이 다시 임시 파싱/fallback으로 흘러갈 수 있으므로, `JwtUtil` helper를 먼저 코드로 박는 편이 낫다
- 문제: forced logout 설계를 문서로만 쌓아 두고 service skeleton부터 만들면, 핵심 read path인 `issued-at millis` 비교를 서비스 안에서 claims 직접 파싱이나 임시 fallback으로 처리하게 될 가능성이 컸다
- 해결: [auth-admin-forced-logout-implementation-order.md](../history/auth/auth-admin-forced-logout-implementation-order.md) 를 추가한 뒤, 실제 코드도 그 순서대로 `JwtUtil` 에 access token 전용 `iatm` claim write와 `getIssuedAtMillis(...)` / `getIssuedAtMillisAllowExpired(...)` helper부터 추가했다
- 이유: forced logout의 핵심은 `token issued-at` 과 `user cutoff` 비교다. 이 계약을 util 층에서 먼저 고정해 두어야 이후 `UserSessionRevocationService` 와 `JwtAuthenticationFilter` 가 ad-hoc JWT parsing 없이 같은 기준을 재사용할 수 있다

## 280) forced logout 구현을 service skeleton부터 열면 `iatm` claim 계약이 다시 임시 parsing/fallback으로 흐르기 쉬우므로, `JwtUtil` helper를 먼저 고정하고 그 위에 service를 얹는 순서가 안전하다
- 문제: package/dependency까지 정한 뒤 바로 `UserSessionRevocationService` 클래스를 만들면, 정작 핵심인 `iatm` write/read helper가 없어서 service 안에 claims 직접 파싱이나 TODO fallback이 들어갈 위험이 있다
- 해결: [auth-admin-forced-logout-implementation-order.md](../history/auth/auth-admin-forced-logout-implementation-order.md) 를 추가해 구현 순서를 `JwtUtil helper -> UserSessionRevocationService skeleton -> JwtAuthenticationFilter wiring -> admin API -> tests` 로 고정했다
- 이유: forced logout의 핵심은 token ordering correctness다. 이 기준 claim/helper를 먼저 만들고 나서 service를 얹어야 임시 계약이 줄고 회귀 반경도 작다

## 281) `UserSessionRevocationService` skeleton 단계에서 filter wiring까지 같이 열면 `A006` 노출, legacy token 처리, exact revoke와 cutoff 순서가 한 번에 섞이므로, 지금은 Redis write/read 계약만 먼저 고정하는 편이 낫다
- 문제: `JwtUtil` helper를 추가한 뒤 바로 `JwtAuthenticationFilter` 까지 붙이면, 서비스 자체의 책임(`refresh delete + access-cutoff write + allow/deny read`)이 맞는지와 filter에서 어떤 에러로 수렴하는지를 한 테스트에서 동시에 디버깅하게 된다
- 해결: [UserSessionRevocationService.java](../backend/src/main/java/com/example/welfare/user/service/UserSessionRevocationService.java) 를 새 클래스로 추가하되, 이번 단계에서는 wiring 없이 skeleton만 두고 [UserSessionRevocationServiceTest.java](../backend/src/test/java/com/example/welfare/user/service/UserSessionRevocationServiceTest.java) 로 `exact revoke`, `cutoff before/after`, `legacy token without iatm` 계약만 unit 수준에서 먼저 고정했다
- 이유: forced logout의 read/write core를 서비스 단위로 먼저 고정해 두어야 이후 `JwtAuthenticationFilter` wiring 에서 실패가 나도 원인을 auth gate와 revoke core 중 어디서 찾을지 분리할 수 있다

## 282) forced logout filter wiring을 열 때 admin API write path까지 같이 붙이면 failure 원인이 revoke core인지 auth gate인지 다시 섞이므로, 이번 단계는 service direct call integration으로 auth gate만 먼저 고정하는 편이 낫다
- 문제: `UserSessionRevocationService` skeleton 다음 단계에서 곧바로 `POST /api/admin/users/forced-logout` 까지 열면, old token 차단 실패가 Redis write path 문제인지 `JwtAuthenticationFilter` gate 문제인지 한 번에 뒤엉킬 수 있다
- 해결: [JwtAuthenticationFilter.java](../backend/src/main/java/com/example/welfare/global/config/JwtAuthenticationFilter.java) 와 [SecurityConfig.java](../backend/src/main/java/com/example/welfare/global/config/SecurityConfig.java) 를 먼저 `UserSessionRevocationService` 기반으로 wiring 하고, [AdminSecurityIntegrationTest.java](../backend/src/test/java/com/example/welfare/integration/AdminSecurityIntegrationTest.java) 에서는 service direct call로 cutoff를 기록한 뒤 `old admin access -> 401 / A006`, `relogin access -> 200` 만 먼저 고정했다
- 이유: forced logout read gate가 제대로 서야 이후 admin API write path를 붙여도 디버깅 surface가 작다. write path보다 auth gate를 먼저 닫는 편이 회귀 반경을 더 잘 통제한다

## 283) forced logout write path를 열 때 `userKey` 유효성/존재 확인 없이 무조건 revoke intent를 받아 버리면 운영자가 오타와 성공을 구분하기 어려우므로, 1차 API도 최소한 `400/C001` 과 `404/U002` 를 분리하는 편이 낫다
- 문제: `/api/admin/users/forced-logout` 를 단순 pass-through 로 열면 blank `userKey` 나 존재하지 않는 대상에도 `accepted=true` 가 떨어져, incident/offboarding 상황에서 운영자가 실제 적용 여부를 오해할 수 있다
- 해결: [UserAdminController.java](../backend/src/main/java/com/example/welfare/user/controller/UserAdminController.java) 에서 body `userKey` trim/blank 검증과 `userRepository.findIdByUserKey(...)` 존재 확인을 먼저 수행하고, [AdminSecurityWebMvcTest.java](../backend/src/test/java/com/example/welfare/admin/AdminSecurityWebMvcTest.java) 로 `blank -> 400/C001`, `missing -> 404/U002`, `valid -> 200` 계약을 고정했다
- 이유: forced logout은 운영자 액션이라 “실패를 너무 조용히 성공 처리하지 않는 것”도 중요하다. 최소한의 input/not-found 구분을 둬야 write path를 신뢰할 수 있다

## 284) legacy admin access token smoke를 실제 서명된 JWT로 따로 고정하지 않으면 `A006` 계약이 malformed token(`A001`)과 다시 섞일 수 있으므로, `iatm` 만 빠진 정상 토큰을 직접 발급해 검증하는 편이 낫다
- 문제: 문서에는 `iatm` 없는 legacy admin access token이 forced logout 보호 경계에서 `401 / A006` 으로 떨어진다고 적어 두었지만, 테스트에서 그냥 임의 문자열이나 malformed JWT를 넣으면 실제로는 `INVALID_TOKEN(A001)` 경로만 확인하게 된다
- 해결: [AdminSecurityIntegrationTest.java](../backend/src/test/java/com/example/welfare/integration/AdminSecurityIntegrationTest.java) 에 `uid`, `roles`, `issuedAt`, `expiration`, 서명은 정상이고 `iatm` 만 없는 legacy admin access token 생성 helper를 추가하고, forced logout 이후 보호 API 접근이 `401 / A006` 으로 수렴하는 smoke를 integration baseline에 넣었다
- 이유: rollout 정책의 핵심은 “형식이 망가진 토큰”이 아니라 “old contract token은 보호 경계에서 재로그인 요구 대상”이라는 점이다. 그래서 테스트도 malformed가 아닌 structurally valid legacy token으로 고정해야 제품 의미와 맞는다

## 285) forced logout 운영 증적까지 한 번에 크게 열면 response contract가 내부 ordering detail을 다시 끌고 올라오므로, 현재 phase에선 `cutoffMillis` 를 response가 아니라 log/Redis에만 남기는 편이 낫다
- 문제: forced logout API와 auth gate, legacy token smoke까지 닫고 나면 운영 증적을 더 남기고 싶어지지만, 여기서 `cutoffMillis` 를 바로 response body에 노출하면 내부 revoke ordering 기준값이 외부 API 계약처럼 굳어질 수 있다
- 해결: [auth-admin-forced-logout-audit-scope-policy.md](../history/auth/auth-admin-forced-logout-audit-scope-policy.md) 를 추가해 current phase의 증적 범위를 `response=userKey+accepted`, `server log=userKey+cutoffMillis`, `Redis=current source of truth` 로 고정했다
- 이유: 지금 중요한 건 revoke correctness와 운영 triage 가능성이지, ordering 숫자를 클라이언트 계약으로 끌어올리는 것이 아니다. response는 최소 ack로 두고 detail은 log/Redis에 남겨야 이후 구현 변경 여지도 유지된다

## 286) forced logout 로그 형식을 별도 hidden knowledge로만 두면 운영자가 어디서 `cutoffMillis` 를 봐야 하는지 다시 헤매므로, 현재 smoke/runbook 문서에 최소 grep 포인트까지 올려 두는 편이 낫다
- 문제: audit scope를 `response=minimal`, `log/Redis=detail` 로 정한 뒤에도, 운영 문서에 실제 기대 로그 라인 형식이 없으면 forced logout 실행 후 `cutoffMillis` 를 어디서 확인해야 하는지 사람 기억에 다시 의존하게 된다
- 해결: [runtime-api-smoke-commands.md](./runtime-api-smoke-commands.md) 에 `POST /api/admin/users/forced-logout` smoke 예시와 함께 기대 로그 라인 `[Admin] forced logout 트리거 userKey=<userKey> cutoffMillis=<epochMillis>` 및 간단한 `grep` 확인 절을 추가했다
- 이유: current phase에선 별도 audit UI나 response field를 열지 않으므로, 로그 라인 형식 자체가 운영 증적의 일부다. 최소한 smoke/runbook 문서에 grep 포인트까지 올려 둬야 실제 운영 사용성이 생긴다

## 287) forced logout 로그에 `actor` 를 바로 얹기 시작하면 revoke correctness hardening과 audit 확장이 다시 섞이므로, 현재 phase에서는 `userKey + cutoffMillis` 만 유지하는 편이 낫다
- 문제: smoke/runbook까지 정리하고 나면 운영자가 “누가 눌렀는지”도 바로 로그에 남기고 싶어질 수 있다. 하지만 여기서 actor identifier까지 추가하면 identifier 선택, masking, retention, future audit storage 같은 논점이 다시 같이 열린다
- 해결: [auth-admin-forced-logout-actor-log-policy.md](../history/auth/auth-admin-forced-logout-actor-log-policy.md) 를 추가해 current forced logout 로그 라인은 계속 `[Admin] forced logout 트리거 userKey=<userKey> cutoffMillis=<epochMillis>` 로 유지하고, `actor` 는 future audit reopen 조건으로 미룬다고 고정했다
- 이유: 지금 단계의 핵심은 old/new token ordering correctness와 운영 triage 가능성이다. `actor` 는 중요하지만 별도 audit problem이라, 1차 hardening 범위에 다시 섞지 않는 편이 경계가 더 깔끔하다

## 288) gap-fill 예산 전략을 세운 뒤에도 곧바로 추가 실행을 기본 pending 으로 두면, 운영 기준과 실험 기준이 다시 섞일 수 있다
- 문제: `2 rounds x 20 calls -> 2 rounds x 40 calls -> 95/API catch-up` 같은 예산 전략을 정리한 뒤에도 `stored detail payload coverage 추가 확대` 를 기본 pending 으로 그대로 두면, small-step 실험과 catch-up run 이 모두 “지금 당장 계속 해야 하는 일”처럼 보일 수 있었다
- 해결: [policy-bokjiro-gap-fill-execution-policy.md](../history/policy/policy-bokjiro-gap-fill-execution-policy.md) 에서 current phase의 gap-fill 추가 실행은 routine default가 아니라 수동 catch-up/on-demand 작업으로만 유지한다고 고정했다
- 이유: 전략을 세웠다는 것과 지금 당장 실행을 계속해야 한다는 것은 다르다. 현재는 coverage/fact 증가 효율이 완만하고, 남은 갭의 중심도 payload signal 분포와 soft signal 판단 쪽으로 이동했으므로 기본 진행축을 다른 canonical/source pending 으로 넘기는 편이 더 맞다

## 289) blocked SQL pending 이 여러 개일 때는 “먼저 다시 열 가능성이 높은 축”을 정하지 않으면 계속 보류 문서만 쌓이고 실제 다음 액션이 흐려질 수 있다
- 문제: 현재 `YOUTH_MID stable code mapping SQL`, `GOV24_SERVICE_FIELD / USER_TYPE / BENEFIT_TYPE import/backfill SQL`, `GOV24_SUPPORT_CONDITION full inventory` 가 모두 source-of-truth 부족으로 막혀 있다. 이 상태에서 우선순위를 따로 정하지 않으면 세 항목이 모두 같은 수준의 막힌 pending처럼 남아 실제 다음 액션이 다시 흐려질 수 있었다
- 해결: [policy-normalization-blocked-sql-reopen-priority.md](../history/policy/policy-normalization-blocked-sql-reopen-priority.md) 에서 reopen 우선순위를 `GOV24_* -> GOV24 supportConditions full inventory -> YOUTH_MID` 로 고정했다
- 이유: `Gov24` 는 current canonical onboarding 기준선과 더 직접 연결되고 current API source도 더 명확하다. 반면 `YOUTH_MID` 는 operator-provided codebook 의존도가 높고, 지금도 label-only fallback으로 당분간 유지 가능하므로 reopen 우선순위를 뒤로 두는 편이 더 맞다

## 290) `GOV24_*` SQL reopen의 practical next step은 old endpoint 재검토가 아니라 current dataset page의 Swagger/schema 확보 경로를 먼저 고정하는 것이다
- 문제: `GOV24_*` reopen 우선순위를 앞에 두더라도, 실제로 어디서 source 증적을 확보할지가 모호하면 다시 deprecated `category` 문서나 sample payload 역추론으로 되돌아갈 위험이 있었다
- 해결: [policy-normalization-gov24-schema-acquisition-path.md](../history/policy/policy-normalization-gov24-schema-acquisition-path.md) 에서 practical next step을 `data.go.kr` current dataset page의 Swagger UI 확인 -> `schema.org/DCAT` provenance 확보 -> provider/operator codebook 요청 순서로 고정했다
- 이유: `Gov24_*` SQL을 다시 열려면 “current source를 어디서 봤는가”가 먼저 명확해야 한다. current dataset page는 이미 official entrypoint이고, deprecated endpoint 재활용보다 Swagger/schema export 증적을 먼저 확보하는 편이 재발 방지에 맞다

## 291) current `data.go.kr` page와 `schema.org` metadata를 실제로 다시 봐도, `GOV24_*` finite inventory는 아직 직접 보이지 않는다
- 문제: current dataset page를 official entrypoint로 인정하더라도, 실제로 `serviceField` / `userType` / `benefitType` finite inventory가 page text나 metadata에 직접 보이는지 확인하지 않으면 “일단 current page를 봤다”는 사실만으로 SQL reopen 조건이 충족된 것처럼 오해할 수 있었다
- 해결: [policy-normalization-gov24-swagger-visibility-check.md](../history/policy/policy-normalization-gov24-swagger-visibility-check.md) 에서 current `data.go.kr` page와 `schema.org` metadata를 다시 확인한 결과, entrypoint/provenance 는 분명하지만 field-level finite inventory는 직접 드러나지 않는다고 고정했다
- 이유: practical next action을 좁히려면 “current page 확인”과 “finite inventory 확보”를 같은 단계로 취급하면 안 된다. 이번 단계의 결론은 current page 확인 완료이고, 따라서 다음 액션은 provider/operator codebook 요청 실행으로 넘어가는 편이 맞다

## 292) `GOV24_*` source가 막혀 있을 때는 “요청 스펙이 있다”와 “바로 보낼 수 있는 템플릿이 있다”를 구분해야 다음 액션이 실제로 움직인다
- 문제: 기존 [policy-normalization-gov24-label-source-plan.md](../history/policy/policy-normalization-gov24-label-source-plan.md) 에도 요청 스펙은 있었지만, 실제 제목/본문/판정 기준까지 내려오지 않으면 여전히 “운영자에게 뭘 보내지?” 단계에서 멈출 수 있었다
- 해결: [policy-normalization-gov24-codebook-request-template.md](../history/policy/policy-normalization-gov24-codebook-request-template.md) 에서 요청 제목, long/short 본문 템플릿, sufficient/insufficient 예시, reopen 판정 기준을 따로 고정했다
- 이유: blocked SQL reopen에서는 source 찾는 일 자체보다 “어떤 자료가 오면 reopen 가능한가”를 명확히 적는 편이 더 중요하다. 이번 단계로 `GOV24_*` practical next action은 실제 요청 발송으로 더 좁혀졌다

## 293) `GOV24_SUPPORT_CONDITION` 은 `serviceField/userType/benefitType` 와 같은 요청 템플릿으로 묶기보다, representative subset과 full inventory를 분리한 별도 템플릿이 더 안전하다
- 문제: `GOV24_*` 공통 codebook 요청 템플릿이 생긴 뒤 `supportConditions` 도 같은 템플릿에 그냥 묶어 버리면, representative subset 근거가 있는 상태와 full inventory reopen 조건이 섞여 다시 판정 기준이 흐려질 수 있었다
- 해결: [policy-normalization-gov24-support-condition-request-template.md](../history/policy/policy-normalization-gov24-support-condition-request-template.md) 에서 `supportConditions` full inventory 요청을 label 3종과 분리된 별도 제목/본문/판정 기준으로 고정했다
- 이유: `supportConditions` 는 이미 subset seed가 있고, full inventory reopen의 최소 단위도 label 3종보다 넓다. practical next action을 명확히 하려면 요청은 한 패키지로 보낼 수 있어도 판정 문서는 분리하는 편이 맞다

## 294) `Gov24` 요청을 한 패키지로 보낼 수 있다는 것과, reopen 판정을 한 번에 내릴 수 있다는 것은 다르다
- 문제: label 3종 템플릿과 `supportConditions` 템플릿이 모두 생긴 뒤, 둘을 one package로 보내는 순간 “같은 응답이면 같은 시점에 같이 reopen” 하는 것처럼 오해할 수 있었다
- 해결: [policy-normalization-gov24-request-package-checklist.md](../history/policy/policy-normalization-gov24-request-package-checklist.md) 에서 발송은 one package, 판정은 two tracks(`label 3종` / `supportConditions`) 로 분리한다고 고정했다
- 이유: blocked source 작업에서는 발송 단위와 판정 단위를 일부러 분리해 둬야 실제 응답이 부분적으로만 충분할 때도 한 축만 먼저 reopen할 수 있다. practical next action을 실제 발송/판정 단계로 넘기려면 이 분리가 필요했다

## 295) blocked source 문서를 충분히 내린 뒤에는 “다음에 뭘 할 수 있는가”를 다시 정하지 않으면, 외부 응답이 오기 전까지 문서만 더 쌓이는 상태가 된다
- 문제: `Gov24` source 경로, visibility check, 요청 템플릿, package checklist까지 모두 정리된 뒤에도 다음 active track을 다시 정하지 않으면, blocked SQL 트랙을 더 파는 문서만 계속 추가하면서 실제로는 아무 state change가 없는 구간에 머물 수 있었다
- 해결: [policy-next-active-track-priority.md](../policy/policy-next-active-track-priority.md) 에서 `Gov24` blocked SQL/doc 트랙은 external response boundary까지 이미 내려왔다고 보고, 다음 기본 진행축을 운영/deploy pending 으로 넘긴다고 고정했다
- 이유: practical next action 기준으로는 Docker Compose / DB 계정 / datasource 전환처럼 바로 실행 가능한 운영 pending 이 더 앞선다. blocked SQL 은 source 응답이 오기 전까지는 backlog 로 유지하는 편이 맞다

## 296) “운영으로 바로 갈 수 있다”와 “운영으로 바로 가야 한다”를 같은 의미로 두면 local-first 검증 원칙과 충돌한다
- 문제: blocked source 문서가 external response boundary까지 내려온 뒤 다음 active track을 운영/deploy 로 넘기는 쪽으로 정리했지만, 사용자 기준은 “운영 전에 로컬에서 가능한 모든 구현/검증을 끝낸 뒤 넘어간다” 였다. 이 기준을 문서에 다시 반영하지 않으면 active-track 정책과 실제 진행 원칙이 어긋난 상태로 남을 수 있었다
- 해결: [policy-next-active-track-priority.md](../policy/policy-next-active-track-priority.md) 를 갱신해 다음 기본 진행축을 local-first closeout 으로 다시 고정했다
- 이유: deploy lane은 available 하더라도, local 테스트/스모크/수정 가능성이 남아 있으면 아직 main track이 아니다. practical next action 기준으로는 로컬에서 끝낼 수 있는 것부터 먼저 닫고, 남은 것이 운영/외부 의존뿐일 때만 운영으로 넘어가는 편이 맞다

## 297) local-first 로 방향을 바꾼 뒤에는 “남은 unchecked 항목”과 “지금 로컬에서 실제로 할 수 있는 일”을 다시 분리하지 않으면, phase-plan 상 미완 리스트가 곧바로 next local action처럼 보일 수 있다
- 문제: `phase-plan` 의 unchecked 항목은 대부분 external blocked 또는 ops-only 인데, 이 상태에서 local-first 로 방향만 바꾸고 실제 local actionable set을 다시 적지 않으면, 여전히 `GOV24_* SQL` 이나 운영 전환 항목이 다음 로컬 작업처럼 보일 수 있었다
- 해결: [policy-local-closeout-pending-inventory.md](../policy/policy-local-closeout-pending-inventory.md) 를 추가해 현재 로컬 actionable work를 `auth/session revoke regression`, `PII split-account local smoke`, `education replay smoke`, `runtime API smoke` 로 다시 고정했다
- 이유: local-first 에서 중요한 것은 unchecked 개수보다 “지금 이 머신에서 state change를 만들 수 있는가”다. practical next action을 분명히 하려면 blocked/ops 항목과 closeout 검증 세트를 분리해서 적는 편이 맞다

## 298) `UserSessionRevocationService` 이후 integration fixture가 여전히 `userId-only` access token이나 core datasource 직접 PII write를 쓰고 있으면, 실제 구현 회귀가 없어도 local regression suite가 거짓 음성으로 깨질 수 있다
- 문제: forced logout / revoke gate 이후 integration 테스트 중 일부는 여전히 `jwtUtil.generateAccessToken(userId)` 를 직접 써서 subject에 `userKey` 가 없는 legacy-style token을 만들고 있었고, `UserCoreDualWriteIntegrationTest` 는 reduced-grant split-account 계약과 달리 core `JdbcTemplate` 로 `youth_welfare_pii.user_pii` 를 직접 업데이트하고 있었다. 이 상태에서는 현재 구현이 정상이어도 local closeout regression이 `401` 또는 grant error로 깨질 수 있었다
- 해결: 관련 integration fixture를 current contract에 맞게 `generateAccessToken(userKey, userId)` 로 정리하고, PII write는 `UserPiiReadWriteRepository.upsertUserPii(...)` 경유로 바꿨다. 또 `ChatSessionApiIntegrationTest` 는 전역 `chatMessageRepository.count()` 대신 session-scope assertion으로 좁혀 다른 테스트와의 shared DB 흔들림을 피했다
- 이유: local-first closeout 단계에서는 실제 구현 버그와 오래된 test fixture를 분리하는 게 우선이다. current auth/split-account 계약에 맞는 fixture로 먼저 기준선을 맞춰야 regression suite가 의미를 갖는다

## 299) PII split-account smoke 는 예전 성공 이력만으로 충분하지 않고, auth/session 회귀 정리 뒤 다시 돌려 현재 로컬 조합 기준으로 확인해야 한다
- 문제: local-first closeout 기준에서는 과거 smoke 성공 이력보다 “지금 워크트리, 지금 테스트 fixture, 지금 이미지 빌드 기준으로도 same path가 다시 통과하는가”가 더 중요하다. auth/session fixture를 손본 뒤 `run-local-pii-sync-cutover-smoke.sh` 를 다시 돌리지 않으면, 현재 조합에서 request-path sync 와 withdraw cleanup 이 함께 유지되는지 확신하기 어려웠다
- 해결: `SMOKE_RESET_DB=true APP_HEALTH_TIMEOUT_SECONDS=180 deploy/smoke/run-local-pii-sync-cutover-smoke.sh` 를 다시 실행해 app build -> DB/Redis/app 기동 -> queue migration -> signup/login -> profile update -> queue `SYNCED` -> withdraw cleanup one-shot 경계를 현재 로컬 상태에서 재검증했다
- 이유: local closeout 에서는 “예전에 됐다”보다 “지금도 된다”가 중요하다. 특히 split-account smoke 는 build, compose, migration, request-path sync, withdraw cleanup이 한 번에 엮여 있어 current 기준선 재확인이 필요했다

## 300) `education replay smoke(rule-only)` 는 local policy snapshot/canonical schema가 없는 DB에서 bootRun 실패나 빈 추천으로 흐르기보다, precondition 부족을 먼저 명시적으로 실패시키는 편이 낫다
- 문제: local-first closeout 순서대로 `deploy/smoke/run-local-education-priority-replay.sh` 를 다시 돌렸더니, `.env` 의 legacy `DB_USERNAME=root` 와 unreconciled runtime 계정 상태 때문에 먼저 DB auth가 깨졌고, 이를 정리한 뒤에는 직전 `SMOKE_RESET_DB=true` PII smoke 영향으로 `welfare_services=0`, `service_taxonomies` 미생성 상태라 sample A/B 추천이 빈 결과가 되었다. 이 상태에서 기존 script는 AI trace 2줄을 가정하고 뒤늦게 실패해, 실제 원인이 `local snapshot/schema 부재` 라는 점이 바로 드러나지 않았다
- 해결: `deploy/smoke/run-local-education-priority-replay.sh` 를 수정해 replay 시작 전 `deploy/mysql/reconcile-local-runtime-db-accounts.sh` 를 자동 호출하고, `.env` 의 legacy `root` 계정을 replay 내부에서 `app_core_rw` / `migration_admin` 로 정규화하게 했다. 또 `service_taxonomies` 존재 여부, `welfare_services` 적재 여부, `compat=기타 + youth_major=교육` target row 존재 여부를 precondition으로 먼저 확인해, 현재 로컬 DB처럼 snapshot/schema가 비어 있을 때는 `education replay precondition unmet: ...` 메시지로 조기 종료하도록 바꿨다
- 이유: local closeout 단계에서는 “추천 로직이 실패했다”와 “재생산용 데이터 전제가 비어 있다”를 분리하는 것이 중요하다. known-positive replay를 다시 유효하게 돌리려면 local policy snapshot과 canonical sidecar schema를 먼저 복구해야 하므로, script가 그 부족을 early-fail로 드러내는 편이 triage와 다음 액션 모두에 더 맞다

## 301) `docker-compose.yml` 에서 `env_file: .env` 만 쓰면 shell override가 컨테이너로 전달되지 않아, 로컬 smoke용 secret/runtime override가 필요한 경계에서 계속 base `.env` 값을 보게 된다
- 문제: local runtime API smoke를 돌리려 `AES_SECRET_KEY`, `SECURITY_ADMIN_EMAILS`, `OPENAI_API_KEY` 등을 shell env로 덮어쓴 뒤 `docker compose up -d --force-recreate app` 를 실행했지만, app 컨테이너는 여전히 `.env` 의 빈 `AES_SECRET_KEY` 와 기본값만 들고 올라왔다. 그 결과 `POST /api/auth/signup` 이 `500 / C002` 로 계속 실패해, shell override 자체가 먹지 않는다는 사실을 먼저 분리해야 했다
- 해결: `docker-compose.yml` 의 app `environment:` 블록에 `JWT_SECRET`, `AES_SECRET_KEY`, `OPENAI_API_KEY`, `GMAIL_USERNAME`, `GMAIL_PASSWORD`, `PUBLIC_DATA_PORTAL_API_KEY`, `YOUTH_API_KEY`, `BOKJIRO_API_KEY`, `SECURITY_ADMIN_EMAILS` explicit pass-through 를 추가했다. 이후 같은 shell override로 app을 재생성하자 컨테이너 env가 실제로 바뀌었고, local runtime smoke가 정상 진행됐다
- 이유: `env_file` 은 파일 내용을 그대로 컨테이너에 넣지만, shell env를 임시로 덮어쓸 수 있는 경계는 `environment:` interpolation 쪽이다. local-first closeout 에서는 smoke용 secret/runtime override가 자주 필요하므로, 이 pass-through가 없으면 실제 로컬 검증이 매번 base `.env` 상태에 묶인다

## 302) allowlist 기반 admin 계정은 공개 signup으로 만들 수 없으므로, 로컬 forced logout runtime smoke에서는 “먼저 일반 사용자 생성 -> allowlist 승격 -> 재로그인” 순서가 필요하다
- 문제: local runtime smoke에서 `SECURITY_ADMIN_EMAILS=admin.runtime.smoke@example.com` 를 준 뒤 그 이메일로 공개 signup을 시도했더니 `403 / A007` 이 반환됐다. 이 상태를 모르면 admin API smoke 자체가 막힌 것처럼 보일 수 있었다
- 해결: 로컬 smoke는 일반 사용자 2명을 먼저 생성한 뒤, 그중 한 명을 `SECURITY_ADMIN_EMAILS` 로 승격하도록 app을 재생성하고 다시 로그인하게 바꿨다. 이 방식으로 `POST /api/admin/users/forced-logout` `200`, target old access `401 / A006`, old refresh `401 / A003`, relogin `200` 경계를 실제 runtime에서 확인했다
- 이유: 현재 제품 계약상 allowlist email은 공개 회원가입 대상이 아니다. 따라서 forced logout runtime smoke는 “미리 준비된 admin 계정” 전제를 실제 로컬에선 “일반 사용자 생성 후 allowlist 승격”으로 풀어야 가장 짧고 재현 가능하다

## 303) `service_taxonomies.provision_method_label VARCHAR(100)` 은 온통청년 live payload 기준으로 너무 짧아, canonical sidecar가 붙은 `POST /api/admin/collect/youth` local replay 복구를 직접 막을 수 있다
- 문제: local-first closeout의 마지막 남은 항목인 `education replay` 를 다시 살리려고 로컬 DB에 sidecar schema를 적용한 뒤 `POST /api/admin/collect/youth` 를 실행했더니, `YouthApiClient` 는 `2363건` 을 정상 수집했지만 `DeferredNormalizedPolicySidecarWriter` 의 `INSERT INTO service_taxonomies ... provision_method_label` 경계에서 `Data too long for column 'provision_method_label' at row 1` 가 반복 발생했다. 그 결과 collect는 끝까지 clean success로 닫히지 못했고, known-positive replay에 필요한 local canonical snapshot 복구가 직접 지연됐다.
- 해결: draft sidecar DDL인 [V2026_04_30_01__create_policy_sidecars.sql](./../backend/src/main/resources/db/migration-draft/V2026_04_30_01__create_policy_sidecars.sql) 의 `service_taxonomies.provision_method_label` 타입을 `VARCHAR(100)` 에서 `TEXT` 로 올리고, 로컬 DB에도 같은 `ALTER TABLE ... MODIFY COLUMN provision_method_label TEXT` 를 적용했다. 그 뒤 같은 로컬 snapshot에서 `deploy/smoke/run-local-education-priority-replay.sh` 를 다시 실행해 `SUMMARY_METRIC A_top10_target=0->1 B_top10_target=0->0` 으로 replay가 실제로 복구되는 것을 확인했다.
- 이유: 이 구간은 추천 로직 버그가 아니라 canonical summary DDL 폭 문제였다. local closeout 관점에서는 known-positive replay를 다시 유효화하는 것이 먼저이므로, 수집 payload 자체를 자르기보다 sidecar summary column을 넉넉히 열어 두는 편이 맞다.

## 304) local-first closeout은 “다음 설계를 더 할 수 있는가”가 아니라 “로컬에서 검증 가능한 핵심 경계가 모두 다시 살아 있는가”로 닫아야 한다
- 문제: `auth/session revoke regression`, `PII split-account smoke`, `runtime API smoke`, `education replay smoke` 를 각각 복구한 뒤에도, 문서상 practical next action이 그대로 남아 있으면 여전히 local pending 이 더 있는 것처럼 읽힐 수 있었다.
- 해결: local closeout inventory와 phase-plan을 갱신해 현재 로컬 검증 세트는 모두 통과했고, 남은 미완 항목은 `GOV24_*`, `YOUTH_MID` 같은 external blocked 또는 운영 환경이 있어야 의미가 있는 ops-only 트랙뿐이라고 정리했다.
- 이유: local-first 원칙의 종료 조건은 unchecked 문서 수가 아니라, “이 머신에서 다시 실행 가능한 핵심 경계가 모두 정상작동하는가”다. closeout 세트가 모두 살아 있으면 그 다음부터는 로컬 추가 설계보다 blocked/ops 분류를 유지하는 편이 맞다.

## 305) `api_sync_logs.status` 를 소문자 MySQL `ENUM` 으로 두고 JPA 엔티티는 대문자 `EnumType.STRING` 으로 읽게 두면, collect 자체는 성공해도 완료 로그를 다시 로딩하는 순간 `No enum constant ... running` 으로 로컬 collect closeout이 깨질 수 있다
- 문제: `provision_method_label` 폭 문제를 푼 뒤 `POST /api/admin/collect/youth` 를 다시 검증했더니, 이번에는 sidecar 저장은 진행되지만 완료 단계에서 `No enum constant com.example.welfare.collect.entity.ApiSyncLog.SyncStatus.running` 이 터졌다. 원인은 `api_sync_logs.status` 가 DB에서는 소문자(`running/success/...`) `ENUM` 인데, 엔티티는 `@Enumerated(EnumType.STRING)` 으로 대문자 enum(`RUNNING/SUCCESS/...`) 을 그대로 읽으려 했기 때문이다. 여기에 converter를 넣자 이번에는 Hibernate schema validation 이 `ENUM` 대 `VARCHAR(30)` 타입 불일치로 앱 기동까지 막았다.
- 해결: `ApiSyncLog.status` 는 [ApiSyncLogStatusConverter](./../backend/src/main/java/com/example/welfare/collect/entity/converter/ApiSyncLogStatusConverter.java) 로 소문자 DB 값과 대문자 도메인 enum을 분리해서 읽고 쓰게 바꿨다. 동시에 [V2026_04_23_01__add_api_sync_logs.sql](./../backend/src/main/resources/db/migration/V2026_04_23_01__add_api_sync_logs.sql) 과 [schema.sql](./../backend/src/main/resources/db/schema.sql) 의 `api_sync_logs.status` 타입을 `VARCHAR(30)` 으로 정리하고, 로컬 DB에도 같은 `ALTER TABLE api_sync_logs MODIFY COLUMN status VARCHAR(30)` 를 적용했다. 이후 앱 재기동과 `POST /api/admin/collect/youth` 재실행에서 최신 `api_sync_logs` row가 `success(requested=2363, saved=2363, failed=0)` 로 정상 종료되는 것을 확인했다.
- 이유: 수집 로그 상태값은 DB enum 구현 세부보다 애플리케이션 enum 계약이 더 중요하다. local closeout 기준에서는 “수집 payload는 성공했지만 완료 로그 역직렬화 때문에 전체 요청이 `C002` 로 끝나는” 상태를 남기면 안 되므로, status column도 문자열 기반으로 맞춰 두는 편이 안전하고 예측 가능하다.

## 306) `YOUTH_MID_RAW_ALIAS` 를 별도 term group으로 저장하기 시작한 뒤에도 refresh scope가 `termGroup` 단일값 기준이면, official `YOUTH_MID` 로 정규화된 다음 stale raw alias row가 남을 수 있다
- 문제: broad backend regression으로 `NormalizedPolicySidecarPersistenceIntegrationTest` 를 다시 태웠더니, 이전 저장에서 `YOUTH_MID_RAW_ALIAS` 로 남겨 둔 row가 다음 refresh에서 official `YOUTH_MID` term만 들어와도 지워지지 않았다. `DeferredNormalizedPolicySidecarWriter` 의 term refresh scope가 현재 aggregate에 들어온 `termGroup` 단일값만 기준으로 delete 대상을 잡고 있었기 때문이다.
- 해결: `DeferredNormalizedPolicySidecarWriter` 에서 `YOUTH_MID` 와 `YOUTH_MID_RAW_ALIAS` 를 같은 refresh family로 보도록 바꿔, 둘 중 어느 쪽이 현재 aggregate에 들어와도 이전 alias/official row를 함께 refresh 하게 정리했다.
- 이유: raw alias 보존 정책이 있어도 read-model 기준선은 stale row가 남지 않아야 한다. local closeout 마지막 broad regression에서는 “현재 aggregate 기준으로 canonical term이 clean하게 교체되는가”까지 확인해야 한다.

## 307) local smoke가 shared DB에 남긴 queue row가 있는 상태에서 `UserPiiSyncStatusIntegrationTest` 가 절대 count를 assert 하면, 실제 구현 회귀 없이도 broad regression이 흔들릴 수 있다
- 문제: `UserPiiSyncStatusIntegrationTest` 는 `pending=1`, `failed=2`, `synced=1` 같은 절대 count를 가정하고 있었는데, local PII smoke와 manual run이 남긴 `user_pii_sync_queue` row 때문에 broad backend suite 재실행 시 `syncedCount` 가 더 크게 나와 실패했다. 서비스 구현은 전체 queue 상태를 집계하는 게 맞아서, 테스트가 shared DB state에 너무 민감한 쪽이었다.
- 해결: 테스트 시작 시 baseline `UserPiiSyncStatusResponse` 를 먼저 읽고, 추가한 fixture row만 delta로 검증하도록 바꿨다. 동시에 oldest/latest ordering이 기존 row와 섞이지 않게 pending/failed/synced row에 극단 timestamp를 주고, failed sample 우선순위도 높은 `attemptCount` 로 고정했다.
- 이유: local-first closeout의 마지막 broad regression은 실제 구현 버그를 찾는 단계이지, shared integration DB에 남은 이전 smoke 흔적 때문에 깨지는 가짜 실패를 남기는 단계가 아니다. baseline-delta 방식으로 바꿔야 full suite green이 의미를 갖는다.

## 308) local-first closeout 이 끝난 뒤에도 다음 액션을 “무언가 더 로컬에서 만들기”로 잡으면, 이미 blocked/ops 단계로 넘어간 항목에 불필요한 설계가 다시 쌓일 수 있다
- 문제: broad backend regression까지 green 이 된 뒤에는 실제로 로컬에서 더 닫을 active pending 이 없는데도, 다음 액션을 막연히 “다음 작업 진행”으로만 두면 `GOV24_*`, `YOUTH_MID`, deploy 같은 항목을 다시 로컬 설계 대상으로 오해할 수 있었다.
- 해결: [policy-post-local-closeout-track-split.md](../policy/policy-post-local-closeout-track-split.md) 를 추가해 2026-05-01 기준 남은 항목은 `external blocked` 와 `ops-only` 두 트랙뿐이라고 고정했다. 이 문서에서 blocked 재개 조건과 ops-only 전환 조건도 같이 박아, 이후부터는 새 로컬 구현보다 “외부 응답 대기” 또는 “운영 전환 결정” 중 어느 쪽인지 먼저 해석하도록 정리했다.
- 이유: local-first 원칙의 끝은 “로컬에서 계속 무언가 더 하는 상태”가 아니라, “이제 로컬로는 더 전진하지 않는다”를 명확히 선언하는 것이다. 그래야 blocked 트랙과 운영 트랙이 다시 섞이지 않는다.

## 309) 문서가 많이 쪼개진 상태에서 구현이 먼저 닫히면, 개별 설계 문서의 `future` 표현이 그대로 남아 현재 계약과 문서가 어긋나 보일 수 있다
- 문제: `admin forced logout` 는 이미 구현과 smoke까지 닫혔는데, `auth-admin-forced-logout-*` 문서 다수는 여전히 “future” 전제와 다음 task 흐름으로 읽혀 현재 코드 계약을 바로 확인하기 어렵게 만들었다. README 도 current-state 문서보다 개별 설계 문서를 나열하는 쪽에 가까워, 실제 source of truth 가 어디인지 헷갈릴 여지가 컸다.
- 해결: [documentation-map.md](../documentation-map.md) 를 추가해 `docs/` 를 current source of truth / design history / external blocked / ops-only 로 나눠 읽는 기준을 만들었다. 또 [auth-session-revocation-current-state.md](../auth/auth-session-revocation-current-state.md) 를 추가해 logout / withdraw / allowlist revoke / forced logout 현재 구현을 한 문서에 모았고, `auth-admin-forced-logout-*` 문서에는 design history status note 를 넣어 “배경 문서” 임을 명시했다.
- 이유: 문서 정리는 파일 수를 무조건 줄이는 것보다, “지금 봐야 할 문서” 와 “결정 배경 문서” 를 분리하는 편이 실제 코드와의 불일치를 더 빠르게 줄인다. 구현이 닫힌 뒤에는 current-state 문서가 앞에 서고, 세부 decision 문서는 뒤로 물러나야 한다.

## 310) `policy-normalization-*` 문서군도 구현이 먼저 닫힌 뒤에는 조사/설계 문서와 현재 구현 문서를 분리하지 않으면, canonical sidecar와 추천 브리지의 현재 상태를 파악하는 데 오히려 시간이 더 걸릴 수 있다
- 문제: normalization 문서군은 schema draft, source 조사, drift inventory, blocked request template, replay 기록, recommendation bridge 판단이 한 폴더에 평평하게 쌓여 있었다. 이 상태에서는 `YOUTH` sidecar 저장이나 education scoring bridge처럼 이미 코드에 들어간 내용도, `GOV24_*` codebook 요청처럼 여전히 blocked 인 내용과 같은 무게로 읽혀 현재 상태 판단이 느려질 수 있었다.
- 해결: [policy-normalization-current-state.md](../policy/policy-normalization-current-state.md) 를 추가해 현재 구현된 범위를 `YOUTH canonical sidecar`, `YOUTH_MID_RAW_ALIAS`, `youth_major summary`, `RecommendationCandidateProjection`, `YOUTH 0/0 income pass-through`, `education canonical bonus` 기준으로 다시 묶었다. 또 [documentation-map.md](../documentation-map.md) 와 [README.md](./README.md) 는 normalization current-state 문서를 먼저 가리키도록 갱신했다.
- 이유: normalization 영역은 파일 수를 줄이는 것보다 “현재 구현 상태” 와 “조사/blocked/design history” 를 먼저 분리하는 편이 실질적이다. 코드와 문서의 불일치는 주로 현재 계약을 바로 찾지 못할 때 커지므로, current-state 문서가 앞에 와야 한다.

## 311) 작업 시작 규칙을 프롬프트에 길게 넣으면 컨텍스트를 많이 쓰고, 문서 진입점이 길면 매번 현재 상태보다 문서 구조부터 다시 해석하게 된다
- 문제: 작업 시작 지시문에 현재 단계, 문서 우선순위, Git 규칙, 문서 갱신 규칙을 매번 길게 적으면 컨텍스트 소비가 크고, `README.md` 도 링크를 너무 많이 직접 나열해 처음 보는 사람이 “지금 뭐부터 읽어야 하는가”보다 “어떤 문서가 있나”를 먼저 해석하게 된다.
- 해결: `docs/README.md` 는 짧은 메인 진입 파일로 줄이고, 별도로 [current-state.md](../current-state.md) 와 [work-guide.md](../work-guide.md) 를 추가했다. 이제 시작점은 `current-state -> work-guide -> phase-plan -> github-workflow` 로 고정하고, 상세 구조는 기존 current-state 문서나 [documentation-map.md](../documentation-map.md) 로 내려가게 정리했다. 또한 [github-workflow.md](../github-workflow.md) 에 stacked PR 회피, stale editor 변경 확인, 큰 PR이면 이유를 본문에 적는 규칙을 보강했다.
- 이유: 반복 지시가 길어질수록 실제 작업 컨텍스트보다 메타 규칙이 더 많은 토큰을 차지한다. 짧은 메인 파일 + 현재 상태 파일 + 작업 규칙 파일로 분리해 두면, 이후에는 짧은 명령문만으로도 같은 작업 습관을 재사용할 수 있고 이번처럼 stacked PR, stale buffer, 문서 해석 과부하가 다시 생길 가능성을 줄일 수 있다.

## 312) 메인 진입 파일이 `README` 하나에만 걸려 있으면, “무슨 파일부터 읽어야 하나”와 “프로젝트 메타는 어디 있나”가 다시 섞여 길어진다
- 문제: `README.md` 를 짧게 줄여도, 작업 시작용 진입점과 프로젝트 메타(기술 스택, 버전, 팀 규모, 현재 단계)를 한 파일에 모두 넣으면 메인 파일이 다시 길어지고 역할이 섞인다.
- 해결: 작업 시작용 메인 파일 [start.md](../start.md) 와 프로젝트 메타용 [project-spec.md](../project-spec.md) 를 분리했다. 이제 이후 지시문은 `docs/start.md를 먼저 읽고 작업해줘` 정도로 짧게 줄일 수 있고, 상세 메타는 `project-spec.md` 로 내려가게 정리했다.
- 이유: 시작 파일은 “어디로 들어갈지”만 짧게 보여줘야 하고, 스펙 파일은 “이 프로젝트가 어떤 조건 위에 있는지”를 따로 가져가야 한다. 진입점과 메타를 분리해야 메인 파일이 다시 비대해지지 않는다.

## 313) source adapter 구조가 있어도 saver/raw 저장 경계가 source별 메서드 증설형이면, 신규 source 온보딩 때 adapter만 추가하고 끝낼 수 없어진다
- 문제: collect 진입은 `CollectSourceAdapter` registry 로 공통화돼 있었지만, 실제 저장 경계는 `CollectItemSaver.saveYouth/saveBokjiroCentral/saveBokjiroLocal` 과 `RawApiPayloadService.saveYouthList/saveBokjiroCentralList/saveBokjiroLocalList` 처럼 source별 메서드를 계속 늘리는 구조였다. 이 상태에서는 새 source를 붙일 때 adapter를 추가해도 saver/raw service를 같이 수정해야 해서 “공통 onboarding 구조”가 중간에서 다시 깨진다.
- 해결: `CollectItemSaver` 에 `SaveCommand` 기반 공통 `save(...)` 엔트리를 추가하고, `RawApiPayloadService` 에도 `saveList(sourceType, sourceId, payload)` 공통 엔트리를 추가했다. 기존 source별 래퍼는 호환용으로 남기되, 실제 `Youth/BokjiroCentral/BokjiroLocal` adapter는 모두 새 공통 엔트리만 타도록 바꿨다. 또 `CollectSource -> WelfareService.SourceType` 매핑을 `CollectSource` 쪽으로 올려 adapter가 개별 enum을 다시 하드코딩하지 않게 정리했다.
- 이유: 지금 단계에서 가장 효과가 큰 1차 리팩터링은 canonical schema 전체를 뒤집는 것이 아니라, 신규 source 온보딩 시 가장 먼저 부딪히는 saver/raw 메서드 증설을 끊는 것이다. 이렇게 해 두면 다음 단계에서는 `detail/backfill` capability 분리와 `NormalizedPolicyAggregate` source-neutral 정리로 이어가기 쉬워진다.

## 314) `detail/backfill` 경로가 복지로 전용 switch와 메서드 이름에 묶여 있으면, list collect를 공통화해도 source onboarding은 detail 단계에서 다시 멈춘다
- 문제: `BokjiroDetailCollectService` 는 중앙/지자체를 직접 switch 하며 fetch했고, `NormalizedPolicySidecarBackfillService` 도 `backfillBokjiroListSidecars/backfillBokjiroDetailSidecars` 내부에서 sourceType별 DTO 역직렬화와 aggregate 생성을 직접 분기했다. 그래서 collect adapter/save 경계를 정리한 뒤에도, detail/backfill 단계에서는 새 source마다 다시 service 본문을 뜯어야 하는 상태가 남아 있었다.
- 해결: 두 서비스 모두 외부 API는 유지한 채 내부를 source capability registry 기반으로 바꿨다. `BokjiroDetailCollectService` 는 source별 `fetchWithStatus` / `toAggregate` capability 맵을 만들고, target 조회/예산 배분/상세 저장 루프는 capability 순회로 처리하게 정리했다. `NormalizedPolicySidecarBackfillService` 도 list/detail aggregate 재생성 함수를 source capability로 등록하고, 기존 `backfillBokjiro*` 래퍼는 공통 `backfillListSidecars/backfillDetailSidecars` 위에 올렸다.
- 이유: 지금 필요한 것은 엔드포인트 이름을 일반화하는 것보다, source 추가 시 바뀌는 코드를 service 본문 switch가 아니라 capability 등록 한 곳으로 몰아 넣는 것이다. 이 단계를 먼저 해 둬야 이후 `NormalizedPolicyAggregate` 를 source-neutral contract로 정리해도 연결점이 분산되지 않는다.

## 315) canonical aggregate summary가 youth/gov24 필드를 직접 들고 있으면, 새 source 하나를 붙일 때마다 aggregate 스키마와 writer 계약부터 다시 흔들린다
- 문제: `NormalizedPolicyAggregate.TaxonomySummary` 는 `youthMajor`, `youthMid`, `gov24ServiceField`, `gov24UserType`, `gov24BenefitType` 를 직접 필드로 들고 있었고, `DeferredNormalizedPolicySidecarWriter` 도 그 필드 이름을 바로 읽고 있었다. 이 상태에서는 source onboarding을 공통 capability로 옮겨도, 새 source에 요약 필드가 하나 더 필요해지는 순간 aggregate record와 writer 파라미터 매핑을 다시 수정해야 했다.
- 해결: `TaxonomySummary` 는 `compatUnifiedCategory`, `provisionMethod`, `summaryLabels`, `authority`, `confidence` 만 남기고, source별 summary 값은 generic `summaryLabels` 맵으로 옮겼다. `WelfareServiceMapper` 는 `YOUTH_MAJOR`, `YOUTH_MID` 를 이 맵에 채우고, `DeferredNormalizedPolicySidecarWriter` 는 필요한 summary key(`YOUTH_MAJOR`, `YOUTH_MID`, `GOV24_*`)를 맵에서 꺼내 sidecar summary 컬럼으로 매핑하게 바꿨다.
- 이유: sidecar DB 스키마까지 지금 당장 일반화하는 건 범위가 크지만, aggregate 계약부터 source-neutral 하게 줄여 두면 이후 source 추가는 “필요한 summary key를 mapper가 채운다” 수준으로 내려간다. 즉 현재 단계에서 가장 효과적인 정리는 aggregate DTO를 generic label carrier로 바꾸는 것이다.

## 316) 작업을 작은 task로 나눠도 PR까지 같은 크기로 잘라 버리면, 기록은 잘게 남아도 리뷰 단위가 너무 잘게 쪼개질 수 있다
- 문제: 기존 GitHub 작업 규칙은 커밋을 작은 task 단위로 만든다는 원칙은 있었지만, PR은 어떤 단위로 묶는지가 충분히 분리돼 있지 않았다. 이 상태에서는 작은 task 하나 끝날 때마다 바로 PR을 올리는 식으로 해석될 수 있고, 반대로 여러 다른 문제를 한 PR에 섞어도 기준이 흐려질 수 있다.
- 해결: `docs/github-workflow.md` 에 `task / 커밋 / PR 단위` 섹션을 추가해, 작은 task는 작은 커밋으로 남기고 여러 작은 커밋이 같은 문제/같은 목표를 닫을 때 PR로 묶는다고 명시했다. 또 브랜치/커밋/PR 섹션에도 각각 `커밋은 작은 변경 기록`, `PR은 큰 해결 단위 전달` 원칙을 보강했다.
- 이유: 로컬 작업 흐름에서는 세밀한 커밋 이력이 유용하지만, 리뷰 흐름에서는 문제 해결 단위로 적당히 묶인 PR이 더 읽기 쉽다. 둘을 같은 크기로 강제하지 않고 역할을 분리해야 실제 협업 속도가 좋아진다.

## 317) recommendation retrieval이 `YOUTH` 이름과 runtime heuristic에 직접 묶여 있으면, canonical read-model을 붙여도 source-neutral 경계가 retrieval 단계에서 다시 깨진다
- 문제: `WelfareServiceRepository.findCandidates*` 는 `YOUTH` 일 때만 `min_income=0 && max_income=0` 을 미지정 sentinel로 취급했고, `RetrievalService` 는 후보를 가져온 뒤에도 매번 `YouthPolicyFilter.isYouthRelevant()` 를 직접 호출해 청년 관련성을 다시 계산했다. 이 구조에서는 source-neutral aggregate/read-model을 이미 만들어도 retrieval 경계가 여전히 source 이름과 youth heuristic을 직접 알고 있어, 새 source나 canonical 백필 상태를 recommendation path가 제대로 활용하지 못했다.
- 해결: repository query는 `0/0 income` 을 source와 무관한 미지정 sentinel로 바꿨고, retrieval 후처리는 canonical `RecommendationCandidateProjection.youthRelevant` 를 우선 사용하도록 수정했다. projection이 없는 경우에만 `searchYouthRelevant` 플래그와 `YouthPolicyFilter` fallback heuristic을 사용하게 해 hardcoded youth 판단을 recommendation runtime hot path에서 한 단계 뒤로 밀었다. 동시에 `RecommendationRegionQueryIntegrationTest` 와 `RetrievalServiceTest` 로 source-neutral `0/0 pass-through` 와 projection 우선 relevance 동작을 고정했다.
- 이유: source-neutral 전환은 DTO 이름을 바꾸는 것보다 “어느 레이어가 source 의미를 직접 아는가”를 줄이는 게 더 중요하다. repository semantics와 retrieval audience filter를 먼저 정리하면, 남은 source 특례는 scoring/read-model 내부의 좁은 영역으로 격리돼 후속 리팩터링 반경이 훨씬 작아진다.

## 318) recommendation scoring이 projection을 이미 들고 있어도 청년 bonus와 특수 대상 판정을 raw entity/tag heuristic으로만 계산하면, canonical bridge를 붙인 효과가 scoring 단계에서 다시 약해진다
- 문제: `RuleScoringService` 는 retrieval이 `RecommendationCandidateProjection` 을 함께 넘기기 시작한 뒤에도, 청년 관련 bonus는 여전히 `YouthPolicyFilter.relevanceBonus(service, tags)` 로만 계산했고, 특수 대상 mismatch도 `title/description/supportContent/lifeStage + legacy tags` 에 대한 문자열 스캔으로만 판정했다. 이 상태에서는 canonical read-model이 있어도 scoring hot path가 projection을 부수 입력으로만 보고, source 문구/태그 유무에 다시 크게 의존하게 된다.
- 해결: `RecommendationCandidateProjection` 에 `audienceRelevanceBonus`, `specialTargetBuckets` 를 추가하고, `CanonicalRecommendationReadModelRepository` 가 `title/summary/minAge/maxAge/taxonomy terms` 기반으로 이 값을 미리 조립하게 바꿨다. `RuleScoringService` 는 이제 projection이 있으면 audience bonus와 special-target signal/mismatch를 projection 우선으로 사용하고, projection이 없을 때만 기존 `YouthPolicyFilter` 와 raw text heuristic을 fallback으로 사용한다. beneficiary bucket의 기존 `+10` 계약은 유지하도록 special bonus와는 분리했고, repository/service 테스트로 이 경계를 고정했다.
- 이유: canonical 전환은 “projection을 읽는다”만으로 끝나지 않고, 실제 점수 계산이 그 projection을 1급 입력으로 써야 의미가 있다. 다만 이번 단계에서는 점수 공식을 바꾸지 않고 입력 경계만 이동해 회귀 반경을 좁히는 편이 안전하다.

## 319) recommendation matcher와 response가 마지막까지 entity의 `unifiedCategory` 문자열을 직접 읽고 있으면, retrieval/scoring만 canonical bridge를 붙여도 priority와 응답 경계에서 다시 legacy compat 직독으로 돌아간다
- 문제: `DefaultPriorityMatcher` 는 projection이 있어도 결국 `compat` 문자열 label을 직접 switch로 비교했고, `RecommendationResponse` 는 저장된 `UserRecommendation -> WelfareService.unifiedCategory` 를 그대로 응답에 실었다. 이 구조에서는 추천 본체가 projection을 함께 hydrate 하더라도, priority weight와 response category는 여전히 entity direct read에 기대어 “read-model을 만들었지만 마지막 출력 경계에서는 안 쓴다”는 어색한 상태가 남는다.
- 해결: `RecommendationCandidateProjection` 에 `priorityBuckets` 를 추가하고, `CanonicalRecommendationReadModelRepository` 가 `compat_unified_category` 를 우선순위 코드 버킷(`HOUSING`, `JOB`, `EDUCATION` 등)으로 한 번 더 조립하게 했다. `DefaultPriorityMatcher` 는 이제 projection bucket이 있으면 그 값을 우선 사용하고, compat 문자열 switch는 fallback으로만 남긴다. 동시에 `RecommendationController` 가 recommendation 응답 생성 전에 projection을 다시 hydrate하고, `RecommendationResponse` 는 `projection.unifiedCategoryCompat` 를 우선 사용해 response의 `unifiedCategory` 가 entity raw field보다 read-model 경계를 먼저 따르도록 바꿨다.
- 이유: current contract상 response `unifiedCategory` 의미 자체는 아직 compat여야 한다. 따라서 이번 단계에서 중요한 건 category meaning을 바꾸는 게 아니라, 그 compat 의미를 어디에서 읽느냐를 canonical read-model 쪽으로 이동시키는 것이다. 이렇게 해야 이후 policy/search/detail response bridge를 열 때도 recommendation path만 따로 legacy direct read에 남지 않는다.

## 320) recommendation만 projection compat를 우선 읽고 policy/search/detail/ranking/bookmark 응답이 계속 entity `unifiedCategory` 를 직접 읽으면, 같은 compat contract인데도 API마다 category 출처가 달라져 read-model 전환이 반쯤만 끝난 상태로 남는다
- 문제: recommendation response는 이미 `projection.unifiedCategoryCompat` 를 우선 읽도록 옮겼지만, `PolicySummaryResponse`, `PolicyDetailResponse`, `PolicyRankingResponse`, 북마크 응답은 여전히 `WelfareService.unifiedCategory` 를 직접 읽고 있었다. 이 상태에서는 표면 계약은 모두 “compat category” 로 같아 보여도, recommendation만 read-model을 보고 나머지 정책 읽기 API는 entity raw field를 보는 이중 구조가 남아 이후 drift나 backfill 상태 차이를 한 번에 설명하기 어려워진다.
- 해결: policy DTO 3종에 projection optional 입력을 추가하고, `PolicyService` / `PolicySearchService` / `PolicyRankingService` / `UserService.getBookmarks` 가 응답 직전에 `CanonicalRecommendationReadModelRepository` 로 projection을 hydrate해서 `unifiedCategoryCompat` 를 우선 넘기도록 바꿨다. 즉 response meaning은 그대로 compat지만, 주요 읽기 API의 category source를 read-model 우선으로 맞췄다.
- 이유: current phase에서 중요한 건 “응답 category를 canonical로 바꾼다”가 아니라, 같은 compat contract를 어디에서 읽는지 일관되게 맞추는 것이다. recommendation 밖의 읽기 API도 read-model 경계를 먼저 따르게 해야 이후 canonical bridge 확대나 entity field 축소를 더 좁은 반경으로 진행할 수 있다.

## 321) recommendation hot path가 대부분 projection 우선으로 옮겨진 뒤에도 AI prompt와 education helper가 마지막까지 entity/raw summary 조합을 직접 읽으면, canonical bridge의 끝단에 작은 우회로가 남는다
- 문제: `RealtimeAiGateway` 는 prompt에 정책 분류를 넣을 때 여전히 `service.getUnifiedCategory()` 를 직접 사용했고, `RuleScoringService` 의 education narrow experiment도 `compat=기타 + youth_major=교육` 조합을 helper 안에서 직접 비교했다. 이 상태에서는 retrieval/scoring/response가 projection 우선으로 바뀌어도, AI 입력과 실험 helper는 여전히 entity/raw summary 조합에 기대는 마지막 우회 경로로 남는다.
- 해결: `ScoredCandidate` 가 `RecommendationCandidateProjection` 을 함께 들고 다니도록 바꾸고, `RuleScoringService` 가 candidate 생성 시 projection을 같이 주입했다. `RealtimeAiGateway` 는 이제 prompt category를 `candidate.projection.unifiedCategoryCompat` 우선으로 읽고, `CanonicalRecommendationReadModelRepository` 는 `educationPriorityBoostEligible` 를 projection 단계에서 미리 계산한다. `RuleScoringService` 의 education experiment helper도 이 projection-derived flag를 우선 사용하고, raw `compat+youthMajor` 비교는 fallback으로만 남겼다.
- 이유: canonical 전환의 마지막 품질은 “핫패스 전체가 같은 read-model 의미를 보느냐”에 달려 있다. prompt와 experiment helper까지 projection-derived 입력으로 맞춰야, 이후 남는 문제를 source normalization/bridge policy 자체로 좁혀서 볼 수 있다.

## 322) policy 읽기 경계와 sidecar writer가 각자 source 문자열/대표 시스템 매핑을 따로 들고 있으면, 지원 source 목록이 늘어날 때 같은 수정이 여러 레이어에 다시 퍼진다
- 문제: `PolicyService` 와 `PolicySearchService` 는 각각 `YOUTH`, `BOKJIRO_CENTRAL`, `BOKJIRO_LOCAL` 문자열을 직접 switch 하며 요청 파라미터를 정규화했고, `DeferredNormalizedPolicySidecarWriter` 도 aggregate source type을 다시 `YOUTH/BOKJIRO` 대표 시스템으로 직접 매핑하고 있었다. 이 구조에서는 새 source를 추가하거나 source 대표 시스템 의미가 바뀔 때, 읽기 API와 sidecar writer가 서로 다른 switch를 따로 수정해야 해 source-neutral 정리 이후에도 catalog가 흩어진 상태로 남는다.
- 해결: `WelfareSourceTypeSupport` 유틸을 추가해 source 문자열 정규화, `WelfareService.SourceType` 변환, aggregate/entity 공통 `primarySourceSystem` 매핑을 한 곳으로 모았다. `PolicyService`, `PolicySearchService`, `DeferredNormalizedPolicySidecarWriter` 는 이제 같은 catalog를 사용하고, writer의 summary label 주입도 binding 리스트를 통해 한 곳에서 적용되게 정리했다. 동시에 `WelfareSourceTypeSupportTest` 로 entity/aggregate enum 양쪽 매핑을 고정했다.
- 이유: source-neutral 구조에서 중요한 것은 “source-specific switch가 0개”가 아니라 “지원 source 목록과 대표 의미를 어디서 관리하는가가 한 군데로 수렴하는가”다. 가장 자주 호출되는 읽기 경계와 writer 경계부터 같은 catalog를 보게 만들어야 이후 source 추가 시 수정 지점을 예측 가능하게 줄일 수 있다.

## 323) detail/backfill 경로가 capability registry를 도입한 뒤에도 예산/호출 관측치와 wrapper source 목록이 계속 `central/local` 하드코딩이면, 내부 구조는 공통화돼도 운영 관측 정보가 예전 2-source 전제에 묶여 남는다
- 문제: `BokjiroDetailCollectService.collectBokjiroDetailsResult()` 는 실행 메타데이터를 `centralBudget/localBudget`, `centralCalls/localCalls` 로 고정해 만들고 있었고, `NormalizedPolicySidecarBackfillService.backfillBokjiro*` 래퍼도 내부에서 다시 `BOKJIRO_CENTRAL`, `BOKJIRO_LOCAL` 목록을 직접 적고 있었다. 이 상태에서는 source capability registry가 이미 도입됐더라도, 새 source를 추가하면 collect/backfill observability 와 wrapper 내부 목록에서 다시 같은 수정이 반복된다.
- 해결: detail collect metadata를 `sourceBudgets`, `sourceCalls` 맵 구조로 바꾸고, log도 `sourceType=calls` 요약을 generic하게 남기도록 정리했다. sidecar backfill 래퍼는 이제 capability registry에서 `supportsList` / `supportsDetail` 인 source를 모아 공통 backfill 메서드로 넘긴다. 기존 external method 이름은 유지해 admin/API 경계는 건드리지 않았다.
- 이유: source-neutral 정리는 “실제 fetch/save path만 registry를 쓴다”로 끝나지 않는다. 실행 후 남는 budget/call observability 와 wrapper source selection도 같은 registry를 기준으로 따라가야 이후 source 추가 시 수정 지점이 다시 늘어나지 않는다.

## 324) education canonical priority 실험이 projection flag를 이미 계산하고도 runtime에서 다시 raw `compat + youthMajor` 조합을 보면, canonical bridge 정책이 read-model이 아니라 scoring helper 구현 세부에 묶여 남는다
- 문제: `CanonicalRecommendationReadModelRepository` 는 이미 `educationPriorityBoostEligible` 를 projection 단계에서 계산하고 있었지만, `RuleScoringService` 는 실험 bonus를 줄 때 `projection.educationPriorityBoostEligible()` 외에 `projection.unifiedCategoryCompat() == 기타 && projection.youthMajorLabel() == 교육` 조건을 다시 직접 비교하고 있었다. 이 상태에서는 canonical major bridge 실험 입력이 “projection 결과” 하나로 닫히지 않고, scoring runtime이 raw summary 의미를 다시 아는 구조가 남는다.
- 해결: `RuleScoringService` 의 education experiment helper는 이제 `projection.educationPriorityBoostEligible()` 만 본다. 테스트도 raw `compat/youthMajor` 값이 education처럼 보이더라도 projection flag가 false면 bonus를 주지 않는 케이스로 바꿔, runtime fallback 제거를 고정했다.
- 이유: canonical bridge 정책은 남을 수 있어도, 그 정책을 어디서 계산하는지는 한 곳이어야 한다. projection 단계에서 한 번 계산한 값을 scoring이 그대로 소비하게 해야 이후 source-neutral 리팩터링에서 “정책”과 “runtime 구현 디테일”을 분리해 볼 수 있다.

## 325) compat category bridge가 writer/read-model/matcher에 각각 별도 switch로 남아 있으면, source-neutral 구조로 바뀐 뒤에도 category 의미 변경이 여러 레이어에 중복 반영돼 drift가 생기기 쉽다
- 문제: `DeferredNormalizedPolicySidecarWriter` 는 compat label을 sidecar code로 바꿀 때 자체 switch를 사용했고, `CanonicalRecommendationReadModelRepository` 는 같은 label을 priority bucket으로 바꿀 때 또 다른 switch를 썼으며, `DefaultPriorityMatcher` 도 projection bucket이 없을 때 compat label을 다시 직접 비교하고 있었다. 이 상태에서는 source 자체보다 `compat bridge 의미` 가 흩어진 상태라, category 추가/수정 시 writer/read-model/runtime matcher를 따로 건드려야 한다.
- 해결: `CompatCategorySupport` 를 추가해 compat label -> compat code / priority bucket 해석을 한 곳으로 모았다. writer는 sidecar code 저장 시, read-model은 priority bucket 조립 시, matcher는 fallback 판단 시 모두 이 support를 사용하게 바꿨다. 테스트도 support 자체를 별도 unit test로 고정했다.
- 이유: source-neutral 구조에서 남아도 되는 legacy는 “bridge policy” 자체이지, 그 bridge를 여러 레이어가 제각각 해석하는 중복 구현이 아니다. compat bridge 의미를 한 군데로 모아야 이후 남은 변화가 source 문제인지 bridge 정책 문제인지 빠르게 분리해 볼 수 있다.

## 326) detail collect와 sidecar backfill이 같은 복지로 source binding을 각자 다시 등록하면, capability registry를 도입해도 source 추가 시 수정 지점이 여전히 두 군데로 남는다
- 문제: `BokjiroDetailCollectService` 와 `NormalizedPolicySidecarBackfillService` 는 둘 다 `BOKJIRO_CENTRAL`, `BOKJIRO_LOCAL` 를 sourceType별로 직접 등록하고 있었지만, fetch/list-parse/detail-aggregate 조합이 사실상 같은 복지로 binding 의미를 반복하고 있었다. 이 상태에서는 source-neutral 구조를 계속 밀어도 “등록 한 곳만 바꾸면 되는가”라는 기준이 detail/backfill 사이에서 다시 깨진다.
- 해결: `BokjiroSourceBinding` support를 추가해 복지로 source별 `sourceType`, detail fetch, list aggregate 변환, detail aggregate 변환을 한 곳으로 모았다. detail collect와 sidecar backfill은 이제 이 binding enum을 순회해 capability map을 만든다. 테스트는 기존 `BokjiroDetailCollectServiceTest`, `NormalizedPolicySidecarBackfillServiceTest` 로 회귀를 고정했다.
- 이유: 지금 단계에서 중요한 것은 새로운 source abstraction을 더 만드는 것보다, 이미 두 서비스가 공유하고 있던 “복지로 source binding 지식”을 한 군데로 수렴시키는 것이다. 이렇게 해야 이후 source 추가가 생겨도 detail/backfill registration이 동시에 따라가며 drift를 줄일 수 있다.

## 327) list source adapter, saver, raw 저장이 같은 source 등록 정보를 각자 부분적으로 다시 만들면, source 추가 시 “수집은 되는데 raw 저장/aggregate 저장 중 한 군데가 빠지는” 식의 drift가 다시 생긴다
- 문제: `YouthCollectSourceAdapter`, `BokjiroCentralCollectSourceAdapter`, `BokjiroLocalCollectSourceAdapter` 는 각각 `sourceType`, `sourceId`, entity mapper, region/tag mapper, normalized aggregate mapper를 직접 조합해 `SaveCommand` 를 만들고 있었고, `CollectItemSaver` 와 `RawApiPayloadService` 도 source별 wrapper를 따로 유지하고 있었다. 이 구조에서는 신규 list source를 붙일 때 adapter/save/raw 경계가 공통화된 것처럼 보여도 실제 등록 정보는 여전히 여러 클래스에 분산돼 drift 가능성이 남는다.
- 해결: `ListCollectSourceBinding` / `ListCollectSourceBindings` 를 추가해 list source별 `sourceType`, `sourceId` 추출, entity/region/tag/aggregate mapper를 descriptor로 묶었다. 세 list adapter는 이제 이 binding으로 raw 저장과 saver 호출을 처리하고, `CollectItemSaver` 도 binding 기반 generic `save(...)` 를 제공한다. `RawApiPayloadService` 도 binding 기반 list raw 저장 엔트리를 추가했고, `ListCollectSourceBindingsTest` 로 source identity 일관성을 고정했다.
- 이유: source-neutral 구조의 핵심은 “공통 API를 만든다”보다 “source registration 자체가 한 군데에 모여 있는가”다. list source descriptor를 도입해야 adapter/save/raw가 같은 등록부를 공유하게 되고, 이후 source 추가 시 빠지는 경로를 줄일 수 있다.

## 328) `WelfareServiceMapper` 안에 compat category switch와 복지로 beneficiary whitelist가 같이 박혀 있으면, source registration을 바깥으로 모아도 실제 정규화 규칙은 mapper 클래스 하나에 다시 뭉쳐 남는다
- 문제: `WelfareServiceMapper` 는 온통청년/복지로 compat category 매핑을 각각 private switch로 들고 있었고, 복지로 상세 대상자 bucket도 `기초생활수급자`, `차상위계층` 추출 whitelist를 mapper 내부 helper로 직접 유지하고 있었다. 이 상태에서는 adapter/save/raw registration이 support/binding으로 이동해도, 실제 source 규칙 테이블은 여전히 mapper 구현 세부에 박혀 있어 새 source 규칙 추가나 정책 조정 시 mapper를 직접 뒤져야 한다.
- 해결: `CollectCategorySupport` 를 추가해 온통청년 major label과 복지로 관심주제 첫 label을 compat category로 정규화하는 규칙을 한 곳으로 모았고, `BokjiroNormalizationSupport` 에 beneficiary whitelist 추출을 옮겼다. `WelfareServiceMapper` 는 이제 DTO별 entity 분기와 aggregate 조립만 담당하고, 복지로 중앙/지자체 taxonomy term 조립도 공통 `bokjiroTerms(...)` helper로 합쳤다. 새 support 두 개는 별도 unit test로 고정했다.
- 이유: source-neutral 구조는 source-specific 로직을 “없애는 것”보다 “규칙 테이블이 어디에 사는지”를 분리하는 편이 더 중요하다. mapper가 source DTO wiring만 남기고 규칙은 support로 밀려야 이후 새 source 추가와 기존 규칙 수정이 서로 덜 얽힌다.

## 329) mapper와 sidecar writer가 같은 canonical 식별자를 각자 문자열로 들고 있으면, source-neutral 구조가 되어도 term/fact 계약 변경 시 두 레이어가 쉽게 어긋난다
- 문제: `WelfareServiceMapper` 는 `YOUTH_MID`, `YOUTH_KEYWORD`, `BK_AGE_ELIGIBILITY`, `BOKJIRO_RULE_APPLY_END_DATE` 같은 canonical term/fact 식별자를 private 상수와 literal string으로 섞어 쓰고 있었고, `DeferredNormalizedPolicySidecarWriter` 도 `YOUTH_MAJOR`, `YOUTH_MID`, `YOUTH_MID_RAW_ALIAS` 를 다시 자기 상수로 들고 있었다. 이 상태에서는 규칙 내용은 support로 분리돼도, canonical schema 식별자 계약은 mapper/writer가 제각각 알고 있어 이름 변경이나 신규 항목 추가 때 drift가 다시 생긴다.
- 해결: `NormalizationKeySupport` 를 추가해 youth/bokjiro summary key, term group, fact group, fact code, fact merge key와 official youth mid label 판정, refresh scope 규칙을 한 곳으로 모았다. `WelfareServiceMapper` 는 youth term/fact 생성과 youth-mid 분류에 이 support를 사용하고, `DeferredNormalizedPolicySidecarWriter` 도 youth summary key 및 refresh scope 판단을 같은 support로 바꿨다. support 자체는 `NormalizationKeySupportTest` 로 고정했다.
- 이유: source-neutral 구조에서 canonical contract는 source별 규칙만큼 중요하다. 규칙 테이블과 식별자 카탈로그를 따로 관리해야 이후 새 source onboarding이나 schema 조정 때 “의미 변경”과 “이름 변경”을 분리해서 다룰 수 있다.

## 330) `sourceField` 문자열과 evidence 우선순위가 mapper, fact merge, read-model heuristic에 따로 박혀 있으면, canonical 식별자를 모아도 “어느 원본 필드에서 왔는가” 의미가 레이어마다 다시 갈라진다
- 문제: `WelfareServiceMapper` 는 `servDgst`, `targetDetail/selectionCriteria`, `aplyYmd` 같은 source-field 문자열을 term/fact 생성에 직접 넘기고 있었고, `NormalizedFactMergeSupport` 는 다시 `targetDetail -> selectionCriteria -> servDgst -> applyMethodDetail -> supportDetail` 우선순위를 별도 map으로 들고 있었으며, `CanonicalRecommendationReadModelRepository` 도 beneficiary term을 판정할 때 `targetDetail/selectionCriteria` literal string 비교를 직접 하고 있었다. 이 상태에서는 source-neutral 계약 이름은 같아도, evidence semantics 자체는 collect와 recommendation이 따로 관리하게 된다.
- 해결: `NormalizationKeySupport` 에 source-field 상수와 composite field 상수, priority helper, token membership helper를 추가했다. `WelfareServiceMapper` 는 youth/bokjiro term/fact sourceField를 이 catalog로 채우고, `NormalizedFactMergeSupport` 는 source-field priority 판단을 support helper로 위임하며, `CanonicalRecommendationReadModelRepository` 는 beneficiary raw term 판정을 composite source-field token membership 기준으로 바꿨다. 관련 helper는 `NormalizationKeySupportTest` 로 고정했다.
- 이유: source-neutral 구조에서 실제 품질을 좌우하는 건 단순한 term/fact 이름뿐 아니라 “그 근거가 어느 원본 필드에서 왔는가”다. evidence semantics를 한 곳으로 모아야 detail/list merge 정책과 read-model heuristic이 같은 canonical provenance를 보게 된다.

## 331) recommendation read-model repository가 청년 relevance, beneficiary, special target, education boost 규칙을 내부 private helper로 전부 들고 있으면, source-neutral collect 경계가 정리돼도 추천 규칙은 repository 구현 파일 하나에 다시 뭉친다
- 문제: `CanonicalRecommendationReadModelRepository.MutableProjection` 는 `audienceRelevanceBonus`, beneficiary detail term 판정, special target bucket 추출, education priority boost eligibility를 모두 내부 helper로 계산하고 있었고, `RuleScoringService` 는 beneficiary/special-target bucket 이름을 다시 자기 상수로 들고 있었다. 이 상태에서는 read-model row loading과 heuristic policy가 한 클래스에 섞여 있어, 규칙 조정이나 재사용이 필요할 때 repository를 직접 수정해야 하고 scoring과의 bucket contract도 중복될 수 있다.
- 해결: `RecommendationProjectionHeuristicSupport` 를 추가해 beneficiary bucket 판정, 청년 relevance bonus, special target bucket 추출, education priority boost eligibility와 bucket 이름을 한 곳으로 모았다. `CanonicalRecommendationReadModelRepository` 는 이제 row를 projection set에 적재한 뒤 heuristic support를 호출해 projection 파생값을 만들고, `RuleScoringService` 도 beneficiary/special-target bucket 상수를 같은 support에서 참조한다. support 자체는 별도 unit test로 고정했다.
- 이유: source-neutral 정리가 collect 쪽에서 끝나더라도 recommendation read-model이 구현 세부와 규칙을 한 파일에 같이 들고 있으면 다음 확장이 어려워진다. row loading과 heuristic policy를 분리해야 source onboarding 구조와 추천 정책 조정이 서로 덜 얽힌다.

## 332) read-model heuristic을 support로 뺀 뒤에도 deadline 판단과 service/tag signal 스캔이 matcher/scoring runtime에 각각 남아 있으면, 추천 경계는 projection 이후 단계에서 다시 중복 helper를 들게 된다
- 문제: `DefaultPriorityMatcher` 는 `DEADLINE` 우선순위를 위해 `applyEndDate` 7일 이내 판정을 자체 helper로 들고 있었고, `RuleScoringService` 는 special target mismatch와 profile match fallback을 위해 `containsAnySignal` / `containsSignal` / `normalize` helper를 직접 유지하고 있었다. 이 상태에서는 projection heuristic은 분리됐더라도, 런타임 recommendation 경계는 다시 문자열 스캔과 deadline 규칙을 파일별로 중복 유지하게 된다.
- 해결: `RecommendationRuntimeSupport` 를 추가해 deadline 판정과 service 본문 + tag 기반 signal 스캔을 공통 helper로 모았다. `DefaultPriorityMatcher` 는 deadline priority 판단을 이 support에 위임하고, `RuleScoringService` 는 special-target/profile fallback signal 판정을 같은 support로 바꿨다. 관련 helper는 `RecommendationRuntimeSupportTest` 로 고정했다.
- 이유: source-neutral 추천 구조를 끝까지 밀려면 read-model 이후 runtime 경계도 공통 helper를 봐야 한다. 그래야 규칙 수정이 repository와 scoring 사이에서 다시 갈라지지 않고, 마지막 남은 legacy string helper도 support 단위로 관리할 수 있다.

## 333) runtime helper를 공통화한 뒤에도 beneficiary bucket 매칭과 special-target/profile fallback 정책이 `RuleScoringService` 안에 남아 있으면, scoring은 여전히 규칙 엔진과 점수 계산을 같이 들고 있게 된다
- 문제: `RuleScoringService` 는 `targetGroupMatches`, `beneficiaryBucketMatches`, `specialAudienceMatchedByTargetTypes`, `specialAudienceMatchedByUserProfile` 를 통해 beneficiary/projection fallback과 special-target profile fallback 규칙을 직접 유지하고 있었다. helper 수준의 문자열 스캔은 support로 빠졌지만, 실제 매칭 정책은 여전히 scoring 파일에 남아 있어 추천 정책 조정과 점수 계산 변경이 함께 얽힌다.
- 해결: `RecommendationMatchingSupport` 를 추가해 beneficiary bucket 매칭, target-group fallback, special-target projection/profile fallback 규칙을 한 곳으로 옮겼다. `RuleScoringService` 는 이제 raw tag/projection 값을 준비한 뒤 이 support를 호출해 match 여부만 받아 점수에 반영한다. support 자체는 `RecommendationMatchingSupportTest` 로 고정했다.
- 이유: 추천 경계 분리는 helper 공통화에서 끝나지 않고, 실제 정책 판단이 scoring 계산 파일 밖으로 빠져야 마무리된다. 이렇게 해야 이후 추천 정책 튜닝과 score formula 변경을 더 작은 반경으로 나눠 다룰 수 있다.

## 334) support 분리 뒤에도 repository/test가 예전 alias 상수나 직접 문자열을 계속 들고 있으면, 코드 구조는 정리돼도 실제 사용 경로는 여전히 두 겹 계약을 유지하게 된다
- 문제: `CanonicalRecommendationReadModelRepository` 는 heuristic support로 옮긴 special-target / beneficiary bucket 이름을 다시 package-private alias 상수로 노출하고 있었고, 일부 테스트도 support 상수 대신 repository alias나 직접 문자열을 참조하고 있었다. 이 상태에서는 런타임 코드는 support catalog를 보더라도 테스트/보조 경계는 예전 alias contract를 계속 유지하게 된다.
- 해결: repository 내부 alias 상수는 제거하고, 관련 테스트는 `RecommendationProjectionHeuristicSupport` 상수를 직접 참조하도록 정리했다. 동시에 `RuleScoringServiceTest` 의 beneficiary bucket 문자열도 support 상수로 맞춰 support catalog 사용을 일관되게 정리했다.
- 이유: cleanup 단계에서는 기능 추가보다 “남겨둔 우회 계약”을 걷어내는 게 중요하다. alias를 계속 두면 다음 리팩터링 때 실제 의존 경로를 판단하기 어려워지므로, support를 도입한 뒤에는 참조 경로도 가능한 한 바로 그 support를 보게 맞추는 편이 낫다.

## 335) support 분리 뒤에도 stale 설명과 projection 경로에서 이미 포함된 dead branch가 남아 있으면, 실제 동작보다 코드가 더 복잡해 보이고 다음 정리 범위를 헷갈리게 만든다
- 문제: `CanonicalRecommendationReadModelRepository` 클래스 주석에는 아직 “retrieval/scoring path에는 연결하지 않는다”는 예전 설명이 남아 있었고, `RecommendationMatchingSupport.specialAudienceMatchedByTargetTypes()` 는 projection bucket 경로에서 이미 `targetTypes ∩ specialTargetBuckets` 매칭을 한 뒤에도 `자립준비청년`, `농어촌` 두 항목을 다시 같은 의미로 한 번 더 비교하는 dead branch를 유지하고 있었다.
- 해결: repository 주석은 현재 상태에 맞게 stale 문장을 제거했고, special-target projection 경로는 `targetTypes.stream().anyMatch(projection.specialTargetBuckets()::contains)` 하나만 남기도록 정리했다. 동작 변화 없이 현재 support 경계를 코드가 그대로 반영하도록 맞춘 cleanup이다.
- 이유: 마지막 cleanup 단계에서는 새 abstraction을 더 만드는 것보다, 이미 옮겨진 계약을 코드와 주석이 정확히 따라가게 만드는 편이 중요하다. stale 설명과 의미 중복 분기를 걷어내야 다음에 보는 사람이 “아직 legacy 경로가 남아 있나?”를 잘못 해석하지 않는다.

## 336) source enum 제약을 당장 풀지 못하더라도, synthetic item dry-run 테스트가 없으면 “지금 구조가 새 DTO 타입에도 실제로 재사용되는가”를 말로만 주장하게 된다
- 문제: source binding, saver, raw payload, adapter 경계를 공통화한 뒤에도 기존 테스트는 대부분 `YouthApiDto`, `Bokjiro*Dto` 같은 실제 source DTO만 다뤘다. 이 상태에서는 코드가 generic signature를 갖고 있어도, 실제로는 기존 DTO에만 우연히 맞는 구조인지 아니면 임의 item 타입에도 재사용되는지 분리해 검증되지 않는다.
- 해결: `SyntheticItem` 과 `ListCollectSourceBinding<SyntheticItem>` 를 사용한 dry-run 테스트를 추가해 세 경계를 따로 고정했다. `RawApiPayloadServiceTest` 는 generic `saveList(binding, item)` 이 synthetic payload도 저장하는지 확인하고, `CollectItemSaverTest` 는 `saveOnce(binding.toSaveCommand(item))` 경로가 sidecar/tag refresh까지 재사용되는지 확인하며, `SyntheticListCollectSourceAdapterTest` 는 abstract list adapter가 synthetic item에도 raw/save generic 엔트리를 그대로 호출하는지 검증한다.
- 이유: 현재 남아 있는 큰 source 종속점은 enum/실제 source 등록부 쪽이지, item DTO 타입 그 자체는 아니다. synthetic dry-run 테스트를 두면 이후 새 source onboarding 때 “새 DTO 타입이라서 generic 경계가 깨진 것인지, 아니면 source catalog/enum을 추가해야 하는 것인지”를 훨씬 빨리 구분할 수 있다.

## 337) 로컬 integrationTest 는 코드 회귀와 별개로 “테스트 DB 계정 비밀번호”와 “draft sidecar 테이블 적용 여부” 두 전제를 만족해야 정상 해석된다
- 문제: `docker compose up -d db redis` 만 다시 띄운 상태에서 `./gradlew integrationTest` 를 바로 실행하면, fresh DB가 `.env` 의 `DB_PASSWORD` 기준으로 `app_core_rw` 계정을 만들기 때문에 `application-integration.yml` 이 기대하는 `app_core_rw / welfare1234!` 와 어긋나 JPA 컨텍스트가 전부 `Access denied` 로 죽는다. 또 이 문제를 바로잡은 뒤에도 fresh init `schema.sql` 만으로는 `service_taxonomies`, `service_taxonomy_terms`, `service_facts` 같은 draft sidecar 테이블이 없어 sidecar/projection 계열 integration 이 `BadSqlGrammarException` 으로 추가 실패한다.
- 해결: 로컬 integration 기준은 `DB_PASSWORD=welfare1234! docker compose down -v` 후 같은 값으로 `docker compose up -d db redis` 해 테스트 계정을 맞추고, 이어서 `deploy/mysql/apply-local-policy-sidecar-draft.sh` 로 draft sidecar SQL을 적용하는 순서로 정리했다. 이 전제 두 개를 맞춘 뒤 `./gradlew integrationTest --no-daemon` 를 다시 실행하니 전체 integration 이 통과했다.
- 이유: 이번 라운드의 실패는 source 구조 리팩터링 회귀가 아니라 로컬 integration fixture 드리프트였다. 이걸 기록해 두지 않으면 다음에도 `Access denied` 나 missing sidecar table 을 코드 회귀로 오해하게 된다.

## 338) collect generic 경계가 자리잡은 뒤에도 source별 public wrapper 메서드를 남겨두면, 코드 사용자는 새 source를 추가할 때 다시 `CollectItemSaver` 와 `RawApiPayloadService` 를 열어 “다음 saveXxx 를 추가해야 하나?”를 고민하게 된다
- 문제: list collect 경계는 이미 `ListCollectSourceBinding<T>` 와 generic `save(binding, item)`, `saveList(binding, item)` 로 수렴돼 있었지만, `CollectItemSaver` 와 `RawApiPayloadService` 에는 `saveYouth*`, `saveBokjiroCentral*`, `saveBokjiroLocal*`, `saveBokjiroCentralList` 같은 source별 public wrapper가 남아 있었다. production adapter는 generic 경계를 사용하면서도 테스트와 일부 호출자는 이 wrapper를 계속 쓰게 되어, “실제 공용 진입점이 무엇인가”가 두 겹으로 보였다.
- 해결: source별 public wrapper는 제거하고, `CollectItemSaver` 에는 generic non-transaction helper `saveOnce(binding, item)` / `saveOnce(binding, item, aggregate)` 만 남겼다. raw 저장도 `saveList(binding, item)` / `saveList(sourceType, sourceId, payload)` 만 유지하고, 관련 단위/통합 테스트는 전부 `ListCollectSourceBindings` 기반 generic 호출로 옮겼다.
- 이유: 재사용성 리팩터링의 목적은 generic API를 추가하는 데서 끝나지 않고, 실제 사용 경로도 그 API 하나로 수렴시키는 것이다. wrapper를 그대로 두면 구조는 정리돼도 사용자가 계속 source별 메서드를 따라가게 되어 OCP 개선 효과가 반감된다.

## 339) 추천 파이프라인에서 “facade 인라인 규칙”, “snapshot/user 이중 조회”, “cluster cache repository 직참조”가 같이 남아 있으면 단계 분리는 돼 보여도 실제 조율 경계가 다시 두꺼워진다
- 문제: `RecommendationFacade` 는 `RuleScoringService.score()` 뒤에 특수 대상 mismatch 필터를 인라인 stream으로 직접 적용하고 있었고, 추천 진입 시 `UserReadService.getRecommendationSnapshot(userId)` 호출 뒤 다시 `UserRepository.findById(userId)` 로 동일 user를 한 번 더 읽었다. 또 `AiScoringService` 는 `ClusterAiResultRepository` 와 `ClusterAiResult` entity를 직접 import해 cluster cache 조회/업데이트를 처리하고 있어, 1차 추천 서비스가 2차 캐시 persistence 세부에 직접 묶여 있었다.
- 해결: mismatch 제거는 `RecommendationPostScoringFilterService` 로 분리해 facade가 “후처리 단계 호출”만 하도록 바꿨고, `UserReadService` 에 `RecommendationReadContext(user, snapshot)` 조회를 추가해 facade가 user/snapshot을 한 번에 받도록 정리했다. cluster AI cache는 `ClusterAiScoreCache` 인터페이스와 `JpaClusterAiScoreCache` 구현으로 감싸서 `AiScoringService` 가 더 이상 repository/entity를 직접 import하지 않게 만들었다. 관련 회귀는 새 `AiScoringServiceTest`, `RecommendationPostScoringFilterServiceTest`, `UserReadServiceTest` 케이스로 고정했다.
- 이유: facade와 scoring 계층은 “파이프라인 단계 조율”과 “도메인 규칙 계산”에 집중해야 한다. 후처리 규칙, user 조립, cache persistence 세부가 이 안으로 다시 들어오면 구조상 한 번 분리한 경계가 재응집되기 때문에, 작은 support/service로 다시 잘라주는 편이 다음 변경 반경을 줄인다.

## 340) collect adapter가 템플릿 메서드 구조를 갖고 있어도 `recordStats` 만 source별 static helper를 직접 부르면, generic collect 경계는 마지막 한 구석에서 다시 source-aware 해진다
- 문제: `AbstractListCollectSourceAdapter` 는 fetch/validate/filter/save 흐름을 템플릿으로 고정하고 있었지만, 각 adapter의 `recordStats()` 구현은 `RawFieldValidator.recordStatsYouth`, `recordStatsBokjiroCentral`, `recordStatsBokjiroLocal` 을 직접 호출하고 있었다. 즉 raw 저장과 saver는 `ListCollectSourceBinding` 으로 공통화됐는데, field quality stats 기록만 다시 adapter별 분기로 남아 있었다.
- 해결: `ListCollectSourceBinding` 에 `statsRecorder` 를 추가하고 `recordStats(items, stats)` 계약을 제공했다. `ListCollectSourceBindings` 는 각 source의 `RawFieldValidator.recordStats*` 를 binding에 싣고, adapter들은 이제 `binding().recordStats(items, stats)` 만 호출한다. synthetic binding 테스트와 binding 단위 테스트도 constructor 변경에 맞춰 갱신해 “stats 경계도 binding이 가진다”는 사실을 고정했다.
- 이유: source onboarding 공통화는 저장 경계뿐 아니라 관측성 경계도 같이 묶여야 완성된다. stats 기록이 binding 계약 안으로 들어오면 새 list source 추가 시 adapter 구현은 fetch/validate/filter/save 훅만 신경 쓰면 되고, 품질 통계 수집도 같은 descriptor에서 따라오게 된다.

## 341) `BokjiroDetailCollectService` 가 대상 로딩, 예산 배분, detail row 병합, welfare_service fallback 적용을 한 파일에서 모두 들고 있으면, detail collect orchestration 과 세부 정책 수정이 불필요하게 같이 움직이게 된다
- 문제: `BokjiroDetailCollectService` 는 capability 기반 source 순회 자체는 이미 정리돼 있었지만, 내부에 `allocateBudgets()` / `selectNextBudgetSource()` 예산 배분 로직과 `toJsonArray()` / `findAgeMin()` / `findApplyEndDate()` / `inferOnlineApply()` 를 이용한 detail persistence-fallback 로직을 모두 함께 유지하고 있었다. 이 상태에서는 “호출 예산 정책을 바꾸는 작업”과 “detail payload 를 entity/fallback 으로 반영하는 작업”이 같은 서비스 파일을 건드리게 되어 책임 경계가 다시 두꺼워진다.
- 해결: source별 호출 비율 계산은 `BokjiroDetailBudgetAllocator` 로 분리하고, detail row merge + `WelfareService.applyDetailFallbacks(...)` 계산은 `BokjiroDetailPersistenceSupport` 로 분리했다. `BokjiroDetailCollectService` 는 이제 target 로딩, fetch/retry, capability 순회, metadata 조립 같은 orchestration 에 집중하고, detail 저장 시에는 `persistenceSupport.mergeDetail(...)` 와 `persistenceSupport.applyFallbacksToService(...)` 만 호출한다. 기존 생성자 시그니처는 유지해 테스트/호출 경계는 흔들지 않았다.
- 이유: 이 단계의 목적은 새 abstraction을 과하게 주입하는 게 아니라, 가장 덩치 큰 service에서 독립적으로 바뀔 수 있는 정책 묶음을 떼어내는 것이다. 예산 배분과 detail fallback 은 서로 다른 이유로 자주 바뀌는 부분이라, orchestration 과 분리해 두는 편이 이후 수정 반경과 테스트 초점을 줄이기 쉽다.

## 342) 추천 파이프라인 단계가 같은 `ScoredCandidate` 인스턴스를 순차적으로 직접 수정하면, 각 단계의 출력이 “새 결과”가 아니라 “이전 객체의 현재 상태”가 되어 순서 변경과 테스트 해석이 어려워진다
- 문제: `AiScoringService` 는 cluster cache hit/miss 이후 `aiScore`, `aiReason` 을 기존 candidate에 직접 세팅했고, `RealtimeAiGateway` 와 `ReRankingService` 도 같은 객체에 `aiScore`, `finalScore`, `aiFallback` 을 덮어쓰고 있었다. 이 구조에서는 추천 단계들이 값 계산과 객체 상태 변경을 동시에 수행하게 되어, 단계 재배치나 병렬화가 어려울 뿐 아니라 테스트도 “반환값” 대신 “원본 객체가 얼마나 변했는가”를 같이 추적해야 했다.
- 해결: `ScoredCandidate` 는 `@Builder(toBuilder = true)` 기반 copy-on-write DTO로 바꾸고 `withAiResult(...)`, `withFinalScore(...)` helper를 추가했다. `AiScoringService`, `RealtimeAiGateway`, `ReRankingService` 는 이제 기존 candidate를 직접 수정하지 않고 새 candidate 인스턴스를 만들어 다음 단계로 넘긴다. 관련 테스트도 반환 리스트를 기준으로 검증하도록 갱신해, cache hit 케이스에서 원본 candidate가 그대로 유지되는 점까지 고정했다.
- 이유: 지금 단계에서 DTO 전체를 record로 갈아엎을 필요는 없지만, 단계별 공유 뮤테이션을 줄이면 추천 파이프라인의 입력/출력 경계가 훨씬 명확해진다. 계산식은 그대로 두고 객체 갱신 방식을 copy-on-write 쪽으로 옮기면 회귀 반경을 크게 늘리지 않고도 구조적 리스크를 줄일 수 있다.

## 343) `CollectSource.executionOrder()` 와 `CollectService` adapter 검증이 서로 다른 source 집합을 보면서도 그 차이를 코드가 설명하지 않으면, manual-only source가 단순 누락인지 의도인지 신규 기여자가 추측해야 한다
- 문제: `BOKJIRO_DETAIL_REFRESH` 는 `collectAll()` 대상에는 포함되지 않지만 adapter는 필수였고, 이 차이는 `CollectSource.executionOrder()` 의 하드코딩 목록과 `CollectService.buildAdapterMap()` 의 `CollectSource.values()` 전체 검증 사이에 암묵적으로만 존재했다. 구조를 모르는 사람이 보면 “왜 이 source는 배치에서 안 돌면서 adapter는 강제하지?”라는 의문이 남는다.
- 해결: `CollectSource` 에 `runsInScheduledBatch`, `requiresAdapter` 메타데이터를 추가하고 `executionOrder()` 는 scheduled source만 반환하게 바꿨다. `CollectService` 의 adapter coverage 검증도 이제 `source.requiresAdapter()` 를 기준으로 돈다. 테스트에는 `BOKJIRO_DETAIL_REFRESH` 가 manual-only 이면서도 adapter 필수 source라는 점을 명시적으로 추가했다.
- 이유: 이 단계의 핵심은 동작을 바꾸는 게 아니라 규칙을 숨기지 않는 것이다. enum 자체가 “배치 실행 여부”와 “adapter 필요 여부”를 함께 들고 있으면 collect 경계의 예외가 service 구현 세부가 아니라 source 메타데이터로 드러난다.

## 344) retrieval/scoring/search refresh가 모두 청년 relevance fallback을 쓰더라도 그 규칙이 `YouthPolicyFilter` 라는 서비스 클래스에 남아 있으면, projection heuristic과 같은 레벨의 정책이 support가 아니라 레거시 service 이름공간에 남아 구조가 덜 정리돼 보인다
- 문제: `RetrievalService`, `RuleScoringService`, `SearchYouthRelevanceService` 는 projection이 없을 때 같은 청년 relevance fallback을 사용했지만, 그 구현은 `YouthPolicyFilter` 서비스에 모여 있었다. 이 상태에서는 실제 역할이 “추천/검색 공통 heuristic policy” 임에도 이름과 의존 형태가 예전 필터 서비스 중심으로 남아 있어, 새 projection support 계층과 경계가 어긋나 보였다.
- 해결: `RecommendationYouthRelevanceSupport` 를 추가해 `isYouthRelevant(...)`, `relevanceBonus(...)` 규칙을 support 계층으로 옮기고, retrieval/scoring/search refresh는 이 support를 직접 보게 바꿨다. 기존 `YouthPolicyFilter` 는 같은 static policy를 재사용하는 deprecated compatibility wrapper로만 남겼고, 새 support 전용 테스트도 추가했다.
- 이유: 이 단계의 목적은 점수 공식을 바꾸는 게 아니라 fallback 규칙의 소속을 맞추는 것이다. audience/special-target/runtime helper를 이미 support 쪽으로 정리한 상태에서 youth relevance도 같은 계층에 둬야 추천 정책이 한 레벨에서 보인다.

## 345) list collect 등록부와 bokjiro detail/backfill 등록부가 따로 살아 있으면, source를 한 군데서 관리한다고 해도 실제 onboarding 때는 “list 쪽 하나, detail 쪽 하나” 두 catalog를 계속 맞춰야 한다
- 문제: `ListCollectSourceBindings` 는 `YOUTH/BOKJIRO_*` list binding을 알고 있었고, `BokjiroSourceBinding` 은 `BOKJIRO_*` detail fetch/list aggregate/detail aggregate를 따로 들고 있었다. 둘 다 source registry 역할을 하지만 서로 다른 파일에 같은 source 목록을 유지하고 있어, 신규 source나 복지로 source 의미 조정 시 등록 지식이 다시 분산돼 있었다.
- 해결: `CollectSourceRegistry` 를 추가해 source type별 list binding과 bokjiro detail/backfill capability를 같은 catalog에 모았다. list adapter들은 이제 이 registry에서 binding을 받고, `BokjiroDetailCollectService` 와 `NormalizedPolicySidecarBackfillService` 도 detail-capable source 목록을 같은 registry에서 순회한다. 기존 `ListCollectSourceBindings`, `BokjiroSourceBinding` 은 테스트/호환 경계용 thin wrapper로만 남겼다.
- 이유: source-neutral 구조의 다음 단계는 “generic API가 있다”보다 “등록부가 몇 군데인가”를 줄이는 것이다. list/detail/backfill이 같은 source catalog를 보면 신규 source 추가 때 수정 지점이 실제로 한 군데로 좁아진다.

## 346) sidecar writer가 canonical aggregate를 받아도 `summary key -> SQL parameter` 매핑과 `youth major` 정규화 로직을 내부 helper로 직접 들고 있으면, 가장 큰 source-specific 경계가 writer 안쪽 구현 디테일과 같이 얽혀 남는다
- 문제: `DeferredNormalizedPolicySidecarWriter` 는 이미 generic `summaryLabels` 맵을 입력으로 받지만, 실제로는 `youthMidLabel/gov24*Label` parameter 바인딩과 `YOUTH_MAJOR` variant 정규화를 자체 private helper로 직접 유지하고 있었다. 이 상태에서는 sidecar summary schema가 아직 source-specific 이라는 사실과, 그 schema를 채우는 canonical mapping policy가 같은 클래스 안에서 같이 움직여 이후 분리 반경이 커진다.
- 해결: `TaxonomySummarySupport` 를 추가해 `summaryLabel(...)`, `applyBoundSummaryLabels(...)`, `normalizeYouthMajorSummary(...)` 정책을 writer 밖으로 뺐다. `DeferredNormalizedPolicySidecarWriter` 는 이제 SQL upsert orchestration에 집중하고, summary key binding과 youth major canonicalization은 support를 호출만 한다. support 전용 테스트도 추가해 variant/duplicate token 정규화를 별도로 고정했다.
- 이유: 지금 단계에서는 DB 컬럼 자체를 바꾸지 않는다. 대신 “source-specific schema를 채우는 policy” 를 먼저 분리해 두면, 다음에 schema를 더 generic하게 바꾸더라도 변경 반경이 writer SQL과 support policy로 나뉘어 훨씬 좁아진다.

## 347) `service_taxonomies` 스키마가 아직 `youth_*`, `gov24_*` 컬럼 중심이더라도, writer가 canonical summary slot을 바로 legacy parameter 이름으로 풀어 쓰면 source-specific schema 의존이 SQL과 parameter 조립에 동시에 퍼진다
- 문제: `DeferredNormalizedPolicySidecarWriter` 는 `TaxonomySummarySupport` 로 summary key lookup과 youth major 정규화를 뺀 뒤에도, 여전히 `youthMajorCode/youthMidLabel/gov24UserTypeLabel` 같은 legacy SQL parameter 이름과 null code 규칙을 직접 조립하고 있었다. 이 상태에서는 canonical summary input과 legacy schema output 사이 bridge가 아직 writer 안쪽에 남아 있어, 이후 `service_taxonomies` schema를 더 generic하게 바꾸려면 SQL과 parameter 조립을 함께 뒤져야 한다.
- 해결: `ServiceTaxonomyLegacySummaryBridge` 를 추가해 canonical summary slot을 legacy taxonomy column parameter로 번역하는 규칙을 한 곳에 모았다. writer는 이제 `serviceId/primarySourceSystem/compat category/authority/confidence` 만 채우고, `youth/gov24/provision_method` parameter 세트는 bridge에 위임한다. bridge 전용 테스트도 추가해 `YOUTH_MAJOR` variant canonicalization과 label-only slot binding을 별도로 고정했다.
- 이유: 지금 단계의 목적은 source-specific DB schema 자체를 없애는 게 아니라, 그 schema를 향한 translation boundary를 한 곳으로 모으는 것이다. bridge를 따로 두면 나중에 schema를 generic slot 구조로 바꿀 때 writer SQL과 slot translation policy를 독립적으로 손볼 수 있다.

## 348) mapper 안에 youth summary/taxonomy/fact 규칙이 그대로 남아 있으면, source registry와 writer bridge를 정리한 뒤에도 새 source onboarding 시 가장 먼저 다시 건드리게 되는 곳이 mapper giant class로 남는다
- 문제: `WelfareServiceMapper` 는 `toNormalizedYouth()` 에서 `YOUTH_MAJOR/YOUTH_MID` summary label, `YouthMidPartition`, youth taxonomy term 조립, age/income/apply-end fact 생성까지 모두 직접 들고 있었다. 이 상태에서는 registry와 writer가 많이 얇아져도, source-specific canonicalization 규칙은 여전히 mapper 본체 안에 뭉쳐 있어 다음 리팩터링이나 신규 source 비교 시 초점이 다시 giant class로 모인다.
- 해결: `YouthNormalizationSupport` 를 추가해 `partitionYouthMidLabels(...)`, `summaryLabels(...)`, `taxonomyTerms(...)`, `facts(...)` 를 support로 옮겼다. `WelfareServiceMapper` 는 이제 youth DTO에서 entity를 만든 뒤 canonical aggregate 조립 시 support를 호출만 하고, 관련 규칙 테스트는 support 전용 테스트로 따로 고정했다.
- 이유: mapper cleanup은 한 번에 다 밀기보다 source 단위로 떼어내는 편이 안전하다. youth 규칙부터 support로 옮겨 두면 다음에 bokjiro taxonomy/fact 묶음을 같은 방식으로 뺄 수 있고, mapper 본체는 공통 조립자 역할에 더 가까워진다.

## 349) youth 규칙을 뺀 뒤에도 bokjiro list/detail taxonomy term과 derived/detail fact 조립이 mapper 안에 남아 있으면, source registry를 정리한 효과가 canonicalization giant class에서 다시 희석된다
- 문제: `WelfareServiceMapper` 는 `toNormalizedBokjiroCentral()`, `toNormalizedBokjiroLocal()`, `toNormalizedBokjiroDetail()` 경로에서 `bokjiroTerms(...)`, `bokjiroDerivedFacts(...)`, `bokjiroDetailTerms(...)`, `bokjiroDetailFacts(...)` 를 직접 유지하고 있었다. 즉 source registry와 writer bridge를 많이 정리한 뒤에도, 실제 복지로 source별 taxonomy/fact 규칙은 여전히 mapper 본체 안에서 조립돼 새 source 비교나 규칙 변경 시 giant class를 다시 열어야 했다.
- 해결: `BokjiroNormalizationSupport` 를 확장해 중앙/지자체 list taxonomy term, detail beneficiary term, 목록 기반 derived fact, 상세 payload 기반 detail fact 조립을 한 곳에 모았다. `WelfareServiceMapper` 는 이제 복지로 entity/core/detail 을 만든 뒤 support가 반환한 taxonomy/fact를 aggregate에 넣기만 하고, `BokjiroNormalizationSupportTest` 로 term/fact 조립 계약을 별도로 고정했다.
- 이유: mapper를 공통 조립자에 가깝게 유지하려면 source-specific canonicalization 규칙을 source support로 밀어 넣는 편이 맞다. youth 다음 bokjiro까지 같은 패턴으로 정리해 두면, 이후 mapper에 남은 source 종속점은 DTO -> entity 변환과 공통 aggregate orchestration 쪽으로 더 좁혀진다.

## 350) writer가 canonical aggregate를 받더라도 `TaxonomySummary` 객체를 곧바로 legacy summary bridge에 넘기면, source-neutral summary slot과 legacy DB column 번역 경계가 다시 한 클래스 안에서 흐려진다
- 문제: `ServiceTaxonomyLegacySummaryBridge` 는 `TaxonomySummary` 를 직접 받아 `YOUTH_MAJOR`, `GOV24_*`, `provisionMethod` 를 바로 legacy `service_taxonomies` parameter로 풀어 쓰고 있었다. 이 상태에서는 canonical summary slot 추출과 legacy column 번역이 같은 단계에 섞여 있어, 이후 generic summary 저장 경계를 더 만들거나 read-model이 같은 slot catalog를 재사용하려 할 때 다시 bridge 구현 세부를 봐야 했다.
- 해결: `CanonicalTaxonomySummarySlots` 를 추가해 `TaxonomySummary` 에서 known summary label과 `provisionMethod` 를 source-neutral slot 집합으로 먼저 읽어내도록 했다. `DeferredNormalizedPolicySidecarWriter` 는 taxonomy를 바로 bridge에 넘기지 않고 `CanonicalTaxonomySummarySlots.from(taxonomy)` 를 만든 뒤 bridge에 전달하고, `ServiceTaxonomyLegacySummaryBridge` 와 `TaxonomySummarySupport` 는 같은 slot catalog 상수를 보게 정리했다. slot 추출 계약은 전용 테스트로 따로 고정했다.
- 이유: schema를 당장 바꾸지 않더라도 “canonical summary slot 추출” 과 “legacy column 번역” 을 분리해 두면 이후 저장 모델을 generic slot 구조로 옮길 때 변경 반경이 더 좁아진다. 같은 slot catalog를 writer/support/bridge가 공유하면 summary key literal이 여러 파일에 다시 퍼지는 것도 막을 수 있다.

## 351) code 경계에서 canonical summary slot을 분리한 뒤에도 저장 모델 설계가 문서로 고정되지 않으면, 다음 source가 새 summary 축을 들고 왔을 때 또 `service_taxonomies` 컬럼을 늘릴지 slot row를 병행할지 논쟁이 반복된다
- 문제: 현재 코드는 `CanonicalTaxonomySummarySlots` 까지 분리돼 `TaxonomySummary -> slot -> legacy column` 경계가 생겼지만, DB 레벨에서는 여전히 `service_taxonomies.youth_*`, `gov24_*`, `provision_method_*` 가 유일한 summary 저장소처럼 보인다. 이 상태에서 다음 source가 `provider_group`, `housing_supply_type` 같은 새 summary 축을 들고 오면, 구현자가 다시 `service_taxonomies` 컬럼을 추가할지 generic row table을 병행할지 매번 즉흥적으로 판단하게 된다.
- 해결: [policy-normalization-summary-slot-storage-plan.md](../history/policy/policy-normalization-summary-slot-storage-plan.md) 를 추가해 장기 canonical truth를 `service_taxonomy_summary_slots` 같은 generic row table로 두고, `service_taxonomies` 는 당분간 legacy projection / compatibility row로 유지하는 방향을 문서로 고정했다. 같이 `policy-source-onboarding-architecture.md`, `policy-normalization-current-state.md`, `db-migration.md` 에도 이 follow-up 설계를 연결해 다음 작업자가 바로 같은 결론에서 출발하게 맞췄다.
- 이유: 지금 단계에서 바로 DDL을 바꾸지 않더라도, 저장 모델 원칙을 먼저 고정해 두면 다음 리팩터링이 “코드 구조 cleanup” 에서 “실제 schema migration” 으로 넘어갈 때 판단 비용이 줄어든다. 특히 source onboarding 관점에서는 새 summary 축이 생겼을 때 `legacy summary row를 또 확장하지 않는다` 는 기준이 미리 있어야 확장성이 흔들리지 않는다.

## 352) generic summary slot 저장 구조를 문서로만 두고 draft DDL이 없으면, 다음 단계에서 writer dual-write나 local replay smoke를 열 때 다시 컬럼/인덱스/unique contract를 즉석에서 결정해야 한다
- 문제: `policy-normalization-summary-slot-storage-plan.md` 로 방향은 고정됐지만, 실제 `service_taxonomy_summary_slots` DDL 초안이 없으면 이후 writer dual-write나 backfill/replay 작업을 시작할 때 `slot_code 를 nullable로 둘지`, `uq_service_summary_slot` 구성을 어떻게 할지, `normalization_code_sets` FK를 붙일지 같은 저장 계약을 다시 작업 중간에 정해야 했다. 이렇게 되면 저장 모델 설계가 또 코드 작업에 끌려가고, source-neutral summary slot 경계의 핵심 결정이 흩어진다.
- 해결: draft migration [`V2026_05_02_01__add_service_taxonomy_summary_slots.sql`](../backend/src/main/resources/db/migration-draft/V2026_05_02_01__add_service_taxonomy_summary_slots.sql) 을 추가해 `service_id`, `slot_key`, `code_set_key`, `slot_code=''`, `slot_label`, `source_field`, `authority`, `confidence` 와 유니크/인덱스/FK 계약을 먼저 고정했다. 같이 `db-migration.md` 와 summary slot storage plan 문서에도 이 DDL을 연결해, 다음 단계는 DDL 설계가 아니라 실제 dual-write 범위를 정하는 작업부터 시작할 수 있게 만들었다.
- 이유: draft migration 은 지금 당장 운영 반영하려는 게 아니라, 저장 모델 결정을 코드 밖에서 먼저 잠그는 역할이다. 이렇게 해 두면 다음 리팩터링은 “테이블을 어떻게 만들까”가 아니라 “언제, 어디까지 dual-write 할까”에 집중할 수 있다.

## 353) draft DDL만 있고 writer가 계속 `service_taxonomies` 에만 쓰면, generic summary slot 구조는 실제 collect/replay 경로에서 한 번도 검증되지 않은 문서 자산으로 남는다
- 문제: `service_taxonomy_summary_slots` 초안을 추가해도 `DeferredNormalizedPolicySidecarWriter` 가 여전히 legacy `service_taxonomies` 만 upsert 하면, generic summary slot은 실제 collect path에서 한 번도 채워지지 않는다. 그러면 다음 단계에서 replay smoke나 read-model 전환을 시작할 때 “DDL은 맞는데 writer가 어떤 slot을 어떻게 정규화해 넣는가”를 다시 한꺼번에 검증해야 해 반경이 커진다.
- 해결: `DeferredNormalizedPolicySidecarWriter` 에 optional `service_taxonomy_summary_slots` capability check를 추가하고, 테이블이 있을 때만 managed slot key 집합을 먼저 refresh-delete 한 뒤 `CanonicalTaxonomySummarySlots` 를 row 단위로 dual-write 하도록 붙였다. `YOUTH_MAJOR` 는 canonical code(`HOUSING` 등)까지 채우고, label-only slot은 `slot_code=''` 를 사용하며, 테이블이 없는 기존 로컬/운영 DB에서는 조용히 skip 한다. 관련 계약은 `DeferredNormalizedPolicySidecarWriterTest` 와 `CanonicalTaxonomySummarySlotsTest` 로 고정했다.
- 이유: draft schema 다음 단계의 핵심은 “실제 writer가 이 구조를 쓸 수 있나”를 작은 반경으로 확인하는 것이다. optional dual-write로 붙여 두면 기존 legacy summary 경계를 깨지 않고도 summary slot 저장 밀도를 로컬 replay에서 바로 관찰할 수 있다.

## 354) writer dual-write를 붙인 뒤에도 local draft apply/replay smoke가 계속 `service_taxonomies` 만 확인하면, summary slot 저장은 코드 테스트로만 검증되고 실제 운영 전제 스크립트에서는 빠진 상태로 남는다
- 문제: `service_taxonomy_summary_slots` DDL과 writer dual-write가 추가된 뒤에도 `apply-local-policy-sidecar-draft.sh` 와 `run-local-education-priority-replay.sh` 는 계속 `service_taxonomies` 존재 여부와 education target row만 확인하고 있었다. 이 상태에서는 기존 local DB처럼 `service_taxonomies` 만 살아 있고 slot table이 없는 snapshot도 replay precondition을 통과할 수 있어, dual-write 구조가 실제 closeout smoke에서 검증되지 않은 채 남을 수 있었다.
- 해결: draft apply 스크립트가 새 `V2026_05_02_01__add_service_taxonomy_summary_slots.sql` 도 함께 적용하고, 적용 후 `service_taxonomy_summary_slots` 존재/row count를 같이 출력하도록 바꿨다. replay smoke도 `service_taxonomy_summary_slots` 를 precondition에 추가하고, auto-apply 조건에 “slot table missing” 을 포함했으며, summary 출력에 `SUMMARY_SLOT_METRIC slot_rows / slot_services / slot_education_services` 를 추가해 dual-write density를 바로 보이게 했다.
- 이유: 저장 모델 확장은 코드 테스트만 통과해서는 부족하고, 실제 로컬 closeout 스크립트가 같은 전제를 강제해야 의미가 있다. replay summary에 slot 밀도를 같이 남기면 이후 read-model 전환 전에도 “legacy summary만 찼는지, generic slot row도 같이 찼는지”를 한 줄로 바로 판단할 수 있다.

## 355) dual-write를 붙인 직후 local replay에서 `service_taxonomy_summary_slots=0` 이 나오면, 새 slot 저장은 “앞으로 collect/update 되는 row” 에만 적용되고 기존 snapshot summary는 그대로 비어 있다는 뜻이다
- 문제: `DeferredNormalizedPolicySidecarWriter` 에 dual-write 를 붙인 뒤 `run-local-education-priority-replay.sh` 를 바로 다시 실행했더니 `SUMMARY_SLOT_METRIC slot_rows=0 slot_services=0 slot_education_services=0` 이 찍혔다. replay 자체와 education priority metric은 통과했지만, 현재 로컬 snapshot은 이미 `service_taxonomies` 가 채워진 상태였고 이번 라운드에서는 collect path를 다시 태우지 않았기 때문에, 새로운 slot row는 “새로 upsert 되는 summary” 에만 적용되고 기존 legacy summary row는 비어 있는 채로 남아 있었다.
- 해결: `V2026_05_02_02__backfill_service_taxonomy_summary_slots.sql` 을 추가해 기존 `service_taxonomies` 의 managed summary 축(`YOUTH_MAJOR`, `YOUTH_MID`, `GOV24_*`, `PROVISION_METHOD`)을 `service_taxonomy_summary_slots` 로 backfill 하도록 했다. 이 과정에서 `provision_method_label` 장문이 `slot_label` 폭과 unique key에 걸려 `Data too long` / `TEXT column used in key` 문제가 드러나, `V2026_05_02_03__widen_service_taxonomy_summary_slot_label.sql` 로 `slot_label` 을 `TEXT` 로 보정하고 unique key를 `(service_id, slot_key, slot_code, authority)` 기준으로 재정의했다. 이후 local draft apply와 replay를 다시 실행하니 `service_taxonomy_summary_slots=5619`, `slot_services=2305`, `slot_education_services=110` 으로 실제 밀도가 확인됐다.
- 이유: generic slot 구조를 현재 snapshot에서 바로 관찰하려면 dual-write만으로는 부족하고, 기존 legacy summary row를 한 번 메우는 backfill이 필요하다. 이 값을 로컬 replay summary까지 남겨야 다음 read-model migration 때 “테이블은 있는데 비어 있는가, 실제 collect snapshot을 커버하는가”를 빠르게 구분할 수 있다.

## 356) summary slot 저장이 실제로 채워진 뒤에도 recommendation read-model이 계속 `service_taxonomies.youth_major_label` 만 직독하면, 새 slot 구조는 수집/write 단계에서만 검증되고 read 경계는 여전히 legacy summary row에 묶여 남는다
- 문제: local replay 기준으로 `service_taxonomy_summary_slots=5619`, `slot_services=2305` 까지 채워졌지만, `CanonicalRecommendationReadModelRepository` 의 base row는 여전히 `service_taxonomies.youth_major_label` 만 직접 읽고 있었다. 이 상태에서는 summary slot 구조가 실제로 채워져도 추천 read-model은 이를 전혀 소비하지 않아, slot 저장이 “쓰기 경계의 부가 데이터” 수준으로만 머물고 다음 전환 단계가 다시 커진다.
- 해결: `CanonicalRecommendationReadModelRepository` 에 optional `service_taxonomy_summary_slots` capability check를 추가하고, 테이블이 있을 때는 `YOUTH_MAJOR` slot subquery를 LEFT JOIN 해 `COALESCE(slot_label, st.youth_major_label)` 로 base row를 만들도록 바꿨다. 즉 추천 read-model에서는 첫 번째로 `YOUTH_MAJOR` 를 slot-first / legacy fallback 경계로 전환했고, 테이블이 없는 기존 DB에서는 예전 SQL을 그대로 사용한다. 관련 계약은 repository test에 slot-first 케이스를 추가해 고정했다.
- 이유: 저장 모델 전환은 “write만 generic” 이 아니라 최소 한 군데 read path가 실제로 새 구조를 소비하기 시작해야 의미가 있다. `youth_major_label` 은 현재 recommendation projection에서 이미 쓰이고 영향 범위도 좁아서, slot-first read를 여는 첫 후보로 가장 안전하다.

## 357) recommendation read-model만 slot-first로 바꾸고 replay smoke와 integration 검증 쿼리가 계속 `service_taxonomies.youth_major_label` 을 직접 읽으면, 실제 closeout 증적은 여전히 legacy summary row 기준으로만 남아 drift를 놓칠 수 있다
- 문제: `CanonicalRecommendationReadModelRepository` 는 `YOUTH_MAJOR` 를 `service_taxonomy_summary_slots` 우선으로 읽게 바뀌었지만, `run-local-education-priority-replay.sh` 와 `EducationPriorityTargetCandidateCompositionIntegrationTest` 의 education target row 판정은 여전히 `service_taxonomies.youth_major_label = '교육'` 을 직접 사용하고 있었다. 이 상태에서는 앱 runtime은 slot-first인데, smoke/integration 증적은 legacy row만 보고 있어 두 경계가 서로 다른 데이터를 근거로 통과할 수 있다.
- 해결: replay script의 local canonical precondition, target count 계산, response metadata query를 모두 `service_taxonomy_summary_slots` 의 `YOUTH_MAJOR` subquery LEFT JOIN + `COALESCE(slot_label, st.youth_major_label)` 기준으로 바꿨다. 같은 slot-first helper를 `EducationPriorityTargetCandidateCompositionIntegrationTest` 에도 적용해, slot table이 있으면 integration query 역시 같은 fallback semantics를 따르도록 맞췄다. 이후 `./gradlew integrationTest --no-daemon --tests com.example.welfare.integration.EducationPriorityTargetCandidateCompositionIntegrationTest` 와 `deploy/smoke/run-local-education-priority-replay.sh` 를 다시 실행해 `SUMMARY_SLOT_METRIC slot_rows=5619 slot_services=2305 slot_education_rows=110 slot_education_services=110`, `SUMMARY_METRIC A_top10_target=5->8 B_top10_target=2->2` 가 그대로 유지되는 것을 확인했다.
- 이유: 저장 모델 전환을 안전하게 닫으려면 runtime read path만이 아니라 그 경로를 검증하는 smoke/integration query도 같은 semantics를 봐야 한다. 그래야 이후 legacy `service_taxonomies.youth_major_label` 값이 비어도 replay와 integration이 실제 runtime과 같은 전제로 pass/fail을 판단하게 된다.

## 358) replay/integration 경계만 slot-first로 바꾸고 local canonical draft apply 스크립트가 계속 `service_taxonomies.youth_major_label` 을 직접 세면, 초기 backfill/apply 증적은 다시 legacy summary row 기준으로 남는다
- 문제: `run-local-education-priority-replay.sh` 와 integration test는 `YOUTH_MAJOR` slot-first로 바뀌었지만, `apply-local-policy-sidecar-draft.sh` 의 `education_target_rows` 집계는 여전히 `service_taxonomies.youth_major_label = '교육'` 을 직접 사용하고 있었다. 이 상태에서는 local draft schema를 처음 올리고 backfill을 적용한 뒤 확인하는 가장 앞단 숫자가 다시 legacy summary row 기준으로 남아, replay 이전 단계의 증적과 그 이후 증적이 서로 다른 fallback semantics를 보게 된다.
- 해결: `apply-local-policy-sidecar-draft.sh` 의 `education_target_rows` 집계도 `service_taxonomy_summary_slots` 의 `YOUTH_MAJOR` subquery LEFT JOIN + `COALESCE(slot_label, st.youth_major_label)` 로 바꿨다. 이후 스크립트를 다시 실행해 `service_taxonomies=2340`, `service_taxonomy_summary_slots=5619`, `education_target_rows=110`, `slot_education_services=110` 이 그대로 유지되는 것을 확인했다.
- 이유: 저장 모델 전환은 collect/write/read뿐 아니라 “초기 schema apply 후 확인하는 집계 스크립트” 까지 같은 의미 체계를 써야 닫힌다. 그래야 이후 slot row가 truth가 되고 legacy summary row가 부분적으로 비어도, local draft apply 단계부터 replay 단계까지 같은 기준으로 숫자를 비교할 수 있다.

## 359) 실행 스크립트만 slot-first로 바꾸고 history replay/inventory 문서가 계속 `service_taxonomies.youth_major_label` 직독을 안내하면, 후속 수동 검증이 다시 legacy summary row 기준으로 회귀할 수 있다
- 문제: replay smoke와 local draft apply는 이미 `YOUTH_MAJOR` slot-first / legacy fallback 으로 맞춰졌지만, `policy-normalization-education-priority-replay-procedure.md` 와 `policy-normalization-compat-other-youth-major-inventory.md` 는 여전히 `service_taxonomies.youth_major_label` 직독이나 그에 준하는 설명을 기준으로 남아 있었다. 이 상태에서는 후속 작업자가 history 문서만 보고 수동 검증을 다시 legacy row 기준으로 수행해, runtime/스크립트와 문서가 서로 다른 의미 체계를 따를 수 있다.
- 해결: education replay 절차 문서에는 `serviceId` 재대조 시 `service_taxonomy_summary_slots(slot_key='YOUTH_MAJOR')` 를 우선 보고, 값이 없을 때만 `service_taxonomies.youth_major_label` 로 fallback 하도록 명시했다. `compat=기타 + canonical youth_major 채움` inventory 문서도 canonical major 판정 기준을 같은 slot-first / legacy fallback semantics 로 설명하게 정리했다.
- 이유: 저장 모델 전환은 코드와 스크립트만 바꾸는 것으로 끝나지 않고, 수동 분석 문서도 같은 truth source를 가리켜야 한다. 그래야 나중에 drift inventory나 replay 분석을 다시 열었을 때 “왜 코드 숫자와 문서상의 SQL 해석이 다르지?” 같은 불필요한 재검증이 줄어든다.

## 360) `slot_rows` 총합만 보이면 summary slot 저장 구조는 확인돼도, 다음 read migration 후보를 어떤 slot key부터 잡아야 하는지는 숫자로 판단하기 어렵다
- 문제: local draft apply와 replay smoke는 이미 `service_taxonomy_summary_slots` 존재와 총 row/service 수를 출력하고 있었지만, 이 값만으로는 `YOUTH_MID`, `PROVISION_METHOD`, `GOV24_*` 중 실제로 populated 된 slot key가 무엇인지 알 수 없었다. 그래서 다음 read migration 후보를 정할 때 다시 DB를 직접 질의해야 하고, `GOV24_*` 처럼 아직 0인 slot에 시간을 쓰는 판단 비용이 남아 있었다.
- 해결: `apply-local-policy-sidecar-draft.sh` 와 `run-local-education-priority-replay.sh` 에 slot key별 density 출력(`slot_services_YOUTH_MAJOR`, `slot_services_YOUTH_MID`, `slot_services_PROVISION_METHOD`, `slot_services_GOV24_*`)을 추가했다. local 재실행 결과 현재 snapshot은 `YOUTH_MAJOR=2288`, `YOUTH_MID=2170`, `PROVISION_METHOD=1161`, `GOV24_SERVICE_FIELD=0`, `GOV24_USER_TYPE=0`, `GOV24_BENEFIT_TYPE=0` 으로 확인됐다.
- 이유: 저장 구조를 다음 단계로 넓힐 때는 “generic table이 있다”보다 “어떤 slot key가 실제로 차는가”가 더 중요한 기준이다. key별 density를 기본 summary에 넣어 두면 다음 read-model 전환은 populated 된 slot부터 고를 수 있고, external blocked 상태인 `GOV24_*` 에 과도하게 손대는 일을 줄일 수 있다.

## 361) slot density상 `YOUTH_MID` 와 `PROVISION_METHOD` 가 실제로 채워져 있어도 read-model projection이 계속 `youth_major` 만 들고 있으면, 다음 read migration 후보를 다시 새 projection 설계부터 열어야 한다
- 문제: local density를 찍어 보니 current snapshot은 `YOUTH_MID=2170`, `PROVISION_METHOD=1161` 으로 이미 populated 되어 있었지만, `CanonicalRecommendationReadModelRepository` 와 `RecommendationCandidateProjection` 은 여전히 `youthMajorLabel` 만 1급 summary field로 들고 있었다. 이 상태에서는 다음 단계에서 `YOUTH_MID` 나 `PROVISION_METHOD` 를 실제 추천/검색/응답 경계에 연결하려 해도, 먼저 projection schema부터 다시 바꿔야 해 read migration 반경이 커진다.
- 해결: `RecommendationCandidateProjection` 에 `youthMidLabel`, `provisionMethodLabel` 필드를 추가하고, `CanonicalRecommendationReadModelRepository` 가 summary slot table이 있으면 `YOUTH_MID`, `PROVISION_METHOD` 를 slot-first / legacy fallback 으로 hydrate 하도록 확장했다. legacy path에서는 각각 `service_taxonomies.youth_mid_label`, `COALESCE(service_taxonomies.provision_method_label, welfare_services.apply_method_name)` 를 읽게 유지했다. repository test도 새 필드가 projection으로 전달되는지 고정했다.
- 이유: 아직 이 두 필드를 실제 scoring이나 response에서 적극 소비하지 않더라도, projection 경계에 먼저 싣는 편이 다음 단계 반경을 훨씬 줄인다. populated 된 slot을 read-model이 최소한 받아 둘 수 있어야, 이후 어떤 서비스가 이 값을 써도 repository 쿼리 구조를 다시 크게 흔들지 않고 연결할 수 있다.

## 362) projection에 `youthMidLabel`, `provisionMethodLabel` 을 실어도 policy 응답이 계속 legacy entity field만 내려주면, 새 summary slot read는 내부에서만 머물고 외부 API에서는 여전히 활용되지 않는다
- 문제: `CanonicalRecommendationReadModelRepository` 와 `RecommendationCandidateProjection` 에 `youthMidLabel`, `provisionMethodLabel` 을 추가한 뒤에도, `PolicySummaryResponse`, `PolicyDetailResponse`, `PolicyRankingResponse` 는 여전히 `WelfareService` 의 기존 필드만 응답에 실어 주고 있었다. 이 상태에서는 새 projection field가 실제 사용자/API 경계에서는 보이지 않아, read-model 확장이 내부 준비 작업으로만 남는다.
- 해결: policy summary/detail/ranking 응답 DTO에 `youthMidLabel`, `provisionMethodLabel` 을 additive field로 추가하고, projection이 있으면 그 값을 그대로 응답에 싣도록 연결했다. `PolicyServiceTest`, `UserServiceTest`, `PolicyRankingServiceTest` 로 목록/북마크/랭킹 경계에서 값이 전달되는지 고정했다.
- 이유: read-model 확장은 실제 소비 지점 하나와 연결돼야 의미가 생긴다. 기존 필드를 덮어쓰지 않고 additive field로 먼저 노출하면 회귀 위험을 크게 늘리지 않으면서도, 이후 검색/추천 설명 계층이나 프론트가 canonical summary를 바로 활용할 수 있는 경로를 열 수 있다.

## 363) policy 응답 DTO에 additive field를 추가해도 WebMvc 레벨 직렬화 contract를 고정하지 않으면, 프론트 연결 전까지는 JSON 필드 누락을 뒤늦게 발견할 수 있다
- 문제: `PolicySummaryResponse`, `PolicyDetailResponse`, `PolicyRankingResponse` 에 `youthMidLabel`, `provisionMethodLabel` 을 추가해도, service 단위 테스트만으로는 실제 controller 응답 JSON에 그 필드가 내려가는지 보장되지 않는다. 프론트가 아직 붙지 않은 단계에서는 이런 누락이 바로 드러나지 않아, 나중에 API 연동 시점에 contract mismatch 로 터질 수 있다.
- 해결: `RecommendationPolicyFlowWebMvcTest`, `UserControllerWebMvcTest` 에 목록/상세/랭킹/북마크 응답 JSON 검증을 추가해 `youthMidLabel`, `provisionMethodLabel` 이 실제 직렬화되는지 고정했다.
- 이유: 프론트 연결 전 단계에서는 WebMvc contract 테스트가 사실상 API 스키마 안전망 역할을 한다. additive field를 백엔드만 보고 넣어 두는 것보다, JSON 응답까지 고정해 두는 편이 이후 연동 반경을 줄인다.

## 364) WebMvc contract까지 고정해도 응답 문서가 예전 필드 집합에 머물면, 프론트 연결 전에는 새 additive field 존재 자체를 다시 코드에서 찾아야 한다
- 문제: `youthMidLabel`, `provisionMethodLabel` 을 policy summary/detail/ranking/bookmark 응답에 추가하고 JSON contract 테스트까지 붙인 뒤에도, `api-mapping.md` 는 여전히 예전 응답 필드 집합만 설명하고 있었다. 이 상태에서는 프론트 연결 전 단계의 개발자나 QA가 새 필드 존재를 문서에서 바로 못 찾고, DTO/WebMvc 테스트를 다시 뒤져야 했다.
- 해결: `api-mapping.md` 에 `GET /api/policies`, `GET /api/policies/search`, `GET /api/policies/{id}`, `GET /api/policies/ranking`, `GET /api/users/me/bookmarks` 의 additive canonical summary field(`youthMidLabel`, `provisionMethodLabel`)를 명시하고 예시 JSON도 갱신했다. 또 `current-state.md` 에 `api-mapping.md` 링크를 추가해 현재 단계의 API contract entrypoint를 분명히 했다.
- 이유: 프론트 연결 전에는 문서가 사실상 계약서 역할을 한다. 테스트가 green 이더라도 문서가 예전 상태면 연동 전 준비 비용이 다시 커지므로, additive response field는 코드와 문서가 같은 턴에 함께 열려 있어야 한다.

## 365) policy 응답만 additive canonical summary field를 노출하고 recommendation 응답이 예전 shape에 머물면, 프론트는 같은 카드 계열인데도 엔드포인트별로 서로 다른 데이터 모델을 다뤄야 한다
- 문제: policy summary/detail/ranking/bookmark 응답에는 `youthMidLabel`, `provisionMethodLabel` 을 추가했지만, 추천 목록/refresh 응답은 여전히 기존 필드 집합만 내려주고 있었다. 이 상태에서는 프론트가 추천 카드와 정책 카드를 함께 다룰 때 canonical summary field 유무가 엔드포인트마다 달라져, 연결 전부터 응답 shape가 불필요하게 갈라진다.
- 해결: `RecommendationResponse` 에 `youthMidLabel`, `provisionMethodLabel` 을 additive field로 추가하고, `RecommendationPolicyFlowWebMvcTest` 에 추천 refresh 응답 JSON 검증을 붙였다. `api-mapping.md` 와 current-state 문서도 추천 응답이 같은 additive field를 노출한다는 점을 반영했다.
- 이유: canonical summary field는 특정 policy API만의 실험 필드가 아니라 projection에서 공통으로 읽는 응답 metadata다. 프론트 연결 전에는 recommendation/policy 응답 shape를 가능한 한 맞춰 두는 편이 이후 카드 UI와 API client 모델을 단순하게 만든다.

## 366) recommendation refresh 응답만 contract 테스트로 고정하고 `GET /api/recommendations` 저장 추천 조회를 빼면, 같은 DTO를 쓰더라도 실제 조회 경로 누락을 뒤늦게 발견할 수 있다
- 문제: `RecommendationResponse` 를 확장하고 추천 refresh 응답 JSON까지 검증해도, 저장된 추천 목록을 돌려주는 `GET /api/recommendations` 경로를 따로 확인하지 않으면 `recommendationFacade.getRecommendations(...)` 흐름에서 `logId` 나 additive field 누락이 숨어 있을 수 있다. 두 엔드포인트는 같은 DTO를 쓰지만 controller 진입점과 mock wiring이 달라, 한쪽만 green 이어도 다른 쪽 누락 가능성이 남는다.
- 해결: `RecommendationPolicyFlowWebMvcTest` 에 `GET /api/recommendations` 전용 케이스를 추가해 `logId`, `unifiedCategory`, `youthMidLabel`, `provisionMethodLabel` 이 실제 저장 추천 조회 응답에도 실리는지 고정했다.
- 이유: 프론트 연결 전에는 “같은 DTO니까 한 경로만 보면 된다”라고 가정하지 않는 편이 안전하다. 추천 목록 조회와 refresh는 사용 빈도가 높고, CTR 추적용 `logId` 도 함께 실어야 하므로 경로별 contract를 분리해 두는 게 맞다.

## 367) `unifiedCategory` 는 compat를 유지하면서 canonical major는 내부 projection에만 남겨 두면, 외부 API에서는 두 분류 체계를 비교하거나 점진 이행을 확인할 수 없다
- 문제: `RecommendationCandidateProjection` 과 read-model은 이미 `youthMajorLabel` 을 `slot-first / legacy fallback` 으로 hydrate 하고 있었지만, policy/recommendation 응답 DTO는 `youthMidLabel`, `provisionMethodLabel` 만 additive field로 노출하고 있었다. 이 상태에서는 API 소비자가 compat `unifiedCategory` 와 canonical major를 동시에 볼 수 없어, category bridge 검증이나 프론트 연결 전 데이터 비교가 다시 내부 코드/DB 조회에 의존하게 된다.
- 해결: `PolicySummaryResponse`, `PolicyDetailResponse`, `PolicyRankingResponse`, `RecommendationResponse` 에 `youthMajorLabel` 을 additive field로 추가하고, service 테스트와 WebMvc contract 테스트를 갱신했다. 같이 `api-mapping.md`, `policy-normalization-current-state.md`, `phase-plan.md` 도 응답 contract 기준으로 반영했다.
- 이유: 현재 단계에서는 `unifiedCategory` 를 compat contract로 유지하는 편이 안전하지만, canonical summary 전환 준비를 하려면 major summary를 additive field로 먼저 노출해 두는 게 맞다. 그래야 기존 계약을 깨지 않으면서도 외부 경계에서 canonical/compat drift를 관찰할 수 있다.

## 368) projection에 `youthMajorLabel`, `youthMidLabel`, `provisionMethodLabel` 을 실어도 AI prompt가 계속 compat 분류와 요약만 보내면, canonical summary 확장은 추천 설명 품질에 아직 반영되지 않는다
- 문제: `CanonicalRecommendationReadModelRepository` 와 응답 DTO는 이미 canonical summary field를 slot-first 기준으로 받아 두고 있었지만, `RealtimeAiGateway` prompt는 여전히 `분류` 와 짧은 설명만 LLM에 전달하고 있었다. 이 상태에서는 canonical summary를 API 밖으로 노출하는 것과 별개로, 실제 AI 재평가 입력은 아직 새 summary 정보를 소비하지 않아 projection 확장이 추천 설명 품질로 이어지지 않는다.
- 해결: `RealtimeAiGateway` 에 prompt line helper를 추가해 후보별로 `정책분야(youthMajorLabel)`, `세부분야(youthMidLabel)`, `제공방식(provisionMethodLabel)` 을 blank-safe 하게 함께 싣도록 바꾸고, `RealtimeAiGatewayTest` 에 포함/생략 규칙을 고정했다.
- 이유: canonical summary를 가장 작게 실제 소비할 수 있는 read path가 AI prompt다. 응답 계약은 additive 로 유지하되, 내부 추천 품질 경계에서는 projection-derived summary를 바로 써 보는 편이 이후 reason 품질 변화나 replay 비교에도 도움이 된다.

## 369) prompt 입력을 canonical summary 기준으로 바꿔도 replay artifact가 점수/fingerprint만 보여 주면, `ai_reason` 설명 품질 변화는 다시 raw JSON이나 DB를 뒤져야 한다
- 문제: `RealtimeAiGateway` prompt에 `youthMajorLabel`, `youthMidLabel`, `provisionMethodLabel` 을 추가한 뒤에는 점수 변화뿐 아니라 `ai_reason` 문장 변화도 같이 봐야 하는데, 기존 replay artifact는 `edu-a/b-*-scores.tsv` 와 `SUMMARY_METRIC` 중심이라 reason diff를 바로 보여주지 못했다. 이 상태에서는 prompt 변경이 실제 설명 품질에 어떤 영향을 줬는지 확인하려면 off/on 응답을 다시 수작업으로 비교해야 한다.
- 해결: `run-local-education-priority-replay.sh` 가 `user_recommendations` snapshot에 `ai_reason` 컬럼도 함께 남기고, sample A/B 각각 `edu-a-ai-reason-diff.tsv`, `edu-b-ai-reason-diff.tsv` 를 생성하도록 확장했다. summary stdout과 nightly append line에도 `SUMMARY_REASON_METRIC A_reason_changed / B_reason_changed` 를 추가했고, replay procedure 문서도 같은 artifact 기준으로 갱신했다.
- 이유: canonical summary를 prompt에 넣는 변경은 점수보다 설명 문장 쪽에서 먼저 드러날 수 있다. replay artifact가 reason diff를 1급 결과물로 남겨야, 이후 품질 비교가 점수 drift와 설명 drift를 함께 보는 형태로 바뀐다.

## 370) `ai_reason` diff artifact를 붙인 뒤엔 실제 local replay 한 번을 다시 태워 baseline 숫자를 남겨 두어야, 다음 run에서 “reason 변화가 새로 생긴 건지”를 바로 판단할 수 있다
- 문제: `edu-a-ai-reason-diff.tsv` / `edu-b-ai-reason-diff.tsv` 와 `SUMMARY_REASON_METRIC` 을 추가했어도, 첫 baseline run을 안 남기면 다음 replay에서 숫자가 바뀌었을 때 그 변화가 코드 영향인지 원래 상태였는지 구분이 어렵다.
- 해결: local `deploy/smoke/run-local-education-priority-replay.sh` 를 다시 실행해 artifact(`/tmp/tmp.vralYMXR1n`) 기준 `SUMMARY_REASON_METRIC A_reason_changed=8 B_reason_changed=0`, `SUMMARY_METRIC A_top10_target=5->8 B_top10_target=2->2` 를 확보했다. 같은 run에서 slot density는 `slot_rows=5619`, `slot_services=2305`, `slot_education_services=110`, `slot_services_YOUTH_MAJOR=2288`, `slot_services_YOUTH_MID=2170`, `slot_services_PROVISION_METHOD=1161` 로 유지됐다.
- 이유: prompt에 canonical summary를 넣은 효과는 sample A에서 reason 문장 변화가 생기고 control sample B에서는 drift가 없는지 같이 봐야 해석이 선명하다. baseline 숫자를 남겨 두면 이후 replay에서 `reason_changed` count와 top-10 개선 수를 한 번에 비교할 수 있다.

## 371) `A_reason_changed=8` 같은 total count만 보면 실제 문장 변화인지 top snapshot 진입/이탈인지 분리되지 않아, rule-only replay에서도 잘못 해석할 수 있다
- 문제: 첫 local replay artifact(`/tmp/tmp.vralYMXR1n`)의 `edu-a-ai-reason-diff.tsv` 를 열어 보니 `service_id 870/888/889/895` 는 `entered`, `616/901/905/906` 은 `exited` 로 top-30 membership 변화였고, actual `ai_reason` text 변화가 아니었다. 그런데 summary는 `A_reason_changed=8` 한 숫자만 보여서, 이걸 그대로 보면 “sample A에서 reason 문장 8개가 바뀌었다”로 읽히기 쉽다.
- 해결: replay script의 reason diff artifact에 `change_type` 컬럼을 추가하고, summary를 `A/B_reason_text_changed` 와 `A/B_reason_membership_changed` 로 분리했다. 이제 total `reason_changed` 는 유지하되, 실제 해석은 text change와 membership change를 따로 본다.
- 이유: canonical summary를 prompt에 넣은 효과는 문장 자체 변화와 순위권 구성 변화가 동시에 나타날 수 있다. 이 둘을 같은 숫자로 합치면 rule-only replay처럼 `ai_reason` 자체가 `NULL` 인 경우에도 misleading total이 나올 수 있으므로, 분리 지표가 필요하다.

## 372) 새 분리 지표를 붙였으면 baseline도 다시 찍어 둬야, 다음 replay에서 “reason text가 새로 바뀐 건지”를 즉시 비교할 수 있다
- 문제: `A/B_reason_text_changed`, `A/B_reason_membership_changed` 를 추가만 하고 baseline artifact를 갱신하지 않으면, 다음 run에서 값이 생겨도 이게 새 현상인지 기존 상태인지 바로 비교할 기준이 없다.
- 해결: local `deploy/smoke/run-local-education-priority-replay.sh` 를 다시 실행해 artifact(`/tmp/tmp.0yFeDNSY6k`) 기준 `SUMMARY_REASON_METRIC A_reason_changed=8 B_reason_changed=0 A_reason_text_changed=0 B_reason_text_changed=0 A_reason_membership_changed=8 B_reason_membership_changed=0` 를 확보했다. 같은 run에서 `SUMMARY_METRIC A_top10_target=5->8 B_top10_target=2->2`, slot density(`slot_rows=5619`, `slot_services=2305`, `slot_education_services=110`)도 그대로 유지됐다.
- 이유: 이 baseline이 있어야 이후 real-openai replay나 prompt 추가 변경에서 `reason text` 자체가 달라졌는지, 아니면 단순히 top snapshot 구성만 바뀐 건지를 바로 분리해서 볼 수 있다.

## 373) `real-openai` replay가 cluster cache를 그대로 타면 prompt 변경 영향보다 캐시 재사용 여부가 먼저 걸려, live AI 이유문장 변화를 검증하지 못할 수 있다
- 문제: `USE_REAL_OPENAI_FOR_REPLAY=true` 만 켠 run(`/tmp/tmp.9tou3184L8`)에서는 `SUMMARY_REASON_METRIC A_reason_text_changed=0 B_reason_text_changed=0` 이고 trace artifact도 대부분 비어 있었다. 이 상태는 prompt 변화가 없어서가 아니라, `cluster_ai_results` cache가 남아 실제 `RealtimeAiGateway` 호출이 충분히 일어나지 않았을 가능성이 컸다.
- 해결: replay script에 `CLEAR_CLUSTER_AI_CACHE_BEFORE_REPLAY=true` 옵션을 추가해 off/on phase 시작 전에 `cluster_ai_results` 를 비우도록 했다. 그 뒤 real-openai replay를 다시 실행해 artifact(`/tmp/tmp.TBDFrxfGqo`) 기준 `A_reason_text_changed=15`, `B_reason_text_changed=15`, `A_top10_target=3->5`, `B_top10_target=0->2`, `A_fp=same`, `B_fp=different` 를 확보했다.
- 이유: canonical summary prompt 효과를 live AI에서 보려면 먼저 cache reuse를 걷어내야 한다. cache가 남아 있으면 “실제 AI가 다시 읽고 reason을 바꿨는가”보다 “예전 cluster 결과를 재사용했는가”가 더 큰 변수가 된다.

## 374) cache를 비운 real-openai replay에서 `A_reason_text_changed=15` 만 보면 prompt 효과처럼 보이지만, control sample B도 동일하게 15건 바뀌면 우선 해석은 live reasoning drift 쪽이어야 한다
- 문제: cache clear 포함 artifact(`/tmp/tmp.TBDFrxfGqo`)를 열어 보면 sample A는 `text_changed=15`, `entered/exited=8`, sample B는 `text_changed=15`, `membership_changed=0` 이다. sample A만 보면 canonical summary prompt가 이유문장을 적극적으로 바꿨다고 읽기 쉽지만, control sample B도 같은 수의 text change가 발생하고 `B_top10_target=0->2`, `B_fp=different` 까지 같이 나왔기 때문에 이것을 그대로 제품 품질 개선 증거로 쓰면 과해진다.
- 해결: current-state와 replay procedure 문서에 이 run의 해석을 “prompt 영향은 보이지만 control drift와 분리되지 않았다”로 고정했다. `edu-a-ai-reason-diff.tsv`, `edu-b-ai-reason-diff.tsv` 에서도 실제로 `직접적인 도움`, `특정 분야에 국한`, `주거비 부담 완화` 같은 phrasing 변화가 양쪽에 공통으로 나타난다는 점을 같이 남겼다.
- 이유: live AI replay는 지금도 코드 회귀 검증보다 진단 증거 수집에 가깝다. control sample이 같은 규모의 text drift를 보이면, 우선 결론은 “canonical summary prompt 효과 존재 가능성”이 아니라 “effect와 live variability를 아직 분리하지 못했다”가 맞다.

## 375) `edu-a/edu-b-ai-reason-diff.tsv` 를 그대로 로그에만 남겨 두면 다음 사람이 artifact를 다시 열어 같은 패턴을 수작업으로 재분류해야 한다
- 문제: cache clear real-openai artifact(`/tmp/tmp.TBDFrxfGqo`)에서 sample A/B reason diff를 읽어 보면, sample A는 `도움이 될 수 있음 -> 실질적인 도움이 됨`, `특정 분야에 국한`, `관심이 있는 청년` 같은 분야 적합성/교육 타깃 phrasing이 반복되고, sample B는 `연관성이 낮음`, `주거비 부담 완화`, `큰 도움이 될 것임` 같은 wording drift가 반복된다. 그런데 이 해석을 로그 한 줄로만 남기면 다음 replay 때 다시 artifact TSV를 열어 같은 분류를 반복하게 된다.
- 해결: live AI artifact 전용 요약 문서 [policy-normalization-live-ai-reason-patterns.md](../history/ai/policy-normalization-live-ai-reason-patterns.md)를 추가해 sample A/B의 `text_changed` / `membership_changed` 구조와 반복 문장 패턴, 현재 결론을 따로 정리했다.
- 이유: 이 단계의 산출물은 코드 변경보다 해석 가능한 진단 기록이다. pattern memo를 별도 문서로 떼 두면 이후 replay를 더 수집해도 “이번 wording drift가 새 현상인지, 이미 보던 패턴인지”를 더 빠르게 비교할 수 있다.

## 376) pattern memo까지 따로 만들어도 replay summary가 여전히 숫자만 뿌리면, 다음 run마다 `edu-a/edu-b-ai-reason-diff.tsv` 를 열어 상위 phrase를 다시 읽어야 한다
- 문제: 현재는 `A_reason_text_changed=15` 같은 count와 별도 memo는 있지만, replay를 막 끝낸 직후 stdout만 보고는 어떤 phrase가 많이 흔들렸는지 알 수 없다. 결국 `ai-reason-diff.tsv` 나 pattern memo를 다시 열어 `연관성이 낮`, `특정 분야에 국한`, `실질적인 도움이`, `주거비 부담` 같은 축을 눈으로 세야 한다.
- 해결: replay script에 `SUMMARY_REASON_PATTERN` 한 줄과 `ai-reason-pattern-summary.tsv` artifact를 추가해 sample A/B별 상위 phrase count를 바로 보이게 했다. 현재 요약 대상 phrase는 `direct_help`, `practical_help`, `narrow_scope`, `interest_fit`, `low_relevance`, `housing_burden`, `strong_help`, `job_opportunity`, `practical_experience`, `creativity` 다.
- 이유: 이 단계는 제품 계약보다 triage 속도가 더 중요하다. count와 함께 “어떤 표현 축이 흔들렸는가”를 summary에 바로 노출하면 다음 replay 비교가 TSV 재독 없이도 훨씬 빨라진다.

## 377) `real-openai` replay가 diagnostic 용도인데도 sample A 미개선을 rule-only와 같은 hard fail로 처리하면, live variability를 수집하기 위한 run 자체가 자주 중단된다
- 문제: cache clear 포함 `real-openai` run에서 artifact(`/tmp/tmp.3DkyjL1B9n`)는 `SUMMARY_REASON_PATTERN A_top_patterns=interest_fit:1 B_top_patterns=strong_help:2` 같은 새 증적을 남겼지만, 동시에 `A_top10_target=0->0` 이라 sample A hard assert에 걸려 exit code 1 로 끝났다. 문서상 real-openai replay는 PR hard gate가 아니라 diagnostic 인데, sample A 미개선까지 hard fail로 두면 이유문장/패턴 drift 증적을 남기는 run이 자주 중단된다.
- 해결: replay summary Python에서 `mode == real-openai` 일 때는 sample A 미개선도 `WARNING:` 으로만 출력하고 종료하지 않도록 바꿨다. `rule-only-invalid-key` 모드의 sample A hard assert는 그대로 유지한다.
- 이유: 현재 live AI replay의 목적은 deterministic gate가 아니라 drift 관찰이다. sample A 미개선 자체도 진단 신호인데, 그 때문에 artifact 수집이 중단되면 오히려 해석 재료가 줄어든다.

## 378) warning-only 정책으로 run을 끝까지 살린 뒤엔, 그 산출물을 다시 current-state 기준선으로 박아 두어야 다음 pattern summary 비교가 가능하다
- 문제: warning-only로 바꾼 뒤 cache clear `real-openai` run(`/tmp/tmp.6YybgXCbIo`)은 끝까지 artifact를 남겼지만, 이 숫자를 문서에 안 박아 두면 다음 run의 `SUMMARY_REASON_PATTERN` 이 새 현상인지 기존 분포인지 바로 비교할 수 없다.
- 해결: latest artifact 기준 `A_reason_text_changed=10`, `B_reason_text_changed=14`, `A_reason_membership_changed=2`, `B_reason_membership_changed=0`, `A_fp=different`, `B_fp=same`, `SUMMARY_REASON_PATTERN A_top_patterns=interest_fit:2,direct_help:1,job_opportunity:1 B_top_patterns=strong_help:1` 를 current-state / replay procedure / phase-plan에 반영했다.
- 이유: real-openai replay는 deterministic 수치보다 패턴 분포를 계속 쌓는 쪽이 더 중요하다. warning run도 기준선으로 승격해 둬야 다음 artifact를 “새 drift”와 “기존 분포의 반복”으로 더 빠르게 나눌 수 있다.

## 379) `YOUTH_MAJOR/YOUTH_MID/PROVISION_METHOD` 만 projection과 응답에 연결한 상태로 두면, 새 summary slot 구조가 `GOV24_*` 축에서는 여전히 write-only 데이터로 남는다
- 문제: `service_taxonomy_summary_slots` 에 managed slot key를 dual-write/backfill 한 뒤에도 recommendation read-model과 policy/recommendation 응답은 `youthMajorLabel`, `youthMidLabel`, `provisionMethodLabel` 만 additive field로 노출하고 있었다. 이 상태면 `GOV24_SERVICE_FIELD`, `GOV24_USER_TYPE`, `GOV24_BENEFIT_TYPE` 는 slot 저장/bridge 경계까지만 검증되고 실제 소비 경계에서는 계속 비가시 상태로 남는다.
- 해결: `CanonicalRecommendationReadModelRepository` base row를 `GOV24_*` slot-first / legacy fallback 으로 확장하고, `RecommendationCandidateProjection`, policy summary/detail/ranking 응답, recommendation 응답에 `gov24ServiceFieldLabel`, `gov24UserTypeLabel`, `gov24BenefitTypeLabel` additive field를 추가했다. 같이 `RealtimeAiGateway` prompt에도 같은 canonical summary 축을 blank-safe 로 넣고, repository/WebMvc/service/gateway 테스트와 `api-mapping.md`, `policy-normalization-current-state.md` 문서를 갱신했다.
- 이유: 현재 local density는 `GOV24_* = 0` 이지만, 구조 완결성은 populated 여부와 별개다. slot-first 저장 구조가 실제 read/API/prompt 경계를 관통해야 다음 source에서 `GOV24_*` 값이 채워졌을 때 추가 설계 없이 바로 소비할 수 있다.

## 380) unit test만으로 `GOV24_*` slot-first 확장을 닫으면, 실제 MySQL에서 slot row와 legacy row가 섞였을 때 per-field 우선순위가 그대로 유지되는지 끝까지 증명되지 않는다
- 문제: `CanonicalRecommendationReadModelRepositoryTest` 는 SQL 결과 row를 직접 stub 해서 `GOV24_*` projection과 응답/API contract를 고정했지만, 실제 integration DB에서 `service_taxonomies` legacy row와 `service_taxonomy_summary_slots` row를 함께 둔 상태의 조인 결과까지는 보지 못했다. 이 경계를 안 닫으면 slot table 존재 시 subquery join/field fallback 이 실제 MySQL에서도 같은 의미로 동작하는지 마지막 확신이 부족하다.
- 해결: `CanonicalRecommendationReadModelIntegrationTest` 를 추가해 integration DB에 `welfare_services`, `service_taxonomies`, `service_taxonomy_summary_slots` fixture를 직접 넣고, `CanonicalRecommendationReadModelRepository.findByServiceIds(...)` 가 `YOUTH_MAJOR`, `PROVISION_METHOD`, `GOV24_SERVICE_FIELD`, `GOV24_BENEFIT_TYPE` 는 slot 값으로 읽고 `YOUTH_MID`, `GOV24_USER_TYPE` 은 legacy row로 fallback 하는 것을 고정했다.
- 이유: slot-first migration은 write-path보다 read-path 우선순위가 더 중요하다. 실제 MySQL integration 한 케이스라도 박아 둬야 이후 query 리팩터링이나 migration 보정 때 “join은 되는데 field priority가 뒤집히는” 회귀를 빨리 잡을 수 있다.

## 381) replay artifact가 여전히 `service_id/title/compat/youth_major` 만 남기면, `GOV24_*` 와 `provisionMethod/youthMid` 축이 prompt나 reason diff에 어떤 후보와 함께 들어갔는지 artifact만 보고는 바로 대조할 수 없다
- 문제: `run-local-education-priority-replay.sh` 는 이미 `RealtimeAiGateway` prompt에 `youthMajor/youthMid/provisionMethod/GOV24_*` canonical summary를 같이 넣지만, artifact 쪽 `response-service-meta.tsv` 는 아직 `service_id/title/compat/youth_major` 네 컬럼만 남기고 있었다. 이 상태면 다음 live replay에서 `ai_reason` 나 pattern drift가 생겨도, 후보별 canonical summary 전체를 보려면 DB나 API 응답을 다시 조회해야 한다.
- 해결: `response-service-meta.tsv` 생성 쿼리를 slot-first / legacy fallback 기준 `youthMajor`, `youthMid`, `provisionMethod`, `gov24ServiceField`, `gov24UserType`, `gov24BenefitType` 까지 확장하고, summary Python parser도 같은 9컬럼 포맷을 읽도록 바꿨다. replay procedure 문서에도 meta artifact가 canonical summary 대조용이라는 점을 같이 남겼다.
- 이유: replay는 점점 “순위 숫자 비교”보다 “왜 이 reason/prompt drift가 났는지”를 보는 단계로 가고 있다. artifact 하나에 후보별 canonical summary 축을 같이 실어야 다음 diff triage가 DB 재조회 없이 끝난다.

## 382) local apply 스크립트가 slot service density만 출력하면, replay summary가 보여주는 `slot_rows_*` 와 바로 대조되지 않아 snapshot 상태를 두 스크립트 사이에서 다시 변환해 읽어야 한다
- 문제: `apply-local-policy-sidecar-draft.sh` 는 `slot_density_<SLOT>=<distinct service count>` 만 출력하고, replay summary는 `slot_services_*` 와 `slot_rows_*` 를 둘 다 출력한다. 이 차이 때문에 local snapshot을 재적용한 직후에는 “서비스 수는 같은데 row 수가 늘었는지” 같은 비교가 즉시 안 되고, `service_taxonomy_summary_slots` 를 다시 직접 조회해야 했다.
- 해결: apply 스크립트에도 `slot_row_density_<SLOT>=<row count>` 출력을 추가해 `YOUTH_MAJOR/YOUTH_MID/PROVISION_METHOD/GOV24_*` 각 축의 service density와 row density를 동시에 보이게 했다. current-state/phase-plan 문서도 같은 관측 축으로 맞췄다.
- 이유: slot-first migration은 write/backfill 결과를 빠르게 읽어내는 관측성이 중요하다. apply와 replay가 같은 density vocabulary를 써야 snapshot 상태를 한 번에 비교할 수 있다.

## 383) replay meta에 canonical summary 축을 더 실은 뒤에도 Python parser가 trailing empty column이나 multiline summary를 그대로 가정하면, artifact는 생성돼도 summary 단계에서 즉시 죽을 수 있다
- 문제: `response-service-meta.tsv` 를 `service_id/title/compat/youth_major/youth_mid/provision_method/gov24_service_field/gov24_user_type/gov24_benefit_type` 9컬럼으로 확장한 뒤 `KEEP_ARTIFACTS=true deploy/smoke/run-local-education-priority-replay.sh` 를 바로 재실행하니, 첫 번째는 trailing empty column 때문에 `expected 9, got 6`, 두 번째는 `provision_method` 장문에 실제 줄바꿈이 들어가 `service_id` 위치에서 `invalid literal for int()` 예외가 났다.
- 해결: replay script의 meta parser는 `split(\"\\t\")` 결과를 9칸까지 padding 하도록 바꾸고, `collect_service_meta` SQL은 `title/unified_category/youth*/provision/gov24*` 텍스트를 `REPLACE(..., CHAR(10|13|9), ' ')` 로 정규화해 TSV row 경계를 깨지 않게 만들었다. 그 뒤 artifact `/tmp/tmp.BbthQST2op` 기준 `response-service-meta.tsv` 가 9컬럼으로 안정적으로 생성되고, `SUMMARY_METRIC A_top10_target=5->8 B_top10_target=2->2`, `SUMMARY_SLOT_METRIC slot_rows=5619 ...` 도 다시 끝까지 출력되는 것을 확인했다.
- 이유: replay artifact는 DB를 다시 조회하지 않고도 prompt/reason drift를 해석하는 용도라, meta TSV가 조금만 brittle해도 전체 smoke가 마지막 summary 단계에서 자주 죽는다. canonical summary 축을 늘릴수록 row-safe text normalization과 lenient parser가 같이 필요하다.

## 384) apply 스크립트가 `slot_density_*` 를 `GROUP BY` 결과만 그대로 출력하면, 0건인 `GOV24_*` slot은 라인이 통째로 빠져 current-state 기준선이 매번 “없음”인지 “0”인지 애매해진다
- 문제: `apply-local-policy-sidecar-draft.sh` 를 실제 다시 돌려 보니 `service_taxonomies=3708`, `service_taxonomy_summary_slots=5674`, `slot_density_YOUTH_MAJOR=2313`, `slot_density_YOUTH_MID=2191`, `slot_density_PROVISION_METHOD=1170` 까지는 잘 나오는데, `GOV24_*` 는 row가 0이라 `GROUP BY slot_key` 결과 자체가 없어져 출력 라인이 사라졌다. 이 상태면 current-state나 triage 로그에서 `GOV24_*` 가 “정말 0인지, 스크립트가 안 본 건지”를 매번 다시 해석해야 한다.
- 해결: apply 스크립트의 density SQL을 `UNION ALL` 고정 축 집계로 바꿔 `GOV24_SERVICE_FIELD`, `GOV24_USER_TYPE`, `GOV24_BENEFIT_TYPE`, `PROVISION_METHOD`, `YOUTH_MAJOR`, `YOUTH_MID` 를 항상 한 줄씩 출력하게 만들고, 실제 재실행으로 `slot_density_GOV24_* = 0`, `slot_row_density_GOV24_* = 0` 이 명시적으로 찍히는 것을 확인했다.
- 이유: slot-first migration의 관측치는 “라인이 있음”보다 “0도 명시적으로 보임”이 더 중요하다. populated 되지 않은 축까지 항상 출력돼야 다음 source onboarding이나 data acquisition 이후 값이 바뀌는 순간 diff가 바로 눈에 들어온다.

## 385) replay summary가 `slot_services_*` 만 보여주면 apply 쪽 `slot_row_density_*` 와 다시 머릿속으로 대응시켜야 해서, 최신 snapshot에서 row inflation이나 duplicate 여부를 한 번에 읽기 어렵다
- 문제: `run-local-education-priority-replay.sh` 는 이미 `summary-slot-metrics.tsv` 에 `slot_rows_YOUTH_MAJOR`, `slot_rows_YOUTH_MID`, `slot_rows_PROVISION_METHOD`, `slot_rows_GOV24_*` 를 모으고 있었지만, 최종 stdout `SUMMARY_SLOT_METRIC` 과 nightly append line에는 아직 `slot_services_*` 만 내보냈다. 이 상태면 replay artifact만 보고는 “distinct service count는 같은데 raw row count가 늘었는지”를 바로 알 수 없고, apply output이나 metrics TSV를 다시 열어야 했다.
- 해결: replay summary와 append line에도 같은 `slot_rows_*` 축을 같이 싣고, `KEEP_ARTIFACTS=true deploy/smoke/run-local-education-priority-replay.sh` 재실행으로 artifact `/tmp/tmp.lP4I9NWUUU` 기준 `slot_rows=5674`, `slot_services=2331`, `slot_rows_YOUTH_MAJOR=2313`, `slot_rows_YOUTH_MID=2191`, `slot_rows_PROVISION_METHOD=1170`, `slot_rows_GOV24_* = 0` 이 끝까지 그대로 노출되는 것을 확인했다.
- 이유: apply와 replay가 같은 density vocabulary를 끝단 summary까지 공유해야, slot-first migration 관측을 “script마다 다른 출력”이 아니라 하나의 inventory 기준선으로 읽을 수 있다.

## 386) 절차 문서가 여전히 `slot_services_*` 중심으로만 읽히면, 실제 스크립트/summary는 `slot_rows_*` 까지 내보내는데도 다음 사람이 replay artifact를 반만 읽고 지나갈 수 있다
- 문제: `run-local-education-priority-replay.sh` 와 current-state/log는 이미 `slot_rows_*` 기준까지 확장됐지만, replay procedure 문서의 summary 해석 bullet은 아직 reason/pattern 쪽까지만 강조하고 있었다. 이 상태면 절차 문서만 보고 replay를 다시 돌리는 사람은 `SUMMARY_SLOT_METRIC` 에 새로 붙은 row density 축을 놓치기 쉽다.
- 해결: replay procedure 문서에 `SUMMARY_SLOT_METRIC` 은 `slot_services_*` 와 `slot_rows_*` 를 같이 읽어 distinct service density와 raw row density를 apply 출력과 같은 축으로 바로 대조하라는 기준을 추가하고, numbering도 다시 맞췄다.
- 이유: inventory/slot-first follow-up은 코드보다 관측 기준 일치가 중요하다. 실제 스크립트가 내는 축과 절차 문서가 안내하는 축이 다르면 같은 artifact를 두고도 해석이 갈린다.

## 387) `GOV24_*` density가 계속 0이면 writer/read-model 버그처럼 보이기 쉽지만, 현재 local snapshot 기준으로는 populate할 source row 자체가 없다
- 문제: `slot_services_GOV24_* = 0`, `slot_rows_GOV24_* = 0` 이 계속 유지되다 보니, 겉으로만 보면 `CanonicalTaxonomySummarySlots` dual-write 나 read-model slot-first 경계가 `Gov24` summary를 놓치고 있는 것처럼 읽힐 여지가 있었다.
- 해결: local DB를 직접 재조회해 `welfare_services.source_type` 분포가 `YOUTH=2364`, `BOKJIRO_CENTRAL=119`, `BOKJIRO_LOCAL=1225` 뿐이고, `service_taxonomy_summary_slots` 도 `PROVISION_METHOD`, `YOUTH_MAJOR`, `YOUTH_MID` 만 채워져 있음을 확인했다. current-state 문서에도 `GOV24_* = 0` 의 이유를 “runtime collect 부재 + blocked import/backfill 트랙 유지”로 명시했다.
- 이유: 현재 `Gov24` 는 active runtime source가 아니라 external codebook 응답을 기다리는 blocked import/backfill 트랙이다. source row 자체가 없는 상태에서 `GOV24_*` 밀도가 0인 것은 현재 구조의 예상 결과이지, 즉시 코드 버그로 볼 신호는 아니다.

## 388) `Gov24` blocked 맥락이 여러 문서에 흩어져 있으면, 다음 사람이 “지금 inactive 인 이유”와 “다시 열 조건”을 한 번에 못 보고 같은 확인을 반복하게 된다
- 문제: 현재 `Gov24` 관련 판단은 active-track 우선순위 문서, local pending inventory, blocked SQL reopen 우선순위, request package checklist에 나뉘어 있었다. 각각은 맞지만, “왜 지금 active 구현 트랙이 아니고 언제 다시 여는가”를 한 번에 보려면 여러 문서를 왕복해야 했다.
- 해결: `policy-gov24-blocked-track-status.md` 를 추가해 현재 inactive 이유, local snapshot에 `Gov24` source row가 없다는 점, reopen 조건, request package/판정 기준, practical next action을 한 장으로 요약하고, active-track 문서와 pending inventory entrypoint 에도 링크를 걸었다.
- 이유: 이 트랙은 지금 코드를 더 파는 단계가 아니라 blocked 상태를 정확히 유지하는 게 중요하다. entrypoint 문서가 하나 있어야 불필요한 재확인과 중복 문서 탐색을 줄일 수 있다.

## 389) blocked entrypoint를 만들어도 `policy-docs-index` 와 전체 문서 맵에 안 걸려 있으면, 실제 읽기 시작 지점에서는 여전히 못 본다
- 문제: `policy-gov24-blocked-track-status.md` 를 추가한 뒤에도 policy 문서군의 첫 진입점인 `policy-docs-index.md` 와 상위 `documentation-map.md` 에는 아직 링크가 없었다. 이 상태면 새 문서는 존재하더라도 “알고 있는 사람만 보는 문서”로 남을 수 있다.
- 해결: `policy-docs-index.md` 의 현재 코드/로컬 검증 기준 및 blocked 섹션, `documentation-map.md` 의 external blocked 트랙 섹션에 `policy-gov24-blocked-track-status.md` 링크를 추가했다.
- 이유: blocked 상태를 잘 유지하려면 문서 자체뿐 아니라 진입 경로가 중요하다. entrypoint가 실제 인덱스에 걸려 있어야 다음 사람이 같은 결론을 다시 만들지 않는다.

## 390) policy 인덱스와 문서 맵에만 링크가 있어도, 실제 top-level 진입점인 `start/current-state/README` 에서 안 보이면 처음 들어온 사람은 여전히 놓친다
- 문제: `policy-gov24-blocked-track-status.md` 를 policy 문서군 내부 인덱스와 `documentation-map.md` 에 연결한 뒤에도, 작업 시작 시 가장 먼저 보는 `start.md`, `current-state.md`, `docs/README.md` 에는 아직 링크가 없었다. 이 상태면 `Gov24` 가 왜 inactive 인지 알고 싶은 사용자는 한 단계 더 들어가서야 문서를 찾게 된다.
- 해결: 세 top-level entrypoint 모두에 `policy-gov24-blocked-track-status.md` 링크를 추가해, `start -> current-state -> policy docs` 어느 경로로 들어와도 blocked 상태 문서를 바로 찾게 정리했다.
- 이유: blocked 트랙은 “존재 여부”보다 “얼마나 빨리 도달하느냐”가 중요하다. top-level entrypoint 에서 바로 보여야 같은 배경 설명을 반복하지 않게 된다.

## 391) `documentation-map.md` 의 blocked 섹션에만 `Gov24` 링크가 있으면, 현재 구현 요약이나 새 작업 읽기 순서만 따라가는 사람은 다시 놓칠 수 있다
- 문제: `documentation-map.md` 에 `policy-gov24-blocked-track-status.md` 링크가 external blocked 트랙 섹션에는 있었지만, 위쪽의 현재 구현 상태 요약 리스트와 “새 작업을 열 때” 읽기 순서에는 아직 없었다. 이 상태면 blocked 트랙이든 active 트랙이든 문서 맵만 빠르게 훑는 사용자는 여전히 해당 entrypoint를 지나칠 수 있다.
- 해결: `documentation-map.md` 의 현재 구현 상태 요약 섹션에 `policy-gov24-blocked-track-status.md` 를 추가하고, 새 작업 읽기 순서에도 “`Gov24` inactive/reopen 판단이 필요하면 먼저 본다”는 한 줄을 넣었다.
- 이유: entrypoint 문서는 한 번만 링크해선 충분하지 않다. 실제 사용 흐름에서 사람들이 훑는 섹션마다 보여야 blocked 상태 판단이 재학습 없이 반복 가능해진다.

## 392) top-level entrypoint에 `Gov24 blocked` 만 있고 `next active track` / `local pending` 이 빠져 있으면, 지금 뭘 해야 하는지는 여전히 한 단계 더 내려가서 찾아야 한다
- 문제: `start.md`, `current-state.md`, `docs/README.md` 는 이제 `policy-gov24-blocked-track-status.md` 를 바로 보여주지만, 반대로 “지금 다음 작업이 뭐냐”를 잡는 `policy-next-active-track-priority.md` 와 `policy-local-closeout-pending-inventory.md` 는 아직 top-level 리스트에 없었다. 이 상태면 blocked 상태는 빨리 찾을 수 있어도 실제 active work 우선순위는 다시 policy 문서군 안으로 들어가야 했다.
- 해결: 세 top-level entrypoint 모두에 `policy-next-active-track-priority.md` 와 `policy-local-closeout-pending-inventory.md` 링크를 추가해, 현재 상태/blocked 상태/다음 작업 우선순위를 같은 높이에서 바로 찾게 정리했다.
- 이유: entrypoint 정리는 특정 한 문서만 드러내는 게 목적이 아니라, “지금 상황 판단 -> 다음 액션 선택” 흐름을 한 화면 안에서 닫게 만드는 게 목적이다.

## 393) top-level entrypoint에 개별 policy current-state만 있고 `policy-docs-index.md` 자체가 없으면, policy 문서군 전체 길찾기는 다시 `documentation-map` 이나 검색에 의존하게 된다
- 문제: `start.md`, `current-state.md`, `docs/README.md` 에 개별 policy 상태 문서는 많이 올라왔지만, 정작 policy 문서군 1차 진입점인 `policy-docs-index.md` 는 빠져 있었다. 이 상태면 사용자는 특정 문서는 바로 열 수 있어도, policy 묶음 전체를 어떻게 읽을지는 다시 `documentation-map.md` 나 파일 검색으로 돌아가야 했다.
- 해결: 세 top-level entrypoint 모두에 `policy-docs-index.md` 링크를 추가해, 개별 current-state 문서와 문서군 인덱스를 같은 층위에서 바로 열 수 있게 정리했다.
- 이유: entrypoint는 개별 문서 노출만으로는 충분하지 않다. 문서군 전체의 읽기 순서를 잡아 주는 인덱스도 같은 시작점에서 보여야 길찾기 비용이 줄어든다.

## 394) policy 쪽만 top-level에서 문서군 인덱스를 보이고 auth 쪽은 안 보이면, 두 문서군의 진입 방식이 달라져 다시 기억 비용이 생긴다
- 문제: `policy-docs-index.md` 를 top-level entrypoint에 올린 뒤에는 policy 문서군은 바로 인덱스로 들어갈 수 있었지만, auth 쪽은 여전히 `auth-session-revocation-current-state.md` 만 노출되고 `auth-docs-index.md` 는 `documentation-map.md` 안으로 들어가야 보였다. 이 상태면 문서군마다 진입 규칙이 달라져 사용자가 다시 예외를 기억해야 했다.
- 해결: `start.md`, `current-state.md`, `docs/README.md` 에 `auth-docs-index.md` 링크를 추가해, auth/policy 두 문서군 모두 top-level entrypoint에서 바로 인덱스로 들어가게 맞췄다.
- 이유: entrypoint 설계는 내용보다 패턴 일관성이 중요하다. 문서군마다 들어가는 방식이 같아야 다음 사용자가 생각 없이도 원하는 인덱스로 이동할 수 있다.

## 395) 수집 쪽만 문서군 인덱스가 없으면, auth/policy 는 인덱스로 들어가고 collect 는 개별 current-state/checklist를 따로 기억해야 해서 구조가 다시 비대칭이 된다
- 문제: auth/policy 문서군은 각각 `auth-docs-index.md`, `policy-docs-index.md` 가 생겨 top-level entrypoint에서도 바로 들어갈 수 있는데, collect 쪽은 여전히 `collect-current-state.md`, `collect-operation-checklist.md`, `collect-ops.md` 를 개별로 기억해야 했다. 이 상태면 문서군마다 길찾기 규칙이 다시 달라졌다.
- 해결: `collect-docs-index.md` 를 추가해 수집 현재 동작 기준, 실행 체크리스트, 운영 기준, 실행/장애 템플릿을 한 문서에서 정리하고, `start.md`, `current-state.md`, `docs/README.md`, `documentation-map.md` 에 모두 연결했다.
- 이유: entrypoint 정리는 특정 주제 하나만 예쁘게 만드는 일이 아니라, 문서군마다 같은 진입 패턴을 갖게 만드는 일이다. collect 도 인덱스가 있어야 문서 구조가 auth/policy 와 같은 수준으로 정리된다.

## 396) top-level entrypoint 와 docs-index 를 정리해도, 각 current-state 문서가 자기 문서군 인덱스로 다시 안 돌아가면 읽기 흐름이 중간에서 끊긴다
- 문제: `start/current-state/README/documentation-map` 에는 `auth/collect/recommendation/policy` 문서군 인덱스를 많이 연결했지만, 정작 각 current-state 문서 안에서는 자기 `*-docs-index.md` 를 다시 가리키지 않았다. 이 상태면 사용자는 개별 current-state 문서로 바로 들어왔을 때 문서군 전체 길찾기를 다시 파일 검색이나 상위 문서로 역이동해야 했다.
- 해결: `auth-session-revocation-current-state.md`, `collect-current-state.md`, `recommendation-current-state.md`, `policy-normalization-current-state.md` 상단에 각 문서군 진입점 링크를 추가했다.
- 이유: entrypoint 설계는 상위 문서에서만 끝나면 부족하다. 많이 열리는 current-state 문서 자체도 “이 문서군 전체는 어디서 시작하나”를 한 줄로 보여줘야 읽기 흐름이 끊기지 않는다.

## 397) current-state 문서만 docs-index 로 돌아가고 checklist/playbook 문서는 그대로 두면, 실제 실행/설계 문서에서 다시 길을 잃는다
- 문제: current-state 문서에는 각 `*-docs-index.md` 진입점을 넣었지만, 실무에서 자주 직접 여는 `collect-operation-checklist.md`, `auth-admin-forced-logout-closeout.md`, `recommendation-pipeline.md`, `policy-source-onboarding-playbook.md` 는 여전히 개별 문서 안에서 문서군 인덱스로 돌아가는 링크가 없었다. 이 상태면 current-state 에서 한 번 정리한 읽기 흐름이 checklist/playbook 단계에서 다시 끊긴다.
- 해결: 위 네 문서 상단에 각각 `collect/auth/recommendation/policy` 문서군 진입점 링크를 추가했다.
- 이유: entrypoint 패턴은 current-state 에만 적용하면 반쪽짜리다. 실제 실행·설계에 자주 쓰는 checklist/playbook 문서도 자기 문서군 인덱스로 바로 복귀할 수 있어야 문서 구조가 일관된다.

## 398) `frontend-qa` 와 `local-validation` 문서군도 top-level 인덱스는 생겼지만, 실제 자주 여는 current-state/checklist/runbook 안에서는 다시 길을 잃을 수 있다
- 문제: `frontend-qa-docs-index.md` 와 `local-validation-docs-index.md` 를 만들고 top-level entrypoint 에도 연결했지만, 정작 많이 직접 여는 `frontend-qa-current-state.md`, `frontend-qa-checklist.md`, `testing.md`, `runtime-api-smoke-commands.md` 는 각 문서 안에서 다시 자기 문서군 인덱스로 돌아가는 링크가 없었다. 이 상태면 사용자는 개별 실행 문서로 곧바로 들어왔을 때 문서군 전체 길찾기를 다시 상위 문서나 검색에 의존하게 된다.
- 해결: 위 네 문서 상단에 각각 `frontend-qa-docs-index.md` 또는 `local-validation-docs-index.md` 진입점 링크를 추가했다.
- 이유: docs-index 패턴은 auth/collect/recommendation/policy 에만 적용하면 또 비대칭이 된다. QA와 로컬 검증 문서도 같은 독법을 따르게 해야 전체 문서 구조가 일관된다.

## 399) current-state/checklist 까지 정리해도 operation/template 문서가 빠지면, 실제 실행 기록 단계에서 다시 문서군 인덱스를 잃는다
- 문제: `auth/collect/recommendation/frontend-qa` 문서군은 top-level, current-state, checklist 일부까지는 docs-index 흐름을 맞췄지만, 정작 실무에서 많이 직접 여는 `auth-operation-checklist.md`, `auth-incident-template.md`, `collect-incident-template.md`, `frontend-qa-template.md`, `recommendation-operation-checklist.md`, `recommendation-replay-template.md` 는 여전히 각 문서 안에서 자기 문서군 인덱스로 다시 돌아가는 링크가 없었다.
- 해결: 위 여섯 문서 상단에 각 문서군 진입점 링크를 추가했다.
- 이유: entrypoint 패턴은 “현재 상태 확인”까지만이 아니라 “실행/기록” 단계까지 이어져야 한다. operation/template 문서가 docs-index 로 복귀하지 않으면 마지막 단계에서 다시 파일명을 외워야 한다.

## 400) policy 문서군은 current-state/playbook 은 정리됐어도, 실제 우선순위·blocked·checklist 문서 안에서는 여전히 인덱스로 복귀하지 못했다
- 문제: `policy-docs-index.md` 와 top-level entrypoint 를 정리한 뒤에도, 실제로 자주 직접 여는 `policy-next-active-track-priority.md`, `policy-local-closeout-pending-inventory.md`, `policy-gov24-blocked-track-status.md`, `policy-source-onboarding-checklist.md` 는 각 문서 안에서 다시 `policy-docs-index.md` 로 돌아가는 링크가 없었다. 이 상태면 policy 문서군은 일부 문서만 entrypoint 규칙을 따르고 나머지는 다시 개별 파일명을 기억해야 했다.
- 해결: 위 네 문서 상단에 모두 `policy-docs-index.md` 진입점 링크를 추가했다.
- 이유: policy 쪽은 문서 수가 많고 분기 판단도 많아서, 개별 문서에서 인덱스로 바로 복귀할 수 있어야 재탐색 비용이 줄어든다.

## 401) 운영/우선순위 문서만 정리하고 구조/이행 설계 문서를 그대로 두면, collect/policy 문서군은 설계 단계에서 다시 길찾기가 끊긴다
- 문제: `collect-docs-index.md` 와 `policy-docs-index.md` 를 만들고 current-state/checklist/ops 일부까지 정리했지만, 실제로 자주 직접 여는 `collect-ops.md`, `policy-normalization-recommendation-read-model.md`, `policy-normalization-recommendation-migration-order.md`, `policy-source-onboarding-architecture.md` 는 여전히 각 문서 안에서 자기 문서군 인덱스로 다시 돌아가는 링크가 없었다.
- 해결: 위 네 문서 상단에 각각 `collect-docs-index.md` 또는 `policy-docs-index.md` 진입점 링크를 추가했다.
- 이유: 문서군 인덱스 규칙은 실행 문서뿐 아니라 구조/이행 설계 문서에도 같게 적용돼야 한다. 그래야 사용자가 설계 문서를 열었을 때도 다시 문서군 전체 맥락으로 쉽게 복귀할 수 있다.

## 402) policy 문서군은 일부 설계 문서만 정리하면 또 비대칭이 남는다
- 문제: `policy-docs-index.md` 진입점을 current-state, checklist, playbook, blocked/priority, 구조/이행 설계 문서까지 넓혔지만, `policy-post-local-closeout-track-split.md`, `policy-source-canonical-onboarding-priority.md`, `policy-source-code-entrypoints.md`, `policy-source-onboarding-template.md` 는 여전히 각 문서 안에서 인덱스로 다시 돌아가는 링크가 없었다.
- 해결: 위 네 문서 상단에도 `policy-docs-index.md` 진입점 링크를 추가했다.
- 이유: policy 문서군은 문서 수가 많아 한두 장만 예외로 남아도 다시 검색 의존이 생긴다. 남은 대표 문서까지 같은 패턴으로 맞춰야 문서군 독법이 완전히 일관된다.

## 403) 몇 장 안 남았더라도 공통 운영/검증 문서가 인덱스 규칙 밖에 남아 있으면 “거의 다 정리됐다”는 감각이 거짓이 된다
- 문제: 대부분의 대표 문서에는 `docs-index` 복귀 링크를 넣었지만, 실제로 자주 보는 공통 문서인 `github-workflow.md` 와 `local-feature-performance-check-2026-05-01.md` 는 아직 각각 `system-docs-index.md`, `local-validation-docs-index.md` 로 다시 돌아가는 링크가 없었다.
- 해결: 두 문서 상단에 각 문서군 진입점 링크를 추가했다.
- 이유: 문서군 정리는 개수보다 일관성이 중요하다. 몇 장만 예외로 남아도 사용자는 “여긴 왜 패턴이 다르지?”를 다시 생각해야 한다.

## 404) archive 쪽 entrypoint를 `archive/README` 에만 두면, 실제 보관 문서를 직접 열었을 때는 다시 history 인덱스로 복귀할 길이 없다
- 문제: `archive/README.md` 에는 이미 `history-docs-index.md` 진입점이 있었지만, 실제 대표 보관 문서인 `archive/project-plan-v11.md` 자체에는 아직 없었다. 이 상태면 사용자가 보관 플랜 문서를 바로 열었을 때는 다시 history 문서군 인덱스로 복귀하려면 상위 디렉터리나 검색에 의존해야 했다.
- 해결: `archive/project-plan-v11.md` 상단에 `../history-docs-index.md` 진입점 링크를 추가했다.
- 이유: history/archive 문서군도 현재 문서군과 같은 규칙을 따라야 한다. 실제 보관 문서 안에서도 한 줄로 인덱스로 복귀할 수 있어야 구조가 완전히 닫힌다.

## 405) local PII cutover smoke가 `.env` 를 안 읽고 placeholder password로 app를 띄우면, 기존 로컬 DB 계정과 어긋나 health check에서 막힌다
- 문제: `run-local-pii-sync-cutover-smoke.sh` 는 split-account smoke를 위해 `DB_USERNAME=app_core_rw` 등을 강제로 잡고 있었지만, password/URL은 로컬 `.env` 대신 placeholder 기본값을 써서 app를 띄웠다. 그 결과 실제 로컬 DB 계정이 `welfare1234!` 인 상태에서는 app가 `Access denied for user 'app_core_rw'` 로 부팅 실패하고, smoke가 health check 단계에서 멈췄다.
- 해결: script가 `.env` 의 password/URL 값은 먼저 읽되, smoke 기본 split-account username은 유지하도록 보정했다. 그 뒤 `AuthControllerWebMvcTest` 계열 auth/session regression, `run-local-pii-sync-cutover-smoke.sh`, `run-local-education-priority-replay.sh` 를 다시 돌려 closeout 검증 세트를 재통과시켰다.
- 이유: local closeout smoke는 “지금 이 로컬 `.env` 와 DB 볼륨”에서도 재현 가능해야 의미가 있다. username만 split-account로 고정하고 password는 `.env` 를 따르지 않으면, smoke 자체가 스크립트 기본값에만 맞는 허상 검증이 된다.

## 406) broad integration suite에서 지역 검색 테스트는 이전 `IT-SRCH-*` 찌꺼기가 남아 있으면 한 번씩 튈 수 있다
- 문제: `PolicySearchRegionQueryIntegrationTest` 는 `@AfterEach` cleanup만 두고 있어서, 이전 실행이 중간에 끊기거나 broad suite 전에 잔존 `IT-SRCH-*` row가 남아 있으면 시작 시점 query 결과가 오염될 수 있었다. 실제로 `./gradlew test integrationTest --no-daemon` 재실행 중 이 테스트가 한 번 실패했고, 같은 타깃 테스트 단독 재실행은 통과했다.
- 해결: 클래스 시작 전에도 같은 cleanup을 태우도록 `@BeforeEach` 를 추가했고, `welfare_services` 삭제 전에 연결된 `service_regions` 도 explicit delete 하도록 정리했다. 그 뒤 `./gradlew integrationTest --no-daemon --tests com.example.welfare.integration.PolicySearchRegionQueryIntegrationTest` 와 전체 `./gradlew test integrationTest --no-daemon` 을 다시 통과시켰다.
- 이유: 이 케이스는 검색 로직 회귀보다 테스트 격리 경계 문제에 가까웠다. local closeout 기준선은 “다시 돌렸을 때 흔들리지 않는지”가 중요하므로, broad suite에서 한 번 튄 지점은 테스트 자체가 self-heal 하도록 보강하는 편이 맞다.

## 407) prefix 기반 test data cleanup을 쓰는 integration test는 시작 전 cleanup이 없으면 같은 종류로 다시 튈 수 있다
- 문제: `RecommendationRegionQueryIntegrationTest`, `PolicyBookmarkIntegrationTest`, `RecommendationFlowIntegrationTest`, `CanonicalRecommendationReadModelIntegrationTest` 도 모두 `TEST_SOURCE_PREFIX` 또는 test email prefix로 test data를 구분하면서 `@AfterEach` cleanup 중심으로만 정리하고 있었다. broad suite가 중간 실패/중단 뒤 다시 돌 때는 같은 류의 잔존 row 오염 가능성이 남는다.
- 해결: 이 4개 클래스도 `@BeforeEach` 에서 cleanup을 한 번 더 태우고, region/summary slot처럼 dependent row가 있는 케이스는 explicit delete 순서를 맞췄다. 그 뒤 관련 타깃 integration들과 전체 `./gradlew test integrationTest --no-daemon` 을 다시 통과시켰다.
- 이유: 이번 라운드 목적은 특정 테스트 1개만 고치는 게 아니라, broad suite 기준의 재현성 경계를 비슷한 패턴 전반에서 한 번 더 닫는 것이다. prefix로 test data를 구분하는 클래스는 시작 전 self-heal cleanup을 두는 편이 전체 suite 안정성에 더 유리하다.

## 408) user/email prefix 기반 integration test도 broad suite 재실행 관점에서는 같은 self-heal cleanup 규칙이 필요하다
- 문제: `UserCoreDualWriteIntegrationTest`, `UserPiiSyncRetrySchedulerIntegrationTest`, `UserMetadataUserKeyBackfillIntegrationTest`, `UserPiiSyncReplayIntegrationTest`, `UserPiiBackfillIntegrationTest`, `AuthRedisIntegrationTest`, `AdminSecurityIntegrationTest` 도 모두 test email prefix로 생성 row를 구분하면서 `@AfterEach` cleanup 중심으로만 정리하고 있었다. broad suite가 이전 중단/실패 후 다시 시작될 때는 같은 패턴으로 stale user row와 queue/redis side effect가 남을 수 있다.
- 해결: 이 7개 클래스도 `@BeforeEach` 에서 cleanup을 한 번 더 태우도록 맞췄다. 그 뒤 관련 타깃 integration들과 전체 `./gradlew test integrationTest --no-daemon` 을 다시 통과시켰다.
- 이유: 지금 closeout 목표는 개별 로직 변경보다 suite 재실행 안정성이다. user-prefix 기반 테스트는 broad suite에서 비슷한 종류의 잔존 오염을 만들기 쉬우므로, 시작 전 self-heal cleanup을 통일하는 편이 가장 값싸고 확실한 방어선이다.

## 409) projection이 있어도 matcher/heuristic이 compat label 문자열을 다시 읽으면 source-neutral read-model 이득이 중간에서 새어 나간다
- 문제: `DefaultPriorityMatcher` 는 projection에 `priorityBuckets` 가 있어도 비어 있으면 다시 `unifiedCategoryCompat` label을 `CompatCategorySupport` 로 해석했고, `CanonicalRecommendationReadModelRepository` 의 education bridge도 `compat=기타` 여부를 label 문자열로 다시 판정하고 있었다. 이 상태면 read-model이 이미 계산한 의미가 있어도 downstream이 legacy compat label에 부분적으로 다시 결합된다.
- 해결: projection에 `compatCategoryCode`, `compatPriorityBucket` 을 같이 싣고, matcher와 education bridge heuristic은 projection이 제공한 code/bucket 의미를 우선 사용하게 바꿨다. 그 뒤 추천 타깃 테스트와 전체 `./gradlew test integrationTest --no-daemon` 을 다시 통과시켰다.
- 이유: compat label은 응답/프롬프트 호환용으로는 계속 필요하지만, 내부 판단까지 label 문자열 재해석에 의존하면 새 source/bridge 규칙이 늘 때마다 downstream 수정 지점이 다시 생긴다. projection에서 한 번 계산한 의미를 재사용하는 편이 더 source-neutral 하다.

## 410) 지역 검색 integration을 FULLTEXT boolean query에 직접 묶어 두면, 로컬 MySQL parser 차이 때문에 region predicate 검증까지 같이 흔들린다
- 문제: `PolicySearchRegionQueryIntegrationTest` 는 `searchByKeywordWithFiltersWithSido*` native FULLTEXT boolean query를 직접 호출하고 있었는데, 현재 로컬 MySQL에서는 test row를 넣어도 `AGAINST(... IN BOOLEAN MODE)` 가 0건으로 떨어져 broad suite에서 계속 실패했다. 원래 검증하려던 것은 keyword relevance가 아니라 `전국 + 매칭 지역만 남는지` 인데, FULLTEXT parser 차이가 region predicate 검증까지 깨고 있었다.
- 해결: 테스트를 `findListWithFilters(...)` 기반 region filter integration으로 바꾸고, test row는 `unifiedCategory=IT_SEARCH_REGION` 고정값으로 좁혀서 자기 데이터만 보게 했다. cleanup도 `service_regions` 를 service id 기준 batch delete 하도록 정리한 뒤, 단독 `PolicySearchRegionQueryIntegrationTest` 와 전체 `./gradlew test integrationTest --no-daemon` 을 다시 통과시켰다.
- 이유: 이 integration의 목적은 FULLTEXT 엔진 동작이 아니라 repository 지역 필터 semantics 검증이다. FULLTEXT boolean parser 차이를 여기서 같이 떠안으면 테스트가 로직 회귀보다 환경 세부에 더 민감해진다.

## 411) education priority replay를 `A_top10_target` 하나로만 hard gate 하면, 실수집 snapshot이 바뀌는 순간 기능이 살아 있어도 smoke가 거짓 실패로 바뀔 수 있다
- 문제: 실수집(`POST /api/admin/collect/youth`) 후 `run-local-education-priority-replay.sh` 를 다시 돌리니 sample A의 canonical bonus 대상 row가 `34위 -> 27위`, `0.06 -> 0.12` 로 실제로 올라왔는데도, 기존 hard gate가 `A_top10_target` 개선만 요구해서 `0->0` 에서 실패했다. 현재 snapshot에서는 compat=`기타` + youth_major=`교육` target row가 응답 전체에 1건만 남아 있어, top10 진입 자체를 고정 기준으로 두는 게 과거 snapshot에 과적합된 상태였다.
- 해결: replay summary에 `A/B_best_target_rank`, `A/B_best_target_score` 를 추가하고, rule-only hard gate도 `top10 진입` 또는 `best target rank 개선` 중 하나가 일어나면 pass 하도록 바꿨다. 그 뒤 replay를 다시 실행해 artifact(`/tmp/tmp.jTCGzE2Yps`) 기준 `A_best_target_rank=34->27`, `A_best_target_score=0.06->0.12`, `B_best_target_rank=34->34` 로 통과를 확인했다.
- 이유: 이 smoke의 핵심은 canonical education bonus가 target row 가시성을 실제로 올리는지 보는 것이다. live snapshot이 바뀐 뒤에도 그 효과가 `rank` 와 `score` 에 드러나면 기능은 살아 있는 것이고, top10 진입만 유일한 기준으로 두면 오히려 정상 동작을 거짓 실패로 처리하게 된다.

## 412) runtime API smoke를 수동 curl 묶음으로만 두면, logout/revoke 같은 미묘한 계약을 매번 사람이 다시 해석하게 된다
- 문제: 실제 런타임에서 `signup -> login -> refresh -> recommendations -> bookmark -> logout` 을 다시 확인할 때마다 쿠키 jar, refresh 후 새 access token, recommendation bookmark path의 `id`, `logout` 요청에 실린 `presented token` 과 더 오래된 login token의 차이를 사람이 다시 조립해야 했다. 특히 logout 뒤 `presented token` 은 `401 / A006` 으로 막히지만, 더 오래된 login token까지 같은 계약으로 막히는 것은 아니어서, 수동 smoke는 쉽게 오해를 만든다.
- 해결: `deploy/smoke/run-local-runtime-api-smoke.sh` 를 추가해 `signup -> login -> refresh -> recommendations refresh -> bookmark -> bookmarks -> logout -> refresh invalidation(401/A001) -> presented access revoke(401/A006)` 를 한 번에 검증하도록 고정했다. 동시에 `older login token after logout` 은 현재 계약상 `200` 도 허용되는 관측값으로 따로 기록하게 했다.
- 이유: 이 경계는 “logout이 무엇을 보장하는가”를 매번 다시 해석하게 두면 안 된다. 스크립트로 고정해야 closeout 때 같은 계약을 반복해서 같은 방식으로 확인할 수 있다.

## 413) admin forced logout도 수동 단계로만 남겨 두면, old access/old refresh/relogin 경계를 다시 칠 때마다 admin 준비와 userKey 조회가 흔들린다
- 문제: forced logout은 일반 runtime smoke보다 한 단계 더 까다롭다. admin token이 필요하고, 대상 `userKey` 도 알아야 하며, 그 뒤 `old access -> 401/A006`, `old refresh -> 401/A003`, `relogin -> 200` 을 순서대로 다시 확인해야 한다. 이걸 수동 curl로만 남겨 두면 로컬에서 매번 admin 계정 준비, DB 조회, 로그 grep을 다시 조합해야 해서 false negative가 섞이기 쉽다.
- 해결: `deploy/smoke/run-local-admin-forced-logout-smoke.sh` 를 추가해 `admin login -> forced logout -> old access deny -> old refresh deny -> relogin recovery -> server log evidence` 를 한 번에 검증하도록 고정했다. 로컬 전용으로 target user의 `userKey` 는 DB read 한 번으로 조회하고, admin 자격 증명은 env(`ADMIN_EMAIL`, `ADMIN_PASSWORD`)로 받되 기본값은 현재 로컬 baseline(`admin@example.com`)을 따른다.
- 이유: forced logout은 auth/session revoke 현재 phase의 핵심 보안 경계 중 하나다. 이 경계도 반복 가능한 smoke로 고정해야 다음 closeout이나 회귀 점검에서 “구현은 맞는데 실행 절차를 다시 조립하다 틀리는” 문제를 줄일 수 있다.

## 414) withdraw 경계도 수동 확인에 남겨 두면 A006/U003와 withdrawn mask를 같은 런에서 다시 증명하기가 번거롭다
- 문제: withdraw는 현재 계약상 `old access -> 401/A006`, `stale refresh -> 410/U003`, `user inactive + email=withdrawn_<id>` 까지 같이 봐야 닫힌다. 그런데 이걸 수동 curl + DB 조회로만 남겨 두면 token 교체 시점, DELETE body의 password, userKey 추적, withdrawn email 확인을 매번 사람이 다시 조합해야 한다.
- 해결: `deploy/smoke/run-local-withdraw-smoke.sh` 를 추가해 `signup -> login -> refresh -> withdraw -> old access deny(401/A006) -> stale refresh deny(410/U003) -> withdrawn email mask` 를 한 번에 검증하도록 고정했다. 로컬 전용으로 userKey와 withdrawn state는 DB read 한 번으로 확인하게 했다.
- 이유: withdraw는 revoke/terminal state가 함께 묶인 경계라서, 성공 기준을 사람 기억에 맡기면 closeout 때 반복 비용이 크다. smoke로 고정해야 같은 계약을 같은 순서로 계속 확인할 수 있다.

## 415) logout / withdraw / forced logout smoke가 각각 있어도, closeout 때 매번 세 개를 순서대로 다시 치는 행위 자체가 누락 포인트가 된다
- 문제: 개별 smoke를 다 만든 뒤에도 closeout 시점에는 결국 `run-local-runtime-api-smoke.sh`, `run-local-withdraw-smoke.sh`, `run-local-admin-forced-logout-smoke.sh` 를 차례로 다시 실행해야 했다. 이 상태는 사람 손으로 순서를 다시 기억해야 하므로, “세 개 모두 돌렸다”는 증적을 남기기에도 번거롭다.
- 해결: `deploy/smoke/run-local-auth-session-smoke.sh` wrapper를 추가해 기본 순서를 `runtime logout -> withdraw -> admin forced logout` 으로 고정했다. 필요하면 `RUN_RUNTIME_API_SMOKE=false` 같은 env로 일부만 끌 수 있지만, closeout 기본값은 세 개를 다 도는 쪽으로 뒀다.
- 이유: revoke 경계는 개별 구현보다 “지금 로컬에서 세트로 다시 살아나는가”가 더 중요하다. wrapper 하나로 묶어야 closeout 기준선이 사람이 아니라 스크립트에 남는다.

## 416) 온통청년 실수집은 같은 API key/page 조합이라도 빠른 연속 호출 중 특정 페이지에서 transient `400/500` 을 뱉을 수 있어서, pacing 없이 돌리면 전체 수집이 가끔 깨진다
- 문제: 로컬에서 `POST /api/admin/collect/youth` 실수집을 다시 태우자 `YouthApiClient` 가 `page=18 status=400` 으로 한 번 실패했다. 그런데 같은 page를 수동 단건 조회하면 `200` 이 나왔고, 재기동 후에는 `page=2 status=500` 도 한 번 관측됐다. 즉 잘못된 page 요청이라기보다 외부 API가 빠른 연속 호출 중 간헐적으로 불안정하게 응답하는 패턴이었다.
- 해결: `YouthApiClient` 에도 `collect.list.request-interval-ms` pacing을 추가하고, 이 엔드포인트의 transient `400` 을 retryable status에 포함시켰다. 그 뒤 Docker app을 재빌드하고 실수집을 다시 실행해 latest `api_sync_logs` 기준 `YOUTH success requested=2363 saved=2363 failed=0` 로 복구를 확인했다.
- 이유: 현재 collect closeout 목표는 “실수집이 가끔 깨지지 않고 끝까지 돈다”는 것이다. 온통청년만 pacing이 빠져 있으면 복지로 계열과 달리 외부 변동성에 그대로 노출된다.

## 417) summary slot local backfill SQL이 `service_taxonomies` 를 다시 조인하면 동일 service row에서도 unique 충돌로 draft apply가 멈출 수 있다
- 문제: `deploy/mysql/apply-local-policy-sidecar-draft.sh` 재실행 중 `V2026_05_02_02__backfill_service_taxonomy_summary_slots.sql` 에서 `Duplicate entry '2419-YOUTH_MAJOR-WELFARE_CULTURE-OFFICIAL'` 가 발생했다. backfill SQL이 `slots` 서브쿼리를 만든 뒤 다시 `service_taxonomies st` 에 조인하며 authority/confidence를 가져오고 있어, 같은 service에 대해 managed slot row가 중복 생성될 수 있었다.
- 해결: authority/confidence를 각 `UNION ALL` branch 안으로 넣고 outer join을 제거한 뒤, backfill insert를 `ON DUPLICATE KEY UPDATE` 로 바꿨다. 그 뒤 `deploy/mysql/apply-local-policy-sidecar-draft.sh` 를 다시 실행해 `service_taxonomy_summary_slots=5672`, `slot_density_YOUTH_MAJOR=2312`, `slot_density_PROVISION_METHOD=1170` 까지 정상 출력되는 것을 확인했다.
- 이유: local draft apply는 replay와 current-state 검증의 베이스라인이라 반복 실행에 안전해야 한다. 여기서 unique 충돌이 나면 collect 회귀와 무관한 SQL 중복 때문에 validation 전체가 멈춘다.

## 418) collect 중단 뒤 남은 `api_sync_logs.RUNNING` row를 그대로 두면, 다음 실수집이 성공해도 수집 이력이 거짓으로 열려 있는 상태가 남는다
- 문제: 로컬 collect/replay 검증 중 DB restart와 중단 실행이 섞이면서 `api_sync_logs` 에 `YOUTH` 기준 `id=1,2,8,13,14` 같은 오래된 `RUNNING` row가 남았다. 실제로 새 `POST /api/admin/collect/youth` 는 정상 완료되는데도, 수집 이력 테이블만 보면 아직 진행 중인 작업이 여러 건 열려 있는 것처럼 보여 진단을 오염시켰다.
- 해결: `ApiSyncLogService.runWithLog(...)` 시작 직전에 같은 `job_name` 의 stale `RUNNING` row를 bulk update로 `FAILED / InterruptedRun` 처리하도록 바꿨다. Docker app 재빌드 후 `POST /api/admin/collect/youth` 를 다시 실행하자 app log에 `stale RUNNING collect log auto-closed job=YOUTH count=5` 가 찍혔고, DB에서도 새 `id=15` 성공 row와 함께 과거 `RUNNING` row들이 모두 `FAILED` 로 닫힌 것을 확인했다.
- 이유: collect 배치의 exclusivity는 in-memory guard가 담당하더라도, operator가 보는 사실원장은 `api_sync_logs` 이다. 중단된 run의 잔여 상태를 시작 경계에서 자동 정리해야 실수집 회귀를 다시 돌릴 때 로그 해석이 틀어지지 않는다.

## 419) recommendation flow integration이 “첫 번째 추천이 내가 만든 정책이어야 한다”까지 강제하면, 실데이터가 많은 로컬 DB에서는 정상 흐름도 거짓 실패가 된다
- 문제: broad suite 재실행 중 `RecommendationFlowIntegrationTest` 가 `$.data[0].serviceId == housingPolicy.id` 를 기대하다 실패했다. 로컬 DB에 실수집 정책이 많이 들어온 상태에서는 추천 refresh 응답의 0번 인덱스가 항상 테스트가 만든 주거 정책일 필요는 없는데, 테스트가 정렬 결과까지 과하게 고정하고 있었다.
- 해결: 테스트 기대치를 “응답 배열 안에 내가 만든 `housingPolicy` 가 존재하고, 그 row의 `title/bookmarked` 상태가 맞는가”로 바꿨다. 그 뒤 단독 `RecommendationFlowIntegrationTest` 와 전체 `./gradlew test integrationTest --no-daemon` 을 다시 통과시켰다.
- 이유: 이 integration의 목적은 refresh/get/bookmark 저장 흐름 검증이지 전역 실데이터까지 포함한 절대 ranking 보장이 아니다. 자기 row 포함과 상태 전파만 검증해야 환경이 커져도 재현성이 유지된다.

## 420) 복지로 list collect가 page 1부터 `429` 로 막히는 날에는, `0건 success` 로 남기는 것보다 실패로 표면화해야 operator가 실제 상태를 읽을 수 있다
- 문제: `POST /api/admin/collect/bokjiro-local` 실검증 중 page 1이 연속 `429` 로 막히는 날이 재현됐다. 완충 이전에는 `requested=0 saved=0 failed=0` 인데도 `SUCCESS` 로 로그가 남았고, 완충을 넣어도 임계치 도달 후 그대로 `0건 success` 로 끝나면 collect 사실원장이 오염됐다.
- 해결: `collect.list.max-consecutive-rate-limit-hits` 를 `3` 으로 올리고 `collect.list.rate-limit-cooldown-ms=10000` 을 추가해 즉시 종료를 줄였다. 그 다음에도 임계치까지 회복하지 못한 경우에는 `BokjiroLocalClient` / `BokjiroCentralClient` 가 `CustomException(COLLECT_API_FAILED)` 를 던지게 바꿨다. `ApiSyncLogService` 의 stale self-heal 때문에 들어간 트랜잭션도 repository query 쪽으로 옮겨, 실패 시 `api_sync_logs` row가 롤백되지 않게 정리했다. 실제 재검증에서 `POST /api/admin/collect/bokjiro-local` 은 `500 / COL001` 을 반환했고, DB에도 `id=21 BOKJIRO_LOCAL failed requested=0 saved=0 failed=1` 이 남았다.
- 이유: 페이지네이션을 끝까지 못 돈 collect는 부분 성공이 아니라 실패로 보는 쪽이 더 안전하다. 특히 현재 구현은 absent row를 delete하지 않아서, `0건 success` 나 `800건 success` 는 실제 최신성보다 “겉보기 성공” 을 더 많이 남긴다. rate-limit 상황은 재시도/다음 배치로 넘기되, 현재 run은 실패로 남겨야 운영 해석이 맞다.

## 421) 복지로 detail gap fill도 연속 `429` 로 한 건도 저장하지 못한 라운드를 `success + stoppedAfterNoSaves` 로만 남기면 list collect 때와 같은 false green이 남는다
- 문제: collect/replay/broad suite 재검증 루프에서 `POST /api/admin/collect/bokjiro-details-gap-fill?rounds=1&maxCallsPerRound=95` 를 다시 치자, 중앙/지자체 detail API가 모두 `429` 로 막혔는데도 응답은 `200` 이고 body는 `saved=0 skipped=198 failed=0 stoppedAfterNoSaves=true` 로 남았다. 이 상태는 “backlog가 다 메워져서 더 저장할 게 없는 것” 과 “rate limit 때문에 아무것도 못 한 것” 을 구분하지 못한다.
- 해결: `BokjiroDetailCollectService` 가 source별 collect stats에 `rateLimitedAbort` 를 싣고, gap fill 라운드에서 `rate-limited abort && saved=0` 인 경우에는 `CustomException(COLLECT_API_FAILED)` 를 던지도록 바꿨다. `BokjiroDetailCollectServiceTest`, `AdminSecurityWebMvcTest`, `./gradlew test integrationTest --no-daemon` 을 통과시킨 뒤, Docker app을 재기동하고 `SECURITY_ADMIN_EMAILS=admin@example.com` override 상태에서 같은 endpoint가 실제 `500 / COL001` 을 반환하는 것까지 확인했다.
- 이유: detail gap fill은 일부 저장이 있었던 라운드까지 롤백하면 안 되므로, “한 건도 저장하지 못한 rate-limit abort” 만 실패로 올리는 최소 경계가 맞다. 그래야 operator가 `saved=0` 을 backlog exhaustion으로 오해하지 않는다.

## 422) fresh Docker app 검증에서 `.env` 의 `SECURITY_ADMIN_EMAILS` 가 비어 있으면 `admin@example.com` 로그인은 되더라도 `ROLE_ADMIN` 이 빠져 admin collect 검증이 막힌다
- 문제: app 이미지를 재빌드해 띄운 뒤 `admin@example.com` 으로 다시 로그인하자 access token roles가 `ROLE_USER` 뿐이었고, `/api/admin/collect/bokjiro-details-gap-fill` 이 `403 / C003` 으로 떨어졌다. 컨테이너 env를 확인해 보니 `SECURITY_ADMIN_EMAILS=` 로 비어 있었다.
- 해결: 로컬 runtime contract를 다시 확인할 때는 `SECURITY_ADMIN_EMAILS=admin@example.com docker compose up -d --force-recreate app` 처럼 shell override로 allowlist를 주입한 뒤 검증했다. 이 상태에서 admin login token에 `ROLE_ADMIN` 이 다시 붙었고, collect admin endpoint도 정상적으로 contract 검증이 가능해졌다.
- 이유: auth/session smoke는 이전 컨테이너 상태를 타고 통과할 수 있지만, fresh rebuild 뒤 admin route를 다시 검증할 때는 allowlist가 진짜 컨테이너 env로 들어갔는지 먼저 확인해야 한다. 그렇지 않으면 code regression이 아니라 env 누락을 기능 버그로 오인하게 된다.

## 423) `BOKJIRO_LOCAL` 이 page 1 연속 `429` 로 실패한 직후 즉시 다시 호출되면, 이미 같은 원인으로 막힌 걸 알면서도 매번 30초씩 재시도하는 낭비가 남아 있었다
- 문제: `POST /api/admin/collect/bokjiro-local` 은 repeated `429` 를 이제 `500 / COL001` 로 surface 하긴 하지만, operator가 버튼을 다시 누르면 같은 JVM 안에서도 page 1을 다시 세 번 재시도하고 cooldown 세 번을 다 태운 뒤 또 실패했다. 실데이터를 바꾸는 것도 아닌데 짧은 시간에 똑같은 외부 호출을 반복해 로컬 검증과 운영 대응이 모두 느려졌다.
- 해결: `BokjiroLocalClient` 에 local in-memory open circuit을 추가해, 연속 `429` 임계치 도달 시 `collect.list.local-rate-limit-open-circuit-ms` 동안 회로를 열어 두고 이후 `fetchAll()` 진입 자체를 즉시 `COL001` 로 중단하게 바꿨다. `BokjiroLocalClientTest` 로 “회로가 열려 있으면 WebClient를 전혀 치지 않는다”를 고정했고, Docker app 재빌드 후 실제로 첫 호출은 `32s / 500 COL001`, 직후 두 번째 호출은 `0s / 500 COL001` 이며 로그에 `최근 연속 429로 수집 회로가 열려 있어 즉시 중단 remainingMs=...` 가 찍히는 것까지 확인했다.
- 이유: 이 회로는 성공을 숨기는 장치가 아니라, 이미 실패가 확정된 짧은 재시도를 외부 API와 앱 둘 다 낭비하지 않게 막는 안전장치다. collect 사실원장은 계속 `FAILED` 로 남기고, operator는 쿨다운이 지난 뒤에만 의미 있는 재시도를 하게 만드는 쪽이 더 안전하다.

## 424) 검색 로그를 search service 안에 직접 섞으면 조회 책임과 HTTP 식별 책임이 같이 얽혀 2차 기능이 핵심 검색 경계를 오염시킨다
- 문제: `search_logs` 는 2차 기능이지만 실제로 붙이려면 `keyword/resultCount` 뿐 아니라 익명 검색 식별용 fingerprint도 필요하다. 이걸 `PolicySearchService` 안에 직접 넣으면 검색 read model, userKey 조회, HTTP fingerprint 생성, 로그 저장이 한 서비스에 섞여 read-only 경계가 무너진다.
- 해결: `ClientFingerprintService` 를 별도로 두고, `PolicySearchLogService` 가 `search_logs` 저장만 담당하도록 분리했다. `PolicyController` 는 검색 응답을 만든 뒤 controller 경계에서 `PolicySearchLogCommand` 를 조립해 logging service에 넘기고, 기존 `PolicySearchService` 는 그대로 read-only로 유지했다. 검색 로그 저장 실패는 warn으로만 남기고 검색 응답은 깨지지 않게 해 secondary logging failure가 핵심 조회 흐름을 막지 않도록 정리했다.
- 이유: 검색 자체와 검색 관측은 생명주기가 다르다. fingerprint 생성은 web concern이고, search log 저장은 write-side concern이며, 실제 검색은 read concern이다. 이 셋을 나누어야 2차 기능을 붙여도 핵심 검색 경계가 덜 흔들린다.

## 425) 카카오 알림톡은 코드 경계만 있다고 바로 next feature 로 취급하면, 실제 병목인 운영 자격 심사 문제를 구현 문제로 오판하게 된다
- 문제: 현재 알림 경계는 `NotificationGateway` 인터페이스로 분리돼 있어 겉보기에는 알림톡 provider만 붙이면 될 것처럼 보인다. 하지만 실제 알림톡 운영은 비즈니스 채널 전환, 발신 프로필 등록, 템플릿 심사, 사업자 증빙이 선행되어야 하고, 학생 개인 신분의 3개월 졸업 프로젝트 운영 범위에서는 이 전제가 더 큰 병목이다.
- 해결: 알림톡을 단순 2차 구현 항목이 아니라 `운영 자격 blocked` 항목으로 재분류했다. practical next 2차 기능 우선순위는 `검색 로그 -> 대시보드 -> 알림톡` 으로 두고, 알림톡은 사업자/심사 조건이 실제로 충족될 때만 reopen 하는 기준으로 문서를 정리했다.
- 이유: 지금 부족한 것은 코드 추상화가 아니라 외부 자격과 심사 가능성이다. 이 상태에서 알림톡 구현을 active track 으로 두면, repo 안에서 해결할 수 없는 문제를 계속 코드 작업처럼 파게 된다.

## 426) 추천/수집 대시보드를 각 도메인 서비스에 따로 얹으면, 2차 운영 지표 기능이 기존 쓰기 경계와 도메인 책임을 다시 섞어 버린다
- 문제: 대시보드는 collect/recommendation/notification/search/user_pii_sync 지표를 한 응답에서 보여줘야 하지만, 이걸 기존 도메인 서비스에 직접 추가하면 각 서비스가 자기 쓰기 책임 외에 admin 집계 책임까지 떠안게 된다. 특히 cross-domain SQL이 여러 repository로 흩어지면 대시보드 변경이 곧 핵심 서비스 변경으로 번진다.
- 해결: `GET /api/admin/dashboard/summary` 를 새 admin read-model 경계로 두고, `AdminDashboardService` 는 오케스트레이션만 담당하게 했다. 실제 SQL 집계는 `AdminDashboardReadRepository` 로 모아 collect/recommendation/notification/search 지표를 읽고, `UserPiiSyncStatusService` 는 기존 status read-model을 그대로 재사용하게 분리했다.
- 이유: 대시보드는 본질적으로 운영 관측용 read 모델이다. 핵심 도메인 서비스에 섞지 않고 admin 전용 query/service 계층으로 빼야 SOLID 경계가 덜 흔들리고, 운영 지표 변경도 기능 서비스 회귀 없이 처리할 수 있다.

## 427) 현재 규모에서 군집 캐시를 바로 활성화하면, 추천 가속보다 군집 설계와 stale invalidation 관리 비용이 더 커질 수 있다
- 문제: 군집 캐시는 겉보기에 추천 속도를 빠르게 할 것 같지만, 실제로는 군집 키 설계, 첫 사용자 miss, 낮은 hit-rate, stale invalidation, AI reason 획일화 같은 비용을 같이 데려온다. 현재처럼 사용자 규모가 크지 않은 졸업 프로젝트에선 이 비용이 실제 이득보다 클 가능성이 높다.
- 해결: 문서 기준을 `청년정책 통합포털 + 개인화 추천` 으로 다시 명시하고, 추천 재사용 전략도 군집 캐시보다 `userKey` 기준 개인 캐시를 우선 검토하는 방향으로 정리했다. 나이대×소득분위 군집 캐시는 실제 사용자 수와 요청 패턴이 충분히 커졌을 때만 reopen 하는 확장 포인트로 남긴다.
- 이유: 지금 제품의 기본 단위는 군집보다 사용자다. 추천 가속이 필요해도 먼저 개인 캐시가 더 단순하고 개인화 손실이 적으며, 군집 캐시는 규모 문제를 실제로 겪기 시작한 뒤에 다시 판단하는 편이 더 합리적이다.

## 428) 현재 구조에서 추천 payload 전체를 캐시하려 하면, persistence/log/북마크 경계까지 같이 복제되어 처음 얻는 속도보다 경계 혼선이 더 커질 수 있다
- 문제: 현재 추천 응답은 이미 `user_recommendations` 테이블에 저장된 row, CTR용 `recommendation_logs`, 북마크 상태 이전, canonical projection 조합을 전제로 움직인다. 이 시점에 Redis에 추천 payload 자체를 별도로 넣으면 추천 결과 저장소가 DB와 Redis 두 군데가 되고, stale invalidation 뿐 아니라 log id 생성 시점과 bookmark 최신성까지 같이 복제 관리해야 한다.
- 해결: 1차 개인 캐시는 추천 row 전체를 저장하지 않고 `RecommendationRefreshCacheService` 에서 `userKey` 기준 `non-personal refresh` 완료 마커만 짧은 TTL로 저장하는 형태로 좁혔다. cache hit 시에도 실제 응답은 계속 `user_recommendations` 의 최신 row 를 읽고, `personal=true` refresh 와 `updateProfile` / `updatePriorities` / `withdraw` 는 즉시 invalidate 하도록 정리했다.
- 이유: 지금 필요한 것은 “같은 사용자가 짧은 시간 안에 같은 refresh를 다시 눌렀을 때 재계산을 한 번 줄이는 것”이지, 추천 저장 체계를 이중화하는 것이 아니다. 마커 캐시는 효과 대비 책임이 훨씬 작고, 현재 persistence/log/bookmark 경계를 그대로 유지할 수 있다.

## 429) 개인 refresh 캐시 key가 `userKey` 만 보면, replay처럼 앱 재기동으로 추천 규칙 플래그가 바뀐 뒤에도 이전 refresh 결과를 재사용해 비교 실험을 오염시킬 수 있다
- 문제: 개인 캐시 도입 뒤 `run-local-education-priority-replay.sh` 가 갑자기 `A_top10_target=5->5`, `A_best_target_rank=3->3` 으로 실패했다. 추천 로직 자체 회귀처럼 보였지만, 실제 원인은 OFF phase가 만든 refresh 마커를 ON phase도 그대로 cache hit 하며 재사용한 것이었다. replay는 `RECOMMEND_PRIORITY_EDUCATION_CANONICAL_BONUS_ENABLED` 값을 바꾸기 위해 앱을 재기동하지만 Redis 마커는 남아 있으므로, `userKey` 단독 key 는 규칙 버전 차이를 구분하지 못했다.
- 해결: `RecommendationRefreshCacheService` key에 `educationCanonicalBonusEnabled` 규칙 버전을 포함시켰다. 같은 사용자라도 추천 규칙 플래그 조합이 달라지면 서로 다른 refresh 마커를 보게 만들었고, 그 뒤 replay smoke는 다시 `A_top10_target=5->8`, `A_best_target_rank=3->1` 로 복구됐다.
- 이유: 개인 캐시는 “같은 입력/같은 규칙 버전의 짧은 재계산”만 줄여야 한다. 캐시 key가 사용자만 구분하고 규칙 버전을 구분하지 않으면, 실험/feature-flag 전환/앱 재기동 비교가 전부 stale hit 로 오염된다.

## 430) education replay smoke는 sample A가 OFF 단계부터 이미 상위권에 포화된 snapshot이면 “추가 개선”만 hard gate로 요구할수록 거짓 실패가 늘어난다
- 문제: collect 실검증을 다시 돌린 최신 snapshot에서 `YOUTH`, `BOKJIRO_CENTRAL`, `BOKJIRO_LOCAL`, `bokjiro-details-gap-fill` 은 모두 정상 완료됐지만, replay는 sample A가 OFF 단계부터 `A_best_target_rank=2`, `A_top10_target=5` 인 상태라 ON에서도 같은 수치가 나오자 `sample A did not improve target row visibility` 로 실패했다. 이건 canonical bonus가 죽은 게 아니라, target row visibility가 이미 충분히 높은 포화 상태인데 gate가 “더 좋아져야만 통과”라고 가정한 경우였다.
- 해결: `run-local-education-priority-replay.sh` 에 `strong_target_visibility()` 조건을 추가해, sample A가 OFF 단계에서 이미 `best_target_rank<=2` 또는 `top10_target_count>=5` 이면 OFF/ON이 동일해도 pass 하도록 보정했다.
- 이유: replay smoke의 목적은 canonical bonus가 죽었는지 빠르게 보는 것이지, 이미 충분히 잘 보이는 snapshot에서도 매번 추가 상승을 강제하는 것이 아니다. 포화 구간을 gate에 반영해야 collect snapshot 변화에 덜 과적합된다.

## 431) 군집 캐시를 문서상으론 보류해두고 코드에는 2D 군집화가 남아 있으면, 추천 운영 기준과 실제 동작이 다시 어긋난다
- 문제: 문서 기준은 이미 `1차는 youth_all 단일 군집, 군집 캐시보다 개인 캐시 우선` 이었지만, 실제 `ClusterService` 는 나이대×소득분위 2D 군집을 계속 만들고 있었다. 그러면 non-personal 추천은 여전히 군집 캐시 경로를 탈 수 있어, “현재는 개인 캐시 중심”이라는 운영 설명과 실제 동작이 어긋난다.
- 해결: `ClusterService` 를 다시 `youth_all` 고정으로 되돌리고, `ClusterServiceTest` 로 어떤 사용자 입력에도 단일 군집만 반환하는 계약을 고정했다. 2D 군집화는 코드 active path가 아니라 2차 확장 포인트로만 남긴다.
- 이유: 지금 규모에서는 군집 hit-rate보다 `userKey` 기준 개인 캐시가 더 단순하고 효과적이다. 군집화는 사용자 수와 요청 패턴이 커졌을 때 다시 여는 편이 맞고, 그 전까지는 문서와 코드가 같은 1차 기준을 따라야 한다.

## 432) CTR 재분석이 막힌 원인이 click instrumentation 자체인지, 단순 표본 부족인지 먼저 분리해야 한다
- 문제: 최근 local DB를 다시 보니 `recommendation_logs` 는 누적되는데 `clicked_logs` 가 거의 없어, CTR 가중치/프롬프트 재조정이 정말 “클릭이 안 찍히는 버그” 때문에 막힌 건지, 아니면 그냥 실사용 클릭 표본이 아직 적은 건지 구분이 필요했다. 이 상태에서 바로 가중치 조정을 논하면 잘못된 병목을 기준으로 설계를 바꿀 수 있었다.
- 해결: 추천 상세 클릭 경계를 `signup -> login -> recommendations refresh -> first recommendation detail(serviceId + logId)` 로 다시 태워 `recommendation_logs.is_clicked=1` 과 `clicked_at` 갱신을 DB에서 직접 확인하는 local smoke를 추가했다. 수동 probe에서 한 번 `recommendation.id` 를 정책 path id로 잘못 넣어 거짓 `500` 이 났지만, 실제 프론트 계약대로 `serviceId + logId` 를 쓰자 `200` 과 함께 클릭 마킹이 정상 반영됐다.
- 이유: 현재 CTR tuning의 병목은 instrumentation이 아니라 표본 부족이다. 클릭 trace 경계가 살아 있다는 것을 smoke로 고정해 두면, 이후에는 로그 수집/사용량 문제와 코드 버그를 분리해서 판단할 수 있다.

## 433) 대시보드에 CTR 비율만 있으면 “왜 지금 추천 품질 튜닝을 안 하는지”가 한 번에 안 보인다
- 문제: `clickThroughRateLast7d` 와 `fallbackRateLast7d` 만으로는 CTR 튜닝 보류 원인이 click 표본 부족인지, 특정 weight stage 편중인지, 아니면 아예 active weight 단계가 바뀌었는지 바로 읽기 어려웠다. 결국 운영자는 다시 DB에서 `recommendation_logs` 와 `score_weights` 를 따로 조회해야 했다.
- 해결: `GET /api/admin/dashboard/summary` recommendation 섹션에 `activeWeightKey`, `activeRuleWeight`, `activeAiWeight`, `totalLogs`, `latestClickedAt`, 최근 7일 `weightBucketsLast7d` 를 추가했다. 집계 SQL은 기존 admin read-model 경계에 유지하고, active weight는 `ScoreWeightService` 를 재사용해 현재 추천 단계와 최근 분포를 한 응답에서 같이 보게 정리했다.
- 이유: CTR 조정의 next step은 단순 비율보다 “현재 얼마나 많은 로그가 쌓였고, 어느 weight stage에 몰려 있으며, 마지막 클릭이 언제였는가”를 같이 봐야 판단이 된다. 이 정도 컨텍스트는 dashboard summary에 함께 있어야 운영자가 DB ad-hoc 쿼리 없이도 현재 병목을 읽을 수 있다.

## 434) collect/search 대시보드가 요약 count만 보여주면 “무엇이 실패했고 검색이 얼마나 헛돌고 있는지”를 다시 DB에서 찾아야 한다
- 문제: 기존 대시보드는 collect 쪽에서 `failedJobsLast24h` count 만 보여 줬고, search 쪽도 총 검색 수와 평균 결과 수만 보여 줬다. 이 상태로는 최근 어떤 collect run이 왜 실패했는지, 검색 결과 0건이 최근 얼마나 자주 나왔는지를 운영자가 다시 `api_sync_logs` / `search_logs` 로 내려가서 따로 봐야 했다.
- 해결: collect 섹션에 `latestFailuresLast7d` 를 추가해 최근 실패 run의 `jobName/status/errorCode/errorMessage/requested/saved/failed` 를 같이 보여 주고, search 섹션에는 `zeroResultSearchesLast7d` 를 추가했다. 집계/리스트 SQL은 계속 `AdminDashboardReadRepository` 안에만 두어 admin read-model 경계를 유지했다.
- 이유: 운영 summary는 count만 보는 화면이 아니라 다음 액션을 바로 정할 수 있어야 한다. 최근 실패 run 세부와 0건 검색 빈도는 운영자가 추가 DB 쿼리 없이도 collect 안정화와 검색 품질 문제를 즉시 분리하게 해 주는 최소한의 맥락이다.

## 435) 운영 지표가 “현재 수치”만 있으면 단기 이상치와 30일 누적 흐름을 한 번에 비교하기 어렵다
- 문제: dashboard summary가 현재 시점 count와 최근 7일 중심 수치만 보여 주면, collect/recommendation/search가 오늘만 튄 건지, 최근 일주일 내내 같은 패턴인지, 30일 누적 기준으로도 비슷한지까지는 다시 쿼리를 나눠 봐야 했다.
- 해결: `trend` 섹션을 추가해 collect/recommendation/search 각각 1일/7일/30일 window point를 함께 반환하게 했다. 기존 summary contract는 유지하고, 추세 비교는 별도 list로만 확장해 API 호환성과 운영 가독성을 같이 유지했다.
- 이유: 운영 판단은 절대값보다 기울기를 같이 봐야 정확해진다. 1/7/30일 추세를 한 응답에 같이 실어 두면, 당일 이상치인지 구조적 추세인지 훨씬 빨리 구분할 수 있다.

## 436) 로컬 검증 스크립트가 늘어나면 “이번엔 어떤 순서로 돌려야 안전한가”가 다시 암묵지로 돌아간다
- 문제: auth/session, recommendation click, admin dashboard, replay smoke가 각각 분리돼 있으면 한 번에 로컬 기준선을 다시 확인할 때 순서를 사람이 기억해야 한다. 특히 replay는 DB/app 재기동을 건드릴 수 있어 앞쪽 smoke와 병렬 또는 잘못된 순서로 돌리면 거짓 실패를 만든다.
- 해결: `run-local-validation-suite.sh` 를 추가해 `auth/session -> recommendation click -> admin dashboard -> replay` 순서를 상위 wrapper로 고정했다. 문서도 이 wrapper를 로컬 검증 기본 진입점으로 연결했다.
- 이유: 반복 검증 루프는 “어떤 스크립트가 있나”보다 “실패 없이 어떤 순서로 다시 태울 수 있나”가 더 중요하다. 상위 wrapper가 있어야 검증 순서가 개인 기억이 아니라 repo contract가 된다.

## 437) 전체 로컬 검증 wrapper가 replay까지 기본 포함하면 빠른 회귀 확인 때 다시 무거워진다
- 문제: `run-local-validation-suite.sh` 는 순서를 고정해 주지만, 기본이 replay 포함인 full run 하나뿐이면 자잘한 회귀 확인에도 DB 재기동과 긴 replay를 매번 감수해야 한다. 결국 wrapper가 생겨도 짧은 피드백 루프에서는 다시 부분 실행 env를 외워야 했다.
- 해결: wrapper에 `VALIDATION_PROFILE=quick|full` 을 추가했다. `quick` 은 `auth/session -> recommendation click -> admin dashboard` 만 돌고, `full` 은 기존처럼 replay까지 포함한다. 개별 `RUN_*` override는 그대로 유지해 필요 시 더 세밀하게 조절할 수 있다.
- 이유: 상위 wrapper는 순서만 고정하는 것으로 끝나지 않고, 빠른 루프와 전체 루프를 둘 다 제공해야 실제로 자주 쓰인다. `quick/full` 프로필이 있어야 반복 검증이 가벼워지고 replay는 정말 필요할 때만 태우게 된다.

## 438) 로컬 검증 wrapper가 성공/실패만 알려주면 어떤 단계가 느린지 다시 체감으로만 추정하게 된다
- 문제: `run-local-validation-suite.sh` 가 상위 순서와 quick/full 프로필을 제공해도, 끝났을 때 각 단계가 몇 초 걸렸는지 정보가 없으면 병목이 auth/session 인지 click 인지 replay 인지를 다시 출력 감으로만 추정해야 했다.
- 해결: wrapper 종료 시 `suite_duration_seconds` 와 `step_duration_seconds=<label>|<seconds>` 요약을 같이 찍도록 보강했다.
- 이유: 반복 검증 도구는 성공 여부뿐 아니라 비용도 바로 보여줘야 실제 루프 최적화에 쓸 수 있다. 단계별 duration이 있으면 “quick면 충분한지”, “replay가 얼마나 무거운지”를 바로 읽을 수 있다.

## 439) 상위 wrapper가 실패만 전파하고 어느 단계에서 끊겼는지 바로 안 찍어 주면, 긴 로컬 검증 중단 지점을 다시 로그 흐름으로 눈으로 찾아야 한다
- 문제: `run-local-validation-suite.sh` 는 하위 smoke 실패를 그대로 전파하지만, 실패 시점에 상위 wrapper가 현재 단계명을 별도로 찍지 않으면 긴 출력에서 “auth/session 중이었는지, click이었는지, replay였는지”를 사람이 다시 따라가야 했다.
- 해결: wrapper에 `CURRENT_STEP_LABEL` 과 `ERR` trap을 넣어 `failed_step=<label>`, `elapsed_before_failure_seconds=<n>` 를 즉시 출력하게 했다.
- 이유: 상위 오케스트레이터는 성공한 경우 요약만 주는 것으로 끝나면 안 되고, 실패한 경우에도 어디서 끊겼는지 가장 먼저 알려줘야 반복 디버깅 속도가 올라간다.

## 440) 상위 wrapper가 env 기반 실행 계획만 지원하면, “지금 어떤 단계가 켜질지” 확인하려고 실제 실행 직전까지 문서를 다시 읽게 된다
- 문제: `run-local-validation-suite.sh` 에 quick/full 과 `RUN_*` override가 생긴 뒤에도, 현재 조합이 실제로 무엇을 돌릴지 확인하려면 스크립트 본문이나 문서를 다시 봐야 했다.
- 해결: wrapper에 `--help` 와 `--print-plan` 을 추가했다. `--print-plan` 은 현재 env/profile 기준 `validation_profile=... auth=... click=... dashboard=... replay=...` 만 출력하고 종료한다.
- 이유: 자주 돌리는 도구는 “실행”뿐 아니라 “실행 전 계획 확인”도 짧아야 한다. 계획 확인이 가벼워야 env override를 안전하게 바꿔가며 쓸 수 있다.

## 441) 실행 계획 확인이 env만 기준이면, 자주 쓰는 quick/full 조합조차 쉘 문법을 기억해야 해서 도구 사용성이 다시 떨어진다
- 문제: `VALIDATION_PROFILE=quick ...` 같은 env override는 유연하지만, quick/full 전환이나 replay skip 같은 자주 쓰는 조합조차 매번 env 문법으로 적어야 해 사용성이 떨어진다.
- 해결: wrapper에 `--quick`, `--full`, `--skip-replay` CLI shortcut을 추가했다. `--print-plan` 과 함께 조합해 실행 없이 계획만 확인할 수도 있게 했다.
- 이유: 반복 실행 도구는 가장 자주 쓰는 조합에 대해 더 짧은 입력 경로를 제공해야 한다. env override는 유지하되, 빈도가 높은 경로는 CLI shortcut으로 내려주는 편이 실사용성이 좋다.

## 442) 프로필과 skip shortcut이 있어도 “dashboard만 다시 확인”, “replay만 다시 태우기” 같은 단일 단계 실행은 여전히 env override를 여러 개 조합해야 한다
- 문제: quick/full/skip-replay 까지 생겨도, 단일 단계만 실행하려면 `RUN_AUTH_SESSION_SMOKE=false ...` 같은 override를 여전히 외워야 했다.
- 해결: wrapper에 `--only auth-session|click|dashboard|replay` 를 추가했다. `--print-plan` 과 조합하면 실제 실행 없이 단일 단계 계획도 바로 확인할 수 있다.
- 이유: 자주 쓰는 로컬 도구는 “전체 실행”뿐 아니라 “특정 단계만 다시 보기”가 빨라야 한다. 단일 단계 shortcut이 있어야 디버깅 중 반복 입력이 줄어든다.

## 443) 단일 단계 실행이 들어간 뒤에도 상위 출력이 기존 booleans만 보여주면, “왜 auth=false click=false 인지”를 다시 역해석해야 한다
- 문제: `--only dashboard` 같은 실행은 내부적으로 다른 단계를 false로 바꾸므로, plan 출력이 booleans만 있으면 단일 단계 의도를 사람이 다시 역으로 읽어야 했다.
- 해결: plan 출력과 failure 출력에 `only_step=...` 를 같이 노출하게 했다.
- 이유: 상위 wrapper는 내부 상태를 사람이 다시 추론하게 만들기보다, 사용자가 준 고수준 의도(`only_step`)를 그대로 드러내는 편이 읽기 쉽다.

## 444) replay artifact 보존이 자주 필요한데 상위 wrapper에서 이 의도를 못 받으면, 하위 스크립트 env를 또 따로 기억해야 한다
- 문제: replay 디버깅 때는 `KEEP_ARTIFACTS=true` 가 자주 필요하지만, 상위 wrapper가 이 intent를 직접 받지 못하면 사용자가 다시 하위 replay 스크립트 전용 env를 떠올려야 했다.
- 해결: wrapper에 `--keep-artifacts` 를 추가하고, plan 출력에도 `keep_artifacts=true` 를 노출한 뒤 replay 하위 스크립트로 그대로 전달하게 했다.
- 이유: 상위 오케스트레이터가 자주 쓰는 디버깅 의도까지 같이 받아줘야 실제 반복 루프가 짧아진다.

## 445) search 대시보드가 zero-result 총량만 보여주면, 실제로 어떤 키워드가 실패를 만들고 있는지 다시 raw `search_logs` 를 내려가 봐야 한다
- 문제: `zeroResultSearchesInWindow` count 만으로는 검색 품질 개선 액션을 바로 잡기 어렵다. 운영자는 결국 `search_logs` 에서 `result_count=0` keyword를 다시 직접 group by 해야 했다.
- 해결: admin dashboard search 섹션에 `zeroResultKeywordsInWindow` 를 추가해 최근 summary window 기준 상위 zero-result keyword를 같이 반환하게 했다.
- 이유: 검색 품질 개선의 첫 단계는 “얼마나 실패했나”보다 “무엇이 실패했나”를 바로 보는 것이다. top zero-result keyword가 summary 응답에 있어야 후속 ranking/filter 개선이 빨라진다.

## 446) `Batch AI Gateway` 를 “남은 기능”이라는 이유만으로 바로 next track에 올리면, 실제 병목보다 운영 복잡도만 먼저 시스템에 들여오게 된다
- 문제: 문서상 2차 기능으로 남아 있다는 이유만으로 `Batch AI Gateway` 를 곧바로 다음 구현 대상으로 삼으면, 현재 트래픽/비용 규모에서는 실시간 개인화가 충분한데도 batch polling, hard deadline fallback, partial completion 처리 같은 운영 모델을 먼저 구현하게 된다.
- 해결: `Batch AI Gateway` 는 지금 단계에서 active backlog가 아니라 “규모 확대 또는 비용 압박 발생 시 재검토할 deferred 2차 기능”으로 문서상 위치를 더 분명히 했다.
- 이유: 지금 프로젝트의 병목은 AI batch 미구현이 아니라 표본/운영 규모 부족이다. 이 상황에서 batch는 기능 공백보다 과한 운영 복잡도 추가에 가깝다.

## 447) search summary에 top zero-result keyword만 있으면, 어떤 지역/필터 조합이 계속 실패하는지는 여전히 raw `search_logs` 를 다시 뒤져야 한다
- 문제: `zeroResultKeywordsInWindow` 는 “무슨 단어가 실패하나”까지는 보여주지만, 실제 triage 단계에서는 `sido/sgg`, `status_filter`, `category`, `source_type`, `online_apply`, `include_closed`, `sort_key` 같은 조건 조합과 최근 샘플을 같이 봐야 원인을 더 빨리 좁힐 수 있다.
- 해결: `/api/admin/dashboard/search-failures` 를 추가해 summary window 기준 zero-result keyword/region/filter pattern/recent sample 상세를 별도 admin API로 분리했다.
- 이유: 요약 대시보드는 가볍게 유지하고, 검색 실패 triage는 별도 상세 endpoint에서 읽게 분리하는 편이 책임이 명확하고 후속 확장도 쉽다.
## 448) recommendation summary에 source/category/weight 분포와 최근 샘플만 있으면, 같은 사용자에게 같은 정책이 반복 노출되는 패턴은 다시 raw `recommendation_logs` 를 `user_key + service_id` 기준으로 group by 해야 한다
- 문제: 추천 triage에서 자주 필요한 것은 “같은 사용자에게 같은 정책이 몇 번 반복 노출되었는가”인데, 상세 응답에 재노출 그룹이 없으면 결국 `recommendation_logs` 를 다시 수동 group by 해야 한다.
- 해결: `recommendation-breakdowns` 응답에 repeat exposure group을 추가해 `user_key + service_id` 기준 노출 횟수, click/fallback 누적, 첫/마지막 노출 시각을 같이 반환하게 했다.
- 이유: 추천 품질 문제는 단순 CTR 총량보다 재노출 패턴에 더 잘 드러나는 경우가 많다. repeat group이 있으면 과한 재노출과 정상 반복 노출을 더 빨리 구분할 수 있다.

## 449) recommendation summary에 CTR/fallback 총량만 있으면, 어떤 source/category/weight stage가 클릭 또는 fallback을 만들고 있는지 다시 raw join을 내려가야 한다
- 문제: summary 응답의 `sentInWindow`, `clickedInWindow`, `fallbackInWindow`, `weightBucketsInWindow` 만으로는 실제 triage 때 “어느 source/category가 fallback을 많이 만들고 있는가”, “어느 weight stage에서 클릭이 붙는가”, “최근 fallback/click 샘플이 무엇인가”를 바로 읽을 수 없다.
- 해결: `/api/admin/dashboard/recommendation-breakdowns` 를 추가해 summary window 기준 source/category/weight breakdown과 최근 fallback/click sample을 별도 admin API로 분리했다.
- 이유: 추천 품질 조정은 총량 지표보다 상세 분포가 먼저 필요하다. 요약 대시보드는 그대로 두고, 세부 triage는 별도 endpoint에 분리하는 편이 책임과 확장성이 더 낫다.

## 450) collect 실패 상세에 총량/분포/샘플만 있으면, 지금도 실패 streak가 이어지는지와 `BOKJIRO_LOCAL` 회로가 열려 있는지를 다시 raw log와 runtime state에서 따로 찾아야 한다
- 문제: 수집 triage에서 실제로 중요한 것은 “지금 연속 실패가 이어지는가”와 “rate-limit 회로가 아직 열려 있는가”인데, 실패 상세에 이 두 신호가 없으면 운영자가 `api_sync_logs` 를 다시 시간순으로 읽고, local circuit state는 로그로만 추정해야 했다.
- 해결: `collect-failures` 응답에 job streak 와 circuit status 를 추가해 최근 연속 `FAILED/PARTIAL_SUCCESS` 길이와 `BOKJIRO_LOCAL` open-circuit 상태를 같이 반환하게 했다.
- 이유: 총량 지표는 과거를 설명하지만, streak 와 circuit 은 현재 진행형 위험을 더 잘 보여준다. 둘을 같이 봐야 “한 번 실패한 것”과 “지금도 계속 막혀 있는 것”을 바로 구분할 수 있다.

## 451) collect summary에 실패 총량과 최신 샘플 몇 개만 있으면, 어떤 job이 반복 실패하는지와 error code 분포를 다시 raw `api_sync_logs` 에서 group by 해야 한다
- 문제: summary 응답의 `failedJobsLast24h`, `latestFailuresInWindow` 만으로는 “어느 collect job이 반복적으로 실패하는가”, “partial success가 어느 정도 섞이는가”, “실패 원인이 어떤 error code에 몰리는가”를 바로 읽기 어렵다.
- 해결: `/api/admin/dashboard/collect-failures` 를 추가해 summary window 기준 failed/partial 총량, job breakdown, error code breakdown, recent sample을 별도 admin API로 분리했다.
- 이유: 수집 triage는 summary 총량보다 실패 분포와 최근 샘플이 먼저 필요하다. 대시보드 요약은 유지하고, 상세는 별도 endpoint로 분리하는 편이 책임과 후속 확장이 더 낫다.

## 452) zero-result 상세에 keyword/region/filter/sample만 있으면, 같은 사용자나 같은 브라우저가 같은 실패 검색을 반복하는 패턴은 다시 raw `search_logs` 를 actor 기준으로 group by 해야 한다
- 문제: zero-result triage에서 실제로 자주 필요한 것은 “같은 사람이 같은 실패 검색을 반복하는가”인데, 상세 응답에 actor 기준 재시도 묶음이 없으면 결국 `user_key` 또는 `client_fingerprint` 로 다시 수동 group by 를 해야 한다.
- 해결: `search-failures` 응답에 retry group을 추가해 `USER_KEY` 또는 `FINGERPRINT` actor 기준으로 같은 zero-result 검색 반복 패턴을 같이 반환하게 했다.
- 이유: 검색 실패 원인은 단순 분포뿐 아니라 반복 행동 패턴에도 숨어 있다. retry group이 있으면 “지속적으로 못 찾는 수요”와 “일회성 실패”를 바로 구분할 수 있다.

## 453) zero-result 상세에 분포와 샘플만 있으면, 같은 actor가 반복 실패하다가 나중에 성공으로 회복한 패턴은 다시 raw `search_logs` 를 actor 기준으로 훑어봐야 한다
- 문제: 검색 triage에서 중요한 건 반복 실패 자체뿐 아니라, 같은 `user_key` 또는 `client_fingerprint` 가 같은 검색을 나중에 성공으로 회복했는지 여부다. 이 정보가 없으면 “계속 막혀 있는 수요”와 “일시 실패 후 해소된 수요”를 분리하기 어렵다.
- 해결: `search-failures` 응답에 recovered search group을 추가해 같은 actor/query 조합에서 zero-result 이후 non-zero result가 붙은 패턴을 같이 반환하게 했다.
- 이유: 회복 그룹이 있으면 검색 품질 문제가 영구적인지, 재시도나 데이터 갱신으로 해소되는 성격인지를 더 빨리 판단할 수 있다.

## 454) 전화번호를 “지금은 안 받는다”고 결정했으면, 프론트에서만 숨기는 걸로 끝내지 말고 프로필 API 계약과 저장 경로도 같이 닫아야 한다
- 문제: 회원가입은 이미 전화번호를 받지 않는데, `UpdateProfileRequest` 와 `ProfileResponse` 에는 여전히 `phone` 필드가 남아 있었고 `UserService.updateProfile()` 도 요청이 오면 `phone_enc` 를 갱신했다. 이 상태에서는 프론트 UI만 숨겨도 다른 클라이언트가 phone을 보내면 제품 정책과 다르게 수집이 계속될 수 있었다.
- 해결: `UpdateProfileRequest` 와 `ProfileResponse` 에서 `phone` 계약을 제거하고, `UserService.updateProfile()` 의 `phone_enc` 갱신도 중단했다. 동시에 프로필 완성도 계산에서 전화번호 가산점을 빼서 “미수집 정책”과 점수 기준도 맞췄다.
- 이유: 개인정보 최소 수집 정책은 UI가 아니라 API 계약에서 닫혀 있어야 안정적이다. DB 컬럼과 PII 경계는 future 확장성 때문에 남겨 두더라도, 현재 제품 범위에서는 dormant 상태로 두는 편이 맞다.

## 455) 챗 메시지 전송 전체를 하나의 트랜잭션으로 감싼 채 외부 AI 호출까지 넣어두면, DB 트랜잭션이 네트워크 지연 시간만큼 길어지고 실패 범위도 불필요하게 커진다
- 문제: `ChatMessageService.sendMessage()` 가 USER 메시지 저장, 후보 조회, `ChatAiGateway` 호출, ASSISTANT 메시지 저장을 한 transaction 안에서 처리하고 있었다. OpenAI 호출이 느리면 DB 커넥션이 계속 잡혀 있고, AI 호출 실패 시 사용자 메시지까지 같이 rollback될 수 있었다.
- 해결: 새 `ChatMessageCommandService` 를 만들어 USER 메시지 저장과 ASSISTANT 메시지 저장을 각각 짧은 transaction으로 분리하고, `ChatMessageService` 는 orchestration과 외부 AI 호출만 담당하게 바꿨다.
- 이유: 외부 네트워크 호출은 transaction 밖으로 밀어내고, DB write는 짧고 명확한 command 경계로 자르는 편이 커넥션 점유와 실패 전파를 줄인다.

## 456) `bokjiro-details-gap-fill` 이 표준 collect 경계 바깥에서 직접 실행되면, 일반 수집과 다른 lock/log discipline을 가져서 운영 관측과 실패 추적이 엇갈린다
- 문제: `CollectAdminController` 가 `BokjiroDetailCollectService.collectBokjiroDetailGapFillResult(...)` 를 직접 호출하고 있어, 일반 source collect가 공유하는 `CollectExecutionGuard` 와 `ApiSyncLogService` 경계를 타지 않았다. 그래서 gap-fill 실패는 `api_sync_logs` 에 안 남고, collect lock discipline도 별도로 흩어져 있었다.
- 해결: `CollectService.collectBokjiroDetailGapFill(...)` 를 추가하고, 전용 `CollectSource.BOKJIRO_DETAIL_GAP_FILL` 을 도입해 gap-fill도 표준 lock/log 경계 안에서 실행되게 바꿨다.
- 이유: 같은 collect 계열 작업은 수동 경로라도 실행 직렬화와 로그 저장 방식을 공유해야 운영자가 같은 기준으로 상태를 읽을 수 있다.

## 457) `AuthService` 가 로그인, refresh/logout, 비밀번호 재설정까지 모두 들고 있으면, 토큰 정책 변경과 비밀번호 재설정 정책 변경이 같은 클래스 수정으로 얽힌다
- 문제: 기존 `AuthService` 는 signup/login 외에도 refresh token rotation, logout, password reset token 저장/메일 발송/비밀번호 변경까지 한 클래스에 몰려 있었다. 이 상태에서는 토큰 수명주기나 비밀번호 재설정 흐름이 바뀔 때마다 같은 서비스가 동시에 바뀌어 책임이 과해졌다.
- 해결: 토큰 발급/refresh/logout 은 `AuthTokenService` 로, 비밀번호 재설정 요청/확정은 `PasswordResetService` 로 분리하고, `AuthService` 는 signup/login/admin role 해석 위주의 orchestration으로 축소했다.
- 이유: 로그인 진입점과 토큰 수명주기, 비밀번호 재설정은 변경 이유가 다르다. API 계약은 그대로 두되 내부 경계를 나누는 편이 SRP에 맞고 테스트도 더 좁게 유지할 수 있다.

## 458) policy 조회 서비스가 추천 persistence repository를 직접 읽으면, policy 도메인이 recommend 저장 모델 변경에 같이 흔들린다
- 문제: `PolicyService` 와 `PolicySearchService` 가 북마크 여부를 계산하려고 `UserRecommendationRepository` 와 `UserRepository` 를 직접 사용하고 있었다. 이 상태에서는 추천 저장 모델이나 사용자 키 조회 방식이 바뀌면 policy read service까지 같이 수정해야 했다.
- 해결: 북마크 읽기 전용 경계를 `RecommendationReadFacade` 로 분리하고, policy 쪽은 더 이상 추천 repository를 직접 조회하지 않게 정리했다.
- 이유: policy는 “북마크 여부가 필요하다”는 의도만 표현하고, 실제 추천 read model 조회 방식은 recommend 도메인 안에 두는 편이 경계가 명확하다.

## 459) `PolicyService` 와 `PolicySearchService` 가 `WelfareServiceRepository` 의 조합식 조회 메서드를 직접 고르면, 필터 조합이 늘어날수록 서비스가 쿼리 선택 책임까지 같이 떠안게 된다
- 문제: 정책 목록과 검색 서비스가 `findListWithFilters`, `searchByKeywordWithFiltersNoRegion`, `...WithSido`, `...WithSidoSgg` 같은 조합식 메서드를 직접 선택하고 있었다. 이 구조에서는 지역/정렬/필터 조합이 늘어날 때마다 서비스가 persistence 분기까지 함께 수정해야 한다.
- 해결: `PolicyListReadCondition`, `PolicySearchReadCondition`, `WelfareServiceReadRepository` 를 추가하고, `PolicyService` 와 `PolicySearchService` 가 목록/검색 조건 객체만 넘기도록 바꿨다. 조합식 쿼리 선택 책임은 read repository 구현으로 이동시켰다.
- 이유: 지금 단계에서는 기존 JPA repository 메서드를 완전히 걷어내지 않더라도, 서비스에서 “무슨 조건으로 읽고 싶은가”만 표현하고 “어떤 조합식 메서드를 고를지”는 read layer에 두는 편이 SRP와 경계 분리에 맞다.

## 460) `UserService` 가 북마크 조회, 프로필/우선순위 수정, 비밀번호 변경, 탈퇴, 알림 수신 거부까지 모두 들고 있으면 사용자 도메인 변경이 한 서비스에 과도하게 몰린다
- 문제: 기존 `UserService` 는 read 경로(`getProfile`, `getBookmarks`)와 command 경로(`updateProfile`, `updatePriorities`, `changePassword`, `withdraw`, `unsubscribeNotifications`)를 함께 들고 있었고, 프로필/우선순위/계정 종료 규칙이 바뀔 때마다 같은 클래스를 같이 수정해야 했다.
- 해결: 북마크 조회는 `UserBookmarkReadService` 로, 프로필/우선순위 변경은 `UserProfileCommandService` 로, 비밀번호/탈퇴/알림 수신 거부는 `UserAccountCommandService` 로 분리했다. `UserService` 는 기존 controller 계약을 유지하는 facade만 남겼다.
- 이유: 외부 API 계약은 유지하면서 내부 책임을 read/bookmark, profile command, account command로 나누면 테스트 범위가 좁아지고 SRP/CQS 위반도 줄일 수 있다.

## 461) `collect-failures` 의 streak를 summary window 안쪽 최근 이력만으로 계산하면, 더 오래 이어진 연속 실패/partial 상태를 과소계상한다
- 문제: `jobStreaks` 계산이 `summaryWindowAgo` 이후 실행만 읽고 있었기 때문에, 예를 들어 7일보다 더 오래 이어진 실패 streak는 대시보드에서 잘린 값으로 보였다. 이 상태에서는 운영자가 “현재 연속 실패 길이”를 실제보다 작게 읽을 수 있었다.
- 해결: `fetchRecentCollectJobRuns(...)` 를 job별 최신 N건 전체 이력 기준으로 바꾸고, `collect-failures` 서비스는 summary window와 별개로 current streak를 계산하게 수정했다.
- 이유: summary window는 분포/샘플 범위를 제한하는 용도이고, streak는 현재 상태를 보여주는 지표다. 두 의미를 섞으면 운영 해석이 틀어진다.

## 462) admin dashboard가 `BokjiroLocalClient` 구현을 직접 보면, collect 운영 관측 계층이 특정 gateway 구현 세부사항에 묶인다
- 문제: `AdminDashboardService` 가 `BokjiroLocalClient.getRateLimitCircuitStatus()` 를 직접 호출하고 있었다. 이 구조에서는 admin dashboard가 collect runtime status 자체가 아니라 특정 source gateway 구현과 그 내부 상태 표현을 직접 알아야 했다.
- 해결: `CollectRuntimeStatusService` 를 추가하고, dashboard는 collect runtime status 전용 서비스가 반환하는 snapshot만 읽도록 바꿨다.
- 이유: 운영 관측 계층은 개별 gateway 구현보다 “현재 수집 런타임 상태”라는 응용 계층 개념에 의존하는 편이 경계가 더 명확하고, 이후 source가 늘어나도 dashboard 수정 범위를 줄일 수 있다.

## 463) `RetrievalService` 가 지역/최신 조합마다 `WelfareServiceRepository` 메서드를 직접 고르면, 추천 후보 조회 규칙이 서비스 코드와 persistence 분기 로직에 같이 퍼진다
- 문제: `RetrievalService` 가 `findCandidatesWithRegionCode`, `findCandidatesWithSido`, `findLatestCandidatesWithRegionCode`, `findLatestCandidatesWithSido`, `findCandidates`, `findLatestCandidates` 를 직접 선택하고 있었다. 이 상태에서는 추천 후보 조회 조합이 바뀔 때 retrieval 서비스와 repository 분기가 함께 수정된다.
- 해결: `RecommendationCandidateReadCondition`, `RecommendationCandidateReadRepository` 를 추가하고, `RetrievalService` 는 추천 후보 조회 의도만 condition으로 넘기게 바꿨다. 실제 조합식 repository 선택은 전용 read repository 구현으로 이동시켰다.
- 이유: 추천 파이프라인 서비스는 “어떤 후보를 읽고 싶은가”에 집중하고, 지역/최신 조합식 persistence 선택은 read 계층으로 숨기는 편이 SRP와 변경 파급도 관리에 더 낫다.

## 464) `ChatPolicyService` 가 챗봇 후보 검색과 fallback 인기 정책 조회를 위해 `WelfareServiceRepository` 메서드 조합을 직접 고르면, 챗 도메인이 policy persistence 분기까지 같이 떠안게 된다
- 문제: `ChatPolicyService` 가 `searchChatCandidates(...)` 와 `findBySearchYouthRelevantTrueAndStatusInOrderByViewCountDescCreatedAtDesc(...)` 를 직접 고르고 있었다. 이 상태에서는 챗봇 후보 조회 규칙이 바뀔 때 질문 해석 서비스와 persistence 분기를 함께 수정해야 했다.
- 해결: `ChatPolicyReadCondition`, `ChatPolicyReadRepository` 를 추가하고, `ChatPolicyService` 는 질문에서 만든 fulltext keyword 와 limit만 조건 객체로 넘기게 바꿨다. 검색 우선/fallback 인기 정책 조회 선택은 전용 read repository 구현으로 이동시켰다.
- 이유: 챗 도메인 서비스는 질문 해석과 limit 정규화에 집중하고, 후보 조회 구현 분기는 read 계층으로 숨기는 편이 SRP와 도메인 경계 분리에 더 낫다.

## 465) policy 상세/목록의 북마크 토글이 recommendation 저장소와 `userKey` 조회를 직접 들고 있으면, policy 도메인이 recommendation command 규칙과 저장 모델 변경에 같이 묶인다
- 문제: `PolicyService.toggleBookmark(...)` 가 `UserRecommendationRepository`, `UserRepository`, `WelfareServiceRepository` 를 직접 사용해 `userKey` 조회, 기존 추천 이력 조회, placeholder 추천 생성, 북마크 한도 검사까지 모두 처리하고 있었다. 동시에 `RecommendationFacade.toggleBookmark(...)` 도 별도 방식으로 북마크 토글을 들고 있어 command 규칙이 두 군데로 갈라져 있었다.
- 해결: `RecommendationBookmarkCommandService` 를 추가해 recommendation ID 기준 토글과 policy service ID 기준 토글을 한 경계로 모으고, `PolicyService` 와 `RecommendationFacade` 는 북마크 command 서비스로만 위임하게 바꿨다.
- 이유: 북마크 토글은 recommendation 저장 모델과 userKey 해석 규칙에 가까운 command다. policy/read 진입점과 recommendation API 진입점이 같은 규칙을 공유하도록 recommendation 도메인 안으로 모으는 편이 경계와 변경 파급도 관리에 더 낫다.

## 466) `PolicyRankingService` 가 랭킹 대상 정책 조회를 위해 `WelfareServiceRepository.findByStatusIn(...)` 를 직접 호출하면, 랭킹 계산 서비스가 persistence selection 책임까지 같이 떠안게 된다
- 문제: `PolicyRankingService` 가 ACTIVE/UPCOMING 정책 목록을 직접 조회하고 있었다. 이 상태에서는 랭킹 대상 정책 범위가 바뀔 때 점수 계산 서비스와 persistence 선택이 함께 수정된다.
- 해결: `PolicyRankingReadRepository` 를 추가하고, `PolicyRankingService` 는 랭킹 대상 정책 목록을 전용 read repository로부터 받게 바꿨다.
- 이유: 랭킹 서비스는 unique view, freshness, explore slot 계산에 집중하고, “어떤 정책이 랭킹 대상인가”라는 조회 규칙은 read 계층으로 숨기는 편이 SRP와 변경 파급도 관리에 더 낫다.

## 467) policy 로그 서비스마다 `UserRepository.findUserKeyById(...)` 를 직접 호출하면, nullable user key 해석 규칙이 여러 서비스에 중복되고 이후 user key 조회 정책 변경이 분산된다
- 문제: `PolicyViewLogService` 와 `PolicySearchLogService` 가 각각 `resolveUserKey(...)` 를 가지고 `UserRepository.findUserKeyById(...)` 를 직접 호출하고 있었다. 이 상태에서는 비로그인 시 `null` 처리, 향후 user key 조회 정책 변경이 서비스별로 흩어진다.
- 해결: `UserKeyLookupService` 를 추가하고, policy 로그 서비스 둘 다 nullable user key 해석을 전용 lookup 서비스로 위임하게 바꿨다.
- 이유: user key 조회는 도메인 서비스의 핵심 로직이 아니라 cross-cutting lookup 규칙에 가깝다. lookup 책임을 한 경계로 모아두는 편이 중복과 변경 파급도를 줄인다.

## 468) policy 로그만 `UserKeyLookupService` 를 쓰고 recommend/user 서비스들은 계속 `findUserKeyById(...)` 를 직접 호출하면, nullable/required user key 해석 규칙이 절반만 정리된 상태로 남는다
- 문제: `RecommendationReadFacade`, `RecommendationLogService`, `UserBookmarkReadService`, `UserProfileCommandService`, `UserAccountCommandService`, `UserReadService`, `AuthTokenService` 가 여전히 각각 `findUserKeyById(...)` 를 직접 호출하고 있었다. 이 상태에서는 policy 쪽만 중복이 줄고, 나머지 경계에서는 required/nullable 규칙과 예외 처리 방식이 다시 흩어진다.
- 해결: 위 서비스들도 `UserKeyLookupService` 를 사용하게 바꿔 nullable lookup(`findNullable`) 과 required lookup(`findRequired`) 규칙을 한 곳으로 모았다.
- 이유: user key 조회는 policy 전용 concern이 아니라 user/recommend/policy 전반의 공통 lookup이다. 공통 경계를 한 번 만들었다면 넓게 적용하는 편이 중복 제거와 후속 정책 변경 대응에 더 낫다.

## 469) 공통 lookup 서비스를 도입한 뒤에도 `RecommendationBookmarkCommandService`, `RecommendationFacade`, `UserCoreSyncService` 가 직접 `findUserKeyById(...)` 를 들고 있으면, 경계가 대부분 정리된 것처럼 보여도 핵심 command/orchestration 경로에는 예외 규칙이 남는다
- 문제: `UserKeyLookupService` 도입 후에도 recommendation 북마크 command, 추천 facade의 저장 추천 조회, user core dual-write sync 경로는 여전히 `UserRepository.findUserKeyById(...)` 를 직접 호출하고 있었다. 이 상태에서는 핵심 진입점에서만 별도 예외 처리/lookup 정책이 남아 “공통 lookup 규칙”이 완결되지 않는다.
- 해결: 세 서비스 모두 `UserKeyLookupService.findRequired(...)` 로 전환하고, 직접 `findUserKeyById(...)` 호출은 운영 코드 기준 lookup 서비스 내부로만 가두었다.
- 이유: lookup 규칙을 공통 경계로 만들었다면, 남은 직접 호출이 command/orchestration 핵심 경로에 있을수록 정책 drift 가능성이 커진다. 마지막 잔여 직접 조회까지 접어야 예외 처리와 후속 변경 포인트를 truly one place 로 모을 수 있다.

## 470) `ChatMessageService`, `ChatSessionService`, `NotificationService` 와 user command/read 서비스들이 각자 `findActiveUser(...)` 나 `findByUserKey(...)` 를 들고 있으면, 탈퇴 사용자 차단 규칙과 userKey 전달 규칙이 서비스별로 조금씩 갈라진다
- 문제: chat 두 서비스와 notification, bookmark/profile/account/auth token 경로가 각자 `UserRepository.findById(...)`, `findByUserKey(...)`, `user.isActive()` 검사를 직접 들고 있었다. 이 상태에서는 탈퇴 사용자 거부 기준과 “user와 userKey를 같이 써야 하는가” 같은 lookup 규칙이 여러 서비스에 중복된다.
- 해결: `UserReadService` 에 `getActiveUserContext(...)`, `getActiveUserByUserKey(...)` 를 추가하고, 위 서비스들이 active user resolution 을 전부 user read 경계로 위임하게 정리했다.
- 이유: active user lookup 은 chat/notification/user command 어느 한 도메인의 핵심 로직이 아니라 cross-cutting read 규칙이다. user entity 와 resolved userKey 를 함께 써야 하는 경우까지 공통화해야 탈퇴 사용자 차단과 lookup drift 를 한 곳에서 관리할 수 있다.

## 471) `AuthService` 와 `PasswordResetService` 가 active user 조회를 각자 직접 들고 있으면, 인증/재설정 경로만 user read 경계 밖의 예외로 남아 lookup 규칙이 완결되지 않는다
- 문제: `AuthService.login(...)` 은 `UserRepository.findByUserKey(...)` 로 active user 를 직접 조회하고 있었고, `PasswordResetService` 도 reset 대상 검증을 위해 같은 조회를 반복하고 있었다. 이 상태에서는 auth/reset 경로만 탈퇴 사용자 차단과 예외 규칙이 별도로 남는다.
- 해결: `AuthService` 는 `UserReadService.getActiveUserByUserKey(...)` 로 위임하고, `PasswordResetService` 는 `UserReadService.findOptionalActiveUserByUserKey(...)` 로 reset 대상 활성 사용자 검증을 공통화했다.
- 이유: 인증과 비밀번호 재설정도 결국 공통 active-user read 규칙 위에 있어야 한다. 마지막 직접 `findByUserKey(...)` 경로까지 정리해야 lookup 정책과 예외 처리 drift 를 truly one place 로 모을 수 있다.

## 472) `UserAdminController` 가 forced logout 전에 `UserRepository.findIdByUserKey(...)` 를 직접 호출하면, 컨트롤러가 user 존재 판단까지 떠안고 user read 경계 바깥의 예외로 남는다
- 문제: forced logout 경로는 userKey 공백 검증 뒤 곧바로 `UserRepository.findIdByUserKey(...)` 를 호출하고 있었다. 이 상태에서는 컨트롤러가 요청/응답 orchestration뿐 아니라 user 존재 확인까지 직접 책임진다.
- 해결: `UserReadService.requireExistingUserIdByUserKey(...)` 를 추가하고, `UserAdminController` 는 forced logout 전에 해당 read 경계만 호출하게 정리했다.
- 이유: 컨트롤러는 입력 검증과 응답 orchestration에 집중하고, user 존재/조회 규칙은 read 서비스로 모아야 이후 admin 경로가 늘어나도 저장소 직접 의존이 다시 번지지 않는다.

## 473) `SearchYouthRelevanceService` 가 backfill 대상 전체 정책을 위해 `WelfareServiceRepository.findAll()` 을 직접 호출하면, relevance 규칙 서비스가 대상 조회 persistence 선택까지 같이 떠안는다
- 문제: `SearchYouthRelevanceService.backfillAll()` 은 전체 정책을 직접 조회한 뒤 tag를 묶고 청년 검색 relevance를 재계산하고 있었다. 이 상태에서는 “어떤 정책이 backfill 대상인가”라는 조회 규칙이 서비스 코드에 묻어난다.
- 해결: `SearchYouthRelevanceReadRepository` 를 추가하고, `backfillAll()` 은 `findBackfillTargetServices()` 로 전체 대상만 받게 정리했다.
- 이유: relevance 계산 서비스는 청년 검색 relevance 규칙과 집계에 집중하고, 대상 조회 범위/선택은 read 계층으로 숨기는 편이 SRP와 후속 backfill 범위 변경 대응에 더 낫다.

## 474) `RecommendationController` 가 `CanonicalRecommendationReadModelRepository` 를 직접 호출하면, 웹 계층이 recommendation read-model 선택과 projection 조립 책임까지 같이 떠안는다
- 문제: 추천 목록/갱신 API는 `UserRecommendation` 목록을 받은 뒤 컨트롤러 내부 `toResponses(...)` 에서 `CanonicalRecommendationReadModelRepository.findByServiceIds(...)` 를 직접 호출하고 있었다. 이 상태에서는 recommendation 응답 조립 규칙이 controller 레벨에 묻어난다.
- 해결: `RecommendationReadFacade` 에 `findCandidateProjections(...)` 를 추가하고, 컨트롤러는 facade를 통해 projection map만 받아 응답 조립하게 정리했다.
- 이유: 웹 계층은 요청/응답 orchestration에 집중하고, recommendation read-model 선택은 recommendation 경계로 숨기는 편이 controller 단 책임과 후속 projection 변경 파급도 관리에 더 낫다.

## 475) `UserBookmarkReadService` 가 recommendation 저장소와 canonical projection을 직접 읽으면, user 도메인이 recommendation persistence와 summary 조립 책임까지 같이 떠안는다
- 문제: 북마크 목록 조회는 active user 해석 뒤 `UserRecommendationRepository.findLatestBookmarkedByUserKey(...)` 와 `CanonicalRecommendationReadModelRepository.findByServiceIds(...)` 를 직접 호출하고 있었다. 이 상태에서는 user 도메인이 recommendation summary 조립 방식을 알아야 한다.
- 해결: `RecommendationReadFacade` 에 `findBookmarkedPolicySummaries(...)` 를 추가하고, `UserBookmarkReadService` 는 active user 해석 후 facade 위임만 하게 정리했다.
- 이유: 북마크 목록은 user 기능이지만, 실제 summary 조립은 recommendation 저장 모델과 canonical projection을 아는 recommendation 경계에 두는 편이 도메인 분리와 변경 파급도 관리에 더 낫다.

## 476) `RecommendationBookmarkCommandService` 가 북마크 placeholder 생성을 위해 `WelfareServiceRepository.findById(...)` 를 직접 호출하면, recommendation command가 policy 저장소 선택까지 같이 떠안는다
- 문제: 정책 북마크 이력이 없을 때 placeholder 추천을 만드는 경로는 `WelfareServiceRepository.findById(...)` 로 정책 엔티티를 직접 조회하고 있었다. 이 상태에서는 recommendation command가 북마크 규칙뿐 아니라 policy entity lookup 구현도 알아야 한다.
- 해결: `PolicyLookupService` 를 추가하고, `RecommendationBookmarkCommandService` 는 `getRequiredService(...)` 로 정책 엔티티 조회를 위임하게 정리했다.
- 이유: placeholder 추천 생성에 정책 엔티티가 필요하더라도, entity lookup은 policy 경계에 두는 편이 cross-domain 저장소 결합을 줄이고 recommendation command의 책임을 북마크 규칙에 집중시키기에 더 낫다.

## 477) `PolicyService` 와 `PolicySearchService` 가 canonical recommendation read-model 을 직접 조회하면, policy 도메인이 recommendation projection 저장 구조를 계속 알아야 한다
- 문제: 정책 목록/상세/검색 응답은 `CanonicalRecommendationReadModelRepository.findByServiceIds(...)` 를 직접 호출해 summary projection 을 조립하고 있었다. 이 상태에서는 policy 서비스가 recommendation read-model 선택과 projection 조회 규칙까지 같이 떠안는다.
- 해결: `RecommendationReadFacade.findCandidateProjectionsByServices(...)` 를 추가하고, `PolicyService` 와 `PolicySearchService` 는 정책 목록만 넘겨 projection map 을 받도록 정리했다.
- 이유: policy 서비스는 정책 필터링과 응답 조립에 집중하고, recommendation canonical projection 조회는 recommendation read 경계에 모아야 projection 저장 구조 변경의 파급 범위를 줄일 수 있다.

## 478) `PolicyRankingService` 가 canonical recommendation read-model 을 직접 조회하면, ranking 계산 서비스가 projection 저장소 선택까지 같이 떠안는다
- 문제: 정책 랭킹 응답은 rankable policy 목록을 구한 뒤 `CanonicalRecommendationReadModelRepository.findByServiceIds(...)` 를 직접 호출해 summary projection 을 조립하고 있었다. 이 상태에서는 ranking 서비스가 점수 계산뿐 아니라 recommendation projection 조회 구현까지 알아야 한다.
- 해결: `PolicyRankingService` 도 `RecommendationReadFacade.findCandidateProjectionsByServices(...)` 를 사용하게 정리했다.
- 이유: 목록/검색/랭킹 모두 정책 summary projection 조회 규칙은 동일한 recommendation read 경계로 모으는 편이 이후 projection 저장 구조나 조립 규칙이 바뀔 때 영향 범위를 줄인다.

## 479) `PolicyService.getDetail(...)` 가 정책 엔티티 조회와 상세/지역/태그 조회를 모두 직접 들고 있으면, 정책 상세 orchestration 서비스가 detail aggregate read 구현까지 같이 떠안는다
- 문제: `PolicyService.getDetail(...)` 는 `WelfareServiceRepository.findById(...)`, `WelfareServiceDetailRepository.findByServiceId(...)`, `ServiceRegionRepository.findByServiceId(...)`, `ServiceTagRepository.findByServiceId(...)` 를 한 메서드 안에서 직접 호출하고 있었다.
- 해결: 정책 엔티티 조회는 `PolicyLookupService`, 상세/지역/태그 묶음 조회는 `PolicyDetailReadService.getAggregate(...)` 로 이동했다.
- 이유: `PolicyService` 는 상세 응답 orchestration과 bookmark/projection 결합만 맡고, detail aggregate read 구현은 별도 read 서비스에 두는 편이 책임 분리가 더 선명하다.

## 480) `RetrievalService` 가 canonical recommendation read-model 저장소를 직접 조회하면, 추천 후보 조회 서비스가 projection 저장 구조까지 같이 떠안는다
- 문제: base/latest 후보를 고른 뒤 `CanonicalRecommendationReadModelRepository.findByServiceIds(...)` 로 projection map 을 직접 조회하고 있었다. 이 상태에서는 retrieval 서비스가 후보 조회 조건뿐 아니라 canonical projection read-model 선택까지 알아야 한다.
- 해결: `RecommendationReadFacade.findCandidateProjectionsByServiceIds(...)` 를 추가하고, `RetrievalService` 는 service id 목록만 넘겨 projection map 을 받도록 정리했다.
- 이유: retrieval 은 후보 selection/filtering 에 집중하고, canonical projection 조회 규칙은 recommendation read 경계에 모으는 편이 projection 저장 구조 변경의 파급을 줄인다.

## 481) `SearchYouthRelevanceService` 가 backfill 대상 조회는 read 경계 뒤로 넘겼는데 태그 조회는 여전히 `ServiceTagRepository` 를 직접 들고 있으면, 백필 서비스가 read 구현을 절반만 숨긴 상태로 남는다
- 문제: `backfillAll()` 은 대상 정책 목록은 `SearchYouthRelevanceReadRepository.findBackfillTargetServices()` 로 읽으면서도, 태그는 다시 `ServiceTagRepository.findByServiceIdIn(...)` 를 직접 호출하고 있었다.
- 해결: `SearchYouthRelevanceReadRepository.findTagsByServiceIds(...)` 를 추가하고, backfill 서비스는 태그 로딩도 같은 read 경계로 받게 정리했다.
- 이유: 백필 서비스는 relevance 재계산 규칙에 집중하고, 대상/태그 읽기 구현은 같은 read 경계 안에 두는 편이 저장소 선택 책임을 한 곳으로 모으기에 낫다.

## 482) `UserPiiSyncQueueService` 가 enqueue만 감싸고 queue row lookup은 processor/replay가 각자 `findByUserKey(...)` 를 직접 호출하면, user pii sync 흐름의 queue access 규칙이 다시 분산된다
- 문제: `UserPiiSyncProcessor.process(...)` 와 `UserPiiSyncReplayService.replaySingle(...)` 는 queue row 존재 확인을 위해 `UserPiiSyncQueueRepository.findByUserKey(...)` 를 직접 호출하고 있었다.
- 해결: `UserPiiSyncQueueService` 에 `findOptional(...)`, `exists(...)` 를 추가하고, processor/replay는 queue lookup도 같은 service 경계로 위임하게 정리했다.
- 이유: enqueue와 queue lookup 규칙을 같은 service로 모아야 user pii sync 흐름에서 queue row access 책임이 한 곳에 남고, replay/processor 간 존재 확인 정책 drift를 줄일 수 있다.

## 483) `UserCoreSyncService` 가 auth/profile projection upsert 구현까지 직접 들고 있으면, core sync orchestration 서비스가 projection 저장 세부사항까지 같이 떠안는다
- 문제: `UserCoreSyncService.syncFromUser(...)` 는 userKey 해석 뒤 `AuthUserRepository.findByUserKey(...)`, `UserProfileRepository.findByUserKey(...)`, save 호출을 모두 직접 처리하고 있었다.
- 해결: auth/profile projection upsert 를 `UserCoreProjectionSyncService` 로 옮기고, `UserCoreSyncService` 는 age 계산과 sync orchestration, pii queue 적재만 맡게 정리했다.
- 이유: core sync는 어떤 projection을 언제 갱신할지만 결정하고, projection별 upsert 구현은 별도 write 경계에 두는 편이 SRP와 후속 변경 파급도 관리에 낫다.

## 484) `PasswordResetService` 가 reset 메일 수신자 조회를 위해 user pii 저장소와 복호화를 직접 들고 있으면, reset flow 서비스가 user read 경계 밖의 PII read 구현까지 떠안는다
- 문제: `requestPasswordReset(...)` 는 userKey를 찾은 뒤 `UserPiiReadWriteRepository.findByUserKey(...)` 와 `AesEncryptUtil.decrypt(...)` 를 직접 호출해 메일 수신자를 만들고 있었다.
- 해결: reset 메일 수신자 조회를 `UserReadService.getNotificationEmailByUserKey(...)` 로 위임하고, `PasswordResetService` 에서 user pii 저장소와 복호화 의존을 제거했다.
- 이유: 비밀번호 재설정 흐름은 토큰 발급과 검증에 집중하고, 활성 사용자 이메일 read 규칙은 user read 경계에 모아두는 편이 PII 접근 규칙을 한 곳에서 유지하기 쉽다.

## 485) `PolicyDetailReadService`, `RetrievalService`, `RuleScoringService` 가 태그 조회를 위해 각자 `ServiceTagRepository` 를 직접 보면, tag read 규칙이 policy/recommend 경계에 다시 흩어진다
- 문제: 정책 상세 aggregate 조회, 추천 후보 후처리, rule scoring 후보 태그 로딩이 모두 `ServiceTagRepository.findByServiceId...` 를 직접 호출하고 있었다.
- 해결: `PolicyTagReadRepository` 를 추가하고, policy/recommend read 경로의 태그 조회를 이 경계 뒤로 이동했다.
- 이유: collect 쪽은 tag write를 그대로 유지하되, 태그 read 구현 선택은 별도 read repository 에 모아야 policy/recommend 서비스가 JPA 저장소 선택과 grouping 세부사항을 직접 들지 않게 된다.

## 486) `BokjiroDetailCollectService` 가 detail 저장 후 relevance refresh 를 위해 `ServiceTagRepository` 로 태그를 다시 읽으면, collect 서비스가 저장 이후 read 구현까지 떠안게 된다
- 문제: 상세 저장이 끝난 뒤 `searchYouthRelevanceService.refreshForService(service, serviceTagRepository.findByServiceId(...))` 형태로 collect 서비스가 태그 조회까지 직접 수행하고 있었다.
- 해결: `SearchYouthRelevanceReadRepository` 에 단건 태그 조회를 추가하고, `SearchYouthRelevanceService.refreshForService(service)` 가 내부에서 태그를 읽어 재계산하도록 정리했다.
- 이유: collect 서비스는 detail 저장 orchestration 에 집중하고, youth relevance 재계산에 필요한 태그 read 규칙은 relevance 경계 안에 두는 편이 read/write 책임이 더 분명하다.

## 487) `PolicyDetailReadService` 가 detail/region/tag 저장소 3개를 직접 조합하면, 정책 상세 aggregate read 경계가 서비스 안에 묻혀 저장소 조합 책임이 다시 분산된다
- 문제: `getAggregate(serviceId)` 는 `WelfareServiceDetailRepository`, `ServiceRegionRepository`, `PolicyTagReadRepository` 를 서비스 본문에서 직접 호출해 aggregate 를 만들고 있었다.
- 해결: `PolicyDetailReadRepository` 를 추가하고, 상세 aggregate 조립을 repository 구현으로 이동했다.
- 이유: 서비스는 상세 응답 orchestration 에 집중하고, 여러 저장소를 묶는 read 조합 책임은 별도 read repository 에 두는 편이 policy read 경계를 더 일관되게 유지한다.

## 488) `UserPiiSyncStatusService`, `UserPiiSyncReplayService` 가 queue 상태 조회와 replay 대상 선정을 위해 `UserPiiSyncQueueRepository` 를 직접 보면, queue access 경계가 다시 status/replay 서비스로 퍼진다
- 문제: 상태 집계(count/oldest/latest/failed samples)와 replay 대상 조회(failed 우선, pending 보충)를 각 서비스가 저장소 질의 형태로 직접 알고 있었다.
- 해결: `UserPiiSyncQueueService` 에 count/status snapshot/failed sample/replay user key 조회 메서드를 추가하고, status/replay 서비스는 이 경계로만 읽게 정리했다.
- 이유: queue row 생성뿐 아니라 queue 상태 read 정책도 같은 service 경계에 모아야 user pii sync 흐름의 저장소 접근 규칙을 한 곳에서 유지하기 쉽다.

## 489) `PolicyRankingService` 가 rankable policy 목록은 read repository를 쓰면서 unique view 집계는 `ServiceViewLogRepository` 를 직접 보면, 랭킹 read 경계가 절반만 묶인 상태로 남는다
- 문제: `getRanking(...)` 은 랭킹 대상 정책은 `PolicyRankingReadRepository` 로 읽으면서도, 7일 고유 조회수 집계는 별도 `ServiceViewLogRepository.findUniqueViewCountsSince(...)` 를 직접 호출하고 있었다.
- 해결: unique view 집계 조회를 `PolicyRankingReadRepository.findUniqueViewCountsSince(...)` 로 이동하고, 랭킹 서비스는 같은 read 경계만 사용하게 정리했다.
- 이유: 랭킹 계산 서비스는 score 계산과 exploration slot 전략에 집중하고, 랭킹에 필요한 persistence 조합은 같은 read repository 안에 두는 편이 경계가 더 일관된다.

## 490) `UserReadService.getProfile(...)` 가 profile/pii/attribute/priority 저장소를 직접 조합하면, 사용자 프로필 aggregate read 규칙이 service 본문에 묻혀 책임이 너무 넓어진다
- 문제: 프로필 조회는 `UserProfileRepository`, `UserPiiReadWriteRepository`, `UserAttributeRepository`, `UserPriorityRepository` 를 service 본문에서 직접 호출해 aggregate 를 만들고 있었다.
- 해결: `UserProfileReadRepository` 와 `UserProfileAggregateReadModel` 을 추가하고, `getProfile(...)` 의 aggregate 조회를 이 read 경계로 이동했다.
- 이유: 사용자 읽기 서비스는 active user 검증과 응답 조립에 집중하고, 여러 저장소를 묶는 프로필 aggregate read 조합은 별도 read repository 에 두는 편이 SRP와 read 경계 일관성에 낫다.

## 491) `UserReadService.getRecommendationContext(...)` 가 profile/attribute/priority 저장소를 직접 조합하면, 추천용 사용자 snapshot read 규칙도 service 본문에 묻혀 read 결합이 다시 넓어진다
- 문제: 추천 snapshot 조회는 `UserProfileRepository`, `UserAttributeRepository`, `UserPriorityRepository` 를 service 본문에서 직접 호출해 `RecommendationUserSnapshot` 입력 aggregate 를 조합하고 있었다.
- 해결: `RecommendationUserReadRepository` 와 `RecommendationUserReadModel` 을 추가하고, `getRecommendationContext(...)` 의 추천용 aggregate 조회를 이 read 경계로 이동했다.
- 이유: 사용자 읽기 서비스는 active user 검증과 snapshot 조립에 집중하고, 추천용 profile/attribute/priority read 조합은 별도 repository 로 내려야 read 경계가 더 일관되고 테스트도 단순해진다.

## 492) `UserPiiBackfillService` 가 missing state 조회와 legacy source 조회를 서로 다른 저장소에서 직접 조합하면, PII 백필 read 규칙이 service 본문에 남아 책임이 다시 넓어진다
- 문제: 백필 서비스는 `UserPiiReadWriteRepository.findMissingEncryptedFields()` 와 `UserRepository.findPiiBackfillSourcesByUserKeys(...)` 를 직접 묶어 state/source 조합을 만들고 있었다.
- 해결: `UserPiiBackfillReadRepository` 를 추가하고, missing state 조회와 legacy source lookup 을 이 read 경계로 이동했다.
- 이유: PII 백필 서비스는 암호화/backfill 정책과 결과 집계에 집중하고, 여러 저장소를 묶는 read 조합은 별도 repository 로 내리는 편이 user backfill 경계를 더 일관되게 유지한다.

## 493) `UserMetadataUserKeyBackfillService` 가 attribute/priority 저장소의 count/update 를 service 본문에서 직접 병렬 조합하면, metadata 백필 write 규칙이 service 안에 남아 책임이 다시 넓어진다
- 문제: metadata user_key 백필은 `UserAttributeRepository`, `UserPriorityRepository` 의 count/update 메서드를 service 본문에서 직접 각각 호출하고 있었다.
- 해결: `UserMetadataBackfillRepository` 를 추가하고, missing count 와 backfill update 조합을 이 repository 경계로 이동했다.
- 이유: metadata 백필 서비스는 처리량 집계와 로깅에 집중하고, attribute/priority 저장소를 함께 다루는 backfill 규칙은 별도 repository 로 내리는 편이 user backfill 경계를 더 일관되게 유지한다.

## 494) `UserReadService.getNotificationTargets(...)` 가 notification target row 조회와 encrypted email bulk lookup 을 service 본문에서 직접 조합하면, 알림 대상 read 규칙이 사용자 읽기 서비스 안에 남아 책임이 다시 넓어진다
- 문제: period별 알림 대상 조회는 `UserProfileRepository.findNotificationTargetsByPeriod(...)` 와 `NotificationPiiReadRepository.findEncryptedEmailsByUserKeys(...)` 를 service 본문에서 직접 묶어 aggregate 를 만들고 있었다.
- 해결: `NotificationTargetReadRepository` 와 `NotificationTargetAggregateReadModel` 을 추가하고, bulk notification target read 조합을 이 repository 경계로 이동했다.
- 이유: 사용자 읽기 서비스는 active-user 검증과 응답 변환에 집중하고, 알림 대상 row/email 조합은 별도 read repository 로 내려야 notification read 경계가 더 일관되고 재사용도 쉬워진다.

## 495) `UserReadService.getNotificationEmailByUserKey(...)` 가 단건 encrypted email lookup만 따로 `NotificationPiiReadRepository` 를 직접 보면, 같은 notification read 규칙이 bulk/단건 경로로 다시 찢어진다
- 문제: bulk 알림 대상 조회는 이미 `NotificationTargetReadRepository` 뒤로 옮겼는데, 단건 이메일 조회는 여전히 `NotificationPiiReadRepository.findEncryptedEmailByUserKey(...)` 를 service 본문에서 직접 호출하고 있었다.
- 해결: `NotificationTargetReadRepository` 에 `findEncryptedEmailByUserKey(...)` 를 추가하고, 단건 notification email read 도 같은 경계로 통일했다.
- 이유: 알림 대상 row/email read 규칙은 bulk/단건을 같은 read repository 에 모아야 notification read policy 변경 시 영향 범위가 작고 일관성도 유지된다.

## 496) `UserProfileCommandService` 와 `UserAccountCommandService` 가 attribute/priority 저장소를 직접 조합하면, metadata write 규칙이 profile 수정/우선순위 저장/탈퇴 경로마다 다시 퍼진다
- 문제: 프로필 수정은 관심분야/대상유형 교체 저장을 직접 처리하고, 우선순위 저장은 priority delete+save 를 직접 수행했으며, 탈퇴도 attribute/priority 삭제를 각각 직접 호출하고 있었다.
- 해결: `UserMetadataCommandRepository` 를 추가하고, attribute replace / priority replace / metadata delete-all / 관심분야 존재 확인을 같은 write 경계로 모았다.
- 이유: metadata 조작 규칙은 profile/account command 서비스에서 중복으로 들고 있기보다, 별도 command repository 에 모아야 변경 영향 범위가 줄고 테스트도 단순해진다.

## 497) `UserReadService` 가 알림 대상 조회와 알림 이메일 조회까지 함께 들고 있으면, 사용자 일반 read 와 notification read 책임이 한 서비스에 다시 섞인다
- 문제: `UserReadService` 는 profile/snapshot/active-user read 외에 `getNotificationTargets(...)`, `getNotificationEmailByUserKey(...)` 같은 notification read 메서드도 같이 갖고 있었고, `NotificationService`, `PasswordResetService` 가 이를 직접 사용하고 있었다.
- 해결: `UserNotificationReadService` 를 추가하고, 알림 대상/알림 이메일 read 메서드를 이 서비스로 이동했다. `NotificationService`, `PasswordResetService` 도 전용 읽기 경계로 교체했다.
- 이유: active-user/profile/snapshot 읽기와 notification target/email 읽기는 바뀌는 이유가 다르므로, 분리해야 `UserReadService` 책임이 가벼워지고 notification read 정책 변경도 독립적으로 다루기 쉽다.

## 498) `AuthService` 가 auth identity read 와 signup write 를 같이 들면, 인증 orchestration 과 lookup hash 규칙/신규 사용자 저장 규칙이 한 서비스에 다시 섞인다
- 문제: 이메일 중복 확인, 로그인 대상 lookup 은 `auth_users` lookup hash 규칙에 묶여 있고, 회원가입은 신규 `User` 저장과 `UserCoreSyncService` 호출까지 같이 들고 있었다. 이 상태면 이메일 식별 규칙이나 signup 저장 흐름이 바뀔 때 `AuthService` 자체를 계속 수정해야 한다.
- 해결: `AuthIdentityReadService` 를 추가해 `existsByEmail(...)`, `findByEmail(...)` 로 auth identity read 규칙을 모으고, `UserRegistrationService` 를 추가해 신규 사용자 저장과 core sync 를 별도 command 경계로 이동했다. `PasswordResetService` 의 auth_users 이메일 lookup 도 같은 read 경계로 통일했다.
- 이유: 인증 서비스는 admin email 정책, 비밀번호 검증, 토큰 발급 같은 orchestration 에 집중하고, auth identity 조회/hash 규칙과 signup write 는 별도 경계로 내리는 편이 SRP와 테스트 격리에 더 낫다.

## 499) `UserReadService` 가 auth_users active 상태 검증까지 직접 들면, 사용자 일반 read 와 auth identity read 책임이 다시 섞인다
- 문제: `UserReadService` 는 active userKey 검증을 위해 `AuthUserRepository.findByUserKey(...)` 를 직접 호출하고 있었고, 이미 aggregate read를 read repository 뒤로 옮긴 뒤에도 auth projection read 세부사항이 서비스 안에 남아 있었다. 게다가 이전 구조 변경 뒤에는 실제로 쓰지 않는 `UserProfileRepository`, `UserPiiReadWriteRepository` 직접 의존도 그대로 남아 있었다.
- 해결: `AuthIdentityReadService.requireActiveUserKey(...)` 를 추가하고, `UserReadService.resolveActiveUserKey(...)` 를 이 read 경계로 교체했다. 함께 남아 있던 불필요한 `AuthUserRepository`, `UserProfileRepository`, `UserPiiReadWriteRepository` 직접 의존도 제거했다.
- 이유: 사용자 읽기 서비스는 active user orchestration과 응답 조립에 집중하고, auth projection 조회/활성 상태 검증은 auth identity read 경계로 모아야 read 책임이 더 일관되고 생성자 의존도도 줄어든다.

## 500) `UserPiiBackfillService` 와 `UserPiiSyncProcessor` 가 같은 `UserPiiReadWriteRepository` write를 직접 두드리면, app PII 쓰기 규칙이 backfill/sync 경로마다 다시 퍼진다
- 문제: 백필 서비스는 `backfillEncryptedFields(...)` 를, sync processor는 `upsertUserPii(...)` 를 각각 직접 호출하고 있었다. 같은 `user_pii` write라도 경로마다 저장소 직접 의존이 남아 있어 write 정책을 공통 경계로 다루기 어려웠다.
- 해결: `UserPiiCommandService` 를 추가하고, app PII upsert/backfill/delete write를 이 서비스로 모았다. `UserPiiBackfillService`, `UserPiiSyncProcessor` 는 이제 write 구현 대신 user pii command 경계에만 의존한다.
- 이유: backfill/sync 같은 별도 작업 서비스는 암호화/큐 상태/집계에 집중하고, app PII write는 한 command 경계로 모아야 변경 영향 범위가 줄고 테스트도 단순해진다.

## 501) `UserCoreProjectionSyncService` 가 auth/profile projection 저장소 둘을 직접 들고 있으면, core sync orchestration과 projection upsert 조합 책임이 다시 한 서비스에 섞인다
- 문제: `UserCoreProjectionSyncService` 는 `AuthUserRepository`, `UserProfileRepository` 를 직접 들고 `find-or-create + syncFrom + save` 패턴을 각각 수행하고 있었다. 이 상태면 core sync 흐름을 바꾸지 않아도 projection 저장 규칙이 바뀔 때 서비스 본문을 다시 열어야 한다.
- 해결: `UserCoreProjectionCommandRepository` / `UserCoreProjectionCommandRepositoryImpl` 을 추가하고, auth/profile projection upsert 조합을 이 command repository 뒤로 이동했다. `UserCoreProjectionSyncService` 는 email hash 계산과 projection sync orchestration만 남겼다.
- 이유: core sync 서비스는 age 계산과 projection sync 흐름에 집중하고, 여러 저장소를 묶는 projection upsert 조합은 별도 command repository 로 내려야 책임이 더 선명하고 테스트도 분리하기 쉽다.

## 502) `UserKeyLookupService` 와 `PolicyLookupService` 같은 얇은 lookup 서비스가 저장소를 직접 들면, lookup 규칙 자체를 별도 read 경계로 독립시키기 어렵다
- 문제: `UserKeyLookupService` 는 `UserRepository.findUserKeyById(...)` 를, `PolicyLookupService` 는 `WelfareServiceRepository.findById(...)` 를 직접 호출하고 있었다. 지금은 단순 조회처럼 보여도 lookup 규칙이 바뀌면 서비스 본문을 수정해야 하고, 얇은 서비스여도 저장소 결합이 그대로 남는다.
- 해결: `UserKeyReadRepository` / `UserKeyReadRepositoryImpl`, `PolicyLookupReadRepository` / `PolicyLookupReadRepositoryImpl` 을 추가하고, 두 lookup 서비스가 이 read repository 뒤로만 의존하게 정리했다.
- 이유: 단건 lookup도 read policy의 일부이므로, 얇은 서비스라도 repository 직접 의존을 줄여 두면 service는 예외 정책에만 집중하고 lookup 구현은 별도 경계에서 관리할 수 있다.

## 503) `UserReadService` 와 `UserRegistrationService` 가 `UserRepository` 를 직접 들면, active user read 와 신규 사용자 저장 규칙이 service 본문에 다시 남는다
- 문제: `UserReadService` 는 active user entity 조회를 위해 `findById(...)`, `findByUserKey(...)` 를 직접 호출했고, `UserRegistrationService` 는 신규 `User` 저장을 위해 `save(...)` 를 직접 호출하고 있었다. 다른 lookup/read/write 경계를 분리한 뒤에도 users 테이블 접근 규칙 일부가 서비스 본문에 남아 있었다.
- 해결: `UserAccountReadRepository` / `UserAccountReadRepositoryImpl`, `UserRegistrationCommandRepository` / `UserRegistrationCommandRepositoryImpl` 을 추가하고, `UserReadService` 와 `UserRegistrationService` 가 이 경계들에만 의존하도록 정리했다.
- 이유: active user 조회와 신규 저장도 users 테이블 접근 policy의 일부이므로, service는 활성 상태 검증과 registration orchestration에 집중하고 실제 저장소 호출은 별도 경계로 내리는 편이 일관된다.

## 504) `PolicyViewLogService` 와 `PolicySearchLogService` 가 로그 저장소를 직접 두드리면, policy 서비스 안에 dedup/save 조합과 JPA reference 생성 같은 저장 세부사항이 다시 남는다
- 문제: 조회 로그 서비스는 dedup exists 조회와 `EntityManager.getReference(...) + save(...)` 를 직접 수행했고, 검색 로그 서비스도 `SearchLogRepository.save(...)` 를 직접 호출하고 있었다. 이 상태면 로그 저장 규칙이나 저장 방식이 바뀔 때 policy 서비스 본문을 다시 열어야 한다.
- 해결: `PolicyViewLogCommandRepository` / `PolicyViewLogCommandRepositoryImpl`, `PolicySearchLogCommandRepository` / `PolicySearchLogCommandRepositoryImpl` 을 추가하고, 두 서비스가 로그 저장/dedup 구현을 command repository 뒤로 위임하게 정리했다.
- 이유: policy 서비스는 userKey 해석, dedup 정책, 예외 처리 같은 orchestration 에 집중하고, 로그 저장 구현은 별도 command 경계로 내려야 책임이 더 선명하고 테스트도 분리하기 쉽다.

## 505) `RecommendationBookmarkCommandService` 가 추천 row 조회·count·placeholder 저장을 `UserRecommendationRepository` 에 직접 묶어 두면, 북마크 규칙과 persistence 조합 책임이 다시 한 서비스에 섞인다
- 문제: 북마크 토글 서비스는 소유 추천 조회, 서비스별 최신 추천 조회, 북마크 수 제한 확인, placeholder 저장을 모두 `UserRecommendationRepository` 로 직접 수행하고 있었다. 이 상태면 북마크 command 규칙은 바꾸지 않아도 추천 row lookup/save 방식이 달라질 때 서비스 본문을 다시 열어야 한다.
- 해결: `RecommendationBookmarkCommandRepository` / `RecommendationBookmarkCommandRepositoryImpl` 을 추가하고, owned recommendation lookup, latest recommendation lookup, bookmark count, placeholder save 를 command 경계 뒤로 이동했다.
- 이유: 북마크 command 서비스는 userKey 해석, limit enforcement, placeholder 필요 여부 같은 orchestration 에 집중하고, 추천 row 조회/저장 세부사항은 별도 command repository 로 내려야 recommendation command 경계가 더 선명해진다.

## 506) `RecommendationPersistenceService` 와 `RecommendationRetentionService` 가 추천 row 교체 저장/정리 삭제를 `UserRecommendationRepository` 에 직접 묶어 두면, 추천 저장 orchestration 과 persistence 교체 규칙이 다시 한 서비스 안에 섞인다
- 문제: 추천 저장 서비스는 기존 최신 추천 조회, 북마크 상태 보존 뒤 전체 삭제, 새 추천 일괄 저장을 직접 수행했고, retention 서비스도 만료 미북마크 삭제를 저장소에 직접 호출하고 있었다. 이 상태면 추천 저장/정리 규칙이 바뀔 때 서비스 본문을 다시 열어야 한다.
- 해결: `RecommendationPersistenceCommandRepository` / `RecommendationPersistenceCommandRepositoryImpl` 을 추가하고, latest recommendation lookup, user별 전체 교체 저장, retention 삭제를 command 경계 뒤로 이동했다.
- 이유: 추천 저장 서비스는 북마크 상태 이전과 recommendation row 구성 같은 orchestration 에 집중하고, 교체 저장/정리 삭제 세부사항은 별도 command repository 로 내려야 recommendation write 경계가 더 일관된다.

## 507) `RecommendationLogService` 와 `ScoreWeightService` 가 recommendation log 저장소를 직접 나눠 쓰면, 알림 로그 write 규칙과 cold-start read 규칙이 서비스 본문에 다시 퍼진다
- 문제: recommendation log 서비스는 미클릭 로그 삭제, 로그 저장, 단건 조회, 최신 logId 매핑 조회를 직접 수행했고, score weight 서비스는 전체 로그 수를 저장소에서 직접 읽고 있었다. 이 상태면 로그 저장/조회 정책이나 cold-start 기준 read 규칙이 바뀔 때 서비스 둘을 함께 다시 열어야 한다.
- 해결: `RecommendationLogCommandRepository` / `RecommendationLogCommandRepositoryImpl`, `RecommendationLogReadRepository` / `RecommendationLogReadRepositoryImpl` 을 추가하고, recommendation log write/read 를 각각 이 경계 뒤로 이동했다.
- 이유: recommendation log 서비스는 로그 조립과 click 처리 orchestration 에 집중하고, cold-start/logId mapping/log deletion 같은 persistence 세부사항은 read/command 경계로 분리해야 recommendation log 책임이 더 선명해진다.

## 508) `ChatMessageService` 와 `ChatMessageCommandService` 가 세션 소유 확인, 메시지 목록/최근 조회, append write를 각각 `ChatSessionRepository` 와 `ChatMessageRepository` 에 직접 걸치면, chat read/write 규칙이 서비스 둘에 다시 퍼진다
- 문제: 메시지 조회 서비스는 소유 세션 확인과 메시지 목록/최근 메시지 조회를 직접 수행했고, command 서비스는 세션 조회, 제목 갱신, assistant append, lastMessageAt touch 를 직접 처리하고 있었다. 이 상태면 chat session/message persistence 조합이 바뀔 때 서비스 둘을 함께 다시 열어야 한다.
- 해결: `ChatMessageReadRepository` / `ChatMessageReadRepositoryImpl`, `ChatMessageCommandRepository` / `ChatMessageCommandRepositoryImpl` 을 추가하고, 세션 소유 확인과 메시지 read 는 read repository로, append write 와 session touch 는 command repository로 이동했다.
- 이유: chat 서비스는 rate limit, AI orchestration, response composition 에 집중하고, session/message persistence 조합은 read/command 경계로 분리해야 chat 책임이 더 선명해진다.

## 509) `ChatSessionService` 와 `ChatSessionCleanupService` 가 세션 목록/소유 확인/생성/삭제를 `ChatSessionRepository` 에 직접 걸치면, 세션 read/write 규칙이 서비스 본문에 다시 남는다
- 문제: 세션 서비스는 최근 세션 목록 조회, 소유 세션 확인, 신규 세션 저장, 삭제를 직접 수행했고, cleanup 서비스도 userKey 기준 전체 삭제를 저장소에 직접 호출하고 있었다. 이 상태면 세션 persistence 조합이 바뀔 때 서비스 둘을 다시 열어야 한다.
- 해결: `ChatSessionReadRepository` / `ChatSessionReadRepositoryImpl`, `ChatSessionCommandRepository` / `ChatSessionCommandRepositoryImpl` 을 추가하고, 세션 목록/소유 확인은 read repository로, 세션 생성/삭제/cleanup delete 는 command repository로 이동했다.
- 이유: chat session 서비스는 active user 검증과 제목 정규화 같은 orchestration 에 집중하고, 세션 persistence 세부사항은 read/command 경계로 분리해야 책임이 더 선명해진다.

## 510) `RecommendationReadFacade` 가 북마크 최신 조회와 canonical projection 조회를 `UserRecommendationRepository`, `CanonicalRecommendationReadModelRepository` 에 직접 걸치면, recommendation summary read 조합이 facade 본문에 남는다
- 문제: recommendation read facade는 북마크 service id 조회, canonical projection 조회, 최신 북마크 recommendation 조회를 서로 다른 저장소에 직접 걸쳐 조합하고 있었다. 이 상태면 summary projection/북마크 read 조합이 바뀔 때 facade 본문을 다시 열어야 한다.
- 해결: `RecommendationSummaryReadRepository` / `RecommendationSummaryReadRepositoryImpl` 을 추가하고, 북마크 service id 조회, canonical projection 조회, 최신 북마크 recommendation 조회를 이 read 경계 뒤로 이동했다.
- 이유: recommendation read facade는 userKey 해석과 summary 응답 조립에 집중하고, recommendation summary read 조합은 별도 repository로 내려야 facade 책임이 더 선명해진다.

## 511) `RecommendationFacade` 가 refresh cache 재사용용 최신 저장 추천 조회와 API 응답용 top recommendation 조회를 `UserRecommendationRepository` 에 직접 걸치면, 결과 목록 read 규칙이 파이프라인 facade 본문에 남는다
- 문제: recommendation facade는 refresh cache hit 시 최신 저장 추천을 직접 읽고, `getRecommendations(...)` 도 top recommendation 목록을 저장소에서 직접 읽고 있었다. 이 상태면 추천 파이프라인 자체를 바꾸지 않아도 결과 목록 read 규칙이 달라질 때 facade 본문을 다시 열어야 한다.
- 해결: `RecommendationResultReadRepository` / `RecommendationResultReadRepositoryImpl` 을 추가하고, 최신 저장 추천 조회와 top recommendation 목록 조회를 이 read 경계 뒤로 이동했다.
- 이유: recommendation facade는 refresh cache reuse 판단과 추천 파이프라인 orchestration에 집중하고, 결과 목록 read 조합은 별도 repository로 내려야 facade 책임이 더 선명해진다.

## 512) `ScoreWeightService` 가 `score_weights` 조회를 `ScoreWeightRepository` 에 직접 걸치면, cold-start stage 계산과 가중치 설정 read 규칙이 한 서비스 안에 다시 섞인다
- 문제: score weight 서비스는 recommendation log 수 읽기는 이미 read repository로 뺐지만, 활성 가중치 목록 조회는 여전히 `ScoreWeightRepository.findByIsActiveTrueOrderByMinLogCountAsc()` 를 직접 호출하고 있었다. 이 상태면 cold-start stage 계산 자체를 바꾸지 않아도 가중치 설정 read 규칙이 달라질 때 서비스 본문을 다시 열어야 한다.
- 해결: `ScoreWeightReadRepository` / `ScoreWeightReadRepositoryImpl` 을 추가하고, 활성 가중치 목록 조회를 이 read 경계 뒤로 이동했다.
- 이유: score weight 서비스는 stage 계산과 progress resolution에 집중하고, 가중치 설정 read 규칙은 별도 repository로 내려야 책임이 더 선명해진다.

## 513) `UserProfileCommandService` 가 우선순위 옵션 code lookup을 `PriorityOptionRepository` 에 직접 걸치면, profile command와 option validation read 규칙이 한 서비스 안에 다시 섞인다
- 문제: 프로필 command 서비스는 관심분야/대상유형/우선순위 저장 orchestration을 맡으면서도, 우선순위 code 검증을 위해 `PriorityOptionRepository.findByCode(...)` 를 직접 호출하고 있었다. 이 상태면 우선순위 옵션 read 규칙이 바뀔 때 command 서비스 본문을 다시 열어야 한다.
- 해결: `PriorityOptionReadRepository` / `PriorityOptionReadRepositoryImpl` 을 추가하고, 우선순위 option code lookup을 이 read 경계 뒤로 이동했다.
- 이유: profile command 서비스는 active user 검증, refresh cache evict, priority row 조립에 집중하고, option validation read 규칙은 별도 repository로 내려야 책임이 더 선명해진다.

## 514) `UserPiiSyncQueueService` 가 queue 저장과 상태/재처리 대상 조회를 모두 `UserPiiSyncQueueRepository` 하나에 직접 걸치면, user pii sync queue의 read/write 규칙이 한 서비스 안에 다시 섞인다
- 문제: queue 서비스는 enqueue 저장, 상태별 count, oldest/latest snapshot, failed sample, replay 대상 userKey 조회를 모두 하나의 JPA 저장소에 직접 걸고 있었다. 이 상태면 queue 저장 규칙과 read 정렬/샘플링 규칙이 바뀔 때 같은 서비스 본문을 함께 다시 열어야 한다.
- 해결: `UserPiiSyncQueueReadRepository` / `UserPiiSyncQueueReadRepositoryImpl`, `UserPiiSyncQueueCommandRepository` / `UserPiiSyncQueueCommandRepositoryImpl` 을 추가하고, queue 저장은 command repository로, 상태/재처리 대상 조회는 read repository로 이동했다.
- 이유: `UserPiiSyncQueueService` 는 enqueue orchestration과 queue API 표면 유지에 집중하고, queue persistence 세부사항은 read/write 경계로 분리해야 책임이 더 선명해진다.

## 515) `AuthIdentityReadService` 가 auth user email lookup hash 조회와 userKey 조회를 `AuthUserRepository` 에 직접 걸치면, auth identity read 규칙이 서비스 본문에 다시 남는다
- 문제: identity read 서비스는 이메일 중복 확인, 이메일 lookup hash 조회, userKey 조회를 위해 `AuthUserRepository` 를 직접 호출하고 있었다. 이 상태면 auth identity lookup 규칙이 바뀔 때 service 본문을 다시 열어야 한다.
- 해결: `AuthIdentityReadRepository` / `AuthIdentityReadRepositoryImpl` 을 추가하고, email lookup hash 존재/조회와 userKey 조회를 이 read 경계 뒤로 이동했다.
- 이유: `AuthIdentityReadService` 는 raw email 정규화와 active 상태 검증 같은 identity read orchestration 에 집중하고, auth identity persistence 세부사항은 별도 read repository 로 내려야 책임이 더 선명해진다.

## 516) `UserPiiCommandService` 가 app PII backfill/upsert/delete write를 `UserPiiReadWriteRepository` 에 직접 걸치면, PII write 규칙이 service 본문에 다시 남는다
- 문제: user pii command 서비스는 이미 backfill/sync/withdraw 경로의 write 진입점 역할을 하면서도, 실제 app PII backfill/upsert/delete 를 위해 `UserPiiReadWriteRepository` 를 직접 호출하고 있었다. 이 상태면 PII write 규칙이 바뀔 때 service 본문을 다시 열어야 한다.
- 해결: `UserPiiCommandRepository` / `UserPiiCommandRepositoryImpl` 을 추가하고, app PII backfill/upsert/delete write를 이 command 경계 뒤로 이동했다.
- 이유: `UserPiiCommandService` 는 PII write orchestration 에 집중하고, JDBC 기반 app PII persistence 세부사항은 별도 command repository 로 내려야 책임이 더 선명해진다.

## 517) `JpaClusterAiScoreCache` 와 `StatusUpdateService` 가 `ClusterAiResultRepository` 를 직접 걸치면, cluster AI cache read/write 규칙이 서비스 둘에 다시 퍼진다
- 문제: 추천 cache 서비스는 clusterId 기준 cache row 조회와 신규 row 저장을 직접 수행했고, 상태 업데이트 서비스는 만료 cluster cache 삭제를 같은 저장소에 직접 호출하고 있었다. 이 상태면 군집 AI cache persistence 규칙이 바뀔 때 추천/수집 서비스 둘을 함께 다시 열어야 한다.
- 해결: `ClusterAiResultReadRepository` / `ClusterAiResultReadRepositoryImpl`, `ClusterAiResultCommandRepository` / `ClusterAiResultCommandRepositoryImpl` 을 추가하고, cache row 조회는 read repository로, 신규 row 저장과 만료 cache 삭제는 command repository로 이동했다.
- 이유: `JpaClusterAiScoreCache` 는 score map 조립과 upsert orchestration에, `StatusUpdateService` 는 상태 전이와 TTL cleanup orchestration에 집중하고, cluster cache persistence 세부사항은 read/write 경계로 분리해야 책임이 더 선명해진다.

## 518) `UserReadService` 가 profile aggregate read, PII 복호화, recommendation snapshot read, active user 조회를 함께 들면, 일반 사용자 읽기 책임이 다시 넓어진다
- 문제: 사용자 읽기 서비스는 profile aggregate 조회와 PII 복호화까지 직접 처리하면서, 동시에 recommendation snapshot 조합과 active user/account 조회도 함께 맡고 있었다. 이 상태면 프로필 응답 조립 규칙이 바뀔 때도 `UserReadService` 본문을 다시 열어야 한다.
- 해결: `UserProfileReadService` 를 추가하고, profile aggregate read와 PII 복호화를 이 전용 서비스로 이동했다. `UserService.getProfile(...)` 도 새 profile read 경계를 직접 사용하도록 정리했다.
- 이유: `UserReadService` 는 active user/account 조회와 recommendation context read에 집중하고, profile 응답 조립과 복호화는 별도 read service 로 분리해야 책임이 더 선명해진다.

## 519) `UserReadService` 가 recommendation snapshot 조합까지 계속 들면, active user/account 조회와 추천용 aggregate read 규칙이 다시 한 서비스에 섞인다
- 문제: profile read를 분리한 뒤에도 `UserReadService` 는 여전히 active user/account 조회와 함께 recommendation snapshot 조합, target/interest/priority projection 변환을 직접 수행하고 있었다. 이 상태면 추천용 snapshot 규칙이 바뀔 때도 일반 사용자 read 서비스 본문을 다시 열어야 한다.
- 해결: `UserRecommendationReadService` 를 추가하고, recommendation snapshot 조합과 `RecommendationReadContext` 생성을 이 전용 read 서비스로 이동했다. `RecommendationFacade` 도 새 recommendation read 경계를 직접 사용하도록 정리했다.
- 이유: `UserReadService` 는 active user/account 조회와 userKey 해석에 집중하고, 추천용 aggregate read/변환은 별도 read service 로 분리해야 책임이 더 선명해진다.

## 520) `UserReadService` 가 active user/account 조회까지 계속 직접 들면, 일반 user lookup 과 active user 검증 책임이 다시 한 서비스에 섞인다
- 문제: recommendation/profile read를 분리한 뒤에도 `UserReadService` 는 여전히 `UserAccountReadRepository` 를 직접 들고 active user entity 조회, active userKey 보정, userKey 기준 active user 검증을 직접 수행하고 있었다. 이 상태면 active user/account 조회 규칙이 바뀔 때도 일반 user read 서비스 본문을 다시 열어야 한다.
- 해결: `ActiveUserReadService` 를 추가하고, active user entity 조회와 active userKey 보정, userKey 기준 active user 검증을 이 전용 read 서비스로 이동했다. `UserReadService` 는 기존 API 표면을 유지하되 새 active read 경계에 위임만 하도록 축소했다.
- 이유: `UserReadService` 는 기존 호출부 호환과 userKey 존재 확인 같은 얇은 facade 역할에 집중하고, active user/account 조회는 별도 read service 로 분리해야 책임이 더 선명해진다.

## 521) active-user consumer들이 계속 `UserReadService` wrapper를 거치면, 실제 active user read 경계를 분리해도 호출부 결합은 그대로 남는다
- 문제: `ActiveUserReadService` 를 만든 뒤에도 chat, notification, auth, user profile/account/bookmark 쪽 서비스들은 여전히 `UserReadService.getActiveUserContext(...)`, `getActiveUserByUserKey(...)`, `findOptionalActiveUserByUserKey(...)` 를 통해 active user 조회를 우회 호출하고 있었다. 이 상태면 active user read 규칙이 바뀔 때 wrapper와 소비자 의존이 함께 남아 분리 효과가 약해진다.
- 해결: active-user 메서드만 쓰던 소비자들을 `ActiveUserReadService` 직접 의존으로 전환하고, `UserReadService` 는 admin 강제 로그아웃에서 쓰는 `requireExistingUserIdByUserKey(...)` 만 남기는 얇은 facade로 축소했다.
- 이유: active user 검증/조회는 `ActiveUserReadService` 에 바로 모아야 read 경계가 실제로 드러나고, `UserReadService` 는 일반 lookup facade 역할만 유지하는 편이 책임이 더 선명하다.

## 522) `UserReadService` 가 결국 admin forced-logout용 `userKey -> userId` 래퍼만 남으면, 별도 서비스로 유지할 이유가 없다
- 문제: active-user consumer를 모두 `ActiveUserReadService` 로 옮긴 뒤 `UserReadService` 에 남은 책임은 `requireExistingUserIdByUserKey(...)` 하나뿐이었다. 이 상태면 controller가 한 단계 더 우회 호출만 하게 되고, 테스트도 쓸모없는 래퍼 mock을 계속 유지해야 한다.
- 해결: `UserKeyLookupService` 에 `requireExistingUserIdByUserKey(...)` 를 직접 추가하고, `UserAdminController` 와 `AdminSecurityWebMvcTest` 를 이 경계로 전환했다. 그 뒤 `UserReadService` 와 전용 테스트는 삭제했다.
- 이유: `userKey -> userId` 존재 검증은 lookup service 하나면 충분하고, 의미 없는 wrapper를 남기지 않는 편이 경계가 더 선명하다.

## 523) `NotificationService` 가 재시도 대상 조회를 `NotificationRepository` 에 직접 걸치면, 알림 발송 orchestration과 retry read 규칙이 다시 한 서비스에 섞인다
- 문제: 알림 서비스는 발송 orchestration을 맡으면서도 `FAILED + nextRetryAtBefore(now)` 조회를 위해 `NotificationRepository` 를 직접 호출하고 있었다. 이 상태면 retry polling 기준이 바뀔 때 발송 서비스 본문을 다시 열어야 한다.
- 해결: `NotificationRetryReadRepository` / `NotificationRetryReadRepositoryImpl` 을 추가하고, 재시도 대상 조회를 이 read 경계 뒤로 이동했다.
- 이유: `NotificationService` 는 대상 발송과 재시도 scheduling orchestration에 집중하고, retry polling read 규칙은 별도 repository 로 내려야 책임이 더 선명하다.

## 524) `NotificationHistoryService` 가 notification header 저장과 item 저장을 저장소 두 개에 직접 걸치면, 알림 이력 orchestration과 write 규칙이 다시 한 서비스에 섞인다
- 문제: 알림 이력 서비스는 상태/retry 초기화가 반영된 notification header 저장과 recommendation item saveAll 을 위해 `NotificationRepository`, `NotificationServiceItemRepository` 를 직접 호출하고 있었다. 이 상태면 이력 저장 규칙이 바뀔 때 서비스 본문을 다시 열어야 한다.
- 해결: `NotificationHistoryCommandRepository` / `NotificationHistoryCommandRepositoryImpl` 을 추가하고, notification header 저장과 item 저장을 이 command 경계 뒤로 이동했다.
- 이유: `NotificationHistoryService` 는 실패/성공 이력 orchestration과 item 조립에 집중하고, notification persistence write 세부사항은 별도 command repository 로 내려야 책임이 더 선명하다.

## 525) `RawApiPayloadService` 와 `NormalizedPolicySidecarBackfillService` 가 같은 `RawApiPayloadRepository` 를 직접 걸치면, raw payload 저장 규칙과 backfill read 규칙이 다시 퍼진다
- 문제: raw payload 저장 서비스는 source/apiCategory 단건 조회 후 upsert save 를 직접 수행했고, sidecar backfill 서비스는 source/apiCategory 목록 조회를 위해 같은 저장소를 직접 호출하고 있었다. 이 상태면 raw payload persistence 규칙이 바뀔 때 저장/백필 서비스 둘을 함께 다시 열어야 한다.
- 해결: `RawApiPayloadReadRepository` / `RawApiPayloadReadRepositoryImpl`, `RawApiPayloadCommandRepository` / `RawApiPayloadCommandRepositoryImpl` 을 추가하고, 저장은 command 경계로, source/apiCategory 단건/목록 조회는 read 경계로 이동했다.
- 이유: `RawApiPayloadService` 는 직렬화/hash/upsert orchestration에, `NormalizedPolicySidecarBackfillService` 는 payload 순회와 aggregate writer 호출에 집중하고, raw payload persistence 세부사항은 read/write 경계로 분리해야 책임이 더 선명하다.

## 526) `StatusUpdateService` 가 `WelfareServiceRepository` 를 직접 걸치면, 상태 전이 orchestration과 ACTIVE/UPCOMING 정책 조회 규칙이 다시 한 서비스에 섞인다
- 문제: 상태 업데이트 스케줄러는 CLOSED/ACTIVE 전이 규칙만 처리하면 되는데도, `WelfareServiceRepository.findByStatus(...)` 를 직접 호출하며 ACTIVE/UPCOMING 대상 조회 규칙까지 함께 들고 있었다. 이 상태면 상태 전이 대상 조회 기준이 바뀔 때 스케줄러 본문을 다시 열어야 한다.
- 해결: `StatusUpdateReadRepository` / `StatusUpdateReadRepositoryImpl` 을 추가하고, ACTIVE/UPCOMING 정책 조회를 이 read 경계 뒤로 이동했다.
- 이유: `StatusUpdateService` 는 날짜 기준 상태 전이와 cluster AI cache TTL cleanup orchestration에 집중하고, 정책 조회 규칙은 별도 read repository 로 내려야 책임이 더 선명하다.

## 527) `ApiSyncLogService` 가 `ApiSyncLogRepository` 를 직접 걸치면, collect log orchestration과 stale RUNNING 복구/save write 규칙이 다시 한 서비스에 섞인다
- 문제: 수집 로그 서비스는 stale RUNNING auto-close, start log save, success/failure update save를 모두 직접 저장소에 보내고 있었다. 이 상태면 collect log write 규칙이 바뀔 때 orchestration 서비스 본문을 다시 열어야 한다.
- 해결: `ApiSyncLogCommandRepository` / `ApiSyncLogCommandRepositoryImpl` 을 추가하고, stale RUNNING 복구와 log save를 command 경계 뒤로 이동했다.
- 이유: `ApiSyncLogService` 는 collect task 실행과 success/failure 상태 전환 orchestration에 집중하고, collect log persistence write 세부사항은 별도 command repository 로 내려야 책임이 더 선명하다.

## 528) `BokjiroDetailCollectService` 가 대상 정책 목록 조회와 기존 detail 존재/조회까지 직접 저장소를 걸치면, 상세 수집 orchestration과 read 규칙이 다시 한 서비스에 섞인다
- 문제: 복지로 상세 수집 서비스는 source별 대상 정책 목록 조회, 기존 detail 존재 확인, 기존 detail row 조회를 직접 저장소에 요청하고 있었다. 이 상태면 상세 수집 대상 선정이나 existing-detail read 규칙이 바뀔 때 orchestration 서비스 본문을 다시 열어야 한다.
- 해결: `BokjiroDetailReadRepository` / `BokjiroDetailReadRepositoryImpl` 을 추가하고, source별 대상 정책 목록 조회와 기존 detail 존재/조회 규칙을 이 read 경계 뒤로 이동했다.
- 이유: `BokjiroDetailCollectService` 는 budget allocation, rate-limit/retry, aggregate/persistence orchestration에 집중하고, 대상 정책/detail read 규칙은 별도 read repository 로 내려야 책임이 더 선명하다.

## 529) `BokjiroDetailCollectService` 가 detail row 저장까지 직접 저장소를 걸치면, 상세 수집 orchestration과 persistence write 규칙이 다시 한 서비스에 섞인다
- 문제: 복지로 상세 수집 서비스는 aggregate 변환 후 merged detail row 저장을 직접 `WelfareServiceDetailRepository.save(...)` 로 호출하고 있었다. 이 상태면 detail persistence write 규칙이 바뀔 때 orchestration 서비스 본문을 다시 열어야 한다.
- 해결: `BokjiroDetailCommandRepository` / `BokjiroDetailCommandRepositoryImpl` 을 추가하고, detail row 저장을 command 경계 뒤로 이동했다.
- 이유: `BokjiroDetailCollectService` 는 budget allocation, rate-limit/retry, aggregate/persistence orchestration에 집중하고, detail persistence write 세부사항은 별도 command repository 로 내려야 책임이 더 선명하다.

## 530) `NormalizedPolicySidecarBackfillService` 가 sourceType/sourceId 기준 정책 lookup을 직접 저장소에 걸치면, sidecar backfill orchestration과 서비스 매칭 read 규칙이 다시 한 서비스에 섞인다
- 문제: sidecar backfill 서비스는 raw payload 순회와 aggregate 재생성만 맡으면 되는데도, 각 payload마다 `WelfareServiceRepository.findBySourceTypeAndSourceId(...)` 를 직접 호출하며 서비스 매칭 read 규칙까지 함께 들고 있었다. 이 상태면 backfill 대상 매칭 기준이 바뀔 때 orchestration 서비스 본문을 다시 열어야 한다.
- 해결: `NormalizedPolicySidecarBackfillReadRepository` / `NormalizedPolicySidecarBackfillReadRepositoryImpl` 을 추가하고, sourceType/sourceId 기준 정책 lookup을 이 read 경계 뒤로 이동했다.
- 이유: `NormalizedPolicySidecarBackfillService` 는 payload 순회와 aggregate upsert orchestration에 집중하고, 서비스 매칭 read 규칙은 별도 read repository 로 내려야 책임이 더 선명하다.

## 531) `CollectItemSaver` 가 기존 정책 lookup과 신규 정책 저장을 직접 저장소에 걸치면, item save orchestration과 정책 upsert read/write 규칙이 다시 한 서비스에 섞인다
- 문제: item save 서비스는 aggregate/region/tag orchestration을 맡으면서도 `WelfareServiceRepository.findBySourceTypeAndSourceId(...)`, `saveAndFlush(...)` 를 직접 호출해 기존 정책 조회와 신규 정책 저장 규칙까지 함께 들고 있었다. 이 상태면 정책 upsert read/write 규칙이 바뀔 때 orchestration 서비스 본문을 다시 열어야 한다.
- 해결: `CollectItemReadRepository` / `CollectItemReadRepositoryImpl`, `CollectItemCommandRepository` / `CollectItemCommandRepositoryImpl` 을 추가하고, 기존 정책 lookup과 신규 정책 저장을 이 read/write 경계 뒤로 이동했다.
- 이유: `CollectItemSaver` 는 item-level aggregate/region/tag save orchestration에 집중하고, 정책 upsert read/write 세부사항은 별도 repository 경계로 내려야 책임이 더 선명하다.

## 532) `CollectItemSaver` 가 tag delete/saveAll 을 직접 저장소에 걸치면, item save orchestration과 tag write 규칙이 다시 한 서비스에 섞인다
- 문제: item save 서비스는 normalized tag 집합 계산 뒤 `ServiceTagRepository.deleteByServiceId(...)`, `flush()`, `saveAll(...)` 을 직접 호출하고 있었다. 이 상태면 tag write 규칙이 바뀔 때 orchestration 서비스 본문을 다시 열어야 한다.
- 해결: `CollectItemTagCommandRepository` / `CollectItemTagCommandRepositoryImpl` 을 추가하고, tag 전체 교체 write 를 command 경계 뒤로 이동했다.
- 이유: `CollectItemSaver` 는 item-level aggregate/region/tag save orchestration에 집중하고, tag persistence write 세부사항은 별도 command repository 로 내려야 책임이 더 선명하다.

## 533) `CollectItemSaver` 가 service region delete/batch insert를 직접 `JdbcTemplate` 으로 다루면, item save orchestration과 region write 규칙이 다시 한 서비스에 섞인다
- 문제: item save 서비스는 region 집합 계산 뒤 `service_regions` delete 와 batch insert SQL 을 직접 들고 있었다. 이 상태면 region persistence write 규칙이나 SQL 튜닝이 바뀔 때 orchestration 서비스 본문을 다시 열어야 한다.
- 해결: `CollectItemRegionCommandRepository` / `CollectItemRegionCommandRepositoryImpl` 을 추가하고, service region 전체 교체 write 를 command 경계 뒤로 이동했다.
- 이유: `CollectItemSaver` 는 item-level aggregate/region/tag save orchestration에 집중하고, region persistence write 세부사항은 별도 command repository 로 내려야 책임이 더 선명하다.

## 534) `DeferredNormalizedPolicySidecarWriter` 가 sidecar table readiness 확인과 기존 fact 조회를 직접 SQL로 들고 있으면, sidecar write orchestration과 read SQL 규칙이 다시 한 클래스에 섞인다
- 문제: sidecar writer 는 taxonomy/fact upsert 를 orchestrate 하면서도 필수 sidecar 테이블 준비 여부 확인, summary slot 테이블 존재 확인, 기존 fact 조회 SQL 까지 직접 들고 있었다. 이 상태면 read SQL 규칙이나 readiness 판정이 바뀔 때 writer 본문을 다시 열어야 한다.
- 해결: `DeferredNormalizedPolicySidecarReadRepository` / `DeferredNormalizedPolicySidecarReadRepositoryImpl` 을 추가하고, sidecar readiness 확인과 기존 fact 조회를 이 read 경계 뒤로 이동했다.
- 이유: `DeferredNormalizedPolicySidecarWriter` 는 taxonomy/terms/facts upsert orchestration에 집중하고, sidecar read SQL 세부사항은 별도 read repository 로 내려야 책임이 더 선명하다.

## 535) `DeferredNormalizedPolicySidecarWriter` 가 taxonomy summary / summary slot write SQL까지 직접 들고 있으면, sidecar write orchestration과 summary persistence 규칙이 다시 한 클래스에 섞인다
- 문제: sidecar writer 는 taxonomy/fact/term orchestration을 맡으면서도 `service_taxonomies` upsert 와 `service_taxonomy_summary_slots` delete/insert SQL 을 직접 들고 있었다. 이 상태면 summary persistence 규칙이나 slot dual-write 세부사항이 바뀔 때 writer 본문을 다시 열어야 한다.
- 해결: `DeferredNormalizedPolicySidecarCommandRepository` / `DeferredNormalizedPolicySidecarCommandRepositoryImpl` 을 추가하고, taxonomy summary 와 summary slot write 를 command 경계 뒤로 이동했다.
- 이유: `DeferredNormalizedPolicySidecarWriter` 는 sidecar upsert 순서와 조건 판단 orchestration에 집중하고, summary persistence write 세부사항은 별도 command repository 로 내려야 책임이 더 선명하다.

## 536) `DeferredNormalizedPolicySidecarWriter` 가 taxonomy term delete/insert SQL까지 직접 들고 있으면, sidecar write orchestration과 taxonomy term persistence 규칙이 다시 한 클래스에 섞인다
- 문제: sidecar writer 는 refresh scope 계산과 fact merge orchestration을 맡으면서도 `service_taxonomy_terms` delete/insert SQL 을 직접 들고 있었다. 이 상태면 taxonomy term persistence 규칙이나 delete scope SQL 이 바뀔 때 writer 본문을 다시 열어야 한다.
- 해결: `DeferredNormalizedPolicySidecarCommandRepository` / `DeferredNormalizedPolicySidecarCommandRepositoryImpl` 에 taxonomy term 전체 교체 write 를 추가하고, writer 는 refresh scope 계산 후 command 경계에 위임만 하도록 정리했다.
- 이유: `DeferredNormalizedPolicySidecarWriter` 는 sidecar upsert 순서와 delete scope 계산 orchestration에 집중하고, taxonomy term persistence write 세부사항은 같은 command repository 로 내려야 책임이 더 선명하다.

## 537) `DeferredNormalizedPolicySidecarWriter` 가 merged fact upsert SQL까지 직접 들고 있으면, sidecar write orchestration과 fact persistence 규칙이 다시 한 클래스에 섞인다
- 문제: sidecar writer 는 기존 fact read 후 merge 결과를 계산하는 orchestration을 맡으면서도 `service_facts` upsert SQL 을 직접 들고 있었다. 이 상태면 fact persistence 규칙이나 SQL 세부사항이 바뀔 때 writer 본문을 다시 열어야 한다.
- 해결: `DeferredNormalizedPolicySidecarCommandRepository` / `DeferredNormalizedPolicySidecarCommandRepositoryImpl` 에 merged fact upsert 를 추가하고, writer 는 merge 결과를 계산한 뒤 command 경계에 위임만 하도록 정리했다.
- 이유: `DeferredNormalizedPolicySidecarWriter` 는 read + merge + write 순서 orchestration에 집중하고, fact persistence write 세부사항은 같은 command repository 로 내려야 책임이 더 선명하다.

## 538) `NormalizedPolicySidecarBackfillService` 가 raw payload 조회와 sourceType/sourceId 매칭을 각각 다른 read 경계로 직접 조합하면, backfill orchestration과 read 조립 규칙이 다시 한 서비스에 섞인다
- 문제: sidecar backfill 서비스는 source별 payload 순회와 aggregate 재생성만 맡으면 되는데도, raw payload 목록 조회와 sourceType/sourceId 기준 서비스 매칭을 서비스 본문에서 직접 조합하고 있었다. 이 상태면 backfill 대상 read 조립 규칙이 바뀔 때 orchestration 서비스 본문을 다시 열어야 한다.
- 해결: `NormalizedPolicySidecarBackfillTarget` 모델과 `findTargetsBySourceTypeAndApiCategoryOrderByFetchedAtAsc(...)` 를 `NormalizedPolicySidecarBackfillReadRepository` 에 추가하고, payload + matched service 조합을 이 read 경계 뒤로 이동했다.
- 이유: `NormalizedPolicySidecarBackfillService` 는 source별 backfill orchestration과 aggregate 재생성에 집중하고, payload/service read 조립 규칙은 별도 read repository 로 내려야 책임이 더 선명하다.
539) `RetrievalService` 가 추천 후보 조회와 태그 read를 서로 다른 read 경계로 직접 조합하던 구조를 줄이기 위해 `RecommendationCandidateReadRepository.findTagsByServiceIds(...)` 를 추가했다. 서비스는 추천 후보 필터 orchestration만 맡고, 태그 조회 구현은 추천 도메인 read boundary 뒤로 이동시켰다.
540) `NotificationService` 는 발송 orchestration 외에 retry 대상 조회 규칙까지 직접 들고 있었다. `NotificationRetryReadService` 를 추가해 `FAILED + nextRetryAt` 조회를 service 바깥으로 이동시키고, 알림 서비스는 재시도 처리 흐름에만 집중하도록 정리했다.
541) `RuleScoringService` 는 추천 도메인 안에서 policy 태그 read 구현을 직접 알고 있었다. 이미 도입한 `RecommendationCandidateReadRepository.findTagsByServiceIds(...)` 를 재사용해 후보 태그 로딩 경계를 추천 도메인 안으로 통일했다.
542) `UserProfileCommandService` 는 우선순위 row 저장 외에 option code 조회와 invalid input 예외 매핑까지 직접 맡고 있었다. `PriorityOptionReadService` 를 추가해 코드 조회 규칙을 분리하고, profile command service 는 row 조립과 저장에 집중하도록 정리했다.
543) `ScoreWeightService` 는 단계 계산 외에 `recommendation_logs` 총건수와 `score_weights` 활성 설정 조회 규칙까지 직접 들고 있었다. `ScoreWeightProgressReadService` 를 추가해 읽기 조합과 미설정 예외를 분리하고, score weight service 는 단계 계산에만 집중하도록 정리했다.
544) `RecommendationLogService` 는 로그 쓰기와 최신 logId 맵 조회를 함께 들고 있었다. `RecommendationLogReadService` 를 분리해 추천 컨트롤러는 조회 전용 service를 사용하게 하고, 기존 service는 command 책임에 더 집중하도록 정리했다.
545) `RecommendationPersistenceService` 는 새 추천 저장 전에 과거 북마크 상태를 `RecommendationPersistenceCommandRepository` 로 읽고 있었다. 읽기/쓰기를 뒤섞지 않기 위해 기존 추천 row 조회를 `RecommendationResultReadRepository` 로 옮기고, command repo에서는 읽기 메서드를 제거했다.
546) `RawApiPayloadService` 는 raw JSON 직렬화 외에 기존 row 조회, 신규 생성, payload 갱신까지 직접 수행하고 있었다. `RawApiPayloadCommandRepository.upsert(...)` 를 추가해 저장 규칙을 command 경계 뒤로 내리고, raw payload service 는 직렬화와 해시 계산에 집중하도록 정리했다.
547) `RecommendationPersistenceService` 는 기존 추천 row를 읽은 뒤 `serviceId -> bookmarked` 맵을 직접 계산하고 있었다. 이 read-side 규칙을 `RecommendationBookmarkStateReadService` 로 분리해 저장 서비스는 row 조립과 replace 저장 orchestration에 집중하도록 정리했다.
548) `RecommendationFacade` 는 refresh-cache hit 시의 저장 추천 조회와 `/recommendations` 목록 조회를 `RecommendationResultReadRepository` 로 직접 처리하고 있었다. facade 가 파이프라인 orchestration 외에 저장 추천 read 규칙까지 들고 있던 셈이라, `RecommendationResultReadService` 를 추가해 조회를 위임하고 facade 는 refresh-cache reuse 판단과 파이프라인 orchestration 에만 집중하도록 정리했다.
549) `RecommendationReadFacade` 는 북마크 상태 조회, canonical projection 조회, 북마크 summary 조립을 한 클래스에 함께 들고 있어서 policy/recommend/user 여러 경계가 같은 facade에 결합돼 있었다. `RecommendationBookmarkReadService` 와 `RecommendationProjectionReadService` 로 역할을 나누고, policy list/search/ranking/detail, recommendation controller, retrieval, user bookmark read 사용처를 직접 전환해 read 책임을 더 분명하게 분리했다.
550) policy 목록/검색/랭킹/상세 서비스는 recommendation 도메인의 북마크 상태 조회와 projection additive field 조립을 직접 알고 있었다. `PolicyPresentationReadService` 를 추가해 이 공통 조립 책임을 policy 경계로 끌어올리고, policy 서비스들은 목록/검색/랭킹/상세 orchestration에만 집중하도록 정리했다.
551) collect 경로에서는 `CollectItemSaver`, `BokjiroDetailCollectService`, `NormalizedPolicySidecarBackfillService` 가 각각 sidecar upsert, detail fallback 적용, youth relevance refresh 후처리를 직접 들고 있었다. `CollectPolicyAggregateApplyService` 를 추가해 이 aggregate 적용 규칙을 한 경계로 모으고, collect 서비스들은 payload fetch/save/backfill orchestration 에만 집중하도록 정리했다.
552) `AdminDashboardService` 는 summary, search failures, recommendation breakdowns, collect failures, trend/window normalization, streak 계산까지 한 클래스에 몰려 있었다. `AdminDashboardSummaryService`, `AdminDashboardSearchService`, `AdminDashboardRecommendationService`, `AdminDashboardCollectService` 로 축을 나누고, 공통 window/limit/ratio 규칙은 `AdminDashboardQueryPolicy` 로 옮겨 admin dashboard read orchestration 을 관심사별로 분리했다.
553) admin dashboard JDBC read 는 여전히 `AdminDashboardReadRepository` 하나에 collect/search/recommendation/notification SQL과 row 모델이 몰려 있었다. `AdminDashboardCollectReadRepository`, `AdminDashboardRecommendationReadRepository`, `AdminDashboardSearchReadRepository`, `AdminDashboardNotificationReadRepository` 로 분리하고, 공통 row record 는 `AdminDashboardReadRows` 로 이동해 각 서비스가 필요한 read 경계만 알도록 정리했다.
554) `RecommendationFacade` 는 refresh cache reuse, cluster assignment, retrieval, scoring, reranking, persistence, CTR log refresh, 저장 추천 조회까지 직접 들고 있었다. `RecommendationGenerationService` 와 `RecommendationAccessService` 를 추가해 생성 파이프라인과 조회 경로를 facade 밖으로 분리하고, facade 는 API 진입점 위임과 북마크 토글만 남기도록 정리했다.
555) `NotificationService` 는 대상 조회, 추천 선정, 본문 생성, 발송 결과 이력 저장, 재시도 규칙까지 모두 직접 들고 있었다. `NotificationRecommendationService`, `NotificationMessageService`, `NotificationDispatchService`, `NotificationRetryService` 로 역할을 나누고, `NotificationService` 는 스케줄 facade 로만 남겨 알림 orchestration 축을 분리했다.
556) `UserService` 는 이미 프로필 조회, 북마크 조회, 프로필/우선순위 수정, 비밀번호 변경, 탈퇴, 알림 수신 거부를 모두 전용 service에 위임하는 얇은 wrapper만 남아 있었다. `UserController` 와 `NotificationController` 가 `UserService` 를 계속 거치면 상단 API 경계에 의미 없는 한 단계가 유지되므로, facade를 제거하고 `UserProfileReadService`, `UserBookmarkReadService`, `UserProfileCommandService`, `UserAccountCommandService` 를 직접 주입하도록 전환했다.
557) `AuthService` 는 이미 token lifecycle은 `AuthTokenService`, 비밀번호 재설정은 `PasswordResetService`, identity lookup은 `AuthIdentityReadService`, signup 저장은 `UserRegistrationService` 로 위임하고 있었지만, 여전히 이메일 중복확인/회원가입/로그인/refresh/logout/admin allowlist 규칙을 한 facade에 모으고 있었다. `AuthAvailabilityService`, `AuthSignupService`, `AuthLoginService`, `AuthSessionService`, `AuthAdminRoleService` 로 역할을 나누고 `AuthController` 와 `AdminSecurityIntegrationTest` 를 직접 전환해, 인증 상단 API와 관리자 allowlist 정책도 facade 없이 각 경계로 연결되게 정리했다.
558) `CollectService` 는 정기 배치, 관리자 수동 source 실행, `bokjiro-details-gap-fill`, source adapter registry dispatch, `api_sync_logs` 경계를 한 facade에 함께 들고 있었다. `CollectBatchService`, `CollectAdminService`, `CollectSourceExecutionService` 로 역할을 나누고 `CollectAdminController`, `AdminSecurityWebMvcTest`, `AdminSecurityIntegrationTest`, collect 단위 테스트를 직접 전환해, 수집 상단 진입점과 source dispatch 경계도 facade 없이 분리했다.
559) `PolicyService` 는 이미 목록 read, 상세 aggregate read, 북마크 command 대부분을 전용 경계로 위임하고 있었지만, 여전히 정책 목록/상세/북마크 API 상단을 한 facade에 묶고 있었다. `PolicyListService`, `PolicyDetailService`, `PolicyBookmarkCommandService` 로 역할을 나누고 `PolicyController`, 정책 단위 테스트, policy/recommendation WebMvc 경로를 직접 전환해, policy 상단 API도 facade 없이 각 경계로 바로 연결되게 정리했다.
560) `RecommendationFacade` 는 이미 추천 생성은 `RecommendationGenerationService`, 저장 추천 조회는 `RecommendationAccessService`, 북마크 토글은 `RecommendationBookmarkCommandService` 로 위임하고 있었지만, 여전히 추천 API 상단을 한 facade에 묶고 있었다. `RecommendationController`, recommendation WebMvc 테스트, 관련 문서를 직접 전환하고 facade 자체를 제거해, recommendation 상단 API도 facade 없이 생성/조회/북마크 경계에 바로 연결되게 정리했다.
561) `NotificationService` 는 이미 추천 준비, 본문 생성, 발송 실행, 재시도 규칙을 하위 서비스로 분리한 뒤에도 여전히 스케줄 진입점과 직접 발송 위임을 한 facade에 묶고 있었다. `NotificationScheduleService` 를 추가하고 facade 자체를 제거해, 알림 상단은 스케줄 트리거와 retry 진입점만 맡고 실제 발송 orchestration은 `NotificationDispatchService` 아래에만 남도록 정리했다.
562) `AdminDashboardService` 는 이미 summary/search/recommendation/collect 축을 전용 서비스로 위임하는 thin wrapper만 남아 있었지만, 여전히 관리자 대시보드 API 상단을 한 facade에 묶고 있었다. `AdminDashboardController` 와 `AdminSecurityWebMvcTest` 를 `AdminDashboardSummaryService`, `AdminDashboardSearchService`, `AdminDashboardRecommendationService`, `AdminDashboardCollectService` 에 직접 연결하고 facade 자체를 제거해, admin dashboard 상단 API도 facade 없이 각 read orchestration 경계에 바로 연결되게 정리했다.
563) `ChatMessageService` 와 `ChatSessionService` 는 이미 read/write repository 경계와 command service를 분리한 뒤에도, 세션 조회/세션 command/대화 실행이라는 서로 다른 API 상단 책임을 두 클래스에 섞어 들고 있었다. `ChatConversationService`, `ChatSessionQueryService`, `ChatSessionCommandService` 로 역할을 다시 나누고 `ChatSessionController` 를 직접 전환해, chat API 상단도 facade 없이 대화 실행과 세션 read/command 경계에 바로 연결되게 정리했다.
564) facade 제거가 끝난 뒤에도 current-state 문서 일부가 예전 상단 진입점 이름을 계속 가리키고 있었다. `architecture.md`, `policy-source-code-entrypoints.md`, `user-data-separation-design.md` 를 현재 코드 기준으로 다시 맞춰 `AuthService`, `UserService`, `CollectService` 같은 제거된 facade 참조를 걷어내고, 실제 진입점인 `Auth*Service`, `User*Service`, `Collect*Service` 묶음으로 동기화했다.
565) 추천/알림에는 Redis 락을 붙였지만, collect 전역 락은 여전히 긴 DB lease 하나에만 의존하고 있었다. 프로세스가 죽으면 stale `api_sync_logs` 를 heal 하기 전에 `collect-global` 락이 최대 6시간 남아 다음 실행이 막힐 수 있어서, `CollectExecutionGuard` 를 짧은 lease + heartbeat 갱신 방식으로 바꿔 크래시 후 lock recovery 시간을 줄였다.
566) `NotificationRetryService` 는 due failed row 를 읽은 뒤 바로 발송해서, 재기동/재진입 타이밍에 같은 retry 대상을 다시 잡을 수 있었다. retry 대상 ID를 먼저 읽고 `nextRetryAt` 을 claim window 로 원자적으로 밀어 올린 뒤에만 발송하도록 바꿔, 실패 row 재진입 시 중복 retry 가능성을 줄였다.
567) `AbstractListCollectSourceAdapter` 는 item 저장 예외는 failed count 로 집계했지만, `saveRawPayload(...)` 예외는 try 블록 밖에 있어서 item 하나의 raw payload 저장 실패가 source 전체 FAILED로 번질 수 있었다. raw payload 저장도 item-level 실패 경계 안으로 넣어 부분실패를 `PARTIAL_SUCCESS` 로 집계하고 다음 item 처리를 계속하도록 정리했다.
568) `RecommendationPersistenceService` 는 generation 시점의 북마크 상태를 복사해 새 추천 row를 저장하지만, `RecommendationBookmarkCommandService` 는 같은 사용자 generation/save 와 직렬화되지 않아 refresh 도중 북마크 토글이 끼어들면 최신 상태가 유실될 여지가 있었다. 북마크 토글도 `RecommendationExecutionGuard` 의 사용자별 Redis 락 아래로 넣어 generation 과 bookmark command 가 같은 userKey 단위로 직렬화되게 정리했다.
569) `YouthCollectSourceAdapter`, `BokjiroCentralCollectSourceAdapter`, `BokjiroLocalCollectSourceAdapter` 는 빈 응답을 source별로 제각각 처리하거나 아예 성공 0건으로 통과시키고 있었다. `CollectListResponsePolicy` 를 추가해 list source의 suspicious empty response를 공통 정책으로 승격시키고, 외부 API 빈응답을 `COLLECT_API_FAILED` 로 처리하도록 정리했다.
570) `RecommendationRefreshCacheService` 는 non-personal refresh 재사용 마커를 단순 존재 여부만 저장해서, 이후 북마크 placeholder 같은 더 늦은 row가 생겨도 `findLatestSavedRecommendations` 가 서로 다른 batch를 섞어 재사용할 수 있었다. refresh 마커에 `recommendedAt` batch token을 저장하고, `RecommendationResultReadRepository` 도 exact batch 조회를 지원하게 바꿔 refresh cache hit과 busy fallback 이 항상 동일 batch 결과셋만 반환하도록 정리했다.
571) list collect client 들은 `YouthApiClient`, `BokjiroCentralClient`, `BokjiroLocalClient` 마다 429/5xx/일반 예외 재시도와 지터 backoff 구현을 따로 들고 있었다. `CollectHttpRetryExecutor` 를 추가해 HTTP 상태 분류, 재시도 로그, 지터 backoff 를 공통 경계로 모으고, source별 차이는 `RETRYABLE`/`RATE_LIMITED`/`FAIL_FAST` 분류만 넘기도록 정리했다.
572) `NotificationDispatchService` 는 발송 성공 후에만 `notifications` row 를 저장해서, 메일 전송 직후 프로세스가 죽으면 같은 일간/주간 window 에서 다시 발송될 수 있었다. `dispatch_key` unique reservation row와 `pending` 상태를 도입해 발송 전에 window별 row를 먼저 선점하고, 이후 성공/실패 결과를 같은 row에 finalize 하도록 바꿔 crash 이후 중복 발송 창구를 줄였다.
573) `RawApiPayloadService` 는 raw payload 저장 실패를 내부에서 로그만 남기고 삼켜서, list collect 와 detail collect 가 raw snapshot 없이도 saved 로 집계될 수 있었다. 저장 메서드가 성공 여부를 반환하게 바꾸고, `AbstractListCollectSourceAdapter` 와 `BokjiroDetailCollectService` 가 raw 저장 실패를 `failedCount` 로 반영한 뒤 다음 item 으로 진행하게 정리했다.
574) `RecommendationGenerationService` 는 추천 저장이 성공한 뒤 `refreshLogs(...)` 후처리가 실패하면 전체 추천 호출이 예외로 끝나 사용자 입장에서는 실패지만 DB에는 새 추천 row가 이미 남는 불일치가 있었다. 로그 후처리를 best-effort 로 낮추고, 추천 저장 결과가 비어 있으면 기존 추천을 지우지 않도록 `RecommendationPersistenceService` 에 빈 입력 guard 를 추가해 저장 batch 일관성을 보강했다.

## 575) `BokjiroDetailCollectService` gap fill 이 앞 라운드 실패 row를 계속 다시 잡으면, 뒤에 남은 backlog 가 있어도 `saved=0` 조건만으로 조기 종료해 false exhaustion 이 생길 수 있다
- 문제: detail gap fill 은 round 단위로 같은 target 목록을 다시 읽는데, 앞쪽 service 하나가 raw 저장 실패나 detail 저장 실패를 내면 다음 round 에서도 그 row를 다시 먼저 시도한다. call budget 이 작을 때는 뒤 backlog 까지 도달하지 못한 채 `saved=0` 라운드가 반복되고, 실제로는 남은 대상이 있어도 “더 저장할 게 없다”고 오판할 수 있다.
- 해결: `BokjiroDetailCollectService` 가 round별 실패 `serviceId` 를 모아 이후 round target 순회에서 제외하고, `saved=0` 이어도 `failedCount>0` 인 경우에는 즉시 종료하지 않게 바꿨다. `BokjiroDetailCollectServiceTest` 에 앞 라운드 실패 row를 건너뛰고 뒤 backlog 를 계속 소진하는 회귀 케이스를 추가했다.
- 이유: gap fill 의 종료 신호는 “진짜 backlog exhaustion” 이어야지 “같은 실패 row 재시도” 여야 하면 안 된다. 실패 row를 일단 제외해야 남은 healthy backlog 를 끝까지 진행하면서도 false green / false exhaustion 을 줄일 수 있다.

## 576) `NotificationRetryService` 가 claim 이후 실행 결과를 남기지 않으면, 스케줄 로그만으로는 due backlog 와 claim skip, terminal failure 를 구분하기 어렵다
- 문제: retry scheduler 는 전역 락 아래에서 돌아도 실제 실행 결과가 발송 성공/재예약/최종 실패/claim 충돌 중 어디로 흘렀는지 집계하지 않았다. 이 상태면 “왜 backlog 가 안 줄었는가”를 운영 로그에서 바로 읽기 어렵고, claim window 도입 후에는 skip 증가 여부도 따로 보이지 않는다.
- 해결: `NotificationRetryService.retryFailedNotifications()` 가 `RetryRunResult` 를 반환하도록 바꾸고, `NotificationScheduleService` 가 `due/claimed/skippedClaim/sent/rescheduled/terminalFailed` 카운트를 한 줄 로그로 남기게 했다. 단위/통합 테스트도 각 결과 카운트를 직접 검증하도록 맞췄다.
- 이유: background retry 는 단순 성공/실패만으로는 충분히 관측되지 않는다. claim 기반 중복 방지까지 들어간 경로에서는 실행 단위 집계를 남겨야 실제 병목이 due backlog 인지, claim 충돌인지, terminal failure 누적인지 빠르게 구분할 수 있다.

## 577) `rule-only-invalid-key` replay 가 실제 OpenAI 호출을 계속 타면, education experiment control sample 해석이 AI 변동에 오염된다
- 문제: `run-local-education-priority-replay.sh` 는 rule-only 모드에서 `OPENAI_API_KEY=invalid-for-rule-only-replay` 를 넘겨 “AI 호출 실패 후 rule-only fallback” 을 기대하고 있었지만, 실제 런타임에서는 여전히 `RealtimeAiGateway` 가 OpenAI 응답을 받아 `responseId=chatcmpl-*` 를 남기고 있었다. 이 상태에서는 sample B control row 이동이 canonical bonus 회귀인지, AI 응답 변동인지 분리할 수 없다.
- 해결: `RealtimeAiGateway` 에 `recommend.ai.force-rule-only` 우회 스위치를 추가하고, replay script가 `USE_REAL_OPENAI_FOR_REPLAY != true` 일 때 `RECOMMEND_AI_FORCE_RULE_ONLY=true` 를 강제로 넘기도록 바꿨다. 이제 rule-only replay의 bootrun trace는 `responseId=none`, `systemFingerprint=none`, `resultsCount=0` 으로 남고 OpenAI를 실제로 건너뛴다.
- 이유: replay의 목적이 “education canonical bonus가 rule score 경계에서 control 대비 어떻게 바뀌는가”를 보는 것이라면, AI 계층은 환경/키 상태와 무관하게 명시적으로 차단돼야 한다. invalid key 실패에 기대는 방식은 실제 property source나 런타임 경로에 따라 흔들릴 수 있다.

## 578) MySQL 전용 SQL 문법을 PostgreSQL 전환 뒤에도 그대로 두면, 스키마는 떠 있어도 실제 실행 경로에서 런타임 오류가 늦게 터진다
- 문제: PostgreSQL 기반으로 올린 뒤에도 `ON DUPLICATE KEY UPDATE`, `DATE_FORMAT(...)`, `MATCH ... AGAINST` 같은 MySQL 전용 문법이 남아 있으면, 애플리케이션은 기동되더라도 수집 저장, 테스트 SQL, 정책 검색처럼 실제로 그 쿼리를 타는 시점에야 실패한다. 이 상태에서는 “DB 전환이 끝났다”는 판단이 거짓 양성이 된다.
- 해결: upsert는 `ON CONFLICT` 로, 날짜 포맷/함수는 PostgreSQL 표현으로, 전문 검색은 PostgreSQL FTS + `pg_trgm` 경로로 전부 치환하고, `test`, `integrationTest`, 실제 검색/수집 실행까지 돌려 쿼리 사용 경로를 같이 검증했다.
- 이유: DB 전환의 완료 기준은 datasource 변경이 아니라 “실행 경로에서 MySQL 문법이 더 이상 호출되지 않는 상태”여야 한다. 정적 스캔만으로는 native query와 테스트 fixture SQL이 빠질 수 있으므로 런타임 검증이 같이 필요하다.

## 579) PostgreSQL 컬럼 타입과 JPA/DTO 표현을 함께 안 맞추면, 스키마 추가보다 매핑 불일치가 더 늦고 더 불명확하게 터진다
- 문제: `json` 컬럼을 `String` 으로 다루거나, DB/엔티티에는 있는데 DTO 응답에는 빠진 필드가 있으면, 저장은 되는데 조회가 비거나, 직렬화 시점에 타입 불일치가 터지거나, 프론트는 필드가 없는 줄 알고 다른 fallback을 타게 된다. `selectionCriteria`, `operatingOrg`, `homepageUrl`, `relatedLaw`, `formFiles` 같은 누락은 이런 식으로 드러났다.
- 해결: PostgreSQL 스키마 변경 시 엔티티, command/read DTO, 실제 프론트 사용 필드를 한 묶음으로 확인하고, 문자열 JSON으로 유지할 필드는 `TEXT` 로 맞추고, 화면에서 쓰는 정책 상세/목록 필드는 DTO 응답까지 같이 노출하도록 정리했다.
- 이유: PostgreSQL 전환에서 진짜 위험한 건 “DDL 성공” 뒤에 남는 표현 계층 불일치다. 스키마, 엔티티, 응답 DTO, 프론트 사용처를 하나의 계약으로 보지 않으면 런타임 문제를 저장/조회/렌더링 단계마다 따로 맞게 된다.

## 580) 실데이터가 이미 적재된 로컬 DB에서 integration test가 “내가 넣은 fixture가 반드시 1등”을 가정하면, 기능은 정상이어도 회귀처럼 깨진다
- 문제: 검색/semantic integration test가 희소 fixture 몇 건만 넣고 “첫 결과가 내가 넣은 정책이어야 한다”고 가정하면, 로컬 DB에 이미 적재된 실데이터가 함께 있을 때 더 강한 후보가 앞에 와도 테스트는 실패한다. 이 상태에서는 실제 기능 회귀와 테스트 설계 문제를 구분하기 어렵다.
- 해결: 순위 1등 단정 대신 “의도한 후보가 결과 집합에 포함되는지”, “fallback/semantic 경로가 실제로 작동했는지”를 검증하도록 바꾸고, fallback 케이스는 fixture 인기 신호를 충분히 높여 전역 데이터보다 우선되게 맞췄다.
- 이유: 로컬 통합 테스트는 빈 DB만을 전제하기 어렵다. 특히 retrieval 계열은 절대 순위보다 경로 실행과 후보 포함 여부를 보는 쪽이 실데이터 공존 환경에서 더 안정적이고 의미가 있다.

## 581) 수집 source의 URL 후보를 대표 필드 1개로만 축약하면, 공식 신청 링크가 비어도 참고 URL이 살아 있는 정책을 canonical 단계에서 잃어버린다
- 문제: `aplyUrlAddr`, `refUrlAddr1`, `refUrlAddr2` 같이 여러 URL 후보가 있는 source를 첫 non-blank 하나만 `detailUrl` 로 접으면, 신청 URL은 비어 있지만 참고 URL이나 본문 내 링크가 살아 있는 정책을 canonical 모델에서 복구하기 어려워진다. 이후 상세 페이지나 챗봇 CTA에서는 “URL 없음”으로 보이지만 raw payload에는 근거가 남아 있는 상태가 생긴다.
- 해결: 당장은 raw payload를 보존해 backfill 가능성을 열어두고, 대표 URL과 별개로 보조 URL 후보 풀을 저장하는 방향을 다음 단계 설계로 분리했다. 상세 필드 노출도 `homepageUrl` 같은 보조 계약을 추가해 대표 URL 하나에만 의존하지 않도록 정리했다.
- 이유: 수집 정규화에서 제일 위험한 손실은 “나중에 복구 가능한가” 여부다. 대표 필드 하나로 납작하게 만들기보다, canonical은 단순하게 유지하더라도 후보 풀이나 raw snapshot을 보존해야 후속 품질 보정이 가능하다.

## 582) optional URL 후보를 `List.of(...)` 로 바로 묶으면 null 값이 하나만 섞여도 mapper 단계에서 즉시 예외가 난다
- 문제: 정책 상세 URL 후보는 source마다 `apply/detail/reference` 필드가 비어 있을 수 있다. 이 상태에서 optional candidate를 `List.of(...)` 로 바로 만들면 null 이 하나만 있어도 `NullPointerException` 이 발생해, URL 후보 풀을 additive 필드로 붙이는 작업이 오히려 수집 정규화 경로를 깨뜨릴 수 있다.
- 해결: URL 후보는 `referenceUrlCandidates(...)` 같은 null-filter helper로 먼저 정리한 뒤 JSON 후보 풀을 만들도록 바꿨다. direct candidate 와 본문 추출 candidate 모두 dedupe 전에 null-safe 하게 모으고, 테스트에서도 optional field 가 비어 있는 케이스를 같이 확인했다.
- 이유: source payload 는 optional field 가 기본인 경우가 많다. additive 계약을 붙일수록 “필드가 없을 때도 안전하게 지나가야 한다”는 조건이 더 중요해지므로, optional candidate 집계는 생성 시점부터 null-safe 경계로 묶어야 한다.

## 583) Docker Desktop/WSL 환경에서 오래된 컨테이너를 재사용하면 bind mount source 경로가 stale 상태로 남아 DB 컨테이너가 아예 안 뜰 수 있다
- 문제: `docker compose up -d db app` 를 다시 올리는 과정에서 `youth-welfare-db` 가 `/docker-entrypoint-initdb.d/01-schema.sql` bind mount 에러로 시작조차 못 했다. 메시지는 `/run/desktop/mnt/host/wsl/docker-desktop-bind-mounts/... no such file or directory` 형태였고, 코드나 SQL 파일이 사라진 것이 아니라 컨테이너가 예전 mount source 경로를 잡고 있었다.
- 해결: stale 컨테이너를 `docker rm -f youth-welfare-db youth-welfare-app` 로 제거한 뒤 `docker compose up -d db redis app` 로 다시 만들었다. volume 데이터는 유지하고 컨테이너만 새로 만들면 mount source 경로가 정상화된다.
- 이유: 이 문제는 앱/DB 로직이 아니라 Docker Desktop + WSL bind mount 재사용 이슈다. init SQL을 수정하지 않았는데도 mount 에러가 먼저 나오면, 파일 내용보다 컨테이너 재생성을 먼저 의심하는 편이 빠르다.

## 584) 기존 PostgreSQL volume은 `schema.sql` 변경을 자동 재적용하지 않으므로, 새 컬럼을 추가해도 app startup validation에서 뒤늦게 죽을 수 있다
- 문제: `reference_urls_json` 을 `schema.sql` 과 엔티티에 추가한 뒤 app을 다시 띄웠지만, 기존 named volume을 쓰는 로컬 Postgres에는 컬럼이 없어 Hibernate validate가 `missing column [reference_urls_json] in table [welfare_service_details]` 로 부팅 직후 실패했다.
- 해결: live DB 스키마를 직접 확인한 뒤 `ALTER TABLE welfare_service_details ADD COLUMN IF NOT EXISTS reference_urls_json TEXT;` 를 적용하고 app을 재기동했다. 이 로컬 테스트 서비스는 유저 데이터가 없으므로, 상황에 따라 `docker compose down -v` 후 전체 재초기화로 맞춰도 된다.
- 이유: `docker-entrypoint-initdb.d` 의 `schema.sql` 은 최초 volume 초기화 때만 적용된다. 이후 스키마 변경은 migration, 명시적 `ALTER TABLE`, 또는 volume reset으로 반영해야 하며, 그렇지 않으면 코드/DDL은 맞아 보여도 런타임 validate에서 늦게 터진다.

## 585) 로컬 테스트 서비스에서 admin smoke를 준비할 때 signup/verification 계약에 계속 막히면, 직접 DB seed를 하더라도 앱이 쓰는 BCrypt 구현으로 맞춰야 한다
- 문제: `admin@example.com` 은 public signup에서 admin allowlist 정책 때문에 막히고, 일반 사용자는 email verification 없이는 회원가입이 되지 않아 runtime admin token을 바로 준비하기 어려웠다. 게다가 DB에 admin row를 직접 넣을 때 Python `bcrypt` 로 만든 hash는 형식상 맞아 보여도 실제 앱 login에서는 계속 `A004` 로 실패했다.
- 해결: 이 로컬 테스트 서비스는 실제 유저가 없으므로 `users`, `auth_users` 에 admin row를 직접 seed해 smoke를 진행했다. 이때 password hash는 임의 구현이 아니라 Spring `BCryptPasswordEncoder` 로 생성한 값을 사용해야 login이 정상 동작했다.
- 이유: 운영 경로라면 signup/verification 계약을 따르는 것이 맞지만, 지금 환경은 로컬 무유저 테스트 서비스다. 다만 직접 seed를 하더라도 “앱이 실제로 쓰는 암호화 구현과 같은지”가 중요하므로, bcrypt 계열이라는 이유만으로 다른 구현 hash를 섞으면 smoke 자체가 거짓 실패가 된다.

## 586) `integrationTest` 는 코드 회귀가 없어도 로컬 PostgreSQL/Redis 컨테이너를 내려 둔 상태면 `JDBCConnectionException` 으로 바로 실패한다
- 문제: `PolicyReferenceUrlRebuildIntegrationTest` 를 추가한 뒤 처음 실행했을 때, 테스트 자체가 아니라 Spring context bootstrap 단계에서 `org.postgresql.util.PSQLException` / `java.net.ConnectException` 로 죽었다. 원인은 직전 turn에서 `docker compose down` 으로 `db`, `redis` 를 모두 내린 상태에서 `application-integration.yml` 이 여전히 `127.0.0.1:5433`, `127.0.0.1:6379` 를 기대하고 있었기 때문이다.
- 해결: `docker compose up -d db redis` 로 integration 의존 서비스를 다시 올리고, health가 `healthy` 인 것을 확인한 뒤 테스트를 재실행했다. 이후 `PolicyReferenceUrlRebuildIntegrationTest` 는 정상 통과했다.
- 이유: 이 프로젝트의 integration profile은 embedded DB가 아니라 로컬 Docker PostgreSQL/Redis를 전제로 한다. 따라서 테스트 실패 로그가 곧바로 코드 회귀를 의미하지는 않으며, `JDBCConnectionException` 이 먼저 보이면 서비스 기동 상태를 우선 확인하는 편이 빠르다.

## 587) `POST /api/admin/collect/youth-details` 는 앱이 healthy여도 upstream DETAIL 500 재시도 때문에 수 분 이상 길어질 수 있으므로, 전체 기능 점검 루프에서는 bounded collect와 분리해 보는 편이 안전하다
- 문제: 2026-05-13 로컬 런타임 점검에서 `collect/all`, `bokjiro-details-gap-fill`, 추천/검색/챗/정책 상세는 모두 빠르게 끝났지만, `POST /api/admin/collect/youth-details` 는 온통청년 DETAIL API의 간헐적 `500` 재시도(`CollectHttpRetryExecutor` warn) 때문에 응답이 오래 닫히지 않았다. 같은 동안 app `/actuator/health` 는 계속 `UP` 이었고, 다른 기능 호출도 정상 처리됐다.
- 해결: 전체 기능 smoke에서는 `collect/all` 결과와 bounded admin 경로(`bokjiro-details-gap-fill`, `reference-urls/rebuild`, `category-audit`, `quality gate`)를 우선 확인하고, `youth-details` 는 별도 장시간 collect 관찰 대상으로 분리했다. 장시간 observation이 불필요할 때는 클라이언트 요청을 중단하고 app health와 로그만 확인한다.
- 이유: 이 문제의 핵심은 앱 장애가 아니라 upstream DETAIL endpoint 변동성이다. 전체 기능 점검 루프에서 이 경로를 같은 timeout/기대시간으로 묶으면 다른 기능이 모두 정상이어도 검증 전체가 불필요하게 길어지거나 hanging처럼 보일 수 있다.

## 588) PostgreSQL/pgvector 로 Compose가 바뀐 뒤에도 local smoke가 MySQL CLI 검증을 그대로 들고 있으면, 앱 기능은 정상이어도 validation suite가 DB 확인 단계에서 계속 거짓 실패한다
- 문제: `collect/all`, `reference-urls/rebuild`, `search-youth-relevance/rebuild`, `embeddings/rebuild`, `retrieval evaluation/gate`, runtime API smoke는 모두 통과했는데도, 일부 smoke 스크립트는 여전히 `docker exec ... mysql ... youth_welfare` 로 `recommendation_logs`, `users`, `user_pii_sync_queue` 를 직접 확인하고 있었다. PostgreSQL/pgvector Compose에서는 애플리케이션 경로는 정상이어도 이 DB 직접 조회 부분 때문에 validation suite가 끝까지 가지 못한다.
- 해결: `deploy/smoke/smoke-common.sh` 에 PostgreSQL `psql -At` 기반 공통 query/apply helper를 추가하고, click/withdraw/admin-forced-logout/runtime/replay/pii-cutover smoke의 DB 확인 구간을 모두 이 helper로 전환했다. boolean 검증은 `1/0` 가 아니라 `true/false` 또는 `t/f`, click 검증은 `is_clicked = true` 와 `clicked_at IS NOT NULL` 기준으로 바꿨다.
- 이유: smoke의 목적은 “현재 런타임 계약이 맞는가”를 보는 것이지, 예전 DB 종류를 전제로 한 관리 편의 명령을 유지하는 것이 아니다. Compose의 DB가 바뀐 뒤에도 DB 직접 확인 구간을 같이 옮기지 않으면, 앱이 정상이어도 검증 체인이 계속 오탐을 낸다.

## 589) local smoke가 signup만 직접 치고 Redis email verification key를 넣지 않으면, 최신 인증 계약에서는 `A012` 때문에 auth/session 계열 점검이 전부 거짓 실패한다
- 문제: 최신 앱은 `/api/auth/signup` 전에 `email-verify:verified:<sha256(normalizedEmail)>` Redis key가 있어야 한다. 그런데 local smoke 스크립트는 예전처럼 signup만 바로 호출하고 있어, runtime/click/withdraw/admin-forced-logout/replay/pii-cutover 경로가 모두 `400/A012` 로 막힐 수 있었다. 여기에 이름 검증까지 한글 2~10자 계약으로 강화돼 영어 기본값이면 `C001` 도 같이 나온다.
- 해결: smoke 공통 helper에 verified-email seed 함수를 추가하고, signup 직전에 Redis `SETEX email-verify:verified:<hash> ... 1` 을 넣도록 바꿨다. 동시에 smoke 기본 이름도 한글 2~10자 값으로 정리해 최신 signup validation과 맞췄다.
- 이유: 이 환경은 로컬 무유저 테스트 서비스라 운영 가입 절차를 끝까지 사람 손으로 따라가는 것이 목적이 아니다. smoke는 “현재 계약을 만족하는 최소 조건”을 자동으로 준비해야 하며, 그렇지 않으면 인증 정책 강화가 있을 때마다 모든 auth/session smoke가 거짓 실패로 오염된다.

## 590) `backend/build.gradle` 의 `bootRun` 이 `.env` 값을 무조건 다시 주입하면, 로컬 replay smoke에서 스크립트가 올바른 PostgreSQL 계정을 넘겨도 `.env` 의 `DB_USERNAME=root` 가 덮어써져 앱이 부팅조차 못 할 수 있다
- 문제: replay smoke는 local `bootRun` 으로 앱을 두 번 띄워 추천 결과를 비교한다. 스크립트 쪽에서는 `DB_USERNAME=app_core_rw`, `APP_PII_DB_URL=jdbc:postgresql://...` 를 명시적으로 넘겼지만, `backend/build.gradle` 의 `bootRun` 이 `../.env` 를 다시 읽어 `DB_USERNAME=root`, MySQL 시절 JDBC 값 등을 environment에 넣고 있어서 결과적으로 `FATAL: password authentication failed for user "root"` 로 부팅이 실패했다.
- 해결: `bootRun` 에서 local Docker PostgreSQL 기준 값(`DB_URL`, `APP_PII_DB_URL`, `NOTIFICATION_PII_DB_URL`, `REDIS_HOST`)을 명시적으로 다시 주입하고, `DB_USERNAME`, `DB_PASSWORD`, `DB_APP_PII_USERNAME`, `DB_APP_PII_PASSWORD`, `DB_NOTIFICATION_PII_RO_USERNAME`, `DB_NOTIFICATION_PII_RO_PASSWORD` 도 `System.getenv(...)` 우선으로 override 하도록 정리했다. replay smoke wrapper도 `.env` 의 `jdbc:mysql://...` 값은 PostgreSQL local URL로 덮어쓰게 맞췄다.
- 이유: smoke/replay는 보통 “스크립트가 넘긴 env가 최종값”이어야 한다. `bootRun` 이 편의상 `.env` 를 다시 읽더라도, 그 값이 현재 런타임 계정/DB 종류보다 우선하면 smoke가 코드 문제가 아닌 환경 오염 때문에 계속 실패한다.

## 591) `npm audit` 는 node_modules가 아니라 lockfile 기준으로 판단하므로, 로컬 설치 트리에서 취약 버전이 사라졌어도 `package-lock.json` 이 stale하면 Git 기준으로는 여전히 취약하게 남는다
- 문제: 로컬 `node_modules` 에는 `axios@1.16.0`, `follow-redirects@1.16.0`, `vite@8.0.12`, `postcss@8.5.14` 가 설치돼 있어도, `frontend/package-lock.json` 이 여전히 `axios@1.14.0`, `follow-redirects@1.15.11`, `vite@8.0.3`, `postcss@8.5.8` 을 가리키고 있으면 서버나 다른 개발자가 `npm audit` 를 다시 돌릴 때 취약하다고 판단한다. 설치 트리만 최신이고 lockfile이 그대로면 “내 로컬에서는 해결된 것처럼 보이는데 Git에는 반영되지 않은 상태”가 된다.
- 해결: `main` 기준 lockfile 버전을 먼저 확인한 뒤 `frontend` 에서 `npm audit fix` 를 다시 실행해 `package-lock.json` 자체를 갱신하고, 그 상태에서 `npm run lint`, `npm run build`, `npm audit --omit=dev --audit-level=high`, `npm audit --audit-level=high` 를 다시 통과시키고 커밋했다. 실제 변경 파일은 `package-lock.json` 하나만 생길 수 있다는 점도 같이 확인했다.
- 이유: npm 보안 작업의 진짜 산출물은 “현재 node_modules” 가 아니라 “다른 환경도 같은 안전 버전을 재현하게 만드는 lockfile” 이다. node_modules가 최신이라는 사실만 믿고 넘어가면, 서버나 CI에서는 여전히 오래된 lockfile을 기준으로 취약점이 재현된다.

## 592) `bootRun` 이 caller env 보다 하드코딩 기본값을 우선하면, smoke가 넘긴 올바른 로컬 PostgreSQL/Redis 설정도 다시 무시된다
- 문제: `backend/build.gradle` 의 `bootRun` 에서 PostgreSQL/Redis 기본값을 무조건 `environment(...)` 로 덮으면, smoke나 수동 실행이 다른 endpoint/schema/port를 넘겨도 child process에서는 항상 하드코딩 값만 보게 된다. 반대로 `.env` 에 MySQL/root 찌꺼기가 남아 있으면 그것도 caller env 보다 먼저 새어들 수 있다.
- 해결: `bootRun` 이 `System.getenv(...) -> .env 값 -> sane default` 순서로 값을 해석하게 바꾸고, JDBC URL은 `jdbc:mysql://...`, `jdbc:postgresql://db:...` 같은 오래된 로컬값을 기본 PostgreSQL localhost URL로 정규화하도록 맞췄다. Redis host도 `redis` 같은 compose 내부 호스트가 남아 있으면 `localhost` 로 정규화한다.
- 이유: local smoke/replay의 핵심 계약은 “스크립트가 넘긴 env가 최종값” 이다. build script 편의 기본값은 fallback이어야지, caller override를 다시 덮어쓰는 1차 source가 되면 안 된다.

## 593) smoke health check가 HTTP 200만 보면, `/actuator/health` body 가 아직 `UP` 이 아닌 과도 상태를 정상 기동으로 오판할 수 있다
- 문제: 일부 smoke helper는 `/actuator/health` 의 응답 코드만 200이면 기동 완료로 판단하고 다음 auth/recommendation 호출로 넘어갔다. 이 상태에서는 앱이 아직 `status=DOWN/OUT_OF_SERVICE` 이거나 비정상 JSON을 돌려도 이후 단계에서 엉뚱한 실패로 보일 수 있다.
- 해결: 공통 `smoke_wait_for_health()` 가 200 응답 뒤에 JSON body를 파싱해 `status == "UP"` 일 때만 통과하도록 바꿨다.
- 이유: smoke의 첫 관문은 “HTTP가 열렸는가”가 아니라 “앱이 실제로 ready 상태인가”다. actuator body까지 같이 보아야 이후 단계 실패를 readiness 문제와 분리할 수 있다.

## 594) replay smoke에서 primary DB username만 `root` 방어하고 PII/query role은 그대로 두면, PostgreSQL 전환 뒤에도 일부 datasource만 부팅 실패한다
- 문제: replay wrapper가 `DB_USERNAME` 만 `app_core_rw` 로 바꾸고 `DB_MIGRATION_USERNAME`, `DB_APP_PII_USERNAME`, `DB_NOTIFICATION_PII_RO_USERNAME` 는 그대로 두면, `.env` 나 셸에 남은 `root` 값 때문에 primary datasource는 살아도 migration/query/PII datasource만 따로 죽을 수 있다.
- 해결: local replay script에 PostgreSQL role 정규화 helper를 추가해 `root` 또는 빈 값이면 각 역할의 기본 계정(`app_core_rw`, `migration_admin`, `app_pii_rw`, `notification_pii_ro`)으로 강제 치환하게 했다. `DB_QUERY_USERNAME` 도 정규화된 migration username을 따라가도록 맞췄다.
- 이유: PostgreSQL 다중 datasource 환경에서는 primary role만 맞는다고 충분하지 않다. smoke가 role 계층 전체를 같은 규칙으로 정규화해야 `.env` 오염이 부분 부팅 실패로 번지지 않는다.

## 595) replay 분석용 SQL이 추천 결과 0건 케이스를 `IN ()` 로 그대로 만들면, 실제 기능 실패 원인보다 SQL 문법 오류가 먼저 드러난다
- 문제: 추천 응답에서 `serviceId` 를 한 건도 못 모았을 때 분석 SQL이 `WHERE ws.id IN ()` 를 만들어 버리면, 원래 보고 싶던 건 “왜 추천이 비었는가” 인데 smoke는 PostgreSQL syntax error로 먼저 죽는다.
- 해결: replay script는 추천 결과에서 수집한 `service_ids` 가 비어 있으면 메타 TSV를 빈 파일로 만들고 바로 return 하도록 바꿨다. signup도 `409` 를 무조건 성공으로 보지 않고 `U001` 같은 진짜 duplicate case만 허용하게 같이 보강했다.
- 이유: 진단 스크립트는 실패 시나리오를 더 잘 보여줘야 한다. 0건 추천 같은 경계 케이스를 별도 처리하지 않으면, 관찰용 SQL이 본래 문제를 가리는 2차 오류를 만든다.

## 596) 복지로 지자체 정책은 단순 상세 링크만 있어도 `isOnlineApply=true` 로 잡히기 쉬워, “관련 사이트 있음”과 “온라인 신청 가능”을 혼동하면 정책 속성이 과대 분류된다
- 문제: `BOKJIRO_LOCAL` 수집 경로에서 `servDtlLink` 같은 상세 URL 존재 여부를 그대로 `inferOnlineApply()` 에 넣으면, 실제로는 안내 페이지 링크만 있는 정책도 모두 “온라인 신청 가능”으로 저장된다. 중앙 복지로는 별도 온라인 가능 플래그를 보는데 지자체만 링크 존재 여부를 근거로 과대 판정되는 불균형이 생긴다.
- 해결: 지자체 복지로는 상세 링크를 온라인 신청 판정 근거에서 제외하고, `aplyMtdNm` 텍스트에 있는 실제 신청 방식 신호만 기준으로 `isOnlineApply` 를 계산하게 바꿨다. 상세 링크는 그대로 `detailUrl` / `referenceUrlsJson` 으로 남기되, 신청 가능 여부 boolean과 분리했다.
- 이유: 링크 존재는 탐색용 CTA 신호이고, 온라인 신청 가능은 자격/절차 신호다. 둘을 같은 판정 함수에 태우면 “열람 가능한 링크”와 “실제 온라인 접수 가능”이 섞여서 사용자에게 잘못된 기대를 준다.

## 597) 챗봇 branch/clarification 메타를 POST 응답에만 두면, 세션 새로고침 뒤에는 같은 메시지가 평문으로만 돌아와 대화 UX가 복구되지 않는다
- 문제: branch suggestion 과 clarification 상태가 `sendMessage()` 의 단발 응답에만 있고 `getMessages()` 재조회 DTO에는 없으면, 새로고침이나 세션 재선택 뒤에는 assistant message가 그냥 텍스트 한 줄로만 보인다. 그러면 branch chip, clarification 안내 같은 UI는 현재 세션 메모리에 남아 있을 때만 잠깐 동작하고, 실제 저장된 대화 기록에서는 재현되지 않는다.
- 해결: `chat_retrieval_snapshots` 의 `question`, `branchSuggestionKeysJson`, `resultCount` 를 이용해 `getMessages()` 단계에서 assistant message 메타를 복원하도록 바꿨다. 프론트도 마지막 assistant message의 `answerMode`, `needsClarification`, `branchSuggestions` 를 다시 읽어, 세션 재조회 후에도 같은 분기/clarification UI를 재구성하게 맞췄다.
- 이유: 대화형 retrieval UX는 “즉시 응답”뿐 아니라 “나중에 다시 열었을 때도 같은 상태를 복원할 수 있는가”가 중요하다. 메타가 저장 모델이나 복원 경로에 없으면 UI는 동작한 것처럼 보여도 실제 기록 재현성은 깨진다.

## 598) `referenceUrlsJson` 을 저장만 하고 상세 화면에서 안 풀어 쓰면, backfill 성공 이후에도 사용자는 여전히 “링크 없음”으로 본다
- 문제: 대표 `detailUrl` 이 비어 있고 후보 URL 풀만 살아 있는 정책은 backend에서 `referenceUrlsJson` 까지 내려줘도, 프론트가 `homepageUrl` 하나만 링크 버튼으로 쓰면 화면상으로는 계속 링크가 없는 정책처럼 보인다. 이 상태에서는 canonical 보존 작업을 했는데도 실제 사용자 체감은 변하지 않는다.
- 해결: 상세 화면이 `referenceUrlsJson` 을 파싱해 dedupe된 추가 링크 목록을 `추가 정보`와 sidebar CTA로 노출하도록 연결했다. `homepageUrl` 이 없더라도 후보 URL이 있으면 최소 1개의 “추가 링크 보기” 버튼이 뜨게 맞췄다.
- 이유: additive 필드는 “저장”과 “노출”이 같이 닫혀야 의미가 있다. 특히 URL 후보 풀은 대표 링크가 비어 있는 정책을 살리기 위한 보완 계약이므로, 화면에서 fallback CTA로 이어지지 않으면 백엔드 보존 가치가 반쯤 사라진다.

## 599) PostgreSQL main에서 예전 MySQL draft apply 스크립트를 그대로 다시 태우면, integrated schema가 이미 있는데도 legacy SQL 때문에 잘못된 재적용 경로로 들어간다
- 문제: `deploy/mysql/apply-local-policy-sidecar-draft.sh` 는 원래 MySQL 시절 draft sidecar DDL/backfill을 local DB에 덮어쓰던 스크립트다. 현재 메인라인은 PostgreSQL `schema.sql` 에 sidecar schema가 통합돼 있는데, 이 스크립트를 그대로 실행하면 `mysql` 전용 DDL/`ON DUPLICATE KEY UPDATE`/옛 포트 전제를 다시 타게 되어 현재 main 기준 검증과 어긋난다.
- 해결: 스크립트가 PostgreSQL runtime을 감지하면 legacy MySQL draft SQL 재적용은 건너뛰고, 현재 integrated schema에 `service_taxonomies`, `service_taxonomy_summary_slots` 가 실제로 있는지만 검증하게 바꿨다. missing이면 “현행 PostgreSQL 부트스트랩/collect flow를 써야 한다”는 명시적 오류를 내도록 fail-fast 경계를 세웠다.
- 이유: 전환 이후 가장 위험한 건 “낡은 복구 스크립트가 조용히 다시 실행되는 것”이다. 현재 메인라인에 이미 흡수된 draft 경로는 자동 적용보다 명시적 검증/차단이 안전하다.

## 600) React callback이 state 객체를 dependency로 물고 있으면, 후속 state merge만으로도 같은 API를 다시 부르는 간접 루프가 생길 수 있다
- 문제: 채팅 화면에서 `enrichPolicyMeta()` 가 `policyMeta` 를 dependency로 가진 상태에서 `loadMessages()` 가 그 callback에 의존하면, 정책 메타를 한 번 합칠 때마다 `loadMessages` 참조가 바뀌고 `useEffect(loadMessages(activeSessionId))` 가 다시 발동한다. 결과적으로 같은 세션 메시지를 불필요하게 반복 조회하는 간접 루프가 생긴다.
- 해결: 메타 존재 여부 판단은 `useRef` 로 분리하고, 실제 state merge는 functional update로만 처리하게 바꿨다. 이렇게 하면 메타 캐시는 유지하면서도 callback identity는 안정적으로 고정된다.
- 이유: 비동기 조회 결과를 같은 화면 state에 축적하는 구조에서는 “조회 중복 방지 cache” 와 “렌더링 state” 를 분리해야 한다. 둘을 같은 dependency 체인에 묶으면 API 재호출 루프가 숨어들기 쉽다.

## 601) 세션/메시지 목록이 비어질 때 보조 UI 메타를 같이 초기화하지 않으면, 직전 대화의 branch/clarification alert가 빈 화면에 남는다
- 문제: 채팅 세션이 삭제되거나 메시지 조회가 실패해도 `messages` 만 비우고 `latestAnswerMeta` 를 유지하면, 실제 대화가 없는 상태에서도 직전 assistant message의 branch suggestion 혹은 clarification alert가 입력창 상단에 계속 남을 수 있다.
- 해결: `sessionId` 가 없을 때, 세션 목록 fetch 실패 시, 마지막 세션 삭제 시, 메시지 fetch 실패 시 모두 `latestAnswerMeta` 를 함께 `null` 로 초기화하도록 맞췄다.
- 이유: 보조 UI 메타는 대화 메시지의 파생 상태다. 기반 데이터가 사라졌는데 파생 상태만 남기면 사용자는 “현재 세션의 상태”로 오해하게 된다.

## 602) fallback용 container 이름을 새 변수로 정리했다면, 옛 변수 참조가 shell 한 군데만 남아 있어도 `set -u` 에서 바로 죽는다
- 문제: draft apply script가 `DB_CONTAINER_NAME` 로 정리된 뒤에도 MySQL docker exec fallback 일부가 계속 `MYSQL_CONTAINER_NAME` 을 직접 읽고 있으면, PostgreSQL happy path 밖의 fallback 실행 시 `unbound variable` 로 바로 실패한다.
- 해결: mysql fallback docker exec 도 전부 `DB_CONTAINER_NAME` 을 사용하도록 통일했다.
- 이유: shell script는 `set -u` 아래에서 “거의 다 바뀐 변수명”이 가장 위험하다. 새 이름으로 표준화했다면 fallback branch까지 완전히 끊어야 한다.

## 603) PostgreSQL main에서 replay smoke의 `RECONCILE_LOCAL_DB_ACCOUNTS=true` 를 그대로 켜면, 검증이 아니라 예전 MySQL 계정 복구 스크립트를 잘못 호출하게 된다
- 문제: `run-local-education-priority-replay.sh` 는 기본값으론 PostgreSQL local URL/role을 쓰지만, 옵션 `RECONCILE_LOCAL_DB_ACCOUNTS=true` 가 켜지면 여전히 `deploy/mysql/reconcile-local-runtime-db-accounts.sh` 를 호출한다. 이 스크립트는 MySQL volume, `mysql:8.0`, `mysql` CLI, grant table 복구를 전제로 하므로 PostgreSQL main에서는 전혀 맞지 않는 경로다.
- 해결: replay smoke는 `DB_URL` 이 PostgreSQL일 때 `RECONCILE_LOCAL_DB_ACCOUNTS=true` 를 즉시 실패시키고, 이 옵션이 legacy MySQL-only 경로라는 메시지를 명시적으로 출력하게 바꿨다.
- 이유: 이런 옵션은 “지금은 안 쓰지만 혹시 필요할 수 있는 fallback” 으로 남아 있을수록 더 위험하다. 현재 mainline과 안 맞는 복구 경로는 조용히 시도하게 두는 것보다 early fail로 막는 편이 안전하다.

## 604) runtime cutover preflight가 여전히 `jdbc:mysql://...` 만 허용하면, 현재 PostgreSQL main 기준의 정상 `.env` 도 잘못된 것으로 판정한다
- 문제: `preflight-runtime-cutover-env.sh` 가 `DB_URL`, `APP_PII_DB_URL`, `NOTIFICATION_PII_DB_URL` 를 모두 `jdbc:mysql://...` 형태로만 파싱하면, 현재 main의 정상 PostgreSQL URL(`jdbc:postgresql://...`, `currentSchema=youth_welfare_pii`)을 넣어도 preflight가 실패한다.
- 해결: preflight가 이제 `jdbc:mysql://` 와 `jdbc:postgresql://` 를 모두 파싱한다. core runtime은 `youth_welfare` DB/public schema를, PII runtime은 MySQL에서는 `youth_welfare_pii` DB를, PostgreSQL에서는 `youth_welfare` DB + `currentSchema=youth_welfare_pii` 를 요구하도록 정리했다. summary 출력도 PostgreSQL schema까지 같이 보여주게 바꿨다.
- 이유: preflight는 현재 mainline을 기준으로 “정상 env인가”를 판정해야 한다. 메인라인이 바뀌었는데도 예전 JDBC 문법만 강제하면, 스크립트는 안전장치가 아니라 거짓 실패 생성기가 된다.

## 605) MySQL 시절 migration 메모를 현재 실행 문서처럼 그대로 두면, PostgreSQL main에서 운영자가 legacy 절차를 따라가며 잘못된 수동 작업을 할 수 있다
- 문제: `docs/core/db-migration.md` 에는 draft sidecar SQL, `mysql -h ...`, `docker exec ... mysql ...`, `SHOW TABLES`, `ANALYZE TABLE` 같은 예전 MySQL 예시가 많이 남아 있다. 이 문서를 현재 main 실행 문서처럼 읽으면 PostgreSQL integrated schema / admin rebuild 흐름과 충돌한다.
- 해결: 문서 상단에 “현재 main은 PostgreSQL, 이 문서는 legacy MySQL migration history” 라는 경계를 명시하고, `current-state`, PostgreSQL playbook, `schema.sql`, local validation 문서로 먼저 보내도록 바꿨다. MySQL 예시 섹션도 `legacy MySQL only` 라벨을 붙였다.
- 이유: 오래된 운영 문서는 코드보다 늦게 사고를 만든다. 실행 경로가 바뀐 뒤에는 문서가 스스로 현재 truth와 legacy history를 구분해야 한다.

## 606) 챗봇 응답이 POST 직후에는 `references` 를 주는데 세션 재조회 DTO에는 ID만 남기면, 새로고침 뒤에는 근거 카드가 절반만 복원된다
- 문제: `ChatAnswerResponse` 는 `references[serviceId,title,reason,evidence]` 를 내려주지만 `ChatMessageResponse` 가 `referencedServiceIds` 만 가지면, 프론트는 새로고침 뒤 reference title/reason/evidence 를 다시 받을 수 없다. 그 결과 첫 응답에서는 연결 정책 설명이 보이는데, 세션 재조회 후에는 같은 카드가 상세 조회 fallback 설명으로 후퇴한다.
- 해결: assistant message 저장 시 `referencesJson` 도 같이 보존하고, `ChatMessageResponse` 에 `references` 필드를 추가해 `GET /messages` 가 POST와 같은 reference payload를 다시 주도록 맞췄다.
- 이유: 대화형 UI에서 “처음 응답 계약”과 “세션 재조회 계약”이 다르면, 기능은 되는 것처럼 보여도 실제 상태 복원성은 깨진다.

## 607) 같은 `PolicySummaryResponse` 를 써도 북마크 화면이 목록 화면보다 source fallback을 덜 쓰면, 특정 source 정책만 정보가 빈약해 보인다
- 문제: 정책 목록은 `hostOrg -> sido -> applyMethodName` fallback을 쓰는데 북마크 화면은 `hostOrg -> applyMethodName` 만 보면, `BOKJIRO_LOCAL` 같이 `hostOrg` 가 비고 `sido/operatingOrg` 가 중요한 정책이 북마크 탭에서만 출처가 덜 보인다.
- 해결: 북마크 `mapBookmark()` 도 `hostOrg -> sido -> operatingOrg -> applyMethodName` 순서로 source를 해석하게 맞췄다.
- 이유: 같은 DTO를 여러 화면이 공유한다면, 화면마다 fallback 정책이 달라지는 순간 “API는 맞는데 어떤 화면만 이상한” 상태가 된다.

## 608) 프로필 완성도를 프론트가 다시 추정하면, 백엔드가 이미 계산한 `profileCompleteness` 와 화면 표시가 어긋날 수 있다
- 문제: 백엔드는 `ProfileResponse.profileCompleteness` 를 내려주는데 프론트가 birth/region/income/employ/priority 만 다시 세어 퍼센트를 계산하면, household type / interest field 같은 서버 기준이 바뀌었을 때 동일 사용자라도 화면 퍼센트가 서버와 다를 수 있다.
- 해결: 마이페이지는 서버가 내려준 `profileCompleteness` 가 있으면 그 값을 우선 사용하고, 없을 때만 로컬 추정값으로 fallback 하게 바꿨다.
- 이유: 파생값은 가능하면 계산한 쪽을 single source of truth로 삼는 편이 계약 불일치를 줄인다.

## 609) 추천 DTO가 정책 카드보다 지나치게 얇으면 추천 화면은 뜨더라도 `D-Day` 와 출처가 조용히 죽는다
- 문제: 추천 목록이 `status/title/description` 만 내려주고 `applyEndDate`, `hostOrg`, `operatingOrg`, `sido` 같은 카드 메타를 누락하면, 프론트는 추천 카드를 그릴 수는 있어도 실제 마감일 기반 `D-Day` 를 계산하지 못하고 출처 줄도 비어 있게 된다. 이 경우 기능 자체는 성공처럼 보여도 추천 화면 품질은 정책 목록보다 눈에 띄게 떨어진다.
- 해결: `RecommendationResponse` 에 `applyEndDate`, `hostOrg`, `operatingOrg`, `sido` 를 추가하고, 추천 컨트롤러가 `service_regions` 에서 `sido` 를 함께 조회해 응답에 포함하도록 맞췄다. 메인 추천 카드도 이제 `formatDday(...)` 와 `hostOrg -> sido -> operatingOrg` fallback 을 실제로 사용한다.
- 이유: 추천도 결국 정책 카드의 한 변형이다. 목록/상세와 다른 화면이라도 사용자가 보는 최소 메타는 같은 수준으로 내려줘야 UI 품질과 계약 일관성이 유지된다.

## 610) 프로필 수정이 `sido/sgg` 만 바꾸고 `regionCode` 를 다시 계산하지 않으면, 추천 지역 매칭은 예전 행정구역 코드를 계속 쓴다
- 문제: 추천 후보 탐색은 `regionCode` 를 우선 사용한다. 그런데 마이페이지 수정이 `sido/sgg` 만 바꾸고 `regionCode` 는 보내지 않거나 예전 값을 유지하면, 사용자는 부산으로 바꿨는데 추천 쿼리는 계속 서울 코드를 쓰는 식의 stale 상태가 생길 수 있다.
- 해결: 프론트는 회원가입과 같은 full sido 명(`서울특별시`, `부산광역시`)을 보내도록 맞췄고, 서버는 `regionCode` 요청이 없더라도 `sido/sgg` 변경이 있으면 `RegionCodeUtil.getRegionCode(...)` 로 다시 계산하게 바꿨다.
- 이유: 지역 정보는 표시용 필드와 추천용 필드가 분리돼 있기 때문에, 둘 중 하나만 갱신하면 조용한 정합성 버그가 된다. 이 축은 저장 시점에서 같이 동기화돼야 한다.

## 611) `householdType` 를 영어 코드로 저장해도 추천 매칭이 한글 substring만 보면, 1인가구/한부모 정책이 조용히 빗나간다
- 문제: 프로필 저장 값이 `ONE_PERSON`, `SINGLE`, `MULTI_PERSON` 같은 코드인데 추천 매칭은 `1인`, `한부모`, `다자녀`, `조손` 같은 한글 신호만 보면, DTO와 DB에는 값이 있어도 실제 추천 점수/필터 매칭은 동작하지 않을 수 있다.
- 해결: 추천 매칭 쪽에서 영어 코드와 한글 설명값을 함께 정규화해 인식하도록 바꿨고, 마이페이지에도 `가구 형태` 입력 UI를 추가해 이후 저장값은 추천 규칙과 바로 연결되게 맞췄다.
- 이유: 이 문제는 “필드가 없는 것”보다 더 위험하다. 값은 저장되고 API도 정상인데, 추천 로직만 다른 표현 체계를 보고 있으면 품질 저하가 조용히 누적된다.

## 612) 프로필 DTO에 `interestFields/targetTypes/notificationMinScore/displayCount` 가 있어도 화면이 안 쓰면, 추천/알림 규칙은 기본값으로만 굳는다
- 문제: 백엔드는 관심 분야, 특화 대상, 알림 최소 점수, 발송 개수를 이미 저장/조회하지만 마이페이지가 이 필드를 전혀 노출하지 않으면 사용자는 값을 바꿀 수 없고, 추천/알림 로직은 결국 서버 기본값에만 묶인다.
- 해결: 마이페이지 우선순위 탭에 `관심 분야`, `특화 대상` multi-select를 추가하고, 알림 탭에 `최소 추천 점수`, `한 번에 받을 정책 수` 설정을 추가해 `/api/users/me` 계약과 실제 UI를 연결했다.
- 이유: DTO에만 있는 값은 “나중에 쓸 필드”가 아니라 거의 항상 죽은 계약이 된다. 추천과 알림처럼 사용자 조정이 핵심인 설정은 저장/조회와 수정 UI가 같이 있어야 의미가 있다.

## 613) admin dashboard summary의 collect 섹션만 `failureWindowDays` 라는 별도 이름을 쓰면, 같은 `summaryWindowDays` 계약인데도 collect만 다른 의미처럼 읽히기 쉽다
- 문제: recommendation/search/notification summary는 모두 `windowDays` 를 쓰는데 collect만 `failureWindowDays` 로 남아 있으면, 실제로는 같은 요청 파라미터(`summaryWindowDays`)를 쓰더라도 소비자는 collect만 별도 기간이나 실패 전용 기간처럼 오해할 수 있다.
- 해결: collect summary DTO도 `windowDays` 로 통일하고, WebMvc 테스트와 admin dashboard smoke도 같은 이름을 검증하도록 맞췄다.
- 이유: 값이 맞아도 이름이 다르면 계약은 반쯤 깨진 상태다. 특히 admin/read-model 응답은 소비자가 필드명만 보고 의미를 해석하므로, 같은 의미는 같은 이름을 써야 한다.

## 614) `/api/admin/dashboard/recommendation-breakdowns` 의 `totalLogs/clickedLogs/fallbackLogs` 는 실제 window 집계인데 이름만 보면 전체 누적처럼 보인다
- 문제: recommendation breakdown 서비스는 모두 `summaryWindowDays` 기준 집계를 반환하지만, DTO 필드가 `totalLogs`, `clickedLogs`, `fallbackLogs` 로만 되어 있으면 운영자가 전체 누적과 기간 집계를 혼동할 수 있다.
- 해결: DTO를 `sentLogsInWindow`, `clickedLogsInWindow`, `fallbackLogsInWindow` 로 명시적으로 바꾸고, 관련 테스트도 같은 semantics 를 검증하게 맞췄다.
- 이유: admin triage 응답은 수치 자체보다 “이 숫자가 어느 기간/분모를 뜻하는가”가 더 중요하다. 기간 의미가 이름에 드러나지 않으면 잘못된 운영 판단으로 이어질 수 있다.

## 615) `/api/admin/dashboard/search-failures` 의 `totalZeroResultSearches` 는 실제로는 window 집계인데 이름만 보면 전역 누적처럼 읽힌다
- 문제: search failure 응답은 `windowDays` 를 같이 주는데도 핵심 count 필드가 `totalZeroResultSearches` 로 되어 있으면, 소비자는 전체 누적 zero-result 횟수로 오해할 수 있다.
- 해결: 해당 필드를 `zeroResultSearchesInWindow` 로 바꾸고 WebMvc/서비스 테스트도 같은 이름을 검증하게 맞췄다.
- 이유: 실패 분석 응답에서 분모와 기간을 잘못 읽으면 triage 우선순위 자체가 흔들린다.

## 616) `/api/admin/dashboard/collect-failures` 의 `jobStreaks` 는 window 집계가 아니라 current streak 인데, 이름만 보면 summary window 안의 streak로 오해하기 쉽다
- 문제: `collect-failures` 응답은 `windowDays` 를 같이 주지만 `jobStreaks` 는 현재 연속 `FAILED/PARTIAL_SUCCESS` 상태를 window와 무관하게 계산한다. 이름만 보면 같은 기간 안의 streak처럼 읽혀 운영자가 최근 14일 streak라고 착각할 수 있다.
- 해결: DTO를 `currentJobStreaks` 로 명시하고, window 집계 count도 `failedJobsInWindow`, `partialSuccessJobsInWindow` 로 맞춰 window 값과 current 상태 값을 구분했다.
- 이유: 한 응답 안에 `window 집계`와 `현재 상태`가 섞일 때는 이름으로 경계를 강하게 드러내지 않으면, 수치는 맞아도 의미 해석이 틀어진다.

## 617) 프론트가 우선순위를 전부 지운 뒤 저장을 허용하면, 백엔드 `UpdatePrioritiesRequest` 의 `@Size(min = 1)` 와 바로 충돌한다
- 문제: 마이페이지는 우선순위 chip을 모두 제거할 수 있었고 저장 버튼도 그대로 눌릴 수 있었다. 하지만 `/api/users/me/priorities` 는 `priorityCodes` 를 1~5개로만 허용하므로, 이 상태에서는 사용자가 조용히 400만 맞게 된다.
- 해결: 프론트 저장 전에 `priorities.length === 0` 을 막고, WebMvc 테스트로도 빈 리스트가 400이라는 계약을 고정했다.
- 이유: 이런 종류는 백엔드 validation이 틀린 게 아니라, 프론트가 그 계약을 모르고 있어서 생기는 전형적인 request DTO 불일치다.

## 618) 비밀번호 최대 길이 계약이 `signup / change / reset` 경로마다 다르면, 같은 사용자가 어떤 화면에서는 성공하고 어떤 화면에서는 400을 맞는다
- 문제: 회원가입은 최대 길이 제한이 없고, 비밀번호 변경은 64자, 재설정은 100자로 달라져 있었다. 이 상태에서는 80자 비밀번호가 가입 후엔 저장되지만, 이후 마이페이지 변경에서는 거부되는 식의 경로별 불일치가 생긴다.
- 해결: `SignupRequest`, `ChangePasswordRequest`, `PasswordResetConfirmRequest` 를 모두 `8~100자`로 맞추고, 프론트 회원가입/재설정/비밀번호 변경 화면도 같은 상한을 사전에 검사하게 정리했다.
- 이유: 인증 계층 입력 제약은 경로가 달라도 하나의 계약으로 움직여야 한다. 비밀번호 정책이 화면마다 다르면 UX도 깨지고 운영 support 비용도 커진다.

## 619) `SignupRequest` 에 `householdType` 가 있어도 회원가입 화면이 그 값을 전혀 받지 않으면, 초기 추천 프로필은 절반만 채워진 상태로 시작한다
- 문제: 백엔드 회원가입 DTO와 저장 경로는 `householdType` 를 이미 지원했지만, 회원가입 UI는 생년월일/지역/소득/취업상태만 받고 가구 형태는 마이페이지에 가서야 수정할 수 있었다.
- 해결: 회원가입 2단계에 `가구 형태` 선택 UI를 추가하고, `SignupRequest.householdType` 로 실제 전송되게 연결했다.
- 이유: 입력 DTO에 이미 있는 사용자 프로필 축이면, 최소한 초기 가입에서 수집할지 명시적으로 포기할지 결정돼 있어야 한다. 지금처럼 DTO만 열려 있으면 초기 추천 품질이 조용히 낮아진다.

## 620) 마이페이지의 로컬 profile completion fallback이 서버 계산식과 다르면, 저장 직후 퍼센트가 잠깐 다른 값으로 보일 수 있다
- 문제: 화면은 평소엔 서버 `profileCompleteness` 를 쓰지만, 저장 직후 이를 비우면 로컬 fallback으로 내려간다. 그런데 그 fallback이 `우선순위` 를 포함하고 `householdType/interestFields` 를 빼고 있으면 서버 계산과 다른 퍼센트와 미완성 항목을 보여줄 수 있다.
- 해결: 로컬 fallback도 `name/birthDate/sido/incomeLevel/employmentStatus/householdType/interestFields` 기준과 같은 가중치로 맞추고, 누락 라벨도 같은 축을 보게 정리했다.
- 이유: 파생값은 가능하면 서버 값을 single source로 삼아야 하지만, fallback이 필요하다면 최소한 같은 계산식을 복제해야 저장 직후 깜빡이는 정합성 오류를 줄일 수 있다.

## 621) 마이페이지 알림/필터 탭에 서버가 모르는 토글을 그대로 두면, 사용자는 “저장됐다”고 믿지만 실제로는 어디에도 반영되지 않는다
- 문제: `브라우저 푸시`, `SMS`, `새 맞춤 정책`, `마감 D-7`, `이벤트·공지`, 기본 정렬/표시 방식/출처 같은 UI는 있었지만, 실제 request DTO나 저장 경로는 이 값을 전혀 받지 않았다. 화면만 보면 저장된 것처럼 보이지만 다음 진입이나 다른 기기에서는 그대로 사라진다.
- 해결: 백엔드가 실제로 지원하는 필드(`notificationYn`, `notificationPeriod`, `notificationMinScore`, `displayCount`)만 남기고, 필터 탭도 현재 실제 저장되는 `includeExpired`만 노출하게 정리했다.
- 이유: 미구현 기능을 “UI만 있는 상태”로 두면 버그보다 더 나쁘다. 사용자는 성공했다고 믿는데 시스템 state는 바뀌지 않기 때문이다.

## 622) 챗 메시지 request DTO가 `2000자 이하`인데 프론트 입력창이 그 상한을 모르면, 사용자는 전송 버튼까지 누른 뒤에야 400을 맞는다
- 문제: `SendChatMessageRequest.content` 는 2000자 상한을 갖고 있지만, 챗 화면 입력창은 길이 제한이나 카운터가 없어 긴 질문을 그대로 보낼 수 있었다. 이 경우 서버는 validation으로 막지만 사용자는 “입력은 됐는데 왜 실패했지” 상태가 된다.
- 해결: 프론트 챗 입력창에 `maxLength=2000` 과 길이 카운터를 추가하고, 전송 직전에도 2000자 초과면 toast로 막게 했다.
- 이유: 텍스트 입력 상한은 대표적인 request DTO 계약이다. 서버에서만 알고 프론트가 모르면 에러 처리는 맞아도 UX는 틀린 상태가 된다.

## 623) 관리자 `forced-logout` 와 `pii-sync-replay/status` 입력을 느슨하게 받으면, 잘못된 `userKey/limit` 가 서비스 로직까지 흘러 들어간다
- 문제: `user_key` 는 DB 기준 32자인데 관리자 API는 `forced-logout` body와 `pii-sync-replay` query param에서 길이 제한을 명시하지 않았고, replay/status limit도 범위 밖 값이 들어와도 서비스 내부에서 조용히 보정됐다.
- 해결: 컨트롤러에서 `userKey <= 32`, `1 <= limit <= 1000`, `1 <= failedSampleLimit <= 20` 경계를 명시적으로 검증하고, 범위를 벗어나면 `C001` 로 바로 거절하게 했다.
- 이유: 관리자 API라고 해서 입력 계약이 느슨하면 안 된다. 잘못된 값이 “어떻게든 돌아간다”는 상태는 smoke와 운영 스크립트가 실제 계약을 오해하게 만든다.

## 624) 대시보드 query param 을 서비스에서 조용히 default로 되돌리기만 하면, 호출자는 자기 요청 값이 반영됐다고 착각할 수 있다
- 문제: `summaryWindowDays`, `trendWindowDays`, `limit` 는 서비스 레이어에서 범위를 보정하고 있었지만, 컨트롤러는 잘못된 입력을 그대로 받았다. 예를 들어 `summaryWindowDays=366` 이나 `trendWindowDays=0` 도 200으로 응답할 수 있어 호출자는 자신의 값이 그대로 적용된 줄 알 수 있다.
- 해결: 대시보드 컨트롤러에서 `1..365`, `1..20` 범위를 먼저 검증하고, 잘못된 값은 `C001` 로 실패시키게 바꿨다.
- 이유: “default로 보정”은 내부 안전장치로는 유용하지만, 외부 API 계약까지 대신하면 안 된다. 요청이 잘못됐으면 우선 거절하고, 정상 요청만 서비스의 default 정책을 타게 해야 계약이 선명해진다.

## 625) request body validation 만 400으로 매핑하고 `ConstraintViolationException` 을 놓치면, query param 계약 위반이 전부 500으로 새어 나온다
- 문제: `@RequestBody` 의 `MethodArgumentNotValidException` 은 400으로 잘 처리돼도, `@RequestParam`/method validation 은 `ConstraintViolationException` 으로 올라온다. 전역 예외 핸들러가 이걸 모르면 `limit=0`, `failedSampleLimit=21` 같은 단순 입력 오류가 `500/C002` 로 보인다.
- 해결: `GlobalExceptionHandler` 에 `ConstraintViolationException -> 400/C001` 매핑을 추가했다.
- 이유: request DTO 계약 점검에서 query param validation도 같은 축이다. body만 400이고 query는 500이면 API 소비자는 같은 종류의 입력 오류를 전혀 다르게 해석하게 된다.

## 626) `welfare_service_details` 에 `supportCycle/provisionType` 를 저장해도 상세 DTO가 계속 `welfare_services` 값만 읽으면, detail 보강이 API에서 조용히 사라진다
- 문제: 복지로 detail 수집은 `supportCycle`, `provisionType` 을 detail row에도 저장한다. 그런데 `PolicyDetailResponse` 가 이 필드를 `WelfareService` 쪽 값으로만 읽으면, service row가 비어 있거나 stale한 경우 detail에서 보강한 값이 상세 API에 전혀 나타나지 않는다.
- 해결: 상세 DTO는 `detail.supportCycle/provisionType` 를 우선하고, 없을 때만 `service` 값으로 fallback 하게 바꿨다.
- 이유: detail row를 따로 두는 목적은 원본 상세 보강을 보존하려는 것이다. 응답 조립 단계에서 다시 요약 row만 보면 그 목적이 무너진다.

## 627) 같은 `PolicySummaryResponse` 를 메인 비로그인 카드와 추천 카드가 다르게 소비하면, 어떤 화면만 출처 줄이 빈다
- 문제: 추천 카드는 이미 `hostOrg -> sido -> operatingOrg` fallback 을 쓰는데, 메인 비로그인 화면의 마감임박 카드만 `hostOrg -> sido` 까지만 보면 `operatingOrg` 만 있는 정책은 그 화면에서만 출처가 비어 보일 수 있다.
- 해결: 메인 마감임박 카드도 `hostOrg -> sido -> operatingOrg` 순으로 source를 해석하게 맞췄다.
- 이유: 같은 응답 필드를 여러 카드가 공유하면, 화면별 fallback 규칙 차이가 곧 “특정 화면만 이상한” 버그가 된다.

## 628) `UserProfile` 에 이미 계산된 `ageBand` 와 `notificationConsentAt` 이 있어도 `ProfileResponse` 로 안 올리면, 마이페이지는 같은 정보를 다시 추정하거나 아예 못 보여준다
- 문제: 프로필 read model은 연령대와 최근 알림 동의 시각을 이미 저장하고 있었지만, `ProfileResponse` 가 이 필드를 내리지 않으면 프론트는 생년월일만 보고 연령 관련 문맥을 다시 추정하거나, 알림 동의 이력을 전혀 보여줄 수 없다.
- 해결: `ProfileResponse` 와 프로필 조회 테스트에 `ageBand`, `notificationConsentAt` 를 추가하고, 마이페이지에서 이를 읽기 전용 메타로 노출했다.
- 이유: 파생값을 서버가 이미 계산해 저장한다면, 읽기 API도 그 값을 single source 로 노출하는 편이 맞다. 같은 정보를 프론트가 다시 유추하기 시작하면 계약이 약해지고 화면마다 표현이 어긋난다.

## 629) `UserProfile.hasPhone` 이 projection 에 있어도 응답으로 안 올리면, 프로필 완성도에 영향을 주는 연락처 상태가 사용자에게는 보이지 않는다
- 문제: user profile projection 은 `hasPhone` 으로 연락처 등록 여부를 이미 알고 있었지만, 프로필 응답이 이를 숨기고 있으면 마이페이지는 “왜 완성도가 덜 찼는지” 설명할 단서를 잃는다.
- 해결: `ProfileResponse` 에 `hasPhone` 을 추가하고, 마이페이지 기본 정보 섹션에 연락처 등록 상태를 read-only 로 노출했다.
- 이유: 직접 수정 경로가 아직 없더라도, 시스템이 이미 계산한 completeness 관련 상태는 최소한 읽을 수 있어야 사용자와 운영자가 현재 profile state 를 해석할 수 있다.

## 630) 챗 `POST` 응답은 clarification 이었는데 `GET /messages` 가 `resultCount==0 && references empty` 로만 추론하면, 참조 정책이 함께 있는 clarification 이 새로고침 뒤 일반 grounded 답변처럼 보일 수 있다
- 문제: AI 응답은 `needsClarification=true` 이면서도 참고 정책을 몇 개 같이 줄 수 있다. 그런데 세션 재조회가 clarification 여부를 `snapshot.resultCount==0` 과 빈 reference 만으로 추론하면, 이런 케이스는 answerMode 가 사라지고 추가 질문 유도 UI도 복원되지 않는다.
- 해결: retrieval snapshot 에 `needsClarification` 을 명시적으로 저장하고, `GET /messages` 는 이 플래그를 우선 복원하게 바꿨다.
- 이유: 챗의 상호작용 메타는 “결과 수” 같은 간접 신호로 다시 추론하기보다, 최초 응답 시점의 의도를 그대로 저장해 재사용하는 편이 계약이 강하다.

## 631) 추천 응답이 canonical projection 의 `summary` 를 무시하고 `welfare_services.description` 만 내려주면, detail 보강이 있어도 메인 추천 카드 설명이 비게 된다
- 문제: 추천 read model projection 은 `support_detail -> support_content -> description` 순으로 요약 문구를 이미 만들고 있었다. 그런데 `RecommendationResponse` 가 여전히 `service.description` 만 사용하면, 서비스 요약 컬럼이 비어 있는 정책은 메인 추천 카드에서 설명이 사라진다.
- 해결: 추천 DTO는 projection 이 있으면 그 `summary` 를 우선 사용하고, 없을 때만 `supportContent`, `description` 순으로 fallback 하게 바꿨다.
- 이유: projection 을 읽는 목적은 저장된 보강 정보를 응답까지 끌어오는 데 있다. DTO 조립이 다시 base entity 필드만 보면 read model 보강이 화면 직전에서 사라진다.

## 648) 챗에서 들어온 정책 상세가 로그인/마이페이지 CTA를 탈 때 `chatFrom` 을 안 넘기면, 인증이나 프로필 등록 뒤 전역 `챗봇` 이 원래 세션을 다시 못 연다
- 문제: `PolicyDetailPage` 의 `자격 확인하기` CTA는 비로그인일 때 `/login`, 로그인 상태에서는 `/mypage` 로 보내지만, 둘 다 현재 상세의 `from` 만 싣고 챗 상위 문맥 `chatFrom` 은 넘기지 않았다. 동시에 `LoginPage` 는 로그인 성공 후 `postLoginAction` 만 원래 화면 state로 되돌려서, 챗에서 시작한 인증 플로우가 끝나면 전역 `챗봇` 이 더 이상 원래 `?session=` 세션을 알 수 없었다.
- 해결: `PolicyDetailPage` 의 로그인/마이페이지 CTA가 모두 `chatFrom` 을 함께 넘기게 맞췄고, `LoginPage`, `SignupPage`, `ResetPasswordPage` 도 이 값을 인증 경로 사이와 로그인 완료 후 복귀 state까지 계속 보존하게 정리했다.
- 이유: 챗 상위 문맥은 정책 화면 안에서만 유지돼서는 부족하다. 상세에서 로그인이나 마이페이지 같은 우회 경로를 타더라도 같은 세션으로 돌아갈 수 있어야 실제 사용자 동선에서 문맥 보존이 완성된다.

## 647) 챗에서 정책 목록을 거쳐 상세로 들어갈 때 `from` 을 목록 경로로만 덮어쓰면, 뒤로가기는 맞아도 헤더/FloatingNav `챗봇` 이 원래 세션을 다시 못 연다
- 문제: `ChatPage` 는 `/policies` 로 갈 때 `from=/chat?session=...` 을 넘기지만, `PoliciesPage` 가 상세로 이동할 때 이 값을 현재 `/policies?...` 로 덮어써 버리고 있었다. 그 결과 상세의 상단 뒤로가기는 목록으로 정상 복귀해도, 헤더나 `FloatingNav` 의 `챗봇` 버튼은 더 이상 원래 `?session=` 문맥을 복원할 수 없었다.
- 해결: 챗 상위 복귀 문맥을 `chatFrom` 으로 분리해 `ChatPage -> PoliciesPage -> PolicyDetailPage` 와 관련 정책 연속 이동까지 함께 전달하도록 맞췄다. `Header`, `FloatingNav` 는 이제 `state.chatFrom` 을 우선 읽어 챗 target을 계산한다.
- 이유: `from` 하나로 즉시 복귀 대상과 더 상위의 세션 문맥을 동시에 표현하면 중간 화면에서 쉽게 덮어써진다. 목록 복귀와 챗 세션 복귀는 목적이 다르므로 state도 분리하는 편이 안전하다.

## 646) 챗에서 `정책 목록 보기` 가 현재 세션 문맥 없이 고정 `"/policies"` 로만 가면, 정책 탐색을 거친 뒤 다시 챗으로 돌아올 기준이 약해진다
- 문제: `ChatPage` 의 연결 정책 카드는 이미 `from=/chat?session=...` 을 싣고 상세로 이동하지만, 상단 `정책 목록 보기` 버튼은 여전히 아무 state 없이 고정 `"/policies"` 로만 이동했다. 이 상태에서는 챗에서 정책 목록 탐색으로 넘어간 뒤 상세나 전역 이동을 거치면, 다시 챗으로 돌아올 세션 기준이 카드 클릭 경로보다 약해진다.
- 해결: `ChatPage` 에 `chatReturnTarget` 을 두고 현재 `activeSessionId` 기반 `?session=` 복귀 target을 한 곳에서 계산하게 정리했다. 상단 `정책 목록 보기` 버튼과 연결 정책 카드 모두 이 target을 `state.from` 으로 사용하게 맞췄다.
- 이유: 같은 화면에서 정책 영역으로 나가는 진입점은 모두 같은 복귀 기준을 써야 한다. 카드만 세션을 보존하고 목록 버튼은 보존하지 않으면, 사용자가 어떤 버튼을 눌렀는지에 따라 복귀 품질이 달라진다.

## 644) 챗이 `?session=` deep link를 받았는데 해당 세션이 이미 없으면, 설명 없이 첫 세션으로 조용히 바뀌어 사용자가 다른 대화를 잘못 연 것으로 느낄 수 있다
- 문제: `ChatPage` 는 세션 목록을 읽은 뒤 `preferredSessionId ?? querySessionId ?? current` 순으로 active 세션을 고른다. 그래서 `?session=123` 같은 deep link가 이미 삭제된 세션을 가리키면 실제로는 첫 세션으로 fallback되지만, 화면에는 별도 안내가 없어 사용자가 “왜 다른 대화가 열렸는지” 이해하기 어렵다.
- 해결: `ChatPage` 에 invalid query guard ref를 두고, 세션 목록 로드가 끝난 뒤에도 `querySessionId` 와 일치하는 세션이 없으면 경고 toast를 한 번만 보여주게 맞췄다. 이후 active/query sync는 기존대로 가장 최근 유효 세션으로 정리된다.
- 이유: fallback 자체보다 중요한 건 사용자가 현재 상태를 설명받는 것이다. 특히 deep link나 뒤로가기 query가 stale해질 수 있는 챗 세션은 “조용한 대체”보다 “유효한 세션으로 이동시켰다”는 피드백이 있어야 혼란이 적다.

## 645) 현재 챗 세션을 삭제했을 때 URL 정리를 effect에만 맡기면, `?session=` 이 한 템포 늦게 바뀌며 복귀 링크나 전역 이동이 잠깐 삭제된 세션을 가리킬 수 있다
- 문제: `ChatPage` 는 세션 삭제 후 `activeSessionId` 만 먼저 바꾸고, URL의 `?session=` 정리는 별도 sync effect가 다음 렌더에서 처리하고 있었다. 이 상태에서는 현재 세션 삭제 직후 아주 짧게라도 주소창과 `location.search` 가 예전 세션을 가리키고, 그 사이 다른 이동이 끼면 복귀 기준이 삭제된 세션으로 남을 수 있다.
- 해결: `replaceSessionQuery()` helper로 챗 session query 정리 기준을 한 곳으로 모으고, 현재 세션 삭제 시에는 fallback 세션 또는 빈 상태를 삭제 handler 안에서 바로 URL과 ref에 반영하게 맞췄다. 이후 effect는 같은 helper를 써서 일반 sync만 유지한다.
- 이유: `activeSessionId` 와 `?session=` 은 챗 화면의 단순 파생값이 아니라 다른 화면으로 넘기는 복귀 문맥이기도 하다. 삭제처럼 사용자가 상태 전환을 명확히 기대하는 이벤트는 다음 effect tick을 기다리기보다 같은 결정 시점에 URL과 state를 같이 정리하는 쪽이 더 안전하다.

## 632) 정책 목록/검색 DTO가 canonical projection `summary` 를 무시하면, 추천은 설명이 보이는데 일반 정책 카드만 비는 화면 차이가 생긴다
- 문제: `PolicySummaryResponse` 도 recommendation 과 같은 projection 을 이미 받고 있었지만, 설명은 계속 `welfare_services.description` 만 사용했다. 이 상태에서는 메인 추천 카드 설명은 보이는데 `/policies` 목록과 검색 결과 카드만 설명이 비는 불일치가 생긴다.
- 해결: 정책 summary DTO도 `projection.summary -> supportContent -> description` 순으로 설명을 조립하게 바꿨다.
- 이유: 같은 canonical projection 을 공유하는 응답이라면, 추천과 목록이 서로 다른 요약 필드 선택 규칙을 가지면 안 된다. 화면별 카드 품질 차이는 대개 이런 DTO 조립 차이에서 나온다.

## 633) 메인 추천이 `user.hasPriorities` 로 분기하는데 auth store가 그 값을 로그인/프로필 저장 경로에서 갱신하지 않으면, 추천 CTA와 empty state가 실제 우선순위 상태와 다르게 움직인다
- 문제: `MainPage` 는 `user?.hasPriorities` 로 `맞춤 재추천`, `새로고침`, empty state CTA를 나눈다. 그런데 auth store의 `user` 는 로그인 직후 `email` 위주로만 채워지거나, 가입 후 우선순위를 곧바로 저장해도 그 결과를 다시 반영하지 않았다. 이 상태에서는 실제로 우선순위가 있어도 메인 화면은 계속 “우선순위를 설정하러 가기” 로 남을 수 있다.
- 해결: auth store에 `setUser` merge setter를 두고, `LoginPage` 는 `/api/users/me` 조회와 가입 직후 우선순위 저장 결과를 `hasPriorities` 로 반영하게 맞췄다. `MainPage` 는 진입 시 프로필을 다시 읽어 `hasPriorities` 를 보정하고, `MyPage` 도 프로필 조회/우선순위 저장 성공 시 같은 플래그를 store에 동기화하게 정리했다.
- 이유: 이런 플래그는 화면 로컬 state가 아니라 로그인 세션의 공통 read model에 가깝다. 추천 분기 기준을 한 화면만 알고 있으면 저장 직후나 재진입 시점에 쉽게 stale state가 생긴다.

## 634) 공개 정책 화면의 북마크가 어떤 곳은 로그인으로 보내고 어떤 곳은 토스트만 띄우면, 같은 보호 기능인데도 해결 경로가 화면마다 달라진다
- 문제: 헤더나 주요 CTA는 비로그인 상태에서 보호 기능을 누르면 `/login` 으로 보내고 복귀 문맥도 함께 넘긴다. 그런데 `PoliciesPage`, `PolicyDetailPage` 의 북마크 버튼은 같은 보호 기능인데도 "로그인 후 이용 가능" 토스트만 띄우고 끝나서, 사용자는 다시 직접 로그인 진입점을 찾아야 했다.
- 해결: 공개 정책 목록과 상세의 북마크 버튼도 비로그인 상태에서는 현재 `pathname/search` 를 `from` 으로 들고 `/login` + `reason=login-required` 로 이동하게 맞췄다.
- 이유: 인증이 필요한 action은 화면마다 해결 방식이 달라지면 안 된다. 특히 북마크처럼 반복 사용되는 액션은 “막힘”보다 “로그인 후 같은 자리로 돌아오는 일관된 동선”이 더 중요하다.

## 635) 마이페이지에 저장한 기본 `statusFilter` 는 정책 목록이 읽어도, URL sync와 활성 필터 칩이 계속 `"신청가능"` 기준을 쓰면 저장값이 기본값처럼 보이지 않는다
- 문제: `includeExpired=true` 사용자는 `/policies` 기본 상태가 `전부표기` 로 시작하도록 이미 맞춰져 있었다. 하지만 URL sync는 여전히 `"신청가능"` 일 때만 `statusFilter` query를 생략했고, 활성 필터 칩도 `"신청가능"` 과 다르면 항상 표시/clear 하도록 하드코딩돼 있었다. 그래서 저장된 기본값 `전부표기` 도 마치 사용자가 추가로 건 필터처럼 보이고, chip clear도 다시 `"신청가능"` 로 되돌려 버렸다.
- 해결: `PoliciesPage` 는 `defaultStatusFilter` 를 URL sync 기준과 활성 필터 칩 기준에 같이 사용하도록 바꿨다. 이제 저장된 기본값은 query/chip에서 기본 상태로 취급하고, clear도 사용자의 저장 기본값으로 돌아간다.
- 이유: “기본값을 저장한다”는 기능은 초기 렌더만 바꾸는 것으로 끝나면 안 된다. URL, active chip, reset/clear까지 모두 같은 기준을 써야 사용자 입장에서 진짜 기본값으로 보인다.

## 636) 공개 목록/상세가 비로그인 상태에서 그려진 뒤 로그인으로 돌아와도 auth 상태를 dependency로 안 보면, bookmark 여부가 계속 비로그인 기준으로 남는다
- 문제: `PoliciesPage`, `PolicyDetailPage` 는 정책 데이터를 처음 불러올 때 bookmark 상태도 함께 받는다. 그런데 fetch effect가 `isLoggedIn` 을 dependency로 보지 않으면, 공개 화면에서 로그인 페이지를 다녀와도 같은 pathname/search에서는 재조회가 일어나지 않아 bookmark 표시가 계속 false로 남을 수 있다.
- 해결: 목록과 상세의 정책 fetch effect에 `isLoggedIn` dependency를 추가해, 로그인 상태가 바뀌면 같은 URL이라도 정책 데이터를 다시 읽게 맞췄다.
- 이유: 공개/로그인 겸용 화면에서 auth 전환은 URL 못지않게 응답 shape를 바꾸는 입력이다. dependency에서 auth를 빼면 “로그인은 됐는데 화면은 옛 상태” 같은 stale read가 남는다.

## 637) 로그인으로 보냈다고 끝내면, 사용자는 북마크처럼 방금 누른 액션을 로그인 후에 다시 한 번 반복해야 한다
- 문제: 공개 정책 목록/상세에서 비로그인 사용자가 북마크를 누르면 이제 로그인으로는 보내지만, 로그인 후에는 같은 화면으로만 돌아오고 실제 북마크 토글은 다시 직접 눌러야 했다. 회원가입이나 비밀번호 재설정을 거쳐 로그인하는 경우에도 이 의도는 더 쉽게 끊겼다.
- 해결: 로그인 진입 시 `postLoginAction` 으로 `toggle-bookmark` 의도를 함께 넘기고, `LoginPage` 는 성공 후 이 action을 원래 화면 state로 되돌리게 했다. `SignupPage`, `ResetPasswordPage` 도 이 action을 그대로 보존하고, `PoliciesPage`, `PolicyDetailPage` 는 로그인 후 돌아오면 이를 한 번만 실행한 뒤 state에서 제거하게 맞췄다.
- 이유: 보호 기능의 UX는 “로그인 화면으로 보냈다”가 끝이 아니다. 사용자가 방금 시도한 intent까지 이어져야 같은 플로우로 체감된다. 그렇지 않으면 technically는 정상이어도 UX는 여전히 두 번 클릭해야 하는 broken flow다.

## 638) 상세 화면이 이미 원래 목록 `from` 을 알고 있어도 브레드크럼과 fallback 버튼이 계속 고정 `"/policies"` 로 가면, 검색/필터 문맥이 다시 날아간다
- 문제: `PolicyDetailPage` 는 상단 뒤로가기와 related policy 연속 이동에서는 이미 `from` 을 보존하고 있었다. 그런데 브레드크럼의 `정책검색`, 카테고리 링크, not found 상태의 `목록으로`, `비슷한 정책 > 더 보기` 는 여전히 고정 `"/policies"` 또는 단순 `?category=` 로 이동해서, 원래 목록의 검색어·지역·상태 필터 문맥을 다시 잃을 수 있었다.
- 해결: 상세 화면에 `listBackTarget`, `categoryListTarget` 을 두고, 원래 `from.pathname === "/policies"` 인 경우에는 그 search를 우선 재사용하도록 맞췄다. 브레드크럼, fallback 목록 버튼, 카테고리/더 보기 링크도 같은 target을 쓰게 정리했다.
- 이유: 복귀 문맥은 뒤로가기 버튼 하나만 맞아도 충분하지 않다. 같은 화면 안의 다른 “목록으로 가는” 진입점도 같은 기준을 써야 사용자가 어디를 눌러도 일관되게 느낀다.

## 639) 챗 페이지가 `?session=` 으로 현재 세션을 보존해도, 전역 헤더 `챗봇` 버튼이 항상 고정 `"/chat"` 으로 가면 세션 복귀 문맥이 다시 끊긴다
- 문제: `ChatPage` 자체는 `activeSessionId` 를 `?session=` query와 동기화하고, 연결 정책 상세로 이동할 때도 `from.search=?session=...` 를 넘기고 있었다. 그런데 다른 화면에서 헤더 `챗봇` 버튼을 누르면 로그인 여부와 상관없이 목표 경로를 항상 고정 `"/chat"` 로 계산해서, 방금 보던 세션 대신 챗 기본 화면으로 돌아갈 수 있었다.
- 해결: `Header` 에 `chatTarget` 을 두고, 현재 위치가 `/chat` 이면 현재 search를, 현재 화면의 `location.state.from.pathname === "/chat"` 이면 그 search를 우선 재사용하게 맞췄다. 로그인 필요 리다이렉트와 로그인 후 직접 챗 이동도 같은 target을 쓰게 정리했다.
- 이유: 세션 복귀는 챗 페이지 내부 state만으로는 완성되지 않는다. 전역 진입점도 같은 session target을 사용해야 `챗 -> 정책 상세 -> 헤더 챗봇` 같은 실제 이동이 자연스럽게 이어진다.

## 640) 헤더만 챗 세션 target을 보존하고 `FloatingNav` 가 계속 고정 `"/chat"` 을 쓰면, 같은 전역 진입점인데도 한쪽만 세션을 잃는다
- 문제: 헤더 `챗봇` 버튼은 이미 `?session=` 복귀 target을 재사용하게 맞췄지만, 우측 `FloatingNav` 의 `AI 챗봇` 은 여전히 `item.path === "/chat"` 만 보고 이동했다. 그래서 `챗 -> 정책 상세 -> FloatingNav AI 챗봇` 흐름에서는 헤더와 달리 세션 query를 다시 잃을 수 있었다.
- 해결: `FloatingNav` 도 `chatTarget` 을 계산해 현재 `/chat` 의 search나 `location.state.from.pathname === "/chat"` 의 search를 우선 재사용하게 바꿨다. 비로그인 리다이렉트와 로그인 후 직접 이동도 같은 target을 쓰게 맞췄다.
- 이유: 세션 복귀 같은 전역 문맥은 컴포넌트 하나만 맞아도 충분하지 않다. 동일한 역할의 전역 네비게이션은 모두 같은 target 계산 규칙을 써야 사용자 체감이 일관된다.

## 641) 챗 페이지가 `?session=` 을 sync할 때마다 `loadSessions` callback까지 query dependency로 새로 만들어지면, 세션 선택만 해도 목록 API를 다시 치게 된다
- 문제: `ChatPage` 는 `activeSessionId -> ?session=` 동기화를 위해 query를 자주 바꾼다. 그런데 `loadSessions` 가 `querySessionId` 를 dependency로 직접 잡고 있으면 callback identity도 같이 바뀌고, 이를 구독한 초기 `useEffect` 가 다시 돌면서 세션 클릭, 삭제 후 fallback, query 정리 같은 동작만으로도 `/api/chat/sessions` 를 재호출하게 된다.
- 해결: `querySessionIdRef` 를 두고 최신 query 값을 ref로만 읽게 바꿨다. `loadSessions` callback은 더 이상 query에 직접 의존하지 않고, 실제 세션 목록을 새로 읽어야 할 때만 그대로 호출된다.
- 이유: URL sync는 UI state 반영이고, 세션 목록 fetch는 서버 read다. 둘의 dependency를 직접 묶어두면 “query를 맞추는 것”과 “목록을 다시 읽는 것”이 불필요하게 결합돼 체감 성능과 로딩 안정성이 같이 나빠진다.

## 642) 마이페이지가 `?tab=` 으로 현재 탭을 보존해도, 전역 `마이페이지` 버튼이 계속 고정 `"/mypage"` 로 가면 상세를 다녀온 뒤 다시 기본 탭으로 떨어진다
- 문제: `MyPage` 는 이미 `?tab=` query로 활성 탭을 보존하고, 정책 상세로 이동할 때도 `from.search=?tab=...` 를 넘기고 있었다. 그런데 헤더와 `FloatingNav` 의 `마이페이지` 버튼은 로그인 여부와 상관없이 목표 경로를 계속 고정 `"/mypage"` 로 계산해서, `북마크 탭 -> 정책 상세 -> 전역 마이페이지` 같은 흐름에서 다시 기본 탭으로 돌아갈 수 있었다.
- 해결: `Header`, `FloatingNav` 모두 `mypageTarget` 을 계산해 현재가 `/mypage` 이면 현재 search를, 현재 화면의 `location.state.from.pathname === "/mypage"` 이면 그 `?tab=` search를 우선 재사용하게 맞췄다. 로그인 필요 리다이렉트와 로그인 후 직접 이동도 같은 target을 쓰게 정리했다.
- 이유: 탭 복귀도 챗 세션 복귀와 같은 종류의 문맥이다. 화면 내부에서 query를 보존하는 것만으로는 부족하고, 전역 진입점도 같은 target 계산을 따라야 사용자가 “같은 마이페이지로 돌아왔다”고 느낀다.

## 643) 정책 목록이 URL로 검색/필터를 이미 보존해도, 전역 `정책 목록/정책검색` 버튼이 계속 고정 `"/policies"` 로 가면 상세를 다녀온 뒤 다시 빈 목록으로 떨어진다
- 문제: `PoliciesPage` 와 `PolicyDetailPage` 는 이미 query 기반으로 검색어·카테고리·지역·상태 필터 문맥을 보존하고 있었다. 그런데 헤더와 `FloatingNav` 의 전역 `정책 목록/정책검색` 버튼은 목표 경로를 계속 고정 `"/policies"` 로 계산해서, `검색 결과/필터된 목록 -> 정책 상세 -> 전역 정책 목록` 흐름에서 원래 목록 문맥을 다시 잃을 수 있었다.
- 해결: `Header`, `FloatingNav` 모두 `policiesTarget` 을 계산해 현재가 `/policies` 이면 현재 search를, 현재 화면의 `location.state.from.pathname === "/policies"` 이면 그 search를 우선 재사용하게 맞췄다.
- 이유: 목록 복귀는 상세 화면 내부 브레드크럼만 맞춰서는 끝나지 않는다. 전역 네비게이션도 같은 search target을 써야 사용자가 어디서 목록으로 돌아가도 같은 결과 집합을 보게 된다.

## 644) Gov24 detail/supportConditions가 `850/900` 건 이후 전부 실패한 건 source row 품질 문제가 아니라 현재 런타임의 Gov24 인증키 부재 때문이다
- 문제: `Gov24` list는 이미 `10937` 건까지 적재됐고, detail/supportConditions도 각각 `850`, `900` 건까지는 쌓였지만, 그 다음 chunk(`maxCallsPerRun=1000`)부터는 `saved=0 failed=1000` 으로 멈췄다. 처음에는 일부 `서비스ID` 에만 detail/support endpoint가 없는 것처럼 보였지만, 단건 재현과 app log를 다시 보면 `CollectHttpRetryExecutor` 가 `request=detail=...` / `request=supportConditions=...` 에 대해 모두 `401 Unauthorized` 를 기록하고 있었다.
- 해결: 직접 upstream endpoint를 확인해 `serviceKey` 없이 호출하면 `401 인증키는 필수`, 당시 local runtime에 실려 있던 key도 Gov24에서 `등록되지 않은 인증키` 로 거절된다는 점을 확인했다. 그 뒤 `.env`, `application.yml`, `docker-compose.yml` 기준을 `PUBLIC_DATA_PORTAL_API_KEY` 중심으로 다시 정리해 `복지로/Gov24` 가 같은 공공데이터포털 key를 기본으로 쓰게 맞췄다. 따라서 local Gov24 확장을 재개하려면 현재 통합 key의 Gov24 승인 상태를 다시 확인하거나, 필요 시 source override key를 별도로 주입하면 된다.
- 이유: 이 상태를 source별 품질 문제로 오해하면 skip 정책이나 mapper를 잘못 손보게 된다. 현재 blocker는 taxonomy도 endpoint shape도 아니라, 런타임 인증키가 Gov24 provider에서 승인되지 않은 점이다.

## 645) Gov24 detail의 `failed=1` 은 구조적 미지원이 아니라 transient upstream 실패일 수 있으므로 단건 재시도 경로가 있어야 진짜 실패를 분리할 수 있다
- 문제: `Gov24 detail` 을 `maxCallsPerRun=1000` 으로 확장했을 때 `requested=1000 saved=999 failed=1` 이 한 번 발생했다. direct upstream으로 같은 `sourceId=394000000108` 을 다시 확인하면 `serviceList`, `serviceDetail`, `supportConditions` 모두 `200` 을 반환했기 때문에, 이를 바로 unsupported service로 분류하면 collect 품질을 과도하게 비관하게 된다.
- 해결: `Gov24DetailCollectService` 에 `collectGov24DetailsForSourceId(...)` 를 추가하고, `CollectSourceExecutionService` / `CollectAdminService` 가 `/api/admin/collect/gov24-details?sourceId=...` 를 지원하게 맞췄다. 실제 재실행 결과 `requested=1 saved=1 skipped=0 failed=0` 으로 복구되어 transient upstream failure 로 분류할 수 있게 됐다.
- 이유: backlog collect는 chunk 실패율만으로는 원인을 알기 어렵다. 단건 재시도 경로가 있어야 “구조적 미지원” 과 “일시적 upstream 오류” 를 분리할 수 있다.

## 646) Gov24 detail/support fetch에 retry가 없으면 키 문제를 해결한 뒤에도 순간적인 upstream 예외 한 번이 그대로 failed row로 남는다
- 문제: `PUBLIC_DATA_PORTAL_API_KEY` 를 바로잡은 뒤 `Gov24` 확장은 다시 진행됐지만, detail/support fetch는 당시 단순 1회 호출이었다. 이 상태면 일시적인 5xx, connection reset, null payload 같은 transient 오류도 그대로 `failedCount` 로 남아, chunk를 키울수록 작은 upstream 흔들림이 운영 지표를 거칠게 만든다.
- 해결: `Gov24DetailCollectService`, `Gov24SupportConditionsCollectService` 에 기존 `collect.detail.retry.max-attempts`, `collect.detail.retry.base-backoff-ms` 를 재사용하는 lightweight retry를 넣었다. 그 뒤 `500`, `1000` 단위 확장을 다시 돌려 `detail/support` 모두 `failed=0` 기준을 확인했다.
- 이유: Gov24는 external API이고, 현재 목표는 hard taxonomy 작업이 아니라 runtime backlog 확장이다. 이 단계에서는 Bokjiro detail처럼 무거운 상태 모델까지는 아니어도, transient fetch를 흡수하는 최소 retry는 있는 편이 운영적으로 안전하다.

## 647) Gov24 support raw payload shape가 flat과 nested로 섞여 있으면 replay/backfill 기준선이 같은 source 안에서도 두 갈래가 된다
- 문제: Gov24 `supportConditions` raw를 점검해 보니 일부 row는 `{"서비스ID","서비스명","JA0101":...}` flat shape, 일부는 `{"서비스ID","서비스명","conditions":{...}}` nested shape로 저장돼 있었다. 현재 fact 생성은 둘 다 읽어도, raw replay/audit 기준선이 source 내부에서 두 갈래면 이후 backfill이나 diff 확인이 불필요하게 복잡해진다.
- 해결: `RawApiPayloadService.saveGov24SupportConditions()` 를 nested shape 고정 저장으로 바꾸고, 기존 flat `3721`건도 DB에서 `payload_json`, `payload_hash` 를 함께 재계산해 nested shape로 일괄 정규화했다. 단건 재수집까지 다시 확인한 결과 현재 `SUPPORT raw shape` 는 `nested=5120`, `flat=0` 이다.
- 이유: raw snapshot은 replay/backfill의 single source of truth 역할을 해야 한다. 같은 source/category 안에서 payload shape가 둘 이상이면 downstream은 사소한 구조 차이를 계속 감안해야 하고, 그 비용이 점점 커진다.

## 648) Gov24 runtime collect가 끝까지 닫힌 뒤에도 `support raw` 와 `support fact service coverage` 가 일치하지 않으면, 남은 차이가 저장 실패인지 all-null payload인지 구분해서 봐야 한다
- 문제: Gov24 `serviceList/detail/support raw` 를 모두 `10937` 건까지 채운 뒤에도, `GOV24_SUPPORT_CONDITION` facts 가 붙은 서비스는 `9959`건으로 남아 있었다. 겉으로만 보면 support fact 생성 누락 버그처럼 보일 수 있다.
- 해결: `support raw는 있지만 fact가 없는` 서비스를 따로 샘플링해 보니, 대표 케이스들은 `payload_json.conditions` 안의 `JA*` 값이 전부 `null` 이었다. 즉 현재 기준 이 차이는 writer 실패보다 “원 payload에 추출할 조건이 없음” 쪽 해석이 더 맞고, 이 상태를 실측 current-state로 남겼다.
- 이유: coverage gap를 모두 코드 버그로 해석하면 unnecessary retry/backfill을 추가하게 된다. 현재처럼 raw는 정상인데 값 자체가 비어 있는 source가 섞인 경우에는, 남은 갭을 데이터 특성으로 분리해 두는 편이 이후 normalization 정책을 세우기 쉽다.

## 649) Gov24 `support fact` 미생성 978건은 최종 audit 기준 all-null 보다 `unmapped official support code-only payload` 로 보는 편이 더 정확하다
- 문제: closeout 직후 초기 샘플만 보면 `support raw는 있지만 fact가 없는` 나머지 케이스를 all-null payload로 넓게 묶기 쉽다. 하지만 실제로는 `JA2101`, `JA2202` 같은 official code가 들어와도 현재 extractor가 읽는 support fact 집합에 포함되지 않으면 fact가 비어 있을 수 있다.
- 해결: `deploy/smoke/run-local-gov24-quality-audit.sh` 와 `policy-gov24-runtime-audit-runbook.md` 를 추가해 missing fact gap를 `no raw / all-null / unmapped-only / mapped-signal anomaly` 로 분해하게 했다. 현재 local audit 결과는 `missing_no_support_raw=0`, `missing_all_null_payload=0`, `missing_unmapped_only_payload=978`, `missing_mapped_signal_payload=0` 이고, 대표 sample들도 `effective_signal_count=2`, `mapped_signal_count=0` 으로 확인된다.
- 이유: 이 분해가 있어야 남은 작업을 retry/backfill이 아니라 “현재 fact extractor가 승격하지 않는 official support condition code 범위를 어떻게 다룰지” 문제로 정확히 옮길 수 있다.

## 650) Gov24 unmapped support code inventory를 바로 fact scope로 올리기보다, 현재 제품이 실제로 그 축을 소비하는지 먼저 따져야 fact drift를 막을 수 있다
- 문제: 남은 `978`건 gap의 중심 code를 뽑아 보니 `JA210*` 사업체 유형, `JA220*/JA120*/JA1299/JA2299` 업종, `JA110*` 창업/사업 단계가 대부분이었다. 이 분포만 보면 곧바로 `GOV24_SUPPORT_CONDITION` fact scope를 넓히고 싶어질 수 있다.
- 해결: `policy-gov24-support-unmapped-inventory.md` 에 현재 top code 분포를 정리하고, 현재 사용자 프로필/추천 matcher가 `연령`, `소득`, `가구`, `고용`, `교육`, `특수대상` 같은 개인 eligibility 축 중심이라는 점을 같이 적어 두었다. 그 결과 현재 단계 판단은 `deferred` 로 유지하고, 사업체/업종 축을 실제로 제품이 소비하기 시작할 때만 다시 active 검토하는 쪽으로 고정했다.
- 이유: official code가 있다는 사실만으로 fact를 늘리면 제품 의미가 불명확한 signal이 쌓인다. 현재 제품이 실제로 쓰지 않는 사업자/업종 축은 inventory로만 남기고, user input / matcher / scoring 이 그 축을 요구할 때 다시 여는 편이 drift를 줄인다.

## 651) 개인 캐시 회귀를 다시 검증하려면 recommendation 타깃 테스트뿐 아니라 Gov24 추가 뒤 바뀐 공통 source 계약 테스트도 함께 현재 truth로 맞춰야 한다
- 문제: 개인 캐시 회귀 검증을 위해 recommendation 타깃 테스트와 broad suite를 다시 돌리자, 추천 자체가 아니라 `CollectSourceExecutionServiceTest`, `WelfareSourceTypeSupportTest` 가 먼저 깨졌다. 원인은 최근 `Gov24`, `GOV24_DETAIL`, `GOV24_SUPPORT_CONDITIONS` 가 필수 adapter/sourceType 집합에 추가됐는데, 테스트 fixture와 기대값이 아직 이전 source 집합에 머물러 있었기 때문이다.
- 해결: `CollectSourceExecutionServiceTest` 는 `GOV24`, `GOV24_DETAIL`, `GOV24_SUPPORT_CONDITIONS` mock adapter를 포함한 현재 adapter map으로 갱신했고, manual-only source 검증도 그 상태에서 `BOKJIRO_DETAIL_REFRESH` 누락을 확인하도록 유지했다. `WelfareSourceTypeSupportTest` 는 `gov24` 를 정상 sourceType 으로 정규화하는 케이스를 추가하고, unknown source reject는 별도 문자열로 바꿨다.
- 이유: 개인 캐시 회귀를 보려면 broad suite 전체가 현재 source 계약과 먼저 일치해야 한다. source 추가로 깨진 공통 테스트를 방치하면 recommendation 경계의 진짜 회귀와 unrelated expectation drift를 구분할 수 없다.

## 652) 개인 캐시 회귀는 단위 테스트만으로 닫히지 않고, replay smoke와 broad suite에서 규칙 버전 전환/저장 추천 reuse가 그대로 유지되는지 같이 봐야 한다
- 문제: `RecommendationRefreshCacheService` 는 Redis 마커 하나로 non-personal refresh 재계산을 줄이는 구조라, 단위 테스트만 통과해도 실제 replay의 OFF/ON phase 재기동, exact batch 조회, broad integration 재실행에서 stale hit가 다시 스며들 수 있다.
- 해결: `RecommendationRefreshCacheServiceTest`, `RecommendationGenerationServiceTest`, `ClusterServiceTest`, `RecommendationResultReadServiceTest`, `RecommendationResultReadRepositoryImplTest`, `RecommendationFlowIntegrationTest`, `CanonicalRecommendationReadModelIntegrationTest` 를 다시 돌리고, `KEEP_ARTIFACTS=true deploy/smoke/run-local-education-priority-replay.sh`, 최종 `./gradlew test integrationTest --no-daemon` 까지 재통과를 확인했다. replay summary는 `A_top10_target=9->9`, `B_top10_target=1->1`, `A_fp=same`, `B_fp=same`, `A_reason_changed=0`, `B_reason_changed=0` 기준으로 안정적이었다.
- 이유: 현재 개인 캐시의 핵심 위험은 “추천 품질이 조금 바뀌는 것”보다 “규칙 버전이 다른 phase가 같은 refresh marker를 stale reuse 하는 것”이다. 그래서 실제 replay smoke와 broad suite까지 같이 green이어야 `collect/replay/broad-suite` 기준으로 회귀가 없다고 볼 수 있다.

## 653) CTR 튜닝은 total log 수만으로 열면 안 되고, click sample과 weight bucket 분산을 같이 봐야 readiness를 잘못 읽지 않는다
- 문제: admin dashboard 기준 recommendation total logs는 이미 `1532` 로 top stage까지 올라가 있었고, weight bucket도 `0.80:0.20`, `0.60:0.40`, `0.40:0.60` 세 구간이 모두 존재했다. 이 숫자만 보면 바로 rule/AI weight tuning을 시작하고 싶어질 수 있다.
- 해결: `run-local-ctr-readiness-audit.sh` 와 `recommendation-ctr-readiness-runbook.md` 를 추가해 total logs, clicked logs, fallback/AI clicked 분포, weight bucket CTR, readiness 판정을 한 번에 보게 했다. 현재 local audit 결과는 `total_logs=1532`, `clicked_logs=13`, `ctr=0.85%`, `fallback_sent/clicked=1022/0`, `ai_sent/clicked=510/13` 이고 readiness는 `DEFERRED_CLICK_SAMPLE_THIN` 이다.
- 이유: stage progress와 tuning readiness는 같은 문제가 아니다. 현재처럼 로그 총량은 충분해도 클릭이 `13` 건뿐이면 weight를 건드릴 근거가 약하다. total log stage와 click sample readiness를 분리해서 읽어야 과도한 조정을 막을 수 있다.

## 654) CTR readiness는 click 수만 아니라 click diversity도 같이 봐야 특정 서비스 편향을 전체 품질 신호로 오해하지 않는다
- 문제: `clicked_logs=13` 이라는 숫자만으로도 이미 표본이 얇지만, 추가로 실제 클릭은 `clicked_users=13`, `clicked_services=2` 로 두 개 서비스에만 집중돼 있었다. 이 상태에서 bucket CTR 차이만 보고 가중치를 조정하면, 특정 인기 서비스 1~2개가 만든 편향을 전체 추천 품질 신호처럼 오해할 수 있다.
- 해결: CTR readiness audit에 `ctr_clicked_users`, `ctr_clicked_services`, `[top_clicked_services]`, `[top_clicked_users]` 를 추가했다. 현재 baseline은 `2622=7 clicks (53.85%)`, `3688=6 clicks (46.15%)` 로 사실상 전 클릭이 두 서비스에 몰려 있다는 점을 같이 기록했다.
- 이유: tuning reopen 판단은 “충분한 클릭 수”와 “충분한 클릭 분산”이 둘 다 필요하다. 현재처럼 서비스 다양성이 거의 없는 상태에서는 log 총량이나 bucket 수만으로 readiness를 선언하면 잘못된 결론이 나온다.

## 655) retrieval/category admin 경로가 각각 useful해도 운영자가 매번 raw JSON을 직접 읽게 하면 baseline 판단 속도가 느리다
- 문제: `retrieval-evaluations/run`, `retrieval-evaluations/gate`, `category-audit` 는 모두 현재 bounded admin 경로로 붙어 있었지만, 운영자가 baseline을 다시 확인하려면 각 응답의 raw JSON에서 핵심 숫자를 따로 골라 읽어야 했다. 이 상태에서는 runtime은 정상이어도 “지금 baseline이 유지되는가”를 빠르게 판정하기 어렵다.
- 해결: `run-local-policy-quality-summary.sh` 와 `policy-quality-summary-runbook.md` 를 추가해 세 경로를 한 번에 호출하고 핵심 summary만 출력하게 했다. 현재 local baseline은 `datasetKey=retrieval-baseline-v2`, `scenarioCount=11`, `top1/top3/branch=1.0`, `fallbackCount=5`, `emptyResultCount=0`, gate `passed=true`, category `searchablePolicyRatio=0.2399`, top unified `일자리`, top youth broad `복지문화 -> 금융·생활지원 (0.3703)` 으로 고정했다.
- 이유: 지금 필요한 건 새로운 기능이 아니라 반복 가능한 운영 판정 경로다. bounded admin 경로를 계속 raw JSON으로 읽게 두기보다, summary smoke 한 번으로 baseline을 읽게 만드는 편이 실제 triage 속도와 재현성을 높인다.

## 656) bounded runtime smoke는 한 번 붙였다고 끝나는 게 아니라, 재실행해도 baseline이 그대로인지 확인해야 “현재 truth” 로 믿을 수 있다
- 문제: `run-local-policy-quality-summary.sh`, `run-local-gov24-quality-audit.sh`, `run-local-ctr-readiness-audit.sh` 를 이미 만들었더라도, 이후 코드/문서 정리나 source 추가가 섞이면 baseline drift가 조용히 들어왔는지 모른 채 문서만 믿게 될 수 있다.
- 해결: 세 smoke를 다시 실제로 재실행해 baseline 유지 여부를 확인했다. 결과는 `retrieval-baseline-v2`, gate `passed=true`, category `searchablePolicyRatio=0.2399`, Gov24 `list/detail/support raw=10937`, `support_fact_services=9959`, `missing_unmapped_only_payload=978`, CTR `total_logs=1532`, `clicked_logs=13`, `clicked_services=2`, readiness `DEFERRED_CLICK_SAMPLE_THIN` 으로 모두 기존 값과 동일했다.
- 이유: 지금 phase의 practical task는 새 기능보다 bounded runtime repeatability 유지다. 같은 smoke를 다시 돌려도 숫자가 그대로 나오는지 확인해야 current-state/runbook이 실제 runtime truth를 계속 반영한다고 볼 수 있다.

## 657) bounded admin runtime runbook은 endpoint가 살아 있는 것만으로 충분하지 않고, 문서가 실제 DTO 응답 shape를 맞게 읽는지도 같이 확인해야 한다
- 문제: `policy-admin-runtime-runbook.md` 는 `search-youth-relevance/rebuild` 를 `scanned/updated/failed` 식으로, `embeddings/rebuild` 를 `failed` 계열 count까지 보는 것처럼 적고 있었는데, 실제 DTO는 각각 `processedCount/updatedCount/relevantCount/excludedCount`, `scope/requestedServiceCount/scannedChunkCount/refreshedChunkCount` 만 내려준다. 이 상태를 두면 endpoint는 정상이어도 runbook이 엉뚱한 필드를 기대하게 된다.
- 해결: admin login 뒤 `reference-urls/rebuild?limitPerSource=20`, `search-youth-relevance/rebuild`, `embeddings/rebuild?serviceId=6790&serviceId=2622` 를 실제로 다시 호출해 baseline을 확인했다. 결과는 `reference-urls: scanned=80 skipped=80 updated=0 failed=0`, `search-youth-relevance: processed=14863 updated=0 relevant=3566 excluded=11297`, `embeddings: scope=service_ids requestedServiceCount=2 scannedChunkCount=7 refreshedChunkCount=0` 이었고, runbook도 이 현재 응답 shape에 맞게 고쳤다.
- 이유: 지금 phase의 practical task는 bounded runtime 경로를 “반복 가능하게 읽고 다시 실행할 수 있는 상태”로 유지하는 것이다. 응답 필드 기대가 실제 DTO와 어긋나면 smoke는 통과해도 운영자가 결과를 잘못 해석하게 된다.

## 658) broader local validation suite를 다시 돌리면 recommendation log/CTR 숫자가 조금씩 움직이므로, quick suite 후 readiness baseline도 같이 재확인해야 한다
- 문제: `run-local-validation-from-env.sh --quick` 는 auth/session, public chat, bookmark consistency, recommendation click, admin dashboard smoke를 한 번에 다시 태운다. 이 중 recommendation refresh/click 단계가 실제 `recommendation_logs` 를 더 쌓기 때문에, quick suite 재실행 뒤에도 CTR 문서를 예전 숫자(`1532/13/0.85%`)로 두면 현재 baseline과 어긋날 수 있다.
- 해결: quick suite를 실제로 다시 돌려 `local validation suite passed`, `suite_duration_seconds=70` 을 확인한 뒤, 곧바로 `run-local-ctr-readiness-audit.sh` 를 다시 읽어 baseline을 갱신했다. 현재 값은 `total_logs=1583`, `clicked_logs=14`, `ctr=0.88%`, `clicked_services=2`, readiness `DEFERRED_CLICK_SAMPLE_THIN` 이다.
- 이유: 현재 phase에서는 CTR tuning을 열지 않더라도, quick suite 같은 synthetic smoke traffic이 추천 로그 baseline을 움직인다는 사실은 같이 관리해야 한다. 그래야 current-state/runbook 숫자가 실제 DB 상태와 계속 맞는다.

## 659) full validation suite까지 다시 돌리면 replay baseline은 그대로여도 recommendation log/CTR 숫자가 한 번 더 움직이므로 latest audit 값을 다시 덮어써야 한다
- 문제: `run-local-validation-from-env.sh --full` 은 quick 단계 전부에 `education priority replay` 까지 포함한다. replay 자체는 recommendation click을 늘리지 않지만, full suite 앞단의 refresh/click smoke가 한 번 더 돌면서 `recommendation_logs` baseline이 다시 움직인다. quick suite 기준 숫자(`1583/14/0.88%`)를 그대로 두면 full-suite 이후 current DB 상태와 또 어긋난다.
- 해결: full suite를 실제로 다시 돌려 `suite_duration_seconds=105`, replay summary `A_top10_target=9->9`, `B_top10_target=1->1`, `A_fp=same`, `B_fp=same`, `A_reason_changed=0`, `B_reason_changed=0` 를 확인한 뒤, 곧바로 `run-local-ctr-readiness-audit.sh` 를 재실행해 최신 baseline을 `total_logs=1634`, `clicked_logs=15`, `ctr=0.92%`, `clicked_services=2`, readiness `DEFERRED_CLICK_SAMPLE_THIN` 으로 갱신했다.
- 이유: 지금 단계에서는 replay와 CTR 둘 다 quality baseline 문서에 묶여 있다. full suite가 replay quality는 건드리지 않아도 recommendation log는 조금씩 늘리므로, final validation까지 다시 돌린 뒤에는 CTR audit 최신값으로 문서를 다시 덮어써야 current truth가 유지된다.

## 660) admin runtime runbook의 로그인 예시는 실제 smoke baseline 자격 증명과 맞아야 한다
- 문제: bounded admin runtime 재검증 과정에서 `policy-admin-runtime-runbook.md`, `runtime-api-smoke-commands.md` 는 여전히 `ADMIN_PASSWORD=\"Password123!\"` 예시를 쓰고 있었지만, 실제 로컬 admin smoke 스크립트 기본값은 `password123!` 였다. 이 상태에서는 endpoint 자체는 정상이어도 문서 예시 그대로 복붙하면 `A004` 를 만나게 된다.
- 해결: 두 문서의 admin login 예시를 현재 local smoke baseline인 `password123!` 로 교정하고, shell env에 이미 `ADMIN_PASSWORD` 가 있거나 `run-local-validation-from-env.sh` 가 `/tmp/youth-welfare-admin-smoke-password` 파일을 준비한 경우에는 그 값을 우선하도록 설명을 보강했다.
- 이유: 지금 phase의 작업은 새 기능보다 반복 가능한 local runtime 검증 유지다. admin runbook의 예시 자격 증명이 실제 smoke baseline과 어긋나면, 같은 엔드포인트를 두고도 문서만 따라 한 사용자는 불필요한 auth 오류를 보게 된다.

## 661) local closeout inventory가 여전히 `draft schema/backfill auto-apply` 중심으로 읽히면, 현재 PostgreSQL integrated schema truth를 다시 과거 실험처럼 오해하게 된다
- 문제: `policy-local-closeout-pending-inventory.md` 와 주변 설명은 오래된 `apply-local-policy-sidecar-draft.sh` 시절 서술이 강하게 남아 있어, replay smoke나 local closeout 절차가 아직도 MySQL draft schema를 다시 깔고 sidecar draft SQL을 재적용하는 것처럼 읽힐 여지가 있었다. 이 상태는 이미 PostgreSQL integrated schema로 정리된 현재 runtime truth와 충돌한다.
- 해결: local closeout inventory 설명을 `draft schema/backfill auto-apply` 관점에서 떼어내고, `apply-local-policy-sidecar-draft.sh` 를 현재는 `PostgreSQL integrated schema preflight` 경계로 읽어야 한다고 명시했다. 동시에 personal refresh-cache regression validation 통과와 Gov24 runtime collect/runtime audit closeout도 현재 기준선에 맞게 inventory에 반영했다.
- 이유: closeout 문서는 “지금 로컬에서 뭘 다시 확인해야 하는가”를 보는 문서다. 여기에 draft schema 시절 표현이 남아 있으면 이미 끝난 전환 작업과 현재 운영 경계를 계속 섞어 읽게 된다.

## 662) `db-migration` 과 `phase-plan` 이 active runtime 절차와 과거 MySQL/draft 이력을 한 문서 안에 같이 담고 있으면, 상단 guardrail 없이 읽는 사람은 과거 절차를 현재 truth로 오해한다
- 문제: `db-migration.md` 는 MySQL draft apply 예시와 migration memo가 길게 남아 있고, `phase-plan.md` 도 상단 active 기준선 아래에 MySQL, draft sidecar, `service_taxonomy_summary_slots` 같은 과거 전환 로그가 이어진다. 가드레일 없이 보면 오래된 이력이 현재 운영 절차처럼 보일 수 있었다.
- 해결: `db-migration.md` 상단에 현재 active truth는 PostgreSQL mainline과 `current-state/testing` 계열 문서라고 못박고, MySQL draft apply 예시는 legacy reference라고 명시했다. `phase-plan.md` 도 문서 맨 위에 “상단 active 기준선 우선, 아래로 갈수록 오래된 이력”이라는 해석 가드레일을 추가해 `deploy/mysql/apply-local-policy-sidecar-draft.sh` 도 현재는 draft SQL 재적용기가 아니라 integrated schema preflight로 읽어야 한다고 정리했다.
- 이유: migration/history 문서는 지우지 않더라도 읽는 규칙은 명확해야 한다. 그렇지 않으면 과거 실험 로그가 active 운영 절차보다 더 크게 보이는 문서 drift가 생긴다.

## 663) root 문서와 documentation map이 `current-state`, `testing`, `*-current-state` 를 가장 먼저 보라고 못 박지 않으면, 사용자는 여전히 긴 `phase-plan` 이력부터 읽으며 active truth를 뒤늦게 찾게 된다
- 문제: `README.md`, `documentation-map.md`, `start.md`, `current-state.md` 는 이미 active/history 경계를 나누기 시작했지만, 충돌 시 어떤 문서를 더 우선해서 읽어야 하는지와 `phase-plan` 을 어디까지 active로 해석해야 하는지가 충분히 강하게 적혀 있지 않았다. 그 결과 문서가 많을수록 오히려 오래된 이력 쪽으로 먼저 시선이 가는 문제가 남아 있었다.
- 해결: root 문서와 map 문서에 “실제 코드 -> `current-state` / `testing` / 각 `*-current-state` -> runbook -> history/legacy” 순으로 읽으라는 우선순위를 명시했다. 또 `phase-plan` 은 최신 상단 closeout/active 기준선만 현재 절차로 읽고, 아래 MySQL/draft/slot 기록은 과거 전환 이력으로 읽으라는 보정도 추가했다.
- 이유: 문서가 많을수록 정보가 아니라 탐색 비용이 문제가 된다. 우선순위 규칙이 명시돼야 active truth를 빠르게 찾고 과거 기록과 현재 계약을 섞어 읽지 않게 된다.

## 664) 각 도메인 docs index가 여전히 `phase-plan` 이나 blocked 상태를 중심으로 소개하면, runtime closeout이 끝난 Gov24나 deferred CTR readiness를 현재 active coding task처럼 오해한다
- 문제: `policy-docs-index.md`, `recommendation-docs-index.md`, `collect-docs-index.md` 에는 여전히 긴 phase-plan 이력이나 과거 blocked 상태를 먼저 떠올리게 하는 설명이 남아 있었다. 특히 Gov24는 runtime collect/runtime audit이 이미 닫혔는데도 단순 inactive처럼 읽힐 여지가 있었고, recommendation도 CTR tuning이 아직 deferred인데 active implementation 후보처럼 보일 수 있었다.
- 해결: 세 index 모두 현재 truth는 각 `*-current-state`, operation checklist, runbook을 우선해서 읽으라고 정리했다. policy index에는 Gov24가 이제 runtime closeout 완료 상태이고 hard taxonomy/import-backfill만 external blocked라는 점을 반영했고, recommendation index에는 실제 계약은 `recommendation-current-state`, `operation-checklist`, `ctr-readiness-runbook` 이라고 못 박았다.
- 이유: index 문서는 실제 작업 진입점이다. 이 문서가 현재 truth를 잘못 가리키면, 이미 닫힌 runtime lane을 다시 파거나 deferred 상태를 active feature work로 착각하게 된다.

## 665) next active priority 문서가 CTR tuning을 여전히 바로 다음 practical task처럼 보이게 두면, 얇은 click sample 상태에서 잘못된 optimization 작업을 열게 된다
- 문제: Gov24 closeout과 personal cache regression validation이 끝난 뒤에도 `policy-next-active-track-priority.md` 를 대충 읽으면 recommendation 쪽 다음 practical task가 곧바로 weight tuning인 것처럼 느껴질 수 있었다. 하지만 CTR readiness audit은 `clicked_logs` 가 매우 적고 `clicked_services=2` 로 click concentration도 심한 상태를 보여준다.
- 해결: active priority 문서를 `bounded runtime checks` 와 broader local validation 반복 확인 중심으로 재정렬하고, CTR tuning은 readiness가 `DEFERRED_CLICK_SAMPLE_THIN` 인 동안은 active 구현 작업이 아니라는 점을 명시했다. recommendation current-state에도 다음 작업은 weight tuning이 아니라 readiness baseline 유지와 runtime/quality smoke 관찰이라고 추가했다.
- 이유: priority 문서는 실제 다음 행동을 결정한다. sample이 충분하지 않은데도 tuning을 active lane처럼 적어두면, 품질이 아니라 noise를 최적화하는 쪽으로 팀이 움직이게 된다.

## 666) active/runtime 문서 정리 작업도 트러블슈팅에 남겨두지 않으면, 나중에 “왜 이렇게 읽게 만들었는가”가 사라져 같은 문서 drift가 반복된다
- 문제: Gov24 closeout, bounded runtime smoke, CTR readiness baseline처럼 수치가 있는 작업은 `troubleshooting-log` 에 잘 남았지만, 그 이후에 진행한 active/legacy 경계 정리와 docs index 재정렬 같은 문서 가드레일 작업은 기록이 거의 없었다. 이 상태에서는 나중에 누군가 같은 문구를 다시 되돌려도 “왜 그 표현을 막았는지”를 추적하기 어렵다.
- 해결: local closeout inventory truth 정리, db-migration/phase-plan guardrail, root/map 우선순위, 각 docs index active truth 정렬, next active priority 재정렬까지의 문서 작업을 이번 661~666 항목으로 분리해 기록했다.
- 이유: 문서 정리는 코드보다 덜 눈에 띄지만, 현재 phase에서는 실제 작업 경로를 결정하는 운영 레일이다. 기록 없이 넘어가면 같은 active/history 혼선이 반복될 가능성이 높다.

## 667) credential example drift는 한 문서 안의 한 섹션만 고치고 끝내면 같은 파일의 다른 예시 블록에서 다시 살아남는다
- 문제: `policy-admin-runtime-runbook.md` 와 일부 admin login 예시는 이미 `password123!` 로 교정했지만, `runtime-api-smoke-commands.md` 안의 공통 변수 블록에는 여전히 `SMOKE_PASSWORD="Password123!"` 가 남아 있었다. 이 상태면 문서상으로는 “교정 완료”처럼 보이는데, 실제로는 복붙 진입점 하나가 아직 실패 값을 유지한다.
- 해결: `runtime-api-smoke-commands.md` 의 남은 공통 변수 블록도 현재 local smoke baseline인 `password123!` 로 맞췄다.
- 이유: 예시 자격 증명 drift는 파일 단위가 아니라 블록 단위로 남는 경우가 많다. 하나의 runbook만 고치고 끝내면 같은 문서군의 다른 copy-paste 진입점이 계속 실패 값을 품고 있을 수 있다.

## 668) top-level 진입점이 `Gov24` 를 여전히 단순 blocked/inactive처럼 부르면, runtime closeout 완료 사실보다 예전 reopen 맥락이 먼저 보인다
- 문제: `documentation-map.md`, `start.md`, `current-state.md` 의 일부 진입점 표현은 `Gov24 blocked 상태`, `inactive/reopen 판단` 같은 예전 wording을 유지하고 있었다. 하지만 현재 truth는 `Gov24` 의 runtime collect/runtime audit은 이미 closeout 되었고, 남은 것은 hard import/backfill blocked와 일부 deferred code 승격 판단이다.
- 해결: top-level 안내 문구를 `Gov24 runtime closeout / blocked track`, `runtime closeout 이후 남은 blocked/deferred 판단` 기준으로 교정했다.
- 이유: entrypoint 문구는 실제 문서 내용을 읽기 전에 인상을 만든다. closeout이 끝난 소스를 여전히 단순 blocked/inactive처럼 소개하면, 현재 active 상태보다 과거 reopen 맥락이 먼저 떠오른다.

## 669) `collect-current-state` 가 아직 `fresh reset 뒤 draft schema/bootstrap 공백` 을 현재 경계처럼 말하면, integrated schema mainline에서도 sidecar가 optional helper처럼 읽힌다
- 문제: `collect-current-state.md` 의 canonical sidecar 설명에는 여전히 “fresh reset 뒤 draft schema/bootstrap 공백은 local helper로 보완” 같은 wording이 남아 있었다. 하지만 현재 PostgreSQL mainline에서는 `service_taxonomies`, `service_taxonomy_terms`, `service_facts` 가 integrated schema의 일부이고, helper/replay smoke는 draft schema를 다시 까는 용도가 아니다.
- 해결: canonical sidecar 설명을 `fresh reset 뒤에도 존재해야 하는 integrated schema` 기준으로 교정하고, local helper/replay smoke는 draft schema auto-apply가 아니라 integrated schema 존재 여부와 collect/replay precondition을 확인하는 보조 경계라고 명시했다.
- 이유: current-state 문서는 현재 계약을 직접 설명하는 문서다. 여기에 예전 draft-bootstrap 표현이 남아 있으면, sidecar가 여전히 optional 또는 임시 복구 대상처럼 읽혀 구조 이해를 흐린다.

## 670) `recommendation-current-state` 가 여전히 `fresh reset 뒤 sidecar 공백` 을 말하면, 현재 문제의 핵심인 replay precondition보다 예전 bootstrap 오해가 먼저 남는다
- 문제: `recommendation-current-state.md` 도 `fresh reset 뒤 sidecar 공백`, `runtime bootstrap이 자동으로 sidecar를 다 복구하는 건 아니다` 같은 표현을 유지하고 있었다. 하지만 현재 PostgreSQL mainline에서 sidecar schema 자체는 integrated이고, fresh reset 뒤 실제로 문제가 되는 건 replay가 기대하는 policy snapshot / canonical read-model 데이터 precondition 쪽이다.
- 해결: 해당 블록을 `fresh reset 뒤 collect/replay 전제` 로 바꾸고, helper/replay smoke는 sidecar bootstrap을 대신하는 경계가 아니라 integrated schema 존재 여부와 collect/replay precondition을 먼저 확인하는 보조 경계라고 정리했다.
- 이유: recommendation current-state는 현재 파이프라인과 검증 전제를 빠르게 읽는 문서다. 예전 sidecar bootstrap wording이 남아 있으면, 현재 구조 문제보다 과거 초기 bootstrap 이슈를 다시 먼저 떠올리게 된다.

## 671) `recommendation-operation-checklist` 가 replay precondition을 여전히 `service_taxonomies 존재` 중심으로만 말하면, 현재 canonical read-model/data 전제를 너무 좁게 읽게 된다
- 문제: `recommendation-operation-checklist.md` 는 replay 전 precondition을 `welfare_services`, `service_taxonomies`, `target row` 정도로만 적고, helper 경계도 `missing sidecar schema` 중심으로 설명하고 있었다. 하지만 현재 replay가 실제로 기대하는 것은 integrated schema 자체보다 canonical sidecar/read-model 데이터와 target row 존재 쪽이다.
- 해결: precondition 설명을 `canonical sidecar / read-model 데이터 존재` 기준으로 넓히고, replay script는 integrated schema 존재 여부와 zero target row 같은 precondition을 먼저 확인하는 보조 경계라고 정리했다.
- 이유: checklist는 실제 실행 직전 보는 문서라서, schema 이름 하나만 보면 현재 필요한 데이터 전제를 놓치기 쉽다. 현재 문제를 더 정확히 설명하는 쪽으로 맞춰야 한다.

## 672) `collect-ops` 가 아직 `MySQL api_sync_logs.status` 라고 적고 있으면, 현재 PostgreSQL mainline에서도 로그 저장 표현이 다른 줄로 오해할 수 있다
- 문제: `collect-ops.md` 의 실행 로그 설명에는 `MySQL api_sync_logs.status` 컬럼이라는 표현이 남아 있었다. 현재 메인라인은 PostgreSQL 기준인데, 이 wording은 상태 저장값의 lowercase 관찰 포인트가 특정 DB vendor에만 묶여 있는 것처럼 읽히게 만든다.
- 해결: 해당 문구를 `현재 PostgreSQL mainline에서도 api_sync_logs.status 저장값은 소문자(...)로 보일 수 있다` 로 교정했다.
- 이유: 현재 active 운영 문서에서는 DB vendor보다 현재 관찰 사실이 우선이다. legacy DB 이름이 남아 있으면 같은 상태값을 보고도 “이건 예전 MySQL 얘기”로 오해할 수 있다.

## 673) `demo-scenario` 가 아직 MySQL/Nginx 기준 환경을 현재 데모 전제처럼 말하면, local validation 문서군과 실제 runtime baseline이 어긋난다
- 문제: `demo-scenario.md` 는 백엔드 데모 전제 환경을 여전히 `Docker Compose + MySQL + Redis + Nginx(선택)` 으로 적고 있었다. 하지만 현재 로컬 검증 기준선은 PostgreSQL mainline이고, 이 문서는 운영 reverse proxy 전제가 아니라 local validation 문서군에 묶여 있다.
- 해결: 데모 기준 환경을 `Docker Compose + PostgreSQL + Redis` 로 교정하고, Nginx/HTTPS 같은 운영 reverse proxy 전제는 이 문서 범위가 아니라고 명시했다.
- 이유: 데모 시나리오는 실제 발표/검수 직전에 바로 복사해 보는 문서다. 환경 설명이 오래돼 있으면 현재 smoke/current-state 기준과 어긋난 준비를 하게 된다.

## 674) `system-docs-index` 가 `db-migration` 을 아직 active draft migration 메모처럼 소개하면, legacy migration inventory와 현재 PostgreSQL truth의 경계가 흐려진다
- 문제: `system-docs-index.md` 는 `db-migration.md` 를 `기존 DB 갱신, draft migration, split-account, sidecar schema 메모` 정도로만 소개하고 있었다. 이 표현만 보면 현재 실행 판단도 그 문서 하나에 의존하는 것처럼 읽힐 수 있다.
- 해결: `db-migration.md` 를 `legacy migration / draft sidecar 메모` 로 다시 소개하고, 현재 실행 판단은 `current-state.md`, `testing.md`, 관련 runbook을 먼저 보라고 보강했다.
- 이유: system index는 DB/데이터 구조 판단을 위해 가장 먼저 여는 entrypoint 중 하나다. 여기서 legacy 메모와 current truth의 위계를 분명히 해야 active 문서 우선순위가 실제로 유지된다.
