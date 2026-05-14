package com.example.welfare.collect.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.util.List;

/**
 * Gov24 공공서비스 목록 JSON 응답 DTO.
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class Gov24ServiceListDto {

    @JsonProperty("page")
    private Integer page;

    @JsonProperty("perPage")
    private Integer perPage;

    @JsonProperty("totalCount")
    private Integer totalCount;

    @JsonProperty("currentCount")
    private Integer currentCount;

    @JsonProperty("matchCount")
    private Integer matchCount;

    @JsonProperty("data")
    private List<Item> data;

    @Getter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Item {

        @JsonProperty("서비스ID")
        private String serviceId;

        @JsonProperty("지원유형")
        private String supportType;

        @JsonProperty("서비스명")
        private String serviceName;

        @JsonProperty("서비스목적요약")
        private String servicePurposeSummary;

        @JsonProperty("지원대상")
        private String supportTarget;

        @JsonProperty("선정기준")
        private String selectionCriteria;

        @JsonProperty("지원내용")
        private String supportContent;

        @JsonProperty("신청방법")
        private String applyMethod;

        @JsonProperty("신청기한")
        private String applyDeadline;

        @JsonProperty("상세조회URL")
        private String detailUrl;

        @JsonProperty("소관기관코드")
        private String managingOrganizationCode;

        @JsonProperty("소관기관명")
        private String managingOrganizationName;

        @JsonProperty("부서명")
        private String departmentName;

        @JsonProperty("조회수")
        private Long viewCount;

        @JsonProperty("소관기관유형")
        private String managingOrganizationType;

        @JsonProperty("사용자구분")
        private String userType;

        @JsonProperty("서비스분야")
        private String serviceField;

        @JsonProperty("접수기관")
        private String receptionOrganization;

        @JsonProperty("전화문의")
        private String inquiryContact;

        @JsonProperty("등록일시")
        private String registeredAt;

        @JsonProperty("수정일시")
        private String modifiedAt;
    }
}
