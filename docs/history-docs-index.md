# 설계와 관측 이력

과거 판단을 찾을 때 사용하는 목차입니다. 날짜·커밋·로컬/서버·표본을 구분하며 현재 동작과 운영 절차는 [현재 상태](current-state.md)와 [환경 안내](core/environment-guide.md)에서 확인합니다.

## 누적 작업과 관측 원문

- [정리 전 작업 이력](history/core/phase-plan-before-doc-cleanup.md)
- [정리 전 현재 상태](history/core/current-state-before-doc-cleanup.md)
- [문제 해결 누적 기록](history/core/troubleshooting-log-before-doc-cleanup.md)
- [기존 migration 설계와 MySQL 예시](history/core/db-migration-before-doc-cleanup.md)
- [기존 추천 상태와 gate 관측](history/recommendation/recommendation-state-before-doc-cleanup.md)
- [기존 정규화 상태와 inventory](history/policy/normalization-state-before-doc-cleanup.md)

정리 전 원문의 사실·수치를 현재 결과로 바꾸지 않습니다. 이동한 문서의 상대 링크는 현재 참조 위치에 맞춥니다.

## 설계 배경

| 분야 | 내용과 진입점 |
| --- | --- |
| 인증 | `history/auth/`: exact revoke·session cutoff·JWT·권한의 결정. [서비스 책임과 이름·구현 순서](history/auth/auth-admin-forced-logout-service-structure-policy.md) |
| 정책·정규화 | `history/policy/`: source 조사·bridge·단위·fact 갱신의 근거 |
| AI와 replay | `history/ai/`: 교육 실험·OpenAI 변동·검증 방법 |
| PostgreSQL 전환 | [기존 Phase 목차](postgres/postgres-refactor-spec-index.md). 현재 공통 탐색/벡터 구조는 [아키텍처](architecture.md) |
| 성능 | [최적화 이력](performance/performance-optimization-log.md), [날짜별 baseline](performance/performance-baseline-current.md) |
| 과거 프로젝트 계획·SQL | [archive/README.md](archive/README.md), [MySQL SQL](archive/mysql-migrations/README.md) |

## 현재도 참조하는 고유 근거

`history/`에 있다는 이유만으로 모두 폐기하지 않습니다.

- [YOUTH 소득 0/0 해석](history/policy/policy-normalization-youth-income-zero-policy.md): 현재 추천 설명이 참조하는 과거 근거. 금액·비율·구간의 단위 정합성은 별도 확인.
- [fact merge 설계](history/policy/policy-normalization-fact-merge-rules.md): list/detail의 역할과 우선순위 근거. 삭제·갱신 동작은 현재 구현과 대조.
- [목록 정렬과 지역](history/policy/policy-listing-sort-region-strategy.md), [카드 source 지역](history/policy/policy-card-source-sido-field.md): 당시 선택과 조사.
- [공식 코드북 출처](policy/policy-user-provided-official-codebooks.md): reference asset·변환·seed의 연결.

과거 `next step`과 단계별 브랜치·명령은 새 작업을 자동 승인하지 않습니다. 미완료 제안은 현재 요구와 대조한 뒤 별도 범위로 정합니다.
