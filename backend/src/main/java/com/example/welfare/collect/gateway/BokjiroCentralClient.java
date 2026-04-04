package com.example.welfare.collect.gateway;

import com.example.welfare.collect.dto.BokjiroCentralDto;
import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class BokjiroCentralClient {

    private final WebClient webClient;

    @Value("${bokjiro.api-key}")
    private String apiKey;

    @Value("${bokjiro.central-base-url}")
    private String baseUrl;

    private static final int PAGE_SIZE = 100;

    /**
     * 복지로 중앙정부 서비스 전체 수집 (XML, 페이징)
     * XXE 비활성화는 WebClientConfig의 XmlMapper 설정에서 처리
     */
    public List<BokjiroCentralDto.Item> fetchAll() {
        List<BokjiroCentralDto.Item> result = new ArrayList<>();
        int pageNo = 1;

        while (true) {
            BokjiroCentralDto response = fetchPage(pageNo, PAGE_SIZE);
            if (response == null || response.getItems() == null || response.getItems().isEmpty()) {
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

    private BokjiroCentralDto fetchPage(int pageNo, int numOfRows) {
        try {
            return webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("https")
                            .host("www.bokjiro.go.kr")
                            .path("/ssis-tbu/twataa/wlfareInfo/moveTWAT52011M.do")
                            .queryParam("serviceKey", apiKey)
                            .queryParam("pageNo", pageNo)
                            .queryParam("numOfRows", numOfRows)
                            .queryParam("srchTrgetAge", "19")    // 청년 대상 필터
                            .build())
                    .accept(MediaType.APPLICATION_XML)
                    .retrieve()
                    .bodyToMono(BokjiroCentralDto.class)
                    .block();
        } catch (Exception e) {
            log.error("[BokjiroCentralClient] 수집 실패 page={}: {}", pageNo, e.getMessage());
            throw new CustomException(ErrorCode.COLLECT_API_FAILED);
        }
    }
}
