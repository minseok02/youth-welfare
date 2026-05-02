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
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("integration")
class RecommendationRegionQueryIntegrationTest {

    private static final String TEST_SOURCE_PREFIX = "IT-REGION-";

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
                PageRequest.of(0, 5000)
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

    private WelfareService saveService(String label) {
        return welfareServiceRepository.save(WelfareService.builder()
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId(TEST_SOURCE_PREFIX + UUID.randomUUID().toString().substring(0, 8))
                .title("지역 추천 테스트 정책 " + label)
                .description("지역 추천 테스트")
                .status(WelfareService.ServiceStatus.ACTIVE)
                .minAge(19)
                .maxAge(34)
                .minIncome(1)
                .maxIncome(10)
                .apiViewCount(0L)
                .build());
    }
}
