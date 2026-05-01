package com.example.welfare.collect.service;

import com.example.welfare.collect.normalization.NormalizedPolicyAggregate;
import com.example.welfare.collect.support.ListCollectSourceBinding;
import com.example.welfare.collect.validation.FieldQualityStats;
import com.example.welfare.policy.entity.ServiceRegion;
import com.example.welfare.policy.entity.ServiceTag;
import com.example.welfare.policy.entity.WelfareService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;

class SyntheticListCollectSourceAdapterTest {

    @Test
    @DisplayName("abstract list adapter 는 synthetic item binding 으로 raw/save generic 경계를 그대로 재사용한다")
    void syntheticAdapterReusesGenericRawAndSaveBoundaries() {
        CollectItemSaver saver = mock(CollectItemSaver.class);
        RawApiPayloadService rawApiPayloadService = mock(RawApiPayloadService.class);
        SyntheticItem item = new SyntheticItem("Y-SYN-ADAPTER-1", "합성 adapter 정책", true);
        ListCollectSourceBinding<SyntheticItem> binding = new ListCollectSourceBinding<>(
                WelfareService.SourceType.YOUTH,
                SyntheticItem::sourceId,
                (items, stats) -> stats.record("syntheticId", items.isEmpty() ? null : items.get(0).sourceId()),
                current -> WelfareService.builder()
                        .sourceType(WelfareService.SourceType.YOUTH)
                        .sourceId(current.sourceId())
                        .title(current.title())
                        .status(WelfareService.ServiceStatus.ACTIVE)
                        .build(),
                (current, entity) -> List.<ServiceRegion>of(),
                (current, entity) -> List.<ServiceTag>of(),
                current -> NormalizedPolicyAggregate.builder()
                        .core(NormalizedPolicyAggregate.Core.builder()
                                .sourceType(NormalizedPolicyAggregate.SourceType.YOUTH)
                                .sourceId(current.sourceId())
                                .title(current.title())
                                .status(NormalizedPolicyAggregate.ServiceStatus.ACTIVE)
                                .build())
                        .build()
        );
        SyntheticListCollectSourceAdapter adapter = new SyntheticListCollectSourceAdapter(
                List.of(item),
                binding,
                saver,
                rawApiPayloadService
        );

        CollectResult result = adapter.collect();

        assertThat(result.requestedCount()).isEqualTo(1);
        assertThat(result.savedCount()).isEqualTo(1);
        assertThat(result.skippedCount()).isZero();
        assertThat(result.filteredCount()).isZero();
        assertThat(result.failedCount()).isZero();
        verify(rawApiPayloadService).saveList(same(binding), same(item));
        verify(saver).save(same(binding), same(item));
    }

    private record SyntheticItem(String sourceId, String title, boolean valid) {
    }

    private static final class SyntheticListCollectSourceAdapter extends AbstractListCollectSourceAdapter<SyntheticItem> {

        private final List<SyntheticItem> items;
        private final ListCollectSourceBinding<SyntheticItem> binding;
        private final CollectItemSaver saver;
        private final RawApiPayloadService rawApiPayloadService;

        private SyntheticListCollectSourceAdapter(List<SyntheticItem> items,
                                                  ListCollectSourceBinding<SyntheticItem> binding,
                                                  CollectItemSaver saver,
                                                  RawApiPayloadService rawApiPayloadService) {
            this.items = items;
            this.binding = binding;
            this.saver = saver;
            this.rawApiPayloadService = rawApiPayloadService;
        }

        @Override
        public CollectSource source() {
            return CollectSource.YOUTH;
        }

        @Override
        protected List<SyntheticItem> fetchItems() {
            return items;
        }

        @Override
        protected void recordStats(List<SyntheticItem> items, FieldQualityStats stats) {
            binding.recordStats(items, stats);
        }

        @Override
        protected void saveRawPayload(SyntheticItem item) {
            rawApiPayloadService.saveList(binding, item);
        }

        @Override
        protected boolean isValid(SyntheticItem item) {
            return item.valid();
        }

        @Override
        protected void saveItem(SyntheticItem item) {
            saver.save(binding, item);
        }

        @Override
        protected String itemId(SyntheticItem item) {
            return item.sourceId();
        }

        @Override
        protected String failureIdLabel() {
            return "syntheticId";
        }
    }
}
