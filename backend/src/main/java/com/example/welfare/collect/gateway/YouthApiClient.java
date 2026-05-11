package com.example.welfare.collect.gateway;

import com.example.welfare.collect.dto.YouthApiDto;
import com.example.welfare.collect.gateway.CollectHttpRetryExecutor.ExecutionResult;
import com.example.welfare.collect.gateway.CollectHttpRetryExecutor.HttpFailureAction;
import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class YouthApiClient {

    private final WebClient webClient;
    private final CollectHttpRetryExecutor collectHttpRetryExecutor;

    @Value("${youth-api.api-key}")
    private String apiKey;
    @Value("${collect.list.retry.max-attempts:3}")
    private int retryMaxAttempts;
    @Value("${collect.list.retry.base-backoff-ms:1000}")
    private long retryBaseBackoffMs;
    @Value("${collect.list.request-interval-ms:300}")
    private long requestIntervalMs;

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
            if (pageNum > 1) {
                sleepQuietly(requestIntervalMs);
            }
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

    public YouthApiDto.Item fetchDetail(String plcyNo) {
        ExecutionResult<YouthApiDto> result = collectHttpRetryExecutor.execute(
                "YouthApiClient",
                "detail=" + plcyNo,
                retryMaxAttempts,
                retryBaseBackoffMs,
                status -> isRetryableStatus(status) ? HttpFailureAction.RETRYABLE : HttpFailureAction.FAIL_FAST,
                () -> webClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .scheme("https")
                                .host("www.youthcenter.go.kr")
                                .path("/go/ythip/getPlcy")
                                .queryParam("apiKeyNm", apiKey)
                                .queryParam("pageType", "2")
                                .queryParam("plcyNo", plcyNo)
                                .queryParam("rtnType", "json")
                                .build())
                        .retrieve()
                        .bodyToMono(YouthApiDto.class)
                        .block(Duration.ofSeconds(20))
        );
        if (result.rateLimited()) return null;
        YouthApiDto dto = result.payload();
        if (dto == null || dto.getResult() == null
                || dto.getResult().getYouthPolicyList() == null
                || dto.getResult().getYouthPolicyList().isEmpty()) {
            return null;
        }
        return dto.getResult().getYouthPolicyList().get(0);
    }

    private YouthApiDto fetchPage(int pageNum, int pageSize) {
        ExecutionResult<YouthApiDto> result = collectHttpRetryExecutor.execute(
                "YouthApiClient",
                "page=" + pageNum,
                retryMaxAttempts,
                retryBaseBackoffMs,
                status -> isRetryableStatus(status) ? HttpFailureAction.RETRYABLE : HttpFailureAction.FAIL_FAST,
                () -> webClient.get()
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
                        .block(Duration.ofSeconds(20))
        );
        if (result.rateLimited()) {
            throw new CustomException(ErrorCode.COLLECT_API_FAILED);
        }
        return result.payload();
    }

    private boolean isRetryableStatus(int status) {
        return status == 400 || status == 429 || status >= 500;
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
