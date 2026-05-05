package com.example.welfare.collect.service;

import com.example.welfare.collect.repository.CollectRuntimeStatusReadRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class CollectRuntimeStatusServiceTest {

    @Mock
    private CollectRuntimeStatusReadRepository collectRuntimeStatusReadRepository;

    @InjectMocks
    private CollectRuntimeStatusService collectRuntimeStatusService;

    @Test
    @DisplayName("persisted openUntil 이 미래면 circuit 을 open 상태로 계산한다")
    void getCircuitStatusWhenOpen() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 4, 22, 0);
        LocalDateTime openUntil = now.plusMinutes(10);
        given(collectRuntimeStatusReadRepository.findOpenUntil("BOKJIRO_LOCAL"))
                .willReturn(Optional.of(openUntil));

        CollectRuntimeStatusService.CircuitStatusSnapshot snapshot =
                collectRuntimeStatusService.getCircuitStatus("BOKJIRO_LOCAL", now);

        assertThat(snapshot.open()).isTrue();
        assertThat(snapshot.remainingMs()).isEqualTo(600_000L);
        assertThat(snapshot.openUntil()).isEqualTo(openUntil);
    }

    @Test
    @DisplayName("persisted openUntil 이 없거나 과거면 circuit 을 closed 로 계산한다")
    void getCircuitStatusWhenClosed() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 4, 22, 0);
        given(collectRuntimeStatusReadRepository.findOpenUntil("BOKJIRO_LOCAL"))
                .willReturn(Optional.of(now.minusMinutes(1)));

        CollectRuntimeStatusService.CircuitStatusSnapshot snapshot =
                collectRuntimeStatusService.getCircuitStatus("BOKJIRO_LOCAL", now);

        assertThat(snapshot.open()).isFalse();
        assertThat(snapshot.remainingMs()).isZero();
        assertThat(snapshot.openUntil()).isNull();
    }
}
