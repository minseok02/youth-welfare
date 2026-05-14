package com.example.welfare.collect.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.util.List;

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class Gov24ServiceDetailDto {

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

        @JsonProperty("서비스목적")
        private String servicePurpose;

        @JsonProperty("신청기한")
        private String applyDeadline;

        @JsonProperty("지원대상")
        private String supportTarget;

        @JsonProperty("선정기준")
        private String selectionCriteria;

        @JsonProperty("지원내용")
        private String supportContent;

        @JsonProperty("신청방법")
        private String applyMethod;

        @JsonProperty("구비서류")
        private String requiredDocuments;

        @JsonProperty("접수기관명")
        private String receptionOrganizationName;

        @JsonProperty("문의처")
        private String contact;

        @JsonProperty("온라인신청사이트URL")
        private String onlineApplySiteUrl;

        @JsonProperty("수정일시")
        private String modifiedAt;

        @JsonProperty("소관기관명")
        private String managingOrganizationName;

        @JsonProperty("행정규칙")
        private String administrativeRule;

        @JsonProperty("자치법규")
        private String localRegulation;

        @JsonProperty("법령")
        private String law;

        @JsonProperty("공무원확인구비서류")
        private String publicOfficerVerifiedDocuments;

        @JsonProperty("본인확인필요구비서류")
        private String identityVerificationDocuments;
    }
}
