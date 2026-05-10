package com.example.welfare.collect.gateway;

import com.example.welfare.collect.dto.BokjiroCentralDto;
import com.example.welfare.collect.gateway.CollectHttpRetryExecutor.ExecutionResult;
import com.example.welfare.collect.gateway.CollectHttpRetryExecutor.HttpFailureAction;
import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class BokjiroCentralClient {

    private final WebClient webClient;
    private final XmlMapper xmlMapper;
    private final CollectHttpRetryExecutor collectHttpRetryExecutor;

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
    @Value("${collect.list.max-items-per-run:10000}")
    private int maxItemsPerRun;

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
            if (result.size() >= maxItemsPerRun) {
                log.warn("[BokjiroCentralClient] max-items-per-run 상한 도달 collected={} maxItemsPerRun={}",
                        result.size(), maxItemsPerRun);
                break;
            }
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

            int remainingSlots = Math.max(maxItemsPerRun - result.size(), 0);
            if (remainingSlots <= 0) {
                log.warn("[BokjiroCentralClient] max-items-per-run 상한 도달 collected={} maxItemsPerRun={}",
                        result.size(), maxItemsPerRun);
                break;
            }

            if (response.getItems().size() > remainingSlots) {
                result.addAll(response.getItems().subList(0, remainingSlots));
                log.warn("[BokjiroCentralClient] max-items-per-run 상한으로 조기 종료 page={} collected={} maxItemsPerRun={}",
                        pageNo, result.size(), maxItemsPerRun);
                break;
            }

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

        ExecutionResult<String> result = collectHttpRetryExecutor.execute(
                "BokjiroCentralClient",
                "page=" + pageNo,
                retryMaxAttempts,
                retryBaseBackoffMs,
                status -> {
                    if (status == 429) {
                        return HttpFailureAction.RATE_LIMITED;
                    }
                    if (status >= 500) {
                        return HttpFailureAction.RETRYABLE;
                    }
                    return HttpFailureAction.FAIL_FAST;
                },
                () -> webClient.get()
                        .uri(URI.create(url))
                        .retrieve()
                        .bodyToMono(String.class)
                        .block(Duration.ofSeconds(20))
        );
        if (result.rateLimited()) {
            return PageFetchResult.rateLimited();
        }

        String xml = result.payload();
        if (xml == null || xml.isBlank()) {
            return PageFetchResult.success(null);
        }
        try {
            return PageFetchResult.success(xmlMapper.readValue(xml, BokjiroCentralDto.class));
        } catch (Exception e) {
            log.error("[BokjiroCentralClient] 수집 실패 page={} parseErr={}", pageNo, e.getMessage(), e);
            throw new CustomException(ErrorCode.COLLECT_API_FAILED);
        }
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
