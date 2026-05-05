package com.example.welfare.collect.service;

import com.example.welfare.collect.repository.CollectRuntimeStatusCommandRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class CollectRuntimeStatusCommandServiceTest {

    @Mock
    private CollectRuntimeStatusCommandRepository collectRuntimeStatusCommandRepository;

    @InjectMocks
    private CollectRuntimeStatusCommandService collectRuntimeStatusCommandService;

    @Test
    @DisplayName("openCircuit 은 runtime status snapshot upsert 를 위임한다")
    void openCircuitDelegates() {
        LocalDateTime openUntil = LocalDateTime.of(2026, 5, 4, 22, 30);

        collectRuntimeStatusCommandService.openCircuit("BOKJIRO_LOCAL", openUntil);

        ArgumentCaptor<LocalDateTime> updatedAtCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        then(collectRuntimeStatusCommandRepository).should()
                .upsertOpenUntil(org.mockito.ArgumentMatchers.eq("BOKJIRO_LOCAL"),
                        org.mockito.ArgumentMatchers.eq(openUntil),
                        updatedAtCaptor.capture());
        assertThat(updatedAtCaptor.getValue()).isNotNull();
    }
}
