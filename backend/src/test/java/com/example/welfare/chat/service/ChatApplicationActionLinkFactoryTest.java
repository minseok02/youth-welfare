package com.example.welfare.chat.service;

import com.example.welfare.chat.dto.response.ChatActionLinkResponse;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.entity.WelfareServiceDetail;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChatApplicationActionLinkFactoryTest {

    private final ChatApplicationActionLinkFactory factory = new ChatApplicationActionLinkFactory(new ObjectMapper());

    @Test
    @DisplayName("본문 추출 링크의 문장 부호와 조사 꼬리표를 제거해 안전한 URL만 action link로 만든다")
    void createLinksSanitizesExtractedTextUrlNoise() {
        WelfareService policy = WelfareService.builder()
                .id(21L)
                .detailUrl("https://detail.example.com")
                .build();
        WelfareServiceDetail detail = WelfareServiceDetail.builder()
                .referenceUrlsJson("""
                        [
                          {"url":"https://www.gov.kr)접속(공동인증서","type":"EXTRACTED_FROM_TEXT","label":"본문 추출 링크","sourceField":"applyMethodDetail"},
                          {"url":"https://apply.example.com/path?x=1&)","type":"APPLY","label":"온라인 신청 사이트","sourceField":"onlineApplySiteUrl"}
                        ]
                        """)
                .build();

        List<ChatActionLinkResponse> links = factory.createLinks(policy, detail);

        assertThat(links)
                .extracting(ChatActionLinkResponse::getUrl)
                .contains("https://www.gov.kr", "https://apply.example.com/path?x=1");
        assertThat(links)
                .extracting(ChatActionLinkResponse::getType)
                .contains("NOTICE", "OFFICIAL_APPLY", "RELATED_SITE");
    }

    @Test
    @DisplayName("한글 도메인 URL은 punycode 로 보존한다")
    void createLinksPreservesInternationalizedDomainName() {
        WelfareService policy = WelfareService.builder()
                .id(22L)
                .detailUrl("https://detail.example.com")
                .build();
        WelfareServiceDetail detail = WelfareServiceDetail.builder()
                .referenceUrlsJson("""
                        [
                          {"url":"https://www.디지털배움터.kr)","type":"DETAIL","label":"본문 추출 링크","sourceField":"applyMethodDetail"},
                          {"url":"https://valid.example.com/notice","type":"DETAIL","label":"관련 사이트","sourceField":"detailUrl"}
                        ]
                        """)
                .build();

        List<ChatActionLinkResponse> links = factory.createLinks(policy, detail);

        assertThat(links)
                .extracting(ChatActionLinkResponse::getUrl)
                .doesNotContain("https://www")
                .contains(
                        "https://www.xn--2z1bw8k1pjz5ccumkb.kr",
                        "https://valid.example.com/notice",
                        "https://detail.example.com");
    }

    @Test
    @DisplayName("도메인이 단일 라벨로 잘린 URL은 action link에서 제외한다")
    void createLinksDropsTruncatedHost() {
        WelfareService policy = WelfareService.builder()
                .id(23L)
                .detailUrl("https://detail.example.com")
                .build();
        WelfareServiceDetail detail = WelfareServiceDetail.builder()
                .referenceUrlsJson("""
                        [
                          {"url":"https://www)","type":"DETAIL","label":"본문 추출 링크","sourceField":"applyMethodDetail"},
                          {"url":"https://valid.example.com/notice","type":"DETAIL","label":"관련 사이트","sourceField":"detailUrl"}
                        ]
                        """)
                .build();

        List<ChatActionLinkResponse> links = factory.createLinks(policy, detail);

        assertThat(links)
                .extracting(ChatActionLinkResponse::getUrl)
                .doesNotContain("https://www")
                .contains("https://valid.example.com/notice", "https://detail.example.com");
    }
}
