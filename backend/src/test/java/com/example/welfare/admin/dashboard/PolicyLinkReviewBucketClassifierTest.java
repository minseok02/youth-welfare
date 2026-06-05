package com.example.welfare.admin.dashboard;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyLinkReviewBucketClassifierTest {

    @Test
    @DisplayName("급부형 제목 패턴을 benefit bucket으로 분류한다")
    void classifiesBenefitSupportTitles() {
        assertThat(PolicyLinkReviewBucketClassifier.classify("출산가정 산후조리비용 지원"))
                .isEqualTo("benefit_support");
        assertThat(PolicyLinkReviewBucketClassifier.classify("임신축하금 지원사업"))
                .isEqualTo("benefit_support");
        assertThat(PolicyLinkReviewBucketClassifier.classify("서산시 군복무 청년 상해보험 가입"))
                .isEqualTo("benefit_support");
    }

    @Test
    @DisplayName("모집형과 프로그램형 제목은 기존 bucket을 유지한다")
    void keepsExistingAnnouncementAndProgramBuckets() {
        assertThat(PolicyLinkReviewBucketClassifier.classify("청년창업 거주지원시설 입주자 1차 모집"))
                .isEqualTo("announcement_recruitment");
        assertThat(PolicyLinkReviewBucketClassifier.classify("청년미래플랜아카데미"))
                .isEqualTo("program_event");
    }
}
