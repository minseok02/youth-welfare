package com.example.welfare.collect.gateway;

import com.example.welfare.collect.dto.BokjiroLocalDto;
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
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class BokjiroLocalClient {

    private final WebClient webClient;
    private final XmlMapper xmlMapper;

    @Value("${bokjiro.api-key}")
    private String apiKey;

    private static final int PAGE_SIZE = 100;

    /**
     * 복지로 지자체 서비스 전체 수집 (XML, 페이징)
     * 엔드포인트: GET https://apis.data.go.kr/B554287/LocalGovernmentWelfareInformations/LcgvWelfarelist
     */
    public List<BokjiroLocalDto.Item> fetchAll() {
        List<BokjiroLocalDto.Item> result = new ArrayList<>();
        int pageNo = 1;

        while (true) {
            BokjiroLocalDto response = fetchPage(pageNo, PAGE_SIZE);
            if (response == null || response.getItems() == null || response.getItems().isEmpty()) {
                break;
            }

            result.addAll(response.getItems());

            if (response.getItems().size() < PAGE_SIZE) {
                break;
            }

            pageNo++;
        }

        log.info("[BokjiroLocalClient] 수집 완료: {}건", result.size());
        return result;
    }

    private BokjiroLocalDto fetchPage(int pageNo, int numOfRows) {
        try {
            String encodedKey = URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
            String url = "https://apis.data.go.kr/B554287/LocalGovernmentWelfareInformations/LcgvWelfarelist"
                    + "?serviceKey=" + encodedKey
                    + "&pageNo=" + pageNo
                    + "&numOfRows=" + numOfRows;

            // fetch as String to avoid content-type negotiation issues
            String xml = webClient.get()
                    .uri(URI.create(url))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (xml == null || xml.isBlank()) {
                return null;
            }

            return xmlMapper.readValue(xml, BokjiroLocalDto.class);
        } catch (Exception e) {
            log.error("[BokjiroLocalClient] 수집 실패 page={}: {}", pageNo, e.getMessage(), e);
            throw new CustomException(ErrorCode.COLLECT_API_FAILED);
        }
    }
}
