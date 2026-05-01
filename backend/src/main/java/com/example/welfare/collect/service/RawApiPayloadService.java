package com.example.welfare.collect.service;

import com.example.welfare.collect.entity.RawApiPayload;
import com.example.welfare.collect.gateway.BokjiroDetailClient;
import com.example.welfare.collect.repository.RawApiPayloadRepository;
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

    private final RawApiPayloadRepository rawApiPayloadRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public <T> void saveList(ListCollectSourceBinding<T> binding, T item) {
        saveList(binding.sourceType(), binding.sourceId(item), item);
    }

    @Transactional
    public void saveList(WelfareService.SourceType sourceType,
                         String sourceId,
                         Object payload) {
        save(
                sourceType,
                RawFieldValidator.normalize(sourceId),
                RawApiPayload.ApiCategory.LIST,
                payload
        );
    }

    @Transactional
    public void saveBokjiroDetail(WelfareService.SourceType sourceType,
                                  String sourceId,
                                  BokjiroDetailClient.DetailPayload payload) {
        save(
                sourceType,
                RawFieldValidator.normalize(sourceId),
                RawApiPayload.ApiCategory.DETAIL,
                payload
        );
    }

    private void save(WelfareService.SourceType sourceType,
                      String sourceId,
                      RawApiPayload.ApiCategory apiCategory,
                      Object payload) {
        if (sourceType == null || sourceId == null || payload == null) {
            return;
        }

        try {
            String payloadJson = objectMapper.writeValueAsString(payload);
            String payloadHash = sha256(payloadJson);
            LocalDateTime fetchedAt = LocalDateTime.now();

            RawApiPayload entity = rawApiPayloadRepository
                    .findBySourceTypeAndSourceIdAndApiCategory(sourceType, sourceId, apiCategory)
                    .orElseGet(() -> RawApiPayload.builder()
                            .sourceType(sourceType)
                            .sourceId(sourceId)
                            .apiCategory(apiCategory)
                            .payloadJson(payloadJson)
                            .payloadHash(payloadHash)
                            .fetchedAt(fetchedAt)
                            .build());

            entity.updatePayload(payloadJson, payloadHash, fetchedAt);
            rawApiPayloadRepository.save(entity);
        } catch (Exception e) {
            log.warn("[RawApiPayloadService] raw 저장 실패 sourceType={} sourceId={} apiCategory={} err={}",
                    sourceType, sourceId, apiCategory, e.getMessage());
        }
    }

    private String sha256(String payloadJson) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(payloadJson.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }
}
