package com.example.welfare.collect.service;

import com.example.welfare.collect.dto.YouthApiDto;
import com.example.welfare.collect.mapper.WelfareServiceMapper;
import com.example.welfare.collect.normalization.NormalizedPolicyAggregate;
import com.example.welfare.collect.normalization.NormalizedPolicySidecarWriter;
import com.example.welfare.collect.repository.CollectItemCommandRepository;
import com.example.welfare.collect.repository.CollectItemReadRepository;
import com.example.welfare.collect.repository.CollectItemTagCommandRepository;
import com.example.welfare.collect.support.ListCollectSourceBinding;
import com.example.welfare.collect.support.ListCollectSourceBindings;
import com.example.welfare.policy.entity.ServiceRegion;
import com.example.welfare.policy.entity.ServiceTag;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.service.SearchYouthRelevanceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CollectItemSaverTest {

    @Mock
    private WelfareServiceMapper mapper;
    @Mock
    private CollectItemReadRepository collectItemReadRepository;
    @Mock
    private CollectItemCommandRepository collectItemCommandRepository;
    @Mock
    private CollectItemTagCommandRepository collectItemTagCommandRepository;
    @Mock
    private PlatformTransactionManager transactionManager;
    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private SearchYouthRelevanceService searchYouthRelevanceService;
    @Mock
    private NormalizedPolicySidecarWriter normalizedPolicySidecarWriter;

    private CollectItemSaver saver;

    @BeforeEach
    void setUp() {
        saver = new CollectItemSaver(
                mapper,
                collectItemReadRepository,
                collectItemCommandRepository,
                collectItemTagCommandRepository,
                transactionManager,
                jdbcTemplate,
                searchYouthRelevanceService,
                normalizedPolicySidecarWriter
        );
    }

    @Test
    @DisplayName("saveYouthOnce 는 기존 tag 를 지우고 현재 tag 집합만 중복 없이 다시 저장한다")
    void saveYouthOnceReplacesTags() {
        YouthApiDto.Item item = youthItem("Y-1");
        ListCollectSourceBinding<YouthApiDto.Item> binding = ListCollectSourceBindings.youth(mapper);
        WelfareService existing = WelfareService.builder()
                .id(11L)
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId("Y-1")
                .title("old")
                .status(WelfareService.ServiceStatus.ACTIVE)
                .build();
        WelfareService incoming = WelfareService.builder()
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId("Y-1")
                .title("new")
                .status(WelfareService.ServiceStatus.ACTIVE)
                .build();

        ServiceTag first = ServiceTag.builder()
                .service(existing)
                .tagType(ServiceTag.TagType.KEYWORD)
                .tagValue("청년")
                .build();
        ServiceTag duplicate = ServiceTag.builder()
                .service(existing)
                .tagType(ServiceTag.TagType.KEYWORD)
                .tagValue("청년")
                .build();
        ServiceTag second = ServiceTag.builder()
                .service(existing)
                .tagType(ServiceTag.TagType.TARGET_GROUP)
                .tagValue("대학생")
                .build();

        given(collectItemReadRepository.findServiceBySourceTypeAndSourceId(WelfareService.SourceType.YOUTH, "Y-1"))
                .willReturn(Optional.of(existing));
        given(mapper.fromYouth(item)).willReturn(incoming);
        given(mapper.regionsFromYouth(item, existing)).willReturn(List.<ServiceRegion>of());
        given(mapper.tagsFromYouth(item, existing)).willReturn(List.of(first, duplicate, second));

        saver.saveOnce(binding, item);

        ArgumentCaptor<List<ServiceTag>> tagsCaptor = ArgumentCaptor.forClass(List.class);
        verify(collectItemTagCommandRepository).replaceAll(eq(11L), tagsCaptor.capture());
        List<ServiceTag> savedTags = tagsCaptor.getValue();
        assertThat(savedTags).hasSize(2);
        assertThat(savedTags)
                .extracting(ServiceTag::getTagType, ServiceTag::getTagValue)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(ServiceTag.TagType.KEYWORD, "청년"),
                        org.assertj.core.groups.Tuple.tuple(ServiceTag.TagType.TARGET_GROUP, "대학생")
                );
        assertThat(savedTags).allMatch(tag -> tag.getService() == existing);

        verify(searchYouthRelevanceService).refreshForService(eq(existing), eq(savedTags));
    }

    @Test
    @DisplayName("saveYouthOnce 는 현재 tag 가 비어 있으면 기존 tag 만 정리하고 저장은 생략한다")
    void saveYouthOnceClearsTagsWhenEmpty() {
        YouthApiDto.Item item = youthItem("Y-2");
        ListCollectSourceBinding<YouthApiDto.Item> binding = ListCollectSourceBindings.youth(mapper);
        WelfareService existing = WelfareService.builder()
                .id(22L)
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId("Y-2")
                .title("old")
                .status(WelfareService.ServiceStatus.ACTIVE)
                .build();
        WelfareService incoming = WelfareService.builder()
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId("Y-2")
                .title("new")
                .status(WelfareService.ServiceStatus.ACTIVE)
                .build();

        given(collectItemReadRepository.findServiceBySourceTypeAndSourceId(WelfareService.SourceType.YOUTH, "Y-2"))
                .willReturn(Optional.of(existing));
        given(mapper.fromYouth(item)).willReturn(incoming);
        given(mapper.regionsFromYouth(item, existing)).willReturn(List.<ServiceRegion>of());
        given(mapper.tagsFromYouth(item, existing)).willReturn(List.of());

        saver.saveOnce(binding, item);

        verify(collectItemTagCommandRepository).replaceAll(22L, List.of());
        verify(searchYouthRelevanceService).refreshForService(eq(existing), eq(List.of()));
    }

    @Test
    @DisplayName("aggregate 병행 저장 경로는 source identity mismatch 를 거부한다")
    void saveYouthOnceRejectsAggregateSourceMismatch() {
        YouthApiDto.Item item = youthItem("Y-3");
        ListCollectSourceBinding<YouthApiDto.Item> binding = ListCollectSourceBindings.youth(mapper);
        WelfareService incoming = WelfareService.builder()
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId("Y-3")
                .title("new")
                .status(WelfareService.ServiceStatus.ACTIVE)
                .build();

        given(mapper.fromYouth(item)).willReturn(incoming);

        assertThatThrownBy(() -> saver.saveOnce(binding, item, normalizedAggregate("WRONG")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("source identity");
    }

    @Test
    @DisplayName("aggregate 병행 저장 경로는 future sidecar writer 에 canonical aggregate 를 전달한다")
    void saveYouthOncePassesAggregateToSidecarWriter() {
        YouthApiDto.Item item = youthItem("Y-4");
        ListCollectSourceBinding<YouthApiDto.Item> binding = ListCollectSourceBindings.youth(mapper);
        WelfareService existing = WelfareService.builder()
                .id(44L)
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId("Y-4")
                .title("old")
                .status(WelfareService.ServiceStatus.ACTIVE)
                .build();
        WelfareService incoming = WelfareService.builder()
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId("Y-4")
                .title("new")
                .status(WelfareService.ServiceStatus.ACTIVE)
                .build();
        NormalizedPolicyAggregate aggregate = normalizedAggregate("Y-4");

        given(collectItemReadRepository.findServiceBySourceTypeAndSourceId(WelfareService.SourceType.YOUTH, "Y-4"))
                .willReturn(Optional.of(existing));
        given(mapper.fromYouth(item)).willReturn(incoming);
        given(mapper.regionsFromYouth(item, existing)).willReturn(List.of());
        given(mapper.tagsFromYouth(item, existing)).willReturn(List.of());

        saver.saveOnce(binding, item, aggregate);

        verify(normalizedPolicySidecarWriter).upsert(existing, aggregate);
    }

    @Test
    @DisplayName("generic save command 는 synthetic item aggregate 도 source-specific DTO 없이 저장 경계를 재사용한다")
    void saveOnceSupportsSyntheticBindingCommand() {
        SyntheticItem item = new SyntheticItem("Y-SYN-SAVE-1", "합성 소스 정책");
        WelfareService existing = WelfareService.builder()
                .id(55L)
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId(item.sourceId())
                .title("old")
                .status(WelfareService.ServiceStatus.ACTIVE)
                .build();
        WelfareService incoming = WelfareService.builder()
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId(item.sourceId())
                .title(item.title())
                .status(WelfareService.ServiceStatus.ACTIVE)
                .build();
        NormalizedPolicyAggregate aggregate = normalizedAggregate(item.sourceId());
        ListCollectSourceBinding<SyntheticItem> binding = new ListCollectSourceBinding<>(
                WelfareService.SourceType.YOUTH,
                SyntheticItem::sourceId,
                (items, stats) -> {
                },
                ignored -> incoming,
                (ignored, entity) -> List.<ServiceRegion>of(),
                (ignored, entity) -> List.of(ServiceTag.builder()
                        .service(entity)
                        .tagType(ServiceTag.TagType.KEYWORD)
                        .tagValue("합성")
                        .build()),
                ignored -> aggregate
        );

        given(collectItemReadRepository.findServiceBySourceTypeAndSourceId(WelfareService.SourceType.YOUTH, item.sourceId()))
                .willReturn(Optional.of(existing));

        saver.saveOnce(binding.toSaveCommand(item));

        verify(normalizedPolicySidecarWriter).upsert(existing, aggregate);
        verify(collectItemTagCommandRepository).replaceAll(eq(55L), any());
        verify(searchYouthRelevanceService).refreshForService(eq(existing), any());
    }

    private YouthApiDto.Item youthItem(String plcyNo) {
        YouthApiDto.Item item = new YouthApiDto.Item();
        ReflectionTestUtils.setField(item, "plcyNo", plcyNo);
        return item;
    }

    private NormalizedPolicyAggregate normalizedAggregate(String sourceId) {
        return NormalizedPolicyAggregate.builder()
                .core(NormalizedPolicyAggregate.Core.builder()
                        .sourceType(NormalizedPolicyAggregate.SourceType.YOUTH)
                        .sourceId(sourceId)
                        .title("title")
                        .status(NormalizedPolicyAggregate.ServiceStatus.ACTIVE)
                        .build())
                .taxonomy(NormalizedPolicyAggregate.TaxonomySummary.builder()
                        .compatUnifiedCategory("주거")
                        .authority(NormalizedPolicyAggregate.Authority.OFFICIAL)
                        .confidence(BigDecimal.ONE)
                        .build())
                .build();
    }

    private record SyntheticItem(String sourceId, String title) {
    }
}
