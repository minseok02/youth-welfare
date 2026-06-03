package com.example.welfare.collect.service;

import com.example.welfare.collect.dto.Gov24ServiceListDto;
import com.example.welfare.collect.gateway.Gov24Client;
import com.example.welfare.collect.mapper.WelfareServiceMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class Gov24CollectSourceAdapterTest {

    @Mock
    private Gov24Client gov24Client;
    @Mock
    private WelfareServiceMapper welfareServiceMapper;
    @Mock
    private CollectItemSaver saver;
    @Mock
    private RawApiPayloadService rawApiPayloadService;
    @Mock
    private CollectListResponsePolicy collectListResponsePolicy;
    @Mock
    private Gov24StaleServiceCleanupService gov24StaleServiceCleanupService;

    private Gov24CollectSourceAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new Gov24CollectSourceAdapter(
                gov24Client,
                welfareServiceMapper,
                saver,
                rawApiPayloadService,
                collectListResponsePolicy,
                gov24StaleServiceCleanupService,
                new ObjectMapper()
        );
        ReflectionTestUtils.setField(adapter, "chunkSize", 2);
        ReflectionTestUtils.setField(adapter, "chunkPauseMs", 0L);
    }

    @Test
    @DisplayName("Gov24 list 수집은 chunk 단위로 저장을 호출하고 최종 결과를 합산한다")
    void collectProcessesGov24InChunksAndAggregatesResult() {
        Gov24ServiceListDto.Item item1 = gov24Item("S1", "서비스 1");
        Gov24ServiceListDto.Item item2 = gov24Item("S2", "서비스 2");
        Gov24ServiceListDto.Item item3 = gov24Item("S3", "서비스 3");

        when(gov24Client.fetchChunk(1, 2))
                .thenReturn(new Gov24Client.PageChunk(1, 2, 3, 2, List.of(item1, item2)));
        when(gov24Client.fetchChunk(2, 2))
                .thenReturn(new Gov24Client.PageChunk(2, 2, 3, 1, List.of(item3)));
        when(rawApiPayloadService.saveList(any(), eq(item1))).thenReturn(true);
        when(rawApiPayloadService.saveList(any(), eq(item2))).thenReturn(true);
        when(rawApiPayloadService.saveList(any(), eq(item3))).thenReturn(true);
        when(gov24StaleServiceCleanupService.cleanupAgainstLiveInventory(List.of("S1", "S2", "S3")))
                .thenReturn(new Gov24StaleServiceCleanupService.CleanupResult(
                        false, "DELETED", 3, 3, 0, 0, List.of()
                ));

        CollectResult result = adapter.collect();

        assertThat(result.requestedCount()).isEqualTo(3);
        assertThat(result.savedCount()).isEqualTo(3);
        assertThat(result.skippedCount()).isZero();
        assertThat(result.filteredCount()).isZero();
        assertThat(result.failedCount()).isZero();
        assertThat(result.metadataJson()).contains("\"chunkSize\":2");
        assertThat(result.metadataJson()).contains("\"chunkCount\":2");
        assertThat(result.metadataJson()).contains("\"staleDeletedCount\":0");
        verify(gov24Client).fetchChunk(1, 2);
        verify(gov24Client).fetchChunk(2, 2);
        verify(rawApiPayloadService, times(3)).saveList(any(), any(Gov24ServiceListDto.Item.class));
        verify(saver, times(3)).save(any(), any(Gov24ServiceListDto.Item.class));
        verify(gov24StaleServiceCleanupService).cleanupAgainstLiveInventory(List.of("S1", "S2", "S3"));
    }

    @Test
    @DisplayName("stale cleanup은 전체 chunk 성공 후 1회만 실행된다")
    void collectRunsStaleCleanupOnlyOnceAfterSuccess() {
        Gov24ServiceListDto.Item item1 = gov24Item("S1", "서비스 1");
        Gov24ServiceListDto.Item item2 = gov24Item("S2", "서비스 2");

        when(gov24Client.fetchChunk(1, 2))
                .thenReturn(new Gov24Client.PageChunk(1, 2, 2, 2, List.of(item1, item2)));
        when(rawApiPayloadService.saveList(any(), eq(item1))).thenReturn(true);
        when(rawApiPayloadService.saveList(any(), eq(item2))).thenReturn(true);
        when(gov24StaleServiceCleanupService.cleanupAgainstLiveInventory(List.of("S1", "S2")))
                .thenReturn(new Gov24StaleServiceCleanupService.CleanupResult(
                        false, "DELETED", 2, 2, 0, 0, List.of()
                ));

        adapter.collect();

        verify(gov24StaleServiceCleanupService, times(1))
                .cleanupAgainstLiveInventory(List.of("S1", "S2"));
    }

    @Test
    @DisplayName("중간 chunk fetch 실패 시 stale cleanup은 실행되지 않는다")
    void collectDoesNotRunStaleCleanupWhenChunkFails() {
        Gov24ServiceListDto.Item item1 = gov24Item("S1", "서비스 1");
        Gov24ServiceListDto.Item item2 = gov24Item("S2", "서비스 2");

        when(gov24Client.fetchChunk(1, 2))
                .thenReturn(new Gov24Client.PageChunk(1, 2, 4, 2, List.of(item1, item2)));
        when(gov24Client.fetchChunk(2, 2))
                .thenThrow(new IllegalStateException("chunk 2 failed"));
        when(rawApiPayloadService.saveList(any(), eq(item1))).thenReturn(true);
        when(rawApiPayloadService.saveList(any(), eq(item2))).thenReturn(true);

        assertThatThrownBy(() -> adapter.collect())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("chunk 2 failed");

        verify(gov24StaleServiceCleanupService, never()).cleanupAgainstLiveInventory(any());
    }

    @Test
    @DisplayName("chunk 결과의 saved/skipped/failed count를 최종 CollectResult에 합산한다")
    void collectAggregatesChunkCounts() {
        Gov24ServiceListDto.Item item1 = gov24Item("S1", "서비스 1");
        Gov24ServiceListDto.Item item2 = gov24Item("S2", "");
        Gov24ServiceListDto.Item item3 = gov24Item("S3", "서비스 3");

        when(gov24Client.fetchChunk(1, 2))
                .thenReturn(new Gov24Client.PageChunk(1, 2, 3, 2, List.of(item1, item2)));
        when(gov24Client.fetchChunk(2, 2))
                .thenReturn(new Gov24Client.PageChunk(2, 2, 3, 1, List.of(item3)));
        when(rawApiPayloadService.saveList(any(), eq(item1))).thenReturn(true);
        when(rawApiPayloadService.saveList(any(), eq(item2))).thenReturn(true);
        when(rawApiPayloadService.saveList(any(), eq(item3))).thenReturn(false);
        when(gov24StaleServiceCleanupService.cleanupAgainstLiveInventory(List.of("S1", "S2", "S3")))
                .thenReturn(new Gov24StaleServiceCleanupService.CleanupResult(
                        false, "DELETED", 3, 3, 0, 0, List.of()
                ));

        CollectResult result = adapter.collect();

        assertThat(result.requestedCount()).isEqualTo(3);
        assertThat(result.savedCount()).isEqualTo(1);
        assertThat(result.skippedCount()).isEqualTo(1);
        assertThat(result.failedCount()).isEqualTo(1);
    }

    private Gov24ServiceListDto.Item gov24Item(String serviceId, String serviceName) {
        Gov24ServiceListDto.Item item = new Gov24ServiceListDto.Item();
        ReflectionTestUtils.setField(item, "serviceId", serviceId);
        ReflectionTestUtils.setField(item, "serviceName", serviceName);
        return item;
    }
}
