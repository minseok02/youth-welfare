package com.example.welfare.user.repository;

import com.example.welfare.user.entity.User;
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
class UserAccountReadRepositoryImplTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserAccountReadRepositoryImpl userAccountReadRepository;

    @Test
    @DisplayName("user account read repository는 id 조회를 위임한다")
    void findByIdDelegates() {
        User user = User.builder().id(7L).userKey("user-key-7").build();
        given(userRepository.findById(7L)).willReturn(Optional.of(user));

        assertThat(userAccountReadRepository.findById(7L)).contains(user);
    }

    @Test
    @DisplayName("user account read repository는 userKey 조회를 위임한다")
    void findByUserKeyDelegates() {
        User user = User.builder().id(7L).userKey("user-key-7").build();
        given(userRepository.findByUserKey("user-key-7")).willReturn(Optional.of(user));

        assertThat(userAccountReadRepository.findByUserKey("user-key-7")).contains(user);
    }
}
