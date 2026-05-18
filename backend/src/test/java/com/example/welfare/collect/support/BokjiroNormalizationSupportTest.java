package com.example.welfare.collect.support;

import com.example.welfare.collect.dto.BokjiroCentralDto;
import com.example.welfare.collect.dto.BokjiroLocalDto;
import com.example.welfare.collect.gateway.BokjiroDetailClient;
import com.example.welfare.collect.normalization.NormalizedPolicyAggregate;
import com.example.welfare.policy.entity.WelfareService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

class BokjiroNormalizationSupportTest {

    @Test
    @DisplayName("복지로 지자체 summary/provision text에서 derived 관심주제와 program keyword를 추출한다")
    void derivesLocalInterestThemesAndProgramKeywords() throws Exception {
        BokjiroLocalDto.Item item = new BokjiroLocalDto.Item();
        setField(item, "servNm", "주거급여수급자 월세보증금 지원");
        setField(item, "servDgst", "무주택 청년에게 월세보증금 융자와 생활안정자금을 지원합니다.");
        setField(item, "srvPvsnNm", "바우처");

        assertThat(BokjiroNormalizationSupport.derivedLocalInterestThemes(item))
                .containsExactly("주거", "생활지원");
        assertThat(BokjiroNormalizationSupport.derivedLocalProgramKeywords(item))
                .contains("주거지원", "월세보증금", "주거급여지원", "금융지원", "생활안정자금", "융자", "바우처");
    }

    @Test
    @DisplayName("복지로 상세 문구에서 기초생활수급자와 차상위계층 버킷을 공통 규칙으로 추출한다")
    void extractsBeneficiaryLabels() {
        assertThat(BokjiroNormalizationSupport.beneficiaryLabels(
                "국민기초생활보장수급자 우대",
                "차상위 본인부담경감대상자 포함"
        )).containsExactly("기초생활수급자", "차상위계층");
    }

    @Test
    @DisplayName("넓은 비공식 취약계층 표현은 beneficiary bucket으로 승격하지 않는다")
    void ignoresBroadLabels() {
        assertThat(BokjiroNormalizationSupport.beneficiaryLabels(
                "취업취약계층 및 정보 소외계층 지원",
                "저소득 한부모가족 우대"
        )).isEmpty();
    }

    @Test
    @DisplayName("복지로 중앙 list taxonomy term 조립을 support에서 공통 처리한다")
    void buildsCentralTerms() throws Exception {
        BokjiroCentralDto.Item item = new BokjiroCentralDto.Item();
        setField(item, "lifeArray", "청년");
        setField(item, "intrsThemaArray", "일자리");
        setField(item, "trgterIndvdlArray", "미취업청년,구직단념청년");

        assertThat(BokjiroNormalizationSupport.centralTerms(item))
                .extracting(NormalizedPolicyAggregate.TaxonomyTerm::termGroup,
                        NormalizedPolicyAggregate.TaxonomyTerm::termLabel,
                        NormalizedPolicyAggregate.TaxonomyTerm::sourceField)
                .containsExactly(
                        tuple("LIFE_STAGE", "청년", "lifeArray"),
                        tuple("INTEREST_THEME", "일자리", "intrsThemaArray"),
                        tuple("TARGET_GROUP", "미취업청년", "trgterIndvdlArray"),
                        tuple("TARGET_GROUP", "구직단념청년", "trgterIndvdlArray")
                );
    }

    @Test
    @DisplayName("복지로 상세 fact 조립을 support에서 공통 처리한다")
    void buildsDetailFacts() {
        BokjiroDetailClient.DetailPayload payload = BokjiroDetailClient.DetailPayload.builder()
                .targetDetail("만 20세 이상 34세 이하 미취업 청년")
                .selectionCriteria("소득 심사")
                .applyMethodDetail("온라인 신청, 2026.5.1 까지")
                .supportDetail("직무교육 제공")
                .build();

        assertThat(BokjiroNormalizationSupport.detailFacts(payload))
                .extracting(NormalizedPolicyAggregate.Fact::factGroup,
                        NormalizedPolicyAggregate.Fact::factCode,
                        NormalizedPolicyAggregate.Fact::sourceField)
                .contains(
                        tuple("AGE", "BOKJIRO_RULE_AGE", "targetDetail/selectionCriteria"),
                        tuple("APPLY_END_DATE", "BOKJIRO_RULE_APPLY_END_DATE", "applyMethodDetail/supportDetail")
                );
        assertThat(BokjiroNormalizationSupport.detailFacts(payload))
                .filteredOn(fact -> "AGE".equals(fact.factGroup()))
                .singleElement()
                .satisfies(fact -> {
                    assertThat(fact.rangeMinInt()).isEqualTo(20);
                    assertThat(fact.rangeMaxInt()).isEqualTo(34);
                });
        assertThat(BokjiroNormalizationSupport.detailFacts(payload))
                .filteredOn(fact -> "APPLY_END_DATE".equals(fact.factGroup()))
                .singleElement()
                .satisfies(fact -> assertThat(fact.dateValue()).isEqualTo(LocalDate.of(2026, 5, 1)));
    }

    @Test
    @DisplayName("복지로 상세 fact는 접수기간 종료일의 연도 생략 포맷도 신청 종료일로 복원한다")
    void buildsDetailApplyEndDateFactFromPartialRangeEnd() {
        BokjiroDetailClient.DetailPayload payload = BokjiroDetailClient.DetailPayload.builder()
                .applyMethodDetail("""
                        1단계 접수기간 : 2024.12.20.~30.
                        2단계 접수기간 : 2025.4.1.~4.10.
                        3단계 접수기간 : 2025.7.21.~7.30.
                        """)
                .build();

        assertThat(BokjiroNormalizationSupport.detailFacts(payload))
                .filteredOn(fact -> "APPLY_END_DATE".equals(fact.factGroup()))
                .singleElement()
                .satisfies(fact -> assertThat(fact.dateValue()).isEqualTo(LocalDate.of(2025, 7, 30)));
    }

    @Test
    @DisplayName("복지로 목록 기반 derived fact 조립을 support에서 공통 처리한다")
    void buildsDerivedFacts() {
        WelfareService service = WelfareService.builder()
                .minAge(18)
                .maxAge(39)
                .applyEndDate(LocalDate.of(2026, 12, 31))
                .build();

        assertThat(BokjiroNormalizationSupport.derivedFacts(service, "만 18세 이상 39세 이하 청년"))
                .extracting(NormalizedPolicyAggregate.Fact::factGroup,
                        NormalizedPolicyAggregate.Fact::factCode)
                .containsExactly(
                        tuple("AGE", "BOKJIRO_RULE_AGE"),
                        tuple("APPLY_END_DATE", "BOKJIRO_RULE_APPLY_END_DATE")
                );
    }

    private void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
