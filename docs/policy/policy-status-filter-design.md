# 정책 상태 필터 설계 (statusFilter)

## 배경

온통청년 API에서 수집된 일부 정책은 DB의 `status` 컬럼이 `ACTIVE`로 저장돼 있지만, `apply_end_date`가 이미 지난 경우가 있다.

원인: 온통청년 API는 정책이 만료돼도 status를 `CLOSED`로 업데이트하지 않는 경우가 있다. 반면 복지로는 수집 시점에 status가 이미 정리된 상태로 내려온다.

이 때문에 기존 `includeClosed: boolean` 방식으로는 "신청 마감된 정책 숨기기"를 정확히 처리할 수 없었다.
→ 2026-05-03 `statusFilter: string` 방식으로 교체했다.

## statusFilter 값 정의

| 값 | 의미 | SQL 조건 |
|----|------|----------|
| `ACTIVE_ONLY` (기본값) | 신청가능·예정 + 마감일 미도래 | `status IN ('ACTIVE', 'UPCOMING') AND (apply_end_date IS NULL OR apply_end_date >= CURRENT_DATE)` |
| `EXPIRED_ONLY` | 종료 또는 마감일 지남 | `status = 'CLOSED' OR (apply_end_date IS NOT NULL AND apply_end_date < CURRENT_DATE)` |
| `ALL` | 모든 상태 | `status IN ('ACTIVE', 'UPCOMING', 'CLOSED')` |

위 SQL은 PostgreSQL의 상태 조건만 요약한 것이다. 상태가 `ACTIVE` 또는 `UPCOMING`이면 마감일이 오늘인 정책도 `ACTIVE_ONLY`에 포함되며, `CURRENT_DATE`는 DB 세션의 날짜를 따른다.

`status`가 유효한 단일 상태(`ACTIVE`, `UPCOMING`, `CLOSED`)로 지정되면 `statusFilter`와 마감일 조건 대신 해당 status로 직접 매칭한다.

## 프론트엔드 UI

`PoliciesPage.jsx`의 필터에서 다음 3개 옵션으로 노출:

| UI 표시 | API 파라미터 |
|---------|-------------|
| 신청가능 | `ACTIVE_ONLY` |
| 마감 | `EXPIRED_ONLY` |
| 전부표기 | `ALL` |

저장된 필터 설정의 `includeExpired`가 참이면 기본값은 "전부표기", 그 외에는 "신청가능"이다. URL에는 이 기본값과 다른 선택만 기록한다. API 자체의 `statusFilter` 미지정 기본값은 `ACTIVE_ONLY`다.

## 영향 범위

### 백엔드

- [`PolicyListService.normalizeStatusFilter()`](../../backend/src/main/java/com/example/welfare/policy/service/PolicyListService.java): 목록 필터 정규화
- [`PolicySearchService.normalizeStatusFilter()`](../../backend/src/main/java/com/example/welfare/policy/service/PolicySearchService.java): 검색 필터 정규화
- [`WelfareServiceReadRepositoryImpl`](../../backend/src/main/java/com/example/welfare/policy/repository/WelfareServiceReadRepositoryImpl.java)
  - `findList()`는 [`WelfareServiceRepository.findListWithFilters()`](../../backend/src/main/java/com/example/welfare/policy/repository/WelfareServiceRepository.java)의 native SQL로 위임한다. 조회·건수 쿼리에 같은 상태 조건을 적용한다.
  - `search()`는 [`WelfareServiceSearchRepositoryImpl.search()`](../../backend/src/main/java/com/example/welfare/policy/repository/WelfareServiceSearchRepositoryImpl.java)로 위임한다. 검색 상태 조건은 `STATUS_FILTER_SQL`에 있다.
- [`PolicyController`](../../backend/src/main/java/com/example/welfare/policy/controller/PolicyController.java): `GET /api/policies`는 query parameter, `POST /api/policies/search`는 JSON body로 `statusFilter` 수신
- `PolicySearchLogCommand.statusFilter` (로그 기록용)
- `PolicySearchLogService`: `searchLog.includeClosed` 필드는 `statusFilter` 값으로 역계산해서 저장

### 프론트엔드

- [`PoliciesPage.jsx`](../../frontend/src/pages/PoliciesPage.jsx): `statusFilter` state와 필터 UI. [`policyFilterOptions.js`](../../frontend/src/lib/policyFilterOptions.js)의 `POLICY_STATUS_FILTER_MAP`으로 API 값 변환
- [`PolicyDetailPage.jsx`](../../frontend/src/pages/PolicyDetailPage.jsx): [`policyDisplay.js`](../../frontend/src/lib/policyDisplay.js)의 `formatPolicyStatusLabel()`을 `formatStatusLabel`로 가져와 뱃지 표시 시 `applyEndDate`도 함께 체크

## 정책 상세 뱃지 수정

`PolicyDetailPage.formatStatusLabel(status, applyEndDate)`:

- DB `status = CLOSED` → "종료"
- `applyEndDate < 오늘` → "종료" (status가 ACTIVE여도)
- `status = ACTIVE` → "진행중"
- `status = UPCOMING` → "예정"

이 수정으로 온통청년 정책이 "진행중"과 "종료" 뱃지를 동시에 표시하던 문제가 해결됐다.

## 한계

- [`StatusUpdateService`](../../backend/src/main/java/com/example/welfare/collect/service/StatusUpdateService.java)는 source 구분 없이 `ACTIVE` 정책의 사업 종료일(`endDate`)이 지났으면 `CLOSED`로 변경한다. 사업 종료일이 없을 때만 신청 마감일(`applyEndDate`)을 대신 확인한다.
  목록·검색은 배치의 상태 변경 여부와 별개로 `apply_end_date < CURRENT_DATE`를 검사하므로, 사업은 진행 중이지만 신청은 마감된 정책이나 배치 반영 전 정책도 `EXPIRED_ONLY`로 조회할 수 있다.
- 온통청년 API가 `apply_end_date` 필드를 누락하거나 형식이 다를 경우 ACTIVE_ONLY 필터에서 노출될 수 있다.
