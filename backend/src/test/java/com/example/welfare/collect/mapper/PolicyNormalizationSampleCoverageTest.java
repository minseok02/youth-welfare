package com.example.welfare.collect.mapper;

import com.example.welfare.collect.dto.BokjiroCentralDto;
import com.example.welfare.collect.dto.BokjiroLocalDto;
import com.example.welfare.collect.dto.YouthApiDto;
import com.example.welfare.collect.gateway.BokjiroDetailClient;
import com.example.welfare.policy.entity.WelfareService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 새 canonical 구조(core/detail/taxonomy/facts)가 실제 source sample을 담을 수 있는지 확인하는 스파이크 테스트.
 * production canonical 모델은 아직 없으므로, 현재 DTO/mapper와 공식 Swagger 기반 representative sample로 coverage를 검증한다.
 */
class PolicyNormalizationSampleCoverageTest {

    private final WelfareServiceMapper mapper = new WelfareServiceMapper();

    @Test
    @DisplayName("온통청년 sample은 core + youth taxonomy + structured age/income facts를 직접 제공한다")
    void youthSampleProvidesCoreTaxonomyAndStructuredFacts() throws Exception {
        YouthApiDto.Item item = new YouthApiDto.Item();
        setField(item, "plcyNo", "Y-2026-001");
        setField(item, "plcyNm", "청년 월세 특별지원");
        setField(item, "plcyExplnCn", "청년의 주거비 부담을 줄이기 위한 정책");
        setField(item, "plcySprtCn", "월 최대 20만원 월세 지원");
        setField(item, "lclsfNm", "주거");
        setField(item, "mclsfNm", "전월세 및 주거급여 지원");
        setField(item, "plcyKywdNm", "월세,보조금,청년주거");
        setField(item, "sprvsnInstCdNm", "국토교통부");
        setField(item, "operInstCdNm", "서울특별시");
        setField(item, "sprtTrgtMinAge", 19);
        setField(item, "sprtTrgtMaxAge", 34);
        setField(item, "earnMinAmt", 0);
        setField(item, "earnMaxAmt", 100);
        setField(item, "bizPrdBgngYmd", "20260101");
        setField(item, "bizPrdEndYmd", "20261231");
        setField(item, "aplyYmd", "20260101 ~ 20261231");
        setField(item, "plcyAplyMthdCn", "온라인 신청");
        setField(item, "aplyUrlAddr", "https://youthcenter.go.kr/policy/Y-2026-001");

        WelfareService service = mapper.fromYouth(item);

        assertThat(service.getTitle()).isEqualTo("청년 월세 특별지원");
        assertThat(service.getUnifiedCategory()).isEqualTo("주거");
        assertThat(service.getCategoryMain()).isEqualTo("주거");
        assertThat(service.getCategorySub()).isEqualTo("전월세 및 주거급여 지원");
        assertThat(service.getKeyword()).contains("월세");
        assertThat(service.getMinAge()).isEqualTo(19);
        assertThat(service.getMaxAge()).isEqualTo(34);
        assertThat(service.getMinIncome()).isEqualTo(0);
        assertThat(service.getMaxIncome()).isEqualTo(100);
        assertThat(service.getApplyStartDate()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(service.getApplyEndDate()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(service.getDetailUrl()).contains("youthcenter.go.kr");
        assertThat(service.getIsOnlineApply()).isTrue();
    }

    @Test
    @DisplayName("복지로 중앙 list + detail sample은 core/detail은 강하지만 structured income facts는 비어 있다")
    void bokjiroCentralSampleProvidesCoreAndDetailButWeakStructuredFacts() throws Exception {
        BokjiroCentralDto.Item item = new BokjiroCentralDto.Item();
        setField(item, "servId", "B-C-1001");
        setField(item, "servNm", "청년 취업 역량강화 지원");
        setField(item, "servDgst", "만 18세 이상 39세 이하 미취업 청년 대상 취업 지원");
        setField(item, "jurMnofNm", "고용노동부");
        setField(item, "jurOrgNm", "한국고용정보원");
        setField(item, "lifeArray", "청년");
        setField(item, "intrsThemaArray", "일자리");
        setField(item, "trgterIndvdlArray", "미취업청년,대학생");
        setField(item, "sprtCycNm", "연 1회");
        setField(item, "srvPvsnNm", "프로그램");
        setField(item, "onapPsbltYn", "Y");
        setField(item, "servDtlLink", "https://bokjiro.go.kr/service/B-C-1001");

        WelfareService service = mapper.fromBokjiroCentral(item);
        BokjiroDetailClient.DetailPayload detail = BokjiroDetailClient.DetailPayload.builder()
                .targetDetail("만 18세 이상 39세 이하 미취업 청년")
                .supportDetail("직무교육, 상담, 면접코칭 지원")
                .applyMethodDetail("온라인 신청 후 센터 방문")
                .selectionCriteria("미취업 여부 확인")
                .contactList("고객센터 02-1234-5678")
                .supportCycle("연 1회")
                .provisionType("프로그램")
                .build();

        assertThat(service.getTitle()).isEqualTo("청년 취업 역량강화 지원");
        assertThat(service.getUnifiedCategory()).isEqualTo("일자리");
        assertThat(service.getMinAge()).isEqualTo(18);
        assertThat(service.getMaxAge()).isEqualTo(39);
        assertThat(service.getMinIncome()).isNull();
        assertThat(service.getMaxIncome()).isNull();
        assertThat(service.getLifeStage()).contains("청년");
        assertThat(service.getProvisionType()).isEqualTo("프로그램");
        assertThat(detail.getTargetDetail()).contains("미취업 청년");
        assertThat(detail.getSupportDetail()).contains("직무교육");
        assertThat(detail.getSelectionCriteria()).contains("미취업");
    }

    @Test
    @DisplayName("복지로 지자체 list + detail sample은 지역과 detail은 강하지만 structured facts는 본문 fallback 의존이다")
    void bokjiroLocalSampleProvidesRegionAndDetailButNeedsFactFallback() throws Exception {
        BokjiroLocalDto.Item item = new BokjiroLocalDto.Item();
        setField(item, "servId", "B-L-2001");
        setField(item, "servNm", "청년 문화패스");
        setField(item, "servDgst", "만 19세 이상 34세 이하 청년에게 문화 활동비를 지원합니다.");
        setField(item, "bizChrDeptNm", "서울특별시 청년정책과");
        setField(item, "lifeNmArray", "청년");
        setField(item, "intrsThemaNmArray", "문화·여가");
        setField(item, "trgterIndvdlNmArray", "청년,1인가구");
        setField(item, "sprtCycNm", "분기별");
        setField(item, "srvPvsnNm", "바우처");
        setField(item, "aplyMtdNm", "온라인 신청 가능");
        setField(item, "servDtlLink", "https://bokjiro.go.kr/service/B-L-2001");
        setField(item, "enfcBgngYmd", "20260101");
        setField(item, "enfcEndYmd", "20261231");
        setField(item, "ctpvNm", "서울특별시");
        setField(item, "sggNm", "마포구");

        WelfareService service = mapper.fromBokjiroLocal(item);
        BokjiroDetailClient.DetailPayload detail = BokjiroDetailClient.DetailPayload.builder()
                .targetDetail("서울 거주 청년 1인가구")
                .supportDetail("문화 활동비 바우처 연 20만원")
                .applyMethodDetail("복지로 또는 시 누리집 온라인 접수")
                .selectionCriteria("거주지 및 연령 요건 심사")
                .contactList("서울시 다산콜센터")
                .supportCycle("분기별")
                .provisionType("바우처")
                .build();

        assertThat(service.getUnifiedCategory()).isEqualTo("문화·여가");
        assertThat(service.getMinAge()).isEqualTo(19);
        assertThat(service.getMaxAge()).isEqualTo(34);
        assertThat(service.getMinIncome()).isNull();
        assertThat(service.getMaxIncome()).isNull();
        assertThat(service.getOperatingOrg()).contains("서울특별시");
        assertThat(service.getApplyMethodName()).contains("온라인");
        assertThat(service.getStartDate()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(service.getEndDate()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(mapper.regionsFromBokjiroLocal(item, service))
                .singleElement()
                .satisfies(region -> {
                    assertThat(region.getSidoName()).isEqualTo("서울특별시");
                    assertThat(region.getSggName()).isEqualTo("마포구");
                });
        assertThat(detail.getSupportDetail()).contains("바우처");
        assertThat(detail.getTargetDetail()).contains("1인가구");
    }

    @Test
    @DisplayName("Gov24 representative sample은 core/detail/hard-filter facts는 강하지만 youth taxonomy bridge가 필요하다")
    void gov24RepresentativeSampleProvidesHardFilterFactsButNeedsYouthBridge() {
        Gov24CompositeSample sample = new Gov24CompositeSample(
                "G-3001",
                "청년 월세 한시 특별지원",
                "청년의 주거비 부담 완화",
                "무주택 청년",
                "연령 및 소득 기준 충족",
                "월 최대 20만원 지원",
                "온라인 신청",
                "예산 소진 시까지",
                "https://gov.kr/service/G-3001",
                "국토교통부",
                "주거",
                "개인",
                "현금",
                "주민등록등본, 임대차계약서",
                "콜센터 1599-0000",
                "https://gov.kr/apply/G-3001",
                19,
                34,
                List.of("JA0203", "JA0320", "JA0327", "JA0412")
        );

        Gov24Projection projection = project(sample);

        assertThat(projection.title()).isEqualTo("청년 월세 한시 특별지원");
        assertThat(projection.serviceField()).isEqualTo("주거");
        assertThat(projection.userType()).isEqualTo("개인");
        assertThat(projection.benefitType()).isEqualTo("현금");
        assertThat(projection.minAge()).isEqualTo(19);
        assertThat(projection.maxAge()).isEqualTo(34);
        assertThat(projection.factCodes()).contains("JA0203", "JA0320", "JA0327", "JA0412");
        assertThat(projection.requiredDocuments()).contains("임대차계약서");
        assertThat(projection.onlineApplyUrl()).contains("gov.kr/apply");
        assertThat(projection.needsYouthTaxonomyBridge()).isTrue();
    }

    private Gov24Projection project(Gov24CompositeSample sample) {
        return new Gov24Projection(
                sample.title(),
                sample.serviceField(),
                sample.userType(),
                sample.benefitType(),
                sample.ageStart(),
                sample.ageEnd(),
                sample.supportConditionCodes(),
                sample.requiredDocuments(),
                sample.onlineApplyUrl(),
                true
        );
    }

    private void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private record Gov24CompositeSample(
            String serviceId,
            String title,
            String summary,
            String target,
            String criteria,
            String support,
            String applyMethod,
            String deadline,
            String detailUrl,
            String agencyName,
            String serviceField,
            String userType,
            String benefitType,
            String requiredDocuments,
            String contact,
            String onlineApplyUrl,
            Integer ageStart,
            Integer ageEnd,
            List<String> supportConditionCodes
    ) {
    }

    private record Gov24Projection(
            String title,
            String serviceField,
            String userType,
            String benefitType,
            Integer minAge,
            Integer maxAge,
            List<String> factCodes,
            String requiredDocuments,
            String onlineApplyUrl,
            boolean needsYouthTaxonomyBridge
    ) {
    }
}
