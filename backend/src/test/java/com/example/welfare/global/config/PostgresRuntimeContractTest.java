package com.example.welfare.global.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class PostgresRuntimeContractTest {

    @Test
    @DisplayName("활성 런타임 설정은 PostgreSQL driver와 PostgreSQL JDBC URL만 사용한다")
    void activeRuntimeConfigUsesPostgresOnly() throws IOException {
        List<Path> runtimeFiles = List.of(
                Path.of("build.gradle"),
                Path.of("src/main/resources/application.yml"),
                Path.of("src/main/resources/application-prod.yml"),
                Path.of("../docker-compose.yml"),
                Path.of("../docker-compose.prod.yml"),
                Path.of("../.env.example"),
                Path.of("../env.production.example")
        );

        for (Path runtimeFile : runtimeFiles) {
            String content = Files.readString(runtimeFile);

            assertThat(content)
                    .as(runtimeFile.toString())
                    .doesNotContain("com.mysql")
                    .doesNotContain("mysql-connector")
                    .doesNotContain("org.hibernate.dialect.MySQL")
                    .doesNotContain("jdbc:" + "mysql://");
        }
    }

    @Test
    @DisplayName("prod profile은 전용 runtime datasource env를 DB_URL/DB_PASSWORD fallback 없이 요구한다")
    void prodProfileRequiresDedicatedRuntimeDatasourceEnvWithoutPrimaryFallbacks() throws IOException {
        String prodConfig = Files.readString(Path.of("src/main/resources/application-prod.yml"));

        assertThat(prodConfig)
                .contains("url: ${ADMIN_RO_DB_URL}")
                .contains("password: ${DB_ADMIN_RO_PASSWORD}")
                .contains("url: ${RECOMMENDATION_REVIEW_GATE_COMMAND_DB_URL}")
                .contains("password: ${DB_RECOMMENDATION_REVIEW_GATE_COMMAND_PASSWORD}")
                .contains("url: ${RECOMMENDATION_PERSISTENCE_COMMAND_DB_URL}")
                .contains("password: ${DB_RECOMMENDATION_PERSISTENCE_COMMAND_PASSWORD}")
                .contains("url: ${CHAT_SESSION_CLEANUP_DB_URL}")
                .contains("password: ${DB_CHAT_SESSION_CLEANUP_PASSWORD}")
                .contains("url: ${CLUSTER_AI_CLEANUP_DB_URL}")
                .contains("password: ${DB_CLUSTER_AI_CLEANUP_PASSWORD}")
                .contains("url: ${RECOMMENDATION_RETENTION_CLEANUP_DB_URL}")
                .contains("password: ${DB_RECOMMENDATION_RETENTION_CLEANUP_PASSWORD}")
                .contains("url: ${COLLECT_EXECUTION_LOCK_CLEANUP_DB_URL}")
                .contains("password: ${DB_COLLECT_EXECUTION_LOCK_CLEANUP_PASSWORD}")
                .contains("url: ${WEB_PUSH_SUBSCRIPTION_CLEANUP_DB_URL}")
                .contains("password: ${DB_WEB_PUSH_SUBSCRIPTION_CLEANUP_PASSWORD}")
                .doesNotContain("${ADMIN_RO_DB_URL:${DB_URL")
                .doesNotContain("${RECOMMENDATION_REVIEW_GATE_COMMAND_DB_URL:${DB_URL")
                .doesNotContain("${RECOMMENDATION_PERSISTENCE_COMMAND_DB_URL:${DB_URL")
                .doesNotContain("${DB_RECOMMENDATION_REVIEW_GATE_COMMAND_PASSWORD:${DB_PASSWORD")
                .doesNotContain("${DB_RECOMMENDATION_PERSISTENCE_COMMAND_PASSWORD:${DB_PASSWORD");
    }

    @Test
    @DisplayName("local Docker compose는 prod profile 필수 runtime datasource env를 명시한다")
    void localDockerComposeProvidesProdRequiredRuntimeDatasourceEnv() throws IOException {
        String compose = Files.readString(Path.of("../docker-compose.yml"));

        assertThat(compose)
                .contains("ADMIN_RO_DB_URL: jdbc:postgresql://db:5432/youth_welfare?sslmode=disable")
                .contains("RECOMMENDATION_REVIEW_GATE_COMMAND_DB_URL: jdbc:postgresql://db:5432/youth_welfare?sslmode=disable")
                .contains("RECOMMENDATION_PERSISTENCE_COMMAND_DB_URL: jdbc:postgresql://db:5432/youth_welfare?sslmode=disable")
                .contains("CHAT_SESSION_CLEANUP_DB_URL: jdbc:postgresql://db:5432/youth_welfare?sslmode=disable")
                .contains("CLUSTER_AI_CLEANUP_DB_URL: jdbc:postgresql://db:5432/youth_welfare?sslmode=disable")
                .contains("RECOMMENDATION_RETENTION_CLEANUP_DB_URL: jdbc:postgresql://db:5432/youth_welfare?sslmode=disable")
                .contains("COLLECT_EXECUTION_LOCK_CLEANUP_DB_URL: jdbc:postgresql://db:5432/youth_welfare?sslmode=disable")
                .contains("WEB_PUSH_SUBSCRIPTION_CLEANUP_DB_URL: jdbc:postgresql://db:5432/youth_welfare?sslmode=disable")
                .contains("DB_ADMIN_RO_USERNAME: admin_dashboard_ro")
                .contains("DB_CLUSTER_AI_CLEANUP_USERNAME: cluster_ai_cleanup_rw")
                .contains("DB_RECOMMENDATION_RETENTION_CLEANUP_USERNAME: recommendation_retention_cleanup_rw")
                .contains("DB_COLLECT_EXECUTION_LOCK_CLEANUP_USERNAME: collect_execution_lock_cleanup_rw")
                .contains("DB_WEB_PUSH_SUBSCRIPTION_CLEANUP_USERNAME: web_push_subscription_cleanup_rw");
    }

    @Test
    @DisplayName("PostgreSQL schema는 MySQL 전용 DDL/DML 문법을 포함하지 않는다")
    void schemaSqlDoesNotContainMysqlOnlySyntax() throws IOException {
        String schema = Files.readString(Path.of("src/main/resources/db/schema.sql"));

        assertThat(schema).doesNotContain(forbiddenMysqlSqlTokens());
    }

    @Test
    @DisplayName("활성 DB resource migration에는 MySQL 전용 SQL 문법을 포함하지 않는다")
    void activeDbResourceMigrationsDoNotContainMysqlOnlySyntax() throws IOException {
        List<Path> migrationFiles;
        try (Stream<Path> paths = Files.walk(Path.of("src/main/resources/db"))) {
            migrationFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".sql"))
                    .toList();
        }

        for (Path migrationFile : migrationFiles) {
            assertThat(Files.readString(migrationFile))
                    .as(migrationFile.toString())
                    .doesNotContain(forbiddenMysqlSqlTokens());
        }
    }

    @Test
    @DisplayName("활성 DB resource migration version은 중복되지 않는다")
    void activeDbResourceMigrationVersionsAreUnique() throws IOException {
        List<Path> migrationFiles;
        try (Stream<Path> paths = Files.list(Path.of("src/main/resources/db/migration"))) {
            migrationFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().matches("^V.+__.+\\.sql$"))
                    .toList();
        }

        Map<String, List<String>> filesByVersion = migrationFiles.stream()
                .collect(Collectors.groupingBy(
                        path -> path.getFileName().toString().replaceFirst("^V(.+)__.+\\.sql$", "$1"),
                        Collectors.mapping(path -> path.getFileName().toString(), Collectors.toList())
                ));

        assertThat(filesByVersion)
                .allSatisfy((version, files) -> assertThat(files)
                        .as("migration version %s", version)
                        .hasSize(1));
    }

    private String[] forbiddenMysqlSqlTokens() {
        return new String[]{
                "USE youth_welfare",
                "AUTO_INCREMENT",
                "ON DUPLICATE KEY",
                "INSERT IGNORE",
                "REPLACE INTO",
                "ENGINE=",
                "engine=InnoDB",
                "CHARSET=",
                "charset=utf8mb4",
                "COLLATE=",
                "TINYINT",
                "UNSIGNED",
                "LONGTEXT",
                "MEDIUMTEXT",
                "DROP FOREIGN KEY",
                "MODIFY COLUMN",
                " ADD INDEX ",
                " ADD KEY ",
                " AFTER ",
                "SET SESSION sql_log_bin",
                "`"
        };
    }

    @Test
    @DisplayName("PostgreSQL schema는 PostgreSQL extension과 sequence-friendly ID 타입을 사용한다")
    void schemaSqlContainsPostgresRuntimeFeatures() throws IOException {
        String schema = Files.readString(Path.of("src/main/resources/db/schema.sql"));

        assertThat(schema)
                .contains("CREATE EXTENSION IF NOT EXISTS pgcrypto")
                .contains("CREATE EXTENSION IF NOT EXISTS pg_trgm")
                .contains("CREATE EXTENSION IF NOT EXISTS vector")
                .contains("BIGSERIAL");
    }

    @Test
    @DisplayName("Gradle runtime env 보정은 MySQL URL과 root 계정을 fail-fast 한다")
    void gradleRuntimeEnvRejectsLegacyMysqlFallbacks() throws IOException {
        String buildGradle = Files.readString(Path.of("build.gradle"));

        assertThat(buildGradle)
                .contains("throw new GradleException('MySQL JDBC URLs are not supported")
                .contains("throw new GradleException('root is not a supported runtime DB username")
                .contains("value.startsWith('jdbc:' + 'mysql://')");
    }

    @Test
    @DisplayName("운영 compose는 app/redis만 포함하고 DB 서비스는 포함하지 않는다")
    void prodComposeDoesNotDefineDatabaseService() throws IOException {
        String prodCompose = Files.readString(Path.of("../docker-compose.prod.yml"));

        assertThat(prodCompose)
                .contains("  app:")
                .contains("  redis:")
                .doesNotContain("  db:")
                .doesNotContain("5432:5432")
                .doesNotContain("5433:5432")
                .doesNotContain("postgres_data");
    }

    @Test
    @DisplayName("로컬 compose DB는 명시적 허용값 없이는 시작되지 않는다")
    void localComposeDatabaseRequiresExplicitGuard() throws IOException {
        String localCompose = Files.readString(Path.of("../docker-compose.yml"));

        assertThat(localCompose)
                .contains("  db:")
                .contains("ALLOW_LOCAL_DOCKER_DB:?");
    }
}
