package com.example.welfare.collect.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import lombok.Getter;

import java.util.List;

/**
 * 복지로 지자체 서비스 XML DTO
 */
@Getter
@JacksonXmlRootElement(localName = "wantedList")
@JsonIgnoreProperties(ignoreUnknown = true)
public class BokjiroLocalDto {

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

        @JacksonXmlProperty(localName = "bizChrDeptNm")
        private String bizChrDeptNm;        // operating_org (지자체 담당부서)

        @JacksonXmlProperty(localName = "lifeNmArray")
        private String lifeNmArray;         // 생애주기명 (콤마) → life_stage + LIFE_STAGE 태그

        @JacksonXmlProperty(localName = "intrsThemaNmArray")
        private String intrsThemaNmArray;   // 관심주제명 (콤마) → INTEREST_THEME + unified_category

        @JacksonXmlProperty(localName = "trgterIndvdlNmArray")
        private String trgterIndvdlNmArray; // 대상유형명 (콤마) → TARGET_GROUP 태그

        @JacksonXmlProperty(localName = "sprtCycNm")
        private String sprtCycNm;           // 지원주기 → support_cycle

        @JacksonXmlProperty(localName = "srvPvsnNm")
        private String srvPvsnNm;           // 제공유형 → provision_type

        @JacksonXmlProperty(localName = "aplyMtdNm")
        private String aplyMtdNm;           // 신청방법 → apply_method_name

        @JacksonXmlProperty(localName = "servDtlLink")
        private String servDtlLink;         // 상세URL → detail_url

        @JacksonXmlProperty(localName = "inqNum")
        private Long inqNum;                // 조회수 → api_view_count

        @JacksonXmlProperty(localName = "lastModYmd")
        private String lastModYmd;          // 최종수정일 (yyyyMMdd) → last_modified_at

        @JacksonXmlProperty(localName = "enfcBgngYmd")
        private String enfcBgngYmd;         // 시행시작일 (yyyyMMdd) → start_date

        @JacksonXmlProperty(localName = "enfcEndYmd")
        private String enfcEndYmd;          // 시행종료일 (yyyyMMdd) → end_date

        @JacksonXmlProperty(localName = "ctpvNm")
        private String ctpvNm;              // 시도명 → service_regions.sido_name

        @JacksonXmlProperty(localName = "sggNm")
        private String sggNm;               // 시군구명 → service_regions.sgg_name
    }
}
