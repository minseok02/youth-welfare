package com.example.welfare.collect.repository;

import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.WelfareServiceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class CollectItemCommandRepositoryImplTest {

    @Mock
    private WelfareServiceRepository welfareServiceRepository;

    @InjectMocks
    private CollectItemCommandRepositoryImpl collectItemCommandRepository;

    @Test
    @DisplayName("collect item command repository는 신규 정책 저장을 위임한다")
    void saveAndFlushDelegates() {
        WelfareService service = WelfareService.builder().id(1L).sourceId("Y-1").build();
        given(welfareServiceRepository.saveAndFlush(service)).willReturn(service);

        assertThat(collectItemCommandRepository.saveAndFlush(service)).isSameAs(service);
    }
}
