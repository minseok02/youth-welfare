package com.example.welfare.notification.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmailClientTest {

    @Mock
    private JavaMailSender javaMailSender;

    @Test
    @DisplayName("메일 발송은 provider-neutral 설정의 from/reply-to 를 메시지에 반영한다")
    void send_appliesFromAndReplyTo() {
        MailDeliveryProperties properties = new MailDeliveryProperties();
        properties.setProvider("smtp");
        properties.setFromAddress("noreply@youthwelfare.app");
        properties.setReplyTo("help@youthwelfare.app");
        EmailClient emailClient = new EmailClient(javaMailSender, properties);

        boolean sent = emailClient.send("user@example.com", "subject", "body");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(javaMailSender).send(captor.capture());
        SimpleMailMessage message = captor.getValue();
        assertThat(sent).isTrue();
        assertThat(message.getFrom()).isEqualTo("noreply@youthwelfare.app");
        assertThat(message.getReplyTo()).isEqualTo("help@youthwelfare.app");
        assertThat(message.getTo()).containsExactly("user@example.com");
        assertThat(message.getSubject()).isEqualTo("subject");
        assertThat(message.getText()).isEqualTo("body");
    }

    @Test
    @DisplayName("메일 발송 실패는 false 를 반환한다")
    void send_returnsFalseOnMailException() {
        MailDeliveryProperties properties = new MailDeliveryProperties();
        properties.setProvider("smtp");
        EmailClient emailClient = new EmailClient(javaMailSender, properties);
        doThrow(new MailSendException("boom")).when(javaMailSender).send(any(SimpleMailMessage.class));

        boolean sent = emailClient.send("user@example.com", "subject", "body");

        assertThat(sent).isFalse();
    }

    @Test
    @DisplayName("메일 로그 식별자는 원본 이메일과 도메인을 노출하지 않는 해시 지문이다")
    void fingerprintEmailDoesNotExposeAddress() {
        EmailClient emailClient = new EmailClient(javaMailSender, MailDeliveryProperties.defaults());

        String fingerprint = emailClient.fingerprintEmail("User@Example.com");

        assertThat(fingerprint).startsWith("sha256:");
        assertThat(fingerprint).doesNotContain("User", "user", "Example", "example", "@");
        assertThat(fingerprint).isEqualTo(emailClient.fingerprintEmail(" user@example.com "));
    }
}
