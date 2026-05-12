package com.example.welfare.global.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 정책 검색/챗봇 후보 검색에서 공통으로 사용하는 토큰화 규칙을 제공한다.
 */
public final class SearchKeywordSupport {

    private static final Pattern SEARCH_TOKEN_PATTERN = Pattern.compile("[0-9A-Za-z가-힣]+");

    private SearchKeywordSupport() {
    }

    public static List<String> extractTokens(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<String> tokens = new ArrayList<>();
        Matcher matcher = SEARCH_TOKEN_PATTERN.matcher(text);
        while (matcher.find()) {
            tokens.add(matcher.group().toLowerCase(Locale.ROOT));
        }
        return List.copyOf(tokens);
    }

    public static String normalizeText(String text) {
        return String.join(" ", extractTokens(text));
    }

    public static String buildTsQuery(String text) {
        return String.join(" & ", extractTokens(text));
    }
}
