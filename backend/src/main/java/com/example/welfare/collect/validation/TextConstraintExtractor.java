package com.example.welfare.collect.validation;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
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
            Pattern.compile("(?:만\\s*)?(\\d{1,2})(?:\\s*세)?\\s*(?:~|～|\\-|–|부터)\\s*(?:만\\s*)?(\\d{1,2})\\s*세");
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
    private static final String MONTH_TOKEN = "(?:0?[1-9]|1[0-2])";
    private static final String DAY_TOKEN = "(?:3[01]|[12]\\d|0?[1-9])";
    private static final String FULL_SEPARATED_DATE =
            "(?:19|20)\\d{2}[./-]" + MONTH_TOKEN + "[./-]" + DAY_TOKEN;
    private static final String DATE_TOKEN =
            "(?:"
                    + FULL_SEPARATED_DATE
                    + "|"
                    + "(?:19|20)\\d{2}(?:0[1-9]|1[0-2])(?:3[01]|[12]\\d|0[1-9])"
                    + ")";
    private static final String PARTIAL_DATE_TOKEN =
            "(?:" + MONTH_TOKEN + "[./-]" + DAY_TOKEN + "|" + DAY_TOKEN + ")";
    private static final Pattern DATE_RANGE =
            Pattern.compile("(" + DATE_TOKEN + ")\\.?\\s*(?:~|\\-|–|부터)\\s*(" + DATE_TOKEN + ")");
    private static final Pattern DATE_RANGE_PARTIAL_END =
            Pattern.compile("(" + FULL_SEPARATED_DATE + ")\\.?\\s*(?:~|\\-|–|부터)\\s*(" + PARTIAL_DATE_TOKEN + ")\\.?");
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
        AgeBounds ageBounds = summarizeAgeBounds(texts);

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

        return new ConstraintSummary(ageBounds.minAge(), ageBounds.maxAge(),
                incomePercentMax, incomeManWonMax, rentManWonMax, applyEndDate);
    }

    public static LocalDate extractApplyEndDate(String... texts) {
        if (texts == null) return null;
        LocalDate latest = null;
        for (String text : texts) {
            if (text == null || text.isBlank()) continue;
            String normalized = text.replace('\u00A0', ' ');

            Matcher rangeMatcher = DATE_RANGE.matcher(normalized);
            while (rangeMatcher.find()) {
                LocalDate end = parseLooseDate(rangeMatcher.group(2));
                latest = max(latest, end);
            }

            Matcher partialRangeMatcher = DATE_RANGE_PARTIAL_END.matcher(normalized);
            while (partialRangeMatcher.find()) {
                LocalDate start = parseLooseDate(partialRangeMatcher.group(1));
                LocalDate end = parsePartialEndDate(start, partialRangeMatcher.group(2));
                latest = max(latest, end);
            }

            Matcher untilMatcher = DATE_UNTIL.matcher(normalized);
            while (untilMatcher.find()) {
                LocalDate end = parseLooseDate(untilMatcher.group(1));
                latest = max(latest, end);
            }
        }
        return latest;
    }

    private static void extractAge(String text, Set<String> out) {
        Matcher range = AGE_RANGE.matcher(text);
        while (range.find()) {
            AgeRange ageRange = toAgeRange(range.group(1), range.group(2));
            if (ageRange != null) {
                out.add("COND_AGE_MIN_" + ageRange.min());
                out.add("COND_AGE_MAX_" + ageRange.max());
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

    private static AgeBounds summarizeAgeBounds(String... texts) {
        if (texts == null) {
            return new AgeBounds(null, null);
        }

        List<AgeRange> ranges = new ArrayList<>();
        List<Integer> mins = new ArrayList<>();
        List<Integer> maxes = new ArrayList<>();

        for (String text : texts) {
            if (text == null || text.isBlank()) {
                continue;
            }
            String normalized = text.replace('\u00A0', ' ');
            collectAgeRanges(normalized, ranges);
            collectAgeMins(normalized, mins);
            collectAgeMaxes(normalized, maxes);
        }

        if (!ranges.isEmpty()) {
            AgeBounds selected = selectPrimaryAgeRange(ranges);
            Integer minAge = selected.minAge();
            Integer maxAge = selected.maxAge();

            Integer tightenedMin = mins.stream().filter(v -> v > 0).max(Integer::compareTo).orElse(null);
            if (tightenedMin != null && (maxAge == null || tightenedMin <= maxAge)) {
                minAge = minAge == null ? tightenedMin : Math.max(minAge, tightenedMin);
            }

            Integer tightenedMax = maxes.stream().filter(v -> v > 0).min(Integer::compareTo).orElse(null);
            if (tightenedMax != null && (minAge == null || tightenedMax >= minAge)) {
                maxAge = maxAge == null ? tightenedMax : Math.min(maxAge, tightenedMax);
            }

            if (minAge != null && maxAge != null && minAge > maxAge) {
                return selected;
            }
            return new AgeBounds(minAge, maxAge);
        }

        Integer minAge = mins.stream().filter(v -> v > 0).max(Integer::compareTo).orElse(null);
        Integer maxAge = maxes.stream().filter(v -> v > 0).min(Integer::compareTo).orElse(null);
        if (minAge != null && maxAge != null && minAge > maxAge) {
            return new AgeBounds(null, null);
        }
        return new AgeBounds(minAge, maxAge);
    }

    private static void collectAgeRanges(String text, List<AgeRange> ranges) {
        Matcher range = AGE_RANGE.matcher(text);
        while (range.find()) {
            AgeRange ageRange = toAgeRange(range.group(1), range.group(2));
            if (ageRange != null) {
                ranges.add(ageRange);
            }
        }
    }

    private static void collectAgeMins(String text, List<Integer> mins) {
        Matcher minMatcher = AGE_MIN.matcher(text);
        while (minMatcher.find()) {
            int min = safeInt(minMatcher.group(1));
            if (min > 0) {
                mins.add(min);
            }
        }
    }

    private static void collectAgeMaxes(String text, List<Integer> maxes) {
        Matcher maxMatcher = AGE_MAX.matcher(text);
        while (maxMatcher.find()) {
            int max = safeInt(maxMatcher.group(1));
            if (max > 0) {
                maxes.add(max);
            }
        }
    }

    private static AgeBounds selectPrimaryAgeRange(List<AgeRange> ranges) {
        if (ranges.isEmpty()) {
            return new AgeBounds(null, null);
        }
        int intersectionMin = ranges.stream().mapToInt(AgeRange::min).max().orElse(-1);
        int intersectionMax = ranges.stream().mapToInt(AgeRange::max).min().orElse(-1);
        if (intersectionMin > 0 && intersectionMax > 0 && intersectionMin <= intersectionMax) {
            return new AgeBounds(intersectionMin, intersectionMax);
        }
        AgeRange first = ranges.get(0);
        return new AgeBounds(first.min(), first.max());
    }

    private static AgeRange toAgeRange(String rawMin, String rawMax) {
        int min = safeInt(rawMin);
        int max = safeInt(rawMax);
        if (min <= 0 || max <= 0) {
            return null;
        }
        return new AgeRange(Math.min(min, max), Math.max(min, max));
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

    private static LocalDate parsePartialEndDate(LocalDate start, String rawEnd) {
        if (start == null || rawEnd == null) return null;
        String normalized = rawEnd.trim().replaceAll("\\.+$", "");
        try {
            LocalDate end;
            if (normalized.contains(".") || normalized.contains("-") || normalized.contains("/")) {
                String[] parts = normalized.split("[./-]");
                if (parts.length != 2) {
                    return null;
                }
                end = LocalDate.of(start.getYear(), Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
            } else {
                end = LocalDate.of(start.getYear(), start.getMonthValue(), Integer.parseInt(normalized));
            }
            return end.isBefore(start) ? end.plusYears(1) : end;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static LocalDate max(LocalDate current, LocalDate candidate) {
        if (candidate == null) return current;
        if (current == null || candidate.isAfter(current)) return candidate;
        return current;
    }

    private record AgeRange(int min, int max) {}

    private record AgeBounds(Integer minAge, Integer maxAge) {}
}
