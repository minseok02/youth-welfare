package com.example.welfare.policy.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.PolicyLookupReadRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class PolicyLookupServiceTest {

    @Mock
    private PolicyLookupReadRepository policyLookupReadRepository;

    @InjectMocks
    private PolicyLookupService policyLookupService;

    @Test
    @DisplayName("정책 lookup은 read repository 결과를 반환한다")
    void getRequiredServiceDelegates() {
        WelfareService service = WelfareService.builder()
                .id(11L)
                .title("청년 정책")
                .build();
        given(policyLookupReadRepository.findById(11L)).willReturn(Optional.of(service));

        assertThat(policyLookupService.getRequiredService(11L)).isEqualTo(service);
    }

    @Test
    @DisplayName("정책 lookup은 없으면 POLICY_NOT_FOUND를 던진다")
    void getRequiredServiceRejectsMissingPolicy() {
        given(policyLookupReadRepository.findById(11L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> policyLookupService.getRequiredService(11L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.POLICY_NOT_FOUND);
    }
}
