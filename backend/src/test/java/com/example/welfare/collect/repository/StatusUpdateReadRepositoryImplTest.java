package com.example.welfare.collect.repository;

import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.WelfareServiceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class StatusUpdateReadRepositoryImplTest {

    @Mock
    private WelfareServiceRepository welfareServiceRepository;

    @InjectMocks
    private StatusUpdateReadRepositoryImpl statusUpdateReadRepository;

    @Test
    @DisplayName("status update read repository는 active 정책 조회를 위임한다")
    void findActiveServicesDelegates() {
        WelfareService service = WelfareService.builder().title("active").build();
        given(welfareServiceRepository.findByStatus(WelfareService.ServiceStatus.ACTIVE))
                .willReturn(List.of(service));

        assertThat(statusUpdateReadRepository.findActiveServices()).containsExactly(service);
    }

    @Test
    @DisplayName("status update read repository는 upcoming 정책 조회를 위임한다")
    void findUpcomingServicesDelegates() {
        WelfareService service = WelfareService.builder().title("upcoming").build();
        given(welfareServiceRepository.findByStatus(WelfareService.ServiceStatus.UPCOMING))
                .willReturn(List.of(service));

        assertThat(statusUpdateReadRepository.findUpcomingServices()).containsExactly(service);
    }
}
