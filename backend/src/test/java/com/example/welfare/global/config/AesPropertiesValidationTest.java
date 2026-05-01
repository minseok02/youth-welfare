package com.example.welfare.global.config;

import com.example.welfare.global.util.AesEncryptUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

class AesPropertiesValidationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(TestConfig.class);

    @Test
    @DisplayName("32-byte UTF-8 key면 context가 정상 기동된다")
    void startsWithValid32ByteKey() {
        contextRunner
                .withPropertyValues("aes.secret-key=12345678901234567890123456789012")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(AesEncryptUtil.class);
                });
    }

    @Test
    @DisplayName("blank key면 context startup에서 실패한다")
    void failsWithBlankKey() {
        contextRunner
                .withPropertyValues("aes.secret-key=")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalArgumentException.class)
                            .hasStackTraceContaining("aes.secret-key must not be blank");
                });
    }

    @Test
    @DisplayName("32-byte가 아니면 context startup에서 실패한다")
    void failsWithWrongByteLength() {
        contextRunner
                .withPropertyValues("aes.secret-key=short-key")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalArgumentException.class)
                            .hasStackTraceContaining("aes.secret-key must be exactly 32 bytes in UTF-8");
                });
    }

    @Configuration
    @EnableConfigurationProperties(AesProperties.class)
    @Import(AesEncryptUtil.class)
    static class TestConfig {
    }
}
