package com.example.welfare.collect.support;

import com.example.welfare.collect.dto.BokjiroCentralDto;
import com.example.welfare.collect.dto.BokjiroLocalDto;
import com.example.welfare.collect.dto.Gov24ServiceListDto;
import com.example.welfare.collect.dto.YouthApiDto;
import com.example.welfare.collect.entity.RawApiPayload;
import com.example.welfare.collect.gateway.BokjiroDetailClient;
import com.example.welfare.collect.mapper.WelfareServiceMapper;
import com.example.welfare.collect.normalization.NormalizedPolicyAggregate;
import com.example.welfare.collect.validation.RawFieldValidator;
import com.example.welfare.policy.entity.WelfareService;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Arrays;
import java.util.List;

public enum CollectSourceRegistry {
    YOUTH(WelfareService.SourceType.YOUTH, false, false) {
        @Override
        public <T> ListCollectSourceBinding<T> listBinding(WelfareServiceMapper mapper) {
            return cast(new ListCollectSourceBinding<>(
                    sourceType(),
                    YouthApiDto.Item::getPlcyNo,
                    RawFieldValidator::recordStatsYouth,
                    mapper::fromYouth,
                    mapper::regionsFromYouth,
                    mapper::tagsFromYouth,
                    mapper::toNormalizedYouth
            ));
        }
    },
    BOKJIRO_CENTRAL(WelfareService.SourceType.BOKJIRO_CENTRAL, true, true) {
        @Override
        public <T> ListCollectSourceBinding<T> listBinding(WelfareServiceMapper mapper) {
            return cast(new ListCollectSourceBinding<>(
                    sourceType(),
                    BokjiroCentralDto.Item::getServId,
                    RawFieldValidator::recordStatsBokjiroCentral,
                    mapper::fromBokjiroCentral,
                    mapper::regionsFromBokjiroCentral,
                    mapper::tagsFromBokjiroCentral,
                    item -> mapper.toNormalizedBokjiroCentral(item, null)
            ));
        }

        @Override
        public BokjiroDetailClient.FetchResult fetchDetail(BokjiroDetailClient detailClient, String sourceId) {
            return detailClient.fetchCentralWithStatus(sourceId);
        }

        @Override
        public NormalizedPolicyAggregate toListAggregate(WelfareServiceMapper mapper,
                                                         ObjectMapper objectMapper,
                                                         RawApiPayload raw) throws Exception {
            return mapper.toNormalizedBokjiroCentral(
                    objectMapper.readValue(raw.getPayloadJson(), BokjiroCentralDto.Item.class),
                    null
            );
        }
    },
    BOKJIRO_LOCAL(WelfareService.SourceType.BOKJIRO_LOCAL, true, true) {
        @Override
        public <T> ListCollectSourceBinding<T> listBinding(WelfareServiceMapper mapper) {
            return cast(new ListCollectSourceBinding<>(
                    sourceType(),
                    BokjiroLocalDto.Item::getServId,
                    RawFieldValidator::recordStatsBokjiroLocal,
                    mapper::fromBokjiroLocal,
                    mapper::regionsFromBokjiroLocal,
                    mapper::tagsFromBokjiroLocal,
                    item -> mapper.toNormalizedBokjiroLocal(item, null)
            ));
        }

        @Override
        public BokjiroDetailClient.FetchResult fetchDetail(BokjiroDetailClient detailClient, String sourceId) {
            return detailClient.fetchLocalWithStatus(sourceId);
        }

        @Override
        public NormalizedPolicyAggregate toListAggregate(WelfareServiceMapper mapper,
                                                         ObjectMapper objectMapper,
                                                         RawApiPayload raw) throws Exception {
            return mapper.toNormalizedBokjiroLocal(
                    objectMapper.readValue(raw.getPayloadJson(), BokjiroLocalDto.Item.class),
                    null
            );
        }
    },
    GOV24(WelfareService.SourceType.GOV24, true, false) {
        @Override
        public <T> ListCollectSourceBinding<T> listBinding(WelfareServiceMapper mapper) {
            return cast(new ListCollectSourceBinding<>(
                    sourceType(),
                    Gov24ServiceListDto.Item::getServiceId,
                    RawFieldValidator::recordStatsGov24,
                    mapper::fromGov24,
                    mapper::regionsFromGov24,
                    mapper::tagsFromGov24,
                    mapper::toNormalizedGov24
            ));
        }

        @Override
        public NormalizedPolicyAggregate toListAggregate(WelfareServiceMapper mapper,
                                                         ObjectMapper objectMapper,
                                                         RawApiPayload raw) throws Exception {
            return mapper.toNormalizedGov24(
                    objectMapper.readValue(raw.getPayloadJson(), Gov24ServiceListDto.Item.class)
            );
        }
    };

    private final WelfareService.SourceType sourceType;
    private final boolean supportsListAggregateBackfill;
    private final boolean supportsDetailCollect;

    CollectSourceRegistry(WelfareService.SourceType sourceType,
                          boolean supportsListAggregateBackfill,
                          boolean supportsDetailCollect) {
        this.sourceType = sourceType;
        this.supportsListAggregateBackfill = supportsListAggregateBackfill;
        this.supportsDetailCollect = supportsDetailCollect;
    }

    public WelfareService.SourceType sourceType() {
        return sourceType;
    }

    public boolean supportsListAggregateBackfill() {
        return supportsListAggregateBackfill;
    }

    public boolean supportsDetailCollect() {
        return supportsDetailCollect;
    }

    public abstract <T> ListCollectSourceBinding<T> listBinding(WelfareServiceMapper mapper);

    public BokjiroDetailClient.FetchResult fetchDetail(BokjiroDetailClient detailClient, String sourceId) {
        throw new IllegalStateException("detail fetch 미지원 sourceType=" + sourceType);
    }

    public NormalizedPolicyAggregate toListAggregate(WelfareServiceMapper mapper,
                                                     ObjectMapper objectMapper,
                                                     RawApiPayload raw) throws Exception {
        throw new IllegalStateException("list aggregate backfill 미지원 sourceType=" + sourceType);
    }

    public NormalizedPolicyAggregate toDetailAggregate(WelfareServiceMapper mapper,
                                                       WelfareService service,
                                                       RawApiPayload raw,
                                                       ObjectMapper objectMapper) throws Exception {
        return mapper.toNormalizedBokjiroDetail(
                service,
                objectMapper.readValue(raw.getPayloadJson(), BokjiroDetailClient.DetailPayload.class)
        );
    }

    public NormalizedPolicyAggregate toDetailAggregate(WelfareServiceMapper mapper,
                                                       WelfareService service,
                                                       BokjiroDetailClient.DetailPayload payload) {
        return mapper.toNormalizedBokjiroDetail(service, payload);
    }

    public static CollectSourceRegistry of(WelfareService.SourceType sourceType) {
        return Arrays.stream(values())
                .filter(registry -> registry.sourceType == sourceType)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("미지원 collect sourceType=" + sourceType));
    }

    public static List<CollectSourceRegistry> detailSources() {
        return Arrays.stream(values())
                .filter(CollectSourceRegistry::supportsDetailCollect)
                .toList();
    }

    public static List<CollectSourceRegistry> listAggregateBackfillSources() {
        return Arrays.stream(values())
                .filter(CollectSourceRegistry::supportsListAggregateBackfill)
                .toList();
    }

    @SuppressWarnings("unchecked")
    private static <T> ListCollectSourceBinding<T> cast(ListCollectSourceBinding<?> binding) {
        return (ListCollectSourceBinding<T>) binding;
    }
}
