package com.example.welfare.policy.service;

import com.example.welfare.policy.entity.ServiceRegion;
import com.example.welfare.policy.entity.ServiceTag;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.entity.WelfareServiceDetail;
import com.example.welfare.policy.repository.PolicyDetailReadRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PolicyDetailReadServiceTest {

    @Mock
    private PolicyDetailReadRepository policyDetailReadRepository;

    @InjectMocks
    private PolicyDetailReadService policyDetailReadService;

    @Test
    @DisplayName("상세 aggregate 조회는 read repository 경계로 위임한다")
    void getAggregateDelegatesToReadRepository() {
        WelfareService service = WelfareService.builder()
                .id(11L)
                .sourceType(WelfareService.SourceType.YOUTH)
                .sourceId("SRC-11")
                .title("청년 월세 지원")
                .status(WelfareService.ServiceStatus.ACTIVE)
                .build();
        WelfareServiceDetail detail = WelfareServiceDetail.builder()
                .service(service)
                .targetDetail("청년")
                .build();
        ServiceRegion region = ServiceRegion.builder()
                .service(service)
                .sidoName("서울특별시")
                .build();
        ServiceTag tag = ServiceTag.builder()
                .service(service)
                .tagType(ServiceTag.TagType.KEYWORD)
                .tagValue("주거")
                .build();
        PolicyDetailReadService.PolicyDetailAggregate aggregate =
                new PolicyDetailReadService.PolicyDetailAggregate(detail, List.of(region), List.of(tag));

        given(policyDetailReadRepository.findAggregate(11L)).willReturn(aggregate);

        PolicyDetailReadService.PolicyDetailAggregate result = policyDetailReadService.getAggregate(11L);

        assertThat(result).isSameAs(aggregate);
        verify(policyDetailReadRepository).findAggregate(11L);
    }
}
