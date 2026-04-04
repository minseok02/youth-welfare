package com.example.welfare.notification.gateway;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmailClient {

    private final JavaMailSender mailSender;

    /**
     * Gmail SMTP 이메일 발송 (카카오 알림톡 실패 시 폴백)
     */
    public boolean send(String to, String subject, String text) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);
            mailSender.send(message);
            log.info("[EmailClient] 발송 성공 to={}", maskEmail(to));
            return true;
        } catch (MailException e) {
            log.error("[EmailClient] 발송 실패 to={}: {}", maskEmail(to), e.getMessage());
            return false;
        }
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "***";
        String local = email.split("@")[0];
        String domain = email.split("@")[1];
        return (local.length() > 2 ? local.substring(0, 2) + "***" : "***") + "@" + domain;
    }
}
