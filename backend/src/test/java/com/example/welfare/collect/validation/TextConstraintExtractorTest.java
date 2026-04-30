package com.example.welfare.collect.validation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TextConstraintExtractorTest {

    @Test
    @DisplayName("첫 번째 나이에 세가 없는 복지로 범위 문구도 age range로 추출한다")
    void extractAgeRangeWithoutFirstSe() {
        Set<String> tokens = TextConstraintExtractor.extract(
                "담양군에 주소를 두고 있는 만 20 ~ 49세 여성"
        );

        assertThat(tokens).contains("COND_AGE_MIN_20", "COND_AGE_MAX_49");
    }

    @Test
    @DisplayName("복지로 detail의 이상~이하 조합 문구도 min/max age로 함께 추출한다")
    void extractAgeFromMinMaxPhrase() {
        Set<String> tokens = TextConstraintExtractor.extract(
                "만 19세 이상 ~ 34세 이하이면서 연소득 3,500만원 이하인 대학생, 청년에게 지원합니다."
        );

        assertThat(tokens).contains("COND_AGE_MIN_19", "COND_AGE_MAX_34");
    }

    @Test
    @DisplayName("기존 세~세 범위 문구도 계속 age range로 추출한다")
    void extractStandardAgeRange() {
        Set<String> tokens = TextConstraintExtractor.extract(
                "경산시에 주소가 있거나 관내 대학에 재학 중인 15세~39세 청년"
        );

        assertThat(tokens).contains("COND_AGE_MIN_15", "COND_AGE_MAX_39");
    }
}
