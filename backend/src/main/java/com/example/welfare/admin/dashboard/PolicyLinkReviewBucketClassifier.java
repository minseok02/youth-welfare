package com.example.welfare.admin.dashboard;

import java.util.regex.Pattern;

public final class PolicyLinkReviewBucketClassifier {

    private static final Pattern ANNOUNCEMENT_RECRUITMENT_PATTERN =
            Pattern.compile("(모집|공고|선발|접수|신청자|참여자|참가자|추가모집|수강생)");

    private static final Pattern BENEFIT_SUPPORT_PATTERN =
            Pattern.compile("(지원금|지원사업|지원 프로그램|수당|장학금|이자 지원|응시료|바우처|급여|보조금|축하금|조리비(?:용)? 지원|보험(?: 가입| 지원)?)");

    private static final Pattern PROGRAM_EVENT_PATTERN =
            Pattern.compile("(프로그램|교육|아카데미|캠프|멘토링|기획단|탐방|실험실|클래스|강좌)");

    private static final Pattern EVENT_CULTURE_PATTERN =
            Pattern.compile("(대회|축제|행사|공연|전시|페스티벌)");

    private PolicyLinkReviewBucketClassifier() {
    }

    public static String classify(String title) {
        String normalizedTitle = title == null ? "" : title;
        if (ANNOUNCEMENT_RECRUITMENT_PATTERN.matcher(normalizedTitle).find()) {
            return "announcement_recruitment";
        }
        if (BENEFIT_SUPPORT_PATTERN.matcher(normalizedTitle).find()) {
            return "benefit_support";
        }
        if (PROGRAM_EVENT_PATTERN.matcher(normalizedTitle).find()) {
            return "program_event";
        }
        if (EVENT_CULTURE_PATTERN.matcher(normalizedTitle).find()) {
            return "event_culture";
        }
        return "other";
    }
}
