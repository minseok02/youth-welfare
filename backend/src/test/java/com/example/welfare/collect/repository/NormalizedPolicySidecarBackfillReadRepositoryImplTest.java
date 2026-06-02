package com.example.welfare.collect.repository;

import com.example.welfare.collect.entity.RawApiPayload;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.collect.repository.RawApiPayloadRepository;
import com.example.welfare.policy.repository.WelfareServiceRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class NormalizedPolicySidecarBackfillReadRepositoryImplTest {

    @Mock
    private RawApiPayloadRepository rawApiPayloadRepository;
    @Mock
    private WelfareServiceRepository welfareServiceRepository;
    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private NormalizedPolicySidecarBackfillReadRepositoryImpl normalizedPolicySidecarBackfillReadRepository;

    @Test
    @DisplayName("sidecar backfill read repository는 sourceType/sourceId 기준 정책 조회를 위임한다")
    void findServiceBySourceTypeAndSourceIdDelegates() {
        WelfareService service = WelfareService.builder().id(101L).sourceId("LOCAL-1").build();
        given(welfareServiceRepository.findBySourceTypeAndSourceId(WelfareService.SourceType.BOKJIRO_LOCAL, "LOCAL-1"))
                .willReturn(Optional.of(service));

        assertThat(normalizedPolicySidecarBackfillReadRepository.findServiceBySourceTypeAndSourceId(
                WelfareService.SourceType.BOKJIRO_LOCAL,
                "LOCAL-1"
        )).contains(service);
    }

    @Test
    @DisplayName("sidecar backfill read repository는 payload 목록과 매칭 서비스를 함께 조립한다")
    void findTargetsBySourceTypeAndApiCategoryOrderByFetchedAtAscDelegates() {
        RawApiPayload raw = RawApiPayload.builder()
                .sourceType(WelfareService.SourceType.BOKJIRO_LOCAL)
                .sourceId("LOCAL-1")
                .apiCategory(RawApiPayload.ApiCategory.LIST)
                .payloadJson("{}")
                .payloadHash("hash")
                .fetchedAt(LocalDateTime.now())
                .build();
        WelfareService service = WelfareService.builder().id(101L).sourceId("LOCAL-1").build();

        given(rawApiPayloadRepository.findAllBySourceTypeAndApiCategoryOrderByFetchedAtAsc(
                WelfareService.SourceType.BOKJIRO_LOCAL,
                RawApiPayload.ApiCategory.LIST
        )).willReturn(List.of(raw));
        given(welfareServiceRepository.findBySourceTypeAndSourceId(WelfareService.SourceType.BOKJIRO_LOCAL, "LOCAL-1"))
                .willReturn(Optional.of(service));

        assertThat(normalizedPolicySidecarBackfillReadRepository.findTargetsBySourceTypeAndApiCategoryOrderByFetchedAtAsc(
                WelfareService.SourceType.BOKJIRO_LOCAL,
                RawApiPayload.ApiCategory.LIST,
                10
        )).containsExactly(new NormalizedPolicySidecarBackfillTarget(raw, service));
    }
}
