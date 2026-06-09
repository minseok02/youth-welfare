package com.example.welfare.global.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SensitiveTextRedactorTest {

    @Test
    @DisplayName("라벨형 이름 주소 조직명 변형을 공통적으로 마스킹한다")
    void redactsLabeledNameAddressAndOrganizationVariants() {
        String source = """
                이름: 김민수
                집 주소= 인천광역시 중구 은하수로 10
                학교 서울대학교
                회사: 청년컴퍼니
                소속 청년정책랩
                """;

        String redacted = SensitiveTextRedactor.redactDirectIdentifiers(source);

        assertThat(redacted)
                .contains("[REDACTED_NAME]")
                .contains("[REDACTED_ADDRESS]")
                .contains("[REDACTED_ORG]");
        assertThat(redacted)
                .doesNotContain("김민수")
                .doesNotContain("인천광역시 중구 은하수로 10")
                .doesNotContain("서울대학교")
                .doesNotContain("청년컴퍼니")
                .doesNotContain("청년정책랩");
    }

    @Test
    @DisplayName("영문 라벨형 표현도 마스킹한다")
    void redactsEnglishLabeledVariants() {
        String source = "name John address 123 Seoul-ro company Open Youth Lab school Korea University";

        String redacted = SensitiveTextRedactor.redactDirectIdentifiers(source);

        assertThat(redacted)
                .contains("[REDACTED_NAME]")
                .contains("[REDACTED_ADDRESS]")
                .contains("[REDACTED_ORG]");
        assertThat(redacted)
                .doesNotContain("John")
                .doesNotContain("123 Seoul-ro")
                .doesNotContain("Open Youth Lab")
                .doesNotContain("Korea University");
    }

    @Test
    @DisplayName("한국어 날짜와 숫자형 날짜, 점 구분 연락처, 외국인등록번호도 마스킹한다")
    void redactsKoreanAndCompactIdentifierVariants() {
        String source = "생일은 2001년 4월 30일, 다른 표기는 20010430, 연락처는 010.1234.5678로 주세요, 해외 표기는 +82 10 9876 5432, 외국인등록번호는 900101-5123456";

        String redacted = SensitiveTextRedactor.redactDirectIdentifiers(source);

        assertThat(redacted)
                .contains("[REDACTED_BIRTH_DATE]")
                .contains("[REDACTED_PHONE]")
                .contains("[REDACTED_RRN]");
        assertThat(redacted)
                .doesNotContain("2001년 4월 30일")
                .doesNotContain("20010430")
                .doesNotContain("010.1234.5678")
                .doesNotContain("+82 10 9876 5432")
                .doesNotContain("900101-5123456");
    }
}
