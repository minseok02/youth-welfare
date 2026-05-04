# 정책 목록 정렬 × 지역 우선 전략

## 결정 (2026-05-04)

**채택: B안** — 최신순만 strict region-first, 나머지는 품질 우선 + tiebreaker

## 배경

지역 필터를 선택하면 해당 지역 정책이 먼저 보이도록 region-first 정렬을 구현했는데,
조회수순에서 문제가 발견됨:
- 조회수 1인 지역 정책이 조회수 1000인 전국 정책보다 앞에 표시됨
- 사용자가 조회수순을 선택했음에도 정렬이 사실상 의미 없어짐

## 검토한 방안

### A안 — 모든 정렬에서 region = tiebreaker
```
ORDER BY 품질(views/latest/deadline) → 지역일치(0/1) → created_at
```
- 조회수/최신/마감임박 모두 품질 우선, 동점일 때만 지역 정책 앞
- 일관성 있지만 "지역 정책 먼저 보기" 체감 약함

### B안 — 정렬에 따라 다르게 ✅ 채택
```
최신순:        지역일치(0/1) → created_at DESC       (strict region-first)
조회수/마감임박: 품질 → 지역일치(tiebreaker) → created_at
```
- 최신순: "내 지역 최신 소식"처럼 지역 그룹이 자연스러움
- 조회수/마감임박: 품질·긴급도가 중요하므로 전국 정책과 공정하게 경쟁
- A안으로 전환하려면 ORDER BY에서 1순위 CASE WHEN :sort = 'LATEST' ... 블록만 제거하면 됨

## 구현 위치

`WelfareServiceRepository.java` — `findListWithFilters`, `searchByKeywordWithFiltersWithSido`, `searchByKeywordWithFiltersWithSidoSgg` 의 ORDER BY

```sql
-- LATEST일 때만 지역 1순위 그룹
CASE
    WHEN :sort = 'LATEST' AND [지역일치조건] THEN 0
    WHEN :sort = 'LATEST' THEN 1
    ELSE 0
END ASC,
-- 품질 정렬 (VIEWS, DEADLINE)
...
-- 공통 tiebreaker
CASE WHEN [지역일치조건] THEN 0 ELSE 1 END ASC,
```
