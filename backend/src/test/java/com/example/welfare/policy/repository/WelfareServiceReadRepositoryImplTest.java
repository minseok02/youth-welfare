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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class WelfareServiceReadRepositoryImplTest {

    @Mock
    private WelfareServiceRepository welfareServiceRepository;
    @Mock
    private WelfareServiceSearchRepository welfareServiceSearchRepository;

    @Test
    @DisplayName("기본 ACTIVE_ONLY 최신순 목록은 전용 fast path 쿼리를 사용한다")
    void findListUsesLatestFastPathForDefaultActiveOnlyList() {
        WelfareServiceReadRepositoryImpl repository = newRepository();
        Pageable pageable = PageRequest.of(0, 20);
        given(welfareServiceRepository.findActiveOnlyLatestList(any(Pageable.class)))
                .willReturn(Page.empty());

        repository.findList(defaultActiveOnlyCondition("LATEST"), pageable);

        verify(welfareServiceRepository).findActiveOnlyLatestList(eq(pageable));
        verifyNoMoreInteractions(welfareServiceRepository);
    }

    @Test
    @DisplayName("기본 ACTIVE_ONLY 마감순 목록은 전용 fast path 쿼리를 사용한다")
    void findListUsesDeadlineFastPathForDefaultActiveOnlyList() {
        WelfareServiceReadRepositoryImpl repository = newRepository();
        Pageable pageable = PageRequest.of(0, 20);
        given(welfareServiceRepository.findActiveOnlyDeadlineList(any(Pageable.class)))
                .willReturn(Page.empty());

        repository.findList(defaultActiveOnlyCondition("DEADLINE"), pageable);

        verify(welfareServiceRepository).findActiveOnlyDeadlineList(eq(pageable));
        verifyNoMoreInteractions(welfareServiceRepository);
    }

    @Test
    @DisplayName("기본 ACTIVE_ONLY 인기순 목록은 전용 fast path 쿼리를 사용한다")
    void findListUsesViewsFastPathForDefaultActiveOnlyList() {
        WelfareServiceReadRepositoryImpl repository = newRepository();
        Pageable pageable = PageRequest.of(0, 20);
        given(welfareServiceRepository.findActiveOnlyViewsList(any(Pageable.class)))
                .willReturn(Page.empty());

        repository.findList(defaultActiveOnlyCondition("VIEWS"), pageable);

        verify(welfareServiceRepository).findActiveOnlyViewsList(eq(pageable));
        verifyNoMoreInteractions(welfareServiceRepository);
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
}
