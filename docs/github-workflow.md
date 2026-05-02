# GitHub 작업 규칙

문서군 진입점: [system-docs-index.md](./system-docs-index.md)

2명이 프론트엔드와 백엔드를 나눠 작업하는 기준이다.

## 영역

| 영역 | 경로 | 기본 검증 |
|------|------|----------|
| 프론트 | `frontend/` | `cd frontend && npm run lint && npm run build` |
| 백엔드 | `backend/` | `cd backend && ./gradlew test` |
| 배포 | `deploy/`, Docker 관련 파일 | 관련 배포 문서 확인 |
| 문서 | `docs/` | 링크, 작업 추적, 검증 결과 확인 |

프론트 작업은 프론트 파일과 관련 문서만, 백엔드 작업은 백엔드 파일과 관련 문서만 수정하는 것을 기본으로 한다.
양쪽 수정이 필요하면 API 계약 변경과 화면 연결을 가능한 한 다른 커밋/PR로 나눈다.

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

커밋 전 확인:

```bash
git status --short
git diff --stat
```

`git add .`는 사용하지 않는다.
작업과 관련된 파일만 명시적으로 staging한다.
오래 열어둔 에디터 버퍼나 stale 변경 때문에 파일이 예전 상태로 되돌아간 흔적이 없는지도 같이 확인한다.

추가 원칙:

- 작은 task가 끝났으면 바로 커밋해도 된다.
- 아직 PR을 보낼 만큼 해결 단위가 안 닫혔으면 커밋만 쌓고 로컬에 유지한다.
- 커밋 로그만 봐도 작업 순서가 따라가져야 한다.

```bash
git add frontend/src/pages/MainPage.jsx docs/phase-plan.md
git commit -m "feat(frontend): connect main policy api"
```

## 푸시와 PR

푸시는 요청이 있을 때만 한다.

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
오래된 중간 PR이 있으면 merge하지 말고 close로 정리한다.

추가 원칙:

- 작은 task 하나 끝날 때마다 PR을 바로 만들 필요는 없다.
- 여러 작은 커밋이 모여 하나의 기능/버그/리팩터링 단위를 닫을 때 PR을 만든다.
- PR은 리뷰어가 "무슨 문제를 어떻게 닫았는지" 한 번에 이해할 수 있는 범위로 묶는다.
- 커밋은 잘게, PR은 문제 해결 단위로 적당히 묶는 쪽을 기본값으로 둔다.

## 검증

변경 영역에 맞는 검증을 실행한다.

- 프론트: `cd frontend && npm run lint && npm run build`
- 백엔드: `cd backend && ./gradlew test`
- DB/Redis 흐름: `docker compose up -d db redis` 후 `cd backend && ./gradlew integrationTest`

실행하지 못한 검증은 이유를 남긴다.

## 문서 갱신

작업 완료 후 `docs/phase-plan.md`를 갱신한다.

- 완료한 항목은 `완료`로 옮긴다.
- 새로 발견한 작업은 `진행 예정`에 추가한다.
- 검증 결과를 반영한다.

문제가 발생했거나 재발 가능성이 있는 판단을 했다면 `docs/troubleshooting-log.md`에 `문제 / 해결 / 이유` 형식으로 남긴다.
