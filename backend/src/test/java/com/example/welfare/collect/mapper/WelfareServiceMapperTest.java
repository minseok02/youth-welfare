package com.example.welfare.collect.mapper;

import com.example.welfare.collect.dto.BokjiroCentralDto;
import com.example.welfare.collect.dto.BokjiroLocalDto;
import com.example.welfare.collect.dto.YouthApiDto;
import com.example.welfare.policy.entity.WelfareService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class WelfareServiceMapperTest {

    private final WelfareServiceMapper mapper = new WelfareServiceMapper();

    @Test
    void fromYouth_infersOnlineApplyFromDetailUrl() throws Exception {
        YouthApiDto.Item item = new YouthApiDto.Item();
        setField(item, "plcyNo", "Y001");
        setField(item, "plcyNm", "청년 주거 지원");
        setField(item, "plcyExplnCn", "정책 소개");
        setField(item, "plcySprtCn", "지원 내용");
        setField(item, "lclsfNm", "주거");
        setField(item, "aplyUrlAddr", "https://example.com/apply");

        WelfareService service = mapper.fromYouth(item);

        assertThat(service.getIsOnlineApply()).isTrue();
    }

    @Test
    void fromYouth_prefersApplyUrlOverReferenceUrls() throws Exception {
        YouthApiDto.Item item = new YouthApiDto.Item();
        setField(item, "plcyNo", "Y002");
        setField(item, "plcyNm", "청년 센터 운영");
        setField(item, "lclsfNm", "참여권리");
        setField(item, "aplyUrlAddr", "https://apply.example.com");
        setField(item, "refUrlAddr1", "https://reference-one.example.com");
        setField(item, "refUrlAddr2", "https://reference-two.example.com");

        WelfareService service = mapper.fromYouth(item);

        assertThat(service.getDetailUrl()).isEqualTo("https://apply.example.com");
    }

    @Test
    void fromYouth_fallsBackToFirstReferenceUrlWhenApplyUrlMissing() throws Exception {
        YouthApiDto.Item item = new YouthApiDto.Item();
        setField(item, "plcyNo", "Y003");
        setField(item, "plcyNm", "청년 센터 운영");
        setField(item, "lclsfNm", "참여권리");
        setField(item, "aplyUrlAddr", " ");
        setField(item, "refUrlAddr1", "https://reference-one.example.com");
        setField(item, "refUrlAddr2", "https://reference-two.example.com");

        WelfareService service = mapper.fromYouth(item);

        assertThat(service.getDetailUrl()).isEqualTo("https://reference-one.example.com");
    }

    @Test
    void fromYouth_fallsBackToSecondReferenceUrlWhenHigherPriorityUrlsMissing() throws Exception {
        YouthApiDto.Item item = new YouthApiDto.Item();
        setField(item, "plcyNo", "Y004");
        setField(item, "plcyNm", "청년 센터 운영");
        setField(item, "lclsfNm", "참여권리");
        setField(item, "aplyUrlAddr", null);
        setField(item, "refUrlAddr1", " ");
        setField(item, "refUrlAddr2", "https://reference-two.example.com");

        WelfareService service = mapper.fromYouth(item);

        assertThat(service.getDetailUrl()).isEqualTo("https://reference-two.example.com");
    }

    @Test
    void fromYouth_prefixesHttpsWhenReferenceUrlStartsWithWww() throws Exception {
        YouthApiDto.Item item = new YouthApiDto.Item();
        setField(item, "plcyNo", "Y005");
        setField(item, "plcyNm", "청년 취업 정보");
        setField(item, "lclsfNm", "일자리");
        setField(item, "refUrlAddr1", "www.work.go.kr");

        WelfareService service = mapper.fromYouth(item);

        assertThat(service.getDetailUrl()).isEqualTo("https://www.work.go.kr");
    }

    @Test
    void youthDto_deserializesReferenceUrls() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();

        YouthApiDto dto = objectMapper.readValue("""
                {
                  "result": {
                    "youthPolicyList": [
                      {
                        "plcyNo": "Y006",
                        "plcyNm": "청년 정책",
                        "plcyPvsnMthdCd": "0042006",
                        "jobCd": "0013003",
                        "schoolCd": "0049005",
                        "sbizCd": "0014008",
                        "refUrlAddr1": "https://reference-one.example.com",
                        "refUrlAddr2": "https://reference-two.example.com"
                      }
                    ],
                    "pagging": {
                      "totCount": 1
                    }
                  }
                }
                """, YouthApiDto.class);

        YouthApiDto.Item item = dto.getResult().getYouthPolicyList().get(0);
        assertThat(item.getPlcyPvsnMthdCd()).isEqualTo("0042006");
        assertThat(item.getJobCd()).isEqualTo("0013003");
        assertThat(item.getSchoolCd()).isEqualTo("0049005");
        assertThat(item.getSbizCd()).isEqualTo("0014008");
        assertThat(item.getRefUrlAddr1()).isEqualTo("https://reference-one.example.com");
        assertThat(item.getRefUrlAddr2()).isEqualTo("https://reference-two.example.com");
    }

    @Test
    void toYouthDetailAggregate_preservesReferenceUrlCandidates() throws Exception {
        YouthApiDto.Item detail = new YouthApiDto.Item();
        setField(detail, "plcyNo", "Y007");
        setField(detail, "plcyNm", "청년 정책");
        setField(detail, "lclsfNm", "주거");
        setField(detail, "plcyPvsnMthdCd", "0042003");
        setField(detail, "aplyUrlAddr", "https://apply.example.com");
        setField(detail, "refUrlAddr1", "www.reference-one.example.com");
        setField(detail, "plcyAplyMthdCn", "온라인 신청은 https://guide.example.com 에서 진행");
        setField(detail, "plcyExplnCn", "정책 안내");

        WelfareService service = mapper.fromYouth(detail);
        var aggregate = mapper.toYouthDetailAggregate(service, detail);
        String json = aggregate.detail().referenceUrlsJson();

        assertThat(aggregate.taxonomy().provisionMethod()).isEqualTo("직접대출");
        assertThat(aggregate.detail().applyMethodDetail()).contains("온라인 신청");
        assertThat(json).contains("https://apply.example.com");
        assertThat(json).contains("https://www.reference-one.example.com");
        assertThat(json).contains("https://guide.example.com");
        assertThat(json).contains("\"type\":\"APPLY\"");
        assertThat(json).contains("\"type\":\"REFERENCE\"");
        assertThat(json).contains("\"type\":\"EXTRACTED_FROM_TEXT\"");
    }

    @Test
    void fromBokjiroLocal_extractsAgeAndOnlineApplyFromText() throws Exception {
        BokjiroLocalDto.Item item = new BokjiroLocalDto.Item();
        setField(item, "servId", "L001");
        setField(item, "servNm", "청년 월세 지원");
        setField(item, "servDgst", "만 19세 이상 34세 이하 청년에게 월세를 지원합니다.");
        setField(item, "intrsThemaNmArray", "주거");
        setField(item, "aplyMtdNm", "온라인 신청 가능");

        WelfareService service = mapper.fromBokjiroLocal(item);

        assertThat(service.getMinAge()).isEqualTo(19);
        assertThat(service.getMaxAge()).isEqualTo(34);
        assertThat(service.getIsOnlineApply()).isTrue();
    }

    @Test
    void fromBokjiroLocal_detailUrlAloneDoesNotMarkOnlineApply() throws Exception {
        BokjiroLocalDto.Item item = new BokjiroLocalDto.Item();
        setField(item, "servId", "L001-A");
        setField(item, "servNm", "청년 센터 안내");
        setField(item, "servDgst", "오프라인 창구에서 신청");
        setField(item, "intrsThemaNmArray", "참여·기회");
        setField(item, "aplyMtdNm", "방문 신청");
        setField(item, "servDtlLink", "https://www.bokjiro.go.kr/detail");

        WelfareService service = mapper.fromBokjiroLocal(item);

        assertThat(service.getDetailUrl()).isEqualTo("https://www.bokjiro.go.kr/detail");
        assertThat(service.getIsOnlineApply()).isFalse();
    }

    @Test
    void fromBokjiroCentral_extractsAgeFromDescription() throws Exception {
        BokjiroCentralDto.Item item = new BokjiroCentralDto.Item();
        setField(item, "servId", "C001");
        setField(item, "servNm", "청년 취업 지원");
        setField(item, "servDgst", "만 18세 이상 39세 이하 청년 대상 지원");
        setField(item, "intrsThemaArray", "일자리");

        WelfareService service = mapper.fromBokjiroCentral(item);

        assertThat(service.getMinAge()).isEqualTo(18);
        assertThat(service.getMaxAge()).isEqualTo(39);
    }

    @Test
    void fromBokjiroLocal_setsSupportContentAndApplyEndDateFallback() throws Exception {
        BokjiroLocalDto.Item item = new BokjiroLocalDto.Item();
        setField(item, "servId", "L002");
        setField(item, "servNm", "청년 문화 지원");
        setField(item, "servDgst", "문화 활동비 지원. 신청기간 2026.01.01 ~ 2026.12.31");
        setField(item, "intrsThemaNmArray", "문화·여가");
        setField(item, "srvPvsnNm", "현금");

        WelfareService service = mapper.fromBokjiroLocal(item);

        assertThat(service.getSupportContent()).isEqualTo("문화 활동비 지원. 신청기간 2026.01.01 ~ 2026.12.31");
        assertThat(service.getApplyEndDate()).isEqualTo(LocalDate.of(2026, 12, 31));
    }

    @Test
    void fromBokjiroLocal_reclassifiesBroadOrMissingSignalsFromSummaryAndProvisionType() throws Exception {
        BokjiroLocalDto.Item housingItem = new BokjiroLocalDto.Item();
        setField(housingItem, "servId", "L003");
        setField(housingItem, "servNm", "주거급여수급자 월세보증금 지원");
        setField(housingItem, "servDgst", "청년 무주택 가구에 월세보증금 융자를 지원합니다.");
        setField(housingItem, "intrsThemaNmArray", "생활지원");
        setField(housingItem, "srvPvsnNm", "융자");

        WelfareService housingService = mapper.fromBokjiroLocal(housingItem);

        BokjiroLocalDto.Item financeItem = new BokjiroLocalDto.Item();
        setField(financeItem, "servId", "L004");
        setField(financeItem, "servNm", "주민소득지원 및 생활안정자금 지원");
        setField(financeItem, "servDgst", "저소득 가구 생활안정자금을 지원합니다.");
        setField(financeItem, "intrsThemaNmArray", "");
        setField(financeItem, "srvPvsnNm", "현금");

        WelfareService financeService = mapper.fromBokjiroLocal(financeItem);

        assertThat(housingService.getUnifiedCategory()).isEqualTo("주거");
        assertThat(financeService.getUnifiedCategory()).isEqualTo("금융·생활지원");
    }

    @Test
    void fromYouth_reclassifiesBroadWelfareCultureToCultureOrHealth() throws Exception {
        YouthApiDto.Item cultureItem = new YouthApiDto.Item();
        setField(cultureItem, "plcyNo", "YC001");
        setField(cultureItem, "plcyNm", "청년 문화예술인 활동 지원");
        setField(cultureItem, "plcyExplnCn", "문화예술 프로그램 참여 지원");
        setField(cultureItem, "lclsfNm", "복지문화");
        setField(cultureItem, "plcyKywdNm", "문화예술");

        WelfareService cultureService = mapper.fromYouth(cultureItem);

        YouthApiDto.Item healthItem = new YouthApiDto.Item();
        setField(healthItem, "plcyNo", "YH001");
        setField(healthItem, "plcyNm", "청년 정신건강 조기중재사업");
        setField(healthItem, "plcyExplnCn", "정신건강 심리상담 및 치료 연계");
        setField(healthItem, "lclsfNm", "금융·복지·문화");
        setField(healthItem, "plcyKywdNm", "상담");

        WelfareService healthService = mapper.fromYouth(healthItem);

        assertThat(cultureService.getUnifiedCategory()).isEqualTo("문화·여가");
        assertThat(healthService.getUnifiedCategory()).isEqualTo("건강·의료");
    }

    private void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
