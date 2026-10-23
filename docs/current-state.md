# 현재 상태

## 현재 작업

문서·Git 기준점 정리를 마치고 수집 코드 개선을 시작했습니다. 이후 우선순위는 수집 → 정규화 → 자격조건 → 평가 기반 → 개인별 순위 품질입니다. FastAPI 도입과 Java 업그레이드는 현재 범위가 아니며, 과거 안정화 단계의 일괄 기능 동결 지시는 승인된 리팩토링을 제한하는 작업 규칙으로 사용하지 않습니다.

2026-10-23 첫 수집 결과 판정 보완은 PR #368로 main에 병합됐으며 이번 작업의 기준 커밋은 `7880dc1a`입니다. 항목 부분 실패와 목록 비교 오류를 구분하는 [현재 결과 계약](collect/collect-current-state.md#전체-수집-결과-계약)과 [의도·완료 조건·검증 기록](phase-plan.md#2026-10-23-수집-결과-판정-보완)을 확인합니다. 코드 병합을 운영 반영이나 실제 수집 복구의 증거로 삼지 않습니다.

snapshot DB의 읽기 전용 진단에서 누락을 확인해 [snapshot 전용 패치·검증 경로](core/db-migration.md#snapshot-전용-검증과-별도-적용)를 준비했습니다. 이 브랜치는 작업 A만 포함하며 [독립 검증·커밋 기록](phase-plan.md#2026-10-23-작업-a-독립-커밋-검증)을 확인합니다. sidecar 재처리 잠금인 작업 B는 포함하지 않습니다. 실제 DB 적용·수집 복구·운영 반영은 하지 않았습니다.

## 코드와 환경 기준

- 백엔드는 Java 17 / Spring Boot 3.5.14 / PostgreSQL 16 + pgvector / Redis 기반입니다. Java 21 전환은 아직 적용하지 않았습니다.
- 프론트는 React / Vite이며, 인증·정책 탐색·추천·챗봇·알림·관리자 흐름이 구현되어 있습니다.
- 로컬 전체 앱은 `docker-compose.yml`의 `app + db + redis`, 서버 배포 설정은 `docker-compose.prod.yml`의 `app + redis + RDS` 구조입니다.
- 로컬 앱이 원격 DB를 사용할 수도 있습니다. [환경 구분](core/environment-guide.md)에서 실행 위치와 앱/DB 대상을 먼저 확인합니다.
- 저장소 설정과 실제 가동 상태는 구분합니다. 현재 서버 health·queue·권한 적용 상태는 해당 서버의 새 관측으로 확인해야 합니다.

## 리팩토링 전에 보존할 계약

- 인증과 세션 무효화: [auth 현재 상태](auth/auth-session-revocation-current-state.md).
- PII 분리와 최소권한: [보안 현재 상태](core/security-hardening-current-state.md), [데이터 분리 설계](core/user-data-separation-design.md), [키 교체](core/pii-key-rotation-runbook.md).
- 수집 list/detail/support의 역할과 실행 경계: [수집 현재 상태](collect/collect-current-state.md), [detail 계약](collect/collect-detail-execution-contract.md).
- 공식 정보·파생 규칙·AI 결과의 출처 차이와 호환 API 계약: [정규화 현재 상태](policy/policy-normalization-current-state.md).
- 북마크와 사용자 데이터를 추천 재생성·정리 작업에서 보호합니다. 기존 구현을 올바른 요구사항으로 간주하지 않습니다.

## 코드 대조에서 드러난 확인 과제

아래는 해결 완료가 아니라 재현 테스트와 설계 검토가 필요한 과제입니다.

| 과제 | 확인할 경계 |
| --- | --- |
| 추천 교체와 북마크 보존 | 후보 밖 기존 북마크, 저장 배치 식별, 삭제/재삽입 |
| 추천 로그와 평가 근거 | 미클릭 로그 삭제, 생성/노출/클릭 구분, 로그 수 기반 가중치 가정 |
| 자격조건 의미 | 사용자 소득 구간과 source 금액·비율·단위, 미입력과 미지정 구분 |
| 수집 원문과 재처리 | DTO 재직렬화와 실제 응답 원문, 변경 이력·재처리, `collect/all` 집계 보완과 별개인 개별/async 결과 판정 |
| 정규화 갱신과 운영 보정 | list/detail 소유 범위, 사라진 fact, 수동 수정값의 재수집 보존 |
| 검색·AI 장애 | 저장 벡터와 질의 벡터의 공간 일치, 외부 호출 실패와 DB 자원 점유 |
| 세션 회수 상태의 내구성 | 운영 Redis 비영속·tmpfs, 상태 유실 뒤 미만료 회수 JWT 재허용 가능성. [auth 위험과 후속 검증](auth/auth-session-revocation-current-state.md#redis-상태-유실-미해결-위험) |

요구사항 자체의 변경 승인과 구현 결함은 구분합니다. [SRS 요구·구현 대조](core/srs-v2.10.md#현재-요구와-구현의-대조)에서 미충족 요구와 변경 검토 항목을 확인합니다.

세부 기능 수정은 각 작업의 기대 동작과 완료 조건을 정한 뒤 진행합니다. 첫 수집 결과 집계 보완으로 위 과제 전체가 해결됐다고 처리하지 않습니다.

## 최근 로컬 수집에서 확인된 미해결 문제

2026-10-21 로컬 Docker 관측에서 목록 4개 source는 성공했지만 snapshot 테이블 두 개가 없어 list diff가 실패했고, Gov24 지원조건은 `48/50`건 저장으로 부분 실패했습니다. local fallback 설정으로 strict embedding 갱신도 건너뛰었습니다. 당시 health `UP`은 이 문제들의 해결 증거가 아닙니다.

정확한 실행 결과는 [로컬 수집 기록](phase-plan.md#2026-10-21-로컬-수집-실행-확인), 진단·복구 범위는 [DB 변경 절차](core/db-migration.md#수집-snapshot-테이블-누락)를 확인합니다. 운영 서버의 현재 상태와 구분하고, schema·수집·embedding 수정은 별도 기능 작업으로 남깁니다.

## 운영 점검

운영 제보 처리 순서는 `정책 오류 제보 -> duplicate -> link review -> drift tail`입니다. 과거 기록의 `policy_error_open_reports=0`은 당시 서버 관측값이며 현재 queue가 비었다는 증거가 아닙니다.

- 운영 상태와 배포 확인: [운영 체크리스트](core/final-ops-closeout-checklist.md#attention과-backlog-판정), [배포 후 점검](core/final-ops-closeout-checklist.md).
- 정책 품질 queue: [triage runbook](policy/policy-data-quality-triage-runbook.md).
- 알림 backlog: [notification runbook](core/notification-backlog-audit-runbook.md).
- 테스트: [testing.md](core/testing.md). 로컬 단위 테스트·통합 테스트·서버 점검의 결과를 서로 대체하지 않습니다.

## 작업과 기록

- 진행·검증·남은 일: [phase-plan.md](phase-plan.md).
- 반복 문제와 해결 근거: [troubleshooting-log.md](core/troubleshooting-log.md).
- 문서별 역할과 통합/보관 범위: [documentation-map.md](documentation-map.md).
- 정리 전 누적 상태와 서버/로컬 관측: [이전 기록](history/core/current-state-before-doc-cleanup.md). 본문 날짜의 근거를 보존하며 현재 실행 지침으로 사용하지 않습니다.
