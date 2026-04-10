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

    private static final int PAGE_SIZE = 100;

    /**
     * 온통청년 정책 전체 수집 (JSON, 페이징)
     * 엔드포인트: GET https://www.youthcenter.go.kr/go/ythip/getPlcy
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
                    .block();
        } catch (Exception e) {
            log.error("[YouthApiClient] 수집 실패 page={}: {}", pageNum, e.getMessage());
            throw new CustomException(ErrorCode.COLLECT_API_FAILED);
        }
    }
}
