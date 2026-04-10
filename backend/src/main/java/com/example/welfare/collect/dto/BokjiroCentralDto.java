package com.example.welfare.collect.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import lombok.Getter;

import java.util.List;

/**
 * 복지로 중앙정부 서비스 XML DTO
 */
@Getter
@JacksonXmlRootElement(localName = "wantedList")
@JsonIgnoreProperties(ignoreUnknown = true)
public class BokjiroCentralDto {

    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "servList")
    private List<Item> items;

    @Getter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Item {

        @JacksonXmlProperty(localName = "servId")
        private String servId;              // source_id

        @JacksonXmlProperty(localName = "servNm")
        private String servNm;              // title

        @JacksonXmlProperty(localName = "servDgst")
        private String servDgst;            // description

        @JacksonXmlProperty(localName = "jurMnofNm")
        private String jurMnofNm;           // host_org

        @JacksonXmlProperty(localName = "jurOrgNm")
        private String jurOrgNm;            // operating_org

        @JacksonXmlProperty(localName = "lifeArray")
        private String lifeArray;           // 생애주기 (콤마) → life_stage + service_tags LIFE_STAGE

        @JacksonXmlProperty(localName = "intrsThemaArray")
        private String intrsThemaArray;     // 관심주제 (콤마) → service_tags INTEREST_THEME + unified_category

        @JacksonXmlProperty(localName = "trgterIndvdlArray")
        private String trgterIndvdlArray;   // 대상유형 (콤마) → service_tags TARGET_GROUP

        @JacksonXmlProperty(localName = "sprtCycNm")
        private String sprtCycNm;           // 지원주기 → support_cycle

        @JacksonXmlProperty(localName = "srvPvsnNm")
        private String srvPvsnNm;           // 제공유형 → provision_type

        @JacksonXmlProperty(localName = "onapPsbltYn")
        private String onapPsbltYn;         // 온라인신청 가능여부 (Y/N) → is_online_apply

        @JacksonXmlProperty(localName = "rprsCtadr")
        private String rprsCtadr;           // 대표 연락처

        @JacksonXmlProperty(localName = "servDtlLink")
        private String servDtlLink;         // 상세URL → detail_url

        @JacksonXmlProperty(localName = "inqNum")
        private Long inqNum;                // 조회수 → api_view_count

        @JacksonXmlProperty(localName = "svcfrstRegTs")
        private String svcfrstRegTs;        // 최초등록일시 → registered_at
    }
}
