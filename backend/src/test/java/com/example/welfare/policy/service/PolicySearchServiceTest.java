package com.example.welfare.policy.service;

import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.WelfareServiceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PolicySearchServiceTest {

    @Mock
    private WelfareServiceRepository welfareServiceRepository;

    @InjectMocks
    private PolicySearchService policySearchService;

    @Test
    @DisplayName("검색은 지역 필터와 NAME 정렬을 정규화해 저장소에 전달한다")
    void searchNormalizesRegionAndNameSort() {
        WelfareService service = WelfareService.builder()
                .id(11L)
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId("SRC-11")
                .title("청년 월세 지원")
                .status(WelfareService.ServiceStatus.ACTIVE)
                .build();

        given(welfareServiceRepository.searchByKeywordWithFilters(
                eq("+청년 +월세"),
                eq("ACTIVE"),
                eq("HOUSING"),
                eq("YOUTH"),
                eq(1),
                eq("서울특별시"),
                eq("강남구"),
                eq("NAME"),
                eq(20),
                eq(20)
        )).willReturn(List.of(service));

        var result = policySearchService.search(
                "청년 월세",
                "ACTIVE",
                "HOUSING",
                "YOUTH",
                true,
                "서울특별시",
                "강남구",
                "name",
                1,
                20
        );

        assertEquals(1, result.size());
        assertEquals(11L, result.get(0).getId());
        verify(welfareServiceRepository).searchByKeywordWithFilters(
                "+청년 +월세",
                "ACTIVE",
                "HOUSING",
                "YOUTH",
                1,
                "서울특별시",
                "강남구",
                "NAME",
                20,
                20
        );
    }
}
