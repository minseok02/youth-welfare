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
}
