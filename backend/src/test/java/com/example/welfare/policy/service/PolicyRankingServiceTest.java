package com.example.welfare.policy.service;

import com.example.welfare.policy.dto.PolicyRankingResponse;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.ServiceViewLogRepository;
import com.example.welfare.policy.repository.WelfareServiceRepository;
import com.example.welfare.recommend.dto.RecommendationCandidateProjection;
import com.example.welfare.recommend.repository.CanonicalRecommendationReadModelRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class PolicyRankingServiceTest {

    @Mock
    private WelfareServiceRepository welfareServiceRepository;
    @Mock
    private ServiceViewLogRepository serviceViewLogRepository;
    @Mock
    private CanonicalRecommendationReadModelRepository canonicalRecommendationReadModelRepository;

    @InjectMocks
    private PolicyRankingService policyRankingService;

    @Test
    @DisplayName("랭킹 응답에 7일 고유 조회수를 포함한다")
    void rankingIncludesUniqueViewCount() {
        WelfareService service = WelfareService.builder()
                .id(1L)
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId("S-1")
                .title("청년 월세 지원")
                .status(WelfareService.ServiceStatus.ACTIVE)
                .viewCount(10)
                .apiViewCount(100L)
                .registeredAt(LocalDateTime.now().minusDays(2))
                .build();

        given(welfareServiceRepository.findByStatusIn(any())).willReturn(List.of(service));
        given(serviceViewLogRepository.findUniqueViewCountsSince(anyCollection(), any()))
                .willReturn(List.of(uniqueCount(1L, 7L)));
        given(canonicalRecommendationReadModelRepository.findByServiceIds(List.of(1L)))
                .willReturn(java.util.Map.of(
                        1L,
                        RecommendationCandidateProjection.builder()
                                .serviceId(1L)
                                .unifiedCategoryCompat("주거")
                                .youthMajorLabel("주거")
                                .youthMidLabel("전월세 및 주거급여 지원")
                                .provisionMethodLabel("온라인")
                                .gov24ServiceFieldLabel("보육")
                                .gov24UserTypeLabel("청년")
                                .gov24BenefitTypeLabel("서비스")
                                .build()
                ));

        List<PolicyRankingResponse> ranking = policyRankingService.getRanking(20);

        assertEquals(1, ranking.size());
        assertEquals(7L, ranking.get(0).getUniqueViewCount7d());
        assertEquals(1L, ranking.get(0).getServiceId());
        assertEquals("주거", ranking.get(0).getUnifiedCategory());
        assertEquals("주거", ranking.get(0).getYouthMajorLabel());
        assertEquals("전월세 및 주거급여 지원", ranking.get(0).getYouthMidLabel());
        assertEquals("온라인", ranking.get(0).getProvisionMethodLabel());
        assertEquals("보육", ranking.get(0).getGov24ServiceFieldLabel());
        assertEquals("청년", ranking.get(0).getGov24UserTypeLabel());
        assertEquals("서비스", ranking.get(0).getGov24BenefitTypeLabel());
    }

    @Test
    @DisplayName("탐색 슬롯은 최근 정책을 top 결과에 포함시킨다")
    void rankingExplorationSlotInjectsRecentPolicies() {
        WelfareService oldHighScore = WelfareService.builder()
                .id(1L)
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId("S-1")
                .title("기존 인기 정책")
                .status(WelfareService.ServiceStatus.ACTIVE)
                .viewCount(500)
                .apiViewCount(1000L)
                .registeredAt(LocalDateTime.now().minusDays(60))
                .build();

        WelfareService oldLowScore = WelfareService.builder()
                .id(2L)
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId("S-2")
                .title("기존 하위 정책")
                .status(WelfareService.ServiceStatus.ACTIVE)
                .viewCount(1)
                .apiViewCount(1L)
                .registeredAt(LocalDateTime.now().minusDays(60))
                .build();

        WelfareService recentNew = WelfareService.builder()
                .id(3L)
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId("S-3")
                .title("신규 정책")
                .status(WelfareService.ServiceStatus.ACTIVE)
                .viewCount(0)
                .apiViewCount(0L)
                .registeredAt(LocalDateTime.now().minusDays(1))
                .build();

        List<WelfareService> services = List.of(oldHighScore, oldLowScore, recentNew);
        given(welfareServiceRepository.findByStatusIn(any())).willReturn(services);
        given(serviceViewLogRepository.findUniqueViewCountsSince(anyCollection(), any()))
                .willReturn(List.of(
                        uniqueCount(1L, 50L),
                        uniqueCount(2L, 1L),
                        uniqueCount(3L, 0L)
                ));
        given(canonicalRecommendationReadModelRepository.findByServiceIds(List.of(1L, 2L, 3L)))
                .willReturn(java.util.Map.of());

        List<PolicyRankingResponse> ranking = policyRankingService.getRanking(10);

        List<Long> ids = ranking.stream().map(PolicyRankingResponse::getServiceId).toList();
        assertEquals(3, ranking.size());
        // 신규 정책이 탐색 슬롯으로 포함되는지 확인
        assertEquals(true, ids.contains(3L));
    }

    private ServiceViewLogRepository.ServiceUniqueViewCount uniqueCount(Long serviceId, Long uniqueCount) {
        return new ServiceViewLogRepository.ServiceUniqueViewCount() {
            @Override
            public Long getServiceId() {
                return serviceId;
            }

            @Override
            public Long getUniqueViewCount() {
                return uniqueCount;
            }
        };
    }
}
