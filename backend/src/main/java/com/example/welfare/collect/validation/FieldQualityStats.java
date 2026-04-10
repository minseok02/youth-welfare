package com.example.welfare.collect.validation;

import lombok.Getter;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 공공API 1회 수집에서 필드별 null/빈값 빈도를 집계하는 통계 클래스.
 * <p>
 * 사용 방법:
 * <pre>
 *   FieldQualityStats stats = new FieldQualityStats("YOUTH", items.size());
 *   stats.record("title",    item.getPolyBizSjnm());
 *   stats.record("minAge",   item.getMinAge());
 *   ...
 *   log.info(stats.summary());
 * </pre>
 * 수집이 끝난 후 {@link #summary()}를 호출하면 아래 형식의 로그를 얻는다:
 * <pre>
 *   [FieldQuality][YOUTH] total=1500
 *     title       : 0 / 1500 (0.0%)
 *     minAge      : 423 / 1500 (28.2%)
 *     ...
 * </pre>
 */
public class FieldQualityStats {

    private final String source;
    @Getter
    private final int total;

    /** 필드 이름 → 빈/null 카운트 (선언 순서 유지) */
    private final Map<String, AtomicInteger> counters = new LinkedHashMap<>();

    public FieldQualityStats(String source, int total) {
        this.source = source;
        this.total = total;
    }

    /**
     * 문자열 필드 기록. null 또는 blank면 카운트 증가.
     */
    public void record(String fieldName, String value) {
        counter(fieldName).incrementAndGet();   // 등록
        if (value == null || value.isBlank()) {
            counter(fieldName + "_BLANK").incrementAndGet();
        }
    }

    /**
     * 숫자/객체 필드 기록. null이면 카운트 증가.
     */
    public void record(String fieldName, Object value) {
        counter(fieldName).incrementAndGet();
        if (value == null) {
            counter(fieldName + "_NULL").incrementAndGet();
        }
    }

    /**
     * 파싱 실패(날짜 등) 카운트 직접 증가.
     */
    public void recordParseFail(String fieldName) {
        counter(fieldName + "_PARSE_FAIL").incrementAndGet();
    }

    public String summary() {
        if (total == 0) return "[FieldQuality][" + source + "] total=0 (no data)";

        StringBuilder sb = new StringBuilder();
        sb.append("[FieldQuality][").append(source).append("] total=").append(total).append('\n');

        // _BLANK / _NULL / _PARSE_FAIL 접미사 키만 출력 (메인 카운터는 숨김)
        counters.forEach((key, cnt) -> {
            if (key.endsWith("_BLANK") || key.endsWith("_NULL") || key.endsWith("_PARSE_FAIL")) {
                int c = cnt.get();
                double pct = (double) c / total * 100;
                String baseField = key.replaceAll("_(BLANK|NULL|PARSE_FAIL)$", "");
                String suffix    = key.substring(baseField.length() + 1);
                sb.append(String.format("  %-30s : %d / %d (%.1f%%)%n",
                        baseField + " [" + suffix + "]", c, total, pct));
            }
        });

        return sb.toString().stripTrailing();
    }

    private AtomicInteger counter(String key) {
        return counters.computeIfAbsent(key, k -> new AtomicInteger(0));
    }
}
