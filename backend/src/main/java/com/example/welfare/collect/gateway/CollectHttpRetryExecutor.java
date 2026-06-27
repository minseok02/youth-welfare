package com.example.welfare.collect.gateway;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.global.util.LogSanitizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntFunction;

@Slf4j
@Component
public class CollectHttpRetryExecutor {

    public <T> ExecutionResult<T> execute(String clientName,
                                          String requestLabel,
                                          int maxAttempts,
                                          long baseBackoffMs,
                                          IntFunction<HttpFailureAction> statusClassifier,
                                          ThrowingSupplier<T> supplier) {
        String safeRequestLabel = sanitizeRequestLabel(requestLabel);
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return ExecutionResult.success(supplier.get());
            } catch (WebClientResponseException e) {
                int status = e.getStatusCode().value();
                HttpFailureAction action = statusClassifier.apply(status);
                if (action == HttpFailureAction.RATE_LIMITED) {
                    if (attempt >= maxAttempts) {
                        log.warn("[{}] rate limit exhausted request={} attempt={}/{} status={}",
                                clientName, safeRequestLabel, attempt, maxAttempts, status);
                        return ExecutionResult.rateLimitedResult();
                    }
                    long waitMs = nextBackoffMillis(baseBackoffMs, attempt);
                    log.warn("[{}] rate limit retry request={} attempt={}/{} status={} waitMs={}",
                            clientName, safeRequestLabel, attempt, maxAttempts, status, waitMs);
                    sleepQuietly(waitMs, clientName + " rate-limit retry");
                    continue;
                }
                if (action == HttpFailureAction.RETRYABLE && attempt < maxAttempts) {
                    long waitMs = nextBackoffMillis(baseBackoffMs, attempt);
                    log.warn("[{}] retryable status retry request={} attempt={}/{} status={} waitMs={}",
                            clientName, safeRequestLabel, attempt, maxAttempts, status, waitMs);
                    sleepQuietly(waitMs, clientName + " retryable status retry");
                    continue;
                }

                log.error("[{}] request failed request={} status={} errorType={}",
                        clientName, safeRequestLabel, status, e.getClass().getSimpleName());
                throw new CustomException(ErrorCode.COLLECT_API_FAILED);
            } catch (Exception e) {
                if (attempt < maxAttempts) {
                    long waitMs = nextBackoffMillis(baseBackoffMs, attempt);
                    log.warn("[{}] retryable exception retry request={} attempt={}/{} waitMs={} errorType={}",
                            clientName, safeRequestLabel, attempt, maxAttempts, waitMs, e.getClass().getSimpleName());
                    sleepQuietly(waitMs, clientName + " exception retry");
                    continue;
                }

                log.error("[{}] request failed request={} errorType={}",
                        clientName, safeRequestLabel, e.getClass().getSimpleName());
                throw new CustomException(ErrorCode.COLLECT_API_FAILED);
            }
        }

        throw new CustomException(ErrorCode.COLLECT_API_FAILED);
    }

    static String sanitizeRequestLabel(String requestLabel) {
        return LogSanitizer.sanitize(requestLabel);
    }

    private long nextBackoffMillis(long baseBackoffMs, int attempt) {
        long base = Math.max(baseBackoffMs, 0L) * Math.max(attempt, 1);
        return base + ThreadLocalRandom.current().nextLong(100, 400);
    }

    private void sleepQuietly(long millis, String context) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(context + " interrupted", e);
        }
    }

    public enum HttpFailureAction {
        RETRYABLE,
        RATE_LIMITED,
        FAIL_FAST
    }

    public record ExecutionResult<T>(T payload, boolean rateLimited) {
        public static <T> ExecutionResult<T> success(T payload) {
            return new ExecutionResult<>(payload, false);
        }

        public static <T> ExecutionResult<T> rateLimitedResult() {
            return new ExecutionResult<>(null, true);
        }
    }

    @FunctionalInterface
    public interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
