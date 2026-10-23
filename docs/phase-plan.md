# 작업 현황

## 현재 범위

문서의 로컬·서버·로컬 앱과 원격 DB 환경을 구분하고, 중복 안내와 과거 누적 기록을 정리했습니다. 재검토 오류·검증 스크립트 2개를 보완한 뒤 source 온보딩 6개→2개, 이어 Gov24·추천 AI exclusion·수집·공통 운영·인증 32개→10개로 감축했습니다. 감축 후 운영 인증·데모·프론트 URL 계약·추천 이력의 불일치도 문서에서 보완했습니다. 앱 기능, DB schema, Java 버전, CI 실행 정책, 운영 서버는 변경하지 않았습니다.

이후 2026-10-22 작업 지침 보완에서는 합의한 코드 패턴·예외 승인·Git 규칙을 연결하고 프론트 단위 테스트를 CI에 추가했습니다. 앞선 문서 정리와 구분하며 상세 범위·검증은 아래 해당 기록을 따릅니다.

후속 요청으로 요구사항 확인용 `grilling`을 프로젝트 범위에 추가하고, 구조·구현·DB·프론트·디버깅 관련 스킬 6개를 검토했습니다. 설치한 스킬과 도입 추천 후보를 아래 기록에서 구분합니다.

토큰 사용량 검토 뒤 사용자가 초기 구성 1안을 선택해, 현재는 `grilling`과 프로젝트용 `systematic-debugging` 2개로 운영합니다. 현재 설치·적용 기준은 [작업 가이드](work-guide.md#요구사항-확인과-스킬-사용)를 따릅니다.

## 문서 정리

- 환경 공통 기준: [environment-guide.md](core/environment-guide.md).
- 정보의 대표 위치와 정리 범위: [documentation-map.md](documentation-map.md).
- 현재 구현·확인 과제: [current-state.md](current-state.md).
- 과거 누적 작업과 관측 결과: [정리 전 작업 이력](history/core/phase-plan-before-doc-cleanup.md).

실제 적용한 문서 이동·통합은 documentation-map에 기록합니다. 검증 결과는 실행 후 아래에 갱신하며, 서버 관측을 로컬 검사 결과로 대신하지 않습니다.

## 기존 보안 구현의 보존 기준

다음은 이번에 새로 구현한 기능이 아니라 기존 커밋의 보안 경계입니다. 관련 문서 계약 테스트가 이 기준을 함께 검사합니다.

- 운영 smoke 로그/아티팩트 보안도 좁혔다.
- auth observation/session, admin dashboard/collect/recommendation/attention, ops baseline/observation 계열 smoke cleanup에 공통 `smoke_sanitize_artifacts` 를 연결했다.
- latest snapshot을 publish하는 auth/ops observation wrapper는 publish 직전에 한 번 더 sanitize한다.
- 이 함수는 `KEEP_ARTIFACTS=true` 로 디버깅 산출물을 보존하더라도 cookie jar를 삭제하고 token/password/API key/email/userKey 계열 값을 redaction한다.
- 운영 smoke 아티팩트 sanitizer 범위를 recommendation/chat/collect/Gov24/real-user 진단 smoke까지 확장했다.
- nightly/active-baseline/current-priority 상위 handoff wrapper의 artifact 경계도 닫았다.
- 운영 artifact directory / secret-like 파일 권한도 공통 경로로 고정했다.
- `smoke_sanitize_artifacts` 는 redaction 뒤 artifact tree의 directory `700`, file `600`을 적용하고 권한 제한을 유지한다.
- `smoke_publish_file` 은 `install -m 600`을 사용한다.

상세 계약은 [보안 현재 상태](core/security-hardening-current-state.md), [원래 작업 이력](history/core/phase-plan-before-doc-cleanup.md)을 확인합니다.

## 검증

2026-10-21 구조 정리 당시 검증:

- `DB_USERNAME=app_core_rw ./gradlew test --rerun-tasks --no-daemon`: 실제 재실행 성공. 288 suites / 1,254 tests, 실패·오류 0, 실제 SMTP opt-in 검사 1개 skip. 처음의 일반 test 명령은 `UP-TO-DATE`였으므로 재실행 결과와 구분합니다.
- `npm run test:unit`: 22 suites / 54 tests 통과, 실패 0.
- 저장소 Markdown 링크·앵커와 코드/스크립트의 문서 경로를 정적으로 대조: 끊어진 대상 0. 외부 웹페이지의 가용성을 검사한 결과는 아닙니다.
- 누적 기록 6개: 기준 커밋의 본문 보존 확인. 이동·통합에 필요한 링크 대상과 보관 표지만 변경했습니다.
- 프론트 흐름 통합: 기대 동작 90줄과 테스트명 참조 52개 보존 확인.
- `bash -n deploy/smoke/run-local-frontend-observation-suite.sh`, 내장 Python 구문 검사, 새로 작성·수정한 Bash 예시 구문 검사, `git diff --check` 통과.

통합 테스트·브라우저·운영 smoke·DB patch·실제 수집/AI/메일/푸시는 이번 범위에서 실행하지 않았습니다. 앱 코드·DB schema·CI·운영 env는 변경하지 않았으며, 스크립트 변경은 통합 문서의 안내 포인터에 한정했습니다. 커밋과 푸시는 수행하지 않았습니다.

## 문서 재검토 보완

상태: 보완 완료. 추가 대규모 통폐합은 다음 기능 작업의 필수 선행 조건이 아닙니다.

- 인증 상태·토큰·사용자 프로필은 메모리에 두고 필터 설정만 persist하는 실제 계약으로 수정했습니다.
- 운영 runtime env를 Compose preflight·앱 기동 전에 생성하도록 순서를 맞추고 관리자 비밀번호 예시를 숨김 입력으로 교체했습니다.
- 통합 프론트 runbook의 오래된 필터 6곳을 실제 테스트명으로 교체하고 선택 프로필 확인 모달 동선을 반영했습니다. 검색어는 URL이 아닌 라우터 상태로 유지하는 동작도 관련 안내에 맞췄습니다.
- Git 작업 규칙의 단위·문서 계약·로컬 통합 준비와 기록 형식을 현재 안내에 맞췄습니다.
- 추천 후보 조회·정렬·교체 저장·로그와 인증된 POST 클릭 경계를 코드 기준으로 보완하고, 분석 SQL은 잔존 로그의 클릭 표시 비율로 한정했습니다.
- 과거 추천 보류 방침과 현재 개선 과제를 분리하고 수집 payload를 실제 HTTP 원문 보존으로 설명하지 않도록 수정했습니다.
- `youth_welfare` DB와 `public`/`youth_welfare_pii` schema를 구분했습니다. 기존 로컬 관측의 snapshot 누락·지원조건 부분 실패·embedding 미갱신은 미해결 상태로 연결했습니다.

2026-10-21 보완 검증:

- `backend/`에서 `DB_USERNAME=app_core_rw ./gradlew test --tests '*ContractTest' --rerun-tasks --no-daemon`: 19 suites / 98 tests, 실패·오류·skip 0. 문서 보완 뒤 계약 검사를 실제 재실행했습니다.
- `frontend/`에서 `npm run test:unit`: 22 suites / 54 tests 통과.
- `frontend/`에서 `PLAYWRIGHT_SKIP_WEBSERVER=true ./node_modules/.bin/playwright test --list --reporter=json`: 실제 등록 테스트 56개를 읽고, 통합 runbook의 10개 필터 안 52개 선택 항목이 모두 일치함을 대조했습니다. 브라우저·앱·fixture를 실행한 검사는 아닙니다.
- Markdown 284개(문서 폴더 282개)의 링크·앵커 2,494개와 코드/스크립트의 문서 참조 76개: 오류 0. 새로 작성·변경한 Bash 예시는 `bash -n`으로 검사했습니다.
- 보관한 누적 기록 6개의 본문 보존, 기존 로컬 수집 기록 원문 유지, runtime env 생성이 Compose preflight/기동보다 앞서는 순서와 비밀번호·클릭 SQL 설명을 정적으로 확인했습니다. `git diff --check` 통과.

이번 보완은 Markdown 16개에 한정했습니다. 실제 DB/SQL·수집·AI·메일·푸시·운영 smoke·브라우저 동작 검증은 실행하지 않았고, 앱 코드·스크립트·CI·운영 env를 추가 변경하지 않았습니다. 커밋·푸시는 하지 않았습니다.

아래 로컬 수집 기록은 별도 실행의 기존 근거입니다. 이번 보완에서 수집을 재실행하거나 schema·운영 설정을 복구한 것이 아닙니다.

## 2026-10-22 실행 계약·검수 기준 보완

- 복구 DB 감사와 앱 쓰기 smoke를 분리했습니다. 별도 runtime env·모든 datasource·Redis·외부 호출 격리를 요구하고, URL/격리 확인/Redis 컨테이너를 명시하지 않거나 기본·운영 대상을 지정하면 런북 guard가 중단합니다. guard는 실제 앱 연결 상태를 자동 입증하는 도구가 아닙니다.
- cookie-only logout의 user-wide cutoff, 해시된 Redis key, 관리자 actor/target 해시 감사 로그를 현재 코드와 맞췄습니다. **Redis 비영속 상태 유실 뒤 미만료 회수 JWT 재허용 가능성은 미해결 보안 과제**로 auth·배포·현재 상태에 명시했습니다.
- 추천 클릭 smoke를 상세 GET(클릭 미변경 확인) → 인증된 POST JSON → DB 클릭/시각 확인으로 수정했습니다. 검색 성능 스크립트도 search/suggestions POST JSON으로 바꾸고, 측정 HTTP 오류를 `passed`로 보고하지 않도록 했습니다. 이전 GET 오류 응답과 새 성공 응답의 시간을 동등 비교하지 않습니다.
- 프론트 수동 QA의 검색어·로그인 reason을 URL query가 아닌 router state 계약으로 정정하고, 과거 수동 관측은 별도 표지와 함께 유지했습니다.
- SRS의 기존 FR/NFR 요구는 유지하고 미입력 기본값·선택지·신규 노출·점수식·클릭 방식·로그 영구 보관·알림 단계의 차이를 대조표로 분리했습니다. 요구 변경 승인이나 결함 해결로 처리하지 않았습니다.
- 신규 source 판정은 공식 codebook, 검토된 label/내부 코드, 불명확 필드 보류를 구분했습니다. AI 추정만으로 hard eligibility를 확정하지 않으며 Gov24의 active runtime/label-first와 외부 공식 코드 import 보류를 구분합니다.

검증:

- `backend/`에서 `DB_USERNAME=app_core_rw ./gradlew test --tests '*ContractTest' --tests '*AuthTokenServiceTest' --tests '*UserSessionRevocationServiceTest' --tests '*RecommendationLogServiceTest' --tests '*NotificationSlotSelectorTest' --rerun --no-daemon`: 24 suites / 132 tests, 실패·오류·skip 0. 이 중 문서/스크립트 계약은 20 suites / 112 tests이며 신규 오프라인 실행 검사 14개를 포함합니다.
- `frontend/`에서 `npm run test:unit`: 22 suites / 54 tests, 실패 0.
- Playwright `--list --reporter=json`으로 등록 테스트 56개를 읽고 통합 runbook의 10개 필터·52개 선택 항목 일치를 확인했습니다. 검색어 URL 비노출·state 복귀 테스트도 등록돼 있습니다. 브라우저·앱·fixture를 실행한 것은 아닙니다.
- Markdown 284개(문서 폴더 282개)의 링크·앵커 2,517개와 코드/스크립트의 문서 참조 77개: 오류 0. 수정 스크립트 2개의 `bash -n`, 신규/변경 Bash 예시 6개와 내장 Python 1개의 구문 검사, `git diff --check`를 통과했습니다.
- SRS의 기존 FR/NFR 요구 행 108개와 기존 2026-10-21 로컬 수집 기록 원문을 작업 시작 시점과 대조해 동일함을 확인했습니다. 보관 문서 본문은 이번 작업에서 변경하지 않았습니다.

기존 문서 편집 기준에 따라 유효한 과거 관측과 요구사항 원문을 보존했습니다. 실제 DB·Redis·AWS·운영 환경·앱 인증 동작은 변경하지 않았고, 실제 수집/AI/메일/푸시·부하·브라우저 검증은 실행하지 않았습니다. 커밋·푸시도 하지 않았습니다. 데이터 보존·자격 의미·세션 회수 내구성은 별도 기능 작업으로 남습니다.

## 2026-10-22 소스 온보딩 문서 감축

완료 범위는 source 온보딩 문서 묶음입니다. 새 보관 사본은 만들지 않았습니다.

- 6개 / 1,539줄 / 39,075자 → 2개 / 203줄 / 15,198자. 글자 수 61.1%, 줄 수 86.8% 감소이며, 이 비율은 프로젝트 전체가 아닌 해당 묶음 기준입니다. `docs/` Markdown 파일은 282개 → 278개입니다.
- architecture/checklist의 분류·저장·근거·호환 판정은 [가이드](policy/policy-source-onboarding-checklist.md) 1~5절, code-entrypoints는 6절, 검증은 7절, playbook의 source별 출처·사례는 8절, 초기 DB 메모·우선순위는 9절에 통합했습니다. 작성 양식은 [템플릿](policy/policy-source-onboarding-template.md)으로 유지했습니다.
- 중복 원본 architecture·playbook·canonical-onboarding-priority·code-entrypoints 4개를 제거했습니다. Git 기준 커밋 `b6af3ad7`에서 기존 원문을 복구할 수 있으며, 이후 보완한 판정 기준은 통합 문서에 반영했습니다.
- 기존 공식 조사 URL 7개, source 분류 예외, 출처·내부 코드·AI 검수 경계, 코드 진입점과 로컬/운영 실행 경고를 유지했습니다. 2026-04-29 메모의 총계 3,376과 source 합계 3,583·category 합계의 불일치는 숨기지 않고 당시 기록의 한계로 표시했습니다.
- 정책 목차·현재 상태·Gov24 안내와 과거 문서의 링크를 통합 절로 연결했습니다. 과거 파일명·사건 서술은 유지하고 이력 문서는 링크 목적지만 변경했습니다. 정리 지도의 오래된 “스크립트는 안내 경로만 변경” 요약도 앞선 후속 수정 범위에 맞췄습니다.

검증: `backend/`에서 `DB_USERNAME=app_core_rw ./gradlew test --tests '*ContractTest' --rerun --no-daemon`을 실제 재실행해 20 suites / 112 tests, 실패·오류·skip 0을 확인했습니다. 내부 Markdown 링크·앵커·코드의 문서 참조와 `git diff --check`도 통과했습니다. SRS 요구 행 108개와 기존 로컬 수집 기록은 그대로이며, 이력 문서 6개는 링크 목적지 외 본문이 동일함을 대조했습니다.

이번 감축은 Markdown만 변경했으며 앱 코드·스크립트·DB·운영 설정·CI는 추가 변경하지 않았습니다. 실제 수집·AI·외부 발송·운영 smoke·브라우저 검증과 커밋·푸시는 수행하지 않았습니다.

## 2026-10-22 다섯 문서 묶음 감축

Gov24 → 추천 AI exclusion → 수집 → 공통 운영 → 인증 순서로 원문·참조·관련 코드를 읽고 통합했습니다. 새 보관 사본은 만들지 않았으며 중복 원본 22개를 제거했습니다. 기존 원문은 Git 기준 커밋 `b6af3ad7`에서 복구할 수 있고, 이전 미커밋 보완의 보호 계약도 통합 문서에 반영했습니다.

| 묶음 | 파일 전→후 | 줄 수 전→후 | 현재 위치 |
| --- | --- | --- | --- |
| Gov24·정책 진행/계획 | 8→2 | 1,691→145 | [데이터 계약](policy/policy-gov24-lane-closeout.md), [작업·검증](policy/policy-gov24-implementation-checklist.md) |
| 추천 AI exclusion/snapshot/gate | 15→2 | 1,394→146 | [파일 조회·해석](recommendation/recommendation-ai-exclusion-latest-status-runbook.md), [증거 재수집](recommendation/recommendation-ai-exclusion-baseline-refresh-runbook.md) |
| 수집 | 3→2 | 963→154 | [현재 동작](collect/collect-current-state.md), [실행·장애](collect/collect-ops.md) |
| 공통 운영 | 3→2 | 646→123 | [baseline](core/ops-baseline-runbook.md), [점검·인계](core/final-ops-closeout-checklist.md) |
| 인증 | 3→2 | 487→106 | [현재 계약](auth/auth-session-revocation-current-state.md), [검증](auth/auth-operation-checklist.md) |

선정 묶음 합계는 32개/5,181줄/186,099자 → 10개/674줄/51,043자이며 본문 글자 수 72.6% 감소입니다. 이 비율은 프로젝트 전체가 아닌 선정 묶음 기준입니다. `docs/` Markdown은 이번 작업 시작 278개 → 256개입니다. 관련 목차도 중복 목록을 제거하고 고유한 문서 진입점을 유지했습니다.

- Gov24는 구현·외부 codebook 보류·과거 local/server 관측을 분리했습니다. 과거 단계별 금지·“로컬 할 일 거의 없음/서버 생긴 뒤”를 현재 규칙으로 사용하지 않습니다.
- 추천 helper script 15개는 제거하지 않았습니다. 파일 비교/export/gate와 실제 suite/snapshot/refresh를 구분하고 승인 상태의 준비·결정·record·실행 단계를 별도로 유지했습니다.
- 수집은 application 기본값과 service fallback을 구분하고, DB lock·manual/scheduled·zero limit·부분 실패·snapshot/embedding 미해결 문제를 보존했습니다. auth observation의 기본 가입·잠금·탈퇴·회수 실행도 조회와 구분해 경고했습니다.
- 운영 점검에 배포·repair·cron 설치를 자동 포함하지 않습니다. Redis 회수 상태 유실 위험과 추천 로그/북마크 보존 결함은 해결 완료가 아니라 별도 기능 과제입니다.
- Markdown과 Gov24 taxonomy 검증 script의 문서 안내 포인터 1곳만 변경했습니다. 소스/CI 해시 대조와 해당 script의 역치환 해시로 실행 로직·기존 비문서 변경이 그대로임을 확인했습니다.

검증: backend에서 `DB_USERNAME=app_core_rw ./gradlew test --tests '*ContractTest' --tests '*AuthTokenServiceTest' --tests '*UserSessionRevocationServiceTest' --tests '*RecommendationLogServiceTest' --tests '*NotificationSlotSelectorTest' --rerun --no-daemon`을 실제 재실행해 24 suites/132 tests, 실패·오류·skip 0을 확인했습니다. 내부 링크·앵커·코드의 문서 포인터, Bash 예시 7개·수정 script의 `bash -n`/내장 Python 구문, 기존 helper 15개 참조·목차의 고유 대상 보존, `git diff --check`도 통과했습니다. 이력 19개는 링크 목적지 외 본문 동일, SRS 요구 행 108개와 기존 로컬 수집 기록 원문도 동일함을 대조했습니다.

실제 DB/Redis/서버 설정·수집·AI·발송·운영 smoke·브라우저·cron 설치는 실행하지 않았고 커밋·푸시도 하지 않았습니다.

## 2026-10-22 감축 후 문서 정합성 보완

추가 삭제·이동 없이 기존 Markdown 15개를 보완했습니다. `docs/` 파일 수는 256개로 유지합니다.

- 운영: 공통 인증 도우미의 mint 기본값 `false`와 cron installer의 `true`를 구분하고 운영 예시에 `false`를 명시했습니다. 토큰/로그인 자격·만료·nightly auth의 실제 쓰기 영향을 설명하고 cron의 같은 명령 내 `$APP_ROOT` 확장 오류도 명시 경로로 고쳤습니다. installer 코드·기존 서버 cron은 그대로이므로 실제 적용값은 별도 확인 대상입니다.
- 데모: 검색·수신거부를 POST JSON, 쿠키 기반 요청을 허용 origin 조건으로 맞췄습니다. 이메일 인증·보호된 요청/응답 파일·외부 발송 비활성 예시를 보완하고 PostgreSQL BOOLEAN 집계·잔존 로그의 클릭 표시 비율·가중치 설정 대조로 수정했습니다.
- 프론트 QA: 선택 세션은 `activeChatSessionId`, reset query/hash와 unsubscribe hash는 읽은 뒤 URL 제거로 맞췄습니다. 현재 체크리스트·결과 템플릿을 갱신하고 날짜가 있는 과거 QA bullet은 유지했습니다.
- 추천: README의 과거 기능 동결 지시를 현재 규칙에서 제외했습니다. PR 검토/draft/merge 후 인계·단계별 개선 기록 4개는 제목·도입부에서 과거 범위를 밝히고 상태 tuple·측정·판단 본문은 보존했습니다. 현재 PR/승인 상태를 새로 확정하지 않습니다.

검증: backend의 `DB_USERNAME=app_core_rw ./gradlew test --tests '*ContractTest' --rerun --no-daemon` 실제 재실행은 20 suites/112 tests, frontend의 `npm run test:unit`은 22 suites/54 tests로 실패·오류·skip 0입니다. 변경 문서의 Bash 예시 32개·cron 명령 3개·JSON 4개·Python 1개의 구문과 코드/설정 대조, 내부 링크·앵커·코드 문서 포인터, `git diff --check`를 확인했습니다. Playwright는 `PLAYWRIGHT_SKIP_WEBSERVER=true ./node_modules/.bin/playwright test --list --reporter=json`으로 등록 56개와 관련 테스트 존재만 확인했으며 브라우저·fixture를 실행하지 않았습니다.

비문서 파일의 해시, SRS 요구 108행·기존 로컬 수집 기록·history/archive 전체, 과거 cron 명령·QA bullet·추천 검토 근거가 작업 시작 시점과 동일함을 대조했습니다. 실제 DB/Redis/운영 환경·cron·수집/AI/발송은 실행·변경하지 않았고 커밋·푸시는 하지 않았습니다. 이번 완료는 위 문서 불일치 보완에 한정하며 기능 결함 해결이나 모든 과거 문장의 재검증을 뜻하지 않습니다.

## 2026-10-22 코드 패턴·Git 작업 지침 보완

- 별도 스킬 설치 없이 `AGENTS.md`를 추가해 기존 `CLAUDE.md`의 환경·보존 계약과 대표 문서로 연결했습니다. 상세 규칙은 중복 작성하지 않았습니다.
- `work-guide.md`에 Spring 객체 관리·어댑터·전략·빌더·팩토리·템플릿 메서드의 사용 조건과 현재 예시를 기록했습니다. 새 코드·직접 수정부에 우선 적용하고, 규칙 예외는 사전 승인 → 적용·검증 → 최종 채택 판단으로 진행합니다.
- `github-workflow.md`에 작업 전 변경·스테이징 보존, 실제 커밋 대상 확인, 조건부 worktree 사용, 충돌·공유 이력·변경 폐기·CI 실패 대응을 보완했습니다. 기존 푸시 요청 원칙과 작은 커밋·문제 해결 단위 PR 규칙을 유지했습니다.
- 외부 Git 관련 스킬 5개의 원문과 검토 커밋을 확인하고 채택·제외한 부분을 Git 문서에 남겼습니다. 고정 메뉴·매번 별도 승인·무조건 전체 검사 등은 프로젝트 절차로 가져오지 않았습니다.
- `.github/workflows/ci.yml`에 `npm ci` 직후 `npm run test:unit` 단계를 추가했습니다. 앱·DB를 시작하기 전에 단위 테스트 실패를 확인하도록 했습니다.

검증:

- `backend/`에서 `DB_USERNAME=app_core_rw ./gradlew test --tests '*ContractTest' --rerun-tasks --no-daemon`: 20 suites / 112 tests, 실패·오류·skip 0. 5개 Gradle 작업이 실제 실행됐습니다.
- `frontend/`에서 `npm run test:unit`: 22 suites / 54 tests, 실패·skip 0.
- CI YAML 구문을 파싱하고 작업 전 설정과 구조를 비교해 `npm ci` 직후 단위 테스트 단계만 추가됐음을 확인했습니다. 변경한 Markdown 5개의 내부 링크·앵커 55개와 변경 범위의 `git diff --check`도 통과했습니다.
- 작업 전 파일 해시와 대조해 기존 파일 수정은 이 작업의 5개 파일에 한정되고, 새 파일은 `AGENTS.md`뿐임을 확인했습니다. 기존 스테이징 내용과 HEAD 커밋도 보존했습니다.

로컬 작업 브랜치는 `docs/common-development-rules`입니다. 기존 미커밋 변경을 함께 커밋하지 않았으며 푸시·원격 CI·GitHub 보호 규칙 변경은 수행하지 않았습니다. 앱·DB 기동이나 실제 수집·AI·발송·브라우저 검증도 이번 범위에 포함하지 않았습니다.

## 2026-10-22 grilling 추가와 스킬 후보 검토

승인된 요구사항 확인용 `grilling` 본체를 `skill-installer`로 `.agents/skills/grilling`에 설치하고 `skill-creator` 기준으로 프로젝트용 지침을 작성했습니다. 원문 커밋 `5beb35b040e3de97e54fa9e386d7e42d15c295c2`와 MIT 라이선스를 보존했습니다. 호출용 `grill-me` 래퍼는 별도로 설치하지 않았으며, 설치한 본체는 다른 `Skill` 도구에 의존하지 않습니다.

중요한 미결정을 먼저 확인하고 사용자 이해 확인 뒤 종속 구현을 시작합니다. 질문은 독립적인 핵심 1~3개로 나누며, 무응답을 승인으로 해석하지 않습니다. 원문의 추천안 동의 유도·하위 에이전트 의무 사용·모든 미래 분기 질문을 제외했습니다. 평범한 수정과 스킬 평가·설치에는 인터뷰를 강제하지 않습니다. AGENTS.md·CLAUDE.md는 작업 가이드의 대표 절로 연결했습니다.

아래 표는 추가 설치 결정 전 6개 후보의 검토 결과입니다. 이후 채택 상태는 아래 2개 초기 운영 기록과 작업 가이드에서 구분합니다. 소스 본문과 관련 참고 파일을 확인하고 Java 17 / Spring Boot 3.5 / JPA, 목적별 Hikari datasource, React 19 / Vite / React Query / Zustand와 대조했습니다. 원문 링크는 검토 커밋에 고정했습니다.

| 후보 원문 | 권고 | 프로젝트에서 유용한 점과 조정 조건 |
| --- | --- | --- |
| [systematic-debugging](https://github.com/obra/superpowers/blob/2ce8aa15d05bd57213e6f746f953aafb7ded7a8b/skills/systematic-debugging/SKILL.md) | 우선 도입 추천, 프로젝트용 수정 필요 | 재현·최근 변경·실패 경계·단일 가설 검증으로 추측성 수정을 줄임. 원문의 미설치 TDD/검증 스킬 의무 호출, 실패 3회만으로 아키텍처 오류 단정, 근거 없는 비율 주장은 제외. 진단 로그도 기존 PII·비밀정보 제한을 따르고 단순 오류에 불필요한 절차를 늘리지 않음. |
| [springboot-patterns](https://github.com/affaan-m/ECC/blob/90606c8315ee09cafc303515c5f33024db6b9882/skills/springboot-patterns/SKILL.md) | 도입 추천, 프로젝트용 수정 필요 | 트랜잭션·검증·예외·캐시·비동기·JPA 경계에 직접 활용 가능. 모든 예외를 재시도하는 예제는 재시도 가능 오류·횟수·멱등성(반복 실행해도 의도치 않은 중복 효과가 없는 성질)을 구분하도록 대체. 기존 API 오류 응답, 인증·프록시·발송 계약을 예제로 덮어쓰지 않음. |
| [supabase-postgres-best-practices](https://github.com/supabase/agent-skills/blob/c9be0e931b7930f7d02126d04774d904c381e7d7/skills/supabase-postgres-best-practices/SKILL.md) | 도입 추천, DB 작업에 한정해 수정 | Supabase 호스팅 없이도 SQL·인덱스·실행계획·연결 수·잠금·최소권한 점검에 유용. 현재 Hikari pool과 목적별 권한을 먼저 확인하고 PgBouncer·Supabase Auth·RLS를 자동 도입하지 않음. 예시의 처리량 수치를 프로젝트 측정값으로 사용하지 않고 운영 schema·권한 변경 권한으로 확대하지 않음. |
| [vercel-react-best-practices](https://github.com/vercel-labs/agent-skills/blob/063bee94c3f4df8453406c830b0a7df0f2860278/skills/react-best-practices/SKILL.md) | 도입 추천, React/Vite 적용 범위로 수정 | 독립 요청 병렬화, 불필요한 상태·Effect·재렌더 감소, 번들 분리에 활용. Next.js·RSC·Server Actions·`next/dynamic` 예제를 적용하지 않음. SWR 도입 대신 기존 React Query의 캐시·요청 중복 제어를 사용하고 현재 `React.lazy` 경로와 비교. 측정 없이 메모이제이션을 일괄 추가하지 않음. |
| [java-coding-standards](https://github.com/affaan-m/ECC/blob/90606c8315ee09cafc303515c5f33024db6b9882/skills/java-coding-standards/SKILL.md) | 별도 설치 보류, 공통 Java/Spring 규칙에 필요한 내용 흡수 추천 | 명명·Optional·가독성·예외·타입 안정성은 유용하지만 Spring 스킬과 중복되고 Quarkus 지침이 섞임. JPA entity·수집 미입력에 불변성·non-null을 일괄 강제하지 않음. 테스트 예제의 `@MockBean`은 [Spring Boot 3.5 공식 API](https://docs.spring.io/spring-boot/3.5/api/java/org/springframework/boot/test/mock/mockito/MockBean.html)에서 deprecated이며 현재 테스트가 쓰는 `@MockitoBean`과 대조 필요. |
| [architecture-patterns](https://github.com/wshobson/agents/blob/0874a27b17a22e2b7c2e2bcd725768e0803cad06/plugins/backend-development/skills/architecture-patterns/SKILL.md) | 구조 설계·의존성 문제 검토에 한정한 조건부 후보 | 모듈 책임·변경 영향·순환 의존성·도메인 용어를 검토하는 자료로 유용. 모든 경계의 인터페이스화·ORM과 도메인 전면 분리·DDD·서비스 분리를 기본 구현 규칙으로 채택하지 않음. 현재 2인 팀의 Spring/JPA 구조에서 해결할 문제·대안·이행 비용을 먼저 비교하고 DB 없는 테스트가 트랜잭션·SQL 검증을 대체한다고 보지 않음. |

인기도는 2026-10-22 GitHub API 조회 기준 저장소 전체 별표 수이며 개별 스킬 추천 수나 효과 검증값이 아닙니다: `obra/superpowers` 303,444, `affaan-m/ECC` 286,113(두 후보가 같은 저장소), `wshobson/agents` 41,538, `vercel-labs/agent-skills` 32,807, `supabase/agent-skills` 2,907. 설치한 `mattpocock/skills` 저장소는 300,456입니다. 원문 적합성과 현재 코드의 근거를 우선해 권고했습니다.

검증:

- `python3 /home/minseok/.codex/skills/.system/skill-creator/scripts/quick_validate.py .agents/skills/grilling`: 통과. 스킬 frontmatter·이름 등 형식을 확인했습니다.
- `backend/`에서 `DB_USERNAME=app_core_rw ./gradlew test --tests '*ContractTest' --rerun-tasks --no-daemon`: 20 suites / 112 tests, 실패·오류·skip 0, 5개 Gradle 작업 실제 실행.
- 변경한 Markdown 5개의 내부 링크·앵커 55개, 원문 MIT 라이선스의 Git blob SHA 일치, 변경 범위 `git diff --check`를 확인했습니다. 작업 전 해시와 대조해 기존 파일 변경은 AGENTS.md·CLAUDE.md·work-guide.md·phase-plan.md 4개에 한정되고 새 파일은 grilling의 SKILL.md·LICENSE·agents/openai.yaml 3개입니다. 기존 스테이징 내용과 HEAD는 보존했습니다.

스킬 파일의 형식 검사는 행동의 정확성이나 모든 다음 작업에서의 선택을 보장하는 검증이 아닙니다. 앱·CI 설정은 이번에 변경하지 않았으며 운영·DB·브라우저·외부 API·원격 CI 실행과 커밋·푸시는 수행하지 않았습니다.

## 2026-10-22 스킬 2개로 초기 운영

- 사용자 선택에 따라 기존 `grilling`을 유지하고 `systematic-debugging`만 프로젝트 범위에 추가했습니다. 나머지 5개 후보는 설치하지 않았습니다.
- `skill-installer`로 검토한 `obra/superpowers` 커밋 `2ce8aa15d05bd57213e6f746f953aafb7ded7a8b`를 가져온 뒤, `skill-creator` 기준으로 재현·원인 조사·단일 가설 검증·수정·재확인 절차를 짧게 작성했습니다. 원문 MIT 라이선스와 출처를 보존했습니다.
- 다른 스킬 의무 호출, 실패 횟수에 따른 아키텍처 오류 단정, 근거 없는 통계와 반복적인 전체 검사는 제외했습니다. 현재 프로젝트와 무관한 upstream 테스트 예제·보조 스크립트도 포함하지 않았습니다.
- AGENTS.md는 작업 가이드로 연결하고 작업 가이드에 두 스킬의 적용 조건과 필요한 본문·문서 절만 읽는 기준을 기록했습니다. 기존 grilling 지침과 앱·CI 설정은 변경하지 않았습니다.

검증:

- `python3 /home/minseok/.codex/skills/.system/skill-creator/scripts/quick_validate.py .agents/skills/systematic-debugging`: 통과. 추가한 SKILL.md는 33줄·2,963자이며 설명은 적용 조건만 짧게 명시했습니다.
- `backend/`에서 `DB_USERNAME=app_core_rw ./gradlew test --tests '*ContractTest' --rerun --no-daemon`: 20 suites / 112 tests, 실패·오류·skip 0. test 작업을 실제 재실행했고 나머지 4개 컴파일·리소스 작업은 변경이 없어 UP-TO-DATE였습니다.
- 변경한 Markdown 4개의 내부 링크·앵커 45개, 원문 라이선스의 Git blob SHA 일치, 변경 범위 `git diff --check`를 확인했습니다.
- 작업 전 해시와 대조해 기존 파일 수정은 AGENTS.md·work-guide.md·phase-plan.md 3개, 새 파일은 systematic-debugging의 SKILL.md·LICENSE 2개에 한정됐습니다. 기존 grilling, 다른 사용자 변경, 스테이징 내용과 HEAD 커밋을 보존했습니다.

형식·문서 계약 검사는 실제 모델의 디버깅 행동을 실험한 결과와 구분합니다. 이번에는 스킬·지침만 변경했으며 앱·DB·외부 서비스·원격 CI 실행과 커밋·푸시는 수행하지 않았습니다.

## 2026-10-22 에이전트·스킬·공통 작업 지침 점검

공통 진입점·작업/Git/환경/검증 문서와 설치한 스킬 2개를 점검하고, 현재 문서의 상시 지시와 과거 동결·승인 문구를 검색했습니다. 모든 업무 문서의 사실관계나 실제 운영 동작을 다시 검증한 작업은 아닙니다.

- 공통 환경·구현 보존 계약을 `CLAUDE.md`에서 `AGENTS.md`로 옮기고 CLAUDE.md는 공통 진입점을 참조하도록 줄였습니다. 기존 데이터·권한·환경 보호 조건은 유지했습니다. 링크는 관련 절을 찾는 경로이며 모든 연결 문서를 반복해서 읽으라는 지시가 아님을 명시했습니다. 두 파일 합계는 3,730 → 3,455 UTF-8 bytes이며 실제 토큰 수를 측정한 값은 아닙니다.
- `grilling`의 일반적인 검토 요청까지 인터뷰로 해석할 여지를 줄이고, 명시적 인터뷰 또는 구현에 영향을 주는 중요한 미결정에 적용하도록 본문·설명을 맞췄습니다. 질문 도구가 없을 때 채팅으로 질문할 수 있도록 했습니다. `systematic-debugging`은 설치·기능 개발 중의 실제 오류도 포함하고, 실패 없는 작업은 제외하도록 설명을 고쳤습니다.
- 작업 가이드에 현재 요청·기존 승인과 일반 권고의 관계, 문서 충돌 시 사실 조사와 결정 확인의 구분을 명시했습니다. 스킬 본문 1~2개는 필요한 지침을 생략하는 상한이 아님을 분명히 했습니다. 합의한 규칙 예외의 사전 승인 → 적용·검증 → 최종 채택 판단은 유지했습니다.
- 새 브랜치는 독립적인 해결 목표에 만들고 같은 목표의 후속 task는 현재 브랜치를 사용할 수 있도록 표현을 맞췄습니다. 변경 없는 조회·검토·상담은 별도 요청 없이 기록만을 위해 저장소를 수정하지 않도록 작업/Git/검증 문서를 맞췄습니다.
- 문서 계약 검증은 관련 test task를 실제 재실행하는 `--rerun`을 기본 예시로 통일했습니다. 전체 테스트·의존 task의 강제 재실행은 영향에 따라 선택하며 관련 계약 검사와 실제 실행 여부 확인은 유지했습니다. 문서 역할 지도에 에이전트·작업 지침의 대표 위치를 추가했습니다.

검증: 두 스킬의 `quick_validate.py` 형식 검사, 변경·기록 문서 9개의 내부 링크·앵커 114개, `git diff --check`를 확인했습니다. backend에서 `DB_USERNAME=app_core_rw ./gradlew test --tests '*ContractTest' --rerun --no-daemon`을 실제 실행해 20 suites / 112 tests, 실패·오류·skip 0을 확인했습니다. test task만 재실행했고 컴파일·리소스 4개 task는 UP-TO-DATE였습니다. 작업 전 해시와 대조해 수정은 이 기록을 포함한 기존 지침·기록 9개에 한정되고 새 파일은 없으며, 다른 사용자 변경·스테이징·HEAD는 보존했습니다.

남은 한계: 프론트 ESLint·CI 테스트는 존재하지만 백엔드의 코드 형식·계층 경계·선정 패턴 전반을 자동 강제하는 설정은 확인되지 않았습니다. 문서·스킬 형식과 계약 검사 통과가 실제 모델의 지침 준수나 코드 일관성 전체를 보장하지 않습니다. 새 스킬·앱 코드·CI 설정은 추가/변경하지 않았고 DB·운영·외부 호출·원격 CI와 커밋·푸시도 실행하지 않았습니다.

## 2026-10-22 Git 정리와 리팩토링 기준점 보존

문서 정리 누적분과 실행 검증 도구 보완을 로컬 커밋으로 구분합니다. 수집·정규화 기능 수정, 푸시·PR 생성·병합·원격 브랜치 삭제는 이번 범위가 아닙니다.

- 시작 상태는 `docs/docs-environment-cleanup`, HEAD `b6af3ad7`, 미커밋 158개(수정 110·삭제 39·미추적 9), 스테이징 0개였습니다.
- 이 개발 컴퓨터의 저장소 밖 `/home/minseok/youth-welfare-git-backup.DgFsAZ`에 추적 파일의 binary/full-index patch, 기존 변경·미추적 파일 119개의 압축본, 모든 기존 Git ref를 포함한 bundle을 보존했습니다. 별도 임시 트리에서 patch 적용·역방향 적용 검사와 파일 복원을 확인했습니다. bundle은 기존 브랜치 215개를 포함하며 Git 검증을 통과했습니다.
- 진행 중 원래 작업공간이 `docs/common-development-rules`로 전환되고 CI·AGENTS·작업 지침 편집이 추가됐습니다. 해당 작업을 덮어쓰거나 커밋에 섞지 않고, 시작 백업을 별도 worktree `/home/minseok/youth-welfare-git-cleanup.DgFsAZ`의 원래 정리 브랜치에 복원했습니다. 원래 작업공간은 다른 편집을 보존하므로 clean 상태로 정리한 것이 아닙니다.
- 실행 검증 스크립트 2개·오프라인 실행 테스트·복구 런북·환경 안내는 `915b5961`에 먼저 커밋했습니다. 테스트 안내 문서는 통합 운영 문서의 새 앵커에 의존하므로 후속 문서 정리 묶음에 남겼습니다. 첫 커밋의 index tree를 별도 디렉터리에 내보내 링크와 계약 테스트를 독립 검증했습니다.
- `origin`을 fetch하고 로컬 `main`과 `origin/main`이 같은 `b6af3ad7`임을 확인했습니다. 두 기준 모두에 포함되고 시작 bundle의 tip과 일치하는 옛 로컬 브랜치 148개만 `git branch -d`로 제거했습니다. 작업 중 추가된 브랜치 1개를 포함해 216개에서 68개로 줄었으며, 미병합 62개·기준/보존용/작업 중 브랜치 6개는 유지했습니다. 열린 PR #365와 원격 브랜치는 변경하지 않았습니다.
- 삭제 이름·원래 tip·upstream과 보존 사유는 백업 폴더의 `branch-cleanup-plan.json`, `branch-cleanup-result.json`에 기록했습니다. 제거한 로컬 브랜치는 bundle의 원래 ref에서 복원할 수 있습니다. 자동 stash·강제 삭제·이력 초기화는 사용하지 않았습니다.

검증:

- 격리 worktree의 `backend/`에서 Java 17과 `DB_USERNAME=app_core_rw RUN_SMTP_SMOKE=false`로 `./gradlew test --rerun-tasks --no-daemon` 실제 재실행: 289 suites / 1,268 tests, 실패·오류 0, 실제 SMTP 검사 1개 skip.
- 첫 커밋의 독립 snapshot에서 `./gradlew test --tests '*ContractTest' --rerun --no-daemon`: 20 suites / 112 tests, 실패·오류·skip 0. 전체 단위 검사와 구분하며 실제 앱/DB smoke를 실행한 것은 아닙니다.
- 최종 작업 기록을 반영한 격리 worktree에서도 같은 문서 계약 명령을 재실행해 20 suites / 112 tests, 실패·오류·skip 0을 확인했습니다.
- 격리 worktree의 `frontend/`에서 `npm run test:unit`: 22 suites / 54 tests 통과. 기존 의존성 디렉터리를 추적 제외된 symlink로 참조했으며 패키지·lock 파일은 바꾸지 않았습니다.
- 내부 Markdown 링크·앵커와 코드의 문서 경로 정적 검사, 변경 파일의 고신뢰 비밀값 형태 검사, Bash 스크립트 4개의 `bash -n`, `git diff --check` 통과. 외부 링크 가용성이나 비밀값 검출의 완전성을 보장하는 검사는 아닙니다.
- 누적 기록 6개는 보관 표지와 Markdown 링크 목적지 외 본문이 기준 커밋과 동일하며, SRS FR/NFR 요구 행 108개도 동일함을 대조했습니다.
- 스테이징 기본 whitespace 검사는 새 보관 파일에 복사된 원문의 Markdown 하드 개행 3곳을 경고했습니다. 원문은 고치지 않았고, copy/rewrite를 인식하는 `git diff --cached -B -M --check`는 통과했습니다. Git 설정이나 검사 기준 파일을 변경하지 않았습니다.

통합 fixture·브라우저 실행·운영 smoke·DB/Redis/AWS 변경·실제 수집/AI/메일/푸시·원격 CI는 실행하지 않았습니다. 작업 브랜치를 `main`에 병합하거나 수집 리팩토링을 시작한 결과가 아닙니다.

## 2026-10-22 후속 Git 정리: 프론트 단위 테스트 CI 분리

기존 `docs/common-development-rules` 작업공간의 변경을 별도 worktree `docs/docs-environment-cleanup`의 문서 정리 커밋 위에서 분리합니다. 첫 커밋에는 `.github/workflows/ci.yml`의 프론트 단위 테스트 단계와 이 기록만 포함합니다. 작업 지침·스킬은 후속 커밋으로 남기며 원래 작업공간의 파일·브랜치·스테이징은 변경하지 않습니다.

추가된 단계는 기존 `frontend-browser-smoke` job에서 `npm ci` 직후 `npm run test:unit`을 실행합니다. 다른 job·권한·트리거·Node 버전·브라우저/DB 실행 설정은 변경하지 않습니다. 기존 미커밋 파일 9개의 별도 백업과 추적 변경 patch는 `/home/minseok/youth-welfare-git-next.Yiw33p`에 보존했습니다.

검증은 별도 worktree에서 수행했습니다. `frontend/`의 `npm run test:unit`은 22 suites / 54 tests, 실패·skip 0입니다. 로컬 실행은 Node 24.11.1 / npm 11.6.2이며, workflow의 Node 22 실행 결과나 GitHub CI 통과로 해석하지 않습니다. PyYAML로 전후 설정을 파싱해 `npm ci` 뒤·DB 기동 전의 단일 단계만 추가됐음을 비교했습니다. `git diff --check`를 통과했습니다. 앱·DB·브라우저·외부 API는 실행하지 않았습니다.

## 2026-10-22 후속 Git 정리: 작업 지침·스킬 통합

선행 CI 커밋 `ad9d0533`과 분리해 작업 지침과 기존 `grilling` 스킬을 8개 파일의 커밋으로 정리합니다. 원래 작업공간에서 작성된 지침·스킬 7개 파일의 내용은 백업본 그대로 보존하고, `phase-plan.md`에는 두 작업공간의 기록을 합쳤습니다. 이전 Git 정리 기록·동시 작업 대응 기록·작업 지침 보완 기록·스킬 검토 기록을 삭제하거나 완료 의미를 바꾸지 않았습니다. 추가 스킬 설치나 애플리케이션 리팩토링은 수행하지 않습니다.

검증:

- 별도 worktree의 `backend/`에서 Java 17로 `DB_USERNAME=app_core_rw RUN_SMTP_SMOKE=false ./gradlew test --tests '*ContractTest' --rerun --no-daemon` 실행: 20 suites / 112 tests, 실패·오류·skip 0. test task를 실제 재실행했으며 전체 백엔드·통합 테스트 결과로 확대하지 않습니다.
- Markdown 260개에서 링크 2,406개를 파싱하고 로컬 파일·앵커와 코드 내 문서 참조 63개를 확인해 누락 0건입니다. 외부 URL의 도달성은 검사하지 않았습니다. 변경 파일의 제한된 고확도 비밀 패턴 검사에서 발견 0건이며 종합 보안 감사가 아닙니다.
- `quick_validate.py .agents/skills/grilling`과 `git diff --check` 통과. 스킬 형식 검사이지 행동 정확성 보장이 아닙니다.
- 백업 파일 9개와 원래 작업공간의 현재 내용을 바이트 단위로 대조하고 전체 추적 변경 patch의 SHA-256도 비교했습니다. 원래 HEAD·브랜치·스테이징이 그대로이며, 이 worktree에 합친 기록 외 8개 파일(CI 포함)은 백업본과 일치합니다.

이 작업은 로컬 커밋 정리입니다. `grilling` 지침에 따라 원격 반영 범위를 질문했으며, 무응답을 푸시·PR 생성·병합 승인으로 간주하지 않습니다. 현재 열린 PR #365(`refactor/admin-dashboard-sections` → `main`)는 별도 작업이므로 변경하지 않았습니다. 원래 작업공간의 중복 미커밋 변경을 지워 깨끗하게 만들지 않았으며, 후속 통합 시 이 기준점과 추가 편집을 다시 대조해야 합니다.

## 2026-10-22 정리 브랜치 푸시와 PR 생성

사용자가 푸시·PR 생성을 승인한 뒤 `origin`을 prune 없이 fetch했습니다. 기준 `origin/main`은 `b6af3ad7`이고 준비한 브랜치는 뒤처짐 없이 4개 커밋 앞선 상태였습니다. 정리용 worktree가 깨끗하고 `git diff -B -M --check origin/main...HEAD`와 문서 참조 검사가 통과함을 재확인했습니다. 애플리케이션·테스트 코드가 바뀌지 않아 앞선 전체 단위 테스트를 새 실행 결과처럼 다시 기록하지 않습니다.

`docs/docs-environment-cleanup`의 `c5727f6d`까지 4개 커밋을 `origin`의 같은 이름 브랜치에 일반 푸시하고 upstream을 설정했습니다. 원격 SHA가 로컬과 일치함을 확인한 뒤 `main` 대상 [PR #366](https://github.com/minseok02/youth-welfare/pull/366)을 생성했습니다. PR 본문에는 문서 중심 163개 파일로 범위가 커진 이유, 실행 계약·CI 변경, 검증 snapshot과 미실행 범위를 구분해 적었습니다. 이 절을 반영하는 후속 커밋은 작업 기록만 추가합니다.

PR 생성 직후 해당 SHA의 [CI 실행](https://github.com/minseok02/youth-welfare/actions/runs/39785991979)에서 security-scan·backend-unit·frontend-browser-smoke가 실행 중이고 codeql-analysis는 skip임을 확인했습니다. 이는 그 시점의 상태이며 최종 성공이 아닙니다. 후속 기록 커밋의 CI도 별도 실행으로 확인해야 합니다.

기록 추가 뒤 Java 17로 계약 테스트를 `--tests '*ContractTest' --rerun --no-daemon` 실행해 20 suites / 112 tests, 실패·오류·skip 0을 확인했습니다. 문서 링크 2,408개와 코드 내 문서 참조 63개에서 로컬 파일·앵커 누락 0건이며 `git diff --check`도 통과했습니다. 이 로컬 검증은 원격 CI 최종 결과가 아닙니다.

병합·자동 병합·PR #365 수정·원격 브랜치 삭제·강제 푸시는 하지 않았습니다. 로컬 백업·복구용 bundle도 업로드하지 않았습니다. 원래 작업공간에 이후 추가된 별도 스킬·지침 편집은 그대로 보존하고 이번 PR에 자동 포함하지 않았습니다. 원래 작업공간을 clean 상태로 만들거나 운영 서버·DB·수집·AI·발송을 변경한 결과가 아닙니다.

## 2026-10-22 원래 작업공간과 정리 브랜치 통합

사용자가 원래 작업공간 정리를 승인하면서 다른 곳의 추가 변경도 다시 확인하도록 요청했습니다. 이전의 6개 차이를 그대로 사용하지 않고 현재 165개 경로를 모두 대조해 PR과 같은 내용 154개·다른 내용 또는 추가 파일 11개를 확인했습니다. 추가된 변경은 AGENTS.md·CLAUDE.md의 계약 대표 위치, 작업/Git/테스트 안내와 문서 지도, 스킬 적용 범위·설명 보완까지 포함합니다. 애플리케이션·패키지·잠금 파일·운영 설정은 이번 추가 변경에 포함되지 않습니다.

- 저장소 밖 `/home/minseok/youth-welfare-workspace-sync.Owo3i0`에 현재 수정·미추적 파일 126개의 압축본, 추적 변경·삭제 39건을 포함한 patch, 원래 index와 staging, 모든 현재 ref의 Git bundle을 보존했습니다. 압축본 126개를 실제 파일과 대조하고 bundle·SHA-256 검증을 통과했습니다. 추적·미추적 경로 1,732개의 내용과 권한도 기록했습니다. ignored env·의존성·운영 산출물은 백업·일괄 대조 대상에서 제외했고 설정·내용을 변경하지 않았습니다.
- 원래 HEAD `b6af3ad7`와 `docs/common-development-rules` ref를 보존했습니다. 원래 작업공간을 잠시 detached HEAD로 두고 `reset --mixed 795d4f11`로 HEAD/index의 기준만 앞으로 옮겼습니다. 전후 전체 1,732개 경로의 내용·권한이 같고 기존 staging이 비어 있음을 확인했습니다. `reset --hard`, 파일 변경 취소, 자동 stash는 사용하지 않았습니다.
- 기존 정리용 worktree가 깨끗함을 확인해 같은 커밋의 detached HEAD로 전환하고, 원래 경로 `/home/minseok/youth-welfare`에서 upstream이 설정된 `docs/docs-environment-cleanup` 브랜치를 사용하도록 옮겼습니다. 작업공간을 삭제하거나 main·기존 별도 PR을 병합하지 않았습니다.
- 최신 원래 파일의 지침·스킬·문서 편집은 유지했습니다. `phase-plan.md`는 이전 공통 snapshot과 대조해 양쪽 공통 절에 미검토 동시 수정이 없음을 확인하고, PR에만 있던 Git 정리·CI 분리·지침 통합·푸시 기록 4개 절을 추가했습니다. troubleshooting의 PR 전용 기록도 유지하고 이번 원래 작업공간 정합화 원칙을 짧게 추가했습니다. 새 systematic-debugging 지침과 MIT 라이선스도 보존합니다.

기존 PR `795d4f11`의 [CI 실행](https://github.com/minseok02/youth-welfare/actions/runs/39786234362)은 backend-unit·frontend 단위 테스트 성공, security-scan의 OSV dependency scan과 frontend-browser-smoke의 npm audit 실패입니다. codeql-analysis와 audit 이후 브라우저 실행은 skip입니다. `systematic-debugging` 지침에 따라 실패 단계·로그를 확인했고 OSV는 당시 프론트 잠금 파일에서 12개 패키지·50개 advisory(High 25개 포함)를 보고했습니다. 두 패키지 파일이 `main` 기준과 같음을 확인했으며, 이번 통합에 자동 의존성 업데이트·감사 기준 완화·실패 무시는 포함하지 않습니다. 영향 평가와 의존성 보완은 별도 후속 과제입니다. 기존 실행 결과와 이 통합 뒤의 새 CI는 구분합니다.

검증:

- 원래 경로의 `backend/`에서 Java 17로 `DB_USERNAME=app_core_rw RUN_SMTP_SMOKE=false ./gradlew test --tests '*ContractTest' --rerun --no-daemon`을 실제 실행했습니다. 20 suites / 112 tests, 실패·오류·skip 0이며 test task를 재실행하고 나머지 4개 컴파일·리소스 task는 UP-TO-DATE였습니다. 전체 백엔드·통합·브라우저 검사는 이번에 다시 실행한 결과가 아닙니다.
- Markdown 261개의 링크 2,411개를 파싱하고 로컬 파일·앵커와 코드 내 문서 참조 63개를 확인해 누락 0건입니다. 두 스킬의 형식 검사와 `git diff --check`도 통과했습니다. 외부 URL 가용성·스킬 행동 전체·종합 보안 검증은 아닙니다.
- 병합한 기록 파일 2개 외 1,730개 경로는 작업 전 내용·존재 여부·권한과 동일합니다. 원래 phase-plan의 15개 절·troubleshooting의 4개 절을 그대로 보존했고 PR 전용 절 4개·1개도 유지했습니다. 누적 이력 6개와 SRS 요구 행 108개 보존 검사도 통과했습니다.
- 이전 PR 기준과 비교한 추가 변경은 지침·스킬·문서 11개 파일뿐입니다. 이미 PR에 같은 내용으로 반영된 154개 경로를 재커밋하지 않습니다. 앱·프론트·검증 스크립트·CI 설정·패키지·잠금 파일은 기준점 이후 변경하지 않았습니다.

이번 추가분은 원래 경로의 정리 브랜치에서 커밋해 기존 PR #366에 일반 푸시하는 범위입니다. 병합·자동 병합·원격 브랜치 삭제·운영 DB/서버·실제 수집/AI/발송·브라우저 검증은 수행하지 않습니다. 기존 CI의 의존성 감사 실패는 작업공간 정리 완료와 구분해 남깁니다.

## 2026-10-22 프론트 의존성 취약점 보완

사용자가 의존성 보완과 Git 정리 PR 마무리(1·2단계)를 승인했습니다. `main` 기준 별도 브랜치 `fix/frontend-dependency-audit`에서 프론트 패키지와 잠금 파일만 보완하고 이 기록을 추가합니다. PR #366의 문서 정리와 PR #365의 기능 변경, 수집 리팩토링·운영 변경은 섞지 않습니다.

- `systematic-debugging` 기준으로 기존 CI의 OSV/npm audit 실패와 현재 감사 결과를 대조했습니다. 보완 전 npm 감사는 영향받는 의존성 경로 13개(High 10·Moderate 3)를 보고했습니다. 기존 OSV의 advisory 50개와는 집계 단위가 다릅니다.
- 직접 의존성의 최소 허용 버전을 Axios `^1.20.0`, React Router DOM `^7.18.2`, Vite `^8.1.6`으로 올리고, 감사에 걸린 간접 의존성과 필요한 하위 트리만 같은 메이저 범위에서 갱신했습니다. 잠금된 실제 버전은 Axios `1.20.0`, React Router DOM/Router `7.18.4`, Vite `8.3.5`입니다. React·MUI·React Query·ESLint·Playwright 등 나머지 직접 의존성의 잠금 버전은 유지했습니다.
- [Axios 수정 릴리스](https://github.com/axios/axios/releases/tag/v1.20.0), [React Router 릴리스](https://github.com/remix-run/react-router/releases/tag/react-router%407.18.4), [Vite 보안 안내](https://github.com/vitejs/vite/security/advisories/GHSA-rq7h-c2jc-7f22)와 npm registry의 버전·Node 요구 조건을 확인했습니다. 기존 Node 22 CI·로컬 Node 24 범위에 맞고 메이저 전환·강제 감사 수정·검사 기준 완화는 하지 않았습니다.
- 원래 작업공간과 공유하던 작업용 node_modules symlink만 확인 후 해제하고 별도 의존성을 설치했습니다. 링크 대상의 원래 의존성 디렉터리는 변경하지 않았습니다. 초기 패키지·작업 기록과 Git 이력 백업, 감사 전후 JSON·잠금 버전 대조는 저장소 밖 `/home/minseok/youth-welfare-dependency-audit.eOX4iO`에 보존합니다.

로컬 검증: Node 24.11.1 / npm 11.6.2에서 독립 `npm ci --ignore-scripts --no-audit --no-fund` 설치 후 단위 테스트 22 suites / 54 tests(실패·skip 0), lint·production build, `npm ls --depth=0`, `npm audit --audit-level=high`를 통과했습니다. 감사 보고는 모든 심각도에서 0건입니다. Java 17의 `DB_USERNAME=app_core_rw RUN_SMTP_SMOKE=false ./gradlew test --tests '*ContractTest' --rerun --no-daemon`은 이 main 기준에서 19 suites / 98 tests, 실패·오류·skip 0입니다. 아직 문서 정리 PR의 추가 계약 테스트 14개를 포함한 기준이 아니므로 그쪽 112개 결과와 구분합니다. 문서 링크·앵커·코드 참조와 `git diff --check`도 통과했습니다. 로컬 Docker가 WSL에서 연결되지 않아 앱·DB·브라우저 연동을 로컬에서 실행하지 않았으며, Node 22와 DB·브라우저 연동의 원격 CI 결과는 별도로 확인합니다.

원격 CI 통과와 변경 검토를 확인한 뒤 사용자 승인 범위에서 의존성 PR을 병합하고, 문서 정리 PR #366에 새 main 기준을 반영해 다시 검증합니다. 운영 배포·데이터·실제 외부 API/AI/발송·기존 PR #365는 변경하지 않습니다.

## 2026-10-22 의존성 보완 병합과 문서 정리 재검증

사용자가 의존성 보완과 문서 정리 PR 마무리(1·2단계)를 승인했습니다. [PR #367](https://github.com/minseok02/youth-welfare/pull/367)의 검토한 커밋 `052e9753`에서 활성 CI 3개(backend-unit·frontend-browser-smoke·security-scan)가 모두 성공한 뒤, SHA를 지정해 일반 merge로 병합했습니다. 병합 커밋은 `d04e0402`이며 [CI 실행](https://github.com/minseok02/youth-welfare/actions/runs/39795247207)에서 CodeQL은 설정에 따라 skip입니다. 우회 병합·감사 기준 완화·브랜치 삭제는 하지 않았습니다.

- 별도 Node 22.23.4에서도 프론트 단위 테스트 22 suites / 54 tests, lint·build를 통과했습니다. 시스템 Node를 바꾸지 않았으며 기존 Node 24 검증과 별도로 확인했습니다.
- 공식 OSV batch API에 수정 전후 잠금 파일의 정확한 패키지·버전을 질의했습니다. 수정 전 12개 영향 패키지·50개 advisory 항목이 재현됐고 수정 후는 0건이며, npm 감사도 모든 심각도 0건입니다. 감사 결과와 패키지 대조는 저장소 밖 기존 의존성 백업에 보존합니다.
- 새 main을 문서 정리 브랜치에 병합하면서 `phase-plan.md`의 기록 충돌만 해소했습니다. 기존 정리 브랜치의 전체 본문을 그대로 보존하고 위 의존성 보완 절을 추가했습니다. 들어온 과거 구현 기록은 정리 전 기준과 동일함을 확인했으며 기존 history 보관본을 유지합니다. 프론트 패키지 두 파일은 새 main과 같게 유지합니다.

통합 로컬 검증: 원래 작업공간에 독립 `npm ci --ignore-scripts --no-audit --no-fund`로 수정된 의존성을 설치하고 Node 24.11.1에서 프론트 단위 테스트 22 suites / 54 tests, lint·build·`npm ls --depth=0`와 npm 감사 0건을 확인했습니다. Java 17의 계약 테스트는 test task를 실제 재실행해 20 suites / 112 tests, 실패·오류·skip 0입니다. Markdown 261개·링크 2,416개·코드 참조 63개에서 누락 0건, 보관 기록 6개·SRS 요구 행 108개 보존과 diff 검사를 통과했습니다. 이 결과는 전체 백엔드·로컬 DB·브라우저 연동 재실행을 뜻하지 않습니다.

통합 커밋을 일반 푸시한 뒤 PR #366의 활성 CI 전부 성공과 변경 검토를 확인하고, 검토한 HEAD SHA를 지정해 병합합니다. 새 원격 CI의 실제 결과와 최종 병합 SHA는 PR 본문에 남깁니다. PR #365·운영 배포·DB·실제 수집/AI/발송은 이번 승인 범위에서 제외합니다.

## 2026-10-23 수집 결과 판정 보완

사용자가 첫 수집 코드 개선을 승인했습니다. 새 main `11d2097d`에서 `fix/backend-collect-result-status` 브랜치를 만들었으며 이번 범위는 `collect/all`의 항목 부분 실패와 목록 비교 오류의 집계·표시입니다. `systematic-debugging` 절차로 코드와 기존 테스트의 기대값을 대조하고, 수정 전 최소 검사 5개 중 3개 실패(목록 부분 실패·snapshot 오류·관리자 부분 실패 응답)를 실제 재현했습니다.

### 의도와 완료 조건 — 구현 후 보완

아래 요약은 코드 커밋 `4ca09cef` 이후 사용자 요청으로 보강한 기록이며 사전에 작성한 계획이 아닙니다. 기존 재현·구현·검증 결과와 미해결 범위는 유지합니다.

- 문제/근거: 항목 실패 수가 있어도 정상 반환을 성공으로 취급했고, 목록 비교 예외가 최종 결과에서 빠졌습니다. 위 수정 전 재현 결과와 기존 테스트의 잘못된 기대값이 근거입니다.
- 의도/기대 동작: 운영자가 실패를 빠뜨리지 않고 목록 저장·후속 수집·목록 비교 중 어느 결과를 재확인해야 하는지 구분할 수 있게 합니다. 데이터 의미의 정확성이나 실제 DB 복구까지 보장하는 작업은 아닙니다.
- 범위/비목표/보존: `collect/all` 결과 모델·집계·응답·완료 로그와 회귀 테스트만 보완합니다. 기존 항목 건수, HTTP/envelope, 실행 순서·예산·권한 경계와 예외 원문 비노출을 보존하며 실제 외부 호출·DB/schema·운영 변경은 제외합니다.
- 선택/이유: 항목 실패를 전체 flag에만 더하면 비교 실패의 위치를 구분할 수 없고, 비교 오류를 source 실패로 덮으면 성공한 목록 저장과 집계의 의미가 달라집니다. 따라서 비교 결과를 별도로 반환하고 정상 처리된 요청의 HTTP 200은 유지했습니다. 대신 소비자는 응답 envelope와 업무 결과 status를 구분하고 추가 집계 필드를 읽어야 합니다.
- 완료 조건: `48/50`의 저장·실패 건수를 보존한 부분 실패, 전건/예외 실패, source 집계 합계와 중복 lane 보존, 목록 저장 성공과 비교 실패의 분리, 실패 후 후속 실행을 검사합니다. 정상 skip·필터·baseline·임계치 미달·count-drop guard를 오류로 바꾸지 않고 기존 호출 순서·예산·HTTP 계약과 오류 원문 비노출도 회귀로 확인해야 합니다.
- 결과/남은 일: 로컬 코드 커밋과 검증은 완료했으며 실제 명령·결과는 아래에 구분해 기록합니다. 원격 CI·병합·운영 반영과 실제 수집/DB 복구는 완료로 취급하지 않습니다. 현재 응답의 대표 설명은 [수집 결과 계약](collect/collect-current-state.md#전체-수집-결과-계약)입니다.

### 구현과 검증

- source 결과에 항목 실패가 있으면 완전 성공으로 표시하지 않습니다. 부분/전건 실패를 나누고 기존 요청·저장·skip·filtered·failed 건수를 유지합니다. source 집계는 성공·부분 실패·실패의 합이 실행 entry 수와 같으며 같은 lane의 forced/rotation 결과는 합치지 않습니다.
- 목록 비교 결과를 별도 목록으로 반환합니다. snapshot 저장/변경 정책 예외는 최종 실패 표시에 포함하지만 성공한 목록 저장과 건수는 유지합니다. source 부분/전건 실패로 비교하지 않은 경우, baseline·임계치 미달·후보 없음·count-drop guard를 실제 비교 오류와 구분합니다.
- 관리자 API의 기존 필드·HTTP 200/envelope는 유지하고 status·부분 실패 실행 수·비교 실패 수/결과를 추가했습니다. source별 success는 이제 항목 실패가 없어야 true이며, 완료 로그도 전체 status와 집계값을 남깁니다. 비밀·PII·SQL/URL을 담을 수 있는 예외 원문은 응답에 넣지 않습니다.
- 실제 수집·AI·메일/웹푸시·DB/schema·운영 설정·호출 예산은 변경하거나 실행하지 않습니다. raw/replay 기반·소스 확장·정규화와 개별/async API 모델은 후속 범위입니다. 이 수정으로 과거 로컬의 누락 테이블·지원조건 빈 응답·embedding 미갱신이 복구됐다고 처리하지 않습니다.

검증: 원래 작업공간의 Java 17에서 `DB_USERNAME=app_core_rw RUN_SMTP_SMOKE=false ./gradlew test --tests '*CollectBatchRunResultTest' --tests '*CollectBatchServiceTest' --tests '*AdminSecurityWebMvcTest.collectAllEndpoint*' --no-daemon`으로 3 suites / 28 tests, 실패·오류·skip 0을 확인했습니다. 초기 실패 3개를 해소했고 전건 실패·skip/필터·비교 오류/생략·후속 실행·forced/rotation 결과 보존을 검사합니다. 이후 `DB_USERNAME=app_core_rw RUN_SMTP_SMOKE=false ./gradlew test --no-daemon`으로 전체 기본 테스트를 실제 실행해 290 suites / 1,292 tests, 실패·오류 0, opt-in SMTP 검사 1개 skip을 확인했습니다. integration 제외 기준을 유지하며 새 회귀는 기존 전체 기본 검사보다 24개 증가했습니다.

전체 검사 뒤 비-list 표본의 source 이름을 정합화하고 `DB_USERNAME=app_core_rw RUN_SMTP_SMOKE=false ./gradlew test --tests '*CollectBatchRunResultTest' --tests '*ContractTest' --rerun --no-daemon`을 실제 실행했습니다. 모델 13개와 문서 계약 112개를 합친 21 suites / 125 tests, 실패·오류·skip 0이며 전체 검사의 부분집합이지 추가 독립 총계가 아닙니다. Markdown 261개·링크 2,420개·코드 문서 참조 63개에서 누락 0건, `bash deploy/smoke/verify-collect-detail-execution-contract.sh`와 `git diff --check`도 통과했습니다. 외부 URL 가용성·프론트 회귀를 별도 실행한 결과는 아닙니다.

실제 외부 API/DB/브라우저/운영 검증과 새 브랜치의 원격 CI는 실행하지 않았습니다. DB 복구·실수집 재검증은 별도이며, stream 중간 예외 때 앞선 forced detail 처리량을 복구해 집계하는 개선과 개별/async 결과 모델도 이번 범위에 포함하지 않습니다. 로컬 변경을 검증·커밋하는 범위로 마무리하며 푸시·PR 생성·병합은 수행하지 않습니다.

### 문서 보완과 로컬 수정분 마무리

사용자가 작업 의도·완료 조건의 기록 보완과 현재 수정분 마무리까지 승인했습니다. 목적은 다음 작업에서도 의도·보존 계약·검증 한계를 추적하는 것이며 `work-guide.md`의 간단한 작성 틀, 위 작업 요약, `current-state.md`의 현재 단계만 보완했습니다. 기존 기능 계약·검증 이력은 재작성하지 않고 새 문서·애플리케이션 변경도 추가하지 않았습니다. 완료 조건은 설명과 코드/회귀 사례 대조, 문서 계약 실제 재실행, 링크/앵커 누락 0과 변경 범위 검토입니다. 푸시·PR·병합·snapshot DB 진단은 이번 승인 범위가 아닙니다. 코드 커밋 `4ca09cef`는 수정하지 않고 문서 보완을 후속 로컬 커밋으로 분리합니다.

검증 결과: 원래 작업공간의 Java 17에서 `DB_USERNAME=app_core_rw RUN_SMTP_SMOKE=false ./gradlew test --tests '*CollectBatchRunResultTest' --tests '*CollectBatchServiceTest' --tests '*AdminSecurityWebMvcTest.collectAllEndpoint*' --tests '*ContractTest' --rerun --no-daemon`을 실제 실행해 23 suites / 140 tests(수집 모델·배치·관리자 API 28개 + 문서 계약 112개), 실패·오류·skip 0을 확인했습니다. test task는 실제 실행됐고 컴파일·리소스 4개 task는 UP-TO-DATE입니다. Markdown 261개·링크 2,423개·코드 참조 63개의 누락 0건과 `git diff --check`를 확인했습니다. 이전 구현·검증 본문과 이후 관측 이력이 그대로이며 `4ca09cef` 이후 코드·테스트·설정·의존성 파일 차이도 없습니다. 앞서 실행한 전체 1,292개를 이번 문서 보완에서 다시 실행한 것은 아니며, 실제 DB/외부 API/브라우저/운영과 원격 CI도 검증 범위에 포함하지 않습니다.

## 2026-10-23 snapshot 패치 준비 — 작업 A

- 문제/근거: `7880dc1a` 기준 조사에서 통합 schema·snapshot migration에는 정의가 있으나 기본 runtime patch 경로에는 생성문이 없었습니다. 2026-10-23 02:26 KST 로컬 Docker DB의 조회 전용 트랜잭션에서 snapshot 테이블 2개와 관련 시퀀스 누락을 확인했습니다. 당시 관측이며 이번 구현에서 실제 DB를 다시 조회하거나 변경하지 않습니다.
- 의도/범위: 기존 PostgreSQL 데이터와 신규 DB 초기화 경로를 유지하면서 snapshot 전용 생성·최소권한 패치와 좁은 적용/검증 경로를 준비합니다. 일반 수집·재처리 잠금·mapper·집계·Java/FastAPI 변경은 제외하며 다른 worktree의 작업 B 파일은 수정하지 않습니다.
- 선택/이유: 통합 schema 전체 재적용이나 기존 patch 전체 재실행은 관련 없는 변경을 포함합니다. snapshot만 대상으로 삼고, 생성과 앱/조회 계정 권한을 함께 검증하는 방식으로 제한합니다. `IF NOT EXISTS`만으로 기존의 잘못된 구조를 복구했다고 간주하지 않습니다.
- 완료 조건: 기존 정의·repository 계약과 일치하는 table/PK/FK/index/sequence, 필요한 앱 SELECT/INSERT·시퀀스 권한과 조회 SELECT를 확인합니다. 명시적 실행 범위·반복 실행 안전성·오류 중단·실행 전제 및 문서 계약을 회귀 검사로 검증합니다. 실제 PostgreSQL 실행을 하지 않은 범위는 미검증으로 남깁니다.
- 구현: snapshot runtime patch와 고정 로컬 socket/guard·Compose db 라벨을 확인하는 전용 helper를 추가했습니다. helper 기본은 조회 계정의 `--check`, `--apply`는 별도 대상/백업 확인 플래그와 기존 migration 계정을 요구하며 SQL 하나만 전달합니다. SQL은 요구 구조와 실효 권한을 검사하고 적용 모드에서만 생성·최소 grant를 한 트랜잭션으로 수행하도록 구성했습니다. 기존 통합 schema·migration·전체 patch 실행기와 production Java는 변경하지 않았습니다.
- 재현/검증: 패치 추가 전 새 정적 계약 3건이 파일 누락으로 모두 실패(RED)했고 추가 후 해결했습니다. Java 17에서 `DB_USERNAME=app_core_rw RUN_SMTP_SMOKE=false ./gradlew test --tests '*CollectSnapshotPatchContractTest' --tests '*PostgresRuntimeContractTest' --tests '*PostgresRuntimeScriptContractTest' --tests '*CollectListDiffServiceTest' --rerun --no-daemon`을 실제 실행해 4 suites / 41 tests(신규 계약 15건 포함), 실패·오류·skip 0을 확인했습니다. Bash helper 검사는 임시 트리의 가짜 Docker만 사용하며 기본 읽기 전용 flag·고정 target·적용 전제·잘못된 라벨/인자/계정 거절·psql 오류 전파를 확인합니다. 실제 PostgreSQL parser/DDL/rollback/재실행 검증은 아닙니다.
- 정적 확인: `bash -n deploy/postgres/apply-local-collect-snapshot-patch.sh`, `git diff --check` 통과. Markdown 261개·링크 2,430개·코드 참조 63개에서 누락 0건입니다. PostgreSQL의 쉼표로 구분한 권한 조회는 어느 하나의 권한만 있어도 true이므로 필요한 권한을 개별 검사했습니다. SQL 오류 중단은 `ON_ERROR_STOP`과 실제 SQL 예외를 사용합니다.
- 최종 검증/상태: 같은 Java 17 환경에서 `DB_USERNAME=app_core_rw RUN_SMTP_SMOKE=false ./gradlew test --tests '*ContractTest' --tests '*CollectListDiffServiceTest' --rerun --no-daemon`을 실제 실행해 22 suites / 129 tests(전체 계약 127건 + 목록 비교 2건), 실패·오류·skip 0을 확인했습니다. test는 실제 실행됐고 컴파일/리소스 4개 task는 UP-TO-DATE입니다. 앞선 41건은 이 검사들의 부분집합이며 독립 총계로 더하지 않습니다. 작업 A의 로컬 구현·오프라인 검증 준비 범위는 완료했습니다.
- 남은 적용/제외: 실제 DDL/GRANT/DB 데이터 변경, 신규 DB 초기화·기존 DB integration fixture, 앱/컨테이너 기동, 실제 수집/외부 호출, 커밋·푸시·PR·병합은 수행하지 않았습니다. 별도 적용 전에는 앱/DB 일치·소유/grant 권한·백업/복원·수집 정지/잠금 영향과 SQL/hash를 확인하고, 적용 후 metadata 검사와 최소 저장/비교·재실행 검증을 해야 합니다. 실제 PostgreSQL parser·DDL·rollback·재실행은 미검증이며 성공한 적용의 되돌림을 DROP으로 자동화하지 않습니다. 다른 Codex 작업 B의 구현·검증을 포함한 결과도 아닙니다.

## 2026-10-23 작업 A 독립 커밋 검증

- 의도/범위: 통합 작업 공간을 보존한 채 기준 `7880dc1a`의 A 전용 브랜치에 snapshot 패치·helper·회귀 계약과 A 기록만 반영합니다. B의 application service·잠금 변경·B 기록과 통합 완료 상태는 이 브랜치에 넣지 않습니다. 실제 DB 적용은 별도입니다.
- 완료 조건: A 전용 파일 원본 일치와 B 변경 부재, 전체 기본 단위 검사·문서 계약의 실제 실행, 링크/공백 확인 후 명시적 staging 내용을 검토하고 로컬 커밋합니다. 기존 원본과 B worktree, 데이터·권한·API 계약을 보존하고 푸시·PR·원격 병합·실제 DB/외부 실행은 하지 않습니다.
- 검증 환경: 외부 검증 worktree에서 기존 Temurin Java 17.0.18+8을 사용합니다. Gradle wrapper/caches만 별도 `GRADLE_USER_HOME`에 복사했으며 원본 build나 env 파일을 공유·복사하지 않습니다.
- 독립 전체 검사: 검증 worktree의 `backend/`에서 `DB_USERNAME=app_core_rw RUN_SMTP_SMOKE=false ./gradlew test --offline --rerun --no-daemon`을 실제 실행했습니다. 291 suites / 1,307 tests 중 1,306개 통과, 실패·오류 0, SMTP opt-in 검사 1개 skip이며 5개 task 모두 실행됐습니다. 계약 21 suites / 127건과 snapshot 계약 15건을 포함합니다. B 신규 application service 검사는 없고 관리자 MVC는 기준선 80건·guard는 3건으로 B 변경이 섞이지 않았습니다. XML 보고서는 별도 외부 백업에 보존했습니다.
- 정적/보존 검사: A 전용 4개 파일의 내용·모드 일치, 전체 변경 7개, 공통 문서의 A 절 원문 및 기존 본문 보존, B 원본과 통합 소스의 보존을 확인했습니다. `git diff --check`, helper의 `bash -n`, Markdown 261개·링크 2,432개·코드 참조 63개 검사에서 누락 0건을 확인했습니다. 실제 PostgreSQL 실행·잠금 경합·외부 수집·운영 반영은 검증하지 않았습니다.
- 후속 계약/커밋: 결과 문서 반영 뒤 같은 환경에서 `DB_USERNAME=app_core_rw RUN_SMTP_SMOKE=false ./gradlew test --tests '*ContractTest' --offline --rerun --no-daemon`을 실제 재실행해 21 suites / 127 tests, 실패·오류·skip 0을 확인했습니다. test는 실행됐고 나머지 4개 task는 UP-TO-DATE였습니다. 전체 검사에 포함된 계약의 재검증으로 총계에 더하지 않습니다. 이 결과를 명시적 staging 검토 후 A의 독립 로컬 커밋에 함께 담으며 SHA는 통합 작업 공간의 Git 정리 기록에서 관리합니다. 푸시·PR·병합은 하지 않습니다.

## 2026-10-23 sidecar 재처리 잠금 — 작업 B

- 문제/근거: 관리자 복지로·Gov24 sidecar backfill 두 endpoint가 기존 `CollectExecutionGuard`를 거치지 않아 일반 수집과 동시에 실행될 수 있습니다. 기준 SHA `7880dc1a8aa5320b00c528c7e7f5f340fac8b706`의 깨끗한 조사 브랜치에서 `fix/backend-sidecar-backfill-lock`을 분리했습니다.
- 의도/범위: 전용 application service에서 기존 `collect-global` 잠금을 요청당 한 번 확보하고 모든 내부 backfill 호출이 끝난 뒤 해제합니다. 복지로 all/list/detail, Gov24 list/regions/support/support-conditions/all 및 list missingOnly를 포함합니다.
- 보존/비목표: URL·파라미터·기본값·검증·응답 scope/집계·관리자 인증·rate limit과 기존 lease/heartbeat/cleanup·트랜잭션 구현을 유지합니다. writer 결과 모델·schema preflight·fact 소유권·SUPPORT mapper·raw 보존·DB/SQL·외부 실행·커밋·푸시는 제외합니다.
- 선택/이유: controller의 실행 분기만 application service로 옮겨 기존 guard를 재사용합니다. application service에는 바깥 트랜잭션을 추가하지 않아 기존 backfill 서비스/저장소의 commit 또는 rollback이 guard의 finally 해제보다 먼저 끝나도록 합니다.
- 완료 조건: 최소 회귀 RED 후 GREEN, 모든 scope의 단일 잠금·busy 시 내부 실행 없음과 HTTP 409/COL002·정상/예외 해제·잘못된 scope/missingOnly 조합의 잠금 전 거절·commit/rollback 후 해제 순서를 DB 없는 단위/MVC 검사로 확인합니다. 문서 계약은 `--rerun`으로 실행하고 실제 동시 DB 경합과 구분합니다.
- 구현 결과: 두 endpoint의 실행 분기를 새 `CollectSidecarBackfillApplicationService`로 위임했습니다. 모든 scope는 검증 후 기존 전역 guard를 한 번 사용하고, all의 내부 순서와 합산·기존 파라미터/기본값/응답·관리자 인증/rate limit을 유지합니다. 기존 guard와 정규화 서비스·writer·저장소·SQL은 수정하지 않았습니다.
- 검증 환경: 본인 `backend/`, 기존 Temurin Java 17.0.18+8 설치를 읽어 실행했습니다. 상대 worktree의 Gradle wrapper/caches만 본인 `backend/build/gradle-user-home/`에 복사해 `GRADLE_USER_HOME`으로 사용했습니다. 본인 build/project cache는 디렉터리이며 symlink가 아닙니다. 상대 폴더에서 작업 명령을 실행하거나 파일·전역 설정을 수정하지 않았고 실제 env 파일은 열거나 source/복사하지 않았습니다.
- RED: `DB_USERNAME=app_core_rw RUN_SMTP_SMOKE=false ./gradlew test --tests '*AdminSecurityWebMvcTest.sidecarBackfillRejectsBusyGlobalLock' --no-daemon` — 2 tests / 2 failures, 두 endpoint 모두 기대 HTTP 409 대신 200을 반환했습니다. 구현 뒤 첫 지정 검사에서는 새 테스트의 Gov24 호출 인자 누락으로 compileTestJava가 실패했고 테스트 호출을 바로잡았습니다.
- GREEN: `DB_USERNAME=app_core_rw RUN_SMTP_SMOKE=false ./gradlew test --tests '*CollectSidecarBackfillApplicationServiceTest' --tests '*CollectExecutionGuardTest' --tests '*AdminSecurityWebMvcTest' --no-daemon` — 실제 test 실행, 3 suites / 141 tests(29 + 4 + 108), 실패·오류·skip 0. RED 2개를 포함해 전체 scope의 단일 잠금/점유 거절, all의 실행 순서·집계, 정상/작업 예외/commit 실패 해제, 잘못된 파라미터의 잠금 전 거절, 기본값·대소문자·alias·인증·rate limit을 확인했습니다. Spring 트랜잭션 프록시와 기록용 transaction manager로 하위 commit/rollback 후 해제 및 바깥 트랜잭션 부재를 검사했으며 실제 DB commit 관측은 아닙니다.
- 문서/마무리 검증: 같은 Java/cache 환경의 본인 backend에서 `DB_USERNAME=app_core_rw RUN_SMTP_SMOKE=false ./gradlew test --tests '*ContractTest' --rerun --no-daemon` — 20 suites / 112 tests, 실패·오류·skip 0. test task는 실제 실행됐고 나머지 4개 task는 UP-TO-DATE였습니다. `git diff --check`, 허용된 변경 9개 파일·빈 staging 검사와 추가 Markdown 링크/앵커 4개 검사를 통과했습니다. 공통 기록 두 파일은 작업 B의 고유 절만 추가했으며, 그 절을 제외한 본문은 기준 SHA와 동일함을 대조했습니다. 테스트 전체·실제 DB 검증의 대체 결과로 취급하지 않습니다.
- 미실행/남은 일: 실제 DB·잠금 경합·lease 만료·운영 권한·외부 수집/replay/AI/발송·통합/브라우저 테스트와 원격 CI는 검증하지 않았습니다. writer 성공 집계·fact 소유권·SUPPORT 대상/해석·raw 보존 문제는 별도 범위입니다. DB 적용·staging·커밋·푸시·PR 등 원격 작업을 하지 않고 허용된 로컬 변경만 남깁니다. 주 담당 snapshot 작업과 통합 시 이 작업 B 절 및 troubleshooting의 작업 B 절을 함께 보존해야 합니다.

## 2026-10-23 작업 B 독립 커밋 검증

- 의도/범위: 통합 작업 공간과 원래 B worktree를 보존한 채 기준 `7880dc1a`의 별도 B 준비 브랜치에 sidecar 재처리 전역 잠금·회귀 테스트·B 기록만 반영합니다. A의 snapshot patch/helper/계약 테스트·A 기록과 통합 완료 상태는 이 브랜치에 넣지 않습니다.
- 완료 조건: B 전용 파일 원본 일치와 A 변경 부재, 전체 기본 단위 검사·문서 계약의 실제 실행, 링크/공백 확인 후 명시적 staging 내용을 검토하고 로컬 커밋합니다. API·lease/heartbeat·트랜잭션·데이터/권한 계약을 보존하고 푸시·PR·원격 병합·실제 DB/외부 실행은 하지 않습니다.
- 검증 환경: A의 커밋 뒤 깨끗한 검증 worktree를 기준 커밋에서 새 B 브랜치로 전환해 재사용합니다. 기존 Temurin Java 17.0.18+8과 별도 Gradle wrapper/caches만 사용하고 원본 env·build는 공유하지 않습니다.
- 독립 전체 검사: 검증 worktree의 `backend/`에서 `DB_USERNAME=app_core_rw RUN_SMTP_SMOKE=false ./gradlew test --offline --rerun --no-daemon`을 실제 실행했습니다. 291 suites / 1,350 tests 중 1,349개 통과, 실패·오류 0, SMTP opt-in 검사 1개 skip이며 compileJava·compileTestJava·test가 실행되고 리소스 2개 task는 UP-TO-DATE였습니다. 계약 20 suites / 112건과 application service 29건·guard 4건·관리자 MVC 108건을 포함하며 A의 snapshot 신규 계약은 없습니다. XML 보고서는 별도 외부 백업에 보존했습니다.
- 정적/보존 검사: B 전용 7개 파일의 내용·모드 일치, 전체 변경 10개, 공통 문서의 B 절 원문 및 기존 본문 보존, B 원본과 통합 소스의 보존을 확인했습니다. `git diff --check`, Markdown 261개·링크 2,428개·코드 참조 63개 검사에서 누락 0건을 확인했습니다. 실제 DB 잠금 경합·lease 만료·운영 권한·외부 수집/발송은 검증하지 않았습니다.
- 후속 계약/커밋: 결과 문서 반영 뒤 같은 환경에서 `DB_USERNAME=app_core_rw RUN_SMTP_SMOKE=false ./gradlew test --tests '*ContractTest' --offline --rerun --no-daemon`을 실제 재실행해 20 suites / 112 tests, 실패·오류·skip 0을 확인했습니다. test는 실행됐고 나머지 4개 task는 UP-TO-DATE였습니다. 전체 검사에 포함된 계약의 재검증으로 총계에 더하지 않습니다. 이 결과를 명시적 staging 검토 후 B의 독립 로컬 커밋에 함께 담으며 SHA는 통합 작업 공간의 Git 정리 기록에서 관리합니다. 푸시·PR·병합은 하지 않습니다.

## 2026-10-23 수집 기반 A·B 통합 검증

- 의도/범위: 같은 `7880dc1a`에서 작업한 A(snapshot 전용 패치 준비)와 B(sidecar backfill 전역 잠금)를 한 로컬 작업 공간에 모아 함께 검사합니다. 양쪽의 기존 수정분은 외부 백업으로 보존하고, 공통 기록 두 문서는 각 작업의 절을 유지하며 합칩니다. B worktree의 파일·브랜치·스테이징은 변경하지 않습니다.
- 선택/보존: 작업 간 독립 변경을 먼저 대조하고 B 전용 파일만 가져옵니다. API·잠금/트랜잭션·권한·데이터 보존 계약과 A의 적용 전제는 확대하거나 완화하지 않습니다. 현재 상태 문서는 로컬 준비 완료와 실제 DB 미적용을 구분해 갱신합니다.
- 완료 조건: 가져온 B 전용 파일의 원본 일치, A 전용 파일과 양쪽 기록 보존, 전체 백엔드 기본 단위 테스트의 실제 재실행, 문서 링크·정적 검사 통과를 확인합니다. 통합 fixture·실제 DB 조회/DDL/GRANT·앱/컨테이너 기동·실제 수집/외부 발송·커밋·푸시·PR·원격 병합은 제외합니다.
- 통합/보존 결과: `fix/backend-collect-foundation-integration`에서 B 전용 7개 파일을 가져왔으며 내용·파일 모드가 원본과 일치합니다. A 전용 4개 파일과 B 원본 9개 파일의 내용·모드, B의 브랜치·HEAD·스테이징을 보존했습니다. 공통 두 문서는 A/B 고유 절을 각각 그대로 유지했고 새 작업 절을 제외한 과거 본문도 기준 커밋과 동일합니다. 전체 변경은 현재 상태 문서를 포함해 14개 파일이며 양쪽 index는 비어 있습니다. 통합 전 patch·파일 압축본·해시 목록은 로컬 외부 백업 `../youth-welfare-collect-integration.9olda2/`에 보존했습니다.
- 전체 단위 검증: 원래 작업 공간의 `backend/`, Temurin Java 17.0.18+8에서 `DB_USERNAME=app_core_rw RUN_SMTP_SMOKE=false ./gradlew test --rerun --no-daemon`을 실제 실행했습니다. 292 suites / 1,365 tests 중 1,364개 통과, 실패·오류 0, SMTP opt-in 검사 1개 skip입니다. 계약 검사 21 suites / 127건, 새 snapshot 계약 15건과 sidecar application service 29건·guard 4건·관리자 MVC 108건이 포함되며 이를 전체 건수에 다시 더하지 않습니다. test와 compileTestJava는 실행됐고 나머지 3개 task는 UP-TO-DATE입니다. 연결 중단 전 완료를 확인하지 못한 시도는 이 결과에 포함하지 않고 다시 실행한 성공만 기록합니다.
- 정적 검증: `git diff --check`, 새 파일 5개의 공백 검사, `bash -n deploy/postgres/apply-local-collect-snapshot-patch.sh`를 통과했습니다. Markdown 261개·링크 2,436개·코드 참조 63개에서 파일/앵커 누락 0, 제한된 고신뢰 secret 패턴 탐지 0건입니다. 외부 URL의 응답이나 모든 비밀정보 부재를 검증한 것은 아닙니다. 추가 검사 명령의 `git diff --no-index` 반환값 1은 파일 차이 자체임을 최소 재현으로 확인해 판정을 바로잡았으며 앱 코드·검증 기준을 낮추지 않았습니다.
- 후속 문서 검사: 현재 상태 갱신 뒤 같은 Java 17 환경에서 `DB_USERNAME=app_core_rw RUN_SMTP_SMOKE=false ./gradlew test --tests '*ContractTest' --rerun --no-daemon`을 실제 재실행해 21 suites / 127 tests, 실패·오류·skip 0을 확인했습니다. test는 실제 실행됐고 나머지 4개 task는 UP-TO-DATE였습니다. 전체 1,365건에 포함된 계약들의 재검증이며 별도 총계로 더하지 않습니다. 검사 후 양쪽 파일·공통 기록·빈 index·기준 HEAD의 보존을 다시 확인했습니다.
- 완료/한계: 로컬 파일 통합과 전체 기본 단위 검사를 완료했습니다. 실제 PostgreSQL parser·DDL/GRANT·rollback·재실행, DB 잠금 경합·lease 만료와 수집 복구는 미검증입니다. 통합 fixture·실제 DB 조회/변경·앱/컨테이너 기동·실제 수집/외부 발송·커밋·푸시·PR·원격 병합은 수행하지 않았으며 기존 B 작업 공간은 그대로 남깁니다.

## 2026-10-23 A·B 독립 커밋 정리

- 의도/범위: 사용자가 Git 계획에 따라 로컬 커밋 정리를 승인했습니다. 기준 `7880dc1a`에서 A(snapshot 패치 준비)와 B(sidecar 재처리 잠금)를 독립 브랜치·커밋으로 준비합니다. 코드와 관련 테스트·계약 문서·해당 작업 기록을 함께 담고, 통합 기록의 최종 커밋은 두 기능이 함께 포함되는 시점으로 남깁니다.
- 보존/선택: 현재 통합 작업 공간과 B 원본 worktree는 유지하고 검증용 외부 worktree 하나를 차례로 재사용합니다. A는 기존 `fix/backend-snapshot-schema-patch`, B는 원래 브랜치를 건드리지 않는 `fix/backend-sidecar-backfill-lock-ready`를 사용합니다. 통합 전 14개 파일의 압축본·patch·해시 목록은 `../youth-welfare-commit-prep.qVQQIU/`에 추가 보존했습니다.
- 완료 조건: A에 B 파일/기록이 없고 B에 A 파일/기록이 없는지 확인하고 각각의 독립 상태에서 전체 기본 단위 검사·문서 계약·링크/공백 검사를 실제 실행합니다. 명시한 파일만 staging한 뒤 index를 다시 검토하고 해당 브랜치에 로컬 커밋합니다. 실제 DB 조회/변경·외부 실행·푸시·PR·병합·기존 브랜치/worktree 정리는 제외합니다.
- A 결과: `fix/backend-snapshot-schema-patch`의 로컬 커밋 `2d2ad565`에 A 전용 7개 파일을 담았습니다. 독립 전체 검사 291 suites / 1,307 tests 중 1,306개 통과·SMTP 1개 skip, 실패·오류 0이며 후속 계약 21 suites / 127건도 모두 통과했습니다. 스테이징 내용과 검증 파일의 일치 및 커밋 tree 일치를 확인했고 B 변경은 포함하지 않았습니다.
- B 결과: `fix/backend-sidecar-backfill-lock-ready`의 로컬 커밋 `cc5b5c39`에 B 전용 10개 파일을 담았습니다. 독립 전체 검사 291 suites / 1,350 tests 중 1,349개 통과·SMTP 1개 skip, 실패·오류 0이며 후속 계약 20 suites / 112건도 모두 통과했습니다. 스테이징 내용과 검증 파일의 일치 및 커밋 tree 일치를 확인했고 A 변경은 포함하지 않았습니다.
- 실제 명령/환경: 검증 worktree의 `backend/`에서 Temurin Java 17.0.18+8과 별도 `GRADLE_USER_HOME`으로 각각 `DB_USERNAME=app_core_rw RUN_SMTP_SMOKE=false ./gradlew test --offline --rerun --no-daemon`, 결과 반영 뒤 `DB_USERNAME=app_core_rw RUN_SMTP_SMOKE=false ./gradlew test --tests '*ContractTest' --offline --rerun --no-daemon`을 실행했습니다. A 전체 검사는 5개 task 모두 실행, B는 컴파일 2개와 test 실행·리소스 2개 UP-TO-DATE이며, 후속 계약은 양쪽 모두 test 실행·나머지 4개 UP-TO-DATE입니다. A/B 전체 검사에는 공통 기준선이 겹치고 후속 계약도 부분집합이므로 건수를 합산하지 않습니다. 각 XML 보고서와 요약은 외부 백업에 보존했습니다.
- 보존/전달 상태: 두 커밋의 부모는 모두 `7880dc1a`로 stacked commit이 아닙니다. 각 브랜치의 현재 상태는 해당 기능만 반영하며 공통 문서의 과거 본문·원래 작업 절을 유지했습니다. 통합 작업 공간은 검증·미커밋 보존본으로 14개 변경과 빈 index를 유지하고, 원래 B worktree의 9개 변경·브랜치·HEAD·빈 index도 그대로입니다. 재사용한 검증 worktree는 B 준비 브랜치에서 깨끗한 상태로 남깁니다.
- 후속 문서 검사: 통합 작업 공간의 현재 상태·커밋 결과 기록을 갱신한 뒤 같은 Java 17·별도 Gradle cache 환경에서 `DB_USERNAME=app_core_rw RUN_SMTP_SMOKE=false ./gradlew test --tests '*ContractTest' --offline --rerun --no-daemon`을 실제 재실행해 21 suites / 127 tests, 실패·오류·skip 0을 확인했습니다. test는 실행됐고 나머지 4개 task는 UP-TO-DATE였습니다. Markdown 261개·링크 2,437개·코드 참조 63개 누락 0 및 `git diff --check`도 통과했습니다. 이 검사는 통합 보존본의 최종 문서 검사이며 독립 A/B 검사 건수에 더하지 않습니다. 두 로컬 커밋의 bundle도 기준 커밋을 prerequisite로 보존하고 `git bundle verify`를 통과했습니다.
- 완료/남은 일: A/B의 독립 로컬 커밋 준비를 완료했습니다. 두 기능이 함께 포함되는 시점의 최종 통합 기록 커밋은 아직 만들지 않았습니다. 향후 원격 작업이 승인되면 별도 PR로 전달하고 A main 반영 후 B를 최신 main 기준으로 맞추며 공통 문서는 양쪽 의도를 대조해 정합화합니다. 푸시·PR·main 병합·기존 브랜치/worktree 삭제와 실제 DB 조회/적용·수집·외부 발송은 수행하지 않았습니다.

## 2026-10-23 A·B 푸시와 개별 PR

- 의도/승인 범위: 사용자가 독립 커밋의 다음 전달 단계를 승인했습니다. A `2d2ad565`와 B `cc5b5c39`만 `origin`의 각 전용 브랜치에 일반 푸시하고 main 대상 PR 두 개를 만듭니다. A는 일반 PR, B는 A 병합 뒤 최신 main 기준 공통 문서 정합화가 남아 초안 PR로 전달합니다. CI 상태를 확인하되 푸시 승인을 병합·자동 병합·기존 PR 변경·브랜치 삭제·실제 DB 적용으로 확대하지 않습니다.
- 사전 확인/보존: Git 원격의 main은 `7880dc1a`이고 두 대상 브랜치는 아직 없었습니다. 저장소는 `minseok02/youth-welfare`, 기존 열린 PR #365의 head는 `a07532e3`이며 이번 범위에서 수정하지 않습니다. 두 커밋의 parent/tree·독립 검증 기록과 외부 bundle, 원래 B와 통합 작업 공간의 보존을 확인했습니다. 현재 통합 보존본의 미커밋 변경이나 다른 브랜치는 푸시하지 않습니다.
- 완료 조건: 원격 두 head가 검증한 로컬 SHA와 일치하고, PR base/head·파일 범위가 A 7개/B 10개와 일치하는지 확인합니다. 로컬 검사와 해당 SHA의 원격 CI를 구분하고 실제 CI 상태·미검증 범위·PR 링크를 이 절에 남깁니다. 실패하면 해당 실행의 근거를 확인하며 무조건 재실행하거나 검사를 완화하지 않습니다.
- 전달 결과: 두 전용 브랜치만 `git push --no-follow-tags --set-upstream origin`의 명시적 refspec으로 일반 푸시했습니다. `git ls-remote`에서 원격 A `2d2ad565`·B `cc5b5c39`와 main `7880dc1a`를 대조했습니다. A [PR #370](https://github.com/minseok02/youth-welfare/pull/370)은 일반 PR, B [PR #369](https://github.com/minseok02/youth-welfare/pull/369)는 초안 PR이며 base는 둘 다 main입니다. 각 PR의 head SHA·단일 커밋·변경 파일 목록(A 7개/B 10개)이 로컬 검증 snapshot과 일치합니다. PR 본문에는 독립 로컬 검사·SMTP skip·실제 DB 미검증·향후 공통 문서 정합화를 명시했습니다.
- 원격 CI 결과: 해당 head SHA의 A [CI 39909396943](https://github.com/minseok02/youth-welfare/actions/runs/39909396943)와 B [CI 39909392940](https://github.com/minseok02/youth-welfare/actions/runs/39909392940)가 모두 completed / success입니다. 각각 security-scan·backend-unit·frontend-browser-smoke 3개 활성 job은 success, 조건부 CodeQL job은 skipped입니다. CI의 임시 DB/브라우저 검사를 기존 DB의 snapshot 패치 실행·실제 잠금 경합·운영 반영의 검증으로 취급하지 않습니다.
- 후속 로컬 기록 검사: 현재 상태/PR 링크 반영 뒤, 이어 CI 완료 기록 반영 뒤 Java 17·별도 Gradle cache로 `DB_USERNAME=app_core_rw RUN_SMTP_SMOKE=false ./gradlew test --tests '*ContractTest' --offline --rerun --no-daemon`을 각각 실제 재실행했습니다. 최종 21 suites / 127 tests, 실패·오류·skip 0이며 test 실행·나머지 4개 task UP-TO-DATE입니다. Markdown 261개·링크 2,444개·코드 참조 63개 누락 0, 제한된 고신뢰 secret 패턴 탐지 0건과 `git diff --check`를 확인했습니다. 외부 링크의 가동 상태나 모든 비밀정보 부재를 보장하는 검사는 아닙니다. 검사 후 이 결과 기록만 갱신했으며 커밋 SHA/tree와 원격 head/main, PR의 미병합 상태·기존 #365 head의 보존을 다시 대조했습니다.
- 완료/남은 일: 푸시·별도 PR 생성·원격 CI 확인까지 완료했습니다. 두 PR은 아직 열려 있고 A는 일반 PR, B는 초안이며 관측 시 둘 다 main과 충돌이 없었습니다. A 병합 승인 이후 B의 최신 main 기준 공통 문서 정합화·재검증·최종 통합 기록은 후속 작업입니다. 기존 PR #365, 통합 보존본 14개 변경·빈 index, 원래 B worktree와 깨끗한 검증 worktree를 보존합니다. 병합·자동 병합·배포·기존 브랜치/worktree 삭제·실제 DB 조회/적용·실제 수집/발송은 하지 않았습니다.

## 2026-10-23 snapshot 병합과 B main 정합화

- 의도/승인 범위: 사용자가 다음 단계인 A 검토·병합과 B의 최신 main 정합화를 승인했습니다. A PR #370은 검증한 head와 CI·미해결 리뷰를 재확인해 merge commit 방식으로 병합하고, B PR #369는 공유 이력을 재작성하지 않는 main 병합으로 갱신합니다. A/B 공통 기록·현재 상태·최종 통합 기록을 보존·정합화한 뒤 검증하고 일반 푸시합니다. 새 B head의 CI 확인 후 검토 가능한 PR로 전환하며 B 자체의 main 병합은 이번 범위에 포함하지 않습니다.
- 보존/비목표: 원래 통합 작업 공간 14개 변경과 B 원본 worktree 9개 변경·브랜치·HEAD·빈 index를 유지합니다. 실행 전 파일·patch·커밋 bundle은 별도 외부 백업에 보존했습니다. 다른 PR #365, 기존 브랜치/worktree 삭제, 강제 푸시·공유 이력 재작성, 실제 DB 조회/DDL/GRANT·수집·발송·운영 배포는 제외합니다.
- 완료 조건: A의 병합 결과/원격 main과 기대 head를 대조하고 B가 새 main과 원래 B 커밋을 모두 포함하는지 확인합니다. 공통 문서는 양쪽 의도·과거 관측·독립 검증을 보존하며 A/B 기능 파일은 이미 검증한 내용과 일치해야 합니다. B의 새 main 기준 diff·전체 기본 단위 검사·문서 계약·링크/공백과 새 head의 원격 CI를 확인하고 실제 결과·미검증 범위를 기록합니다.
- A 병합: PR #370의 head `2d2ad565`와 CI 성공·리뷰/스레드 부재·main `7880dc1a`를 재확인했습니다. main은 당시 보호 설정이 없었고 merge commit 허용·병합 후 자동 브랜치 삭제 비활성임을 확인했습니다. 기대 head를 지정한 merge 요청이 성공했으며 원격 main `1b602b29`의 부모는 `7880dc1a`와 `2d2ad565`입니다. A 병합 후 main CI `39913747412`는 실행 중입니다.
- A main 검증: 병합 후 [main CI 39913747412](https://github.com/minseok02/youth-welfare/actions/runs/39913747412)에서 security-scan·backend-unit·frontend-browser-smoke가 success, CodeQL은 skipped임을 확인했습니다. CI의 임시 DB 검증을 기존 DB에 대한 전용 패치 적용으로 해석하지 않습니다.
- B main 정합화: 공유된 B head `cc5b5c39`에서 `origin/main`을 `--no-ff --no-commit`으로 일반 병합했습니다. 예상한 공통 문서 current-state·phase-plan·troubleshooting 3개만 내용 충돌이며 코드 충돌은 없습니다. 원래 A/B 작업 절과 독립 검증 절을 보존해 해결한 후 merge commit `5d595749`를 만들었으며 부모는 `cc5b5c39`와 `1b602b29`입니다. 공유 이력 재작성이나 강제 푸시는 하지 않았습니다.
- B 통합 로컬 검증: 검증 worktree의 Java 17·별도 cache에서 `DB_USERNAME=app_core_rw RUN_SMTP_SMOKE=false ./gradlew test --offline --rerun --no-daemon`을 실제 실행했습니다. 292 suites / 1,365 tests 중 1,364개 통과·SMTP 1개 skip, 실패·오류 0입니다. A/B 기능 파일 11개의 내용·모드 일치와 새 main 기준 diff 10개 파일, 원본 작업 공간·과거 기록 보존을 확인했습니다. merge commit 뒤 최종 통합 기록만 별도 문서 커밋으로 반영합니다.
- 상태: 최종 문서 계약·전달과 새 B head의 원격 CI 확인은 이어서 진행합니다. B 자체의 main 병합·실제 DB 적용·수집/운영 반영은 하지 않았습니다.

## 2026-10-23 B main 병합 정합화

- 의도/범위: A PR #370의 main 병합 `1b602b29`를 공유된 B head `cc5b5c39`에 일반 merge로 반영합니다. 원래 B 이력은 재작성하지 않고 A/B 기능 파일과 작업 기록을 모두 보존합니다. 최종 통합 기록 보완은 이 main 정합화 커밋 뒤 별도 문서 커밋으로 분리합니다.
- 충돌 해결/보존: current-state의 A/B 단독 설명을 실제 A main 반영·B 미병합 상태로 갱신합니다. phase-plan의 원래 A/B 작업 절과 각각의 독립 검증 절, troubleshooting의 양쪽 원인을 모두 유지하며 기존 본문은 기준 커밋과 같습니다. 코드 충돌이나 기능 수정은 없습니다.
- 완료 조건: A/B 전용 파일의 원본 일치, 새 main 기준 B diff와 source 보존, 전체 기본 단위 검사 및 문서 링크/공백을 확인해 merge commit으로 기록합니다. 실제 DB 조회/적용·수집/발송·B main 병합·강제 푸시·다른 PR 변경은 제외합니다.
- 실제 검증: 검증 worktree의 `backend/`, Temurin Java 17.0.18+8·별도 Gradle cache에서 `DB_USERNAME=app_core_rw RUN_SMTP_SMOKE=false ./gradlew test --offline --rerun --no-daemon`을 실제 실행했습니다. 292 suites / 1,365 tests 중 1,364개 통과, 실패·오류 0, SMTP opt-in 1개 skip입니다. compileTestJava·test는 실행됐고 나머지 3개 task는 UP-TO-DATE였습니다. 계약 21 suites / 127건과 A 신규 계약 15건·B application service 29건/guard 4건/관리자 MVC 108건이 포함되며 재합산하지 않습니다.
- 보존/정적 확인: A/B 전용 11개 파일의 내용·모드 일치, 새 main 기준 diff 10개 파일, 원래 작업/독립 검증 절과 기준 본문 보존을 확인했습니다. Markdown 261개·링크 2,438개·코드 참조 63개 누락 0, `git diff --check` 통과입니다. 이 결과를 일반 merge commit에 담고 후속 문서 커밋에서 남아 있던 통합 기록을 반영합니다. 실제 DB 적용·잠금 경합·수집/운영 복구의 증거는 아닙니다.

## 2026-10-23 수집 기반 통합 기록 마무리

- 의도/범위: main 정합화 커밋 `5d595749` 뒤 현재 상태와 phase-plan 두 문서만 보완합니다. 통합 보존본에 남아 있던 통합 검사·독립 커밋 준비·PR/CI 전달·A 병합 기록을 고유 절 단위로 반영하고 A/B 원래 작업 절과 독립 검증 절은 그대로 유지합니다.
- 보존/선택: 기능 파일과 설정·SQL·회귀 테스트는 정합화 커밋에서 바꾸지 않습니다. 당시 관측의 미커밋/미병합 상태는 해당 작업 시점의 기록으로 보존하고 현재 구현·병합 상태는 current-state에서 구분합니다. 추가적인 기능 수정·문서 대규모 통폐합·B main 병합·실제 DB 적용은 범위가 아닙니다.
- 완료 조건: 두 문서만의 diff, 원래/통합 기록 보존과 기능 파일 원본 일치, 문서 계약의 실제 재실행·링크/공백 확인 후 별도 문서 커밋합니다. 전체 기본 단위 검사는 기능 파일이 같은 `5d595749`의 1,365건 결과와 연결하며 문서 계약 127건은 그 부분집합을 새 문서 상태에서 재검증합니다. 새 head의 원격 CI는 PR #369에서 별도로 확인합니다.
- 실제 검증/결과: 같은 Java 17·별도 cache 환경에서 `DB_USERNAME=app_core_rw RUN_SMTP_SMOKE=false ./gradlew test --tests '*ContractTest' --offline --rerun --no-daemon`을 실제 실행해 21 suites / 127 tests, 실패·오류·skip 0을 확인했습니다. test 실행·나머지 4개 task UP-TO-DATE입니다. Markdown 261개·링크 2,445개·코드 참조 63개 누락 0과 `git diff --check`, A/B 기능 파일 원본 일치·과거/통합 기록 보존을 확인했습니다. 이 결과를 두 문서만의 독립 커밋에 담아 일반 푸시하며 새 head의 원격 CI 결과와 검토 가능 전환은 PR에서 확인합니다. 최종 문서 상태에서는 전체 기본 단위 검사를 다시 돌리지 않았고 동일 기능 파일의 앞선 merge snapshot 검사와 새 문서 계약 검사를 연결합니다.

## 다음 작업

다음 범위는 [현재 확인 과제](current-state.md)의 각 항목을 재현 가능한 기능 작업으로 분리하는 것입니다. 추가적인 대규모 문서 통폐합을 필수 선행 작업으로 두지 않습니다. 과거의 `KEEP_OBSERVING` 값은 추천 품질 실험과 운영 관측의 기록으로 남기고, 데이터 유실·보안·계약 불일치 수정의 금지 조건으로 사용하지 않습니다.

## 2026-10-21 로컬 수집 실행 확인

- 실행 환경: 로컬 Docker `youth-welfare-app`(`127.0.0.1:8082`), 앱의 실제 DB URL은 `jdbc:postgresql://db:5432/youth_welfare?sslmode=disable`이다.
- `23:03:52–23:09:59 Asia/Seoul`에 `POST /api/admin/collect/all`을 한 번 실행했다. HTTP `200`, 소요 시간 `366.89초`이며 `api_sync_logs`의 실행 ID `432–436`과 실제 정책·raw payload 저장을 대조했다.
- 목록 저장 결과: 온통청년 `3,472`, 복지로 중앙 `155`, 복지로 지자체 `1,294`, 정부24 `10,963`건이다. 목록 4개 실행은 모두 `success`, 실패 `0`건이며, 복지로의 필터 제외 건수는 중앙 `306`, 지자체 `3,531`건이다.
- 신규 정책은 `1,209`건이고 전체 정책 수는 `14,981 → 16,190`건으로 증가했다. 저장 건수는 신규와 기존 정책 갱신을 합한 값이다.
- 후속 정부24 지원조건은 `50`건 중 `48`건 저장, `2`건 실패로 DB에 `partial_success`가 기록됐다. `490000000286`, `B55365500002`는 각각 3회 재시도에도 빈 응답이었다. 전체 HTTP 응답의 `completedWithFailures=false`만으로 모든 항목 성공으로 판단하지 않는다.
- 추가 확인: `collect_list_snapshots`, `collect_list_snapshot_items` 테이블이 없어 목록 변경 비교가 실패한다. `OPENAI_EMBEDDING_FORCE_LOCAL_FALLBACK=true` 설정으로 strict 임베딩 갱신은 건너뛴다. 이번 확인에서는 schema·앱 설정을 수정하지 않았다.
- 종료 후 `/actuator/health`는 `200 / UP`, 남은 수집 lock과 열린 circuit은 모두 `0`이었다. 실행 근거는 `tmp/collect-local-check/20261021T140352Z/summary.txt`, `verified-results.json`, `collection-log.txt`에 남겼다. 다른 상세 수집 경로와 운영 서버는 이번 확인 범위에 포함하지 않았다.
