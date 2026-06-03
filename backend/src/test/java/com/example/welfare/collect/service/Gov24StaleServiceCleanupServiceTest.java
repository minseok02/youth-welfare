package com.example.welfare.collect.service;

import com.example.welfare.collect.repository.RawApiPayloadRepository;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.WelfareServiceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class Gov24StaleServiceCleanupServiceTest {

    private final WelfareServiceRepository welfareServiceRepository = mock(WelfareServiceRepository.class);
    private final RawApiPayloadRepository rawApiPayloadRepository = mock(RawApiPayloadRepository.class);
    private final Gov24StaleServiceCleanupService service =
            new Gov24StaleServiceCleanupService(welfareServiceRepository, rawApiPayloadRepository);

    @Test
    @DisplayName("live inventory에 없는 Gov24 sourceId는 raw와 service에서 함께 삭제한다")
    void cleanupDeletesSmallStaleSet() {
        List<String> existing = new java.util.ArrayList<>();
        existing.add("C");
        existing.add("A");
        existing.add("B");
        for (int i = 0; i < 10_000; i++) {
            existing.add("LIVE-" + i);
        }
        given(welfareServiceRepository.findSourceIdsBySourceType(WelfareService.SourceType.GOV24))
                .willReturn(existing);
        given(welfareServiceRepository.deleteBySourceTypeAndSourceIdIn(WelfareService.SourceType.GOV24, List.of("C")))
                .willReturn(1);
        List<String> live = new java.util.ArrayList<>();
        live.add("A");
        live.add("B");
        for (int i = 0; i < 10_000; i++) {
            live.add("LIVE-" + i);
        }

        Gov24StaleServiceCleanupService.CleanupResult result =
                service.cleanupAgainstLiveInventory(live);

        assertThat(result.skipped()).isFalse();
        assertThat(result.staleCount()).isEqualTo(1);
        assertThat(result.deletedCount()).isEqualTo(1);
        assertThat(result.staleSourceIds()).containsExactly("C");
        verify(rawApiPayloadRepository).deleteBySourceTypeAndSourceIdIn(WelfareService.SourceType.GOV24, List.of("C"));
        verify(welfareServiceRepository).deleteBySourceTypeAndSourceIdIn(WelfareService.SourceType.GOV24, List.of("C"));
    }

    @Test
    @DisplayName("live inventory가 안전 기준보다 작으면 stale 삭제를 건너뛴다")
    void cleanupSkipsWhenLiveInventoryLooksUnsafe() {
        given(welfareServiceRepository.findSourceIdsBySourceType(WelfareService.SourceType.GOV24))
                .willReturn(List.of("A", "B", "C"));

        Gov24StaleServiceCleanupService.CleanupResult result =
                service.cleanupAgainstLiveInventory(List.of("A", "B"));

        assertThat(result.skipped()).isTrue();
        assertThat(result.reason()).isEqualTo("LIVE_COUNT_BELOW_THRESHOLD");
        verify(rawApiPayloadRepository, never()).deleteBySourceTypeAndSourceIdIn(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyList());
        verify(welfareServiceRepository, never()).deleteBySourceTypeAndSourceIdIn(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    @DisplayName("stale 삭제량이 guard를 넘으면 대량 삭제를 막고 건너뛴다")
    void cleanupSkipsWhenStaleDeleteGuardTrips() {
        List<String> existing = java.util.stream.IntStream.range(0, 110)
                .mapToObj(i -> "S" + i)
                .toList();
        List<String> live = java.util.stream.IntStream.range(0, 10000)
                .mapToObj(i -> "L" + i)
                .toList();
        given(welfareServiceRepository.findSourceIdsBySourceType(WelfareService.SourceType.GOV24))
                .willReturn(existing);

        Gov24StaleServiceCleanupService.CleanupResult result =
                service.cleanupAgainstLiveInventory(live);

        assertThat(result.skipped()).isTrue();
        assertThat(result.reason()).isEqualTo("STALE_DELETE_GUARD");
        assertThat(result.staleCount()).isEqualTo(110);
        verify(rawApiPayloadRepository, never()).deleteBySourceTypeAndSourceIdIn(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyList());
        verify(welfareServiceRepository, never()).deleteBySourceTypeAndSourceIdIn(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyList());
    }
}
