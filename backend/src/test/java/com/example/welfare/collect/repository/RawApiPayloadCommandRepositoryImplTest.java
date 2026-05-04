package com.example.welfare.collect.repository;

import com.example.welfare.collect.entity.RawApiPayload;
import com.example.welfare.policy.entity.WelfareService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class RawApiPayloadCommandRepositoryImplTest {

    @Mock
    private RawApiPayloadRepository rawApiPayloadRepository;
    @Mock
    private RawApiPayloadReadRepository rawApiPayloadReadRepository;

    @InjectMocks
    private RawApiPayloadCommandRepositoryImpl rawApiPayloadCommandRepository;

    @Test
    @DisplayName("raw api payload command repository는 저장을 위임한다")
    void saveDelegates() {
        RawApiPayload raw = RawApiPayload.builder().sourceId("SRC-1").build();
        given(rawApiPayloadRepository.save(raw)).willReturn(raw);

        assertThat(rawApiPayloadCommandRepository.save(raw)).isEqualTo(raw);
    }

    @Test
    @DisplayName("raw api payload command repository는 기존 row를 찾아 payload upsert를 수행한다")
    void upsertDelegatesWithExistingRow() {
        LocalDateTime fetchedAt = LocalDateTime.now();
        RawApiPayload raw = RawApiPayload.builder()
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId("SRC-1")
                .apiCategory(RawApiPayload.ApiCategory.LIST)
                .payloadJson("{\"a\":1}")
                .payloadHash("hash-1")
                .fetchedAt(fetchedAt.minusMinutes(1))
                .build();
        given(rawApiPayloadReadRepository.findBySourceTypeAndSourceIdAndApiCategory(
                WelfareService.SourceType.YOUTH,
                "SRC-1",
                RawApiPayload.ApiCategory.LIST
        )).willReturn(Optional.of(raw));
        given(rawApiPayloadRepository.save(raw)).willReturn(raw);

        assertThat(rawApiPayloadCommandRepository.upsert(
                WelfareService.SourceType.YOUTH,
                "SRC-1",
                RawApiPayload.ApiCategory.LIST,
                "{\"a\":2}",
                "hash-2",
                fetchedAt
        )).isEqualTo(raw);
        then(rawApiPayloadRepository).should().save(raw);
    }
}
