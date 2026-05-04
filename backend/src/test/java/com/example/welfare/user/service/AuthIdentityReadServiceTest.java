package com.example.welfare.user.service;

import com.example.welfare.user.entity.AuthUser;
import com.example.welfare.user.repository.AuthUserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthIdentityReadServiceTest {

    @Mock
    private AuthUserRepository authUserRepository;

    @InjectMocks
    private AuthIdentityReadService authIdentityReadService;

    @Test
    @DisplayName("이메일 존재 확인은 normalized lookup hash로 위임한다")
    void existsByEmailUsesLookupHash() {
        when(authUserRepository.existsByEmailLookupHash("b4c9a289323b21a01c3e940f150eb9b8c542587f1abfd8f0e1cc1ffc5e475514"))
                .thenReturn(true);

        assertThat(authIdentityReadService.existsByEmail(" USER@example.com ")).isTrue();
    }

    @Test
    @DisplayName("이메일 조회는 normalized lookup hash로 auth user를 찾는다")
    void findByEmailUsesLookupHash() {
        AuthUser authUser = AuthUser.builder().userKey("user-key-1").build();
        when(authUserRepository.findByEmailLookupHash("b4c9a289323b21a01c3e940f150eb9b8c542587f1abfd8f0e1cc1ffc5e475514"))
                .thenReturn(Optional.of(authUser));

        assertThat(authIdentityReadService.findByEmail("USER@example.com"))
                .contains(authUser);
    }
}
