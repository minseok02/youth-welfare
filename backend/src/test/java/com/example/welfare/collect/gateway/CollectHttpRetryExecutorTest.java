package com.example.welfare.collect.gateway;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CollectHttpRetryExecutorTest {

    private final CollectHttpRetryExecutor executor = new CollectHttpRetryExecutor();

    @Test
    @DisplayName("retryable status 는 성공할 때까지 재시도한다")
    void executeRetriesRetryableStatus() {
        AtomicInteger attempts = new AtomicInteger();

        CollectHttpRetryExecutor.ExecutionResult<String> result = executor.execute(
                "test-client",
                "page=1",
                3,
                0L,
                status -> status >= 500
                        ? CollectHttpRetryExecutor.HttpFailureAction.RETRYABLE
                        : CollectHttpRetryExecutor.HttpFailureAction.FAIL_FAST,
                () -> {
                    if (attempts.getAndIncrement() == 0) {
                        throw httpException(HttpStatus.INTERNAL_SERVER_ERROR);
                    }
                    return "ok";
                }
        );

        assertThat(result.rateLimited()).isFalse();
        assertThat(result.payload()).isEqualTo("ok");
        assertThat(attempts).hasValue(2);
    }

    @Test
    @DisplayName("rate limit 이 재시도 한도를 넘으면 rateLimited 결과를 돌려준다")
    void executeReturnsRateLimitedWhen429Persists() {
        CollectHttpRetryExecutor.ExecutionResult<String> result = executor.execute(
                "test-client",
                "page=2",
                2,
                0L,
                status -> status == 429
                        ? CollectHttpRetryExecutor.HttpFailureAction.RATE_LIMITED
                        : CollectHttpRetryExecutor.HttpFailureAction.FAIL_FAST,
                () -> {
                    throw httpException(HttpStatus.TOO_MANY_REQUESTS);
                }
        );

        assertThat(result.rateLimited()).isTrue();
        assertThat(result.payload()).isNull();
    }

    @Test
    @DisplayName("fail-fast status 는 즉시 COL001 로 surface 한다")
    void executeFailsFastOnNonRetryableStatus() {
        assertThatThrownBy(() -> executor.execute(
                "test-client",
                "page=3",
                3,
                0L,
                status -> status == 400
                        ? CollectHttpRetryExecutor.HttpFailureAction.FAIL_FAST
                        : CollectHttpRetryExecutor.HttpFailureAction.RETRYABLE,
                () -> {
                    throw httpException(HttpStatus.BAD_REQUEST);
                }
        )).isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode()).isEqualTo(ErrorCode.COLLECT_API_FAILED));
    }

    @Test
    @DisplayName("로그용 request label 은 민감한 key/value 를 마스킹한다")
    void sanitizeRequestLabelRedactsSensitiveValues() {
        String source = "https://apis.data.go.kr/path?serviceKey=secret-key&page=1 apiKey=api-secret token=plain-token";

        String sanitized = CollectHttpRetryExecutor.sanitizeRequestLabel(source);

        assertThat(sanitized)
                .contains("serviceKey=<redacted>")
                .contains("apiKey=<redacted>")
                .contains("token=<redacted>")
                .contains("page=1")
                .doesNotContain("secret-key")
                .doesNotContain("api-secret")
                .doesNotContain("plain-token");
    }

    @Test
    @DisplayName("로그용 request label 마스킹은 null 을 그대로 허용한다")
    void sanitizeRequestLabelAllowsNull() {
        assertThat(CollectHttpRetryExecutor.sanitizeRequestLabel(null)).isNull();
    }

    private WebClientResponseException httpException(HttpStatus status) {
        return WebClientResponseException.create(
                status.value(),
                status.getReasonPhrase(),
                HttpHeaders.EMPTY,
                new byte[0],
                StandardCharsets.UTF_8
        );
    }
}
