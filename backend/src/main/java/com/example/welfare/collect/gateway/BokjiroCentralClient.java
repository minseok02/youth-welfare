package com.example.welfare.collect.gateway;

import com.example.welfare.collect.dto.BokjiroCentralDto;
import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Component
@RequiredArgsConstructor
public class BokjiroCentralClient {

    private final WebClient webClient;
    private final XmlMapper xmlMapper;

    @Value("${bokjiro.api-key}")
    private String apiKey;
    @Value("${collect.list.request-interval-ms:300}")
    private long requestIntervalMs;
    @Value("${collect.list.retry.max-attempts:3}")
    private int retryMaxAttempts;
    @Value("${collect.list.retry.base-backoff-ms:1000}")
    private long retryBaseBackoffMs;
    @Value("${collect.list.max-consecutive-rate-limit-hits:3}")
    private int maxConsecutiveRateLimitHits;
    @Value("${collect.list.rate-limit-cooldown-ms:10000}")
    private long rateLimitCooldownMs;

    private static final int PAGE_SIZE = 100;

    /**
     * 복지로 중앙정부 서비스 전체 수집 (XML, 페이징)
     * 엔드포인트: GET https://apis.data.go.kr/B554287/NationalWelfareInformationsV001/NationalWelfarelistV001
     * XXE 비활성화는 WebClientConfig의 XmlMapper 설정에서 처리
     */
    public List<BokjiroCentralDto.Item> fetchAll() {
        List<BokjiroCentralDto.Item> result = new ArrayList<>();
        int pageNo = 1;
        int rateLimitHits = 0;

        while (true) {
            sleepQuietly(requestIntervalMs);
            PageFetchResult<BokjiroCentralDto> fetchResult = fetchPage(pageNo, PAGE_SIZE);
            if (fetchResult.isRateLimited()) {
                rateLimitHits++;
                log.warn("[BokjiroCentralClient] 429 감지 page={} collected={} rateLimitHits={} cooldownMs={}",
                        pageNo, result.size(), rateLimitHits, rateLimitCooldownMs);
                if (rateLimitHits >= maxConsecutiveRateLimitHits) {
                    log.warn("[BokjiroCentralClient] 연속 429 임계치 도달로 수집 중단 page={} collected={} rateLimitHits={}",
                            pageNo, result.size(), rateLimitHits);
                    throw new CustomException(ErrorCode.COLLECT_API_FAILED);
                }
                sleepQuietly(rateLimitCooldownMs);
                continue;
            }

            BokjiroCentralDto response = fetchResult.getPayload();
            if (response == null || response.getItems() == null || response.getItems().isEmpty()) {
                break;
            }
            rateLimitHits = 0;

            result.addAll(response.getItems());

            if (response.getItems().size() < PAGE_SIZE) {
                break; // 마지막 페이지
            }

            pageNo++;
        }

        log.info("[BokjiroCentralClient] 수집 완료: {}건", result.size());
        return result;
    }

    private PageFetchResult<BokjiroCentralDto> fetchPage(int pageNo, int numOfRows) {
        String encodedKey = URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
        String url = "https://apis.data.go.kr/B554287/NationalWelfareInformationsV001/NationalWelfarelistV001"
                + "?serviceKey=" + encodedKey
                + "&callTp=D"
                + "&pageNo=" + pageNo
                + "&numOfRows=" + numOfRows
                + "&srchKeyCode=003"
                + "&arrgOrd=001";

        for (int attempt = 1; attempt <= retryMaxAttempts; attempt++) {
            try {
                String xml = webClient.get()
                        .uri(URI.create(url))
                        .retrieve()
                        .bodyToMono(String.class)
                        .block(Duration.ofSeconds(20));

                if (xml == null || xml.isBlank()) {
                    return PageFetchResult.success(null);
                }

                return PageFetchResult.success(xmlMapper.readValue(xml, BokjiroCentralDto.class));
            } catch (WebClientResponseException e) {
                int status = e.getStatusCode().value();
                if (status == 429) {
                    if (attempt >= retryMaxAttempts) {
                        return PageFetchResult.rateLimited();
                    }

                    long waitMs = retryBaseBackoffMs * attempt + ThreadLocalRandom.current().nextLong(100, 400);
                    log.warn("[BokjiroCentralClient] 429 재시도 page={} attempt={}/{} waitMs={}",
                            pageNo, attempt, retryMaxAttempts, waitMs);
                    sleepQuietly(waitMs);
                    continue;
                }

                log.error("[BokjiroCentralClient] 수집 실패 page={} status={}: {}", pageNo, status, e.getMessage(), e);
                throw new CustomException(ErrorCode.COLLECT_API_FAILED);
            } catch (Exception e) {
                log.error("[BokjiroCentralClient] 수집 실패 page={}: {}", pageNo, e.getMessage(), e);
                throw new CustomException(ErrorCode.COLLECT_API_FAILED);
            }
        }

        return PageFetchResult.rateLimited();
    }

    private void sleepQuietly(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("복지로 중앙 수집 대기 중 인터럽트 발생", e);
        }
    }

    @lombok.Getter
    @lombok.RequiredArgsConstructor(staticName = "of")
    private static class PageFetchResult<T> {
        private final T payload;
        private final boolean rateLimited;

        static <T> PageFetchResult<T> success(T payload) {
            return PageFetchResult.of(payload, false);
        }

        static <T> PageFetchResult<T> rateLimited() {
            return PageFetchResult.of(null, true);
        }
    }
}
