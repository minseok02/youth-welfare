package com.example.welfare.collect.gateway;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 복지로 상세 API 호출 클라이언트.
 * - 중앙: NationalWelfaredetailedV001
 * - 지자체: LcgvWelfaredetailed
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BokjiroDetailClient {

    private final WebClient webClient;
    private final XmlMapper xmlMapper;

    @Value("${bokjiro.api-key}")
    private String apiKey;

    public DetailPayload fetchCentral(String serviceId) {
        String encodedKey = URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
        String url = "https://apis.data.go.kr/B554287/NationalWelfareInformationsV001/NationalWelfaredetailedV001"
                + "?serviceKey=" + encodedKey
                + "&servId=" + encode(serviceId);
        return fetchDetail(url, serviceId, "CENTRAL");
    }

    public DetailPayload fetchLocal(String serviceId) {
        String encodedKey = URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
        String url = "https://apis.data.go.kr/B554287/LocalGovernmentWelfareInformations/LcgvWelfaredetailed"
                + "?serviceKey=" + encodedKey
                + "&servId=" + encode(serviceId);
        return fetchDetail(url, serviceId, "LOCAL");
    }

    public FetchResult fetchCentralWithStatus(String serviceId) {
        String encodedKey = URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
        String url = "https://apis.data.go.kr/B554287/NationalWelfareInformationsV001/NationalWelfaredetailedV001"
                + "?serviceKey=" + encodedKey
                + "&servId=" + encode(serviceId);
        return fetchDetailWithStatus(url, serviceId, "CENTRAL");
    }

    public FetchResult fetchLocalWithStatus(String serviceId) {
        String encodedKey = URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
        String url = "https://apis.data.go.kr/B554287/LocalGovernmentWelfareInformations/LcgvWelfaredetailed"
                + "?serviceKey=" + encodedKey
                + "&servId=" + encode(serviceId);
        return fetchDetailWithStatus(url, serviceId, "LOCAL");
    }

    private DetailPayload fetchDetail(String url, String serviceId, String source) {
        try {
            String xml = webClient.get()
                    .uri(URI.create(url))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(20));

            if (xml == null || xml.isBlank()) return null;
            JsonNode root = xmlMapper.readTree(xml);

            String resultCode = text(root, "resultCode");
            if (resultCode != null && !"0".equals(resultCode) && !"00".equals(resultCode)) {
                log.warn("[BokjiroDetailClient] 상세 조회 실패 source={} servId={} resultCode={}",
                        source, serviceId, resultCode);
                return null;
            }

            return DetailPayload.builder()
                    .targetDetail(firstText(root, "tgtrDtlCn", "sprtTrgtCn"))
                    .supportDetail(firstText(root, "alwServCn", "servDgst"))
                    .applyMethodDetail(firstText(root, "aplyMtdCn", "aplyMtdNm"))
                    .selectionCriteria(text(root, "slctCritCn"))
                    .contactList(firstText(root, "inqplCtadrList", "rprsCtadr"))
                    .supportCycle(text(root, "sprtCycNm"))
                    .provisionType(text(root, "srvPvsnNm"))
                    .build();
        } catch (WebClientResponseException e) {
            log.warn("[BokjiroDetailClient] 상세 조회 HTTP 예외 source={} servId={} status={} err={}",
                    source, serviceId, e.getStatusCode().value(), e.getMessage());
            throw new CustomException(ErrorCode.COLLECT_API_FAILED);
        } catch (Exception e) {
            log.warn("[BokjiroDetailClient] 상세 조회 예외 source={} servId={} err={}",
                    source, serviceId, e.getMessage());
            throw new CustomException(ErrorCode.COLLECT_API_FAILED);
        }
    }

    private FetchResult fetchDetailWithStatus(String url, String serviceId, String source) {
        try {
            String xml = webClient.get()
                    .uri(URI.create(url))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(20));

            if (xml == null || xml.isBlank()) return FetchResult.success(null);
            JsonNode root = xmlMapper.readTree(xml);

            String resultCode = text(root, "resultCode");
            if (resultCode != null && !"0".equals(resultCode) && !"00".equals(resultCode)) {
                log.warn("[BokjiroDetailClient] 상세 조회 실패 source={} servId={} resultCode={}",
                        source, serviceId, resultCode);
                return FetchResult.success(null);
            }

            DetailPayload payload = DetailPayload.builder()
                    .targetDetail(firstText(root, "tgtrDtlCn", "sprtTrgtCn"))
                    .supportDetail(firstText(root, "alwServCn", "servDgst"))
                    .applyMethodDetail(firstText(root, "aplyMtdCn", "aplyMtdNm"))
                    .selectionCriteria(text(root, "slctCritCn"))
                    .contactList(firstText(root, "inqplCtadrList", "rprsCtadr"))
                    .supportCycle(text(root, "sprtCycNm"))
                    .provisionType(text(root, "srvPvsnNm"))
                    .build();

            return FetchResult.success(payload);
        } catch (WebClientResponseException e) {
            int status = e.getStatusCode().value();
            boolean rateLimited = status == 429;
            boolean retryable = (status >= 500 && status < 600);
            log.warn("[BokjiroDetailClient] 상세 조회 HTTP 예외 source={} servId={} status={} retryable={} err={}",
                    source, serviceId, status, retryable, e.getMessage());
            return FetchResult.failure(retryable, rateLimited, status);
        } catch (Exception e) {
            log.warn("[BokjiroDetailClient] 상세 조회 예외 source={} servId={} err={}",
                    source, serviceId, e.getMessage());
            return FetchResult.failure(true, false, null);
        }
    }

    private String firstText(JsonNode root, String... keys) {
        for (String key : keys) {
            String v = text(root, key);
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }

    private String text(JsonNode root, String key) {
        JsonNode node = root.path(key);
        if (node.isMissingNode() || node.isNull()) return null;
        String value = node.asText();
        return value == null || value.isBlank() ? null : value;
    }

    private String encode(String raw) {
        return URLEncoder.encode(raw == null ? "" : raw, StandardCharsets.UTF_8);
    }

    @Getter
    @Builder
    public static class DetailPayload {
        private String targetDetail;
        private String supportDetail;
        private String applyMethodDetail;
        private String selectionCriteria;
        private String contactList;
        private String supportCycle;
        private String provisionType;

        public boolean isEmpty() {
            return targetDetail == null
                    && supportDetail == null
                    && applyMethodDetail == null
                    && selectionCriteria == null
                    && contactList == null
                    && supportCycle == null
                    && provisionType == null;
        }
    }

    @Getter
    @Builder
    public static class FetchResult {
        private DetailPayload payload;
        private boolean retryable;
        private boolean rateLimited;
        private Integer statusCode;

        public static FetchResult success(DetailPayload payload) {
            return FetchResult.builder()
                    .payload(payload)
                    .retryable(false)
                    .rateLimited(false)
                    .statusCode(null)
                    .build();
        }

        public static FetchResult failure(boolean retryable, boolean rateLimited, Integer statusCode) {
            return FetchResult.builder()
                    .payload(null)
                    .retryable(retryable)
                    .rateLimited(rateLimited)
                    .statusCode(statusCode)
                    .build();
        }
    }
}
