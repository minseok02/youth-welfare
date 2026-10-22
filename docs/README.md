# 프로젝트 문서

현재 구현과 확인 과제는 [current-state.md](current-state.md), 로컬·서버별 실행 대상은 [환경 안내](core/environment-guide.md)를 먼저 확인합니다. 작업마다 전체 문서를 읽지 않고 해당 분야의 목차에서 필요한 계약과 절차를 찾습니다.

## 시작할 때

| 목적 | 문서 |
| --- | --- |
| 기술과 구조 | [프로젝트 정보](project-spec.md), [아키텍처](architecture.md) |
| 작업 범위와 진행 | [작업 가이드](work-guide.md), [작업 현황](phase-plan.md), [Git 규칙](github-workflow.md) |
| 로컬 개발과 테스트 | [환경 구분](core/environment-guide.md), [테스트 실행](core/testing.md) |
| 서버 배포와 점검 | [배포](deployment.md), [운영 점검](core/final-ops-closeout-checklist.md) |
| 문서 정리와 정보의 위치 | [문서 역할과 정리 지도](documentation-map.md) |

## 분야별 목차

- [인증과 세션](auth/auth-docs-index.md)
- [수집](collect/collect-docs-index.md)
- [정책 데이터와 정규화](policy/policy-docs-index.md)
- [추천](recommendation/recommendation-docs-index.md)
- [프론트 QA](frontend/frontend-qa-docs-index.md)
- [성능](performance/performance-docs-index.md)
- [공통 구조와 운영](core/system-docs-index.md)

## 현재 설명과 과거 기록

현재 구현, 지켜야 할 요구사항, 미래 제안, 날짜가 있는 관측 결과를 구분합니다. 코드와 요구사항이 다르면 불일치를 기록하며, 코드에 맞춰 요구사항을 임의로 낮추지 않습니다.

[작업 이력](history/core/phase-plan-before-doc-cleanup.md), [문제 해결 이력](history/core/troubleshooting-log-before-doc-cleanup.md), [설계 배경](history-docs-index.md), [보관 자료](archive/README.md)는 현재 설명에 필요한 근거를 찾을 때 사용합니다. `history/`에 있어도 현재 계약이 참조하는 고유한 근거는 확인합니다. 과거 서버 수치나 로컬 seed 결과를 현재 운영 상태로 해석하지 않습니다.
