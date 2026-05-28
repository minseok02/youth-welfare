package com.example.welfare.collect.validation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
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

    @Test
    @DisplayName("두 번째 bound 앞에도 만이 붙는 복지로 범위 문구를 age range로 추출한다")
    void extractAgeRangeWithSecondBoundMan() {
        Set<String> tokens = TextConstraintExtractor.extract(
                "가입연령 : 신청 당시 만 15세~만 40세"
        );

        assertThat(tokens).contains("COND_AGE_MIN_15", "COND_AGE_MAX_40");
    }

    @Test
    @DisplayName("full-width range와 참조 사업 연령대가 함께 있어도 primary age range를 sane하게 요약한다")
    void summarizePrefersPrimaryRangeWhenReferencedRangeWouldInvertBounds() {
        TextConstraintExtractor.ConstraintSummary summary = TextConstraintExtractor.summarize(
                "경제적으로 어려움을 겪고 있는 인천 청년(35~39세) 주거비 부담 완화 * 국가사업(청년월세 한시 특별지원(19~34세))과 동일한 사업기준 적용, 대상연령만 확대",
                "ㅇ (나이) 35세～39세이하 (2025년 신청의 경우 1985~1989년)"
        );

        assertThat(summary.minAge()).isEqualTo(35);
        assertThat(summary.maxAge()).isEqualTo(39);
    }

    @Test
    @DisplayName("한 자리 월/일이 포함된 복지로 신청 마감 문구도 apply end date로 추출한다")
    void extractApplyEndDateFromSingleDigitMonthDay() {
        LocalDate applyEndDate = TextConstraintExtractor.extractApplyEndDate(
                "온라인 신청, 2026.5.1 까지 접수"
        );

        assertThat(applyEndDate).isEqualTo(LocalDate.of(2026, 5, 1));
    }

    @Test
    @DisplayName("복수 접수기간의 종료일이 월/일 또는 일만 쓰인 경우 가장 늦은 종료일로 추출한다")
    void extractLatestApplyEndDateFromPartialRangeEnds() {
        LocalDate applyEndDate = TextConstraintExtractor.extractApplyEndDate(
                """
                1단계 접수기간 : 2024.12.20.~30.
                2단계 접수기간 : 2025.4.1.~4.10.
                3단계 접수기간 : 2025.7.21.~7.30.
                """
        );

        assertThat(applyEndDate).isEqualTo(LocalDate.of(2025, 7, 30));
    }
}
