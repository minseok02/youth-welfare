package com.example.welfare.collect.support;

import com.example.welfare.collect.entity.RawApiPayload;
import com.example.welfare.collect.gateway.BokjiroDetailClient;
import com.example.welfare.collect.mapper.WelfareServiceMapper;
import com.example.welfare.collect.normalization.NormalizedPolicyAggregate;
import com.example.welfare.policy.entity.WelfareService;
import com.fasterxml.jackson.databind.ObjectMapper;

public enum BokjiroSourceBinding {
    CENTRAL(WelfareService.SourceType.BOKJIRO_CENTRAL) {
        @Override
        public BokjiroDetailClient.FetchResult fetchDetail(BokjiroDetailClient detailClient, String sourceId) {
            return CollectSourceRegistry.BOKJIRO_CENTRAL.fetchDetail(detailClient, sourceId);
        }

        @Override
        public NormalizedPolicyAggregate toListAggregate(WelfareServiceMapper welfareServiceMapper,
                                                         ObjectMapper objectMapper,
                                                         RawApiPayload raw) throws Exception {
            return CollectSourceRegistry.BOKJIRO_CENTRAL.toListAggregate(welfareServiceMapper, objectMapper, raw);
        }
    },
    LOCAL(WelfareService.SourceType.BOKJIRO_LOCAL) {
        @Override
        public BokjiroDetailClient.FetchResult fetchDetail(BokjiroDetailClient detailClient, String sourceId) {
            return CollectSourceRegistry.BOKJIRO_LOCAL.fetchDetail(detailClient, sourceId);
        }

        @Override
        public NormalizedPolicyAggregate toListAggregate(WelfareServiceMapper welfareServiceMapper,
                                                         ObjectMapper objectMapper,
                                                         RawApiPayload raw) throws Exception {
            return CollectSourceRegistry.BOKJIRO_LOCAL.toListAggregate(welfareServiceMapper, objectMapper, raw);
        }
    };

    private final WelfareService.SourceType sourceType;

    BokjiroSourceBinding(WelfareService.SourceType sourceType) {
        this.sourceType = sourceType;
    }

    public WelfareService.SourceType sourceType() {
        return sourceType;
    }

    public abstract BokjiroDetailClient.FetchResult fetchDetail(BokjiroDetailClient detailClient, String sourceId);

    public abstract NormalizedPolicyAggregate toListAggregate(WelfareServiceMapper welfareServiceMapper,
                                                              ObjectMapper objectMapper,
                                                              RawApiPayload raw) throws Exception;

    public NormalizedPolicyAggregate toDetailAggregate(WelfareServiceMapper welfareServiceMapper,
                                                       WelfareService service,
                                                       RawApiPayload raw,
                                                       ObjectMapper objectMapper) throws Exception {
        return CollectSourceRegistry.of(sourceType).toDetailAggregate(welfareServiceMapper, service, raw, objectMapper);
    }

    public NormalizedPolicyAggregate toDetailAggregate(WelfareServiceMapper welfareServiceMapper,
                                                       WelfareService service,
                                                       BokjiroDetailClient.DetailPayload payload) {
        return CollectSourceRegistry.of(sourceType).toDetailAggregate(welfareServiceMapper, service, payload);
    }
}
