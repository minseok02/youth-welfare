# 정책 카드 source 필드 — sido 추가 결정 (2026-05-04)

## 문제

목록 API 카드에서 주관기관(hostOrg)이 없는 정책의 source 자리에
"방문, 인터넷"(applyMethodName)이 표시됨.

상세 페이지에서는 "경기도"처럼 지역명이 정상적으로 나와 사용자에게 혼란을 줌.

## 원인

| API | 지역 정보 출처 |
|-----|----------------|
| 목록 `GET /api/policies` | `welfare_services` 단일 테이블 → 지역 없음 |
| 상세 `GET /api/policies/{id}` | `service_regions` JOIN → 지역 있음 |

프론트 `mapPolicySummary`의 source 폴백:
- 변경 전: `hostOrg → applyMethodName`
- 변경 후: `hostOrg → sido → applyMethodName`

## DB 조사 결과 (2026-05-04 기준)

```
전체 정책:                 3,705개
다중 sido 정책:             0개   ← 처음 우려했던 케이스 없음
sido_name 채워진 service_regions: 1,223행
sido_name NULL인 service_regions: 17,031행
hostOrg NULL인 정책:         1,223개
```

source_type별 sido_name 상태:

| source_type | sido_name 채워짐 | sido_name NULL |
|-------------|-----------------|----------------|
| YOUTH | 0 | 17,031 |
| BOKJIRO_LOCAL | 1,223 | 0 |
| BOKJIRO_CENTRAL | — | — (service_regions 행 없음) |

→ 실효 범위: **BOKJIRO_LOCAL 1,223개**만 "방문, 인터넷" → 지역명으로 변경됨.
YOUTH, BOKJIRO_CENTRAL은 sido=null이라 기존 폴백 동작 유지.

## 다중 지역 처리

처음에는 service_regions 다중 행이 문제가 될 것으로 예상했으나,
`sido_name` 기준 다중 sido 정책은 0개임.
YOUTH 온통청년의 multi-row는 region_code(시군구 코드)가 여러 개지만
sido_name은 전부 NULL이라 이 변경에 영향 없음.

## 구현

- `ServiceRegionRepository.findFirstSidoByServiceIds()` — sido_name이 있는 행만 service_id당 하나(MIN) 반환
- `PolicySummaryResponse.sido` 필드 추가, `from()` 4-arg 오버로드
- `PolicyPresentationReadService.buildSidoMap()` — 페이지 단위 배치 조회 (쿼리 1회)
  - `PolicyListService`, `PolicySearchService` 양쪽이 `buildSummaryPage()`를 통해 공통 적용
  - 최초에는 `PolicyService`/`PolicySearchService` 각각에 구현했으나, main merge 시 `PolicyService` → `PolicyListService`/`PolicyPresentationReadService` 분리가 적용되어 `PolicyPresentationReadService`로 통합
- `PoliciesPage.jsx mapPolicySummary` — `hostOrg || sido || applyMethodName`

## 검토했으나 채택하지 않은 방안

### A안 — applyMethodName 폴백 제거
hostOrg도 sido도 없으면 source 자리가 비어버림.
전국 정책(BOKJIRO_CENTRAL, YOUTH)이 source 없이 렌더링됨 → 기각.

### C안 — 현행 유지
데이터가 있는데 표시하지 않는 것 → 기각.
