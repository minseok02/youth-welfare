package com.example.welfare.user.service;

import com.example.welfare.global.util.AesEncryptUtil;
import com.example.welfare.user.entity.AuthUser;
import com.example.welfare.user.entity.User;
import com.example.welfare.user.entity.UserProfile;
import com.example.welfare.user.event.UserPiiSyncRequestedEvent;
import com.example.welfare.user.repository.AuthUserRepository;
import com.example.welfare.user.repository.UserProfileRepository;
import com.example.welfare.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class UserCoreSyncServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuthUserRepository authUserRepository;

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private UserPiiSyncQueueService userPiiSyncQueueService;

    @Mock
    private AesEncryptUtil aesEncryptUtil;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @Test
    @DisplayName("core sync는 auth/profile 저장 후 pii sync queue와 after-commit 이벤트를 적재한다")
    void syncFromUserEnqueuesPiiSync() {
        User user = User.builder()
                .id(1L)
                .email("user@example.com")
                .passwordHash("pw-hash")
                .name("Queue User")
                .birthDate(LocalDate.of(1998, 1, 10))
                .phoneEnc("enc-phone")
                .sido("서울특별시")
                .sgg("강남구")
                .incomeLevel((byte) 5)
                .employmentStatus("EMPLOYED")
                .householdType("SINGLE")
                .build();

        given(userRepository.findUserKeyById(1L)).willReturn(Optional.of("user-key-1"));
        given(authUserRepository.findByUserKey("user-key-1")).willReturn(Optional.empty());
        given(userProfileRepository.findByUserKey("user-key-1")).willReturn(Optional.empty());
        given(aesEncryptUtil.encrypt("user@example.com")).willReturn("enc-email");
        given(aesEncryptUtil.encrypt("Queue User")).willReturn("enc-name");
        given(aesEncryptUtil.encrypt("1998-01-10")).willReturn("enc-birth");

        UserCoreSyncService service = new UserCoreSyncService(
                userRepository,
                authUserRepository,
                userProfileRepository,
                userPiiSyncQueueService,
                aesEncryptUtil,
                applicationEventPublisher
        );

        service.syncFromUser(user);

        ArgumentCaptor<AuthUser> authUserCaptor = ArgumentCaptor.forClass(AuthUser.class);
        then(authUserRepository).should().save(authUserCaptor.capture());
        assertThat(authUserCaptor.getValue().getUserKey()).isEqualTo("user-key-1");
        assertThat(authUserCaptor.getValue().getPasswordHash()).isEqualTo("pw-hash");

        ArgumentCaptor<UserProfile> userProfileCaptor = ArgumentCaptor.forClass(UserProfile.class);
        then(userProfileRepository).should().save(userProfileCaptor.capture());
        assertThat(userProfileCaptor.getValue().getUserKey()).isEqualTo("user-key-1");
        assertThat(userProfileCaptor.getValue().getSido()).isEqualTo("서울특별시");
        assertThat(userProfileCaptor.getValue().isHasName()).isTrue();
        assertThat(userProfileCaptor.getValue().isHasBirthDate()).isTrue();
        assertThat(userProfileCaptor.getValue().isHasPhone()).isTrue();

        then(userPiiSyncQueueService).should()
                .enqueue("user-key-1", "enc-email", "enc-name", "enc-birth", "enc-phone");
        then(applicationEventPublisher).should().publishEvent(new UserPiiSyncRequestedEvent("user-key-1"));
    }
}
