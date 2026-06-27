package com.example.welfare.global.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class BackendLogRedactionContractTest {

    @Test
    @DisplayName("공통 unhandled exception 로그는 throwable 원문 대신 sanitizer를 통과한 요약만 남긴다")
    void globalExceptionHandlerLogsSanitizedUnhandledExceptionSummary() throws IOException {
        String source = Files.readString(Path.of("../backend/src/main/java/com/example/welfare/global/exception/GlobalExceptionHandler.java"));

        assertThat(source)
                .contains("LogSanitizer.sanitizeSingleLine(e.getMessage(), 300)")
                .doesNotContain("log.error(\"Unhandled exception\", e)");
    }

    @Test
    @DisplayName("chat context state parse 실패는 raw JSON snippet을 포함할 수 있는 throwable을 로그에 싣지 않는다")
    void chatContextParseFailureDoesNotLogThrowable() throws IOException {
        String source = Files.readString(Path.of("../backend/src/main/java/com/example/welfare/chat/service/ChatSessionContextStateService.java"));

        assertThat(source)
                .contains("context state parse failed, reset state errorType={}")
                .doesNotContain("context state parse failed, reset state\", e");
    }

    @Test
    @DisplayName("web-push/collect 운영 로그 값은 공통 로그 sanitizer를 통과한다")
    void externalOperationalLogValuesUseCommonLogSanitizer() throws IOException {
        String webPush = Files.readString(Path.of("../backend/src/main/java/com/example/welfare/notification/service/WebPushDispatchService.java"));
        String collectRetry = Files.readString(Path.of("../backend/src/main/java/com/example/welfare/collect/gateway/CollectHttpRetryExecutor.java"));
        String policyViewLog = Files.readString(Path.of("../backend/src/main/java/com/example/welfare/policy/service/PolicyViewLogService.java"));

        assertThat(webPush)
                .contains("LogSanitizer.sanitizeSingleLine(errorMessage, 160)")
                .doesNotContain("return errorMessage.trim().replaceAll");
        assertThat(collectRetry)
                .contains("return LogSanitizer.sanitize(requestLabel);");
        assertThat(policyViewLog)
                .contains("errorType={}")
                .doesNotContain("RedisKeyHash.sha256Hex(userKey), exception)");
    }

    @Test
    @DisplayName("남은 운영 로그 실패 경로는 throwable stacktrace 대신 errorType만 남긴다")
    void remainingOperationalFailureLogsDoNotEmitThrowableStacktrace() throws IOException {
        String aes = Files.readString(Path.of("../backend/src/main/java/com/example/welfare/global/util/AesEncryptUtil.java"));
        String policySearch = Files.readString(Path.of("../backend/src/main/java/com/example/welfare/policy/service/PolicySearchLogService.java"));
        String chatSnapshot = Files.readString(Path.of("../backend/src/main/java/com/example/welfare/chat/service/ChatRetrievalSnapshotService.java"));
        String policyRegionAudit = Files.readString(Path.of("../backend/src/main/java/com/example/welfare/admin/dashboard/service/AdminPolicyRegionAuditService.java"));

        assertThat(aes)
                .contains("AES encrypt failed errorType={}")
                .contains("AES decrypt failed errorType={}")
                .doesNotContain("log.error(\"AES encrypt failed\", e)")
                .doesNotContain("log.error(\"AES decrypt failed\", e)");
        assertThat(policySearch)
                .contains("검색 로그 저장 실패 keywordLength={} resultCount={} page={} size={} errorType={}")
                .doesNotContain("command.size(),\n                    e)");
        assertThat(chatSnapshot)
                .contains("snapshot 저장 실패 type={} scenarioKey={} sessionId={} questionLength={} errorType={}")
                .doesNotContain("lengthOf(snapshot.getQuestion()),\n                    e)");
        assertThat(policyRegionAudit)
                .contains("scheduled failed errorType={}")
                .doesNotContain("e.getClass().getSimpleName(), e");
    }
}
