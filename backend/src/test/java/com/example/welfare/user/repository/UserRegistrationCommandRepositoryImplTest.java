package com.example.welfare.user.repository;

import com.example.welfare.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class UserRegistrationCommandRepositoryImplTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserRegistrationCommandRepositoryImpl userRegistrationCommandRepository;

    @Test
    @DisplayName("user registration command repository는 신규 사용자 저장을 위임한다")
    void saveDelegates() {
        User user = User.builder().email("user@example.com").build();

        userRegistrationCommandRepository.save(user);

        then(userRepository).should().save(user);
    }
}
