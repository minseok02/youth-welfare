package com.example.welfare.collect.support;

import com.example.welfare.collect.dto.BokjiroCentralDto;
import com.example.welfare.collect.dto.BokjiroLocalDto;
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
            return detailClient.fetchCentralWithStatus(sourceId);
        }

        @Override
        public NormalizedPolicyAggregate toListAggregate(WelfareServiceMapper welfareServiceMapper,
                                                         ObjectMapper objectMapper,
                                                         RawApiPayload raw) throws Exception {
            return welfareServiceMapper.toNormalizedBokjiroCentral(
                    objectMapper.readValue(raw.getPayloadJson(), BokjiroCentralDto.Item.class),
                    null
            );
        }
    },
    LOCAL(WelfareService.SourceType.BOKJIRO_LOCAL) {
        @Override
        public BokjiroDetailClient.FetchResult fetchDetail(BokjiroDetailClient detailClient, String sourceId) {
            return detailClient.fetchLocalWithStatus(sourceId);
        }

        @Override
        public NormalizedPolicyAggregate toListAggregate(WelfareServiceMapper welfareServiceMapper,
                                                         ObjectMapper objectMapper,
                                                         RawApiPayload raw) throws Exception {
            return welfareServiceMapper.toNormalizedBokjiroLocal(
                    objectMapper.readValue(raw.getPayloadJson(), BokjiroLocalDto.Item.class),
                    null
            );
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
        return welfareServiceMapper.toNormalizedBokjiroDetail(
                service,
                objectMapper.readValue(raw.getPayloadJson(), BokjiroDetailClient.DetailPayload.class)
        );
    }

    public NormalizedPolicyAggregate toDetailAggregate(WelfareServiceMapper welfareServiceMapper,
                                                       WelfareService service,
                                                       BokjiroDetailClient.DetailPayload payload) {
        return welfareServiceMapper.toNormalizedBokjiroDetail(service, payload);
    }
}
