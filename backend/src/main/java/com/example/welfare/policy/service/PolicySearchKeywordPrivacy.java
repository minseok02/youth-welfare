package com.example.welfare.policy.service;

import java.util.regex.Pattern;

final class PolicySearchKeywordPrivacy {

    private static final Pattern EMAIL_KEYWORD_PATTERN =
            Pattern.compile("[A-Z0-9._%+-]{1,64}@[A-Z0-9.-]+\\.[A-Z]{2,}", Pattern.CASE_INSENSITIVE);
    private static final Pattern PHONE_KEYWORD_PATTERN =
            Pattern.compile("\\b(?:\\+?82[-.\\s]?)?0?1[016789][-.\\s]?\\d{3,4}[-.\\s]?\\d{4}\\b");
    private static final Pattern RESIDENT_ID_KEYWORD_PATTERN =
            Pattern.compile("\\b\\d{6}[-\\s]?[1-8]\\d{6}\\b");
    private static final Pattern LONG_NUMBER_KEYWORD_PATTERN =
            Pattern.compile("\\b\\d{4,6}[-\\s]\\d{2,6}[-\\s]\\d{2,8}\\b");

    private PolicySearchKeywordPrivacy() {
    }

    static boolean containsSensitiveIdentifier(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return false;
        }
        return EMAIL_KEYWORD_PATTERN.matcher(keyword).find()
                || PHONE_KEYWORD_PATTERN.matcher(keyword).find()
                || RESIDENT_ID_KEYWORD_PATTERN.matcher(keyword).find()
                || LONG_NUMBER_KEYWORD_PATTERN.matcher(keyword).find();
    }
}
