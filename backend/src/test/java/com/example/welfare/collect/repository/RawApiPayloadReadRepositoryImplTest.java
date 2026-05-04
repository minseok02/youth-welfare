package com.example.welfare.collect.repository;

import com.example.welfare.collect.entity.RawApiPayload;
import com.example.welfare.policy.entity.WelfareService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class RawApiPayloadReadRepositoryImplTest {

    @Mock
    private RawApiPayloadRepository rawApiPayloadRepository;

    @InjectMocks
    private RawApiPayloadReadRepositoryImpl rawApiPayloadReadRepository;

    @Test
    @DisplayName("raw api payload read repository는 단건 조회를 위임한다")
    void findBySourceTypeAndSourceIdAndApiCategoryDelegates() {
        RawApiPayload raw = RawApiPayload.builder().sourceId("SRC-1").build();
        given(rawApiPayloadRepository.findBySourceTypeAndSourceIdAndApiCategory(
                WelfareService.SourceType.YOUTH,
                "SRC-1",
                RawApiPayload.ApiCategory.LIST
        )).willReturn(Optional.of(raw));

        assertThat(rawApiPayloadReadRepository.findBySourceTypeAndSourceIdAndApiCategory(
                WelfareService.SourceType.YOUTH,
                "SRC-1",
                RawApiPayload.ApiCategory.LIST
        )).contains(raw);
    }

    @Test
    @DisplayName("raw api payload read repository는 목록 조회를 위임한다")
    void findAllBySourceTypeAndApiCategoryOrderByFetchedAtAscDelegates() {
        RawApiPayload raw = RawApiPayload.builder().sourceId("SRC-1").build();
        given(rawApiPayloadRepository.findAllBySourceTypeAndApiCategoryOrderByFetchedAtAsc(
                WelfareService.SourceType.YOUTH,
                RawApiPayload.ApiCategory.LIST
        )).willReturn(List.of(raw));

        assertThat(rawApiPayloadReadRepository.findAllBySourceTypeAndApiCategoryOrderByFetchedAtAsc(
                WelfareService.SourceType.YOUTH,
                RawApiPayload.ApiCategory.LIST
        )).containsExactly(raw);
    }
}
