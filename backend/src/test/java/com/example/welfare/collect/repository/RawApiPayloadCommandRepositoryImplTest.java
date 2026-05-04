package com.example.welfare.collect.repository;

import com.example.welfare.collect.entity.RawApiPayload;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class RawApiPayloadCommandRepositoryImplTest {

    @Mock
    private RawApiPayloadRepository rawApiPayloadRepository;

    @InjectMocks
    private RawApiPayloadCommandRepositoryImpl rawApiPayloadCommandRepository;

    @Test
    @DisplayName("raw api payload command repository는 저장을 위임한다")
    void saveDelegates() {
        RawApiPayload raw = RawApiPayload.builder().sourceId("SRC-1").build();
        given(rawApiPayloadRepository.save(raw)).willReturn(raw);

        assertThat(rawApiPayloadCommandRepository.save(raw)).isEqualTo(raw);
    }
}
