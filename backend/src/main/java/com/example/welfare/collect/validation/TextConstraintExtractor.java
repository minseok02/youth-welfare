package com.example.welfare.collect.validation;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 비정형 정책 안내 문구에서 제한/자격 조건을 규칙 기반으로 추출한다.
 * AI 없이 안정적으로 식별 가능한 패턴만 다룬다.
 *
 * 출력 포맷(서비스 태그 KEYWORD 저장용):
 * - COND_AGE_MIN_{n}
 * - COND_AGE_MAX_{n}
 * - COND_INCOME_PCT_LE_{n}
 * - COND_INCOME_WON_LE_{n}    // 만원 단위
 * - COND_RENT_WON_LE_{n}      // 만원 단위
 */
public final class TextConstraintExtractor {

    private TextConstraintExtractor() {}

    private static final Pattern AGE_RANGE =
            Pattern.compile("(?:만\\s*)?(\\d{1,2})(?:\\s*세)?\\s*(?:~|\\-|–|부터)\\s*(?:만\\s*)?(\\d{1,2})\\s*세");
    private static final Pattern AGE_MIN =
            Pattern.compile("(?:만\\s*)?(\\d{1,2})\\s*세\\s*이상");
    private static final Pattern AGE_MAX =
            Pattern.compile("(?:만\\s*)?(\\d{1,2})\\s*세\\s*이하");

    private static final Pattern INCOME_PCT =
            Pattern.compile("(?:중위소득|기준\\s*중위소득|소득|소득인정액)[^\\n\\r]{0,16}?(\\d{2,3})\\s*%\\s*이하");
    private static final Pattern INCOME_WON =
            Pattern.compile("(?:연\\s*소득|가구\\s*소득|소득인정액|소득)[^\\n\\r]{0,12}?(\\d{2,5})\\s*만원\\s*이하");
    private static final Pattern RENT_WON =
            Pattern.compile("(?:월세|임차료|임대료)[^\\n\\r]{0,10}?(\\d{1,4})\\s*만원\\s*이하");
    private static final String DATE_TOKEN =
            "(?:"
                    + "(?:19|20)\\d{2}[./-](?:0?[1-9]|1[0-2])[./-](?:0?[1-9]|[12]\\d|3[01])"
                    + "|"
                    + "(?:19|20)\\d{2}(?:0[1-9]|1[0-2])(?:0[1-9]|[12]\\d|3[01])"
                    + ")";
    private static final Pattern DATE_RANGE =
            Pattern.compile("(" + DATE_TOKEN + ")\\s*(?:~|\\-|–|부터)\\s*(" + DATE_TOKEN + ")");
    private static final Pattern DATE_UNTIL =
            Pattern.compile("(" + DATE_TOKEN + ")\\s*까지");

    public record ConstraintSummary(
            Integer minAge,
            Integer maxAge,
            Integer incomePercentMax,
            Integer incomeManWonMax,
            Integer rentManWonMax,
            LocalDate applyEndDate
    ) {}

    public static Set<String> extract(String... texts) {
        Set<String> out = new LinkedHashSet<>();
        if (texts == null) return out;

        for (String text : texts) {
            if (text == null || text.isBlank()) continue;
            String normalized = text.replace('\u00A0', ' ');

            extractAge(normalized, out);
            extractIncomePct(normalized, out);
            extractIncomeWon(normalized, out);
            extractRentWon(normalized, out);
        }
        return out;
    }

    public static ConstraintSummary summarize(String... texts) {
        Set<String> tokens = extract(texts);

        Integer minAge = tokens.stream()
                .filter(v -> v.startsWith("COND_AGE_MIN_"))
                .map(v -> v.substring("COND_AGE_MIN_".length()))
                .map(TextConstraintExtractor::safeInt)
                .filter(v -> v > 0)
                .max(Integer::compareTo)
                .orElse(null);

        Integer maxAge = tokens.stream()
                .filter(v -> v.startsWith("COND_AGE_MAX_"))
                .map(v -> v.substring("COND_AGE_MAX_".length()))
                .map(TextConstraintExtractor::safeInt)
                .filter(v -> v > 0)
                .min(Integer::compareTo)
                .orElse(null);

        Integer incomePercentMax = tokens.stream()
                .filter(v -> v.startsWith("COND_INCOME_PCT_LE_"))
                .map(v -> v.substring("COND_INCOME_PCT_LE_".length()))
                .map(TextConstraintExtractor::safeInt)
                .filter(v -> v > 0)
                .min(Integer::compareTo)
                .orElse(null);

        Integer incomeManWonMax = tokens.stream()
                .filter(v -> v.startsWith("COND_INCOME_WON_LE_"))
                .map(v -> v.substring("COND_INCOME_WON_LE_".length()))
                .map(TextConstraintExtractor::safeInt)
                .filter(v -> v > 0)
                .min(Integer::compareTo)
                .orElse(null);

        Integer rentManWonMax = tokens.stream()
                .filter(v -> v.startsWith("COND_RENT_WON_LE_"))
                .map(v -> v.substring("COND_RENT_WON_LE_".length()))
                .map(TextConstraintExtractor::safeInt)
                .filter(v -> v > 0)
                .min(Integer::compareTo)
                .orElse(null);

        LocalDate applyEndDate = extractApplyEndDate(texts);

        return new ConstraintSummary(minAge, maxAge, incomePercentMax, incomeManWonMax, rentManWonMax, applyEndDate);
    }

    public static LocalDate extractApplyEndDate(String... texts) {
        if (texts == null) return null;
        for (String text : texts) {
            if (text == null || text.isBlank()) continue;
            String normalized = text.replace('\u00A0', ' ');

            Matcher rangeMatcher = DATE_RANGE.matcher(normalized);
            if (rangeMatcher.find()) {
                LocalDate end = parseLooseDate(rangeMatcher.group(2));
                if (end != null) return end;
            }

            Matcher untilMatcher = DATE_UNTIL.matcher(normalized);
            if (untilMatcher.find()) {
                LocalDate end = parseLooseDate(untilMatcher.group(1));
                if (end != null) return end;
            }
        }
        return null;
    }

    private static void extractAge(String text, Set<String> out) {
        Matcher range = AGE_RANGE.matcher(text);
        while (range.find()) {
            int min = safeInt(range.group(1));
            int max = safeInt(range.group(2));
            if (min > 0 && max > 0) {
                out.add("COND_AGE_MIN_" + Math.min(min, max));
                out.add("COND_AGE_MAX_" + Math.max(min, max));
            }
        }

        Matcher minM = AGE_MIN.matcher(text);
        while (minM.find()) {
            int min = safeInt(minM.group(1));
            if (min > 0) out.add("COND_AGE_MIN_" + min);
        }

        Matcher maxM = AGE_MAX.matcher(text);
        while (maxM.find()) {
            int max = safeInt(maxM.group(1));
            if (max > 0) out.add("COND_AGE_MAX_" + max);
        }
    }

    private static void extractIncomePct(String text, Set<String> out) {
        Matcher m = INCOME_PCT.matcher(text);
        while (m.find()) {
            int pct = safeInt(m.group(1));
            if (pct >= 50 && pct <= 300) out.add("COND_INCOME_PCT_LE_" + pct);
        }
    }

    private static void extractIncomeWon(String text, Set<String> out) {
        Matcher m = INCOME_WON.matcher(text);
        while (m.find()) {
            int manWon = safeInt(m.group(1));
            if (manWon > 0) out.add("COND_INCOME_WON_LE_" + manWon);
        }
    }

    private static void extractRentWon(String text, Set<String> out) {
        Matcher m = RENT_WON.matcher(text);
        while (m.find()) {
            int manWon = safeInt(m.group(1));
            if (manWon > 0) out.add("COND_RENT_WON_LE_" + manWon);
        }
    }

    private static int safeInt(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (Exception ignored) {
            return -1;
        }
    }

    private static LocalDate parseLooseDate(String raw) {
        if (raw == null) return null;
        String normalized = raw.trim();
        if (normalized.contains(".") || normalized.contains("-") || normalized.contains("/")) {
            String[] parts = normalized.split("[./-]");
            if (parts.length < 3) {
                return null;
            }
            try {
                int year = Integer.parseInt(parts[0]);
                int month = Integer.parseInt(parts[1]);
                int day = Integer.parseInt(parts[2]);
                return LocalDate.of(year, month, day);
            } catch (Exception ignored) {
                return null;
            }
        }

        String digits = normalized.replaceAll("[^0-9]", "");
        if (digits.length() != 8) {
            return null;
        }
        try {
            return LocalDate.parse(digits, DateTimeFormatter.ofPattern("yyyyMMdd"));
        } catch (Exception ignored) {
            return null;
        }
    }
}
