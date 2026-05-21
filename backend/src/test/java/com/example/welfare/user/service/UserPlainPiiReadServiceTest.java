package com.example.welfare.user.service;

import com.example.welfare.global.util.AesEncryptUtil;
import com.example.welfare.user.entity.User;
import com.example.welfare.user.repository.UserPiiReadModel;
import com.example.welfare.user.repository.UserPiiReadWriteRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserPlainPiiReadServiceTest {

    @Mock private UserPiiReadWriteRepository userPiiReadWriteRepository;
    @Mock private AesEncryptUtil aesEncryptUtil;

    @Test
    @DisplayName("app_pii 암호문이 있으면 users 평문보다 암호문 값을 우선한다")
    void resolveCurrentFallsBackToEncryptedPii() {
        User user = User.builder()
                .id(1L)
                .email("legacy@example.com")
                .passwordHash("hash")
                .name("레거시이름")
                .birthDate(LocalDate.of(1999, 1, 1))
                .build();
        when(userPiiReadWriteRepository.findByUserKey("user-key-1"))
                .thenReturn(Optional.of(new UserPiiReadModel(
                        "user-key-1",
                        "enc-email",
                        "enc-name",
                        "enc-birth",
                        null
                )));
        when(aesEncryptUtil.decrypt("enc-email")).thenReturn("user@example.com");
        when(aesEncryptUtil.decrypt("enc-name")).thenReturn("홍길동");
        when(aesEncryptUtil.decrypt("enc-birth")).thenReturn("2000-01-10");

        UserPlainPiiReadService service = new UserPlainPiiReadService(userPiiReadWriteRepository, aesEncryptUtil);

        UserPlainPii pii = service.resolveCurrent(user, "user-key-1");

        assertThat(pii.email()).isEqualTo("user@example.com");
        assertThat(pii.name()).isEqualTo("홍길동");
        assertThat(pii.birthDate()).isEqualTo(LocalDate.of(2000, 1, 10));
    }

    @Test
    @DisplayName("app_pii 암호문이 비어 있으면 users 평문 이메일을 fallback으로 사용한다")
    void resolveCurrentFallsBackToUserPlainEmailWhenEncryptedEmailMissing() {
        User user = User.builder()
                .id(1L)
                .email("user@example.com")
                .passwordHash("hash")
                .build();
        when(userPiiReadWriteRepository.findByUserKey("user-key-1"))
                .thenReturn(Optional.of(new UserPiiReadModel(
                        "user-key-1",
                        null,
                        null,
                        null,
                        null
                )));

        UserPlainPiiReadService service = new UserPlainPiiReadService(userPiiReadWriteRepository, aesEncryptUtil);

        UserPlainPii pii = service.resolveCurrent(user, "user-key-1");

        assertThat(pii.email()).isEqualTo("user@example.com");
        assertThat(pii.name()).isNull();
        assertThat(pii.birthDate()).isNull();
    }
}
