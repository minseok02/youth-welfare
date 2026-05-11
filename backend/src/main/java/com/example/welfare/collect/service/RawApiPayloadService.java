package com.example.welfare.collect.service;

import com.example.welfare.collect.dto.YouthApiDto;
import com.example.welfare.collect.entity.RawApiPayload;
import com.example.welfare.collect.gateway.BokjiroDetailClient;
import com.example.welfare.collect.repository.RawApiPayloadCommandRepository;
import com.example.welfare.collect.support.ListCollectSourceBinding;
import com.example.welfare.collect.validation.RawFieldValidator;
import com.example.welfare.policy.entity.WelfareService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;

@Slf4j
@Service
@RequiredArgsConstructor
public class RawApiPayloadService {

    private final RawApiPayloadCommandRepository rawApiPayloadCommandRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public <T> boolean saveList(ListCollectSourceBinding<T> binding, T item) {
        return saveList(binding.sourceType(), binding.sourceId(item), item);
    }

    @Transactional
    public boolean saveList(WelfareService.SourceType sourceType,
                            String sourceId,
                            Object payload) {
        return save(
                sourceType,
                RawFieldValidator.normalize(sourceId),
                RawApiPayload.ApiCategory.LIST,
                payload
        );
    }

    @Transactional
    public boolean saveBokjiroDetail(WelfareService.SourceType sourceType,
                                     String sourceId,
                                     BokjiroDetailClient.DetailPayload payload) {
        return save(
                sourceType,
                RawFieldValidator.normalize(sourceId),
                RawApiPayload.ApiCategory.DETAIL,
                payload
        );
    }

    @Transactional
    public boolean saveYouthDetail(String sourceId, YouthApiDto.Item detail) {
        return save(
                WelfareService.SourceType.YOUTH,
                RawFieldValidator.normalize(sourceId),
                RawApiPayload.ApiCategory.DETAIL,
                detail
        );
    }

    private boolean save(WelfareService.SourceType sourceType,
                         String sourceId,
                         RawApiPayload.ApiCategory apiCategory,
                         Object payload) {
        if (sourceType == null || sourceId == null || payload == null) {
            return false;
        }

        try {
            String payloadJson = objectMapper.writeValueAsString(payload);
            String payloadHash = sha256(payloadJson);
            LocalDateTime fetchedAt = LocalDateTime.now();

            rawApiPayloadCommandRepository.upsert(
                    sourceType,
                    sourceId,
                    apiCategory,
                    payloadJson,
                    payloadHash,
                    fetchedAt
            );
            return true;
        } catch (Exception e) {
            log.warn("[RawApiPayloadService] raw 저장 실패 sourceType={} sourceId={} apiCategory={} err={}",
                    sourceType, sourceId, apiCategory, e.getMessage());
            return false;
        }
    }

    private String sha256(String payloadJson) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(payloadJson.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }
}
