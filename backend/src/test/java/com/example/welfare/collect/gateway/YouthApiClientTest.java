package com.example.welfare.collect.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

class YouthApiClientTest {

    @Test
    @DisplayName("온통청년 목록 URI는 youth-api.base-url 설정값을 기준으로 만든다")
    void buildPageUriUsesConfiguredBaseUrl() {
        YouthApiClient client = client("https://example.test/custom/youth");

        URI uri = client.buildPageUri(2, 100);

        assertThat(uri.toString())
                .isEqualTo("https://example.test/custom/youth?apiKeyNm=test-key&pageNum=2&pageSize=100");
    }

    @Test
    @DisplayName("온통청년 상세 URI는 설정 base-url과 detail query contract를 사용한다")
    void buildDetailUriUsesConfiguredBaseUrlAndDetailParameters() {
        YouthApiClient client = client("https://www.youthcenter.go.kr/go/ythip/getPlcy");

        URI uri = client.buildDetailUri("P 1");

        assertThat(uri.toString())
                .isEqualTo("https://www.youthcenter.go.kr/go/ythip/getPlcy?apiKeyNm=test-key&pageType=2&plcyNo=P%201&rtnType=json");
    }

    private YouthApiClient client(String baseUrl) {
        YouthApiClient client = new YouthApiClient(null, null);
        ReflectionTestUtils.setField(client, "apiKey", "test-key");
        ReflectionTestUtils.setField(client, "baseUrl", baseUrl);
        return client;
    }
}
