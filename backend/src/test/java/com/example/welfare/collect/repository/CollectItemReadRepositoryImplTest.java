package com.example.welfare.collect.repository;

import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.WelfareServiceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class CollectItemReadRepositoryImplTest {

    @Mock
    private WelfareServiceRepository welfareServiceRepository;

    @InjectMocks
    private CollectItemReadRepositoryImpl collectItemReadRepository;

    @Test
    @DisplayName("collect item read repository는 sourceType/sourceId 기준 정책 조회를 위임한다")
    void findServiceBySourceTypeAndSourceIdDelegates() {
        WelfareService service = WelfareService.builder().id(1L).sourceId("Y-1").build();
        given(welfareServiceRepository.findBySourceTypeAndSourceId(WelfareService.SourceType.YOUTH, "Y-1"))
                .willReturn(Optional.of(service));

        assertThat(collectItemReadRepository.findServiceBySourceTypeAndSourceId(
                WelfareService.SourceType.YOUTH,
                "Y-1"
        )).contains(service);
    }
}
