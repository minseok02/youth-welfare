package com.example.welfare.user.repository;

import com.example.welfare.user.entity.AuthUser;
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
class AuthIdentityReadRepositoryImplTest {

    @Mock
    private AuthUserRepository authUserRepository;

    @InjectMocks
    private AuthIdentityReadRepositoryImpl authIdentityReadRepository;

    @Test
    @DisplayName("auth identity read repository는 email lookup hash 존재 여부를 위임한다")
    void existsByEmailLookupHashDelegates() {
        given(authUserRepository.existsByEmailLookupHash("hash-1")).willReturn(true);

        assertThat(authIdentityReadRepository.existsByEmailLookupHash("hash-1")).isTrue();
    }

    @Test
    @DisplayName("auth identity read repository는 email lookup hash 조회를 위임한다")
    void findByEmailLookupHashDelegates() {
        AuthUser authUser = AuthUser.builder().userKey("user-key-1").build();
        given(authUserRepository.findByEmailLookupHash("hash-1")).willReturn(Optional.of(authUser));

        assertThat(authIdentityReadRepository.findByEmailLookupHash("hash-1")).contains(authUser);
    }

    @Test
    @DisplayName("auth identity read repository는 userKey 조회를 위임한다")
    void findByUserKeyDelegates() {
        AuthUser authUser = AuthUser.builder().userKey("user-key-1").build();
        given(authUserRepository.findByUserKey("user-key-1")).willReturn(Optional.of(authUser));

        assertThat(authIdentityReadRepository.findByUserKey("user-key-1")).contains(authUser);
    }
}
