package com.example.welfare.integration;

import com.example.welfare.policy.entity.ServiceRegion;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.ServiceRegionRepository;
import com.example.welfare.policy.repository.WelfareServiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("integration")
class RecommendationRegionQueryIntegrationTest {

    private static final String TEST_SOURCE_PREFIX = "IT-REGION-";
    private static final String PRIORITY_TEST_REGION_CODE = "99999";
    private static final String PRIORITY_TEST_SIDO = "테스트광역시";

    @Autowired
    private WelfareServiceRepository welfareServiceRepository;

    @Autowired
    private ServiceRegionRepository serviceRegionRepository;

    @BeforeEach
    void setup() {
        cleanup();
    }

    @AfterEach
    void cleanup() {
        List<WelfareService> testServices = welfareServiceRepository.findAll().stream()
                .filter(service -> service.getSourceId() != null && service.getSourceId().startsWith(TEST_SOURCE_PREFIX))
                .toList();

        if (testServices.isEmpty()) {
            return;
        }

        serviceRegionRepository.findAll().stream()
                .filter(region -> region.getService() != null && testServices.contains(region.getService()))
                .forEach(serviceRegionRepository::delete);
        testServices.forEach(welfareServiceRepository::delete);
    }

    @Test
    @DisplayName("0/0 소득 조건은 source와 무관하게 미지정 sentinel로 통과시킨다")
    void findCandidatesTreatZeroZeroIncomeAsSourceNeutralPassThrough() {
        WelfareService passThrough = welfareServiceRepository.save(WelfareService.builder()
                .sourceType(WelfareService.SourceType.BOKJIRO_CENTRAL)
                .sourceId(TEST_SOURCE_PREFIX + UUID.randomUUID().toString().substring(0, 8))
                .title("0/0 소득 조건 테스트")
                .description("소득 미지정 sentinel")
                .status(WelfareService.ServiceStatus.ACTIVE)
                .minAge(19)
                .maxAge(34)
                .minIncome(0)
                .maxIncome(0)
                .apiViewCount(0L)
                .build());
        WelfareService strictIncome = welfareServiceRepository.save(WelfareService.builder()
                .sourceType(WelfareService.SourceType.BOKJIRO_LOCAL)
                .sourceId(TEST_SOURCE_PREFIX + UUID.randomUUID().toString().substring(0, 8))
                .title("소득 제한 정책")
                .description("소득분위 상한 3")
                .status(WelfareService.ServiceStatus.ACTIVE)
                .minAge(19)
                .maxAge(34)
                .minIncome(1)
                .maxIncome(3)
                .apiViewCount(0L)
                .build());

        List<WelfareService> results = welfareServiceRepository.findCandidates(
                25,
                5,
                PageRequest.of(0, 5000, Sort.by(Sort.Direction.DESC, "id"))
        );

        assertThat(results)
                .extracting(WelfareService::getId)
                .contains(passThrough.getId())
                .doesNotContain(strictIncome.getId());
    }

    @Test
    @DisplayName("지역코드 추천 후보는 전국 정책과 매칭 지역 정책만 포함하고 중복 반환하지 않는다")
    void findCandidatesWithRegionCodeIncludesNationwideAndMatchingLocalWithoutDuplicates() {
        WelfareService nationwide = saveService("nationwide");
        WelfareService seoul = saveService("seoul");
        WelfareService busan = saveService("busan");

        serviceRegionRepository.saveAll(List.of(
                ServiceRegion.builder()
                        .service(seoul)
                        .regionCode("11680")
                        .sidoName("서울특별시")
                        .sggName("강남구")
                        .build(),
                ServiceRegion.builder()
                        .service(seoul)
                        .regionCode("11740")
                        .sidoName("서울특별시")
                        .sggName("강동구")
                        .build(),
                ServiceRegion.builder()
                        .service(busan)
                        .regionCode("26350")
                        .sidoName("부산광역시")
                        .sggName("해운대구")
                        .build()
        ));

        List<WelfareService> results = welfareServiceRepository.findCandidatesWithRegionCode(
                25,
                5,
                "11680",
                "서울특별시",
                null,
                PageRequest.of(0, 5000)
        );

        assertThat(results)
                .extracting(WelfareService::getId)
                .contains(nationwide.getId(), seoul.getId())
                .doesNotContain(busan.getId());
        assertThat(results.stream()
                .filter(service -> service.getId().equals(seoul.getId()))
                .count()).isEqualTo(1L);
    }

    @Test
    @DisplayName("지역코드 추천 후보는 매칭된 BOKJIRO_LOCAL 정책을 전국 정책보다 먼저 노출한다")
    void findCandidatesWithRegionCodePrioritizesMatchingLocalPolicies() {
        WelfareService matchingLocal = saveService("region-local-first", WelfareService.SourceType.BOKJIRO_LOCAL);
        WelfareService nationwide = saveService("region-nationwide", WelfareService.SourceType.YOUTH);

        serviceRegionRepository.save(ServiceRegion.builder()
                .service(matchingLocal)
                .regionCode(PRIORITY_TEST_REGION_CODE)
                .sidoName(PRIORITY_TEST_SIDO)
                .sggName("테스트중구")
                .build());

        List<WelfareService> results = welfareServiceRepository.findCandidatesWithRegionCode(
                25,
                5,
                PRIORITY_TEST_REGION_CODE,
                PRIORITY_TEST_SIDO,
                null,
                PageRequest.of(0, 10)
        );

        assertThat(results).extracting(WelfareService::getId)
                .contains(matchingLocal.getId(), nationwide.getId());
        assertThat(indexOf(results, matchingLocal))
                .isLessThan(indexOf(results, nationwide));
    }

    @Test
    @DisplayName("지역코드 추천 후보는 같은 매칭 BOKJIRO_LOCAL 안에서도 youth/non-기타 신호를 먼저 노출한다")
    void findCandidatesWithRegionCodePrioritizesYouthRelevantNonOtherMatchingLocalPolicies() {
        WelfareService strongLocal = saveService(
                "region-local-strong",
                WelfareService.SourceType.BOKJIRO_LOCAL,
                true,
                "주거"
        );
        WelfareService weakLocal = saveService(
                "region-local-weak",
                WelfareService.SourceType.BOKJIRO_LOCAL,
                false,
                "기타"
        );

        serviceRegionRepository.saveAll(List.of(
                ServiceRegion.builder()
                        .service(weakLocal)
                        .regionCode(PRIORITY_TEST_REGION_CODE)
                        .sidoName(PRIORITY_TEST_SIDO)
                        .sggName("테스트중구")
                        .build(),
                ServiceRegion.builder()
                        .service(strongLocal)
                        .regionCode(PRIORITY_TEST_REGION_CODE)
                        .sidoName(PRIORITY_TEST_SIDO)
                        .sggName("테스트중구")
                        .build()
        ));

        List<WelfareService> results = welfareServiceRepository.findCandidatesWithRegionCode(
                25,
                5,
                PRIORITY_TEST_REGION_CODE,
                PRIORITY_TEST_SIDO,
                null,
                PageRequest.of(0, 10)
        );

        assertThat(indexOf(results, strongLocal))
                .isLessThan(indexOf(results, weakLocal));
    }

    @Test
    @DisplayName("지역코드 추천 후보는 region_code가 비어도 same-sido BOKJIRO_LOCAL youth/non-기타 후보를 포함한다")
    void findCandidatesWithRegionCodeIncludesSameSidoLocalYouthBridgeWithoutExactRegionCode() {
        WelfareService sameSidoLocal = saveService(
                "region-local-sido-bridge",
                WelfareService.SourceType.BOKJIRO_LOCAL,
                true,
                "주거"
        );
        WelfareService sameSidoOther = saveService(
                "region-local-sido-other",
                WelfareService.SourceType.BOKJIRO_LOCAL,
                true,
                "기타"
        );

        serviceRegionRepository.saveAll(List.of(
                ServiceRegion.builder()
                        .service(sameSidoLocal)
                        .regionCode(null)
                        .sidoName(PRIORITY_TEST_SIDO)
                        .sggName("다른시군구")
                        .build(),
                ServiceRegion.builder()
                        .service(sameSidoOther)
                        .regionCode(null)
                        .sidoName(PRIORITY_TEST_SIDO)
                        .sggName("다른시군구")
                        .build()
        ));

        List<WelfareService> results = welfareServiceRepository.findCandidatesWithRegionCode(
                25,
                5,
                PRIORITY_TEST_REGION_CODE,
                PRIORITY_TEST_SIDO,
                null,
                PageRequest.of(0, 20)
        );

        assertThat(results).extracting(WelfareService::getId)
                .contains(sameSidoLocal.getId())
                .doesNotContain(sameSidoOther.getId());
    }

    @Test
    @DisplayName("지역코드 추천 후보는 사용자 sgg가 있으면 다른 시군구 same-sido fallback을 포함하지 않는다")
    void findCandidatesWithRegionCodeExcludesDifferentSggSameSidoFallbackWhenUserSggPresent() {
        WelfareService sameSidoDifferentSgg = saveService(
                "region-local-sido-different-sgg",
                WelfareService.SourceType.BOKJIRO_LOCAL,
                true,
                "주거"
        );
        WelfareService sameSggBridge = saveService(
                "region-local-sido-same-sgg",
                WelfareService.SourceType.BOKJIRO_LOCAL,
                true,
                "주거"
        );

        serviceRegionRepository.saveAll(List.of(
                ServiceRegion.builder()
                        .service(sameSidoDifferentSgg)
                        .regionCode(null)
                        .sidoName("경기도")
                        .sggName("고양시")
                        .build(),
                ServiceRegion.builder()
                        .service(sameSggBridge)
                        .regionCode(null)
                        .sidoName("경기도")
                        .sggName("수원시")
                        .build()
        ));

        List<WelfareService> results = welfareServiceRepository.findCandidatesWithRegionCode(
                25,
                5,
                "41110",
                "경기도",
                "수원시",
                PageRequest.of(0, 5000)
        );

        assertThat(results).extracting(WelfareService::getId)
                .contains(sameSggBridge.getId())
                .doesNotContain(sameSidoDifferentSgg.getId());
    }

    @Test
    @DisplayName("지역코드 추천 후보는 service_regions가 없는 지자체 정책을 전국 정책처럼 포함하지 않는다")
    void findCandidatesWithRegionCodeExcludesRegionlessLocalPolicies() {
        WelfareService regionlessLocal = saveService(
                "regionless-local-should-be-excluded",
                WelfareService.SourceType.BOKJIRO_LOCAL,
                true,
                "주거"
        );
        List<WelfareService> results = welfareServiceRepository.findCandidatesWithRegionCode(
                25,
                5,
                "41110",
                "경기도",
                "수원시",
                PageRequest.of(0, 20)
        );

        assertThat(results).extracting(WelfareService::getId)
                .doesNotContain(regionlessLocal.getId());
    }

    @Test
    @DisplayName("지역코드 추천 후보는 bounded same-sido BOKJIRO_LOCAL fallback을 generic exact-region 후보보다 먼저 노출한다")
    void findCandidatesWithRegionCodePrioritizesBoundedSameSidoLocalFallbackOverGenericExactRegion() {
        WelfareService sameSidoLocal = saveService(
                "region-local-sido-priority",
                WelfareService.SourceType.BOKJIRO_LOCAL,
                true,
                "주거"
        );
        WelfareService exactRegionYouth = saveService(
                "region-exact-generic",
                WelfareService.SourceType.YOUTH,
                true,
                "일자리"
        );

        serviceRegionRepository.saveAll(List.of(
                ServiceRegion.builder()
                        .service(sameSidoLocal)
                        .regionCode(null)
                        .sidoName(PRIORITY_TEST_SIDO)
                        .sggName("다른시군구")
                        .build(),
                ServiceRegion.builder()
                        .service(exactRegionYouth)
                        .regionCode(PRIORITY_TEST_REGION_CODE)
                        .sidoName(PRIORITY_TEST_SIDO)
                        .sggName("테스트중구")
                        .build()
        ));

        List<WelfareService> results = welfareServiceRepository.findCandidatesWithRegionCode(
                25,
                5,
                PRIORITY_TEST_REGION_CODE,
                PRIORITY_TEST_SIDO,
                null,
                PageRequest.of(0, 20)
        );

        assertThat(results).extracting(WelfareService::getId)
                .contains(sameSidoLocal.getId(), exactRegionYouth.getId());
        assertThat(indexOf(results, sameSidoLocal))
                .isLessThan(indexOf(results, exactRegionYouth));
    }

    @Test
    @DisplayName("지역코드 추천 후보는 service_regions가 없는 Gov24도 시도 텍스트가 맞으면 지역 후보로 먼저 노출한다")
    void findCandidatesWithRegionCodePrioritizesGov24SidoTextMatchWithoutServiceRegion() {
        WelfareService gov24TextMatch = saveServiceWithTitle(
                "gov24-region-text-match",
                WelfareService.SourceType.GOV24,
                "인천 청년 자격증 응시료 지원",
                "인천광역시 거주 청년 지원",
                true,
                "일자리"
        );
        WelfareService exactRegionYouth = saveService(
                "region-exact-after-gov24-text",
                WelfareService.SourceType.YOUTH,
                true,
                "일자리"
        );

        serviceRegionRepository.save(ServiceRegion.builder()
                .service(exactRegionYouth)
                .regionCode("28110")
                .sidoName("인천광역시")
                .sggName("중구")
                .build());

        List<WelfareService> results = welfareServiceRepository.findCandidatesWithRegionCode(
                25,
                5,
                "28110",
                "인천광역시",
                null,
                PageRequest.of(0, 5000)
        );

        assertThat(results).extracting(WelfareService::getId)
                .contains(gov24TextMatch.getId(), exactRegionYouth.getId());
        assertThat(indexOf(results, gov24TextMatch))
                .isLessThan(indexOf(results, exactRegionYouth));
    }

    @Test
    @DisplayName("지역코드 최신 추천 후보도 전국 정책과 매칭 지역 정책만 포함하고 중복 반환하지 않는다")
    void findLatestCandidatesWithRegionCodeIncludesNationwideAndMatchingLocalWithoutDuplicates() {
        WelfareService nationwide = saveService("latest-nationwide");
        WelfareService seoul = saveService("latest-seoul");
        WelfareService busan = saveService("latest-busan");

        serviceRegionRepository.saveAll(List.of(
                ServiceRegion.builder()
                        .service(seoul)
                        .regionCode("11680")
                        .sidoName("서울특별시")
                        .sggName("강남구")
                        .build(),
                ServiceRegion.builder()
                        .service(seoul)
                        .regionCode("11740")
                        .sidoName("서울특별시")
                        .sggName("강동구")
                        .build(),
                ServiceRegion.builder()
                        .service(busan)
                        .regionCode("26350")
                        .sidoName("부산광역시")
                        .sggName("해운대구")
                        .build()
        ));

        List<WelfareService> results = welfareServiceRepository.findLatestCandidatesWithRegionCode(
                25,
                5,
                "11680",
                "서울특별시",
                null,
                PageRequest.of(0, 5000)
        );

        assertThat(results)
                .extracting(WelfareService::getId)
                .contains(nationwide.getId(), seoul.getId())
                .doesNotContain(busan.getId());
        assertThat(results.stream()
                .filter(service -> service.getId().equals(seoul.getId()))
                .count()).isEqualTo(1L);
    }

    @Test
    @DisplayName("지역코드 최신 추천 후보도 매칭된 BOKJIRO_LOCAL 정책을 전국 정책보다 먼저 노출한다")
    void findLatestCandidatesWithRegionCodePrioritizesMatchingLocalPolicies() {
        WelfareService matchingLocal = saveService("latest-region-local-first", WelfareService.SourceType.BOKJIRO_LOCAL);
        WelfareService nationwide = saveService("latest-region-nationwide", WelfareService.SourceType.YOUTH);

        serviceRegionRepository.save(ServiceRegion.builder()
                .service(matchingLocal)
                .regionCode(PRIORITY_TEST_REGION_CODE)
                .sidoName(PRIORITY_TEST_SIDO)
                .sggName("테스트중구")
                .build());

        List<WelfareService> results = welfareServiceRepository.findLatestCandidatesWithRegionCode(
                25,
                5,
                PRIORITY_TEST_REGION_CODE,
                PRIORITY_TEST_SIDO,
                null,
                PageRequest.of(0, 10)
        );

        assertThat(results).extracting(WelfareService::getId)
                .contains(matchingLocal.getId(), nationwide.getId());
        assertThat(indexOf(results, matchingLocal))
                .isLessThan(indexOf(results, nationwide));
    }

    @Test
    @DisplayName("지역코드 최신 추천 후보도 같은 매칭 BOKJIRO_LOCAL 안에서 youth/non-기타 신호를 먼저 노출한다")
    void findLatestCandidatesWithRegionCodePrioritizesYouthRelevantNonOtherMatchingLocalPolicies() {
        WelfareService strongLocal = saveService(
                "latest-region-local-strong",
                WelfareService.SourceType.BOKJIRO_LOCAL,
                true,
                "주거"
        );
        WelfareService weakLocal = saveService(
                "latest-region-local-weak",
                WelfareService.SourceType.BOKJIRO_LOCAL,
                false,
                "기타"
        );

        serviceRegionRepository.saveAll(List.of(
                ServiceRegion.builder()
                        .service(weakLocal)
                        .regionCode(PRIORITY_TEST_REGION_CODE)
                        .sidoName(PRIORITY_TEST_SIDO)
                        .sggName("테스트중구")
                        .build(),
                ServiceRegion.builder()
                        .service(strongLocal)
                        .regionCode(PRIORITY_TEST_REGION_CODE)
                        .sidoName(PRIORITY_TEST_SIDO)
                        .sggName("테스트중구")
                        .build()
        ));

        List<WelfareService> results = welfareServiceRepository.findLatestCandidatesWithRegionCode(
                25,
                5,
                PRIORITY_TEST_REGION_CODE,
                PRIORITY_TEST_SIDO,
                null,
                PageRequest.of(0, 10)
        );

        assertThat(indexOf(results, strongLocal))
                .isLessThan(indexOf(results, weakLocal));
    }

    @Test
    @DisplayName("지역코드 최신 추천 후보도 region_code가 비어도 same-sido BOKJIRO_LOCAL youth/non-기타 후보를 포함한다")
    void findLatestCandidatesWithRegionCodeIncludesSameSidoLocalYouthBridgeWithoutExactRegionCode() {
        WelfareService sameSidoLocal = saveService(
                "latest-region-local-sido-bridge",
                WelfareService.SourceType.BOKJIRO_LOCAL,
                true,
                "금융·생활지원"
        );
        WelfareService sameSidoOther = saveService(
                "latest-region-local-sido-other",
                WelfareService.SourceType.BOKJIRO_LOCAL,
                true,
                "기타"
        );

        serviceRegionRepository.saveAll(List.of(
                ServiceRegion.builder()
                        .service(sameSidoLocal)
                        .regionCode(null)
                        .sidoName(PRIORITY_TEST_SIDO)
                        .sggName("다른시군구")
                        .build(),
                ServiceRegion.builder()
                        .service(sameSidoOther)
                        .regionCode(null)
                        .sidoName(PRIORITY_TEST_SIDO)
                        .sggName("다른시군구")
                        .build()
        ));

        List<WelfareService> results = welfareServiceRepository.findLatestCandidatesWithRegionCode(
                25,
                5,
                PRIORITY_TEST_REGION_CODE,
                PRIORITY_TEST_SIDO,
                null,
                PageRequest.of(0, 20)
        );

        assertThat(results).extracting(WelfareService::getId)
                .contains(sameSidoLocal.getId())
                .doesNotContain(sameSidoOther.getId());
    }

    @Test
    @DisplayName("지역코드 최신 추천 후보도 사용자 sgg가 있으면 다른 시군구 same-sido fallback을 포함하지 않는다")
    void findLatestCandidatesWithRegionCodeExcludesDifferentSggSameSidoFallbackWhenUserSggPresent() {
        WelfareService sameSidoDifferentSgg = saveService(
                "latest-region-local-sido-different-sgg",
                WelfareService.SourceType.BOKJIRO_LOCAL,
                true,
                "금융·생활지원"
        );
        WelfareService sameSggBridge = saveService(
                "latest-region-local-sido-same-sgg",
                WelfareService.SourceType.BOKJIRO_LOCAL,
                true,
                "금융·생활지원"
        );

        serviceRegionRepository.saveAll(List.of(
                ServiceRegion.builder()
                        .service(sameSidoDifferentSgg)
                        .regionCode(null)
                        .sidoName("경기도")
                        .sggName("고양시")
                        .build(),
                ServiceRegion.builder()
                        .service(sameSggBridge)
                        .regionCode(null)
                        .sidoName("경기도")
                        .sggName("수원시")
                        .build()
        ));

        List<WelfareService> results = welfareServiceRepository.findLatestCandidatesWithRegionCode(
                25,
                5,
                "41110",
                "경기도",
                "수원시",
                PageRequest.of(0, 5000)
        );

        assertThat(results).extracting(WelfareService::getId)
                .contains(sameSggBridge.getId())
                .doesNotContain(sameSidoDifferentSgg.getId());
    }

    @Test
    @DisplayName("지역코드 최신 추천 후보도 service_regions가 없는 지자체 정책을 전국 정책처럼 포함하지 않는다")
    void findLatestCandidatesWithRegionCodeExcludesRegionlessLocalPolicies() {
        WelfareService regionlessLocal = saveService(
                "latest-regionless-local-should-be-excluded",
                WelfareService.SourceType.BOKJIRO_LOCAL,
                true,
                "금융·생활지원"
        );
        List<WelfareService> results = welfareServiceRepository.findLatestCandidatesWithRegionCode(
                25,
                5,
                "41110",
                "경기도",
                "수원시",
                PageRequest.of(0, 20)
        );

        assertThat(results).extracting(WelfareService::getId)
                .doesNotContain(regionlessLocal.getId());
    }

    @Test
    @DisplayName("지역코드 최신 추천 후보도 bounded same-sido BOKJIRO_LOCAL fallback을 generic exact-region 후보보다 먼저 노출한다")
    void findLatestCandidatesWithRegionCodePrioritizesBoundedSameSidoLocalFallbackOverGenericExactRegion() {
        WelfareService sameSidoLocal = saveService(
                "latest-region-local-sido-priority",
                WelfareService.SourceType.BOKJIRO_LOCAL,
                true,
                "금융·생활지원"
        );
        WelfareService exactRegionYouth = saveService(
                "latest-region-exact-generic",
                WelfareService.SourceType.YOUTH,
                true,
                "일자리"
        );

        serviceRegionRepository.saveAll(List.of(
                ServiceRegion.builder()
                        .service(sameSidoLocal)
                        .regionCode(null)
                        .sidoName(PRIORITY_TEST_SIDO)
                        .sggName("다른시군구")
                        .build(),
                ServiceRegion.builder()
                        .service(exactRegionYouth)
                        .regionCode(PRIORITY_TEST_REGION_CODE)
                        .sidoName(PRIORITY_TEST_SIDO)
                        .sggName("테스트중구")
                        .build()
        ));

        List<WelfareService> results = welfareServiceRepository.findLatestCandidatesWithRegionCode(
                25,
                5,
                PRIORITY_TEST_REGION_CODE,
                PRIORITY_TEST_SIDO,
                null,
                PageRequest.of(0, 20)
        );

        assertThat(results).extracting(WelfareService::getId)
                .contains(sameSidoLocal.getId(), exactRegionYouth.getId());
        assertThat(indexOf(results, sameSidoLocal))
                .isLessThan(indexOf(results, exactRegionYouth));
    }

    @Test
    @DisplayName("지역코드 최신 추천 후보도 service_regions가 없는 Gov24 시도 텍스트 매칭을 먼저 노출한다")
    void findLatestCandidatesWithRegionCodePrioritizesGov24SidoTextMatchWithoutServiceRegion() {
        WelfareService gov24TextMatch = saveServiceWithTitle(
                "latest-gov24-region-text-match",
                WelfareService.SourceType.GOV24,
                "인천 청년도약기지",
                "인천광역시 청년 취업 지원",
                true,
                "일자리"
        );
        WelfareService exactRegionYouth = saveService(
                "latest-region-exact-after-gov24-text",
                WelfareService.SourceType.YOUTH,
                true,
                "일자리"
        );

        serviceRegionRepository.save(ServiceRegion.builder()
                .service(exactRegionYouth)
                .regionCode("28110")
                .sidoName("인천광역시")
                .sggName("중구")
                .build());

        List<WelfareService> results = welfareServiceRepository.findLatestCandidatesWithRegionCode(
                25,
                5,
                "28110",
                "인천광역시",
                null,
                PageRequest.of(0, 5000)
        );

        assertThat(results).extracting(WelfareService::getId)
                .contains(gov24TextMatch.getId(), exactRegionYouth.getId());
        assertThat(indexOf(results, gov24TextMatch))
                .isLessThan(indexOf(results, exactRegionYouth));
    }

    @Test
    @DisplayName("시도 추천 후보는 전국 정책과 같은 시도 정책만 포함한다")
    void findCandidatesWithSidoIncludesNationwideAndMatchingLocalWithoutDuplicates() {
        WelfareService nationwide = saveService("sido-nationwide");
        WelfareService seoul = saveService("sido-seoul");
        WelfareService busan = saveService("sido-busan");

        serviceRegionRepository.saveAll(List.of(
                ServiceRegion.builder()
                        .service(seoul)
                        .regionCode("11740")
                        .sidoName("서울특별시")
                        .sggName("강동구")
                        .build(),
                ServiceRegion.builder()
                        .service(busan)
                        .regionCode("26350")
                        .sidoName("부산광역시")
                        .sggName("해운대구")
                        .build()
        ));

        List<WelfareService> results = welfareServiceRepository.findCandidatesWithSido(
                25,
                5,
                "서울특별시",
                PageRequest.of(0, 5000)
        );

        assertThat(results)
                .extracting(WelfareService::getId)
                .contains(nationwide.getId(), seoul.getId())
                .doesNotContain(busan.getId());
        assertThat(results.stream()
                .filter(service -> service.getId().equals(seoul.getId()))
                .count()).isEqualTo(1L);
    }

    @Test
    @DisplayName("시도 추천 후보는 매칭된 BOKJIRO_LOCAL 정책을 전국 정책보다 먼저 노출한다")
    void findCandidatesWithSidoPrioritizesMatchingLocalPolicies() {
        WelfareService matchingLocal = saveService("sido-local-first", WelfareService.SourceType.BOKJIRO_LOCAL);
        WelfareService nationwide = saveService("sido-nationwide-priority", WelfareService.SourceType.YOUTH);

        serviceRegionRepository.save(ServiceRegion.builder()
                .service(matchingLocal)
                .regionCode(PRIORITY_TEST_REGION_CODE)
                .sidoName(PRIORITY_TEST_SIDO)
                .sggName("테스트중구")
                .build());

        List<WelfareService> results = welfareServiceRepository.findCandidatesWithSido(
                25,
                5,
                PRIORITY_TEST_SIDO,
                PageRequest.of(0, 10)
        );

        assertThat(results).extracting(WelfareService::getId)
                .contains(matchingLocal.getId(), nationwide.getId());
        assertThat(indexOf(results, matchingLocal))
                .isLessThan(indexOf(results, nationwide));
    }

    @Test
    @DisplayName("시도 최신 추천 후보는 전국 정책과 같은 시도 정책만 포함한다")
    void findLatestCandidatesWithSidoIncludesNationwideAndMatchingLocalWithoutDuplicates() {
        WelfareService nationwide = saveService("latest-sido-nationwide");
        WelfareService seoul = saveService("latest-sido-seoul");
        WelfareService busan = saveService("latest-sido-busan");

        serviceRegionRepository.saveAll(List.of(
                ServiceRegion.builder()
                        .service(seoul)
                        .regionCode("11740")
                        .sidoName("서울특별시")
                        .sggName("강동구")
                        .build(),
                ServiceRegion.builder()
                        .service(busan)
                        .regionCode("26350")
                        .sidoName("부산광역시")
                        .sggName("해운대구")
                        .build()
        ));

        List<WelfareService> results = welfareServiceRepository.findLatestCandidatesWithSido(
                25,
                5,
                "서울특별시",
                PageRequest.of(0, 5000)
        );

        assertThat(results)
                .extracting(WelfareService::getId)
                .contains(nationwide.getId(), seoul.getId())
                .doesNotContain(busan.getId());
        assertThat(results.stream()
                .filter(service -> service.getId().equals(seoul.getId()))
                .count()).isEqualTo(1L);
    }

    @Test
    @DisplayName("시도 최신 추천 후보도 매칭된 BOKJIRO_LOCAL 정책을 전국 정책보다 먼저 노출한다")
    void findLatestCandidatesWithSidoPrioritizesMatchingLocalPolicies() {
        WelfareService matchingLocal = saveService("latest-sido-local-first", WelfareService.SourceType.BOKJIRO_LOCAL);
        WelfareService nationwide = saveService("latest-sido-nationwide-priority", WelfareService.SourceType.YOUTH);

        serviceRegionRepository.save(ServiceRegion.builder()
                .service(matchingLocal)
                .regionCode(PRIORITY_TEST_REGION_CODE)
                .sidoName(PRIORITY_TEST_SIDO)
                .sggName("테스트중구")
                .build());

        List<WelfareService> results = welfareServiceRepository.findLatestCandidatesWithSido(
                25,
                5,
                PRIORITY_TEST_SIDO,
                PageRequest.of(0, 10)
        );

        assertThat(results).extracting(WelfareService::getId)
                .contains(matchingLocal.getId(), nationwide.getId());
        assertThat(indexOf(results, matchingLocal))
                .isLessThan(indexOf(results, nationwide));
    }

    private WelfareService saveService(String label) {
        return saveService(label, WelfareService.SourceType.YOUTH);
    }

    private WelfareService saveService(String label, WelfareService.SourceType sourceType) {
        return saveService(label, sourceType, true, "주거");
    }

    private WelfareService saveService(String label,
                                       WelfareService.SourceType sourceType,
                                       boolean searchYouthRelevant,
                                       String unifiedCategory) {
        return saveServiceWithTitle(
                label,
                sourceType,
                "지역 추천 테스트 정책 " + label,
                "지역 추천 테스트",
                searchYouthRelevant,
                unifiedCategory
        );
    }

    private WelfareService saveServiceWithTitle(String label,
                                                WelfareService.SourceType sourceType,
                                                String title,
                                                String description,
                                                boolean searchYouthRelevant,
                                                String unifiedCategory) {
        return welfareServiceRepository.save(WelfareService.builder()
                .sourceType(sourceType)
                .sourceId(TEST_SOURCE_PREFIX + UUID.randomUUID().toString().substring(0, 8))
                .title(title + " " + label)
                .description(description)
                .status(WelfareService.ServiceStatus.ACTIVE)
                .minAge(19)
                .maxAge(34)
                .minIncome(1)
                .maxIncome(10)
                .searchYouthRelevant(searchYouthRelevant)
                .unifiedCategory(unifiedCategory)
                .apiViewCount(0L)
                .build());
    }

    private int indexOf(List<WelfareService> services, WelfareService target) {
        return services.stream()
                .map(WelfareService::getId)
                .toList()
                .indexOf(target.getId());
    }
}
