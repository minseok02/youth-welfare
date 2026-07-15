package com.example.welfare.policy.service;

import com.example.welfare.global.config.JacksonConfig;
import com.example.welfare.policy.dto.PolicyRankingResponse;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.PolicyRankingReadRepository;
import com.example.welfare.recommend.dto.RecommendationCandidateProjection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class PolicyRankingServiceTest {

    @Mock
    private PolicyRankingReadRepository policyRankingReadRepository;
    @Mock
    private PolicyPresentationReadService policyPresentationReadService;

    private PolicyRankingService fixedClockService() {
        return new PolicyRankingService(
                policyRankingReadRepository,
                policyPresentationReadService,
                Clock.fixed(Instant.parse("2026-05-30T00:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    @DisplayName("랭킹 응답에 7일 고유 조회수를 포함한다")
    void rankingIncludesUniqueViewCount() {
        PolicyRankingService policyRankingService = fixedClockService();
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

        given(policyRankingReadRepository.findRankableSnapshots()).willReturn(List.of(snapshot(service)));
        given(policyRankingReadRepository.findUniqueViewCountsSinceForStatuses(any(), any()))
                .willReturn(List.of(uniqueCount(1L, 7L)));
        given(policyRankingReadRepository.findServicesByIds(List.of(1L))).willReturn(List.of(service));
        given(policyPresentationReadService.findProjections(List.of(service)))
                .willReturn(java.util.Map.of(
                        1L,
                        RecommendationCandidateProjection.builder()
                                .serviceId(1L)
                                .unifiedCategoryCompat("주거")
                                .youthMajorLabel("주거")
                                .youthMidLabel("전월세 및 주거급여 지원")
                                .provisionMethodLabel("온라인")
                                .gov24ServiceFieldLabel("주거·자립")
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
        assertEquals("주거·자립", ranking.get(0).getGov24ServiceFieldLabel());
        assertEquals("청년", ranking.get(0).getGov24UserTypeLabel());
        assertEquals("서비스", ranking.get(0).getGov24BenefitTypeLabel());
    }

    @Test
    @DisplayName("탐색 슬롯은 최근 정책을 top 결과에 포함시킨다")
    void rankingExplorationSlotInjectsRecentPolicies() {
        PolicyRankingService policyRankingService = fixedClockService();
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
        given(policyRankingReadRepository.findRankableSnapshots()).willReturn(services.stream().map(this::snapshot).toList());
        given(policyRankingReadRepository.findUniqueViewCountsSinceForStatuses(any(), any()))
                .willReturn(List.of(
                        uniqueCount(1L, 50L),
                        uniqueCount(2L, 1L),
                        uniqueCount(3L, 0L)
                ));
        given(policyRankingReadRepository.findServicesByIds(anyCollection())).willAnswer(invocation -> {
            java.util.Collection<Long> ids = invocation.getArgument(0);
            return services.stream().filter(service -> ids.contains(service.getId())).toList();
        });
        given(policyPresentationReadService.findProjections(services))
                .willReturn(java.util.Map.of());

        List<PolicyRankingResponse> ranking = policyRankingService.getRanking(10);

        List<Long> ids = ranking.stream().map(PolicyRankingResponse::getServiceId).toList();
        assertEquals(3, ranking.size());
        // 신규 정책이 탐색 슬롯으로 포함되는지 확인
        assertEquals(true, ids.contains(3L));
    }

    @Test
    @DisplayName("랭킹 projection 조회는 정규화된 응답 후보에만 수행한다")
    void rankingProjectionLookupUsesSelectedServicesOnly() {
        PolicyRankingService policyRankingService = fixedClockService();
        List<WelfareService> services = java.util.stream.LongStream.rangeClosed(1, 150)
                .mapToObj(id -> WelfareService.builder()
                        .id(id)
                        .sourceType(WelfareService.SourceType.YOUTH)
                        .sourceId("S-" + id)
                        .title("정책 " + id)
                        .status(WelfareService.ServiceStatus.ACTIVE)
                        .viewCount((int) id)
                        .apiViewCount(id)
                        .registeredAt(LocalDateTime.now().minusDays(id))
                        .build())
                .toList();

        given(policyRankingReadRepository.findRankableSnapshots()).willReturn(services.stream().map(this::snapshot).toList());
        given(policyRankingReadRepository.findUniqueViewCountsSinceForStatuses(any(), any()))
                .willReturn(List.of());
        given(policyRankingReadRepository.findServicesByIds(anyCollection())).willAnswer(invocation -> {
            java.util.Collection<Long> ids = invocation.getArgument(0);
            return services.stream().filter(service -> ids.contains(service.getId())).toList();
        });
        given(policyPresentationReadService.findProjections(any()))
                .willReturn(java.util.Map.of());

        List<PolicyRankingResponse> ranking = policyRankingService.getRanking(100_000);

        ArgumentCaptor<List<WelfareService>> projectionServices = ArgumentCaptor.forClass(List.class);
        verify(policyPresentationReadService).findProjections(projectionServices.capture());
        assertEquals(100, ranking.size());
        assertEquals(100, projectionServices.getValue().size());
    }

    @Test
    @DisplayName("동일 size 랭킹 반복 호출은 TTL 안에서 캐시를 재사용한다")
    void rankingReusesCacheWithinTtl() {
        PolicyRankingService policyRankingService = fixedClockService();
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

        given(policyRankingReadRepository.findRankableSnapshots()).willReturn(List.of(snapshot(service)));
        given(policyRankingReadRepository.findUniqueViewCountsSinceForStatuses(any(), any()))
                .willReturn(List.of(uniqueCount(1L, 7L)));
        given(policyRankingReadRepository.findServicesByIds(List.of(1L))).willReturn(List.of(service));
        given(policyPresentationReadService.findProjections(List.of(service))).willReturn(java.util.Map.of());

        policyRankingService.getRanking(20);
        policyRankingService.getRanking(20);

        verify(policyRankingReadRepository, times(1)).findRankableSnapshots();
        verify(policyRankingReadRepository, times(1)).findUniqueViewCountsSinceForStatuses(any(), any());
        verify(policyRankingReadRepository, times(1)).findServicesByIds(List.of(1L));
        verify(policyPresentationReadService, times(1)).findProjections(List.of(service));
    }

    @Test
    @DisplayName("랭킹은 Redis shared cache hit 시 repository를 호출하지 않는다")
    void rankingUsesRedisSharedCacheHit() throws Exception {
        RedisTemplate<String, String> redisTemplate = org.mockito.Mockito.mock(RedisTemplate.class);
        ValueOperations<String, String> valueOperations = org.mockito.Mockito.mock(ValueOperations.class);
        var objectMapper = new JacksonConfig().objectMapper();
        PolicyRankingService policyRankingService = new PolicyRankingService(
                policyRankingReadRepository,
                policyPresentationReadService,
                Clock.fixed(Instant.parse("2026-05-30T00:00:00Z"), ZoneOffset.UTC),
                redisTemplate,
                objectMapper,
                Duration.ofSeconds(30)
        );
        List<PolicyRankingResponse> cached = List.of(PolicyRankingResponse.builder()
                .serviceId(77L)
                .title("캐시 랭킹 정책")
                .sourceType("YOUTH")
                .uniqueViewCount7d(3L)
                .viewCount(10L)
                .apiViewCount(100L)
                .rankingScore(0.9)
                .build());

        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("policy:ranking:v1:full:20")).willReturn(objectMapper.writeValueAsString(cached));

        List<PolicyRankingResponse> result = policyRankingService.getRanking(20);

        assertEquals(1, result.size());
        assertEquals(77L, result.get(0).getServiceId());
        verifyNoInteractions(policyRankingReadRepository, policyPresentationReadService);
        verify(valueOperations).get("policy:ranking:v1:full:20");
        verify(valueOperations, org.mockito.Mockito.never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("후보 모드 Redis 캐시는 full 랭킹 캐시와 다른 키를 사용한다")
    void rankingCandidateModeUsesSeparateRedisCacheKey() throws Exception {
        RedisTemplate<String, String> redisTemplate = org.mockito.Mockito.mock(RedisTemplate.class);
        ValueOperations<String, String> valueOperations = org.mockito.Mockito.mock(ValueOperations.class);
        var objectMapper = new JacksonConfig().objectMapper();
        PolicyRankingService policyRankingService = new PolicyRankingService(
                policyRankingReadRepository,
                policyPresentationReadService,
                Clock.fixed(Instant.parse("2026-05-30T00:00:00Z"), ZoneOffset.UTC),
                redisTemplate,
                objectMapper,
                Duration.ofSeconds(30),
                true,
                "popular_recent_union",
                4000
        );
        List<PolicyRankingResponse> cached = List.of(PolicyRankingResponse.builder()
                .serviceId(88L)
                .title("후보 캐시 랭킹 정책")
                .sourceType("YOUTH")
                .uniqueViewCount7d(3L)
                .viewCount(10L)
                .apiViewCount(100L)
                .rankingScore(0.9)
                .build());

        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("policy:ranking:v1:popular_recent_union:4000:20"))
                .willReturn(objectMapper.writeValueAsString(cached));

        List<PolicyRankingResponse> result = policyRankingService.getRanking(20);

        assertEquals(1, result.size());
        assertEquals(88L, result.get(0).getServiceId());
        verifyNoInteractions(policyRankingReadRepository, policyPresentationReadService);
        verify(valueOperations).get("policy:ranking:v1:popular_recent_union:4000:20");
    }

    @Test
    @DisplayName("후보 모드는 rough 상위권 밖의 unique-view 정책과 최신 탐색 정책을 보존한다")
    void rankingCandidateModeKeepsUniqueViewAndRecentCandidates() {
        PolicyRankingService policyRankingService = new PolicyRankingService(
                policyRankingReadRepository,
                policyPresentationReadService,
                Clock.fixed(Instant.parse("2026-05-30T00:00:00Z"), ZoneOffset.UTC),
                null,
                null,
                Duration.ofSeconds(30),
                true,
                "popular_recent_union",
                10
        );
        LocalDateTime old = LocalDateTime.of(2026, 1, 1, 0, 0);
        List<WelfareService> services = java.util.stream.LongStream.rangeClosed(1, 58)
                .mapToObj(id -> WelfareService.builder()
                        .id(id)
                        .sourceType(WelfareService.SourceType.YOUTH)
                        .sourceId("S-" + id)
                        .title("기존 인기 정책 " + id)
                        .status(WelfareService.ServiceStatus.ACTIVE)
                        .viewCount(1000 - (int) id)
                        .apiViewCount(1000L - id)
                        .registeredAt(old)
                        .build())
                .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
        WelfareService recentLowScore = WelfareService.builder()
                .id(59L)
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId("S-59")
                .title("최신 저조회 정책")
                .status(WelfareService.ServiceStatus.ACTIVE)
                .viewCount(0)
                .apiViewCount(0L)
                .registeredAt(LocalDateTime.of(2026, 5, 29, 0, 0))
                .build();
        WelfareService uniqueTail = WelfareService.builder()
                .id(60L)
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId("S-60")
                .title("고유조회 급등 정책")
                .status(WelfareService.ServiceStatus.ACTIVE)
                .viewCount(0)
                .apiViewCount(0L)
                .registeredAt(old)
                .build();
        services.add(recentLowScore);
        services.add(uniqueTail);

        given(policyRankingReadRepository.findRankableSnapshots()).willReturn(services.stream().map(this::snapshot).toList());
        given(policyRankingReadRepository.findUniqueViewCountsSinceForStatuses(any(), any()))
                .willReturn(List.of(uniqueCount(60L, 10_000L)));
        given(policyRankingReadRepository.findServicesByIds(anyCollection())).willAnswer(invocation -> {
            java.util.Collection<Long> ids = invocation.getArgument(0);
            return services.stream().filter(service -> ids.contains(service.getId())).toList();
        });
        given(policyPresentationReadService.findProjections(any())).willReturn(java.util.Map.of());

        List<PolicyRankingResponse> ranking = policyRankingService.getRanking(10);

        List<Long> ids = ranking.stream().map(PolicyRankingResponse::getServiceId).toList();
        assertEquals(10, ranking.size());
        assertTrue(ids.contains(60L));
        assertTrue(ids.contains(59L));
    }

    private PolicyRankingReadRepository.RankableServiceSnapshot snapshot(WelfareService service) {
        return new PolicyRankingReadRepository.RankableServiceSnapshot() {
            @Override
            public Long getId() {
                return service.getId();
            }

            @Override
            public WelfareService.SourceType getSourceType() {
                return service.getSourceType();
            }

            @Override
            public Integer getViewCount() {
                return service.getViewCount();
            }

            @Override
            public Long getApiViewCount() {
                return service.getApiViewCount();
            }

            @Override
            public LocalDateTime getCreatedAt() {
                return service.getCreatedAt();
            }

            @Override
            public LocalDateTime getRegisteredAt() {
                return service.getRegisteredAt();
            }

            @Override
            public LocalDateTime getLastModifiedAt() {
                return service.getLastModifiedAt();
            }
        };
    }

    private PolicyRankingReadRepository.ServiceUniqueViewCount uniqueCount(Long serviceId, Long uniqueCount) {
        return new PolicyRankingReadRepository.ServiceUniqueViewCount() {
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
