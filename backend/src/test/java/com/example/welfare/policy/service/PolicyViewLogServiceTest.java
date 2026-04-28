package com.example.welfare.policy.service;

import com.example.welfare.policy.entity.WelfareService;
import com.example.welfare.policy.repository.ServiceViewLogRepository;
import com.example.welfare.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PolicyViewLogServiceTest {

    @Mock
    private ServiceViewLogRepository serviceViewLogRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private PolicyViewLogService policyViewLogService;

    @Test
    @DisplayName("같은 사용자의 24시간 내 중복 조회는 카운트하지 않는다")
    void duplicateUserViewInWindow() {
        given(userRepository.findUserKeyById(1L)).willReturn(java.util.Optional.of("user-key-1"));
        given(serviceViewLogRepository.existsByServiceIdAndUserKeyAndViewedAtAfter(eq(10L), eq("user-key-1"), any()))
                .willReturn(true);

        boolean increase = policyViewLogService.registerViewIfFirstInWindow(10L, 1L, "fp");

        assertFalse(increase);
        verify(serviceViewLogRepository, never()).save(any());
    }

    @Test
    @DisplayName("중복이 아니면 조회 로그를 남기고 카운트한다")
    void firstViewInWindow() {
        given(userRepository.findUserKeyById(1L)).willReturn(java.util.Optional.of("user-key-1"));
        given(serviceViewLogRepository.existsByServiceIdAndUserKeyAndViewedAtAfter(eq(10L), eq("user-key-1"), any()))
                .willReturn(false);
        given(entityManager.getReference(eq(WelfareService.class), eq(10L)))
                .willReturn(WelfareService.builder().id(10L).build());

        boolean increase = policyViewLogService.registerViewIfFirstInWindow(10L, 1L, "fp");

        assertTrue(increase);
        verify(serviceViewLogRepository).save(any());
    }

    @Test
    @DisplayName("비로그인 식별자도 24시간 내 중복이면 카운트하지 않는다")
    void duplicateAnonymousViewInWindow() {
        given(serviceViewLogRepository.existsByServiceIdAndClientFingerprintAndViewedAtAfter(eq(10L), eq("fp"), any()))
                .willReturn(true);

        boolean increase = policyViewLogService.registerViewIfFirstInWindow(10L, null, "fp");

        assertFalse(increase);
        verify(serviceViewLogRepository, never()).save(any());
    }
}
