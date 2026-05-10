# 현재 상태

- 현재는 운영 전환 단계가 아니라 로컬 기능/구조 검증 단계입니다.
- 운영 서버는 아직 없습니다.
- 프론트 연동 전까지는 백엔드, 문서, 로컬 smoke 기준으로 검증합니다.
- 지금 우선순위는 기능 검증, 구조 검증, 수정, 최적화/보안, 프론트 연동 검증 순서입니다.
- 운영/배포 관련 작업은 마지막 단계에서만 다룹니다.
- 2026-05-10 기준 복지로 운영 계정을 확보했고, `중앙 list`, `중앙 detail`, `지자체 list`, `지자체 detail` 을 각각 일일 `100,000` quota로 다시 운영합니다.
- 코드 안전 상한은 복지로 list source별 `1회 10,000 items`, detail source별 `1회 10,000 calls` 로 둡니다.
- 복지로 detail backlog 는 더 이상 개발 계정 quota 때문에 의도적으로 남겨 두는 상태로 보지 않고, `gap fill` / `refresh` 로 full coverage 를 다시 채우는 대상으로 봅니다.

## 지금 먼저 볼 문서

- 인증 문서군 진입점: [auth-docs-index.md](./auth-docs-index.md)
- 수집 문서군 진입점: [collect-docs-index.md](./collect-docs-index.md)
- 추천 문서군 진입점: [recommendation-docs-index.md](./recommendation-docs-index.md)
- 프론트 QA 문서군 진입점: [frontend-qa-docs-index.md](./frontend-qa-docs-index.md)
- 정책 문서군 진입점: [policy-docs-index.md](./policy-docs-index.md)
- 공통 로컬 검증 문서군 진입점: [local-validation-docs-index.md](./local-validation-docs-index.md)
- 시스템 문서군 진입점: [system-docs-index.md](./system-docs-index.md)
- 히스토리 문서군 진입점: [history-docs-index.md](./history-docs-index.md)
- 인증/세션: [auth-session-revocation-current-state.md](./auth-session-revocation-current-state.md)
- 수집: [collect-current-state.md](./collect-current-state.md)
- 추천: [recommendation-current-state.md](./recommendation-current-state.md)
- 정책 정규화: [policy-normalization-current-state.md](./policy-normalization-current-state.md)
- 웹 기능 접근표: [feature-access-matrix.md](./feature-access-matrix.md)
- 다음 active track 우선순위: [policy-next-active-track-priority.md](./policy-next-active-track-priority.md)
- 로컬 closeout pending: [policy-local-closeout-pending-inventory.md](./policy-local-closeout-pending-inventory.md)
- `Gov24` blocked 상태: [policy-gov24-blocked-track-status.md](./policy-gov24-blocked-track-status.md)
- 신규 source 구조: [policy-source-onboarding-architecture.md](./policy-source-onboarding-architecture.md)
- API 응답 contract: [api-mapping.md](./api-mapping.md)

## 정책 목록 정렬 변경 이력 (2026-05-04)

### 현재 지원 sort 값 (`?sort=`)

| 값 | 설명 | UI 노출 |
|----|------|---------|
| `LATEST` | 최신순 (기본값) | ✅ |
| `VIEWS` | 조회수순 | ✅ |
| `DEADLINE` | 마감임박순 — apply_end_date 빠른 순, NULL(상시)은 맨 뒤 | ✅ |
| `NAME` | 이름순 | ❌ UI에서 제거됨, API 코드는 유지 |
| `RELEVANCE` | 관련도순 (검색 전용) | — |

### 변경 이유

- **DEADLINE 추가**: 프론트엔드 마감임박순 UI 기능 지원을 위해 백엔드 normalizeSort()와 SQL ORDER BY에 추가
- **NAME 제거 (UI)**: 프론트 정렬 Select에서 이름순 옵션 제거. 백엔드 코드(normalizeSort, SQL)는 API 호환성을 위해 그대로 유지

### 지역 우선 정렬 (region-first, B안)

지역 필터(sido/sgg)가 선택된 경우 sort 값에 따라 다르게 적용 (B안):

| sort | 지역 처리 |
|------|-----------|
| `LATEST` | 지역 일치 정책이 무조건 먼저 (strict region-first 그룹) |
| `VIEWS`, `DEADLINE` | 품질/긴급도 우선, 동점일 때만 지역 일치 정책 앞 (tiebreaker) |

프론트엔드 auto-sort: 지역 Select에서 전체 외 값 선택 시 sort를 `latest`로 자동 전환, 전체 복귀 시 `views`로 복귀.

- 관련 파일: `PolicyListService.java`, `PolicySearchService.java`, `WelfareServiceRepository.java`, `PolicyListReadCondition.java`, `PoliciesPage.jsx`
- 설계 배경: [policy-listing-sort-region-strategy.md](./history/policy/policy-listing-sort-region-strategy.md)

## 정책 카드 source 필드 변경 이력 (2026-05-04)

### 변경 전 → 후

프론트 `mapPolicySummary`의 source 폴백 순서:

| 전 | 후 |
|----|-----|
| `hostOrg → applyMethodName` | `hostOrg → sido → applyMethodName` |

### 변경 이유

- hostOrg가 null인 정책에서 카드 source 자리에 "방문, 인터넷"(applyMethodName)이 표시됨
- 상세 API에는 service_regions JOIN으로 지역명("경기")이 나오지만 목록 API에는 지역 정보가 없었음
- BOKJIRO_LOCAL 1,223개 정책이 hostOrg=null, sido_name=값 있음 → 이 정책들만 "경기" 등으로 표시됨
- YOUTH는 sido_name=null(region_code만 존재), BOKJIRO_CENTRAL은 service_regions 행 자체 없음 → 변화 없음

### 구현 위치

- `ServiceRegionRepository.java` — `findFirstSidoByServiceIds()` 추가
- `PolicySummaryResponse.java` — `sido` 필드 추가, `from()` 4-arg 오버로드 추가
- `PolicyPresentationReadService.java` — `buildSidoMap()` 추가 (목록·검색 공통 적용)
- `PoliciesPage.jsx` — `mapPolicySummary` source 폴백에 `sido` 삽입

## 정책 서비스 구조 변경 이력 (2026-05-05, main merge)

`PolicyService.java`가 역할별로 분리됨 (main branch, 2026-05-05 merge):

| 이전 | 이후 |
|------|------|
| `PolicyService.getList()` | `PolicyListService.getList()` |
| `PolicyService.getDetail()` | `PolicyDetailService.getDetail()` |
| `PolicyService.toggleBookmark()` | `PolicyBookmarkCommandService.toggleBookmark()` |
| (분산) 북마크·projection 조회 | `PolicyPresentationReadService.buildSummaryPage()` |

sido 로직(`buildSidoMap`)은 `PolicyPresentationReadService`에 통합되어 목록·검색 공통 적용.

## 진행/기록

- 진행 상황: [phase-plan.md](./phase-plan.md)
- 문제 기록: [troubleshooting-log.md](./troubleshooting-log.md)
- 전체 길찾기: [documentation-map.md](./documentation-map.md)
