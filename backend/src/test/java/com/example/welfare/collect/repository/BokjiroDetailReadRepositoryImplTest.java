package com.example.welfare.collect.repository;

import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.entity.WelfareServiceDetail;
import com.example.welfare.policy.repository.WelfareServiceDetailRepository;
import com.example.welfare.policy.repository.WelfareServiceRepository;
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
class BokjiroDetailReadRepositoryImplTest {

    @Mock
    private WelfareServiceRepository welfareServiceRepository;

    @Mock
    private WelfareServiceDetailRepository welfareServiceDetailRepository;

    @InjectMocks
    private BokjiroDetailReadRepositoryImpl bokjiroDetailReadRepository;

    @Test
    @DisplayName("bokjiro detail read repository는 source별 대상 정책 목록 조회를 위임한다")
    void findTargetsBySourceTypeDelegates() {
        WelfareService service = WelfareService.builder().title("policy").build();
        given(welfareServiceRepository.findBySourceType(WelfareService.SourceType.BOKJIRO_LOCAL))
                .willReturn(List.of(service));

        assertThat(bokjiroDetailReadRepository.findTargetsBySourceType(WelfareService.SourceType.BOKJIRO_LOCAL))
                .containsExactly(service);
    }

    @Test
    @DisplayName("bokjiro detail read repository는 existing detail 존재 확인을 위임한다")
    void existsDetailByServiceIdDelegates() {
        given(welfareServiceDetailRepository.existsByServiceId(10L)).willReturn(true);

        assertThat(bokjiroDetailReadRepository.existsDetailByServiceId(10L)).isTrue();
    }

    @Test
    @DisplayName("bokjiro detail read repository는 existing detail row 조회를 위임한다")
    void findDetailByServiceIdDelegates() {
        WelfareServiceDetail detail = WelfareServiceDetail.builder().id(101L).build();
        given(welfareServiceDetailRepository.findByServiceId(10L)).willReturn(Optional.of(detail));

        assertThat(bokjiroDetailReadRepository.findDetailByServiceId(10L)).contains(detail);
    }
}
