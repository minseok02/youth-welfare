package com.example.welfare.collect.mapper;

import com.example.welfare.collect.dto.BokjiroCentralDto;
import com.example.welfare.collect.dto.BokjiroLocalDto;
import com.example.welfare.collect.dto.YouthApiDto;
import com.example.welfare.collect.gateway.BokjiroDetailClient;
import com.example.welfare.collect.normalization.NormalizedPolicyAggregate;
import com.example.welfare.policy.entity.WelfareService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

class NormalizedPolicyAggregateTest {

    private final WelfareServiceMapper mapper = new WelfareServiceMapper();

    @Test
    void toNormalizedYouth_buildsCoreTaxonomyAndFacts() throws Exception {
        YouthApiDto.Item item = new YouthApiDto.Item();
        setField(item, "plcyNo", "Y001");
        setField(item, "plcyNm", "청년 월세 지원");
        setField(item, "plcyExplnCn", "청년 주거비 부담 완화");
        setField(item, "plcySprtCn", "월 최대 20만원 지원");
        setField(item, "lclsfNm", "주거");
        setField(item, "mclsfNm", "전월세 및 주거급여 지원");
        setField(item, "plcyKywdNm", "월세,주거,청년");
        setField(item, "sprtTrgtMinAge", 19);
        setField(item, "sprtTrgtMaxAge", 34);
        setField(item, "earnMinAmt", 0);
        setField(item, "earnMaxAmt", 100);
        setField(item, "aplyYmd", "20260101 ~ 20261231");
        setField(item, "plcyAplyMthdCn", "온라인 신청");
        setField(item, "aplyUrlAddr", "https://example.com/apply");

        NormalizedPolicyAggregate aggregate = mapper.toNormalizedYouth(item);

        assertThat(aggregate.core().sourceId()).isEqualTo("Y001");
        assertThat(aggregate.core().title()).isEqualTo("청년 월세 지원");
        assertThat(aggregate.detail().supportDetail()).isEqualTo("월 최대 20만원 지원");
        assertThat(aggregate.detail().applyMethodDetail()).isEqualTo("온라인 신청");
        assertThat(aggregate.taxonomy().compatUnifiedCategory()).isEqualTo("주거");
        assertThat(aggregate.taxonomy().summaryLabel("YOUTH_MAJOR")).isEqualTo("주거");
        assertThat(aggregate.taxonomy().summaryLabel("YOUTH_MID")).isEqualTo("전월세 및 주거급여 지원");
        assertThat(aggregate.taxonomyTerms())
                .extracting(NormalizedPolicyAggregate.TaxonomyTerm::termGroup,
                        NormalizedPolicyAggregate.TaxonomyTerm::termLabel)
                .contains(
                        tuple("YOUTH_MAJOR", "주거"),
                        tuple("YOUTH_MID", "전월세 및 주거급여 지원"),
                        tuple("YOUTH_KEYWORD", "월세")
                );
        assertThat(aggregate.facts())
                .filteredOn(fact -> "AGE".equals(fact.factGroup()))
                .singleElement()
                .satisfies(fact -> {
                    assertThat(fact.factMergeKey()).isEqualTo("YOUTH_AGE_ELIGIBILITY");
                    assertThat(fact.operator()).isEqualTo(NormalizedPolicyAggregate.Operator.RANGE);
                    assertThat(fact.rangeMinInt()).isEqualTo(19);
                    assertThat(fact.rangeMaxInt()).isEqualTo(34);
                    assertThat(fact.authority()).isEqualTo(NormalizedPolicyAggregate.Authority.OFFICIAL);
                });
        assertThat(aggregate.facts())
                .filteredOn(fact -> "APPLY_END_DATE".equals(fact.factGroup()))
                .singleElement()
                .satisfies(fact -> assertThat(fact.dateValue()).isEqualTo(LocalDate.of(2026, 12, 31)));
    }

    @Test
    void toNormalizedYouth_splitsOfficialMidAndPreservesRawAliasBucket() throws Exception {
        YouthApiDto.Item item = new YouthApiDto.Item();
        setField(item, "plcyNo", "Y002");
        setField(item, "plcyNm", "청년 역량 지원");
        setField(item, "plcyExplnCn", "청년 취업 역량 강화");
        setField(item, "plcySprtCn", "교육 및 취업 지원");
        setField(item, "lclsfNm", "일자리");
        setField(item, "mclsfNm", "취업,재직자,온·오프라인교육");

        NormalizedPolicyAggregate aggregate = mapper.toNormalizedYouth(item);

        assertThat(aggregate.taxonomy().summaryLabel("YOUTH_MID")).isNull();
        assertThat(aggregate.taxonomyTerms())
                .extracting(NormalizedPolicyAggregate.TaxonomyTerm::termGroup,
                        NormalizedPolicyAggregate.TaxonomyTerm::termLabel)
                .contains(
                        tuple("YOUTH_MID", "취업"),
                        tuple("YOUTH_MID", "재직자"),
                        tuple("YOUTH_MID_RAW_ALIAS", "온·오프라인교육")
                );
    }

    @Test
    void toNormalizedBokjiroCentral_usesDetailPayloadAndDerivedFacts() throws Exception {
        BokjiroCentralDto.Item item = new BokjiroCentralDto.Item();
        setField(item, "servId", "C001");
        setField(item, "servNm", "청년 취업 지원");
        setField(item, "servDgst", "만 18세 이상 39세 이하 청년 대상 취업 지원");
        setField(item, "lifeArray", "청년");
        setField(item, "intrsThemaArray", "일자리");
        setField(item, "trgterIndvdlArray", "미취업청년");
        setField(item, "sprtCycNm", "연 1회");
        setField(item, "srvPvsnNm", "프로그램");
        setField(item, "onapPsbltYn", "Y");
        setField(item, "servDtlLink", "https://bokjiro.go.kr/service/C001");

        BokjiroDetailClient.DetailPayload detailPayload = BokjiroDetailClient.DetailPayload.builder()
                .targetDetail("만 18세 이상 39세 이하 미취업 청년")
                .supportDetail("상담, 교육, 취업연계")
                .applyMethodDetail("복지로 온라인 신청")
                .selectionCriteria("연령 및 취업 상태 심사")
                .contactList("고객센터 02-0000-0000")
                .supportCycle("연 1회")
                .provisionType("프로그램")
                .build();

        NormalizedPolicyAggregate aggregate = mapper.toNormalizedBokjiroCentral(item, detailPayload);

        assertThat(aggregate.taxonomy().compatUnifiedCategory()).isEqualTo("일자리");
        assertThat(aggregate.taxonomy().authority()).isEqualTo(NormalizedPolicyAggregate.Authority.SYSTEM_DERIVED);
        assertThat(aggregate.detail().targetDetail()).isEqualTo("만 18세 이상 39세 이하 미취업 청년");
        assertThat(aggregate.detail().supportDetail()).isEqualTo("상담, 교육, 취업연계");
        assertThat(aggregate.taxonomyTerms())
                .extracting(NormalizedPolicyAggregate.TaxonomyTerm::termGroup,
                        NormalizedPolicyAggregate.TaxonomyTerm::termLabel)
                .contains(
                        tuple("LIFE_STAGE", "청년"),
                        tuple("INTEREST_THEME", "일자리"),
                        tuple("TARGET_GROUP", "미취업청년")
                );
        assertThat(aggregate.facts())
                .filteredOn(fact -> "AGE".equals(fact.factGroup()))
                .singleElement()
                .satisfies(fact -> {
                    assertThat(fact.factCode()).isEqualTo("BOKJIRO_RULE_AGE");
                    assertThat(fact.factMergeKey()).isEqualTo("BK_AGE_ELIGIBILITY");
                    assertThat(fact.authority()).isEqualTo(NormalizedPolicyAggregate.Authority.RULE_DERIVED);
                    assertThat(fact.rangeMinInt()).isEqualTo(18);
                    assertThat(fact.rangeMaxInt()).isEqualTo(39);
                    assertThat(fact.evidenceText()).contains("39세");
                });
    }

    @Test
    void toNormalizedBokjiroLocal_fallsBackToServiceFieldsWhenDetailIsMissing() throws Exception {
        BokjiroLocalDto.Item item = new BokjiroLocalDto.Item();
        setField(item, "servId", "L001");
        setField(item, "servNm", "청년 문화패스");
        setField(item, "servDgst", "만 19세 이상 34세 이하 청년에게 문화 활동비를 지원합니다.");
        setField(item, "intrsThemaNmArray", "문화·여가");
        setField(item, "lifeNmArray", "청년");
        setField(item, "trgterIndvdlNmArray", "청년,1인가구");
        setField(item, "srvPvsnNm", "바우처");
        setField(item, "sprtCycNm", "분기별");
        setField(item, "aplyMtdNm", "온라인 신청 가능");
        setField(item, "servDtlLink", "https://bokjiro.go.kr/service/L001");

        NormalizedPolicyAggregate aggregate = mapper.toNormalizedBokjiroLocal(item, null);

        assertThat(aggregate.core().title()).isEqualTo("청년 문화패스");
        assertThat(aggregate.detail().supportDetail()).contains("문화 활동비");
        assertThat(aggregate.detail().applyMethodDetail()).isEqualTo("온라인 신청 가능");
        assertThat(aggregate.detail().onlineApplyUrl()).isEqualTo("https://bokjiro.go.kr/service/L001");
        assertThat(aggregate.taxonomyTerms())
                .extracting(NormalizedPolicyAggregate.TaxonomyTerm::termGroup,
                        NormalizedPolicyAggregate.TaxonomyTerm::termLabel)
                .contains(
                        tuple("INTEREST_THEME", "문화·여가"),
                        tuple("TARGET_GROUP", "1인가구")
                );
        assertThat(aggregate.facts())
                .filteredOn(fact -> "AGE".equals(fact.factGroup()))
                .singleElement()
                .satisfies(fact -> {
                    assertThat(fact.factCode()).isEqualTo("BOKJIRO_RULE_AGE");
                    assertThat(fact.factMergeKey()).isEqualTo("BK_AGE_ELIGIBILITY");
                    assertThat(fact.rangeMinInt()).isEqualTo(19);
                    assertThat(fact.rangeMaxInt()).isEqualTo(34);
                });
    }

    @Test
    void toNormalizedBokjiroDetail_enrichesDetailAndFactsFromPayload() {
        WelfareService service = WelfareService.builder()
                .sourceType(WelfareService.SourceType.BOKJIRO_CENTRAL)
                .sourceId("C002")
                .title("청년 취업 지원")
                .description("기존 목록 요약")
                .unifiedCategory("일자리")
                .detailUrl("https://bokjiro.go.kr/service/C002")
                .status(WelfareService.ServiceStatus.ACTIVE)
                .build();
        BokjiroDetailClient.DetailPayload payload = BokjiroDetailClient.DetailPayload.builder()
                .targetDetail("만 20세 이상 34세 이하 미취업 청년, 국민기초생활보장수급자 우대")
                .supportDetail("직무교육 제공")
                .applyMethodDetail("온라인 신청, 2026.12.31 까지")
                .selectionCriteria("연령 요건 확인, 차상위 본인부담경감대상자 포함")
                .contactList("고객센터")
                .supportCycle("연 1회")
                .provisionType("프로그램")
                .build();

        NormalizedPolicyAggregate aggregate = mapper.toNormalizedBokjiroDetail(service, payload);

        assertThat(aggregate.detail().targetDetail()).contains("20세");
        assertThat(aggregate.detail().applyMethodDetail()).contains("2026.12.31");
        assertThat(aggregate.taxonomyTerms())
                .extracting(NormalizedPolicyAggregate.TaxonomyTerm::termGroup,
                        NormalizedPolicyAggregate.TaxonomyTerm::termLabel,
                        NormalizedPolicyAggregate.TaxonomyTerm::sourceField,
                        NormalizedPolicyAggregate.TaxonomyTerm::authority)
                .containsExactly(
                        tuple("TARGET_GROUP", "기초생활수급자", "targetDetail/selectionCriteria",
                                NormalizedPolicyAggregate.Authority.SYSTEM_DERIVED),
                        tuple("TARGET_GROUP", "차상위계층", "targetDetail/selectionCriteria",
                                NormalizedPolicyAggregate.Authority.SYSTEM_DERIVED)
                );
        assertThat(aggregate.facts())
                .filteredOn(fact -> "AGE".equals(fact.factGroup()))
                .singleElement()
                .satisfies(fact -> {
                    assertThat(fact.factCode()).isEqualTo("BOKJIRO_RULE_AGE");
                    assertThat(fact.factMergeKey()).isEqualTo("BK_AGE_ELIGIBILITY");
                    assertThat(fact.rangeMinInt()).isEqualTo(20);
                    assertThat(fact.rangeMaxInt()).isEqualTo(34);
                    assertThat(fact.sourceField()).isEqualTo("targetDetail/selectionCriteria");
                });
        assertThat(aggregate.facts())
                .filteredOn(fact -> "APPLY_END_DATE".equals(fact.factGroup()))
                .singleElement()
                .satisfies(fact -> {
                    assertThat(fact.factCode()).isEqualTo("BOKJIRO_RULE_APPLY_END_DATE");
                    assertThat(fact.factMergeKey()).isEqualTo("BK_APPLY_END_DATE");
                    assertThat(fact.dateValue()).isEqualTo(LocalDate.of(2026, 12, 31));
                });
    }

    @Test
    void toNormalizedBokjiroDetail_ignoresBroadNonWhitelistBeneficiaryLabels() {
        WelfareService service = WelfareService.builder()
                .sourceType(WelfareService.SourceType.BOKJIRO_LOCAL)
                .sourceId("L002")
                .title("복지로 지원")
                .unifiedCategory("금융·생활지원")
                .status(WelfareService.ServiceStatus.ACTIVE)
                .build();
        BokjiroDetailClient.DetailPayload payload = BokjiroDetailClient.DetailPayload.builder()
                .targetDetail("취업취약계층 및 정보 소외계층 지원")
                .selectionCriteria("저소득 한부모가족 우대")
                .build();

        NormalizedPolicyAggregate aggregate = mapper.toNormalizedBokjiroDetail(service, payload);

        assertThat(aggregate.taxonomyTerms()).isEmpty();
    }

    private void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
