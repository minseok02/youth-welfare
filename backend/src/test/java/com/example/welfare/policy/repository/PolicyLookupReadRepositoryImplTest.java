package com.example.welfare.policy.repository;

import com.example.welfare.policy.entity.WelfareService;
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
class PolicyLookupReadRepositoryImplTest {

    @Mock
    private WelfareServiceRepository welfareServiceRepository;

    @InjectMocks
    private PolicyLookupReadRepositoryImpl policyLookupReadRepository;

    @Test
    @DisplayName("policy lookup read repository는 정책 엔티티 조회를 위임한다")
    void findByIdDelegates() {
        WelfareService service = WelfareService.builder()
                .id(11L)
                .title("청년 정책")
                .build();
        given(welfareServiceRepository.findById(11L)).willReturn(Optional.of(service));

        assertThat(policyLookupReadRepository.findById(11L)).contains(service);
    }
}
