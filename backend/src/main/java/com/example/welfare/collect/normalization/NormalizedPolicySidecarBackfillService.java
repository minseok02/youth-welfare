package com.example.welfare.collect.normalization;

import com.example.welfare.collect.entity.RawApiPayload;
import com.example.welfare.collect.dto.Gov24SupportConditionsDto;
import com.example.welfare.collect.mapper.WelfareServiceMapper;
import com.example.welfare.collect.repository.NormalizedPolicySidecarBackfillReadRepository;
import com.example.welfare.collect.repository.NormalizedPolicySidecarBackfillTarget;
import com.example.welfare.collect.service.CollectPolicyAggregateApplyService;
import com.example.welfare.collect.support.CollectSourceRegistry;
import com.example.welfare.policy.entity.WelfareService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class NormalizedPolicySidecarBackfillService {

    private static final List<String> GOV24_LIST_SUMMARY_SLOT_KEYS = List.of(
            CanonicalTaxonomySummarySlots.SLOT_GOV24_SERVICE_FIELD,
            CanonicalTaxonomySummarySlots.SLOT_GOV24_USER_TYPE,
            CanonicalTaxonomySummarySlots.SLOT_GOV24_BENEFIT_TYPE
    );

    private final NormalizedPolicySidecarBackfillReadRepository normalizedPolicySidecarBackfillReadRepository;
    private final WelfareServiceMapper welfareServiceMapper;
    private final CollectPolicyAggregateApplyService collectPolicyAggregateApplyService;
    private final ObjectMapper objectMapper;
    private final Map<WelfareService.SourceType, SidecarBackfillCapability> backfillCapabilities;

    public NormalizedPolicySidecarBackfillService(NormalizedPolicySidecarBackfillReadRepository normalizedPolicySidecarBackfillReadRepository,
                                                  WelfareServiceMapper welfareServiceMapper,
                                                  CollectPolicyAggregateApplyService collectPolicyAggregateApplyService,
                                                  ObjectMapper objectMapper) {
        this.normalizedPolicySidecarBackfillReadRepository = normalizedPolicySidecarBackfillReadRepository;
        this.welfareServiceMapper = welfareServiceMapper;
        this.collectPolicyAggregateApplyService = collectPolicyAggregateApplyService;
        this.objectMapper = objectMapper;
        this.backfillCapabilities = buildBackfillCapabilities(welfareServiceMapper, objectMapper);
    }

    @Transactional
    public BackfillResult backfillBokjiroListSidecars(int limitPerSource) {
        return backfillListSidecars(List.of(
                WelfareService.SourceType.BOKJIRO_CENTRAL,
                WelfareService.SourceType.BOKJIRO_LOCAL
        ), limitPerSource);
    }

    @Transactional
    public BackfillResult backfillGov24ListSidecars(int limitPerSource) {
        return backfillListSidecars(List.of(WelfareService.SourceType.GOV24), limitPerSource);
    }

    @Transactional
    public BackfillResult backfillGov24MissingListSidecars(int limitPerSource) {
        return backfillListSourceMissingSummarySlots(
                WelfareService.SourceType.GOV24,
                limitPerSource,
                GOV24_LIST_SUMMARY_SLOT_KEYS
        );
    }

    @Transactional
    public BackfillResult backfillGov24SupportConditionSidecars(int limitPerSource) {
        return backfillGov24SupportConditionSource(limitPerSource);
    }

    @Transactional
    public BackfillResult backfillBokjiroDetailSidecars(int limitPerSource) {
        return backfillDetailSidecars(configuredSourceTypes(SidecarBackfillCapability::supportsDetail), limitPerSource);
    }

    @Transactional
    public BackfillResult backfillListSidecars(List<WelfareService.SourceType> sourceTypes, int limitPerSource) {
        BackfillResult result = BackfillResult.empty();
        for (WelfareService.SourceType sourceType : sourceTypes) {
            result = result.plus(backfillListSource(sourceType, limitPerSource));
        }
        return result;
    }

    @Transactional
    public BackfillResult backfillDetailSidecars(List<WelfareService.SourceType> sourceTypes, int limitPerSource) {
        BackfillResult result = BackfillResult.empty();
        for (WelfareService.SourceType sourceType : sourceTypes) {
            result = result.plus(backfillDetailSource(sourceType, limitPerSource));
        }
        return result;
    }

    private BackfillResult backfillListSource(WelfareService.SourceType sourceType, int limitPerSource) {
        SidecarBackfillCapability capability = backfillCapabilities.get(sourceType);
        if (capability == null || !capability.supportsList()) {
            return BackfillResult.empty();
        }

        List<NormalizedPolicySidecarBackfillTarget> targets = normalizedPolicySidecarBackfillReadRepository
                .findTargetsBySourceTypeAndApiCategoryOrderByFetchedAtAsc(
                        sourceType,
                        RawApiPayload.ApiCategory.LIST,
                        limitPerSource
                );

        return backfillListTargets(capability, targets);
    }

    private BackfillResult backfillListSourceMissingSummarySlots(WelfareService.SourceType sourceType,
                                                                 int limitPerSource,
                                                                 List<String> requiredSummarySlotKeys) {
        SidecarBackfillCapability capability = backfillCapabilities.get(sourceType);
        if (capability == null || !capability.supportsList()) {
            return BackfillResult.empty();
        }

        List<NormalizedPolicySidecarBackfillTarget> targets = normalizedPolicySidecarBackfillReadRepository
                .findTargetsMissingSummarySlotsBySourceTypeAndApiCategoryOrderByFetchedAtAsc(
                        sourceType,
                        RawApiPayload.ApiCategory.LIST,
                        requiredSummarySlotKeys,
                        limitPerSource
                );

        return backfillListTargets(capability, targets);
    }

    private BackfillResult backfillListTargets(SidecarBackfillCapability capability,
                                               List<NormalizedPolicySidecarBackfillTarget> targets) {
        int scanned = 0;
        int upserted = 0;
        int missingService = 0;
        int failed = 0;

        for (NormalizedPolicySidecarBackfillTarget target : targets) {
            scanned++;
            RawApiPayload raw = target.rawApiPayload();
            WelfareService service = target.matchedService();
            if (service == null) {
                missingService++;
                continue;
            }

            try {
                NormalizedPolicyAggregate aggregate = capability.toListAggregate(raw);
                collectPolicyAggregateApplyService.applySidecarBackfill(service, aggregate);
                upserted++;
            } catch (Exception e) {
                failed++;
                log.warn("[NormalizedPolicySidecarBackfillService] list backfill 실패 sourceType={} sourceId={} err={}",
                        raw.getSourceType(), raw.getSourceId(), e.getMessage());
            }
        }

        return new BackfillResult(scanned, upserted, missingService, failed);
    }

    private BackfillResult backfillDetailSource(WelfareService.SourceType sourceType, int limitPerSource) {
        SidecarBackfillCapability capability = backfillCapabilities.get(sourceType);
        if (capability == null || !capability.supportsDetail()) {
            return BackfillResult.empty();
        }

        List<NormalizedPolicySidecarBackfillTarget> targets = normalizedPolicySidecarBackfillReadRepository
                .findTargetsBySourceTypeAndApiCategoryOrderByFetchedAtAsc(
                        sourceType,
                        RawApiPayload.ApiCategory.DETAIL,
                        limitPerSource
                );

        int scanned = 0;
        int upserted = 0;
        int missingService = 0;
        int failed = 0;

        for (NormalizedPolicySidecarBackfillTarget target : targets) {
            scanned++;
            RawApiPayload raw = target.rawApiPayload();
            WelfareService service = target.matchedService();
            if (service == null) {
                missingService++;
                continue;
            }

            try {
                NormalizedPolicyAggregate aggregate = capability.toDetailAggregate(service, raw);
                collectPolicyAggregateApplyService.applySidecarBackfill(service, aggregate);
                upserted++;
            } catch (Exception e) {
                failed++;
                log.warn("[NormalizedPolicySidecarBackfillService] detail backfill 실패 sourceType={} sourceId={} err={}",
                        raw.getSourceType(), raw.getSourceId(), e.getMessage());
            }
        }

        return new BackfillResult(scanned, upserted, missingService, failed);
    }

    private BackfillResult backfillGov24SupportConditionSource(int limitPerSource) {
        List<NormalizedPolicySidecarBackfillTarget> targets = normalizedPolicySidecarBackfillReadRepository
                .findTargetsBySourceTypeAndApiCategoryOrderByFetchedAtAsc(
                        WelfareService.SourceType.GOV24,
                        RawApiPayload.ApiCategory.SUPPORT,
                        limitPerSource
                );

        int scanned = 0;
        int upserted = 0;
        int missingService = 0;
        int failed = 0;

        for (NormalizedPolicySidecarBackfillTarget target : targets) {
            scanned++;
            RawApiPayload raw = target.rawApiPayload();
            WelfareService service = target.matchedService();
            if (service == null) {
                missingService++;
                continue;
            }

            try {
                Gov24SupportConditionsDto.Item payload = parseGov24SupportConditionsRaw(raw);
                NormalizedPolicyAggregate aggregate = welfareServiceMapper.toGov24SupportConditionsAggregate(service, payload);
                collectPolicyAggregateApplyService.replaceFactCodeSet(service, "GOV24_SUPPORT_CONDITION", aggregate);
                upserted++;
            } catch (Exception e) {
                failed++;
                log.warn("[NormalizedPolicySidecarBackfillService] Gov24 support backfill 실패 sourceId={} err={}",
                        raw.getSourceId(), e.getMessage());
            }
        }

        return new BackfillResult(scanned, upserted, missingService, failed);
    }

    private Gov24SupportConditionsDto.Item parseGov24SupportConditionsRaw(RawApiPayload raw) throws Exception {
        JsonNode root = objectMapper.readTree(raw.getPayloadJson());
        JsonNode conditionsNode = root.path("conditions");
        Map<String, Object> conditions = conditionsNode.isObject()
                ? objectMapper.convertValue(conditionsNode, new TypeReference<>() {})
                : Map.of();
        return Gov24SupportConditionsDto.Item.fromRawPayload(
                textOrNull(root.get("서비스ID")),
                textOrNull(root.get("서비스명")),
                conditions
        );
    }

    private String textOrNull(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        return node.asText();
    }

    private List<WelfareService.SourceType> configuredSourceTypes(java.util.function.Predicate<SidecarBackfillCapability> predicate) {
        return backfillCapabilities.values().stream()
                .filter(predicate)
                .map(SidecarBackfillCapability::sourceType)
                .toList();
    }

    private Map<WelfareService.SourceType, SidecarBackfillCapability> buildBackfillCapabilities(WelfareServiceMapper welfareServiceMapper,
                                                                                                 ObjectMapper objectMapper) {
        EnumMap<WelfareService.SourceType, SidecarBackfillCapability> capabilities =
                new EnumMap<>(WelfareService.SourceType.class);
        for (CollectSourceRegistry binding : CollectSourceRegistry.values()) {
            ListAggregateLoader listLoader = binding.supportsListAggregateBackfill()
                    ? raw -> binding.toListAggregate(welfareServiceMapper, objectMapper, raw)
                    : null;
            DetailAggregateLoader detailLoader = binding.supportsDetailCollect()
                    ? (service, raw) -> binding.toDetailAggregate(welfareServiceMapper, service, raw, objectMapper)
                    : null;
            if (listLoader == null && detailLoader == null) {
                continue;
            }
            capabilities.put(
                    binding.sourceType(),
                    new SidecarBackfillCapability(
                            binding.sourceType(),
                            listLoader,
                            detailLoader
                    )
            );
        }

        return Map.copyOf(capabilities);
    }

    public record BackfillResult(int scannedCount, int upsertedCount, int missingServiceCount, int failedCount) {
        private static BackfillResult empty() {
            return new BackfillResult(0, 0, 0, 0);
        }

        public BackfillResult plus(BackfillResult other) {
            return new BackfillResult(
                    scannedCount + other.scannedCount,
                    upsertedCount + other.upsertedCount,
                    missingServiceCount + other.missingServiceCount,
                    failedCount + other.failedCount
            );
        }
    }

    private record SidecarBackfillCapability(
            WelfareService.SourceType sourceType,
            ListAggregateLoader listLoader,
            DetailAggregateLoader detailLoader
    ) {
        private boolean supportsList() {
            return listLoader != null;
        }

        private boolean supportsDetail() {
            return detailLoader != null;
        }

        private NormalizedPolicyAggregate toListAggregate(RawApiPayload raw) throws Exception {
            return listLoader.load(raw);
        }

        private NormalizedPolicyAggregate toDetailAggregate(WelfareService service, RawApiPayload raw) throws Exception {
            return detailLoader.load(service, raw);
        }
    }

    @FunctionalInterface
    private interface ListAggregateLoader {
        NormalizedPolicyAggregate load(RawApiPayload raw) throws Exception;
    }

    @FunctionalInterface
    private interface DetailAggregateLoader {
        NormalizedPolicyAggregate load(WelfareService service, RawApiPayload raw) throws Exception;
    }
}
