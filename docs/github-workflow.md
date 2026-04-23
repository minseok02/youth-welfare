# GitHub 작업 규칙

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
