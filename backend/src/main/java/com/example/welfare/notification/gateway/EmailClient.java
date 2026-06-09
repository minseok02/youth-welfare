package com.example.welfare.notification.gateway;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Slf4j
@Component
public class EmailClient {

    private final JavaMailSender mailSender;
    private final MailDeliveryProperties mailDeliveryProperties;

    @Autowired
    public EmailClient(JavaMailSender mailSender, MailDeliveryProperties mailDeliveryProperties) {
        this.mailSender = mailSender;
        this.mailDeliveryProperties = mailDeliveryProperties;
    }

    EmailClient(JavaMailSender mailSender) {
        this(mailSender, MailDeliveryProperties.defaults());
    }

    /**
     * SMTP 이메일 발송.
     */
    public boolean send(String to, String subject, String text) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);
            if (StringUtils.hasText(mailDeliveryProperties.getFromAddress())) {
                message.setFrom(mailDeliveryProperties.getFromAddress());
            }
            if (StringUtils.hasText(mailDeliveryProperties.getReplyTo())) {
                message.setReplyTo(mailDeliveryProperties.getReplyTo());
            }
            mailSender.send(message);
            log.info("[EmailClient] 발송 성공 provider={} recipientRef={}",
                    mailDeliveryProperties.getProvider(),
                    fingerprintEmail(to));
            return true;
        } catch (MailException e) {
            log.error("[EmailClient] 발송 실패 provider={} recipientRef={} errorType={}",
                    mailDeliveryProperties.getProvider(),
                    fingerprintEmail(to),
                    e.getClass().getSimpleName());
            return false;
        }
    }

    String fingerprintEmail(String email) {
        if (!StringUtils.hasText(email)) {
            return "unknown";
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(email.trim().toLowerCase().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 6; i++) {
                hex.append(String.format("%02x", digest[i]));
            }
            return "sha256:" + hex;
        } catch (NoSuchAlgorithmException e) {
            return "unavailable";
        }
    }
}
