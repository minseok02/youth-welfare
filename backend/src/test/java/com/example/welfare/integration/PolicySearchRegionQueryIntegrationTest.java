package com.example.welfare.integration;

import com.example.welfare.global.util.RegionCodeUtil;
import com.example.welfare.policy.entity.ServiceRegion;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.PolicySearchReadCondition;
import com.example.welfare.policy.repository.ServiceRegionRepository;
import com.example.welfare.policy.repository.WelfareServiceReadRepository;
import com.example.welfare.policy.repository.WelfareServiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
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
class PolicySearchRegionQueryIntegrationTest {

    private static final String TEST_SOURCE_PREFIX = "IT-SRCH-";
    private static final String TEST_CATEGORY = "IT_SEARCH_REGION";

    @Autowired
    private WelfareServiceRepository welfareServiceRepository;

    @Autowired
    private ServiceRegionRepository serviceRegionRepository;
    @Autowired
    private WelfareServiceReadRepository welfareServiceReadRepository;

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
    @DisplayName("정책 목록은 단축 시도 요청으로 전체 시도명 저장 지역 정책을 찾는다")
    void listWithShortSidoMatchesFullSidoNameRows() {
        String token = "itsearch" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        WelfareService nationwide = saveSearchService("nationwide-short-sido-list", token);
        WelfareService seoulLocal = saveSearchService("seoul-short-sido-list", token, WelfareService.SourceType.BOKJIRO_LOCAL);
        WelfareService busanLocal = saveSearchService("busan-short-sido-list", token, WelfareService.SourceType.BOKJIRO_LOCAL);

        serviceRegionRepository.saveAll(List.of(
                ServiceRegion.builder()
                        .service(seoulLocal)
                        .sidoName("서울특별시")
                        .sggName("강남구")
                        .build(),
                ServiceRegion.builder()
                        .service(busanLocal)
                        .sidoName("부산광역시")
                        .sggName("해운대구")
                        .build()
        ));
        serviceRegionRepository.flush();

        Page<WelfareService> result = welfareServiceReadRepository.findList(
                new com.example.welfare.policy.repository.PolicyListReadCondition(
                        TEST_CATEGORY,
                        null,
                        null,
                        "ACTIVE_ONLY",
                        "서울",
                        "강남구",
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
                .contains(nationwide.getId(), seoulLocal.getId())
                .doesNotContain(busanLocal.getId());
    }

    @Test
    @DisplayName("정책 검색은 단축 시도 요청으로 전체명 row와 code-only row를 모두 지역 매칭한다")
    void keywordSearchWithShortSidoMatchesFullNameAndRegionCodeRows() {
        String token = "itsearch" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        WelfareService nationwide = saveSearchService("nationwide-short-sido-search", token);
        WelfareService seoulLocal = saveSearchService("seoul-full-name-search", token, WelfareService.SourceType.BOKJIRO_LOCAL);
        WelfareService seoulYouth = saveSearchService("seoul-code-only-search", token, WelfareService.SourceType.YOUTH);
        WelfareService busanYouth = saveSearchService("busan-code-only-search", token, WelfareService.SourceType.YOUTH);

        serviceRegionRepository.saveAll(List.of(
                ServiceRegion.builder()
                        .service(seoulLocal)
                        .sidoName("서울특별시")
                        .sggName("강남구")
                        .build(),
                ServiceRegion.builder()
                        .service(seoulYouth)
                        .regionCode("11680")
                        .build(),
                ServiceRegion.builder()
                        .service(busanYouth)
                        .regionCode("26350")
                        .build()
        ));
        serviceRegionRepository.flush();

        Page<WelfareService> result = welfareServiceReadRepository.search(
                new PolicySearchReadCondition(
                        token,
                        null,
                        "ACTIVE_ONLY",
                        TEST_CATEGORY,
                        null,
                        null,
                        "서울",
                        "강남구",
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
                .contains(nationwide.getId(), seoulLocal.getId(), seoulYouth.getId())
                .doesNotContain(busanYouth.getId());
    }

    @Test
    @DisplayName("시도+시군구 지역 검색은 전국 정책과 매칭 지역 정책만 반환한다")
    void searchWithSidoAndSggReturnsOnlyMatchingPolicies() {
        String token = "itsearch" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        WelfareService nationwide = saveSearchService("nationwide", token);
        WelfareService seoul = saveSearchService("seoul", token);
        WelfareService busan = saveSearchService("busan", token);

        serviceRegionRepository.saveAll(List.of(
                ServiceRegion.builder()
                        .service(seoul)
                        .sidoName("서울특별시")
                        .sggName("강남구")
                        .build(),
                ServiceRegion.builder()
                        .service(busan)
                        .sidoName("부산광역시")
                        .sggName("해운대구")
                        .build()
        ));
        serviceRegionRepository.flush();

        Page<WelfareService> result = welfareServiceRepository.findListWithFilters(
                TEST_CATEGORY,
                WelfareService.SourceType.YOUTH.name(),
                null,
                "ACTIVE_ONLY",
                "서울특별시",
                "강남구",
                RegionCodeUtil.getSidoCode("서울특별시"),
                RegionCodeUtil.getRegionCode("서울특별시", "강남구"),
                null,
                "LATEST",
                null,
                null,
                null,
                null,
                null,
                PageRequest.of(0, 100)
        );

        assertThat(result.getContent())
                .extracting(WelfareService::getId)
                .containsExactlyInAnyOrder(nationwide.getId(), seoul.getId());
    }

    @Test
    @DisplayName("정책 검색은 시도+시군구 조건에서 전국 정책과 매칭 지역 정책만 반환하고 지역 정책을 먼저 노출한다")
    void keywordSearchWithSidoAndSggReturnsMatchingPoliciesFirst() {
        String token = "itsearch" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        WelfareService nationwide = saveSearchService("nationwide-search", token);
        WelfareService seoul = saveSearchService("seoul-search", token);
        WelfareService busan = saveSearchService("busan-search", token);

        serviceRegionRepository.saveAll(List.of(
                ServiceRegion.builder()
                        .service(seoul)
                        .sidoName("서울특별시")
                        .sggName("강남구")
                        .build(),
                ServiceRegion.builder()
                        .service(busan)
                        .sidoName("부산광역시")
                        .sggName("해운대구")
                        .build()
        ));
        serviceRegionRepository.flush();

        Page<WelfareService> result = welfareServiceReadRepository.search(
                new PolicySearchReadCondition(
                        token,
                        null,
                        "ACTIVE_ONLY",
                        TEST_CATEGORY,
                        WelfareService.SourceType.YOUTH.name(),
                        null,
                        "서울특별시",
                        "강남구",
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
                .containsExactly(seoul.getId(), nationwide.getId());
    }

    @Test
    @DisplayName("넓은 키워드 검색 첫 페이지는 Gov24 후보가 candidate window에 있으면 발견성을 보존한다")
    void broadKeywordSearchKeepsGov24VisibleOnFirstPage() {
        String token = "itsearch" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        WelfareService gov24 = saveSearchService("gov24-discovery", token, WelfareService.SourceType.GOV24);
        for (int i = 0; i < 12; i += 1) {
            saveSearchService("youth-dominant-" + i, token, WelfareService.SourceType.YOUTH);
        }

        Page<WelfareService> result = welfareServiceReadRepository.search(
                new PolicySearchReadCondition(
                        token,
                        null,
                        "ACTIVE_ONLY",
                        TEST_CATEGORY,
                        null,
                        null,
                        null,
                        null,
                        "RELEVANCE",
                        null,
                        null,
                        null,
                        null,
                        null
                ),
                PageRequest.of(0, 10)
        );

        assertThat(result.getTotalElements()).isEqualTo(13);
        assertThat(result.getContent()).hasSize(10);
        assertThat(result.getContent())
                .extracting(WelfareService::getId)
                .contains(gov24.getId());
    }

    @Test
    @DisplayName("시도 전용 지역 검색은 전국 정책과 같은 시도 정책을 반환한다")
    void searchWithSidoOnlyReturnsOnlyMatchingPolicies() {
        String token = "itsearch" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        WelfareService nationwide = saveSearchService("nationwide2", token);
        WelfareService seoul = saveSearchService("seoul2", token);
        WelfareService busan = saveSearchService("busan2", token);

        serviceRegionRepository.saveAll(List.of(
                ServiceRegion.builder()
                        .service(seoul)
                        .sidoName("서울특별시")
                        .sggName("강동구")
                        .build(),
                ServiceRegion.builder()
                        .service(busan)
                        .sidoName("부산광역시")
                        .sggName("해운대구")
                        .build()
        ));
        serviceRegionRepository.flush();

        Page<WelfareService> result = welfareServiceRepository.findListWithFilters(
                TEST_CATEGORY,
                WelfareService.SourceType.YOUTH.name(),
                null,
                "ACTIVE_ONLY",
                "서울특별시",
                null,
                RegionCodeUtil.getSidoCode("서울특별시"),
                null,
                null,
                "LATEST",
                null,
                null,
                null,
                null,
                null,
                PageRequest.of(0, 100)
        );

        assertThat(result.getContent())
                .extracting(WelfareService::getId)
                .containsExactlyInAnyOrder(nationwide.getId(), seoul.getId());
    }

    private WelfareService saveSearchService(String label, String token) {
        return saveSearchService(label, token, WelfareService.SourceType.YOUTH);
    }

    private WelfareService saveSearchService(String label, String token, WelfareService.SourceType sourceType) {
        return welfareServiceRepository.saveAndFlush(WelfareService.builder()
                .sourceType(sourceType)
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
