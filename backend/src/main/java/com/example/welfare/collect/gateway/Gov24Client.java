package com.example.welfare.collect.gateway;

import com.example.welfare.collect.dto.Gov24ServiceDetailDto;
import com.example.welfare.collect.dto.Gov24ServiceListDto;
import com.example.welfare.collect.dto.Gov24SupportConditionsDto;
import com.example.welfare.collect.gateway.CollectHttpRetryExecutor.ExecutionResult;
import com.example.welfare.collect.gateway.CollectHttpRetryExecutor.HttpFailureAction;
import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class Gov24Client {

    private static final int PAGE_SIZE = 100;
    private static final String COND_SERVICE_ID_EQ =
            URLEncoder.encode("cond[서비스ID::EQ]", StandardCharsets.UTF_8);

    private final WebClient webClient;
    private final CollectHttpRetryExecutor collectHttpRetryExecutor;

    @Value("${gov24.api-key}")
    private String apiKey;
    @Value("${gov24.base-url:https://api.odcloud.kr/api}")
    private String baseUrl;
    @Value("${collect.list.request-interval-ms:300}")
    private long requestIntervalMs;
    @Value("${collect.list.retry.max-attempts:3}")
    private int retryMaxAttempts;
    @Value("${collect.list.retry.base-backoff-ms:1000}")
    private long retryBaseBackoffMs;
    @Value("${gov24.max-items-per-run:20000}")
    private int maxItemsPerRun;

    @PostConstruct
    private void encodeApiKey() {
        this.apiKey = URLEncoder.encode(this.apiKey, StandardCharsets.UTF_8);
    }

    public List<Gov24ServiceListDto.Item> fetchAll() {
        List<Gov24ServiceListDto.Item> result = new ArrayList<>();
        int page = 1;

        while (true) {
            if (result.size() >= maxItemsPerRun) {
                log.warn("[Gov24Client] max-items-per-run 상한 도달 collected={} maxItemsPerRun={}",
                        result.size(), maxItemsPerRun);
                break;
            }

            if (page > 1) {
                sleepQuietly(requestIntervalMs);
            }

            Gov24ServiceListDto response = fetchPage(page, PAGE_SIZE);
            if (response == null || response.getData() == null || response.getData().isEmpty()) {
                break;
            }

            int remainingSlots = Math.max(maxItemsPerRun - result.size(), 0);
            if (response.getData().size() > remainingSlots) {
                result.addAll(response.getData().subList(0, remainingSlots));
                log.warn("[Gov24Client] max-items-per-run 상한으로 조기 종료 page={} collected={} maxItemsPerRun={}",
                        page, result.size(), maxItemsPerRun);
                break;
            }

            result.addAll(response.getData());

            Integer totalCount = response.getTotalCount();
            Integer currentCount = response.getCurrentCount();
            if ((totalCount != null && result.size() >= totalCount)
                    || (currentCount != null && currentCount < PAGE_SIZE)) {
                break;
            }

            page++;
        }

        log.info("[Gov24Client] 수집 완료: {}건", result.size());
        return result;
    }

    public Gov24ServiceDetailDto.Item fetchDetail(String serviceId) {
        URI uri = UriComponentsBuilder.fromHttpUrl(baseUrl)
                .path("/gov24/v3/serviceDetail")
                .queryParam("serviceKey", apiKey)
                .queryParam("page", 1)
                .queryParam("perPage", 1)
                .queryParam("returnType", "JSON")
                .queryParam(COND_SERVICE_ID_EQ, serviceId)
                .build(true)
                .toUri();

        ExecutionResult<Gov24ServiceDetailDto> result = collectHttpRetryExecutor.execute(
                "Gov24Client",
                "detail=" + serviceId,
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
                        .uri(uri)
                        .retrieve()
                        .bodyToMono(Gov24ServiceDetailDto.class)
                        .block(Duration.ofSeconds(20))
        );

        if (result.rateLimited()) {
            return null;
        }
        Gov24ServiceDetailDto payload = result.payload();
        if (payload == null || payload.getData() == null || payload.getData().isEmpty()) {
            return null;
        }
        return payload.getData().get(0);
    }

    public Gov24SupportConditionsDto.Item fetchSupportConditions(String serviceId) {
        URI uri = UriComponentsBuilder.fromHttpUrl(baseUrl)
                .path("/gov24/v3/supportConditions")
                .queryParam("serviceKey", apiKey)
                .queryParam("page", 1)
                .queryParam("perPage", 1)
                .queryParam("returnType", "JSON")
                .queryParam(COND_SERVICE_ID_EQ, serviceId)
                .build(true)
                .toUri();

        ExecutionResult<Gov24SupportConditionsDto> result = collectHttpRetryExecutor.execute(
                "Gov24Client",
                "supportConditions=" + serviceId,
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
                        .uri(uri)
                        .retrieve()
                        .bodyToMono(Gov24SupportConditionsDto.class)
                        .block(Duration.ofSeconds(20))
        );

        if (result.rateLimited()) {
            return null;
        }
        Gov24SupportConditionsDto payload = result.payload();
        if (payload == null || payload.getData() == null || payload.getData().isEmpty()) {
            return null;
        }
        return payload.getData().get(0);
    }

    private Gov24ServiceListDto fetchPage(int page, int perPage) {
        URI uri = UriComponentsBuilder.fromHttpUrl(baseUrl)
                .path("/gov24/v3/serviceList")
                .queryParam("serviceKey", apiKey)
                .queryParam("page", page)
                .queryParam("perPage", perPage)
                .queryParam("returnType", "JSON")
                .build(true)
                .toUri();

        ExecutionResult<Gov24ServiceListDto> result = collectHttpRetryExecutor.execute(
                "Gov24Client",
                "page=" + page,
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
                        .uri(uri)
                        .retrieve()
                        .bodyToMono(Gov24ServiceListDto.class)
                        .block(Duration.ofSeconds(20))
        );

        if (result.rateLimited()) {
            throw new CustomException(ErrorCode.COLLECT_API_FAILED);
        }
        return result.payload();
    }

    private void sleepQuietly(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Gov24 수집 대기 중 인터럽트 발생", e);
        }
    }
}
