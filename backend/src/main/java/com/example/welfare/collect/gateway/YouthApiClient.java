package com.example.welfare.collect.gateway;

import com.example.welfare.collect.dto.YouthApiDto;
import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Component
@RequiredArgsConstructor
public class YouthApiClient {

    private final WebClient webClient;

    @Value("${youth-api.api-key}")
    private String apiKey;
    @Value("${collect.list.retry.max-attempts:3}")
    private int retryMaxAttempts;
    @Value("${collect.list.retry.base-backoff-ms:1000}")
    private long retryBaseBackoffMs;

    private static final int PAGE_SIZE = 100;

    /**
     * 온통청년 정책 전체 수집 (JSON, 페이징)
     * 현재 운영 확인 기준 유효 엔드포인트: GET https://www.youthcenter.go.kr/go/ythip/getPlcy
     * 공식 문서의 /opi/youthPlcyList.do 예시는 302로 내부 8080 포트로 리다이렉트되며,
     * 현재 실행 환경에서는 해당 리다이렉트 대상이 접근되지 않아 레거시 엔드포인트를 사용한다.
     */
    public List<YouthApiDto.Item> fetchAll() {
        List<YouthApiDto.Item> result = new ArrayList<>();
        int pageNum = 1;

        while (true) {
            YouthApiDto response = fetchPage(pageNum, PAGE_SIZE);
            if (response == null || response.getResult() == null
                    || response.getResult().getYouthPolicyList() == null
                    || response.getResult().getYouthPolicyList().isEmpty()) {
                break;
            }

            List<YouthApiDto.Item> items = response.getResult().getYouthPolicyList();
            result.addAll(items);

            int totalCnt = response.getResult().getTotalCnt();
            if (result.size() >= totalCnt || items.size() < PAGE_SIZE) {
                break;
            }

            pageNum++;
        }

        log.info("[YouthApiClient] 수집 완료: {}건", result.size());
        return result;
    }

    private YouthApiDto fetchPage(int pageNum, int pageSize) {
        for (int attempt = 1; attempt <= retryMaxAttempts; attempt++) {
            try {
                return webClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .scheme("https")
                                .host("www.youthcenter.go.kr")
                                .path("/go/ythip/getPlcy")
                                .queryParam("apiKeyNm", apiKey)
                                .queryParam("pageNum", pageNum)
                                .queryParam("pageSize", pageSize)
                                .build())
                        .retrieve()
                        .bodyToMono(YouthApiDto.class)
                        .block(Duration.ofSeconds(20));
            } catch (WebClientResponseException e) {
                int status = e.getStatusCode().value();
                if (isRetryableStatus(status) && attempt < retryMaxAttempts) {
                    long waitMs = retryBaseBackoffMs * attempt + ThreadLocalRandom.current().nextLong(100, 400);
                    log.warn("[YouthApiClient] 수집 재시도 page={} status={} attempt={}/{} waitMs={}",
                            pageNum, status, attempt, retryMaxAttempts, waitMs);
                    sleepQuietly(waitMs);
                    continue;
                }

                log.error("[YouthApiClient] 수집 실패 page={} status={}: {}", pageNum, status, e.getMessage());
                throw new CustomException(ErrorCode.COLLECT_API_FAILED);
            } catch (Exception e) {
                if (attempt < retryMaxAttempts) {
                    long waitMs = retryBaseBackoffMs * attempt + ThreadLocalRandom.current().nextLong(100, 400);
                    log.warn("[YouthApiClient] 수집 재시도 page={} attempt={}/{} waitMs={} err={}",
                            pageNum, attempt, retryMaxAttempts, waitMs, e.getMessage());
                    sleepQuietly(waitMs);
                    continue;
                }

                log.error("[YouthApiClient] 수집 실패 page={}: {}", pageNum, e.getMessage());
                throw new CustomException(ErrorCode.COLLECT_API_FAILED);
            }
        }

        throw new CustomException(ErrorCode.COLLECT_API_FAILED);
    }

    private boolean isRetryableStatus(int status) {
        return status == 429 || status >= 500;
    }

    private void sleepQuietly(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("온통청년 수집 재시도 대기 중 인터럽트 발생", e);
        }
    }
}
