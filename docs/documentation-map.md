# 문서 역할과 정리 지도

문서 시작점은 [README.md](README.md)입니다. 여기서는 정보의 대표 위치와 통합·보관 범위를 관리합니다. 처음의 파일별 후보 수는 삭제 목표나 확정된 실행 목록이 아닙니다.

## 내용과 환경 구분

- **현재 구현:** 코드·설정·재현으로 확인한 동작.
- **요구사항과 결정:** 기대 동작과 보호할 계약. 현재 코드가 다르면 불일치로 남김.
- **실행 절차:** 전제 환경·권한·부작용·검증/복구 순서.
- **관측 이력:** 날짜·커밋·앱/DB·표본에 한정된 결과.
- **제안과 미확인:** 미구현 아이디어 또는 검증이 필요한 주장.

같은 파일에 이 내용이 섞여 있으면 절 단위로 처리합니다. 로컬과 서버의 절차 차이는 중복이 아닙니다. [환경 안내](core/environment-guide.md)를 공통으로 참조하며, 각 runbook은 실제 차이가 있는 설정과 안전 절차를 유지합니다.

## 대표 위치

| 정보 | 대표 문서 |
| --- | --- |
| 시작과 분야별 목차 | [README.md](README.md), 분야별 `*-docs-index.md` |
| 현재 작업 방향·미해결 과제 | [current-state.md](current-state.md) |
| 기술·모듈·호출 구조 | [project-spec.md](project-spec.md), [architecture.md](architecture.md) |
| 로컬/서버/원격 DB 구분 | [environment-guide.md](core/environment-guide.md) |
| 로컬 검증 | [testing.md](core/testing.md), [local-validation-docs-index.md](core/local-validation-docs-index.md) |
| 서버 배포와 운영 | [deployment.md](deployment.md), [운영 후속 점검](core/final-ops-closeout-checklist.md) |
| API 계약과 외부 데이터 매핑 | [api-mapping.md](core/api-mapping.md). 서로 다른 목적의 절을 구분 |
| 데이터/보안 계약 | [보안 현재 상태](core/security-hardening-current-state.md), [정규화 현재 상태](policy/policy-normalization-current-state.md) |
| 작업과 문제 해결 기록 | [phase-plan.md](phase-plan.md), [troubleshooting-log.md](core/troubleshooting-log.md) |
| 설계 배경과 과거 측정 | [history-docs-index.md](history-docs-index.md), `history/`, `archive/` |

목차는 상세 설명을 복사하지 않고 대표 문서로 연결합니다. `start.md`는 기존 진입 경로를 위한 짧은 안내로 유지합니다.

## 적용한 추출과 보존

| 원본 | 현재 내용의 위치 | 보존 위치와 처리 |
| --- | --- | --- |
| `current-state.md` 누적 서버/로컬 수치 | current-state의 현재 범위·계약·확인 과제 | [정리 전 상태](history/core/current-state-before-doc-cleanup.md) |
| `phase-plan.md` 누적 작업 이력 | phase-plan의 이번 작업·보존 계약·검증 | [정리 전 작업 이력](history/core/phase-plan-before-doc-cleanup.md) |
| `core/troubleshooting-log.md` 누적 기록 | 반복 문제의 짧은 현재 안내 | [문제 해결 이력](history/core/troubleshooting-log-before-doc-cleanup.md) |
| 추천 현재 상태의 관측·status ladder | [현재 구현과 확인 과제](recommendation/recommendation-current-state.md) | [기존 추천 기록](history/recommendation/recommendation-state-before-doc-cleanup.md) |
| 정규화 현재 상태의 inventory·실험·closeout | [현재 계약과 확인 과제](policy/policy-normalization-current-state.md) | [기존 정규화 기록](history/policy/normalization-state-before-doc-cleanup.md) |
| `core/db-migration.md`의 legacy 설명 | [로컬 초기화·기존 DB·RDS 변경 절차](core/db-migration.md) | [기존 migration 원문](history/core/db-migration-before-doc-cleanup.md) |
| 프론트 사용자 흐름 runbook 10개 | [통합 runbook의 흐름별 절](frontend/frontend-user-flow-runbook.md) | 유효한 기대 동작을 통합하고 원본 10개 제거. 재검토에서 오래된 테스트 필터 6곳과 확인 모달 동선을 현재 구현에 맞춰 보완. observation script의 안내 포인터도 해당 앵커로 변경 |
| auth helper-name·helper-method-name·implementation-order 3개 | [기존 서비스 설계의 통합한 서비스 결정](history/auth/auth-admin-forced-logout-service-structure-policy.md#통합한-서비스-결정) | 책임 구분·선택 근거·안전한 적용 순서를 흡수하고 원본 3개 제거. Git 기준 커밋에서 원문 복구 가능 |
| source 온보딩 architecture·checklist·playbook·priority·code-entrypoints·template 6개 | [가이드](policy/policy-source-onboarding-checklist.md), [검토 템플릿](policy/policy-source-onboarding-template.md) | 반복 흐름을 통합하고 원본 4개 제거. 분류 예외·공식 조사 출처·코드 진입점·날짜가 있는 초기 관측을 가이드 각 절에 흡수. 새 보관 사본은 만들지 않음 |
| Gov24·정책 진행/계획 8개 | [데이터·소비 계약](policy/policy-gov24-lane-closeout.md), [작업·검증](policy/policy-gov24-implementation-checklist.md) | 보류/reopen·초기 promotion·전역 pending/priority/track split의 반복 원본 6개 제거. 날짜별 로컬/서버 관측과 새 목표의 조건은 구분해서 유지 |
| 추천 AI exclusion·snapshot·gate 15개 | [파일 조회·해석](recommendation/recommendation-ai-exclusion-latest-status-runbook.md), [실제 재수집](recommendation/recommendation-ai-exclusion-baseline-refresh-runbook.md) | 원본 13개 제거. 실제 helper script 15개는 그대로 두고 입력/출력·read/write·승인 경계를 한 곳에서 설명 |
| 수집 현재 상태·checklist·ops 3개 | [현재 동작](collect/collect-current-state.md), [실행·장애 판정](collect/collect-ops.md) | checklist 1개 제거. scheduled/manual·효과적인 설정과 service fallback·부분 실패·복구 상한을 구분 |
| 공통 안정화·운영 안내 3개 | [baseline](core/ops-baseline-runbook.md), [점검·인계](core/final-ops-closeout-checklist.md) | stabilization 원본 1개 제거. 관측과 배포/repair/cron 설치를 분리하고 과거 동결 지시를 현재 규칙에서 제외 |
| 인증 현재 상태·checklist·closeout 3개 | [현재 계약](auth/auth-session-revocation-current-state.md), [검증](auth/auth-operation-checklist.md) | closeout 1개 제거. 해시 key·iatm·cookie-only·증적과 Redis 유실 미해결 위험을 유지 |

누적본은 원래 커밋 `b6af3ad7`의 본문을 보존하고 이동에 필요한 상대 링크만 바꿉니다. 기존 구현을 새로 검증하거나 현재 운영 상태로 확정한 기록이 아닙니다.

## 역할이 달라 유지하는 문서

- 소스 온보딩의 실행 가이드와 검토 결과 작성 템플릿.
- PII·인증·운영 권한 계약과 날짜가 있는 cut-over 설계 기록.
- 조회용 진단과 실제 backfill·repair·seed·approval write 절차.
- 로컬 통합 테스트와 서버 배포/운영 smoke.
- 성능 측정 방법과 날짜·환경이 고정된 측정 결과.
- 공식 코드북·source 조사·단위 해석의 고유 근거.

## 정리 범위와 남긴 자료

현재 안내와 이력 6개를 분리하고, 프론트 흐름 10개와 인증 결정 3개를 각각 통합했습니다. 이후 source 온보딩은 6개→2개, 이어 Gov24·추천 AI exclusion·수집·공통 운영·인증의 32개→10개로 줄였습니다. 새 보관 사본은 추가하지 않았고 원본 누적 기록과 MySQL SQL은 유지합니다. 서버/로컬 차이·고유한 운영 계약·진단 입력·상태 변경 경계는 통합 절에 남깁니다.

앱 코드·DB schema·CI 실행 정책·운영 설정은 변경하지 않았습니다. 최초 구조 정리에서는 프론트 observation 스크립트의 문서 포인터 10개를 바꿨고, 2026-10-22 후속 보완에서는 추천 클릭·검색 성능 스크립트 2개와 오프라인 계약 테스트도 수정·추가했습니다. source 온보딩 감축은 Markdown만, 이어진 5개 묶음 감축은 Markdown과 Gov24 taxonomy 검증 script의 문서 안내 포인터 1곳만 변경합니다.

문서 재검토 보완은 토큰 메모리 저장·관리자 숨김 입력·runtime env 생성 순서·로컬 테스트 준비·추천 로그/클릭·DB/schema 이름을 실제 구현과 대조한 수정입니다. 최근 수집 문제는 해당 로컬 관측으로만 연결하며, 누락 테이블이나 부분 실패를 문서 수정으로 해결했다고 처리하지 않습니다. 완료와 검증 결과는 [작업 현황](phase-plan.md)에 남깁니다.

감축 후 재검토에서는 운영 인증·cron 기본값, 데모 API/SQL, 프론트 세션·토큰 URL 계약, 추천 README와 과거 PR/개선 기록의 구분을 보완했습니다. 추가 파일 삭제·이동 없이 기존 경로와 과거 관측을 유지하며, cron 설치기나 기존 운영 설정이 바뀐 것으로 처리하지 않습니다.

## 추가 통합의 조건

AI exclusion의 snapshot/compare/drift는 통합했으며, 남은 후보 누락·AI 0점 진단도 다음을 확인한 묶음부터 처리합니다. 확인하지 못한 원본은 일괄 삭제하지 않습니다.

1. 각 절의 고유 정보와 현재 유효성.
2. 이동할 문서·절 또는 보관 위치.
3. Markdown 링크·앵커·본문 경로와 스크립트의 문서 포인터.
4. 문서 내용·경로를 읽는 계약 테스트.
5. 로컬/서버 차이, 쓰기 작업과 외부 호출의 경고.

문서 정리 검증에는 정적 참조 확인과 관련 테스트만 포함합니다. 운영 접속, schema patch, 실제 수집·AI·메일·푸시·부하 시험은 별도 작업 범위입니다.
