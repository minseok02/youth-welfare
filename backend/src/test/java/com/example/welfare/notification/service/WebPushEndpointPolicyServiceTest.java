package com.example.welfare.notification.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebPushEndpointPolicyServiceTest {

    @Test
    @DisplayName("허용된 https push service endpoint는 통과한다")
    void validateSubscriptionEndpointAcceptsAllowedHttpsHost() throws Exception {
        WebPushEndpointPolicyService service = new WebPushEndpointPolicyService(
                host -> List.of(InetAddress.getByName("142.250.207.74")),
                "fcm.googleapis.com,updates.push.services.mozilla.com,push.apple.com,notify.windows.com"
        );

        service.validateSubscriptionEndpoint("https://fcm.googleapis.com/fcm/send/example");
    }

    @Test
    @DisplayName("http endpoint는 거부한다")
    void validateSubscriptionEndpointRejectsNonHttps() {
        WebPushEndpointPolicyService service = new WebPushEndpointPolicyService(
                host -> List.of(),
                "fcm.googleapis.com"
        );

        assertThatThrownBy(() -> service.validateSubscriptionEndpoint("http://fcm.googleapis.com/fcm/send/example"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("allowlist에 없는 host는 거부한다")
    void validateSubscriptionEndpointRejectsNonAllowlistedHost() {
        WebPushEndpointPolicyService service = new WebPushEndpointPolicyService(
                host -> List.of(),
                "fcm.googleapis.com"
        );

        assertThatThrownBy(() -> service.validateSubscriptionEndpoint("https://evil.example/push"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("allowlist host라도 private address로 resolve되면 거부한다")
    void validateSubscriptionEndpointRejectsPrivateResolvedAddress() throws Exception {
        WebPushEndpointPolicyService service = new WebPushEndpointPolicyService(
                host -> List.of(InetAddress.getByName("127.0.0.1")),
                "fcm.googleapis.com"
        );

        assertThatThrownBy(() -> service.validateSubscriptionEndpoint("https://fcm.googleapis.com/fcm/send/example"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("allowlist host라도 userinfo가 포함된 endpoint는 거부한다")
    void validateSubscriptionEndpointRejectsUserInfo() {
        WebPushEndpointPolicyService service = new WebPushEndpointPolicyService(
                host -> List.of(),
                "fcm.googleapis.com"
        );

        assertThatThrownBy(() -> service.validateSubscriptionEndpoint("https://token@fcm.googleapis.com/fcm/send/example"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("allowlist host라도 fragment가 포함된 endpoint는 거부한다")
    void validateSubscriptionEndpointRejectsFragment() {
        WebPushEndpointPolicyService service = new WebPushEndpointPolicyService(
                host -> List.of(),
                "fcm.googleapis.com"
        );

        assertThatThrownBy(() -> service.validateSubscriptionEndpoint("https://fcm.googleapis.com/fcm/send/example#fragment"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("allowlist host라도 443이 아닌 명시 포트는 거부한다")
    void validateSubscriptionEndpointRejectsNonDefaultExplicitPort() {
        WebPushEndpointPolicyService service = new WebPushEndpointPolicyService(
                host -> List.of(),
                "fcm.googleapis.com"
        );

        assertThatThrownBy(() -> service.validateSubscriptionEndpoint("https://fcm.googleapis.com:8443/fcm/send/example"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("로그용 endpoint 설명은 전체 URL 대신 host만 반환한다")
    void describeEndpointForLogReturnsHostOnly() {
        WebPushEndpointPolicyService service = new WebPushEndpointPolicyService(
                host -> List.of(),
                "fcm.googleapis.com"
        );

        assertThat(service.describeEndpointForLog("https://fcm.googleapis.com/fcm/send/example?token=123"))
                .isEqualTo("fcm.googleapis.com");
    }
}
