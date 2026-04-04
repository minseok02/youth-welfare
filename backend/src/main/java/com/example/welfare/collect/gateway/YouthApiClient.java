package com.example.welfare.collect.gateway;

import com.example.welfare.collect.dto.YouthApiDto;
import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class YouthApiClient {

    private final WebClient webClient;

    @Value("${youth-api.api-key}")
    private String apiKey;

    @Value("${youth-api.base-url}")
    private String baseUrl;

    private static final int PAGE_SIZE = 100;

    /**
     * 온통청년 정책 전체 수집 (페이징)
     */
    public List<YouthApiDto.Item> fetchAll() {
        List<YouthApiDto.Item> result = new ArrayList<>();
        int pageNo = 1;

        while (true) {
            YouthApiDto response = fetchPage(pageNo, PAGE_SIZE);
            if (response == null || response.getBody() == null
                    || response.getBody().getItems() == null
                    || response.getBody().getItems().isEmpty()) {
                break;
            }

            result.addAll(response.getBody().getItems());

            int totalCount = response.getBody().getTotalCount() != null
                    ? response.getBody().getTotalCount() : 0;
            if (result.size() >= totalCount) {
                break;
            }

            pageNo++;
        }

        log.info("[YouthApiClient] 수집 완료: {}건", result.size());
        return result;
    }

    private YouthApiDto fetchPage(int pageNo, int numOfRows) {
        try {
            return webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("https")
                            .host(extractHost(baseUrl))
                            .path(extractPath(baseUrl))
                            .queryParam("openApiVlak", apiKey)
                            .queryParam("pageIndex", pageNo)
                            .queryParam("pageSize", numOfRows)
                            .queryParam("srchPolyBizSecd", "")   // 전체 조회
                            .build())
                    .retrieve()
                    .bodyToMono(YouthApiDto.class)
                    .block();
        } catch (Exception e) {
            log.error("[YouthApiClient] 수집 실패 page={}: {}", pageNo, e.getMessage());
            throw new CustomException(ErrorCode.COLLECT_API_FAILED);
        }
    }

    private String extractHost(String url) {
        // e.g. "https://www.youthcenter.go.kr/..." → "www.youthcenter.go.kr"
        return url.replaceAll("https?://", "").split("/")[0];
    }

    private String extractPath(String url) {
        String withoutScheme = url.replaceAll("https?://[^/]+", "");
        return withoutScheme.isEmpty() ? "/" : withoutScheme;
    }
}
