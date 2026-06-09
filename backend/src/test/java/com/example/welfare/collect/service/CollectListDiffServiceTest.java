package com.example.welfare.collect.service;

import com.example.welfare.collect.repository.CollectListSnapshotRepository;
import com.example.welfare.policy.entity.WelfareService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class CollectListDiffServiceTest {

    @Mock
    private CollectListSnapshotRepository collectListSnapshotRepository;

    private CollectListDiffService collectListDiffService;

    @BeforeEach
    void setUp() {
        collectListDiffService = new CollectListDiffService(collectListSnapshotRepository, new ObjectMapper());
    }

    @Test
    @DisplayName("이전 snapshot 이 없으면 baseline 으로 저장하고 변경 수는 0으로 둔다")
    void recordSnapshotCreatesBaseline() {
        given(collectListSnapshotRepository.fetchCurrentItems(WelfareService.SourceType.GOV24))
                .willReturn(List.of(
                        new CollectListSnapshotRepository.ListItemFingerprint("g1", "hash-1"),
                        new CollectListSnapshotRepository.ListItemFingerprint("g2", "hash-2")
                ));
        given(collectListSnapshotRepository.findLatestSnapshot(WelfareService.SourceType.GOV24))
                .willReturn(Optional.empty());
        given(collectListSnapshotRepository.saveSnapshot(any(), any())).willReturn(10L);

        CollectListDiffService.CollectListDiff diff =
                collectListDiffService.recordSnapshot(CollectSource.GOV24, CollectResult.of(2, 2, 0, 0, 0));

        assertThat(diff.baseline()).isTrue();
        assertThat(diff.totalCount()).isEqualTo(2);
        assertThat(diff.newCount()).isZero();
        assertThat(diff.changedCount()).isZero();
        assertThat(diff.missingCount()).isZero();
    }

    @Test
    @DisplayName("현재 fingerprint 와 직전 snapshot 을 비교해 신규/변경/누락을 계산한다")
    void recordSnapshotCalculatesDiff() {
        given(collectListSnapshotRepository.fetchCurrentItems(WelfareService.SourceType.BOKJIRO_CENTRAL))
                .willReturn(List.of(
                        new CollectListSnapshotRepository.ListItemFingerprint("same", "hash-a"),
                        new CollectListSnapshotRepository.ListItemFingerprint("changed", "hash-new"),
                        new CollectListSnapshotRepository.ListItemFingerprint("new", "hash-c")
                ));
        given(collectListSnapshotRepository.findLatestSnapshot(WelfareService.SourceType.BOKJIRO_CENTRAL))
                .willReturn(Optional.of(new CollectListSnapshotRepository.ListSnapshot(
                        7L,
                        WelfareService.SourceType.BOKJIRO_CENTRAL,
                        "BOKJIRO_CENTRAL",
                        LocalDateTime.of(2026, 6, 8, 2, 0),
                        3,
                        0,
                        0,
                        0
                )));
        given(collectListSnapshotRepository.fetchSnapshotItems(7L))
                .willReturn(Map.of(
                        "same", "hash-a",
                        "changed", "hash-old",
                        "missing", "hash-b"
                ));
        given(collectListSnapshotRepository.saveSnapshot(any(), any())).willReturn(8L);

        CollectListDiffService.CollectListDiff diff =
                collectListDiffService.recordSnapshot(CollectSource.BOKJIRO_CENTRAL, CollectResult.of(3, 3, 0, 0, 0));

        assertThat(diff.baseline()).isFalse();
        assertThat(diff.previousSnapshotId()).isEqualTo(7L);
        assertThat(diff.newSourceIds()).containsExactly("new");
        assertThat(diff.changedSourceIds()).containsExactly("changed");
        assertThat(diff.missingSourceIds()).containsExactly("missing");

        ArgumentCaptor<CollectListSnapshotRepository.SaveSnapshotCommand> commandCaptor =
                ArgumentCaptor.forClass(CollectListSnapshotRepository.SaveSnapshotCommand.class);
        then(collectListSnapshotRepository).should().saveSnapshot(commandCaptor.capture(), any());
        assertThat(commandCaptor.getValue().newCount()).isEqualTo(1);
        assertThat(commandCaptor.getValue().changedCount()).isEqualTo(1);
        assertThat(commandCaptor.getValue().missingCount()).isEqualTo(1);
        then(collectListSnapshotRepository).should().fetchSnapshotItems(eq(7L));
    }
}
