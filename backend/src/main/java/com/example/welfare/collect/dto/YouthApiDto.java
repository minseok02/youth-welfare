package com.example.welfare.collect.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.util.List;

/**
 * 온통청년 공공API JSON 응답 DTO
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class YouthApiDto {

    @JsonProperty("body")
    private Body body;

    @Getter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Body {
        @JsonProperty("items")
        private List<Item> items;

        @JsonProperty("totalCount")
        private Integer totalCount;

        @JsonProperty("pageNo")
        private Integer pageNo;

        @JsonProperty("numOfRows")
        private Integer numOfRows;
    }

    @Getter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Item {

        @JsonProperty("bizId")
        private String bizId;               // 정책번호 → source_id

        @JsonProperty("polyBizSjnm")
        private String polyBizSjnm;         // 정책명 → title

        @JsonProperty("polyItcnCn")
        private String polyItcnCn;          // 정책소개 → description

        @JsonProperty("sporCn")
        private String sporCn;              // 지원내용 → support_content

        @JsonProperty("polyBizTy")
        private String polyBizTy;           // 정책대부류명 → category_main

        @JsonProperty("polyBizSecd")
        private String polyBizSecd;         // 정책중부류명 → category_sub

        @JsonProperty("keywords")
        private String keywords;            // 키워드 (콤마 구분) → service_tags KEYWORD

        @JsonProperty("plyBizInsDt")
        private String plyBizInsDt;         // 등록일 → registered_at

        @JsonProperty("plyBizMdfcnDt")
        private String plyBizMdfcnDt;       // 수정일 → last_modified_at

        @JsonProperty("sporScvl")
        private String sporScvl;            // 지원규모

        @JsonProperty("rqutPrdSe")
        private String rqutPrdSe;           // 신청기간 구분

        @JsonProperty("rqutUrla")
        private String rqutUrla;            // 신청URL → detail_url

        @JsonProperty("aplyMthdItm")
        private String aplyMthdItm;         // 신청방법 → apply_method_name

        @JsonProperty("mngtMson")
        private String mngtMson;            // 주관기관명 → host_org

        @JsonProperty("implMson")
        private String implMson;            // 이행기관명 → operating_org

        @JsonProperty("minAge")
        private Integer minAge;             // 최소나이

        @JsonProperty("maxAge")
        private Integer maxAge;             // 최대나이

        @JsonProperty("incmeLowLimit")
        private Integer incmeLowLimit;      // 소득하한 → min_income

        @JsonProperty("incmeUpLimit")
        private Integer incmeUpLimit;       // 소득상한 → max_income

        @JsonProperty("bizPrdBgngDt")
        private String bizPrdBgngDt;        // 사업시작일 → start_date

        @JsonProperty("bizPrdEndDt")
        private String bizPrdEndDt;         // 사업종료일 → end_date

        @JsonProperty("rqutPrdBgngDt")
        private String rqutPrdBgngDt;       // 신청시작일 → apply_start_date

        @JsonProperty("rqutPrdEndDt")
        private String rqutPrdEndDt;        // 신청종료일 → apply_end_date

        @JsonProperty("regionCd")
        private String regionCd;            // 지역코드 (콤마 구분) → service_regions

        @JsonProperty("inqNum")
        private Long inqNum;                // 조회수 → api_view_count
    }
}
