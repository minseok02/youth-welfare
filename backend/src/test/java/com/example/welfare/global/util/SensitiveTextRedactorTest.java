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
}
