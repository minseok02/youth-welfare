package com.example.welfare.collect.gateway;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class BokjiroLocalClientTest {

    @Mock
    private WebClient webClient;

    @Mock
    private XmlMapper xmlMapper;

    private BokjiroLocalClient client;

    @BeforeEach
    void setUp() {
        client = new BokjiroLocalClient(webClient, xmlMapper);
        ReflectionTestUtils.setField(client, "localRateLimitOpenCircuitMs", 1_800_000L);
    }

    @Test
    @DisplayName("최근 연속 429로 열린 local rate-limit circuit이 남아 있으면 요청 없이 즉시 COL001로 중단한다")
    void fetchAllShortCircuitsWhenRateLimitCircuitIsOpen() {
        AtomicLong openUntil = (AtomicLong) ReflectionTestUtils.getField(client, "rateLimitCircuitOpenUntilEpochMs");
        openUntil.set(System.currentTimeMillis() + 60_000L);

        assertThatThrownBy(() -> client.fetchAll())
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode()).isEqualTo(ErrorCode.COLLECT_API_FAILED));

        verifyNoInteractions(webClient, xmlMapper);
    }
}
