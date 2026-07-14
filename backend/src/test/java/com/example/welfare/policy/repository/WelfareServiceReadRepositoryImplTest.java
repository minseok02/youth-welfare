package com.example.welfare.policy.repository;

import com.example.welfare.policy.entity.WelfareService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class WelfareServiceReadRepositoryImplTest {

    @Mock
    private WelfareServiceRepository welfareServiceRepository;
    @Mock
    private WelfareServiceSearchRepository welfareServiceSearchRepository;

    @Test
    @DisplayName("기본 ACTIVE_ONLY 최신순 목록은 row fast path와 exact count를 사용한다")
    void findListUsesLatestFastPathForDefaultActiveOnlyList() {
        WelfareServiceReadRepositoryImpl repository = newRepository();
        Pageable pageable = PageRequest.of(0, 20);
        WelfareService service = welfareService(1L);
        given(welfareServiceRepository.findActiveOnlyLatestListRows(any(Pageable.class)))
                .willReturn(List.of(service));
        given(welfareServiceRepository.countActiveOnlyVisibleList()).willReturn(123L);

        Page<WelfareService> result = repository.findList(defaultActiveOnlyCondition("LATEST"), pageable);

        assertThat(result.getContent()).containsExactly(service);
        assertThat(result.getTotalElements()).isEqualTo(123L);
        verify(welfareServiceRepository).findActiveOnlyLatestListRows(eq(pageable));
        verify(welfareServiceRepository).countActiveOnlyVisibleList();
        verifyNoMoreInteractions(welfareServiceRepository);
    }

    @Test
    @DisplayName("기본 ACTIVE_ONLY 마감순 목록은 row fast path와 exact count를 사용한다")
    void findListUsesDeadlineFastPathForDefaultActiveOnlyList() {
        WelfareServiceReadRepositoryImpl repository = newRepository();
        Pageable pageable = PageRequest.of(0, 20);
        given(welfareServiceRepository.findActiveOnlyDeadlineListRows(any(Pageable.class)))
                .willReturn(List.of());
        given(welfareServiceRepository.countActiveOnlyVisibleList()).willReturn(123L);

        Page<WelfareService> result = repository.findList(defaultActiveOnlyCondition("DEADLINE"), pageable);

        assertThat(result.getTotalElements()).isEqualTo(123L);
        verify(welfareServiceRepository).findActiveOnlyDeadlineListRows(eq(pageable));
        verify(welfareServiceRepository).countActiveOnlyVisibleList();
        verifyNoMoreInteractions(welfareServiceRepository);
    }

    @Test
    @DisplayName("기본 ACTIVE_ONLY 인기순 목록은 row fast path와 exact count를 사용한다")
    void findListUsesViewsFastPathForDefaultActiveOnlyList() {
        WelfareServiceReadRepositoryImpl repository = newRepository();
        Pageable pageable = PageRequest.of(0, 20);
        given(welfareServiceRepository.findActiveOnlyViewsListRows(any(Pageable.class)))
                .willReturn(List.of());
        given(welfareServiceRepository.countActiveOnlyVisibleList()).willReturn(123L);

        Page<WelfareService> result = repository.findList(defaultActiveOnlyCondition("VIEWS"), pageable);

        assertThat(result.getTotalElements()).isEqualTo(123L);
        verify(welfareServiceRepository).findActiveOnlyViewsListRows(eq(pageable));
        verify(welfareServiceRepository).countActiveOnlyVisibleList();
        verifyNoMoreInteractions(welfareServiceRepository);
    }

    @Test
    @DisplayName("기본 ACTIVE_ONLY 목록 count는 Redis cache hit 시 DB count를 호출하지 않는다")
    void findListUsesRedisCachedActiveOnlyCount() {
        RedisTemplate<String, String> redisTemplate = org.mockito.Mockito.mock(RedisTemplate.class);
        ValueOperations<String, String> valueOperations = org.mockito.Mockito.mock(ValueOperations.class);
        WelfareServiceReadRepositoryImpl repository = new WelfareServiceReadRepositoryImpl(
                welfareServiceRepository,
                welfareServiceSearchRepository,
                redisTemplate,
                Duration.ofSeconds(30)
        );
        Pageable pageable = PageRequest.of(0, 20);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("policy:list:active-only:count:v1")).willReturn("456");
        given(welfareServiceRepository.findActiveOnlyLatestListRows(any(Pageable.class)))
                .willReturn(List.of());

        Page<WelfareService> result = repository.findList(defaultActiveOnlyCondition("LATEST"), pageable);

        assertThat(result.getTotalElements()).isEqualTo(456L);
        verify(valueOperations).get("policy:list:active-only:count:v1");
        verify(welfareServiceRepository).findActiveOnlyLatestListRows(eq(pageable));
        verify(welfareServiceRepository, never()).countActiveOnlyVisibleList();
        verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("기본 ACTIVE_ONLY 목록 count cache miss는 exact count를 Redis에 TTL과 함께 저장한다")
    void findListWritesActiveOnlyCountOnRedisMiss() {
        RedisTemplate<String, String> redisTemplate = org.mockito.Mockito.mock(RedisTemplate.class);
        ValueOperations<String, String> valueOperations = org.mockito.Mockito.mock(ValueOperations.class);
        WelfareServiceReadRepositoryImpl repository = new WelfareServiceReadRepositoryImpl(
                welfareServiceRepository,
                welfareServiceSearchRepository,
                redisTemplate,
                Duration.ofSeconds(45)
        );
        Pageable pageable = PageRequest.of(0, 20);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("policy:list:active-only:count:v1")).willReturn(null);
        given(welfareServiceRepository.findActiveOnlyLatestListRows(any(Pageable.class)))
                .willReturn(List.of());
        given(welfareServiceRepository.countActiveOnlyVisibleList()).willReturn(789L);

        Page<WelfareService> result = repository.findList(defaultActiveOnlyCondition("LATEST"), pageable);

        assertThat(result.getTotalElements()).isEqualTo(789L);
        verify(welfareServiceRepository).countActiveOnlyVisibleList();
        verify(valueOperations).set("policy:list:active-only:count:v1", "789", Duration.ofSeconds(45));
    }

    @Test
    @DisplayName("필터가 있으면 기존 통합 목록 쿼리를 유지한다")
    void findListFallsBackToGeneralQueryWhenFilterIsPresent() {
        WelfareServiceReadRepositoryImpl repository = newRepository();
        Pageable pageable = PageRequest.of(0, 20);
        given(welfareServiceRepository.findListWithFilters(
                eq("주거"),
                eq(null),
                eq(null),
                eq("ACTIVE_ONLY"),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq("LATEST"),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                any(Pageable.class)
        )).willReturn(Page.empty());

        repository.findList(new PolicyListReadCondition(
                "주거",
                null,
                null,
                "ACTIVE_ONLY",
                null,
                null,
                null,
                "LATEST",
                null,
                null,
                null,
                null,
                null
        ), pageable);

        verify(welfareServiceRepository).findListWithFilters(
                eq("주거"),
                eq(null),
                eq(null),
                eq("ACTIVE_ONLY"),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq("LATEST"),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq(pageable)
        );
        verifyNoMoreInteractions(welfareServiceRepository);
    }

    private WelfareServiceReadRepositoryImpl newRepository() {
        return new WelfareServiceReadRepositoryImpl(welfareServiceRepository, welfareServiceSearchRepository);
    }

    private PolicyListReadCondition defaultActiveOnlyCondition(String sort) {
        return new PolicyListReadCondition(
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
        );
    }

    private WelfareService welfareService(Long id) {
        return WelfareService.builder()
                .id(id)
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId("SRC-" + id)
                .title("청년 정책 " + id)
                .status(WelfareService.ServiceStatus.ACTIVE)
                .build();
    }
}
