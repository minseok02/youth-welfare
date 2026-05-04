package com.example.welfare.collect.service;

import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.WelfareServiceRepository;
import com.example.welfare.recommend.repository.ClusterAiResultCommandRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class StatusUpdateServiceTest {

    @Mock
    private WelfareServiceRepository welfareServiceRepository;

    @Mock
    private ClusterAiResultCommandRepository clusterAiResultCommandRepository;

    @InjectMocks
    private StatusUpdateService statusUpdateService;

    @Test
    @DisplayName("status update는 상태 갱신 후 만료된 cluster ai cache 삭제를 command repository로 위임한다")
    void updateStatusesDeletesExpiredClusterAiCache() {
        given(welfareServiceRepository.findByStatus(WelfareService.ServiceStatus.ACTIVE)).willReturn(List.of());
        given(welfareServiceRepository.findByStatus(WelfareService.ServiceStatus.UPCOMING)).willReturn(List.of());

        statusUpdateService.updateStatuses();

        then(clusterAiResultCommandRepository).should().deleteExpiredBefore(any(LocalDateTime.class));
    }
}
