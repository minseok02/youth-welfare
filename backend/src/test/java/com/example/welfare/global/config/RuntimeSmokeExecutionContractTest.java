package com.example.welfare.global.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Runs the real scripts in a copied, isolated tree; HTTP/DB/credential helpers are fakes. */
class RuntimeSmokeExecutionContractTest {

    @TempDir
    Path temporaryRoot;

    private static final String CLICK_SCRIPT = "deploy/smoke/run-local-recommendation-click-smoke.sh";
    private static final String LATENCY_SCRIPT = "deploy/performance/run-local-api-latency-baseline.sh";

    @Test
    void clickSmokeSeparatesDetailGetFromAuthenticatedPostAndChecksDatabase() throws Exception {
        Result result = runClick("success", "91");

        assertThat(result.exitCode()).as(result.output()).isZero();
        String requests = Files.readString(temporaryRoot.resolve("requests.txt"));
        assertThat(requests)
                .contains("GET\thttp://smoke.invalid/api/policies/42\t")
                .contains("POST\thttp://smoke.invalid/api/policies/42/recommendation-click\t")
                .contains("Authorization: Bearer fixture-access")
                .contains("Content-Type: application/json -d {\"logId\":91}")
                .doesNotContain("?logId=", "?log_id=");
        assertThat(requests.indexOf("GET\thttp://smoke.invalid/api/policies/42\t"))
                .isLessThan(requests.indexOf("POST\thttp://smoke.invalid/api/policies/42/recommendation-click\t"));
        assertThat(Files.readString(temporaryRoot.resolve("sql.txt")))
                .contains("WHERE id = 91 AND service_id = 42");
        assertThat(result.output()).contains("recommendation click smoke passed");
    }

    @ParameterizedTest
    @ValueSource(strings = {"detail_mutates", "click_http_error", "click_not_saved", "timestamp_missing"})
    void clickSmokeFailsForWrongMutationHttpFailureOrMissingDatabaseEvidence(String mode) throws Exception {
        Result result = runClick(mode, "91");

        assertThat(result.exitCode()).as(result.output()).isNotZero();
        assertThat(result.output()).doesNotContain("recommendation click smoke passed");
        if (mode.equals("detail_mutates")) {
            assertThat(Files.readString(temporaryRoot.resolve("requests.txt")))
                    .doesNotContain("/recommendation-click");
        }
    }

    @Test
    void clickSmokeRejectsNonNumericLogIdBeforeDetailOrSqlInterpolation() throws Exception {
        Result result = runClick("success", "91;DROP TABLE recommendation_logs");

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.output()).contains("must be positive integer IDs");
        assertThat(Files.readString(temporaryRoot.resolve("requests.txt")))
                .doesNotContain("/api/policies/42");
        assertThat(Files.readString(temporaryRoot.resolve("sql.txt"))).doesNotContain("DROP TABLE");
    }

    @ParameterizedTest
    @ValueSource(strings = {"success", "http_error"})
    void latencySmokeUsesPostJsonAndDoesNotReportHttpErrorsAsPassed(String mode) throws Exception {
        prepareScript(LATENCY_SCRIPT);
        write("deploy/performance/perf-common.sh", """
                perf_now_ts_utc() { printf 'fixture'; }
                smoke_require_command() { :; }
                perf_require_python() { :; }
                perf_write_run_context() { printf 'fixture=true\n' > "$1"; }
                smoke_resolve_admin_credentials() { :; }
                perf_publish_latest() { :; }
                """);
        // No real curl process is reachable through the script's PATH.
        writeExecutable("bin/curl", """
                #!/usr/bin/env python3
                import json, os, sys
                from pathlib import Path
                args = sys.argv[1:]
                root = Path(os.environ['HARNESS_DIR'])
                with (root / 'http-requests.jsonl').open('a') as out:
                    out.write(json.dumps(args, ensure_ascii=False) + '\\n')
                url = next(arg for arg in args if arg.startswith('http://'))
                if '-o' in args:
                    Path(args[args.index('-o') + 1]).write_text('{"data":{"content":[{"id":42}]}}')
                if '-w' in args:
                    status = '503' if os.environ['HARNESS_MODE'] == 'http_error' and url.endswith('/search') else '200'
                    print(status + '\\t0.001\\t0.0001\\t0.0008\\t50', end='')
                """);
        Result result = run(LATENCY_SCRIPT, Map.of("HARNESS_MODE", mode, "RUNS", "1", "WARMUP_RUNS", "0",
                "API_LATENCY_ROOT", temporaryRoot.resolve("latency-root").toString()));

        String requests = Files.readString(temporaryRoot.resolve("http-requests.jsonl"));
        assertThat(requests).doesNotContain("search?keyword=", "suggestions?keyword=");
        // Parse and validate arguments rather than merely finding POST text in the source file.
        Path verifier = write("verify-requests.py", """
                import json, sys
                rows = [json.loads(line) for line in open(sys.argv[1])]
                searches = [r for r in rows if any(u.endswith('/search') or u.endswith('/search/suggestions') for u in r)]
                assert len(searches) == 3, searches
                for row in searches:
                    assert row[row.index('-X') + 1] == 'POST', row
                    assert 'Content-Type: application/json' in row, row
                    body = json.loads(row[row.index('--data-binary') + 1])
                    assert body['keyword'] == '청년', body
                assert any(json.loads(r[r.index('--data-binary') + 1]).get('sort') == 'deadline' for r in searches)
                """);
        Result verified = execute(new ProcessBuilder("python3", verifier.toString(),
                temporaryRoot.resolve("http-requests.jsonl").toString()));
        assertThat(verified.exitCode()).as(verified.output()).isZero();

        if (mode.equals("success")) {
            assertThat(result.exitCode()).as(result.output()).isZero();
            assertThat(result.output()).contains("api_latency_baseline=passed");
        } else {
            assertThat(result.exitCode()).as(result.output()).isNotZero();
            assertThat(result.output()).contains("api_latency_baseline=failed").doesNotContain("api_latency_baseline=passed");
            assertThat(temporaryRoot.resolve("artifacts/api-latency-summary.json")).exists();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing_target", "default_port", "production_port", "default_redis", "unconfirmed", "isolated"})
    void restoreRunbookGuardRejectsUnsafeTargetsBeforeInvokingWriteSmoke(String mode) throws Exception {
        String runbook = Files.readString(Path.of("../docs/core/db-backup-restore-rehearsal-runbook.md"));
        int start = runbook.indexOf("```bash\n(\n");
        assertThat(start).isGreaterThanOrEqualTo(0);
        int bodyStart = start + "```bash\n".length();
        String guard = runbook.substring(bodyStart, runbook.indexOf("```", bodyStart));
        write("guard.sh", guard);
        write("deploy/smoke/run-local-runtime-api-smoke.sh", """
                [[ "$ENV_FILE" == '.env.restore-runtime' && "$SMOKE_DB_MODE" == postgres ]] || exit 96
                [[ "$APP_BASE_URL" == 'http://127.0.0.1:18082' ]] || exit 96
                [[ "$APP_HEALTH_URL" == "$APP_BASE_URL/actuator/health" ]] || exit 96
                [[ "$REDIS_CONTAINER_NAME" == 'restore-rehearsal-redis' ]] || exit 96
                touch "$HARNESS_DIR/write-smoke-invoked"
                """);
        String restoreUrl = switch (mode) {
            case "missing_target" -> "";
            case "default_port" -> "http://127.0.0.1:8082";
            default -> "http://127.0.0.1:18082";
        };
        String productionUrl = mode.equals("production_port") ? "https://localhost:18082" : "https://production.invalid";
        Result result = run("guard.sh", Map.of("RESTORE_APP_BASE_URL", restoreUrl,
                "PRODUCTION_APP_BASE_URL", productionUrl,
                "RESTORE_REDIS_CONTAINER_NAME", mode.equals("default_redis") ? "youth-welfare-redis" : "restore-rehearsal-redis",
                "RESTORE_ISOLATION_CONFIRMED", mode.equals("unconfirmed") ? "false" : "true"));
        assertThat(result.exitCode() == 0).as(result.output()).isEqualTo(mode.equals("isolated"));
        assertThat(Files.exists(temporaryRoot.resolve("write-smoke-invoked"))).isEqualTo(mode.equals("isolated"));
    }

    private Result runClick(String mode, String logId) throws Exception {
        prepareScript(CLICK_SCRIPT);
        write("deploy/smoke/smoke-common.sh", """
                smoke_require_command() { :; }
                smoke_sanitize_artifacts() { :; }
                smoke_normalize_bool() { printf '%s' "$1"; }
                smoke_build_email() { printf 'fixture@example.invalid'; }
                smoke_print_step() { :; }
                smoke_ensure_policy_fixture() { :; }
                smoke_seed_verified_email() { :; }
                smoke_wait_for_health() { printf '{}' > "$4"; printf '200'; }
                smoke_assert_status() { [[ "$1" == "$2" ]] || { echo 'unexpected HTTP status' >&2; return 1; }; }
                smoke_db_query() {
                  printf '%s\n' "$1" >> "$HARNESS_DIR/sql.txt"
                  if [[ "$1" == *'COUNT('* ]]; then printf '0'; return; fi
                  local clicked=f clicked_at=NULL
                  if [[ -f "$HARNESS_DIR/click-posted" ]]; then
                    [[ "$HARNESS_MODE" == click_not_saved ]] || clicked=t
                    [[ "$HARNESS_MODE" == timestamp_missing ]] || clicked_at='2026-10-22 12:00:00'
                  elif [[ "$HARNESS_MODE" == detail_mutates ]]; then
                    clicked=t; clicked_at='2026-10-22 12:00:00'
                  fi
                  printf '91\t42\t%s\t%s\n' "$clicked" "$clicked_at"
                }
                smoke_http_status() {
                  local method="$1" url="$2" output="$3"; shift 3
                  printf '%s\t%s\t%s\n' "$method" "$url" "$*" >> "$HARNESS_DIR/requests.txt"
                  case "$url" in
                    */api/auth/login) printf '{"data":{"accessToken":"fixture-access"}}' > "$output" ;;
                    */api/recommendations/refresh)
                      printf '{"data":[{"id":7,"serviceId":42,"logId":"%s"}]}' "$HARNESS_LOG_ID" > "$output" ;;
                    */recommendation-click)
                      touch "$HARNESS_DIR/click-posted"; printf '{}' > "$output"
                      if [[ "$HARNESS_MODE" == click_http_error ]]; then printf '403'; return; fi ;;
                    *) printf '{}' > "$output" ;;
                  esac
                  printf '200'
                }
                """);
        return run(CLICK_SCRIPT, Map.of("HARNESS_MODE", mode, "HARNESS_LOG_ID", logId));
    }

    private void prepareScript(String relativePath) throws IOException {
        Path target = temporaryRoot.resolve(relativePath);
        Files.createDirectories(target.getParent());
        Files.copy(Path.of("..").resolve(relativePath), target);
    }

    private Path write(String relativePath, String content) throws IOException {
        Path target = temporaryRoot.resolve(relativePath);
        Files.createDirectories(target.getParent());
        Files.writeString(target, content);
        return target;
    }

    private void writeExecutable(String relativePath, String content) throws IOException {
        assertThat(write(relativePath, content).toFile().setExecutable(true)).isTrue();
    }

    private Result run(String script, Map<String, String> overrides) throws Exception {
        // Any accidental direct external tool invocation fails instead of reaching a service.
        for (String command : new String[]{"curl", "docker", "psql", "redis-cli", "aws"}) {
            if (!Files.exists(temporaryRoot.resolve("bin/" + command))) {
                writeExecutable("bin/" + command, "#!/bin/bash\necho 'unexpected external command' >&2\nexit 97\n");
            }
        }
        ProcessBuilder builder = new ProcessBuilder("/bin/bash", temporaryRoot.resolve(script).toString());
        builder.directory(temporaryRoot.toFile());
        builder.environment().clear();
        builder.environment().putAll(Map.of("PATH", temporaryRoot.resolve("bin") + ":/usr/bin:/bin",
                "HARNESS_DIR", temporaryRoot.toString(), "APP_BASE_URL", "http://smoke.invalid",
                "ARTIFACT_DIR", temporaryRoot.resolve("artifacts").toString()));
        builder.environment().putAll(overrides);
        Files.createDirectories(temporaryRoot.resolve("artifacts"));
        return execute(builder);
    }

    private Result execute(ProcessBuilder builder) throws Exception {
        Path output = Files.createTempFile(temporaryRoot, "process-", ".txt");
        Process process = builder.redirectErrorStream(true).redirectOutput(output.toFile()).start();
        boolean finished = process.waitFor(30, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
        }
        assertThat(finished).as("offline script must finish within 30 seconds").isTrue();
        return new Result(process.exitValue(), Files.readString(output));
    }

    private record Result(int exitCode, String output) { }
}
