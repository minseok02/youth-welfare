package com.example.welfare.collect.service;

import com.example.welfare.collect.dto.BokjiroCentralDto;
import com.example.welfare.collect.entity.RawApiPayload;
import com.example.welfare.collect.repository.RawApiPayloadRepository;
import com.example.welfare.policy.entity.WelfareService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RawApiPayloadServiceTest {

    @Mock
    private RawApiPayloadRepository rawApiPayloadRepository;

    private RawApiPayloadService rawApiPayloadService;

    @BeforeEach
    void setUp() {
        rawApiPayloadService = new RawApiPayloadService(rawApiPayloadRepository, new ObjectMapper());
    }

    @Test
    void saveBokjiroCentralListCreatesOrUpdatesRawPayload() {
        BokjiroCentralDto.Item item = new BokjiroCentralDto.Item();
        ReflectionTestUtils.setField(item, "servId", "WLF00000060");
        ReflectionTestUtils.setField(item, "servNm", "청년내일저축계좌");

        given(rawApiPayloadRepository.findBySourceTypeAndSourceIdAndApiCategory(
                WelfareService.SourceType.BOKJIRO_CENTRAL,
                "WLF00000060",
                RawApiPayload.ApiCategory.LIST
        )).willReturn(Optional.empty());

        rawApiPayloadService.saveBokjiroCentralList(item);

        verify(rawApiPayloadRepository).save(any(RawApiPayload.class));
    }

    @Test
    void saveSkipsWhenSourceIdMissing() {
        BokjiroCentralDto.Item item = new BokjiroCentralDto.Item();
        ReflectionTestUtils.setField(item, "servNm", "이름만 있는 정책");

        rawApiPayloadService.saveBokjiroCentralList(item);

        verifyNoInteractions(rawApiPayloadRepository);
    }
}
