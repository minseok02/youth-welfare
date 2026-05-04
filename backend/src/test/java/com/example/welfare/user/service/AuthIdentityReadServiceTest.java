package com.example.welfare.user.service;

import com.example.welfare.global.exception.CustomException;
import com.example.welfare.global.exception.ErrorCode;
import com.example.welfare.user.entity.AuthUser;
import com.example.welfare.user.repository.AuthUserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

    @Test
    @DisplayName("userKey 조회는 auth user를 그대로 위임한다")
    void findByUserKeyDelegates() {
        AuthUser authUser = AuthUser.builder().userKey("user-key-1").build();
        when(authUserRepository.findByUserKey("user-key-1")).thenReturn(Optional.of(authUser));

        assertThat(authIdentityReadService.findByUserKey("user-key-1")).contains(authUser);
    }

    @Test
    @DisplayName("active userKey 확인은 auth user active 상태를 검증한다")
    void requireActiveUserKeyValidatesState() {
        when(authUserRepository.findByUserKey("user-key-1"))
                .thenReturn(Optional.of(AuthUser.builder().userKey("user-key-1").isActive(true).build()));
        when(authUserRepository.findByUserKey("user-key-2"))
                .thenReturn(Optional.of(AuthUser.builder().userKey("user-key-2").isActive(false).build()));

        assertThat(authIdentityReadService.requireActiveUserKey("user-key-1")).isEqualTo("user-key-1");
        assertThatThrownBy(() -> authIdentityReadService.requireActiveUserKey("user-key-2"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.WITHDRAWN_USER);
        assertThatThrownBy(() -> authIdentityReadService.requireActiveUserKey("missing"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }
}
