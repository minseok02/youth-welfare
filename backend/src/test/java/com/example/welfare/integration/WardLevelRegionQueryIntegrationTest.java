package com.example.welfare.integration;

import com.example.welfare.global.util.RegionCodeUtil;
import com.example.welfare.policy.entity.ServiceRegion;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.ServiceRegionRepository;
import com.example.welfare.policy.repository.WelfareServiceReadRepository;
import com.example.welfare.policy.repository.WelfareServiceRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("integration")
class WardLevelRegionQueryIntegrationTest {

    private static final String TEST_SOURCE_PREFIX = "IT-WARD-";
    private static final String TEST_CATEGORY = "IT_WARD_REGION";

    @Autowired
    private WelfareServiceRepository welfareServiceRepository;

    @Autowired
    private WelfareServiceReadRepository welfareServiceReadRepository;

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

        List<Long> serviceIds = testServices.stream()
                .map(WelfareService::getId)
                .toList();
        serviceIds.forEach(serviceId ->
                serviceRegionRepository.deleteAllInBatch(serviceRegionRepository.findByServiceId(serviceId)));
        welfareServiceRepository.deleteAllByIdInBatch(serviceIds);
        welfareServiceRepository.flush();
    }

    @Test
    @DisplayName("추천 지역 후보는 구 단위 sgg로도 multi-district city 정책을 찾는다")
    void recommendationCandidatesMatchWardLevelRegionCode() {
        WelfareService nationwide = saveService("recommend-nationwide");
        WelfareService suwonWard = saveService("recommend-suwon-ward");

        serviceRegionRepository.save(ServiceRegion.builder()
                .service(suwonWard)
                .regionCode("41111")
                .sidoName("경기도")
                .sggName("장안구")
                .build());

        List<WelfareService> results = welfareServiceRepository.findCandidatesWithRegionCode(
                25,
                5,
                RegionCodeUtil.getRegionCode("경기도", "장안구"),
                "경기도",
                "장안구",
                PageRequest.of(0, 5000)
        );

        assertThat(results)
                .extracting(WelfareService::getId)
                .contains(nationwide.getId(), suwonWard.getId());
    }

    @Test
    @DisplayName("정책 목록 검색은 구 단위 sgg로도 multi-district city 정책을 찾는다")
    void policyListMatchesWardLevelRegionCode() {
        String token = "itward" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        WelfareService nationwide = saveService("search-nationwide", token);
        WelfareService suwonWard = saveService("search-suwon-ward", token);
        WelfareService otherWard = saveService("search-other-ward", token);

        serviceRegionRepository.saveAll(List.of(
                ServiceRegion.builder()
                        .service(suwonWard)
                        .regionCode("41111")
                        .sidoName("경기도")
                        .sggName("장안구")
                        .build(),
                ServiceRegion.builder()
                        .service(otherWard)
                        .regionCode("41113")
                        .sidoName("경기도")
                        .sggName("권선구")
                        .build()
        ));
        serviceRegionRepository.flush();

        Page<WelfareService> result = welfareServiceReadRepository.findList(
                new com.example.welfare.policy.repository.PolicyListReadCondition(
                        TEST_CATEGORY,
                        WelfareService.SourceType.YOUTH,
                        null,
                        "ACTIVE_ONLY",
                        "경기도",
                        "장안구",
                        null,
                        "LATEST",
                        null,
                        null,
                        null,
                        null,
                        null
                ),
                PageRequest.of(0, 100)
        );

        assertThat(result.getContent())
                .extracting(WelfareService::getId)
                .containsExactlyInAnyOrder(nationwide.getId(), suwonWard.getId());
    }

    private WelfareService saveService(String label) {
        return saveService(label, "ward");
    }

    private WelfareService saveService(String label, String token) {
        return welfareServiceRepository.saveAndFlush(WelfareService.builder()
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId(TEST_SOURCE_PREFIX + UUID.randomUUID().toString().substring(0, 8))
                .title("정책 " + label + " " + token)
                .description(token + " 설명")
                .keyword(token)
                .unifiedCategory(TEST_CATEGORY)
                .status(WelfareService.ServiceStatus.ACTIVE)
                .searchYouthRelevant(true)
                .apiViewCount(0L)
                .build());
    }
}
