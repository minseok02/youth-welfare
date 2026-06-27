package com.example.welfare.global.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ProdComposeHardeningContractTest {

    private static final Path PROD_COMPOSE = Path.of("../docker-compose.prod.yml");
    private static final Path DOCKERFILE = Path.of("Dockerfile");

    @Test
    @DisplayName("운영 compose는 app/redis만 띄우고 RDS 전제를 깨는 로컬 DB 서비스를 포함하지 않는다")
    void prodComposeContainsOnlyAppAndRedisServices() throws IOException {
        String compose = Files.readString(PROD_COMPOSE);

        assertThat(compose)
                .contains("  app:")
                .contains("  redis:")
                .doesNotContain("  db:")
                .doesNotContain("postgres_data")
                .doesNotContain("ALLOW_LOCAL_DOCKER_DB")
                .doesNotContain("5432:5432")
                .doesNotContain("5433:5432");
    }

    @Test
    @DisplayName("운영 app 컨테이너는 runtime env file만 읽고 DB secret을 compose에 직접 inline하지 않는다")
    void prodAppUsesRenderedRuntimeEnvFileWithoutInlineDatabaseSecrets() throws IOException {
        String compose = Files.readString(PROD_COMPOSE);

        assertThat(compose)
                .contains("env_file:")
                .contains("${APP_RUNTIME_ENV_FILE:-.env.runtime.production}")
                .contains("SPRING_PROFILES_ACTIVE: ${SPRING_PROFILES_ACTIVE:-prod}")
                .doesNotContain("DB_URL:")
                .doesNotContain("DB_PASSWORD:")
                .doesNotContain("JWT_SECRET:")
                .doesNotContain("AES_SECRET_KEY:")
                .doesNotContain("OPENAI_API_KEY:");
    }

    @Test
    @DisplayName("운영 app 컨테이너는 loopback bind와 최소 권한 hardening을 유지한다")
    void prodAppContainerKeepsHardeningControls() throws IOException {
        String compose = Files.readString(PROD_COMPOSE);

        assertThat(compose)
                .contains("      - \"127.0.0.1:8082:8080\"")
                .contains("    user: \"10001:10001\"")
                .contains("    init: true")
                .contains("    read_only: true")
                .contains("      - /tmp:size=64m,mode=1777")
                .contains("      - no-new-privileges:true")
                .contains("    cap_drop:")
                .contains("      - ALL")
                .contains("    pids_limit: 256")
                .contains("    mem_limit: 1g")
                .contains("wget -qO- http://127.0.0.1:8080/actuator/health");
    }

    @Test
    @DisplayName("운영 redis 컨테이너는 persistence 없는 cache 전제와 최소 권한 hardening을 유지한다")
    void prodRedisContainerKeepsCacheAndHardeningControls() throws IOException {
        String compose = Files.readString(PROD_COMPOSE);

        assertThat(compose)
                .contains("    command: [\"redis-server\", \"--save\", \"\", \"--appendonly\", \"no\"]")
                .contains("      - \"127.0.0.1:6379:6379\"")
                .contains("    user: \"999:1000\"")
                .contains("    init: true")
                .contains("    read_only: true")
                .contains("      - /data:size=64m,mode=700,uid=999,gid=1000")
                .contains("      - /tmp:size=16m,mode=1777")
                .contains("      - no-new-privileges:true")
                .contains("    cap_drop:")
                .contains("      - ALL")
                .contains("    pids_limit: 128")
                .contains("    mem_limit: 256m");
    }

    @Test
    @DisplayName("운영 Dockerfile runtime stage는 non-root appuser와 tmpdir 경계를 강제한다")
    void backendDockerfileRunsAsNonRootAppUser() throws IOException {
        String dockerfile = Files.readString(DOCKERFILE);

        assertThat(dockerfile)
                .contains("groupadd -r --gid 10001 appuser")
                .contains("useradd -r --uid 10001 --gid appuser --home-dir /app --shell /usr/sbin/nologin appuser")
                .contains("COPY --from=build --chown=appuser:appuser /app/build/libs/*.jar app.jar")
                .contains("USER appuser:appuser")
                .contains("ENTRYPOINT [\"java\", \"-Djava.io.tmpdir=/tmp\", \"-jar\", \"app.jar\"]");
    }
}
