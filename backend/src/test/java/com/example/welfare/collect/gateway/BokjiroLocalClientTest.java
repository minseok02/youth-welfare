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

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class BokjiroLocalClientTest {

    @Mock
    private WebClient webClient;

    @Mock
    private XmlMapper xmlMapper;

    @Mock
    private CollectHttpRetryExecutor collectHttpRetryExecutor;

    @Mock
    private com.example.welfare.collect.service.CollectRuntimeStatusService collectRuntimeStatusService;

    @Mock
    private com.example.welfare.collect.service.CollectRuntimeStatusCommandService collectRuntimeStatusCommandService;

    private BokjiroLocalClient client;

    @BeforeEach
    void setUp() {
        client = new BokjiroLocalClient(
                webClient,
                xmlMapper,
                collectHttpRetryExecutor,
                collectRuntimeStatusService,
                collectRuntimeStatusCommandService
        );
        ReflectionTestUtils.setField(client, "localRateLimitOpenCircuitMs", 1_800_000L);
    }

    @Test
    @DisplayName("최근 연속 429로 열린 local rate-limit circuit이 남아 있으면 요청 없이 즉시 COL001로 중단한다")
    void fetchAllShortCircuitsWhenRateLimitCircuitIsOpen() {
        given(collectRuntimeStatusService.getCircuitStatus(
                eq(com.example.welfare.collect.service.CollectRuntimeStatusService.BOKJIRO_LOCAL_CIRCUIT_KEY),
                any(LocalDateTime.class)
        )).willReturn(new com.example.welfare.collect.service.CollectRuntimeStatusService.CircuitStatusSnapshot(
                "BOKJIRO_LOCAL",
                true,
                60_000L,
                LocalDateTime.now().plusMinutes(1)
        ));

        assertThatThrownBy(() -> client.fetchAll())
                .isInstanceOf(CustomException.class)
                .satisfies(ex -> assertThat(((CustomException) ex).getErrorCode()).isEqualTo(ErrorCode.COLLECT_API_FAILED));

        verifyNoInteractions(webClient, xmlMapper);
    }

    @Test
    @DisplayName("rate-limit circuit status 조회는 persisted runtime snapshot을 그대로 노출한다")
    void getRateLimitCircuitStatusReadsPersistedSnapshot() {
        LocalDateTime openUntil = LocalDateTime.now().plusMinutes(30);
        given(collectRuntimeStatusService.getCircuitStatus(
                eq(com.example.welfare.collect.service.CollectRuntimeStatusService.BOKJIRO_LOCAL_CIRCUIT_KEY),
                any(LocalDateTime.class)
        )).willReturn(new com.example.welfare.collect.service.CollectRuntimeStatusService.CircuitStatusSnapshot(
                "BOKJIRO_LOCAL",
                true,
                1_800_000L,
                openUntil
        ));

        BokjiroLocalClient.RateLimitCircuitStatus status = client.getRateLimitCircuitStatus();

        assertThat(status.open()).isTrue();
        assertThat(status.remainingMs()).isEqualTo(1_800_000L);
        assertThat(status.openUntil()).isEqualTo(openUntil);
    }
}
