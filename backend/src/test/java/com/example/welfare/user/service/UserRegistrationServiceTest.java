package com.example.welfare.user.service;

import com.example.welfare.user.dto.request.SignupRequest;
import com.example.welfare.user.entity.User;
import com.example.welfare.user.repository.UserRegistrationCommandRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserRegistrationServiceTest {

    @Mock
    private UserRegistrationCommandRepository userRegistrationCommandRepository;
    @Mock
    private UserCoreSyncService userCoreSyncService;

    @InjectMocks
    private UserRegistrationService userRegistrationService;

    @Test
    @DisplayName("회원가입 저장은 user 생성 후 core sync까지 위임한다")
    void registerSavesAndSyncsUser() {
        SignupRequest request = new SignupRequest();
        ReflectionTestUtils.setField(request, "email", "user@example.com");
        ReflectionTestUtils.setField(request, "name", "홍길동");

        userRegistrationService.register(request, "encoded-password");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRegistrationCommandRepository).save(userCaptor.capture());
        verify(userCoreSyncService).syncFromUser(userCaptor.getValue());
        assertThat(userCaptor.getValue().getEmail()).isEqualTo("user@example.com");
        assertThat(userCaptor.getValue().getPasswordHash()).isEqualTo("encoded-password");
        assertThat(userCaptor.getValue().getName()).isEqualTo("홍길동");
    }
}
