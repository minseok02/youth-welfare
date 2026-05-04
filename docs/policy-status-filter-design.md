# 정책 상태 필터 설계 (statusFilter)

## 배경

온통청년 API에서 수집된 일부 정책은 DB의 `status` 컬럼이 `ACTIVE`로 저장돼 있지만, `apply_end_date`가 이미 지난 경우가 있다.

원인: 온통청년 API는 정책이 만료돼도 status를 `CLOSED`로 업데이트하지 않는 경우가 있다. 반면 복지로는 수집 시점에 status가 이미 정리된 상태로 내려온다.

이 때문에 기존 `includeClosed: boolean` 방식으로는 "신청 마감된 정책 숨기기"를 정확히 처리할 수 없었다.
→ 2026-05-03 `statusFilter: string` 방식으로 교체했다.

## statusFilter 값 정의

| 값 | 의미 | SQL 조건 |
|----|------|----------|
| `ACTIVE_ONLY` (기본값) | 신청가능·예정 + 마감일 미도래 | `status IN (ACTIVE, UPCOMING) AND (apply_end_date IS NULL OR apply_end_date >= CURDATE())` |
| `EXPIRED_ONLY` | 종료 또는 마감일 지남 | `status = CLOSED OR apply_end_date < CURDATE()` |
| `ALL` | 모든 상태 | `status IN (ACTIVE, UPCOMING, CLOSED)` |

`status` 파라미터가 명시되면 `statusFilter`를 무시하고 단일 status 직접 매칭한다.

## 프론트엔드 UI

`PoliciesPage.jsx`의 필터에서 RadioGroup 3개 옵션으로 노출:

| UI 표시 | API 파라미터 |
|---------|-------------|
| 신청가능 | `ACTIVE_ONLY` |
| 마감 | `EXPIRED_ONLY` |
| 전부표기 | `ALL` |

기본값: "신청가능" (URL에 포함되지 않음).

## 영향 범위

### 백엔드

- `WelfareServiceRepository.java`
  - `searchByKeywordWithFiltersNoRegion` / `WithSido` / `WithSidoSgg` (native SQL): `statusFilter` 조건 3개
  - `findListWithFilters` (JPQL): `statusFilter` 조건
- `PolicyService.normalizeStatusFilter()`
- `PolicySearchService.normalizeStatusFilter()`
- `PolicyController`: 두 엔드포인트 모두 `statusFilter` 파라미터 수신
- `PolicySearchLogCommand.statusFilter` (로그 기록용)
- `PolicySearchLogService`: `searchLog.includeClosed` 필드는 `statusFilter` 값으로 역계산해서 저장

### 프론트엔드

- `PoliciesPage.jsx`: `statusFilter` state + `STATUS_FILTER_MAP` 변환 + RadioGroup UI
- `PolicyDetailPage.jsx`: `formatStatusLabel()` — 뱃지 표시 시 `applyEndDate`도 함께 체크

## 정책 상세 뱃지 수정

`PolicyDetailPage.formatStatusLabel(status, applyEndDate)`:
- DB `status = CLOSED` → "종료"
- `applyEndDate < 오늘` → "종료" (status가 ACTIVE여도)
- `status = ACTIVE` → "진행중"
- `status = UPCOMING` → "예정"

이 수정으로 온통청년 정책이 "진행중"과 "종료" 뱃지를 동시에 표시하던 문제가 해결됐다.

## 한계

- `status` 업데이트 배치(`StatusUpdateService`)는 온통청년 정책의 `apply_end_date` 경과 시 `status`를 `CLOSED`로 변경해주지 않는다.
  현재는 쿼리 레벨에서 `applyEndDate < CURDATE()` 조건으로 처리한다.
- 온통청년 API가 `apply_end_date` 필드를 누락하거나 형식이 다를 경우 ACTIVE_ONLY 필터에서 노출될 수 있다.
