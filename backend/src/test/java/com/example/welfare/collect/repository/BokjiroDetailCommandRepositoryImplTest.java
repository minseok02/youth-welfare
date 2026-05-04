package com.example.welfare.collect.repository;

import com.example.welfare.policy.entity.WelfareServiceDetail;
import com.example.welfare.policy.repository.WelfareServiceDetailRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class BokjiroDetailCommandRepositoryImplTest {

    @Mock
    private WelfareServiceDetailRepository welfareServiceDetailRepository;

    @InjectMocks
    private BokjiroDetailCommandRepositoryImpl bokjiroDetailCommandRepository;

    @Test
    @DisplayName("bokjiro detail command repository는 detail 저장을 위임한다")
    void saveDelegates() {
        WelfareServiceDetail detail = WelfareServiceDetail.builder().id(101L).build();
        given(welfareServiceDetailRepository.save(detail)).willReturn(detail);

        assertThat(bokjiroDetailCommandRepository.save(detail)).isSameAs(detail);
    }
}
