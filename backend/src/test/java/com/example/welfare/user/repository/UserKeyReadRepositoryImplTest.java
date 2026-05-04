package com.example.welfare.user.repository;

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
class UserKeyReadRepositoryImplTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserKeyReadRepositoryImpl userKeyReadRepository;

    @Test
    @DisplayName("user key read repository는 user id -> userKey lookup을 위임한다")
    void findUserKeyByIdDelegates() {
        given(userRepository.findUserKeyById(7L)).willReturn(Optional.of("user-key-7"));

        assertThat(userKeyReadRepository.findUserKeyById(7L)).contains("user-key-7");
    }

    @Test
    @DisplayName("user key read repository는 userKey -> user id lookup을 위임한다")
    void findIdByUserKeyDelegates() {
        given(userRepository.findIdByUserKey("user-key-7")).willReturn(Optional.of(7L));

        assertThat(userKeyReadRepository.findIdByUserKey("user-key-7")).contains(7L);
    }
}
