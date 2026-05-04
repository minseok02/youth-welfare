package com.example.welfare.policy.repository;

import com.example.welfare.policy.entity.WelfareService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class PolicyViewLogCommandRepositoryImplTest {

    @Mock
    private ServiceViewLogRepository serviceViewLogRepository;
    @Mock
    private EntityManager entityManager;

    @Test
    @DisplayName("policy view log command repository는 로그인 dedup 조회를 위임한다")
    void existsDuplicateUserViewDelegates() {
        given(serviceViewLogRepository.existsByServiceIdAndUserKeyAndViewedAtAfter(eq(10L), eq("user-key-1"), any()))
                .willReturn(true);

        PolicyViewLogCommandRepositoryImpl repository = new PolicyViewLogCommandRepositoryImpl(
                serviceViewLogRepository,
                entityManager
        );

        assertThat(repository.existsDuplicateUserView(10L, "user-key-1", LocalDateTime.now())).isTrue();
    }

    @Test
    @DisplayName("policy view log command repository는 비로그인 dedup 조회를 위임한다")
    void existsDuplicateAnonymousViewDelegates() {
        given(serviceViewLogRepository.existsByServiceIdAndClientFingerprintAndViewedAtAfter(eq(10L), eq("fp"), any()))
                .willReturn(true);

        PolicyViewLogCommandRepositoryImpl repository = new PolicyViewLogCommandRepositoryImpl(
                serviceViewLogRepository,
                entityManager
        );

        assertThat(repository.existsDuplicateAnonymousView(10L, "fp", LocalDateTime.now())).isTrue();
    }

    @Test
    @DisplayName("policy view log command repository는 조회 로그 저장을 위임한다")
    void saveViewDelegates() {
        WelfareService serviceRef = WelfareService.builder().id(10L).build();
        given(entityManager.getReference(eq(WelfareService.class), eq(10L))).willReturn(serviceRef);

        PolicyViewLogCommandRepositoryImpl repository = new PolicyViewLogCommandRepositoryImpl(
                serviceViewLogRepository,
                entityManager
        );

        repository.saveView(10L, "user-key-1", "fp");

        then(serviceViewLogRepository).should().save(any());
    }
}
