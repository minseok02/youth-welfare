package com.example.welfare.collect.repository;

import com.example.welfare.policy.entity.ServiceTag;
import com.example.welfare.policy.repository.ServiceTagRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class CollectItemTagCommandRepositoryImplTest {

    @Mock
    private ServiceTagRepository serviceTagRepository;

    @InjectMocks
    private CollectItemTagCommandRepositoryImpl collectItemTagCommandRepository;

    @Test
    @DisplayName("collect item tag command repository는 전체 tag 교체를 위임한다")
    void replaceAllDelegates() {
        ServiceTag tag = ServiceTag.builder().tagValue("청년").build();

        collectItemTagCommandRepository.replaceAll(11L, List.of(tag));

        then(serviceTagRepository).should().deleteByServiceId(11L);
        then(serviceTagRepository).should().flush();
        then(serviceTagRepository).should().saveAll(List.of(tag));
    }

    @Test
    @DisplayName("collect item tag command repository는 빈 tag 집합이면 saveAll 을 생략한다")
    void replaceAllSkipsSaveAllWhenEmpty() {
        collectItemTagCommandRepository.replaceAll(12L, List.of());

        then(serviceTagRepository).should().deleteByServiceId(12L);
        then(serviceTagRepository).should().flush();
        then(serviceTagRepository).shouldHaveNoMoreInteractions();
    }
}
