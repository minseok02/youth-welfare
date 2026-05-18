package com.example.welfare.notification.gateway;

import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
public class EmailClient {

    private final JavaMailSender mailSender;
    private final MailDeliveryProperties mailDeliveryProperties;

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
            log.info("[EmailClient] 발송 성공 provider={} to={}",
                    mailDeliveryProperties.getProvider(),
                    maskEmail(to));
            return true;
        } catch (MailException e) {
            log.error("[EmailClient] 발송 실패 provider={} to={}: {}",
                    mailDeliveryProperties.getProvider(),
                    maskEmail(to),
                    e.getMessage());
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
