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
    @DisplayName("시도+시군구 지역 검색은 전국 정책과 매칭 지역 정책만 반환한다")
    void searchWithSidoAndSggReturnsOnlyMatchingPolicies() {
        String token = "지역검색토큰" + UUID.randomUUID().toString().substring(0, 4);
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

        Page<WelfareService> result = welfareServiceRepository.searchByKeywordWithFiltersWithSidoSgg(
                "+" + token,
                null,
                0,
                null,
                null,
                null,
                "서울특별시",
                "강남구",
                "RELEVANCE",
                PageRequest.of(0, 100)
        );

        assertThat(result.getContent())
                .extracting(WelfareService::getId)
                .containsExactlyInAnyOrder(nationwide.getId(), seoul.getId());
    }

    @Test
    @DisplayName("시도 전용 지역 검색은 전국 정책과 같은 시도 정책을 반환한다")
    void searchWithSidoOnlyReturnsOnlyMatchingPolicies() {
        String token = "지역검색토큰" + UUID.randomUUID().toString().substring(0, 4);
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

        Page<WelfareService> result = welfareServiceRepository.searchByKeywordWithFiltersWithSido(
                "+" + token,
                null,
                0,
                null,
                null,
                null,
                "서울특별시",
                "RELEVANCE",
                PageRequest.of(0, 100)
        );

        assertThat(result.getContent())
                .extracting(WelfareService::getId)
                .containsExactlyInAnyOrder(nationwide.getId(), seoul.getId());
    }

    private WelfareService saveSearchService(String label, String token) {
        return welfareServiceRepository.save(WelfareService.builder()
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId(TEST_SOURCE_PREFIX + UUID.randomUUID().toString().substring(0, 8))
                .title("정책 " + label + " " + token)
                .description(token + " 설명")
                .keyword(token)
                .status(WelfareService.ServiceStatus.ACTIVE)
                .searchYouthRelevant(true)
                .apiViewCount(0L)
                .build());
    }
}
