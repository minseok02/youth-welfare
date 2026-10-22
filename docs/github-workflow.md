# GitHub 작업 규칙

문서군 진입점: [system-docs-index.md](core/system-docs-index.md)

2명이 프론트엔드와 백엔드를 나눠 작업하는 기준이다.

## 작업 시작과 기존 변경 보존

작업을 시작할 때 현재 브랜치, 작업 트리(파일의 현재 수정 상태), 스테이징(다음 커밋에 포함할 변경), 기존 worktree를 확인한다.

```bash
git branch --show-current
git status --short
git diff --stat
git diff --cached --stat
git worktree list
```

- 작업 전에 있던 수정·삭제·미추적 파일과 스테이징 상태를 임의로 되돌리거나 이번 작업의 변경으로 간주하지 않는다.
- 같은 파일에 기존 변경이 있으면 해당 부분을 읽고 필요한 부분만 수정한다. 파일 전체를 다시 생성해 기존 편집을 덮어쓰지 않는다.
- 기존 스테이징을 임의로 해제하거나 커밋에 섞지 않는다. 같은 파일 안에 변경이 섞이면 변경 구간(hunk)을 나눠 선택하고 `git diff --cached`로 확인한다. 안전하게 분리할 수 없다면 변경을 보존한 채 커밋 범위를 정한다.
- 충돌이나 다른 작업의 진행 흔적이 있으면 먼저 상태를 확인한다. 작업 공간을 깨끗하게 만들기 위한 자동 `stash`, 변경 폐기, 이력 초기화를 수행하지 않는다.

## 영역

| 영역 | 경로 | 기본 검증 |
|------|------|----------|
| 프론트 | `frontend/` | `cd frontend && npm run test:unit && npm run lint && npm run build` + 영향에 맞는 browser 검사 |
| 백엔드 | `backend/` | `cd backend && DB_USERNAME=app_core_rw ./gradlew test --no-daemon` |
| 배포 | `deploy/`, Docker 관련 파일 | 관련 배포 문서 확인 |
| 문서 | `docs/` | 링크, 작업 추적, 검증 결과 확인 |

프론트 작업은 프론트 파일과 관련 문서만, 백엔드 작업은 백엔드 파일과 관련 문서만 수정하는 것을 기본으로 한다.
양쪽 수정이 필요하면 API 계약 변경과 화면 연결을 가능한 한 다른 커밋/PR로 나눈다.

현재 PR/browser 기준선은 [.github/workflows/ci.yml](../.github/workflows/ci.yml) 에서 아래로 묶는다.

- `backend test`
- `frontend unit`
- `frontend npm audit`
- `runtime logout smoke`
- `admin forced logout smoke`
- `frontend lint/build/e2e`

## 브랜치

`main`에 직접 커밋하지 않는다.

형식:

```text
<type>/<area>-<short-topic>
```

예시:

```text
feat/frontend-policy-list
fix/backend-bookmark-toggle
docs/docs-github-workflow
```

사용할 값:

- `type`: `feat`, `fix`, `docs`, `test`, `refactor`, `chore`
- `area`: `frontend`, `backend`, `deploy`, `docs`, `common`

추가 원칙:

- 작업마다 새 브랜치를 만든다.
- 브랜치를 너무 오래 끌지 않는다.
- 중간 브랜치를 base로 하는 연쇄 PR(stacked PR)은 기본값으로 만들지 않는다.
- 가능하면 `main` 기준 단일 해결 PR로 정리한다.
- 브랜치는 작은 task 여러 개를 담을 수 있지만, 그 task들이 같은 문제/같은 목표를 향해야 한다.

worktree는 한 저장소의 다른 브랜치를 별도 디렉터리에서 작업하는 기능이다. 관련 없는 작업과 격리가 필요할 때 사용하고, 이미 적절한 작업 공간이 있으면 재사용한다. 매번 만드는 필수 절차는 아니다.

- 기준 브랜치·커밋을 확인한다. 새 worktree에는 현재 작업 공간의 미커밋 변경이 자동으로 포함되지 않는다.
- 저장소 안에 worktree를 만들면 그 디렉터리가 Git 추적에서 제외되는지 먼저 확인한다.
- 작업 공간의 의존성 설치·초기 검증은 필요한 범위에서 수행하고, 기존 실패와 새 변경으로 생긴 실패를 구분한다.
- 정리 전에 소유·사용 여부와 수정·미추적 파일을 확인한다. 다른 작업의 worktree를 임의로 지우거나 삭제 거부를 `--force`로 우회하지 않는다.

## task / 커밋 / PR 단위

- 작업은 먼저 작은 task로 나눈다.
- 작은 task 하나가 끝날 때마다 커밋할 수 있는 상태를 만든다.
- 커밋은 "작은 변경 단위"를 남기는 기록이다.
- PR은 "큰 해결 단위"를 묶어 보여주는 전달 단위다.

예:

- 정책 검색 쿼리 분리
- 검색 테스트 보강
- 문서/검증 결과 반영

위 3개가 같은 문제를 닫는 흐름이면:

- 커밋은 2~3개로 나눠도 된다.
- PR은 1개로 묶는다.

반대로 목표가 다른 변경이면:

- 같은 브랜치/같은 PR로 밀어 넣지 않는다.
- task가 작아도 문제 축이 다르면 커밋/PR을 분리한다.

## 커밋

커밋은 작은 task 단위로 만든다.
관련 없는 변경은 섞지 않는다.

형식:

```text
<type>(<area>): <summary>
```

예시:

```text
feat(frontend): connect policy list api
fix(backend): preserve bookmark state
docs(docs): add github workflow
```

스테이징 전에는 작업 변경을 읽고, 스테이징 후에는 실제 커밋 대상을 다시 확인한다.

```bash
git status --short
git diff --stat
git diff -- path/to/changed-file
git diff --cached --stat
git diff --cached
git diff --cached --check
```

`git add .`는 사용하지 않는다.
작업과 관련된 파일만 명시적으로 staging한다.
`git add -A`나 `git commit -am`으로 관련 없는 변경을 함께 포함하지 않는다. 같은 파일의 일부만 포함하려면 `git add -p -- path/to/file` 등으로 구간을 선택한다.
오래 열어둔 에디터 버퍼나 stale 변경 때문에 파일이 예전 상태로 되돌아간 흔적이 없는지도 같이 확인한다.

추가 원칙:

- 작은 task가 끝났으면 바로 커밋해도 된다.
- 아직 PR을 보낼 만큼 해결 단위가 안 닫혔으면 커밋만 쌓고 로컬에 유지한다.
- 커밋 로그만 봐도 작업 순서가 따라가져야 한다.
- 메시지는 최종 변경의 목적과 동작을 설명한다. 관련 검증을 확인하고 실행하지 못한 검증은 성공으로 기록하지 않는다.

```bash
git add frontend/src/pages/MainPage.jsx docs/phase-plan.md
git commit -m "feat(frontend): connect main policy api"
```

## 푸시와 PR

푸시는 요청이 있을 때만 한다.
요청 범위의 원격 저장소·브랜치를 확인한다. 푸시 요청을 병합·PR 닫기·브랜치 삭제·강제 푸시까지 허용한 것으로 확대하지 않는다. 같은 범위에 대해 이미 받은 승인은 다시 묻지 않는다.

```bash
git push -u origin feat/frontend-policy-list
```

PR 제목은 커밋 메시지 형식을 따른다.
PR 본문은 아래 형식을 쓴다.

```markdown
## 변경 내용
- 

## 검증
- 

## 영향 범위
- 

## 남은 작업
- 
```

PR 범위가 커졌다면 왜 커졌는지 본문에 적는다.
오래된 중간 PR도 고유한 변경과 사용 여부를 확인한다. 정리가 요청된 범위에서만 닫고, 오래됐다는 이유만으로 자동 병합·삭제하지 않는다.

추가 원칙:

- 작은 task 하나 끝날 때마다 PR을 바로 만들 필요는 없다.
- 여러 작은 커밋이 모여 하나의 기능/버그/리팩터링 단위를 닫을 때 PR을 만든다.
- PR은 리뷰어가 "무슨 문제를 어떻게 닫았는지" 한 번에 이해할 수 있는 범위로 묶는다.
- 커밋은 잘게, PR은 문제 해결 단위로 적당히 묶는 쪽을 기본값으로 둔다.

## 충돌, 이력 변경과 복구

이력 재작성은 기존 커밋의 식별자가 달라지는 변경이다. 충돌은 양쪽의 의도와 관련 테스트를 확인해 해결하고 `ours`/`theirs`로 한쪽을 일괄 선택하지 않는다. 진행 중인 작업을 취소할 때는 해당 작업의 중단 명령을 검토하되, 취소 과정에서 버릴 수정이 없는지 확인한다.

| 상황 | 기준 |
| --- | --- |
| 자신이 만든 미공유 커밋 정리 | 관련 없는 변경을 보존하고 필요한 경우 백업 브랜치·패치를 남긴 뒤 범위 안에서 수행한다. 백업 브랜치는 미커밋 파일의 백업이 아니다. |
| 공유된 커밋의 amend/rebase, 강제 푸시 | 대상·이유·협업 영향·복구 방법을 설명하고 사전 승인을 받는다. 일반 푸시 거절은 원격 변경을 조사할 사유이며 자동 강제 푸시의 근거가 아니다. |
| 기존 변경 폐기 | `reset --hard`, `clean -f`, 기존 변경을 지우는 `restore`/`checkout`, 미병합 브랜치·작업 공간 삭제는 실제로 버릴 내용을 확인하고 명시적으로 승인된 범위에서만 수행한다. 자신이 이번 작업에서 만든 변경의 수정·철회와 구분한다. |
| 이미 공유한 변경의 취소 | 이력을 지우기보다 취소 내용을 새 커밋으로 남기는 `revert`를 우선 검토하고, 데이터·API·다른 변경에 미치는 영향도 확인한다. |

승인된 강제 푸시에도 대상 브랜치와 기대하는 원격 커밋을 확인하고 `--force-with-lease`를 사용한다. 이 옵션 자체가 승인이나 완전한 보호를 대신하지 않는다. `reflog`는 로컬 참조 이동 기록이며 모든 미커밋 변경을 복구하는 백업이 아니다.

## 검증

변경 영역에 맞는 검증을 실행한다. 준비 조건과 단위·통합·외부 호출의 차이는 [테스트 실행 기준](core/testing.md), 앱/DB 대상과 부작용은 [환경 안내](core/environment-guide.md)를 따른다. 아래 명령은 저장소 루트에서 시작한다.

- 프론트 단위·정적: `cd frontend && npm run test:unit && npm run lint && npm run build`
- 프론트 browser: 테스트용 앱·fixture를 준비한 뒤 `cd frontend && npm run test:e2e`. 배포 origin 대상 검사는 서버 데이터를 변경할 수 있다.
- 프론트 보안 기준선: `cd frontend && npm audit`
- 백엔드: `cd backend && DB_USERNAME=app_core_rw ./gradlew test --no-daemon`
- 문서 계약: `backend/`에서 `DB_USERNAME=app_core_rw ./gradlew test --tests '*ContractTest' --rerun-tasks --no-daemon`. 문서 변경은 test 캐시에 반영되지 않을 수 있으므로 실제 실행 여부를 확인한다.
- DB/Redis 흐름: **개발 컴퓨터에서만** `ALLOW_LOCAL_DOCKER_DB=true docker compose --env-file .env -f docker-compose.yml up -d db redis`, `bash deploy/smoke/preflight-integration-runtime.sh` 순서로 준비한 뒤 `cd backend && DB_USERNAME=app_core_rw ./gradlew integrationTest --no-daemon`. 운영 RDS에 fixture를 쓰지 않는다.
- runtime auth/session 기준선: `APP_BASE_URL=http://127.0.0.1:8082 APP_HEALTH_URL=http://127.0.0.1:8082/actuator/health REQUIRE_RECOMMENDATIONS=false bash deploy/smoke/run-local-runtime-api-smoke.sh`
- auth/session 보안 경계: `bash deploy/smoke/run-local-admin-forced-logout-smoke.sh`

runtime/admin smoke는 로그인·세션 무효화 등 상태 변경을 포함한다. 로컬/서버의 대상과 테스트 계정을 확인한 별도 검증 범위에서만 실행한다. 프론트에서 라우팅, 인증 복귀, admin dashboard를 건드렸다면 관련 browser smoke까지 확인한다.

실행하지 못한 검증은 이유를 남긴다.

검증 결과는 실제 검사한 변경 상태와 연결한다. 관련 코드·설정이 바뀌어 결과가 더 이상 적용되지 않으면 영향에 맞춰 다시 검사한다. 변경과 무관한 검사를 매번 전부 재실행하거나 일부 검사 통과를 전체 성공으로 표현하지 않는다. CI 설정이 있다는 것과 필수 검사·리뷰를 통과해야 병합할 수 있다는 것은 다르므로 GitHub의 보호 규칙은 실제 확인한 범위만 보고한다.

### CI 실패 대응

1. 대상 PR·커밋 SHA, 실패한 작업 이름과 실행 URL을 확인하고 해당 로그의 실패 지점을 읽는다. 연결된 GitHub 도구가 충분하면 이를 우선하고, 필요하면 `gh`를 사용한다.
2. 코드·테스트 실패, 환경·권한·의존성 문제를 근거로 구분한다. 로그를 얻지 못했으면 원인을 미확인으로 남기고 추측을 확정하지 않는다. 일시적 실패라는 근거 없이 재실행만 반복하지 않는다.
3. 승인된 작업 범위 안에서 원인을 수정하고 관련 검사를 실행한다. 검사 삭제·기준 완화·실패 무시로 통과시키지 않는다. 규칙 예외나 범위 확대는 [작업 가이드](work-guide.md#코드-패턴과-변경-기준)의 승인 절차를 따른다.
4. 수정 뒤의 로컬 결과와 해당 커밋의 원격 CI 결과를 구분해 기록한다. 실행 중이거나 확인하지 않은 원격 검사는 통과로 표시하지 않는다.

## 문서 갱신

작업 완료 후 `docs/phase-plan.md`를 갱신한다.

- 해당 작업에 완료 여부와 범위를 명시한다. 기존에 없는 `완료`/`진행 예정` 절을 기계적으로 추가하지 않는다.
- 새로 발견한 기능·운영 문제는 미해결 과제로 남기고 문서 수정 완료와 구분한다.
- 실행 명령·대상 환경·결과·skip·미실행 범위를 반영한다.

문제가 발생했거나 재발 가능성이 있는 판단을 했다면 `docs/core/troubleshooting-log.md`에 `문제 / 해결 / 이유` 형식으로 남긴다.

## 참고한 외부 스킬

2026-10-22에 아래 원문을 검토하고 필요한 원칙만 프로젝트 기준으로 다시 작성했다. 링크는 검토한 커밋에 고정했다. 스킬은 설치하지 않았으며, 원문의 명령·승인·자동 실행 절차를 통째로 채택한 것은 아니다.

| 원문 | 반영한 부분 | 그대로 가져오지 않은 부분 |
| --- | --- | --- |
| [git-advanced-workflows](https://github.com/wshobson/agents/blob/0874a27b17a22e2b7c2e2bcd725768e0803cad06/plugins/developer-essentials/skills/git-advanced-workflows/SKILL.md) | 작은 변경 단위, 공유 이력 보호, 백업·중단·복구의 구분 | 고급 명령의 상시 사용, 변경 폐기 명령을 일반 복구 절차로 실행하는 예시 |
| [using-git-worktrees](https://github.com/obra/superpowers/blob/2ce8aa15d05bd57213e6f746f953aafb7ded7a8b/skills/using-git-worktrees/SKILL.md) | 기존 작업 공간 확인, 격리가 필요한 경우 worktree 사용, 추적 제외 확인 | 고정된 설치·초기 검증 명령, 별도 동의 질문과 추적 제외 설정의 자동 커밋 절차 |
| [finishing-a-development-branch](https://github.com/obra/superpowers/blob/2ce8aa15d05bd57213e6f746f953aafb7ded7a8b/skills/finishing-a-development-branch/SKILL.md) | 검증 후 통합 판단, 미커밋 파일과 다른 작업 공간 보존 | 매번 고정 선택지를 묻는 메뉴와 디렉터리 이름만으로 정리 소유권을 판단하는 방식 |
| [verification-before-completion](https://github.com/obra/superpowers/blob/2ce8aa15d05bd57213e6f746f953aafb7ded7a8b/skills/verification-before-completion/SKILL.md) | 완료·통과 주장에 실제 검증 근거 연결 | 변경이 없어도 같은 검사를 반복하거나 제한된 검증의 유효성까지 부정하는 절차 |
| [gh-fix-ci](https://github.com/openai/skills/blob/49f948faa9258a0c61caceaf225e179651397431/skills/.curated/gh-fix-ci/SKILL.md) | 실패한 검사·실행 URL·로그 확인, 수정 뒤 재검증 | 이미 승인된 일반 수정에도 매번 별도 계획 승인을 요구하는 절차 |

명령 의미는 Git 공식 문서의 [diff](https://git-scm.com/docs/git-diff), [worktree](https://git-scm.com/docs/git-worktree), [push](https://git-scm.com/docs/git-push), [reflog](https://git-scm.com/docs/git-reflog)를 함께 확인한다.
