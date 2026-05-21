package com.example.welfare.integration.support;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;

public final class IntegrationRuntimePreflightMain {

    private IntegrationRuntimePreflightMain() {
    }

    public static void main(String[] args) throws Exception {
        List<String> failures = new ArrayList<>();

        String primaryUrl = env("INTEGRATION_DB_URL", "jdbc:postgresql://127.0.0.1:5433/youth_welfare?sslmode=disable");
        String primaryUsername = env("INTEGRATION_DB_USERNAME", "app_core_rw");
        String primaryPassword = env("INTEGRATION_DB_PASSWORD", "");

        String piiUrl = env("INTEGRATION_APP_PII_DB_URL", "jdbc:postgresql://127.0.0.1:5433/youth_welfare?sslmode=disable&currentSchema=youth_welfare_pii");
        String piiUsername = env("INTEGRATION_APP_PII_DB_USERNAME", "app_pii_rw");
        String piiPassword = env("INTEGRATION_APP_PII_DB_PASSWORD", primaryPassword);

        String adminRoUrl = env("INTEGRATION_ADMIN_RO_DB_URL", "jdbc:postgresql://127.0.0.1:5433/youth_welfare?sslmode=disable");
        String adminRoUsername = env("INTEGRATION_ADMIN_RO_DB_USERNAME", "admin_dashboard_ro");
        String adminRoPassword = env("INTEGRATION_ADMIN_RO_DB_PASSWORD", primaryPassword);

        String clusterAiCleanupUrl = env("INTEGRATION_CLUSTER_AI_CLEANUP_DB_URL", "jdbc:postgresql://127.0.0.1:5433/youth_welfare?sslmode=disable");
        String clusterAiCleanupUsername = env("INTEGRATION_CLUSTER_AI_CLEANUP_DB_USERNAME", "cluster_ai_cleanup_rw");
        String clusterAiCleanupPassword = env("INTEGRATION_CLUSTER_AI_CLEANUP_DB_PASSWORD", primaryPassword);

        String recommendationRetentionCleanupUrl = env("INTEGRATION_RECOMMENDATION_RETENTION_CLEANUP_DB_URL", "jdbc:postgresql://127.0.0.1:5433/youth_welfare?sslmode=disable");
        String recommendationRetentionCleanupUsername = env("INTEGRATION_RECOMMENDATION_RETENTION_CLEANUP_DB_USERNAME", "recommendation_retention_cleanup_rw");
        String recommendationRetentionCleanupPassword = env("INTEGRATION_RECOMMENDATION_RETENTION_CLEANUP_DB_PASSWORD", primaryPassword);

        String webPushSubscriptionCleanupUrl = env("INTEGRATION_WEB_PUSH_SUBSCRIPTION_CLEANUP_DB_URL", "jdbc:postgresql://127.0.0.1:5433/youth_welfare?sslmode=disable");
        String webPushSubscriptionCleanupUsername = env("INTEGRATION_WEB_PUSH_SUBSCRIPTION_CLEANUP_DB_USERNAME", "web_push_subscription_cleanup_rw");
        String webPushSubscriptionCleanupPassword = env("INTEGRATION_WEB_PUSH_SUBSCRIPTION_CLEANUP_DB_PASSWORD", primaryPassword);

        String notificationUrl = env("INTEGRATION_NOTIFICATION_PII_DB_URL", "jdbc:postgresql://127.0.0.1:5433/youth_welfare?sslmode=disable&currentSchema=youth_welfare_pii");
        String notificationUsername = env("INTEGRATION_NOTIFICATION_PII_DB_USERNAME", "notification_pii_ro");
        String notificationPassword = env("INTEGRATION_NOTIFICATION_PII_DB_PASSWORD", primaryPassword);

        String redisHost = env("INTEGRATION_REDIS_HOST", "127.0.0.1");
        int redisPort = Integer.parseInt(env("INTEGRATION_REDIS_PORT", "6379"));

        checkJdbc("primary", primaryUrl, primaryUsername, primaryPassword, failures);
        checkJdbc("pii-rw", piiUrl, piiUsername, piiPassword, failures);
        checkJdbc("admin-ro", adminRoUrl, adminRoUsername, adminRoPassword, failures);
        checkJdbc("cluster-ai-cleanup", clusterAiCleanupUrl, clusterAiCleanupUsername, clusterAiCleanupPassword, failures);
        checkJdbc("recommendation-retention-cleanup", recommendationRetentionCleanupUrl, recommendationRetentionCleanupUsername, recommendationRetentionCleanupPassword, failures);
        checkJdbc("web-push-subscription-cleanup", webPushSubscriptionCleanupUrl, webPushSubscriptionCleanupUsername, webPushSubscriptionCleanupPassword, failures);
        checkJdbc("notification-pii-ro", notificationUrl, notificationUsername, notificationPassword, failures);
        checkRedis(redisHost, redisPort, failures);

        if (!failures.isEmpty()) {
            throw new IllegalStateException(String.join(System.lineSeparator(), failures));
        }
    }

    private static void checkJdbc(String label, String url, String username, String password, List<String> failures) {
        try (Connection ignored = DriverManager.getConnection(url, username, password)) {
            // no-op
        } catch (Exception e) {
            failures.add(String.format(
                    "integration runtime preflight failed for %s datasource: url=%s username=%s cause=%s",
                    label,
                    url,
                    username,
                    e.getMessage()
            ));
        }
    }

    private static void checkRedis(String host, int port, List<String> failures) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 1000);
        } catch (Exception e) {
            failures.add(String.format(
                    "integration runtime preflight failed for redis: host=%s port=%d cause=%s",
                    host,
                    port,
                    e.getMessage()
            ));
        }
    }

    private static String env(String key, String defaultValue) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
