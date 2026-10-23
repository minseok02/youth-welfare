package com.example.welfare.global.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** Static SQL contracts and real Bash execution with a fake Docker; never connects to PostgreSQL. */
class CollectSnapshotPatchContractTest {

    private static final String PATCH = "deploy/postgres/patches/V2026_10_23_01__add_collect_list_diff_snapshots.sql";
    private static final String HELPER = "deploy/postgres/apply-local-collect-snapshot-patch.sh";

    @TempDir
    Path temporaryRoot;

    @Test
    void patchDefinesTheSameSnapshotObjectsAsFreshSchemaAndMigration() throws Exception {
        String patch = Files.readString(Path.of("..", PATCH));
        String schema = Files.readString(Path.of("src/main/resources/db/schema.sql"));
        String migration = Files.readString(Path.of(
                "src/main/resources/db/migration/V2026_06_09_01__add_collect_list_diff_snapshots.sql"));

        assertThat(snapshotDefinitions(patch)).hasSize(4)
                .containsExactlyElementsOf(snapshotDefinitions(migration))
                .containsExactlyElementsOf(snapshotDefinitions(schema));
    }

    @Test
    void patchIsAtomicAndAddsOnlySnapshotReadInsertAndSequenceGrants() throws Exception {
        String patch = Files.readString(Path.of("..", PATCH));

        assertThat(patch).contains("\\set ON_ERROR_STOP on", "BEGIN;", "COMMIT;",
                        "GRANT SELECT, INSERT ON TABLE public.collect_list_snapshots, public.collect_list_snapshot_items TO :\"app_username\";",
                        "GRANT USAGE, SELECT ON SEQUENCE public.collect_list_snapshots_id_seq TO :\"app_username\";",
                        "GRANT SELECT ON TABLE public.collect_list_snapshots, public.collect_list_snapshot_items TO :\"admin_ro_username\";")
                .doesNotContain("GRANT ALL", "ALL TABLES", "ALL SEQUENCES", "ALTER ROLE", "CREATE ROLE",
                        "DROP TABLE", "DELETE FROM", "TRUNCATE TABLE", "OWNER TO", "INSERT INTO", "UPDATE public.");
        assertThat(patch.indexOf("BEGIN;")).isLessThan(patch.indexOf("CREATE TABLE IF NOT EXISTS"));
        assertThat(patch.lastIndexOf("COMMIT;")).isGreaterThan(patch.indexOf("GRANT SELECT, INSERT"));
    }

    @Test
    void patchChecksExistingStructureAndEffectivePrivilegesInReadOnlyModeToo() throws Exception {
        String patch = Files.readString(Path.of("..", PATCH));

        assertThat(patch).contains("snapshot_check_only", "BEGIN READ ONLY;", "information_schema.columns",
                "pg_constraint", "pg_index", "pg_get_serial_sequence", "has_table_privilege",
                "has_sequence_privilege", "has_schema_privilege", "has_any_column_privilege", "RAISE EXCEPTION", "snapshot_privileges_ok",
                "current_database() = 'youth_welfare'", "snapshot_check_only must be true or false",
                "snapshot privileges are not ready", "collect_snapshot_schema=ready");
        assertThat(patch.indexOf("COMMIT;")).isLessThan(patch.indexOf("collect_snapshot_schema=ready"));
    }

    @Test
    void schemaMutationsAreAllInsideApplyOnlyConditionalBlocks() throws Exception {
        String patch = Files.readString(Path.of("..", PATCH));
        var blocks = Pattern.compile("\\\\if :snapshot_check_only\\R\\\\else\\R(.*?)\\\\endif", Pattern.DOTALL)
                .matcher(patch).results().toList();
        assertThat(blocks).hasSize(2);
        var mutations = Pattern.compile("(?m)^(CREATE|GRANT) ").matcher(patch).results().toList();
        assertThat(mutations).hasSize(7).allSatisfy(mutation ->
                assertThat(blocks.stream().anyMatch(block -> block.start() < mutation.start() && block.end() > mutation.end()))
                        .as("mutation at offset %s is apply-only", mutation.start()).isTrue());
        assertThat(patch).doesNotContain("\\quit");
    }

    @Test
    void helperDefaultsToReadOnlyCheckOnPinnedLocalDockerSocket() throws Exception {
        Result result = run(List.of(), Map.of());

        assertThat(result.exitCode()).as(result.output()).isZero();
        String args = Files.readString(temporaryRoot.resolve("docker-args.txt"));
        assertThat(args).contains("--host\nunix:///var/run/docker.sock", "snapshot_check_only=true",
                        "--username\nadmin_dashboard_ro", "--host\n/var/run/postgresql", "--dbname\nyouth_welfare", "-X", "ON_ERROR_STOP=1")
                .doesNotContain("tcp://", "remote-context", "--username\npostgres");
        assertThat(Files.readString(temporaryRoot.resolve("psql-input.sql")))
                .isEqualTo(Files.readString(Path.of("..", PATCH)))
                .doesNotContain("unrelated-patch-sentinel");
        assertThat(result.output()).contains("mode=check").doesNotContain("patch applied");
    }

    @ParameterizedTest
    @ValueSource(strings = {"none", "local_only", "backup_only"})
    void applyRequiresLocalOptInAndBackupConfirmationBeforeDocker(String mode) throws Exception {
        Map<String, String> flags = switch (mode) {
            case "local_only" -> Map.of("ALLOW_LOCAL_DOCKER_DB", "true");
            case "backup_only" -> Map.of("SNAPSHOT_BACKUP_CONFIRMED", "true");
            default -> Map.of();
        };
        Result result = run(List.of("--apply"), flags);

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.output()).contains("ALLOW_LOCAL_DOCKER_DB=true", "SNAPSHOT_BACKUP_CONFIRMED=true");
        assertThat(temporaryRoot.resolve("docker-args.txt")).doesNotExist();
    }

    @Test
    void confirmedApplyUsesMigrationRoleAndOnlyTheFixedSnapshotPatch() throws Exception {
        Result result = run(List.of("--apply"), Map.of(
                "ALLOW_LOCAL_DOCKER_DB", "true", "SNAPSHOT_BACKUP_CONFIRMED", "true"));

        assertThat(result.exitCode()).as(result.output()).isZero();
        assertThat(Files.readString(temporaryRoot.resolve("docker-args.txt")))
                .contains("snapshot_check_only=false", "--username\nmigration_admin")
                .doesNotContain("--username\npostgres", "unrelated-patch-sentinel");
        assertThat(Files.readString(temporaryRoot.resolve("psql-input.sql")))
                .isEqualTo(Files.readString(Path.of("..", PATCH)));
        assertThat(result.output()).contains("snapshot patch applied");
    }

    @ParameterizedTest
    @ValueSource(strings = {"false|true|db", "true|false|db", "true|true|app"})
    void wrongOrStoppedContainerIsRejectedBeforePsql(String state) throws Exception {
        Result result = run(List.of("--check"), Map.of("HARNESS_CONTAINER_STATE", state));

        assertThat(result.exitCode()).isNotZero();
        assertThat(temporaryRoot.resolve("psql-input.sql")).doesNotExist();
        assertThat(result.output()).doesNotContain("collect_snapshot_schema=ready");
    }

    @ParameterizedTest
    @ValueSource(strings = {"--check", "--apply"})
    void psqlFailurePropagatesWithoutReportingSuccess(String mode) throws Exception {
        Result result = run(List.of(mode), Map.of("HARNESS_PSQL_FAIL", "true",
                "ALLOW_LOCAL_DOCKER_DB", "true", "SNAPSHOT_BACKUP_CONFIRMED", "true"));

        assertThat(result.exitCode()).isEqualTo(71);
        assertThat(result.output()).doesNotContain("collect_snapshot_schema=ready", "snapshot patch applied");
    }

    @Test
    void unknownArgumentsAndInvalidRolesAreRejectedWithoutDocker() throws Exception {
        Result unknown = run(List.of("--all"), Map.of());
        assertThat(unknown.exitCode()).isNotZero();
        assertThat(temporaryRoot.resolve("docker-args.txt")).doesNotExist();

        Result invalid = run(List.of("--check"), Map.of("SNAPSHOT_APP_ROLE", "app_core_rw;DROP ROLE anything"));
        assertThat(invalid.exitCode()).isNotZero();
        assertThat(invalid.output()).doesNotContain("DROP ROLE");
        assertThat(temporaryRoot.resolve("docker-args.txt")).doesNotExist();
    }

    private List<String> snapshotDefinitions(String sql) {
        return Pattern.compile("CREATE (?:TABLE|INDEX) IF NOT EXISTS [^;]+;", Pattern.CASE_INSENSITIVE)
                .matcher(sql).results().map(match -> match.group())
                .filter(statement -> statement.contains("collect_list_snapshot"))
                .map(statement -> statement.replace("public.", "").replaceAll("\\s+", ""))
                .toList();
    }

    private Result run(List<String> arguments, Map<String, String> overrides) throws Exception {
        Path helper = temporaryRoot.resolve(HELPER);
        Files.createDirectories(helper.getParent());
        Files.copy(Path.of("..", HELPER), helper, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        Path patch = temporaryRoot.resolve(PATCH);
        Files.createDirectories(patch.getParent());
        Files.copy(Path.of("..", PATCH), patch, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        Files.writeString(patch.getParent().resolve("V9999__unrelated.sql"), "unrelated-patch-sentinel");
        Path docker = temporaryRoot.resolve("bin/docker");
        Files.createDirectories(docker.getParent());
        Files.writeString(docker, """
                #!/usr/bin/env python3
                import os, sys
                from pathlib import Path
                root = Path(os.environ['HARNESS_DIR'])
                args = sys.argv[1:]
                with (root / 'docker-args.txt').open('a') as out:
                    out.write('\\n'.join(args) + '\\n')
                assert args[:2] == ['--host', 'unix:///var/run/docker.sock'], args
                args = args[2:]
                if args[0] == 'inspect':
                    print(os.environ.get('HARNESS_CONTAINER_STATE', 'true|true|db'))
                elif args[0] == 'exec':
                    (root / 'psql-input.sql').write_text(sys.stdin.read())
                    if os.environ.get('HARNESS_PSQL_FAIL') == 'true':
                        print('fixture PostgreSQL check failed', file=sys.stderr)
                        sys.exit(71)
                    print('collect_snapshot_schema=ready')
                else:
                    sys.exit(97)
                """);
        assertThat(docker.toFile().setExecutable(true)).isTrue();
        List<String> command = new ArrayList<>(List.of("bash", helper.toString()));
        command.addAll(arguments);
        ProcessBuilder builder = new ProcessBuilder(command).directory(temporaryRoot.toFile()).redirectErrorStream(true);
        builder.environment().clear();
        builder.environment().putAll(Map.of("PATH", temporaryRoot.resolve("bin") + ":/usr/bin:/bin",
                "HARNESS_DIR", temporaryRoot.toString(), "DOCKER_HOST", "tcp://remote.invalid:2375",
                "DOCKER_CONTEXT", "remote-context", "PATCH_DIR", "/must-not-use", "ENV_FILE", "/must-not-read"));
        builder.environment().putAll(overrides);
        Path output = Files.createTempFile(temporaryRoot, "script-", ".txt");
        Process process = builder.redirectOutput(output.toFile()).start();
        boolean finished = process.waitFor(15, TimeUnit.SECONDS);
        if (!finished) process.destroyForcibly();
        assertThat(finished).isTrue();
        return new Result(process.exitValue(), Files.readString(output));
    }

    private record Result(int exitCode, String output) { }
}
