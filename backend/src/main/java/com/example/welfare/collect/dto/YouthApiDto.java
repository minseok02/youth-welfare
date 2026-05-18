package com.example.welfare.collect.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.util.List;

/**
 * 온통청년 공공API JSON 응답 DTO
 * 현재 운영 확인 기준 엔드포인트: GET https://www.youthcenter.go.kr/go/ythip/getPlcy
 * 실제 응답 구조: { "result": { "youthPolicyList": [...], "totalCnt": N } }
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class YouthApiDto {

    @JsonProperty("result")
    private Result result;

    @Getter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Result {
        @JsonProperty("youthPolicyList")
        private List<Item> youthPolicyList;

        @JsonProperty("pagging")
        private Pagging pagging;

        public int getTotalCnt() {
            return pagging != null && pagging.getTotCount() != null ? pagging.getTotCount() : 0;
        }
    }

    @Getter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Pagging {
        @JsonProperty("totCount")
        private Integer totCount;

        @JsonProperty("pageNum")
        private Integer pageNum;

        @JsonProperty("pageSize")
        private Integer pageSize;
    }

    @Getter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Item {

        @JsonProperty("plcyNo")
        private String plcyNo;              // 정책번호 → source_id

        @JsonProperty("plcyNm")
        private String plcyNm;              // 정책명 → title

        @JsonProperty("plcyExplnCn")
        private String plcyExplnCn;         // 정책소개 → description

        @JsonProperty("plcySprtCn")
        private String plcySprtCn;          // 지원내용 → support_content

        @JsonProperty("lclsfNm")
        private String lclsfNm;             // 정책대분류명 → category_main (일자리/주거/교육/복지문화/참여권리)

        @JsonProperty("mclsfNm")
        private String mclsfNm;             // 정책중분류명 → category_sub

        @JsonProperty("plcyKywdNm")
        private String plcyKywdNm;          // 키워드 (콤마 구분) → service_tags KEYWORD

        @JsonProperty("plcyPvsnMthdCd")
        private String plcyPvsnMthdCd;      // 정책제공방법코드 → PROVISION_METHOD summary label

        @JsonProperty("sprvsnInstCdNm")
        private String sprvsnInstCdNm;      // 주관기관명 → host_org

        @JsonProperty("operInstCdNm")
        private String operInstCdNm;        // 운영기관명 → operating_org

        @JsonProperty("sprtTrgtMinAge")
        private Integer sprtTrgtMinAge;     // 지원대상 최소나이 → min_age

        @JsonProperty("sprtTrgtMaxAge")
        private Integer sprtTrgtMaxAge;     // 지원대상 최대나이 → max_age

        @JsonProperty("earnMinAmt")
        private Integer earnMinAmt;         // 소득하한 → min_income

        @JsonProperty("earnMaxAmt")
        private Integer earnMaxAmt;         // 소득상한 → max_income

        @JsonProperty("bizPrdBgngYmd")
        private String bizPrdBgngYmd;       // 사업시작일 (yyyyMMdd) → start_date

        @JsonProperty("bizPrdEndYmd")
        private String bizPrdEndYmd;        // 사업종료일 (yyyyMMdd) → end_date

        /**
         * 신청기간 문자열 (예: "20260101 ~ 20261231", "상시모집", null).
         * 범위 형식이므로 파싱하지 않음 — apply_method_name에 그대로 저장.
         */
        @JsonProperty("aplyYmd")
        private String aplyYmd;             // 신청기간 → apply_method_name에 포함

        @JsonProperty("plcyAplyMthdCn")
        private String plcyAplyMthdCn;      // 신청방법 → apply_method_name

        @JsonProperty("aplyUrlAddr")
        private String aplyUrlAddr;         // 신청URL → detail_url

        @JsonProperty("refUrlAddr1")
        private String refUrlAddr1;         // 참고URL1 → aplyUrlAddr 없을 때 폴백

        @JsonProperty("refUrlAddr2")
        private String refUrlAddr2;         // 참고URL2 → refUrlAddr1도 없을 때 폴백

        @JsonProperty("sbizCd")
        private String sbizCd;             // 정책특화요건코드

        @JsonProperty("zipCd")
        private String zipCd;               // 지역코드 (콤마 구분) → service_regions

        @JsonProperty("inqCnt")
        private Long inqCnt;                // 조회수 → api_view_count

        /**
         * 등록일시 (형식: "yyyy-MM-dd HH:mm:ss" 또는 "yyyyMMdd").
         * parseDateTimeLoose에서 두 형식 모두 처리.
         */
        @JsonProperty("frstRegDt")
        private String frstRegDt;           // 최초등록일시 → registered_at

        @JsonProperty("lastMdfcnDt")
        private String lastMdfcnDt;         // 최종수정일시 → last_modified_at
    }
}
