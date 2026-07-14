package com.example.welfare.integration;

import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.PolicyListReadCondition;
import com.example.welfare.policy.repository.WelfareServiceReadRepository;
import com.example.welfare.policy.repository.WelfareServiceRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("integration")
class PolicyListFastPathIntegrationTest {

    private static final String TEST_SOURCE_PREFIX = "IT-LIST-FAST-";

    @Autowired
    private WelfareServiceRepository welfareServiceRepository;
    @Autowired
    private WelfareServiceReadRepository welfareServiceReadRepository;

    @BeforeEach
    void setup() {
        cleanup();
    }

    @AfterEach
    void cleanup() {
        List<Long> serviceIds = welfareServiceRepository.findAll().stream()
                .filter(service -> service.getSourceId() != null && service.getSourceId().startsWith(TEST_SOURCE_PREFIX))
                .map(WelfareService::getId)
                .toList();
        if (!serviceIds.isEmpty()) {
            welfareServiceRepository.deleteAllByIdInBatch(serviceIds);
            welfareServiceRepository.flush();
        }
    }

    @Test
    @DisplayName("기본 ACTIVE_ONLY 목록 fast path는 최신순/마감순/인기순 native query를 정상 실행한다")
    void defaultActiveOnlyListFastPathQueriesExecute() {
        String token = UUID.randomUUID().toString().replace("-", "");
        WelfareService active = welfareServiceRepository.save(WelfareService.builder()
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId(TEST_SOURCE_PREFIX + "ACTIVE-" + token)
                .title("fast path active")
                .status(WelfareService.ServiceStatus.ACTIVE)
                .applyEndDate(LocalDate.now().plusDays(7))
                .apiViewCount(9_999_999L)
                .viewCount(9_999_999)
                .registeredAt(LocalDateTime.of(2099, 1, 1, 0, 0))
                .lastModifiedAt(LocalDateTime.of(2099, 1, 2, 0, 0))
                .build());
        WelfareService expired = welfareServiceRepository.save(WelfareService.builder()
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId(TEST_SOURCE_PREFIX + "EXPIRED-" + token)
                .title("fast path expired")
                .status(WelfareService.ServiceStatus.ACTIVE)
                .applyEndDate(LocalDate.now().minusDays(1))
                .registeredAt(LocalDateTime.of(2099, 1, 3, 0, 0))
                .lastModifiedAt(LocalDateTime.of(2099, 1, 4, 0, 0))
                .build());
        WelfareService closed = welfareServiceRepository.save(WelfareService.builder()
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId(TEST_SOURCE_PREFIX + "CLOSED-" + token)
                .title("fast path closed")
                .status(WelfareService.ServiceStatus.CLOSED)
                .applyEndDate(LocalDate.now().plusDays(7))
                .registeredAt(LocalDateTime.of(2099, 1, 5, 0, 0))
                .lastModifiedAt(LocalDateTime.of(2099, 1, 6, 0, 0))
                .build());
        welfareServiceRepository.flush();

        assertFastPathResult("LATEST", active.getId(), expired.getId(), closed.getId());
        assertFastPathResult("DEADLINE", active.getId(), expired.getId(), closed.getId());
        assertFastPathResult("VIEWS", active.getId(), expired.getId(), closed.getId());
    }

    private void assertFastPathResult(String sort, Long activeId, Long expiredId, Long closedId) {
        List<Long> ids = welfareServiceReadRepository.findList(
                        new PolicyListReadCondition(
                                null,
                                null,
                                null,
                                "ACTIVE_ONLY",
                                null,
                                null,
                                null,
                                sort,
                                null,
                                null,
                                null,
                                null,
                                null
                        ),
                        PageRequest.of(0, 20_000)
                ).getContent().stream()
                .map(WelfareService::getId)
                .toList();

        assertThat(ids).contains(activeId);
        assertThat(ids).doesNotContain(expiredId, closedId);
    }
}
