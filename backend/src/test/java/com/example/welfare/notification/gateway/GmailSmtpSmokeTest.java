package com.example.welfare.notification.gateway;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.time.LocalDateTime;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class GmailSmtpSmokeTest {

    @Test
    @EnabledIfEnvironmentVariable(named = "RUN_SMTP_SMOKE", matches = "true")
    void sendSmokeEmailThroughConfiguredSmtp() {
        String host = envOrDefault("MAIL_HOST", "smtp.gmail.com");
        int port = Integer.parseInt(envOrDefault("MAIL_PORT", "587"));
        String username = requiredEnvOrFallback("MAIL_USERNAME", "GMAIL_USERNAME");
        String password = requiredEnvOrFallback("MAIL_PASSWORD", "GMAIL_PASSWORD");
        String to = envOrDefault("SMTP_SMOKE_TO", username);

        EmailClient emailClient = new EmailClient(mailSender(username, password));

        boolean sent = emailClient.send(
                to,
                "[SMOKE TEST] youth-welfare SMTP",
                "SMTP smoke test sent at " + LocalDateTime.now()
        );

        assertThat(sent).isTrue();
    }

    private JavaMailSenderImpl mailSender(String username, String password) {
        return mailSender(
                envOrDefault("MAIL_HOST", "smtp.gmail.com"),
                Integer.parseInt(envOrDefault("MAIL_PORT", "587")),
                username,
                password
        );
    }

    private JavaMailSenderImpl mailSender(String host, int port, String username, String password) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host);
        sender.setPort(port);
        sender.setUsername(username);
        sender.setPassword(password);

        Properties properties = sender.getJavaMailProperties();
        properties.put("mail.smtp.auth", "true");
        properties.put("mail.smtp.starttls.enable", "true");
        properties.put("mail.smtp.connectiontimeout", "10000");
        properties.put("mail.smtp.timeout", "10000");
        properties.put("mail.smtp.writetimeout", "10000");

        return sender;
    }

    private String requiredEnvOrFallback(String primaryName, String legacyName) {
        String primary = System.getenv(primaryName);
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        return requiredEnv(legacyName);
    }

    private String requiredEnv(String name) {
        String value = System.getenv(name);
        assertThat(value)
                .as(name + " environment variable")
                .isNotBlank();
        return value;
    }

    private String envOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
